package com.pjsk.toolbox.data.deck

import com.pjsk.toolbox.data.profile.PlayerSnapshot
import com.pjsk.toolbox.ui.common.intOrNull
import com.pjsk.toolbox.ui.common.parseJsonObjectOrNull
import com.pjsk.toolbox.ui.common.str
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 把**裁剪过**的内置快照行还原成组卡引擎（allium-deck WASM）要的**原始表形状**。
 *
 * ## 为什么需要这一层
 *
 * 引擎的 `load_masterdata` 只吃**原始 master data**（形如 `"cards": "<cards.json 原文>"`），
 * 它在内部自己做「raw → 扁平结构」的转换。而本项目的快照为了省体积把几张表裁短了
 * （`cards` 把 `cardParameters` 折成 `p1/p2/p3`、`areaItemLevels` 把六个加成率折成 `r/ar`……）。
 *
 * 于是只有两条路：
 *  1. 把原始 `cards.json`（33.9 MB）塞进 APK —— 包体积翻倍，不可接受；
 *  2. **在内存里按需还原**（本文件的方案）—— 只在算的时候多占几十 MB、多花一两秒。
 *
 * 用户已选 (2)。
 *
 * ## 这一层最容易出错的地方
 *
 * 还原出来的字段名/类型必须和上游**逐字一致**，否则引擎会给出「看起来合理但错」的结果
 * （Rust 侧反序列化失败有时是静默丢字段）。所以这里每个函数都只做**机械映射**，
 * 不做任何推断，并且全部是纯函数、可被 `tools/logic-check` 断言。
 */

/** 一张卡的原始形态需要的三类等级参数名。 */
private val PARAM_TYPES = listOf("param1", "param2", "param3")

/**
 * `cards.json`：把裁出来的 `p1/p2/p3` 还原成 `cardParameters` 数组。
 *
 * 原始行是 `cardParameters: [{cardParameterType, cardLevel, power}, …]`（1471 张卡 × 最多 60 级 × 3 项），
 * 这是整个还原层里唯一的大头（约 10 MB JSON）。我们已经把 `specialTrainingCosts` 之类的字段裁掉了，
 * 但引擎**只用** `cardParameters` 的数值 + 卡片上那几个短字段（稀有度/属性/角色/技能 id/支援团），
 * 这些裁剪器都留着，所以还原是完备的。
 *
 * 特训加成：原始表里叫 `specialTrainingPower1/2/3BonusFixed`，我们存在 `trainBonus` 数组里。`n *`n * ⚠️ `cardParameters` 的每一项除了三个数值字段，**还必须带 `id` 和 `cardId`**：`n * 参考实现 Moesekai 的 `transformCards` 产出的是 `{id, cardId, cardLevel, cardParameterType, power}`。`n * 少了 `cardId`，参数可能挂不到正确的卡上 —— 表现为卡能被解析、但语义被打乱`n * （真机实测：`trained:false` 却 `level:60`，且搜索只访问了 17 个节点）。
 */
