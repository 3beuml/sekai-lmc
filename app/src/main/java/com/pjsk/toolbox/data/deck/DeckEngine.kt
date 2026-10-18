package com.pjsk.toolbox.data.deck

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import java.io.ByteArrayInputStream

/**
 * 组卡引擎的 WebView 宿主。
 *
 * ## 为什么用 WebView 而不是 JNI
 *
 * 引擎（`@empty-sekai/allium-deck-wasm`）是浏览器方向的 WASM 产物：glue 是 ES module，
 * 通过 `WebAssembly.instantiateStreaming` 加载 `.wasm`。要在 Android 里跑它，
 * 用 WebView 是**唯一不需要引入 Rust 工具链/NDK**的路（另一条路是重新用 Rust 编译成
 * `.so`，调研估算是 2–4 天且新增一类构建依赖）。
 *
 * ## 为什么不用 androidx.webkit 的 WebViewAssetLoader
 *
 * 那个依赖只为了"把 assets 映射成一个 https 源"。这里用 `shouldInterceptRequest`
 * 自己做同样的事：ES module 必须从非 `file://` 的源加载（否则被 CORS 挡），
 * 所以把 `assets/deck/` 下的文件映到 `https://deck.local/` 同路径 就够了，不用多一个依赖。
 *
 * ## 生命周期
 *
 * WebView 要**在 UI 线程创建和销毁**；引擎内部有全局状态（masterdata 缓存），
 * 所以一台机器实例只对应一个 WebView，页面退出时 `destroy()`。
 */
class DeckEngine(private val context: Context) {

    @Volatile
    private var webView: WebView? = null

    /** 引擎把事件推过来（ready / loaded / result / error / elapsed）。 */
    val events = Channel<Pair<String, String>>(Channel.UNLIMITED)

    private var ready: CompletableDeferred<Unit>? = null

    /**
     * 已注册的落盘文件：假源下的路径 → 本地文件。
     *
     * 目前有 `masterdata.json`（约 10 MB）与可选的 `musicMetas.json`（约 2.4 MB）。
     * 为什么走这条路而不是把 JSON 塞进 `evaluateJavascript`：见类注释与 §17.2。
     */
    @Volatile
    private var servedFiles: Map<String, java.io.File> = emptyMap()

    /** 在 UI 线程调用；返回 WebView 供 Compose 的 AndroidView 挂上去。 */
    @SuppressLint("SetJavaScriptEnabled")
    fun attach(): WebView {
        webView?.let { return it }
        val readySignal = CompletableDeferred<Unit>()
        ready = readySignal
        val view = WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            addJavascriptInterface(Bridge(), "DeckBridge")
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?,
                ): WebResourceResponse? {
                    val url = request?.url?.toString() ?: return null
                    if (!url.startsWith(BASE)) return null
                    val path = url.removePrefix(BASE).substringBefore('?')
                    // 注册过的落盘文件（masterdata / musicMetas）：直接返回文件流，
                    // 避免把十几 MB 的 JSON 塞进 JS 字符串
                    servedFiles[path.trimStart('/')]?.let { file ->
                        return WebResourceResponse(
                            "application/json", "utf-8", 200, "OK",
                            mapOf("Cache-Control" to "no-store"), file.inputStream(),
                        )
                    }
                    val assetPath = "deck/" + path.trimStart('/')
                    val stream = runCatching { context.assets.open(assetPath) }.getOrNull()
                        ?: return WebResourceResponse("text/plain", "utf-8", 404, "Not Found", emptyMap(), ByteArrayInputStream(ByteArray(0)))
                    val mime = when {
                        assetPath.endsWith(".js") -> "text/javascript"
                        assetPath.endsWith(".html") -> "text/html"
                        assetPath.endsWith(".wasm") -> "application/wasm"
                        else -> "application/octet-stream"
                    }
                    return WebResourceResponse(mime, null, 200, "OK", mapOf("Cache-Control" to "no-store"), stream)
                }
            }
            loadUrl("$BASE/boot.html")
        }
        webView = view
        return view
    }

    /** 等引擎的 wasm `init()` 完成（超时返回 false，不抛）。 */
    suspend fun awaitReady(): Boolean = runCatching {
        withTimeoutOrFalse(READY_TIMEOUT_MS)
    }.getOrDefault(false)

    private suspend fun withTimeoutOrFalse(timeoutMs: Long): Boolean {
        val signal = ready ?: return false
        return kotlinx.coroutines.withTimeoutOrNull(timeoutMs) {
            signal.await()
            true
        } ?: false
    }

    /**
     * 注册 masterdata 落盘文件；WebView 通过 `/masterdata.json` 直接取它（不经过 JS 字符串）。
     *
     * @param musicMetas 每首歌的分数/PT 系数表（`musicMetas.json`，2.4 MB）。
     *   给了才会注册 `/musicMetas.json` —— 引擎算「分数 / 活动 PT」要用它，
     *   算「综合力」不需要。没给时 boot.html 把第二个入参传 `"[]"`。
     */
    fun loadMasterDataFile(master: java.io.File, musicMetas: java.io.File? = null) {
        servedFiles = buildMap {
            put("masterdata.json", master)
            musicMetas?.let { put("musicMetas.json", it) }
        }
        val musicArg = if (musicMetas != null) "'/musicMetas.json'" else "null"
        webView?.evaluateJavascript(
            "window.deckBoot.loadFromUrl('/masterdata.json', $musicArg)",
            null,
        )
    }

    /** 旧通道：直接传字符串。⚠️ 已弃用 —— 15 MB JSON 转义后要 148 MB，会 OOM。 */
    fun loadMasterData(masterdataJson: String) {
        val js = "window.deckBoot.load(${org.json.JSONObject.quote(masterdataJson)})"
        webView?.evaluateJavascript(js, null)
    }

    /** 在 UI 线程调用：跑一次推荐。 */
    fun recommend(userJson: String, paramsJson: String) {
        val js = "window.deckBoot.run(${org.json.JSONObject.quote(userJson)}, " +
            "${org.json.JSONObject.quote(paramsJson)})"
        webView?.evaluateJavascript(js, null)
    }

    fun destroy() {
        runCatching {
            webView?.removeJavascriptInterface("DeckBridge")
            webView?.destroy()
        }
        webView = null
        ready = null
    }

    private inner class Bridge {
        @JavascriptInterface
        fun post(kind: String, payload: String) {
            if (kind == "ready") ready?.complete(Unit)
            events.trySend(kind to payload)
        }
    }

    companion object {
        /** 假源。**必须是 https**，否则 ES module 会被当成不透明源拒绝。 */
        const val BASE = "https://deck.local"

        private const val READY_TIMEOUT_MS = 20_000L
    }
}


