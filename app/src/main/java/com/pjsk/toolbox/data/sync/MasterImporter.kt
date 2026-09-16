package com.pjsk.toolbox.data.sync

import com.pjsk.toolbox.data.db.AppDatabase
import com.pjsk.toolbox.data.db.MasterRowEntity
import android.util.Log
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.io.File
import java.io.Reader

/**
 * 把下载好的 master data JSON 导入数据库。
 *
 * 关键点：
 *  - 用 [JsonArrayStreamer] 流式切分顶层数组，**不整体载入**，所以 54 MB 的表也不会 OOM；
 *  - 每 [BATCH_SIZE] 行写一次库，`cards.json` 约 1 万行、`gachas.json` 约千行，批量写入很快；
 *  - 简中叠加层只更新 `nameZh` 列，不覆盖原始 `data`，因此「日服为主 + 中文名」可以增量维护。
 */
class MasterImporter(private val db: AppDatabase) {

    private val dao = db.masterDao()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** 全量导入一张表（先清空旧数据）。返回导入行数。 */
    fun importTable(
        tableName: String,
        file: File,
        onProgress: (rows: Int) -> Unit = {},
    ): Int = file.bufferedReader(Charsets.UTF_8).use { reader ->
        importTable(tableName, reader, onProgress)
    }

    /**
     * 从任意 [Reader] 导入。
     *
     * 存在的理由：**内置数据快照打包在 APK 的 assets 里**，只能拿到 InputStream、
     * 没有真实文件路径。有了这个重载就不用先落临时文件再导入。
     */
    fun importTable(
        tableName: String,
        reader: Reader,
        onProgress: (rows: Int) -> Unit = {},
    ): Int {
        val schema = TableSchemas[tableName]
        var count = 0
        val batch = ArrayList<MasterRowEntity>(BATCH_SIZE)
        val started = System.nanoTime()
        var insertNanos = 0L

        dao.deleteTableBlocking(tableName)

        JsonArrayStreamer.forEachElement(reader) { elementText ->
            val obj = runCatching { json.parseToJsonElement(elementText) as? JsonObject }.getOrNull()
                ?: return@forEachElement
            val id = obj.firstLong(schema.idKeys)?.toInt() ?: return@forEachElement

            // 名称与排序值一律从**原始行**取，因为裁剪后的 JSON 里字段名可能已经改短
            // （cards.json 的 prefix → 不保留，assetbundleName → bundle）。
            val projector = schema.projector
            val storedText: String = if (projector == null) {
                // 快速路径：不裁剪，直接用原始文本，省掉「解析→重新序列化」的开销。
                // 54 MB 的 costume3ds.json 靠这条路径才能保持导入速度。
                elementText
            } else {
                projector.project(obj)?.toString() ?: return@forEachElement
            }

            batch += MasterRowEntity(
                tableName = tableName,
                id = id,
                name = schema.extractName(obj),
                nameZh = null,
                sortValue = obj.firstLong(schema.sortKeyNames) ?: id.toLong(),
                data = storedText,
            )
            count++

            if (batch.size >= BATCH_SIZE) {
                val t = System.nanoTime()
                dao.insertRowsBlocking(batch.toList())
                insertNanos += System.nanoTime() - t
                batch.clear()
                onProgress(count)
            }
        }
        if (batch.isNotEmpty()) {
            val t = System.nanoTime()
            dao.insertRowsBlocking(batch.toList())
            insertNanos += System.nanoTime() - t
        }
        onProgress(count)

        val totalMs = (System.nanoTime() - started) / 1_000_000
        if (totalMs >= SLOW_LOG_MS) {
            Log.i(
                TAG,
                "$tableName 导入较慢：$count 行 / ${totalMs}ms" +
                    "（其中入库 ${insertNanos / 1_000_000}ms，其余是 JSON 解析与字段裁剪）",
            )
        }
        return count
    }

