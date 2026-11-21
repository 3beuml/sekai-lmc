package com.pjsk.toolbox.data.update

import android.content.Context
import com.pjsk.toolbox.data.settings.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * App 自身的更新检测（和 master data 的「数据同步」完全无关）。
 *
 * 做法照 ZZUPocket：只问 GitHub Releases 的 `latest` 接口，**不在 App 内下载 APK**。
 * 发现新版 → 弹窗里给更新说明 →「去下载」用系统浏览器打开 release 页/直链。
 *
 * 为什么不下 APK 自己装：
 *  · 要 `REQUEST_INSTALL_PACKAGES` 权限 + 「允许安装未知应用」的确认流程；
 *  · GitHub 的下载域名在国内不保证可达，浏览器至少还有用户自己的网络环境可用；
 *  · 本项目是侧载分发，用户本来就会手动装包，跳浏览器没有多一道门槛。
 */

/**
 * 一次可用的「新版本」信息。字段全部来自 GitHub Releases 的 latest 接口：
 *
 * - [version] —— tag 去掉 v/V 前缀后的版本号（与本地 `versionName` 同口径比较）；
 * - [notes] —— release body，就是发布时写的更新说明；
 * - [pageUrl] —— release 页（浏览器兜底入口）；
 * - [downloadUrl] —— assets 里第一个 `.apk` 的直链（走 GitHub CDN，能否访问取决于用户网络）。
 */
data class AppRelease(
    val version: String,
    val notes: String,
    val pageUrl: String,
    val downloadUrl: String?,
)

/**
 * 检测更新的纯逻辑（自检里断言，见 `tools/logic-check` 第 32 节）。
 *
 * 版本比较刻意用 **versionName 语义比较**而不是 versionCode：
 * 远端 release 只有 tag，没有 versionCode；用同一口径即可。
 */
object AppUpdate {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * 一次 release 到另一个 release 的比较：1 = a 更新，0 = 相同，-1 = a 更旧。
     *
     * 口径：
     *  · 前缀 v/V 自动剥掉；每段只取**开头连续数字**当值（"0-rc1" 当 0），非法段当 0；
     *  · 数字段严格相等时，走**预发后缀规则**：无后缀（正式版）比带后缀新 ——
     *    `0.2.0-rc1 < 0.2.0`，装了 rc 的用户在正式版发布后能看到提示；
     *  · 两个都带后缀时比后缀里的数字（rc2 > rc1）。
     */
    fun compareVersions(a: String?, b: String?): Int {
        fun parts(v: String?): Pair<List<Int>, String?> {
            if (v == null) return emptyList<Int>() to null
            val s = v.trim().removePrefix("v").removePrefix("V")
            val digits = Regex("^\\d+(?:\\.\\d+)*").find(s)?.value
            val core = digits ?: ""
            val suffix = if (core.length < s.length) s.drop(core.length).trim() else null
            val nums = core.split('.').map { part -> part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
                .takeIf { core.isNotEmpty() } ?: emptyList()
            return nums to suffix
        }
        val (left, leftSuffix) = parts(a)
        val (right, rightSuffix) = parts(b)
        val n = maxOf(left.size, right.size)
        for (i in 0 until n) {
            val x = left.getOrElse(i) { 0 }
            val y = right.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y).coerceIn(-1, 1)
        }
        // 数字打平：预发后缀定先后 —— 正式 > 预发；rc 之间比后缀数字
        if (leftSuffix != rightSuffix) {
            if (leftSuffix == null) return 1
            if (rightSuffix == null) return -1
            fun tail(s: String): Int =
                Regex("\\d+").find(s)?.value?.toIntOrNull() ?: 0
            return tail(leftSuffix).compareTo(tail(rightSuffix)).coerceIn(-1, 1)
        }
        return 0
    }

    /**
     * 红点 = 远端有比本地**新**的版本，且不是用户已经点过「不再提醒」的那一个。
     * 屏蔽只认具体版本：之后出了更新的版本，仍然会提醒 —— 所以这里只判不等。
     */
    fun shouldShowDot(current: String?, latest: String?, muted: String?): Boolean =
        latest != null &&
            compareVersions(latest, current) > 0 &&
            (muted == null || compareVersions(latest, muted) != 0)

    /** 本机 versionName；读不到（理论不可能）返回空串，比较时按「最旧」处理。 */
    fun localVersion(context: Context): String =
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()

