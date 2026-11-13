package io.github.wwwvolley.easylog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * logcat `-v threadtime` 的行解析。
 *
 * 这些用例全部取自真机 `adb logcat -v threadtime` 的原样输出（含 tag 后面的对齐空格），
 * 改正则前先跑一遍 —— 正文里的 ` : ` 是最容易踩的一处。
 */
class LogcatParserTest {

    @Test
    fun `解析标准的 threadtime 行`() {
        val line = LogcatParser.parse(
            "11-13 17:13:56.123  4821  4930 I YBDL    : [PROVISION][连接] 开始连接",
        )

        assertEquals(LogLevel.INFO, line?.level)
        assertEquals("YBDL", line?.tag)
        // 模块/分类的拆分留给 LogParser，这里原样保留
        assertEquals("[PROVISION][连接] 开始连接", line?.message)
    }

    @Test
    fun `正文以星号开头时升级为关键节点并去掉星号`() {
        val line = LogcatParser.parse(
            "11-13 17:13:56.123  4821  4930 I YBDL    : ★ [扫描][流程] 命中目标设备",
        )

        assertEquals(LogLevel.STEP, line?.level)
        assertEquals("[扫描][流程] 命中目标设备", line?.message)
    }

    @Test
    fun `tag 与冒号之间没有空格也能解析`() {
        val line = LogcatParser.parse("11-13 17:13:56.123  4821  4930 D Tag:hello")

        assertEquals(LogLevel.DEBUG, line?.level)
        assertEquals("Tag", line?.tag)
        assertEquals("hello", line?.message)
    }

    @Test
    fun `正文里的冒号不会把正文截断`() {
        val line = LogcatParser.parse(
            "11-13 17:13:56.123  4821  4930 I YBDL    : GET https://api.example.com:8443/robot/bind : retry",
        )

        assertEquals("GET https://api.example.com:8443/robot/bind : retry", line?.message)
    }

    @Test
    fun `各级别映射正确`() {
        val cases = mapOf(
            "V" to LogLevel.VERBOSE,
            "D" to LogLevel.DEBUG,
            "I" to LogLevel.INFO,
            "W" to LogLevel.WARN,
            "E" to LogLevel.ERROR,
            "A" to LogLevel.ERROR,
        )
        cases.forEach { (flag, expected) ->
            val line = LogcatParser.parse("11-13 17:13:56.123  4821  4930 $flag YBDL    : x")
            assertEquals("级别 $flag", expected, line?.level)
        }
    }

    @Test
    fun `非日志行直接忽略`() {
        assertNull(LogcatParser.parse("--------- beginning of main"))
        assertNull(LogcatParser.parse("--------- beginning of crash"))
        assertNull(LogcatParser.parse(""))
        assertNull(LogcatParser.parse("任意一段普通文本"))
    }

    @Test
    fun `空正文不报错`() {
        val line = LogcatParser.parse("11-13 17:13:56.123  4821  4930 I YBDL    :")

        assertEquals(LogLevel.INFO, line?.level)
        assertEquals("", line?.message)
    }
}
