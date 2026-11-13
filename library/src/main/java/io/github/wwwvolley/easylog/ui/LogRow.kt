package io.github.wwwvolley.easylog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.wwwvolley.easylog.ConsoleColors
import io.github.wwwvolley.easylog.EasyLogTime
import io.github.wwwvolley.easylog.LogEntry
import io.github.wwwvolley.easylog.LogLevel

/**
 * 面板里的一行。
 *
 * @property entry 原始日志。
 * @property deltaMillis 与界面上一条（更旧的）可见行的间隔，-1 表示这是第一行。
 */
internal data class UiRow(
    val entry: LogEntry,
    val deltaMillis: Long,
)

/**
 * 单行渲染：第一行是「时间 / 间隔 / 级别 / 范围」，第二行才是正文。
 *
 * - 间隔（`+120ms`）比绝对时间戳更常看：配网卡顿时先问「这一步等了多久」，
 *   而不是「几点几分几秒」。
 * - 正文默认最多 3 行，点一下展开全部。真机上没法横滑，长报文折行读比左右拖现实。
 * - STEP 与 ERROR 左侧有 2dp 色条，手指快速滚动时靠色块定位，不用逐行读文字。
 * - 颜色全部来自 [LocalConsoleColors]：面板里换配色，这里整片跟着变，不用重启。
 */
@Composable
internal fun LogRow(
    row: UiRow,
    expanded: Boolean,
    showThread: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val colors: ConsoleColors = LocalConsoleColors.current
    val entry = row.entry
    val levelColor = argb(colors.colorOf(entry.level))
    val emphasize = entry.level == LogLevel.STEP || entry.level == LogLevel.ERROR

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (entry.level == LogLevel.ERROR) levelColor.copy(alpha = 0.07f)
                else Color.Transparent,
            )
            .pointerInput(entry.id) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongClick() },
                )
            }
            .drawBehind {
                if (emphasize) {
                    drawRect(
                        color = levelColor,
                        topLeft = Offset.Zero,
                        size = Size(ConsoleStyle.RowMarkWidth.toPx(), size.height),
                    )
                }
            }
            .padding(start = 10.dp, end = 10.dp, top = 3.dp, bottom = 3.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row {
                BasicText(
                    text = EasyLogTime.formatClock(entry.timeMillis),
                    style = ConsoleStyle.meta(colors),
                )
                if (row.deltaMillis >= 0L) {
                    Spacer(Modifier.width(6.dp))
                    BasicText(
                        text = EasyLogTime.formatDelta(row.deltaMillis),
                        style = ConsoleStyle.meta(colors).copy(
                            color = argb(colors.textTertiary).copy(alpha = 0.75f),
                        ),
                    )
                }
                Spacer(Modifier.width(6.dp))
                BasicText(
                    text = entry.level.label,
                    style = ConsoleStyle.meta(colors).copy(
                        color = levelColor,
                        fontWeight = if (emphasize) FontWeight.Medium else FontWeight.Normal,
                    ),
                )
                if (entry.scopeLabel.isNotEmpty()) {
                    Spacer(Modifier.width(6.dp))
                    BasicText(
                        text = entry.scopeLabel,
                        style = ConsoleStyle.meta(colors).copy(color = levelColor.copy(alpha = 0.85f)),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (showThread && entry.threadName.isNotEmpty()) {
                    Spacer(Modifier.width(6.dp))
                    BasicText(
                        text = "@${entry.threadName}",
                        style = ConsoleStyle.meta(colors),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            BasicText(
                text = entry.message,
                style = ConsoleStyle.message(colors).copy(
                    color = when (entry.level) {
                        LogLevel.ERROR -> argb(colors.errorText)
                        LogLevel.STEP -> argb(colors.stepText)
                        else -> argb(colors.text)
                    },
                ),
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
            )

            entry.throwable?.let { stack ->
                BasicText(
                    text = stack,
                    style = ConsoleStyle.meta(colors).copy(color = levelColor.copy(alpha = 0.8f)),
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
