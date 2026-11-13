package io.github.wwwvolley.easylog

import android.os.Build

/**
 * 从 **本进程** 的 logcat 里取日志，喂给 [EasyLog]。
 *
 * 这是「零代码接入」的一半：宿主只要正常 `Log.d/i/w/e`，本库旁路读一份过来
 * 就能在手机上看到 —— 不需要改日志出口、不需要挂 sink、Android Studio 的
 * Logcat 窗口也照旧（我们只是多读了一个 consumer，不消费、不替换）。
 *
 * ## 为什么不直接 hook `Log`
 * Java 的静态方法没法在运行时替换（除非字节码插桩，那会让接入变复杂，
 * 而本项目的目的正是「接入要简单」）。读 logcat 是唯一不需要改宿主代码的路。
 *
 * ## 权限
 * Android 4.1 起普通应用**只能读到自己的进程**，所以这里不加任何权限；
 * API 24+ 再显式带 `--pid`（低版本不支持该参数，靠系统自身的进程隔离）。
 *
 * ## 开销
 * 单独一条低优先级线程，逐行解析（正则不做回溯，单行 O(n)）。
 * 只在 debug 包启用；`stop()` 之后进程与线程都会被回收。
 */
internal class LogcatCapture(
    private val minLevel: LogLevel,
    private val tags: Set<String>,
    private val ignoreTags: Set<String>,
    /** 本应用包名。系统偶尔拿包名当 tag 打行（`W com.foo.bar usrcalluqfd: ...`），一律是噪音。 */
    private val packageTag: String?,
    private val onLine: (level: LogLevel, tag: String, message: String) -> Unit,
) {

    @Volatile
    private var running = false

    @Volatile
    private var process: Process? = null

    private var thread: Thread? = null

    /** 启动失败的原因（比如 ROM 禁了 `logcat`），正常为 `null`。 */
    @Volatile
    var lastError: String? = null
        private set

    val isRunning: Boolean
        get() = running

    fun start() {
        if (running) return
        running = true
        lastError = null
        thread = Thread({ loop() }, THREAD_NAME).apply {
            isDaemon = true
            // 读日志比业务线程次要，抢 CPU 会让配网这种蓝牙时序敏感的场景变慢
            priority = Thread.MIN_PRIORITY
            start()
        }
    }

    fun stop() {
        running = false
        runCatching { process?.destroy() }
        process = null
        thread?.interrupt()
        thread = null
    }

    private fun loop() {
        var failures = 0
        while (running) {
            try {
                val proc = ProcessBuilder(command())
                    // logcat 的报错也走 stdout，统一在同一处读，避免 stderr 缓冲区写满卡住
                    .redirectErrorStream(true)
                    .start()
                process = proc
                failures = 0
                proc.inputStream.bufferedReader().use { reader ->
                    while (running) {
                        val line = reader.readLine() ?: break
                        consume(line)
                    }
                }
                runCatching { proc.destroy() }
                process = null
            } catch (e: InterruptedException) {
                break
            } catch (t: Throwable) {
                lastError = t.message ?: t.javaClass.simpleName
            }

            if (!running) break
            // logcat 自己退出（ROM 限制、被系统回收）时重来一次；连续失败就别死循环了
            if (++failures > MAX_RETRY) break
            try {
                Thread.sleep(RETRY_DELAY_MS)
            } catch (e: InterruptedException) {
                break
            }
        }
        running = false
    }

    private fun consume(raw: String) {
        val line = LogcatParser.parse(raw) ?: return
        if (!line.level.covers(minLevel)) return

        val tag = line.tag
        if (tags.isNotEmpty() && tag !in tags) return
        if (isPackageTag(tag)) return
        // 前缀匹配：`VRI[MainActivity]@1a2b3c` 这种带后缀的也一起挡掉
        if (ignoreTags.isNotEmpty() && ignoreTags.any { tag.startsWith(it) }) return

        onLine(line.level, tag, line.message)
    }

    /** tag 就是本应用包名 —— ART / libcore 拿包名当 tag 打的行，全是噪音。 */
    private fun isPackageTag(tag: String): Boolean {
        val pkg = packageTag ?: return false
        if (tag == pkg) return true
        // logcat 的 tag 上限 23 字符，超长包名会被截断成尾巴（`...easylog.sample`），
        // 所以比尾巴；长度下限避免 `a.b` 这种短 tag 误伤
        return tag.length >= MIN_PACKAGE_TAG_LENGTH && pkg.endsWith(tag)
    }

    private fun command(): List<String> = buildList {
        add("logcat")
        add("-v")
        add("threadtime")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            add("--pid=${android.os.Process.myPid()}")
        }
    }

    private companion object {
        const val THREAD_NAME = "easylog-logcat"
        const val RETRY_DELAY_MS = 1_000L
        const val MAX_RETRY = 3

        /** 比包名尾巴时不接受比这更短的 tag，否则 `com`、`app` 之类会被误挡。 */
        const val MIN_PACKAGE_TAG_LENGTH = 8
    }
}
