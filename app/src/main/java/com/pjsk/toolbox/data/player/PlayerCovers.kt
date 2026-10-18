package com.pjsk.toolbox.data.player

import kotlin.random.Random

/**
 * 播放器的封面来源。
 *
 * - [SONG_JACKET]：原来的行为 —— 显示当前歌曲的曲绘（默认，老用户零感知）。
 * - [CARD_ART]：按 [CoverFilter] 的条件从卡表里挑一张卡面当封面。
 *   附带的好处：封面取色（`data/palette/ArtworkPalette.kt`）会自动跟着变。
 */
enum class PlayerCoverMode(val label: String, val description: String) {
    SONG_JACKET("歌曲封面", "显示当前歌曲的曲绘（默认）"),
    CARD_ART("自定义卡面", "按下面的条件挑卡面当封面"),
}

/** 封面比例。 */
enum class CoverAspect(val label: String, val description: String) {
    /** 正方形。卡面原图是 2520×1440（16:9），裁成方形会切掉左右各 21%（保中间 56%）。 */
    SQUARE("方形", "和曲绘一样是正方形，视觉上最满"),

    /** 16:9 横幅。整张卡面不裁构图，但上下会留空 —— 播放器会给它配一层模糊铺底。 */
    WIDE("16:9", "完整卡面不裁构图，配模糊铺底填空隙"),
}

/** 用卡面的哪一版。 */
enum class CoverVariant(val label: String, val description: String) {
    NORMAL("觉醒前", "只用未觉醒卡面"),
    TRAINED("觉醒后", "只用觉醒后卡面（★1/★2/生日卡没有这一版，会被排除）"),
    BOTH("都要", "两版都参与随机，各占一半权重"),
}

/**
 * 封面选卡的**筛选条件**。
 *
 * ## 为什么存条件而不是存卡 id 列表
 *
 * 用户的心智是"我要一歌的 ★4 卡面"这种**条件**，而不是"这 37 张卡的清单"：
 * 新卡上线后条件天然把它们包含进来（这正是"便于筛选增加"的含义），
 * 而且存储只有几个字段，不用管上限。
 *
 * 代价：卡表更新后同一条件命中会变。所以界面上必须**实时显示命中张数**，
 * 让用户看得见条件的后果 —— 不能让他以为"选了就有"。
 *
 * ## 空条件的语义
 *
 * `characterIds` 为空 = **不限角色**（等于全角色）；`rarityKeys` 为空 = **不限星级**。
 * 这与卡牌页的筛选口径一致（`CardQuery` 里空集合就是不筛）。
 */
data class CoverFilter(
    val characterIds: Set<Int> = emptySet(),
    val rarityKeys: Set<String> = emptySet(),
    val variant: CoverVariant = CoverVariant.BOTH,
) {
    /** 是否至少限定了一个维度（否则等于"全卡表随机"，应该提示用户去收窄）。 */
    val isNarrowed: Boolean get() = characterIds.isNotEmpty() || rarityKeys.isNotEmpty()
}

/** 挑卡只需要这几个字段，抽成小结构：纯函数可断言，也不用把整个 Card 拖进判定逻辑。 */
data class CoverCandidate(
    val id: Int,
    val characterId: Int,
    val rarityKey: String,
    /** 这张卡**有没有**觉醒后卡面（★1/★2/生日卡没有）。 */
    val trainedAvailable: Boolean,
)

/** 一次挑选的结果：哪张卡 + 用哪一版卡面。 */
data class CoverPick(val cardId: Int, val trained: Boolean)

/** 卡面上可选的稀有度 key，顺序即界面顺序。 */
val COVER_RARITY_KEYS: List<String> = listOf(
    "rarity_4",
    "rarity_3",
    "rarity_2",
    "rarity_1",
    "rarity_birthday",
)

