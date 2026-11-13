package io.github.wwwvolley.easylog

/**
 * 日志级别。
 *
 * 排序即过滤依据（[ordinal] 越大越严重）：面板里选一个「最低级别」，
 * 低于它的行不显示。`STEP` 插在 INFO 与 WARN 之间 —— 它比普通信息重要，
 * 但不是异常，界面上单独用一种颜色（青色）加粗显示，用来标记「关键节点」。
 */
enum class LogLevel(val label: String) {
    /** 最细的过程日志，默认不看。 */
    VERBOSE("V"),

    /** 调试信息。 */
    DEBUG("D"),

    /** 业务信息（接口回包、状态迁移结果）。 */
    INFO("I"),

    /**
     * **关键节点**：配网/绑定这类链路上「走到哪一步了」的路标。
     *
     * 与 [DEBUG] 的区别只在展示：面板把它加粗并左边描一条青线，
     * 手指快速滚动时能靠颜色一眼定位，不用逐行读文字。
     */
    STEP("★"),

    /** 失败但可继续（某个分支提前返回、一次重试）。 */
    WARN("W"),

    /** 链路中断、接口报错。 */
    ERROR("E"),
    ;

    /** 是否达到 [other] 这个门槛（含）。 */
    fun covers(other: LogLevel): Boolean = ordinal >= other.ordinal
}
