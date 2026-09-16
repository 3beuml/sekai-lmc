package com.pjsk.toolbox.data.card

import androidx.compose.ui.graphics.Color
import com.pjsk.toolbox.data.db.MasterRowEntity
import com.pjsk.toolbox.data.settings.NameLanguage
import com.pjsk.toolbox.ui.common.intOrNull
import com.pjsk.toolbox.ui.common.jsonObject
import com.pjsk.toolbox.ui.common.longOrNull
import com.pjsk.toolbox.ui.common.str
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 卡牌属性。
 *
 * 颜色按**游戏内配色**取（COOL 蓝 / CUTE 粉 / HAPPY 黄 / MYSTERIOUS 紫 / PURE 绿）。
 * 说明：网页版把 PURE 写成了近白色 `hsl(0 0% 95%)`，但 PURE 在游戏里是绿色，这里纠正过来。
 * 另外这几个色值是按游戏观感手调的十六进制，不是从官方素材里取的（不复制官方素材是本项目的原则）。
 */
enum class CardAttr(val key: String, val label: String, val color: Color) {
    COOL("cool", "COOL", Color(0xFF4799EB)),
    CUTE("cute", "CUTE", Color(0xFFF075B3)),
    HAPPY("happy", "HAPPY", Color(0xFFFFCC33)),
    MYSTERIOUS("mysterious", "MYSTERIOUS", Color(0xFFA667E4)),
    PURE("pure", "PURE", Color(0xFF47D18C)),
    ;

    companion object {
        fun of(key: String?): CardAttr? = entries.firstOrNull { it.key == key }
    }
}

/**
 * 组合。key 是 master data 里 `gameCharacters.unit` 的取值。
 *
 * ⚠️ **虚拟歌手的 key 是 `piapro`，不是 `virtual_singer`** —— 这是踩过的坑：
 * 实测 `gameCharacters.json` 里 id 21–26（初音ミク / 鏡音リン / 鏡音レン / 巡音ルカ /
 * MEIKO / KAITO）的 `unit` 字段全是 `piapro`。早先把枚举 key 写成字面量
 * `"virtual_singer"`，于是 [of] 对这 6 个人一律返回 null，连锁后果有三个：
 *  1. 角色筛选面板里「VIRTUAL SINGER」这一组永远是空的；
 *  2. 这 6 个人不属于任何 [UNIT_ORDER] 分组，**在面板里彻底消失**；
 *  3. 按组合筛选虚拟歌手，命中 0 张卡。
 *
 * [of] 仍然接受 `virtual_singer` 这种写法（[AssetUrls.unitLogo] 等地方也统一按
 * 两个写法都认），免得别处按字面量查到 null。
 */
enum class CardUnit(val key: String, val label: String) {
    VIRTUAL_SINGER("piapro", "VIRTUAL SINGER"),
    LEO_NEED("light_sound", "Leo/need"),
    MORE_MORE_JUMP("idol", "MORE MORE JUMP!"),
    VIVID_BAD_SQUAD("street", "Vivid BAD SQUAD"),
    WONDERLANDS("theme_park", "ワンダーランズ×ショウタイム"),
    NIIGO("school_refusal", "25時、ナイトコードで。"),
    ;

    companion object {
        fun of(key: String?): CardUnit? = when (key) {
            // 兼容写错的别名：历史上代码与素材目录都用过 virtual_singer
            "virtual_singer" -> VIRTUAL_SINGER
            else -> entries.firstOrNull { it.key == key }
        }

        fun labelOf(key: String?): String? = of(key)?.label ?: key?.takeIf { it.isNotBlank() }
    }
}

/**
 * 等级上限。
 *
 * 权威来源是 `cardRarities.json`（实测：`rarity_1` maxLevel 20；`rarity_2` 30；
 * `rarity_3` 40 → 特训后 50；`rarity_4` 50 → 特训后 60；`rarity_birthday` 60
 * 且**没有** `trainingMaxLevel` 字段）。这张表会在同步时进数据库，
 * [FALLBACK_RARITY_CAPS] 只是「还没同步到这张表」时的兜底，不是唯一真相。
 */
data class RarityCap(val maxLevel: Int, val trainingMaxLevel: Int?)

val FALLBACK_RARITY_CAPS: Map<String, RarityCap> = mapOf(
    "rarity_1" to RarityCap(20, null),
    "rarity_2" to RarityCap(30, null),
    "rarity_3" to RarityCap(40, 50),
    "rarity_4" to RarityCap(50, 60),
    "rarity_birthday" to RarityCap(60, null),
)

/**
 * 星级。
 *
 * `rarity_birthday`（生日卡）没有数字后缀，但它在游戏里就是 ★4 档，所以按 4 算。
 */
