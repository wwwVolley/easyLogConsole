package io.github.wwwvolley.easylog

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 文件名模板解析。纯函数、不碰文件系统，便于单测。
 *
 * 支持的占位符见 [LogFileConfig.fileNamePattern]。
 * 解析完再过一遍非法字符：Windows 的 `\ / : * ? " < > |` 和 NUL 全部换成 `_`，
 * 因为 `getExternalFilesDir` 在部分机型上会被映射成 FUSE 路径，
 * 带 `:` 的名字能创建但 adb 拉不下来（现场拷日志时才发现，很浪费时间）。
 */
internal object LogFileNamer {

    private val ILLEGAL = charArrayOf('/', '\\', ':', '*', '?', '"', '<', '>', '|', '\u0000')

    fun resolve(
        pattern: String,
        timeMillis: Long,
        pid: Int,
        packageName: String?,
    ): String {
        val date = Date(timeMillis)
        val text = pattern
            .replace("{date}", SimpleDateFormat("yyyyMMdd", Locale.US).format(date))
            .replace("{time}", SimpleDateFormat("HHmmss", Locale.US).format(date))
            .replace("{datetime}", SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(date))
            .replace("{pid}", pid.toString())
            .replace("{package}", packageName ?: "app")

        val sanitized = buildString(text.length) {
            text.forEach { append(if (it in ILLEGAL) '_' else it) }
        }
        return sanitized.ifBlank { "easylog.log" }
    }
}
