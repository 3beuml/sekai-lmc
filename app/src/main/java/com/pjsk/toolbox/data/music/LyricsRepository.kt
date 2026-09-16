package com.pjsk.toolbox.data.music

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * 歌词仓库。
 *
 * ## 数据源
 *
 * `https://translation.exmeaning.com/files/translation/lyrics/`
 *  - `index.json`（约 198 KB，711 首）：每首的 `revision` / `state` / 中日照名 / 有哪些版本
 *  - `music_<id>.json`（约 20 KB）：正文。**没有这首歌时返回 404 纯文本 `404 page not found`**
 *
 * 参考站会给 `music_<id>.json` 加 `?rev=<revision>`，但实测那是**纯缓存破坏参数**
 * （rev=1/3/99999 返回的字节完全相同），服务端不看它。我们照加一个，
 * 好处是 OkHttp 的磁盘缓存键会随 revision 变化，自然失效。
 *
 * ## 缓存策略（为什么要这样）
 *
 * - **索引**：进程内缓存一次；失败时退回磁盘副本。198 KB 不该每次开歌词页都拉。
 * - **正文**：以 `(musicId, revision)` 为键存磁盘。**revision 没变就不发请求** ——
 *   这样第二次看同一首歌的词是零流量，而且**断网也能看已经看过的词**。
 *
 * 这也是「不把歌词塞进内置快照」的代价与补偿：快照会随 APK 冻结，
 * 而歌词是社区持续在补的，放磁盘缓存反而更新更及时。
 */
class LyricsRepository(
    private val context: Context,
    private val client: OkHttpClient,
) {

    private val dir: File = File(context.cacheDir, "lyrics").apply { mkdirs() }
    private val indexFile: File = File(dir, "index.json")

    /** 进程内缓存：索引只拉一次。 */
    private var cachedIndex: List<LyricsIndexEntry>? = null

    /**
     * 这首歌**有没有歌词正文**。
     *
     * - `true` / `false`：索引已经拿到了，这是确定答案
     * - `null`：**还不知道**（第一次用又没网，索引没缓存下来）
     *
     * ⚠️ 界面上的用法是「**只有明确 false 才隐藏入口**」：
     * 不知道的时候照常显示 —— 宁可让用户点进去看到"这首没有歌词"，
     * 也不要把一个本来能用的入口藏掉。
     */
    suspend fun hasLyrics(musicId: Int): Boolean? = withContext(Dispatchers.IO) {
        val index = runCatching { loadIndex(forceRefresh = false) }.getOrNull()
        if (index.isNullOrEmpty()) return@withContext null
        index.firstOrNull { it.musicId == musicId }?.hasDocument ?: false
    }

    /** 载入某首歌的歌词。**不抛异常**，所有失败都变成 [LyricsResult.Failed]。 */
    suspend fun load(musicId: Int, forceRefresh: Boolean = false): LyricsResult =
        withContext(Dispatchers.IO) {
            val index = runCatching { loadIndex(forceRefresh) }.getOrNull()
            // 索引拿不到（首次且断网）时也要能干活：直接试正文
            val entry = index?.firstOrNull { it.musicId == musicId }

            if (entry != null && entry.state == "satisfied_no_lyrics") {
                // 索引已经明确说「没有歌词」，不必再去碰 404。
                // 具体原因（`noLyricsReason`）只写在文档里，而这类曲子的文档就是 404，
                // 所以 UI 用通用说法，不编原因。
                return@withContext LyricsResult.NoLyrics(null)
            }
            if (index != null && entry == null) {
                return@withContext LyricsResult.NoEntry
            }

            val revision = entry?.revision ?: 0
            val doc = runCatching { loadDocument(musicId, revision, forceRefresh) }
                .getOrElse { return@withContext LyricsResult.Failed(it.message ?: "歌词加载失败") }
                ?: return@withContext if (entry != null && entry.state == "satisfied_no_lyrics") {
                    LyricsResult.NoLyrics(null)
                } else {
                    LyricsResult.NoEntry
                }

            when (doc.state) {
                "incomplete" -> LyricsResult.Incomplete(doc)
                else -> LyricsResult.Ok(doc, entry)
            }
        }

    /** 索引：内存 → 磁盘 → 网络。 */
    private fun loadIndex(forceRefresh: Boolean): List<LyricsIndexEntry> {
        if (!forceRefresh) {
            cachedIndex?.let { return it }
        }
        val fresh = fetchText(INDEX_URL)?.let { text ->
            parseLyricsIndex(text).takeIf { it.isNotEmpty() }?.also {
                runCatching { indexFile.writeText(text) }
            }
        }
        val entries = fresh
            ?: cachedIndex
            ?: runCatching { parseLyricsIndex(indexFile.readText()) }.getOrNull()?.takeIf { it.isNotEmpty() }
            ?: emptyList()
        cachedIndex = entries
        return entries
    }

    /**
     * 正文：`(musicId, revision)` 命中磁盘缓存就直接用，否则联网。
     *
     * ⚠️ 拿不到索引（revision=0）时**不能**信任磁盘副本 —— 分不清缓存是哪一版，
     * 这时一律以网络为准（拿不到就退缓存）。
     */
    private fun loadDocument(musicId: Int, revision: Int, forceRefresh: Boolean): LyricsDocument? {
        val cacheFile = File(dir, "music_${musicId}_rev$revision.json")

        if (!forceRefresh && revision > 0 && cacheFile.isFile) {
            runCatching { parseLyricsDocument(cacheFile.readText(), musicId) }
                .getOrNull()
                ?.takeIf { it.revision == revision }
                ?.let { return it }
        }

        val url = "$DOC_BASE/music_$musicId.json" + if (revision > 0) "?rev=$revision" else ""
        val text = fetchText(url)
        if (text != null) {
            val doc = runCatching { parseLyricsDocument(text, musicId) }.getOrNull()
            if (doc != null) {
                // 只有 revision 对得上（或索引未知）才落盘，避免把旧版当新版缓存
                if (revision <= 0 || doc.revision == revision) {
                    runCatching { cacheFile.writeText(text) }
                }
                return doc
            }
        }
        // 网络失败/404：退回磁盘上任意一版，好过什么都不显示
        return runCatching {
            dir.listFiles { f -> f.name.startsWith("music_${musicId}_rev") }
                ?.maxByOrNull { it.lastModified() }
                ?.let { parseLyricsDocument(it.readText(), musicId) }
        }.getOrNull()
    }

    /** `satisfied_no_lyrics` 的原因只写在文档里，而那种曲子的文档是 404 —— 拿不到就算了。 */
    private fun fetchText(url: String): String? = runCatching {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            // 参考站有过「200 但返回 HTML」的坑（见笔记 §11.6 那条 search-index），
            // 这里顺手拦一下：正文必须是 JSON 开头
            val body = response.body?.string() ?: return@use null
            val trimmed = body.trimStart()
            if (!trimmed.startsWith("{")) return@use null
            body
        }
    }.getOrNull()

    private companion object {
        const val DOC_BASE = "https://translation.exmeaning.com/files/translation/lyrics"
        const val INDEX_URL = "$DOC_BASE/index.json"
    }
}
