# easyLogConsole

App 内悬浮日志台：一个半透明悬浮球 + 一块半透明日志面板。
**真机上直接看关键链路日志，不用连电脑、不用开 Android Studio。**

为「蓝牙扫描 → 连接 → 读 SN → 上报 → 下发 WiFi → 云端激活」这类跨层链路设计：
一条链路走完，手机上点开就能按环节筛、按时间看间隔，关掉再打开还是你刚才那套过滤条件。

- 接入成本：**1 行依赖 / 0 行代码**
- 只在 debug 包出现，release 包连类都不打进去
- 不影响 Android Studio 控制台，也不影响你自己往自己目录落盘

---

## 快速开始

### 步骤 1：加依赖（三选一）

本项目当前以 **1.0.0** 开发，还没发到 Maven Central，三种引入方式按团队情况选：

**A. 复合构建（团队内部推荐，改库源码立刻生效）**

```kotlin
// settings.gradle.kts
includeBuild("../easyLogConsole")
```

```kotlin
// app/build.gradle.kts
dependencies {
    debugImplementation("io.github.wwwvolley:easylog-console:1.0.0")
}
```

那个工程的模块名就叫 `easylog-console`、group 是 `io.github.wwwvolley`，跟坐标一致，
Gradle 会自动把依赖替换成本地源码 —— **不用写 `dependencySubstitution`**。

**B. 本地 Maven 仓库**

```bash
# 在 easyLogConsole 仓库里执行一次
./gradlew :easylog-console:publishToMavenLocal
```

```kotlin
// settings.gradle.kts
dependencyResolutionManagement { repositories { mavenLocal(); google(); mavenCentral() } }

// app/build.gradle.kts
dependencies { debugImplementation("io.github.wwwvolley:easylog-console:1.0.0") }
```

**C. 拷贝源码**

把 `library/src/main/java/io/github/wwwvolley/easylog/` 整个包拷进你的工程，
模块名随意。核心层不依赖 Compose（`ui` 包除外），最小可用只需要 Compose + core-ktx。

> 为什么是 `debugImplementation` 而不是 `implementation`：release 包**连这个 AAR 都不打进去**，
> 比运行时判断更彻底 —— 包体零增长，反编译也看不到。库内部还有一道 `debugOnly` 兜底。

### 步骤 2：打一行日志

什么都不用初始化，你原来的写法就行：

```kotlin
Log.i("YBDL", "[PROVISION][连接] connectGatt(autoConnect = false)")
```

### 步骤 3：在手机上打开

装上 debug 包，屏幕右侧会出现一个半透明的 **LOG** 球。点它 → 日志面板从底部升起，
刚才那行日志就在里面：

```
17:52:37.677 +107ms  I  连接 · 流程  connectGatt(autoConnect = false)
```

### 步骤 4：让关键节点显眼（可选）

正文以 `★` 开头的行会被升级成「关键节点」：绿色色条 + 绿星，滚动时一眼能定位。

```kotlin
Log.i("YBDL", "★ [连接][流程] GATT 已连接，开始读 SN")
Log.i("YBDL", "★ [云端][流程] 激活成功")
```

### 检查清单（接完确认这几条）

| 检查 | 期望 |
|---|---|
| 屏幕上有没有球 | 有（右侧，半透明，可拖） |
| `adb logcat -s YBDL` | 照常输出，一行不少 |
| 面板里的日志 | 只出现业务日志，没有 `Choreographer` / `VRI` 刷屏 |
| 点页面上的按钮 | 正常响应（球的空白区不消费触摸） |
| 装 release 包 | 完全看不到球 |

---

## 面板怎么用

| 区域 | 说明 |
|---|---|
| 顶部 | `日志台 N 条`、暂停/继续、清空、复制、分享、外观、关闭 |
| 链路 chips | 按 tag 分链路，点一下只看这条链路，再点一下回到全部 |
| 级别 chips | 全部 / 关键+ / 警告+ / 异常 |
| 搜索框 | 关键字，匹配模块、分类、正文、链路，忽略大小写；键盘弹出时面板自动上移 |
| 日志行 | 时间 + **与上一行的间隔**（`+120ms`）+ 级别 + `模块 · 分类` + 正文 |
| 行交互 | 点一下展开全文（支持长文本/堆栈），长按复制这一条 |
| 底部状态 | `共 N 条 · 筛出 M 条 · 自动跟随最新`，落盘中会额外提示 |
| 外观页 | 面板 / 悬浮球 / 遮罩三个不透明度滑杆 + 三套配色 + 落盘路径 + 导出 |

### 你的操作会被记住

排查问题时反复开合面板是常态，所以面板的状态分两层存：

