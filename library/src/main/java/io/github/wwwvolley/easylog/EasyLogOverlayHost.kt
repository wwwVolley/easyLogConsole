package io.github.wwwvolley.easylog

import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import io.github.wwwvolley.easylog.ui.EasyLogConsole

/**
 * 一行挂载：给**不用 Compose 写根布局**的工程（或者懒得改 `setContent` 的时候）用。
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
 *
 * 用 Compose 写主界面的工程建议直接在根布局里放 `EasyLogConsole()`（更省一层 View），
 * 两者效果一致，不要同时用。
 */
fun ComponentActivity.installEasyLogOverlay(config: EasyLogConfig = EasyLogConfig()) {
    EasyLog.install(this, config)

    val decor = window.decorView as? ViewGroup ?: return
    if (decor.findViewWithTag<View>(OVERLAY_TAG) != null) return

    val overlay = ComposeView(this).apply {
        tag = OVERLAY_TAG
        // 关掉 AppCompat 的自动换行/主题依赖：这里只画自己的一套配色
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
