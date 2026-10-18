package com.pjsk.toolbox.data.profile

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * 拉取结果。
 *
 * ⚠️ **上游的 404 是无法区分原因的一个码**：`/public/{区服}/…` 这条路由是 catch-all，
 * 实测「UID 不存在」「没上传过数据」「没开『允许公开 API 访问』」「参数写错」都返回
 * 同一个 36 字节的 `{"status":404,"message":"not found"}`（连非法区服 `zz` 也是）。
 * 所以 [NotFound] 这个分支**不能**在界面上断言具体原因，只能把四种可能一起列给用户。
 */
sealed interface PlayerFetchResult {
    data class Success(val snapshot: PlayerSnapshot) : PlayerFetchResult

    /** 上游 404：查不到可读数据（原因不可区分，见类注释）。 */
    object NotFound : PlayerFetchResult

    /** 网络/HTTP 层面的失败，带一句能看的原因。 */
    data class Failed(val message: String) : PlayerFetchResult
}

/**
 * 「我的账号」取数与缓存。
 *
 * ## 数据从哪来
 * `GET https://suite-api.haruki.seiunx.com/public/{server}/suite/{gameId}?key=…`
 * —— **免鉴权、免注册、只读 GET**。数据是玩家自己上传到 Haruki 工具箱的**快照**
 * （响应里带 `upload_time`），不是实时数据。
 *
 * ## 为什么不放 cacheDir
 * 快照存 `filesDir/player/`。放 `cacheDir` 会被系统在空间紧张时整目录回收，
 * 用户就会莫名其妙"绑定丢了"—— 而重新拉一次要联网，且要用户自己记得去上传。
 *
 * ## 为什么存原始 JSON 而不是解析后的对象
 * 解析规则以后会改（上游加减字段、我们要补字段）。存原文，解析失败时还能重试，
 * 也不用为模型写一套序列化/反序列化。
 */
class PlayerRepository(
    context: Context,
    private val http: OkHttpClient,
) {

    private val dir = File(context.filesDir, "player")

    /** 读本地快照；没有或解析不出来就返回 null（不抛异常）。 */
    fun loadCached(account: PlayerAccount): PlayerSnapshot? = runCatching {
        val f = File(dir, account.cacheFileName)
        if (!f.isFile) return null
        parseSnapshot(f.readText(), account)
    }.getOrNull()

    /** 删除本地快照（解绑时调）。 */
    fun clear(account: PlayerAccount) {
        runCatching { File(dir, account.cacheFileName).delete() }
    }

    /**
     * 联网拉一份新快照。成功后**顺手写盘**，失败不动旧缓存
     * （旧快照比没有好，界面会显示它的时间）。
     */
    suspend fun fetch(account: PlayerAccount): PlayerFetchResult = withContext(Dispatchers.IO) {
        val url = "$API_BASE/${account.server}/suite/${account.gameId}?key=$KEYS"
        val request = Request.Builder().url(url).get().build()

        val raw = runCatching {
            http.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                when {
                    response.code == 404 -> return@withContext PlayerFetchResult.NotFound
                    // 403 在现版本基本不出现；留着是为了上游行为回退时能说清原因。
                    response.code == 403 -> return@withContext PlayerFetchResult.Failed("上游拒绝：该账号没有打开「允许公开 API 访问」。")
                    !response.isSuccessful -> return@withContext PlayerFetchResult.Failed("服务器返回 ${response.code}。")
                    text.isBlank() -> return@withContext PlayerFetchResult.Failed("服务器返回了空内容。")
                    else -> text
                }
            }
        }.getOrElse { return@withContext PlayerFetchResult.Failed(it.message ?: it::class.simpleName ?: "网络错误") }

        val snapshot = parseSnapshot(raw, account)
            ?: return@withContext PlayerFetchResult.Failed("返回的内容不是预期的格式（可能上游改版了）。")

        // 写盘放在解析成功之后：宁可没有缓存，也不要在磁盘上留一份读不出来的文件。
        runCatching {
            dir.mkdirs()
            File(dir, account.cacheFileName).writeText(raw)
            Log.i(TAG, "快照已缓存：${account.cacheFileName}（${raw.length} 字符）")
        }.onFailure { Log.w(TAG, "快照写盘失败（不影响本次显示）", it) }

        PlayerFetchResult.Success(snapshot)
    }

    companion object {
        private const val TAG = "PlayerRepository"

        private const val API_BASE = "https://suite-api.haruki.seiunx.com/public"

        /**
         * 只申请我们真的会用的字段。
         *
         * ⚠️ 这不是凭据，是**字段选择器** —— 上游文档里 `?key=` 用来挑返回哪些表，
         * 不带也能读全（但响应会有几百 KB）。少要一点对上游的服务也更客气。
         */
        private const val KEYS = "userGamedata,userCards,userMusicResults,userMaterials," +
            "userProfile,userDecks,userCharacters,userBonds,userChallengeLiveSoloStages,upload_time," +
            // ⚠️ 区域道具与称号现在是「我的」页还没用、但下一批（综合力）必需的：
            // 少要这两个 key，将来算出来的区域道具/称号分项会恒为 0，而界面看起来完全正常
            // —— 这是本轮最容易踩的静默错误的坑，所以**提前要上**。
            "userAreas,userHonors"
    }
}
