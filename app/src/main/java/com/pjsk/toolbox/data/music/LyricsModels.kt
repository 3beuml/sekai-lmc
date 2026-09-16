package com.pjsk.toolbox.data.music

import com.pjsk.toolbox.ui.common.intOrNull
import com.pjsk.toolbox.ui.common.parseJsonObjectOrNull
import com.pjsk.toolbox.ui.common.str
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * 歌词的数据模型与**纯函数**解析。
 *
 * 数据源：`translation.exmeaning.com/files/translation/lyrics/`
 * （社区维护的歌词库，词条来自 Sekaipedia，**CC BY-SA 4.0，必须署名**——
 * 所以 [LyricsCredit] / [LyricsRendition.translators] 不是可选装饰，是许可要求）。
 *
 * ## ⚠️ 线上同时存在两套 schema，必须都支持
 *
 * 实测抽查 30 首：**29 首是 v3、1 首是 v4**。只写 v3 的解析器遇到 v4 不会报错，
 * 只会**静默地一行翻译都不显示** —— 属于最难发现的一类问题。
 *
 * | | 版本正文 | 翻译在哪 |
 * | --- | --- | --- |
 * | **v3** | `renditions[].{full,game}.lines[]` | 行内字段 `zh-CN` |
 * | **v4** | 同上（行内**没有** `zh-CN`） | `translationEditions[].renditions[].{full,game}.translations[]`，**按下标平行对应** |
 *
 * v4 的译本选择：用 `defaultTranslationEditionKey`（实测 = `main`，label 已是中文「默认译本」）。
 *
 * ## 没有时间戳
 *
 * 数据源的校验器是**严格键白名单**，多一个 `startTime` 就会整份文档判非法；
 * 全文件也扫不到任何时间类字段。所以**做不到逐句卡拉OK高亮**，
 * 参考站（pjsk.moe）自己也没做。这里不做假的"按行数均分估算"——
 * 那只会得到一条会飘的高亮线。
 */
// ─────────────────────────────────────────────────────────────
// 模型
// ─────────────────────────────────────────────────────────────

/** 注音：`text` 是原字，`reading` 是假名（`null` 表示这段不需要注音）。 */
data class LyricsRuby(val text: String, val reading: String?)

/** 演唱片段。一段台词可能由多个人接唱，所以 `performerIds` 是列表。 */
data class LyricsSegment(
    val text: String,
    val performerIds: List<Int>,
    val ruby: List<LyricsRuby>,
)

/** 一行歌词。 */
data class LyricsLine(
    val id: String,
    val order: Int,
    val japanese: String,
    /** 中文翻译；v4 里从译本的平行数组按下标取。取不到就是 null。 */
    val translation: String?,
    val segments: List<LyricsSegment>,
    /** 这一行之前要不要断开一个段落（数据里没有空行约定，只靠这个标记）。 */
    val stanzaBreakBefore: Boolean,
) {
    /** 这一行出现过哪些演唱者（按出现顺序去重）。 */
    val performerIds: List<Int>
        get() = segments.flatMap { it.performerIds }.distinct()

    /** 这一行有没有假名注音（决定要不要走 ruby 渲染路径）。 */
    val hasRuby: Boolean get() = segments.any { seg -> seg.ruby.any { it.reading != null } }
}

/** 一个版本（`full` 完整版 / `game` 游戏剪辑版）。 */
data class LyricsVersion(val kind: String, val lines: List<LyricsLine>)

/** 来源与许可的一条（**许可要求必须展示**）。 */
data class LyricsCredit(
    val provider: String,
    val title: String,
    val revisionUrl: String?,
    val licenseName: String?,
    val licenseUrl: String?,
) {
    /** 显示用的来源名。认不出的 provider 原样显示，不编。 */
    val providerLabel: String
        get() = when (provider) {
            "sekaipedia" -> "Sekaipedia"
            "vocaloid_fandom" -> "Vocaloid Lyrics Wiki"
            "moegirl_public_exact" -> "萌娘百科"
            else -> provider
        }
}

