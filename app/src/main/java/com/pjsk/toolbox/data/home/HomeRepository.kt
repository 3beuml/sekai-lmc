package com.pjsk.toolbox.data.home

import com.pjsk.toolbox.data.db.MasterDao
import com.pjsk.toolbox.data.music.buildDefaultVocals
import com.pjsk.toolbox.ui.common.intOrNull
import com.pjsk.toolbox.ui.common.jsonObject
import com.pjsk.toolbox.ui.common.longOrNull
import com.pjsk.toolbox.ui.common.str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * 列表里那一行要用的「默认音源版本」。
 *
 * 挑法是**`musicVocals` 里 `seq` 最小的那个** —— 实测 713/717 首符合
 * 「原曲→世界版→另一版→伴奏」的顺序，所以它和游戏内默认播的那个一致。
 * 列表上的 ▶ 直接播它，不用再查一次库。
 */
data class HomeMusicVocal(
    val id: Int,
    val assetbundleName: String,
    val caption: String,
)

/** 首页「最新歌曲」用的一首歌。 */
data class HomeMusic(
    val id: Int,
    val title: String,
    /** 曲绘素材名（`music/jacket/<bundle>/<bundle>.webp`）。 */
    val assetbundleName: String,
    val publishedAt: Long,
    /**
     * 官方组合标签（来自 `musicTags.json`）：
     * `vocaloid` / `light_music_club` / `idol` / `street` / `theme_park` / `school_refusal` / `other`。
     * `all` 是每首歌都有的占位标签，没有区分度，解析时已经滤掉。
     */
    val unitTags: List<String>,
    /**
     * 演出形式分类（来自 `musicCategories.json`）：
     * `image` / `mv_2d` / `mv` / `original`。一首歌可能同时属于多个。
     *
     * 加了它是因为歌曲页的筛选要用这一维（原本只有组合标签）。
     */
    val categories: List<String>,
    val composer: String?,
    /** 默认音源版本；没有音源行时为 null（列表上的 ▶ 会置灰）。 */
    val defaultVocal: HomeMusicVocal? = null,
    /**
     * `musics.fillerSec`：游戏音源**开头那段空白**，播放时要跳过。
     * 列表上直接播放需要它，否则会先响一段沉默。
     */
    val fillerSec: Double? = null,
)

/** 首页「最新活动」用的一个活动。 */
data class HomeEvent(
    val id: Int,
    val name: String,
    val assetbundleName: String,
    val startAt: Long,
    val closedAt: Long,
)

/**
 * 首页「当前卡池」用一个卡池。
 *
 * `cardIds` 来自 `RowProjectors.GACHAS` 裁出来的 `gc` 字段 —— 那是「卡池 → 卡牌」的唯一映射，
 * 卡池详情页靠它列出这个池子里的卡。
 */
data class HomeGacha(
    val id: Int,
    val name: String,
    val assetbundleName: String,
    val startAt: Long,
    val endAt: Long,
    /** `gachas.gachaType`：ceil / normal / gift / beginner。卡池列表用它做分类筛选。 */
    val gachaType: String,
    val cardIds: List<Int>,
    /**
     * 这个池子的 **UP 卡**（`gachas.pk`，官方 `gachaPickups` 的 `cardId`，保持原顺序）。
     *
     * 卡池详情页靠它把重点卡排在最前、并打「UP」标。旧数据里没有这个字段时为空 ——
     * 那就退化成「没有 UP」，不会崩。
     */
    val upCardIds: List<Int> = emptyList(),
    /** 第一次能在这个池子抽到的卡（全员池子按开始时间算出来的，见 [debutCardIdsByGacha]）。 */
    val debutCardIds: Set<Int> = emptySet(),
)

/** 首页「即将到来的生日」用一个角色。 */
data class HomeBirthday(
    val characterId: Int,
    val name: String,
    val month: Int,
    val day: Int,
)

/**
 * 首页各区块的数据源。
 *
 * 三张表都**已经在内置快照里**（`musics` / `events` / `characterProfiles`），
 * 所以首页这些区块不需要新增任何数据，只是把已有数据组装出来。
 */
