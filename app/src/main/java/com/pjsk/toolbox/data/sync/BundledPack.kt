package com.pjsk.toolbox.data.sync

import android.content.Context
import com.pjsk.toolbox.data.db.AppDatabase
import com.pjsk.toolbox.data.db.SyncStateEntity
import com.pjsk.toolbox.data.db.VersionStateEntity
import com.pjsk.toolbox.data.remote.ServerRegion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.InputStreamReader

/**
 * 导入**内置数据快照**（打包在 APK 的 `assets/datapack/` 里）。
 *
 * 为什么要有它：用户体验要求「打开 App 就能看到内容」。如果第一屏等用户自己去同步，
 * 手机上从 GitHub Pages 拉数据可能要好几个小时 —— 那等于第一屏永远看不到东西。
 * 所以数据在**构建时**（`tools/datapack/build.ps1`）就裁好、连同中文名一起打进 APK。
 *
 * 快照里有什么：
 *  - `<表名>`：裁剪后的紧凑 JSON 数组（例如 `cards.json` 从 33.4 MB 压到 1.43 MB）
 *  - `overlay/<表名>`：`{ "id": "中文名" }`，装完就是中文，飞行模式也是中文
 *  - `manifest.json`：快照元信息（版本号、每张表的行数）
 *
 * 导入是**流式**的（逐个元素解析 + 分批写库），所以十几 MB 的数据也不会 OOM。
 */