/**
 * 一个演唱版本对应的歌词。
 *
 * 注意 rendition **不是** `musicVocals` 的一一对应：
 * 伴奏版没有词、有的曲子几个音源版本共用一份词（实测 585 有 7 个 rendition、
 * 而它的 `performers` 全是空的）。所以界面上按 [label] 展示，不强行和音源版本对齐。
 */
data class LyricsRendition(
    val key: String,
    val kind: String,
    /** 数据里的原始标签（英文），如 `SEKAI Version`。 */
    val rawLabel: String,
    val performerNames: List<String>,
    val full: LyricsVersion?,
    val game: LyricsVersion?,
    val credits: List<LyricsCredit>,
    val translators: List<String>,
) {
    /** 中文标签。规则见 [renditionDisplayLabel]。 */
    val displayLabel: String get() = renditionDisplayLabel(kind, rawLabel, performerNames)

    val hasFull: Boolean get() = full != null
    val hasGame: Boolean get() = game != null
}

/** 某首歌的歌词文档。 */
data class LyricsDocument(
    val musicId: Int,
    val revision: Int,
    val state: String,
    val renditions: List<LyricsRendition>,
)

/** 歌词索引里的一行。索引很小（711 首），一次拉全表。 */
data class LyricsIndexEntry(
    val musicId: Int,
    val revision: Int,
    val state: String,
    val titleJa: String?,
    val titleZh: String?,
    val availableVersions: List<String>,
) {
    /** 这份数据**存在**（索引里有），不代表一定有歌词正文。 */
    val hasDocument: Boolean
        get() = state == "complete" || state == "game_only" || state == "incomplete"
}

/** 歌词加载结果。UI 按这个分支显示，不用自己猜状态。 */
sealed interface LyricsResult {
    /** 索引里根本没有这首歌。 */
    data object NoEntry : LyricsResult

    /** 索引说这首曲子**没有歌词**（实测原因是纯器乐）。 */
    data class NoLyrics(val reason: String?) : LyricsResult

    /** 有文档但还在录入。 */
    data class Incomplete(val document: LyricsDocument) : LyricsResult

    data class Ok(val document: LyricsDocument, val entry: LyricsIndexEntry?) : LyricsResult

    data class Failed(val message: String) : LyricsResult
}

// ─────────────────────────────────────────────────────────────
// 纯函数解析
// ─────────────────────────────────────────────────────────────

private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

private fun JsonObject.arr(key: String): JsonArray? = this[key] as? JsonArray

private fun JsonElement?.asObj(): JsonObject? = this as? JsonObject

private fun JsonObject.bool(key: String): Boolean = str(key) == "true"

/**
 * 把 `performerId` 变成角色 id。
 *
 * 实测格式是**中文**的「歌唱者-17」「歌唱者-01」（就是 `gameCharacterId`）。
 * 这里取**结尾的数字**而不是硬匹配前缀：前缀万一以后改字，数字部分仍然是对的。
 */
fun parsePerformerId(raw: String?): Int? {
    val text = raw?.trim().orEmpty()
    if (text.isEmpty()) return null
    val digits = Regex("(\\d+)\\s*$").find(text)?.groupValues?.get(1) ?: return null
    return digits.toIntOrNull()
}

/**
 * 版本的中文标签。
 *
 * 数据里的 `label` 是英文（`SEKAI Version` / `Alt. Group Covers (Full) — Leo/need`），
 * 直接显示对中文界面很突兀，所以按 `kind` 给中文，再补上演唱者。
 *
 * `performers` 可能是空的（实测 585 的 7 个 rendition 全空），
 * 这时退回到原始标签里**破折号后面那段**——那里通常正是团名/角色名，
 * 否则 5 个 rendition 会得到 5 个一模一样的「另一版」，用户根本分不清。
 */
