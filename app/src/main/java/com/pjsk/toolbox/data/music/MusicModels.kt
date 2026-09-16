package com.pjsk.toolbox.data.music

import androidx.compose.ui.graphics.Color
import com.pjsk.toolbox.data.card.PjskCharacter
import com.pjsk.toolbox.data.card.pickName
import com.pjsk.toolbox.data.card.pickSecondary
import com.pjsk.toolbox.data.db.MasterRowEntity
import com.pjsk.toolbox.data.home.HomeMusicVocal
import com.pjsk.toolbox.data.settings.NameLanguage
import com.pjsk.toolbox.ui.common.intOrNull
import com.pjsk.toolbox.ui.common.jsonObject
import com.pjsk.toolbox.ui.common.longOrNull
import com.pjsk.toolbox.ui.common.str

/**
 * 歌曲模块的数据模型与**纯函数**解析。
 *
 * 为什么解析要单独放一层、而且必须是纯函数：
 * 这一层出错**不会报错**，只会显示错的东西（少一个版本、演唱者张冠李戴、
 * 分类徽章挂错），而这类问题在真机上极难发现。写成纯函数就能进
 * `tools/logic-check.ps1` 直接断言。
 *
 * ## ⚠️ 贯穿本文件的一条铁律：一律按 `musicId` 过滤，绝不用行主键 `id`
 *
 * `musicVocals` / `musicDifficulties` / `musicTags` / `musicCategories` 这几张表
 * **都有一列全局自增 `id`，而它和别的曲子的 musicId 会撞车**。实测：
 * `musicVocals` 里 `id == 644` 的那一行属于 **musicId 258**；
 * `musicDifficulties` 里 `id == 644` 属于 musicId 129。
 * 用 `id` 过滤会拿到别的曲子的音源/难度，而且**看起来完全正常**。
 */
// ─────────────────────────────────────────────────────────────
// 枚举
// ─────────────────────────────────────────────────────────────

/**
 * 歌曲分类（`musicCategories.musicCategoryName`）。
 *
 * 注意它的含义是**「这首歌在游戏里长什么样」**，不是音乐风格：
 * 静态图片 / 2D MV / 3D MV / 原创MV。标签与配色对齐参考站。
 */
enum class MusicCategory(val key: String, val label: String, val color: Color) {
    MV("mv", "3D MV", Color(0xFF4488DD)),
    MV_2D("mv_2d", "2D MV", Color(0xFF44BB88)),
    ORIGINAL("original", "原创MV", Color(0xFFFF9900)),
    IMAGE("image", "静态图片", Color(0xFF888888)),
    ;

    companion object {
        fun of(key: String?): MusicCategory? = entries.firstOrNull { it.key == key }
    }
}

/**
 * 难度档位。
 *
 * **枚举顺序就是游戏内的显示顺序**（easy→normal→hard→expert→master→append），
 * 不是 `id` 也不是 `seq` 的顺序 —— 数据里这两者都不可靠。
 */
enum class MusicDifficultyKind(
    val key: String,
    val abbr: String,
    val label: String,
    val color: Color,
) {
    EASY("easy", "EAS", "EASY", Color(0xFF5AC06E)),
    NORMAL("normal", "NOR", "NORMAL", Color(0xFF56A4D4)),
    HARD("hard", "HAR", "HARD", Color(0xFFEFAF28)),
    EXPERT("expert", "EXP", "EXPERT", Color(0xFFE84D53)),
    MASTER("master", "MAS", "MASTER", Color(0xFFBB58B8)),
    APPEND("append", "APP", "APPEND", Color(0xFFEE92BC)),
    ;

    companion object {
        fun of(key: String?): MusicDifficultyKind? = entries.firstOrNull { it.key == key }
    }
}