fun starsOf(rarityKey: String?): Int {
    if (rarityKey.isNullOrBlank()) return 0
    rarityKey.removePrefix("rarity_").toIntOrNull()?.let { return it }
    return if (rarityKey == "rarity_birthday") 4 else 0
}

/** 某一等级下的三项数值。 */
data class CardStats(val vocal: Int, val dance: Int, val visual: Int) {
    /**
     * 综合力。
     *
     * **必须自己加**：实测 `cards.json` 的 `cardParameters` 里 `cardParameterType`
     * 只取 `param1` / `param2` / `param3` 三个值，不存在 `param4`，也没有任何总和字段。
     */
    val total: Int get() = vocal + dance + visual
}

/**
 * 卡面素材的三种形态。
 *
 * **这是三态而不是两态，而且是实测出来的**：
 *  - 大多数 3★/4★ 有未觉醒 + 觉醒两张卡面；
 *  - 1★/2★/生日卡只有未觉醒卡面；
 *  - 但**「出厂即特训后」的卡（如联动限定）只有觉醒卡面**，
 *    实测 `id=1462`（`res017_no056`，联动限定）的 `card_normal.webp` 返回 **404**，
 *    而 `card_after_training.webp` 正常。若按「有没有觉醒图」两态判断，
 *    这类卡会去请求一个必然 404 的 URL，界面上就是一张破图。
 *    识别字段：`initialSpecialTrainingStatus == "done"`。
 */
enum class CardArtPlan {
    /** 只有觉醒卡面。 */
    TRAINED_ONLY,

    /** 未觉醒 + 觉醒都有（左半未觉醒 / 右半觉醒）。 */
    BOTH,

    /** 只有未觉醒卡面。 */
    NORMAL_ONLY,
}

/**
 * 一张卡（列表与详情共用同一个模型）。
 *
 * 各字段来源：`nameJa` / `nameZh` 来自数据库的 `name` / `nameZh` 列
 * （`nameZh` 是简中服叠加层按 id 匹配写进来的，匹配不到就为 null）；
 * 其余全部来自 `master_rows.data` 里那一行 JSON。
 */
data class Card(
    val id: Int,
    val assetbundleName: String,
    val rarityKey: String,
    val attr: CardAttr?,
    val characterId: Int,
    val skillId: Int?,
    val skillName: String?,
    val releaseAt: Long,
    /** 有没有「觉醒（特训后）」卡面。1★/2★/生日卡没有。 */
    val hasTrained: Boolean,
    /** 是否「出厂即特训后」（只有觉醒图，没有未觉醒图）。 */
    val trainedOnly: Boolean,
    /** 特训给三项属性各加多少（下标对应表现力/技巧/体能）。 */
    val trainBonus: List<Int>,
    /** 数值序列的长度。正常应等于该稀有度的最终等级上限。 */
    val maxLevel: Int,
    val vocal: List<Int>,
    val dance: List<Int>,
    val visual: List<Int>,
    val nameJa: String?,
    val nameZh: String?,
    /** 卡牌类型 id（`cardSupplies.json` 里查类型名：通常 / 期间限定 / 联动限定…）。 */
    val supplyId: Int? = null,
    /** 附属组合（虚拟歌手的活动归属单位）。 */
    val supportUnit: String? = null,
    /** 扭蛋短语（卡池里那句台词）。 */
    val gachaPhrase: String? = null,
) {
    val stars: Int get() = starsOf(rarityKey)

    /** 卡面素材该用哪一张/哪两张。 */
    val artPlan: CardArtPlan
        get() = when {
            trainedOnly -> CardArtPlan.TRAINED_ONLY
            hasTrained -> CardArtPlan.BOTH
            else -> CardArtPlan.NORMAL_ONLY
        }

    /** 中文名优先，回退日文原名，再回退 #id。 */
    val displayName: String
        get() = nameZh?.takeIf { it.isNotBlank() }
            ?: nameJa?.takeIf { it.isNotBlank() }
            ?: "#$id"

    val hasChineseName: Boolean get() = !nameZh.isNullOrBlank()

    /**
     * [level] 级时的四项数值。
     *
     * @param applyTrainingBonus 是否把特训加成算进去。特训后才会加（见
     *   `specialTrainingPower[1-3]BonusFixed`）。
     */
    fun statsAt(level: Int, applyTrainingBonus: Boolean = false): CardStats {
        fun value(series: List<Int>, bonusIndex: Int): Int {
            // 下标 = 等级 - 1；越界时退到最后一档，避免返回 0 让用户以为数据丢了
            val base = series.getOrNull(level - 1) ?: series.lastOrNull() ?: 0
            val bonus = if (applyTrainingBonus) trainBonus.getOrNull(bonusIndex) ?: 0 else 0
            return base + bonus
        }
        return CardStats(
            vocal = value(vocal, 0),
            dance = value(dance, 1),
            visual = value(visual, 2),
        )
    }
}