/** 稀有度的界面文案。 */
fun coverRarityLabel(key: String): String = when (key) {
    "rarity_4" -> "★4"
    "rarity_3" -> "★3"
    "rarity_2" -> "★2"
    "rarity_1" -> "★1"
    "rarity_birthday" -> "生日"
    else -> key
}

/** 这张卡满不满足筛选条件（角色 / 星级两维）。 */
fun matchesCoverFilter(candidate: CoverCandidate, filter: CoverFilter): Boolean {
    if (filter.characterIds.isNotEmpty() && candidate.characterId !in filter.characterIds) return false
    if (filter.rarityKeys.isNotEmpty() && candidate.rarityKey !in filter.rarityKeys) return false
    return true
}

/**
 * 把候选卡按 [CoverFilter.variant] 展开成"可挑的（卡, 版本）对"。
 *
 * 展开而不是"先挑卡再挑版本"，是为了让「都要」这个选项有明确含义：
 * 两版各占**一半权重**，而不是"随机挑卡再随机挑版本"（后者在混有
 * 无觉醒后卡的情况下权重会失真）。
 *
 * ⚠️ `variant = TRAINED` 时，没有觉醒后卡面的卡（★1/★2/生日）**会被整体排除** ——
 * 这不是 bug，是唯一正确的做法（否则只能显示一张 404）。界面必须把排除的张数说出来，
 * 否则用户会以为"★1 加进去怎么没有"。
 */
fun expandCoverVariants(candidates: List<CoverCandidate>, variant: CoverVariant): List<CoverPick> =
    candidates.flatMap { candidate ->
        when (variant) {
            CoverVariant.NORMAL -> listOf(CoverPick(candidate.id, trained = false))
            CoverVariant.TRAINED ->
                if (candidate.trainedAvailable) listOf(CoverPick(candidate.id, trained = true)) else emptyList()
            CoverVariant.BOTH ->
                if (candidate.trainedAvailable) {
                    listOf(CoverPick(candidate.id, trained = false), CoverPick(candidate.id, trained = true))
                } else {
                    listOf(CoverPick(candidate.id, trained = false))
                }
        }
    }

/**
 * 从可挑集合里随机取一个。
 *
 * "随机"最容易被察觉的失败模式是**连着两次给出同一张卡**（同一张卡的两个版本算同一张）——
 * 用户会以为功能没生效。所以只要还有别的卡，就排除上一次那张。
 */
fun pickCover(picks: List<CoverPick>, previousCardId: Int?, random: Random): CoverPick? {
    if (picks.isEmpty()) return null
    if (picks.size == 1) return picks.first()
    val candidates = if (previousCardId != null) {
        picks.filter { it.cardId != previousCardId }.ifEmpty { picks }
    } else {
        picks
    }
    return candidates[random.nextInt(candidates.size)]
}

// ── 偏好编解码（只处理字符串 ↔ 集合，容错优先）──

/** 逗号分隔的正整数集合。脏数据（空/非数字/0/负数）静默丢掉。 */
fun parseIdSet(raw: String?): Set<Int> {
    if (raw.isNullOrBlank()) return emptySet()
    return raw.split(',')
        .mapNotNull { it.trim().toIntOrNull() }
        .filter { it > 0 }
        .toSet()
}

fun encodeIdSet(ids: Collection<Int>): String =
    ids.filter { it > 0 }.distinct().sorted().joinToString(",")

/**
 * 稀有度 key 集合。**只保留已知的五个 key** —— 手改过的偏好、或以后上游改了 key 名，
 * 丢掉的后果是"筛不到任何卡"，界面上会显示命中 0 张，比静默塞进一个无效 key 好排查。
 */
fun parseRarityKeys(raw: String?): Set<String> {
    if (raw.isNullOrBlank()) return emptySet()
    return raw.split(',')
        .map { it.trim() }
        .filter { it in COVER_RARITY_KEYS }
        .toSet()
}

fun encodeRarityKeys(keys: Collection<String>): String =
    keys.filter { it in COVER_RARITY_KEYS }.distinct().joinToString(",")
