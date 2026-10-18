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
    const val AREA_ITEMS_FILE = "areaItems.json"
    const val AREA_ITEM_LEVELS_FILE = "areaItemLevels.json"
    const val HONORS_FILE = "honors.json"
    const val MASTER_LESSONS_FILE = "masterLessons.json"
    const val MUSIC_METAS_FILE = "musicMetas.json"

    /**
     * 该表要用哪个裁剪器。返回 `null` 表示**不裁剪**，直接存原始 JSON 文本
     * （省掉一次「解析→重新序列化」，54 MB 的表因此还能保持快速导入）。
     */
    fun forFile(fileName: String): RowProjector? = when (fileName) {
        CARDS_FILE -> CARDS
        GACHAS_FILE -> GACHAS
        AREA_ITEMS_FILE -> AREA_ITEMS
        AREA_ITEM_LEVELS_FILE -> AREA_ITEM_LEVELS
        HONORS_FILE -> HONORS
        MASTER_LESSONS_FILE -> MASTER_LESSONS
        MUSIC_METAS_FILE -> MUSIC_METAS
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

    // ─────────────────────────────────────────────────────────────
    // 综合力（区域道具 / 称号）—— 2026-10 批次 C
    //
    // 这三张表的原始体积合计 4.23 MB（`honors.json` 一张 3.69 MB），
    // 而本 App 只需要其中很少的字段。裁剪口径与依据见 docs/research/pjsk-power-bonds-data-research.md §2.4。
    // ─────────────────────────────────────────────────────────────

    /**
     * 区域道具（56 行）。只留「id + 名字」—— `flavorText` / `spawnPoint` / `assetbundleName` 用不到。
     *
     * 12,568 B → 约 2.5 KB。名字是给「加成明细」用的（不然只能显示「道具 #31 +1.5%」）。
     */
    val AREA_ITEMS = RowProjector { obj ->
        // 幂等：裁过的行只剩 id/name，没有 flavorText
        if (obj["name"] is JsonPrimitive && obj["flavorText"] == null) return@RowProjector obj
        val id = obj.intOrNull("id") ?: return@RowProjector null
        buildJsonObject {
            put("id", id)
            obj.intOrNull("areaId")?.let { put("areaId", it) }
            obj.stringOrNull("name")?.let { put("name", it) }
        }
    }

    /**
     * 区域道具等级（1,140 行 = 57 件 × 20 级）。原始 521,267 B，其中 `sentence`（带 `<color>` 的说明文本）
     * 就占 128,243 B，而且我们从不显示它 —— 丢掉。
     *
     * ⚠️ **这张表没有 `id` 字段**（行由 `areaItemId` + `level` 唯一确定），
     * 而 `master_rows` 的主键是 `(tableName, id)`。直接按 `areaItemId` 当主键会让同一件道具的
     * 20 个等级互相覆盖（只剩最后一级），界面上表现为「所有道具都是 20 级」。
     * 所以这里**自己造一个 id = areaItemId × 1000 + level**，保证唯一且可反解。
     */
    val AREA_ITEM_LEVELS = RowProjector { obj ->
        // 幂等：已经有 id 说明裁过了
        if (obj["id"] is JsonPrimitive) return@RowProjector obj
        val itemId = obj.intOrNull("areaItemId") ?: return@RowProjector null
        val level = obj.intOrNull("level") ?: return@RowProjector null
        buildJsonObject {
            put("id", itemId * 1000 + level)
            put("areaItemId", itemId)
            put("level", level)
            obj.stringOrNull("targetUnit")?.let { put("targetUnit", it) }
            obj.stringOrNull("targetCardAttr")?.let { put("targetCardAttr", it) }
            // ⚠️ 这一项**缺省**表示「不限角色」，绝不能当成 0 号角色（560 行没有这个字段）
            (obj.intOrNull("targetGameCharacterId") ?: 0).takeIf { it > 0 }?.let {
                put("targetGameCharacterId", it)
            }
            put("r", rateArray(obj, "power1BonusRate", "power2BonusRate", "power3BonusRate"))
            put("ar", rateArray(obj, "power1AllMatchBonusRate", "power2AllMatchBonusRate", "power3AllMatchBonusRate"))
        }
    }

    /**
     * 称号（8,801 行 / 3,694,822 B）。体积几乎全部来自每行最多 100 个 `levels`（每级还带一段说明文本）。
     *
     * 裁剪口径：**只保留 `bonus > 0` 的等级，并且丢掉所有等级都 0 的称号**。
     * 依据：`bonus` 全文只有 `0` 与 `10` 两种取值，全表只有 34 个等级是 10（见研究文档 §2.3）。
     * 3,694,822 B → 约 2.4 KB。
     *
     * ⚠️ 代价：查不到的行按「加成 0」处理（界面不会显示这些称号的名字）。
     * 这正好是我们要的 —— 显示一个「+0」的称号没有意义。
     */
    val HONORS = RowProjector { obj ->
        // 幂等：裁过的行 levels 里只有 {level,bonus}，没有 description
        val id = obj.intOrNull("id") ?: return@RowProjector null
        val positiveLevels = buildJsonArray {
            obj.arrayOrNull("levels")?.forEach { element ->
                val levelObj = element as? JsonObject ?: return@forEach
                val bonus = levelObj.intOrNull("bonus") ?: 0
                if (bonus <= 0) return@forEach
                add(
                    buildJsonObject {
                        put("level", levelObj.intOrNull("level") ?: return@forEach)
                        put("bonus", bonus)
                    },
                )
            }
        }
        // 没有任何正加成 → 整行不需要（省掉 8,700 多行）
        if (positiveLevels.isEmpty()) return@RowProjector null
        buildJsonObject {
            put("id", id)
            obj.stringOrNull("name")?.let { put("name", it) }
            obj.stringOrNull("honorRarity")?.let { put("honorRarity", it) }
            put("levels", positiveLevels)
        }
    }

    /**
     * 突破固定加成（25 行 = 5 稀有度 × 突破 1..5）。
     *
     * ⚠️ 这张表**没有 `id`**，主键是 `(cardRarityType, masterRank)`。
     * 直接拿 `masterRank` 当主键的话，5 个稀有度会互相覆盖（只剩 5 行），
     * 而界面上完全看不出异常 —— 所以这里造一个 `id = 稀有度序号 × 10 + masterRank`。
     *
     * 裁剪只留算综合力要的字段；`sentence`/说明文本一律丢掉。
     */
    val MASTER_LESSONS = RowProjector { obj ->
        val rarityKey = obj.stringOrNull("cardRarityType") ?: return@RowProjector null
        val rank = obj.intOrNull("masterRank") ?: return@RowProjector null
        if (rank <= 0) return@RowProjector null
        val ordinal = listOf("rarity_1", "rarity_2", "rarity_3", "rarity_4", "rarity_birthday")
            .indexOf(rarityKey)
            .let { if (it >= 0) it + 1 else 9 }
        buildJsonObject {
            put("id", ordinal * 10 + rank)
            put("cardRarityType", rarityKey)
            put("masterRank", rank)
            put("b", rateArray(obj, "power1BonusFixed", "power2BonusFixed", "power3BonusFixed"))
        }
    }

    /**
     * 每首歌 × 每个难度的**分数/PT 系数**（社区产物 `music_metas.json`，5.6 MB / 3,804 行）。
     *
     * ## 为什么这张表不在 `TableCatalog` 的上游仓库里
     *
     * 它不是 sekai-master-db 的表，而是社区（pjsk.moe 那条线）从游戏资源反解出来的派生物。
     * 组卡引擎算「分数 / 活动 PT」必须用它（`load_masterdata` 的**第二个参数**），
     * 而 master data 里只有谱面等级、没有这些系数。
     * 所以这张表标记成 `snapshotOnly`：只随内置快照分发，**在线同步不碰它**
     * （上游仓库里没有这个文件，去下只会 404）。
     *
     * ## 只留引擎要的 12 个字段
     *
     * 上游 33 个字段里，`solo_score` / `pt_per_hour_*` / `pspi_*` 之类是 pjsk.moe 自己算的
     * 展示值，引擎不读。逐字对齐引擎的测试夹具（`tests/music_metas.json`）后只剩这 12 个：
     * `music_id / difficulty / music_time / event_rate / base_score / base_score_auto /
     * skill_score_solo / skill_score_auto / skill_score_multi / fever_score /
     * fever_end_time / tap_count`。**数值类型原样搬运**（`base_score` 是 f64、
     * `event_rate`/`tap_count` 是整数），一旦统一成浮点，引擎会报 `expected i32`。
     *
     * ## 为什么要造 id
     *
     * 行由 `(music_id, difficulty)` 唯一确定，**没有 `id` 字段**，而 `master_rows`
     * 的主键是 `(tableName, id)`。直接拿 `music_id` 当主键，同一首歌的 6 个难度会互相覆盖
     * → 只剩最后一档，而界面上完全看不出来。这里造 `id = musicId × 10 + 难度序号`。
     */
    val MUSIC_METAS = RowProjector { obj ->
        // 幂等：已经有 id 说明裁过了（在线同步/重复导入时不至于越裁越少）
        if (obj["id"] is JsonPrimitive) return@RowProjector obj
        val musicId = obj.intOrNull("music_id") ?: return@RowProjector null
        val difficulty = obj.stringOrNull("difficulty") ?: return@RowProjector null
        val diffOrdinal = MUSIC_DIFFICULTY_ORDER.indexOf(difficulty)
        if (diffOrdinal < 0) return@RowProjector null
        buildJsonObject {
            put("id", musicId * 10 + diffOrdinal)
            put("music_id", musicId)
            put("difficulty", difficulty)
            // ⚠️ 原样搬运，不做任何类型转换（见上面「数值类型原样」一段）
            for (key in MUSIC_META_NUMERIC_FIELDS) obj[key]?.let { put(key, it) }
        }
    }

    /** 引擎认的难度名，顺序决定 [MUSIC_METAS] 造 id 时用的序号。 */
    private val MUSIC_DIFFICULTY_ORDER = listOf("easy", "normal", "hard", "expert", "master", "append")

    /** 引擎读的数值字段（除 `music_id` / `difficulty` 之外的全部 10 个）。 */
    private val MUSIC_META_NUMERIC_FIELDS = listOf(
        "music_time", "event_rate", "base_score", "base_score_auto",
        "skill_score_solo", "skill_score_auto", "skill_score_multi",
        "fever_score", "fever_end_time", "tap_count",
    )

    /** 把三个 `powerN…Rate` 读成一个长度 3 的数组（缺省给 0）。 */
    private fun rateArray(obj: JsonObject, key1: String, key2: String, key3: String): JsonArray =        buildJsonArray {
            add(JsonPrimitive(obj.doubleOrNull(key1) ?: 0.0))
            add(JsonPrimitive(obj.doubleOrNull(key2) ?: 0.0))
            add(JsonPrimitive(obj.doubleOrNull(key3) ?: 0.0))
        }
}

