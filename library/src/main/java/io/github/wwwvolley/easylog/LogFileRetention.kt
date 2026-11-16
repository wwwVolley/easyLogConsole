package io.github.wwwvolley.easylog

/**
 * 按时间清理的判断。纯函数、不碰文件系统，便于单测。
 *
 * ## 为什么不能只看文件名里的日期
 * 模板里的 `{date}` 只在**创建文件时**解析一次，进程不重启就一直是旧日期；
 * 而滚动出来的 `.1` / `.2` 副本名字里还带着原日期，却可能是昨天写的。
 * 拿名字里的日期当依据，会出现「明明刚写过却判定过期」或者反过来。
 * 所以时间一律用文件的 `lastModified()`，这里只负责回答另一个问题：
 * **这个文件是不是本库自己滚出来的**。
 *
 * ## 只删自己的文件
 * 宿主可能把日志目录当自己的在用（[LogFileConfig.directory] 是宿主指定的）。
 * 判定严格按模板的静态前缀 + 静态后缀来，别人的文件一个都不碰。
 */
internal object LogFileRetention {

    /**
     * 从文件名模板推出「哪些文件是本库的」。
     *
     * 模板 `easylog_{date}.log` →
     * 静态前缀 `easylog_`、静态后缀 `.log`；
     * 于是 `easylog_20261116.log`、`easylog_20261116.log.3` 都算自己的，
     * 而 `crash.log`、`easylog-manual.log` 不算。
     *
     * @param fileName 目录里的文件名（不含路径）
     * @param pattern 宿主配置的 [LogFileConfig.fileNamePattern]（原始模板，未解析）
     */
    fun isOwnFile(fileName: String, pattern: String): Boolean {
        if (fileName.isEmpty()) return false
        val open = pattern.indexOf('{')
        val close = pattern.lastIndexOf('}')

        // 模板里没有占位符（固定文件名）：只认它本身和它的滚动副本
        if (open < 0 && close < 0) return stripRollIndex(fileName) == pattern

        val prefix = if (open >= 0) pattern.substring(0, open) else pattern
        val suffix = if (close >= 0 && close + 1 <= pattern.length) pattern.substring(close + 1) else ""

        // 先剥掉滚动序号（`.1` `.2` …），否则后缀永远对不上
        val core = stripRollIndex(fileName)
        if (!core.startsWith(prefix)) return false
        if (suffix.isEmpty()) return core.length > prefix.length
        if (!core.endsWith(suffix)) return false
        // 必须比「前缀 + 后缀」长，否则只是个空壳名字
        return core.length > prefix.length + suffix.length
    }

    /**
     * `a.log.1` → `a.log`；`a.log` → `a.log`。
     *
     * 只认「最后一段全是数字」的情况，避免把 `easylog_v1.2.log` 这类正常名字截坏。
     */
    private fun stripRollIndex(fileName: String): String {
        val dot = fileName.lastIndexOf('.')
        if (dot <= 0 || dot == fileName.length - 1) return fileName
        val tail = fileName.substring(dot + 1)
        return if (tail.all { it.isDigit() }) fileName.substring(0, dot) else fileName
    }
}
