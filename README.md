# easyLogConsole

App 内悬浮日志台：一个半透明悬浮球 + 一块半透明日志面板，**真机上直接看关键链路日志，不用连电脑、不用开 Android Studio**。

为「蓝牙扫描 → 连接 → 读 SN → 上报 → 下发 WiFi → 云端激活」这类跨层链路设计：一条链路走完，手机上点开就能按环节筛、按时间看间隔。

---

## 接入：一行依赖，零行代码

```kotlin
// app/build.gradle.kts
dependencies {
    debugImplementation("io.github.wwwvolley:easylog-console:1.0.0")
}
```

**完了。** 不需要改 `Application`，不需要改根布局，不需要动任何一行日志打点 ——
库自带一个 ContentProvider，进程启动时自动完成三件事：

1. 用默认配置把库装起来；
2. 开始采集**本进程**的 logcat；
3. 把悬浮球挂到每个 Activity 的 `decorView` 上（最上层，空白区不挡点击）。

你原来的 `Log.d/i/w/e`、`AppLog`、Android Studio 的 Logcat 窗口，全部照旧。

> 为什么不用 `implementation`：`debugImplementation` 让 release 包**连这个 AAR 都不打进去**，
> 比运行时判断更彻底 —— 包体零增长，反编译也看不到。

| | |
|---|---|
| 只在 debug 出现 | `debugImplementation` + `debugOnly`（`FLAG_DEBUGGABLE`）双保险；release 包连类都没有 |
| 接入成本 | **1 行依赖 / 0 行代码** |
| 不影响控制台 | 从不调 `android.util.Log`，也从不拦截它 —— 只是多读了一份自己的 logcat |
| 悬浮球 | 半透明（默认 55%，可调）、可拖动、松手吸附左右边、位置记忆；有新的 WARN/ERROR 时带未读红点 |
| 显示条件 | `BallVisibility` 自定义「什么时候出现」，也可运行时 `setBallVisible()` 临时隐藏 |
| 面板 | 半透明（默认 82%，可调）、遮罩浓度可调；能一边看日志一边操作界面；键盘弹出自动上移 |
| 外观 | 面板 / 球 / 遮罩三个滑杆 + 三套配色（暗色 / 终端绿 / 亮色），面板内实时调、持久化 |
| 落盘 | 可选，默认**一个字节都不写**。目录、文件名模板、滚动大小、保留数量全由开发者指定 |
| 过滤 | 链路（tag）/ 级别 / 关键字 三个维度，边打边看 |
| 行信息 | 时间 + **与上一行的间隔** + 级别 + 模块·分类 + 正文；点开看全文，长按复制单条 |
| 性能 | 写端只入环形缓冲，UI 每 120ms 拉一次增量：瞬间刷 300 条也只触发一次重组 |
| 体积 | 零三方依赖（只有 Compose + core-ktx）；核心层无 Compose 依赖，可纯 JVM 单测 |

---

## 日志是怎么进来的

**读本进程的 logcat**（`logcat -v threadtime --pid=<自身>`），不是 hook `Log`。

- 不用改任何打点代码：`Log.i("YBDL", "[PROVISION][连接] 开始连接")` 直接就能在面板里看到；
- 不用加权限：Android 4.1 起普通应用只能读到自己进程的日志；
- 不影响控制台：logcat 可以有很多 consumer，我们只是其中一个；
- 格式兼容：正文的 `[模块][分类]` 前缀会被拆成「模块 · 分类」两段，最多两段、每段 ≤16 字，报文里的方括号不会被误拆。

### 关键节点：正文以 `★` 开头

```kotlin
Log.i("YBDL", "★ [连接][流程] connectGatt(autoConnect=false)")
```

面板里这行会显示成绿色色条 + 绿色星标，手指快速滚动时一眼能定位。

### 默认降噪

每个 debug 包的 logcat 里都有大量和业务无关的行（`Choreographer`、`OpenGLRenderer`、
`VRI[...]`、ART 的 JIT、三星的 `IDS_TAG`……），默认按 **tag 前缀**挡掉一批，
并且丢弃「tag 就是本应用包名」的行（`W com.foo.bar usrcalluqfd: ...` 这种）。
默认还丢掉了 VERBOSE 一档。

嫌不够/嫌太多就自己调：

```kotlin
EasyLogConfig(
    logcatMinLevel = LogLevel.INFO,          // 更安静
    logcatTags = setOf("YBDL"),              // 只收自己的业务 tag
    logcatIgnoreTags = emptySet(),           // 一条都不挡，全量收
)
```

---

## 想要自定义配置（1 行）

```kotlin
// Application.onCreate()
EasyLog.install(this, EasyLogConfig(logcatTags = setOf("YBDL"), capacity = 5000))
```

