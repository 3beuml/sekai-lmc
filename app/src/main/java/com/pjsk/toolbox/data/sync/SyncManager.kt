package com.pjsk.toolbox.data.sync

import android.content.Context
import android.util.Log
import com.pjsk.toolbox.data.db.AppDatabase
import com.pjsk.toolbox.data.db.SyncStateEntity
import com.pjsk.toolbox.data.db.VersionStateEntity
import com.pjsk.toolbox.data.remote.DataModule
import com.pjsk.toolbox.data.remote.MasterRepository
import com.pjsk.toolbox.data.remote.ServerRegion
import com.pjsk.toolbox.data.remote.TableCatalog
import com.pjsk.toolbox.data.remote.TableSpec
import com.pjsk.toolbox.util.NetworkUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 同步编排：版本探测 → 条件下载 → 流式导入 → 应用中文名叠加层 → 记录状态。
 *
 * 更新策略（针对「GitHub Pages 没有版本清单」这一现实）：
 *  1. 先探测最新 commit 里的版本号（1 次请求）；
 *  2. 版本号与本机记录一致时，默认直接跳过全部下载（`force = false`）；
 *  3. 否则逐表发条件请求，命中 304 的表零下载；
 *  4. 成功导入后删除原始 JSON，只保留数据库内容。
 */
