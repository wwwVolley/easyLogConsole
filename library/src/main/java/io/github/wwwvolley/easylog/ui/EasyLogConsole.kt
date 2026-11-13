package io.github.wwwvolley.easylog.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.wwwvolley.easylog.EasyLog
import io.github.wwwvolley.easylog.EasyLogConfig
import io.github.wwwvolley.easylog.EasyLogConsoleController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 日志台入口：一个可拖动的悬浮球，点开就是半透明日志面板。
 *
 * ## 放哪里
 * 放在 Compose 根布局的最外层（`setContent` 里、所有页面之上）：
 * ```kotlin
 * Box(Modifier.fillMaxSize()) {
 *     AppNavHost(...)
 *     EasyLogConsole()
 * }
 * ```
 * 单 Activity 的工程挂在根上，页面来回切它都不重建、日志滚动位置也不会丢。
 *
 * ## 为什么自己不消费触摸
 * 收起态只有一个球，`Box` 本身不挂任何指针输入，空白区域的触摸会直接落到下面的页面上；
 * 展开态才铺一层 scrim 接管点击（点面板外收起）。用 `WindowManager` 加悬浮窗那套
 * 反而要额外权限、还要自己处理 Insets 和主题，单 Activity 工程没必要。
 */
@Composable
fun EasyLogConsole(modifier: Modifier = Modifier) {
    val config = EasyLog.currentConfig

    var enabled by remember { mutableStateOf(EasyLog.isEnabled) }
    DisposableEffect(Unit) {
        val handle = EasyLog.addEnabledListener { enabled = it }
        onDispose { handle.close() }
    }

    var open by remember { mutableStateOf(EasyLogConsoleController.isOpen) }
    DisposableEffect(Unit) {
        val handle = EasyLogConsoleController.addListener { open = it }
        onDispose { handle.close() }
    }

    if (!enabled) return

    Box(modifier = modifier.fillMaxSize()) {
        when {
            open -> LogPanel(
                config = config,
                onClose = {
                    EasyLogConsoleController.hide()
                    seenErrorCount = EasyLog.errorCount()
                },
            )

            config.trigger == EasyLogConfig.Trigger.FloatingBall -> FloatingBall(
                onOpen = {
                    EasyLogConsoleController.show()
                    seenErrorCount = EasyLog.errorCount()
                },
            )

            else -> Unit
        }
    }
}

/**
 * 已经「看过」的异常数。
 *
 * 放在文件级而不是 `remember`：面板一开一合，悬浮球 Composable 会被销毁重建，
 * 状态留在 remember 里的话红点会永远清零，等于没有未读提醒。
 */
private var seenErrorCount by mutableStateOf(EasyLog.errorCount())

/**
 * 悬浮球。
 *
 * - 拖动松手后吸附到最近的左右边，位置按屏幕比例持久化，重启后还在原地；
 * - 有新的 WARN/ERROR 时右上角带红色计数，点开一次即清零；
 * - 拖动与点击用两个 `pointerInput`：点击检测等触摸超过 slop 才让位给拖动，
 *   手指轻点不会因为一个像素的抖动被判成「拖了一下」。
 */
@Composable
private fun FloatingBall(onOpen: () -> Unit) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    var totalErrors by remember { mutableLongStateOf(EasyLog.errorCount()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            totalErrors = EasyLog.errorCount()
        }
    }
    val unread = (totalErrors - seenErrorCount).coerceAtLeast(0L)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val ballPx = with(density) { ConsoleStyle.BallSize.toPx() }
        val maxX = (constraints.maxWidth - ballPx).coerceAtLeast(0f)
        val maxY = (constraints.maxHeight - ballPx).coerceAtLeast(0f)

        val initial = remember(maxX, maxY) {
            val saved = EasyLog.ballPreferences?.ballPosition
            val fx = (saved?.first ?: 0.94f).coerceIn(0f, 1f)
            val fy = (saved?.second ?: 0.62f).coerceIn(0f, 1f)
            Offset(fx * maxX, fy * maxY)
        }
        val position = remember(maxX, maxY) { Animatable(initial, Offset.VectorConverter) }

        Box(
            modifier = Modifier
                .offset { IntOffset(position.value.x.roundToInt(), position.value.y.roundToInt()) }
                .size(ConsoleStyle.BallSize)
                .pointerInput(maxX, maxY) {
                    detectDragGestures(
                        onDragEnd = {
                            val targetX = if (position.value.x < maxX / 2f) 0f else maxX
                            scope.launch {
                                position.animateTo(Offset(targetX, position.value.y), tween(160))
                            }
                            if (maxX > 0f && maxY > 0f) {
                                // 存的是**吸附后**的位置，不是松手那一刻的：存松手位置的话，
                                // 下次启动球会停在屏幕中间，而不是贴边
                                EasyLog.ballPreferences?.ballPosition =
                                    (targetX / maxX) to (position.value.y / maxY)
                            }
                        },
                    ) { change, drag ->
                        change.consume()
                        val nx = (position.value.x + drag.x).coerceIn(0f, maxX)
                        val ny = (position.value.y + drag.y).coerceIn(0f, maxY)
                        scope.launch { position.snapTo(Offset(nx, ny)) }
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onOpen() })
                },
        ) {
            // 球体单独一层、单独裁剪。红点要探到圆外，挂在这一层上会被 CircleShape 削掉一角
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(CircleShape)
                    .background(ConsoleStyle.BallBackground)
                    .border(0.5.dp, ConsoleStyle.Stroke, CircleShape),
            ) {
                BasicText(
                    text = "LOG",
                    style = ConsoleStyle.TitleStyle.copy(fontSize = 12.sp),
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            if (unread > 0L) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-4).dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(ConsoleStyle.Badge)
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(
                        text = if (unread > 99) "99+" else unread.toString(),
                        style = ConsoleStyle.MetaStyle.copy(fontSize = 8.sp, color = Color.White),
                    )
                }
            }
        }
    }
}
