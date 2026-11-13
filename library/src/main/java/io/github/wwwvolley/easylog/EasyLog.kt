package io.github.wwwvolley.easylog

import android.content.Context
import android.content.pm.ApplicationInfo
import java.io.File
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
 *   库只提供 [accept] 这个「入口」，谁接谁负责。所以宿主的
 *   `Log.d/i/w/e`（Android Studio 的 Logcat 窗口）照常输出，两边互不干扰 ——
 *   这是接入本库**唯一不能省**的一条约束：转发是**旁路**，不是替换。
 * - **不接管文件写入**：本库默认一个文件都不写。要落盘由宿主在
 *   [EasyLogConfig.file] 里指定目录（见 [LogFileConfig]），库只在自己的滚动文件里追加，
 *   不删、不截断、不锁宿主目录里别人的文件。
 * - **核心层不依赖 Compose**：`LogStore` / `LogEntry` / `LogParser` / `LogFileSink`
 *   都是纯 Kotlin/JVM，可以在 JVM 单测里直接跑；Compose 只出现在 `ui` 包里。
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

    /** 本包是否可调试（`FLAG_DEBUGGABLE`）。release 签名包这里是 `false`。 */
    @Volatile
    private var debuggable = false

    @Volatile
    private var fileSink: LogFileSink? = null

    @Volatile
    private var palette: ConsolePalette? = null

    private val sinks = CopyOnWriteArrayList<(LogEntry) -> Unit>()

    /**
     * 总开关。面板上的开关改的就是它，值会持久化 ——
     * 下次冷启动保持用户上次的选择，不用每次重新点开。
     */
    private val enabled = ObservableValue(true)

    /** 运行时外观（透明度、配色），面板里可调，见 [ConsoleAppearance]。 */
    private val appearanceState = ObservableValue(ConsoleAppearance())

    val isEnabled: Boolean
        get() = enabled.value

    /**
     * 本包**是否允许**出现日志台。
     *
     * `debugOnly = true`（默认）且当前不是可调试包时恒为 `false`：
     * 此时 [accept] 会立刻返回（零开销），UI 也一行都不画。
     * 这是「release 包不允许展示」的**唯一**实现点，别在 UI 里另判 `BuildConfig.DEBUG`。
     */
    val isAvailable: Boolean
        get() = installed && (!config.debugOnly || debuggable)

    /** UI 是否该画东西：允许出现 **且** 用户没关掉。 */
    val canShow: Boolean
        get() = isAvailable && enabled.value

    /** 当前生效的配置。 */
    val currentConfig: EasyLogConfig
        get() = config

    /** 当前生效的外观。 */
    val appearance: ConsoleAppearance
        get() = appearanceState.value

    /** 当前配色对应的预设；宿主自己传了自定义 [ConsoleColors] 且用户没换过时为 `null`。 */
    val currentPalette: ConsolePalette?
        get() = palette

    /** 落盘文件名（绝对路径），没开落盘时为 `null`。 */
    val fileLogPath: String?
        get() = fileSink?.currentPath

    /** 落盘异常原因，正常为 `null`。面板会显示它。 */
    val fileLogError: String?
        get() = fileSink?.lastError

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
        val app = context.applicationContext ?: context
        debuggable = (app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

        val normalized = config.normalized()
        this.config = normalized
        store = LogStore(normalized.capacity)
        prefs = ConsolePrefs(app)
        installed = true

        // 先 assign 配置再判 isAvailable：release 包里这里直接落成 false，
        // 用户之前手动开过也不会让它在 release 包里冒出来
        enabled.value = if (!isAvailable) false else (prefs?.enabled ?: normalized.enabled)

        val savedPalette = ConsolePalette.byName(prefs?.palette)
        palette = savedPalette
        appearanceState.value = ConsoleAppearance(
            panelAlpha = prefs?.panelAlpha ?: normalized.panelAlpha,
            ballAlpha = prefs?.ballAlpha ?: normalized.ballAlpha,
            scrimAlpha = prefs?.scrimAlpha ?: normalized.scrimAlpha,
            colors = savedPalette?.colors ?: normalized.colors,
        ).normalized()

        normalized.file?.let { startFileLogging(it) }
    }

    fun setEnabled(value: Boolean) {
        // release 包里想把开关打开？直接忽略。省得改天有人从别处调进了 release
        if (value && !isAvailable) return
        enabled.value = value
        prefs?.enabled = value
    }

    fun addEnabledListener(listener: (Boolean) -> Unit): AutoCloseable =
        enabled.addListener(listener)

    // ── 外观 ──────────────────────────────────────────────────────

    /**
     * 改外观（面板里的滑杆走这里）。传 `null` 表示这一项不变。
     *
     * 改动会持久化，下次冷启动沿用；想恢复代码里的默认值就 [resetAppearance]。
     */
    fun updateAppearance(
        panelAlpha: Float? = null,
        ballAlpha: Float? = null,
        scrimAlpha: Float? = null,
        palette: ConsolePalette? = null,
    ) {
        val current = appearanceState.value
        val next = current.copy(
            panelAlpha = panelAlpha ?: current.panelAlpha,
            ballAlpha = ballAlpha ?: current.ballAlpha,
            scrimAlpha = scrimAlpha ?: current.scrimAlpha,
            colors = palette?.colors ?: current.colors,
        ).normalized()

        appearanceState.value = next
        prefs?.apply {
            this.panelAlpha = next.panelAlpha
            this.ballAlpha = next.ballAlpha
            this.scrimAlpha = next.scrimAlpha
            if (palette != null) this.palette = palette.name
        }
        if (palette != null) this.palette = palette
    }

    /** 外观恢复成 [EasyLogConfig] 里给的值，并清掉本地记住的偏好。 */
    fun resetAppearance() {
        prefs?.apply {
            panelAlpha = null
            ballAlpha = null
            scrimAlpha = null
            palette = null
        }
        palette = null
        appearanceState.value = ConsoleAppearance(
            panelAlpha = config.panelAlpha,
            ballAlpha = config.ballAlpha,
            scrimAlpha = config.scrimAlpha,
            colors = config.colors,
        ).normalized()
    }

    fun addAppearanceListener(listener: (ConsoleAppearance) -> Unit): AutoCloseable =
        appearanceState.addListener(listener)

    // ── 落盘 ──────────────────────────────────────────────────────

    /**
     * 开始落盘。已经在落盘的会先停掉再按新配置起 —— 面板里改目录走这条路。
     *
     * 宿主自己也在写日志时**不需要**关掉自己的写入：两边是独立的文件句柄，
     * 库只碰 [LogFileConfig.fileNamePattern] 生成的那个文件及其滚动副本。
     */
    fun startFileLogging(config: LogFileConfig) {
        fileSink?.close()
        fileSink = LogFileSink(config.normalized(), packageName = prefs?.packageName)
    }

    fun stopFileLogging() {
        fileSink?.close()
        fileSink = null
    }

    val isFileLogging: Boolean
        get() = fileSink != null

    /** 把当前缓冲区导出成一个文本文件（不动落盘任务），返回写好的文件；失败返回 `null`。 */
    fun exportTo(target: File): File? = runCatching {
        target.parentFile?.mkdirs()
        target.writeText(entries().joinToString("\n") { it.toPlainLine() })
        target
    }.getOrNull()

    // ── 打点 ──────────────────────────────────────────────────────

    /**
     * 通用入口：只给级别、链路名和正文，模块/分类从正文的 `[模块][分类]` 前缀里拆。
     *
     * 这是给「已经有日志出口」的宿主用的适配方法，接完老代码一行都不用动。
     * **宿主不要在这里面做 `Log.x` 以外的事**：本方法只负责收集，
     * 控制台（Android Studio Logcat）的输出仍然由宿主自己的 `Log.x` 负责。
     */
    fun accept(
        level: LogLevel,
        tag: String,
        message: String,
        throwable: Throwable? = null,
    ) {
        if (!canShow) return
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
        if (!canShow) return

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
        fileSink?.accept(stored)
        // 外接 sink 异常不能影响调用方（日志库把业务线程搞崩是最难查的事故之一）
        sinks.forEach { runCatching { it(stored) } }
    }

    /** 关键节点：面板里绿色加粗显示，配网这类链路的路标全走它。 */
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
     * 旁路消费：上报、自己再落一份、桥接别的 SDK 之类。
     *
     * 回调在**打点线程**上同步执行，别在里面做 IO —— 需要落盘请自己转线程，
     * 或者直接用 [EasyLogConfig.file]（库已经帮你异步化了）。
     * 返回的对象 `close()` 就解绑。
     */
    fun addSink(sink: (LogEntry) -> Unit): AutoCloseable {
        sinks.add(sink)
        return AutoCloseable { sinks.remove(sink) }
    }

    internal val ballPreferences: ConsolePrefs?
        get() = prefs
}
