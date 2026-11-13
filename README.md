# easyLogConsole

App 内悬浮日志台：一个悬浮球 + 一块半透明日志面板，**真机上直接看关键链路日志，不用连电脑、不用开 Android Studio**。

为「蓝牙扫描 → 连接 → 读 SN → 上报 → 下发 WiFi → 云端激活」这类跨层链路设计：一条链路走完，手机上点开就能按环节筛、按时间看间隔。

---

## 特性

| | |
|---|---|
| 接入成本 | 3 行；业务打点代码零改动（挂在已有日志出口后面即可） |
| 悬浮球 | 可拖动、松手吸附左右边、位置记忆；有新的 WARN/ERROR 时带未读红点 |
| 面板 | 半透明，能一边看日志一边操作界面；键盘弹出自动上移 |
| 过滤 | 链路（trace）/ 级别 / 关键字 三个维度，边打边看 |
| 行信息 | 时间 + **与上一行的间隔** + 级别 + 模块·分类 + 正文；点开看全文，长按复制单条 |
| 视觉 | STEP（关键节点）与 ERROR 左侧有 2dp 色条，快速滚动也能定位；自带深色配色，不读宿主主题 |
| 性能 | 写端只入环形缓冲，UI 每 120ms 拉一次增量：瞬间刷 300 条也只触发一次重组 |
| 体积 | 零三方依赖（只有 Compose + core-ktx），AAR 约 60KB；核心层无 Compose 依赖，可纯 JVM 单测 |

---

## 接入

### 1. 依赖

库还没发到公共仓库，用下面任一方式：

**A. 复合构建（同一台机器上有源码，改一行两边生效，推荐开发期用）**

```kotlin
// settings.gradle.kts
includeBuild("../easyLogConsole") {
    dependencySubstitution {
        substitute(module("io.github.wwwvolley:easylog-console"))
            .using(project(":library"))
    }
}
```

```kotlin
// app/build.gradle.kts
dependencies { implementation("io.github.wwwvolley:easylog-console:1.0.0") }
```

**B. 发到本地 Maven**

```bash
./gradlew :library:publishToMavenLocal     # 之后正常 implementation 即可
```

**C. 发到 GitHub Packages / 内网 Nexus**：改 `library/build.gradle.kts` 里 `publishing.repositories` 一段。

### 2. 初始化（Application）

```kotlin
EasyLog.install(
    this,
    EasyLogConfig(
        enabled = BuildConfig.DEBUG,   // 生产包默认关；现场要开就改成 true
        capacity = 3000,
    ),
)
```

### 3. 挂载界面（二选一，效果一样，别同时用）

**Compose 工程**（推荐，少一层 View）：

```kotlin
setContent {
    Box(Modifier.fillMaxSize()) {
        AppNavHost(...)
        EasyLogConsole()        // 放最外层，页面切换时不重建、滚动位置不丢
    }
}
```

**View / 老工程**：

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)
    installEasyLogOverlay()     // 往 decorView 挂一个 ComposeView
}
```

> 浮层盖在内容之上，但**空白区域不消费触摸**，业务点击照常穿透；只有球和面板本身吃事件。

### 4. 接已有的日志出口（关键一步）

库里**不拦截 logcat**，而是提供一个入口，谁接谁负责。宿主要做的只是把已有日志转进来：

```kotlin
// 例：项目里已有的 AppLog 薄封装，改它的 sink
object AppLog {
    var sink: ((LogLevel, String, String, Throwable?) -> Unit)? = null

    fun w(tag: String, msg: String, t: Throwable? = null) {
        Log.w("APP", "[$tag] $msg", t)
        sink?.invoke(io.github.wwwvolley.easylog.LogLevel.WARN, tag, msg, t)
    }
}

