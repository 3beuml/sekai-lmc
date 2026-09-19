package com.pjsk.toolbox.data.sync

import com.pjsk.toolbox.ui.common.intOrNull
import com.pjsk.toolbox.ui.common.longOrNull
import com.pjsk.toolbox.ui.common.str
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 单行 master data 的**字段裁剪器**。
 *
 * 为什么需要：`MasterRowEntity.data` 存的是原始 JSON，这在大多数表上都很好
 * （体积小、字段随版本变化也不怕）。但有例外：`cards.json` 有 **35 MB**，
 * 其中 99% 是 `cardParameters` —— 一个把「每张卡 × 每个等级 × 每种属性」全列出来的
 * 大数组（4★ 卡一张就有 60 级 × 3 属性 = 180 条记录，约 24 KB）。
 *
 * 而我们真正要用的，只是每种属性随等级变化的**数值序列**。所以导入时把它压成
 * `{"p1":[...],"p2":[...],"p3":[...]}`，顺带把 UI 需要的字段挑出来，
 * **35 MB → 约 1 MB**，而且信息一点没丢（任意等级的性能值都还在，以后要做等级滑块也不用重新同步）。
 *
 * 返回 `null` 表示这一行可以整行丢弃。
 */
fun interface RowProjector {
    fun project(obj: JsonObject): JsonObject?
}

object RowProjectors {

    const val CARDS_FILE = "cards.json"
    const val GACHAS_FILE = "gachas.json"

    /**
     * 该表要用哪个裁剪器。返回 `null` 表示**不裁剪**，直接存原始 JSON 文本
     * （省掉一次「解析→重新序列化」，54 MB 的表因此还能保持快速导入）。
     */
    fun forFile(fileName: String): RowProjector? = when (fileName) {
        CARDS_FILE -> CARDS
        GACHAS_FILE -> GACHAS
        else -> null
    }

    // ─────────────────────────────────────────────────────────────
    // cards.json
    // ─────────────────────────────────────────────────────────────

    /**
     * `cardParameterType` → 属性名的映射。
     *
     * 实测确认（抓 `cards.json` 前 2 MB / 111 张卡统计）：`cardParameterType` 只取
     * `param1` / `param2` / `param3` 三个值，**不存在 param4**，
     * 所以「综合力」必须由三项相加得到，数据里没有现成的总和。
     *
     * 对应关系沿用社区叫法（与网页版一致）：表现力(Vo) / 技巧(Da) / 体能(Vi)。
     */
    private const val PARAM_VOCAL = "param1"
    private const val PARAM_DANCE = "param2"
    private const val PARAM_VISUAL = "param3"

    /**
     * 卡牌行裁剪。产出结构（**官方字段名保持不变**，只有三处是自定义的短名）：
     * ```json
     * {
     *   "id": 1,
     *   "assetbundleName": "res001_no001",  // 官方名，拼图片 URL 用
     *   "cardRarityType": "rarity_1",
     *   "attr": "cool",
     *   "characterId": 1,
     *   "skillId": 1,                       // 去 skills.json 查技能描述
     *   "cardSkillName": "足元の小さな花",
     *   "releaseAt": 1601391600000,
     *   "hasTrained": false,                // ← 自定义：有没有「觉醒（特训后）」卡面
     *   "trainBonus": [0, 0, 0],            // ← 自定义：特训给三项属性各加多少
     *   "maxLevel": 20,                     // ← 自定义：数值序列的长度（= 该稀有度最终等级上限）
     *   "p1": [1065, ...],                  // ← 自定义：表现力，下标 0 = 1 级
     *   "p2": [ ... ],                      // ← 自定义：技巧
     *   "p3": [ ... ]                       // ← 自定义：体能
     * }
     * ```
     *
     * 为什么**刻意保留官方字段名**而不是全改成短名：`AssetUrls` / `TablePresentation` /
     * `JsonFormat` 里那些通用助手都按官方字段名取值（`assetbundleName()` 等），
     * 保留原名就不用为卡牌开一套特例，通用表浏览器也能照常显示卡牌表。
     *
     * `hasTrained` 的判据是 `specialTrainingCosts` 是否非空。**这不是猜测**：
     * 实测前 2 MB 的 111 张卡，`specialTrainingCosts` 长度只有两种取值 ——
     * `rarity_1`/`rarity_2` 恒为 0，`rarity_3`/`rarity_4` 恒为 2；
     * 而 CDN 上 `card_after_training.webp` 的存在性也恰好是
     * `rarity_1`/`rarity_2`/`rarity_birthday` 全部 404、`rarity_3`/`rarity_4` 全部 200。
     * 两边完全吻合，所以可以直接用它来决定「要不要显示右半张觉醒图」，
     * 而不用去请求一个必然 404 的 URL。
     * （另有更权威的旁证：`cardRarities.json` 里只有 `rarity_3`/`rarity_4` 带
     * `trainingMaxLevel` 字段，`rarity_1`/`rarity_2`/`rarity_birthday` 都没有。）
     */
    val CARDS = RowProjector { obj -> projectCard(obj) }

