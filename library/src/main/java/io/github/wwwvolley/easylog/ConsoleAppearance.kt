package io.github.wwwvolley.easylog

/**
 * 日志台的「外观」，运行时可改、会持久化。
 *
 * 与 [EasyLogConfig] 分开是有原因的：config 是**代码里定的一次性参数**（容量、链路、
 * 落盘目录），改它意味着改代码重新打包；而外观是**用着用着就想调一调的东西**
 * （这个页面太亮、面板挡住关键按钮、文字在深色页面上看不清），
 * 应该能在手机上直接拖着调，调完记住。
 *
 * 面板里「外观」页改的就是这里，三个滑杆 + 一套配色。
 *
 * @property panelAlpha 面板底色不透明度（0.2 ~ 1.0）。默认 0.82：
 *   既要能看清日志，又要能看见面板底下的页面在动。
 * @property ballAlpha 悬浮球不透明度（0.15 ~ 1.0）。默认 0.55，压到 0.15 基本只剩一个影子。
 * @property scrimAlpha 面板外遮罩的不透明度（0 ~ 0.8）。默认 0.30 ——
 *   遮罩只是为了让面板层次分明，调太重就把底下页面糊死了，反而违背调试工具的用途。
 * @property colors 当前配色。
 */
data class ConsoleAppearance(
    val panelAlpha: Float = 0.82f,
    val ballAlpha: Float = 0.55f,
    val scrimAlpha: Float = 0.30f,
    val colors: ConsoleColors = ConsoleColors(),
) {

    fun normalized(): ConsoleAppearance = copy(
        panelAlpha = panelAlpha.coerceIn(MIN_ALPHA, 1f),
        ballAlpha = ballAlpha.coerceIn(MIN_BALL_ALPHA, 1f),
        scrimAlpha = scrimAlpha.coerceIn(0f, MAX_SCRIM_ALPHA),
    )

    companion object {
        const val MIN_ALPHA = 0.2f
        const val MIN_BALL_ALPHA = 0.15f
        const val MAX_SCRIM_ALPHA = 0.8f
    }
}
