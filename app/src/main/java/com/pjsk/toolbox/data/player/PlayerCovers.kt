package com.pjsk.toolbox.data.player

import kotlin.random.Random

/**
 * 播放器的封面来源。
 *
 * - [SONG_JACKET]：原来的行为 —— 显示当前歌曲的曲绘（老用户升上来零感知）。
 * - [CARD_ART]：从用户选定的卡面集合里**随机**挑一张当封面。
 *   附带的好处：封面取色（`data/palette/ArtworkPalette.kt`）会自动跟着变 ——
 *   卡面的色彩比曲绘更强烈，取色效果反而更明显。
 */
enum class PlayerCoverMode(val label: String, val description: String) {
    SONG_JACKET("歌曲封面", "显示当前歌曲的曲绘（默认）"),
    CARD_ART("自定义卡面", "从你选定的卡牌里随机挑一张当封面"),
}

/** 选定集合的大小上限。 */
const val MAX_COVER_CARDS = 200

/**
 * 从选定集合里随机挑一张卡面。
 *
 * ## 为什么不连续重复
 *
 * "随机"最容易被察觉的失败模式是**连着两次给出同一张** —— 用户会以为功能没生效。
 * 所以只要集合里还有别的选择，就排除上一次那张。
 *
 * ## 返回 null 的两种正常情况
 *
 * - 集合为空（没选卡 / 选的卡都不在卡表里了）→ 调用方**回退到歌曲封面**；
 * - 这个函数本身不接受"无效 id"，过滤是调用方的事（它才知道卡表长什么样）。
 *
 * ## 为什么抽成纯函数
 *
 * 随机逻辑错了不会报错，只会"看着不对"。做成纯函数就能进 `tools/logic-check` 断言
 * （不连续重复 / 必在集合内 / 单卡集合稳定 / 空集合返回 null）。
 */
fun pickCoverCardId(
    selected: Collection<Int>,
    previous: Int?,
    random: Random,
): Int? {
    if (selected.isEmpty()) return null
    if (selected.size == 1) return selected.first()
    // 排除上一张；万一排除后为空（理论上不会发生，selected.size > 1）就退回全集
    val candidates = if (previous != null) {
        selected.filter { it != previous }.ifEmpty { selected.toList() }
    } else {
        selected.toList()
    }
    return candidates[random.nextInt(candidates.size)]
}

/**
 * 把一串卡 id 解析成有序去重列表（上限 [MAX_COVER_CARDS]）。
 *
 * 存在偏好里的是逗号分隔的字符串（和 `favoriteSongs` 那套一致，不引入新存储）。
 * 解析要**宽容**：用户手改过的、旧版本写坏的、超量的都不该让播放器崩或卡死。
 */
fun parseCoverCardIds(raw: String?): List<Int> {
    if (raw.isNullOrBlank()) return emptyList()
    return raw.split(',')
        .mapNotNull { it.trim().toIntOrNull() }
        .filter { it > 0 }
        .distinct()
        .take(MAX_COVER_CARDS)
}

/** 反向：把选定集合写进偏好。顺序保留（用户看到的顺序 = 他勾选时的顺序）。 */
fun encodeCoverCardIds(ids: Collection<Int>): String =
    ids.asSequence().filter { it > 0 }.distinct().take(MAX_COVER_CARDS).joinToString(",")
