package io.github.wwwvolley.easylog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 正文前缀解析：宿主既有的 `[模块][分类] 正文` 格式要能拆出来，且不能误伤正文里的方括号。 */
class LogParserTest {

    @Test
    fun `拆出模块与分类`() {
        val (module, category, body) = LogParser.split("[连接][流程] 开始连接 YB-018")
        assertEquals("连接", module)
        assertEquals("流程", category)
        assertEquals("开始连接 YB-018", body)
    }

    @Test
    fun `只有一个方括号时只给模块`() {
        val (module, category, body) = LogParser.split("[扫描] 发现 3 个设备")
        assertEquals("扫描", module)
        assertEquals("", category)
        assertEquals("发现 3 个设备", body)
    }

    @Test
    fun `正文里的方括号不被误拆`() {
        val raw = """下发报文 {"ssid":["a","b"]}"""
        val (module, category, body) = LogParser.split(raw)
        assertEquals("", module)
        assertEquals("", category)
        assertEquals(raw, body)
    }

    @Test
    fun `报文以方括号开头但超过单段长度上限时按正文处理`() {
        val raw = "[这是一段特别特别长的设备型号字符串不该被当成模块] hello"
        val (module, _, body) = LogParser.split(raw)
        assertEquals("", module)
        assertEquals(raw, body)
    }

    @Test
    fun `换行正文不影响解析`() {
        val (module, category, body) = LogParser.split("[GATT][底层] 133\n第二行")
        assertEquals("GATT", module)
        assertEquals("底层", category)
        assertTrue(body.startsWith("133"))
    }
}
