package com.pjsk.toolbox.data.card

import com.pjsk.toolbox.data.db.MasterRowEntity
import com.pjsk.toolbox.ui.common.intOrNull
import com.pjsk.toolbox.ui.common.jsonObject
import com.pjsk.toolbox.ui.common.str
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 卡牌剧情（`cardEpisodes.json`）的一篇。
 *
 * 实测（2026-09 快照，2768 行）：每张卡最多 2 篇，`cardEpisodePartType` 取值
 * `first_part` / `second_part`。**1447 张卡里有 63 张没有任何剧情**（例如 `id=1462`），
 * 所以 UI 必须能优雅地显示「这张卡没有剧情」，而不是空白。
 */
data class CardEpisode(
    val id: Int,
    val cardId: Int,
    /** 1 = 前篇，2 = 后篇。由 `cardEpisodePartType` 归一化而来。 */
    val part: Int,
    val title: String?,
    /**
     * 剧本资源 id（如 `001001_ichika01`）。
     *
     * ⚠️ 这只是**剧本文件的键**，正文不在 master data 里 —— 要读正文得另外下载
     * scenario 资源并解析剧本格式，属于「以后做」的范围。这里保留它只是为了
     * 将来接正文时不用改数据层。
     */
    val scenarioId: String?,
    val releaseConditionId: Int?,
    /**
     * 读完后给三项能力各加多少（下标对应 表现力/技巧/体能）。
     *
     * ⚠️ **不是固定值，是分档的**。全量实测（2768 行 / 1384 张有剧情的卡）只有 5 种组合，
     * 且**三项永远相等**（不相等的有 0 条）：
     *
     * | 前篇 | 后篇 | 卡数 |
     * |---|---|---|
     * | +250 | +600 | 742 |
     * | +150 | +300 | 265 |
     * | +200 | +500 | 221 |
     * | +240 | +550 | 130 |
     * | +100 | +200 | 26 |
     *
     * 所以「一篇给多少」必须从数据读，不能写死 —— 一开始我按 `id=1` 那一条推测是固定 +100，
     * 结果 `id=1468` 实测前篇 +200 / 后篇 +500，差了一倍以上。
     */
    val powerBonus: List<Int>,
    val costs: List<EpisodeCost>,
    val rewardResourceBoxIds: List<Int>,
) {
    /** 前篇 / 后篇。 */
    val partLabel: String get() = if (part == 2) "后篇" else "前篇"

    /** 剧情加成合计（三项之和）。 */
    val totalPowerBonus: Int get() = powerBonus.sum()
}

/** 开放一篇剧情需要的素材。 */
data class EpisodeCost(
    val resourceId: Int,
    val resourceType: String?,
    val quantity: Int,
)

/** 从数据库行解析一篇剧情。 */
fun parseCardEpisode(row: MasterRowEntity): CardEpisode? {
    val obj = row.jsonObject() ?: return null
    val cardId = obj.intOrNull("cardId") ?: return null
    val partType = obj.str("cardEpisodePartType").orEmpty()
    val part = when {
        partType.contains("second") -> 2
        partType.contains("first") -> 1
        // 万一将来出现第三篇 / 改了命名：已有的按顺序占位，不要丢掉整行数据
        else -> obj.intOrNull("seq") ?: 1
    }
    return CardEpisode(
        id = obj.intOrNull("id") ?: row.id,
        cardId = cardId,
        part = part,
        title = obj.str("title")?.takeIf { it.isNotBlank() },
        scenarioId = obj.str("scenarioId")?.takeIf { it.isNotBlank() },
        releaseConditionId = obj.intOrNull("releaseConditionId"),
        powerBonus = obj.intSeries("power1BonusFixed", "power2BonusFixed", "power3BonusFixed"),
        costs = obj.episodeCosts(),
        rewardResourceBoxIds = obj.intSeriesOf("rewardResourceBoxIds"),
    )
}

private fun JsonObject.episodeCosts(): List<EpisodeCost> =
    (this["costs"] as? JsonArray)
        ?.mapNotNull { element ->
            val c = element as? JsonObject ?: return@mapNotNull null
            val resourceId = c.intOrNull("resourceId") ?: return@mapNotNull null
            EpisodeCost(
                resourceId = resourceId,
                resourceType = c.str("resourceType"),
                quantity = c.intOrNull("quantity") ?: 0,
            )
        }
        ?: emptyList()

/** 依次读若干同类型的整数字段。 */
private fun JsonObject.intSeries(vararg keys: String): List<Int> =
    keys.map { intOrNull(it) ?: 0 }

/** 读一个整数数组字段。 */
private fun JsonObject.intSeriesOf(key: String): List<Int> =
    (this[key] as? JsonArray)
        ?.mapNotNull { (it as? JsonPrimitive)?.content?.toDoubleOrNull()?.toInt() }
        ?: emptyList()

