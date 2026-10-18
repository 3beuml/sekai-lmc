package com.pjsk.toolbox.data.profile

import com.pjsk.toolbox.data.home.HomeMusic

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
            SongPlayStatus(played = true, fullCombo = r.fullCombo, allPerfect = r.allPerfect)
        } else {
            old.copy(
                fullCombo = old.fullCombo || r.fullCombo,
                allPerfect = old.allPerfect || r.allPerfect,
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

/**
 * 有哪些难度可以选 —— 只在数据里**真的存在**的难度里选，顺序按 [difficultyOrder]。
 *
 * 一句 `userMusicResults` 里没出现 Append、内置表里也没有，就不该给这个 chip。
 */
fun availableDifficulties(levels: Map<Int, Map<String, Int>>): List<String> {
    val seen = LinkedHashSet<String>()
    levels.values.forEach { perSong -> perSong.keys.forEach { seen.add(it.lowercase()) } }
    return seen.sortedBy { difficultyOrder(it) }
}
