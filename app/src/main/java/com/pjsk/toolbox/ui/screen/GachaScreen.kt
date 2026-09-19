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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.pjsk.toolbox.PjskApp
import com.pjsk.toolbox.data.card.Card
import com.pjsk.toolbox.data.card.displayNameFor
import com.pjsk.toolbox.data.card.supplyType
import com.pjsk.toolbox.data.home.GachaTag
import com.pjsk.toolbox.data.home.HomeGacha
import com.pjsk.toolbox.data.home.gachaFocusCardIds
import com.pjsk.toolbox.data.home.gachaRestCardIds
import com.pjsk.toolbox.data.home.gachaTagsOf
import com.pjsk.toolbox.data.home.rowTagsOf
import com.pjsk.toolbox.data.home.statusOfGacha
import com.pjsk.toolbox.data.remote.AssetUrls
import com.pjsk.toolbox.data.remote.ServerRegion
import com.pjsk.toolbox.data.story.StoryStatus
import com.pjsk.toolbox.ui.common.RowDivider
import com.pjsk.toolbox.ui.common.TagChip
import com.pjsk.toolbox.ui.common.TagTone

/**
 * 卡池。
 *
 * **列表与详情在同一个界面里**（内部用 `selected` 切换）：两者结构都很简单，
 * 而且从详情返回列表不该丢滚动位置 —— 用一个 `LazyColumn` 状态最省事，
 * 也省掉一条路由。
 *
 * ── 池内卡牌的排布（2026-09-19 改）──
 *
 * 原来就是把 `gachas.gc` 原样铺出来，而这个字段实测 **1002 / 1004 个池就是卡 id 升序**，
 * 于是每个池子打开都是 `#2 #3 #4 #6 #7 #10…`，几十个池子长得一模一样（用户原话：
 * 「好几个卡池里都按顺序排的，感觉差异化不大」）。
 *
 * 现在分两段：
 *  - **重点卡**（`UP + 本池首发`）铺在最前，带角标。池内卡数中位 150 张，而重点卡中位
 *    只有 3 张（978 / 1004 个池 ≤10 张）—— 这才是这个池子区别于别的池子的部分；
 *  - **其余卡**（往期池早就出现过的老卡，占 97%）默认**折叠**成一行，点开才铺。
 *    折叠不是隐藏数据：那一行必须写明张数（「其余 146 张」），
 *    不然用户会以为池子里就 3 张卡。
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
    // 限定卡的 id 集合：池子的「限定」标签要按**池里有没有限定卡**算，
    // 口径来自 cardSupplies（见 CardSupplyType），不是按名字猜的。
    val limitedCardIds = remember(cards) {
        cards.filter { it.supplyType?.limited == true }.mapTo(HashSet()) { it.id }
    }
    val characterNames = remember(characters) {
        characters.mapNotNull { it.nameJa?.takeIf { n -> n.isNotBlank() } }.toSet()
    }
    val tagMap = remember(gachas, limitedCardIds, characterNames) {
        val now = System.currentTimeMillis()
        gachas.associate { it.id to gachaTagsOf(it, { id -> id in limitedCardIds }, characterNames, now) }
    }
    val visibleGachas = remember(gachas, tagMap, tagFilter) {
        if (tagFilter == GachaTag.ALL) gachas
        else gachas.filter { tagFilter in (tagMap[it.id] ?: emptySet()) }
    }

    val current = remember(gachas, selectedId) { gachas.firstOrNull { it.id == selectedId } }
    // 「其余卡」的展开状态：**刻意用 remember（不是 rememberSaveable）**——
    // 它是纯浏览态，换个池子或从卡牌详情返回时收起才是合理的默认。
    var restExpanded by remember(current?.id) { mutableStateOf(false) }
    val focusIds = remember(current, cardById) {
        current?.let { gacha ->
            gachaFocusCardIds(
                pool = gacha.cardIds,
                upCardIds = gacha.upCardIds,
                debutCardIds = gacha.debutCardIds,
                releaseAtOf = { id -> cardById[id]?.releaseAt ?: Long.MIN_VALUE },
            )
        }.orEmpty()
    }
    val restIds = remember(current, focusIds, cardById) {
        current?.let { gacha ->
            gachaRestCardIds(
                pool = gacha.cardIds,
                focusCardIds = focusIds,
                releaseAtOf = { id -> cardById[id]?.releaseAt ?: Long.MIN_VALUE },
            )
        }.orEmpty()
    }
    val upIds = remember(current) { current?.upCardIds.orEmpty().toHashSet() }

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
                    // 实测 1004 个池的分布：ceil 757 / normal 223 / gift 17 / beginner 7。
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
                            GachaRow(
                                gacha = gacha,
                                region = region,
                                tags = rowTagsOf(tagMap[gacha.id] ?: emptySet()),
                                onClick = { selectedId = gacha.id },
                            )
                            RowDivider()
                        }
                    }
                }
            } else {
                // ── 第 2 层：卡池详情 = 重点卡 + 折叠的其余卡 ──
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 120.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
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
                            val tags = rowTagsOf(tagMap[current.id] ?: emptySet())
                            if (tags.isNotEmpty()) {
                                Spacer(Modifier.height(6.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    tags.forEach { tag -> GachaTagChip(tag) }
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "本池重点 ${focusIds.size} 张 · 池内共 ${current.cardIds.size} 张",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "重点 = 卡池 UP（官方 pickups）+ 本池首发（这张卡第一次能抽到就是在这里）",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                    }

                    items(focusIds, key = { it }) { cardId ->
                        GachaCardCell(
                            card = cardById[cardId],
                            region = region,
                            fallbackId = cardId,
                            name = cardById[cardId]?.displayNameFor(nameLanguage),
                            up = cardId in upIds,
                            debut = cardId in current.debutCardIds,
                            onClick = { onOpenCard(cardId) },
                        )
                    }

                    // 「其余卡」：折叠成一行。往期池早就出现过的老卡占池内 97%，
                    // 一屏铺不完、而且每个池子都长这样，所以默认收起。
                    if (restIds.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Column {
                                Spacer(Modifier.height(6.dp))
                                RowDivider()
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { restExpanded = !restExpanded }
                                        .padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "其余 ${restIds.size} 张（往期卡池已出现）",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        text = if (restExpanded) "收起" else "展开",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.rotate(if (restExpanded) 180f else 0f),
                                    )
                                }
                                if (restExpanded) Spacer(Modifier.height(4.dp))
                            }
                        }
                    }
                    if (restExpanded) {
                        items(restIds, key = { it }) { cardId ->
                            GachaCardCell(
                                card = cardById[cardId],
                                region = region,
                                fallbackId = cardId,
                                name = cardById[cardId]?.displayNameFor(nameLanguage),
                                up = false,
                                debut = false,
                                onClick = { onOpenCard(cardId) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 列表行上的一枚卡池标签。
 *
 * 口径见 `data/home/GachaTags.kt`：限定是「池内有没有限定卡」，复刻是名字带 `[復刻]`，
 * 生日池看名字开头的角色名，纪念看周年/完结章。配色按重要程度分（见 TagChip）：
 * 限定用 primary，其余（复刻 / 生日池 / 纪念 / 一回限定）用 secondaryContainer ——
 * 能被看见，但不跟「限定」抢眼。
 */
