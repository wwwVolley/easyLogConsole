package io.github.wwwvolley.easylog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 过滤条件：级别门槛、链路、关键字，以及「记住的链路已经不在了」这类边界。
 *
 * 这些用例锁的是**面板重建与增量追加必须同一口径** —— 两侧都调 [ConsoleFilter.matches]，
 * 早期版本在两处各写一遍判断，改了一处忘了另一处会导致「新来的日志不受过滤」。
 */
class ConsoleFilterTest {

    private fun entry(
        level: LogLevel = LogLevel.DEBUG,
        trace: String = "PROVISION",
        module: String = "连接",
        category: String = "流程",
        message: String = "开始连接",
    ) = LogEntry(
        id = 0L,
        timeMillis = 1L,
        level = level,
        trace = trace,
        module = module,
        category = category,
        message = message,
        threadName = "main",
    )

    @Test
    fun `默认只看 DEBUG 以上`() {
        val filter = ConsoleFilter.DEFAULT
        assertFalse(filter.matches(entry(level = LogLevel.VERBOSE)))
        assertTrue(filter.matches(entry(level = LogLevel.DEBUG)))
        assertTrue(filter.matches(entry(level = LogLevel.STEP)))
        assertTrue(filter.matches(entry(level = LogLevel.ERROR)))
    }

    @Test
    fun `按链路筛掉别的链路`() {
        val filter = ConsoleFilter(trace = "PROVISION")
        assertTrue(filter.matches(entry(trace = "PROVISION")))
        assertFalse(filter.matches(entry(trace = "SWITCH")))
    }

    @Test
    fun `关键字命中模块分类正文，忽略大小写`() {
        val e = entry(module = "连接", category = "流程", message = "GATT connected")
        assertTrue(ConsoleFilter(query = "连接").matches(e))
        assertTrue(ConsoleFilter(query = "流程").matches(e))
        assertTrue(ConsoleFilter(query = "gatt").matches(e))
        assertFalse(ConsoleFilter(query = "扫描").matches(e))
    }

    @Test
    fun `关键字首尾空白不算内容`() {
        // 输入框里常见「连接 」这种尾随空格（搜完接着敲），不能因此一条都搜不到
        assertTrue(ConsoleFilter(query = "  连接  ").matches(entry()))
        assertTrue(ConsoleFilter(query = "   ").matches(entry()))
    }

    @Test
    fun `链路名的空串归一成全部链路`() {
        assertEquals(null, ConsoleFilter(trace = "  ").normalized().trace)
        // 但关键字保持原文：trim 会把用户正在输入的尾随空格吃掉，光标会跳
        assertEquals(" a b ", ConsoleFilter(query = " a b ").normalized().query)
    }

    @Test
    fun `isDefault 只在三个维度都默认时为真`() {
        assertTrue(ConsoleFilter().isDefault)
        assertFalse(ConsoleFilter(trace = "SWITCH").isDefault)
        assertFalse(ConsoleFilter(minLevel = LogLevel.ERROR).isDefault)
        assertFalse(ConsoleFilter(query = "x").isDefault)
    }

    @Test
    fun `三个维度同时生效`() {
        val filter = ConsoleFilter(trace = "PROVISION", minLevel = LogLevel.WARN, query = "超时")
        assertFalse(filter.matches(entry(level = LogLevel.INFO, message = "连接超时")))
        assertTrue(filter.matches(entry(level = LogLevel.WARN, message = "连接超时")))
        assertTrue(filter.matches(entry(level = LogLevel.ERROR, message = "连接超时")))
        assertFalse(filter.matches(entry(level = LogLevel.ERROR, message = "连接成功")))
        assertFalse(filter.matches(entry(level = LogLevel.ERROR, message = "连接超时", trace = "SWITCH")))
    }
}