初始化是幂等的：自动初始化先跑过也没关系，这里的配置会覆盖它（仅这一次）。

如果连那点开销都不想要，在清单里关掉自动初始化：

```xml
<application>
    <meta-data
        android:name="io.github.wwwvolley.easylog.AUTO_INIT"
        android:value="false" />
</application>
```

关掉之后要自己 `EasyLog.install(this)` 或 `installEasyLogOverlay()`。

---

## 落盘（可选，默认不写文件）

```kotlin
EasyLog.startFileLogging(
    LogFileConfig(
        directory = File(getExternalFilesDir(null), "easylog"), // 目录你说了算
        minLevel = LogLevel.DEBUG,
        maxFileBytes = 2L * 1024 * 1024,                        // 超过就滚动
        maxFiles = 5,                                           // 保留 5 份
    ),
)
```

- 默认 `file = null` = **一个字节都不写**，完全不碰宿主自己的日志目录；
- 只 append 自己生成的那个文件及 `.1 .2 …` 滚动副本，不删、不截断别人的文件；
- 打点线程只 `offer` 进队列，单线程每 800ms 批量 write + flush，不阻塞 BLE 回调；
- 面板「外观」页能看到当前落盘路径，也能一键导出当前缓冲区。

`EasyLog.exportTo(File)` 是一次性导出，不影响落盘任务。

---

## 不想用 logcat（精确投递）

有两条纯 API 的路，适合「不想让系统日志进来」「需要精确的模块/分类/异常堆栈」的工程：

```kotlin
// 1) 结构化：模块、分类、级别都由你给
EasyLog.record(LogLevel.INFO, trace = "PROVISION", module = "连接", category = "数据", message = "MTU=247")

// 2) 兼容老格式：正文写 `[模块][分类] 正文`，库自己拆
EasyLog.accept(LogLevel.WARN, tag = "PROVISION", message = "[下发][异常] GATT 133，重试 1/2")
```

已经有日志出口的，把 sink 挂上去即可（业务打点代码照样不用改）：

```kotlin
AppLog.sink = { level, tag, msg, throwable -> EasyLog.accept(level.toEasyLogLevel(), tag, msg, throwable) }
```

这时建议 `EasyLogConfig(autoCaptureLogcat = false)`，避免两路日志重复。

---

## 界面挂载方式（三选一，别同时用）

| 方式 | 用法 | 适用 |
|---|---|---|
| **自动**（默认） | 什么都不写 | 99% 的场景 |
| 手动挂载 | `installEasyLogOverlay()` | 关掉了自动初始化 / 只想在某个 Activity 出现 |
| 根布局内 | `EasyLogConsole()` | Compose 工程想省一层 View（配 `autoAttachOverlay = false`） |

多 Activity 重建（旋转、深色模式切换）不会叠出两个球 —— `decorView` 上有 tag 去重。

---

## 三个「不要影响原页面 / 原有能力」的设计

### 一、不影响 Android Studio 控制台

库**从不调用** `android.util.Log`，也从不拦截它。它读的是 logcat 的另一个 consumer，
你的 `Log.x` 输出、`adb logcat -s YBDL | grep PROVISION` 的过滤方式完全不变。

### 二、不影响宿主自己的落盘

默认 `file = null`，一个字节都不写。要落盘时也只碰 `LogFileConfig` 指定的目录与文件名模板，
不删、不截断、不锁别人的文件。

### 三、不影响原页面观看

球和面板的透明度都加在**颜色 alpha** 上（不是 `Modifier.alpha` —— 那会走离屏合成并按节点边界
裁剪，把悬浮球上的红点削掉一角），叠在页面上还能看见底下的内容。
面板默认 82% + 30% 遮罩，进「外观」页能实时拖到 20% ~ 100%。
浮层的空白区**不消费触摸**，Compose 根没命中任何指针输入时会把事件还给页面。

> 顺带一个坑：`Modifier.alpha != 1f` 会给子树加离屏层并按**节点边界**裁剪，
> 凡是靠 `offset` / 负 padding 探出父节点的子元素都会被切。红点必须画在未裁剪的外层。

---

## 显示时机（`BallVisibility`）

```kotlin
EasyLogConfig(
    ballVisibility = BallVisibility { userRepo.isLoggedIn },   // 每 500ms 问一次，实现要轻
)
```

内置两种：`Always`（默认）、`AfterFirstOpen`（面板打开过一次后才出现）。
临时藏一下用 `EasyLogConsoleController.setBallVisible(false)`，优先级高于条件，
传 `null` 恢复。完全不想要球就 `EasyLogConfig.trigger = Trigger.Hidden`，自己调
`EasyLogConsoleController.show()` 打开。

---

## API

### 打点

