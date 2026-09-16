package com.pjsk.toolbox.data.sync

import android.content.Context
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
     * @param wifiOnly 仅在不计流量的网络下执行（大表加起来可达上百 MB）。
     * @param force 即使版本号未变化也强制走一遍条件请求；false 时版本一致会直接跳过。
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
        val specs = TableCatalog.forModules(modules)
        if (specs.isEmpty()) {
            log("没有选中任何数据模块。")
            return@withContext
        }

        _state.value = _state.value.copy(running = true, currentTable = null)
        log("开始同步「${region.displayName}」，共 ${specs.size} 张表，约 ${TableCatalog.formatBytes(TableCatalog.totalBytes(modules))}。")

        // 1) 版本探测（失败不阻断）
        val info = repository.probeVersion(region).getOrNull()
        if (info != null) {
            _state.value = _state.value.copy(remoteVersion = info, versionChecked = true)
        }

        // 2) 版本一致时快速跳过
        val localVersion = db.versionStateDao().byRegion(region.id)?.masterVersion
        val alreadySynced = db.syncStateDao().all()
            .filter { it.region == region.id }
            .map { it.tableName }
            .toSet()
        val allTablesPresent = specs.all { it.fileName in alreadySynced }
        if (!force && info?.masterVersion != null && info.masterVersion == localVersion && allTablesPresent) {
            log("数据已是最新（master version ${info.masterVersion}），本次未下载任何文件。")
            _state.value = _state.value.copy(running = false, currentTable = null, localVersion = localVersion)
            return@withContext
        }

        var failures = 0

        // 3) 逐表下载 + 导入
        for (spec in specs) {
            setPhase(spec, TableState(spec = spec, phase = Phase.DOWNLOADING))
            _state.value = _state.value.copy(currentTable = spec.fileName)

            val progress: (Long, Long) -> Unit = { read, total ->
                updateTable(spec) { it.copy(phase = Phase.DOWNLOADING, read = read, total = total) }
            }

            var download = repository.downloadTable(region, spec, onProgress = progress)

            // 一致性保护：收到 304 说明远端没变，但「本地 ETag 还在、数据库里却没这张表」
            // 是可能发生的（例如数据库被 fallbackToDestructiveMigration 重建过）。
            // 这种情况下必须忽略 ETag 强制重下，否则会误报「已是最新」而实际无数据可用。
            if (download is MasterRepository.DownloadResult.NotModified) {
                val local = db.syncStateDao().byTable(spec.fileName)
                if (local == null || local.rowCount == 0) {
                    log("${spec.fileName}：收到 304 但本地无数据，改为强制完整下载。")
                    download = repository.downloadTable(region, spec, forceFull = true, onProgress = progress)
                }
            }

            when (download) {
                is MasterRepository.DownloadResult.NotModified -> {
                    val rows = db.syncStateDao().byTable(spec.fileName)?.rowCount ?: 0
                    setPhase(spec, TableState(spec = spec, phase = Phase.NOT_MODIFIED, rows = rows))
                    log("${spec.fileName}：远端未变化（304），已跳过。")
                    continue
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
                    repository.removeCached(region, spec.fileName)
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

        // 4) 记录版本
        if (info?.masterVersion != null) {
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

        log(
            if (failures == 0) "同步完成 ✅ 全部表已就绪。"
            else "同步结束，但有 $failures 张表失败（可直接重试，已成功的表会走 304 跳过）。",
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
        private const val KEY_REGION = "region"
        private const val KEY_MODULES = "modules"

        /**
         * 默认勾选的模块：只包含「体积可控」的四块。
         * 卡牌(35MB+)、扭蛋(47MB)、服装(56MB) 默认不勾，让用户明确知道要花多少流量。
         */
        val DEFAULT_MODULES: Set<DataModule> = setOf(
            DataModule.CHARACTER,
            DataModule.MUSIC,
            DataModule.EVENT,
            DataModule.STICKER,
        )
    }
}
