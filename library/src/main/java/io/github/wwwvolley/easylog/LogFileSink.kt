package io.github.wwwvolley.easylog

import android.os.Process
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * 把日志写到开发者指定的目录。
 *
 * ## 打点线程只做一件事：入队
 * [accept] 里只有一次 `level` 判断和一次 `offer`，绝不做 IO。
 * BLE 回调线程上多花 1ms，整条配网链路就多 1ms 抖动 —— 排查时序问题时这点抖动是致命的。
 *
 * ## 队列满时丢新日志
 * 磁盘写不动（存储满、被 SAF 挂住）时队列会涨，涨满 20000 行后**丢弃新日志**。
 * 反过来（丢旧的、留新的）会让文件里出现时间空洞却看不出来，更糟。
 * 丢弃本身会计入 [droppedCount]，面板里能看见。
 *
 * ## 滚动（两个维度，谁先触发算谁）
 * - **按体积**：当前文件超过 [LogFileConfig.maxFileBytes] 就改名为 `xxx.log.1`，
 *   旧的依次后移，超 [LogFileConfig.maxFiles] 的最旧一个删掉；
 * - **按时间**：配了 [LogFileConfig.maxAgeHours] 时，`lastModified()` 早于
 *   `now - maxAgeHours` 的文件也会被删掉。
 *
 * 两者缺一不可：文件数管「总量封顶」，但日志稀疏时（一天几百 KB）
 * 永远滚不到上限，只有时间维度才能做到「真的只留最近一天」。
 *
 * 清理只动按 [LogFileConfig.fileNamePattern] 认得出的文件（见 [LogFileRetention]），
 * 目录里别人的文件一律不碰。
 */
