package io.github.wwwvolley.easylog

/**
 * 一条已经进入缓冲区的日志，**不可变**。
 *
 * 字段拆得比一条 logcat 细，是因为面板要做按维度过滤：
 * `trace` 用来分链路（PROVISION / SWITCH / 自定义），
 * `module` + `category` 用来分「链路里的哪一环、哪一类」，
 * 这样手指在真机上点两下就能把「连接」这一段的日志挑出来，不用敲 grep。
 *
 * @property id 单调自增，面板靠它做增量拉取（`entriesSince`），也是列表的稳定 key。
 * @property timeMillis 记录时刻。
 * @property level 级别。
 * @property trace 链路名，例如 `PROVISION`、`SWITCH`；单条日志没有链路时为空串。
 * @property module 模块，例如 `连接`、`扫描`；无法解析时为空串。
 * @property category 分类，例如 `流程`、`异常`；无法解析时为空串。
 * @property message 正文（已按配置截断）。
 * @property threadName 记录线程名。
 * @property throwable 异常的堆栈文本（已折叠），没有则为 null。
 */
data class LogEntry(
    val id: Long,
    val timeMillis: Long,
    val level: LogLevel,
    val trace: String,
    val module: String,
    val category: String,
    val message: String,
    val threadName: String,
    val throwable: String? = null,
) {

    /** 面板里左侧那一段：`连接 · 流程`。两者都为空时退化成 [trace]。 */
    val scopeLabel: String
        get() = when {
            module.isNotEmpty() && category.isNotEmpty() -> "$module · $category"
            module.isNotEmpty() -> module
            category.isNotEmpty() -> category
            else -> trace
        }

    /** 复制/分享时用的纯文本行。 */
    fun toPlainLine(): String = buildString {
        append(EasyLogTime.formatClock(timeMillis))
        append(' ')
        append(level.label)
        append(' ')
        if (trace.isNotEmpty()) {
            append('[').append(trace).append("] ")
        }
        if (module.isNotEmpty()) {
            append('[').append(module).append(']')
        }
        if (category.isNotEmpty()) {
            append('[').append(category).append(']')
        }
        if (module.isNotEmpty() || category.isNotEmpty()) append(' ')
        append(message)
        throwable?.let { append('\n').append(it) }
    }
}