/** `musicVocals.musicVocalType` → 徽章短标签。 */
fun vocalTypeLabel(type: String?): String = when (type) {
    "original_song" -> "原曲"
    "virtual_singer" -> "虚拟歌手"
    "sekai" -> "世界"
    "another_vocal" -> "另一版"
    "instrumental" -> "伴奏"
    "april_fool_2022" -> "愚人节"
    "streaming_live" -> "直播"
    else -> "其他"
}

/**
 * 游戏内选曲界面的那排分类 = `musicTags.musicTag` 的 7 个取值。
 *
 * 实测这 7 个取值和游戏里选曲界面的分类按钮**一一对应**，不需要任何推导，
 * 所以「按团浏览」这件事直接用它，别再另造一套分组。
 *
 * [unitProfileKey] 指向 `unitProfiles.json` 的 `unit` 字段（用来取官方组合色），
 * [logoAbbr] 指向内置的组合 logo（`assets/icons/unit/<abbr>.webp`，60×60）。
 * ⚠️ 两套 key 的拼法**不一样**（`light_music_club` vs `light_sound`、`vocaloid` vs `piapro`），
 * 写错的表现是「配色和 logo 都对不上」，不会报错，所以集中在这里映射一次。
 *
 * `other`（53 首）没有 logo、也没有官方组合色 —— 它是兜底分类，不是真的团。
 */
enum class MusicUnitTag(
    val key: String,
    val label: String,
    val logoAbbr: String?,
    val unitProfileKey: String?,
) {
    VIRTUAL_SINGER("vocaloid", "VIRTUAL SINGER", "vs", "piapro"),
    LEO_NEED("light_music_club", "Leo/need", "ln", "light_sound"),
    MORE_MORE_JUMP("idol", "MORE MORE JUMP!", "mmj", "idol"),
    VIVID_BAD_SQUAD("street", "Vivid BAD SQUAD", "vbs", "street"),
    WONDERLANDS("theme_park", "ワンダーランズ×ショウタイム", "wxs", "theme_park"),
    NIIGO("school_refusal", "25時、ナイトコードで。", "n25", "school_refusal"),
    OTHER("other", "其他", null, null),
    ;

    companion object {
        fun of(key: String?): MusicUnitTag? = entries.firstOrNull { it.key == key }

        /** 列表里的显示顺序就是游戏内选曲界面的顺序（VS 在最前）。 */
        val ordered: List<MusicUnitTag> get() = entries.toList()
    }
}

/**
 * `musicCategories.musicCategoryName` → 中文标签；认不出的**原样返回 key**，不编说法。
 */
fun musicCategoryLabel(key: String?): String =
    MusicCategory.of(key)?.label ?: key.orEmpty()

/**
 * 把 `musics.releaseConditionId` 变成中文。
 *
 * 实测出现的取值只有这 4 个；认不出的**原样显示 id**，
 * 而不是编一个听起来合理的说法。
 */
fun releaseConditionLabel(id: Int?, limitedTime: Boolean): String {
    val base = when (id) {
        1 -> "初始拥有"
        5 -> "从音乐商店购买"
        6 -> "无"
        10 -> "从礼物中领取"
        null -> "未知"
        else -> "未知（id=$id）"
    }
    // 期间限定：`limitedTimeMusics` 里有这首歌就加后缀
    return if (limitedTime) "$base（期间限定）" else base
}

// ─────────────────────────────────────────────────────────────
// 模型
// ─────────────────────────────────────────────────────────────

data class MusicDifficulty(
    val kind: MusicDifficultyKind,
    val playLevel: Int,
    val noteCount: Int,
)

/**
 * 演唱者。
 *
 * [isGameCharacter] 区分两类：
 *  - `true` ＝ 游戏内角色（1~26），有头像、有中文名，**可以点击**跳转到卡牌图鉴；
 *  - `false` ＝ 社外角色（`outsideCharacters`，可不 / GUMI / flower…），
 *    只有日文名、没有头像，界面上只做纯文字展示、不可点击。
 */
data class MusicSinger(
    val id: Int,
    val isGameCharacter: Boolean,
    val nameJa: String,
    val nameZh: String?,
) {
    val displayName: String
        get() = nameZh?.takeIf { it.isNotBlank() } ?: nameJa
}

