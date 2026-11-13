# easyLogConsole

App 内悬浮日志台：一个半透明悬浮球 + 一块半透明日志面板，**真机上直接看关键链路日志，不用连电脑、不用开 Android Studio**。

为「蓝牙扫描 → 连接 → 读 SN → 上报 → 下发 WiFi → 云端激活」这类跨层链路设计：一条链路走完，手机上点开就能按环节筛、按时间看间隔。

---

## 特性

| | |
|---|---|
| 只在 debug 出现 | 默认 `debugOnly`，用 `FLAG_DEBUGGABLE` 判定；release 签名包里浮层不渲染、`accept` 直接 return |
| 接入成本 | 3 行；业务打点代码零改动（挂在已有日志出口后面即可） |
| 不影响控制台 | **不接管 Android Studio Logcat**：宿主的 `Log.x` 一行不用改，库只是旁路复制一份 |
| 悬浮球 | 半透明（默认 50%，可调）、可拖动、松手吸附左右边、位置记忆；有新的 WARN/ERROR 时带未读红点 |
| 显示条件 | `BallVisibility` 自定义「什么时候出现」，也可运行时 `setBallVisible()` 临时隐藏 |
| 面板 | 半透明（默认 82%，可调）、遮罩浓度可调；能一边看日志一边操作界面；键盘弹出自动上移 |
| 外观 | 面板 / 球 / 遮罩三个滑杆 + 三套配色（暗色 / 终端绿 / 亮色），面板内实时调、持久化 |
| 落盘 | 可选。目录、文件名模板、滚动大小、保留数量全由开发者指定；异步批量写，不阻塞打点线程 |
| 过滤 | 链路（trace）/ 级别 / 关键字 三个维度，边打边看 |
| 行信息 | 时间 + **与上一行的间隔** + 级别 + 模块·分类 + 正文；点开看全文，长按复制单条 |
| 性能 | 写端只入环形缓冲，UI 每 120ms 拉一次增量：瞬间刷 300 条也只触发一次重组 |
| 体积 | 零三方依赖（只有 Compose + core-ktx）；核心层无 Compose 依赖，可纯 JVM 单测 |

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
        debugOnly = true,            // 默认值；release 包不出现，别改成 false
        capacity = 3000,
        ballAlpha = 0.5f,            // 悬浮球半透明，不挡页面
        panelAlpha = 0.82f,          // 面板半透明，能看见底下的界面
        // 需要留存日志时才配；不配 = 一个文件都不写
        file = LogFileConfig(directory = File(filesDir, "easylog")),
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
// 例：项目里已有的 AppLog 薄封装
object AppLog {
    var sink: ((LogLevel, String, String, Throwable?) -> Unit)? = null

    fun w(tag: String, msg: String, t: Throwable? = null) {
        Log.w("APP", "[$tag] $msg", t)                 // ← 控制台照旧输出，一行没改
        sink?.invoke(io.github.wwwvolley.easylog.LogLevel.WARN, tag, msg, t)   // ← 旁路复制
    }
}

