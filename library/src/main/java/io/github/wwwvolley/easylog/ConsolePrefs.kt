package io.github.wwwvolley.easylog

import android.content.Context
import android.content.SharedPreferences

/**
 * 面板自己要记住的几件小事：开关、悬浮球位置、外观。
 *
 * 用 `SharedPreferences` 而不是 DataStore：只有几个键，DataStore 要额外引依赖、
 * 还得起协程读写，为一个悬浮球位置不值得。宿主不需要在自己的配置里预留任何键名 ——
 * 这里的文件名和键名都带 `easylog` 前缀，不会和宿主的偏好撞车。
 */
internal class ConsolePrefs(context: Context?) {

    private val appContext: Context? = context?.applicationContext

    private val prefs: SharedPreferences? = appContext
        ?.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** 当前进程的包名，落盘文件名模板 `{package}` 用。 */
    val packageName: String? = appContext?.packageName

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

    var panelAlpha: Float?
        get() = prefs?.takeIf { it.contains(KEY_PANEL_ALPHA) }?.getFloat(KEY_PANEL_ALPHA, 0.82f)
        set(value) = putFloat(KEY_PANEL_ALPHA, value)

    var ballAlpha: Float?
        get() = prefs?.takeIf { it.contains(KEY_BALL_ALPHA) }?.getFloat(KEY_BALL_ALPHA, 0.55f)
        set(value) = putFloat(KEY_BALL_ALPHA, value)

    var scrimAlpha: Float?
        get() = prefs?.takeIf { it.contains(KEY_SCRIM_ALPHA) }?.getFloat(KEY_SCRIM_ALPHA, 0.3f)
        set(value) = putFloat(KEY_SCRIM_ALPHA, value)

    /** 配色预设名（[ConsolePalette.name]）。 */
    var palette: String?
        get() = prefs?.getString(KEY_PALETTE, null)
        set(value) {
            if (value == null) prefs?.edit()?.remove(KEY_PALETTE)?.apply()
            else prefs?.edit()?.putString(KEY_PALETTE, value)?.apply()
        }

    private fun putFloat(key: String, value: Float?) {
        val editor = prefs?.edit() ?: return
        if (value == null) editor.remove(key).apply() else editor.putFloat(key, value).apply()
    }

    private companion object {
        const val FILE = "easylog_console"
        const val KEY_ENABLED = "enabled"
        const val KEY_BALL_X = "ball_x"
        const val KEY_BALL_Y = "ball_y"
        const val KEY_PANEL_ALPHA = "panel_alpha"
        const val KEY_BALL_ALPHA = "ball_alpha"
        const val KEY_SCRIM_ALPHA = "scrim_alpha"
        const val KEY_PALETTE = "palette"
    }
}
