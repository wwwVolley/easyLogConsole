package io.github.wwwvolley.easylog.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.wwwvolley.easylog.ConsoleAppearance
import io.github.wwwvolley.easylog.ConsoleColors
import io.github.wwwvolley.easylog.ConsoleFilter
import io.github.wwwvolley.easylog.EasyLog
import io.github.wwwvolley.easylog.EasyLogConfig
import io.github.wwwvolley.easylog.LogEntry
import io.github.wwwvolley.easylog.LogLevel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** 过滤条件在 [ConsoleFilter] 上（核心层，可持久化），这里只放轮询与裁剪相关的常量。 */
private const val POLL_INTERVAL_MS = 120L
private const val MAX_SHARE_CHARS = 120_000

/**
 * 过滤条件落盘的延迟。
 *
 * 搜索框是「每敲一个字就改一次条件」，直接写 `SharedPreferences` 会变成
 * 输入一个词提交十几次磁盘写；去抖到手指停下 400ms 再写一次就够，
 * 即使进程在 400ms 内被杀，丢的也只是最后几个字符。
 */
private const val FILTER_SAVE_DELAY_MS = 400L

/**
 * 半透明日志台：底部抽屉，上方的界面仍然可见。
 *
 * ## 透明度
 * 面板底色 = `colors.panel` × `appearance.panelAlpha`，遮罩 = `colors.scrim` × `appearance.scrimAlpha`。
 * 两个值都能在「外观」页里用滑杆调，默认 82% / 30% —— 面板要能读、遮罩不能把页面糊死。
 *
 * ## 刷新策略
 * 一个 `LaunchedEffect(filters, paused)` 同时管「重建」和「增量」：
 * 分成两个 effect 会出现重建与追加并发改同一个列表的窗口，日志会重复或丢行。
 * 循环里每 [POLL_INTERVAL_MS] 毫秒按 id 拉一次增量，100 条日志也只触发一次重组。
 *
 * ## 为什么 `reverseLayout = true`
 * index 0 贴着底部，新日志天然出现在手指最近的位置，不用写「滚动到底部」的动画，
 * 日志密集时也不会抖。
 *
 * ## 用户的操作会被记住
 * 面板开合会销毁整个 Composable，所以状态不能放 `remember`，分两层存：
 * - **跨进程（设置）**：过滤的链路 / 级别 / 关键字 —— [EasyLog.filter]，落盘在
 *   `SharedPreferences`；改动去抖 [FILTER_SAVE_DELAY_MS] 后写，离开组合时再兜一次。
 * - **同进程（会话态）**：暂停、停在外观页、展开了哪几行 —— [PanelSessionState]。
 * 一句话：点悬浮球重新打开，看到的是离开时那个面板，而不是一个刚出厂的默认态。
 */
