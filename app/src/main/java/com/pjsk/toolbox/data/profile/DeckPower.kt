package com.pjsk.toolbox.data.profile

import com.pjsk.toolbox.data.card.Card
import com.pjsk.toolbox.data.card.CardEpisode
import com.pjsk.toolbox.data.card.fullStats
import com.pjsk.toolbox.ui.common.intOrNull
import com.pjsk.toolbox.ui.common.parseJsonObjectOrNull
import com.pjsk.toolbox.ui.common.str
import kotlinx.serialization.json.JsonArray

/**
 * 综合力（编成力）的纯计算。
 *
 * ## 公式来源
 *
 * 社区里被 pjsk.moe 署名采用的实现是 **xfl03/sekai-calculator**（LGPL-2.1）。这里照抄它的**分项结构**：
 *
 * ```
 * 单卡 cardTotal = base + areaItemBonus + characterBonus
 * 编成 total    = Σ_5张卡(cardTotal) + honorBonus
 * ```
 *
 * 四部分的口径（逐条对应 `deck-calculator.ts` / `card-power-calculator.ts`）：
 *
 * | 分项 | 口径 |
 * |:--|:--|
 * | `base` | 等级基础值 + 特训加成 + 已读剧情加成 + 突破加成（复用 `Card.fullStats`） |
 * | `areaItemBonus` | 每件区域道具 `rate% × 该卡基础值`，**逐维 floor 后三项相加** |
 * | `characterBonus` | 角色等级加成率 `rate% × 该卡基础值`，逐维 floor 后相加 |
 * | `honorBonus` | 持有称号的 `bonus` **固定值直接相加**（不乘任何百分比） |
 *
 * ## 与参考实现**故意不同**的一处
 *
 * 区域道具的「同组 / 同色额外加成」（`powerNAllMatchBonusRate`）本实现要求
 * **整队 5 张卡都是该组合 / 都是该属性**才生效。
 *
 * 参考实现把「队伍里该组合的卡数」直接当布尔用（≥1 即真），于是**混编队伍里只要有一张
 * 该组合的卡，就会拿到「整队同组」那一档加成** —— 与游戏里「同ユニットのみで編成すると」的
 * 描述不符。这里按游戏描述实现。若真机对拍发现数字偏小，第一个要复查的就是这一处。
 *
 * ## 没有做的部分（明确不假装有）
 *
 * `fixtureBonus`（自定义世界家具）与 `gateBonus`（大门）**没有计算** —— 它们要
 * `mysekaiGateLevels` / `mysekaiGates` 两张表加两个用户 key，而这项加成只对玩烤森的玩家不为 0。
 * 界面会明确写出「不含烤森加成」，否则用户会拿 App 的数字和游戏里的对不上却不知道为什么。
 */

/** `areaItemLevels.json` 裁剪后的一行。 */
data class AreaItemLevelRow(
    val areaItemId: Int,
    val level: Int,
    /** `any` 表示不限；`multi_unit` 是一个**永远不匹配**的特殊值（见类注释）。 */
    val targetUnit: String,
    val targetCardAttr: String,
    /** 0 = 不限角色（上游缺省这个字段表示不限，绝不能当 0 号角色）。 */
    val targetGameCharacterId: Int,
    /** 三项加成率（百分数，如 `2.0` = 2%）。 */
    val rates: List<Double>,
    /** 「整队同组 / 同属」时的三项加成率。 */
    val allMatchRates: List<Double>,
)

/** 区域道具等级表的主键（裁剪器造的 `id = areaItemId × 1000 + level`）。 */
fun areaItemLevelKey(areaItemId: Int, level: Int): Long =
    areaItemId.toLong() * 1000L + level.toLong()