@Composable
private fun GachaTagChip(tag: GachaTag) {
    TagChip(
        text = tag.label,
        tone = if (tag == GachaTag.LIMITED) TagTone.ACCENT else TagTone.MUTED,
    )
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
private fun GachaRow(
    gacha: HomeGacha,
    region: ServerRegion,
    tags: List<GachaTag>,
    onClick: () -> Unit,
) {
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
            // 标签小签：以前这些标签**只用来筛选**，行上什么都不显示，
            // 于是「哪个池是复刻的」在列表里根本看不出来。
            if (tags.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    tags.forEach { tag -> GachaTagChip(tag) }
                }
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
 *
 * 左上角挂角标（右上要留给属性图标的位置已被卡面本身的构图占掉，左上是空的）：
 *  - `UP`：这个池子的官方 pickups，也就是池子在卖的那几张；
 *  - `首发`：这张卡第一次能在卡池里抽到就是在本池。
 * 两者可以同时成立，所以是「最多两枚」而不是互斥的一枚。
 */
@Composable
private fun GachaCardCell(
    card: Card?,
    region: ServerRegion,
    fallbackId: Int,
    name: String?,
    up: Boolean,
    debut: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(1.68f)) {
            if (card != null) {
                AsyncImage(
                    model = AssetUrls.cardPreview(region, card.assetbundleName, trained = false),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(6.dp)),
                    contentScale = ContentScale.Crop,
                )
            } else {
                // 卡池里有极少数卡不在卡表里（例如已下线的），给个占位而不是空白
                Box(
                    modifier = Modifier
                        .fillMaxSize()
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
            if (up || debut) {
                Row(
                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (up) TagChip(text = "UP", tone = TagTone.HOT)
                    if (debut) TagChip(text = "首发", tone = TagTone.MUTED)
                }
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
