package com.pjsk.toolbox.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.pjsk.toolbox.PjskApp
import com.pjsk.toolbox.data.card.Card
import com.pjsk.toolbox.data.card.displayNameFor
import com.pjsk.toolbox.data.home.HomeGacha
import com.pjsk.toolbox.data.remote.AssetUrls
import com.pjsk.toolbox.data.remote.ServerRegion
import com.pjsk.toolbox.data.story.StoryStatus
import com.pjsk.toolbox.ui.common.RowDivider

/**
 * 卡池。
 *
 * **列表与详情在同一个界面里**（内部用 `selected` 切换）：两者结构都很简单，
 * 而且从详情返回列表不该丢滚动位置 —— 用一个 `LazyColumn` 状态最省事，
 * 也省掉一条路由。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GachaScreen(
    region: ServerRegion,
    onBack: () -> Unit,
    onOpenCard: (Int) -> Unit,
) {
    val container = (LocalContext.current.applicationContext as PjskApp).container
    val gachas by container.homeRepository.gachas.collectAsState(initial = emptyList())
    val cards by container.cardRepository.cards.collectAsState()
    val nameLanguage by container.appSettings.nameLanguage.collectAsState()

    /**
     * 当前在看的卡池。
     *
     * 存的是 **id 而不是对象**：这样 `rememberSaveable` 直接就能存（Int 可塞进 Bundle），
     * 返回这一屏时也能恢复；而且卡池列表是异步加载的，用 id 记的话
     * 「列表晚一步到」也只是先显示列表、到了再显示详情，不会丢状态。
     *
     * ⚠️ 这两个都必须用 `rememberSaveable`：点卡池里的卡 → 卡牌详情 → 返回时，
     * `remember` 会把「选了哪个分类」和「在看哪个池」一起清掉（见 ui/common/Savers.kt）。
     */
    var selectedId by rememberSaveable { mutableStateOf<Int?>(null) }
    val characters by container.cardRepository.characters.collectAsState(initial = emptyList())
    var tagFilter by rememberSaveable { mutableStateOf(GachaTag.ALL) }
    val cardById = remember(cards) { cards.associateBy { it.id } }
    val characterNames = remember(characters) {
        characters.mapNotNull { it.nameJa?.takeIf { n -> n.isNotBlank() } }.toSet()
    }
    val tagMap = remember(gachas, cardById, characterNames) {
        val now = System.currentTimeMillis()
        gachas.associate { it.id to tagsOfGacha(it, cardById, characterNames, now) }
    }
    val visibleGachas = remember(gachas, tagMap, tagFilter) {
        if (tagFilter == GachaTag.ALL) gachas
        else gachas.filter { tagFilter in (tagMap[it.id] ?: emptySet()) }
    }

    val current = remember(gachas, selectedId) { gachas.firstOrNull { it.id == selectedId } }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(current?.name ?: "卡池") },
                navigationIcon = {
                    IconButton(
                        onClick = { if (selectedId != null) selectedId = null else onBack() },
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (current == null) {
                // ── 第 1 层：卡池列表 + 分类筛选 ──
                Column(modifier = Modifier.fillMaxSize()) {
                    // 分类筛选：按 `gachas.gachaType` 分。
                    // 实测 1001 个池的分布：ceil 754 / normal 223 / gift 17 / beginner 7。
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(GachaTag.entries.toList(), key = { it.name }) { tag ->
                            val count = remember(gachas, tagMap, tag) {
                                if (tag == GachaTag.ALL) gachas.size
                                else tagMap.values.count { tag in it }
                            }
                            FilterChip(
                                selected = tag == tagFilter,
                                onClick = { tagFilter = tag },
                                label = { Text("${tag.label} $count") },
                            )
                        }
                    }
                    RowDivider()
                    Text(
                        text = "共 ${visibleGachas.size} / ${gachas.size} 个卡池，按开始时间倒序",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    ) {
                        items(visibleGachas, key = { it.id }) { gacha ->
                            GachaRow(gacha = gacha, region = region, onClick = { selectedId = gacha.id })
                            RowDivider()
                        }
                    }
                }
            } else {
                // ── 第 2 层：卡池详情 + 池内卡牌网格 ──
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 120.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        Column {
                            AsyncImage(
                                model = AssetUrls.gachaLogo(region, current.assetbundleName),
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp)
                                    .clip(RoundedCornerShape(10.dp)),
                                contentScale = ContentScale.Fit,
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StoryStatusBadge(statusOfGacha(current))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "${shortDate(current.startAt)} ~ ${shortDate(current.endAt)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "卡池内容（${current.cardIds.size} 张）",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                    items(current.cardIds, key = { it }) { cardId ->
                        val card = cardById[cardId]
                        GachaCardCell(
                            card = card,
                            region = region,
                            fallbackId = cardId,
                            name = card?.displayNameFor(nameLanguage),
                            onClick = { onOpenCard(cardId) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * 卡池分类筛选。
 *
 * 口径用 `gachas.gachaType`（数据自带、1001 个池全覆盖）：
 * 天井池 754 / 普通池 223 / 赠送池 17 / 新手池 7。
 * 名字里的 `[復刻]`（260 个）、周年纪念、角色生日池这些是**另一套口径**，
 * 要按它们筛的话得解析名字，留到以后。
 */
private enum class GachaTag(val label: String) {
    ALL("全部"),
    LIMITED("限定"),
    PERMANENT("常驻"),
    RERUN("复刻"),
    BIRTHDAY("生日池"),
    ANNIVERSARY("纪念"),
    ONCE_ONLY("一回限定"),
    RUNNING("进行中"),
    ENDED("已结束"),
}

/**
 * 卡池里的卡是不是「限定」。
 *
 * 取值来自 `cardSupplies.json`（实测 7 行）：
 * 1 normal 常驻 / 2 birthday 生日 / 3 term_limited 期间限定 /
 * 4 colorful_festival_limited / 5 bloom_festival_limited /
 * 6 unit_event_limited / 7 collaboration_limited。
 * 也就是 **3~7 都是限定**，1、2 不是。
 */
private val LIMITED_SUPPLY_IDS = setOf(3, 4, 5, 6, 7)

/**
 * 算出一个卡池身上的标签。**一个池可以同时是「限定 + 复刻 + 进行中」**，
 * 所以这里返回集合，筛选时是「包含该标签」而不是互斥单选。
 */
private fun tagsOfGacha(
    gacha: HomeGacha,
    cardById: Map<Int, Card>,
    characterNames: Set<String>,
    now: Long,
): Set<GachaTag> {
    val tags = mutableSetOf<GachaTag>()
    val limited = gacha.cardIds.any { cardById[it]?.supplyId in LIMITED_SUPPLY_IDS }
    if (limited) tags += GachaTag.LIMITED else tags += GachaTag.PERMANENT
    val name = gacha.name
    if (name.contains("[復刻]")) tags += GachaTag.RERUN
    if (name.contains("[1回限定]")) tags += GachaTag.ONCE_ONLY
    if (name.contains("周年記念") || name.contains("フィナーレチャプター")) tags += GachaTag.ANNIVERSARY
    // 生日池：名字方括号里就是角色名（实测 `[桐谷遥]…` 这种，每个角色 6 个）
    Regex("^[\\[【]([^\\]】]+)[\\]】]").find(name)?.groupValues?.get(1)?.let { tag ->
        if (tag in characterNames) tags += GachaTag.BIRTHDAY
    }
    if (now in gacha.startAt until gacha.endAt) tags += GachaTag.RUNNING else tags += GachaTag.ENDED
    return tags
}
/** 卡池的进行状态（按日期算，和活动剧情同一套规则）。 */
private fun statusOfGacha(gacha: HomeGacha): StoryStatus {
    val now = System.currentTimeMillis()
    return when {
        now < gacha.startAt -> StoryStatus.UPCOMING
        now >= gacha.endAt -> StoryStatus.ENDED
        else -> StoryStatus.RUNNING
    }
}

/** `2026-09-21` 这种短日期。 */
private fun shortDate(millis: Long): String =
    java.time.Instant.ofEpochMilli(millis)
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalDate()
        .toString()

/** 状态小标（和剧情页/首页同一套配色）。 */
@Composable
private fun StoryStatusBadge(status: StoryStatus) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(
                when (status) {
                    StoryStatus.RUNNING -> MaterialTheme.colorScheme.primary
                    StoryStatus.UPCOMING -> MaterialTheme.colorScheme.tertiary
                    StoryStatus.ENDED -> MaterialTheme.colorScheme.surfaceVariant
                },
            )
            .padding(horizontal = 5.dp, vertical = 1.dp),
    ) {
        Text(
            text = status.label,
            style = MaterialTheme.typography.labelSmall,
            color = when (status) {
                StoryStatus.RUNNING -> MaterialTheme.colorScheme.onPrimary
                StoryStatus.UPCOMING -> MaterialTheme.colorScheme.onTertiary
                StoryStatus.ENDED -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}
@Composable
private fun GachaRow(gacha: HomeGacha, region: ServerRegion, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = AssetUrls.gachaLogo(region, gacha.assetbundleName),
            contentDescription = null,
            modifier = Modifier
                .width(96.dp)
                .height(54.dp)
                .clip(RoundedCornerShape(6.dp)),
            contentScale = ContentScale.Fit,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = gacha.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StoryStatusBadge(statusOfGacha(gacha))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "${shortDate(gacha.startAt)} ~ ${shortDate(gacha.endAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = "${gacha.cardIds.size} 张",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 卡池里的一张卡。
 *
 * 卡面用**列表用的预览档**（800px），点进去是卡牌详情 —— 和「图鉴」里的点击行为一致。
 * 卡池里的卡 id 理论上都能在卡表里找到；找不到时只显示编号，不假装有卡面。
 */
@Composable
private fun GachaCardCell(
    card: Card?,
    region: ServerRegion,
    fallbackId: Int,
    name: String?,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
    ) {
        if (card != null) {
            AsyncImage(
                model = AssetUrls.cardPreview(region, card.assetbundleName, trained = false),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.68f)
                    .clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Crop,
            )
        } else {
            // 卡池里有极少数卡不在卡表里（例如已下线的），给个占位而不是空白
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.68f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "#$fallbackId",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = name ?: "#$fallbackId",
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
