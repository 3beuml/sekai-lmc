package com.pjsk.toolbox.data.story

import com.pjsk.toolbox.data.music.MusicUnitTag
import com.pjsk.toolbox.util.decodeQueryFields
import com.pjsk.toolbox.util.decodeQueryItems
import com.pjsk.toolbox.util.encodeQueryFields
import com.pjsk.toolbox.util.encodeQueryItems

/**
 * `eventStoryUnits.json` 的一行：某个活动剧情和哪个团相关。
 *
 * 实测（2026-09-16）：**450 行**，`eventStoryUnitRelation` 只有 `main` / `sub`，
 * 214 个活动剧情**全部**有行。`unit` 取值只有
 * `light_sound / idol / street / theme_park / school_refusal` + 5 行 `none`
 * —— **完全没有 `piapro`（VIRTUAL SINGER）**，所以"虚拟歌手没有活动剧情"这件事
 * 是**数据自己**说的，不用在代码里写死：以后官方真给它加了，筛选里那个团会自己冒出来。
 */
data class EventStoryUnit(
    val eventStoryId: Int,
    val unit: String,
    /** `eventStoryUnitRelation == "main"`。 */
    val isMain: Boolean,
)

/**
 * `unit == "none"`（不属于任何团）在筛选里的落点。
 *
 * 复用歌曲页那个「其他」（`MusicUnitTag.OTHER.key`）：用户要求这 5 个活动
 * 归到「其他」里，而不是没有筛选项可选。
 */
const val STORY_UNIT_OTHER = "other"

/**
 * ⚠️⚠️ **`eventStoryUnits.unit` 用的是 `unitProfiles` 那一套 key**
 * （`light_sound` / `idol` / `street` / `theme_park` / `school_refusal` / `piapro`），
 * 而 [MusicUnitTag.key] 是**另一套**：Leo/need 的 `key` 是 `light_music_club`，
 * 只有 `unitProfileKey` 才是 `light_sound`。
 *
 * 混了会出两个症状（都被自检抓到过）：**Leo/need 被排到最末**（查不到 → 落到兜底），
 * **名字直接显示成 `light_sound`**。这和 §11.12 那个"VIRTUAL SINGER 取值是 `piapro`
 * 不是 `virtual_singer`"是同一类坑，只是换了一张表。
 *
 * ⚠️ 所以匹配一律走 **`unitProfileKey`**，不要走 `key`。
 */
private fun tagOfStoryUnit(unit: String): MusicUnitTag? =
    MusicUnitTag.entries.firstOrNull { it.unitProfileKey == unit }

/** 筛选面板里团 chip 的顺序：游戏内组合顺序，**「其他」永远排最后**。 */
private val STORY_UNIT_ORDER: List<String> =
    MusicUnitTag.ordered.mapNotNull { it.unitProfileKey } + STORY_UNIT_OTHER

/** 团 key → 中文显示名。认不出的 key 原样返回（**不编一个听起来合理的说法**）。 */
private fun storyUnitLabel(unit: String): String = when (unit) {
    STORY_UNIT_OTHER -> MusicUnitTag.OTHER.label
    else -> tagOfStoryUnit(unit)?.label ?: unit
}

/**
 * 把 `eventStoryUnits` 的行归约成"这个活动剧情相关哪些团"。**纯函数**，自检里断言。
 *
 * ⚠️ 三个必须处理的点：
 *  1. **要先去重**：450 行 ÷ 214 个活动 ≈ 2.1 行/活动，同一个团可能有多行；
 *  2. `none` 归到 [STORY_UNIT_OTHER]（用户要求），否则这 5 个最新的联动活动
 *     会变成"哪个团都筛不出来"；
 *  3. 排序按**游戏内组合顺序**（`MusicUnitTag.ordered`），这样 chip 的顺序是稳定的，
 *     不会因为数据里行的先后而跳动。
 *
 * `main` / `sub` 不参与筛选（用户要的是"**相关**"），只保留在模型里备用。
 */