    /**
     * 卡池。
     *
     * ⚠️ `gachas.json` 原始 **45.1 MB**（1004 个卡池），一直被"没有裁剪器且超过 4 MB"的规则挡在
     * 快照之外。它大的原因只有一个：每个卡池内联了 `gachaCardRarityRates`（60 条概率明细）
     * 和 `gachaDetails`（19~60 张卡的权重）。裁剪掉这些之后只剩 **1.3 MB（2.9%）**。
     *
     * 保留 `gachaDetails` 的 **`cardId`**（丢掉 `weight`）—— 这是「卡池 → 卡牌」的唯一映射，
     * 卡池详情页要靠它列出这个池子里的卡、并让用户点进卡牌详情。
     *
     * 另外保留 `gachaPickups` 的 `cardId` 成 `pk` —— 这是**官方口径的「UP 卡」**
     * （池子门面那几张），卡池详情页靠它把重点卡排在最前、并打 UP 标。
     * 实测 1004 个池全都有 pickups（共 3691 条），且**永远是 `gc` 的真子集**，
     * 所以「UP」不需要任何猜测。代价约 25 KB。
     */
    val GACHAS = RowProjector { obj -> projectGacha(obj) }

    private fun projectGacha(obj: JsonObject): JsonObject? {
        val id = obj.intOrNull("id") ?: return null
        // 幂等保护：已经裁过的行（有 gc 字段、没有 gachaDetails）原样返回，
        // 否则 App 导入内置快照时会再裁一次，把卡牌列表写成空数组。
        if (obj["gc"] is JsonArray && obj["gachaDetails"] == null) return obj
        return buildJsonObject {
            put("id", id)
            obj.str("gachaType")?.let { put("gachaType", it) }
            obj.str("name")?.let { put("name", it) }
            obj.str("assetbundleName")?.let { put("bundle", it) }
            obj.longOrNull("startAt")?.let { put("startAt", it) }
            obj.longOrNull("endAt")?.let { put("endAt", it) }
            (obj["isShowPeriod"] as? JsonPrimitive)?.content?.toBooleanStrictOrNull()?.let { put("isShowPeriod", it) }
            // 这个池子里会出的卡（有序、去重）
            put("gc", buildJsonArray {
                val seen = HashSet<Int>()
                (obj["gachaDetails"] as? JsonArray).orEmpty().forEach { element ->
                    val cardId = (element as? JsonObject)?.intOrNull("cardId") ?: return@forEach
                    if (seen.add(cardId)) add(cardId)
                }
            })
            // 这个池子的 UP 卡（有序、去重）。见上面 GACHAS 的说明。
            put("pk", buildJsonArray {
                val seen = HashSet<Int>()
                (obj["gachaPickups"] as? JsonArray).orEmpty().forEach { element ->
                    val cardId = (element as? JsonObject)?.intOrNull("cardId") ?: return@forEach
                    if (seen.add(cardId)) add(cardId)
                }
            })
        }
    }