/** 一个演唱版本（＝一个音源文件）。 */
data class MusicVocal(
    val id: Int,
    val vocalType: String,
    /** 日文原版说明，如 `セカイver.` / `バーチャル・シンガーver.`。 */
    val captionJa: String,
    /** 简中服说明（叠加层给的中文），如 `「世界」ver.`；没有就为 null。 */
    val captionZh: String?,
    val assetbundleName: String,
    val singers: List<MusicSinger>,
) {
    val typeLabel: String get() = vocalTypeLabel(vocalType)

    /** 说明文字：中文优先，回退日文。 */
    val caption: String get() = captionZh?.takeIf { it.isNotBlank() } ?: captionJa
}

/** 相关活动（`eventMusics` → `events`）。 */
data class MusicEventRef(
    val eventId: Int,
    val name: String,
    val assetbundleName: String?,
    val startAt: Long?,
    val endAt: Long?,
)

/**
 * 一首歌的全部展示数据。
 *
 * [secForMusicScoreMaker] / [fillerSec] 是**给阶段 3 的播放器留的**（算剪辑时长、
 * 跳过开头空白），本页不显示 —— 用户明确要求详情页不放时长。
 */
data class MusicDetail(
    val id: Int,
    val nameJa: String,
    val nameZh: String?,
    val assetbundleName: String,
    val pronunciation: String?,
    val lyricist: String?,
    val composer: String?,
    val arranger: String?,
    val creatorArtistName: String?,
    val publishedAt: Long,
    val releaseCondition: String,
    /** `musicTags` 里这首歌的组合标签（已去掉无区分度的 `all`）。 */
    val unitTags: List<String>,
    val categories: List<MusicCategory>,
    val difficulties: List<MusicDifficulty>,
    val vocals: List<MusicVocal>,
    val originalVideoLink: String?,
    val relatedEvents: List<MusicEventRef>,
    val secForMusicScoreMaker: Int?,
    val fillerSec: Double?,
) {
    fun displayNameFor(language: NameLanguage): String =
        pickName(nameZh, nameJa, "#$id", language)

    fun secondaryNameFor(language: NameLanguage): String? =
        pickSecondary(nameZh, nameJa, language)
}

/** 演唱者的名字来源（两张表）。 */
data class MusicSingerNames(
    /** `gameCharacters.json`：id → 角色。 */
    val gameCharacters: Map<Int, PjskCharacter>,
    /** `outsideCharacters.json`：id → 名字（只有日文，没有中文名也没有头像）。 */
    val outsideCharacters: Map<Int, String>,
)

// ─────────────────────────────────────────────────────────────
// 纯函数解析
// ─────────────────────────────────────────────────────────────

/** 从 `musics.json` 的一行取出「曲目主体」；缺 id 或素材名就返回 null。 */
private data class MusicCore(
    val id: Int,
    val nameJa: String,
    val nameZh: String?,
    val assetbundleName: String,
    val pronunciation: String?,
    val lyricist: String?,
    val composer: String?,
    val arranger: String?,
    val creatorArtistId: Int?,
    val publishedAt: Long,
    val releaseConditionId: Int?,
    val secForMusicScoreMaker: Int?,
    val fillerSec: Double?,
)

/**
 * 作曲/作词/编曲字段的清洗。
 *
 * 实测有一批曲子的这三个字段是**字面量 `-`**（最多的一位"制作人"就是 `-`，74 首），
 * 不过滤的话音乐人列表第一名会是一条横杠。空白同理。
 */
private fun cleanCredit(value: String?): String? =
    value?.trim()?.takeIf { it.isNotEmpty() && it != "-" && it != "－" }

