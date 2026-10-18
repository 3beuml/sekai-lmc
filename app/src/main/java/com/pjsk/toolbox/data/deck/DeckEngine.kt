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

    /** 在 UI 线程调用：把 masterdata 交给引擎（`{"cards":"…",…}`）。 */
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