fun eventStoryUnitKeys(rows: List<EventStoryUnit>): List<String> {
    if (rows.isEmpty()) return emptyList()
    val keys = rows
        .map { row -> if (row.unit.isBlank() || row.unit == "none") STORY_UNIT_OTHER else row.unit }
        .distinct()
    return keys.sortedBy { key ->
        STORY_UNIT_ORDER.indexOf(key).takeIf { it >= 0 } ?: Int.MAX_VALUE
    }
}

/** 活动剧情列表的筛选与排序状态。 */
data class StoryQuery(
    /** 选中的团（多选，取"或"）。空 = 不筛。 */
    val units: Set<String> = emptySet(),
    val sort: StorySort = StorySort.NEWEST,
) {
    val isFiltering: Boolean get() = units.isNotEmpty()

    /** 清掉筛选，保留排序（面板上的「重置」）。 */
    fun cleared(): StoryQuery = StoryQuery(sort = sort)
}

/** 活动剧情的排序方式。列表本来就按时间倒序，这里只是把顺序**变成用户可选的**。 */
enum class StorySort(val label: String) {
    NEWEST("最新在前"),
    OLDEST("最早在前"),
}

private const val STORY_QUERY_FIELDS = 2

/**
 * 压成一行文本 / 还原（项目约定：筛选状态要能跨界面返回，见 `ui/common/Savers.kt`）。
 * 结构对不上就退回默认值，**绝不抛异常**。
 */
fun StoryQuery.toSaveText(): String = encodeQueryFields(
    encodeQueryItems(units),
    sort.name,
)

/** [toSaveText] 的逆运算。 */
fun storyQueryFromSaveText(raw: String): StoryQuery {
    val f = decodeQueryFields(raw, STORY_QUERY_FIELDS) ?: return StoryQuery()
    return StoryQuery(
        units = decodeQueryItems(f[0]).toSet(),
        sort = StorySort.entries.firstOrNull { it.name == f[1] } ?: StorySort.NEWEST,
    )
}

/**
 * 筛选 + 排序。**纯函数**，自检里断言。
 *
 * 筛选口径是"**任一相关**"：一个活动只要和选中的任一团相关（`main` 或 `sub`）就留下 ——
 * 混合活动（实测去重后挂 5 个团的有 20 个）在这 5 个团里都会出现，这是有意的。
 */
fun filterStoryRows(rows: List<StoryRow>, query: StoryQuery): List<StoryRow> {
    val filtered = if (query.units.isEmpty()) {
        rows
    } else {
        rows.filter { row -> row.unitKeys.any { it in query.units } }
    }
    return sortStoryRows(filtered, query.sort)
}

/** 只排序。两个函数都是**稳定排序**，时间相同的保持原有先后。 */
fun sortStoryRows(rows: List<StoryRow>, sort: StorySort): List<StoryRow> = when (sort) {
    StorySort.NEWEST -> rows.sortedByDescending { it.sortAt }
    StorySort.OLDEST -> rows.sortedBy { it.sortAt }
}

/** 筛选面板里的一个"团"选项。 */
data class StoryUnitOption(val key: String, val label: String, val count: Int)

/**
 * 造筛选面板的团选项 —— **数据驱动**：只列数据里真的出现过活动的团。
 *
 * 所以"虚拟歌手没有活动剧情"不用写特判：它本来就没有行，自然不会出现在这里。
 * 认不出的 key 原样显示（不编一个听起来合理的说法）。
 */
fun buildStoryUnitOptions(rows: List<StoryRow>): List<StoryUnitOption> {
    val counts = LinkedHashMap<String, Int>()
    rows.forEach { row ->
        row.unitKeys.forEach { key -> counts[key] = (counts[key] ?: 0) + 1 }
    }
    return counts.entries
        .sortedBy { entry ->
            STORY_UNIT_ORDER.indexOf(entry.key).takeIf { it >= 0 } ?: Int.MAX_VALUE
        }
        .map { (key, count) ->
            StoryUnitOption(
                key = key,
                // ⚠️ 走 unitProfileKey 那一套（见 tagOfStoryUnit 的说明）
                label = storyUnitLabel(key),
                count = count,
            )
        }
}
