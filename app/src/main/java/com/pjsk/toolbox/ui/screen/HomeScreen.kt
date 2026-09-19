package com.pjsk.toolbox.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.pjsk.toolbox.data.home.HomeEvent
import com.pjsk.toolbox.data.home.HomeMusic
import com.pjsk.toolbox.data.home.HomeRepository
import androidx.compose.foundation.layout.aspectRatio
import com.pjsk.toolbox.data.story.StoryStatus
import com.pjsk.toolbox.data.home.HomeBirthday
import com.pjsk.toolbox.data.home.HomeGacha
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.pjsk.toolbox.PjskApp
import com.pjsk.toolbox.data.card.Card
import com.pjsk.toolbox.data.card.CardArtPlan
import com.pjsk.toolbox.data.card.FALLBACK_RARITY_CAPS
import com.pjsk.toolbox.data.card.displayNameFor
import com.pjsk.toolbox.data.card.secondaryNameFor
import com.pjsk.toolbox.data.remote.AssetUrls
import com.pjsk.toolbox.data.remote.ServerRegion
import com.pjsk.toolbox.data.sync.BundledState
import com.pjsk.toolbox.ui.common.AttrIcon
import com.pjsk.toolbox.ui.common.CardArtImage
import com.pjsk.toolbox.ui.common.CardArtPlaceholder
import com.pjsk.toolbox.ui.common.EmptyState
import com.pjsk.toolbox.ui.common.PrefetchCardImages
import com.pjsk.toolbox.ui.common.listArtUrls
import com.pjsk.toolbox.ui.common.openExternalLink
import com.pjsk.toolbox.ui.common.rememberEmptyConfirmed
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DATE_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault())

/** 首页卡面横滑里显示多少张。 */
private const val LATEST_CARD_COUNT = 20

/** 卡面槽位的尺寸。固定的好处是图片加载前后不会跳动；不裁切保证卡面完整。 */
private val CARD_SLOT_WIDTH = 245.dp
private val CARD_SLOT_HEIGHT = 150.dp

