import com.pjsk.toolbox.data.remote.TableCatalog
import com.pjsk.toolbox.data.sync.ContentSha
import com.pjsk.toolbox.data.sync.JsonArrayStreamer
import com.pjsk.toolbox.data.sync.RowProjectors
import com.pjsk.toolbox.data.sync.TableSchemas
import com.pjsk.toolbox.data.sync.extractOverlayName
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.BufferedWriter
import java.io.File
import java.io.OutputStreamWriter
import java.time.Instant

/**
 * 构建「内置数据快照」。
 *
 * 这是「打开 App 立刻有内容」的地基：把 master data 预先裁好、连同中文名一起打进
 * APK 的 assets，App 首次启动直接导入，**不需要任何网络**。
 *
 * 三条设计原则：
 *  1. **复用 App 里的 `RowProjectors` / `TableSchemas`**，绝不重写一套裁剪规则。
 *     两边各写一遍的结果是「内置数据」和「在线同步」的名字/字段慢慢不一致，且不报错。
 *  2. **只裁不猜**：裁剪规则本身已被 73 项离线自检覆盖（`tools/logic-check.ps1`）。
 *  3. **体积有上限**：没有裁剪器、且原始文件又特别大的表直接跳过（例如 46.8 MB 的
 *     `gachas.json`），否则会把 APK 撑大。这类表等真正要用时再补裁剪器。
 *
 * 输入：`tools/datapack/raw/<表名>`（日服）、`tools/datapack/cn/<表名>`（简中服，可选）
 * 输出：`app/src/main/assets/datapack/`
 *        ├─ manifest.json          快照元信息（版本、每张表的行数/体积）
 *        ├─ <表名>                  裁剪后的紧凑 JSON 数组
 *        └─ overlay/<表名>          { "id": "中文名", … }（仅 cnOverlay 表）
 *
 * 运行：tools/datapack/build.ps1
 */

/** 没有裁剪器时，原始文件超过这个大小就跳过（避免 APK 被撑大）。 */
private const val MAX_RAW_BYTES_WITHOUT_PROJECTOR = 4L * 1024 * 1024

private val lenientJson = Json { isLenient = true; ignoreUnknownKeys = true }

