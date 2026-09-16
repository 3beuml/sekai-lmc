package com.pjsk.toolbox

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.pjsk.toolbox.data.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class PjskApp : Application(), ImageLoaderFactory {

    lateinit var container: AppContainer
        private set

    /**
     * 应用级协程作用域。
     *
     * 用来在启动时后台导入「内置数据快照」—— 首页不等它，导入完的表会通过 Room 的 Flow
     * 自动出现在界面上。用 SupervisorJob 是为了让某一张表导入失败不会连带取消整个作用域。
     */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.ensureBundledDataInstalled(appScope)
        // 内置数据（同步那步）之后再静默增量同步一次：
        // 版本没变的话它一个文件都不下，变了才会去补 —— 这样新活动上线后
        // 用户只要打开 App 就能看到，不需要自己去点「数据同步」。
        container.autoSyncOnStart(appScope)
    }

    /**
     * 显式配置 Coil 的图片加载器（单例）。
     *
     * **为什么必须显式配置**：素材图片是「下载一次、长期复用」的东西，而
     * `storage.sekai.best` 的卡面小图单张 58 KB，实测从这台机器下载要 1–7 秒
     * （8–56 KB/s，波动很大）。在这么慢的前提下，缓存有没有生效就是体验的分水岭：
     *
     *  - 有缓存：滑过去再滑回来是**本地读取**，瞬间出现；
     *  - 没缓存：每次都要重新下载，用户看到的就是「刚才还在，现在没了，还在转圈」。
     *
     * 三个配置各自解决一件事：
     *  1. **磁盘缓存 512 MB**：默认缓存偏小，一滚动就把它挤掉；卡面/曲绘总量也就几百 MB，
     *     给足空间才能做到「看过就一直在」。放在 `cacheDir` 下，系统清理缓存时会一起回收，
     *     不会永久占用用户空间。
     *  2. **内存缓存 25%**：列表快速滚动时避免频繁回到磁盘解码。
     *  3. **`respectCacheHeaders(false)`**：素材 CDN 本身发的是
     *     `Cache-Control: max-age=1209600`（14 天，可缓存），正常情况下不需要这一条；
     *     但万一中间有层返回 `no-cache`，严格遵守缓存头就会导致**永远不缓存**。
     *     这里明确以「URL 不变就复用」为准。
     */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(512L * 1024 * 1024)
                    .build()
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    // 33%（原来是 25%）：预览档提到 800px 后单张解码约占 1.2 MB，
                    // 需要保证「首页 20 张 + 图鉴可见 8 张」能同时待在内存里，
                    // 否则会退回「切换页面时互相挤出、重新解码」的老问题。
                    .maxSizePercent(0.33)
                    .build()
            }
            .respectCacheHeaders(false)
            // 淡入缩短到 100ms：原来默认的 300ms 在「切页面重新解码」时
            // 会让每一次都看起来像在加载。100ms 保留一点过渡感但不抢眼。
            .crossfade(100)
            .build()
}
