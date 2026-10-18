package com.pjsk.toolbox.data.profile

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

/**
 * 「我的账号」数据模型。
 *
 * 数据来源是 **Haruki 工具箱的公开套件 API**（见 [PlayerRepository]），
 * 它是**玩家自己上传的快照**，不是实时数据 —— 所以快照里一定要带 [PlayerSnapshot.uploadTime]，
 * 界面上必须显示它，否则用户会以为看到的是"刚刚的"。
 *
 * ⚠️ 这里**只放纯数据与纯函数**：解析、校验、统计。
 * 网络与文件 I/O 在 `PlayerRepository`，界面在 `ui/screen`。
 * 这么切是为了让这些逻辑能被 `tools/logic-check` 断言（§8 的约定）。
 */

/** 一个绑定账号的身份：区服 + 游戏 UID。 */
data class PlayerAccount(
    /** 区服 id，与 `ServerRegion.id` 同义：`jp` / `cn` / `tw` / `kr` / `en`。 */
    val server: String,
    /** 游戏 UID（纯数字字符串，**不是**引继码）。 */
    val gameId: String,
) {
    /** 本地缓存文件名。 */
    val cacheFileName: String get() = "${server}_$gameId.json"
}

/** 一张持有的卡。 */
data class OwnedCard(
    val cardId: Int,
    val level: Int,
    val masterRank: Int,
    val skillLevel: Int,
    val trained: Boolean,
    /** `episodes` 里已读的篇数（影响卡面属性加成）。 */
    val episodesRead: Int,
)

/** 一条歌曲成绩（同一首歌的不同难度各一条）。 */
data class MusicResult(
    val musicId: Int,
    /** 原始难度字符串（`easy` / `normal` / `hard` / `expert` / `master` / `append`）。 */
    val difficulty: String,
    val fullCombo: Boolean,
    val allPerfect: Boolean,
    val highScore: Long,
)

/** 一种材料的持有量。 */
data class MaterialAmount(val materialId: Int, val quantity: Int)

/** 玩家快照：公开套件 API 里我们真正要用的那部分。 */
data class PlayerSnapshot(
    val account: PlayerAccount,
    /** Unix **秒**（上游就是秒；界面换算成本地时间）。 */
    val uploadTime: Long,
    val name: String,
    val rank: Int,
    val coin: Long,
    val totalExp: Long,
    val cards: List<OwnedCard>,
    val musicResults: List<MusicResult>,
    val materials: List<MaterialAmount>,
)

// ─────────────────────────────────────────────────────────────
// 校验（纯函数）
// ─────────────────────────────────────────────────────────────

/**
 * 游戏 UID 是否合法。
 *
 * 规则与社区前端一致：**首字符 1–9，其后至多 19 位数字**（即 1–20 位）。
 * ⚠️ 这条只是"格式像"，**不代表这个 UID 存在** —— 上游对"不存在 / 没上传 / 没开公开"
 * 返回的是同一个 404，无法区分（见 `PlayerRepository` 的失败态说明）。
 */
fun isValidGameId(raw: String): Boolean = GAME_ID_REGEX.matches(normalizeGameId(raw))

/** 去掉首尾空白与中间的空格/连字符（用户从游戏里复制的串常带这些）。 */
fun normalizeGameId(raw: String): String =
    raw.filterNot { it.isWhitespace() || it == '-' || it == '　' }

private val GAME_ID_REGEX = Regex("^[1-9]\\d{0,19}$")

// ─────────────────────────────────────────────────────────────
// 统计（纯函数，可断言）
// ─────────────────────────────────────────────────────────────

/** 卡牌侧的汇总。 */
data class CardStats(
    val owned: Int,
    /** `specialTrainingStatus == "done"`（特训完成）的张数。 */
    val trained: Int,
    /** 技能等级最高的那张卡的技能等级；没有卡时是 0。 */
    val maxSkillLevel: Int,
    /** 突破等级≥1 的张数（做过突破的卡有多少）。 */
    val anyMasterRank: Int,
)

fun cardStats(cards: List<OwnedCard>): CardStats = CardStats(
    owned = cards.size,
    trained = cards.count { it.trained },
    maxSkillLevel = cards.maxOfOrNull { it.skillLevel } ?: 0,
    anyMasterRank = cards.count { it.masterRank >= 1 },
)

/** 单个难度的成绩统计。 */
data class DifficultyStat(
    val difficulty: String,
    /** 打过这个难度的曲目数。 */
    val played: Int,
    val fullCombo: Int,
    val allPerfect: Int,
)

/** 难度的展示顺序（未知难度排在最后，不让上游新增难度把界面搞乱）。 */
private val DIFFICULTY_ORDER = listOf("easy", "normal", "hard", "expert", "master", "append")

/**
 * 按难度汇总成绩。
 *
 * 同一首歌同一难度只应有一条；上游若给了多条，这里按"取最好"合并（任一 FC 即 FC），
 * 不因为脏数据把计数算重。
 */