private fun parseMusicCore(row: MasterRowEntity): MusicCore? {
    val obj = row.jsonObject() ?: return null
    val bundle = obj.str("assetbundleName")?.takeIf { it.isNotBlank() } ?: return null
    val titleJa = obj.str("title")?.takeIf { it.isNotBlank() }
        ?: row.name?.takeIf { it.isNotBlank() }
        ?: return null
    return MusicCore(
        id = row.id,
        nameJa = titleJa,
        // 中文曲名走叠加层写进 `nameZh` 列（来源是简中服的 `infos[0].title`）
        nameZh = row.nameZh?.takeIf { it.isNotBlank() }?.takeIf { it != titleJa },
        assetbundleName = bundle,
        pronunciation = obj.str("pronunciation")?.takeIf { it.isNotBlank() },
        lyricist = cleanCredit(obj.str("lyricist")),
        composer = cleanCredit(obj.str("composer")),
        arranger = cleanCredit(obj.str("arranger")),
        creatorArtistId = obj.intOrNull("creatorArtistId")?.takeIf { it > 0 },
        publishedAt = obj.longOrNull("publishedAt") ?: row.sortValue,
        releaseConditionId = obj.intOrNull("releaseConditionId"),
        secForMusicScoreMaker = obj.intOrNull("secForMusicScoreMaker"),
        fillerSec = obj.str("fillerSec")?.toDoubleOrNull(),
    )
}

/** `musicDifficulties.json` 的一行 → 难度。 */
fun parseMusicDifficulty(row: MasterRowEntity): Pair<Int, MusicDifficulty>? {
    val obj = row.jsonObject() ?: return null
    val musicId = obj.intOrNull("musicId") ?: return null
    val kind = MusicDifficultyKind.of(obj.str("musicDifficulty")) ?: return null
    return musicId to MusicDifficulty(
        kind = kind,
        playLevel = obj.intOrNull("playLevel") ?: 0,
        noteCount = obj.intOrNull("totalNoteCount") ?: 0,
    )
}

/** `musicVocals.json` 的一行 → 演唱版本（含演唱者名字解析）。 */
fun parseMusicVocal(row: MasterRowEntity, names: MusicSingerNames): Pair<Int, MusicVocal>? {
    val obj = row.jsonObject() ?: return null
    val musicId = obj.intOrNull("musicId") ?: return null
    val bundle = obj.str("assetbundleName")?.takeIf { it.isNotBlank() } ?: return null

    // `characters` 是嵌在音源行里的数组，每项带 characterType / characterId
    val singers = (obj["characters"] as? kotlinx.serialization.json.JsonArray)
        ?.mapNotNull { element ->
            val c = element as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
            val characterId = c.intOrNull("characterId") ?: return@mapNotNull null
            val isGame = c.str("characterType") == "game_character"
            if (isGame) {
                val character = names.gameCharacters[characterId] ?: return@mapNotNull null
                MusicSinger(
                    id = characterId,
                    isGameCharacter = true,
                    nameJa = character.nameJa.orEmpty().ifBlank { "#$characterId" },
                    nameZh = character.nameZh,
                )
            } else {
                // 社外角色（可不 / GUMI…）：只有日文名
                val outside = names.outsideCharacters[characterId] ?: return@mapNotNull null
                MusicSinger(id = characterId, isGameCharacter = false, nameJa = outside, nameZh = null)
            }
        }
        .orEmpty()

    return musicId to MusicVocal(
        id = row.id,
        vocalType = obj.str("musicVocalType").orEmpty(),
        captionJa = obj.str("caption").orEmpty(),
        captionZh = row.nameZh?.takeIf { it.isNotBlank() },
        assetbundleName = bundle,
        singers = singers,
    )
}

/** 这三张表都是「一行 = 一个 musicId + 一个取值」，统一成一个解析器。 */
private fun parseMusicIdAndValue(row: MasterRowEntity, valueKey: String): Pair<Int, String>? {
    val obj = row.jsonObject() ?: return null
    val musicId = obj.intOrNull("musicId") ?: return null
    val value = obj.str(valueKey)?.takeIf { it.isNotBlank() } ?: return null
    return musicId to value
}