/** 解析 `areaItemLevels.json` 的行 → `(areaItemId, level) → 行`。 */
fun parseAreaItemLevelRows(jsonRows: List<String>): Map<Long, AreaItemLevelRow> {
    val out = HashMap<Long, AreaItemLevelRow>(jsonRows.size * 2)
    for (raw in jsonRows) {
        val obj = raw.parseJsonObjectOrNull() ?: continue
        val areaItemId = obj.intOrNull("areaItemId") ?: continue
        val level = obj.intOrNull("level") ?: continue
        if (areaItemId <= 0 || level <= 0) continue
        out[areaItemLevelKey(areaItemId, level)] = AreaItemLevelRow(
            areaItemId = areaItemId,
            level = level,
            targetUnit = obj.str("targetUnit").orEmpty().ifBlank { "any" },
            targetCardAttr = obj.str("targetCardAttr").orEmpty().ifBlank { "any" },
            targetGameCharacterId = obj.intOrNull("targetGameCharacterId") ?: 0,
            rates = doubleSeries(obj, "r"),
            allMatchRates = doubleSeries(obj, "ar"),
        )
    }
    return out
}

/** 解析区域道具名（`areaItems.json`）→ `id → 名字`。 */
fun parseAreaItemNames(jsonRows: List<String>): Map<Int, String> {
    val out = HashMap<Int, String>(jsonRows.size * 2)
    for (raw in jsonRows) {
        val obj = raw.parseJsonObjectOrNull() ?: continue
        val id = obj.intOrNull("id") ?: continue
        val name = obj.str("name") ?: continue
        out[id] = name
    }
    return out
}

/** 解析称号加成 `honors.json` → `honorId → (level → bonus)`。 */
fun parseHonorBonuses(jsonRows: List<String>): Map<Int, Map<Int, Int>> {
    val out = HashMap<Int, MutableMap<Int, Int>>(64)
    for (raw in jsonRows) {
        val obj = raw.parseJsonObjectOrNull() ?: continue
        val id = obj.intOrNull("id") ?: continue
        val levels = obj["levels"] as? JsonArray ?: continue
        val perLevel = HashMap<Int, Int>(4)
        for (element in levels) {
            val levelObj = (element as? kotlinx.serialization.json.JsonObject) ?: continue
            val level = levelObj.intOrNull("level") ?: continue
            val bonus = levelObj.intOrNull("bonus") ?: 0
            if (level > 0 && bonus > 0) perLevel[level] = bonus
        }
        if (perLevel.isNotEmpty()) out[id] = perLevel
    }
    return out
}

/**
 * 解析 `characterRanks.json` → `(characterId, characterRank) → 三项加成率`。
 *
 * 这张表体积 869 KB（裁剪后），只在算综合力时读一次。
 */
fun parseCharacterRankRates(jsonRows: List<String>): Map<Long, List<Double>> {
    val out = HashMap<Long, List<Double>>(jsonRows.size * 2)
    for (raw in jsonRows) {
        val obj = raw.parseJsonObjectOrNull() ?: continue
        val characterId = obj.intOrNull("characterId") ?: continue
        val rank = obj.intOrNull("characterRank") ?: continue
        if (characterId <= 0 || rank <= 0) continue
        out[characterRankKey(characterId, rank)] = listOf(
            obj.doubleOrNullSafe("power1BonusRate"),
            obj.doubleOrNullSafe("power2BonusRate"),
            obj.doubleOrNullSafe("power3BonusRate"),
        )
    }
    return out
}

/** `(characterId, rank)` 打成一个 Long 键。 */
fun characterRankKey(characterId: Int, rank: Int): Long =
    characterId.toLong() * 1000L + rank.toLong()