/** 角色。 */
data class PjskCharacter(
    val id: Int,
    val nameJa: String?,
    val nameZh: String?,
    val unitKey: String?,
) {
    val displayName: String
        get() = nameZh?.takeIf { it.isNotBlank() }
            ?: nameJa?.takeIf { it.isNotBlank() }
            ?: "#$id"

    val unitLabel: String? get() = CardUnit.labelOf(unitKey)
}

/**
 * 从数据库行解析一张卡。解析不出 `id` 或素材名就返回 null（宁可少一行，也不要坏一行）。
 *
 * **刻意兼容两种 `cardParameters` 形状**：
 *  - 新格式：`RowProjectors.CARDS` 裁剪后的紧凑序列 `p1/p2/p3`；
 *  - 旧格式：官方原始形状（`cardParameters` 是对象数组）、或用户手机上**在启用裁剪之前**
 *    就已经同步过的行。
 * 之所以要兼容：同步有「版本没变就跳过」的快路径，旧行可能长期留着。
 * 如果不兼容，表现就是「明明同步过了、列表却是空的」，属于最难排查的一类问题。
 */
fun parseCard(row: MasterRowEntity): Card? {
    val obj = row.jsonObject() ?: return null
    val bundle = obj.str("assetbundleName")?.takeIf { it.isNotBlank() } ?: return null

    val compactVocal = obj.intSeries("p1")
    val isCompact = compactVocal.isNotEmpty()

    val vocal: List<Int>
    val dance: List<Int>
    val visual: List<Int>
    if (isCompact) {
        vocal = compactVocal
        dance = obj.intSeries("p2")
        visual = obj.intSeries("p3")
    } else {
        val grouped = groupCardParameters(obj)
        vocal = grouped["param1"].orEmpty()
        dance = grouped["param2"].orEmpty()
        visual = grouped["param3"].orEmpty()
    }

    val trainBonus = obj.intSeries("trainBonus").takeIf { it.size >= 3 }
        ?: listOf(
            obj.intOrNull("specialTrainingPower1BonusFixed") ?: 0,
            obj.intOrNull("specialTrainingPower2BonusFixed") ?: 0,
            obj.intOrNull("specialTrainingPower3BonusFixed") ?: 0,
        )

    val hasTrained = obj.str("hasTrained")?.toBooleanStrictOrNull()
        ?: ((obj["specialTrainingCosts"] as? JsonArray)?.isNotEmpty() == true)

    // 三态的第三态：出厂即特训后（只有觉醒图）
    val trainedOnly = obj.str("trainedOnly")?.toBooleanStrictOrNull()
        ?: (obj.str("initialSpecialTrainingStatus") == "done")

    val maxLevel = obj.intOrNull("maxLevel")
        ?: maxOf(vocal.size, dance.size, visual.size)

    return Card(
        id = obj.intOrNull("id") ?: row.id,
        assetbundleName = bundle,
        rarityKey = obj.str("cardRarityType").orEmpty(),
        attr = CardAttr.of(obj.str("attr")),
        characterId = obj.intOrNull("characterId") ?: 0,
        skillId = obj.intOrNull("skillId"),
        skillName = obj.str("cardSkillName")?.takeIf { it.isNotBlank() },
        releaseAt = obj.longOrNull("releaseAt") ?: row.sortValue,
        hasTrained = hasTrained,
        trainedOnly = trainedOnly,
        trainBonus = trainBonus,
        maxLevel = maxLevel,
        vocal = vocal,
        dance = dance,
        visual = visual,
        nameJa = row.name,
        nameZh = row.nameZh,
        supplyId = obj.intOrNull("cardSupplyId"),
        // "none" 是「没有附属组合」的标记值，不该当成一个组合显示出来
        supportUnit = obj.str("supportUnit")?.takeIf { it.isNotBlank() && it != "none" },
        // "-" 是「没有扭蛋短语」的占位值
        gachaPhrase = obj.str("gachaPhrase")?.takeIf { it.isNotBlank() && it != "-" },
    )
}

/** 从数据库行解析一个角色。 */
fun parseCharacter(row: MasterRowEntity): PjskCharacter? {
    val obj = row.jsonObject() ?: return null
    return PjskCharacter(
        id = row.id,
        nameJa = row.name,
        nameZh = row.nameZh,
        unitKey = obj.str("unit")?.takeIf { it.isNotBlank() },
    )
}

