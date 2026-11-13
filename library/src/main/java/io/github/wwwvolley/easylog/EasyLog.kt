package io.github.wwwvolley.easylog

import android.content.Context
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 本库的唯一入口。
 *
 * ## 最小接入（3 行）
 * ```kotlin
 * // Application.onCreate()
 * EasyLog.install(this)
 *
 * // 已有的日志出口后面挂一行（推荐，业务打点代码一行都不用改）
 * AppLog.sink = { level, tag, message, throwable -> EasyLog.accept(level, tag, message, throwable) }
 * ```
 * 之后 Compose 根布局里放一个 `EasyLogConsole()`，或者直接
 * `ComponentActivity.installEasyLogOverlay()`，悬浮球就出来了。
 *
 * ## 设计上的取舍
 * - **不在库里拦截 logcat**：宿主本来就有自己的日志出口（`AppLog` 这种薄封装），
 *   库只提供 `accept` 这个「入口」，谁接谁负责，不需要反射、不需要改框架。
 * - **核心层不依赖 Compose**：`LogStore` / `LogEntry` / `LogParser` 都是纯 Kotlin，
 *   可以在 JVM 单测里直接跑；Compose 只在 `ui` 包里出现。
 * - **写端不等读端**：入队就返回，面板什么时候取由它自己按节流决定，
 *   不会因为「面板没开」而在 BLE 回调线程上多做一次格式化。
 */
object EasyLog {

    @Volatile
    private var config: EasyLogConfig = EasyLogConfig().normalized()

    @Volatile
    private var store: LogStore = LogStore(config.capacity)

    @Volatile
    private var prefs: ConsolePrefs? = null

    @Volatile
    private var installed = false

    private val sinks = CopyOnWriteArrayList<(LogEntry) -> Unit>()

    /**
     * 总开关。面板上的开关按钮改的就是它，值会持久化 ——
     * 下次冷启动保持用户上次的选择，不用每次重新点开。
     */
    private val enabled = ObservableValue(true)

    val isEnabled: Boolean
        get() = enabled.value

    /** 当前生效的配置。 */
    val currentConfig: EasyLogConfig
        get() = config

    /**
     * 初始化。幂等，且**首次调用者说了算**：宿主通常在 `Application.onCreate` 里配一次，
     * 之后 Activity 侧再调（比如 [installEasyLogOverlay]）不会把配置改回默认值，
     * 只会补上缺失的 Context。
     */
    fun install(context: Context, config: EasyLogConfig = EasyLogConfig()) {
        if (installed) {
            if (prefs == null) prefs = ConsolePrefs(context)
            return
        }
        val normalized = config.normalized()
        this.config = normalized
        store = LogStore(normalized.capacity)
        prefs = ConsolePrefs(context)
        enabled.value = prefs?.enabled ?: normalized.enabled
        installed = true
    }

    fun setEnabled(value: Boolean) {
        enabled.value = value
        prefs?.enabled = value
    }

    fun addEnabledListener(listener: (Boolean) -> Unit): AutoCloseable =
        enabled.addListener(listener)

    // ── 打点 ──────────────────────────────────────────────────────

    /**
     * 通用入口：只给级别、链路名和正文，模块/分类从正文的 `[模块][分类]` 前缀里拆。
     *
     * 这是给「已经有日志出口」的宿主用的适配方法，接完老代码一行都不用动。
     */
    fun accept(
        level: LogLevel,
        tag: String,
        message: String,
        throwable: Throwable? = null,
    ) {
        val (module, category, body) = LogParser.split(message)
        record(level, tag, module, category, body, throwable)
    }

    /**
     * 结构化入口：模块/分类由调用方直接给，不做解析。
     *
     * 新代码用这个更准 —— 正文里本来就有方括号时（报文、JSON）不会被误拆。
     */
    fun record(
        level: LogLevel,
        trace: String,
        module: String,
        category: String,
        message: String,
        throwable: Throwable? = null,
    ) {
        if (!isEnabled) return

        val limit = config.maxMessageChars
        val entry = LogEntry(
            id = 0L,
            timeMillis = System.currentTimeMillis(),
            level = level,
            trace = trace,
            module = module,
            category = category,
            message = if (message.length > limit) message.take(limit) + "…(${message.length}字)" else message,
            threadName = Thread.currentThread().name,
            throwable = throwable?.let {
                val text = it.stackTraceToString()
                if (text.length > limit) text.take(limit) + "…" else text
            },
        )

        val stored = store.append(entry)
        // 外接 sink 异常不能影响调用方（日志库把业务线程搞崩是最难查的事故之一）
        sinks.forEach { runCatching { it(stored) } }
    }

    /** 关键节点：面板里青色加粗显示，配网这类链路的路标全走它。 */
    fun step(trace: String, module: String, category: String, message: String) =
        record(LogLevel.STEP, trace, module, category, message)

    /** 失败节点：红色，悬浮球红点计入。提前 return 的分支都应该先打一条。 */
    fun fail(trace: String, module: String, message: String, throwable: Throwable? = null) =
        record(LogLevel.ERROR, trace, module, "", message, throwable)

    fun d(tag: String, message: String) = accept(LogLevel.DEBUG, tag, message)

    fun i(tag: String, message: String) = accept(LogLevel.INFO, tag, message)

    fun w(tag: String, message: String, throwable: Throwable? = null) =
        accept(LogLevel.WARN, tag, message, throwable)

    fun e(tag: String, message: String, throwable: Throwable? = null) =
        accept(LogLevel.ERROR, tag, message, throwable)

    // ── 读取 ──────────────────────────────────────────────────────

    /** 全量快照（旧 → 新）。面板重建过滤结果时用。 */
    fun entries(): List<LogEntry> = store.snapshot()

    /** 增量快照：id 大于 [sinceId] 的部分（旧 → 新）。面板的定时轮询用这个。 */
    fun entriesSince(sinceId: Long): List<LogEntry> = store.snapshotSince(sinceId)

    fun latestId(): Long = store.latestId

    fun errorCount(): Long = store.errorCount

    fun size(): Int = store.size

    fun clear() = store.clear()

    /**
     * 旁路消费：文件落盘、上报、桥接 logcat 之类。
     *
     * 回调在**打点线程**上同步执行，别在里面做 IO —— 需要落盘请自己转线程。
     * 返回的对象 `close()` 就解绑。
     */
    fun addSink(sink: (LogEntry) -> Unit): AutoCloseable {
        sinks.add(sink)
        return AutoCloseable { sinks.remove(sink) }
    }

    internal val ballPreferences: ConsolePrefs?
        get() = prefs
}
