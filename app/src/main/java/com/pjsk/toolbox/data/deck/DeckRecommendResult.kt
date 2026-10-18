package com.pjsk.toolbox.data.deck

import com.pjsk.toolbox.ui.common.str
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 引擎（allium-deck WASM `recommend`）返回的 JSON → 强类型。
 *
 * ## 为什么单独一层
 *
 * 引擎输出是**扁平的 snake_case JSON**，界面上要用的是"排名 / 综合力 / 5 张卡"。
 * 如果在 Compose 里直接翻 `JsonObject`，字段名一改就是空值而不是编译错误 ——
 * 而空值在界面上表现为"综合力 0"，看着像"账号很弱"而不是"解析错了"。
 * 所以这里把读取集中成一个纯函数，交给 `tools/logic-check` 断言。
 *
 * ## 字段名两种写法都认
 *
 * 引擎的 `parameters.md` 明确 `recommend` 的**入参** camelCase / snake_case 都收。
 * 出参我们按调研实测的 snake_case 读（`total_power` / `card_id` …），但同时接受
 * camelCase 别名 —— 多认一种写法的代价是一次 `?:`，而认错的代价是整页数字全为 0。
 *
 * ## 缺字段一律给默认值，不抛
 *
 * 只有顶层不是 JSON、或没有 `decks` 数组时才抛 —— 那两种情况界面必须报错而不是显示空列表。
 * 单张卡缺 `event_bonus`、整套缺 `live_score` 都属于**正常**（`target=power` 不给分数）。
 */
data class RecommendedCard(
    /** 引擎的 `card_id` 就是游戏里的**卡 id**（不是 userCard id），可直接 join 内置 `cards.json`。 */
    val cardId: Int,
    val rarity: String,
    val attr: String,
    val characterId: Int,
    val level: Int,
    val masterRank: Int,
    val powerTotal: Int,
    /**
     * 技能加分率，**已经是百分数**（引擎 README 的样例是不带单位的 `120.0`，即 120%）。
     * 不要再乘 100 —— 那会变成 12000%。
     */
    val skillScoreUp: Double,
    val skillLevel: Int,
    val episode1Read: Boolean,
    val episode2Read: Boolean,
    /** 引擎**实际采用**的特训状态。卡图标用哪一版（特训前/后）应当听它的，不是猜的。 */
    val trained: Boolean,
    val hasCanvasBonus: Boolean,
    val canvasPower: Int,
    val eventBonus: Double?,
)

/** 一套推荐卡组。 */
data class RecommendedDeck(
    val rank: Int,
    val cards: List<RecommendedCard>,
    val totalPower: Int,
    /** 引擎实际优化的目标值（`target=power` 时与 [totalPower] 相同）。 */
    val targetValue: Double,
    /** 技能合计（倍率口径，不是分数）。 */
    val skillScore: Double,
    val liveScore: Double,
    val eventPoint: Int?,
    /**
     * 活动加成合计（**百分数，可能带半档**：引擎的加成按 0.1% 计，`230.5` 就是 230.5%）。
     * 所以这里用 Double —— 截成 Int 会把 230.5 显示成 230，而这个数在控分场景下是要精确对的。
     */
    val eventBonusTotal: Double?,
    /**
     * 请求了**精确加成档**（控分）时，引擎回报这一套对应哪个档位（百分数）。
     * 没请求档位、或引擎没回这个字段时是 null —— 界面据此决定要不要显示「档 N%」。
     */
    val targetBonus: Int?,
    /**
     * 世界链接（WL）终章的**洗牌加成率**（百分数）。0 表示不是 WL 或没吃到。
     *
     * 为什么要单独露出来：WL 终章的加成 = 活动卡加成 + 洗牌加成，两截加起来能到 300%+。
     * 只显示总数的话，用户会以为"这期活动加成怎么这么高"，看不出其中 50% 来自组队多样性。
     */
    val shuffleBonusRate: Int?,
    val multiLiveScoreUp: Double?,
)

/** 一次推荐的完整结果。 */
data class DeckRecommendResult(
    val decks: List<RecommendedDeck>,
    /** 引擎自报的搜索完整性：`complete` 才是**精确** Top-K。 */
    val completion: String,
    val timedOut: Boolean,
    val buildPoolMs: Int,
    val searchMs: Int,
    val poolSize: Int,
) {
    /**
     * 结果是否达到「精确」。
     *
     * 引擎的契约：只有 `completion == "complete"` 才保证是完整可行集的真正 Top-K；
     * 超时返回的是**合法但未证明完整**的当前最优。界面上必须说清这一点，
     * 不能让用户以为「就这 5 套了」。
     */
    val isExact: Boolean
        get() = completion.equals("complete", ignoreCase = true) ||
            (completion.isBlank() && !timedOut)
}

private val lenientJson = Json { isLenient = true; ignoreUnknownKeys = true }

/**
 * 解析 `recommend` 的返回值。
 *
 * @throws IllegalArgumentException 顶层不是 JSON 对象、或没有 `decks` 数组
 *   （这两种情况下界面上没有任何可信信息可显示）。
 */
