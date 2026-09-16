package com.pjsk.toolbox.ui.common

import com.pjsk.toolbox.data.db.MasterRowEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * master data 是异构 JSON（同一字段在不同区服可能是字符串也可能是数字），
 * 所有取值都走「宽容解析」：取不到就返回 null，绝不抛异常。
 *
 * 全 UI 层统一用这里的助手，不要在页面里各写一遍 runCatching。
 */

private val prettyJson = Json { prettyPrint = true }
private val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }

/** 格式化输出原始 JSON（详情页用）。 */
fun JsonElement.prettyPrint(): String =
    runCatching { prettyJson.encodeToString(JsonElement.serializer(), this) }
        .getOrDefault(toString())

fun String.parseJsonObjectOrNull(): JsonObject? =
    runCatching { lenientJson.parseToJsonElement(this) as? JsonObject }.getOrNull()

/**
 * 把「原始 JSON 文本」重新格式化成带缩进的样子；解析失败就原样返回。
 *
 * 存在的原因：[prettyPrint] 定义在 [JsonElement] 上，而数据库里存的是 **String**（`MasterRowEntity.data`）。
 * 直接写 `row.data.prettyPrint()` 会报
 * 「Unresolved reference ... because of a receiver type mismatch」。
 */
fun String.prettyPrintJson(): String {
    val element = runCatching { lenientJson.parseToJsonElement(this) }.getOrNull() ?: return this
    return element.prettyPrint()
}

/** 把一行的原始 JSON 解析成对象；解析失败返回 null（UI 需容错）。 */
fun MasterRowEntity.jsonObject(): JsonObject? = data.parseJsonObjectOrNull()

/** 只在非 JsonNull 时返回文本内容（数字/布尔也按文本返回）。 */
fun JsonObject.str(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content

fun JsonObject.intOrNull(key: String): Int? = str(key)?.toDoubleOrNull()?.toInt()

fun JsonObject.longOrNull(key: String): Long? = str(key)?.toDoubleOrNull()?.toLong()

/**
 * 显示名：**中文名优先，回退日文原名，再回退 #id**。
 *
 * 这是「日服数据 + 简中界面」方案在 UI 侧的落点：简中区服尚未更新的新内容
 * `nameZh` 为 null，就自然显示日文原名，不需要任何特殊分支。
 */
fun MasterRowEntity.displayName(): String =
    nameZh?.takeIf { it.isNotBlank() }
        ?: name?.takeIf { it.isNotBlank() }
        ?: "#$id"

/** 该行是否还没有中文名（UI 上可以给个小标记）。 */
fun MasterRowEntity.hasChineseName(): Boolean = !nameZh.isNullOrBlank()

/** 素材 bundle 名。sekai master data 所有表统一用 `assetbundleName` 这个字段名。 */
fun MasterRowEntity.assetbundleName(): String? =
    jsonObject()?.str("assetbundleName")?.takeIf { it.isNotBlank() }

fun MasterRowEntity.characterId(): Int? = jsonObject()?.intOrNull("characterId")

fun MasterRowEntity.musicId(): Int? = jsonObject()?.intOrNull("musicId")

fun MasterRowEntity.eventId(): Int? = jsonObject()?.intOrNull("eventId")

fun MasterRowEntity.cardId(): Int? = jsonObject()?.intOrNull("cardId")

/** 从 JSON 数组字段里取出每项的某个键（例如 cards.json 的 `supportUnit`）。 */
fun JsonObject.stringList(key: String): List<String> =
    (this[key] as? kotlinx.serialization.json.JsonArray)
        ?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p !is JsonNull }?.content }
        ?: emptyList()
