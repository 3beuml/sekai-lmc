package com.pjsk.toolbox.data.profile

import com.pjsk.toolbox.data.home.HomeMusic
import com.pjsk.toolbox.data.music.SongQuery
import com.pjsk.toolbox.data.music.filterSongs
import com.pjsk.toolbox.util.decodeQueryFields
import com.pjsk.toolbox.util.decodeQueryItems
import com.pjsk.toolbox.util.encodeQueryFields
import com.pjsk.toolbox.util.encodeQueryItems

/**
 * 「我的歌曲」的纯逻辑：先按难度把成绩归并成索引，再按状态筛歌。
 *
 * 和 [PlayerModels] 一样，这里**只放纯函数**（能被 `tools/logic-check` 断言）。
 * 数据来源有两边：
 *  - 玩家的 `userMusicResults`（哪首歌在哪个难度上 FC / AP 了）；
 *  - 内置的 `musicDifficulties.json`（这首歌**有没有**这个难度、难度等级多少）。
 *
 * ## 为什么两边的歌集合不能直接相交
 *
 * 玩家成绩里只有「打过的」，内置表里是「全部」。
 * 所以「没 FC」的语义是**在内置表有这一难度的歌里，成绩索引里没有 FC 的那批** ——
 * 包括从没打过的（那当然没 FC）。反过来，如果拿"有成绩的歌"当全集，
 * 「没 FC」会永远筛出空集，因为成绩里剩下的恰好都是没 FC 的……听起来对，
 * 但那样**从没打过的歌永远进不了这个列表**，而那正是最该被看见的一批。
 */
data class SongPlayStatus(
    val played: Boolean,
    val fullCombo: Boolean,
    val allPerfect: Boolean,
    /**
     * 该难度的最高分。
     *
     * 加了它是因为**歌曲详情页要显示分数** —— 那里一次只看一首歌，
     * 没必要为了一个数字再单独解析一遍快照。列表页不用它，也不受影响（默认 0）。
     */
    val highScore: Long = 0L,
) {
    companion object {
        val NONE = SongPlayStatus(played = false, fullCombo = false, allPerfect = false)
    }
}

/**
 * 「我的歌曲」顶部的状态筛选。
 *
 * ⚠️ **[FC] 包含 [AP]**：游戏数据里 AP 的 `fullComboFlg` 也是 true，AP 是 FC 的子集。
 * 所以恒有 `AP + 未AP = 全部`、`FC + 未FC = 全部`、`AP ≤ FC ≤ 已打过 ≤ 全部`。
 * 如果把 [FC] 做成"只 FC 不 AP"，前两条等式就不成立 —— 用户一加就知道数字错了，
 * 而且"我 FC 了这首"在 AP 之后仍然是真话。
 */
enum class SongPlayFilter(val label: String) {
    ALL("全部"),
    PLAYED("已打过"),
    FC("FC 的"),
    AP("AP 的"),
    NO_FC("未 FC"),
    NO_AP("未 AP"),
}

/**
 * 把某一难度下的成绩压成 `musicId → 状态`。
 *
 * 同一首歌同一难度上游若给了多条，按**取最好**合并（任一 FC 即 FC、任一 AP 即 AP），
 * 与 [musicStats] 的口径完全一致 —— 否则「我的」页的计数和这一屏的列表会对不上。
 *
 * 难度比对**不区分大小写**（上游大小写未经逐字段核验，而 `musicStats` 也是小写化的）。
 */
fun playStatusIndex(results: List<MusicResult>, difficulty: String): Map<Int, SongPlayStatus> {
    val want = difficulty.lowercase()
    val out = HashMap<Int, SongPlayStatus>()
    for (r in results) {
        if (r.difficulty.lowercase() != want) continue
        val old = out[r.musicId]
        out[r.musicId] = if (old == null) {
            SongPlayStatus(
                played = true,
                fullCombo = r.fullCombo,
                allPerfect = r.allPerfect,
                highScore = r.highScore,
            )
        } else {
            old.copy(
                fullCombo = old.fullCombo || r.fullCombo,
                allPerfect = old.allPerfect || r.allPerfect,
                highScore = maxOf(old.highScore, r.highScore),
            )
        }
    }
    return out
}

/**
 * 某一首歌在**各难度**上的成绩（`难度 key → 状态`）。歌曲详情页用它。
 *
 * 与 [playStatusIndex] 是同一套合并口径（重复行取最好：任一 FC 即 FC、分数取最高），
 * 只是索引方向反过来。没有成绩的难度**不会**出现在结果里 —— 调用方按
 * `map[key] ?: SongPlayStatus.NONE` 当"未打"处理即可，不要在这里补默认值
 * （那会让"这首歌有没有成绩"这个判断失去依据）。
 */
