package com.pjsk.toolbox.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pjsk.toolbox.PjskApp
import com.pjsk.toolbox.data.home.HomeMusic
import com.pjsk.toolbox.data.profile.PlayerSnapshot
import com.pjsk.toolbox.data.remote.AssetUrls
import com.pjsk.toolbox.data.remote.ServerRegion
import com.pjsk.toolbox.data.score.Best30Entry
import com.pjsk.toolbox.data.score.Best30Result
import com.pjsk.toolbox.data.score.ChartConstants
import com.pjsk.toolbox.data.score.computeBest30
import com.pjsk.toolbox.data.score.formatBest30
import com.pjsk.toolbox.ui.common.PlayerDataGate
import com.pjsk.toolbox.ui.common.RemoteImage
import com.pjsk.toolbox.ui.common.RowDivider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Best30 的独立页面。
 *
 * ## 为什么要有这一屏（而不是只在「我的」里放个列表）
 *
 * 30 条记录里大部分歌**你认不出名字**（尤其日文名 + 冷门曲），光看「曲名 + 定数」
 * 根本对不上是哪首。所以这一屏**每一条都带曲绘**——图比名字快得多，
 * 一眼就能认出"哦是这首"。
 *
 * 顺带解决了另外两个问题：
 *  - 30 条放在底部弹层里只能看到 7 条（受 480dp 上限约束），翻起来很难受；
 *  - 独立页面可以给每行更多高度（64dp 曲绘 + 两行标题），而不是挤成一行小字。
 *
 * ## 数据前提
 *
 * 成绩来自绑定账号的本地快照，定数来自内置 assets。未绑定 / 本地没快照时走
 * [PlayerDataGate]，不显示一个空列表。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Best30Screen(
    region: ServerRegion,
    onBack: () -> Unit,
    onOpenBind: () -> Unit,
    onOpenMyPlayer: () -> Unit,
    /** 点某一首 → 歌曲详情。带了这一首的排名，用户往往想回去看完整成绩。 */
    onOpenDetail: (Int) -> Unit,
) {
    val context = LocalContext.current
    val container = (context.applicationContext as PjskApp).container
    val settings = remember { container.appSettings }
    val playerRepository = remember { container.playerRepository }

    val account by settings.playerAccount.collectAsState()
    val musics by container.homeRepository.musics.collectAsState(initial = emptyList())

    val snapshotState by produceState<Best30Load>(Best30Load.Loading, account) {
        val acc = account
        value = if (acc == null) {
            Best30Load.Unbound
        } else {
            // ⚠️ 这里刻意**不联网**：进这一屏只是换个展示方式，不该额外打一次 Haruki
            // （ToS 里明确不希望客户端反复拉取）。只读本地缓存；没有缓存就引导去「我的」读一次。
            val snap = withContext(Dispatchers.IO) { playerRepository.loadCached(acc) }
            if (snap == null) Best30Load.NoCache else Best30Load.Ready(snap)
        }
    }

    when (val state = snapshotState) {
        Best30Load.Unbound -> PlayerDataGate(
            title = "Best30",
            reason = "还没有绑定账号。",
            actionLabel = "去绑定",
            onBack = onBack,
            onAction = onOpenBind,
        )

        Best30Load.NoCache -> PlayerDataGate(
            title = "Best30",
            reason = "本地还没有这份账号的成绩数据，去「我的」读取一次就有了。",
            actionLabel = "去「我的」",
            onBack = onBack,
            onAction = onOpenMyPlayer,
        )

        Best30Load.Loading -> ScaffoldWithBack("Best30", onBack) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(28.dp))
            }
        }

        is Best30Load.Ready -> Best30Content(
            region = region,
            snapshot = state.snapshot,
            musics = musics,
            onBack = onBack,
            onOpenDetail = onOpenDetail,
        )
    }
}

