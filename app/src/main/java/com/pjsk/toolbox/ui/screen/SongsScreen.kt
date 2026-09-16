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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
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
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pjsk.toolbox.PjskApp
import com.pjsk.toolbox.data.card.PjskCharacter
import com.pjsk.toolbox.data.card.displayNameFor
import com.pjsk.toolbox.data.home.HomeMusic
import com.pjsk.toolbox.data.music.MusicCategory
import com.pjsk.toolbox.data.music.MusicUnitTag
import com.pjsk.toolbox.data.music.SongCategoryCard
import com.pjsk.toolbox.data.music.SongQuery
import com.pjsk.toolbox.data.music.SongSort
import com.pjsk.toolbox.data.music.buildSongCategoryCards
import com.pjsk.toolbox.data.music.filterSongs
import com.pjsk.toolbox.data.player.playFromList
import com.pjsk.toolbox.data.player.toTrack
import com.pjsk.toolbox.data.remote.AssetUrls
import com.pjsk.toolbox.data.remote.ServerRegion
import com.pjsk.toolbox.data.settings.NameLanguage
import com.pjsk.toolbox.ui.common.EmptyState
import com.pjsk.toolbox.ui.common.RemoteImage
import com.pjsk.toolbox.ui.common.RowDivider
import com.pjsk.toolbox.ui.common.SongListColumn
import com.pjsk.toolbox.ui.common.SongQuerySaver
import com.pjsk.toolbox.util.moveMatchesFirst
import com.pjsk.toolbox.util.parseHexColor
import kotlinx.coroutines.launch