fun songStatusByDifficulty(results: List<MusicResult>, musicId: Int): Map<String, SongPlayStatus> {
    val out = HashMap<String, SongPlayStatus>(6)
    for (r in results) {
        if (r.musicId != musicId) continue
        val key = r.difficulty.lowercase()
        val old = out[key]
        out[key] = if (old == null) {
            SongPlayStatus(
                played = true,
                fullCombo = r.fullCombo,
                allPerfect = r.allPerfect,
                highScore = r.highScore,
            )
        } else {
            old.copy(
                fullCombo = old.fullCombo || r.fullCombo,
                allPerfect = old.allPerfect || r.allPerfect,
                highScore = maxOf(old.highScore, r.highScore),
            )
        }
    }
    return out
}

/**
 * 内置表里**有这一难度**的歌。`levels` 是 `musicDifficulties` 的索引（见 MusicRepository）。
 *
 * 没有这一难度的歌直接出局：对一首没有 Append 的歌说它「没 AP」纯属噪音。
 * `levels` 为空（表还没同步）时退化成「全部歌」而不是空列表 ——
 * 宁可多显示，也不要让整屏空白得像是功能坏了。
 */
fun songsWithDifficulty(
    musics: List<HomeMusic>,
    levels: Map<Int, Map<String, Int>>,
    difficulty: String,
): List<HomeMusic> {
    if (levels.isEmpty()) return musics
    val want = difficulty.lowercase()
    return musics.filter { song ->
        levels[song.id]?.keys?.any { it.lowercase() == want } == true
    }
}

/**
 * 按状态筛。`source` 应该是 [songsWithDifficulty] 的结果。
 *
 * 语义：
 *  - [SongPlayFilter.ALL]  → 原样
 *  - [SongPlayFilter.PLAYED] → 有成绩的
 *  - [SongPlayFilter.FC]  → FC 过的（**含 AP 的**，见枚举上的说明）
 *  - [SongPlayFilter.AP]  → AP 过的
 *  - [SongPlayFilter.NO_FC]  → 没 FC 的（含从没打过的）
 *  - [SongPlayFilter.NO_AP]  → 没 AP 的（含从没打过的）
 */
fun filterSongsByPlayStatus(
    source: List<HomeMusic>,
    index: Map<Int, SongPlayStatus>,
    filter: SongPlayFilter,
): List<HomeMusic> = when (filter) {
    SongPlayFilter.ALL -> source
    SongPlayFilter.PLAYED -> source.filter { index[it.id]?.played == true }
    SongPlayFilter.FC -> source.filter { index[it.id]?.fullCombo == true }
    SongPlayFilter.AP -> source.filter { index[it.id]?.allPerfect == true }
    SongPlayFilter.NO_FC -> source.filter { index[it.id]?.fullCombo != true }
    SongPlayFilter.NO_AP -> source.filter { index[it.id]?.allPerfect != true }
}

/**
 * 哪些筛选属于「工作清单」—— 这些列表按难度等级从低到高排。
 *
 * 正面的（FC 的 / AP 的 / 已打过）保持传入顺序（歌曲页是"最新上线在前"），
 * 因为那几个列表是"我打出了什么"，按时间看更有意义。
 */
fun isWorkList(filter: SongPlayFilter): Boolean =
    filter == SongPlayFilter.NO_FC || filter == SongPlayFilter.NO_AP

/**
 * 「没 FC / 没 AP」列表的排序：**按难度等级从低到高**。
 *
 * 其它状态保持传入顺序（歌曲页是"最新上线在前"）。
 * 理由：没 FC 的列表是"接下来打什么"的工作清单，从简单的往上打才有用；
 * 按新歌在前排会让一整屏都是 33 级。
 */
fun sortForWork(musics: List<HomeMusic>, levels: Map<Int, Map<String, Int>>, difficulty: String): List<HomeMusic> {
    val want = difficulty.lowercase()
    fun levelOf(song: HomeMusic): Int =
        levels[song.id]?.entries?.firstOrNull { it.key.lowercase() == want }?.value ?: Int.MAX_VALUE
    return musics.sortedWith(
        compareBy<HomeMusic> { levelOf(it) }.thenBy { it.id },
    )
}