class HomeRepository(private val dao: MasterDao) {

    /** 最新歌曲：按 `publishedAt` 倒序。 */
    val musics: Flow<List<HomeMusic>> = kotlinx.coroutines.flow.combine(
        dao.observeAll(TABLE_MUSICS),
        dao.observeAll(TABLE_MUSIC_TAGS),
        dao.observeAll(TABLE_MUSIC_CATEGORIES),
        // 音源表：列表上的 ▶ 要"点了就响"，得先知道默认版本是哪个素材
        dao.observeAll(TABLE_MUSIC_VOCALS),
    ) { rows, tagRows, categoryRows, vocalRows ->
        // musicId -> 组合标签（去掉无区分度的 all）
        val tagsByMusic = tagRows.groupBy { it.jsonObject()?.intOrNull("musicId") ?: 0 }
            .mapValues { (_, list) ->
                list.mapNotNull { it.jsonObject()?.str("musicTag")?.takeIf { t -> t != "all" && t.isNotBlank() } }
            }
        // musicId -> 演出形式分类（静态图片 / 2D MV / …）
        val categoriesByMusic = categoryRows.groupBy { it.jsonObject()?.intOrNull("musicId") ?: 0 }
            .mapValues { (_, list) ->
                list.mapNotNull { it.jsonObject()?.str("musicCategoryName")?.takeIf { c -> c.isNotBlank() } }
            }
        // musicId -> 默认音源版本（seq 最小的那个）
        val defaultVocalByMusic = vocalRows.groupBy { it.jsonObject()?.intOrNull("musicId") ?: 0 }
            .mapValues { (_, list) ->
                val first = list.minByOrNull { it.jsonObject()?.intOrNull("seq") ?: Int.MAX_VALUE }
                val obj = first?.jsonObject()
                val bundle = obj?.str("assetbundleName")?.takeIf { it.isNotBlank() }
                if (first == null || bundle == null) {
                    null
                } else {
                    HomeMusicVocal(
                        id = first.id,
                        assetbundleName = bundle,
                        caption = obj.str("caption").orEmpty(),
                    )
                }
            }
        run {
            rows.mapNotNull { row ->
                val obj = row.jsonObject() ?: return@mapNotNull null
                val bundle = obj.str("assetbundleName") ?: return@mapNotNull null
                val title = row.nameZh?.takeIf { it.isNotBlank() }
                    ?: row.name?.takeIf { it.isNotBlank() }
                    ?: obj.str("title")
                    ?: return@mapNotNull null
                HomeMusic(
                    id = row.id,
                    title = title,
                    assetbundleName = bundle,
                    publishedAt = obj.longOrNull("publishedAt") ?: row.sortValue,
                    composer = obj.str("composer")?.takeIf { it.isNotBlank() && it != "-" },
                    unitTags = tagsByMusic[row.id].orEmpty(),
                    categories = categoriesByMusic[row.id].orEmpty(),
                    defaultVocal = defaultVocalByMusic[row.id],
                    fillerSec = obj.str("fillerSec")?.toDoubleOrNull(),
                )
            }.sortedByDescending { it.publishedAt }
        }
    }.flowOn(Dispatchers.Default)

    /** 活动：按 `startAt` 倒序（新→旧）。 */
    val events: Flow<List<HomeEvent>> = dao.observeAll(TABLE_EVENTS)
        .map { rows ->
            rows.mapNotNull { row ->
                val obj = row.jsonObject() ?: return@mapNotNull null
                val bundle = obj.str("assetbundleName") ?: return@mapNotNull null
                val name = row.nameZh?.takeIf { it.isNotBlank() } ?: row.name ?: return@mapNotNull null
                HomeEvent(
                    id = row.id,
                    name = name,
                    assetbundleName = bundle,
                    startAt = obj.longOrNull("startAt") ?: row.sortValue,
                    closedAt = obj.longOrNull("closedAt") ?: row.sortValue,
                )
            }.sortedByDescending { it.startAt }
        }
        .flowOn(Dispatchers.Default)