| 方法 | 说明 |
|---|---|
| `EasyLog.accept(level, tag, message, throwable?)` | 通用入口，`[模块][分类]` 从正文拆 |
| `EasyLog.record(level, trace, module, category, message, throwable?)` | 结构化入口，不做解析 |
| `EasyLog.step / fail / d / i / w / e` | 上面的语法糖 |

### 控制与读取

| 方法 | 说明 |
|---|---|
| `install(context, config)` / `installFromBootstrap(context)` | 初始化（自动初始化会调后者） |
| `startLogcatCapture()` / `stopLogcatCapture()` | 采集开关，`isCapturingLogcat` / `logcatError` 可查 |
| `startFileLogging(config)` / `stopFileLogging()` / `fileLogPath` | 落盘 |
| `exportTo(File)` | 一次性导出缓冲区 |
| `entries()` / `entriesSince(id)` / `latestId()` / `errorCount()` / `size()` / `clear()` | 读缓冲 |
| `addSink { }` / `addEnabledListener { }` / `addAppearanceListener { }` | 旁路订阅 |
| `updateAppearance(...)` / `resetAppearance()` / `appearance` | 外观 |
| `isAvailable` / `canShow` / `isInstalled` / `setEnabled(Boolean)` | 状态 |

### 配置（`EasyLogConfig`）

全部有默认值。常用的几项：

| 字段 | 默认 | 说明 |
|---|---|---|
| `debugOnly` | `true` | 只在可调试包出现（`FLAG_DEBUGGABLE`） |
| `autoCaptureLogcat` | `true` | 自动采集本进程 logcat |
| `logcatMinLevel` | `DEBUG` | 采信的最低级别 |
| `logcatTags` | 空 | 只收这些 tag（空 = 不限） |
| `logcatIgnoreTags` | 一批系统噪音 | 前缀匹配，`emptySet()` = 全量收 |
| `autoAttachOverlay` | `true` | 自动挂到每个 Activity |
| `capacity` | `3000` | 缓冲条数（200 ~ 20000） |
| `panelAlpha` / `ballAlpha` / `scrimAlpha` | `0.82 / 0.55 / 0.30` | 三处不透明度默认值 |
| `ballVisibility` / `trigger` | `Always` / `FloatingBall` | 出现条件 |
| `colors` | 暗色 | `ConsolePalette.DARK / TERMINAL / LIGHT` 或自定义 `ConsoleColors` |
| `file` | `null` | 落盘，`null` = 不写 |

---

## 复用其他项目

**方式 A：Maven 坐标（推荐）**

```kotlin
dependencies { debugImplementation("io.github.wwwvolley:easylog-console:1.0.0") }
```

发布到本地/私有仓库：

```bash
./gradlew :easylog-console:publishToMavenLocal   # 本机 ~/.m2
./gradlew :easylog-console:publishReleasePublicationToLocalRepoRepository  # build/repo 目录
```

也可以把 `library/build.gradle.kts` 里 `repositories` 换成 GitHub Packages / 公司私服。

**方式 B：复合构建（改库源码时用）**

```kotlin
// settings.gradle.kts
includeBuild("../easyLogConsole")
```

依赖照写坐标 —— 那个工程的模块名就叫 `easylog-console`、group 是 `io.github.wwwvolley`，
跟坐标一致，Gradle 会自动替换成本地源码，**不用写 `dependencySubstitution`**。

**方式 C：拷源码**

`library/src/main/java/io/github/wwwvolley/easylog/` 整个包拷进去即可，
核心层不依赖 Compose（`ui` 包除外），模块名随意。

---

## 已知边界（都是故意这么设计的）

- **只采本进程日志**：多进程 App 里，其它进程的日志不会出现在这个面板里（系统限制）。
- **tag 上限 23 字符**：超长 tag 会被 logcat 截断，按 tag 精确筛选时注意。
- **不用 logcat 时没有 `trace`**：`accept` 的 trace 就是 tag；想自定义链路名请用 `record`。
- **面板打开状态不跨 Activity 保留**：换页会重建浮层，滚动位置重置（日志本身不丢）。
- **`STEP` 只由 `★` 前缀或 `EasyLog.step()` 产生**：logcat 本身没有这个级别。
- **个别 ROM 可能禁 `logcat`**：此时 `EasyLog.logcatError` 有值、面板为空，
  改用 `EasyLog.accept` 即可。

---

## 示例工程

```bash
./gradlew :sample:installDebug
```

`sample/MainActivity` 里**没有任何初始化代码**，只用原生 `Log.x` 打点 ——
这就是「一行依赖、零行代码」的可执行证明。

---

## 测试

```bash
./gradlew :easylog-console:testDebugUnitTest
# 日志解析、logcat 行解析、环形缓冲、文件名模板、落盘与滚动，共 30 个用例
```

## 许可证

MIT
