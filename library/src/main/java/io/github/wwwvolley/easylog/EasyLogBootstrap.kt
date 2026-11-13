package io.github.wwwvolley.easylog

import android.content.ComponentName
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri

/**
 * 自动初始化入口 —— **宿主一行代码都不写**的原因。
 *
 * 库的清单里注册了这个空 Provider，系统在 `Application.onCreate` 之前把它创建出来，
 * 我们借这个时机做三件事：
 * 1. [EasyLog.installFromBootstrap]：用默认配置把库装起来；
 * 2. 开始采集本进程 logcat（[EasyLogConfig.autoCaptureLogcat]）；
 * 3. 注册 Activity 生命周期回调，把悬浮球挂到每个 Activity 的 `decorView` 上。
 *
 * 这里**没有业务逻辑，也不读磁盘**：`onCreate` 只做上面三件轻量事，
 * 免得把冷启动拖慢（Provider 的耗时会算进应用启动时间）。
 *
 * ## 关掉自动初始化
 * 需要自己调 `EasyLog.install(...)` 配非默认参数时，在宿主清单加：
 * ```xml
 * <application>
 *     <meta-data
 *         android:name="io.github.wwwvolley.easylog.AUTO_INIT"
 *         android:value="false" />
 * </application>
 * ```
 * 不关也能用 —— 宿主的配置会覆盖这次默认安装（见 [EasyLog.install]）。
 *
 * ## 为什么用 Provider 而不是 `androidx.startup`
 * 少一个依赖。库的契约是「加一行依赖就能跑」，不该再要求宿主引别的初始化框架。
 */
class EasyLogBootstrap : ContentProvider() {

    override fun onCreate(): Boolean {
        val app = context?.applicationContext ?: return false
        if (!isAutoInitEnabled(app)) return false

        EasyLog.installFromBootstrap(app)
        // release 包里 isAvailable 恒为 false，挂浮层没有意义，连回调都不注册
        if (EasyLog.isAvailable) EasyLogOverlayAutoAttach.attach(app)
        return true
    }

    // 这个 Provider 不对外提供数据，下面都是空实现
    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    companion object {

        /** 宿主清单里用这个 key 关掉自动初始化。 */
        const val META_DATA = "io.github.wwwvolley.easylog.AUTO_INIT"

        /**
         * 自动初始化是否开启（读 Provider 节点上的 meta-data，默认开）。
         *
         * 读取失败按「开」处理：多装一次不影响功能，反之会让零代码接入静默失效，
         * 后者极难排查。
         */
        fun isAutoInitEnabled(context: Context): Boolean = runCatching {
            val info = context.packageManager.getProviderInfo(
                ComponentName(context, EasyLogBootstrap::class.java),
                PackageManager.GET_META_DATA,
            )
            info.metaData?.getBoolean(META_DATA, true) ?: true
        }.getOrDefault(true)
    }
}
