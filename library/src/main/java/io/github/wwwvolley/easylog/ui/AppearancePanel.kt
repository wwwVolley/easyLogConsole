package io.github.wwwvolley.easylog.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.wwwvolley.easylog.ConsoleAppearance
import io.github.wwwvolley.easylog.ConsoleColors
import io.github.wwwvolley.easylog.ConsolePalette
import io.github.wwwvolley.easylog.EasyLog
import io.github.wwwvolley.easylog.EasyLogConfig
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 「外观」页：面板 / 悬浮球 / 遮罩的不透明度 + 配色。
 *
 * ## 为什么放在面板里，而不是只给代码配置
 * 这几项是**当场就要调**的：面板挡住关键按钮、文字在深色页面上看不清、
 * 录屏时球太显眼。改代码重新打包的代价太大，滑杆拖一下立刻生效才叫能用。
 * 调完的值写进 SharedPreferences，下次冷启动沿用；「恢复默认」清掉这些键。
 *
 * 顺带把落盘状态放在这一页：路径、是否有错、以及一个「导出」按钮 ——
 * 真机上想拿到文件，比翻文件管理器方便。
 */
@Composable
internal fun AppearanceSettings(
    config: EasyLogConfig,
    appearance: ConsoleAppearance,
    modifier: Modifier = Modifier,
) {
    val colors: ConsoleColors = LocalConsoleColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SectionTitle("不透明度", colors)

        AlphaSlider(
            label = "面板",
            value = appearance.panelAlpha,
            range = ConsoleAppearance.MIN_ALPHA..1f,
            colors = colors,
        ) { EasyLog.updateAppearance(panelAlpha = it) }

        AlphaSlider(
            label = "悬浮球",
            value = appearance.ballAlpha,
            range = ConsoleAppearance.MIN_BALL_ALPHA..1f,
            colors = colors,
        ) { EasyLog.updateAppearance(ballAlpha = it) }

        AlphaSlider(
            label = "遮罩",
            value = appearance.scrimAlpha,
            range = 0f..ConsoleAppearance.MAX_SCRIM_ALPHA,
            colors = colors,
        ) { EasyLog.updateAppearance(scrimAlpha = it) }

        SectionTitle("配色", colors)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val current = EasyLog.currentPalette
            ConsolePalette.entries.forEach { palette ->
                PaletteChip(
                    palette = palette,
                    selected = current == palette,
                    colors = colors,
                ) { EasyLog.updateAppearance(palette = palette) }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                text = "改动会记住，下次启动沿用",
                style = ConsoleStyle.meta(colors).copy(fontSize = 9.sp),
            )
            Spacer(Modifier.weight(1f))
            BasicText(
                text = "恢复默认",
                style = ConsoleStyle.chip(colors).copy(color = argb(colors.step)),
                modifier = Modifier
                    .clickable { EasyLog.resetAppearance() }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }

        SectionTitle("落盘", colors)
        FileSection(config = config, colors = colors) {
            scope.launch {
                val dir = config.file?.directory
                    ?: File(context.getExternalFilesDir(null) ?: context.cacheDir, "easylog")
                val target = File(dir, "easylog_export_${System.currentTimeMillis()}.txt")
                val written = withContext(Dispatchers.IO) { EasyLog.exportTo(target) }
                Toast.makeText(
                    context,
                    if (written != null) "已导出：${written.absolutePath}" else "导出失败",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }

        Spacer(Modifier.height(12.dp))
    }
}

// ── 落盘状态 ──────────────────────────────────────────────────────

@Composable
private fun FileSection(config: EasyLogConfig, colors: ConsoleColors, onExport: () -> Unit) {
    val fileConfig = config.file
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp)) {
        if (fileConfig == null) {
            BasicText(
                text = "未开启。需要留存时在 EasyLogConfig.file 里指定目录 ——\n" +
                    "库只 append 自己的文件，不影响你原有的落盘逻辑。",
                style = ConsoleStyle.meta(colors).copy(fontSize = 10.sp, lineHeight = 15.sp),
            )
        } else {
            val path = EasyLog.fileLogPath ?: fileConfig.directory.absolutePath
            BasicText(
                text = "目录：${fileConfig.directory.absolutePath}",
                style = ConsoleStyle.meta(colors).copy(fontSize = 10.sp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            BasicText(
                text = "文件：${path.substringAfterLast('/')}",
                style = ConsoleStyle.meta(colors).copy(fontSize = 10.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            EasyLog.fileLogError?.let { error ->
                Spacer(Modifier.height(2.dp))
                BasicText(
                    text = "写入异常：$error",
                    style = ConsoleStyle.meta(colors).copy(fontSize = 10.sp, color = argb(colors.error)),
                )
            }
        }

        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(argb(colors.surface))
                .border(0.5.dp, argb(colors.stroke), RoundedCornerShape(8.dp))
                .clickable(onClick = onExport),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                text = "导出当前 ${EasyLog.size()} 条到文件",
                style = ConsoleStyle.chip(colors).copy(color = argb(colors.text)),
            )
        }
    }
}

// ── 滑杆 ──────────────────────────────────────────────────────────

/**
 * 极简滑杆。
 *
 * 不引 `material3`：本库只依赖 compose.foundation，
 * 为了三个滑杆把 Material 拖进宿主（尤其是没用 Material 的工程）不值得。
 * 轨道和滑块直接 `drawBehind` 画，点击与拖动各一个 `pointerInput`。
 */
@Composable
private fun AlphaSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    colors: ConsoleColors,
    onChange: (Float) -> Unit,
) {
    val fraction = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BasicText(text = label, style = ConsoleStyle.chip(colors))
            Spacer(Modifier.weight(1f))
            BasicText(
                text = "${(value * 100).roundToInt()}%",
                style = ConsoleStyle.meta(colors).copy(color = argb(colors.textSecondary)),
            )
        }
        Spacer(Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(26.dp)
                .pointerInput(range) {
                    detectTapGestures { offset ->
                        onChange(toValue(offset.x, size.width.toFloat(), range))
                    }
                }
                .pointerInput(range) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        onChange(toValue(change.position.x, size.width.toFloat(), range))
                    }
                }
                .drawBehind {
                    val trackHeight = 4.dp.toPx()
                    val corner = CornerRadius(trackHeight / 2f)
                    val centerY = size.height / 2f
                    val thumbRadius = 7.dp.toPx()
                    val cx = (size.width * fraction).coerceIn(thumbRadius, size.width - thumbRadius)

                    drawRoundRect(
                        color = argb(colors.surface),
                        topLeft = Offset(0f, centerY - trackHeight / 2f),
                        size = Size(size.width, trackHeight),
                        cornerRadius = corner,
                    )
                    drawRoundRect(
                        color = argb(colors.step),
                        topLeft = Offset(0f, centerY - trackHeight / 2f),
                        size = Size(size.width * fraction, trackHeight),
                        cornerRadius = corner,
                    )
                    drawCircle(color = argb(colors.step), radius = thumbRadius, center = Offset(cx, centerY))
                    drawCircle(
                        color = argb(colors.panel),
                        radius = thumbRadius - 3.dp.toPx(),
                        center = Offset(cx, centerY),
                    )
                },
        )
    }
}