| 记什么 | 存哪 | 关掉面板再开 | 杀进程重开 |
|---|---|---|---|
| 链路 / 级别 / 关键字 | `SharedPreferences` | 保持 | **保持** |
| 面板与悬浮球透明度、配色 | `SharedPreferences` | 保持 | **保持** |
| 悬浮球位置 | `SharedPreferences` | 保持 | **保持** |
| 日志台总开关 | `SharedPreferences` | 保持 | **保持** |
| 暂停状态 | 内存 | 保持 | 重置为「继续」 |
| 停在外观页 | 内存 | 保持 | 重置为日志列表 |
| 展开了哪几行 | 内存 | 保持 | 重置 |

一句话：点悬浮球重新打开，看到的是**你离开时那个面板**，不是一个刚出厂的默认态。

> 关键字是边打边筛的，落盘做了 400ms 去抖（一个词不该换十几次磁盘写），
> 离开面板时会再兜一次 —— 敲完字立刻点 ✕ 也不会丢。

---

## 进阶用法

### 1. 只收自己的 tag（多模块共用日志出口时最有用）

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        EasyLog.install(this, EasyLogConfig(logcatTags = setOf("YBDL", "BLE")))
    }
}
```

自动初始化已经跑过也没关系：这里的配置会覆盖它一次，之后再调保持幂等。

### 2. 更安静，或者更全

```kotlin
EasyLogConfig(
    logcatMinLevel = LogLevel.INFO,   // 更安静：丢掉 DEBUG
    logcatIgnoreTags = emptySet(),    // 更全：一条都不挡（默认挡一批系统噪音）
    capacity = 5000,                  // 缓冲条数，默认 3000
)
```

默认忽略列表按 **tag 前缀** 匹配，覆盖 `Choreographer`、`OpenGLRenderer`、`VRI[...]`、
`Adreno`、三星的 `IDS_TAG`、ART 等每个 debug 包都在刷但和业务无关的行，
另外会丢弃「tag 就是本应用包名」的行。

### 3. 落盘到自己的目录（默认一个字节都不写）

```kotlin
EasyLogConfig(
    file = LogFileConfig(
        directory = File(getExternalFilesDir(null), "easylog"), // 目录你说了算
        fileNamePattern = "easylog_{date}-{time}.log",          // {date} {time} {datetime} {pid} {package}
        minLevel = LogLevel.DEBUG,
        maxFileBytes = 2L * 1024 * 1024,                        // 超过就滚动成 .1 .2 …
        maxFiles = 5,                                           // 最多留 5 份
    ),
)
```

- 默认 `file = null`，完全不碰宿主自己的日志目录；
- 只 append 自己生成的那个文件及滚动副本，不删、不截断别人的文件；
- 打点线程只入队（队列满就丢弃新日志），单线程每 800ms 批量 write + flush，不卡 BLE 回调。

拷贝出来看：

```bash
adb pull /sdcard/Android/data/你的包名/files/easylog/
```

一次性导出当前缓冲区（不影响落盘任务）：

```kotlin
EasyLog.exportTo(File(context.filesDir, "snapshot.log"))
```

### 4. 结构化打点（不靠 logcat 也能用）

不想让系统日志进来、或者需要精确的模块/分类/堆栈时：

```kotlin
// 模块、分类、级别全由你给，正文里的方括号不会被误拆
EasyLog.record(LogLevel.INFO, trace = "PROVISION", module = "连接", category = "数据", message = "MTU=247")

// 兼容老格式：正文写 [模块][分类] 正文，库自己拆
EasyLog.accept(LogLevel.WARN, tag = "PROVISION", message = "[下发][异常] GATT 133，重试 1/2")

