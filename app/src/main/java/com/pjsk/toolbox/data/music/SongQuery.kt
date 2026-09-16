package com.pjsk.toolbox.data.music

import com.pjsk.toolbox.data.card.PjskCharacter
import com.pjsk.toolbox.data.home.HomeMusic
import com.pjsk.toolbox.util.decodeQueryFields
import com.pjsk.toolbox.util.decodeQueryItems
import com.pjsk.toolbox.util.encodeQueryFields
import com.pjsk.toolbox.util.encodeQueryItems

/**
 * 歌曲列表的筛选条件与排序。
 *
 * 四个维度**都是多选**（同维取「或」、跨维取「且」），和卡牌图鉴那套一致。
 * 空集合 = 这一维不筛选。
 *
 * ⚠️ 这个类的编解码（[toSaveText] / [songQueryFromSaveText]）是**筛选不丢**的关键：
 * 跳到歌曲详情再返回时，普通 `remember` 会把条件全清掉。约定见
 * `docs/PROJECT_NOTES.md` §11.13，编码助手在 `util/QueryText.kt`。
 */
data class SongQuery(
    val search: String = "",
    /** 组合（`musicTags` 的取值，即游戏内选曲界面的分类）。 */
    val units: Set<String> = emptySet(),
    /** 演出形式分类（`musicCategories` 的取值）。 */
    val categories: Set<String> = emptySet(),
    val sort: SongSort = SongSort.NEWEST,
) {
    /** 启用的**维度个数**（不是选中项个数），用于顶栏筛选按钮上的角标。 */
    val activeCount: Int
        get() = (if (units.isNotEmpty()) 1 else 0) + (if (categories.isNotEmpty()) 1 else 0)

    val isFiltering: Boolean get() = search.isNotBlank() || activeCount > 0

    /** 清掉除排序外的全部条件（面板上的「重置」）。 */
    fun cleared(): SongQuery = SongQuery(sort = sort)
}

/** 排序方式。只有两种（用户要求删掉「按名字」）。 */
enum class SongSort(val label: String) {
    NEWEST("最新上线"),
    OLDEST("最早上线"),
}

private const val SONG_QUERY_FIELDS = 4

/**
 * 压成一行文本 / 还原。`search`（自由文本）放**最后一个字段**：
 * `decodeQueryFields` 带 `limit`，搜索词里混进分隔符也不会把前面的字段撞错位。
 */
fun SongQuery.toSaveText(): String = encodeQueryFields(
    encodeQueryItems(units),
    encodeQueryItems(categories),
    sort.name,
    search,
)

/** [toSaveText] 的逆运算。结构对不上就退回默认值，**绝不抛异常**。 */
fun songQueryFromSaveText(raw: String): SongQuery {
    val f = decodeQueryFields(raw, SONG_QUERY_FIELDS) ?: return SongQuery()
    return SongQuery(
        units = decodeQueryItems(f[0]).toSet(),
        categories = decodeQueryItems(f[1]).toSet(),
        sort = SongSort.entries.firstOrNull { it.name == f[2] } ?: SongSort.NEWEST,
        search = f[3],
    )
}

/**
 * 筛选 + 排序。纯函数，方便单独验证。
 *
 * 搜索同时匹配**曲名**（中文名已在数据层优先取过）、**作曲**与**编号**。
 * 别名（罗马音 / 中文俗称，社区那份 13043 条）**没有纳入** ——
 * 那份数据要联网取、且是第三方派生数据（许可证不明），留到以后再说。
 */
fun filterSongs(source: List<HomeMusic>, query: SongQuery): List<HomeMusic> {
    val keyword = query.search.trim()
    val filtered = source.filter { music ->
        if (query.units.isNotEmpty() && music.unitTags.none { it in query.units }) return@filter false
        if (query.categories.isNotEmpty() && music.categories.none { it in query.categories }) return@filter false
        if (keyword.isNotEmpty()) {
            val hit = music.title.contains(keyword, ignoreCase = true) ||
                music.composer?.contains(keyword, ignoreCase = true) == true ||
                music.id.toString() == keyword
            if (!hit) return@filter false
        }
        true
    }
    return sortSongs(filtered, query.sort)
}