fun parseMusicTag(row: MasterRowEntity): Pair<Int, String>? =
    parseMusicIdAndValue(row, "musicTag")?.takeIf { it.second != "all" }

fun parseMusicCategory(row: MasterRowEntity): Pair<Int, String>? =
    parseMusicIdAndValue(row, "musicCategoryName")

fun parseMusicOriginal(row: MasterRowEntity): Pair<Int, String>? =
    parseMusicIdAndValue(row, "videoLink")

/** `eventMusics.json`：一行 = 某首歌属于某个活动。 */
fun parseEventMusic(row: MasterRowEntity): Pair<Int, Int>? {
    val obj = row.jsonObject() ?: return null
    val musicId = obj.intOrNull("musicId") ?: return null
    val eventId = obj.intOrNull("eventId") ?: return null
    return musicId to eventId
}

/**
 * 从 `musicVocals.json` 反查「每首歌的**默认音源版本**」。
 *
 * 挑法是 **`seq` 最小的那个** —— 实测 713/717 首符合「原曲→世界版→另一版→伴奏」的顺序，
 * 所以它和游戏内默认播的那个一致。列表行上的 ▶ 直接播它，不用再查一次库。
 *
 * ⚠️ 和别处一样按 `musicId` 分组（**不是**行主键）：那几张表的全局 `id`
 * 会和别的曲子的 musicId 撞车。
 */
fun buildDefaultVocals(vocalRows: List<MasterRowEntity>): Map<Int, HomeMusicVocal> =
    vocalRows
        .groupBy { it.jsonObject()?.intOrNull("musicId") ?: 0 }
        .mapNotNull { (musicId, rows) ->
            if (musicId <= 0) return@mapNotNull null
            val first = rows.minByOrNull { it.jsonObject()?.intOrNull("seq") ?: Int.MAX_VALUE }
                ?: return@mapNotNull null
            val obj = first.jsonObject() ?: return@mapNotNull null
            val bundle = obj.str("assetbundleName")?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            musicId to HomeMusicVocal(
                id = first.id,
                assetbundleName = bundle,
                caption = obj.str("caption").orEmpty(),
            )
        }
        .toMap()

/** `limitedTimeMusics.json`：期间限定的曲目。 */
fun parseLimitedTimeMusic(row: MasterRowEntity): Int? =
    row.jsonObject()?.intOrNull("musicId")

/**
 * 从 `musicVocals.json` 反查「**每个角色唱过哪些歌**」。
 *
 * 这是「人物」页的数据来源：用户拍板的口径是**甲 = 他唱过的歌**
 * （不是"他所在团的歌"）。实测初音ミク 487 首、镜音铃 160、星乃一歌 90。
 *
 * ⚠️ 只收 `characterType == "game_character"`：
 * `outside_character`（可不 / GUMI 那 43 位）没有游戏内角色页，收进来会得到
 * 一堆点不开的条目。
 */
fun buildCharacterSongIds(vocalRows: List<MasterRowEntity>): Map<Int, Set<Int>> {
    val result = mutableMapOf<Int, MutableSet<Int>>()
    vocalRows.forEach { row ->
        val obj = row.jsonObject() ?: return@forEach
        val musicId = obj.intOrNull("musicId") ?: return@forEach
        val characters = obj["characters"] as? kotlinx.serialization.json.JsonArray ?: return@forEach
        characters.forEach { element ->
            val c = element as? kotlinx.serialization.json.JsonObject ?: return@forEach
            if (c.str("characterType") != "game_character") return@forEach
            val characterId = c.intOrNull("characterId") ?: return@forEach
            result.getOrPut(characterId) { mutableSetOf() }.add(musicId)
        }
    }
    return result
}

/**
 * 把各表的行组装成一首歌的展示数据。**纯函数**，输入给全表也行（内部按 musicId 过滤）。
 *
 * @param musicRow    `musics.json` 里这一首的行
 * @param artistRows  `musicArtists.json`（用 `creatorArtistId` 查曲师名）
 * @param eventRows   `events.json`（相关活动的名字与起止）
 */