/** 把官方原始形状的 `cardParameters`（对象数组）按属性分组、按等级排序后取出数值序列。 */
private fun groupCardParameters(obj: JsonObject): Map<String, List<Int>> {
    val grouped = HashMap<String, MutableList<Pair<Int, Int>>>(3)
    (obj["cardParameters"] as? JsonArray)?.forEach { element ->
        val p = element as? JsonObject ?: return@forEach
        val type = p.str("cardParameterType") ?: return@forEach
        val level = p.intOrNull("cardLevel") ?: return@forEach
        val power = p.intOrNull("power") ?: return@forEach
        grouped.getOrPut(type) { ArrayList() } += level to power
    }
    return grouped.mapValues { (_, list) -> list.sortedBy { it.first }.map { it.second } }
}

/** 读一个整数数组字段（`[1,2,3]`）。非数字项直接跳过，不抛异常。 */
private fun JsonObject.intSeries(key: String): List<Int> =
    (this[key] as? JsonArray)
        ?.mapNotNull { (it as? JsonPrimitive)?.content?.toDoubleOrNull()?.toInt() }
        ?: emptyList()

// ─────────────────────────────────────────────────────────────
// 名称语言
//
// 三种模式的区别**只在于「有中文时怎么显示」**：简中服进度落后于日服，
// 新内容的 nameZh 为空，这时任何模式都会回退到日文原名 —— 否则列表里会出现一片空白。
// ─────────────────────────────────────────────────────────────

/**
 * 按语言偏好挑主名字。
 *
 * ⚠️ 从 `private` 改成公开：歌曲模块（`data/music`）也要用同一套「中文优先 / 日文 /
 * 双语对照」的规则。**规则必须只有一份** —— 否则某天改了一种模式的行为，
 * 卡牌和歌曲就会不一致。
 */
fun pickName(zh: String?, ja: String?, fallback: String, language: NameLanguage): String {
    val zhClean = zh?.takeIf { it.isNotBlank() }
    val jaClean = ja?.takeIf { it.isNotBlank() }
    return when (language) {
        // 中日两种模式都优先中文，只是副标题取舍不同；日文模式反过来
        NameLanguage.CHINESE_FIRST, NameLanguage.BILINGUAL -> zhClean ?: jaClean ?: fallback
        NameLanguage.JAPANESE -> jaClean ?: zhClean ?: fallback
    }
}

/** 副标题（另一个语言的名字）；不需要时返回 null。同样被歌曲模块复用。 */
fun pickSecondary(zh: String?, ja: String?, language: NameLanguage): String? {
    val zhClean = zh?.takeIf { it.isNotBlank() }
    val jaClean = ja?.takeIf { it.isNotBlank() }
    return when (language) {
        // 中文优先：不显示日文副标题，界面最干净
        NameLanguage.CHINESE_FIRST -> null
        NameLanguage.JAPANESE -> zhClean?.takeIf { it != jaClean }
        NameLanguage.BILINGUAL -> jaClean?.takeIf { it != zhClean }
    }
}

/** 按语言偏好选主标题。 */
fun Card.displayNameFor(language: NameLanguage): String =
    pickName(nameZh, nameJa, "#$id", language)

/** 副标题（另一个语言的名字）；不需要时返回 null。 */
fun Card.secondaryNameFor(language: NameLanguage): String? =
    pickSecondary(nameZh, nameJa, language)

/**
 * 下载用的文件名：`<id>_<卡名>_<状态>.png`（用户拍板的格式，如 `1465_星野一歌_特训后.png`）。
 *
 * 为什么用这个而不是素材名（`res011_no057_card_normal.png`）：
 *  - **好认**：文件管理器/相册里一眼知道是哪张卡；
 *  - **不重名**：id 在前，天然唯一，也不会像素材名那样出现 `(...1).png` 这种后缀；
 *  - **能排序**：按文件名排序就是按 id 排序。
 *
 * Windows/安卓的非法字符统一换成 `_`，避免出现存不下的文件名。
 */
fun Card.downloadFileName(trained: Boolean, language: NameLanguage): String {
    val safeName = displayNameFor(language)
        .replace(Regex("""[\\/:*?"<>|\n\r\t]"""), "_")
        .trim()
        .ifBlank { assetbundleName }
    return "${id}_${safeName}_${if (trained) "特训后" else "通常"}.png"
}

fun PjskCharacter.displayNameFor(language: NameLanguage): String =
    pickName(nameZh, nameJa, "#$id", language)

fun PjskCharacter.secondaryNameFor(language: NameLanguage): String? =
    pickSecondary(nameZh, nameJa, language)
