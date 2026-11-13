package io.github.wwwvolley.easylog

/**
 * 日志台配置。全部有默认值，`EasyLog.install(context)` 什么都不传就能用。
 *
 * 刻意做成 `data class` 而不是 Builder：配置项少、且都是不可变的「一次性」参数，
 * 用 `copy()` 覆盖个别项宿主侧读起来更短。
 *
 * @property enabled 是否启用（总开关）。`install` 时会用本地保存的用户选择覆盖它，
 *   所以这里给 `true` 表示「默认开」；不想默认开就传 `false`。
 * @property capacity 缓冲条数上限（200 ~ 20000）。手机屏幕一屏约 25 行，
 *   3000 条已经够翻几分钟的历史，再多只是白占内存。
 * @property maxMessageChars 单条正文的最大字符数，超出截断（含堆栈）。
 *   防止某次把整个 base64 报文打进来，把缓冲挤爆。
 * @property panelAlpha 面板底色不透明度（0.5 ~ 1.0）：半透明才能看见底下的界面。
 * @property panelHeightFraction 面板高度占屏幕的比例（0.3 ~ 0.9）。
 * @property showThreadName 是否在每行显示线程名。默认关：BLE 排查九成看顺序，
 *   线程名会把一行挤成两行，需要时再打开。
 * @property trigger 打开方式。
 */
data class EasyLogConfig(
    val enabled: Boolean = true,
    val capacity: Int = 3000,
    val maxMessageChars: Int = 4000,
    val panelAlpha: Float = 0.86f,
    val panelHeightFraction: Float = 0.62f,
    val showThreadName: Boolean = false,
    val trigger: Trigger = Trigger.FloatingBall,
) {

    internal fun normalized(): EasyLogConfig = copy(
        capacity = capacity.coerceIn(200, 20_000),
        maxMessageChars = maxMessageChars.coerceIn(200, 200_000),
        panelAlpha = panelAlpha.coerceIn(0.5f, 1f),
        panelHeightFraction = panelHeightFraction.coerceIn(0.3f, 0.9f),
    )

    /**
     * 悬浮球的出现方式。
     *
     * 只做两种，够用且不需要额外权限：
     * - [FloatingBall]：启用就常驻，适合内部包 / debug 包；
     * - [Hidden]：不显示悬浮球，由宿主自己调 `EasyLogOpen.show(context)` 打开面板
     *   （比如「版本号连点 7 次」那种隐藏入口）。
     */
    enum class Trigger {
        FloatingBall,
        Hidden,
    }
}