internal class LogFileSink(
    private val config: LogFileConfig,
    private val packageName: String?,
    private val pid: Int = Process.myPid(),
) : AutoCloseable {

    private val queue = LinkedBlockingQueue<String>(MAX_QUEUE)

    private val executor: ScheduledExecutorService =
        Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "easyLog-file").apply { isDaemon = true }
        }

    @Volatile
    private var writer: BufferedWriter? = null

    @Volatile
    private var currentFile: File? = null

    /** 上次做过期清理的时刻。0 表示还没清过，首轮落盘就会清一次。 */
    @Volatile
    private var lastPruneAtMs = 0L

    /** 最近一次写盘失败的原因，面板里直接显示给人看。 */
    @Volatile
    var lastError: String? = null
        private set

    /** 因为队列满被丢掉的条数。 */
    @Volatile
    var droppedCount: Long = 0L
        private set

    val currentPath: String?
        get() = currentFile?.absolutePath

    init {
        executor.scheduleWithFixedDelay(
            {
                runCatching { flushQueue() }
                    .onFailure { lastError = it.message ?: it::class.java.simpleName }
            },
            config.flushIntervalMs,
            config.flushIntervalMs,
            TimeUnit.MILLISECONDS,
        )
    }

    fun accept(entry: LogEntry) {
        if (!entry.level.covers(config.minLevel)) return
        if (!queue.offer(format(entry))) droppedCount++
    }

    /** 立即把队列写完并 flush。宿主在退出前想保证落全时调它。 */
    fun flushNow() {
        runCatching { flushQueue() }
            .onFailure { lastError = it.message ?: it::class.java.simpleName }
    }

    override fun close() {
        executor.shutdown()
        // 给自己 300ms 把队列写完；写不完就放弃，别在退出路径上挂太久
        executor.awaitTermination(300, TimeUnit.MILLISECONDS)
        runCatching { flushQueue() }
        runCatching { writer?.close() }
        writer = null
        currentFile = null
    }

    // ── 内部 ──────────────────────────────────────────────────────

    private fun format(entry: LogEntry): String = buildString(96) {
        append(EasyLogTime.formatClock(entry.timeMillis))
        append(' ')
        append(entry.level.label)
        append(' ')
        if (entry.trace.isNotEmpty()) append('[').append(entry.trace).append("] ")
        if (entry.module.isNotEmpty()) append('[').append(entry.module).append(']')
        if (entry.category.isNotEmpty()) append('[').append(entry.category).append(']')
        if (entry.module.isNotEmpty() || entry.category.isNotEmpty()) append(' ')
        if (config.includeThreadName && entry.threadName.isNotEmpty()) {
            append("(@").append(entry.threadName).append(") ")
        }
        append(entry.message)
        entry.throwable?.let { append('\n').append(it) }
    }

    private fun flushQueue() {
        if (queue.isEmpty() && writer == null) return

        val dir = config.directory
        if (!dir.isDirectory && !dir.mkdirs() && !dir.isDirectory) {
            lastError = "无法创建目录：${dir.absolutePath}"
            queue.clear()
            return
        }

        val out = ensureWriter() ?: return

        val batch = StringBuilder(4096)
        while (true) {
            val line = queue.poll() ?: break
            batch.append(line).append('\n')
            // 一次最多攒 256KB，避免长时间离线后一次性拼出几百 MB 的串
            if (batch.length >= MAX_BATCH_CHARS) break
        }
        if (batch.isNotEmpty()) {
            out.write(batch.toString())
            out.flush()
            lastError = null
        }

        val size = currentFile?.length() ?: 0L
        if (size >= config.maxFileBytes) roll()

        pruneExpiredIfDue()
    }

    /**
     * 按 [LogFileConfig.maxAgeHours] 删过期文件。
     *
     * 挂在落盘周期里做，不单独起线程：一是不值得为「一天清一次」再养一个线程，
     * 二是这个时机天然「目录刚被确认可写」，失败也只是少删几个文件，不影响写日志。
     * 用 [PRUNE_INTERVAL_MS] 节流，避免每 800ms 扫一遍目录。
     */
    private fun pruneExpiredIfDue() {
        val hours = config.maxAgeHours ?: return
        val now = System.currentTimeMillis()
        if (now - lastPruneAtMs < PRUNE_INTERVAL_MS) return
        lastPruneAtMs = now

        val cutoff = now - hours * 3_600_000L
        val currentName = currentFile?.name
        config.directory.listFiles()?.forEach { file ->
            // 正在写的那个永远不删：写句柄还开着，删了文件会变成孤儿，
            // 后续写入落到已 unlink 的 inode 上，看起来就是「日志突然不更新了」
            if (!file.isFile || file.name == currentName) return@forEach
            if (!LogFileRetention.isOwnFile(file.name, config.fileNamePattern)) return@forEach
            if (file.lastModified() < cutoff) runCatching { file.delete() }
        }
    }

    private fun ensureWriter(): BufferedWriter? {
        writer?.let { return it }
        val name = LogFileNamer.resolve(config.fileNamePattern, System.currentTimeMillis(), pid, packageName)
        val file = File(config.directory, name)
        return runCatching {
            // append = true：库如果被重启，同一个文件里接着写，不擦掉上一次的内容
            BufferedWriter(FileWriter(file, true)).also {
                writer = it
                currentFile = file
            }
        }.onFailure {
            lastError = "打开文件失败：${it.message}"
        }.getOrNull()
    }

    /**
     * 滚动：`a.log` → `a.log.1` → `a.log.2` …，超过 [LogFileConfig.maxFiles] 的删掉。
     *
     * 从大往小移，否则 `a.log.1` 会被 `a.log.2` 覆盖掉。
     */
    private fun roll() {
        val file = currentFile ?: return
        runCatching { writer?.close() }
        writer = null

        val dir = config.directory
        for (index in config.maxFiles - 1 downTo 1) {
            val from = File(dir, "${file.name}.$index")
            if (!from.exists()) continue
            val to = File(dir, "${file.name}.${index + 1}")
            if (index + 1 >= config.maxFiles) from.delete() else from.renameTo(to)
        }
        file.renameTo(File(dir, "${file.name}.1"))
        currentFile = null
    }

    private companion object {
        const val MAX_QUEUE = 20_000
        const val MAX_BATCH_CHARS = 256 * 1024

        /** 过期清理的节流间隔。清理要扫目录，只有 800ms 一次的落盘节奏才需要节流。 */
        const val PRUNE_INTERVAL_MS = 5 * 60_000L
    }
}
