package com.pjsk.toolbox.util

/**
 * 收藏歌曲的编解码：`Map<musicId, 收藏时间>` ↔ `Set<"id:时间">`。
 *
 * 收藏存在 `SharedPreferences` 的字符串集合里（**不进数据库**：那要动 Room 表结构
 * 并写迁移，风险远大于收益）。抽成纯函数是为了让它能进 `logic-check` 自检 ——
 * 存坏了的表现是"收藏莫名其妙少了/多了"，在真机上很难发现。
 *
 * 解析规则上刻意**宽容**：
 *  - 时间戳缺失或损坏 → 当作 0（排最后），**不把这条收藏整个丢掉**
 *  - id 解析不出来 → 这一条才丢（没有 id 就没意义）
 */
fun encodeFavoriteSongs(favorites: Map<Int, Long>): Set<String> =
    favorites.map { (id, at) -> "$id:$at" }.toSet()

fun decodeFavoriteSongs(raw: Set<String>): Map<Int, Long> =
    raw.mapNotNull { entry ->
        val parts = entry.split(":")
        val id = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: return@mapNotNull null
        val at = parts.getOrNull(1)?.trim()?.toLongOrNull() ?: 0L
        id to at
    }.toMap()
