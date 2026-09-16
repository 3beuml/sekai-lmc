package com.pjsk.toolbox.data.story

import com.pjsk.toolbox.data.db.MasterRowEntity
import com.pjsk.toolbox.ui.common.intOrNull
import com.pjsk.toolbox.ui.common.jsonObject
import com.pjsk.toolbox.ui.common.str
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 剧情资源的类型。**决定正文的 URL 形状**（见 `AssetUrls.storyScenario`），
 * 所以它不只是 UI 分类，也是资源定位的一部分。
 */
enum class StoryAssetKind {
    UNIT,
    EVENT,
    CARD,
    AREA,
    SELF,
    SPECIAL,
}

/**
 * 剧情分类。顺序即「筛选」面板里的显示顺序（对齐 pjsk.moe 的 breadcrumb-story）。
 *
 * ⚠️ [AREA]（区域对话）本轮**没有数据**：它需要 `actionSets.json`（实测 **956 KB**）
 * 和 `areas.json`（区域名），为它单独加近 1 MB 的表不划算，先留入口 + 说明。
 */
enum class StoryType(
    val key: String,
    val label: String,
    val kind: StoryAssetKind,
) {
    UNIT("unit", "主线剧情", StoryAssetKind.UNIT),
    EVENT("event", "活动剧情", StoryAssetKind.EVENT),
    CARD("card", "卡牌剧情", StoryAssetKind.CARD),
    ;

    companion object {
        /** 默认分类：用户要求剧情页默认展示**活动**。 */
        val DEFAULT = EVENT

        /**
         * 按 key 找分类。**`self`（自我介绍）已被用户要求删除**，
         * 但老路由 / 老状态里可能还留着这个 key，所以要能优雅退化到默认分类而不是崩。
         */
        fun of(key: String?): StoryType = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

/**
 * 活动的进行状态（按**日期**判定，不依赖任何"已读/已完成"的本地状态）。
 *
 * 这也是「数据能不能跟上更新」的可见部分：只要 master data 同步到了新活动，
 * 它就会自动以「未开始 / 进行中」出现，不需要改代码。
 */
enum class StoryStatus(val label: String) {
    UPCOMING("未开始"),
    RUNNING("进行中"),
    ENDED("已结束"),
}

/**
 * 剧情列表的**第 1 层**行。
 *
 * 6 种类型的「第一层」语义完全不同（活动 / 组合 / 卡 / 区域 / 角色 / 特殊条目），
 * 所以用一个模型收口 —— 否则要写 6 套列表页。
 *
 * @param id 打开第 2 层用的 id（活动 id / 卡 id / 组合 key / 特殊条目 id）
 */
data class StoryRow(
    val id: String,
    val title: String,
    val subtitle: String?,
    /** 缩略图（活动 logo / 卡面 / **角色头像**）；没有就显示占位。 */
    val imageUrl: String?,
    /** 右侧小字，如「8 话」「2 篇」。 */
    val trailing: String?,
    /** 排序用：活动/特殊用开放时间，其它用 id。 */
    val sortAt: Long,
    /** 活动的进行状态；非活动（主线/其它）为 null。 */
    val status: StoryStatus? = null,
    /**
     * **不为 null 时点这一行直接进阅读器**，不再经过第 2 层。
     *
     * 用户明确要求：自我介绍、特殊剧情**不要点两次**。
     * 前者一个角色只有一篇，后者把「话」拍平成行 —— 两者都没有「中间列表」的价值。
     */
    val directScenarioId: String? = null,
    /** 配合 [directScenarioId]：正文所在的 bundle（自我介绍为 null）。 */
    val directBundle: String? = null,
    /**
     * **活动剧情**相关的团（`eventStoryUnits.json` 归约出来的；其它类型为空）。
     *
     * 实测：214 个活动剧情**全部**有行，`unit` 只有 5 个团 + 5 行 `none`（已归到 `other`）
     * —— 所以"虚拟歌手没有活动剧情"是数据自己说的，不用写特判。见 `data/story/StoryQuery.kt`。
     */
    val unitKeys: List<String> = emptyList(),
)

/**
 * 剧情列表的**第 2 层**：一话（或一篇）。
 *
 * @param assetbundleName 交给 [com.pjsk.toolbox.data.remote.AssetUrls.storyScenario] 定位资源。
 *   注意**各类型含义不同**：活动=活动bundle、卡牌=卡bundle、主线=**章节**bundle、
 *   自我介绍=null、特殊=该话的 bundle。
 */
data class StoryEntry(
    val title: String,
    /** 「第1話」「オープニング」「前篇」这类标签。 */
    val label: String?,
    val scenarioId: String,
    val assetbundleName: String?,
    /**
     * 这一段出场的角色 id。**区域对话的第 2 层用它显示头像** ——
     * `actionSets.characterIds` 给的就是这一段对话的出场角色（通常 1~3 个）。
     */
    val characterIds: List<Int> = emptyList(),
)

/** 正文来源。简中服进度落后，所以**按话**判定，同一活动里也可能有的有中文有的没有。 */
enum class StorySource(val label: String) {
    CN("国服"),
    JP("日服"),
}

/** 正文里的一句台词。 */
data class StoryLine(
    val speaker: String?,
    val body: String,
    /** 语音文件名（不含扩展名）；没有配音时为 null。 */
    val voiceId: String?,
    /**
     * 说话人的**角色 id**（由 `TalkCharacters[].Character2dId` 经 `character2ds` 映射而来）。
     * 用来显示头像 —— 没有它就只能靠名字猜，而名字在日文/中文之间还不一致。
     */
    val characterId: Int?,
)

/** 一篇解析好的正文。 */
data class StoryScript(
    val source: StorySource,
    val lines: List<StoryLine>,
    /** 开场背景，用作页面氛围底图；取不到就是 null。 */
    val firstBackground: String?,
    val bgm: String?,
)

// ─────────────────────────────────────────────────────────────
// 解析
// ─────────────────────────────────────────────────────────────

/**
 * 从一条数据库行解析出「活动剧情」的第 1 层行。
 *
 * ⚠️ `eventStories.json` 每个活动**只有一行**，各话内联在 `eventStoryEpisodes[]` 里
 * —— 所以话数要数数组长度，不能按行数算。
 */
fun parseEventStoryRow(
    row: MasterRowEntity,
    eventName: String?,
    eventBundle: String?,
    eventStartAt: Long,
    /** 这个活动剧情相关的团（由 `eventStoryUnits.json` 归约，见 [eventStoryUnitKeys]）。 */
    unitKeys: List<String> = emptyList(),
): StoryRow? {
    val obj = row.jsonObject() ?: return null
    val eventId = obj.intOrNull("eventId") ?: return null
    val episodes = obj.arraySize("eventStoryEpisodes")
    val chapters = obj.arraySize("chapters")
    if (episodes == 0 && chapters == 0) return null
    return StoryRow(
        id = eventId.toString(),
        title = eventName ?: "活动 #$eventId",
        subtitle = row.jsonObject()?.str("outline")?.lineSequence()?.firstOrNull()?.take(60),
        imageUrl = null, // 缩略图由调用方按 region 拼（这里拿不到 region）
        trailing = "$episodes 话",
        sortAt = eventStartAt,
        unitKeys = unitKeys,
    )
}

/** 解析「主线剧情」第 1 层行（`unitStories.json` 只有 6 行，一行一个组合）。 */
fun parseUnitStoryRow(row: MasterRowEntity, unitLabel: String?): StoryRow? {
    val obj = row.jsonObject() ?: return null
    val unitKey = obj.str("unit") ?: return null
    val chapters = obj["chapters"] as? JsonArray ?: return null
    val episodes = chapters.sumOf { chapter ->
        ((chapter as? JsonObject)?.get("episodes") as? JsonArray)?.size ?: 0
    }
    if (episodes == 0) return null
    return StoryRow(
        id = unitKey,
        title = unitLabel ?: unitKey,
        subtitle = "${chapters.size} 章",
        imageUrl = null,
        trailing = "$episodes 话",
        sortAt = obj.intOrNull("seq")?.toLong() ?: 0L,
    )
}

/** 第 2 层：活动的一话。 */
fun eventStoryEntries(row: MasterRowEntity): List<StoryEntry> {
    val obj = row.jsonObject() ?: return emptyList()
    val bundle = obj.str("assetbundleName")
    return (obj["eventStoryEpisodes"] as? JsonArray).orEmpty().mapNotNull { element ->
        val ep = element as? JsonObject ?: return@mapNotNull null
        val scenarioId = ep.str("scenarioId") ?: return@mapNotNull null
        StoryEntry(
            title = ep.str("title") ?: "第 ${ep.intOrNull("episodeNo") ?: 0} 话",
            label = "第 ${ep.intOrNull("episodeNo") ?: 0} 话",
            scenarioId = scenarioId,
            assetbundleName = bundle,
        )
    }
}

/** 第 2 层：主线的一话（跨章节拍平；`assetbundleName` 用**章节**的）。 */
fun unitStoryEntries(row: MasterRowEntity): List<StoryEntry> {
    val obj = row.jsonObject() ?: return emptyList()
    val chapters = obj["chapters"] as? JsonArray ?: return emptyList()
    val out = ArrayList<StoryEntry>()
    chapters.forEach { chapter ->
        val ch = chapter as? JsonObject ?: return@forEach
        val chapterBundle = ch.str("assetbundleName")
        (ch["episodes"] as? JsonArray).orEmpty().forEach { element ->
            val ep = element as? JsonObject ?: return@forEach
            val scenarioId = ep.str("scenarioId") ?: return@forEach
            out += StoryEntry(
                title = ep.str("title") ?: scenarioId,
                label = ep.str("episodeNoLabel"),
                scenarioId = scenarioId,
                // ⚠️ 主线正文在**章节**的 bundle 下，不是 episode 的
                assetbundleName = chapterBundle,
            )
        }
    }
    return out
}

/** 第 2 层：特殊剧情的一话。 */
fun specialStoryEntries(row: MasterRowEntity): List<StoryEntry> {
    val obj = row.jsonObject() ?: return emptyList()
    return (obj["episodes"] as? JsonArray).orEmpty().mapNotNull { element ->
        val ep = element as? JsonObject ?: return@mapNotNull null
        val scenarioId = ep.str("scenarioId") ?: return@mapNotNull null
        StoryEntry(
            title = ep.str("title") ?: scenarioId,
            label = null,
            scenarioId = scenarioId,
            assetbundleName = ep.str("assetbundleName"),
        )
    }
}

// ── JSON 小工具（局部使用，避免污染公共 API）──────────────

private fun JsonObject.arraySize(key: String): Int = (this[key] as? JsonArray)?.size ?: 0

private fun JsonObject.longOrNullCompat(key: String): Long? =
    (this[key] as? JsonPrimitive)?.content?.toDoubleOrNull()?.toLong()
