package io.github.wwwvolley.easylog.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 面板的**会话态**：同一个进程里关掉再打开，要跟「没关过」一样。
 *
 * 为什么这些不写进 `SharedPreferences`、而过滤条件和外观写了？因为性质不同：
 *
 * - 过滤（链路 / 级别 / 关键字）、外观（透明度 / 配色）、悬浮球位置、总开关 ——
 *   是**设置**，用户调一次就想一直这样，所以跨冷启动记在磁盘上（[io.github.wwwvolley.easylog.ConsolePrefs]）。
 * - 暂停、停在「外观」页、展开了哪几行 —— 是**当下正在干什么**，memory 里留着就够；
 *   跨冷启动还恢复的话，下次打开面板看见「已暂停」「外观页」，用户会以为日志不采了。
 * - 展开的行存的是日志 id，id 只在当前进程的缓冲区里有意义，落盘反而是脏数据。
 *
 * 放在文件级的 `object` 而不是 `remember`：面板每次开合都会离开组合，`remember` 全丢，
 * 这正是「点悬浮球重新打开，刚才的操作全没了」的根本原因。
 */
internal object PanelSessionState {

    /** 是否停在「外观」页。 */
    var settingsOpen by mutableStateOf(false)

    /** 是否暂停刷新。 */
    var paused by mutableStateOf(false)

    /** 已展开的日志 id（长文本整段看）。 */
    var expandedIds by mutableStateOf(emptySet<Long>())

    /** 清空日志时顺手清掉展开态：那些 id 再也不会回来了。 */
    fun forgetExpanded() {
        expandedIds = emptySet()
    }
}