private fun toValue(x: Float, width: Float, range: ClosedFloatingPointRange<Float>): Float {
    if (width <= 0f) return range.start
    val fraction = (x / width).coerceIn(0f, 1f)
    return range.start + fraction * (range.endInclusive - range.start)
}

@Composable
private fun SectionTitle(text: String, colors: ConsoleColors) {
    BasicText(
        text = text,
        style = ConsoleStyle.meta(colors).copy(fontSize = 9.sp, color = argb(colors.textTertiary)),
        modifier = Modifier.padding(start = 12.dp, top = 10.dp, bottom = 4.dp),
    )
}

@Composable
private fun PaletteChip(
    palette: ConsolePalette,
    selected: Boolean,
    colors: ConsoleColors,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(argb(colors.surface))
            .border(
                width = if (selected) 1.dp else 0.5.dp,
                color = if (selected) argb(colors.step) else argb(colors.stroke),
                shape = RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 预设的小色块：直接取自该预设的底色，比纯文字好认
        Box(
            modifier = Modifier
                .width(10.dp)
                .height(10.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(argb(palette.colors.panel))
                .border(0.5.dp, argb(palette.colors.stroke), RoundedCornerShape(3.dp)),
        )
        Spacer(Modifier.width(5.dp))
        BasicText(
            text = palette.label,
            style = ConsoleStyle.chip(colors).copy(
                color = if (selected) argb(colors.text) else argb(colors.textSecondary),
            ),
        )
    }
}
