package io.github.wwwvolley.easylog.sample

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.wwwvolley.easylog.ConsolePalette
import io.github.wwwvolley.easylog.EasyLog
import io.github.wwwvolley.easylog.EasyLogConsoleController
import io.github.wwwvolley.easylog.LogFileConfig
import io.github.wwwvolley.easylog.LogLevel
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 示例工程 —— 演示**零代码接入**。
 *
 * 注意这个类里没有任何 `EasyLog.install` / `installEasyLogOverlay`：
 * 库自带一个 ContentProvider，进程启动时就把库装好、把采集开起来、把浮层挂上去了。
 * 这里只写业务代码，日志用 Android 原生的 `Log.x` 打 —— 这正是想证明的事：
 *
 * > 加了依赖之后，老的 `Log.d/i/w/e` 一行都不用改，手机上就能看到。
 *
 * 正文以 `★` 开头的那行会显示成「关键节点」（绿色色条），
 * 宿主用 `Log.i(TAG, "★ 开始连接")` 就能拿到，不必专门调库的 API。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "★ [示例][启动] MainActivity 创建，日志台已自动就绪")
        setContent { SampleScreen() }
    }
}

private const val TAG = "YBDL"

@Composable
private fun SampleScreen() {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var tapCount by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF6F7F9))
            .systemBarsPadding()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BasicText(
            text = "easyLog 示例",
            style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Medium, color = Color(0xFF1A1C1F)),
        )
        BasicText(
            text = "没有一行初始化代码 —— 加依赖就够了。\n" +
                "点「走一遍配网」用原生 Log 打日志，然后点 LOG 球打开日志台。\n" +
                "「点我计数」用来验证浮层不挡业务点击。",
            style = TextStyle(fontSize = 13.sp, lineHeight = 20.sp, color = Color(0xFF5C6470)),
        )

        Action("走一遍配网（原生 Log 打点）") { scope.launch { simulateProvision() } }
        Action("造一条异常（红点 +1）") {
            Log.e(TAG, "[云端][异常] bind-status 轮询超时 15s（模拟）")
        }
        Action("关键节点 / 普通信息各一条") {
            Log.i(TAG, "★ [下发][流程] 写入 WiFi 凭据 ssid=Home-2.4G")
            Log.d(TAG, "[下发][数据] 设备回 0x00")
        }
        Action("瞬间刷 300 条（压一压重组）") {
            repeat(300) { index -> Log.d(TAG, "[压测][流程] 第 $index 条 · 观察面板是否掉帧") }
        }
        Action("点我计数：$tapCount") { tapCount++ }
        Action("切换配色（暗色 → 终端绿 → 亮色）") {
            val order = ConsolePalette.entries
            val index = order.indexOf(EasyLog.currentPalette ?: order.last())
            EasyLog.updateAppearance(palette = order[(index + 1) % order.size])
        }
        Action("临时藏起悬浮球 3 秒（录屏/截图场景）") {
            scope.launch {
                EasyLogConsoleController.setBallVisible(false)
                delay(3_000)
                EasyLogConsoleController.setBallVisible(null)
            }
        }
        Action("开始落盘到应用目录") {
            // 落盘默认关闭（库一个字节都不写）。要存就自己给一个目录，一行搞定
            EasyLog.startFileLogging(
                LogFileConfig(
                    directory = File(context.getExternalFilesDir(null), "easylog"),
                    minLevel = LogLevel.DEBUG,
                ),
            )
            Log.i(TAG, "★ [落盘][流程] 已开启，路径=${EasyLog.fileLogPath}")
            Toast.makeText(context, EasyLog.fileLogPath ?: "开启失败", Toast.LENGTH_LONG).show()
        }
    }
}

@Composable
private fun Action(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            text = text,
            style = TextStyle(fontSize = 14.sp, color = Color(0xFF1A1C1F)),
        )
    }
}

/**
 * 模拟配网：扫描 → 连接 → GATT → 读 SN → 保活 → 下发 WiFi → 云端激活。
 *
 * **全程只用 `android.util.Log`**：这些行会照常出现在 Android Studio 的 Logcat 里，
 * 同时被本库旁路采集到悬浮日志台 —— 这就是「不影响 Studio 控制台」的实证。
 */
private suspend fun simulateProvision() {
    Log.i(TAG, "★ [权限][流程] 蓝牙 / 定位 / 通知 均已授权")
    delay(180)

    Log.i(TAG, "★ [扫描][流程] 开始扫描 6s，过滤 FFF0 广播")
    delay(400)
    Log.d(TAG, "[扫描][底层] onScanResult rssi=-52 name=小伴019 mac=AC:12:XX:XX:9F:21")
    delay(260)
    Log.i(TAG, "[扫描][流程] 命中目标设备，停止扫描")

    Log.i(TAG, "★ [连接][流程] connectGatt(autoConnect=false)")
    delay(900)
    Log.i(TAG, "[连接][状态] onConnectionStateChange status=0 newState=CONNECTED")
    delay(200)
    Log.d(TAG, "[连接][底层] discoverServices() 发起")
    delay(420)
    Log.i(TAG, "[连接][流程] 服务发现完成，FFF0/FFF1/FFF2 就绪 · MTU=247")

    Log.i(TAG, "★ [读SN][流程] 读 FFF2")
    delay(300)
    Log.i(TAG, "[读SN][数据] SN=YB019-20261113001 固件=1.4.2")

    Log.i(TAG, "★ [保活][流程] 进入 30s 保活（设备无交互会主动断开）")
    delay(500)
    Log.d(TAG, "[保活][流程] 心跳 ok #1")

    Log.i(TAG, "★ [下发][流程] 写入 WiFi 凭据 ssid=Home-2.4G pwd=****(11)")
    delay(600)
    Log.i(TAG, "[下发][状态] 设备回 0x00 已接收，开始连路由器")
    delay(1200)
    Log.i(TAG, "[下发][状态] 设备已联网 ip=192.168.1.42 rssi=-47")

    Log.i(TAG, "★ [云端][网络] POST /robot/bind")
    delay(700)
    Log.i(TAG, "[云端][网络] HTTP 200 · code=0 · robotId=8801")
    Log.i(TAG, "★ [云端][网络] 轮询 GET /robot/bind-status ×3")
    delay(500)
    Log.i(TAG, "★ [云端][流程] 激活成功，可以退出配网页了")
}
