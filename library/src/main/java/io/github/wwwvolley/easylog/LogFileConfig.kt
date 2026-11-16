package io.github.wwwvolley.easylog

import java.io.File

/**
 * 落盘配置。**默认不落盘** —— `EasyLogConfig.file` 为 `null` 时库一个字节都不写。
 *
 * ## 为什么做成「宿主指定目录」而不是给个默认路径
 * 日志文件该放哪儿是宿主的决定，不是库的：
 * - 想给人捞：`context.getExternalFilesDir("logs")`，USB 连电脑就能拷；
 * - 想长期留：`context.filesDir/logs`，卸载才清；
 * - 想跟自己的日志放一起：直接给宿主自己的目录，库只 append 不截断。
 *
 * 库**不会**删掉目录里任何不是自己滚出来的文件，也不会改宿主的写入策略 ——
 * 宿主继续用自己的方式写自己的文件，两边互不影响，最多是同一个目录多一个名字。
 *
 * ## 线程与开销
 * 打点时只把一行字符串丢进无锁队列就返回；真正写文件在库自己的单线程上，
 * 每 [flushIntervalMs] 批量写一次并 flush。队列满（默认 20000 行）时**直接丢弃新日志**，
 * 宁可少几行，也不让业务线程因为磁盘慢而卡住。
 *
 * @property directory 目录。不存在会被创建；创建失败会在面板里显示原因，不影响 App 运行。
 * @property fileNamePattern 文件名模板，支持占位符：
 *   `{date}` → 20261113、`{time}` → 163045、`{datetime}` → 20261113-163045、
 *   `{pid}` → 进程号、`{package}` → 包名。非法字符会被替换成 `_`。
 * @property minLevel 低于这个级别的不写文件（默认 DEBUG，与 logcat 的 `d` 对齐）。
 * @property maxFileBytes 单文件字节上限，超过就滚动（默认 2MB）。
 * @property maxFiles 保留的文件数（含当前文件，默认 5）。超出的最旧文件会被删。
 * @property maxAgeHours 保留时长（小时），`null`（默认）= 不按时间清。
 *   超过这个时长的文件会被删掉，**与 [maxFiles] 同时生效**，谁先触发算谁。
 *   两个维度解决的是不同问题：[maxFiles] 管「体积封顶」，这个管「时间范围」——
 *   日志很稀疏时（一天几百 KB）靠文件数永远删不到，时间维度才能真的只留最近一天。
 * @property flushIntervalMs 批量落盘的间隔（100 ~ 10000ms，默认 800ms）。
 * @property includeThreadName 每行是否带线程名。
 */
data class LogFileConfig(
    val directory: File,
    val fileNamePattern: String = "easylog_{date}.log",
    val minLevel: LogLevel = LogLevel.DEBUG,
    val maxFileBytes: Long = 2L * 1024 * 1024,
    val maxFiles: Int = 5,
    val maxAgeHours: Int? = null,
    val flushIntervalMs: Long = 800L,
    val includeThreadName: Boolean = false,
) {

    internal fun normalized(): LogFileConfig = copy(
        fileNamePattern = fileNamePattern.ifBlank { "easylog_{date}.log" },
        maxFileBytes = maxFileBytes.coerceIn(64L * 1024, 64L * 1024 * 1024),
        maxFiles = maxFiles.coerceIn(1, 50),
        // 上限 30 天：再长就不该靠滚动文件了，该上服务端
        maxAgeHours = maxAgeHours?.coerceIn(1, 24 * 30),
        flushIntervalMs = flushIntervalMs.coerceIn(100L, 10_000L),
    )
}