// 关键节点与失败节点
EasyLog.step("PROVISION", "连接", "流程", "已连上，开始读 SN")
EasyLog.fail("PROVISION", "下发", "WiFi 下发超时", throwable)
```

这时建议 `EasyLogConfig(autoCaptureLogcat = false)`，避免两路日志重复。

### 5. 桥接已有日志出口（业务打点代码不用改）

```kotlin
// 例如工程里已有的 AppLog
AppLog.sink = { level, tag, message, throwable ->
    EasyLog.accept(level.toEasyLogLevel(), tag, message, throwable)
}
```

`toEasyLogLevel()` 是你自己写的一个小扩展（`android.util.Log.DEBUG -> LogLevel.DEBUG` 之类）。
转发是**旁路**：`Log.x` 照常输出到控制台，两边互不干扰。

### 6. 预置过滤条件（打开面板就是筛好的）

```kotlin
// Application.onCreate()
EasyLog.install(this, EasyLogConfig(logcatTags = setOf("YBDL")))
EasyLog.setFilter(ConsoleFilter(trace = "PROVISION"))        // 只看配网链路
EasyLog.setFilter(ConsoleFilter(minLevel = LogLevel.ERROR))  // 只看异常
EasyLog.resetFilter()                                        // 回到默认
```

面板里改过滤条件也会写回这里并持久化，两者是同一份状态。

### 7. 控制悬浮球什么时候出现

```kotlin
EasyLogConfig(
    // 每 500ms 问一次，实现要轻；返回 false 时球不显示
    ballVisibility = BallVisibility { userRepo.isLoggedIn && BuildConfig.DEBUG },
)
```

内置 `BallVisibility.Always`（默认）、`BallVisibility.AfterFirstOpen`（打开过一次后才常驻）。
运行时临时藏一下：

```kotlin
val handle = EasyLogConsoleController.setBallVisible(false)  // 优先级高于条件
// 想恢复：setBallVisible(null)
```

完全不想要球、只留一个隐藏入口（比如「版本号连点 7 次」）：

```kotlin
EasyLogConfig(trigger = EasyLogConfig.Trigger.Hidden)
EasyLogConsoleController.show()   // 自己在入口里调
EasyLogConsoleController.toggle()
```

### 8. 自定义配色

```kotlin
EasyLogConfig(colors = ConsolePalette.TERMINAL.colors)   // 暗色 / TERMINAL / LIGHT
EasyLogConfig(colors = ConsoleColors(panel = 0xE61A1D21, text = 0xFFE8EAED /* ... */))
```

面板「外观」页里也能实时切换三套预设，切换后会持久化。

### 9. 自己挂浮层（不想要自动挂载时）

```kotlin
// 清单里关掉自动初始化
// <meta-data android:name="io.github.wwwvolley.easylog.AUTO_INIT" android:value="false" />

// Application.onCreate()
EasyLog.install(this, EasyLogConfig(autoAttachOverlay = false))

// 方式 A：Compose 根布局里（省一层 View）
Box(Modifier.fillMaxSize()) {
    AppNavHost()
    EasyLogConsole()
}

