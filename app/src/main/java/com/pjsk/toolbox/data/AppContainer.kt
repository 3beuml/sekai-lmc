package com.pjsk.toolbox.data

import android.content.Context
import com.pjsk.toolbox.data.card.CardRepository
import com.pjsk.toolbox.data.db.AppDatabase
import com.pjsk.toolbox.data.home.HomeRepository
import com.pjsk.toolbox.data.music.LyricsRepository
import com.pjsk.toolbox.data.music.MusicRepository
import com.pjsk.toolbox.data.settings.AppSettings
import com.pjsk.toolbox.data.player.MusicPlayer
import com.pjsk.toolbox.data.player.MusicPlayerFactory
import com.pjsk.toolbox.data.remote.MasterRepository
import com.pjsk.toolbox.data.remote.MirrorFallbackInterceptor
import com.pjsk.toolbox.data.sync.BundledPack
import com.pjsk.toolbox.data.sync.BundledState
import com.pjsk.toolbox.data.sync.MasterImporter
import com.pjsk.toolbox.data.sync.SyncManager
import com.pjsk.toolbox.data.story.StoryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 手写的极简依赖容器（不引入 Hilt，减少一层注解处理与编译风险）。
 */
class AppContainer(private val context: Context) {

    private val appContext: Context = context.applicationContext

    /**
     * 应用级协程作用域。
     *
     * 现在有两个用途：① 卡牌列表的解析结果缓存（`stateIn`）；
     * ② 内置数据快照的导入。用 SupervisorJob 让其中一项失败不会连带取消其它。
     */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** master data 原始 JSON 的临时落盘目录；导入数据库后会立即删除，不长期占用空间。 */
    val masterDataDir: File = File(appContext.cacheDir, "masterdata").apply { mkdirs() }

    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(180, TimeUnit.SECONDS) // 54 MB 的表在慢网上需要更久
        .followRedirects(true)
        // 卡面与音频默认走镜像站（见 AssetUrls.MIRROR_HOST 的实测数据）；
        // 镜像挂了或那边缺文件时，这一层自动改回官方 CDN 重试一次。
        .addInterceptor(MirrorFallbackInterceptor())
        .build()

    val database: AppDatabase by lazy { AppDatabase.build(appContext) }

    /** 用户偏好（外观、名称语言）。 */
    val appSettings: AppSettings by lazy { AppSettings(appContext) }

    val masterRepository: MasterRepository by lazy {
        MasterRepository(httpClient, masterDataDir)
    }

    val masterImporter: MasterImporter by lazy { MasterImporter(database) }

    val syncManager: SyncManager by lazy {
        SyncManager(appContext, masterRepository, database, masterImporter)
    }

    /**
     * 卡牌/角色读取。
     *
     * 卡牌是唯一「读取逻辑值得单独成层」的模块：它要解析裁剪后的 JSON、
     * 拼角色名、算特训前后数值、做内存筛选排序，塞进通用表浏览器里会很别扭。
     */
    val cardRepository: CardRepository by lazy { CardRepository(database.masterDao(), appScope) }

    /**
     * 首页各区块（最新歌曲 / 最新活动 / 生日）。三张表都已在快照里，
     * 所以它不需要新增数据，只是把已有数据组装成首页要的形状。
     */
    val homeRepository: HomeRepository by lazy { HomeRepository(database.masterDao()) }

    /**
     * 歌曲详情（资料页）。
     *
     * 和 [cardRepository] 不同，它**不养常驻缓存**：详情页是按需打开的单首页面，
     * 每次只关心一首歌，用一次性的挂载查询更简单（理由写在 MusicRepository 的注释里）。
     * 阶段 3 的播放器会需要常驻的曲库流，那时再按需加。
     */
    val musicRepository: MusicRepository by lazy { MusicRepository(database.masterDao()) }

    /**
     * 歌词。
     *
     * 和别的模块不同，它**不来自游戏 master data，也不进内置快照**：
     * 词条来自社区（Sekaipedia，CC BY-SA 4.0），是持续在补的，冻结进 APK 反而会过时。
     * 所以走「联网取 + 按 revision 落磁盘缓存」，缓存命中就零流量、断网也能看已看过的词。
     */
    val lyricsRepository: LyricsRepository by lazy {
        LyricsRepository(appContext, httpClient)
    }

    /**
     * 播放器工厂。
     *
     * 整个进程只该有一个：它持有共享的 [androidx.media3.datasource.cache.SimpleCache]
     * （同一个缓存目录只能有一个实例，它会锁目录）。
     */
    val musicPlayerFactory: MusicPlayerFactory by lazy {
        MusicPlayerFactory(appContext, httpClient)
    }

    /**
     * **全局播放器**（阶段 6 起）。
     *
     * ⚠️ 从"页面级"改成全局是刻意的：底部状态栏要在离开歌曲详情页之后继续显示、
     * 继续控制，播放器就不能跟着页面一起销毁。
     *
     * ⚠️ ExoPlayer 必须在**带 Looper 的线程**上创建，所以这个懒加载只能从主线程触发。
     * `AppContainer` 建在 `Application.onCreate`，播放器由界面首次读取时创建 —— 都满足。
     */
    val musicPlayer: MusicPlayer by lazy { musicPlayerFactory.create() }

    /**
     * 剧情模块。直接复用共享的 [httpClient]：正文（一话 60~158 KB）在线取，
     * 不走内置快照 —— 全部内置会有几十 MB。
     */
    val storyRepository: StoryRepository by lazy {
        StoryRepository(database.masterDao(), httpClient)
    }

