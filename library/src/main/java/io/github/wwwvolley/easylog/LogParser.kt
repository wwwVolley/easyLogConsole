package io.github.wwwvolley.easylog

/**
 * 从日志正文里拆出「模块」「分类」。
 *
 * 宿主工程常见的既有格式是 `[连接][流程] 开始扫描`（本项目里
 * `ProvisionTrace` / `SwitchTrace` 就是这个形状），本库不改宿主的打点代码，
 * 只在入库时按前缀拆一次，面板就能直接按模块过滤。
 *
 * 只认**行首**连续最多两个方括号，且每段不超过 [MAX_SEGMENT] 个字：
 * 正文里自己带的 `[xxx]`（比如 JSON 数组、报文里的字段）不会被误拆。
 */
internal object LogParser {

    private const val MAX_SEGMENT = 16

    private val twoSegments = Regex("""^\[([^\[\]\n]{1,$MAX_SEGMENT})\]\s*\[([^\[\]\n]{1,$MAX_SEGMENT})\]\s*([\s\S]*)$""")
    private val oneSegment = Regex("""^\[([^\[\]\n]{1,$MAX_SEGMENT})\]\s*([\s\S]*)$""")

    /**
     * @return 三元组：模块、分类、剩余正文。没匹配上时前两项为空串，正文原样返回。
     */
    fun split(message: String): Triple<String, String, String> {
        twoSegments.matchEntire(message)?.let {
            return Triple(it.groupValues[1], it.groupValues[2], it.groupValues[3])
        }
        oneSegment.matchEntire(message)?.let {
            return Triple(it.groupValues[1], "", it.groupValues[2])
        }
        return Triple("", "", message)
    }
}
