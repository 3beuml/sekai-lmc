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
 * 一位演唱者。
 *
 * ⚠️ [id] 是 **gameCharacterId**（实测歌词里的 `performerId` 形如 `歌唱者-09`，
 * 末尾数字与 master data 的 `gameCharacters.id` 完全一致，抽样 20/20 命中），
 * 所以它既能拿去查头像、也能拿去查角色名。
 *
 * [id] 允许为 null：万一以后数据里出现没有 id 只有名字的条目，名字仍要能显示在版本标签里。
 */
data class LyricsPerformer(val id: Int?, val name: String)

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
    /** 这个版本的演唱者，**保持数据里的顺序**（版本标签按这个顺序显示）。 */
    val performers: List<LyricsPerformer>,
    val full: LyricsVersion?,
    val game: LyricsVersion?,
    val credits: List<LyricsCredit>,
    val translators: List<String>,
) {
    /**
     * **id → 名字**。界面上标注"这一句是谁唱的"必须用它，**不要按位置取**。
     *
     * 踩过的坑（真实 bug）：原来用「这一行里第几个演唱者」当索引进 [performerNames]，
     * 而两个列表毫无关系 —— 某行只有一个人唱时，它的下标永远是 0，
     * 于是整页都显示成名单里的第一个人，头像（用 id 查，是对的）和名字对不上。
     */
    val nameById: Map<Int, String> get() = performers.mapNotNull { p -> p.id?.let { it to p.name } }.toMap()

    /** 只要名字列表（版本标签用，保持数据顺序）。 */
    val performerNames: List<String> get() = performers.map { it.name }

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
    /**
     * 是否走的**兜底解析**（文档结构不是已知的 v1/v3/v4）。
     *
     * 兜底时只能保证"日文原文 + 中文翻译"这两样，来源/演唱者/译本可能缺失，
     * 所以界面上要如实说一句，而不是假装一切正常。
     */
    val degraded: Boolean = false,
)

/**
 * 解析结果。
 *
 * ⚠️ 为什么要区分这么细：以前解析失败直接返回 null，调用方又把它归成"索引里没有这首歌"，
 * 于是界面显示「这首曲子还没有歌词」—— 真相是"格式不认识"。
 * 那一次的代价是：用户在 #803 上看到"没有歌词"，排查时只能靠逐个字段试。
 *
 * 现在的规矩：**不知道就说不支持，别假装没有内容**。
 */
sealed interface LyricsParse {
    /** 解析成功。[degraded] = 走的是兜底路径。 */
    data class Ok(val document: LyricsDocument, val degraded: Boolean) : LyricsParse

    /** 连"行数组"都找不到：记录文档的 version 与顶层字段名，便于排查（并打进日志）。 */
    data class Unsupported(val version: Int, val topFields: List<String>) : LyricsParse

    /** 响应不是 JSON（例如 404 页面、被截断的内容）。 */
    data object NotJson : LyricsParse
}

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

    /** 文档在、但**格式不认识**（不是"没有歌词"）。附带 version 与顶层字段名，便于排查。 */
    data class Unsupported(val version: Int, val topFields: List<String>) : LyricsResult

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

/**
 * 解析一个歌词文档。支持已知的 v3 / v4 结构，并提供**兜底路径**。
 *
 * 线上实测存在三套 schema（新增 2026-09-18 发现 v1）：
 *
 * | | 结构 | 说明 |
 * | --- | --- | --- |
 * | **v1** | 顶层直接 `lines[]`，没有 `renditions`；来源在 `attributions`，译者是 `attribution` 字符串 | 个别老文档（实测 #803）。**没有演唱者名单** |
 * | **v3** | `renditions[].{full,game}.lines[]`，行内 `zh-CN` | 主流 |
 * | **v4** | 同上，但翻译在 `translationEditions[].renditions[].{full,game}.translations[]`（按下标平行） | 少数 |
 *
 * 兜底路径的规矩：**任何位置只要能找到"行数组"（元素是带 `japanese` 的对象），
 * 就至少把原文与翻译显示出来**，并标记 [LyricsDocument.degraded]，
 * 而不是整首歌空白。连行数组都找不到才返回 [LyricsParse.Unsupported]。
 */
