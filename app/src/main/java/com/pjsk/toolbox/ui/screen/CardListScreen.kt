package com.pjsk.toolbox.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.pjsk.toolbox.PjskApp
import com.pjsk.toolbox.data.card.Card
import com.pjsk.toolbox.data.card.CardArtPlan
import com.pjsk.toolbox.data.card.CardAttr
import com.pjsk.toolbox.data.card.CardQuery
import com.pjsk.toolbox.data.card.CardSort
import com.pjsk.toolbox.data.card.CardSupplyType
import com.pjsk.toolbox.data.card.CardUnit
import com.pjsk.toolbox.data.card.FALLBACK_RARITY_CAPS
import com.pjsk.toolbox.data.card.PjskCharacter
import com.pjsk.toolbox.data.card.displayNameFor
import com.pjsk.toolbox.data.card.filterCards
import com.pjsk.toolbox.data.card.listTagLabel
import com.pjsk.toolbox.data.card.maxTotal
import com.pjsk.toolbox.data.card.secondaryNameFor
import com.pjsk.toolbox.data.card.starsOf
import com.pjsk.toolbox.data.card.supplyType
import com.pjsk.toolbox.data.remote.AssetUrls
import com.pjsk.toolbox.data.remote.ServerRegion
import com.pjsk.toolbox.data.settings.NameLanguage
import com.pjsk.toolbox.ui.common.AttrIcon
import com.pjsk.toolbox.ui.common.CardArtImage
import com.pjsk.toolbox.ui.common.CardArtPlaceholder
import com.pjsk.toolbox.ui.common.CardListSkeleton
import com.pjsk.toolbox.ui.common.CardQuerySaver
import com.pjsk.toolbox.ui.common.EmptyState
import com.pjsk.toolbox.ui.common.PrefetchCardImages
import com.pjsk.toolbox.ui.common.TagChip
import com.pjsk.toolbox.ui.common.TagTone
import com.pjsk.toolbox.ui.common.listArtUrls
import com.pjsk.toolbox.ui.common.rememberEmptyConfirmed
import com.pjsk.toolbox.util.moveMatchesFirst

/** 稀有度筛选项。`rarity_birthday` 单独列出来，因为它没有数字后缀。 */
private val RARITY_OPTIONS: List<Pair<String, String>> = listOf(
    "rarity_4" to "★4",
    "rarity_3" to "★3",
    "rarity_2" to "★2",
    "rarity_1" to "★1",
    "rarity_birthday" to "生日",
)

