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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.pjsk.toolbox.data.remote.AssetUrls
import com.pjsk.toolbox.data.remote.ServerRegion
import com.pjsk.toolbox.data.story.StoryEntry
import com.pjsk.toolbox.data.story.StoryQuery
import com.pjsk.toolbox.data.story.StoryRow
import com.pjsk.toolbox.data.story.StorySort
import com.pjsk.toolbox.data.story.StoryStatus
import com.pjsk.toolbox.data.story.StoryType
import com.pjsk.toolbox.data.story.buildStoryUnitOptions
import com.pjsk.toolbox.data.story.filterStoryRows
import com.pjsk.toolbox.ui.common.EmptyState
import com.pjsk.toolbox.ui.common.RowDivider
import com.pjsk.toolbox.ui.common.StoryQuerySaver
import com.pjsk.toolbox.util.moveMatchesFirst

/**
 * 剧情列表（第 1 层）/ 分话列表（第 2 层）。
 *
 * **两层共用一个界面**：两者的结构完全一样（一列可点的条目），
 * 只是数据源不同（`rows` vs `entries`）—— 分成两个文件只会把顶栏、返回、加载态抄两遍。
 *
 * 用户明确的：
 *  - **默认展示活动剧情**；
 *  - **活动剧情**可以按「相关的团」筛选，并选最新/最早（2026-09-16 加）；
 *  - **主线剧情不加筛选**（用户明确）—— 它是按组合分章节的，没有"哪个团"这个维度。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StoryScreen(
    type: StoryType,
    /** 非 null = 第 2 层（某一活动/组合/特殊条目的分话列表）。 */
    parentId: String?,
    /** 第 2 层的标题（从上一步带过来，免得再查一次）。 */
    parentTitle: String?,
    region: ServerRegion,
    onOpenType: (StoryType) -> Unit,
    onOpenRow: (StoryRow) -> Unit,
    onOpenEntry: (StoryEntry) -> Unit,
    onBack: () -> Unit,
) {
    val container = (LocalContext.current.applicationContext as PjskApp).container
    val repository = container.storyRepository

    var loading by remember(type, parentId) { mutableStateOf(true) }
    var rows by remember(type, parentId) { mutableStateOf<List<StoryRow>>(emptyList()) }
    var entries by remember(type, parentId) { mutableStateOf<List<StoryEntry>>(emptyList()) }
    var error by remember(type, parentId) { mutableStateOf<String?>(null) }

    // 活动剧情的筛选（按团）+ 排序。**用 rememberSaveable**：进第 2 层再返回不能丢（§11.13 的约定）
    var query by rememberSaveable(stateSaver = StoryQuerySaver) { mutableStateOf(StoryQuery()) }
    // 面板开合只是临时状态，返回时不该自己弹出来
    var showFilter by remember { mutableStateOf(false) }

    LaunchedEffect(type, parentId, region) {
        loading = true
        error = null
        runCatching {
            if (parentId == null) {
                rows = repository.rows(type, region)
            } else {
                entries = repository.entries(type, parentId)
            }
        }.onFailure { error = it.message ?: "加载失败" }
        loading = false
    }

    val isSecondLevel = parentId != null
    // 只有「活动剧情」有团的概念（主线是按组合分章节、卡牌剧情没有团）——
    // 所以筛选只在这一类上出现（不放点了没反应的按钮，用户也明确说主线不用加）。
    val filterable = !isSecondLevel && type == StoryType.EVENT

    // 数据驱动的团选项：只列数据里真的有的团。虚拟歌手没有活动剧情 → 不会出现在这里
    val unitOptions = remember(rows, filterable) {
        if (filterable) buildStoryUnitOptions(rows) else emptyList()
    }
    val visibleRows = remember(rows, query, filterable) {
        if (filterable) filterStoryRows(rows, query) else rows
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isSecondLevel) (parentTitle ?: type.label) else type.label) },
                // ⚠️ 只有**第 2 层起**才有返回箭头。
                // 第 1 层（`story` 这个顶级板块）是底部导航直达的，出现"返回到哪"
                // 的箭头会让人以为还有上一层，而实际上它和卡牌/歌曲是同一级。
                navigationIcon = {
                    if (isSecondLevel) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                },
                actions = {
                    // 顶栏右侧留给**各分类自己的筛选**（用户要求）。
                    // 目前只有活动剧情有得筛（按团 + 最新/最早）。
                    if (filterable && unitOptions.isNotEmpty()) {
                        IconButton(onClick = { showFilter = true }) {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = "筛选",
                                tint = if (query.isFiltering) {
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
            // 分类切换器：和歌曲页顶部同一种样式（Tab 排），一眼看出现在在哪个分类。
            //
            // ⚠️ **卡牌剧情在这里刻意不列**（用户要求的 a 方案）：分类里不显示，
            // 但 `StoryType.CARD` 的路由保留着 —— 卡牌详情点某一篇剧情时仍然能进阅读器。
            //
            // 用 Tab 而不是 chip：chip 表达的是"可叠加的筛选条件"，而这里是**互斥的分类**，
            // Tab 才是对的语义（用户要求对齐歌曲页）。
            if (!isSecondLevel) {
                TabRow(selectedTabIndex = CHIP_TYPES.indexOf(type).coerceAtLeast(0)) {
                    CHIP_TYPES.forEach { candidate ->
                        Tab(
                            selected = candidate == type,
                            onClick = { if (candidate != type) onOpenType(candidate) },
                            text = { Text(candidate.label) },
                        )
                    }
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                error != null -> EmptyState(
                    title = "加载失败",
                    description = error ?: "",
                )


                isSecondLevel && entries.isEmpty() -> EmptyState(
                    title = "这里没有可读的剧情",
                    description = "这张卡或这个活动可能还没有剧情数据。",
                )

                !isSecondLevel && rows.isEmpty() -> EmptyState(
                    title = "还没有${type.label}数据",
                    description = "数据会在导入内置快照后出现。",
                )

                // 筛出空来了：说清楚是"筛选"造成的，不然看起来像数据没了
                !isSecondLevel && visibleRows.isEmpty() -> EmptyState(
                    title = "没有符合条件的活动剧情",
                    description = "换个团，或者点筛选面板里的「重置」。",
                )

                isSecondLevel -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    items(entries, key = { it.scenarioId }) { entry ->
                        EntryRow(entry = entry, region = region, onClick = { onOpenEntry(entry) })
                        RowDivider()
                    }
                }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    items(visibleRows, key = { "${type.key}-${it.id}" }) { row ->
                        StoryRowItem(row = row, onClick = { onOpenRow(row) })
                        RowDivider()
                    }
                }
            }
            }
        }
    }

    /*
     * ── 活动剧情的筛选面板 ──
     *
     * 只有「活动剧情」有这一项（主线按组合分章节、卡牌剧情没有团的概念），
     * 而且**只在数据里真的有团的时候**才给按钮 —— 不放点了没反应的按钮。
     *
     * 团列表是数据驱动的：数据里没有 `piapro`（虚拟歌手），所以那个 chip 不会出现；
     * 以后官方给它加了活动剧情，这里会自动冒出来，不用改代码。
     */
    if (showFilter && filterable) {
        ModalBottomSheet(
            onDismissRequest = { showFilter = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("筛选", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { query = query.cleared() }) { Text("重置") }
                }

                FilterGroupTitle("相关的团（一个活动可能和多个团相关）")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    // 选中的浮到该组最前（和歌曲页/卡牌页同一条约定）
                    unitOptions.moveMatchesFirst { it.key in query.units }.forEach { option ->
                        FilterChip(
                            selected = option.key in query.units,
                            onClick = {
                                query = query.copy(
                                    units = if (option.key in query.units) {
                                        query.units - option.key
                                    } else {
                                        query.units + option.key
                                    },
                                )
                            },
                            label = { Text("${option.label} ${option.count}", maxLines = 1) },
                        )
                    }
                }

                FilterGroupTitle("排序")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StorySort.entries.forEach { sort ->
                        FilterChip(
                            selected = query.sort == sort,
                            onClick = { query = query.copy(sort = sort) },
                            label = { Text(sort.label) },
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = { showFilter = false },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (query.isFiltering) {
                            "显示 ${visibleRows.size} / ${rows.size} 个活动"
                        } else {
                            "显示全部 ${rows.size} 个活动"
                        },
                    )
                }
            }
        }
    }
}

