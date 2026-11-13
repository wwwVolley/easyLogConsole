package io.github.wwwvolley.easylog

/** 一行 logcat 解析出来的东西。 */
internal data class LogcatLine(
    val level: LogLevel,
    val tag: String,
    val message: String,
)

/**
 * logcat `-v threadtime` 的单行解析。
 *
 * 形如：
 * ```
 * 11-13 17:13:56.123  1234  5678 I YBDL    : [PROVISION][连接] 开始连接
 * ```
 * 拆成「级别 / tag / 正文」三段。**纯函数**，不碰任何 Android API，可以直接单测。
 *
 * 约定：正文以 `★` 开头的那一行按 [LogLevel.STEP]（关键节点）处理，
 * 这样宿主只要写 `Log.i("YBDL", "★ 开始连接")` 就能在面板里拿到绿色路标，
 * 不用为了配色去专门调本库的 API。
 */
internal object LogcatParser {

    /**
     * 月份 日期 时间 PID TID 级别 TAG : 正文
     *
     * tag 用 `.*?` 惰性匹配到**最后一个** `" : "` 之前 —— tag 本身不含空格，
     * 但正文里可能有 ` : `（URL、JSON 都会出现），所以必须惰性 + 从右侧锚定 `\s*:\s?`。
     */
    private val LINE = Regex(
        """^\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3}\s+\d+\s+\d+\s+([VDIWEFA])\s+(.*?)\s*:\s?(.*)$""",
    )

    /** 正文开头的关键节点标记。 */
    private const val STEP_MARK = "★"

    fun parse(raw: String): LogcatLine? {
        val match = LINE.matchEntire(raw.trimEnd('\r')) ?: return null
        val tag = match.groupValues[2].trim()
        val message = match.groupValues[3]
        val plain = when (match.groupValues[1]) {
            "V" -> LogLevel.VERBOSE
            "D" -> LogLevel.DEBUG
            "I" -> LogLevel.INFO
            "W" -> LogLevel.WARN
            "E", "A", "F" -> LogLevel.ERROR
            else -> return null
        }
        val step = message.startsWith(STEP_MARK)
        return LogcatLine(
            level = if (step) LogLevel.STEP else plain,
            tag = tag,
            message = if (step) message.removePrefix(STEP_MARK).trimStart() else message,
        )
    }
}