fun parseLyrics(text: String, fallbackMusicId: Int): LyricsParse {
    val root = text.parseJsonObjectOrNull() ?: return LyricsParse.NotJson
    val version = root.intOrNull("version") ?: 0
    val musicId = root.intOrNull("musicId") ?: fallbackMusicId
    val revision = root.intOrNull("revision") ?: 0
    val state = root.str("state").orEmpty()

    // ── ① 已知结构：renditions[]（v3 / v4）──
    // 注意：**只要 renditions 字段存在，就走结构化路径**，哪怕里面全被丢弃。
    // 否则「格式支持但这份文档没有可用内容」会被误报成「格式不支持」。
    val renditionArray = root.arr("renditions")
    val editionTranslations: Map<String, Map<String, List<String>>> =
        if (version >= 4) parseEditionTranslations(root) else emptyMap()

    val renditions = renditionArray.orEmpty().mapNotNull { element ->
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
            // ⚠️ 这里必须把 `performerId` 一起留下来（解析成数字当 key）。
            // 只留名字的话，界面就只剩下"按位置猜"一条路 —— 那正是之前的 bug。
            performers = parsePerformers(obj),
            full = full,
            game = game,
            credits = parseCredits(obj),
            translators = parseTranslators(obj),
        )
    }
    if (renditionArray != null) {
        return LyricsParse.Ok(
            LyricsDocument(musicId, revision, state, renditions, degraded = false),
            degraded = false,
        )
    }

    // ── ② 兜底：在文档里找"行数组"（v1 的 lines 就在顶层）──
    val lineElements = findLineArray(root)
        ?: return LyricsParse.Unsupported(version, root.keys.sorted())
    val lines = lineElements.mapIndexedNotNull { index, element ->
        parseLyricsLine(element.asObj(), index, parallelTranslation = null)
    }
    if (lines.isEmpty()) return LyricsParse.Unsupported(version, root.keys.sorted())

    val fallback = LyricsRendition(
        key = "main",
        kind = "",
        // 兜底时不知道版本类型，如实写"默认版本"，不编成"SEKAI 版"
        rawLabel = "默认版本",
        performers = parsePerformers(root),
        full = LyricsVersion(kind = "", lines = lines),
        game = null,
        credits = parseCredits(root),
        translators = parseTranslators(root),
    )
    return LyricsParse.Ok(
        LyricsDocument(musicId, revision, state, listOf(fallback), degraded = true),
        degraded = true,
    )
}

/** 兼容旧调用点与测试：只要文档，失败就是 null。新代码请用 [parseLyrics] 以便区分失败原因。 */
fun parseLyricsDocument(text: String, fallbackMusicId: Int): LyricsDocument? =
    (parseLyrics(text, fallbackMusicId) as? LyricsParse.Ok)?.document

/**
 * 递归找第一个「看起来是歌词行的数组」：元素是对象、且其中多数带 `japanese` 字符串。
 *
 * 深度限制 4 层：既够覆盖 v1（顶层）与未来可能多包一层的结构，
 * 又不会在畸形文档里乱跑。找不到就返回 null → 上层报 Unsupported。
 */
private fun findLineArray(element: JsonElement, depth: Int = 0): List<JsonElement>? {
    if (depth > 4) return null
    when (element) {
        is JsonArray -> {
            val objects = element.filterIsInstance<JsonObject>()
            if (objects.isNotEmpty()) {
                val withJapanese = objects.count { it.str("japanese") != null }
                if (withJapanese >= maxOf(1, objects.size / 2)) return element
            }
            for (child in element) findLineArray(child, depth + 1)?.let { return it }
        }
        is JsonObject -> for (value in element.values) findLineArray(value, depth + 1)?.let { return it }
        else -> Unit
    }
    return null
}

/** 演唱者名单。v3/v4 在 rendition 里；v1 没有（空列表）。 */
private fun parsePerformers(obj: JsonObject): List<LyricsPerformer> =
    obj.arr("performers").orEmpty().mapNotNull { element ->
        val po = element.asObj() ?: return@mapNotNull null
        val name = po.str("name") ?: return@mapNotNull null
        LyricsPerformer(id = parsePerformerId(po.str("performerId")), name = name)
    }

/** 来源与许可。v3/v4 叫 `provenance`，**v1 叫 `attributions`**（两种都认）。 */
private fun parseCredits(obj: JsonObject): List<LyricsCredit> {
    val array = obj.arr("provenance") ?: obj.arr("attributions") ?: return emptyList()
    return array.mapNotNull { parseCredit(it.asObj()) }.distinct()
}

/** 译者。v3/v4 在 `translationCredits` 里；**v1 是 `attribution` 字符串**。 */
private fun parseTranslators(obj: JsonObject): List<String> {
    obj.obj("translationCredits")?.let { credits ->
        val list = listOfNotNull(credits.str("translation"), credits.str("proofreading"))
        if (list.isNotEmpty()) return list
    }
    return listOfNotNull(obj.str("attribution")?.takeIf { it.isNotBlank() })
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
        parseLyricsLine(element.asObj(), index, parallelTranslations?.getOrNull(index))
    }
    return LyricsVersion(kind = block.obj("version")?.str("kind").orEmpty(), lines = parsed)
}

/**
 * 一行歌词的解析 —— **结构化路径与兜底路径共用**。
 *
 * 兜底时 [parallelTranslation] 传 null（旧格式的翻译都在行内 `zh-CN` 里）。
 */
private fun parseLyricsLine(obj: JsonObject?, index: Int, parallelTranslation: String?): LyricsLine? {
    if (obj == null) return null
    val japanese = obj.str("japanese") ?: return null
    // v3 用行内的 zh-CN；v4 用平行数组（下标对齐）。两个都没有就是没有翻译。
    val inline = obj.str("zh-CN")?.takeIf { it.isNotBlank() }
    val parallel = parallelTranslation?.takeIf { it.isNotBlank() }
    return LyricsLine(
        id = obj.str("id").orEmpty(),
        order = obj.intOrNull("order") ?: index,
        japanese = japanese,
        translation = inline ?: parallel,
        segments = obj.arr("segments").orEmpty().mapNotNull { parseSegment(it.asObj()) },
        stanzaBreakBefore = obj.bool("stanzaBreakBefore"),
    )
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