    private fun projectCard(obj: JsonObject): JsonObject? {
        val id = obj.intOrNull("id") ?: return null

        // ── 幂等保护（必须的）──
        //
        // 内置数据快照（assets/datapack/）里存的就是**已经裁剪过**的行，而 App 导入时会
        // 再走一次同一个裁剪器。没有这个保护，第二次裁剪会因为找不到 cardParameters
        // 而把 p1/p2/p3 全部写成**空数组**，hasTrained 也会变成 false。
        // 表现是「综合力显示 0、觉醒图不显示」，而且不报任何错 —— 极难排查。
        // 判据：已经有 p1、且已经没有 cardParameters，说明这行已经裁过了。
        if (obj["p1"] is JsonArray && obj.arrayOrNull("cardParameters") == null) return obj

        // 把扁平的大数组按属性分组，再按等级排序，取出数值序列
        val series = HashMap<String, MutableList<Pair<Int, Int>>>(3)
        obj.arrayOrNull("cardParameters")?.forEach { element ->
            val p = element as? JsonObject ?: return@forEach
            val type = p.stringOrNull("cardParameterType") ?: return@forEach
            val level = p.intOrNull("cardLevel") ?: return@forEach
            val power = p.intOrNull("power") ?: return@forEach
            series.getOrPut(type) { ArrayList() } += level to power
        }

        fun toJsonArray(type: String): JsonArray {
            val ordered = series[type]?.sortedBy { it.first } ?: emptyList()
            return buildJsonArray { ordered.forEach { add(JsonPrimitive(it.second)) } }
        }

        val maxLevel = series.values.maxOfOrNull { list -> list.maxOfOrNull { it.first } ?: 0 } ?: 0
        val hasTrained = (obj.arrayOrNull("specialTrainingCosts")?.size ?: 0) > 0

        return buildJsonObject {
            put("id", id)
            obj.stringOrNull("assetbundleName")?.let { put("assetbundleName", it) }
            obj.stringOrNull("cardRarityType")?.let { put("cardRarityType", it) }
            obj.stringOrNull("attr")?.let { put("attr", it) }
            obj.intOrNull("characterId")?.let { put("characterId", it) }
            obj.intOrNull("skillId")?.let { put("skillId", it) }
            obj.stringOrNull("cardSkillName")?.let { put("cardSkillName", it) }
            obj.longOrNull("releaseAt")?.let { put("releaseAt", it) }
            // prefix 必须保留：它是卡牌在**日服**里的显示名。
            // 踩过的坑：第一版把它裁掉了，于是简中服还没更新的新卡既没有中文名、
            // 也没有日文原名，界面上只能显示 "#1465"。
            obj.stringOrNull("prefix")?.let { put("prefix", it) }
            // 详情页「基本信息」要用的字段（都很短，留着不占地方）
            obj.stringOrNull("gachaPhrase")?.let { put("gachaPhrase", it) }
            obj.stringOrNull("supportUnit")?.let { put("supportUnit", it) }
            obj.intOrNull("cardSupplyId")?.let { put("cardSupplyId", it) }
            put("hasTrained", hasTrained)
            // 「出厂即特训后」的卡（如联动限定卡）：它**根本没有未觉醒卡面**，
            // 请求 card_normal.webp 会 404。实测例：id=1462（res017_no056）。
            // 所以卡面显示必须是三态，不能是「有没有觉醒图」两态。
            put("trainedOnly", obj.stringOrNull("initialSpecialTrainingStatus") == "done")
            put(
                "trainBonus",
                buildJsonArray {
                    add(JsonPrimitive(obj.intOrNull("specialTrainingPower1BonusFixed") ?: 0))
                    add(JsonPrimitive(obj.intOrNull("specialTrainingPower2BonusFixed") ?: 0))
                    add(JsonPrimitive(obj.intOrNull("specialTrainingPower3BonusFixed") ?: 0))
                },
            )
            put("maxLevel", maxLevel)
            put("p1", toJsonArray(PARAM_VOCAL))
            put("p2", toJsonArray(PARAM_DANCE))
            put("p3", toJsonArray(PARAM_VISUAL))
        }
    }
}
