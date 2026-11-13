package io.github.wwwvolley.easylog.sample

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import io.github.wwwvolley.easylog.BallVisibility
import io.github.wwwvolley.easylog.ConsolePalette
import io.github.wwwvolley.easylog.EasyLog
import io.github.wwwvolley.easylog.EasyLogConfig
import io.github.wwwvolley.easylog.EasyLogConsoleController
import io.github.wwwvolley.easylog.LogFileConfig
import io.github.wwwvolley.easylog.LogLevel
import io.github.wwwvolley.easylog.installEasyLogOverlay
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 示例工程：模拟一条完整的蓝牙配网链路，用来验证日志台。
 *
 * 用 `installEasyLogOverlay()` 挂载（而不是在根布局里放 `EasyLogConsole()`），
 * 因为这条路径更「外来」——它要往 decorView 上塞一个 ComposeView，
 * 顺便也验证了「浮层空白区不挡业务点击」。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        EasyLog.install(
            this,
            EasyLogConfig(
                // 默认就是 true：release 签名包里日志台一行都不画。
                // 这里显式写出来，是为了让示例本身就是「正确用法」的样板
                debugOnly = true,
                capacity = 2000,
                panelHeightFraction = 0.66f,
                showThreadName = false,
                // 悬浮球半透明，不挡页面；面板 80%、遮罩 30%，进面板还能「外观」页再调
                ballAlpha = 0.5f,
                panelAlpha = 0.8f,
                scrimAlpha = 0.25f,
                // 落盘：目录由宿主指定，库只 append 自己的文件。
                // 模拟器/真机上 adb pull 这个路径就能拿到日志
                file = LogFileConfig(
                    directory = File(getExternalFilesDir(null), "easylog"),
                    minLevel = LogLevel.DEBUG,
                ),
                // 自定义触发：这里演示「只在打过标记之后才出现」。
                // 宿主可以换成「登录后才出现」「只在配网页出现」之类的任意条件
                ballVisibility = BallVisibility { true },
            ),
        )
        setContent { SampleScreen() }
        // 挂载浮层。放在 setContent 之后，保证它在内容之上
        installEasyLogOverlay()
    }
}

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
            text = "点「走一遍配网」造日志，然后点右下角的 LOG 球打开日志台。\n" +
                "「点我计数」用来验证浮层不挡业务点击。",
            style = TextStyle(fontSize = 13.sp, lineHeight = 20.sp, color = Color(0xFF5C6470)),
        )

        Action("走一遍配网（模拟 BLE 全链路）") { scope.launch { simulateProvision() } }
        Action("造一条异常（红点 +1）") {
            EasyLog.fail("PROVISION", "云端", "bind-status 轮询超时 15s（模拟）")
        }
        Action("走老格式 [模块][分类]（兼容已有日志出口）") {
            EasyLog.w("PROVISION", "[下发][异常] GATT 133，重试 1/2（模拟）")
            EasyLog.i("PROVISION", "[读SN][数据] SN=SIM-20261113-0001（模拟）")
        }
        Action("瞬间刷 300 条（压一压重组）") {
            repeat(300) { index ->
                EasyLog.record(
                    level = LogLevel.DEBUG,
                    trace = "STRESS",
                    module = "压测",
                    category = "流程",
                    message = "第 $index 条 · 用来观察面板是否掉帧",
                )
            }
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
        Action("落盘路径") {
            Toast.makeText(context, EasyLog.fileLogPath ?: "未开启落盘", Toast.LENGTH_LONG).show()
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
 * 打点方式故意混用两种，验证两条入口都能进面板：
 * - `EasyLog.step/record/fail`：结构化，模块与分类直接给；
 * - `EasyLog.w/i`：老格式 `[模块][分类] 正文`，由库自己解析。
 */
private suspend fun simulateProvision() {
    val trace = "PROVISION"

    EasyLog.step(trace, "权限", "流程", "蓝牙 / 定位 / 通知 均已授权")
    delay(180)

    EasyLog.step(trace, "扫描", "流程", "开始扫描 6s，过滤 FFF0 广播")
    delay(400)
    EasyLog.record(LogLevel.DEBUG, trace, "扫描", "底层", "onScanResult rssi=-52 name=小伴019 mac=AC:12:XX:XX:9F:21")
    delay(260)
    EasyLog.record(LogLevel.INFO, trace, "扫描", "流程", "命中目标设备，停止扫描")

    EasyLog.step(trace, "连接", "流程", "connectGatt(autoConnect=false)")
    delay(900)
    EasyLog.record(LogLevel.INFO, trace, "连接", "状态", "onConnectionStateChange status=0 newState=CONNECTED")
    delay(200)
    EasyLog.record(LogLevel.DEBUG, trace, "连接", "底层", "discoverServices() 发起")
    delay(420)
    EasyLog.record(LogLevel.INFO, trace, "连接", "流程", "服务发现完成，FFF0/FFF1/FFF2 就绪 · MTU=247")

    EasyLog.step(trace, "读SN", "流程", "读 FFF2")
    delay(300)
    EasyLog.record(LogLevel.INFO, trace, "读SN", "数据", "SN=YB019-20261113001 固件=1.4.2")

    EasyLog.step(trace, "保活", "流程", "进入 30s 保活（设备无交互会主动断开）")
    delay(500)
    EasyLog.record(LogLevel.DEBUG, trace, "保活", "流程", "心跳 ok #1")

    EasyLog.step(trace, "下发", "流程", "写入 WiFi 凭据 ssid=Home-2.4G pwd=****(11)")
    delay(600)
    EasyLog.record(LogLevel.INFO, trace, "下发", "状态", "设备回 0x00 已接收，开始连路由器")
    delay(1200)
    EasyLog.record(LogLevel.INFO, trace, "下发", "状态", "设备已联网 ip=192.168.1.42 rssi=-47")

    EasyLog.step(trace, "云端", "网络", "POST /robot/bind")
    delay(700)
    EasyLog.record(LogLevel.INFO, trace, "云端", "网络", "HTTP 200 · code=0 · robotId=8801")
    EasyLog.step(trace, "云端", "网络", "轮询 GET /robot/bind-status ×3")
    delay(500)
    EasyLog.step(trace, "云端", "流程", "激活成功，可以退出配网页了")
}