    /**
     * 应用简中名的叠加层：按 id 把中文名写进已有行的 `nameZh`。
     *
     * 实测依据：日服 `musics.json` 没有 `infos` 字段，简中版的同一个 id 才有
     * `infos[0].title = "将手"`。简中进度落后于日服，所以匹配不到的新内容会保持
     * `nameZh = null`，UI 侧自动回退到日文原名 —— 这正是我们要的行为。
     */
    fun applyOverlay(
        tableName: String,
        file: File,
        onProgress: (rows: Int) -> Unit = {},
    ): Int = file.bufferedReader(Charsets.UTF_8).use { reader ->
        applyOverlay(tableName, reader, onProgress)
    }

    /** 从任意 [Reader] 应用中文名叠加层（内置快照的中文名也在 assets 里）。 */
    fun applyOverlay(
        tableName: String,
        reader: Reader,
        onProgress: (rows: Int) -> Unit = {},
    ): Int {
        val schema = TableSchemas[tableName]
        var matched = 0
        val batch = ArrayList<Pair<Int, String>>(OVERLAY_BATCH_SIZE)
        val started = System.nanoTime()

        // ⚠️ 这里原来是**逐行** `dao.existsBlocking(tableName, id)`，也就是每写一个中文名
        // 先查一次库（简中名共 8,168 条 → 8 千次 SELECT），而叠加层本来就不该是瓶颈。
        // 现在一次性把这张表的 id 读进内存（最多几千个 Int），再在内存里判断，
        // 语义不变（仍然只更新确实存在的行，避免为简中独有的 id 造孤儿数据），查询次数从 N 降到 1。
        val existingIds = dao.allIdsBlocking(tableName).toHashSet()

        fun flush() {
            if (batch.isEmpty()) return
            db.runInTransaction {
                batch.forEach { (id, zh) -> dao.updateNameZhBlocking(tableName, id, zh) }
            }
            batch.clear()
        }

        JsonArrayStreamer.forEachElement(reader) { elementText ->
            val obj = runCatching { json.parseToJsonElement(elementText) as? JsonObject }.getOrNull()
                ?: return@forEachElement
            val id = obj.firstLong(schema.idKeys)?.toInt() ?: return@forEachElement

            val zh = schema.extractOverlayName(obj) ?: return@forEachElement

            // 只更新确实存在的行，避免为简中独有的 id 产生孤儿数据
            if (id !in existingIds) return@forEachElement

            batch += id to zh
            matched++
            if (batch.size >= OVERLAY_BATCH_SIZE) {
                flush()
                onProgress(matched)
            }
        }
        flush()
        onProgress(matched)

        val totalMs = (System.nanoTime() - started) / 1_000_000
        if (totalMs >= SLOW_LOG_MS) {
            Log.i(TAG, "$tableName 中文名叠加较慢：$matched 条 / ${totalMs}ms")
        }
        return matched
    }

    /**
     * 中文名叠加层是否需要为了这张表下载简中数据。
     * [com.pjsk.toolbox.data.remote.TableSpec] 上的 `cnOverlay` 已标注哪些表有中文名，
     * 但几十 MB 的表是否真的去下载，交给调用方（[MasterImporter.isLargeOverlay]）决定。
     */
    fun isLargeOverlay(fileSizeBytes: Long): Boolean = fileSizeBytes > LARGE_OVERLAY_THRESHOLD

    private companion object {
        const val TAG = "MasterImporter"

        /** 单张表（或它的中文名叠加）超过这个耗时就打一行日志，方便定位首次打开的耗时来源。 */
        const val SLOW_LOG_MS = 400L

        const val BATCH_SIZE = 500
        const val OVERLAY_BATCH_SIZE = 500
        const val LARGE_OVERLAY_THRESHOLD = 8L * 1024 * 1024
    }
}

// JsonObject 的宽容取值助手已抽到 JsonExt.kt（internal），
// 这样 RowProjectors 也能共用同一套规则，避免两处解析行为不一致。