fun renditionDisplayLabel(kind: String?, rawLabel: String, performerNames: List<String>): String {
    val base = when (kind) {
        "vocaloid" -> "VIRTUAL SINGER 版"
        "sekai" -> "SEKAI 版"
        "alternate" -> "另一版"
        null -> null
        else -> null
    }
    if (performerNames.isNotEmpty()) {
        val names = performerNames.joinToString("、")
        return if (base != null) "$base · $names" else "$rawLabel · $names"
    }
    val tail = rawLabel.substringAfterLast('—').trim().ifBlank { rawLabel }
    return if (base != null) "$base（$tail）" else tail
}

/** `state` → 中文说明。 */
fun lyricsStateLabel(state: String?): String = when (state) {
    "complete" -> "完整歌词"
    "game_only" -> "仅游戏版歌词"
    "satisfied_no_lyrics" -> "无歌词"
    "incomplete" -> "歌词录入中"
    else -> "未知状态"
}

/** `noLyricsReason` → 中文说明。 */
fun noLyricsReasonLabel(reason: String?): String = when (reason) {
    "catalog_instrumental" -> "这首曲子是纯器乐，本来就没有歌词"
    null -> "这首曲子目前没有歌词资料"
    else -> "这首曲子目前没有歌词资料（$reason）"
}

/** 解析索引（711 首，一次全拉）。 */
fun parseLyricsIndex(text: String): List<LyricsIndexEntry> {
    val root = text.parseJsonObjectOrNull() ?: return emptyList()
    val songs = root.arr("songs") ?: return emptyList()
    return songs.mapNotNull { element ->
        val obj = element.asObj() ?: return@mapNotNull null
        val musicId = obj.intOrNull("musicId") ?: return@mapNotNull null
        val title = obj.obj("title")
        LyricsIndexEntry(
            musicId = musicId,
            revision = obj.intOrNull("revision") ?: 0,
            state = obj.str("state").orEmpty(),
            titleJa = title?.str("ja-JP"),
            titleZh = title?.str("zh-CN"),
            availableVersions = obj.arr("availableVersions")
                ?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
                .orEmpty(),
        )
    }
}

/** 解析一个歌词文档（v3 / v4 都支持）。 */
fun parseLyricsDocument(text: String, fallbackMusicId: Int): LyricsDocument? {
    val root = text.parseJsonObjectOrNull() ?: return null
    val version = root.intOrNull("version") ?: 0
    val musicId = root.intOrNull("musicId") ?: fallbackMusicId
    val renditionArray = root.arr("renditions") ?: return null

    // v4：翻译在译本里，按「renditionKey → {版本 → 平行字符串数组}」索引
    val editionTranslations: Map<String, Map<String, List<String>>> =
        if (version >= 4) parseEditionTranslations(root) else emptyMap()

    val renditions = renditionArray.mapNotNull { element ->
        val obj = element.asObj() ?: return@mapNotNull null
        val key = obj.str("key") ?: return@mapNotNull null
        val perRenditionTranslations = editionTranslations[key].orEmpty()

        val full = obj.obj("full")?.let { parseLyricsVersion(it, perRenditionTranslations["full"]) }
        val game = obj.obj("game")?.let { parseLyricsVersion(it, perRenditionTranslations["game"]) }
        // 两个版本都没有正文的行 → 这份 rendition 没内容，丢掉
        if (full == null && game == null) return@mapNotNull null

        LyricsRendition(
            key = key,
            kind = obj.str("kind").orEmpty(),
            rawLabel = obj.str("label").orEmpty().ifBlank { key },
            performerNames = obj.arr("performers")
                ?.mapNotNull { (it.asObj()?.str("name")) }
                .orEmpty(),
            full = full,
            game = game,
            credits = obj.arr("provenance").orEmpty().mapNotNull { parseCredit(it.asObj()) }.distinct(),
            translators = obj.obj("translationCredits")?.let { credits ->
                listOfNotNull(credits.str("translation"), credits.str("proofreading"))
            }.orEmpty(),
        )
    }

    return LyricsDocument(
        musicId = musicId,
        revision = root.intOrNull("revision") ?: 0,
        state = root.str("state").orEmpty(),
        renditions = renditions,
    )
}