/** 综合力的四个分项（都是整数点数，与游戏内显示的量级一致）。 */
data class DeckPowerParts(
    val base: Int,
    val areaItemBonus: Int,
    val characterBonus: Int,
    val honorBonus: Int,
    val total: Int,
    /** 参与计算的卡数（正常是 5；挑战 Live 是 1）。 */
    val cardCount: Int,
    /** 参与计算的区域道具件数（诊断用：如果是 0，说明快照里没有 `userAreas`）。 */
    val areaItemCount: Int,
    /** 参与计算的称号数（诊断用）。 */
    val honorCount: Int,
) {
    companion object {
        /** 四项都为 0 的空结果（界面据此判断"算不出来"）。 */
        val EMPTY = DeckPowerParts(0, 0, 0, 0, 0, 0, 0, 0)
    }
}

/**
 * 算一套编成的综合力。
 *
 * 返回 `null` = **算不出来**（编成为空 / 卡牌表缺这张卡）——
 * 调用方应当整块不显示，而不是显示一个 0（0 会让用户以为是真实数据）。
 *
 * @param deckCards 当前编成的持有卡（最多 5 张）
 * @param cards 内置卡牌表（`cardId → Card`），拿等级数值与属性
 * @param episodesByCard 内置卡牌剧情表（`cardId → episodes`），拿已读剧情的加成
 * @param userCharacterRanks 玩家的角色等级（`characterId → characterRank`）
 * @param characterRankRates `(characterId, rank) → 三项加成率`
 * @param userAreaItems 玩家持有的区域道具（`userAreas[].areaItems[]`）
 * @param areaItemLevels `(areaItemId, level) → 加成率行`
 * @param honorBonuses `honorId → (level → bonus)`
 * @param userHonors 玩家持有的称号
 * @param cardUnits 卡 → 它可能所属的组合（虚拟歌手卡会有多个）。缺省按「没有组合」处理。
 */