// 方式 B：塞进 decorView
installEasyLogOverlay()
```

`decorView` 上有 tag 去重，旋转 / 深色模式切换不会叠出两个球。

### 10. 运行时控制

```kotlin
EasyLog.setEnabled(false)            // 等于用户关掉日志台（持久化）
EasyLogConsoleController.show()      // 打开面板
EasyLogConsoleController.hide()
EasyLogConsoleController.isOpen
EasyLog.clear()                      // 清空缓冲
EasyLog.startLogcatCapture()         // 采集开关
EasyLog.stopLogcatCapture()          // 只关心「从现在开始」时用
EasyLog.addSink { entry -> }         // 旁路消费：上报、再落一份、桥接别的 SDK
EasyLog.entries()                    // 全量快照
EasyLog.errorCount()                 // 未读红点用它
```

---

## 三个「不影响原有能力」的设计

### 一、不影响 Android Studio 控制台

库**从不调用** `android.util.Log`，也从不拦截它。它读的是 logcat 的另一个 consumer，
你的 `Log.x` 输出、`adb logcat -s YBDL | grep PROVISION` 的过滤方式完全不变。

### 二、不影响宿主自己的落盘

默认 `file = null`，一个字节都不写。要落盘时也只碰 `LogFileConfig` 指定的目录与文件名模板，
不删、不截断、不锁别人的文件。

### 三、不影响原页面观看

球和面板的透明度都加在**颜色 alpha** 上，叠在页面上还能看见底下的内容。
面板默认 82% + 30% 遮罩，进「外观」页能实时拖到 20% ~ 100%。
浮层空白区**不消费触摸**，事件会落到下面的页面上。

> 实现上有个坑值得记：`Modifier.alpha != 1f` 会给子树加离屏层并按**节点边界**裁剪，
> 凡是靠 `offset` / 负 padding 探出父节点的子元素都会被切（悬浮球上的红点就是这么被削掉的）。
> 所以透明度一律加在颜色上，红点画在未裁剪的外层。

---

## API 速查

### 打点

| 方法 | 说明 |
|---|---|
| `EasyLog.accept(level, tag, message, throwable?)` | 通用入口，`[模块][分类]` 从正文拆 |
| `EasyLog.record(level, trace, module, category, message, throwable?)` | 结构化入口，不做解析 |
| `EasyLog.step / fail / d / i / w / e` | 上面的语法糖 |

### 控制与读取

| 方法 | 说明 |
|---|---|
| `install(context, config)` | 初始化（自动初始化会先跑一次） |
| `startLogcatCapture()` / `stopLogcatCapture()` | 采集开关，`isCapturingLogcat` / `logcatError` 可查 |
| `startFileLogging(config)` / `stopFileLogging()` / `fileLogPath` | 落盘 |
| `exportTo(File)` | 一次性导出缓冲区 |
| `setFilter(ConsoleFilter)` / `resetFilter()` / `filter` | 过滤条件 |
| `updateAppearance(...)` / `resetAppearance()` / `appearance` | 外观 |
| `entries()` / `entriesSince(id)` / `latestId()` / `errorCount()` / `size()` / `clear()` | 读缓冲 |
| `addSink { }` / `addEnabledListener { }` / `addAppearanceListener { }` / `addFilterListener { }` | 旁路订阅 |
| `isAvailable` / `canShow` / `isInstalled` / `setEnabled(Boolean)` | 状态 |

### 配置（`EasyLogConfig`）

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
| `colors` | 暗色 | `ConsolePalette.DARK / TERMINAL / LIGHT` 或自定义 |
| `file` | `null` | 落盘，`null` = 不写 |
| `showThreadName` | `false` | 每行是否显示线程名 |

---

## 复用与发布（维护者）

```bash
./gradlew :easylog-console:publishToMavenLocal                                   # 装到 ~/.m2
./gradlew :easylog-console:publishReleasePublicationToLocalRepoRepository        # 出到 build/repo
./gradlew :easylog-console:assembleRelease                                       # 只要 AAR
```

坐标 `io.github.wwwvolley:easylog-console:<version>`，版本号可由
`gradle.properties` 的 `easylog.version` 覆盖。想发到 GitHub Packages / 公司私服，
把 `library/build.gradle.kts` 里的 `repositories` 换掉即可，publication 部分不用动。

发版流程：

```bash
git tag -a v1.0.0 -m "easyLogConsole 1.0.0"
git push origin main --tags
./gradlew :easylog-console:publishToMavenLocal   # 或推到私有仓库
```

---

## 常见问题

**面板里一条日志都没有？**
先看 `adb logcat` 有没有输出。若你的日志打在别的进程里（多进程 App），
本进程读不到 —— 那是系统限制，用 `EasyLog.record(...)` 手动投递。

**球被系统/页面挡住了？**
库把浮层挂在 `decorView` 最上层，正常不会。真被挡住时拖到边缘（位置会记住）。

**日志太吵？**
`logcatTags = setOf("YBDL")` 只收自己的 tag，或 `logcatMinLevel = LogLevel.INFO`。

**影响性能吗？**
写端只入环形缓冲就返回；UI 每 120ms 拉一次增量，瞬间刷 300 条也只触发一次重组。
面板关闭时不拉、不重组。`adb logcat` 本身的开销来自系统，库只是多一个 consumer。

**release 包里会有残留吗？**
`debugImplementation` 让 AAR 不进包；即使被别的途径打进去，`debugOnly` 也会让
`EasyLogConsole` 一行都不画、`accept` 直接返回。

**个别 ROM 禁了 logcat？**
`EasyLog.logcatError` 会有值，面板为空。改用 `EasyLog.record / accept` 手动投递。

---

## 已知边界（都是故意的）

- **只采本进程日志**：多进程 App 里其它进程的日志不会出现在面板（系统限制）。
- **tag 上限 23 字符**：超长 tag 会被 logcat 截断，按 tag 精确筛选时注意。
- **不用 logcat 时没有 trace**：`accept` 的 trace 就是 tag；想自定义链路名请用 `record`。
- **滚动位置不保留**：新日志总在手指最近的位置（面板按「跟随最新」设计）。
- **`STEP` 只由 `★` 前缀或 `EasyLog.step()` 产生**：logcat 本身没有这个级别。
- **核心层不含 Compose**：`LogStore` / `LogEntry` / `LogParser` / `LogFileSink` / `ConsoleFilter`
  都是纯 Kotlin/JVM，可以在 JVM 单测里直接跑。

---

## 示例工程

```bash
./gradlew :sample:installDebug
```

`sample/MainActivity` 里**没有任何初始化代码**，只用原生 `Log.x` 打点 ——
这就是「一行依赖、零行代码」的可执行证明。界面上还有配网链路模拟、造异常、300 条压测、
配色切换、临时隐藏悬浮球等按钮。

## 测试

```bash
./gradlew :easylog-console:testDebugUnitTest
# 日志前缀解析、logcat 行解析、环形缓冲、文件名模板、落盘与滚动、过滤条件，共 37 个用例
```

## 许可证

MIT