// ─────────────────────────────────────────────────────────────
// 所属活动
// ─────────────────────────────────────────────────────────────

/**
 * 一张卡所属的活动（用于卡片详情里显示「出自活动」+ 活动 logo）。
 *
 * 关系来自 `eventCards.json`（实测 1110 行，字段 `cardId` / `eventId` / `isDisplayCardStory`），
 * 展示用的名字与素材名来自 `events.json`。
 *
 * ⚠️ **不是每张卡都有活动**：实测 `#1462`（联动限定）、`#1`（最早期卡）都查不到，
 * 这种卡不该显示这个区块。`cardEpisodes.json` 和 `eventCards.json` 是两个独立的表 ——
 * 「有剧情」和「有活动」互不蕴含。
 */
data class CardEvent(
    val id: Int,
    /** 素材名，用来拼活动 logo（`event/<bundle>/logo/logo.webp`）。 */
    val assetbundleName: String,
    /** 简中名优先，缺失时回退日文名。 */
    val name: String?,
)

/** 从数据库行解析一个活动。 */
fun parseCardEvent(row: MasterRowEntity): CardEvent? {
    val bundle = (row.jsonObject()?.str("assetbundleName"))?.takeIf { it.isNotBlank() } ?: return null
    return CardEvent(
        id = row.id,
        assetbundleName = bundle,
        name = row.nameZh?.takeIf { it.isNotBlank() }
            ?: row.name?.takeIf { it.isNotBlank() },
    )
}

// ─────────────────────────────────────────────────────────────
// 突破（Master Rank）
// ─────────────────────────────────────────────────────────────

/** 突破等级上限。实测 `masterLessons.json` 里每个稀有度都是 1..5。 */
const val MASTER_RANK_MAX: Int = 5

/**
 * 突破**每级**给三项能力各加多少。
 *
 * 来源：`masterLessons.json`（实测 25 行 = 5 稀有度 × 5 级；同一稀有度的 5 个等级
 * 加成完全相同，所以「突破 N 级」= N × 本表的值）。
 * 实测值：1★ 50 / 2★ 100 / 3★ 150 / 4★ 200 / 生日卡 180。
 *
 * **为什么写死在代码里而不是进 data pack**：这张表只有 5 个数、且进游戏以来没变过；
 * 而它按「稀有度」而不是按「卡」建表，加进 pack 要动导入器 / 表结构 / 投影器三处，
 * 并重建 11 MB 的包，收益不匹配。sekai.best 也是写死的
 * （`CardDetail.tsx`: `const masterRankRewards = [0, 50, 100, 150, 200]`）。
 *
 * 顺带一提：他们那个数组没有生日卡那一项，`rarity_birthday` 会取到 `undefined`；
 * 我们这里补上了实测值 180。
 */
val MASTER_RANK_BONUS: Map<String, Int> = mapOf(
    "rarity_1" to 50,
    "rarity_2" to 100,
    "rarity_3" to 150,
    "rarity_4" to 200,
    "rarity_birthday" to 180,
)

/** 该稀有度突破每级的加成；未知稀有度返回 0（不显示突破区）。 */
fun masterRankBonusOf(rarityKey: String?): Int = MASTER_RANK_BONUS[rarityKey] ?: 0

/**
 * 能力值公式的**唯一实现**（对齐 sekai.best `CardDetail.tsx` 的算法）：
 *
 * ```
 * 最终值 = 等级对应基础值
 *        + 特训加成（等级超过特训前上限时）
 *        + 已读剧情的加成
 *        + 突破等级 × 每级加成
 * ```
 *
 * @param episodeBonus 已勾选「已读」的剧情加成之和（`[前篇, 后篇]` 相加后的三项）。
 * @param masterRank 突破等级 0..[MASTER_RANK_MAX]。
 */
fun Card.fullStats(
    level: Int,
    applyTrainingBonus: Boolean,
    masterRank: Int = 0,
    episodeBonus: List<Int> = emptyList(),
): CardStats {
    val base = statsAt(level, applyTrainingBonus = applyTrainingBonus)
    val rankBonus = masterRank.coerceIn(0, MASTER_RANK_MAX) * masterRankBonusOf(rarityKey)
    return CardStats(
        vocal = base.vocal + episodeBonus.getOrElse(0) { 0 } + rankBonus,
        dance = base.dance + episodeBonus.getOrElse(1) { 0 } + rankBonus,
        visual = base.visual + episodeBonus.getOrElse(2) { 0 } + rankBonus,
    )
}

/** 把若干篇已读剧情的加成逐项相加。 */
fun sumEpisodeBonus(episodes: List<CardEpisode>): List<Int> {
    if (episodes.isEmpty()) return listOf(0, 0, 0)
    return List(3) { index -> episodes.sumOf { it.powerBonus.getOrElse(index) { 0 } } }
}
