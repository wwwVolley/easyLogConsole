package io.github.wwwvolley.easylog.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.wwwvolley.easylog.LogLevel

/**
 * 面板自己的视觉常量。
 *
 * **不读宿主的 MaterialTheme**：这是一个调试工具，宿主的主题可能被业务改成各种品牌色、
 * 也可能压根没用 Material（纯 foundation、GameActivity 之类）。自带一套深色配色，
 * 换来的是「扔进任何工程都长一个样」，装完就能用。
 */
internal object ConsoleStyle {

    /** 面板底色（配合 `config.panelAlpha` 变半透明）。 */
    val Panel = Color(0xFF14161A)

    /** 面板内部的次级底色：搜索框、未选中的 chip。 */
    val Surface = Color(0x14FFFFFF)

    val Stroke = Color(0x26FFFFFF)
    val Divider = Color(0x14FFFFFF)

    val TextPrimary = Color(0xFFE9EDF2)
    val TextSecondary = Color(0xFF98A2B0)
    val TextTertiary = Color(0xFF6B7480)

    val Step = Color(0xFF4ED8A0)
    val Info = Color(0xFF6EB4F7)
    val Warn = Color(0xFFF5B942)
    val Error = Color(0xFFFF6B6B)
    val Debug = Color(0xFF9AA0A6)
    val Verbose = Color(0xFF6B7480)

    /** 正文配色：异常与关键节点各带一点底色倾向，扫读时不用看级别字母。 */
    val ErrorText = Color(0xFFFFD9D9)
    val StepText = Color(0xFFD8FFF0)

    val Scrim = Color(0x59000000)
    val BallBackground = Color(0xF2161A20)
    val Badge = Color(0xFFFF5D5D)

    val Mono = FontFamily.Monospace

    val TitleStyle = TextStyle(
        fontFamily = Mono,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Medium,
        color = TextPrimary,
    )

    val MetaStyle = TextStyle(
        fontFamily = Mono,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        color = TextTertiary,
    )

    val MessageStyle = TextStyle(
        fontFamily = Mono,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        color = TextPrimary,
    )

    val ChipStyle = TextStyle(
        fontFamily = Mono,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        color = TextSecondary,
    )

    val BallSize = 52.dp

    /** 行左侧状态色条宽度（STEP / ERROR 才有）。 */
    val RowMarkWidth = 2.dp

    fun colorOf(level: LogLevel): Color = when (level) {
        LogLevel.VERBOSE -> Verbose
        LogLevel.DEBUG -> Debug
        LogLevel.INFO -> Info
        LogLevel.STEP -> Step
        LogLevel.WARN -> Warn
        LogLevel.ERROR -> Error
    }
}