class SyncManager(
    private val context: Context,
    private val repository: MasterRepository,
    private val db: AppDatabase,
    private val importer: MasterImporter,
) {

    enum class Phase { PENDING, DOWNLOADING, IMPORTING, OVERLAY, DONE, NOT_MODIFIED, FAILED, SKIPPED }

    /** 中文名叠加层的应用范围。 */
    enum class OverlayScope {
        /** 不下载简中数据。 */
        NONE,

        /** 只给体积小的表做中文化（默认）。 */
        SMALL_ONLY,

        /** 全部表都做，包括 34 MB 的 cards.json / 46 MB 的 gachas.json。 */
        ALL,
    }

    data class TableState(
        val spec: TableSpec,
        val phase: Phase = Phase.PENDING,
        val read: Long = 0L,
        val total: Long = 0L,
        val rows: Int = 0,
        val message: String? = null,
        val overlayApplied: Int = 0,
    ) {
        val progress: Float
            get() = if (total > 0L) (read.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
    }

    data class UiState(
        val region: ServerRegion = ServerRegion.DEFAULT,
        val selectedModules: Set<DataModule> = DEFAULT_MODULES,
        val running: Boolean = false,
        val currentTable: String? = null,
        val remoteVersion: MasterRepository.VersionInfo? = null,
        val localVersion: String? = null,
        val versionChecked: Boolean = false,
        val tables: Map<String, TableState> = emptyMap(),
        val log: List<String> = emptyList(),
    ) {
        /** 已选模块的总体积（用于在按钮上提示用户会下载多少）。 */
        val selectedBytes: Long get() = TableCatalog.totalBytes(selectedModules)

        val doneCount: Int get() = tables.values.count { it.phase == Phase.DONE || it.phase == Phase.NOT_MODIFIED }
    }

    private val prefs = context.getSharedPreferences("sync", Context.MODE_PRIVATE)

    /**
     * SyncManager 是随 App 存活的单例，所以用它自己的 scope 做「切区服后清库」这类
     * 不依赖界面生命周期的工作。
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(
        UiState(
            region = ServerRegion.fromId(prefs.getString(KEY_REGION, null) ?: ServerRegion.DEFAULT.id),
            selectedModules = prefs.getStringSet(KEY_MODULES, null)
                ?.mapNotNull { id -> DataModule.entries.firstOrNull { it.id == id } }
                ?.toSet()
                ?: DEFAULT_MODULES,
        ),
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    /**
     * 切换区服。
     *
     * ⚠️ 会**清空本地已下载的 master data**。原因是 `master_rows` 表刻意不含区服列
     * （统一存储让同步逻辑只有一份），所以本地数据一次性只属于一个区服。
     * 不清空的话会出现严重后果：切到另一个区服后，条件请求命中 304，
     * App 会显示「已是最新」，但库里的内容其实还是上一个区服的。
     *
     * 代价：切回原区服需要重新同步（首次会完整下载，之后 ETag 生效恢复零下载）。
     */
    fun setRegion(region: ServerRegion) {
        val previous = _state.value.region
        prefs.edit().putString(KEY_REGION, region.id).apply()
        _state.value = _state.value.copy(region = region, versionChecked = false, tables = emptyMap())
        if (previous != region) {
            scope.launch { wipeAllData(reason = "已从「${previous.displayName}」切换到「${region.displayName}」") }
        }
    }

    private suspend fun wipeAllData(reason: String) = withContext(Dispatchers.IO) {
        val dao = db.masterDao()
        TableCatalog.ALL.forEach { dao.deleteTableBlocking(it.fileName) }
        db.syncStateDao().clear()
        log("$reason，本地数据已清空，请重新同步。")
        refreshFromDatabase()
    }

    fun toggleModule(module: DataModule) {
        val current = _state.value.selectedModules.toMutableSet()
        if (!current.remove(module)) current += module
        prefs.edit().putStringSet(KEY_MODULES, current.map { it.id }.toSet()).apply()
        _state.value = _state.value.copy(selectedModules = current)
    }

    /** 读取数据库里记录的同步状态，用于在页面上显示「已下载哪些表」。 */
    suspend fun refreshFromDatabase() = withContext(Dispatchers.IO) {
        val region = _state.value.region
        val stored = db.syncStateDao().all().associateBy { it.tableName }
        val tables = TableCatalog.ALL.associate { spec ->
            val s = stored[spec.fileName]?.takeIf { it.region == region.id }
            spec.fileName to TableState(
                spec = spec,
                phase = if (s != null) Phase.DONE else Phase.PENDING,
                rows = s?.rowCount ?: 0,
            )
        }
        val version = db.versionStateDao().byRegion(region.id)
        _state.value = _state.value.copy(
            tables = tables,
            localVersion = version?.masterVersion,
            remoteVersion = version?.let {
                MasterRepository.VersionInfo(it.masterVersion, it.assetVersion, it.commitDate)
            },
        )
    }

    /** 只探测版本，不下载。 */
    suspend fun checkVersion() {
        val region = _state.value.region
        log("正在检查「${region.displayName}」的数据版本…")
        val result = repository.probeVersion(region)
        result.fold(
            onSuccess = { info ->
                val local = _state.value.localVersion
                log(
                    if (info.masterVersion == null) {
                        "已获取远端提交时间：${info.commitDate ?: "未知"}"
                    } else {
                        "远端 master version = ${info.masterVersion}，本机 = ${local ?: "尚未同步"}"
                    },
                )
                _state.value = _state.value.copy(remoteVersion = info, versionChecked = true)
            },
            onFailure = { e ->
                // 常见原因：GitHub API 未鉴权限速（60 次/小时/IP）或断网
                log("版本检查失败：${e.message}（不影响直接下载，可直接点「开始同步」）")
                _state.value = _state.value.copy(versionChecked = true)
            },
        )
    }

    /**
     * 执行同步。
     *
     * 判定逻辑是 **blob sha**（不是版本号，也不是 ETag）：
     *  1. 取一次仓库文件列表（trees API，1 个请求、无 CDN 缓存）→ 每张表的权威 sha；
     *  2. 与本地记的 sha 比：**一致的整张表跳过，一个请求都不发**；
     *  3. 不一致的下载（带 `?cb=` 绕过 CDN 缓存）→ **写盘前校验 sha** → 通过才入库；
     *  4. 全部通过才写「已同步到 XX 版本」；有失败就**不写**，下次启动继续补。
     *
     * 这套组合解决三个具体问题（都真实发生过）：
     *  - 版本号相同但其实有表变化 → 现在按内容判定，不会漏；
     *  - CDN 给了部署前的旧副本 → sha 对不上，不会覆盖本地数据（旧副本污染不了本地）；
     *  - 拿到旧数据还被记成"已同步" → 只有全部校验通过才记版本，不会卡在旧版本。
     *
     * @param wifiOnly 仅在不计流量的网络下执行（大表加起来可达上百 MB）。
     * @param force 忽略 sha 一致这个快速跳过，强制把所有表重新核对/下载一遍。
     */
    suspend fun runSync(
        wifiOnly: Boolean,
        overlayScope: OverlayScope,
        force: Boolean = false,
    ) = withContext(Dispatchers.IO) {
        if (_state.value.running) {
            log("同步已在进行中，忽略本次请求。")
            return@withContext
        }
        if (wifiOnly && !NetworkUtils.isUnmetered(context)) {
            log("已开启「仅 WiFi 下载」，当前网络不是 Wi-Fi，已取消。")
            return@withContext
        }
        if (!NetworkUtils.isOnline(context)) {
            log("当前无网络连接。")
            return@withContext
        }

        val region = _state.value.region
        val modules = _state.value.selectedModules
        // 同步顺序 = 模块优先级（卡牌 / 歌曲在前，卡池垫底）+ 组内按体积升序。
        // 慢线路上「先把最常用的数据拿到手」远比「按目录一次性铺开」重要：
        // 用户点进卡牌页就该是最新的，而不是等 40 MB 的卡池表下完。
        val specs = TableCatalog.forModules(modules).sortedWith(
            compareBy({ it.module.syncPriority }, { it.approxBytes }),
        )
        if (specs.isEmpty()) {
            log("没有选中任何数据模块。")
            return@withContext
        }

        _state.value = _state.value.copy(running = true, currentTable = null)
        log("开始同步「${region.displayName}」，共 ${specs.size} 张表，约 ${TableCatalog.formatBytes(TableCatalog.totalBytes(modules))}。")

        // 1) 版本探测：**只用于界面显示**（"数据版本 6.8.0.42"），判断更新不用它
        val info = repository.probeVersion(region).getOrNull()
        if (info != null) {
            _state.value = _state.value.copy(remoteVersion = info, versionChecked = true)
        }

        // 2) 取仓库文件列表（权威 sha）。失败就**不猜**：既不能当成"没有更新"，
        //    也不能贸然全量重下（那会在限速时白白耗掉上百 MB）。
        val remoteShas = repository.fetchTreeShas(region).getOrElse { error ->
            val why = error.message ?: error::class.java.simpleName
            log("无法获取远端文件列表（$why），本次跳过同步。数据保持原样，下次启动会再试。")
            Log.i(TAG, "同步中止：取仓库树失败 —— $why")
            _state.value = _state.value.copy(running = false, currentTable = null)
            return@withContext
        }
        Log.i(TAG, "取到仓库文件列表：${remoteShas.size} 个文件，本次核对 ${specs.size} 张表")

        // 3) 逐表判定 + 下载 + 导入（判定规则见 [SyncDecision]，有离线断言钉着）
        val localVersion = db.versionStateDao().byRegion(region.id)?.masterVersion

        var failures = 0
        var skipped = 0
        var updated = 0

        for (spec in specs) {
            val remoteSha = remoteShas[spec.fileName]
            when (SyncDecision.decide(repository.readContentSha(region, spec.fileName), remoteSha, force)) {
                SyncDecision.Action.SKIP_MISSING_REMOTE -> {
                    // 仓库树里没有这张表：可能被上游删/改名了。不动本地数据，只记一笔。
                    skipped++
                    setPhase(spec, TableState(spec = spec, phase = Phase.SKIPPED, message = "远端已无此表"))
                    log("${spec.fileName}：远端文件列表里没有它，已跳过（本地数据保持不变）。")
                    continue
                }

                SyncDecision.Action.SKIP_UNCHANGED -> {
                    skipped++
                    val rows = db.syncStateDao().byTable(spec.fileName)?.rowCount ?: 0
                    setPhase(spec, TableState(spec = spec, phase = Phase.NOT_MODIFIED, rows = rows))
                    continue
                }

                SyncDecision.Action.DOWNLOAD -> Unit
            }

            setPhase(spec, TableState(spec = spec, phase = Phase.DOWNLOADING))
            _state.value = _state.value.copy(currentTable = spec.fileName)

            val progress: (Long, Long) -> Unit = { read, total ->
                updateTable(spec) { it.copy(phase = Phase.DOWNLOADING, read = read, total = total) }
            }

            // 带 `?cb=` 绕过 CDN 缓存，最多试两次（第一次可能撞上仍在缓存的旧副本）
            var download = repository.downloadTable(
                region = region,
                spec = spec,
                expectedSha = remoteSha,
                cacheBust = true,
                onProgress = progress,
            )
            if (download is MasterRepository.DownloadResult.ShaMismatch) {
                log("${spec.fileName}：内容 sha 对不上（多半是 CDN 旧副本），换一次请求重试…")
                download = repository.downloadTable(
                    region = region,
                    spec = spec,
                    expectedSha = remoteSha,
                    cacheBust = true,
                    onProgress = progress,
                )
            }

            when (download) {
                is MasterRepository.DownloadResult.NotModified -> {
                    // 新流程不会发条件请求，所以正常走不到这里；真到了就当成"已是最新"
                    val rows = db.syncStateDao().byTable(spec.fileName)?.rowCount ?: 0
                    setPhase(spec, TableState(spec = spec, phase = Phase.NOT_MODIFIED, rows = rows))
                    skipped++
                }

                is MasterRepository.DownloadResult.ShaMismatch -> {
                    failures++
                    setPhase(
                        spec,
                        TableState(
                            spec = spec,
                            phase = Phase.FAILED,
                            message = "内容校验未通过（CDN 可能仍在给旧副本）",
                        ),
                    )
                    log(
                        "${spec.fileName}：内容 sha 校验两次都没通过（期望 ${download.expected.take(8)}，" +
                            "实得 ${download.actual.take(8)}）。**本地数据保持不变**，稍后再试。",
                    )
                }

                is MasterRepository.DownloadResult.Failed -> {
                    failures++
                    setPhase(
                        spec,
                        TableState(spec = spec, phase = Phase.FAILED, message = download.message),
                    )
                    log("${spec.fileName}：${download.message}")
                    continue
                }
                is MasterRepository.DownloadResult.Downloaded -> {
                    setPhase(
                        spec,
                        TableState(
                            spec = spec,
                            phase = Phase.IMPORTING,
                            read = download.bytes,
                            total = download.bytes,
                        ),
                    )

                    // 注意：这里不能写成 runCatching { ... }.getOrElse { ...; continue }，
                    // 因为 Kotlin 不允许在 lambda 内部对「外部循环」使用 continue。
                    val importAttempt = runCatching {
                        importer.importTable(spec.fileName, download.file) { done ->
                            updateTable(spec) { it.copy(phase = Phase.IMPORTING, rows = done) }
                        }
                    }
                    val rows = importAttempt.getOrNull()

                    if (rows == null) {
                        val reason = importAttempt.exceptionOrNull()?.message ?: "未知错误"
                        failures++
                        setPhase(
                            spec,
                            TableState(spec = spec, phase = Phase.FAILED, message = "解析失败：$reason"),
                        )
                        log("${spec.fileName}：解析失败 → $reason")
                        repository.removeCached(region, spec.fileName)
                        continue
                    }

                    val overlayCount = if (spec.cnOverlay && shouldOverlay(spec, overlayScope)) {
                        runCatching { applyOverlayFor(spec, region) }.getOrDefault(0)
                    } else {
                        0
                    }

                    db.syncStateDao().upsert(
                        SyncStateEntity(
                            tableName = spec.fileName,
                            region = region.id,
                            etag = download.etag,
                            lastModified = download.lastModified,
                            rowCount = rows,
                            updatedAt = System.currentTimeMillis(),
                            module = spec.module.id,
                        ),
                    )
                    // 入库成功即删除原始 JSON，避免长期占用上百 MB 空间
                    // （`.sha` 与 `.etag` 会保留，它们让下次同步零请求）
                    repository.removeCached(region, spec.fileName)
                    updated++
                    setPhase(
                        spec,
                        TableState(
                            spec = spec,
                            phase = Phase.DONE,
                            rows = rows,
                            read = download.bytes,
                            total = download.bytes,
                            overlayApplied = overlayCount,
                        ),
                    )
                    log(
                        buildString {
                            append("${spec.fileName}：导入 $rows 行")
                            if (overlayCount > 0) append("，其中 $overlayCount 行补上了中文名")
                            append("（${TableCatalog.formatBytes(download.bytes)}）")
                        },
                    )
                }
            }
        }

        // 4) 记录版本 —— **只有一次都没失败才记**。
        //
        // 这一步以前是无条件的，后果很严重：只要有一张表拿到的还是 CDN 旧副本（或下载中断），
        // 它也会把"已同步到新版本"写进去，于是之后每次启动都判定"版本一致"直接跳过，
        // 数据就一直停在旧版本，直到下次版本号变化（约一周）。
        // 现在：有失败就不写版本，下次启动会继续比对 sha 并把没同步好的表补上。
        val allVerified = failures == 0
        if (allVerified && info?.masterVersion != null) {
            db.versionStateDao().upsert(
                VersionStateEntity(
                    region = region.id,
                    masterVersion = info.masterVersion,
                    assetVersion = info.assetVersion,
                    commitDate = info.commitDate,
                    checkedAt = System.currentTimeMillis(),
                ),
            )
        }
        Log.i(
            TAG,
            "同步结束：跳过 $skipped 张（sha 一致/远端无此表）、更新 $updated 张、失败 $failures 张" +
                "；记录版本=${allVerified && info?.masterVersion != null}",
        )

        log(
            when {
                failures > 0 ->
                    "同步结束：更新 $updated 张，跳过 $skipped 张，**$failures 张失败**。" +
                        "失败的会在下次同步自动重试（已完成的表按 sha 跳过，不会重下）。"
                updated > 0 -> "同步完成 ✅ 更新 $updated 张表，其余 $skipped 张内容未变（sha 一致，零请求）。"
                else -> "数据已是最新：${specs.size} 张表的内容 sha 与远端完全一致，本次未下载任何文件。"
            },
        )
        _state.value = _state.value.copy(
            running = false,
            currentTable = null,
            localVersion = info?.masterVersion ?: localVersion,
        )
        refreshFromDatabase()
    }

    /** 删除某个模块的本地数据（含中文名叠加层结果）。 */
    suspend fun removeModule(module: DataModule) = withContext(Dispatchers.IO) {
        val dao = db.masterDao()
        TableCatalog.forModules(setOf(module)).forEach { spec ->
            dao.deleteTableBlocking(spec.fileName)
            db.syncStateDao().delete(spec.fileName)
        }
        log("已删除「${module.displayName}」模块的本地数据。")
        refreshFromDatabase()
    }

    private suspend fun applyOverlayFor(spec: TableSpec, region: ServerRegion): Int {
        if (region == ServerRegion.CN) return 0 // 主数据本身就是简中，无需叠加
        val cn = ServerRegion.CN
        val result = repository.downloadTable(cn, spec) { read, total ->
            updateTable(spec) { it.copy(phase = Phase.OVERLAY, read = read, total = total) }
        }
        return when (result) {
            is MasterRepository.DownloadResult.Downloaded -> {
                val matched = runCatching {
                    importer.applyOverlay(spec.fileName, result.file) { done ->
                        updateTable(spec) { it.copy(phase = Phase.OVERLAY, rows = done) }
                    }
                }.getOrDefault(0)
                repository.removeCached(cn, spec.fileName)
                matched
            }

            else -> 0
        }
    }

    private fun shouldOverlay(spec: TableSpec, scope: OverlayScope): Boolean = when (scope) {
        OverlayScope.NONE -> false
        OverlayScope.SMALL_ONLY -> !importer.isLargeOverlay(spec.approxBytes)
        OverlayScope.ALL -> true
    }

    private fun setPhase(spec: TableSpec, state: TableState) {
        _state.value = _state.value.copy(tables = _state.value.tables + (spec.fileName to state))
    }

    private fun updateTable(spec: TableSpec, transform: (TableState) -> TableState) {
        val current = _state.value.tables[spec.fileName] ?: TableState(spec = spec)
        _state.value = _state.value.copy(tables = _state.value.tables + (spec.fileName to transform(current)))
    }

    private fun log(message: String) {
        // 只保留最近 200 条，避免长时间同步把内存撑大
        val next = (_state.value.log + message).takeLast(200)
        _state.value = _state.value.copy(log = next)
    }

    companion object {
        /** logcat 标签：显式启动同步 / 跳过同步的判据都打在这里，便于测冷启动时从外部核对。 */
        private const val TAG = "SyncManager"

        private const val KEY_REGION = "region"
        private const val KEY_MODULES = "modules"

        /**
         * 默认勾选的模块。
         *
         * 现在**包含卡牌**：卡牌是这个 App 的主入口之一，用户即使用内置快照能用，
         * 也会希望新卡上线后列表是新的。代价是版本真的变化时会多下约 35 MB
         * （`cards.json` 33.4 MB + 卡牌剧情 1.6 MB），但那只在**版本变化时**才发生
         * —— 平时同步靠版本号与条件请求，一个文件都不下。
         *
         * 仍然不默认勾选的是**扭蛋**（46 MB 的 `gachas.json`，只在首页看一眼）与
         * **贴纸**（目前没有对应的图鉴界面）。
         */
        val DEFAULT_MODULES: Set<DataModule> = setOf(
            DataModule.CARD,
            DataModule.MUSIC,
            DataModule.CHARACTER,
            DataModule.EVENT,
        )
    }
}
