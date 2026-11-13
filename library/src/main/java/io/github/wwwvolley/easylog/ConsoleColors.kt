package io.github.wwwvolley.easylog

/**
 * 日志台配色。
 *
 * 全部是 `0xAARRGGBB` 的 [Long]，**核心层不引 Compose**：
 * 配置对象和单测都在纯 JVM 上跑，`ui` 包里才换算成 `Color`。
 *
 * ## 为什么需要它
 * 日志台是叠在业务页面上的浮层，宿主的底色可能是白、是深蓝、是游戏画面。
 * 同一套「深色底 + 浅字」在某些页面上刚好，在另一些页面上就糊成一片 ——
 * 所以底色和文字色都要能换：内置 [ConsolePalette] 三套预设，
 * 宿主也可以直接构造 `ConsoleColors(...)` 给任意颜色。
 *
 * @property panel 面板底色（不透明；实际不透明度由 `ConsoleAppearance.panelAlpha` 乘上去）。
 * @property scrim 面板外那层遮罩的基色（实际不透明度由 `ConsoleAppearance.scrimAlpha` 决定）。
 * @property surface 面板内部的次级底色：搜索框、未选中的 chip。
 * @property stroke 描边。
 * @property divider 分隔线。
 * @property text 正文与标题的基色。
 * @property textSecondary 次要信息（chip、时间戳）。
 * @property textTertiary 更淡的辅助信息（间隔、线程名）。
 * @property step `STEP` 关键节点色。
 * @property info / warn / error / debug / verbose 各级别色。
 * @property errorText 异常正文的前景色（比 [error] 淡，长堆栈也读得下去）。
 * @property stepText 关键节点正文的前景色。
 * @property ballBackground 悬浮球底色。
 * @property badge 悬浮球上未读异常红点的颜色。
 */
data class ConsoleColors(
    val panel: Long = 0xFF14161A,
    val scrim: Long = 0xFF000000,
    val surface: Long = 0x14FFFFFF,
    val stroke: Long = 0x26FFFFFF,
    val divider: Long = 0x14FFFFFF,
    val text: Long = 0xFFE9EDF2,
    val textSecondary: Long = 0xFF98A2B0,
    val textTertiary: Long = 0xFF6B7480,
    val step: Long = 0xFF4ED8A0,
    val info: Long = 0xFF6EB4F7,
    val warn: Long = 0xFFF5B942,
    val error: Long = 0xFFFF6B6B,
    val debug: Long = 0xFF9AA0A6,
    val verbose: Long = 0xFF6B7480,
    val errorText: Long = 0xFFFFD9D9,
    val stepText: Long = 0xFFD8FFF0,
    val ballBackground: Long = 0xFF161A20,
    val badge: Long = 0xFFFF5D5D,
) {

    /** 某个级别对应的颜色。 */
    fun colorOf(level: LogLevel): Long = when (level) {
        LogLevel.VERBOSE -> verbose
        LogLevel.DEBUG -> debug
        LogLevel.INFO -> info
        LogLevel.STEP -> step
        LogLevel.WARN -> warn
        LogLevel.ERROR -> error
    }
}

/**
 * 内置配色预设。
 *
 * 面板里可以实时切换（写进 `SharedPreferences`，下次冷启动保持），
 * 宿主也可以在 [EasyLogConfig.colors] 里直接指定 —— 代码里给的默认值会被
 * 用户上一次在面板里的选择覆盖，这符合「谁最后动手听谁的」。
 */
enum class ConsolePalette(val label: String, val colors: ConsoleColors) {

    /** 默认。深底浅字，接近 Android Studio 的默认主题。 */
    DARK("暗色", ConsoleColors()),

    /** 终端风：黑绿底，光看颜色就知道这是调试面板，不会和业务界面混淆。 */
    TERMINAL(
        "终端绿",
        ConsoleColors(
            panel = 0xFF07120B,
            surface = 0x1A3DFF88,
            stroke = 0x3D31FF7A,
            divider = 0x1F31FF7A,
            text = 0xFF8CFFB8,
            textSecondary = 0xFF4FD98A,
            textTertiary = 0xFF2E8F5B,
            step = 0xFF35FF8F,
            info = 0xFF6FE3C6,
            warn = 0xFFFFD166,
            error = 0xFFFF6B6B,
            debug = 0xFF4FD98A,
            verbose = 0xFF2E8F5B,
            errorText = 0xFFFFC9C9,
            stepText = 0xFFB8FFD6,
            ballBackground = 0xFF0B1F14,
            badge = 0xFFFF5D5D,
        ),
    ),

    /**
     * 亮底深字。叠在白色页面上想看清下面的内容时用 ——
     * 深色面板即便调到 30% 不透明度，在纯白页面上也像一块灰雾。
     */
    LIGHT(
        "亮色",
        ConsoleColors(
            panel = 0xFFF6F7F9,
            surface = 0x0F000000,
            stroke = 0x1F000000,
            divider = 0x14000000,
            text = 0xFF1A1D21,
            textSecondary = 0xFF5B6470,
            textTertiary = 0xFF8A93A0,
            step = 0xFF0B8F5E,
            info = 0xFF1F6FB2,
            warn = 0xFF9A6A00,
            error = 0xFFC62828,
            debug = 0xFF5B6470,
            verbose = 0xFF8A93A0,
            errorText = 0xFF8B1A1A,
            stepText = 0xFF0A6B47,
            ballBackground = 0xFF2B3038,
            badge = 0xFFD93A3A,
        ),
    ),
    ;

    companion object {
        /** 按持久化的名字找预设，找不到（旧版本留下的键）返回 null。 */
        fun byName(name: String?): ConsolePalette? =
            entries.firstOrNull { it.name == name }
    }
}
