package com.pjsk.toolbox.util

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.OutputStream

/**
 * 把素材存进系统相册 / 音乐库。
 *
 * ## 为什么不再用系统 [android.app.DownloadManager]
 *
 * 这里原来是 `DownloadManager.enqueue(... setDestinationInExternalPublicDir("SekaiLMC", name))`，
 * **所有下载都必然失败**，真机 logcat 抓到的原始报错是：
 *
 * ```
 * E DatabaseUtils: java.lang.IllegalStateException:
 *     Not one of standard directories: SekaiLMC
 *     at com.android.providers.downloads.DownloadProvider.call(DownloadProvider.java:772)
 * ```
 *
 * 原因是 `setDestinationInExternalPublicDir(dirType, subPath)` 的第一个参数**必须是
 * `Environment.DIRECTORY_*` 这类系统标准目录常量**（`DIRECTORY_DOWNLOADS` / `DIRECTORY_PICTURES` …），
 * 而我们把应用自己的子目录名 `"SekaiLMC"` 当成了 `dirType` 传进去，
 * DownloadProvider 直接判定非法并拒绝建任务 —— 连一次网络请求都没发出去。
 *
 * ## 现在怎么做
 *
 * 自己用 OkHttp 下载（共享 `AppContainer.httpClient`），再落盘：
 *  - **API 29+**：写 `MediaStore`，用 `IS_PENDING` 占位避免相册里出现半张图。
 *    **不需要任何存储权限**。
 *  - **API 26~28**：直接写公共目录文件 + `MediaScannerConnection` 通知相册，
 *    这一段需要 `WRITE_EXTERNAL_STORAGE`（清单里已用 `maxSdkVersion="28"` 限住）。
 *
 * 好处是失败**能拿到真实原因**（HTTP 状态码、0 字节、被系统拒绝），
 * 而不像 DownloadManager 那样只丢一个错误码。
 */
object AssetDownloader {

    /** 当前系统是否还需要先申请存储权限。 */
    val needsStoragePermission: Boolean
        get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

    /**
     * 存到哪里。
     *
     * 图片进相册（`Pictures/`），音频进音乐库（`Music/`）——
     * 这样系统相册和音乐播放器都能直接看到，不需要用户去文件管理器里翻。
     */
    enum class Kind(val mimeType: String, val publicDir: String, val subDir: String) {
        IMAGE("image/png", Environment.DIRECTORY_PICTURES, "SekaiLMC"),
        AUDIO("audio/mpeg", Environment.DIRECTORY_MUSIC, "SekaiLMC"),
    }

    /** 下载完成的结果。 */
    data class Saved(
        /** 可直接给系统打开的 Uri。 */
        val uri: Uri,
        /** 给人看的路径，用于提示文案。 */
        val displayPath: String,
        val bytes: Long,
    )

