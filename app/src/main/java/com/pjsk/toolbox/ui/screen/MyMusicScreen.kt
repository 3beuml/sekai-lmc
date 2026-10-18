package com.pjsk.toolbox.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pjsk.toolbox.PjskApp
import com.pjsk.toolbox.data.home.HomeMusic
import com.pjsk.toolbox.data.music.MusicCategory
import com.pjsk.toolbox.data.music.MusicUnitTag
import com.pjsk.toolbox.data.music.SongQuery
import com.pjsk.toolbox.data.music.filterSongs
import com.pjsk.toolbox.data.profile.MusicResult
import com.pjsk.toolbox.data.profile.MyMusicQuery
import com.pjsk.toolbox.data.profile.MyMusicSort
import com.pjsk.toolbox.data.profile.PlayerAccount
import com.pjsk.toolbox.data.profile.STATUS_CHOICES
import com.pjsk.toolbox.data.profile.SongPlayFilter
import com.pjsk.toolbox.data.profile.SongPlayStatus
import com.pjsk.toolbox.data.profile.availableDifficulties
import com.pjsk.toolbox.data.profile.difficultyCounts
import com.pjsk.toolbox.data.profile.difficultyLabel
import com.pjsk.toolbox.data.profile.difficultySummary
import com.pjsk.toolbox.data.profile.filterMySongs
import com.pjsk.toolbox.data.profile.myMusicQueryFromSaveText
import com.pjsk.toolbox.data.profile.playStatusIndex
import com.pjsk.toolbox.data.profile.songsWithDifficulty
import com.pjsk.toolbox.data.profile.statusCounts
import com.pjsk.toolbox.data.profile.toSaveText
import com.pjsk.toolbox.data.remote.AssetUrls
import com.pjsk.toolbox.data.remote.ServerRegion
import com.pjsk.toolbox.data.settings.ScoreMetric
import com.pjsk.toolbox.ui.common.EmptyState
import com.pjsk.toolbox.ui.common.FilterGroupHint
import com.pjsk.toolbox.ui.common.FilterGroupTitle
import com.pjsk.toolbox.ui.common.FilterSheet
import com.pjsk.toolbox.ui.common.PlayerDataGate
import com.pjsk.toolbox.ui.common.RemoteImage
import com.pjsk.toolbox.ui.common.RowDivider
import com.pjsk.toolbox.util.moveMatchesFirst
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 「我的歌曲」：把快照里的 `userMusicResults` 摊成一张可筛可排的清单。
 *
 * ## 界面结构（0.1.15 按用户要求重排）
 *
 * 列表上方**什么都不留**：只有标题栏（第二行是当前选中的摘要）和两个按钮（搜索 / 筛选）。
 * 难度、组合、分类、排序、状态全部收进筛选面板 —— 这是用户的原话
 * 「把上面的东西清理掉，easy 这种做成筛选放到里面」。
 *
 * ## 筛选面板的计数口径（不说清就会被当成 bug）
 *
 * | 组 | 数字是什么 |
 * |:--|:--|
 * | 状态 | 在**当前难度 + 组合 + 分类 + 搜索**下，这一档有多少首（不受状态自身影响） |
 * | 难度 | 该难度在**当前状态 + 组合 + 分类 + 搜索**下有多少首（切过去就是列表数量） |
 * | 组合 / 分类 | 只受**搜索**影响（和歌曲页一致，就是这一栏一共有多少首） |
 *
 * ## 数据前提
 *
 * 成绩只来自绑定账号的**快照**，未绑定 / 本地没快照时走 [PlayerDataGate]，
 * 不显示空列表（空列表会让人以为自己的成绩丢了）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyMusicScreen(
    region: ServerRegion,
    onBack: () -> Unit,
    onOpenBind: () -> Unit,
    onOpenMyPlayer: () -> Unit,
    onOpenDetail: (Int) -> Unit,
) {
    val context = LocalContext.current
    val container = (context.applicationContext as PjskApp).container
    val settings = remember { container.appSettings }
    val playerRepository = remember { container.playerRepository }
    val musicRepository = remember { container.musicRepository }

    val account by settings.playerAccount.collectAsState()
    val scoreMetric by settings.scoreMetric.collectAsState()
    val musics by container.homeRepository.musics.collectAsState(initial = emptyList())
    val levels by musicRepository.difficultyLevels.collectAsState(initial = emptyMap())

    // 快照在 IO 线程读，界面先渲染骨架（缓存文件通常几十毫秒，列表初始化更慢）
    val snapshotState by produceState<SnapshotLoad>(SnapshotLoad.Loading, account) {
        val acc = account
        value = if (acc == null) {
            SnapshotLoad.Unbound
        } else {
            val snap = withContext(Dispatchers.IO) { playerRepository.loadCached(acc) }
            if (snap == null) SnapshotLoad.NoCache else SnapshotLoad.Ready(snap.musicResults)
        }
    }

    when (val state = snapshotState) {
        SnapshotLoad.Unbound -> PlayerDataGate(
            title = "我的歌曲",
            reason = "还没有绑定账号。",
            actionLabel = "去绑定",
            onBack = onBack,
            onAction = onOpenBind,
        )

        SnapshotLoad.NoCache -> PlayerDataGate(
            title = "我的歌曲",
            reason = "本地还没有这份账号的成绩数据，去「我的」读取一次就有了。",
            actionLabel = "去「我的」",
            onBack = onBack,
            onAction = onOpenMyPlayer,
        )

        SnapshotLoad.Loading -> LoadingScreen(onBack)

        is SnapshotLoad.Ready -> MyMusicContent(
            region = region,
            results = state.results,
            musics = musics,
            levels = levels,
            account = account,
            // 进来时默认停在「我的」页那个切换选中的指标（默认 AP）那一档
            initialStatus = if (scoreMetric == ScoreMetric.AP) SongPlayFilter.AP else SongPlayFilter.FC,
            metric = scoreMetric,
            onBack = onBack,
            onOpenDetail = onOpenDetail,
        )
    }
}

