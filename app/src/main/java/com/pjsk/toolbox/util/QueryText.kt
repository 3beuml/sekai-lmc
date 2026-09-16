package com.pjsk.toolbox.util

/**
 * 「把筛选条件存成一行文本」的公用编解码。
 *
 * ## 为什么需要它（**项目约定，2026-09-14 起**）
 *
 * 筛选条件必须能在**离开这个界面再回来**之后还原。Compose 里 `remember` 只保证
 * 「重组之间」不丢 —— 一旦导航到别的界面，当前界面的 composition 会被销毁，
 * 只有 `rememberSaveable` 写进 NavBackStackEntry 的值会被 Navigation 存下来。
 *
 * 而 `rememberSaveable` 默认只认能塞进 Bundle 的类型（String / Int / 枚举…），
 * 筛选条件往往是一个**数据类**，所以要自己给出一对
 * 「存成 String」/「从 String 还原」的**纯函数**，再在 UI 层用
 * `androidx.compose.runtime.saveable.Saver` 包一层。
 *
 * 规矩（详见 docs/PROJECT_NOTES.md §11.13）：
 *  1. 编解码写成 `Xxx.toSaveText()` / `xxxFromSaveText(raw)` 一对**纯函数**，
 *     放在**数据层**（不要放 UI 层），这样 `tools/logic-check.ps1` 能直接断言往返一致；
 *  2. 用 [encodeQueryFields] / [decodeQueryFields] 拼拆字段，不要自己 `joinToString("|")`
 *     —— 分隔符统一用 [QUERY_FIELD_SEP]（SOH 控制字符，输入法打不出来），
 *     不会和用户输进搜索框的字符撞车；
 *  3. **还原失败一律退回默认值，绝不允许抛异常**。存进去的那行文本可能来自
 *     旧版本（枚举改过名）或以后新增的字段，崩在这一步是纯亏。
 *
 * ## 为什么不用 kotlinx-serialization 的 `@Serializable`
 *
 * `tools/logic-check.ps1` 是拿**裸 kotlinc、不带 serialization 编译器插件**编译的，
 * 加了注解自检跑起来会变成 `SerializerNotFoundException` —— 而自检正是这个项目里
 * 唯一能把关的地方。手写编解码没有这个依赖，还能顺手断言行数、未知枚举名等边界。
 */

/**
 * 字段分隔符：U+0001 SOH。
 *
 * 用控制字符而不是 `|` / `\t`，是因为**自由文本字段（搜索框）里什么都可能被粘进来**。
 * 配合 [decodeQueryFields] 的 `limit`，把自由文本放在最后一个字段就是绝对安全的。
 */
const val QUERY_FIELD_SEP = "\u0001"

/** 同一个字段内部多个取值（集合）之间的分隔符。取值都是自家定义的 key，不会含逗号。 */
const val QUERY_ITEM_SEP = ","

/** 把若干字段拼成一行。 */
fun encodeQueryFields(vararg fields: String): String = fields.joinToString(QUERY_FIELD_SEP)

/**
 * 把一行拆回 [count] 个字段；结构对不上时返回 null（调用方应当退回默认值）。
 *
 * ⚠️ 这里带 `limit = count`：**最后一个字段里就算混进了分隔符也不会串位**，
 * 所以「搜索词」这类自由文本要放在最后一个字段（见 [encodeQueryFields] 的调用方）。
 * 字段总数不足 [count] 时（比如以后减少字段、读到旧数据）返回 null。
 */
fun decodeQueryFields(raw: String, count: Int): List<String>? {
    val parts = raw.split(QUERY_FIELD_SEP, limit = count)
    return if (parts.size == count) parts else null
}

/** 把一个集合拼成一个字段。 */
fun encodeQueryItems(items: Iterable<String>): String = items.joinToString(QUERY_ITEM_SEP)

/** 把一个字段拆成取值列表。空字段拆出空列表（不是含一个空串的列表）。 */
fun decodeQueryItems(raw: String): List<String> =
    if (raw.isEmpty()) emptyList() else raw.split(QUERY_ITEM_SEP)