/** 某一难度的汇总（「我的歌曲」顶部的计数条）。 */
data class DifficultySummary(
    val difficulty: String,
    /** 内置表里**有这一难度**的曲目数（分母）。 */
    val total: Int,
    /** 打过这一难度的曲目数。 */
    val played: Int,
    val fullCombo: Int,
    val allPerfect: Int,
) {
    /** 已打比例；分母为 0 时给 0（不产生 NaN）。 */
    val playedFraction: Float get() = if (total <= 0) 0f else played.toFloat() / total
    val fcFraction: Float get() = if (total <= 0) 0f else fullCombo.toFloat() / total
    val apFraction: Float get() = if (total <= 0) 0f else allPerfect.toFloat() / total
}

/**
 * 算一个难度的汇总。
 *
 * `played` / `fullCombo` / `allPerfect` 只数**内置表里真有这一难度**的歌，
 * 否则上游多给一条脏数据就会让「FC 37 / 共 36 首」这种数字出现。
 */
fun difficultySummary(
    difficulty: String,
    songsWithDifficulty: List<HomeMusic>,
    index: Map<Int, SongPlayStatus>,
): DifficultySummary {
    val ids = songsWithDifficulty.mapTo(HashSet(songsWithDifficulty.size)) { it.id }
    val played = index.filterKeys { it in ids }.values
    return DifficultySummary(
        difficulty = difficulty,
        total = ids.size,
        played = played.count { it.played },
        fullCombo = played.count { it.fullCombo },
        allPerfect = played.count { it.allPerfect },
    )
}

/** 「我的歌曲」的排序。 */
enum class MyMusicSort(val label: String) {
    NEWEST("最新上线"),
    OLDEST("最早上线"),

    /**
     * 按难度等级从低到高。
     *
     * 这一项替代了被删掉的「未 FC / 未 AP」清单的一个用途：想从简单的往上刷时，
     * 用它把同一批歌按等级排一遍就行，不必再占一个状态 chip。
     */
    LEVEL_ASC("等级从低到高"),
}

/** 面板上给出的四个状态。未 FC / 未 AP 换成了下面两个排除开关，不再占 chip 位。 */
val STATUS_CHOICES: List<SongPlayFilter> = listOf(
    SongPlayFilter.ALL,
    SongPlayFilter.PLAYED,
    SongPlayFilter.FC,
    SongPlayFilter.AP,
)

/**
 * 「我的歌曲」的筛选条件。
 *
 * 结构和歌曲页的 [com.pjsk.toolbox.data.music.SongQuery] 刻意保持同形
 * （搜索 / 组合 / 分类都是同一套语义：同维多选取或、跨维取且），
 * 只是多了「状态 + 难度」这两个玩家数据维，以及两个排除开关。
 */
data class MyMusicQuery(
    val status: SongPlayFilter = SongPlayFilter.ALL,
    /** 难度。空串 = 不按难度筛（正常情况总会有一个，默认 Master）。 */
    val difficulty: String = "",
    val units: Set<String> = emptySet(),
    val categories: Set<String> = emptySet(),
    val sort: MyMusicSort = MyMusicSort.NEWEST,
    /** 「隐藏已 FC 的」：把 FC 过的剔掉，剩下的就是"还没 FC"的（含从没打过的）。 */
    val hideFc: Boolean = false,
    /** 「隐藏已 AP 的」。 */
    val hideAp: Boolean = false,
    val search: String = "",
) {
    /**
     * 已启用的筛选**维度个数**（筛选入口上的角标）。
     *
     * ⚠️ 难度**不算**：它总有一个值（默认 Master），算进去角标会永远是 1，
     * 那就等于没有提示作用。
     */
    val activeCount: Int
        get() = (if (status != SongPlayFilter.ALL) 1 else 0) +
            (if (units.isNotEmpty()) 1 else 0) +
            (if (categories.isNotEmpty()) 1 else 0) +
            (if (hideFc) 1 else 0) +
            (if (hideAp) 1 else 0) +
            (if (search.isNotBlank()) 1 else 0)

    val isFiltering: Boolean get() = activeCount > 0
}

/** [MyMusicQuery] 的字段个数（编解码契约，改字段必须同步改）。 */
private const val MY_MUSIC_QUERY_FIELDS = 8

/** 压成一行文本（`rememberSaveable` 用；从歌曲详情返回时筛选不能丢）。搜索词放最后。 */
fun MyMusicQuery.toSaveText(): String = encodeQueryFields(
    status.name,
    difficulty,
    encodeQueryItems(units),
    encodeQueryItems(categories),
    sort.name,
    if (hideFc) "1" else "",
    if (hideAp) "1" else "",
    search,
)

