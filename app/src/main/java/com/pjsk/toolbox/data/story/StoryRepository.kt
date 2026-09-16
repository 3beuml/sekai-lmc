package com.pjsk.toolbox.data.story

import com.pjsk.toolbox.data.db.MasterDao
import com.pjsk.toolbox.data.remote.AssetUrls
import com.pjsk.toolbox.data.remote.ServerRegion
import com.pjsk.toolbox.ui.common.intOrNull
import com.pjsk.toolbox.ui.common.jsonObject
import com.pjsk.toolbox.ui.common.longOrNull
import com.pjsk.toolbox.ui.common.str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 剧情模块的数据入口。
 *
 * 设计取舍：
 *  - **一次性读**（`observeAll(...).first()`）而不是 Flow —— 剧情数据在导入后就不再变，
 *    列表也不需要响应式更新，用 Flow 只会让 6 个类型各自挂一堆订阅。
 *  - **正文在线加载**：一话 60~158 KB，全部内置会有几十 MB，所以走 CDN + 缓存。
 *  - **中文按话回退**：先试简中桶，404 再取日服桶，并把来源告诉 UI。
 *    不能整站假设 —— 简中服进度落后，同一活动里也可能有的有中文有的没有。
 */
class StoryRepository(
    private val dao: MasterDao,
    private val client: OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * `Character2dId` → `characterId` 的映射（来自 `character2ds.json`，579 行）。
     *
     * 正文里只给 `Character2dId`（还带服装版本，如 287 = `v2_10an`），
     * 而头像要用的是**角色 id**，所以必须先过这张表。
     */
    private suspend fun character2dMap(): Map<Int, Int> =
        dao.observeAll(TABLE_CHARACTER_2DS).first().mapNotNull { row ->
            val obj = row.jsonObject() ?: return@mapNotNull null
            val characterId = obj.intOrNull("characterId") ?: return@mapNotNull null
            row.id to characterId
        }.toMap()

    /** 第 1 层：某一类型的列表行。 */
    suspend fun rows(type: StoryType, region: ServerRegion): List<StoryRow> =
        withContext(Dispatchers.Default) {
            when (type) {
                StoryType.EVENT -> eventRows(region)
                StoryType.UNIT -> unitRows()
                StoryType.CARD -> cardRows(region)
            }
        }

    /** 第 2 层：某一行的各话。 */
    suspend fun entries(type: StoryType, id: String): List<StoryEntry> =
        withContext(Dispatchers.Default) {
            when (type) {
                StoryType.EVENT -> {
                    val row = rowByField(TABLE_EVENT_STORIES, "eventId", id)
                    // ⚠️ `eventStoryEpisodes[]` **没有**「每话出场角色」字段，
                    // 所以这里用**该活动所属组合的成员**做近似（方案 A）——
                    // 精确的出场角色藏在正文的 `AppearCharacters` 里，
                    // 要为列表页拿它就得把每一话的正文都抓一遍（21 话约 2~3 MB），不划算。
                    // 点进阅读器后是**逐句精确头像**。
                    val members = row?.jsonObject()?.intOrNull("eventId")
                        ?.let { eventId -> eventMembers(eventId) }
                        .orEmpty()
                    row?.let(::eventStoryEntries)?.map { it.copy(characterIds = members) }
                }
                StoryType.UNIT -> {
                    val row = rowByField(TABLE_UNIT_STORIES, "unit", id)
                    val members = unitMembers(id)
                    row?.let(::unitStoryEntries)?.map { it.copy(characterIds = members) }
                }
                // 主线 / 活动 / 区域对话有第 2 层；卡牌与特殊剧情直接进阅读器
                StoryType.CARD -> emptyList()
            }.orEmpty()
        }

    // ── 第 1 层 ──────────────────────────────────────────────

    private suspend fun eventRows(region: ServerRegion): List<StoryRow> {
        val events = dao.observeAll(TABLE_EVENTS).first().associateBy { it.id }
        // `eventStoryUnits.json`：活动剧情 → 相关的团（450 行，214 个活动剧情全覆盖）。
        // 给活动剧情列表做「按团筛选」用；归约规则见 `eventStoryUnitKeys`。
        val unitKeysByStory: Map<Int, List<String>> = dao.observeAll(TABLE_EVENT_STORY_UNITS)
            .first()
            .groupBy { row -> row.jsonObject()?.intOrNull("eventStoryId") ?: -1 }
            .mapValues { (_, unitRows) ->
                eventStoryUnitKeys(
                    unitRows.mapNotNull { unitRow ->
                        val unitObj = unitRow.jsonObject() ?: return@mapNotNull null
                        val unit = unitObj.str("unit") ?: return@mapNotNull null
                        EventStoryUnit(
                            eventStoryId = unitObj.intOrNull("eventStoryId") ?: -1,
                            unit = unit,
                            isMain = unitObj.str("eventStoryUnitRelation") == "main",
                        )
                    },
                )
            }
        return dao.observeAll(TABLE_EVENT_STORIES).first()
            .mapNotNull { row ->
                val eventId = row.jsonObject()?.intOrNull("eventId") ?: return@mapNotNull null
                val event = events[eventId]
                val eventObj = event?.jsonObject()
                val eventBundle = eventObj?.str("assetbundleName")
                val startAt = eventObj?.longOrNull("startAt")
                val closedAt = eventObj?.longOrNull("closedAt")
                parseEventStoryRow(
                    row = row,
                    eventName = event?.nameZh?.takeIf { it.isNotBlank() } ?: event?.name,
                    eventBundle = eventBundle,
                    // ⚠️ 排序键用**真实的开放时间**（`events.startAt`），不是 `sortValue`：
                    // 后者会回退到 id，只能算"大致的时间顺序"。缺失时才退回 id。
                    eventStartAt = startAt ?: (event?.sortValue ?: 0L),
                    unitKeys = unitKeysByStory[row.id] ?: emptyList(),
                )?.copy(
                    // 用户要求：活动名下面显示**活动的起止时间**（而不是长简介）
                    subtitle = formatPeriod(startAt, closedAt),
                    // **进行中 / 已结束**：纯按日期算（对齐参考站的活动剧情列表）
                    status = statusOf(startAt, closedAt),
                    // 缩略图用活动 logo —— 实测 `event/<bundle>/logo/logo.webp` 是唯一可靠的路径
                    imageUrl = eventBundle?.takeIf { it.isNotBlank() }
                        ?.let { AssetUrls.eventLogo(region, it) },
                )
            }
            .sortedByDescending { it.sortAt } // 默认最新在前（筛选面板里可以改成最早在前）
    }

    /**
     * 主线剧情：`unitStories.json` 只有 **6 行**（一个组合一行），话在各章里内联。
     *
     * ⚠️ 标题**不能直接用 `unit` 字段** —— 那是内部 key（`light_sound` / `idol` / `piapro`…），
     * 直接显示出来就是「没做好」的样子。组合名从 `unitProfiles.json` 取（带中文叠加层），
     * logo 用内置的 `assets/icons/unit/<缩写>.webp`。
     */
    private suspend fun unitRows(): List<StoryRow> {
        val profiles = dao.observeAll(TABLE_UNIT_PROFILES).first().associateBy { row ->
            row.jsonObject()?.str("unit") ?: row.name ?: ""
        }
        return dao.observeAll(TABLE_UNIT_STORIES).first()
            .mapNotNull { row ->
                val unitKey = row.jsonObject()?.str("unit") ?: return@mapNotNull null
                val profile = profiles[unitKey]
                parseUnitStoryRow(
                    row = row,
                    unitLabel = profile?.nameZh?.takeIf { it.isNotBlank() }
                        ?: profile?.name?.takeIf { it.isNotBlank() }
                        ?: unitKey,
                )?.copy(imageUrl = AssetUrls.unitLogo(unitKey))
            }
            .sortedBy { it.sortAt }
    }



    /**
     * 卡牌剧情：一行一张**有剧情的卡**。
     *
     * 只列有剧情的卡（实测 1447 张里 1384 张有），否则列表里会混进 63 张点进去是空的卡。
     */
    private suspend fun cardRows(region: ServerRegion): List<StoryRow> {
        val cards = dao.observeAll(TABLE_CARDS).first().associateBy { it.id }
        val episodesByCard = dao.observeAll(TABLE_CARD_EPISODES).first()
            .groupBy { it.jsonObject()?.intOrNull("cardId") ?: 0 }
        val characters = dao.observeAll(TABLE_CHARACTERS).first().associateBy { it.id }
        return episodesByCard.entries
            .mapNotNull { (cardId, episodes) ->
                val card = cards[cardId] ?: return@mapNotNull null
                val bundle = card.jsonObject()?.str("assetbundleName") ?: return@mapNotNull null
                val character = characters[card.jsonObject()?.intOrNull("characterId") ?: 0]
                StoryRow(
                    id = cardId.toString(),
                    title = card.nameZh?.takeIf { it.isNotBlank() } ?: card.name ?: "#$cardId",
                    subtitle = character?.nameZh?.takeIf { it.isNotBlank() } ?: character?.name,
                    imageUrl = AssetUrls.cardIcon(region, bundle, trained = false),
                    trailing = "${episodes.size} 篇",
                    sortAt = card.sortValue,
                )
            }
            .sortedByDescending { it.sortAt }
    }

    // ── 正文 ─────────────────────────────────────────────────

    /**
     * 加载并解析一篇正文。
     *
     * 先试**简中**桶、失败再取**日服**桶 —— 简中服的进度落后于日服，
     * 所以「这一话有没有中文」只能逐话探测。返回的 [StoryScript.source] 告诉 UI 该标哪个徽标。
     */
    suspend fun loadScript(
        type: StoryType,
        assetbundleName: String?,
        scenarioId: String,
    ): Result<StoryScript> = withContext(Dispatchers.IO) {
        val order = listOf(ServerRegion.CN, ServerRegion.JP)
        val char2d = character2dMap()
        var lastError: Throwable? = null
        for (region in order) {
            val url = AssetUrls.storyScenario(region, type.kind, assetbundleName, scenarioId)
            val result = runCatching { fetchAndParse(url, char2d) }
            result.onSuccess { parsed ->
                val source = if (region == ServerRegion.CN) StorySource.CN else StorySource.JP
                return@withContext Result.success(parsed.copy(source = source))
            }
            lastError = result.exceptionOrNull()
        }
        Result.failure(lastError ?: IllegalStateException("正文加载失败"))
    }

    private fun fetchAndParse(url: String, char2dToCharacterId: Map<Int, Int>): StoryScript {
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            val body = response.body?.string() ?: error("响应为空")
            val root = json.parseToJsonElement(body) as? JsonObject ?: error("正文不是 JSON 对象")
            val lines = (root["TalkData"] as? JsonArray).orEmpty().mapNotNull { element ->
                val talk = element as? JsonObject ?: return@mapNotNull null
                val text = talk.str("Body")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                // 说话人头像：Character2dId → character2ds → characterId
                val char2dId = (talk["TalkCharacters"] as? JsonArray).orEmpty()
                    .firstNotNullOfOrNull { (it as? JsonObject)?.intOrNull("Character2dId") }
                StoryLine(
                    speaker = talk.str("WindowDisplayName")?.takeIf { it.isNotBlank() },
                    body = text,
                    voiceId = (talk["Voices"] as? JsonArray).orEmpty()
                        .firstNotNullOfOrNull { (it as? JsonObject)?.str("VoiceId")?.takeIf { v -> v.isNotBlank() } },
                    characterId = char2dId?.let { char2dToCharacterId[it] },
                )
            }
            if (lines.isEmpty()) error("正文里没有任何台词")
            return StoryScript(
                // 调用方会按实际用的区服覆盖掉
                source = StorySource.JP,
                lines = lines,
                firstBackground = root.str("FirstBackground")?.takeIf { it.isNotBlank() },
                bgm = root.str("FirstBgm")?.takeIf { it.isNotBlank() },
            )
        }
    }

    // ── 取行工具 ─────────────────────────────────────────────

    /**
     * 按某个字段取行。
     *
     * `field = "id"` 走主键列（`row.id`），其它字段从行 JSON 里读 ——
     * 注意 `eventStories` 的主键是它自己的 id，而我们要按 **`eventId`** 找，所以两者要分开。
     */
    private suspend fun rowByField(table: String, field: String, value: String) =
        dao.observeAll(table).first().firstOrNull { row ->
            val obj = row.jsonObject() ?: return@firstOrNull false
            when (field) {
                "id" -> row.id.toString() == value
                "eventId" -> obj.intOrNull("eventId")?.toString() == value
                else -> obj.str(field) == value
            }
        }

    /** 某个组合的成员角色 id（按 id 排序）。主线剧情的话列表用它们做头像。 */
    private suspend fun unitMembers(unitKey: String): List<Int> =
        dao.observeAll(TABLE_CHARACTERS).first()
            .filter { it.jsonObject()?.str("unit") == unitKey }
            .map { it.id }
            .sorted()

    /** 某个活动所属组合的成员（`events.unit`；不是组合活动的就返回空）。 */
    private suspend fun eventMembers(eventId: Int): List<Int> {
        val event = dao.observeAll(TABLE_EVENTS).first().firstOrNull { it.id == eventId }
        val unitKey = event?.jsonObject()?.str("unit")?.takeIf { it.isNotBlank() && it != "none" }
            ?: return emptyList()
        return unitMembers(unitKey)
    }


    /** 把活动的起止时间格式化成列表里那一行小字：`2026-08-31 ~ 2026-09-08`。 */
    private fun formatPeriod(startAt: Long?, closedAt: Long?): String? {
        if (startAt == null && closedAt == null) return null
        val start = startAt?.let(::formatDate)
        val end = closedAt?.let(::formatDate)
        return when {
            start != null && end != null -> "$start ~ $end"
            start != null -> "$start 起"
            else -> "$end 止"
        }
    }

    /**
     * 按**日期**判定活动状态。
     *
     * 刻意不依赖任何本地"已读/已完成"状态 —— 只要 master data 同步到了新活动，
     * 它就会自动以「未开始 / 进行中」出现，不需要改代码或重新打包。
     */
    private fun statusOf(startAt: Long?, closedAt: Long?): StoryStatus? {
        if (startAt == null && closedAt == null) return null
        val now = System.currentTimeMillis()
        return when {
            startAt != null && now < startAt -> StoryStatus.UPCOMING
            closedAt != null && now >= closedAt -> StoryStatus.ENDED
            else -> StoryStatus.RUNNING
        }
    }

    private fun formatDate(millis: Long): String =
        java.time.Instant.ofEpochMilli(millis)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDate()
            .toString()

    /** 读一个整数数组字段（`[1,2,3]`）；非数字项直接跳过，不抛异常。 */
    private fun JsonObject.intList(key: String): List<Int> =
        (this[key] as? JsonArray).orEmpty()
            .mapNotNull { (it as? JsonPrimitive)?.content?.toIntOrNull() }

    private companion object {
        const val TABLE_EVENT_STORIES = "eventStories.json"

/**
 * 活动剧情 → 相关的团。**已经在快照里、也已经在数据库里**（2026-09-16 查证）：
 * 它挂在 `MasterCatalog` 的 EVENT 模块下，`manifest.json` 里 `rows: 450`，
 * 走 `TableSchemas` 的默认 schema 导入 —— 所以这个筛选**不需要任何数据侧改动**。
 */
const val TABLE_EVENT_STORY_UNITS = "eventStoryUnits.json"
        const val TABLE_UNIT_STORIES = "unitStories.json"
        const val TABLE_UNIT_PROFILES = "unitProfiles.json"
        const val TABLE_CHARACTER_PROFILES = "characterProfiles.json"
        const val TABLE_CHARACTER_2DS = "character2ds.json"
        const val TABLE_EVENTS = "events.json"
        const val TABLE_CARDS = "cards.json"
        const val TABLE_CARD_EPISODES = "cardEpisodes.json"
        const val TABLE_CHARACTERS = "gameCharacters.json"
    }
}
