package io.github.wwwvolley.easylog

import java.util.concurrent.CopyOnWriteArrayList

/**
 * 极简可观察值。
 *
 * 不用 `StateFlow`：核心层保持零协程依赖，宿主/单测里都能直接 `set` 完立刻读；
 * 也不引入 Compose 的 `mutableStateOf`，避免核心层被 UI 框架绑死。
 * 监听器列表用 `CopyOnWriteArrayList`，注册/注销与通知并发是安全的。
 */
internal class ObservableValue<T>(initial: T) {

    private val listeners = CopyOnWriteArrayList<(T) -> Unit>()

    @Volatile
    private var current: T = initial

    var value: T
        get() = current
        set(newValue) {
            if (current == newValue) return
            current = newValue
            listeners.forEach { it(newValue) }
        }

    fun addListener(listener: (T) -> Unit): AutoCloseable {
        listeners.add(listener)
        return AutoCloseable { listeners.remove(listener) }
    }
}
