package io.github.wwwvolley.easylog.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.wwwvolley.easylog.ConsoleColors

/**
 * 当前生效的配色。
 *
 * 走 `CompositionLocal` 而不是层层传参：面板里十几个小 Composable 都要颜色，
 * 一路 `colors = colors` 传下去会让每个签名都多一个参数，加一个色彩字段就要改一圈签名。
 * 用 `staticCompositionLocalOf`（不做细粒度订阅）：配色整块换，换一次全量重组正合适。
 */
internal val LocalConsoleColors = staticCompositionLocalOf<ConsoleColors> { ConsoleColors() }

/** ARGB 的 Long → Compose 的 Color。 */
internal fun argb(value: Long): Color = Color(value.toInt())

/**
 * 面板自己的视觉常量。
 *
 * **不读宿主的 MaterialTheme**：这是一个调试工具，宿主的主题可能被业务改成各种品牌色、
 * 也可能压根没用 Material（纯 foundation、GameActivity 之类）。自带一套配色，
 * 换来的是「扔进任何工程都长一个样」，装完就能用；要改色就换 [ConsoleColors]。
 *
 * 颜色全部从参数传进来（`ConsoleStyle.title(colors)`），不做成 `object` 里的常量 ——
 * 常量没法跟着面板里的「外观」设置实时变。
 */
internal object ConsoleStyle {

    val Mono = FontFamily.Monospace

    val BallSize = 52.dp

    /** 行左侧状态色条宽度（STEP / ERROR 才有）。 */
    val RowMarkWidth = 2.dp

    fun title(colors: ConsoleColors): TextStyle = TextStyle(
        fontFamily = Mono,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Medium,
        color = argb(colors.text),
    )

    fun meta(colors: ConsoleColors): TextStyle = TextStyle(
        fontFamily = Mono,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        color = argb(colors.textTertiary),
    )

    fun message(colors: ConsoleColors): TextStyle = TextStyle(
        fontFamily = Mono,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        color = argb(colors.text),
    )

    fun chip(colors: ConsoleColors): TextStyle = TextStyle(
        fontFamily = Mono,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        color = argb(colors.textSecondary),
    )
}
