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
 * 特训加成：原始表里叫 `specialTrainingPower1/2/3BonusFixed`，我们存在 `trainBonus` 数组里。
 */
fun restoreCardsJson(croppedRows: List<String>): String {
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
                sb.append("{\"cardParameterType\":\"").append(PARAM_TYPES[index])
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
        val bonus = doubleArray(obj, "b")
        buildJsonObject {
            put("cardRarityType", rarity)
            put("masterRank", rank)
            for (i in 0..2) put("power${i + 1}BonusFixed", bonus.getOrElse(i) { 0.0 })
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
 * 组装 `recommend` 的 `params_json`：**纯综合力最优**（`target: "power"`）。
 *
 * 不带 `event_id` / `music_id`，所以不需要 `music_metas`（引擎的 `target=power` + 无活动
 * 是唯一完全离线的组合）。稀有度配置全开：等级满、剧情已读、突破与技能按用户实际值。
 */
fun buildPowerParams(limit: Int = 10, timeoutMs: Int = 60_000): String = buildString(512) {
    append("{\"region\":\"jp\",\"live_type\":\"solo\",\"target\":\"power\"")
    append(",\"limit\":").append(limit.coerceIn(1, 30))
    append(",\"timeout_ms\":").append(timeoutMs)
    for (rarity in listOf("rarity1", "rarity2", "rarity3", "rarity4", "rarityBirthday")) {
        append(",\"${rarity}Config\":{\"disable\":false,\"levelMax\":false,\"episodeRead\":false")
        append(",\"masterMax\":false,\"skillMax\":false}")
    }
    append('}')
}

// ── 小工具 ──

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
