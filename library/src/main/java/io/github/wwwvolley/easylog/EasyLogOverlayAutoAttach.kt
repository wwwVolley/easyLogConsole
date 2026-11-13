package io.github.wwwvolley.easylog

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity

/**
 * 把浮层自动挂到每个 Activity 上。
 *
 * 只在 [EasyLogBootstrap] 里注册一次；Activity 重建（旋转、深色模式切换、
 * 多语言切换）时会重新 `resumed`，靠 `decorView` 上的 tag 去重，不会叠出两个球。
 *
 * 为什么在 `onActivityResumed` 而不是 `onActivityCreated`：`decorView` 在
 * `setContentView` 之前是空的，挂上去也会被后续内容盖住；`resumed` 时
 * 内容层已经铺好，浮层加进去天然在**最上层**。
 */
internal object EasyLogOverlayAutoAttach {

    fun attach(context: Context) {
        val app = context.applicationContext as? Application ?: return
        app.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityResumed(activity: Activity) {
                    val componentActivity = activity as? ComponentActivity ?: return
                    if (!EasyLog.canShow) return
                    if (!EasyLog.currentConfig.autoAttachOverlay) return
                    componentActivity.attachEasyLogOverlay()
                }

                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
                override fun onActivityStarted(activity: Activity) = Unit
                override fun onActivityPaused(activity: Activity) = Unit
                override fun onActivityStopped(activity: Activity) = Unit
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
                override fun onActivityDestroyed(activity: Activity) = Unit
            },
        )
    }
}