    /**
     * 角色生日。
     *
     * ⚠️ `characterProfiles.birthday` 是**日文格式的字符串**（实测 `"8月11日"`），
     * 不是月/日两个数字字段，所以要在这里解析出来 —— 解析不了的角色直接跳过，
     * 不猜也不显示成 0 月 0 日。
     */
    val birthdays: Flow<List<HomeBirthday>> = dao.observeAll(TABLE_CHARACTER_PROFILES)
        .map { rows ->
            rows.mapNotNull { row ->
                val obj = row.jsonObject() ?: return@mapNotNull null
                val raw = obj.str("birthday") ?: return@mapNotNull null
                val parsed = parseBirthday(raw) ?: return@mapNotNull null
                val charId = obj.intOrNull("characterId") ?: row.id
                HomeBirthday(
                    characterId = charId,
                    name = "", // 名字由调用方按「名称语言」设置填（这里拿不到设置）
                    month = parsed.first,
                    day = parsed.second,
                )
            }
        }
        .flowOn(Dispatchers.Default)

    /** 卡池：按 `startAt` 倒序（新→旧）。1004 个。 */
    val gachas: Flow<List<HomeGacha>> = dao.observeAll(TABLE_GACHAS)
        .map { rows ->
            val parsed = rows.mapNotNull { row ->
                val obj = row.jsonObject() ?: return@mapNotNull null
                val bundle = obj.str("bundle") ?: return@mapNotNull null
                HomeGacha(
                    id = row.id,
                    name = obj.str("name") ?: "卡池 #${row.id}",
                    assetbundleName = bundle,
                    startAt = obj.longOrNull("startAt") ?: row.sortValue,
                    endAt = obj.longOrNull("endAt") ?: row.sortValue,
                    gachaType = obj.str("gachaType").orEmpty(),
                    cardIds = obj.intList("gc"),
                    upCardIds = obj.intList("pk"),
                )
            }
            // 首发卡要**跨池子**算（按开始时间升序走一遍），所以放在这里一次性补上，
            // 而不是留给界面去算 —— 界面里那次会随每次重组重跑，1004 个池 × 上百张卡不便宜。
            val debut = debutCardIdsByGacha(parsed)
            parsed.map { gacha ->
                gacha.copy(debutCardIds = debut[gacha.id].orEmpty())
            }.sortedByDescending { it.startAt }
        }
        .flowOn(Dispatchers.Default)

    /** 当前时间到「下一次生日」的天数（0 = 今天）。 */
    companion object {
        private const val TABLE_GACHAS = "gachas.json"
        private const val TABLE_MUSIC_TAGS = "musicTags.json"
        private const val TABLE_MUSIC_CATEGORIES = "musicCategories.json"
        private const val TABLE_MUSIC_VOCALS = "musicVocals.json"
        private const val TABLE_MUSICS = "musics.json"
        private const val TABLE_EVENTS = "events.json"
        private const val TABLE_CHARACTER_PROFILES = "characterProfiles.json"

        /** 读一个整数数组字段（`[1,2,3]`）；非数字项直接跳过。 */
    private fun kotlinx.serialization.json.JsonObject.intList(key: String): List<Int> =
        (this[key] as? kotlinx.serialization.json.JsonArray).orEmpty()
            .mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull() }

    /** `"8月11日"` → `(8, 11)`；格式不对返回 null。 */
    fun parseBirthday(raw: String): Pair<Int, Int>? {
            val m = Regex("""(\d{1,2})\s*月\s*(\d{1,2})\s*日""").find(raw) ?: return null
            val month = m.groupValues[1].toIntOrNull() ?: return null
            val day = m.groupValues[2].toIntOrNull() ?: return null
            if (month !in 1..12 || day !in 1..31) return null
            return month to day
        }

        /** 距下一次生日的天数（跨年也对）。 */
        fun daysUntilBirthday(month: Int, day: Int, today: java.time.LocalDate = java.time.LocalDate.now()): Int {
            var next = java.time.LocalDate.of(today.year, month, day)
            if (next.isBefore(today)) next = java.time.LocalDate.of(today.year + 1, month, day)
            return java.time.temporal.ChronoUnit.DAYS.between(today, next).toInt()
        }
    }
}