/** 筛选面板里的小标题（和歌曲页那个同款）。 */
@Composable
private fun FilterGroupTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
    )
}

/**
 * 分类切换里只显示这两类（用户要求）。
 *
 * **卡牌剧情**不在这里列，但 `StoryType.CARD` 的路由**保留着**：
 * 卡牌详情里的「卡牌剧情」板块（前篇 / 后篇）照常显示，点进去仍然能进阅读器。
 * 区域对话 / 特殊剧情则整个从界面上撤掉。
 */
private val CHIP_TYPES = listOf(StoryType.UNIT, StoryType.EVENT)

/** 第 1 层的一行：缩略图 + 标题 + 副标题 + 右侧小字。 */
@Composable
private fun StoryRowItem(row: StoryRow, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (row.imageUrl != null) {
            AsyncImage(
                model = row.imageUrl,
                contentDescription = null,
                modifier = Modifier
                    .width(84.dp)
                    .height(48.dp)
                    .clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Fit,
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            row.subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        row.trailing?.let {
            Spacer(Modifier.width(8.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // 活动的「进行中 / 已结束」小标（用户要求，对齐参考站的活动剧情列表）
        row.status?.let { status ->
            Spacer(Modifier.width(6.dp))
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
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 第 2 层的一行：标签 + 标题 +（区域对话）出场角色头像。 */
@Composable
private fun EntryRow(
    entry: StoryEntry,
    region: ServerRegion,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            entry.label?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(text = entry.title, style = MaterialTheme.typography.bodyLarge)
        }
        // 出场角色头像：圆形 + **顶部对齐**（`chr_tl_` 是 376×840 的竖长半身图，
        // 居中裁切会正好切到胸口，看不出是谁）
        if (entry.characterIds.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
                entry.characterIds.take(4).forEach { charId ->
                    AsyncImage(
                        model = AssetUrls.characterAvatar(region, charId),
                        contentDescription = null,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.TopCenter,
                    )
                }
            }
            if (entry.characterIds.size > 4) {
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "+${entry.characterIds.size - 4}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
