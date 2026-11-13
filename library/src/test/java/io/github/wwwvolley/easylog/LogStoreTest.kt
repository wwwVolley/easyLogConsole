package io.github.wwwvolley.easylog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 环形缓冲：容量上限、增量拉取、异常计数。 */
class LogStoreTest {

    private fun entry(level: LogLevel = LogLevel.DEBUG, message: String = "m") = LogEntry(
        id = 0L,
        timeMillis = 1L,
        level = level,
        trace = "T",
        module = "M",
        category = "C",
        message = message,
        threadName = "main",
    )

    @Test
    fun `超过容量时丢弃最旧的`() {
        val store = LogStore(200)
        repeat(250) { store.append(entry(message = "m$it")) }

        val snapshot = store.snapshot()
        assertEquals(200, snapshot.size)
        assertEquals("m50", snapshot.first().message)
        assertEquals("m249", snapshot.last().message)
    }

    @Test
    fun `id 单调自增`() {
        val store = LogStore(200)
        val first = store.append(entry())
        val second = store.append(entry())
        assertEquals(1L, first.id)
        assertEquals(2L, second.id)
    }

    @Test
    fun `增量拉取只给新条目`() {
        val store = LogStore(200)
        repeat(5) { store.append(entry(message = "m$it")) }
        val checkpoint = store.latestId
        repeat(3) { store.append(entry(message = "n$it")) }

        val fresh = store.snapshotSince(checkpoint)
        assertEquals(3, fresh.size)
        assertEquals(listOf("n0", "n1", "n2"), fresh.map { it.message })
        assertTrue(store.snapshotSince(store.latestId).isEmpty())
    }

    @Test
    fun `增量拉取在缓冲被环形截断后仍然只给新条目`() {
        val store = LogStore(200)
        repeat(300) { store.append(entry(message = "m$it")) }
        val fresh = store.snapshotSince(299)
        // 299 之前的都已被丢弃，剩下的应该是 300.. 之后（当前没有新的）
        assertEquals(1, fresh.size)
        assertEquals("m299", fresh.first().message)
    }

    @Test
    fun `警告与异常计入错误计数`() {
        val store = LogStore(200)
        store.append(entry(level = LogLevel.DEBUG))
        store.append(entry(level = LogLevel.STEP))
        store.append(entry(level = LogLevel.WARN))
        store.append(entry(level = LogLevel.ERROR))
        assertEquals(2L, store.errorCount)
    }

    @Test
    fun `清空后 id 不回退`() {
        val store = LogStore(200)
        store.append(entry())
        val before = store.latestId
        store.clear()
        assertEquals(0, store.size)
        val after = store.append(entry())
        assertTrue(after.id > before)
    }
}