/** [toSaveText] 的逆运算。结构对不上就退回默认值，绝不抛异常。 */
fun myMusicQueryFromSaveText(raw: String): MyMusicQuery {
    val f = decodeQueryFields(raw, MY_MUSIC_QUERY_FIELDS) ?: return MyMusicQuery()
    return MyMusicQuery(
        status = SongPlayFilter.entries.firstOrNull { it.name == f[0] } ?: SongPlayFilter.ALL,
        difficulty = f[1],
        units = decodeQueryItems(f[2]).toSet(),
        categories = decodeQueryItems(f[3]).toSet(),
        sort = MyMusicSort.entries.firstOrNull { it.name == f[4] } ?: MyMusicSort.NEWEST,
        hideFc = f[5] == "1",
        hideAp = f[6] == "1",
        search = f[7],
    )
}

/**
 * 「我的歌曲」的完整筛选 + 排序。纯函数，可断言。
 *
 * 顺序是刻意的：
 *  1. 搜索 / 组合 / 分类 —— **直接复用歌曲页的 [filterSongs]**，保证这两处的
 *     「组合多选是或、跨维是且」完全一致，不各写一套；
 *  2. 难度 —— 只留真有这一难度的歌；
 *  3. 状态 —— 那四档其中之一；
 *  4. 两个排除开关（可以和状态叠加，叠加出来是空集就是空集，不做特殊处理）；
 *  5. 排序。
 */
fun filterMySongs(
    musics: List<HomeMusic>,
    results: List<MusicResult>,
    levels: Map<Int, Map<String, Int>>,
    query: MyMusicQuery,
): List<HomeMusic> {
    val base = filterSongs(
        musics,
        SongQuery(search = query.search, units = query.units, categories = query.categories),
    )
    val ofDifficulty = if (query.difficulty.isBlank()) base else songsWithDifficulty(base, levels, query.difficulty)
    val index = playStatusIndex(results, query.difficulty)
    var out = filterSongsByPlayStatus(ofDifficulty, index, query.status)
    if (query.hideFc) out = filterSongsByPlayStatus(out, index, SongPlayFilter.NO_FC)
    if (query.hideAp) out = filterSongsByPlayStatus(out, index, SongPlayFilter.NO_AP)
    return when (query.sort) {
        MyMusicSort.NEWEST -> out.sortedByDescending { it.publishedAt }
        MyMusicSort.OLDEST -> out.sortedWith(compareBy({ it.publishedAt }, { it.id }))
        MyMusicSort.LEVEL_ASC -> sortForWork(out, levels, query.difficulty)
    }
}

/**
 * 各状态在当前集合下的数量（面板上「状态」那一行的数字）。
 *
 * `songs` 应当是**已经按难度 + 组合 + 分类 + 搜索过滤**过的集合，
 * 但**不能**按状态过滤过 —— 否则选中 FC 之后其余三个数字会一起缩成 0，
 * 看起来像是功能坏了。
 */
fun statusCounts(
    songs: List<HomeMusic>,
    index: Map<Int, SongPlayStatus>,
    choices: List<SongPlayFilter> = STATUS_CHOICES,
): Map<SongPlayFilter, Int> = choices.associateWith { option ->
    filterSongsByPlayStatus(songs, index, option).size
}

/**
 * 各难度的数量（面板上「难度」那一行的数字）：该难度下**当前状态**有多少首。
 *
 * 这样切难度时看到的数字，就是切过去之后列表会有多少首 —— 面板不用解释自己。
 */
fun difficultyCounts(
    base: List<HomeMusic>,
    results: List<MusicResult>,
    levels: Map<Int, Map<String, Int>>,
    difficulties: List<String>,
    status: SongPlayFilter,
): Map<String, Int> = difficulties.associateWith { d ->
    filterSongsByPlayStatus(
        songsWithDifficulty(base, levels, d),
        playStatusIndex(results, d),
        status,
    ).size
}

/**
 * 有哪些难度可以选 —— 只在数据里**真的存在**的难度里选，顺序按 [difficultyOrder]。
 *
 * `userMusicResults` 里没出现、内置表里也没有的难度，就不该给这个 chip。
 */
fun availableDifficulties(levels: Map<Int, Map<String, Int>>): List<String> {
    val seen = LinkedHashSet<String>()
    levels.values.forEach { perSong -> perSong.keys.forEach { seen.add(it.lowercase()) } }
    return seen.sortedBy { difficultyOrder(it) }
}
