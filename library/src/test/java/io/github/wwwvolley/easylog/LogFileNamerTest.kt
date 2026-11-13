package io.github.wwwvolley.easylog

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 落盘文件名模板。纯函数，边界情况比想象中多（占位符、非法字符、空模板）。 */
class LogFileNamerTest {

    private val time = 1_763_000_000_000L

    private fun expect(pattern: String, timeMillis: Long = time): String =
        SimpleDateFormat(pattern, Locale.US).format(Date(timeMillis))

    @Test
    fun `替换日期占位符`() {
        val name = LogFileNamer.resolve("easylog_{date}.log", time, 100, "com.demo")
        assertEquals("easylog_${expect("yyyyMMdd")}.log", name)
    }

    @Test
    fun `替换时间与日期时间占位符`() {
        val name = LogFileNamer.resolve("{datetime}_{time}.log", time, 100, "com.demo")
        assertEquals("${expect("yyyyMMdd-HHmmss")}_${expect("HHmmss")}.log", name)
    }

    @Test
    fun `替换进程号与包名`() {
        val name = LogFileNamer.resolve("p{pid}_{package}.log", time, 4321, "com.luvbotics.ybdlrobot")
        assertEquals("p4321_com.luvbotics.ybdlrobot.log", name)
    }

    @Test
    fun `包里没有包名时退化成 app`() {
        assertEquals("app.log", LogFileNamer.resolve("{package}.log", time, 1, null))
    }

    @Test
    fun `路径分隔符等非法字符被替换`() {
        val name = LogFileNamer.resolve("a/b\\c:d*e?f\"g<h>i|j.log", time, 1, "p")
        // 一个都不能留：留一个 File() 会拿它当子目录，文件落到别处
        listOf('/', '\\', ':', '*', '?', '"', '<', '>', '|').forEach {
            assertFalse("不该包含 $it", name.contains(it))
        }
        assertTrue(name.endsWith(".log"))
    }

    @Test
    fun `空模板回退到默认名`() {
        assertEquals("easylog.log", LogFileNamer.resolve("", time, 1, "p"))
        assertEquals("easylog.log", LogFileNamer.resolve("   ", time, 1, "p").trim())
    }
}
