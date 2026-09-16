package com.pjsk.toolbox.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.pjsk.toolbox.data.sync.ContentSha
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

/**
 * 负责「与远端 master data 交互」：版本探测（用于界面显示）+ 按内容 sha 下载与校验。
 *
 * 远端事实（全部实测确认）：
 *  - 数据文件：`https://sekai-world.github.io/sekai-master-db<region>-diff/<file>.json`
 *  - 版本号藏在**往回若干条** commit 的 message 里（机器人每天推的提交没有版本号）：
 *      GET https://api.github.com/repos/Sekai-World/sekai-master-db-diff/commits?per_page=20
 *      → commit.message = "master version 6.7.0.40 asset version 6.7.0.40"
 *  - 仓库里每个文件的 blob sha：
 *      GET https://api.github.com/repos/Sekai-World/sekai-master-db-diff/git/trees/main?recursive=1
 *      → 一次请求拿到全部文件（实测 398 个、未被截断）——**这是判断"有没有更新"的权威依据**
 *
 * ⚠️ **判断更新与校验内容一律用 blob sha，不要用 ETag / Last-Modified / 版本号**：
 *  - Pages 前面是 Fastly 缓存（max-age=600）。我们踩过一次真坑：版本提交之后 6.5 小时抓取，
 *    仍拿到部署前的旧副本（少 5 张卡、2 首歌），而体积只差 0.4%，比大小根本发现不了；
 *  - trees API 是**仓库直读、不过缓存**，所以"上游有没有新提交"是立即准确的；
 *  - 下载时带 `?cb=<时间戳>` 绕过 CDN 缓存，下完再算一遍 sha 与期望值比对，
 *    不一致就不覆盖本地数据 —— 这样"旧副本污染本地数据"在原理上就不可能发生。
 *
 * ⚠️ GitHub 的 API 在**未鉴权**情况下限速 60 次/小时/IP，所以一次同步只调：
 *    ① [probeVersion] 一次（拿版本号显示用，可选）② [fetchTreeShas] 一次。绝不要放进循环。
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
        /** 304：远端未变化（保留给条件请求路径，正常流程不走这条）。 */
        data object NotModified : DownloadResult

        data class Downloaded(
            val file: File,
            val bytes: Long,
            val etag: String?,
            val lastModified: String?,
        ) : DownloadResult

        /**
         * 内容 sha 与仓库里的对不上 —— **绝不能入库**。
         *
         * 成因通常是 CDN 还在给部署前的旧副本（或下载被中途截断）。
         * 调用方应带新的 `?cb=` 再试一次；仍不一致就报错并保留本地旧数据。
         */
        data class ShaMismatch(val expected: String, val actual: String) : DownloadResult

        data class Failed(val message: String, val code: Int? = null) : DownloadResult
    }

    private val versionRegexMaster = Regex("""master version\s+([0-9][0-9.]*)""")
    private val versionRegexAsset = Regex("""asset version\s+([0-9][0-9.]*)""")

    /** 缓存文件名按区服区分，避免切换区服时误用另一区的数据。 */
    fun cachedFile(region: ServerRegion, fileName: String): File =
        File(workDir, "${region.id}__$fileName")

    private fun etagFileOf(cached: File): File = File(cached.parentFile, "${cached.name}.etag")

    // ── 内容 sha：判断「有没有更新」与「下到的是不是那份」的权威判据 ────────
    //
    // 为什么不用版本号或 ETag 判断更新：
    //  - **版本号**只在游戏大版本时跳（约每周），而表的内容可能在两次版本号之间被改
    //    （不过实测真正影响我们的表都是跟版本提交一起变的）；
    //  - **ETag / Last-Modified 来自 GitHub Pages，前面是 Fastly 缓存**：我们踩过一次真坑 ——
    //    09-13 的版本提交之后 6.5 小时抓取，仍拿到部署前的旧副本（少了 5 张卡、2 首歌），
    //    而且体积只差 0.4%，靠比大小根本发现不了。
    //
    // 所以：**判定与校验都用仓库的 blob sha**（内容哈希，差一个字节都对不上）。
    // git trees API 是仓库直读、不经过 Pages 缓存，所以"上游有没有新提交"是立即准确的；
    // 下载时再带 `?cb=<时间戳>` 绕过 CDN 缓存，下完算一遍 sha 对不上就重试/放弃。

    private fun shaFileOf(cached: File): File = File(cached.parentFile, "${cached.name}.sha")

    /** 本地记录的「当前这份内容对应上游哪个 sha」。没有记录（例如旧版本升上来的库）时返回 null。 */
    fun readContentSha(region: ServerRegion, fileName: String): String? =
        shaFileOf(cachedFile(region, fileName)).takeIf { it.isFile }?.readText()?.trim()?.ifBlank { null }

    /**
     * 记下某张表当前内容对应的上游 sha。
     *
     * 两条来源：① 下载并校验通过之后；② **内置快照导入时**（sha 写在快照的 manifest 里）——
     * 后者让「全新安装后的首次同步」也能直接判定为「无更新」，一个字节都不用下。
     */
    fun seedContentSha(region: ServerRegion, fileName: String, sha: String) {
        runCatching {
            val cached = cachedFile(region, fileName)
            cached.parentFile?.mkdirs()
            shaFileOf(cached).writeText(sha)
        }
    }

    /**
     * 取上游仓库里每个文件的 blob sha（1 个请求、无 CDN 缓存）。
     *
     * 失败（限速、断网、响应被截断）时返回 [Result.failure]：调用方**必须**把它当成
     * 「无法确认是否有更新」，而不是「没有更新」—— 前者只是这次不更新，后者会让数据永远停在旧版本。
     */
    suspend fun fetchTreeShas(region: ServerRegion): Result<Map<String, String>> =
        withContext(Dispatchers.IO) {
            val url = "https://api.github.com/repos/Sekai-World/sekai-master-db${region.repoSuffix}" +
                "/git/trees/main?recursive=1"
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", USER_AGENT)
                .build()
            runCatching {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("取仓库文件列表失败：HTTP ${response.code}")
                    val root = json.parseToJsonElement(response.body?.string().orEmpty()).jsonObject
                    if (root["truncated"]?.jsonPrimitive?.content == "true") {
                        // 截断意味着"没列到的表"不能当成不存在，这份列表不可信
                        throw IOException("仓库文件列表被 GitHub 截断，本次不判断更新")
                    }
                    val map = HashMap<String, String>(512)
                    root["tree"]?.jsonArray?.forEach { element ->
                        val obj = element as? JsonObject ?: return@forEach
                        if (obj["type"]?.jsonPrimitive?.content != "blob") return@forEach
                        val path = obj["path"]?.jsonPrimitive?.content ?: return@forEach
                        val sha = obj["sha"]?.jsonPrimitive?.content ?: return@forEach
                        map[path] = sha
                    }
                    if (map.isEmpty()) throw IOException("仓库文件列表为空")
                    map
                }
            }
        }

    /**
     * 算一个文件的 **git blob sha**（`sha1("blob <字节数>\0" + 内容)`）。
     *
     * 与仓库里 `git hash-object` 的结果同算法，所以可以直接和 trees API 给的 sha 比。
     * 注意长度用的是**字节数**：这个数据集里全是日文，按字符数算会全错。
     */
    fun gitBlobShaOf(file: File): String = ContentSha.of(file)

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
     * 下载一张表，**并在写盘前校验内容 sha**。
     *
     * 流程：URL（必要时带 `?cb=` 绕过 CDN 缓存）→ 写到 `.part` → 算 git blob sha 与
     * [expectedSha] 比对 → 一致才原子改名覆盖本地文件并记下 sha；不一致则删掉临时文件、
     * 返回 [DownloadResult.ShaMismatch]，**本地已有数据完全不受影响**。
     *
     * @param expectedSha 期望的 sha（来自 [fetchTreeShas]）。为 null 时跳过校验（不建议）。
     * @param cacheBust 是否带 `?cb=<时间戳>`。**内容已变化的表必须为 true**：
     *   Pages 的 Fastly 缓存可能还在给部署前的旧副本，那正是"数据悄悄停在旧版本"的成因。
     */
    suspend fun downloadTable(
        region: ServerRegion,
        spec: TableSpec,
        expectedSha: String? = null,
        cacheBust: Boolean = false,
        onProgress: (read: Long, total: Long) -> Unit,
    ): DownloadResult = withContext(Dispatchers.IO) {
        val cached = cachedFile(region, spec.fileName)
        val etagFile = etagFileOf(cached)
        val url = region.tableUrl(spec.fileName) +
            if (cacheBust) "?cb=${System.currentTimeMillis()}" else ""
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)

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

                        // ⚠️ 写盘前先校验：这是「数据不会被旧副本/半截文件污染」的那道闸。
                        if (read == 0L) {
                            tmp.delete()
                            return@use DownloadResult.Failed("下载到 0 字节（文件可能不存在）")
                        }
                        if (expectedSha != null) {
                            val actual = gitBlobShaOf(tmp)
                            if (actual != expectedSha) {
                                tmp.delete()
                                return@use DownloadResult.ShaMismatch(expected = expectedSha, actual = actual)
                            }
                        }

                        if (cached.isFile) cached.delete()
                        if (!tmp.renameTo(cached)) {
                            tmp.copyTo(cached, overwrite = true)
                            tmp.delete()
                        }
                        // 记下这份内容对应的 sha：下次同步据此判断是否需要更新
                        expectedSha?.let { shaFileOf(cached).writeText(it) }
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