/**
 * 歌曲板块。
 *
 * ## 结构（照留声器）
 *
 * 顶部三个 Tab —— **歌曲 / 分类 / 人物** —— 下面那块用 `HorizontalPager`，
 * **只有这块区域能左右滑**，页面其余部分不受影响。
 *
 *  - **歌曲**：列表。顶栏 🔍 展开搜索，⚙ 打开**底部弹层**筛选（和卡牌图鉴同构）
 *  - **分类**：两列卡片网格（＝留声器的 Albums，内容用游戏内选曲界面的 7 个分类）
 *  - **人物**：游戏内角色的列表
 *
 * ## 两个"点进去"是**独立页面**，不是切回歌曲 Tab 去筛选
 *
 * 用户明确要求：点分类卡 / 点角色 → push 一个**专门展示这一类（人）的歌**的页面
 * （`songs/category/{tag}`、`songs/character/{id}`）。它们不复用「歌曲」Tab，
 * 只是内部共用同一个行组件（[SongListColumn]），免得三个列表长得不一样。
 *
 * ## 没有返回箭头
 *
 * 这是底部导航直达的顶级板块。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongsScreen(
    region: ServerRegion,
    onOpenDetail: (Int) -> Unit = {},
    onOpenCategory: (String) -> Unit = {},
    onOpenCharacter: (Int) -> Unit = {},
) {
    val container = (LocalContext.current.applicationContext as PjskApp).container
    val musics by container.homeRepository.musics.collectAsState(initial = emptyList())
    val unitColors by container.musicRepository.unitColors.collectAsState(initial = emptyMap())
    val characters by container.cardRepository.characters.collectAsState(initial = emptyList())
    val characterSongIds by container.musicRepository.characterSongIds.collectAsState(initial = emptyMap())
    val nameLanguage by container.appSettings.nameLanguage.collectAsState()
    // 收藏：musicId -> 收藏时间（收藏页要按"最近收藏在前"排序）
    val favorites by container.appSettings.favoriteSongs.collectAsState()
    val player = container.musicPlayer
    // 列表行要知道"现在播的是哪首"才能把 ▶ 换成 ⏸
    val playback by player.state.collectAsState()

    // 筛选条件用 rememberSaveable：跳到歌曲详情再回来时不能丢（见 ui/common/Savers.kt）
    var query by rememberSaveable(stateSaver = SongQuerySaver) { mutableStateOf(SongQuery()) }
    // 当前 Tab 也用 rememberSaveable：从详情页返回时还停在原来那一栏
    var tabName by rememberSaveable { mutableStateOf(SongTab.SONGS.name) }
    val initialTab = SongTab.entries.firstOrNull { it.name == tabName } ?: SongTab.SONGS

    // 纯临时状态：搜索框展开、筛选弹层开合 —— 返回时不该自己弹出来
    var searchOpen by remember { mutableStateOf(false) }
    var showFilter by remember { mutableStateOf(false) }

    val visible = remember(musics, query) { filterSongs(musics, query) }
    val cards = remember(musics) { buildSongCategoryCards(musics) }
    val songCounts = remember(characterSongIds) { characterSongIds.mapValues { it.value.size } }

    val pagerState = rememberPagerState(initialPage = initialTab.ordinal) { SongTab.entries.size }
    val scope = rememberCoroutineScope()

    // 滑动切页后把当前页同步回可保存的状态（这样从详情页返回时还停在原来那一栏）
    LaunchedEffect(pagerState.currentPage) {
        val target = SongTab.entries[pagerState.currentPage]
        if (target.name != tabName) tabName = target.name
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (searchOpen) {
                        TextField(
                            value = query.search,
                            onValueChange = { query = query.copy(search = it) },
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
                            Text("歌曲", style = MaterialTheme.typography.titleLarge)
                            Text(
                                text = if (query.isFiltering) {
                                    "筛选出 ${visible.size} / ${musics.size} 首"
                                } else {
                                    "共 ${musics.size} 首"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (searchOpen) {
                                // 关掉搜索框时必须一起清空搜索词，
                                // 否则列表会被一个看不见的条件筛着，用户完全莫名其妙
                                searchOpen = false
                                query = query.copy(search = "")
                            } else {
                                searchOpen = true
                            }
                        },
                    ) {
                        Icon(
                            imageVector = if (searchOpen) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = "搜索",
                            tint = if (query.search.isNotBlank()) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    IconButton(onClick = { showFilter = true }) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = "筛选",
                            tint = if (query.activeCount > 0) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = pagerState.currentPage) {
                SongTab.entries.forEachIndexed { index, item ->
                    Tab(
                        selected = index == pagerState.currentPage,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        text = { Text(item.label) },
                    )
                }
            }

            // 只有这一块能左右滑（页面其余部分不受影响）
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                when (SongTab.entries[page]) {
                    SongTab.SONGS -> SongListColumn(
                        musics = visible,
                        region = region,
                        unitColors = unitColors,
                        favoriteIds = favorites.keys,
                        emptyTitle = "没有符合条件的歌曲",
                        emptyDescription = "换个筛选条件，或者清空筛选。",
                        onToggleFavorite = { container.appSettings.toggleFavoriteSong(it) },
                        // 整份列表入队、从点的那首开始；点的是正在播的那首就是暂停/继续
                        onPlay = { music -> player.playFromList(visible, music.id) },
                        onOpenDetail = onOpenDetail,
                        playingMusicId = playback.track?.musicId,
                        isPlayRequested = playback.isPlayRequested,
                    )

                    SongTab.CATEGORIES -> CategoryGrid(
                        cards = cards,
                        region = region,
                        unitColors = unitColors,
                        onClick = { onOpenCategory(it.key) },
                    )

                    SongTab.ARTISTS -> CharacterList(
                        characters = characters,
                        region = region,
                        songCounts = songCounts,
                        nameLanguage = nameLanguage,
                        onClick = onOpenCharacter,
                    )

                    SongTab.FAVORITES -> {
                        // 按**收藏时间**倒序：刚点的那颗星立刻在第一个，
                        // 而不是按发布时间排（那样新收藏的老歌会沉到下面，找不到）
                        val favoriteMusics = remember(musics, favorites) {
                            musics.filter { it.id in favorites }
                                .sortedByDescending { favorites[it.id] ?: 0L }
                        }
                        SongListColumn(
                            musics = favoriteMusics,
                            region = region,
                            unitColors = unitColors,
                            favoriteIds = favorites.keys,
                            emptyTitle = "还没有收藏的歌曲",
                            emptyDescription = "在歌曲列表点右侧的 ☆ 就能收藏；收藏只存在本机。",
                            onToggleFavorite = { container.appSettings.toggleFavoriteSong(it) },
                            onPlay = { music -> player.playFromList(favoriteMusics, music.id) },
                            onOpenDetail = onOpenDetail,
                            playingMusicId = playback.track?.musicId,
                            isPlayRequested = playback.isPlayRequested,
                        )
                    }
                }
            }
        }
    }

    if (showFilter) {
        SongFilterSheet(
            query = query,
            musics = musics,
            matchedCount = visible.size,
            onChange = { query = it },
            onDismiss = { showFilter = false },
        )
    }
}

/** 歌曲板块内部的四个 Tab。内容区可左右滑，Tab 也可点。 */
private enum class SongTab(val label: String) {
    SONGS("歌曲"),
    CATEGORIES("分类"),
    ARTISTS("人物"),
    FAVORITES("收藏"),
}

// ─────────────────────────────────────────────────────────────
// 分类（＝留声器的 Albums）
// ─────────────────────────────────────────────────────────────

@Composable
private fun CategoryGrid(
    cards: List<SongCategoryCard>,
    region: ServerRegion,
    unitColors: Map<String, String>,
    onClick: (MusicUnitTag) -> Unit,
) {
    if (cards.isEmpty()) {
        EmptyState(title = "还没有歌曲数据", description = "去「更多 → 数据同步」拉一次数据。")
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(cards, key = { it.tag.key }) { card ->
            CategoryCard(
                card = card,
                region = region,
                accent = card.tag.unitProfileKey
                    ?.let { unitColors[it] }
                    ?.let { parseHexColor(it) },
                onClick = { onClick(card.tag) },
            )
        }
    }
}