// Application 里接上
AppLog.sink = { level, tag, msg, t -> EasyLog.accept(level, tag, msg, t) }
```

`accept` 会自动把正文开头的 `[模块][分类]` 拆成两个维度（最多两段、每段 ≤16 字），所以已有的
`"[连接][流程] 开始连接"` 这种格式不用改一个字，面板就能按「连接」筛。

新代码建议直接用结构化 API：

```kotlin
EasyLog.step("PROVISION", "扫描", "流程", "开始扫描 6s")      // 关键节点，绿色 + 色条
EasyLog.record(LogLevel.INFO, "PROVISION", "读SN", "数据", "SN=YB019-..." )
EasyLog.fail("PROVISION", "云端", "bind-status 轮询超时 15s")  // 红色 + 红点 +1
```

---

## 三个「不要影响原页面 / 原有能力」的设计

这是本库最在意的部分，单独说明。

### 一、不影响 Android Studio 控制台

库**不抢占 `Log`**、不反射、不重定向 `System.out`。宿主的 `Log.d/i/w/e` 原样输出，
`adb logcat -s YBDL | grep PROVISION` 这套过滤方式完全不变。
`EasyLog.accept()` 是**旁路**：写进环形缓冲 + 交给可选的文件 sink，走的是另一条路。

### 二、不影响宿主自己的落盘

- 默认 `EasyLogConfig.file = null`，库**一个字节都不写**；
- 配了之后也只 `append` 自己按 `fileNamePattern` 生成的那个文件及其滚动副本（`.1`、`.2`…），
  **不删、不截断、不锁**目录里别人的文件；
- 目录由宿主指定（`filesDir` / `getExternalFilesDir` / 自己的日志目录都行），
  两边各写各的，最多是同一个目录里多一个名字；
- 写盘在库自己的单线程上，每 `flushIntervalMs` 批量刷一次；队列满（2 万行）时**丢弃新日志**，
  绝不让业务线程因为磁盘慢而卡住。

### 三、不影响原页面观看

- 三处透明度都可调：悬浮球 15%~100%、面板 20%~100%、遮罩 0%~80%；
- 面板只占屏幕下半部分（`panelHeightFraction`），上半部分页面照常可见可点；
- 悬浮球半透明靠**颜色 alpha** 实现，不挂 `Modifier.alpha` ——
  节点级 alpha 会让 Compose 走离屏合成并按节点边界裁剪，探出球体的红点会被削掉一角；
- 调完的值写进 `SharedPreferences`（`easylog_console`），下次冷启动沿用，
  「外观 → 恢复默认」清掉这些键回到代码里的默认值。

---

## 显示时机（`BallVisibility`）

```kotlin
EasyLogConfig(
    ballVisibility = BallVisibility { userRepo.isLoggedIn },   // 每 500ms 问一次，实现要够轻
    // 也可用现成的：BallVisibility.Always / BallVisibility.AfterFirstOpen
)
```

临时藏（录屏、截图、演示）：

```kotlin
EasyLogConsoleController.setBallVisible(false)   // 隐藏
EasyLogConsoleController.setBallVisible(null)    // 交还给 ballVisibility 条件
```

> 优先级：`setBallVisible()` 覆盖 > `ballVisibility` 条件。
> `Trigger.Hidden` 则完全不显示球，只由 `EasyLogConsoleController.show()` 打开面板（隐藏入口场景）。

---

## 落盘（`LogFileConfig`）

| 字段 | 默认 | 说明 |
|---|---|---|
| `directory` | 必填 | 目录，不存在会自动创建；创建失败会在面板「外观 → 落盘」里显示原因 |
| `fileNamePattern` | `easylog_{date}.log` | 占位符：`{date}` `{time}` `{datetime}` `{pid}` `{package}`；非法字符自动换成 `_` |
| `minLevel` | `DEBUG` | 低于它不写文件（与 logcat 的 `d` 对齐） |
| `maxFileBytes` | 2MB | 单文件上限，超过滚动成 `xxx.log.1` |
| `maxFiles` | 5 | 保留文件数（含当前），超出的最旧一个删掉 |
| `flushIntervalMs` | 800ms | 批量刷盘间隔 |
| `includeThreadName` | false | 每行是否带线程名 |

运行时开关 / 改配置：

```kotlin
EasyLog.startFileLogging(LogFileConfig(File(filesDir, "logs"), minLevel = LogLevel.INFO))
EasyLog.stopFileLogging()
EasyLog.fileLogPath      // 当前文件名（绝对路径），没开就是 null
EasyLog.fileLogError     // 最近一次写盘失败原因，正常为 null
EasyLog.exportTo(File(dir, "dump.txt"))    // 把当前缓冲一次性导出（不动落盘任务）
```

面板里也能看到落盘路径，并有「导出当前 N 条到文件」按钮。

---

## API

### 打点

| 方法 | 说明 |
|---|---|
| `accept(level, tag, message, throwable?)` | 通用入口，正文里的 `[模块][分类]` 交给库解析 |
| `record(level, trace, module, category, message, throwable?)` | 结构化入口，不做解析（正文本体带方括号时用这个） |
| `step(trace, module, category, message)` | 关键节点，`LogLevel.STEP` |
| `fail(trace, module, message, throwable?)` | 失败节点，`LogLevel.ERROR` |
| `d/i/w/e(tag, message, throwable?)` | 单链路名简化版 |
| `addSink { entry -> ... }` | 旁路消费：上报 / 桥接其他日志系统。返回的 `AutoCloseable` 一 `close()` 就解绑 |

### 控制与读取

| 成员 | 说明 |
|---|---|
| `isAvailable` | 本包**是否允许**出现（`debugOnly` + `FLAG_DEBUGGABLE`）；release 包里恒为 `false` |
| `canShow` | `isAvailable && isEnabled`，UI 真正判断用这个 |
| `isEnabled` / `setEnabled(Boolean)` | 用户总开关，持久化；release 包里传 `true` 会被忽略 |
| `appearance` / `updateAppearance(...)` / `resetAppearance()` | 运行时外观（透明度、配色），面板里的滑杆走这里 |
| `EasyLogConsoleController.show() / hide() / toggle()` | 主动开合面板 |
| `EasyLogConsoleController.setBallVisible(Boolean?)` | 临时显隐悬浮球，`null` 交还给条件判断 |
| `entries()` / `entriesSince(id)` / `latestId()` / `errorCount()` / `clear()` | 缓冲读取 |

### 配置（`EasyLogConfig`）

| 字段 | 默认 | 说明 |
|---|---|---|
| `debugOnly` | `true` | 只在可调试包出现。判定用 `FLAG_DEBUGGABLE`，不依赖宿主的 `BuildConfig` |
| `enabled` | `true` | 用户可见总开关的默认值；`debugOnly` 不过时直接被忽略 |
| `capacity` | `3000` | 缓冲条数（200~20000） |
| `maxMessageChars` | `4000` | 单条正文/堆栈上限，防整段 base64 挤爆缓冲 |
| `ballAlpha` | `0.5` | 悬浮球不透明度（0.15~1.0） |
| `panelAlpha` | `0.82` | 面板不透明度（0.2~1.0） |
| `scrimAlpha` | `0.30` | 面板外遮罩不透明度（0~0.8） |
| `panelHeightFraction` | `0.62` | 面板高度占屏比（0.3~0.9） |
| `showThreadName` | `false` | 每行是否显示线程名（BLE 排查九成看顺序，默认关） |
| `trigger` | `FloatingBall` | `FloatingBall` 按条件显示球 / `Hidden` 只由宿主 `show()` 打开 |
| `colors` | `ConsoleColors()` | 配色；内置 `ConsolePalette.DARK / TERMINAL / LIGHT`，也可自定义每个颜色 |
| `ballVisibility` | `Always` | 悬浮球出现条件，见上文 |
| `file` | `null` | 落盘配置，`null` = 不写任何文件 |

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
- **不默认落盘**：要留存必须显式给目录。分享仍走剪贴板 + `ACTION_SEND` 文本
  （不落临时文件，省掉 `FileProvider` 和 authority 冲突）。
- **不持久化历史日志到内存之外**：杀进程即清空；要长期留存请开 `file` 或自己 `addSink`。
- **面板不跟随宿主主题**：调试工具自带一套配色，换来「装进任何工程长相一致」；
  需要跟随就换 `ConsolePalette` 或传自定义 `ConsoleColors`。
- **`debugOnly` 不是安全边界**：它防的是「release 包里误带调试浮层」，
  不是防逆向。真要彻底删掉，用 `debugImplementation` 依赖。

---

## 示例工程

`./gradlew :sample:installDebug`，界面里有：

- 走一遍配网（模拟 BLE 全链路，含 STEP / INFO / 异常）
- 造一条异常（验证红点）
- 走老格式 `[模块][分类]`（验证解析兼容）
- 瞬间刷 300 条（验证重组开销）
- 点我计数（验证浮层不挡业务点击）
- 切换配色（暗色 → 终端绿 → 亮色）
- 临时藏起悬浮球 3 秒（验证 `setBallVisible`）
- 落盘路径（验证 `LogFileConfig`）

## 测试

```bash
./gradlew :library:testDebugUnitTest
# 日志解析、环形缓冲、文件名模板、落盘与滚动，共 23 个用例
```

## 许可证

MIT，见 [LICENSE](LICENSE)。要换成 Apache-2.0 只改 `LICENSE` 与 `library/build.gradle.kts` 里 POM 的
`licenses` 一段。
