package com.pjsk.toolbox.util

/**
 * 收藏歌曲的「版本偏好」编解码：`Map<musicId, vocalId>` ↔ `Set<"musicId:vocalId">`。
 *
 * 作用范围：**只给收藏歌曲**记一份"以后播这首就用这个版本"。和 [encodeFavoriteSongs]
 * 同一套范式——存进 `SharedPreferences` 的字符串集合里（**不进数据库**：那要动 Room
 * 表结构 + 写迁移，风险远大于收益），抽成纯函数是为了让它能进 `logic-check` 自检。
 *
 * 存坏了的表现是"收藏歌播的版本和当初选的不一致"，在真机上很难发现。
 *
 * 解析规则刻意**宽容**（和 [decodeFavoriteSongs] 一致）：
 *  - vocalId 缺失或损坏 → 当作 0（不会让整条记忆作废；0 在挑选时会查不到对应 vocal 行，
 *    自动回退到全局态/默认，最坏只是"这条记忆没生效"，不会崩）。
 *    不过实际我们写入时永远带有效 vocalId，损坏只可能来自跨版本/手动改 prefs。
 *  - musicId 解析不出来 → 这一条才丢（没有 musicId 就没意义）。
 */
fun encodePreferredVocals(preferred: Map<Int, Int>): Set<String> =
    preferred.map { (musicId, vocalId) -> "$musicId:$vocalId" }.toSet()

fun decodePreferredVocals(raw: Set<String>): Map<Int, Int> =
    raw.mapNotNull { entry ->
        val parts = entry.split(":")
        val musicId = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: return@mapNotNull null
        val vocalId = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 0
        musicId to vocalId
    }.toMap()