private fun parseCredit(obj: JsonObject?): LyricsCredit? {
    if (obj == null) return null
    val provider = obj.str("provider") ?: return null
    return LyricsCredit(
        provider = provider,
        title = obj.str("title").orEmpty(),
        revisionUrl = obj.str("revisionUrl"),
        licenseName = obj.str("licenseName"),
        licenseUrl = obj.str("licenseUrl"),
    )
}

/**
 * v4：从 `translationEditions` 里取出「默认译本」的平行翻译数组。
 *
 * 结构：`translationEditions[].renditions[].{full,game}.translations: string[]`，
 * 与同 key 的 `renditions[].{full,game}.lines[]` **按下标一一对应**。
 */
private fun parseEditionTranslations(root: JsonObject): Map<String, Map<String, List<String>>> {
    val editions = root.arr("translationEditions") ?: return emptyMap()
    val defaultKey = root.str("defaultTranslationEditionKey")
    val chosen = editions
        .mapNotNull { it.asObj() }
        .let { list -> list.firstOrNull { it.str("key") == defaultKey } ?: list.firstOrNull() }
        ?: return emptyMap()

    val result = mutableMapOf<String, MutableMap<String, List<String>>>()
    chosen.arr("renditions").orEmpty().forEach { element ->
        val obj = element.asObj() ?: return@forEach
        val renditionKey = obj.str("renditionKey") ?: return@forEach
        val perVersion = result.getOrPut(renditionKey) { mutableMapOf() }
        listOf("full", "game").forEach { kind ->
            val texts = obj.obj(kind)?.arr("translations")
                ?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
            if (!texts.isNullOrEmpty()) perVersion[kind] = texts
        }
    }
    return result
}

/**
 * 解析一个版本。
 *
 * @param parallelTranslations v4 的平行翻译数组（可能为 null = v3，走行内 `zh-CN`）
 */
private fun parseLyricsVersion(block: JsonObject, parallelTranslations: List<String>?): LyricsVersion? {
    val lines = block.arr("lines") ?: return null
    val parsed = lines.mapIndexedNotNull { index, element ->
        val obj = element.asObj() ?: return@mapIndexedNotNull null
        val japanese = obj.str("japanese") ?: return@mapIndexedNotNull null
        // v3 用行内的 zh-CN；v4 用平行数组（下标对齐）。两个都没有就是没有翻译。
        val inline = obj.str("zh-CN")?.takeIf { it.isNotBlank() }
        val parallel = parallelTranslations?.getOrNull(index)?.takeIf { it.isNotBlank() }
        LyricsLine(
            id = obj.str("id").orEmpty(),
            order = obj.intOrNull("order") ?: index,
            japanese = japanese,
            translation = inline ?: parallel,
            segments = obj.arr("segments").orEmpty().mapNotNull { parseSegment(it.asObj()) },
            stanzaBreakBefore = obj.bool("stanzaBreakBefore"),
        )
    }
    return LyricsVersion(kind = block.obj("version")?.str("kind").orEmpty(), lines = parsed)
}

private fun parseSegment(obj: JsonObject?): LyricsSegment? {
    if (obj == null) return null
    return LyricsSegment(
        text = obj.str("text").orEmpty(),
        performerIds = obj.arr("performerIds").orEmpty().mapNotNull { parsePerformerId((it as? kotlinx.serialization.json.JsonPrimitive)?.content) },
        ruby = obj.arr("ruby").orEmpty().mapNotNull { r ->
            val ro = r.asObj() ?: return@mapNotNull null
            LyricsRuby(text = ro.str("text").orEmpty(), reading = ro.str("reading"))
        },
    )
}