class BundledPack(
    private val context: Context,
    private val db: AppDatabase,
    private val importer: MasterImporter,
) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** APK 里到底有没有打包数据（没有的话就退回纯在线同步）。 */
    val isBundled: Boolean
        get() = runCatching { context.assets.open(MANIFEST).close() }.isSuccess

    /**
     * 需不需要导入。
     *
     * 判据是「**内置清单里有哪张表本地还没有同步记录**」——也就是**缺表就补**。
     *
     * ⚠️ 这里原来是「本地一条同步记录都没有」（即只认全新安装），有两个后果：
     *  1. 老用户升级 App 后，**新打包进快照的表一张也进不去**（加 materials / unitStories
     *     那几轮就是靠 `pm clear` 才验证到的）；
     *  2. 某张表**导入失败了**（比如拿不到主键），它会一直缺失且**永不重试**。
     *
     * 配合 [install] 里「已有数据的表跳过」，这样既不会覆盖用户在线同步到的更新数据，
     * 又能自己把缺的表补上。
     */
    suspend fun needsInstall(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val manifest = readManifest() ?: return@runCatching false
            manifest.entries.any { entry ->
                val file = entry.file ?: return@any false
                // ⚠️ **数实际行数，不要信 `sync_state.rowCount`。**
                //
                // 那份记录写的是**清单里声称的行数**（`entry.rows`），不是真正导进去的行数。
                // 踩过的坑：`unitStories.json` 因为主键取不到数字，`MasterImporter` 把 6 行
                // **全部静默跳过**（不抛异常），于是记录写着 6 行、实际 0 行；
                // 判据读那条记录就认为「这张表没问题」，跳过 → 修好的主键永远没机会生效，
                // 主线剧情一直是空的。数实际行数就没这个坑。
                db.masterDao().count(file) == 0
            }
        }.getOrDefault(false)
    }

    /**
     * 执行导入。
     *
     * @param onProgress (已完成表数, 总表数, 当前表名)。表是按「首页优先」的顺序导入的，
     *   所以首页需要的表会最先就绪，用户很快就能看到第一屏内容。
     */
    suspend fun install(
        onProgress: (done: Int, total: Int, table: String) -> Unit = { _, _, _ -> },
    ): Int = withContext(Dispatchers.IO) {
        val manifest = readManifest() ?: return@withContext 0
        val entries = orderHomeFirst(manifest.entries)
        val now = System.currentTimeMillis()

        entries.forEachIndexed { index, entry ->
            val fileName = entry.file ?: return@forEachIndexed
            onProgress(index, entries.size, fileName)

            // **表里已经有数据就跳过**：内置快照可能比用户在线同步过的数据旧，不能覆盖。
            // 判据同样是**数实际行数**（见 needsInstall 的说明：同步记录里的 rowCount 不可信）。
            if (db.masterDao().count(fileName) > 0) return@forEachIndexed

            val dataPath = "$DIR/$fileName"
            val opened = runCatching {
                context.assets.open(dataPath).use { input ->
                    InputStreamReader(input, Charsets.UTF_8).use { reader ->
                        importer.importTable(fileName, reader)
                    }
                }
            }
            // 单张表失败不该让整个首次启动废掉：记下来继续导下一张
            if (opened.isFailure) {
                failures += "$fileName: ${opened.exceptionOrNull()?.message}"
                return@forEachIndexed
            }

            // 中文名叠加层（只在打包时确实生成了的情况下）
            if (entry.hasOverlay) {
                runCatching {
                    context.assets.open("$DIR/overlay/$fileName").use { input ->
                        InputStreamReader(input, Charsets.UTF_8).use { reader ->
                            importer.applyOverlay(fileName, reader)
                        }
                    }
                }.onFailure { failures += "$fileName 中文名: ${it.message}" }
            }

            // 记一条同步状态，让「数据同步」页能正确显示哪些模块已经有数据。
            // ETag 故意留空：内置数据没有条件请求的凭据，下次在线同步会重新校验一遍。
            db.syncStateDao().upsert(
                SyncStateEntity(
                    tableName = fileName,
                    region = ServerRegion.DEFAULT.id,
                    etag = null,
                    lastModified = null,
                    rowCount = entry.rows,
                    updatedAt = now,
                    module = entry.module.orEmpty(),
                ),
            )
        }

        onProgress(entries.size, entries.size, "")

        // 记下快照对应的官方版本，之后在线同步可以据此「版本没变就跳过」。
        manifest.snapshot?.let { snapshot ->
            db.versionStateDao().upsert(
                VersionStateEntity(
                    region = ServerRegion.DEFAULT.id,
                    masterVersion = snapshot.masterVersion,
                    assetVersion = snapshot.assetVersion,
                    commitDate = snapshot.versionCommitAt,
                    checkedAt = now,
                ),
            )
        }
        entries.size
    }

    /** 导入过程中单张表的失败信息（不中断整体导入，但事后可查）。 */
    val failures: MutableList<String> = mutableListOf()

    /** 快照里记的官方版本，UI 上可以显示「数据版本」。 */
    suspend fun snapshotVersion(): String? = withContext(Dispatchers.IO) {
        readManifest()?.snapshot?.masterVersion
    }

    // ─────────────────────────────────────────────────────────────

    private fun readManifest(): PackManifest? = runCatching {
        val text = context.assets.open(MANIFEST).use { it.readBytes().toString(Charsets.UTF_8) }
        val root = json.parseToJsonElement(text) as? JsonObject ?: return@runCatching null

        val snapshot = (root["snapshot"] as? JsonObject)?.let { obj ->
            PackSnapshot(
                generatedAt = obj.str("generatedAt"),
                masterVersion = obj.str("masterVersion"),
                assetVersion = obj.str("assetVersion"),
                versionCommitAt = obj.str("versionCommitAt"),
            )
        }
        val entries = (root["tables"] as? JsonArray).orEmpty().mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val file = obj.str("file") ?: return@mapNotNull null
            PackEntry(
                file = file,
                module = obj.str("module"),
                rows = obj.str("rows")?.toIntOrNull() ?: 0,
                hasOverlay = obj.str("hasOverlay") == "true",
            )
        }
        PackManifest(snapshot = snapshot, entries = entries)
    }.getOrNull()

    /**
     * 按「首页优先」排序。
     *
     * 首页要显示当前活动、最新卡面、最新歌曲、角色生日，这些对应的表先导，
     * 用户打开 App 一两秒就能看到第一屏有内容；图鉴用的其余表在后台继续导。
     */
    private fun orderHomeFirst(entries: List<PackEntry>): List<PackEntry> {
        val priority = HOME_PRIORITY.withIndex().associate { (index, name) -> name to index }
        return entries.sortedBy { priority[it.file] ?: Int.MAX_VALUE }
    }

    private fun JsonObject.str(key: String): String? =
        (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.content

    private data class PackManifest(val snapshot: PackSnapshot?, val entries: List<PackEntry>)

    private data class PackSnapshot(
        val generatedAt: String?,
        val masterVersion: String?,
        val assetVersion: String?,
        val versionCommitAt: String?,
    )

    private data class PackEntry(
        val file: String?,
        val module: String?,
        val rows: Int,
        val hasOverlay: Boolean,
    )

    private companion object {
        const val DIR = "datapack"
        const val MANIFEST = "$DIR/manifest.json"

        /** 首页用得上的表，放最前面导。 */
        val HOME_PRIORITY = listOf(
            "cards.json",
            "cardRarities.json",
            "cardSupplies.json",
            "skills.json",
            "gameCharacters.json",
            "gameCharacterUnits.json",
            "characterProfiles.json",
            "events.json",
            "eventCards.json",
            "eventMusics.json",
            "musics.json",
            "musicDifficulties.json",
        )
    }
}

/** 内置快照的导入状态，供首页显示一条不打扰的进度提示。 */
sealed interface BundledState {
    /** 还没检查。 */
    data object Unknown : BundledState

    /** APK 里没有打包数据（开发期可能没跑打包脚本），只能靠在线同步。 */
    data object NotBundled : BundledState

    /** 本地已有数据或已导完。 */
    data object Ready : BundledState

    data class Installing(val done: Int, val total: Int, val table: String) : BundledState

    data class Failed(val message: String) : BundledState
}