    /**
     * 下载并保存。失败返回 [Result.failure]，由调用方决定怎么提示 ——
     * 不让一次下载失败把整个详情页带崩。
     *
     * **带自动重试**：原图 PNG 实测最大到 **5.7 MB**（生日卡），一次长传输里网络抖一下
     * 就会整体失败。所以对**瞬时错误**（IO 异常 / 5xx）最多重试 [MAX_ATTEMPTS] 次、指数退避；
     * 而 404 / 403 这类「服务器明确说没有」的错误**不重试**，直接报出来。
     *
     * @param onProgress 已下载字节 / 总字节（总字节未知时为 -1）。
     */
    suspend fun download(
        context: Context,
        client: OkHttpClient,
        url: String,
        fileName: String,
        kind: Kind,
        onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> },
    ): Result<Saved> = withContext(Dispatchers.IO) {
        var last: Throwable? = null
        for (attempt in 1..MAX_ATTEMPTS) {
            val result = runCatching {
                val safeName = fileName.replace(Regex("""[\\/:*?"<>|]"""), "_")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    saveViaMediaStore(context, client, url, safeName, kind, onProgress)
                } else {
                    saveViaFile(context, client, url, safeName, kind, onProgress)
                }
            }
            result.onSuccess { return@withContext result }
            last = result.exceptionOrNull()
            // 「文件不存在」重试多少次都一样，立刻放弃
            if (last is AssetMissingException) break
            if (attempt < MAX_ATTEMPTS) {
                onProgress(0L, -1L) // 让界面上的进度回到起点，而不是停在半路
                delay(RETRY_BACKOFF_MS * attempt)
            }
        }
        Result.failure(last ?: IllegalStateException("下载失败"))
    }

    // ── 存在性探测 ───────────────────────────────────────────

    /**
     * 探测一个远端资源是否存在。
     *
     * 返回值有**三种**含义，调用方必须区分：
     *  - `success(true)`：存在（2xx）；
     *  - `success(false)`：**明确不存在**（404 / 403）；
     *  - `failure`：没问出来（离线、超时等）—— 这时**不能**当成「不存在」，
     *    否则一次网络抖动就会把内容从界面上抹掉。
     *
     * 之所以需要它：`cards.gachaPhrase` 只说明「这张卡有抽卡台词」，
     * **不保证语音文件存在**。实测 `res010_no056`（卡 `#1468`）有台词但语音是 404，
     * 而同期的 `res017_no056` 有语音 —— 桶里 `sound/gacha/get_voice/res010/`
     * 一路列到 `no055` 就没有了。所以板块的有无要以这个探测为准。
     */
    suspend fun exists(client: OkHttpClient, url: String): Result<Boolean> =
        withContext(Dispatchers.IO) {
            runCatching {
                client.newCall(Request.Builder().url(url).head().build()).execute().use { response ->
                    when {
                        response.isSuccessful -> true
                        response.code == 404 || response.code == 403 -> false
                        else -> error("HTTP ${response.code}")
                    }
                }
            }
        }

    // ── 只下载到 App 缓存目录（不落相册/音乐库）────────────

    /**
     * 把远端文件下到 App 缓存目录，返回本地文件；已存在且非空就直接复用。
     *
     * 用途是「**先下后播**」：抽卡语音只有约 20 KB，先拿到本地再交给 MediaPlayer，
     * 比让它自己去边流边放可靠得多 —— `setDataSource(httpUrl)` 会同步建立连接，
     * 而播放器状态机一旦在错误时机被打断，表现就是「点了没反应」。
     * 顺带还能重复秒播，不用每次重新请求。
     */
    suspend fun cacheToCacheDir(
        context: Context,
        client: OkHttpClient,
        url: String,
        subDir: String,
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.cacheDir, subDir).apply { mkdirs() }
            val file = File(dir, url.substringAfterLast('/').ifBlank { "asset.bin" })
            if (file.length() > 0L) return@runCatching file
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) error("服务器返回 HTTP ${response.code}")
                val body = response.body ?: error("响应内容为空")
                file.outputStream().use { out -> body.byteStream().copyTo(out) }
            }
            if (file.length() == 0L) {
                file.delete()
                error("下载到 0 字节（文件可能不存在）")
            }
            file
        }
    }

    // ── API 29+ ──────────────────────────────────────────────

    private fun saveViaMediaStore(
        context: Context,
        client: OkHttpClient,
        url: String,
        fileName: String,
        kind: Kind,
        onProgress: (Long, Long) -> Unit,
    ): Saved {
        val resolver = context.contentResolver
        val collection = when (kind) {
            Kind.IMAGE -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            Kind.AUDIO -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val relativePath = "${kind.publicDir}/${kind.subDir}"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, kind.mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            // 占位中：写盘的这段时间相册里不会出现半张图
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values)
            ?: error("系统拒绝创建文件（MediaStore.insert 返回 null）")

        val bytes: Long
        try {
            val stream = resolver.openOutputStream(uri)
                ?: error("无法打开输出流")
            bytes = stream.use { copy(client, url, it, onProgress) }
        } catch (t: Throwable) {
            // 失败要删掉占位，否则相册里会留下 0 字节的孤儿文件
            runCatching { resolver.delete(uri, null, null) }
            throw t
        }

        resolver.update(
            uri,
            ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
            null,
            null,
        )
        // update 之后 ContentResolver 才知道真实大小，通知一次让相册刷新
        runCatching { resolver.notifyChange(uri, null) }

        val display = "${Environment.getExternalStorageDirectory()}/$relativePath/$fileName"
        return Saved(uri, display, bytes)
    }

    // ── API 26~28 ────────────────────────────────────────────

    private fun saveViaFile(
        context: Context,
        client: OkHttpClient,
        url: String,
        fileName: String,
        kind: Kind,
        onProgress: (Long, Long) -> Unit,
    ): Saved {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(kind.publicDir),
            kind.subDir,
        )
        if (!dir.exists() && !dir.mkdirs()) {
            error("无法创建目录 ${dir.absolutePath}（存储权限被拒绝？）")
        }
        val file = File(dir, fileName)
        val bytes = file.outputStream().use { copy(client, url, it, onProgress) }
        // 通知相册/音乐库，否则新文件在系统应用里看不见
        runCatching {
            MediaScannerConnection.scanFile(
                context,
                arrayOf(file.absolutePath),
                arrayOf(kind.mimeType),
                null,
            )
        }
        return Saved(Uri.fromFile(file), file.absolutePath, bytes)
    }

    // ── 共用 ─────────────────────────────────────────────────

    /** 边下边写，返回总字节数。 */
    private fun copy(
        client: OkHttpClient,
        url: String,
        out: OutputStream,
        onProgress: (Long, Long) -> Unit,
    ): Long {
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) {
                // 区分「没有这个文件」和「服务器暂时出错」：前者重试无意义
                if (response.code == 404 || response.code == 403) {
                    throw AssetMissingException("服务器返回 HTTP ${response.code}（文件不存在）")
                }
                error("服务器返回 HTTP ${response.code}")
            }
            val body = response.body ?: error("响应内容为空")
            val total = body.contentLength()
            val buffer = ByteArray(64 * 1024)
            var done = 0L
            body.byteStream().use { input ->
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    out.write(buffer, 0, read)
                    done += read
                    onProgress(done, total)
                }
            }
            out.flush()
            // ⚠️ 必须挡住「HTTP 200 但 0 字节」：CDN 上不存在的路径有时会返回
            // 一个空 body 而不是 404，照写就会得到一个 0 字节的坏文件。
            if (done == 0L) error("下载到 0 字节（文件可能不存在）")
            return done
        }
    }

    /** 服务器明确说「没有这个文件」——重试没有意义，别浪费用户时间。 */
    private class AssetMissingException(message: String) : IllegalStateException(message)

    /** 瞬时错误最多重试几次（含第一次）。 */
    private const val MAX_ATTEMPTS = 3

    /** 重试退避基数：第 1 次失败等 700ms，第 2 次等 1400ms。 */
    private const val RETRY_BACKOFF_MS = 700L
}
