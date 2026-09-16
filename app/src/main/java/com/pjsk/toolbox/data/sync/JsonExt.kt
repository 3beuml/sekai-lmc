package com.pjsk.toolbox.data.sync

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * master data 的「宽容取值」助手。
 *
 * 为什么需要：master data 是异构 JSON，同一个字段在不同区服/版本里可能是字符串也可能是数字
 * （例如 `id` 有时是 `1` 有时是 `"1"`），而且官方随时会加字段。所以这里一律**取不到就返回 null**，
 * 绝不抛异常 —— 官方更新数据格式不应该让 App 崩掉。
 */

/** 只在不是 JsonNull 时返回字符串内容（数字/布尔也会以字符串形式返回）。 */
internal fun JsonPrimitive.contentOrNullSafe(): String? =
    if (this is JsonNull) null else content

internal fun JsonPrimitive.longOrNullSafe(): Long? =
    if (this is JsonNull) null else content.toLongOrNull()

internal fun JsonObject.primitiveOrNull(key: String): JsonPrimitive? = this[key] as? JsonPrimitive

internal fun JsonObject.stringOrNull(key: String): String? =
    primitiveOrNull(key)?.contentOrNullSafe()?.takeIf { it.isNotBlank() }

/** 取数值：`1`、`"1"`、`1.0` 都接受。 */
internal fun JsonObject.longOrNull(key: String): Long? {
    val v = primitiveOrNull(key) ?: return null
    return v.longOrNullSafe() ?: v.contentOrNullSafe()?.toDoubleOrNull()?.toLong()
}

internal fun JsonObject.intOrNull(key: String): Int? = longOrNull(key)?.toInt()

/** 取布尔：真正的 `true` / `"true"` / `1` 都算 true。 */
internal fun JsonObject.boolOrNull(key: String): Boolean? {
    val v = primitiveOrNull(key) ?: return null
    return when (val s = v.contentOrNullSafe()) {
        "true", "1" -> true
        "false", "0" -> false
        else -> null
    }
}

/** 按候选顺序取第一个非空字符串。 */
internal fun JsonObject.firstString(keys: List<String>): String? {
    for (k in keys) stringOrNull(k)?.let { return it }
    return null
}

/** 按候选顺序取第一个能解析成数值的字段（数字或数字字符串都行）。 */
internal fun JsonObject.firstLong(keys: List<String>): Long? {
    for (k in keys) longOrNull(k)?.let { return it }
    return null
}

/** 按路径取值，路径中的数字段视为数组下标。例：`["infos","0","title"]`。 */
internal fun JsonObject.stringAtPath(path: List<String>): String? {
    var current: JsonElement = this
    for (segment in path) {
        current = when (val node = current) {
            is JsonObject -> node[segment] ?: return null
            is JsonArray -> segment.toIntOrNull()?.let { idx -> node.getOrNull(idx) } ?: return null
            else -> return null
        }
    }
    return (current as? JsonPrimitive)?.contentOrNullSafe()?.takeIf { it.isNotBlank() }
}

internal fun JsonObject.arrayOrNull(key: String): JsonArray? = this[key] as? JsonArray

internal fun JsonObject.objOrNull(key: String): JsonObject? = this[key] as? JsonObject
