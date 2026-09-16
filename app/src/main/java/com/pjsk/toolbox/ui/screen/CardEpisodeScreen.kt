package com.pjsk.toolbox.ui.screen

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pjsk.toolbox.PjskApp
import com.pjsk.toolbox.data.card.CardEpisode
import com.pjsk.toolbox.data.card.FALLBACK_RARITY_CAPS
import com.pjsk.toolbox.data.card.displayNameFor
import com.pjsk.toolbox.data.remote.AssetUrls
import com.pjsk.toolbox.data.remote.ServerRegion
import com.pjsk.toolbox.ui.common.EmptyState
import com.pjsk.toolbox.ui.common.KeyValueRow
import com.pjsk.toolbox.ui.common.RowDivider
import com.pjsk.toolbox.ui.common.SectionTitle
import coil.compose.AsyncImage

/**
 * 卡牌剧情详情（从详情页剧情区块点进来）。
 *
 * **这是「跳转目标」而不是剧情阅读器。** 本项目第一版在这里展示的是
 * 「这篇剧情是什么、什么条件开放、要花多少素材、读完给多少能力值」——
 * 也就是把 master data 里**确实有**的字段讲清楚。
 *
 * 剧情**正文**不在这里显示，原因是正文不在 master data 里：`cardEpisodes.json` 只给
 * `scenarioId`（如 `001001_ichika01`），正文要另外下载 scenario 资源并解析剧本格式。
 * 那属于「以后做」的范围。本项目宁可不显示，也不编造 —— 所以这里只把
 * `scenarioId` 原样列出来，并说明正文暂不提供。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardEpisodeScreen(
    cardId: Int,
    part: Int,
    region: ServerRegion,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val container = (context.applicationContext as PjskApp).container
    val repository = container.cardRepository

    val cards by repository.cards.collectAsState()
    val episodesByCard by repository.cardEpisodes.collectAsState(initial = emptyMap())
    val caps by repository.rarityCaps.collectAsState(initial = FALLBACK_RARITY_CAPS)
    // 素材 id → 中文名（简中服只有前 200 条，缺的回退日文名，再缺就显示 id）
    val materials by repository.materials.collectAsState(initial = emptyMap())
    val nameLanguage by container.appSettings.nameLanguage.collectAsState()

    val card = remember(cards, cardId) { cards.firstOrNull { it.id == cardId } }
    val episode: CardEpisode? = remember(episodesByCard, cardId, part) {
        episodesByCard[cardId].orEmpty().firstOrNull { it.part == part }
    }
    val cap = card?.let { caps[it.rarityKey] }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(episode?.partLabel?.let { "卡牌剧情 · $it" } ?: "卡牌剧情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        if (card == null || episode == null) {
            Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                EmptyState(
                    title = if (card == null) "找不到这张卡" else "这篇剧情不存在",
                    description = "该卡可能没有剧情，或数据尚未同步。",
                )
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            // ── 属于哪张卡 ──
            SectionTitle("所属卡牌")
            InfoCard {
                KeyValueRow("卡名", card.displayNameFor(nameLanguage))
                KeyValueRow("编号", "#${card.id}")
                KeyValueRow("星级", "★".repeat(card.stars.coerceAtLeast(1)))
            }

            // ── 这篇剧情 ──
            SectionTitle("剧情信息")
            InfoCard {
                KeyValueRow("篇目", episode.partLabel)
                KeyValueRow("标题", episode.title ?: "（官方没有给标题）")
                episode.releaseConditionId?.let {
                    KeyValueRow("开放条件 id", it.toString())
                }
                // 把「有 id 但没有解释」这件事讲清楚：这张表的取值含义不在 master data 里
                if (episode.releaseConditionId != null) {
                    Text(
                        text = "开放条件的具体要求（角色等级 / 卡片等级等）不在 master data 里，" +
                            "master data 只给了条件 id。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            // ── 读完能拿什么 ──
            SectionTitle("通关奖励")
            InfoCard {
                KeyValueRow(
                    "能力值加成",
                    episode.powerBonus.joinToString(" / ") { "+${"%,d".format(it)}" },
                )
                KeyValueRow("综合力合计", "+${"%,d".format(episode.totalPowerBonus)}")
                Text(
                    text = "表现力 / 技巧 / 体能各 +${"%,d".format(episode.powerBonus.firstOrNull() ?: 0)}，" +
                        "在卡牌详情页把这篇标成「已读」就会计入综合力。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                // 奖励道具
                val rewardBoxes = episode.rewardResourceBoxIds
                if (rewardBoxes.isNotEmpty()) {
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    KeyValueRow("奖励组 id", rewardBoxes.joinToString("、"))
                    Text(
                        text = "奖励具体是什么道具需要 `resourceBoxes.json`，这张表不在当前数据包里。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ── 开放要花什么 ──
            if (episode.costs.isNotEmpty()) {
                SectionTitle("开放消耗")
                InfoCard {
                    episode.costs.forEach { cost ->
                        Row(
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AsyncImage(
                                model = AssetUrls.materialIcon(region, cost.resourceId),
                                contentDescription = null,
                                modifier = Modifier.size(32.dp),
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = materials[cost.resourceId] ?: "素材 #${cost.resourceId}",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                cost.resourceType?.let {
                                    Text(
                                        text = it,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Text(
                                text = "× ${"%,d".format(cost.quantity)}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                        RowDivider()
                    }
                    Text(
                        text = "素材图标按素材 id 取自 storage.sekai.best（`thumbnail/material/material<id>.webp`），" +
                            "名字来自随快照内置的 `materials.json`。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }

            // ── 正文 ──
            SectionTitle("剧情正文")
            InfoCard {
                Text(
                    text = "本工具暂不提供剧情正文。",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "正文不在 master data 里：剧情的剧本资源 id 是「${episode.scenarioId ?: "（无）"}」，" +
                        "要显示正文需要另外下载并解析剧本资源，属于后续功能。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        Toast.makeText(
                            context,
                            "剧情正文阅读器还没做，先把数据接口留好了",
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                ) {
                    Text("去游戏里看")
                }
                Text(
                    text = "提示：在游戏内「卡牌 → 这张卡 → 剧情」即可阅读，读完记得回上一页把「已读」打开，" +
                        "综合力就会按实际数值计算。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * 信息分组的容器。
 *
 * ⚠️ 和 `CardDetailScreen.kt` 里那个同名私有函数一样，**刻意不画框**。
 * 原来这里是 `Surface(color = surfaceVariant.copy(alpha = 0.4f))`，
 * 在近白的页面背景（`#F5FAF9`）上就是一块明显更暗的灰绿方块，
 * 用户反馈「暗色的框住那些东西感觉怪怪的」。现在内容直接落在背景上，
 * 分组交给 [SectionTitle] 的主色竖条，行间用 [RowDivider] 的细线。
 */
@Composable
private fun InfoCard(content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) { content() }
}