/** 只做排序，不筛选。分类页 / 人物页的「按时间先后」按钮用它。 */
fun sortSongs(source: List<HomeMusic>, sort: SongSort): List<HomeMusic> = when (sort) {
    SongSort.NEWEST -> source.sortedByDescending { it.publishedAt }
    // 同一时间上线的按 id 兜底，保证顺序稳定（否则每次重排会跳动）
    SongSort.OLDEST -> source.sortedWith(compareBy({ it.publishedAt }, { it.id }))
}

/**
 * 某个分类（＝游戏内选曲界面的分类）下的**成员**。
 *
 * `other` 返回**空列表** —— 它本来就不是一个团（`unitProfileKey` 是 null），
 * 所以"按人筛选"这件事对它不成立。界面上正是靠"返回空"来决定**不显示筛选按钮**，
 * 不需要另外写一个 `if (tag == OTHER)`。
 *
 * 返回的是**该团自己的成员**（例如 Leo/need 是 4 位），
 * **不含借调过来的虚拟歌手** —— 用户要的是"团里的人"。
 * 代价：这个分类里由 VS 演唱的曲子，用成员筛选是筛不出来的
 * （VS 有自己的分类，去那里筛）。
 */
fun unitMembers(tag: MusicUnitTag, characters: List<PjskCharacter>): List<PjskCharacter> {
    val key = tag.unitProfileKey ?: return emptyList()
    return characters.filter { it.unitKey == key }
}

/**
 * 按演唱者筛选。同一维取「或」：勾了几个人，只要其中任何一个人唱过就留下。
 *
 * @param songIdsByCharacter `characterId → 他唱过的 musicId`（`buildCharacterSongIds`）
 */
fun filterSongsBySingers(
    songs: List<HomeMusic>,
    characterIds: Set<Int>,
    songIdsByCharacter: Map<Int, Set<Int>>,
): List<HomeMusic> {
    if (characterIds.isEmpty()) return songs
    val allowed = characterIds.flatMapTo(mutableSetOf()) { songIdsByCharacter[it].orEmpty() }
    return songs.filter { it.id in allowed }
}

/** 某个分类下的一格卡片要显示的东西。 */
data class SongCategoryCard(
    val tag: MusicUnitTag,
    /** 该分类下的曲数。 */
    val songCount: Int,
    /**
     * 卡面用的曲绘素材名 = **该分类里最新上线的那首歌**的曲绘。
     *
     * 为什么不用组合 logo：内置 logo 只有 **60×60**（实测），铺进两列网格的卡片
     * 会被放大到糊；素材桶里也没有更大的（10 种可能路径全部 404）。
     * 曲绘是 740×740 的正方形，正好当卡面 —— logo 缩小到 20dp 当角标用。
     */
    val jacketAssetbundleName: String?,
)

/**
 * 统计各分类的曲数、以及卡面用哪张曲绘。纯函数。
 *
 * ⚠️ **各分类之和会大于总曲数**：一首歌可以同时挂多个组合标签
 * （实测 197 首有 2 个以上标签，还有 12 首全团合唱曲挂了 6~7 个）。
 * 这是数据事实，不是 bug，界面上不要把两者当成一回事。
 */
fun buildSongCategoryCards(musics: List<HomeMusic>): List<SongCategoryCard> =
    MusicUnitTag.ordered.mapNotNull { tag ->
        val inTag = musics.filter { tag.key in it.unitTags }
        if (inTag.isEmpty()) return@mapNotNull null
        SongCategoryCard(
            tag = tag,
            songCount = inTag.size,
            jacketAssetbundleName = inTag.maxByOrNull { it.publishedAt }?.assetbundleName,
        )
    }