    /**
     * 解析 `releases/latest` 的响应体。**纯函数**，自检里用假 JSON 断言。
     *
     * 返回 null 的情况只有「这不是一份能用的 release 描述」（没有 tag / JSON 坏了）——
     * 调用方据此当作「无法确认」，绝不显示「已是最新」。
     */
    fun parseRelease(body: String): AppRelease? = runCatching {
        val root = json.parseToJsonElement(body).jsonObject
        val version = root["tag_name"]?.jsonPrimitive?.content?.trim().orEmpty()
        if (version.isBlank()) return null
        val assets = root["assets"]?.jsonArray
        val apk = assets
            ?.map { it.jsonObject }
            ?.firstOrNull { (it["name"]?.jsonPrimitive?.content ?: "").endsWith(".apk", ignoreCase = true) }
        AppRelease(
            version = version.removePrefix("v").removePrefix("V"),
            notes = root["body"]?.jsonPrimitive?.content.orEmpty(),
            pageUrl = root["html_url"]?.jsonPrimitive?.content
                ?: "https://github.com/3beuml/sekai-lmc/releases/latest",
            downloadUrl = apk?.get("browser_download_url")?.jsonPrimitive?.content,
        )
    }.getOrNull()
}

/**
 * GitHub Releases 的只读拉取。
 *
 * **刻意单独一个 OkHttpClient**，不看 [com.pjsk.toolbox.data.AppContainer.httpClient] 的
 * 超时（那边给 54 MB 数据表留了 180 秒）：检测更新只求「问一下」，
 * 开屏静默场景和手动场景都不该卡很久。这也和 MasterRepository 的请求分开，
 * 避免一次慢查询把更新检测拖住。
 *
 * 公开仓不需要 token；`api.github.com` 可达即可，`github.com` 被墙也不影响检测
 * （只有点「去下载」时才需要访问 github.com）。
 */
class AppReleaseClient {

    private val http = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun latest(): AppRelease? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(RELEASES_URL)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", USER_AGENT)
                .build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                AppUpdate.parseRelease(response.body?.string().orEmpty())
            }
        }.getOrNull()
    }

    private companion object {
        const val RELEASES_URL = "https://api.github.com/repos/3beuml/sekai-lmc/releases/latest"

        /** GitHub API 要求带 UA；用项目名而不是 OkHttp 默认值，出问题时便于识别。 */
        const val USER_AGENT = "sekai-lmc"
    }
}

/**
 * 检测更新的进程内状态中心：更多页入口、弹窗、底部导航角标共享同一份。
 *
 *  · 开屏静默查一次（[checkSilently]，失败无声，角标不误报也不打扰）；
 *  · 手动查（[checkExplicit]）给弹窗完整的 Checking→结论 状态；
 *  · 「不再提醒」落在 [AppSettings]，重启后依然生效。
 */
class UpdateCenter(private val settings: AppSettings) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = AppReleaseClient()

    private val _release = MutableStateFlow<AppRelease?>(null)

    /** 最近一次成功拉到的远端 release；null = 还没查到过（不等于没有新版）。 */
    val release: StateFlow<AppRelease?> = _release.asStateFlow()

    /** 手动检查的阶段性状态，弹窗用来显示「正在检查 / 已是最新 / 网络不通」。 */
    sealed interface Phase {
        data object Idle : Phase
        data object Checking : Phase
        data object Done : Phase
    }

    private val _phase = MutableStateFlow<Phase>(Phase.Idle)
    val phase: StateFlow<Phase> = _phase.asStateFlow()

    /** 被「不再提醒」的版本号；持久化在 AppSettings，重启后依然生效。 */
    val mutedVersion: StateFlow<String?> = settings.updateMutedVersion

    /** 静默检查：只改 [release]，失败保持/置空（角标自然不亮）。 */
    fun checkSilently() {
        scope.launch { _release.value = client.latest() }
    }

    /** 手动检查：完整走一遍 Checking → Done，结论状态由 phase + release 表达。 */
    fun checkExplicit() {
        scope.launch {
            _phase.value = Phase.Checking
            _release.value = client.latest()
            _phase.value = Phase.Done
        }
    }

    /** 记下「不再提醒」的版本（只屏蔽这一版；之后的新版仍会提醒）。 */
    fun setUpdateMutedVersion(version: String?) {
        settings.setUpdateMutedVersion(version)
    }
}