/** 快照读取的三种结局。`Loading` 只有一瞬，但没有它首帧会闪「去读取」的提示。 */
private sealed interface SnapshotLoad {
    data object Loading : SnapshotLoad
    data object Unbound : SnapshotLoad
    data object NoCache : SnapshotLoad
    data class Ready(val results: List<MusicResult>) : SnapshotLoad
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoadingScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("我的歌曲") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(28.dp))
        }
    }
}

/** 默认选哪个难度：**Master**（最多人打的那个）。没有 Master 的账号退回最高的那一档。 */
private fun defaultDifficulty(available: List<String>): String? {
    if (available.isEmpty()) return null
    return available.firstOrNull { it == "master" } ?: available.last()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MyMusicContent(
    region: ServerRegion,
    results: List<MusicResult>,
    musics: List<HomeMusic>,
    levels: Map<Int, Map<String, Int>>,
    account: PlayerAccount?,
    initialStatus: SongPlayFilter,
    /** 「我的」页那个切换当前看的是 AP 还是 FC —— 状态组的说明文字跟着它。 */
    metric: ScoreMetric,
    onBack: () -> Unit,
    onOpenDetail: (Int) -> Unit,
) {
    val available = remember(levels) { availableDifficulties(levels) }
    val defaultDiff = defaultDifficulty(available).orEmpty()

    // 筛选条件用 rememberSaveable：从歌曲详情返回时不能丢（和歌曲页同一套做法）
    var query by rememberSaveable(stateSaver = MyMusicQuerySaver) {
        mutableStateOf(
            MyMusicQuery(status = initialStatus, difficulty = defaultDiff),
        )
    }
    // 难度集合是异步来的：第一次进来时 query.difficulty 可能还是空串，这里补上默认值
    val effectiveQuery = remember(query, defaultDiff) {
        if (query.difficulty.isBlank()) query.copy(difficulty = defaultDiff) else query
    }

    // 纯临时状态：搜索框展开、筛选面板开合 —— 返回时不该自己弹出来
    var searchOpen by remember { mutableStateOf(false) }
    var showFilter by remember { mutableStateOf(false) }

    // 搜索 → 组合/分类 这两个「外层」结果，用来算各组的计数
    val searchScoped = remember(musics, effectiveQuery.search) {
        filterSongs(musics, SongQuery(search = effectiveQuery.search))
    }
    val scopeForStatusAndDifficulty = remember(
        searchScoped,
        effectiveQuery.units,
        effectiveQuery.categories,
    ) {
        filterSongs(
            searchScoped,
            SongQuery(units = effectiveQuery.units, categories = effectiveQuery.categories),
        )
    }
    val unitCountBase = remember(searchScoped, effectiveQuery.categories) {
        filterSongs(searchScoped, SongQuery(categories = effectiveQuery.categories))
    }
    val categoryCountBase = remember(searchScoped, effectiveQuery.units) {
        filterSongs(searchScoped, SongQuery(units = effectiveQuery.units))
    }

    // 当前难度下的歌（状态过滤之前）
    val ofDifficulty = remember(scopeForStatusAndDifficulty, levels, effectiveQuery.difficulty) {
        songsWithDifficulty(scopeForStatusAndDifficulty, levels, effectiveQuery.difficulty)
    }
    val index = remember(results, effectiveQuery.difficulty) {
        playStatusIndex(results, effectiveQuery.difficulty)
    }
    val visible = remember(musics, results, levels, effectiveQuery) {
        filterMySongs(musics, results, levels, effectiveQuery)
    }
    val currentSummary = remember(ofDifficulty, index, effectiveQuery.difficulty) {
        difficultySummary(effectiveQuery.difficulty, ofDifficulty, index)
    }
    // 面板上的数字：一次算好，别在每次重组里重算
    val statusCountMap = remember(ofDifficulty, index) { statusCounts(ofDifficulty, index) }
    val difficultyCountMap = remember(
        scopeForStatusAndDifficulty,
        results,
        levels,
        available,
        effectiveQuery.status,
    ) {
        difficultyCounts(
            scopeForStatusAndDifficulty,
            results,
            levels,
            available,
            effectiveQuery.status,
        )
    }
    val unitCounts = remember(unitCountBase) {
        MusicUnitTag.ordered.associate { tag -> tag.key to unitCountBase.count { tag.key in it.unitTags } }
    }
    val categoryCounts = remember(categoryCountBase) {
        MusicCategory.entries.associate { c -> c.key to categoryCountBase.count { c.key in it.categories } }
    }

    val listState = rememberLazyListState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (searchOpen) {
                        TextField(
                            value = effectiveQuery.search,
                            onValueChange = { query = effectiveQuery.copy(search = it) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("曲名 / 作曲 / 编号") },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {}),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                        )
                    } else {
                        Column {
                            Text("我的歌曲", style = MaterialTheme.typography.titleLarge)
                            // 副标题跟着当前筛选走：难度 + 状态 + 数量
                            Text(
                                text = buildString {
                                    append(account?.let { "UID ${it.gameId}" } ?: "未绑定")
                                    append(" · ")
                                    append(difficultyLabel(effectiveQuery.difficulty))
                                    append(" · ")
                                    append(effectiveQuery.status.label)
                                    append(" ")
                                    append(visible.size)
                                    append("/")
                                    append(ofDifficulty.size)
                                    if (!effectiveQuery.isFiltering) append(" 首")
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (searchOpen) {
                                // 关搜索框时一起清空搜索词，否则列表被一个看不见的条件筛着
                                searchOpen = false
                                query = effectiveQuery.copy(search = "")
                            } else {
                                searchOpen = true
                            }
                        },
                    ) {
                        Icon(
                            imageVector = if (searchOpen) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = "搜索",
                            tint = if (effectiveQuery.search.isNotBlank()) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    // 筛选入口：图标 + 已启用的维度个数角标（难度不算，见 MyMusicQuery）
                    BadgedBox(
                        badge = {
                            if (effectiveQuery.activeCount > 0) {
                                Badge { Text(effectiveQuery.activeCount.toString()) }
                            }
                        },
                    ) {
                        IconButton(onClick = { showFilter = true }) {
                            Icon(
                                Icons.Default.FilterList,
                                contentDescription = "筛选",
                                tint = if (effectiveQuery.isFiltering) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (available.isEmpty()) {
                EmptyState(
                    title = "还没有难度数据",
                    description = "内置的谱面难度表还没导入，去「更多 → 数据同步」检查一下。",
                )
                return@Column
            }

            // 顶部只留一行「当前条件」的快捷出口：有筛选时才出现，一点就重置
            if (effectiveQuery.isFiltering) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.material3.TextButton(
                        onClick = {
                            query = MyMusicQuery(
                                status = SongPlayFilter.ALL,
                                difficulty = effectiveQuery.difficulty,
                            )
                        },
                    ) { Text("清除筛选") }
                }
            }

            if (visible.isEmpty()) {
                EmptyState(
                    title = when (effectiveQuery.status) {
                        SongPlayFilter.FC -> "这一难度还没有 FC 过的"
                        SongPlayFilter.AP -> "这一难度还没有 AP 过的"
                        SongPlayFilter.PLAYED -> "这一难度还没打过"
                        SongPlayFilter.ALL -> "没有符合条件的歌曲"
                        SongPlayFilter.NO_FC, SongPlayFilter.NO_AP -> "没有符合条件的歌曲"
                    },
                    description = "换个难度或状态看看，或者点右上角「筛选」放宽条件。",
                )
                return@Column
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 16.dp),
            ) {
                items(visible, key = { it.id }) { song ->
                    val level = levels[song.id]?.get(effectiveQuery.difficulty)
                    val playStatus = index[song.id] ?: SongPlayStatus.NONE
                    MySongRow(
                        song = song,
                        region = region,
                        level = level,
                        status = playStatus,
                        onClick = { onOpenDetail(song.id) },
                    )
                    RowDivider()
                }
            }
        }
    }

    if (showFilter) {
        MyMusicFilterSheet(
            query = effectiveQuery,
            available = available,
            difficultyCounts = difficultyCountMap,
            statusCounts = statusCountMap,
            unitCounts = unitCounts,
            categoryCounts = categoryCounts,
            summary = currentSummary,
            metric = metric,
            matchedCount = visible.size,
            totalCount = ofDifficulty.size,
            onChange = { query = it },
            onDismiss = { showFilter = false },
        )
    }
}

/**
 * 筛选面板。全部条件都在这里：状态 / 难度 / 组合 / 分类 / 排序 / 两个排除开关。
 *
 * 和歌曲页共用 [FilterSheet] 这个外壳，中间的内容各页自己拼 —— 两页需要的组不一样，
 * 硬塞进一个模型会把彼此的需求互相污染。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MyMusicFilterSheet(
    query: MyMusicQuery,
    available: List<String>,
    difficultyCounts: Map<String, Int>,
    statusCounts: Map<SongPlayFilter, Int>,
    unitCounts: Map<String, Int>,
    categoryCounts: Map<String, Int>,
    summary: com.pjsk.toolbox.data.profile.DifficultySummary,
    metric: ScoreMetric,
    matchedCount: Int,
    totalCount: Int,
    onChange: (MyMusicQuery) -> Unit,
    onDismiss: () -> Unit,
) {
    FilterSheet(
        onDismiss = onDismiss,
        // 重置保留难度：这一屏的整套统计都是"按难度"的，重置难度等于换了一屏
        onReset = { onChange(MyMusicQuery(difficulty = query.difficulty)) },
        footerLabel = if (query.isFiltering) "显示 $matchedCount / $totalCount 首" else "显示全部 $totalCount 首",
    ) {
        // ── 状态 ──
        FilterGroupTitle("状态")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            STATUS_CHOICES.forEach { option ->
                // 四档是层层包含的（AP ⊆ FC ⊆ 已打 ⊆ 全部），所以这里**单选**：
                // 多选没有意义（同时选 FC 和 AP 就等于选 AP）
                FilterChip(
                    selected = query.status == option,
                    onClick = { onChange(query.copy(status = option)) },
                    label = { Text("${option.label} ${statusCounts[option] ?: 0}") },
                )
            }
        }
        FilterGroupHint("FC 含 AP；数字是当前难度 + 组合 + 分类下的数量。")

        // ── 难度 ──
        FilterGroupTitle("难度")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            available.forEach { d ->
                FilterChip(
                    selected = query.difficulty == d,
                    onClick = { onChange(query.copy(difficulty = d)) },
                    // 数字 = 切过去之后列表会有多少首（当前状态 + 组合 + 分类下）
                    label = { Text("${difficultyLabel(d)} ${difficultyCounts[d] ?: 0}") },
                )
            }
        }
        // 当前难度的三条比例：这一屏唯一的图形化信息，放在难度组下面
        Spacer(Modifier.height(6.dp))
        RatioBar("已打", summary.played, summary.total, MaterialTheme.colorScheme.secondary)
        RatioBar("FC", summary.fullCombo, summary.total, MaterialTheme.colorScheme.primary)
        RatioBar("AP", summary.allPerfect, summary.total, MaterialTheme.colorScheme.tertiary)
        FilterGroupHint("分母是该难度有谱面的曲目总数，不是「我打过的」。")

        // ── 组合 ──
        FilterGroupTitle("组合")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            MusicUnitTag.ordered
                .filter { tag -> (unitCounts[tag.key] ?: 0) > 0 }
                .moveMatchesFirst { tag -> tag.key in query.units }
                .forEach { tag ->
                    FilterChip(
                        selected = tag.key in query.units,
                        onClick = {
                            onChange(
                                query.copy(
                                    units = if (tag.key in query.units) {
                                        query.units - tag.key
                                    } else {
                                        query.units + tag.key
                                    },
                                ),
                            )
                        },
                        label = { Text("${tag.label} ${unitCounts[tag.key] ?: 0}", maxLines = 1) },
                    )
                }
        }

        // ── 分类 ──
        FilterGroupTitle("分类（演出形式）")
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            MusicCategory.entries
                .filter { c -> (categoryCounts[c.key] ?: 0) > 0 }
                .moveMatchesFirst { c -> c.key in query.categories }
                .forEach { c ->
                    FilterChip(
                        selected = c.key in query.categories,
                        onClick = {
                            onChange(
                                query.copy(
                                    categories = if (c.key in query.categories) {
                                        query.categories - c.key
                                    } else {
                                        query.categories + c.key
                                    },
                                ),
                            )
                        },
                        label = { Text("${c.label} ${categoryCounts[c.key] ?: 0}", maxLines = 1) },
                    )
                }
        }

        // ── 排序 ──
        FilterGroupTitle("排序")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MyMusicSort.entries.forEach { sort ->
                FilterChip(
                    selected = query.sort == sort,
                    onClick = { onChange(query.copy(sort = sort)) },
                    label = { Text(sort.label) },
                )
            }
        }

        // ── 排除开关 ──
        // 这一组是「未 FC / 未 AP」清单的替代：不占 chip 位，但能力还在。
        FilterGroupTitle("排除")
        SwitchRow(
            label = "隐藏已 FC 的",
            checked = query.hideFc,
            onCheckedChange = { onChange(query.copy(hideFc = it)) },
        )
        SwitchRow(
            label = "隐藏已 AP 的",
            checked = query.hideAp,
            onCheckedChange = { onChange(query.copy(hideAp = it)) },
        )
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** 一行「标签 + 分数 + 条」。分母是「有这一难度的曲目数」。 */
@Composable
private fun RatioBar(label: String, value: Int, total: Int, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(36.dp),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(9.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(if (total <= 0) 0f else (value.toFloat() / total).coerceIn(0f, 1f))
                    .height(9.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(color),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = "$value / $total",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(80.dp),
        )
    }
}

/**
 * 一行「我的歌曲」。
 *
 * 和歌曲页的 `SongRow` 不同：那一行右侧是 ▶ / ★（跟播放器有关），
 * 这一行右侧是**难度等级 + 成绩徽章**（跟玩家有关）。两件事塞进一行会挤成一团，
 * 所以这里不复用，只复用卡面和分隔线。
 */
@Composable
private fun MySongRow(
    song: HomeMusic,
    region: ServerRegion,
    level: Int?,
    status: SongPlayStatus,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RemoteImage(
            url = AssetUrls.musicJacket(region, song.assetbundleName),
            contentDescription = null,
            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(6.dp)),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            song.composer?.takeIf { it.isNotBlank() }?.let { composer ->
                Text(
                    text = composer,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = level?.let { "Lv.$it" } ?: "—",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            PlayStatusBadge(status)
        }
    }
}

/** 成绩徽章。AP / FC / 已打 / 未打 四态 —— 「未打」也画出来，不能留空让人以为是数据没读到。 */
@Composable
private fun PlayStatusBadge(status: SongPlayStatus) {
    val (label, color) = when {
        status.allPerfect -> "AP" to MaterialTheme.colorScheme.tertiary
        status.fullCombo -> "FC" to MaterialTheme.colorScheme.primary
        status.played -> "已打" to MaterialTheme.colorScheme.onSurfaceVariant
        else -> "未打" to MaterialTheme.colorScheme.outline
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
    }
}

/**
 * [MyMusicQuery] 的 Saver。放在这个文件里而不是 `ui/common/Savers.kt`：
 * 只有这一屏用它，而且它和本文件里的 defaultDifficulty 默认值是一套的
 * （难度为空时由界面补默认值，这里不猜）。
 */
private val MyMusicQuerySaver = androidx.compose.runtime.saveable.Saver<MyMusicQuery, String>(
    save = { it.toSaveText() },
    restore = { myMusicQueryFromSaveText(it) },
)
