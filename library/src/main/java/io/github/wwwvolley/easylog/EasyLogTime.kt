package io.github.wwwvolley.easylog

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 时间格式化。
 *
 * 不用 `java.time`：本库 minSdk 23，`java.time` 要 26 或额外开脱糖，
 * 为了一个时间戳给宿主加脱糖配置不划算。`SimpleDateFormat` 非线程安全，
 * 所以放 `ThreadLocal` —— 日志会从 BLE 回调线程、协程线程同时进来。
 */
internal object EasyLogTime {

    private val clock = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue(): SimpleDateFormat =
            SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    }

    /** `14:23:07.481` */
    fun formatClock(timeMillis: Long): String = clock.get()!!.format(Date(timeMillis))

    /**
     * 与上一条的间隔。配网排查时最常看的就是这个数：
     * 「连接耗时 30s」「轮询间隔 1.5s」比绝对时间戳直观得多。
     */
    fun formatDelta(deltaMillis: Long): String = when {
        deltaMillis < 0L -> ""
        deltaMillis < 1_000L -> "+${deltaMillis}ms"
        deltaMillis < 60_000L -> "+%.1fs".format(Locale.US, deltaMillis / 1000.0)
        else -> "+%dm%02ds".format(Locale.US, deltaMillis / 60_000L, (deltaMillis % 60_000L) / 1000L)
    }
}