fun computeDeckPower(
    deckCards: List<OwnedCard>,
    cards: Map<Int, Card>,
    episodesByCard: Map<Int, List<CardEpisode>>,
    userCharacterRanks: Map<Int, Int>,
    characterRankRates: Map<Long, List<Double>>,
    userAreaItems: List<OwnedAreaItem>,
    areaItemLevels: Map<Long, AreaItemLevelRow>,
    honorBonuses: Map<Int, Map<Int, Int>>,
    userHonors: List<OwnedHonor>,
    cardUnits: (Card) -> List<String> = { emptyList() },
): DeckPowerParts? {
    if (deckCards.isEmpty()) return null

    // ── 1. 每张卡的基础三维 + 属性 / 组合 ──
    data class Entry(val owned: OwnedCard, val card: Card, val base: List<Int>)

    val entries = deckCards.mapNotNull { owned ->
        val card = cards[owned.cardId] ?: return@mapNotNull null
        val readEpisodes = resolveReadEpisodes(episodesByCard[card.id].orEmpty(), owned)
        val stats = card.fullStats(
            level = owned.level.coerceAtLeast(1),
            applyTrainingBonus = owned.trained,
            masterRank = owned.masterRank,
            episodeBonus = com.pjsk.toolbox.data.card.sumEpisodeBonus(readEpisodes),
        )
        Entry(owned, card, listOf(stats.vocal, stats.dance, stats.visual))
    }
    if (entries.isEmpty()) return null

    // ── 2. 队伍级的「整队同组 / 同属」判定（见类注释的"故意不同"一节） ──
    val deckUnitSets = entries.map { cardUnits(it.card) }
    val deckAttrs = entries.map { it.card.attr?.key }.distinct()
    val allSameAttr = deckAttrs.size == 1 && deckAttrs.first() != null

    fun allCardsBelongTo(unit: String): Boolean =
        deckUnitSets.all { units -> unit in units }

    // ── 3. 逐卡累加三个有加成率的分项 ──
    var baseTotal = 0
    var areaItemTotal = 0
    var characterTotal = 0
    val usedAreaItemIds = LinkedHashSet<Int>()

    for (entry in entries) {
        baseTotal += entry.base.sum()

        // 3a. 区域道具
        val areaAcc = DoubleArray(3)
        for (owned in userAreaItems) {
            val row = areaItemLevels[areaItemLevelKey(owned.areaItemId, owned.level)] ?: continue
            if (row.targetUnit != "any" && row.targetUnit != "multi_unit") {
                if (row.targetUnit !in cardUnits(entry.card)) continue
            } else if (row.targetUnit == "multi_unit") {
                // 参考实现也永远不匹配这一档（targetUnit 只能是 any 或卡自己的组合）
                continue
            }
            if (row.targetCardAttr != "any" && row.targetCardAttr != entry.card.attr?.key) continue
            if (row.targetGameCharacterId > 0 && row.targetGameCharacterId != entry.card.characterId) continue

            val allMatch =
                (row.targetUnit != "any" && allCardsBelongTo(row.targetUnit)) ||
                    (row.targetCardAttr != "any" && allSameAttr)
            val rates = if (allMatch) row.allMatchRates else row.rates
            for (i in 0..2) {
                areaAcc[i] += (rates.getOrElse(i) { 0.0 }) / 100.0 * entry.base.getOrElse(i) { 0 }
            }
            usedAreaItemIds.add(owned.areaItemId)
        }
        // 逐维 floor 后三项相加（顺序不能改，否则末尾会差几点）
        areaItemTotal += (0..2).sumOf { kotlin.math.floor(areaAcc[it]).toInt() }

        // 3b. 角色等级
        val rank = userCharacterRanks[entry.card.characterId] ?: 0
        val rankRates = characterRankRates[characterRankKey(entry.card.characterId, rank)].orEmpty()
        characterTotal += (0..2).sumOf { i ->
            kotlin.math.floor(rankRates.getOrElse(i) { 0.0 } / 100.0 * entry.base.getOrElse(i) { 0 }).toInt()
        }
    }

    // ── 4. 称号：固定值直接相加，不乘任何倍率 ──
    var honorTotal = 0
    var honorCount = 0
    for (honor in userHonors) {
        val bonus = honorBonuses[honor.honorId]?.get(honor.level) ?: continue
        honorTotal += bonus
        honorCount++
    }

    return DeckPowerParts(
        base = baseTotal,
        areaItemBonus = areaItemTotal,
        characterBonus = characterTotal,
        honorBonus = honorTotal,
        total = baseTotal + areaItemTotal + characterTotal + honorTotal,
        cardCount = entries.size,
        areaItemCount = usedAreaItemIds.size,
        honorCount = honorCount,
    )
}

/**
 * 把 `userCards[].episodes` 的原始键解析成**已读的那些 CardEpisode**。
 *
 * 键的含义上游没有文档，所以两种口径都试：
 *  1. 当成 `cardEpisodeId`（社区实现里的写法）；
 *  2. 当成篇号 `1/2`。
 *
 * 两种都能对上的话以 id 优先（更具体）。一个都对不上就返回空列表 ——
 * 那时综合力会少算剧情加成，但绝不编一个数字。
 */
fun resolveReadEpisodes(episodes: List<CardEpisode>, owned: OwnedCard): List<CardEpisode> {
    if (episodes.isEmpty() || owned.readEpisodeKeys.isEmpty()) return emptyList()
    val byId = episodes.filter { it.id in owned.readEpisodeKeys }
    if (byId.isNotEmpty()) return byId
    return episodes.filter { it.part in owned.readEpisodeKeys }
}

// ── 小工具 ──

private fun doubleSeries(
    obj: kotlinx.serialization.json.JsonObject,
    key: String,
): List<Double> {
    val array = obj[key] as? JsonArray ?: return listOf(0.0, 0.0, 0.0)
    return array.mapNotNull { it.toString().trim('"').toDoubleOrNull() }
}

private fun kotlinx.serialization.json.JsonObject.doubleOrNullSafe(key: String): Double =
    (this[key]?.toString()?.trim('"'))?.toDoubleOrNull() ?: 0.0