fun main(args: Array<String>) {
    val root = File(args.firstOrNull() ?: ".")
    val rawDir = File(root, "tools/datapack/raw")
    val cnDir = File(root, "tools/datapack/cn")
    val outDir = File(root, "app/src/main/assets/datapack")

    require(rawDir.isDirectory) { "找不到 ${rawDir.absolutePath}，先跑 tools/datapack/fetch-raw.mjs" }

    outDir.deleteRecursively()
    val overlayDir = File(outDir, "overlay").apply { mkdirs() }

    println("源数据目录 : ${rawDir.absolutePath}")
    println("中文叠加层 : ${if (cnDir.isDirectory) cnDir.absolutePath else "(无，只打包日服原文)"}")
    println("输出目录   : ${outDir.absolutePath}\n")

    val tableEntries = mutableListOf<JsonObject>()
    val skipped = mutableListOf<JsonObject>()
    val shaMismatch = mutableListOf<String>()
    var totalRows = 0L
    var totalOverlayRows = 0L

    // 每张表在上游仓库里的 blob sha（由 fetch-raw.mjs 从 git trees API 取来写进 raw/_shas.json）。
    //
    // 为什么要写进快照：App 首次导入内置数据后，就**知道手里这份对应上游哪个 sha**，
    // 于是首次在线同步只要取一次仓库树（1 个请求）就能判断「有没有更新」，
    // 既不用把十几 MB 重新下一遍，也能在下载后校验内容对不对（见 MasterRepository）。
    val shasFile = File(rawDir, "_shas.json")
    val shas: Map<String, String> = if (shasFile.isFile) {
        val obj = runCatching {
            lenientJson.parseToJsonElement(shasFile.readText()) as? JsonObject
        }.getOrNull()
        obj?.entries?.mapNotNull { (k, v) ->
            (v as? kotlinx.serialization.json.JsonPrimitive)?.content?.let { k to it }
        }?.toMap().orEmpty()
    } else {
        emptyMap()
    }
    if (shas.isEmpty()) {
        println("⚠️ 没找到 raw/_shas.json（先跑 fetch-raw.mjs）——快照将不带 sha，" +
            "App 首次同步会把所有表重新下一遍。\n")
    }

    for (spec in TableCatalog.ALL) {
        val rawFile = File(rawDir, spec.fileName)
        if (!rawFile.isFile) {
            skipped += buildJsonObject {
                put("file", spec.fileName)
                put("reason", "源数据未下载（tools/datapack/raw/ 里没有这个文件）")
            }
            continue
        }

        // ── 0. 先确认「本地这份源数据」就是「记录的那份」──
        // 对不上就**中止打包**（不是跳过）。理由：manifest 里记的是上游的 sha，
        // 如果本地内容其实更旧，快照就会"谎报"自己是最新的 —— App 之后判定"sha 一致"，
        // 于是那几张表**永远不会被更新**。这正是我们刚踩过的坑：
        // GACHA 模块的源数据是几天前抓的，而重抓时没带 gacha 模块，两边就此对不上。
        val expectedSha = shas[spec.fileName]
        if (expectedSha != null) {
            val actual = ContentSha.of(rawFile)
            if (actual != expectedSha) {
                shaMismatch += spec.fileName
                skipped += buildJsonObject {
                    put("file", spec.fileName)
                    put("reason", "源数据与上游 sha 不一致（本地 ${actual.take(8)} / 上游 ${expectedSha.take(8)}），先重跑 fetch-raw.mjs")
                }
                continue
            }
        }

        val projector = RowProjectors.forFile(spec.fileName)
        val rawBytes = rawFile.length()
        if (projector == null && rawBytes > MAX_RAW_BYTES_WITHOUT_PROJECTOR) {
            val mb = "%.1f".format(rawBytes / 1048576.0)
            skipped += buildJsonObject {
                put("file", spec.fileName)
                put("reason", "没有裁剪器且原始体积 $mb MB 超过上限，直接打包会把 APK 撑大")
                put("rawBytes", rawBytes)
            }
            continue
        }

        // ── 1. 裁剪并写出紧凑 JSON 数组 ──
        val outFile = File(outDir, spec.fileName)
        var rows = 0L
        outFile.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write("[")
            JsonArrayStreamer.forEachElement(rawFile) { elementText ->
                val obj = runCatching {
                    lenientJson.parseToJsonElement(elementText) as? JsonObject
                }.getOrNull() ?: return@forEachElement

                // 没有裁剪器时，把原始元素**紧凑化**：省掉缩进与换行。
                // 这些数据在 APK 里会被再压缩一次，但紧凑化能减小安装后的占用与解析开销。
                val stored: JsonObject = projector?.project(obj) ?: obj
                if (rows > 0) writer.write(",")
                writer.write(stored.toString())
                rows++
            }
            writer.write("]")
        }

        // ── 2. 中文名叠加层 ──
        val cnFile = File(cnDir, spec.fileName)
        var overlayRows = 0L
        var hasOverlay = false
        if (spec.cnOverlay && cnFile.isFile) {
            val schema = TableSchemas[spec.fileName]
            val sb = StringBuilder(64 * 1024)
            // 必须写成**数组**形式：[{"id":1,"name":"星乃 一歌"}, …]
            //
            // 踩过的坑：第一版写成了 JSON 对象 {"1":"星乃 一歌", …}，而 App 侧的
            // JsonArrayStreamer 只认顶层**数组**（它一直在等 '['），于是整份叠加层
            // 一条都没被应用 —— 界面上显示日文名，但两边都不报错，很难发现。
            sb.append("[")
            JsonArrayStreamer.forEachElement(cnFile) { elementText ->
                val obj = runCatching {
                    lenientJson.parseToJsonElement(elementText) as? JsonObject
                }.getOrNull() ?: return@forEachElement

                val id = obj.firstId(schema.idKeys) ?: return@forEachElement
                val zh = schema.extractOverlayName(obj) ?: return@forEachElement

                if (overlayRows > 0) sb.append(",")
                sb.append("{\"id\":").append(id).append(",\"name\":").append(quote(zh)).append("}")
                overlayRows++
            }
            sb.append("]")
            if (overlayRows > 0) {
                File(overlayDir, spec.fileName).writeText(sb.toString(), Charsets.UTF_8)
                hasOverlay = true
            }
        }

        totalRows += rows
        totalOverlayRows += overlayRows
        tableEntries += buildJsonObject {
            put("file", spec.fileName)
            put("module", spec.module.name)
            put("rows", rows)
            put("bytes", outFile.length())
            put("rawBytes", rawBytes)
            put("projected", projector != null)
            put("hasOverlay", hasOverlay)
            put("overlayRows", overlayRows)
            // 上游仓库里这份源数据的 blob sha（App 用它判断"有没有更新"与"下到的是不是这份"）
            shas[spec.fileName]?.let { put("sha", it) }
        }
        println(
            "  %-34s %6d 行  %8s → %8s%s".format(
                spec.fileName,
                rows,
                human(rawBytes),
                human(outFile.length()),
                if (overlayRows > 0) "  中文名 $overlayRows 条" else "",
            ),
        )
    }

    // ── 3. manifest ──
    val versionFile = File(rawDir, "_version.json")
    val version = if (versionFile.isFile) {
        runCatching { lenientJson.parseToJsonElement(versionFile.readText()) as? JsonObject }.getOrNull()
    } else {
        null
    }
    val versionCommit = version?.get("recentVersionCommit") as? JsonObject

    val manifest = buildJsonObject {
        put(
            "snapshot",
            buildJsonObject {
                put("generatedAt", Instant.now().toString())
                put("dataSource", "sekai-master-db-diff (日服) + sekai-master-db-cn-diff (简中名)")
                versionCommit?.get("masterVersion")?.let { put("masterVersion", it) }
                versionCommit?.get("assetVersion")?.let { put("assetVersion", it) }
                versionCommit?.get("at")?.let { put("versionCommitAt", it) }
            },
        )
        put("tables", buildJsonArray { tableEntries.forEach { add(it) } })
        put("skipped", buildJsonArray { skipped.forEach { add(it) } })
    }
    File(outDir, "manifest.json").writeText(manifest.toString(), Charsets.UTF_8)

    val packBytes = outDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    println("\n打包完成：")
    println("  表数        ${tableEntries.size} 张（跳过 ${skipped.size} 张）")
    println("  总行数      $totalRows 行")
    println("  中文名      $totalOverlayRows 条")
    println("  快照总大小  ${human(packBytes)}")
    if (skipped.isNotEmpty()) {
        println("  跳过：")
        skipped.forEach { s ->
            println("    ${s["file"]?.toString()?.trim('"')}  ← ${s["reason"]?.toString()?.trim('"')}")
        }
    }

    // 源数据与上游 sha 对不上 → 这份快照不可信，**必须让构建失败**，不能悄悄产出
    if (shaMismatch.isNotEmpty()) {
        println("\n✗ 有 ${shaMismatch.size} 张表的源数据与上游 sha 不一致：" +
            shaMismatch.joinToString(", "))
        println("  这些表的内容是旧的，但 manifest 会记成上游最新的 sha —— App 会因此永远不更新它们。")
        println("  先重跑抓取（node tools/datapack/fetch-raw.mjs <模块...>）再打包。")
        kotlin.system.exitProcess(1)
    }
}

private fun JsonObject.firstId(keys: List<String>): Long? {
    for (k in keys) {
        val v = this[k] as? kotlinx.serialization.json.JsonPrimitive ?: continue
        v.content.toLongOrNull()?.let { return it }
    }
    return null
}

/** 极简 JSON 字符串转义。 */
private fun quote(s: String): String {
    val sb = StringBuilder(s.length + 2)
    sb.append('"')
    for (ch in s) {
        when (ch) {
            '"' -> sb.append("\\\"")
            '\\' -> sb.append("\\\\")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            else -> if (ch < ' ') sb.append("\\u%04x".format(ch.code)) else sb.append(ch)
        }
    }
    sb.append('"')
    return sb.toString()
}

private fun human(bytes: Long): String =
    if (bytes >= 1048576) "%.2f MB".format(bytes / 1048576.0) else "%.1f KB".format(bytes / 1024.0)