fun musicStats(results: List<MusicResult>): List<DifficultyStat> {
    val merged = LinkedHashMap<Pair<Int, String>, MusicResult>()
    for (r in results) {
        val key = r.musicId to r.difficulty
        val old = merged[key]
        merged[key] = if (old == null) {
            r
        } else {
            old.copy(
                fullCombo = old.fullCombo || r.fullCombo,
                allPerfect = old.allPerfect || r.allPerfect,
                highScore = maxOf(old.highScore, r.highScore),
            )
        }
    }
    return merged.values
        .groupBy { it.difficulty }
        .map { (difficulty, list) ->
            DifficultyStat(
                difficulty = difficulty,
                played = list.size,
                fullCombo = list.count { it.fullCombo },
                allPerfect = list.count { it.allPerfect },
            )
        }
        .sortedBy { stat ->
            val i = DIFFICULTY_ORDER.indexOf(stat.difficulty.lowercase())
            if (i >= 0) i else DIFFICULTY_ORDER.size
        }
}

/** 难度字符串的展示名。 */
fun difficultyLabel(difficulty: String): String = when (difficulty.lowercase()) {
    "easy" -> "Easy"
    "normal" -> "Normal"
    "hard" -> "Hard"
    "expert" -> "Expert"
    "master" -> "Master"
    "append" -> "Append"
    else -> difficulty
}

/** 材料侧的汇总。 */
data class MaterialStats(val kinds: Int, val nonZeroKinds: Int, val totalQuantity: Long)

fun materialStats(materials: List<MaterialAmount>): MaterialStats = MaterialStats(
    kinds = materials.size,
    nonZeroKinds = materials.count { it.quantity > 0 },
    totalQuantity = materials.sumOf { it.quantity.toLong() },
)

// ─────────────────────────────────────────────────────────────
// 解析（纯函数，可断言）
// ─────────────────────────────────────────────────────────────

private val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }

/**
 * 把公开套件 API 的响应解析成 [PlayerSnapshot]。
 *
 * 解析是**容错**的：上游随时可能加减字段，所以
 *  - 顶层拿不到 `userGamedata` 时返回 null（说明这不是一份可用快照）；
 *  - 数组里单条坏数据只跳过那一条，不让整份解析失败；
 *  - 数字字段缺失时给 0，不让界面崩。
 *
 * `upload_time` 上游可能给秒也可能是毫秒（社区里两种都见过）：这里按月小于 `1e12`
 * 判为秒并换算成秒，避免界面上算出 1970 年。
 */
fun parseSnapshot(raw: String, account: PlayerAccount): PlayerSnapshot? {
    val root = runCatching { lenientJson.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return null
    val gamedata = root.obj("userGamedata") ?: return null

    return PlayerSnapshot(
        account = account,
        uploadTime = root.long("upload_time").let { if (it > 1_000_000_000_000L) it / 1000 else it },
        name = gamedata.str("name").orEmpty(),
        rank = gamedata.int("rank"),
        coin = gamedata.long("coin"),
        totalExp = gamedata.long("totalExp"),
        cards = root.array("userCards").mapNotNull { it as? JsonObject }.mapNotNull { card ->
            val id = card.int("cardId")
            if (id <= 0) return@mapNotNull null
            OwnedCard(
                cardId = id,
                level = card.int("level"),
                masterRank = card.int("masterRank"),
                skillLevel = card.int("skillLevel"),
                trained = card.str("specialTrainingStatus") == "done",
                // `episodes` 形如 {"1":"read","2":"unread"}（上游形状未经逐字段实测，
                // 所以这里只做"值里含 read 且不含 unread"的宽松判断，不假设具体结构）。
                episodesRead = (card["episodes"] as? JsonObject)?.count { (_, v) ->
                    val s = v.toString()
                    s.contains("read") && !s.contains("unread")
                } ?: 0,
            )
        },
        musicResults = root.array("userMusicResults").mapNotNull { it as? JsonObject }.mapNotNull { r ->
            val id = r.int("musicId")
            if (id <= 0) return@mapNotNull null
            MusicResult(
                musicId = id,
                difficulty = r.str("musicDifficultyType").orEmpty(),
                fullCombo = r.bool("fullComboFlg"),
                allPerfect = r.bool("fullPerfectFlg"),
                highScore = r.long("highScore"),
            )
        },
        materials = root.array("userMaterials").mapNotNull { it as? JsonObject }.mapNotNull { m ->
            val id = m.int("materialId")
            if (id <= 0) return@mapNotNull null
            MaterialAmount(materialId = id, quantity = m.int("quantity"))
        },
    )
}

// ── JsonObject 取值小工具（都容错，缺了给默认值；上游加减字段不让界面崩） ──

private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

private fun JsonObject.array(key: String): JsonArray = (this[key] as? JsonArray) ?: JsonArray(emptyList())

private fun JsonObject.str(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.int(key: String): Int =
    (this[key] as? JsonPrimitive)?.intOrNull ?: 0

private fun JsonObject.long(key: String): Long =
    (this[key] as? JsonPrimitive)?.longOrNull ?: 0L

private fun JsonObject.bool(key: String): Boolean =
    (this[key] as? JsonPrimitive)?.booleanOrNull ?: false