fun buildMusicDetail(
    musicRow: MasterRowEntity,
    difficultyRows: List<MasterRowEntity>,
    vocalRows: List<MasterRowEntity>,
    tagRows: List<MasterRowEntity>,
    categoryRows: List<MasterRowEntity>,
    artistRows: List<MasterRowEntity>,
    originalRows: List<MasterRowEntity>,
    eventMusicRows: List<MasterRowEntity>,
    eventRows: List<MasterRowEntity>,
    limitedTimeRows: List<MasterRowEntity>,
    names: MusicSingerNames,
): MusicDetail? {
    val core = parseMusicCore(musicRow) ?: return null
    val musicId = core.id

    // 难度：按枚举顺序排（游戏内顺序），不看 id / seq
    val difficulties = difficultyRows
        .mapNotNull { parseMusicDifficulty(it) }
        .filter { it.first == musicId }
        .map { it.second }
        .sortedBy { it.kind.ordinal }

    // 演唱版本：按 musicVocals.seq 排（游戏内顺序）。实测 713/717 首符合
    // 「原曲→世界版→另一版→伴奏」，所以第一项就是默认版本。
    val vocals = vocalRows
        .filter { it.jsonObject()?.intOrNull("musicId") == musicId }
        .sortedBy { it.jsonObject()?.intOrNull("seq") ?: 0 }
        .mapNotNull { parseMusicVocal(it, names)?.second }

    val unitTags = tagRows
        .mapNotNull { parseMusicTag(it) }
        .filter { it.first == musicId }
        .map { it.second }
        .distinct()

    val categories = categoryRows
        .mapNotNull { parseMusicCategory(it) }
        .filter { it.first == musicId }
        .mapNotNull { MusicCategory.of(it.second) }
        .distinct()
        .sortedBy { it.ordinal }

    val creatorArtistName = core.creatorArtistId?.let { artistId ->
        artistRows.firstOrNull { it.id == artistId }
            ?.let { it.nameZh?.takeIf { n -> n.isNotBlank() } ?: it.name }
    }

    val originalVideoLink = originalRows
        .mapNotNull { parseMusicOriginal(it) }
        .firstOrNull { it.first == musicId }
        ?.second

    val limitedTime = limitedTimeRows.any { parseLimitedTimeMusic(it) == musicId }

    val eventById = eventRows.associateBy { it.id }
    val relatedEvents = eventMusicRows
        .mapNotNull { parseEventMusic(it) }
        .filter { it.first == musicId }
        .mapNotNull { (_, eventId) ->
            val row = eventById[eventId] ?: return@mapNotNull null
            MusicEventRef(
                eventId = eventId,
                name = row.nameZh?.takeIf { it.isNotBlank() } ?: row.name ?: "#$eventId",
                assetbundleName = row.jsonObject()?.str("assetbundleName"),
                startAt = row.jsonObject()?.longOrNull("startAt"),
                endAt = row.jsonObject()?.longOrNull("closedAt"),
            )
        }
        .distinctBy { it.eventId }

    return MusicDetail(
        id = musicId,
        nameJa = core.nameJa,
        nameZh = core.nameZh,
        assetbundleName = core.assetbundleName,
        pronunciation = core.pronunciation,
        lyricist = core.lyricist,
        composer = core.composer,
        arranger = core.arranger,
        creatorArtistName = creatorArtistName,
        publishedAt = core.publishedAt,
        releaseCondition = releaseConditionLabel(core.releaseConditionId, limitedTime),
        unitTags = unitTags,
        categories = categories,
        difficulties = difficulties,
        vocals = vocals,
        originalVideoLink = originalVideoLink,
        relatedEvents = relatedEvents,
        secForMusicScoreMaker = core.secForMusicScoreMaker,
        fillerSec = core.fillerSec,
    )
}
