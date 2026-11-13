package io.github.wwwvolley.easylog

/**
 * 日志台的过滤条件，会持久化。
 *
 * 三个维度合在一起是**同一件事**：面板里换了链路、换了级别、敲了关键字，
 * 关闭面板再打开应该还是刚才那套条件 —— 排查一个问题时经常要反复开合面板看日志，
 * 每次都重新选一遍链路、重打一遍关键字是最容易让人放弃这类工具的地方。
 *
 * 与 [EasyLogConfig] 的分工和 [ConsoleAppearance] 一样：config 是代码里定的一次性参数，
 * 这里是**用着用着就想调**的东西，所以走 `SharedPreferences` 记住。
 *
 * 宿主也可以主动设置，比如「启动后默认只看配网链路」：
 * ```kotlin
 * EasyLog.setFilter(ConsoleFilter(trace = "PROVISION"))
 * ```
 *
 * @property trace 只看某条链路（`null` = 全部链路）。存的是链路名本身 ——
 *   链路名来自日志正文的 `[链路]` 前缀，重装、清空日志后依然有效。
 * @property minLevel 最低级别（含）。默认 [LogLevel.DEBUG]：正常调试不想看 VERBOSE 噪音，
 *   要看全量时在面板上切不到比它更低的值，需要的话用 [setFilter] 传 [LogLevel.VERBOSE]。
 * @property query 关键字，匹配模块 / 分类 / 正文 / 链路，忽略大小写。
 */
data class ConsoleFilter(
    val trace: String? = null,
    val minLevel: LogLevel = LogLevel.DEBUG,
    val query: String = "",
) {

    /** 是否等于默认条件（全部链路 + DEBUG 以上 + 无关键字）。 */
    val isDefault: Boolean
        get() = trace == null && minLevel == LogLevel.DEBUG && query.isEmpty()

    /**
     * 规范化：链路名去空白，空串视为「不筛链路」。
     *
     * **不对 [query] 做 trim**：输入框里的原文要原样留在框里（
     * 用户可能正打到一半，中间带空格也是常态），只在 [matches] 时忽略首尾空白。
     */
    fun normalized(): ConsoleFilter =
        copy(trace = trace?.trim()?.takeIf(String::isNotEmpty))

    /** 这条日志是否符合当前条件。面板重建列表与增量追加都走这里，保证两处口径一致。 */
    fun matches(entry: LogEntry): Boolean {
        if (trace != null && entry.trace != trace) return false
        if (!entry.level.covers(minLevel)) return false
        val q = query.trim()
        if (q.isEmpty()) return true
        return entry.message.contains(q, ignoreCase = true) ||
            entry.module.contains(q, ignoreCase = true) ||
            entry.category.contains(q, ignoreCase = true) ||
            entry.trace.contains(q, ignoreCase = true)
    }

    companion object {
        /** 默认条件：全部链路、DEBUG 及以上、无关键字。 */
        val DEFAULT: ConsoleFilter = ConsoleFilter()
    }
}
