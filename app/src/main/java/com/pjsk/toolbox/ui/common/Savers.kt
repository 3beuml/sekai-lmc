package com.pjsk.toolbox.ui.common

import androidx.compose.runtime.saveable.Saver
import com.pjsk.toolbox.data.card.CardQuery
import com.pjsk.toolbox.data.card.cardQueryFromSaveText
import com.pjsk.toolbox.data.card.toSaveText
import com.pjsk.toolbox.data.music.SongQuery
import com.pjsk.toolbox.data.music.songQueryFromSaveText
import com.pjsk.toolbox.data.music.toSaveText
import com.pjsk.toolbox.data.story.StoryQuery
import com.pjsk.toolbox.data.story.storyQueryFromSaveText
import com.pjsk.toolbox.data.story.toSaveText
import com.pjsk.toolbox.util.decodeQueryItems
import com.pjsk.toolbox.util.encodeQueryItems

/**
 * 筛选状态的跨界面保存器（**项目约定，2026-09-14 起 —— 加新筛选功能请照这里做**）
 *
 * ## 为什么需要
 *
 * `AppNav` 用的是 Navigation Compose 的 `NavHost`。跳到下一层界面（比如卡牌图鉴 →
 * 卡牌详情）时，**上一层的 composition 会被销毁**，普通 `remember` 存的东西全部回到初值。
 * 只有 `rememberSaveable` 写进 back-stack entry 的值，会在返回时被 Navigation 还原。
 *
 * 所以：**筛选条件、排序方式一律用 `rememberSaveable`，不要用 `remember`。**
 * 典型的错法（真机上被用户抓到过）：
 * ```kotlin
 * var query by remember { mutableStateOf(CardQuery()) }   // ❌ 返回后筛选全没了
 * ```
 * 正确写法：
 * ```kotlin
 * var query by rememberSaveable(stateSaver = CardQuerySaver) { mutableStateOf(CardQuery()) }
 * ```
 *
 * ## 加一个新筛选时怎么做
 *
 * 1. 若状态是一个数据类：在**数据层**写 `Xxx.toSaveText()` / `xxxFromSaveText(raw)`
 *    一对纯函数（用 `util/QueryText.kt` 的 `encodeQueryFields` / `decodeQueryFields`；
 *    自由文本字段放最后一个），在这里加一个 `Saver`，并在 `LogicCheck.kt` 里断言往返一致；
 * 2. 若状态只是一个 `Set<String>`：直接用下面的 [StringSetSaver]；
 * 3. 若状态是 `Int` / `Boolean` / 枚举：`rememberSaveable { mutableIntStateOf(0) }`
 *    这样直接用就行，不用写 Saver（枚举走 Serializable，能塞进 Bundle）。
 *
 * ## 什么**不**该保存
 *
 * 只在一屏内有效的临时 UI 状态**不要**保存，否则返回时会突兀地弹出来：
 * 底部筛选面板的开/关（`showFilterSheet`）、下拉菜单的开/关（`sortMenuOpen`）、
 * 下载进度、网络探测结果等。这些继续用 `remember`。
 */

/** 卡牌图鉴的筛选条件 + 排序方式。 */
val CardQuerySaver: Saver<CardQuery, String> = Saver(
    save = { it.toSaveText() },
    restore = { cardQueryFromSaveText(it) },
)

/** 歌曲列表的筛选条件 + 排序方式。规矩和卡牌那套完全一致。 */
val SongQuerySaver: Saver<SongQuery, String> = Saver(
    save = { it.toSaveText() },
    restore = { songQueryFromSaveText(it) },
)

/** 活动剧情列表的筛选（按团）+ 排序。同样走数据层的纯编解码。 */
val StoryQuerySaver: Saver<StoryQuery, String> = Saver(
    save = { it.toSaveText() },
    restore = { storyQueryFromSaveText(it) },
)

/**
 * 一个字符串集合（歌曲的「组合」多选这类）。
 *
 * 用显式的字符串编解码而不是靠 Java Serializable：集合的序列化形式依赖实现类，
 * 而这里存的东西要能跨版本读回来。取值都是自家定义的 key（`vocaloid` 这种），
 * 不含逗号。
 */
val StringSetSaver: Saver<Set<String>, String> = Saver(
    save = { encodeQueryItems(it) },
    restore = { decodeQueryItems(it).toSet() },
)

/**
 * 一个整数集合（分类页的「成员」多选：角色 id）。
 *
 * 解析不出数字的项直接丢掉，不抛异常 —— 和上面那个一样，存进去的东西要能跨版本读回来。
 */
val IntSetSaver: Saver<Set<Int>, String> = Saver(
    save = { it.joinToString(",") },
    restore = { raw ->
        if (raw.isEmpty()) emptySet()
        else raw.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
    },
)