/**
 * 卡牌图鉴。
 *
 * 排版照 sekai.best 的默认（`grid`）视图：一个 **16:9 的图框**，里面**左右各占 50%** ——
 * 左半边未特训、右半边觉醒；不可特训的卡（1★/2★/生日卡）只有一张，整张占满。
 *
 * 手机上 sekai.best 的 `grid` 断点本身就是「一行一个格子」，所以这里直接用单列，
 * 与网页在手机上的观感一致。图框下方是卡名与角色名。
 *
 * 注意图片是 `object-fit: cover` 居中裁切：每半边显示的是卡面**正中的一条竖构图**，
 * 而不是完整卡面。这是 sekai.best 的做法，照搬过来。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardListScreen(
    region: ServerRegion,
    onOpenDetail: (Int) -> Unit,
    onOpenSync: () -> Unit,
    /**
     * 进来时预设筛选的角色（从歌曲详情点演唱者跳过来时用）。
     *
     * 只在**首次进入**时生效：`rememberSaveable` 的初始化 lambda 只跑一次，
     * 之后由保存状态接管 —— 所以用户在这里改了筛选、跳到卡牌详情再返回，
     * 改过的筛选项不会被这个预设值覆盖（这正是上一轮修的那个 bug）。
     */
    initialCharacterId: Int? = null,
) {
    val container = (LocalContext.current.applicationContext as PjskApp).container
    val repository = container.cardRepository

    // cards 现在是 StateFlow（仓库里用 stateIn 缓存了解析结果），所以不需要 initial
    val cards by repository.cards.collectAsState()
    val characters by repository.characters.collectAsState(initial = emptyList())
    val rarityCaps by repository.rarityCaps.collectAsState(initial = FALLBACK_RARITY_CAPS)
    val nameLanguage by container.appSettings.nameLanguage.collectAsState()

    // ⚠️ 必须是 rememberSaveable：跳到卡牌详情再返回时，`remember` 会丢（见 ui/common/Savers.kt）。
    // 初始化时若带了预设角色（从歌曲详情点演唱者过来），就先把这一维选上。
    var query by rememberSaveable(stateSaver = CardQuerySaver) {
        mutableStateOf(
            initialCharacterId?.let { CardQuery(characterIds = setOf(it)) } ?: CardQuery(),
        )
    }
    // 顶栏搜索框是否展开。**纯临时状态**：返回这一屏时不该自己弹出来，所以用 remember
    var searchOpen by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    // 筛选从「内联展开的一大块面板」改成「底部面板」，入口是右上角那个筛选图标
    var showFilterSheet by remember { mutableStateOf(false) }

    val characterUnits = remember(characters) {
        characters.associate { it.id to it.unitKey }
    }
    val characterNames = remember(characters, nameLanguage) {
        characters.associate { it.id to it.displayNameFor(nameLanguage) }
    }

    val visible = remember(cards, query, rarityCaps, characterUnits) {
        filterCards(cards, query, rarityCaps, characterUnits)
    }

    // 筛选面板里「组合 → 角色」的分组。必须按**卡**统计，不能按角色的 unit 字段分组：
    // 虚拟歌手的 unit 只有 `piapro` 一个值，光看它的话这 6 个人只会出现在
    // VIRTUAL SINGER 组里，而「每个团里的虚拟歌手」是玩家真实的心智模型。
    val characterGroups = remember(cards, characters) {
        characterGroupsOf(cards, characters)
    }

    // 「卡池类型」每一项有多少张卡（面板上直接写出来）
    val supplyCounts = remember(cards) {
        cards.groupingBy { it.supplyType }.eachCount().filterKeys { it != null }
            .mapKeys { (key, _) -> key!! }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (searchOpen) {
                        CardSearchField(
                            value = query.search,
                            onValueChange = { query = query.copy(search = it) },
                        )
                    } else {
                        Column {
                            Text("卡牌图鉴", style = MaterialTheme.typography.titleLarge)
                            // 计数并进标题第二行，省掉原本常驻的一整行
                            Text(
                                text = if (query.isFiltering) {
                                    "筛选出 ${visible.size} / ${cards.size} 张"
                                } else {
                                    "共 ${cards.size} 张"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                // ⚠️ 刻意**没有返回箭头**：卡牌是底部导航直达的顶级板块，
                // 不该出现"返回到哪"的箭头。返回键由系统处理。
                actions = {
                    IconButton(
                        onClick = {
                            if (searchOpen) {
                                // 关搜索框时一起清空条件，否则列表会被一个看不见的条件筛着
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
                    // 筛选入口：图标 + 已启用的**维度个数**角标
                    BadgedBox(
                        badge = {
                            if (query.activeCount > 0) {
                                Badge { Text(query.activeCount.toString()) }
                            }
                        },
                    ) {
                        IconButton(onClick = { showFilterSheet = true }) {
                            Icon(
                                Icons.Default.FilterList,
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
            if (cards.isEmpty()) {
                // 先给 3 秒宽限期显示灰骨架。数据现在就在本地数据库里，通常几百毫秒就到；
                // 一为空就喊「去下载」会让用户每次切换页面都撞上这句话，非常焦虑。
                if (rememberEmptyConfirmed(isEmpty = true)) {
                    EmptyState(
                        title = "卡牌数据还没准备好",
                        description = "首次启动会从内置数据自动导入。如果一直是这样，去「更多 → 数据同步」检查一下。",
                        actionLabel = "去数据同步",
                        onAction = onOpenSync,
                    )
                } else {
                    CardListSkeleton()
                }
                return@Column
            }

            // 筛选激活时留一行「清除筛选」的快捷出口。
            // 平时不显示 —— 计数已经并进标题，条件都收进筛选面板了。
            if (query.isFiltering) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { query = CardQuery() }) { Text("清除筛选") }
                }
            }

            if (visible.isEmpty()) {
                EmptyState(title = "没有符合条件的卡牌", description = "试着放宽筛选条件。")
                return@Column
            }

            // 预取：视口前后各 10 条的卡面。
            // 卡面单张 58 KB、约 1–7 秒，用户往往滑得比下载快；
            // 预取请求不随组合销毁而取消，所以能真正下完并落盘，滑回来就是本地读取。
            PrefetchCardImages(
                listState = listState,
                itemCount = visible.size,
                urlsAt = { index -> visible.getOrNull(index)?.listArtUrls(region).orEmpty() },
                radius = 3,
            )

            // LazyColumn 自带虚拟化，所以不需要分页：只组合可见的那几项。
            // weight(1f) 保证它一定拿到「筛选面板之后剩下的全部高度」。
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    bottom = 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(visible, key = { it.id }) { card ->
                    CardTile(
                        card = card,
                        region = region,
                        name = card.displayNameFor(nameLanguage),
                        secondaryName = card.secondaryNameFor(nameLanguage),
                        characterName = characterNames[card.characterId],
                        totalPower = card.maxTotal(rarityCaps),
                        onClick = { onOpenDetail(card.id) },
                    )
                }            }
        }
    }

    if (showFilterSheet) {
        CardFilterSheet(
            query = query,
            onChange = { query = it },
            characterGroups = characterGroups,
            nameLanguage = nameLanguage,
            supplyCounts = supplyCounts,
            matchedCount = visible.size,
            totalCount = cards.size,
            onDismiss = { showFilterSheet = false },
        )
    }
}

/**
 * 单个卡牌条目。
 *
 * 图框是 16:9；左半边未特训、右半边觉醒，两张图都用居中裁切。
 * 角标也照 sekai.best：属性圆点贴图框右上、稀有度星星贴图框左下。
 */
@Composable
private fun CardTile(
    card: Card,
    region: ServerRegion,
    name: String,
    secondaryName: String?,
    characterName: String?,
    totalPower: Int,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(CARD_FRAME_ASPECT),
        ) {
            // 最底层：属性色底 + 3 KB 方形小图标。
            // 卡面要 1–7 秒才到，这一层让格子立刻「有内容」而不是空白。
            CardArtPlaceholder(region = region, card = card, modifier = Modifier.fillMaxSize())

            when (card.artPlan) {
                // 出厂即特训后的卡（如联动限定）：**只有觉醒图**，
                // 请求未觉醒图会 404，所以这里绝不能去取 card_normal。
                CardArtPlan.TRAINED_ONLY -> CardArtImage(
                    url = AssetUrls.cardPreview(region, card.assetbundleName, trained = true),
                    contentDescription = card.displayName,
                    modifier = Modifier.fillMaxSize(),
                    alignment = Alignment.Center,
                )

                // 1★/2★/生日卡：只有未觉醒图，整张占满
                CardArtPlan.NORMAL_ONLY -> CardArtImage(
                    url = AssetUrls.cardPreview(region, card.assetbundleName, trained = false),
                    contentDescription = card.displayName,
                    modifier = Modifier.fillMaxSize(),
                    alignment = Alignment.Center,
                )

                // 两张都有：左半边取**通常图的左侧**，右半边取**特训后图的右侧**
                //
                // 这里刻意**不画中间的分割线**：两张图内容本身就不同，接缝天然可见。
                // 之前画了一条 1dp 的线、颜色用 colorScheme.surface ——
                // 浅色主题下 surface 是近白色 #F5FAF9，于是成了一道刺眼的白边。
                CardArtPlan.BOTH -> Row(modifier = Modifier.fillMaxSize()) {
                    CardArtImage(
                        url = AssetUrls.cardPreview(region, card.assetbundleName, trained = false),
                        contentDescription = card.displayName,
                        modifier = Modifier.weight(1f).fillMaxSize(),
                        alignment = Alignment.CenterStart,
                    )
                    CardArtImage(
                        url = AssetUrls.cardPreview(region, card.assetbundleName, trained = true),
                        contentDescription = card.displayName,
                        modifier = Modifier.weight(1f).fillMaxSize(),
                        alignment = Alignment.CenterEnd,
                    )
                }
            }

            // 右上：属性图标（内置在 APK 里的小图，比原来的色点更好认）
            if (card.attr != null) {
                AttrIcon(
                    attr = card.attr,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                    size = 18.dp,
                )
            }

            // 左上：「限定」角标（右上被属性图标占了、左下是星星，只剩这里空着）。
            // 只对限定卡挂这一枚，常驻和生日卡不挂 —— 见 Card.listTagLabel 的说明。
            card.listTagLabel?.let { label ->
                TagChip(
                    text = label,
                    tone = TagTone.ACCENT,
                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
                )
            }

            // 左下：稀有度星星
            Row(
                modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(card.stars) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = Color(0xFFFFD54F),
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
        }

        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!secondaryName.isNullOrBlank()) {
                Text(
                    text = secondaryName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = buildString {
                        characterName?.let { append(it) }
                        card.attr?.let {
                            if (isNotEmpty()) append(" · ")
                            append(it.label)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    text = "综合力 ${"%,d".format(totalPower)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * 格子宽高比 = 1.68。
 *
 * 为什么不写 16:9（1.778）：卡面本身不是固定宽高比 —— 实测 1★ 是 2520×1440（1.75:1）、
 * 4★ 是 2338×1440（1.62:1）。在「左右各取一半」的排布下，每个半边能看到的图片宽度比例
 * 等于 `格子比例 / (2 × 图片比例)`：
 *  - 取 1.68 时是 48%~52%（基本正好一半，符合「左半放左半边、右半放右半边」）
 *  - 取 16:9 时是 51%~55%（每半边多看一点，中间约 10% 内容两边各显示一次）
 * 两者都不裁掉内容，只是取景范围略有差别。
 */
private const val CARD_FRAME_ASPECT = 1.68f

// ─────────────────────────────────────────────────────────────
// 筛选面板（底部弹层，四维全多选）
//
// 为什么从「内联展开的 320dp 面板」改成底部弹层：
//  - 旧版把 4 行 chip + 一个打开二级弹窗的入口全塞在列表上方，视觉又密又重；
//  - 26 个角色 + 6 个组合在内联面板里根本放不下（旧版正是为此又开了第二个弹窗）；
//  - sekai.best / pjsk.moe 的筛选都是抽屉/弹层形态，用户已经习惯。
//
// 筛选逻辑：**同维取「或」、跨维取「且」**（见 CardQuery 的说明）。
// 面板**即时生效**，不设「应用」按钮 —— 底部直接显示「当前会筛出 N 张」。
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun CardFilterSheet(
    query: CardQuery,
    onChange: (CardQuery) -> Unit,
    characterGroups: List<CharacterGroup>,
    nameLanguage: NameLanguage,
    supplyCounts: Map<CardSupplyType, Int>,
    matchedCount: Int,
    totalCount: Int,
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
                TextButton(onClick = { onChange(CardQuery()) }) { Text("重置") }
            }

            FilterGroup("属性") {
                // 选中的排到前面（点了中间某个 chip 之后它还夹在一排未选中里，扫一眼看不出选了什么）
                CardAttr.entries.moveMatchesFirst { it in query.attrs }.forEach { attr ->
                    FilterChip(
                        selected = attr in query.attrs,
                        onClick = {
                            onChange(
                                query.copy(
                                    attrs = if (attr in query.attrs) query.attrs - attr
                                    else query.attrs + attr,
                                ),
                            )
                        },
                        label = { Text(attr.label) },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(attr.color),
                            )
                        },
                    )
                }
            }

            FilterGroup("稀有度") {
                RARITY_OPTIONS.moveMatchesFirst { it.first in query.rarityKeys }
                    .forEach { (key, label) ->
                        FilterChip(
                            selected = key in query.rarityKeys,
                            onClick = {
                                onChange(
                                    query.copy(
                                        rarityKeys = if (key in query.rarityKeys) {
                                            query.rarityKeys - key
                                        } else {
                                            query.rarityKeys + key
                                        },
                                    ),
                                )
                            },
                            label = { Text(label) },
                        )
                    }
            }

            FilterGroup("组合") {
                CardUnit.entries.moveMatchesFirst { it in query.units }.forEach { unit ->
                    FilterChip(
                        selected = unit in query.units,
                        onClick = {
                            onChange(
                                query.copy(
                                    units = if (unit in query.units) query.units - unit
                                    else query.units + unit,
                                ),
                            )
                        },
                        label = { Text(unit.label, maxLines = 1) },
                    )
                }
            }

            // 角色按组合分组：56 个平铺太难找。
            // 虚拟歌手会在**每个团**里再出现一次（他们确实以那个团的成员身份出卡），
            // 所以同一个名字会重复出现在多组里 —— 这是刻意的，见 belongsToUnit。
            Text(
                text = "角色",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
            )
            characterGroups.forEach { group ->
                Text(
                    text = group.unit.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    group.members.forEach { character ->
                        val selected = character.id in query.characterIds
                        FilterChip(
                            selected = selected,
                            onClick = {
                                onChange(
                                    query.copy(
                                        characterIds = if (selected) {
                                            query.characterIds - character.id
                                        } else {
                                            query.characterIds + character.id
                                        },
                                    ),
                                )
                            },
                            label = { Text(character.displayNameFor(nameLanguage)) },
                        )
                    }
                }
            }

            FilterGroup("卡池类型") {
                // 张数直接写在 chip 上：光看「联动限定」四个字没法判断这一项值不值得点，
                // 「82 张」才有信息量。计数按**全部卡**算（不随其它维度变），
                // 否则「还有几张」会随着筛选互相牵动，越点越难看懂。
                CardSupplyType.entries.moveMatchesFirst { it in query.supplyTypes }
                    .forEach { supply ->
                        FilterChip(
                            selected = supply in query.supplyTypes,
                            onClick = {
                                onChange(
                                    query.copy(
                                        supplyTypes = if (supply in query.supplyTypes) {
                                            query.supplyTypes - supply
                                        } else {
                                            query.supplyTypes + supply
                                        },
                                    ),
                                )
                            },
                            label = {
                                Text("${supply.label} ${supplyCounts[supply] ?: 0}")
                            },
                        )
                    }
            }

            FilterGroup("排序") {
                CardSort.entries.forEach { sort ->
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
                    if (query.isFiltering) "显示 $matchedCount / $totalCount 张"
                    else "显示全部 $totalCount 张",
                )
            }
        }
    }
}

/**
 * 顶栏里的搜索框（点 🔍 才展开，顶替标题的位置）。
 *
 * 关闭/清空由顶栏那个 ✕ 负责 —— 顶栏空间紧张，不放第二个「关掉」的入口。
 */
@Composable
private fun CardSearchField(value: String, onValueChange: (String) -> Unit) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text("卡名 / 角色名 / id") },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = {}),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}

/**
 * 筛选面板里的一组 chip（标题 + 自动换行）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterGroup(
    title: String,
    content: @Composable FlowRowScope.() -> Unit,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        content = content,
    )
}

/** 角色的分组顺序。虚拟歌手排最前，与游戏内的编队顺序一致。 */
private val UNIT_ORDER = listOf(
    CardUnit.VIRTUAL_SINGER,
    CardUnit.LEO_NEED,
    CardUnit.MORE_MORE_JUMP,
    CardUnit.VIVID_BAD_SQUAD,
    CardUnit.WONDERLANDS,
    CardUnit.NIIGO,
)

/** 筛选面板里的一组角色（一个组合 + 该组合下会出现的角色）。 */
private data class CharacterGroup(
    val unit: CardUnit,
    val members: List<PjskCharacter>,
)

/**
 * 把角色按组合分组。
 *
 * **不能只按角色的 `unit` 分组**：虚拟歌手的 `unit` 只有一个值 `piapro`，
 * 那样分组的结果是「VIRTUAL SINGER 一组 6 人，各团只有 4 人」，
 * 而实际上每个团都有虚拟歌手的卡（`cards.supportUnit` 就是那个团）。
 * 所以这里按**卡**统计：某个角色只要有一张卡属于某组合，他就出现在那一组里。
 *
 * 结果是虚拟歌手会重复出现在多个组 —— 与「允许重复」的取舍一致，
 * 也让「组合=Leo/need + 角色=初音未来」能筛出 51 张（Leo/need 的初音卡）。
 *
 * 只在「有卡」时才列出角色，避免出现点了没反应的死 chip。
 */
private fun characterGroupsOf(
    cards: List<Card>,
    characters: List<PjskCharacter>,
): List<CharacterGroup> {
    val unitOfCharacter = characters.associate { it.id to CardUnit.of(it.unitKey) }
    val idsByUnit = mutableMapOf<CardUnit, MutableSet<Int>>()

    cards.forEach { card ->
        val characterUnit = unitOfCharacter[card.characterId] ?: return@forEach
        // 卡主的组合
        idsByUnit.getOrPut(characterUnit) { mutableSetOf() } += card.characterId
        // 虚拟歌手的卡再按 supportUnit 借调到各团
        if (characterUnit == CardUnit.VIRTUAL_SINGER) {
            val borrowed = CardUnit.of(card.supportUnit) ?: return@forEach
            if (borrowed != CardUnit.VIRTUAL_SINGER) {
                idsByUnit.getOrPut(borrowed) { mutableSetOf() } += card.characterId
            }
        }
    }

    return UNIT_ORDER.mapNotNull { unit ->
        val ids = idsByUnit[unit] ?: return@mapNotNull null
        val members = characters.filter { it.id in ids }
        if (members.isEmpty()) null else CharacterGroup(unit, members)
    }
}
