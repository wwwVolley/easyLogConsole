package io.github.wwwvolley.easylog

/**
 * 面板的开合状态，供宿主主动控制。
 *
 * 两种用法：
 * - 悬浮球（默认）：用户自己点，宿主不用管；
 * - 隐藏入口（`Trigger.Hidden`）：宿主在「版本号连点 7 次」「摇一摇」这类位置调 [show]。
 *
 * 状态放在库里而不是面板内部，是因为「打开」这个动作可能来自面板之外的任何地方，
 * 而面板挂在另一个组合层级上，两者需要一个共同的锚点。
 */
object EasyLogConsoleController {

    private val open = ObservableValue(false)

    val isOpen: Boolean
        get() = open.value

    fun show() {
        open.value = true
    }

    fun hide() {
        open.value = false
    }

    fun toggle() {
        open.value = !open.value
    }

    fun addListener(listener: (Boolean) -> Unit): AutoCloseable = open.addListener(listener)
}
