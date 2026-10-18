package com.pjsk.toolbox.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pjsk.toolbox.PjskApp
import com.pjsk.toolbox.data.home.HomeMusic
import com.pjsk.toolbox.data.profile.PlayerAccount
import com.pjsk.toolbox.data.profile.MusicResult
import com.pjsk.toolbox.data.profile.SongPlayFilter
import com.pjsk.toolbox.data.profile.SongPlayStatus
import com.pjsk.toolbox.data.profile.availableDifficulties
import com.pjsk.toolbox.data.profile.difficultyLabel
import com.pjsk.toolbox.data.profile.difficultySummary
import com.pjsk.toolbox.data.profile.filterSongsByPlayStatus
import com.pjsk.toolbox.data.profile.isWorkList
import com.pjsk.toolbox.data.profile.playStatusIndex
import com.pjsk.toolbox.data.profile.songsWithDifficulty
import com.pjsk.toolbox.data.profile.sortForWork
import com.pjsk.toolbox.data.remote.AssetUrls
import com.pjsk.toolbox.data.remote.ServerRegion
import com.pjsk.toolbox.data.settings.ScoreMetric
import com.pjsk.toolbox.ui.common.EmptyState
import com.pjsk.toolbox.ui.common.PlayerDataGate
import com.pjsk.toolbox.ui.common.RemoteImage
import com.pjsk.toolbox.ui.common.RowDivider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 「我的歌曲」：把快照里的 `userMusicResults` 摊成一张可操作的清单。
 *
 * ## 三个问题，一个屏解决
 *
 * 1. **我现在什么水平** —— 顶部按难度给出「已打 / 共 · FC / AP」，分母是内置表里
 *    **真有这一难度**的曲目数（不是"我打过的"，那样永远是 100%）。
 * 2. **哪些还没 FC / 没 AP** —— 状态筛选。这两个列表**包含从没打过的歌**，
 *    因为没打过当然没 FC，而那正是最该被看见的一批（口径见 `SongPlayModels`）。
 * 3. **从哪首开始** —— 没 FC / 没 AP 的列表按**难度等级从低到高**排，
 *    不是按新歌在前（那样一整屏都是 33 级，没法当工作清单用）。
 *
 * ## 数据前提
 *
 * 成绩只来自绑定账号的**快照**（`userMusicResults`），所以这一屏和「我的」一样，
 * 需要先绑定 + 读过一次数据；未绑定时走 [PlayerDataGate] 而不是显示空列表。
 * 难度等级来自内置 `musicDifficulties.json`，与玩家数据无关，所以**没绑定也能显示歌曲**，
 * 只是没有成绩 —— 但那种"半个功能"没有使用价值，仍然统一走门。
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
            initialFilter = if (scoreMetric == ScoreMetric.AP) SongPlayFilter.AP else SongPlayFilter.FC,
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun MyMusicContent(
    region: ServerRegion,
    results: List<MusicResult>,
    musics: List<HomeMusic>,
    levels: Map<Int, Map<String, Int>>,
    account: PlayerAccount?,
    initialFilter: SongPlayFilter,
    /** 「我的」页那个切换当前看的是 AP 还是 FC —— 难度 chip 上写的数跟着它。 */
    metric: ScoreMetric,
    onBack: () -> Unit,
    onOpenDetail: (Int) -> Unit,
) {
    val available = remember(levels) { availableDifficulties(levels) }

    // 记住用户选的难度 / 状态：从歌曲详情返回时不能跳回默认值。
    // ⚠️ 存**名字**而不是下标：难度集合是异步来的，下标会随数据到达而错位。
    var difficultyName by rememberSaveable { mutableStateOf("") }
    var filterName by rememberSaveable { mutableStateOf(initialFilter.name) }
    val filter = SongPlayFilter.entries.firstOrNull { it.name == filterName } ?: initialFilter
    val difficulty = difficultyName.takeIf { it in available } ?: defaultDifficulty(available).orEmpty()

    // 计分板：每个难度的汇总（一个难度一行，方便横向比较自己卡在哪一档）
    val summaries = remember(results, musics, levels, available) {
        available.map { d ->
            val ofDifficulty = songsWithDifficulty(musics, levels, d)
            difficultySummary(d, ofDifficulty, playStatusIndex(results, d))
        }
    }
    val current = summaries.firstOrNull { it.difficulty == difficulty }
    val index = remember(results, difficulty) {
        if (difficulty.isBlank()) emptyMap() else playStatusIndex(results, difficulty)
    }

    // 当前难度下的歌 → 按状态筛 →（工作清单）按等级升序
    val ofDifficulty = remember(musics, levels, difficulty) {
        if (difficulty.isBlank()) emptyList() else songsWithDifficulty(musics, levels, difficulty)
    }
    val visible = remember(ofDifficulty, index, filter, levels, difficulty) {
        val filtered = filterSongsByPlayStatus(ofDifficulty, index, filter)
        if (isWorkList(filter)) {
            sortForWork(filtered, levels, difficulty)
        } else {
            filtered
        }
    }
    // chip 上的数量：一次算完，别在 6 个 chip 里各跑一遍整表
    val filterCounts = remember(ofDifficulty, index) {
        SongPlayFilter.entries.associateWith { option ->
            filterSongsByPlayStatus(ofDifficulty, index, option).size
        }
    }

    val listState = rememberLazyListState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("我的歌曲", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = buildString {
                                append(account?.let { "UID ${it.gameId}" } ?: "未绑定")
                                if (current != null) {
                                    // 副标题跟着当前 chip 走：停在「AP 的」就显示 AP 的数，
                                    // 不能固定写"已打"，否则和下面选中的 chip 对不上。
                                    append(" · ${difficultyLabel(current.difficulty)} ")
                                    append(filter.label)
                                    append(" ")
                                    append(filterCounts[filter] ?: 0)
                                    append("/${current.total}")
                                }
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // ── 难度选择 ──
            if (available.isEmpty()) {
                EmptyState(
                    title = "还没有难度数据",
                    description = "内置的谱面难度表还没导入，去「更多 → 数据同步」检查一下。",
                )
                return@Column
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                available.forEach { d ->
                    val s = summaries.firstOrNull { it.difficulty == d }
                    // 指标由「我的」页那个 AP/FC 切换决定：切到 FC 后这一行就全是 FC 数
                    val metricCount = if (metric == ScoreMetric.AP) s?.allPerfect else s?.fullCombo
                    FilterChip(
                        selected = d == difficulty,
                        onClick = { difficultyName = d },
                        label = {
                            Text("${difficultyLabel(d)} ${metricCount ?: 0}${metric.label}")
                        },
                    )
                }
            }

            // ── 当前难度的计数卡 ──
            current?.let { s ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    ),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = difficultyLabel(s.difficulty),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "${s.total} 首",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        RatioBar("已打", s.played, s.total, MaterialTheme.colorScheme.secondary)
                        RatioBar("FC", s.fullCombo, s.total, MaterialTheme.colorScheme.primary)
                        RatioBar("AP", s.allPerfect, s.total, MaterialTheme.colorScheme.tertiary)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "分母是内置谱面里「有这一难度」的曲目数；FC 含 AP。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // ── 状态筛选 ──
            // ⚠️ 六个 chip，每个都带数字，一行放不下（六×约 90dp ≈ 540dp，屏幕只有约 390dp）。
            // 用 FlowRow 折成两行，而不是横向滚动 —— 这一行是用来**横向比较**的，
            // 藏一半在屏幕外就失去意义了。
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                SongPlayFilter.entries.forEach { option ->
                    FilterChip(
                        selected = filter == option,
                        onClick = { filterName = option.name },
                        label = { Text("${option.label} ${filterCounts[option] ?: 0}") },
                    )
                }
            }
            Text(
                text = "未 FC / 未 AP 含从未打过的曲目；FC 含 AP。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            )

            if (visible.isEmpty()) {
                EmptyState(
                    title = when (filter) {
                        SongPlayFilter.NO_FC -> "这一难度已经全部 FC 了"
                        SongPlayFilter.NO_AP -> "这一难度已经全部 AP 了"
                        SongPlayFilter.FC -> "这一难度还没有 FC 过的"
                        SongPlayFilter.AP -> "这一难度还没有 AP 过的"
                        SongPlayFilter.PLAYED -> "这一难度还没打过"
                        SongPlayFilter.ALL -> "没有可显示的歌曲"
                    },
                    description = "换一个难度或状态看看。",
                )
                return@Column
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 16.dp),
            ) {
                items(visible, key = { it.id }) { song ->
                    val level = levels[song.id]?.get(difficulty)
                    val status = index[song.id] ?: SongPlayStatus.NONE
                    MySongRow(
                        song = song,
                        region = region,
                        level = level,
                        status = status,
                        onClick = { onOpenDetail(song.id) },
                    )
                    RowDivider()
                }
            }
        }
    }
}

/** 一行「标签 + 分数 + 条」。和「我的」页那种条同源，但这屏的数值口径不同（分母含没打过的）。 */
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
            modifier = Modifier.width(76.dp),
        )
    }
}

/**
 * 一行「我的歌曲」。
 *
 * 和歌曲页的 [com.pjsk.toolbox.ui.common.SongRow] 不同：那一行右侧是 ▶ / ★（跟播放器有关），
 * 这一行右侧是**难度等级 + 成绩徽章**（跟玩家有关）。两件事塞进一行会挤成一团，
 * 所以这里不复用，而是只复用卡面和分隔线。
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