    /** 内置数据快照（打包在 APK 的 assets 里）。 */
    val bundledPack: BundledPack by lazy {
        BundledPack(appContext, database, masterImporter, masterRepository)
    }

    private val _bundledState = MutableStateFlow<BundledState>(BundledState.Unknown)

    /** 内置快照的导入进度。首页据此显示一条不打扰的提示。 */
    val bundledState: StateFlow<BundledState> = _bundledState.asStateFlow()

    /**
     * 启动时调用一次：需要的话在**后台**把内置快照导进本地数据库。
     *
     * 刻意不阻塞任何界面：首页照常渲染，导入完成的表会通过 Room 的 Flow 自动填充到列表上。
     * 所以用户看到的是「打开就有内容」，而不是「请稍候，正在准备数据」。
     */
    /**
     * 启动时**静默增量同步**一次。
     *
     * 这是「数据能不能跟上活动更新」的关键：原来的同步只有「更多 → 数据同步」里
     * 那个按钮才会跑，用户不点就永远拿不到新活动 —— 而剧情列表完全来自 master data，
     * 没有列表就没有入口，正文再新也读不到。
     *
     * 为什么敢在启动时跑：
     *  - [SyncManager.runSync] 会**先比对版本号**，一致就直接跳过、一个文件都不下
     *    （见 SyncManager 里「数据已是最新…本次未下载任何文件」那条分支）；
     *  - 显式传 `wifiOnly = false`：只在**版本真的变了**时才会走到下载，那时用流量也合理；
     *    想严格省流量的话可以在设置里加开关（本轮没做）。
     *  - 失败/离线都只是往日志里写一行，不影响任何界面。
     */
    private var autoSyncStarted = false
    fun autoSyncOnStart(scope: CoroutineScope) {
        if (autoSyncStarted) return
        autoSyncStarted = true
        scope.launch {
            // ⚠️ **必须等内置导入结束再同步**。原来这两个是并行启动的，后果是：
            // 同步第一步做版本比对时，内置导入还没写版本号（它只在**导入结束时**才写），
            // 于是判定「数据不是最新」→ 把刚刚才从 APK 导进去的那十来 MB 又从网上完整下载一遍
            // 再重新导入一次。内置数据**故意不写 ETag**（见 BundledPack.install），
            // 所以那一轮连一个 304 都命中不了 —— 用户看到的就是「第一次打开同步半天」。
            //
            // 串起来之后：导入完成 → 版本号已写好 → 下面的 runSync 走
            // 「数据已是最新（master version …），本次未下载任何文件」分支 → **首次启动零下载**。
            // 保证内置导入一定先跑：即使将来有人把两个方法的调用顺序写反了，
            // 这里也会自己把导入任务建起来，而不会退化成「不等导入就同步」。
            val install = bundledInstallJob ?: ensureBundledDataInstalled(scope)
            runCatching { install.await() }
            runCatching {
                syncManager.runSync(
                    wifiOnly = false,
                    overlayScope = SyncManager.OverlayScope.SMALL_ONLY,
                )
            }
        }
    }

    private var bundledInstallJob: Deferred<Unit>? = null

    /**
     * 需要的话在**后台**把内置快照导进本地数据库，并返回这个任务的句柄。
     *
     * 刻意不阻塞任何界面：首页照常渲染，导入完成的表会通过 Room 的 Flow 自动填充到列表上。
     * 所以用户看到的是「打开就有内容」，而不是「请稍候，正在准备数据」。
     *
     * 返回 [Deferred] 是为了让 [autoSyncOnStart] 能等它结束（见那里的说明）——
     * 这也是「首次打开不做冗余下载」的关键。
     */
    fun ensureBundledDataInstalled(scope: CoroutineScope): Deferred<Unit> {
        bundledInstallJob?.let { return it }
        val job = scope.async { installBundledData() }
        bundledInstallJob = job
        return job
    }

    private suspend fun installBundledData() {
        val pack = bundledPack
        if (!pack.isBundled) {
            // 开发期可能没跑 tools/datapack/build.ps1，这时退回在线同步即可
            _bundledState.value = BundledState.NotBundled
            return
        }
        if (!pack.needsInstall()) {
            _bundledState.value = BundledState.Ready
            return
        }
        _bundledState.value = BundledState.Installing(0, 0, "")
        val result = runCatching {
            pack.install { done, total, table ->
                _bundledState.value = BundledState.Installing(done, total, table)
            }
        }
        _bundledState.value = result.fold(
            onSuccess = {
                // ⚠️ **单张表导入失败原来是完全静默的**：`BundledPack.failures` 只是记着，
                // 没有任何地方显示。结果是某张表（unitStories）几轮都导不进去、
                // 对应界面整块空白，却只有去翻「全部数据表」才看得到「未下载」。
                // 现在把失败一并报出来，走首页那条现成的失败横幅。
                val failed = pack.failures.toList()
                if (failed.isNotEmpty()) {
                    BundledState.Failed(
                        "有 ${failed.size} 张表导入失败（对应界面会是空的）：" +
                            failed.joinToString("；") { it.take(80) },
                    )
                } else {
                    BundledState.Ready
                }
            },
            onFailure = {
                BundledState.Failed(it.message ?: it::class.simpleName ?: "未知错误")
            },
        )
    }
}
