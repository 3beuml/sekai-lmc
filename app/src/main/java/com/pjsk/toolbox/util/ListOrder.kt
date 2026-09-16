package com.pjsk.toolbox.util

/**
 * 把满足 [predicate] 的项排到前面，其余保持原顺序。
 *
 * 用在筛选面板的 chip 组上：点了中间某个 chip 之后，它还待在原地会很别扭
 * （选中态夹在一排未选中里，扫一眼看不出选了什么），所以让选中的浮到最前。
 *
 * 实现用稳定的 `sortedByDescending`：**同组内的相对顺序不变**，
 * 所以这一排不会因为你多点了几下就整体洗牌，只是被选中的往上挪。
 */
fun <T> List<T>.moveMatchesFirst(predicate: (T) -> Boolean): List<T> =
    sortedByDescending { predicate(it) }
