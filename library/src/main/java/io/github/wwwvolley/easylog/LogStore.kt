package io.github.wwwvolley.easylog

import kotlin.collections.ArrayDeque

/**
 * 环形缓冲：只保留最近 [maxSize] 条，满了从最旧的开始丢。
 *
 * ## 为什么不用 Flow 往下游推
 * 日志会从 BLE 回调线程、协程线程、主线程同时进来，频率可能到每秒上百条。
 * 用 `MutableSharedFlow` 往下游每来一条就 emit，面板侧就得为每一条做一次
 * Compose 状态更新 —— 这在真机上就是肉眼可见的掉帧。
 * 这里改成「写端只管入队 + 自增 id，读端按 id 增量拉」，面板每 120ms 取一批，
 * 100 条日志也只触发一次重组。
 *
 * ## 线程安全
 * 入队、快照、清空都走同一把锁。临界区里只有 `ArrayDeque` 操作和一次 `copy`，
 * 不格式化、不做正则，持有时间在微秒级。
 */
internal class LogStore(capacity: Int) {

    private val maxSize = capacity.coerceIn(MIN_CAPACITY, MAX_CAPACITY)
    private val lock = Any()
    private val buffer = ArrayDeque<LogEntry>(maxSize)

    private var nextId = 1L
    private var errorSequence = 0L

    /** 已经产生过多少条 WARN/ERROR —— 悬浮球上的红点靠它判断「有没有新的异常」。 */
    val errorCount: Long
        get() = synchronized(lock) { errorSequence }

    val size: Int
        get() = synchronized(lock) { buffer.size }

    val latestId: Long
        get() = synchronized(lock) { nextId - 1 }

    /**
     * 入队一条。[entry] 的 id 会被忽略，由这里统一分配。
     */
    fun append(entry: LogEntry): LogEntry {
        val stored: LogEntry
        synchronized(lock) {
            stored = entry.copy(id = nextId++)
            buffer.addLast(stored)
            if (buffer.size > maxSize) {
                buffer.removeFirst()
            }
            if (stored.level.covers(LogLevel.WARN)) {
                errorSequence++
            }
        }
        return stored
    }

    /** 全量快照，按时间正序（最旧 → 最新）。 */
    fun snapshot(): List<LogEntry> = synchronized(lock) { buffer.toList() }

    /**
     * 增量快照：只要 id 大于 [sinceId] 的部分。
     *
     * 从尾部往前找，找到第一条 id ≤ sinceId 就停，只反转取到的那一小段，
     * 避免每次都把 3000 条复制一遍。
     */
    fun snapshotSince(sinceId: Long): List<LogEntry> = synchronized(lock) {
        if (buffer.isEmpty() || buffer.last().id <= sinceId) return emptyList()
        val tail = ArrayList<LogEntry>(buffer.size)
        for (entry in buffer.asReversed()) {
            tail.add(entry)
            if (entry.id <= sinceId) {
                tail.removeAt(tail.lastIndex)
                break
            }
        }
        tail.reverse()
        tail
    }

    fun clear() = synchronized(lock) {
        buffer.clear()
    }

    companion object {
        private const val MIN_CAPACITY = 200
        private const val MAX_CAPACITY = 20_000
    }
}