// Application 里接上
AppLog.sink = { level, tag, msg, t -> EasyLog.accept(level, tag, msg, t) }
```

`accept` 会自动把正文开头的 `[模块][分类]` 拆成两个维度（最多两段、每段 ≤16 字），所以已有的
`"[连接][流程] 开始连接"` 这种格式不用改一个字，面板就能按「连接」筛。

新代码建议直接用结构化 API：

```kotlin
EasyLog.step("PROVISION", "扫描", "流程", "开始扫描 6s")      // 关键节点，青色 + 色条
EasyLog.record(LogLevel.INFO, "PROVISION", "读SN", "数据", "SN=YB019-..." )
EasyLog.fail("PROVISION", "云端", "bind-status 轮询超时 15s")  // 红色 + 红点 +1
```

---

## API

### 打点

| 方法 | 说明 |
|---|---|
| `accept(level, tag, message, throwable?)` | 通用入口，正文里的 `[模块][分类]` 交给库解析 |
| `record(level, trace, module, category, message, throwable?)` | 结构化入口，不做解析（正文本体带方括号时用这个） |
| `step(trace, module, category, message)` | 关键节点，`LogLevel.STEP` |
| `fail(trace, module, message, throwable?)` | 失败节点，`LogLevel.ERROR` |
| `v/d/i/w/e(tag, message, throwable?)` | 单链路名简化版 |
| `addSink { entry -> ... }` | 旁路消费：落盘 / 上报 / 桥接其他日志系统。返回的 `AutoCloseable` 一 `close()` 就解绑 |

### 控制与读取

| 成员 | 说明 |
|---|---|
| `isEnabled` / `setEnabled(Boolean)` | 总开关，持久化，下次冷启动保持 |
| `EasyLogConsoleController.show() / hide() / toggle()` | 主动开合面板，给「版本号连点 7 次」「摇一摇」这类隐藏入口用（配合 `Trigger.Hidden`） |
| `entries()` / `entriesSince(id)` / `latestId()` / `errorCount()` / `clear()` | 缓冲读取，宿主自己做二次消费时用 |

### 配置（`EasyLogConfig`）

| 字段 | 默认 | 说明 |
|---|---|---|
| `enabled` | `true` | 是否启用；`install` 后会被本地保存的用户选择覆盖（首次装包按这个值） |
| `capacity` | `3000` | 缓冲条数（200~20000） |
| `maxMessageChars` | `4000` | 单条正文/堆栈上限，防整段 base64 挤爆缓冲 |
| `panelAlpha` | `0.86` | 面板不透明度（0.5~1.0） |
| `panelHeightFraction` | `0.62` | 面板高度占屏比（0.3~0.9） |
| `showThreadName` | `false` | 每行是否显示线程名（BLE 排查九成看顺序，默认关） |
| `trigger` | `FloatingBall` | `FloatingBall` 常驻悬浮球 / `Hidden` 只由宿主 `show()` 打开 |

---

## 复用其他项目

- **同工程多模块**：直接 `implementation`，任意模块都能打点。
- **另一个仓库**：`includeBuild`（方案 A），或复制 `library/` 目录 + 一行依赖。
- **多个项目长期用**：`publishToMavenLocal` / 发内网仓库 / GitHub Packages，坐标
  `io.github.wwwvolley:easylog-console:1.0.0`，改 `group` 与 `version` 只动
  `library/build.gradle.kts` 顶部两个 `providers.gradleProperty` 的默认值（也可用 `-Peasylog.version=` 覆盖）。

库本身不依赖任何宿主代码，也没有 `ContentProvider` / `Initializer` 自动注册 —— **宿主清单一行都不用改**。

---

## 已知边界（都是故意这么设计的）

- **不拦截 logcat**：宿主已经有自己的日志出口，库只提供 `accept` 入口，不做反射、不抢占 `Log`。
- **不落文件**：导出走剪贴板 + `ACTION_SEND` 文本。落文件要求宿主注册 `FileProvider` 和 authority，
  两个库各要一个就会打架，得不偿失。
- **不持久化历史日志**：只在内存里留最近 N 条，杀进程即清空 —— 需要留存请用 `addSink` 自己写盘。
- **面板不跟随宿主深色/浅色主题**：调试工具自带一套深色配色，换来「装进任何工程长相一致」。

---

## 示例工程

`./gradlew :sample:installDebug`，界面里有：

- 走一遍配网（模拟 BLE 全链路，含 STEP / INFO / 异常）
- 造一条异常（验证红点）
- 走老格式 `[模块][分类]`（验证解析兼容）
- 瞬间刷 300 条（验证重组开销）
- 点我计数（验证浮层不挡业务点击）

## 测试

```bash
./gradlew :library:testDebugUnitTest    # 解析器 + 环形缓冲，11 个用例
```

## 许可证

MIT，见 [LICENSE](LICENSE)。要换成 Apache-2.0 只改 `LICENSE` 与 `library/build.gradle.kts` 里 POM 的
`licenses` 一段。