/** 这一步的三种结局（与「我的歌曲」同构，便于将来合并）。 */
private sealed interface Best30Load {
    data object Loading : Best30Load
    data object Unbound : Best30Load
    data object NoCache : Best30Load
    data class Ready(val snapshot: PlayerSnapshot) : Best30Load
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScaffoldWithBack(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding)) { content() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Best30Content(
    region: ServerRegion,
    snapshot: PlayerSnapshot,
    musics: List<HomeMusic>,
    onBack: () -> Unit,
    onOpenDetail: (Int) -> Unit,
) {
    val context = LocalContext.current
    val chartConstants by produceState<Map<Int, Map<String, Double>>>(emptyMap()) {
        value = withContext(Dispatchers.IO) {
            ChartConstants.load { context.assets.open(BEST30_ASSET) }
        }
    }
    val result = remember(snapshot.musicResults, chartConstants) {
        computeBest30(snapshot.musicResults, chartConstants)
    }
    val musicById = remember(musics) { musics.associateBy { it.id } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Best30", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "平均定数 ${formatBest30(result.average)} · " +
                                "${result.entries.size} / ${Best30Result.LIMIT} 首",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        if (result.isEmpty) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("还没有计入 Best30 的谱面", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Best30 只统计 AP / FC 过的、且定数已知的谱面（Expert / Master / Append）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            return@Scaffold
        }

        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // 头部：平均数 + 规则说明（一句话，避免有人以为高分也算）
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                ),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = formatBest30(result.average),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "平均定数",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    Text(
                        text = if (result.isComplete) {
                            "AP 计定数、FC 计定数 − 1；只看 AP / FC，不看高分。"
                        } else {
                            "合格谱面只有 ${result.poolSize} 个（不足 ${Best30Result.LIMIT}），" +
                                "这个平均值与社区的 B30 不可比。"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 24.dp),
            ) {
                itemsIndexed(result.entries, key = { _, e -> "${e.musicId}-${e.difficulty}" }) { index, entry ->
                    val song = musicById[entry.musicId]
                    Best30ListRow(
                        rank = index + 1,
                        entry = entry,
                        title = song?.title,
                        jacketUrl = song?.let { AssetUrls.musicJacket(region, it.assetbundleName) },
                        onClick = { onOpenDetail(entry.musicId) },
                    )
                    RowDivider()
                }
            }
        }
    }
}

/** 内置定数表的 assets 路径（生成脚本见 tools/pjskb30/build-asset.mjs）。 */
private const val BEST30_ASSET = "pjskb30/chart-constants.csv"

/**
 * Best30 的一行（独立页面用）：排名 · 曲绘 · 曲名 + 难度/定数 · 贡献值 + AP/FC。
 *
 * 曲绘是这一行存在的理由 —— 30 条日文曲名没人认得住，图一眼就行。
 *
 * ⚠️ 曲绘可能取不到（快照里有定数表覆盖、但 `musics.json` 里还没有的新曲）：
 * 这时 [jacketUrl] 为 null，[RemoteImage] 会画占位块，标题退回 `#id`。
 * 不隐藏整行 —— 那样会让排名与「已计入 N」对不上。
 */
@Composable
fun Best30ListRow(
    rank: Int,
    entry: Best30Entry,
    title: String?,
    jacketUrl: String?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$rank",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(26.dp),
        )
        RemoteImage(
            url = jacketUrl,
            contentDescription = null,
            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(6.dp)),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title ?: "#${entry.musicId}",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${difficultyAbbr(entry.difficulty)} · 定数 ${oneDecimal(entry.constant)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = oneDecimal(entry.value),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (entry.allPerfect) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
            Text(
                text = if (entry.allPerfect) "AP" else "FC",
                style = MaterialTheme.typography.labelSmall,
                color = if (entry.allPerfect) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
        }
    }
}

/** 定数 / 贡献值一位小数（`32.7`）。 */
fun oneDecimal(value: Double): String = String.format(java.util.Locale.US, "%.1f", value)

/** 难度缩写。`data.music` 里那份只覆盖歌曲页的三档，这里补齐六档。 */
fun difficultyAbbr(difficulty: String): String = when (difficulty.lowercase()) {
    "easy" -> "EZY"
    "normal" -> "NRM"
    "hard" -> "HRD"
    "expert" -> "EXP"
    "master" -> "MAS"
    "append" -> "APD"
    else -> difficulty.uppercase().take(3)
}
