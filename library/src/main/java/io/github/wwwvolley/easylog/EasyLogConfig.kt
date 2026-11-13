package io.github.wwwvolley.easylog

import java.io.File

/**
 * 日志台配置。全部有默认值，`EasyLog.install(context)` 什么都不传就能用。
 *
 * 刻意做成 `data class` 而不是 Builder：配置项都是不可变的「一次性」参数，
 * 用 `copy()` 覆盖个别项宿主侧读起来更短。
 *
 * @property debugOnly **只在可调试包（debug / profile）里出现**。默认 `true`。
 *   判定用的是 `ApplicationInfo.FLAG_DEBUGGABLE`，不是宿主的 `BuildConfig.DEBUG` ——
 *   库拿不到宿主的 BuildConfig，而 FLAG_DEBUGGABLE 是运行时真实依据：
 *   release 签名包装上去也打不开，改这个值还得改代码重新打包。
 *   想在 release 包里也留着（内部灰度包）才置 `false`。
 * @property enabled 用户可见的总开关的默认值。会被 `SharedPreferences` 里
 *   「用户上次的选择」覆盖；[debugOnly] 判定不通过时这个值直接被忽略。
 * @property capacity 缓冲条数上限（200 ~ 20000）。手机一屏约 25 行，
 *   3000 条够翻几分钟历史，再多只是白占内存。
 * @property maxMessageChars 单条正文的最大字符数，超出截断（含堆栈）。
 *   防止某次把整个 base64 报文打进来把缓冲挤爆。
 * @property panelAlpha 面板底色不透明度默认值（0.2 ~ 1.0）。运行时可在面板里调，见 [ConsoleAppearance]。
 * @property ballAlpha 悬浮球不透明度默认值（0.15 ~ 1.0）。
 * @property scrimAlpha 面板外遮罩不透明度默认值（0 ~ 0.8）。
 * @property panelHeightFraction 面板高度占屏幕的比例（0.3 ~ 0.9）。
 * @property showThreadName 是否在每行显示线程名。默认关：BLE 排查九成看顺序，
 *   线程名会把一行挤成两行，需要时再打开。
 * @property trigger 打开方式。
 * @property colors 配色。内置 [ConsolePalette] 三套，也可以直接给自定义的 [ConsoleColors]。
 * @property ballVisibility 悬浮球**什么时候出现**。默认常驻；宿主可以按登录态、
 *   当前页面、远程开关等条件自己决定，见 [BallVisibility]。
 * @property file 落盘配置。`null`（默认）= **不写任何文件**，
 *   宿主本来怎么存日志完全不受影响；需要时按 [LogFileConfig] 指定目录即可。
 */
data class EasyLogConfig(
    val debugOnly: Boolean = true,
    val enabled: Boolean = true,
    val capacity: Int = 3000,
    val maxMessageChars: Int = 4000,
    val panelAlpha: Float = 0.82f,
    val ballAlpha: Float = 0.55f,
    val scrimAlpha: Float = 0.30f,
    val panelHeightFraction: Float = 0.62f,
    val showThreadName: Boolean = false,
    val trigger: Trigger = Trigger.FloatingBall,
    val colors: ConsoleColors = ConsoleColors(),
    val ballVisibility: BallVisibility = BallVisibility.Always,
    val file: LogFileConfig? = null,
) {

    internal fun normalized(): EasyLogConfig = copy(
        capacity = capacity.coerceIn(200, 20_000),
        maxMessageChars = maxMessageChars.coerceIn(200, 200_000),
        panelAlpha = panelAlpha.coerceIn(ConsoleAppearance.MIN_ALPHA, 1f),
        ballAlpha = ballAlpha.coerceIn(ConsoleAppearance.MIN_BALL_ALPHA, 1f),
        scrimAlpha = scrimAlpha.coerceIn(0f, ConsoleAppearance.MAX_SCRIM_ALPHA),
        panelHeightFraction = panelHeightFraction.coerceIn(0.3f, 0.9f),
        file = file?.normalized(),
    )

    /**
     * 悬浮球的出现方式。
     *
     * 只做两种，够用且不需要额外权限：
     * - [FloatingBall]：启用后按 [ballVisibility] 决定显不显示；
     * - [Hidden]：完全不显示悬浮球，由宿主自己调
     *   `EasyLogConsoleController.show()` 打开面板（比如「版本号连点 7 次」那种隐藏入口）。
     */
    enum class Trigger {
        FloatingBall,
        Hidden,
    }
}

/**
 * 「现在要不要显示悬浮球」。
 *
 * 每 500ms 被问一次，实现要**足够轻**（不要在里面发请求、不要读磁盘）。
 * 常用来实现：
 * - 只在登录后出现：`{ userRepo.isLoggedIn }`；
 * - 只在配网页出现：`{ currentRoute == "bind" }`；
 * - 跟随远程开关：`{ remoteConfig.showDebugBall }`。
 *
 * 想临时藏一下（某个页面截图、录屏）不用改条件，调
 * `EasyLogConsoleController.setBallVisible(false)` 就够了。
 */
fun interface BallVisibility {

    fun shouldShow(): Boolean

    companion object {

        /** 默认：安装后就常驻（仍受 debugOnly 与用户总开关约束）。 */
        @JvmField
        val Always: BallVisibility = BallVisibility { true }

        /** 只在面板打开过之后出现 —— 适合「不想默认打扰，但给个入口」的场景。 */
        @JvmField
        val AfterFirstOpen: BallVisibility = BallVisibility { EasyLogConsoleController.hasOpenedOnce }
    }
}
