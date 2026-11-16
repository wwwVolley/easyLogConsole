package io.github.wwwvolley.easylog

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「这个文件是不是本库滚出来的」。
 *
 * 判定错了后果不对称：
 * - 判宽了 → 删掉宿主目录里的别的文件，**不可恢复**；
 * - 判窄了 → 过期文件留着，最坏只是多占几 MB。
 * 所以用例里刻意把「长得像但不是」的名字都列了一遍。
 */
class LogFileRetentionTest {

    private val pattern = "easylog_{date}.log"

    @Test
    fun `自己写的文件和它的滚动副本都算自己的`() {
        listOf(
            "easylog_20261116.log",
            "easylog_20261116.log.1",
            "easylog_20261116.log.12",
        ).forEach {
            assertTrue(it, LogFileRetention.isOwnFile(it, pattern))
        }
    }

    @Test
    fun `别人的文件一律不算`() {
        listOf(
            "crash.log",
            "other_easylog_20261116.log",
            "easylog_20261116.txt",
            "easylog",
            ".log",
            "",
        ).forEach {
            assertFalse(it, LogFileRetention.isOwnFile(it, pattern))
        }
    }

    @Test
    fun `占位符在中间时按前后缀夹住`() {
        val p = "logs_{datetime}_run.log"
        assertTrue(LogFileRetention.isOwnFile("logs_20261116-163045_run.log", p))
        assertTrue(LogFileRetention.isOwnFile("logs_20261116-163045_run.log.2", p))
        assertFalse(LogFileRetention.isOwnFile("logs_20261116-163045_other.log", p))
    }

    @Test
    fun `模板以占位符结尾时没有后缀要求`() {
        val p = "easylog_{date}"
        assertTrue(LogFileRetention.isOwnFile("easylog_20261116", p))
        assertFalse(LogFileRetention.isOwnFile("easylog_", p))
    }

    @Test
    fun `固定文件名模板只认自己`() {
        val p = "app.log"
        assertTrue(LogFileRetention.isOwnFile("app.log", p))
        assertTrue(LogFileRetention.isOwnFile("app.log.3", p))
        assertFalse(LogFileRetention.isOwnFile("app2.log", p))
    }

    @Test
    fun `名字里带小数段不会被当成滚动序号截坏`() {
        // `stripRollIndex` 只剥「最后一段全是数字」，`v1.2.log` 的最后一段是 log，
        // 不能把 `.2` 当滚动序号切掉，否则后缀就对不上了
        assertTrue(LogFileRetention.isOwnFile("easylog_v1.2.log", pattern))
    }
}