fun restoreCardsJson(croppedRows: List<String>, keepIds: Set<Int>? = null): String {
    val sb = StringBuilder(11 * 1024 * 1024)
    sb.append('[')
    var first = true
    for (raw in croppedRows) {
        val obj = raw.parseJsonObjectOrNull() ?: continue
        val id = obj.intOrNull("id") ?: continue
        if (!first) sb.append(',')
        first = false
        val series = listOf(
            obj["p1"] as? JsonArray,
            obj["p2"] as? JsonArray,
            obj["p3"] as? JsonArray,
        )
        sb.append("{\"id\":").append(id)
        obj.str("assetbundleName")?.let { sb.append(",\"assetbundleName\":").append(quote(it)) }
        obj.str("cardRarityType")?.let { sb.append(",\"cardRarityType\":").append(quote(it)) }
        obj.str("attr")?.let { sb.append(",\"attr\":").append(quote(it)) }
        obj.intOrNull("characterId")?.let { sb.append(",\"characterId\":").append(it) }
        obj.intOrNull("skillId")?.let { sb.append(",\"skillId\":").append(it) }
        obj.str("supportUnit")?.let { sb.append(",\"supportUnit\":").append(quote(it)) }
        val train = obj["trainBonus"] as? JsonArray
        train?.forEachIndexed { index, element ->
            val value = (element as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
            sb.append(",\"specialTrainingPower").append(index + 1).append("BonusFixed\":").append(value)
        }
        // cardParameters：三个属性各自的「等级 → 数值」
        sb.append(",\"cardParameters\":[")
        var firstParam = true
        series.forEachIndexed { index, array ->
            if (array == null) return@forEachIndexed
            array.forEachIndexed { levelIndex, element ->
                val power = (element as? JsonPrimitive)?.content?.toIntOrNull() ?: return@forEachIndexed
                if (!firstParam) sb.append(',')
                firstParam = false
                sb.append("{\"id\":").append(id * 100000L + index * 1000L + (levelIndex + 1))
                    .append(",\"cardId\":").append(id)
                    .append(",\"cardParameterType\":\"").append(PARAM_TYPES[index])
                    .append("\",\"cardLevel\":").append(levelIndex + 1)
                    .append(",\"power\":").append(power).append('}')
            }
        }
        sb.append("]}")
    }
    sb.append(']')
    return sb.toString()
}

/**
 * `areaItemLevels.json`：把 `r`/`ar` 两个三分量数组还原成六个 `power{1,2,3}BonusRate` 字段。
 *
 * `targetGameCharacterId` 缺省时**不写这个字段**（原始表里缺省就是「不限角色」，
 * 写 0 会被引擎当成 0 号角色）。
 */
fun restoreAreaItemLevelsJson(croppedRows: List<String>): String =
    joinObjects(croppedRows) { obj ->
        val itemId = obj.intOrNull("areaItemId") ?: return@joinObjects null
        val level = obj.intOrNull("level") ?: return@joinObjects null
        val rates = doubleArray(obj, "r")
        val allMatch = doubleArray(obj, "ar")
        buildJsonObject {
            put("areaItemId", itemId)
            put("level", level)
            put("targetUnit", obj.str("targetUnit") ?: "any")
            put("targetCardAttr", obj.str("targetCardAttr") ?: "any")
            obj.intOrNull("targetGameCharacterId")?.takeIf { it > 0 }?.let { put("targetGameCharacterId", it) }
            for (i in 0..2) {
                put("power${i + 1}BonusRate", rates.getOrElse(i) { 0.0 })
                put("power${i + 1}AllMatchBonusRate", allMatch.getOrElse(i) { 0.0 })
            }
            // 原始表里有 sentence（带 <color> 的说明文本），引擎不读，这里不还原（省 128 KB）
        }
    }

/** `areaItems.json`：只用得到 id / areaId / name，但引擎按原始字段名取，所以补回去。 */
fun restoreAreaItemsJson(croppedRows: List<String>): String =
    joinObjects(croppedRows) { obj ->
        val id = obj.intOrNull("id") ?: return@joinObjects null
        buildJsonObject {
            put("id", id)
            obj.intOrNull("areaId")?.let { put("areaId", it) }
            obj.str("name")?.let { put("name", it) }
        }
    }

/**
 * `honors.json`：我们的快照只留了带正加成的 34 行，且 `levels` 只保留 `{level, bonus}`。
 *
 * 还原时把 `honorId` 补回每一级（原始表每级都带），`description` 不还原（引擎不读）。
 * 其余 8,700 多个「加成 0」的称号**不还原**：它们对综合力的贡献恒为 0，
 * 缺了不会影响推荐结果 —— 但**用户持有列表里那些 id 查不到**，引擎若要求 id 必须存在会报错，
 * 这是一个待真机确认的点（见 REPORT）。
 */
fun restoreHonorsJson(croppedRows: List<String>): String =
    joinObjects(croppedRows) { obj ->
        val id = obj.intOrNull("id") ?: return@joinObjects null
        val levels = obj["levels"] as? JsonArray ?: return@joinObjects null
        buildJsonObject {
            put("id", id)
            obj.str("name")?.let { put("name", it) }
            obj.str("honorRarity")?.let { put("honorRarity", it) }
            put(
                "levels",
                buildJsonArray {
                    levels.forEach { element ->
                        val levelObj = element as? JsonObject ?: return@forEach
                        add(
                            buildJsonObject {
                                put("honorId", id)
                                put("level", levelObj.intOrNull("level") ?: return@forEach)
                                put("bonus", levelObj.intOrNull("bonus") ?: 0)
                            },
                        )
                    }
                },
            )
        }
    }

/** `masterLessons.json`：把 `b` 数组还原成三个 `power{1,2,3}BonusFixed`。 */
fun restoreMasterLessonsJson(croppedRows: List<String>): String =
    joinObjects(croppedRows) { obj ->
        val rarity = obj.str("cardRarityType") ?: return@joinObjects null
        val rank = obj.intOrNull("masterRank") ?: return@joinObjects null
        // ⚠️ 这三个字段上游是 **i32**，写成 200.0 会被引擎拒绝：
        //   「invalid type: floating point '0.0', expected i32」
        val bonus = intArray(obj, "b")
        buildJsonObject {
            put("cardRarityType", rarity)
            put("masterRank", rank)
            for (i in 0..2) put("power${i + 1}BonusFixed", bonus.getOrElse(i) { 0 })
        }
    }

/**
 * 组装 `load_masterdata` 的第一个参数：`{"cards": "<cards.json 原文>", …}`。
 *
 * ⚠️ 值是**字符串**（表原文），不是对象 —— 引擎内部再解析一次。这里必须正确转义。
 */
fun buildMasterDataPayload(tables: Map<String, String>): String = buildString(tables.size * 1024) {
    append('{')
    var first = true
    for ((name, text) in tables) {
        if (!first) append(',')
        first = false
        append(quote(name)).append(':').append(quote(text))
    }
    append('}')
}

/**
 * 组装 `recommend` 的 `user_json`：上传链路的 camelCase 形状。
 *
 * 只放引擎真正读的那几项（其余 key 缺省即空数组，不会崩 —— 见调研文档 §4.1）。
 * `episodes` 必须写成 `[{cardEpisodeId, scenarioStatus:"already_read"}]`，
 * 因为引擎按 `scenarioStatus` 判断已读，再回 `cardEpisodes` 查加成。
 */
fun buildUserJson(snapshot: PlayerSnapshot): String = buildString(256 * 1024) {
    append("{\"userGamedata\":{\"deck\":").append(1).append(",\"name\":")
        .append(quote(snapshot.name)).append(",\"rank\":").append(snapshot.rank).append("},\"userCards\":[")
    snapshot.cards.forEachIndexed { index, card ->
        if (index > 0) append(',')
        append("{\"cardId\":").append(card.cardId)
        append(",\"level\":").append(card.level)
        append(",\"masterRank\":").append(card.masterRank)
        append(",\"skillLevel\":").append(card.skillLevel)
        append(",\"specialTrainingStatus\":").append(quote(if (card.trained) "done" else "not_done"))
        append(",\"episodes\":[")
        var firstEp = true
        for (key in card.readEpisodeKeys) {
            if (!firstEp) append(',')
            firstEp = false
            append("{\"cardEpisodeId\":").append(key).append(",\"scenarioStatus\":\"already_read\"}")
        }
        append("]}")
    }
    append("],\"userAreas\":[")
    // 区域道具按「区域」分组：引擎读 userAreas[].areaItems[]，所以每件道具塞进一个区域对象里
    snapshot.areaItems.forEachIndexed { index, item ->
        if (index > 0) append(',')
        append("{\"areaId\":").append(index + 1).append(",\"areaItems\":[{\"areaItemId\":")
            .append(item.areaItemId).append(",\"level\":").append(item.level).append("}]}")
    }
    append("],\"userCharacters\":[")
    snapshot.characters.forEachIndexed { index, character ->
        if (index > 0) append(',')
        append("{\"characterId\":").append(character.characterId)
            .append(",\"characterRank\":").append(character.characterRank).append('}')
    }
    append("],\"userHonors\":[")
    snapshot.honors.forEachIndexed { index, honor ->
        if (index > 0) append(',')
        append("{\"honorId\":").append(honor.honorId).append(",\"level\":").append(honor.level).append('}')
    }
    append("]}")
}

/**
 * 组卡目标（引擎的 `target`）。
 *
 * - [POWER] 纯综合力最优：**完全离线**，不需要活动、不需要曲目系数。
 * - [BONUS] 活动加成最优：要 `event_id`（活动相关的表快照里都有）。
 * - [SCORE] 单局得分最优：要 `music_id` + 难度，且**必须有 `musicMetas`**
 *   （per-song 系数，引擎的第二个入参）。带 `event_id` 时还会算活动 PT。
 */
enum class DeckTarget(val key: String, val label: String) {
    POWER("power", "综合力"),
    BONUS("bonus", "活动加成"),
    SCORE("score", "分数"),
}

/** 玩法（引擎的 `live_type`）。目标=综合力时这一项不影响结果。 */
enum class DeckLiveType(val key: String, val label: String) {
    SOLO("solo", "单人"),
    MULTI("multi", "多人"),
}

/**
 * 组装 `recommend` 的 `params_json`。
 *
 * 只有 `target=power` + 无活动 / 无曲目是**完全离线**的组合；
 * [BONUS] 需要活动表，[SCORE] 需要 `musicMetas`。缺了对应输入时**不要**拼这个参数 ——
 * 引擎会因为缺少上下文而按兜底表计算，结果是"看起来正常但不对"的数字。
 *
 * 稀有度配置全开：等级/突破/技能/剧情一律按用户实际值（`levelMax=false` 等），
 * 因为我们算的是"我现在的卡能组出什么"，不是"全都练满能到什么"。
 *
 * @param eventId 真实活动 id。`target=score` 时给了它才会算活动 PT。
 * @param musicId 曲目 id；`target=score` 必需。
 * @param musicDiff 难度名（`easy`…`append`）；缺省引擎按 `expert` 处理，这里总要显式给。
 * @param multiTeammatePower / multiTeammateScoreUp 多人模式的队友综合力与技能值。
 *   不给就是 0 —— 结果会偏低，但**排名仍然可比**（同一份缺口对所有候选一样）。
 * @param targetBonusList **控分**用：给定时走 `target=bonus` 的"精确档位"模式，
 *   每个档位各返回一套 Top-K（引擎文档：`one Top-K per tier`，最多 32 档、每档 0..10000）。
 *   给了档位就**不再**是"最大化加成" —— 那是两种语义，不要混。
 *   ⚠️ 档位比较的是**实际加成值**，不是四舍五入后的显示值：4.8%/4.9%/5.1%/5.2% 的卡组
 *   都**不算**命中 5%。
 */
fun buildRecommendParams(
    target: DeckTarget = DeckTarget.POWER,
    liveType: DeckLiveType = DeckLiveType.SOLO,
    limit: Int = 10,
    timeoutMs: Int = 60_000,
    eventId: Int? = null,
    musicId: Int? = null,
    musicDiff: String? = null,
    multiTeammatePower: Int? = null,
    multiTeammateScoreUp: Int? = null,
    targetBonusList: List<Int>? = null,
): String = buildString(512) {
    append("{\"region\":\"jp\",\"live_type\":\"").append(liveType.key)
    append("\",\"target\":\"").append(target.key).append('"')
    append(",\"limit\":").append(limit.coerceIn(1, 30))
    append(",\"timeout_ms\":").append(timeoutMs)
    eventId?.let { append(",\"event_id\":").append(it) }
    musicId?.let { append(",\"music_id\":").append(it) }
    if (musicId != null && !musicDiff.isNullOrBlank()) {
        append(",\"music_diff\":\"").append(musicDiff).append('"')
    }
    multiTeammatePower?.let { append(",\"multi_teammate_power\":").append(it) }
    multiTeammateScoreUp?.let { append(",\"multi_teammate_score_up\":").append(it) }
    // 控分档位：最多 32 档、每档 0..10000（引擎的硬限制，超了直接解析错误）
    targetBonusList?.takeIf { it.isNotEmpty() }?.let { tiers ->
        append(",\"target_bonus_list\":[")
        tiers.take(32).forEachIndexed { index, tier ->
            if (index > 0) append(',')
            append(tier.coerceIn(0, 10_000))
        }
        append(']')
    }
    for (rarity in listOf("rarity1", "rarity2", "rarity3", "rarity4", "rarityBirthday")) {
        append(",\"${rarity}Config\":{\"disable\":false,\"levelMax\":false,\"episodeRead\":false")
        append(",\"masterMax\":false,\"skillMax\":false}")
    }
    append('}')
}

/**
 * 纯综合力最优（`target=power`，无活动、无曲目）—— 引擎唯一完全离线的组合。
 *
 * 保留这个入口是因为它是**默认模式**，而且自检与老调用点都按这个名字写的。
 */
fun buildPowerParams(limit: Int = 10, timeoutMs: Int = 60_000): String =
    buildRecommendParams(target = DeckTarget.POWER, limit = limit, timeoutMs = timeoutMs)

/**
 * 把库里的 `musicMetas` 行还原成引擎 `load_masterdata` 的**第二个参数**。
 *
 * 两件事：
 *  1. 丢掉裁剪器造的 `id`（那是本 App 数据库的主键，引擎不认这个字段；
 *     多给字段虽然大多会被 serde 忽略，但**没必要拿这个去赌**）。
 *  2. 拼成一个 JSON 数组文本 —— 引擎直接 `JSON.parse` 它，不能传空串
 *     （§17.3 第 1 条：空串会报 `EOF while parsing a value`）。
 *
 * 行里若缺 `music_id` / `difficulty` 就丢掉：那种行对引擎一定无用，
 * 留着只会让它整张表解析失败。
 */
fun restoreMusicMetasJson(croppedRows: List<String>): String = buildString(croppedRows.size * 640) {
    append('[')
    var first = true
    for (raw in croppedRows) {
        val obj = raw.parseJsonObjectOrNull() ?: continue
        if (obj.intOrNull("music_id") == null) continue
        if (obj.str("difficulty").isNullOrBlank()) continue
        if (!first) append(',')
        first = false
        append(
            kotlinx.serialization.json.JsonObject(
                obj.filterKeys { it != "id" },
            ).toString(),
        )
    }
    append(']')
}

// ── 小工具 ──

/** 整数序列（`masterLessons` 的突破加成是 i32，不能用 Double 序列化）。 */
private fun intArray(obj: JsonObject, key: String): List<Int> {
    val array = obj[key] as? JsonArray ?: return emptyList()
    return array.mapNotNull { (it as? JsonPrimitive)?.content?.toDoubleOrNull()?.toInt() }
}

private fun doubleArray(obj: JsonObject, key: String): List<Double> {
    val array = obj[key] as? JsonArray ?: return emptyList()
    return array.mapNotNull { (it as? JsonPrimitive)?.content?.toDoubleOrNull() }
}

/** 逐行映射成 JSON 数组文本（丢掉返回 null 的行）。 */
private inline fun joinObjects(rows: List<String>, crossinline mapper: (JsonObject) -> JsonObject?): String {
    val sb = StringBuilder(rows.size * 160)
    sb.append('[')
    var first = true
    for (raw in rows) {
        val obj = raw.parseJsonObjectOrNull() ?: continue
        val mapped = mapper(obj) ?: continue
        if (!first) sb.append(',')
        first = false
        sb.append(mapped.toString())
    }
    sb.append(']')
    return sb.toString()
}

/** 最小 JSON 字符串转义（表原文里一定有引号/换行/反斜杠）。 */
internal fun quote(text: String): String {
    val sb = StringBuilder(text.length + 16)
    sb.append('"')
    for (ch in text) {
        when (ch) {
            '"' -> sb.append("\\\"")
            '\\' -> sb.append("\\\\")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            else -> if (ch < ' ') sb.append("\\u%04x".format(ch.code)) else sb.append(ch)
        }
    }
    sb.append('"')
    return sb.toString()
}