/**
 * 首页。
 *
 * 结构是「纵向区块流」：每块一个标题 + 横向滑动的内容。
 *
 * 本轮只把**卡面区块**做成真的；其余区块（当前卡池 / 最新歌曲 / 最新活动 / 生日 / 友链）
 * 先以占位卡片的形式占住位置，等各自的页面做完再填。
 *
 * 卡面区块的规则（已确认）：
 *  - **只显示觉醒图**：有觉醒图就用觉醒图，只有未觉醒图的卡（1★/2★/生日卡）用未觉醒图
 *  - **按原图比例完整显示，不裁切**：卡面本身不是固定宽高比（1★ 是 2520×1440、4★ 是 2338×1440），
 *    所以这里用固定槽位 + `ContentScale.Fit`，宁可留一点边也不切掉画面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    region: ServerRegion,
    onOpenRoute: (String) -> Unit,
    onOpenCard: (Int) -> Unit = {},
    onOpenSongs: () -> Unit = {},
    onOpenMusic: (Int) -> Unit = {},
    onOpenGacha: () -> Unit = {},
) {
    val container = (LocalContext.current.applicationContext as PjskApp).container

    // cards 现在是 StateFlow（仓库里用 stateIn 缓存了解析结果），所以不需要 initial；
    // 这也正是「首页 ↔ 图鉴来回切不再闪骨架」的关键。
    val cards by container.cardRepository.cards.collectAsState()
    val characters by container.cardRepository.characters.collectAsState(initial = emptyList())
    val caps by container.cardRepository.rarityCaps.collectAsState(initial = FALLBACK_RARITY_CAPS)
    val bundledState by container.bundledState.collectAsState()
    val nameLanguage by container.appSettings.nameLanguage.collectAsState()

    // 首页其余区块的数据（三张表都已内置，不需要新增数据）
    val musics by container.homeRepository.musics.collectAsState(initial = emptyList())
    val events by container.homeRepository.events.collectAsState(initial = emptyList())
    val birthdays by container.homeRepository.birthdays.collectAsState(initial = emptyList())

    val characterNames = remember(characters, nameLanguage) {
        characters.associate { it.id to it.displayNameFor(nameLanguage) }
    }
    // 「最新」按开放时间倒序。cards 本身已按 releaseAt 倒序，这里再取前 N 张。
    val latestCards = remember(cards) { cards.take(LATEST_CARD_COUNT) }

    // 当前卡池：进行中的（按结束时间从近到远，快结束的排前面）
    val gachas by container.homeRepository.gachas.collectAsState(initial = emptyList())
    val runningGachas = remember(gachas) {
        val now = System.currentTimeMillis()
        gachas.filter { now in it.startAt until it.endAt }.sortedBy { it.endAt }.take(12)
    }
    // 30 天内要过生日的角色，按「还有几天」升序（今天就过的最前）
    val upcomingBirthdays = remember(birthdays) {
        birthdays
            .map { it to HomeRepository.daysUntilBirthday(it.month, it.day) }
            .filter { it.second <= BIRTHDAY_WINDOW_DAYS }
            .sortedBy { it.second }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("sekai lmc") }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            // ── 内置数据导入进度（不阻塞任何操作）──
            if (bundledState is BundledState.Installing) {
                item {
                    val state = bundledState as BundledState.Installing
                    BundledProgressBanner(
                        done = state.done,
                        total = state.total,
                        table = state.table,
                    )
                }
            }
            (bundledState as? BundledState.Failed)?.let { failed ->
                item { BundledFailedBanner(message = failed.message, onOpenSync = { onOpenRoute(ROUTE_SYNC) }) }
            }

            // ── 最新卡牌 ──
            item {
                SectionHeader(
                    title = "最新卡牌",
                    subtitle = if (cards.isEmpty()) null else "共 ${cards.size} 张",
                    actionLabel = "图鉴",
                    onAction = { onOpenRoute(ROUTE_CODEX) },
                )
            }
            item {
                if (latestCards.isEmpty()) {
                    // 与图鉴页同样的规矩：先给 3 秒宽限期显示灰骨架，
                    // 超时后仍为空才提示去同步 —— 否则每次切回首页都看到「没有数据」很焦虑。
                    if (rememberEmptyConfirmed(isEmpty = true)) {
                        EmptyState(
                            title = "还没有卡牌数据",
                            description = "首次启动会从内置数据自动导入。如果一直是这样，去「更多 → 数据同步」检查一下。",
                        )
                    } else {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            repeat(3) {
                                Box(
                                    modifier = Modifier
                                        .width(CARD_SLOT_WIDTH)
                                        .height(CARD_SLOT_HEIGHT)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                )
                            }
                        }
                    }
                } else {
                    val rowState = rememberLazyListState()
                    // 预取整条横滑的卡面（只有 20 条，±10 基本全覆盖）
                    PrefetchCardImages(
                        listState = rowState,
                        itemCount = latestCards.size,
                        urlsAt = { index -> latestCards.getOrNull(index)?.listArtUrls(region).orEmpty() },
                        radius = 3,
                    )
                    LazyRow(
                        state = rowState,
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(latestCards, key = { it.id }) { card ->
                            LatestCardTile(
                                card = card,
                                region = region,
                                title = card.displayNameFor(nameLanguage),
                                subtitle = characterNames[card.characterId],
                                onClick = { onOpenCard(card.id) },
                            )
                        }
                    }
                }
            }

            // ── 当前卡池（进行中的，按结束时间排）──
            if (runningGachas.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = "当前卡池",
                        subtitle = "进行中 ${runningGachas.size} 个",
                        actionLabel = "全部",
                        onAction = onOpenGacha,
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(runningGachas, key = { it.id }) { gacha ->
                            GachaTile(gacha = gacha, region = region, onClick = onOpenGacha)
                        }
                    }
                }
            }
            // ── 最新歌曲（按 publishedAt 倒序，取最近 N 首）──
            if (musics.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = "最新歌曲",
                        subtitle = "共 ${musics.size} 首",
                        actionLabel = "全部",
                        onAction = onOpenSongs,
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(musics.take(LATEST_MUSIC_COUNT), key = { it.id }) { music ->
                            MusicTile(music = music, region = region, onClick = { onOpenMusic(music.id) })
                        }
                    }
                }
            }

            // ── 最新活动（按 startAt 倒序 + 进行中标记）──
            if (events.isNotEmpty()) {
                item {
                    SectionHeader(title = "最新活动", subtitle = "共 ${events.size} 个")
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(events.take(LATEST_EVENT_COUNT), key = { it.id }) { event ->
                            EventTile(event = event, region = region)
                        }
                    }
                }
            }

            // ── 即将到来的生日（30 天内，按临近程度排序）──
            if (upcomingBirthdays.isNotEmpty()) {
                item {
                    SectionHeader(title = "即将到来的生日", subtitle = "30 天内")
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(upcomingBirthdays, key = { it.first.characterId }) { (birthday, days) ->
                            BirthdayTile(
                                birthday = birthday,
                                days = days,
                                name = characterNames[birthday.characterId].orEmpty(),
                                region = region,
                            )
                        }
                    }
                }
            }

            // ── 工具（静态。两行两列：4 个一屏就能看全，不用横滑去找）──
            //
            // ⚠️ 外链条目（贴纸制作器 / Sonolus / 配套工具）**必须写清"第三方"**：
            // 它们是别人做的东西，不能让用户以为是本应用的功能（用户明确要求）。
            // 外链的卡片会多一个「跳到外面去」的小图标（应用内跳转的「数据同步」没有）。
            item {
                SectionHeader(title = "工具", subtitle = null)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    LinkRow(
                        LinkSpec(
                            title = "数据同步",
                            note = "检查游戏数据更新",
                            onClick = { onOpenRoute(ROUTE_SYNC) },
                        ),
                        LinkSpec(
                            title = "贴纸制作器",
                            note = "第三方网站 pjsk.moe，表情包制作",
                            url = "https://pjsk.moe/zh-cn/sticker-maker/",
                        ),
                    )
                    LinkRow(
                        LinkSpec(
                            title = "Sonolus 模拟器",
                            note = "第三方网站，官网下载",
                            url = "https://sonolus.com/zhs",
                        ),
                        LinkSpec(
                            title = "模拟器配套工具",
                            note = "第三方网站，Sonolus 服务器列表",
                            url = "https://tool.sonolus.reikohaku.fun/server-list",
                        ),
                    )
                }
            }

            // ── 参考项目（原「友情链接」）──
            // 这几个站是本应用的数据格式与交互的参考来源，同样是第三方的。
            item {
                SectionHeader(title = "参考项目", subtitle = null)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    LinkRow(
                        LinkSpec(
                            title = "sekai.best",
                            note = "第三方参考站，Sekai Viewer",
                            url = "https://sekai.best",
                        ),
                        LinkSpec(
                            title = "pjsk.moe",
                            note = "第三方参考站，Moesekai",
                            url = "https://pjsk.moe",
                        ),
                    )
                }
            }

            /*
             * ── 作者（2026-09-16 用起来了这块预留位）──
             *
             * 只有一项，所以用**整宽卡片**（半宽会空一半，很难看）。
             * ⚠️ 这里是**作者自己的东西**，所以副标题**不写**「第三方」——
             * 和上面「工具 / 参考项目」那两个区块的规则正好相反（那些必须标第三方）。
             * 以后要加"本项目仓库"之类的，再补一张整宽卡就行。
             */
            item {
                SectionHeader(title = "作者", subtitle = null)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                ) {
                    LinkTile(
                        spec = LinkSpec(
                            title = "GitHub 主页",
                            note = "github.com/3beuml",
                            url = "https://github.com/3beuml",
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/** 首页「最新歌曲」的一格：曲绘 + 歌名 + 作曲家。 */
@Composable
private fun MusicTile(music: HomeMusic, region: ServerRegion, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(TRACK_TILE_WIDTH)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = AssetUrls.musicJacket(region, music.assetbundleName),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = music.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        music.composer?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 首页「最新活动」的一格：活动 logo + 名字 + 起止 + 进行中标记。 */
@Composable
private fun EventTile(event: HomeEvent, region: ServerRegion) {
    val now = System.currentTimeMillis()
    val status = when {
        now < event.startAt -> StoryStatus.UPCOMING
        now >= event.closedAt -> StoryStatus.ENDED
        else -> StoryStatus.RUNNING
    }
    Column(modifier = Modifier.width(EVENT_TILE_WIDTH)) {
        AsyncImage(
            model = AssetUrls.eventLogo(region, event.assetbundleName),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Fit,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = event.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            StoryStatusBadge(status)
            Spacer(Modifier.width(6.dp))
            Text(
                text = "${shortDate(event.startAt)} ~ ${shortDate(event.closedAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 首页「生日」的一格：圆形角色头像 + 名字 + 还有几天。 */
@Composable
private fun BirthdayTile(
    birthday: HomeBirthday,
    days: Int,
    name: String,
    region: ServerRegion,
) {
    Column(
        modifier = Modifier.width(80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AsyncImage(
            model = AssetUrls.characterAvatar(region, birthday.characterId),
            contentDescription = null,
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop,
            // `chr_tl_` 是 376×840 的竖长半身图，**必须对齐顶部**才露脸
            alignment = Alignment.TopCenter,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = name.ifBlank { "#${birthday.characterId}" },
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = if (days == 0) "今天！" else "还有 $days 天",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = "${birthday.month}月${birthday.day}日",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 首页「工具 / 参考项目」里的一格。
 *
 * 三种情况：
 *  - 有 [url] → 用系统浏览器打开（**外链**，卡片右上角会有一个"跳到外面去"的小图标）
 *  - 有 [onClick] → 应用内跳转（不显示那个图标）
 *  - 两个都没有 → 点了没反应（不要这么用）
 */
private data class LinkSpec(
    val title: String,
    val note: String,
    val url: String? = null,
    val onClick: (() -> Unit)? = null,
)

/**
 * 一行两格。**高度对齐**：同一行两张卡片的高必须一样，
 * 否则副标题换行的那张会比另一张高，背景块看起来一高一低。
 */
@Composable
private fun LinkRow(left: LinkSpec, right: LinkSpec) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LinkTile(left, Modifier.weight(1f).fillMaxHeight())
        LinkTile(right, Modifier.weight(1f).fillMaxHeight())
    }
}

/** 首页的工具 / 参考项目一格。有 [LinkSpec.url] 就打开浏览器。 */
@Composable
private fun LinkTile(spec: LinkSpec, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .clickable {
                when {
                    spec.onClick != null -> spec.onClick.invoke()
                    spec.url != null -> openExternalLink(context, spec.url)
                }
            }
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = spec.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            // 「这个点了会跳到外面去」——只有外链才画，一眼能分清应用内/应用外
            if (spec.url != null) {
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = "外部链接",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = spec.note,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 活动状态小标（和剧情页里那个同一套配色）。 */
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

/** `2026-09-21` 这种短日期。 */
private fun shortDate(millis: Long): String =
    java.time.Instant.ofEpochMilli(millis)
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalDate()
        .toString()

/** 首页各区块的条数与阈值。 */
private const val LATEST_MUSIC_COUNT = 10
private const val LATEST_EVENT_COUNT = 10
private const val BIRTHDAY_WINDOW_DAYS = 30
private val TRACK_TILE_WIDTH = 110.dp
private val EVENT_TILE_WIDTH = 190.dp

/** 首页「当前卡池」的一格：卡池 logo + 名字 + 起止 + 状态。 */
@Composable
private fun GachaTile(gacha: HomeGacha, region: ServerRegion, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(EVENT_TILE_WIDTH)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = AssetUrls.gachaLogo(region, gacha.assetbundleName),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .height(78.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Fit,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = gacha.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            StoryStatusBadge(StoryStatus.RUNNING)
            Spacer(Modifier.width(6.dp))
            Text(
                text = "至 ${shortDate(gacha.endAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
/** 区块标题 + 右侧动作。 */
@Composable
private fun SectionHeader(
    title: String,
    subtitle: String?,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(actionLabel)
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/**
 * 首页的单个卡面。
 *
 * **只显示觉醒图**：有觉醒图就用觉醒图；只有未觉醒图的卡（1★/2★/生日卡）用未觉醒图。
 * 用 [ContentScale.Fit] 而不是 Crop —— 卡面宽高比不固定，裁切会切掉画面。
 */
@Composable
private fun LatestCardTile(
    card: Card,
    region: ServerRegion,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier.width(CARD_SLOT_WIDTH).clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(CARD_SLOT_HEIGHT)
                .clip(RoundedCornerShape(12.dp)),
        ) {
            // 最底层：属性色底 + 3 KB 小图标，让格子立刻有内容
            CardArtPlaceholder(region = region, card = card, modifier = Modifier.fillMaxSize())
            CardArtImage(
                url = AssetUrls.cardPreview(
                    region = region,
                    assetbundleName = card.assetbundleName,
                    // 首页的既定规则：有觉醒图就显示觉醒图。
                    // 「出厂即特训后」的卡（artPlan = TRAINED_ONLY）也只有觉醒图，
                    // 所以统一写成「只要不是『只有通常图』就用特训后图」。
                    trained = card.artPlan != CardArtPlan.NORMAL_ONLY,
                    size = AssetUrls.CardSize.SMALL,
                ),
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
            // 左下角：稀有度星星
            Row(
                modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(card.stars) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = Color(0xFFFFD54F),
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
            // 右上角：属性图标（内置在 APK 里的小图）
            if (card.attr != null) {
                AttrIcon(
                    attr = card.attr,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                    size = 20.dp,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = buildString {
                subtitle?.let { append(it) }
                card.attr?.let {
                    if (isNotEmpty()) append(" · ")
                    append(it.label)
                }
                if (isNotEmpty()) append(" · ")
                val date = runCatching {
                    DATE_FORMATTER.format(Instant.ofEpochMilli(card.releaseAt))
                }.getOrNull() ?: "—"
                append(date)
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 首次启动的导入进度条。刻意做成一条细提示，不挡内容、不弹窗。 */
@Composable
private fun BundledProgressBanner(done: Int, total: Int, table: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        val label = if (total > 0) {
            "正在准备内置数据 $done / $total${if (table.isNotEmpty()) " · ${table.removeSuffix(".json")}" else ""}"
        } else {
            "正在准备内置数据…"
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        if (total > 0) {
            LinearProgressIndicator(
                progress = { done.toFloat() / total.toFloat() },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun BundledFailedBanner(message: String, onOpenSync: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "内置数据导入失败",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                textAlign = TextAlign.Start,
            )
            TextButton(onClick = onOpenSync) { Text("去数据同步") }
        }
    }
}

// 路由常量集中放在这里，避免首页直接依赖 AppNav 里的字符串导致改一处漏一处
private const val ROUTE_CODEX = "cards"
private const val ROUTE_SYNC = "sync"