/**
 * 一张「专辑」卡。点它 push 一个**独立页面**显示该分类的歌（不切 Tab 去筛选）。
 *
 * 卡面用**该分类里最新那首歌的曲绘**：内置组合 logo 只有 60×60（实测），
 * 铺进两列网格会被放大到糊，素材桶里也没有更大的，所以 logo 缩到 24dp 当角标。
 */
@Composable
private fun CategoryCard(
    card: SongCategoryCard,
    region: ServerRegion,
    accent: Color?,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            RemoteImage(
                url = card.jacketAssetbundleName?.let { AssetUrls.musicJacket(region, it) },
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            // 底部渐变：让白字在任何封面上都读得出来
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.45f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.72f),
                        ),
                    ),
            )
            AssetUrls.unitLogo(card.tag.unitProfileKey)?.let { logo ->
                RemoteImage(
                    url = logo,
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .size(24.dp),
                    contentScale = ContentScale.Fit,
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp),
            ) {
                Text(
                    text = card.tag.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${card.songCount} 首",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
        }
        if (accent != null && accent != Color.White) {
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accent),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
// 人物（＝留声器的 Artists，内容用游戏内角色）
// ─────────────────────────────────────────────────────────────

@Composable
private fun CharacterList(
    characters: List<PjskCharacter>,
    region: ServerRegion,
    songCounts: Map<Int, Int>,
    nameLanguage: NameLanguage,
    onClick: (Int) -> Unit,
) {
    // 只列**真的唱过歌**的角色，避免出现点进去是空页的条目；按曲数从多到少排
    val listed = remember(characters, songCounts) {
        characters.filter { (songCounts[it.id] ?: 0) > 0 }
            .sortedByDescending { songCounts[it.id] ?: 0 }
    }
    if (listed.isEmpty()) {
        EmptyState(title = "还没有演唱数据", description = "去「更多 → 数据同步」拉一次数据。")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
    ) {
        items(listed, key = { it.id }) { character ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onClick(character.id) }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RemoteImage(
                    url = AssetUrls.characterAvatar(region, character.id),
                    contentDescription = null,
                    // 半身像是 376×840 竖构图，圆形头像必须顶部对齐，否则是胸口不是脸
                    modifier = Modifier.size(44.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = character.displayNameFor(nameLanguage),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    character.unitLabel?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    text = "${songCounts[character.id] ?: 0} 首",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            RowDivider()
        }
    }
}

// ─────────────────────────────────────────────────────────────
// 筛选：底部弹层（和卡牌图鉴同构）
// ─────────────────────────────────────────────────────────────

/**
 * 筛选面板。
 *
 * 和卡牌图鉴**用同一种形态**（顶栏 ⚙ → 底部弹层）：两处交互不一致的话，
 * 用户每换一个板块都要重新找一遍筛选在哪。
 *
 * 搜索框**不在这里**重复放一份：它已经在顶栏了，同一个条件两个入口
 * 只会让人怀疑哪个生效。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SongFilterSheet(
    query: SongQuery,
    musics: List<HomeMusic>,
    matchedCount: Int,
    onChange: (SongQuery) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
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
                TextButton(onClick = { onChange(query.cleared()) }) { Text("重置") }
            }

            FilterGroupTitle("组合（游戏内选曲界面的分类）")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                // 选中的排到前面：点了中间某个之后它还夹在一排未选中里，扫一眼看不出选了什么
                MusicUnitTag.ordered
                    .filter { tag -> musics.any { tag.key in it.unitTags } }
                    .moveMatchesFirst { tag -> tag.key in query.units }
                    .forEach { tag ->
                        val count = musics.count { tag.key in it.unitTags }
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
                            label = { Text("${tag.label} $count", maxLines = 1) },
                        )
                    }
            }

            FilterGroupTitle("分类（演出形式）")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                MusicCategory.entries
                    .filter { category -> musics.any { category.key in it.categories } }
                    .moveMatchesFirst { category -> category.key in query.categories }
                    .forEach { category ->
                        val count = musics.count { category.key in it.categories }
                        FilterChip(
                            selected = category.key in query.categories,
                            onClick = {
                                onChange(
                                    query.copy(
                                        categories = if (category.key in query.categories) {
                                            query.categories - category.key
                                        } else {
                                            query.categories + category.key
                                        },
                                    ),
                                )
                            },
                            label = { Text("${category.label} $count", maxLines = 1) },
                        )
                    }
            }

            FilterGroupTitle("排序")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SongSort.entries.forEach { sort ->
                    FilterChip(
                        selected = query.sort == sort,
                        onClick = { onChange(query.copy(sort = sort)) },
                        label = { Text(sort.label) },
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (query.isFiltering) "显示 $matchedCount / ${musics.size} 首"
                    else "显示全部 ${musics.size} 首",
                )
            }
        }
    }
}

@Composable
private fun FilterGroupTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
    )
}