fun parseDeckRecommendResult(text: String): DeckRecommendResult {
    val root = runCatching { lenientJson.parseToJsonElement(text) as? JsonObject }.getOrNull()
        ?: throw IllegalArgumentException("引擎返回的不是 JSON 对象")
    val rawDecks = root["decks"] as? JsonArray
        ?: throw IllegalArgumentException("引擎返回里没有 decks 数组")

    val completion = root.readText("completion", "search_completion", "searchCompletion").orEmpty()
    val timedOut = root.readBool("timed_out", "timedOut") ?: false
    // 性能字段在三种输出里分散在不同对象里，**必须全部收进来再逐键找**：
    //  - wasm 绑定（moesekai 的 TS）→ `performance: {build_pool_ms, search_ms, pool_size}`
    //  - 引擎原始 JSON（README 样例）→ `diagnostics.pool_size` + `timing.{build_pool_ms, search_ms}`
    // 只取"第一个存在的对象"会静默丢字段（曾经就是这样：pool_size 读到了、build_pool_ms 恒为 0）。
    val perfSources = listOf("performance", "diagnostics", "timing")
        .mapNotNull { root[it] as? JsonObject }
    fun perfInt(vararg keys: String): Int = perfSources.firstNotNullOfOrNull { it.readInt(*keys) } ?: 0

    val decks = rawDecks.mapIndexedNotNull { index, element ->
        val obj = element as? JsonObject ?: return@mapIndexedNotNull null
        val cards = (obj["cards"] as? JsonArray)?.mapNotNull { cardElement ->
            (cardElement as? JsonObject)?.let(::readCard)
        }.orEmpty()
        RecommendedDeck(
            rank = obj.readInt("rank") ?: (index + 1),
            cards = cards,
            // 兜底：输出里没给整套综合力时，用逐卡 power 求和。
            // 不兜底的话界面上会显示「综合力 0」—— 那看起来像"这套卡很弱"，
            // 而不是"字段没读到"，是最糟糕的错法。
            totalPower = obj.readInt("total_power", "totalPower")
                ?: cards.sumOf { it.powerTotal },
            targetValue = obj.readDouble("target_value", "targetValue") ?: 0.0,
            skillScore = obj.readDouble("skill_score", "skillScore") ?: 0.0,
            liveScore = obj.readDouble("live_score", "liveScore") ?: 0.0,
            eventPoint = obj.readInt("event_point", "eventPoint"),
            eventBonusTotal = obj.readDouble("event_bonus_total", "eventBonusTotal"),
            // 控分档位：引擎可能叫 target_bonus / tier，两种都认
            targetBonus = obj.readInt("target_bonus", "targetBonus", "tier"),
            shuffleBonusRate = obj.readInt("shuffle_bonus_rate", "shuffleBonusRate"),
            multiLiveScoreUp = obj.readDouble("multi_live_score_up", "multiLiveScoreUp"),
        )
    }

    return DeckRecommendResult(
        decks = decks,
        completion = completion,
        timedOut = timedOut,
        buildPoolMs = perfInt("build_pool_ms", "buildPoolMs"),
        searchMs = perfInt("search_ms", "searchMs"),
        poolSize = perfInt("pool_size", "poolSize"),
    )
}

private fun readCard(obj: JsonObject): RecommendedCard? {
    // 没有 cardId 的条目无法连接到内置卡表，也就无法显示 —— 直接丢掉，
    // 但保留其余条目（宁可少一格，也不要整页空白）。
    val cardId = obj.readInt("card_id", "cardId") ?: return null
    return RecommendedCard(
        cardId = cardId,
        rarity = obj.readText("rarity").orEmpty(),
        attr = obj.readText("attr").orEmpty(),
        characterId = obj.readInt("character_id", "characterId") ?: 0,
        level = obj.readInt("level") ?: 0,
        masterRank = obj.readInt("master_rank", "masterRank") ?: 0,
        powerTotal = obj.readInt("power_total", "powerTotal") ?: 0,
        skillScoreUp = obj.readDouble("skill_score_up", "skillScoreUp") ?: 0.0,
        skillLevel = obj.readInt("skill_level", "skillLevel") ?: 0,
        episode1Read = obj.readBool("episode1_read", "episode1Read") ?: false,
        episode2Read = obj.readBool("episode2_read", "episode2Read") ?: false,
        trained = obj.readBool("trained") ?: false,
        hasCanvasBonus = obj.readBool("has_canvas_bonus", "hasCanvasBonus") ?: false,
        canvasPower = obj.readInt("canvas_power", "canvasPower") ?: 0,
        eventBonus = obj.readDouble("event_bonus", "eventBonus"),
    )
}

// ── 按优先级取第一个存在的键 ──

private fun JsonObject.readText(vararg keys: String): String? {
    for (key in keys) str(key)?.takeIf { it.isNotBlank() }?.let { return it }
    return null
}

private fun JsonObject.readInt(vararg keys: String): Int? {
    for (key in keys) readText(key)?.toDoubleOrNull()?.let { return it.toInt() }
    return null
}

private fun JsonObject.readDouble(vararg keys: String): Double? {
    for (key in keys) readText(key)?.toDoubleOrNull()?.let { return it }
    return null
}

private fun JsonObject.readBool(vararg keys: String): Boolean? {
    for (key in keys) {
        when ((this[key] as? JsonPrimitive)?.content?.lowercase()) {
            "true" -> return true
            "false" -> return false
        }
    }
    return null
}
