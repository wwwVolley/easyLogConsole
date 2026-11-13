package io.github.wwwvolley.easylog

import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import io.github.wwwvolley.easylog.ui.EasyLogConsole

/**
 * 手动挂载（可选）。**用了自动初始化的话不需要它** —— 库已经替你挂在每个 Activity 上了。
 *
 * 只有两种情况才用手动挂：
 * 1. 清单里关掉了自动初始化（[EasyLogBootstrap.META_DATA] = false）；
 * 2. 只想让某一个 Activity 出现悬浮球（比如只有调试页）。
 *
 * ```kotlin
 * override fun onCreate(savedInstanceState: Bundle?) {
 *     super.onCreate(savedInstanceState)
 *     setContentView(R.layout.activity_main)
 *     installEasyLogOverlay()
 * }
 * ```
 *
 * 实现是把一个 `ComposeView` 挂到 `decorView` 上：它盖在所有内容之上，
 * 但**空白区域不消费触摸** —— Compose 根没有命中任何指针输入时会把事件还回去，
 * `ViewGroup` 继续分发给下面的页面，所以不影响业务点击。
 */
fun ComponentActivity.installEasyLogOverlay() {
    // 已经装过（自动初始化或宿主自己的 install）就只挂 View ——
    // 重复 install 会把 logcat 采集重启一遍，中间几十毫秒的日志会丢
    if (!EasyLog.isInstalled) EasyLog.install(this)
    attachEasyLogOverlay()
}

/** 用自定义配置安装并挂载。 */
fun ComponentActivity.installEasyLogOverlay(config: EasyLogConfig) {
    EasyLog.install(this, config)
    attachEasyLogOverlay()
}

/**
 * 只挂 View，不碰配置。自动初始化与上面的手动入口都走这里。
 *
 * `decorView` 上的 tag 用来去重：Activity 重建、重复调用都不会叠出第二个浮层。
 */
internal fun ComponentActivity.attachEasyLogOverlay() {
    if (!EasyLog.canShow) return

    val decor = window.decorView as? ViewGroup ?: return
    if (decor.findViewWithTag<View>(OVERLAY_TAG) != null) return

    val overlay = ComposeView(this).apply {
        tag = OVERLAY_TAG
        setContent { EasyLogConsole() }
    }
    decor.addView(
        overlay,
        ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        ),
    )
}

/** 挂载后可以从 decorView 上认出来的标记，避免 Activity 重建时重复添加。 */
private const val OVERLAY_TAG = "io.github.wwwvolley.easylog.overlay"
