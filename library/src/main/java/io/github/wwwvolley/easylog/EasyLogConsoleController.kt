package io.github.wwwvolley.easylog

/**
 * 面板的开合状态与悬浮球显隐，供宿主主动控制。
 *
 * 三种用法：
 * - 悬浮球（默认）：用户自己点，宿主不用管；
 * - 条件显示：`EasyLogConfig.ballVisibility` 给一个条件，库每 500ms 问一次；
 * - 隐藏入口（`Trigger.Hidden`）：宿主在「版本号连点 7 次」「摇一摇」这类位置调 [show]。
 *
 * 状态放在库里而不是面板内部，是因为「打开」这个动作可能来自面板之外的任何地方，
 * 而面板挂在另一个组合层级上，两者需要一个共同的锚点。
 */
object EasyLogConsoleController {

    private val open = ObservableValue(false)

    /** `null` = 不干预，听 [EasyLogConfig.ballVisibility]；非 `null` = 宿主强制显 / 隐。 */
    private val ballOverride = ObservableValue<Boolean?>(null)

    @Volatile
    private var openedOnce = false

    val isOpen: Boolean
        get() = open.value

    /** 面板是否至少被打开过一次。[BallVisibility.AfterFirstOpen] 用它。 */
    val hasOpenedOnce: Boolean
        get() = openedOnce

    /** 宿主强制显隐悬浮球；传 `null` 交还给 `ballVisibility` 条件判断。 */
    val ballVisibleOverride: Boolean?
        get() = ballOverride.value

    fun show() {
        openedOnce = true
        open.value = true
    }

    fun hide() {
        open.value = false
    }

    fun toggle() {
        if (!open.value) openedOnce = true
        open.value = !open.value
    }

    fun addListener(listener: (Boolean) -> Unit): AutoCloseable = open.addListener(listener)

    /**
     * 临时藏 / 显示悬浮球，优先级高于 `EasyLogConfig.ballVisibility`。
     *
     * 典型用法：某个页面要录屏、要截图，`onResume` 传 `false`、`onPause` 传 `null`。
     * 注意它只影响**球**，面板已经打开的话照常显示。
     */
    fun setBallVisible(visible: Boolean?) {
        ballOverride.value = visible
    }

    fun addBallVisibilityListener(listener: (Boolean?) -> Unit): AutoCloseable =
        ballOverride.addListener(listener)
}
