package io.github.wwwvolley.easylog

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * 落盘：写入内容、级别门槛、大小滚动。
 *
 * 全部走真实文件系统（JUnit 的临时目录），不 mock —— 这个类最容易出错的正是
 * 「目录没建成」「改名顺序错了」这类真机才暴露的问题，mock 掉就测不到了。
 *
 * `flushIntervalMs` 一律设成 60 秒，让定时线程不掺和，只测 `flushNow()` 的确定性行为。
 */
class LogFileSinkTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun entry(
        level: LogLevel = LogLevel.INFO,
        module: String = "连接",
        category: String = "流程",
        message: String = "m",
        throwable: String? = null,
    ) = LogEntry(
        id = 1L,
        timeMillis = 1_763_000_000_000L,
        level = level,
        trace = "PROVISION",
        module = module,
        category = category,
        message = message,
        threadName = "main",
        throwable = throwable,
    )

    private fun sink(
        dir: File,
        minLevel: LogLevel = LogLevel.DEBUG,
        maxFileBytes: Long = 2L * 1024 * 1024,
        maxFiles: Int = 3,
    ) = LogFileSink(
        config = LogFileConfig(
            directory = dir,
            fileNamePattern = "easylog.log",
            minLevel = minLevel,
            maxFileBytes = maxFileBytes,
            maxFiles = maxFiles,
            flushIntervalMs = 60_000L,
        ),
        packageName = "com.demo",
        pid = 4242,
    )

    @Test
    fun `写出级别 链路 模块 分类与正文`() {
        val dir = temp.newFolder("basic")
        sink(dir).use { log ->
            log.accept(entry(level = LogLevel.STEP, message = "开始连接"))
            log.flushNow()
        }

        val text = File(dir, "easylog.log").readText()
        assertTrue(text, text.contains("★ [PROVISION] [连接][流程] 开始连接"))
    }

    @Test
    fun `异常堆栈换行写在正文下面`() {
        val dir = temp.newFolder("throwable")
        sink(dir).use { log ->
            log.accept(entry(level = LogLevel.ERROR, throwable = "java.io.IOException: boom"))
            log.flushNow()
        }

        val text = File(dir, "easylog.log").readText()
        assertTrue(text, text.contains("java.io.IOException: boom"))
    }

    @Test
    fun `低于门槛的级别不写文件`() {
        val dir = temp.newFolder("level")
        sink(dir, minLevel = LogLevel.WARN).use { log ->
            log.accept(entry(level = LogLevel.DEBUG, message = "不该出现"))
            log.accept(entry(level = LogLevel.WARN, message = "该出现"))
            log.flushNow()
        }

        val text = File(dir, "easylog.log").readText()
        assertFalse(text, text.contains("不该出现"))
        assertTrue(text, text.contains("该出现"))
    }

    @Test
    fun `超过大小上限就滚动并保留旧文件`() {
        val dir = temp.newFolder("roll")
        sink(dir, maxFileBytes = 200, maxFiles = 3).use { log ->
            repeat(5) { log.accept(entry(message = "line-$it " + "x".repeat(80))) }
            log.flushNow()
            log.accept(entry(message = "after-roll " + "y".repeat(80)))
            log.flushNow()
        }

        val names = dir.list()?.toList().orEmpty()
        assertTrue("应有滚动副本：$names", names.any { it.endsWith(".1") })
        assertTrue("当前文件应重新创建：$names", names.any { it == "easylog.log" })
    }

    @Test
    fun `超过保留数量的最旧副本被删掉`() {
        val dir = temp.newFolder("prune")
        sink(dir, maxFileBytes = 120, maxFiles = 2).use { log ->
            repeat(6) {
                log.accept(entry(message = "gen$it " + "z".repeat(100)))
                log.flushNow()
            }
        }

        val names = dir.listFiles()?.map { it.name }.orEmpty()
        // maxFiles = 2 → 最多一个 .1 副本；.2 及以上不该存在
        assertFalse("不该留 .2：$names", names.any { it.endsWith(".2") })
        assertTrue("至少该有一个滚动副本：$names", names.any { it.endsWith(".1") })
    }

    @Test
    fun `目录不存在时自动创建`() {
        val dir = File(temp.newFolder("nested"), "a/b/c")
        sink(dir).use { log ->
            log.accept(entry(message = "hi"))
            log.flushNow()
        }

        val file = File(dir, "easylog.log")
        assertTrue("应自动建目录并落文件：${file.absolutePath}", file.exists())
    }
}
