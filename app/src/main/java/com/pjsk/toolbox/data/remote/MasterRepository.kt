package com.pjsk.toolbox.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

/**
 * 负责「与远端 master data 交互」：版本探测 + 条件下载。
 *
 * 远端事实（全部实测确认）：
 *  - 数据文件：`https://sekai-world.github.io/sekai-master-db<region>-diff/<file>.json`
 *  - 版本号藏在最新 commit 的 message 里：
 *      GET https://api.github.com/repos/Sekai-World/sekai-master-db-diff/commits?per_page=1
 *      → commit.message = "master version 6.7.0.40 asset version 6.7.0.40"
 *  - 条件请求：GitHub Pages 返回 ETag / Last-Modified，命中 304 就完全不用下载。
 *
 * ⚠️ GitHub 的 commits API 在**未鉴权**情况下限速 60 次/小时/IP。
 *    所以 [probeVersion] 只应在用户手动点击「检查更新」或一次完整同步的开头调用一次，
 *    绝不要放进循环或做成定时轮询。
 */
class MasterRepository(
    private val client: OkHttpClient,
    private val workDir: File,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    },
) {

    data class VersionInfo(
        val masterVersion: String?,
        val assetVersion: String?,
        val commitDate: String?,
    ) {
        /** 用于和本地记录比较的标识。 */
        val key: String get() = masterVersion ?: commitDate ?: "unknown"
    }

    sealed interface DownloadResult {
        /** 304：远端未变化。 */
        data object NotModified : DownloadResult

        data class Downloaded(
            val file: File,
            val bytes: Long,
            val etag: String?,
            val lastModified: String?,
        ) : DownloadResult

        data class Failed(val message: String, val code: Int? = null) : DownloadResult
    }

    private val versionRegexMaster = Regex("""master version\s+([0-9][0-9.]*)""")
    private val versionRegexAsset = Regex("""asset version\s+([0-9][0-9.]*)""")

    /** 缓存文件名按区服区分，避免切换区服时误用另一区的数据。 */
    fun cachedFile(region: ServerRegion, fileName: String): File =
        File(workDir, "${region.id}__$fileName")

    private fun etagFileOf(cached: File): File = File(cached.parentFile, "${cached.name}.etag")

    /**
     * 内置快照导入后留下的「条件请求种子」文件（内容是 HTTP 日期）。
     *
     * 存在的理由：内置快照导入的表**故意不写 ETag**（快照里没有条件请求凭据），
     * 于是首次在线同步时每一张表都只能完整重下 —— 实测默认模块就有 10.6 MB。
     * 但快照里记着**它的数据版本对应的 commit 时间**，而 GitHub Pages 实测支持
     * `If-Modified-Since`（返回 304），所以用它做一次条件请求：
     * 快照之后**没变过的表直接 304**，只有真变过的才会下载。
     *
     * ⚠️ 种子的取值**刻意偏保守**（用版本 commit 时间，而不是快照生成时间）：
     * 偏旧最多导致「本来可以跳过的表也下一遍」（多花流量但结果正确），
     * 偏新则会**漏掉更新**（收到 304 却拿着旧数据），那才是必须避免的。
     */
    private fun sinceFileOf(cached: File): File = File(cached.parentFile, "${cached.name}.since")

    /**
     * 给某个区服的某张表写入条件请求种子（[httpDate] 必须是 HTTP 日期格式，
     * 例如 `Sun, 13 Sep 2026 15:00:14 GMT`）。由内置快照导入时调用。
     */
    fun seedModifiedSince(region: ServerRegion, fileName: String, httpDate: String) {
        runCatching {
            val cached = cachedFile(region, fileName)
            cached.parentFile?.mkdirs()
            sinceFileOf(cached).writeText(httpDate)
        }
    }

    /**
     * 探测远端数据版本。返回 [Result.failure] 时通常是限速或断网，调用方应把它当成
     * 「无法确认是否有更新」，而不是「没有更新」。
     *
     * ⚠️ **要往回扫若干条提交，不能只看最新那一条**。这个仓库每天都会推
     * `update user information` 这类提交，而真正的版本号提交长这样：
     * `master version 6.8.0.42 asset version 6.8.0.40`，它往往在几天之前
     * （实测 2026-09-15 时，最新提交是「update user information」，版本号提交在 09-13）。
     *
     * 只看一条的后果很具体：`masterVersion` 长期是 `null`，于是
     *  ① 「检查更新」显示不出远端版本；
     *  ② `SyncManager.runSync` 里「版本一致就一个文件都不下」的快速跳过**永远不成立**，
     *    导致**每次启动**都要对所选表逐个发条件请求（66 次往返，慢网下几十秒），
     *    虽然都命中 304、不花流量，但白白占着网络，也拖慢首屏图片加载。
     *
     * 取 20 条与打包脚本 `tools/datapack/fetch-raw.mjs` 的做法保持一致（那边也是往回扫 20 条）。
     */
    suspend fun probeVersion(region: ServerRegion): Result<VersionInfo> = withContext(Dispatchers.IO) {
        val url = "https://api.github.com/repos/Sekai-World/sekai-master-db${region.repoSuffix}/commits?per_page=$VERSION_SCAN_COMMITS"
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", USER_AGENT)
            .build()
        runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("版本探测失败：HTTP ${response.code}")
                }
                val body = response.body?.string().orEmpty()
                val root = json.parseToJsonElement(body).jsonArray
                if (root.isEmpty()) throw IOException("版本探测失败：commit 列表为空")

                var found: VersionInfo? = null
                for (element in root) {
                    val commit = (element as? JsonObject)?.get("commit")?.jsonObject ?: continue
                    val message = commit["message"]?.jsonPrimitive?.content.orEmpty()
                    val master = versionRegexMaster.find(message)?.groupValues?.get(1) ?: continue
                    found = VersionInfo(
                        masterVersion = master,
                        assetVersion = versionRegexAsset.find(message)?.groupValues?.get(1),
                        commitDate = commit["committer"]?.jsonObject?.get("date")?.jsonPrimitive?.content,
                    )
                    break
                }
                found ?: throw IOException("最近 $VERSION_SCAN_COMMITS 条提交里都没有版本号")
            }
        }
    }

    /**
     * 下载一张表。带条件请求；命中 304 直接返回 [DownloadResult.NotModified]。
     * 先写 `.part` 临时文件，成功后再原子改名，避免留下半个损坏的 JSON 被当成有效缓存。
     *
     * @param forceFull 忽略本地 ETag，强制完整下载。
     *   用于「本地数据库缺失但 ETag 还在」的情况（例如房间数据库被 destructive migration 重建过），
     *   否则会收到 304 却没有任何数据可用。
     */
    suspend fun downloadTable(
        region: ServerRegion,
        spec: TableSpec,
        forceFull: Boolean = false,
        onProgress: (read: Long, total: Long) -> Unit,
    ): DownloadResult = withContext(Dispatchers.IO) {
        val cached = cachedFile(region, spec.fileName)
        val etagFile = etagFileOf(cached)
        val previousEtag = etagFile.takeIf { it.isFile }?.readText()?.trim()?.ifBlank { null }
        // 没有 ETag 时退回「快照种子」的 If-Modified-Since（见 sinceFileOf 的说明）
        val previousSince = if (previousEtag != null) {
            null
        } else {
            sinceFileOf(cached).takeIf { it.isFile }?.readText()?.trim()?.ifBlank { null }
        }

        val builder = Request.Builder()
            .url(region.tableUrl(spec.fileName))
            .header("User-Agent", USER_AGENT)
        // 注意：条件是「本地有 ETag」，而不是「本地有 JSON 文件」。
        // 导入成功后我们会删掉 JSON 以省空间，但刻意保留 ETag —— 它很小，
        // 而它正是让「已同步过的表零下载」得以成立的关键。
        if (!forceFull && previousEtag != null) {
            builder.header("If-None-Match", previousEtag)
        } else if (!forceFull && previousSince != null) {
            builder.header("If-Modified-Since", previousSince)
        }

        try {
            client.newCall(builder.build()).execute().use { response ->
                when {
                    response.code == 304 -> DownloadResult.NotModified

                    !response.isSuccessful -> DownloadResult.Failed(
                        message = "下载失败：HTTP ${response.code}",
                        code = response.code,
                    )

                    else -> {
                        val body = response.body ?: return@use DownloadResult.Failed("下载失败：响应体为空")
                        val total = body.contentLength()
                        val tmp = File(workDir, "${cached.name}.part")
                        var read = 0L
                        body.byteStream().use { input ->
                            tmp.outputStream().use { output ->
                                val buf = ByteArray(64 * 1024)
                                while (true) {
                                    val n = input.read(buf)
                                    if (n <= 0) break
                                    output.write(buf, 0, n)
                                    read += n
                                    onProgress(read, total)
                                }
                            }
                        }
                        if (cached.isFile) cached.delete()
                        if (!tmp.renameTo(cached)) {
                            tmp.copyTo(cached, overwrite = true)
                            tmp.delete()
                        }
                        val newEtag = response.header("ETag")
                        newEtag?.let { etagFile.writeText(it) } ?: etagFile.delete()
                        DownloadResult.Downloaded(
                            file = cached,
                            bytes = read,
                            etag = newEtag,
                            lastModified = response.header("Last-Modified"),
                        )
                    }
                }
            }
        } catch (e: IOException) {
            DownloadResult.Failed(message = "网络错误：${e.message ?: e::class.java.simpleName}")
        }
    }

    /**
     * 下载完并成功入库后删掉原始 JSON。
     *
     * **刻意保留 `.etag` 文件**：它只有几十字节，但正是它让「已同步过的表下次同步零下载」
     * 成立。如果连它一起删掉，每次同步都会把所有表重新下载一遍（最大几张表加起来上百 MB）。
     * 需要彻底重置时用 [clearAll]。
     */
    fun removeCached(region: ServerRegion, fileName: String) {
        val cached = cachedFile(region, fileName)
        if (cached.isFile) cached.delete()
        // etagFileOf(cached) 保留，不要删
    }

    /** 读取已缓存的 JSON 文本（仅用于体积很小的表，例如 cardRarities.json）。 */
    suspend fun readCachedText(region: ServerRegion, fileName: String): String? =
        withContext(Dispatchers.IO) {
            cachedFile(region, fileName).takeIf { it.isFile }?.readText()
        }

    fun clearAll() {
        workDir.listFiles()?.forEach { it.delete() }
    }

    companion object {
        /**
         * GitHub 与素材 CDN 都建议带上可识别的 UA；
         * 方便对方在日志里区分正常客户端与爬虫。
         */
        const val USER_AGENT = "sekai-lmc/0.1 (Android; fan-made data viewer)"

        /**
         * 版本探测时往回扫多少条提交（见 [probeVersion] 的说明）。
         * 与 `tools/datapack/fetch-raw.mjs` 取的条数一致。
         */
        const val VERSION_SCAN_COMMITS = 20
    }
}