@Composable
internal fun LogPanel(
    config: EasyLogConfig,
    appearance: ConsoleAppearance,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val colors: ConsoleColors = LocalConsoleColors.current

    // 过滤条件：跨进程记住 —— 初值就是上次落盘的那套
    val initialFilter = remember { EasyLog.filter }
    var traceFilter by remember { mutableStateOf(initialFilter.trace) }
    var minLevel by remember { mutableStateOf(initialFilter.minLevel) }
    var query by remember { mutableStateOf(initialFilter.query) }

    // 会话态（暂停 / 停在外观页 / 展开了哪几行）：同一进程里关掉再打开跟没关过一样。
    // 这三项不落盘，原因见 PanelSessionState 的注释
    val paused = PanelSessionState.paused
    val settingsOpen = PanelSessionState.settingsOpen
    val expanded = PanelSessionState.expandedIds

    val rows = remember { mutableStateListOf<UiRow>() }
    val traces = remember { mutableStateListOf<String>() }

    var lastId by remember { mutableLongStateOf(0L) }
    var totalCount by remember { mutableStateOf(0) }

    val listState = rememberLazyListState()
    val filters = ConsoleFilter(traceFilter, minLevel, query)

    // 输入时去抖落盘（一个词不该换十几次磁盘写）
    LaunchedEffect(filters) {
        if (filters == EasyLog.filter) return@LaunchedEffect
        delay(FILTER_SAVE_DELAY_MS)
        EasyLog.setFilter(filters)
    }
    // 离开组合（关面板 / 页面销毁 / 用户把日志台关掉）时再兜一次：
    // 「敲完关键字立刻点 ✕」不会因为没到去抖时间而丢掉这次的输入
    val latestFilters = rememberUpdatedState(filters)
    DisposableEffect(Unit) {
        onDispose { EasyLog.setFilter(latestFilters.value) }
    }

    LaunchedEffect(filters, paused) {
        val all = EasyLog.entries()
        totalCount = all.size
        traces.clear()
        traces.addAll(all.mapNotNullTo(LinkedHashSet()) { it.trace.takeIf(String::isNotEmpty) })

        rows.clear()
        var previousTime = 0L
        for (entry in all.filter { filters.matches(it) }) {
            rows.add(0, UiRow(entry, deltaOf(entry, previousTime)))
            previousTime = entry.timeMillis
        }
        lastId = all.lastOrNull()?.id ?: 0L
        if (rows.isNotEmpty()) listState.scrollToItem(0)

        if (paused) return@LaunchedEffect

        while (isActive) {
            delay(POLL_INTERVAL_MS)
            totalCount = EasyLog.size()
            val fresh = EasyLog.entriesSince(lastId)
            if (fresh.isEmpty()) continue
            lastId = fresh.last().id
            var newestTime = rows.firstOrNull()?.entry?.timeMillis ?: 0L
            for (entry in fresh) {
                if (!filters.matches(entry)) continue
                rows.add(0, UiRow(entry, deltaOf(entry, newestTime)))
                newestTime = entry.timeMillis
            }
            while (rows.size > config.capacity) {
                rows.removeAt(rows.lastIndex)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // 遮罩的浓度也可调：默认 30%，只用来分层，不用来遮挡
            .background(argb(colors.scrim).copy(alpha = appearance.scrimAlpha))
            // 键盘弹出时整体上移：搜索是要边打边看结果的，被键盘盖住这半屏等于白搜。
            // 放在 background 之后 = scrim 依旧铺满全屏，只有内容让位
            .imePadding()
            .pointerInput(Unit) { detectTapGestures { onClose() } },
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(config.panelHeightFraction)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(argb(colors.panel).copy(alpha = appearance.panelAlpha))
                // 吃掉面板内的点击，别冒到 scrim 上把面板关了
                .pointerInput(Unit) { detectTapGestures { } }
                .navigationBarsPadding(),
        ) {
            PanelHeader(
                total = totalCount,
                paused = paused,
                settingsOpen = settingsOpen,
                colors = colors,
                onTogglePause = { PanelSessionState.paused = !paused },
                onToggleSettings = { PanelSessionState.settingsOpen = !settingsOpen },
                onClear = {
                    EasyLog.clear()
                    PanelSessionState.forgetExpanded()
                    rows.clear()
                    lastId = 0L
                    totalCount = 0
                },
                onCopy = { copyToClipboard(context, rows) },
                onShare = { share(context, rows) },
                onClose = onClose,
            )

            if (settingsOpen) {
                AppearanceSettings(
                    config = config,
                    appearance = appearance,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
            } else {
                TraceChips(traces = traces, selected = traceFilter, colors = colors) { traceFilter = it }

                LevelChips(selected = minLevel, colors = colors) { minLevel = it }

                SearchBar(value = query, colors = colors, onValueChange = { query = it })

                Divider(colors)

                if (rows.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText(
                            // 有过滤条件却空着，必须说清楚是「筛没了」还是「真没有」，
                            // 否则用户会以为日志没在采
                            text = if (filters.isDefault) {
                                "暂无日志 · 触发一次链路看看"
                            } else {
                                "当前过滤条件下没有日志 · 点「全部链路」或清空关键字"
                            },
                            style = ConsoleStyle.meta(colors).copy(fontSize = 12.sp),
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        reverseLayout = true,
                        contentPadding = PaddingValues(bottom = 6.dp),
                    ) {
                        items(items = rows, key = { it.entry.id }) { row ->
                            LogRow(
                                row = row,
                                expanded = row.entry.id in expanded,
                                showThread = config.showThreadName,
                                onClick = {
                                    PanelSessionState.expandedIds =
                                        if (row.entry.id in expanded) {
                                            expanded - row.entry.id
                                        } else {
                                            expanded + row.entry.id
                                        }
                                },
                                onLongClick = { copyToClipboard(context, listOf(row)) },
                            )
                        }
                    }
                }

                val fileState = if (EasyLog.isFileLogging) " · 落盘中" else ""
                // 过滤生效时把命中数摆出来：光看列表变短不容易判断是「筛掉了」还是「没日志进来」
                val filterState = if (filters.isDefault) "" else " · 筛出 ${rows.size} 条"
                BasicText(
                    text = when {
                        paused -> "已暂停 · 点「继续」恢复刷新$filterState$fileState"
                        else -> "共 $totalCount 条$filterState · 自动跟随最新$fileState"
                    },
                    style = ConsoleStyle.meta(colors).copy(fontSize = 9.sp),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
        }
    }
}

private fun deltaOf(entry: LogEntry, previousTime: Long): Long =
    if (previousTime == 0L) -1L else entry.timeMillis - previousTime

// ── 头部 ──────────────────────────────────────────────────────────

@Composable
private fun PanelHeader(
    total: Int,
    paused: Boolean,
    settingsOpen: Boolean,
    colors: ConsoleColors,
    onTogglePause: () -> Unit,
    onToggleSettings: () -> Unit,
    onClear: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 6.dp, top = 10.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(text = "日志台", style = ConsoleStyle.title(colors))
        Spacer(Modifier.width(6.dp))
        BasicText(text = "$total 条", style = ConsoleStyle.meta(colors))
        Spacer(Modifier.weight(1f))
        if (!settingsOpen) {
            TextAction(
                text = if (paused) "继续" else "暂停",
                onClick = onTogglePause,
                colors = colors,
                color = if (paused) argb(colors.step) else argb(colors.textSecondary),
            )
            TextAction("清空", onClear, colors)
            TextAction("复制", onCopy, colors)
            TextAction("分享", onShare, colors)
        }
        TextAction(
            text = if (settingsOpen) "返回" else "外观",
            onClick = onToggleSettings,
            colors = colors,
            color = if (settingsOpen) argb(colors.step) else argb(colors.textSecondary),
        )
        TextAction("✕", onClose, colors, argb(colors.text))
    }
}

@Composable
private fun TextAction(
    text: String,
    onClick: () -> Unit,
    colors: ConsoleColors,
    color: Color = argb(colors.textSecondary),
) {
    BasicText(
        text = text,
        style = ConsoleStyle.chip(colors).copy(color = color),
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 5.dp),
    )
}

// ── 过滤条 ────────────────────────────────────────────────────────

@Composable
private fun TraceChips(
    traces: List<String>,
    selected: String?,
    colors: ConsoleColors,
    onSelect: (String?) -> Unit,
) {
    // 上次记住的链路这一轮还没出现过（清空过日志、或换了一条链路在测）：
    // 把选中项补在最前面。否则 chips 上一个高亮的都没有、列表却是空的，
    // 看起来像「面板坏了」，用户根本不知道该点哪里才能退回去。
    val visible = if (selected != null && traces.none { it == selected }) {
        listOf(selected) + traces
    } else {
        traces
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Chip(text = "全部链路", selected = selected == null, colors = colors) { onSelect(null) }
        visible.forEach { trace ->
            Chip(text = trace, selected = selected == trace, colors = colors) {
                onSelect(if (selected == trace) null else trace)
            }
        }
    }
}

@Composable
private fun LevelChips(selected: LogLevel, colors: ConsoleColors, onSelect: (LogLevel) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Chip(text = "全部", selected = selected == LogLevel.DEBUG, colors = colors) { onSelect(LogLevel.DEBUG) }
        Chip("关键+", selected == LogLevel.STEP, colors, argb(colors.step)) { onSelect(LogLevel.STEP) }
        Chip("警告+", selected == LogLevel.WARN, colors, argb(colors.warn)) { onSelect(LogLevel.WARN) }
        Chip("异常", selected == LogLevel.ERROR, colors, argb(colors.error)) { onSelect(LogLevel.ERROR) }
    }
}

@Composable
private fun Chip(
    text: String,
    selected: Boolean,
    colors: ConsoleColors,
    color: Color = argb(colors.textSecondary),
    onClick: () -> Unit,
) {
    BasicText(
        text = text,
        style = ConsoleStyle.chip(colors).copy(
            color = if (selected) argb(colors.text) else color.copy(alpha = 0.8f),
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) color.copy(alpha = 0.18f) else argb(colors.surface))
            .border(
                width = 0.5.dp,
                color = if (selected) color.copy(alpha = 0.55f) else argb(colors.stroke),
                shape = RoundedCornerShape(999.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 4.dp),
    )
}

@Composable
private fun SearchBar(value: String, colors: ConsoleColors, onValueChange: (String) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .height(30.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(argb(colors.surface))
            .border(0.5.dp, argb(colors.stroke), RoundedCornerShape(8.dp))
            .padding(horizontal = 9.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            BasicText(
                text = "过滤关键字（模块 / 正文）",
                style = ConsoleStyle.chip(colors).copy(color = argb(colors.textTertiary)),
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = ConsoleStyle.chip(colors).copy(color = argb(colors.text)),
            cursorBrush = SolidColor(argb(colors.step)),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun Divider(colors: ConsoleColors) {
    Box(Modifier.fillMaxWidth().height(0.5.dp).background(argb(colors.divider)))
}

// ── 复制 / 分享 ───────────────────────────────────────────────────

private fun buildText(rows: List<UiRow>): String {
    if (rows.isEmpty()) return "(无日志)"
    val sb = StringBuilder()
    // rows 是「新→旧」，导出按时间正序更符合阅读习惯
    for (row in rows.asReversed()) {
        sb.append(row.entry.toPlainLine()).append('\n')
        if (sb.length > MAX_SHARE_CHARS) {
            sb.append("…(已截断)\n")
            break
        }
    }
    return sb.toString()
}

private fun copyToClipboard(context: Context, rows: List<UiRow>) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("easyLog", buildText(rows)))
    Toast.makeText(context, "已复制 ${rows.size} 条日志", Toast.LENGTH_SHORT).show()
}

/**
 * 分享走 `ACTION_SEND` + `EXTRA_TEXT`，**不落文件**。
 *
 * 落文件要求宿主在自己的清单里注册 `FileProvider` 和 authority ——
 * 一个「装完就能用」的调试库不该逼宿主改清单，两个库各要一个 authority 还会打起来。
 * 文本走 Binder 有约 1MB 上限，这里按 12 万字截断，一次完整配网链路够用。
 */
private fun share(context: Context, rows: List<UiRow>) {
    runCatching {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "easyLog 日志")
            putExtra(Intent.EXTRA_TEXT, buildText(rows))
        }
        context.startActivity(
            Intent.createChooser(send, "分享日志").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.onFailure {
        Toast.makeText(context, "没有可用的分享应用", Toast.LENGTH_SHORT).show()
    }
}
