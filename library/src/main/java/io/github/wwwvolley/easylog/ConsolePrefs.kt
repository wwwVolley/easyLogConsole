package io.github.wwwvolley.easylog

import android.content.Context
import android.content.SharedPreferences

/**
 * 面板自己要记住的几件小事：开关、悬浮球位置。
 *
 * 用 `SharedPreferences` 而不是 DataStore：只有两三个键，DataStore 要额外引依赖、
 * 还得起协程读写，为一个悬浮球位置不值得。宿主不需要在自己的配置里预留任何键名。
 */
internal class ConsolePrefs(context: Context?) {

    private val prefs: SharedPreferences? = context
        ?.applicationContext
        ?.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var enabled: Boolean?
        get() = prefs?.takeIf { it.contains(KEY_ENABLED) }?.getBoolean(KEY_ENABLED, true)
        set(value) {
            if (value == null) prefs?.edit()?.remove(KEY_ENABLED)?.apply()
            else prefs?.edit()?.putBoolean(KEY_ENABLED, value)?.apply()
        }

    /** 悬浮球位置，用屏幕比例存（换分辨率、转屏后仍然落在相对位置）。 */
    var ballPosition: Pair<Float, Float>?
        get() {
            val p = prefs ?: return null
            if (!p.contains(KEY_BALL_X) || !p.contains(KEY_BALL_Y)) return null
            return p.getFloat(KEY_BALL_X, 0.9f) to p.getFloat(KEY_BALL_Y, 0.55f)
        }
        set(value) {
            val editor = prefs?.edit() ?: return
            if (value == null) {
                editor.remove(KEY_BALL_X).remove(KEY_BALL_Y).apply()
            } else {
                editor.putFloat(KEY_BALL_X, value.first).putFloat(KEY_BALL_Y, value.second).apply()
            }
        }

    private companion object {
        const val FILE = "easylog_console"
        const val KEY_ENABLED = "enabled"
        const val KEY_BALL_X = "ball_x"
        const val KEY_BALL_Y = "ball_y"
    }
}
