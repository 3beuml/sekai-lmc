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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.PauseCircleFilled
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material3.Slider
import com.pjsk.toolbox.data.music.fillerMsOf
import com.pjsk.toolbox.data.music.formatClock
import com.pjsk.toolbox.data.player.MusicTrack
import com.pjsk.toolbox.data.player.PlaybackState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.pjsk.toolbox.data.music.MusicDetail
import com.pjsk.toolbox.data.music.MusicDifficulty
import com.pjsk.toolbox.data.music.MusicEventRef
import com.pjsk.toolbox.data.music.MusicSinger
import com.pjsk.toolbox.data.music.MusicUnitTag
import com.pjsk.toolbox.data.music.MusicVocal
import com.pjsk.toolbox.data.remote.AssetUrls
import com.pjsk.toolbox.data.remote.ServerRegion
import com.pjsk.toolbox.data.settings.NameLanguage
import com.pjsk.toolbox.ui.common.EmptyState
import com.pjsk.toolbox.ui.common.KeyValueRow
import com.pjsk.toolbox.ui.common.RemoteImage
import com.pjsk.toolbox.ui.common.RowDivider
import com.pjsk.toolbox.ui.common.SectionTitle
import com.pjsk.toolbox.ui.common.openExternalLink
import java.time.Instant
import java.time.ZoneId


/**
 * 歌曲详情（资料页）。
 *
 * ## 这一页**不放**什么，以及为什么
 *
 *  - **时长**：用户明确要求不放（时间轴归播放器）；
 *  - **试听 / 版本切换**：阶段 3~5 的播放器才做，所以「演唱版本」区块**只展示**，
 *    连播放图标都不放 —— 免得出现「看起来能点、点了没反应」；
 *  - **谱面图入口**：后置（放上去就会点了 404）。
 *
 * ## 「演唱者」为什么放在「演唱版本」里，而不是页面顶部
 *
 * 一首歌的不同版本**演唱者完全不同**（644 的虚拟歌手版是「可不」，
 * 世界版是 MEIKO / 宵崎奏 / 暁山瑞希）。把演唱者提到页面顶部会张冠李戴，
 * 所以它跟着版本走。
 *
 * 数据全部离线（曲绘除外），组装逻辑在 `data/music/MusicModels.kt`，
 * 且全部是纯函数、进 `tools/logic-check.ps1` 自检。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicDetailScreen(
    musicId: Int,
    region: ServerRegion,
    onBack: () -> Unit,
    onOpenEvent: (Int) -> Unit,
    onOpenCharacter: (Int) -> Unit,
    onOpenLyrics: () -> Unit,
) {
    val container = (LocalContext.current.applicationContext as PjskApp).container
    val nameLanguage by container.appSettings.nameLanguage.collectAsState()

    var detail by remember(musicId) { mutableStateOf<MusicDetail?>(null) }
    var loading by remember(musicId) { mutableStateOf(true) }
    // null = 还不知道（社区歌词索引没加载出来）；只有**明确 false** 才隐藏歌词入口
    var lyricsAvailable by remember(musicId) { mutableStateOf<Boolean?>(null) }

    // 用**全局播放器**（阶段 6 起）：离开这一屏后底部状态栏还要能继续控制它，
    // 所以不能在这里创建/释放。⚠️ 千万不要加 DisposableEffect { release() }。
    val player = container.musicPlayer
    val playback by player.state.collectAsState()

    // 按需载入：只在 musicId 变化时查一次（不养常驻缓存，理由见 MusicRepository）
    LaunchedEffect(musicId) {
        loading = true
        detail = container.musicRepository.detail(musicId)
        // 顺便问一次"这首有没有歌词"，用来决定要不要显示下面的歌词入口。
        // 索引是联网拿的（约 198 KB、进程内只拉一次）；拿不到时返回 null → 照常显示入口。
        lyricsAvailable = container.lyricsRepository.hasLyrics(musicId)
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("歌曲详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        val current = detail
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                current == null -> EmptyState(
                    title = "找不到这首歌",
                    description = "歌曲数据还没同步到本机。去「更多 → 数据同步」拉一次即可。",
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = 32.dp,
                    ),
                ) {
                    item { Hero(detail = current, region = region, nameLanguage = nameLanguage) }
                    item { BasicInfo(detail = current) }
                    if (current.difficulties.isNotEmpty()) {
                        item { DifficultySection(rows = current.difficulties) }
                    }
                    if (current.vocals.isNotEmpty()) {
                        item {
                            VocalSection(
                                vocals = current.vocals,
                                detail = current,
                                region = region,
                                nameLanguage = nameLanguage,
                                playback = playback,
                                onToggleVocal = { vocal ->
                                    // 点的是**正在播的那个版本** → 只是播放/暂停
                                    if (playback.track?.vocalId == vocal.id) {
                                        player.togglePlayPause()
                                    } else {
                                        val newTrack = vocal.assetbundleName
                                            .takeIf { it.isNotBlank() }
                                            ?.let { bundle ->
                                                MusicTrack(
                                                    musicId = current.id,
                                                    vocalId = vocal.id,
                                                    title = current.displayNameFor(nameLanguage),
                                                    versionLabel = vocal.caption,
                                                    audioUrl = AssetUrls.musicAudio(bundle),
                                                    jacketAssetbundleName = current.assetbundleName,
                                                    fillerMs = fillerMsOf(current.fillerSec),
                                                )
                                            }
                                        /*
                                         * 只有"这首歌就是正在播的那一首"才走原地换版本
                                         * （队列、光标都不动）。用户明确要求：
                                         * **点版本不该让在播的歌变成别的歌**，
                                         * 也不该把播放光标跳到队列里这首歌的位置。
                                         */
                                        val handled = newTrack != null && player.playVersion(newTrack)
                                        if (!handled) {
                                            // 点的是**另一首歌**的版本 = 用户要开始听这首歌：
                                            // 这首歌的版本入队，从头播（队列会被这份新队列替换）。
                                            val tracks = current.vocals.mapNotNull { v ->
                                                v.assetbundleName.takeIf { it.isNotBlank() }?.let { bundle ->
                                                    MusicTrack(
                                                        musicId = current.id,
                                                        vocalId = v.id,
                                                        title = current.displayNameFor(nameLanguage),
                                                        versionLabel = v.caption,
                                                        audioUrl = AssetUrls.musicAudio(bundle),
                                                        jacketAssetbundleName = current.assetbundleName,
                                                        fillerMs = fillerMsOf(current.fillerSec),
                                                    )
                                                }
                                            }
                                            val at = tracks.indexOfFirst { it.vocalId == vocal.id }
                                            if (at >= 0) player.playQueue(tracks, at)
                                        }
                                    }
                                },
                                onSeek = { player.seekToDisplay(it) },
                                onOpenCharacter = onOpenCharacter,
                            )
                        }
                    }
                    if (current.relatedEvents.isNotEmpty()) {
                        item {
                            EventSection(
                                events = current.relatedEvents,
                                region = region,
                                onOpenEvent = onOpenEvent,
                            )
                        }
                    }
                    current.originalVideoLink?.takeIf { it.isNotBlank() }?.let { link ->
                        item { OriginalSection(link = link) }
                    }
                    // 歌词入口：**只有明确知道这首没有歌词时才隐藏**（用户要求）。
                    // 社区索引查不到（首次且没网）时为 null，那时照常显示 ——
                    // 宁可点进去看到"这首没有歌词"，也不要把本来能用的入口藏掉。
                    if (lyricsAvailable != false) {
                        item { LyricsEntry(onClick = onOpenLyrics) }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// 顶部：曲绘 + 曲名 + 组合 chip + 分类徽章
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Hero(detail: MusicDetail, region: ServerRegion, nameLanguage: NameLanguage) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RemoteImage(
                url = AssetUrls.musicJacket(region, detail.assetbundleName),
                contentDescription = null,
                modifier = Modifier.size(112.dp).clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = detail.displayNameFor(nameLanguage),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                detail.secondaryNameFor(nameLanguage)?.let {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (detail.unitTags.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        // 组合名映射只有一份（`MusicUnitTag`），歌曲页的分类网格也用它 ——
                        // 之前这里是页面内写死的 when，会和歌曲页各写一套、慢慢走偏。
                        detail.unitTags.forEach { tag ->
                            LabelChip(text = MusicUnitTag.of(tag)?.label ?: tag)
                        }
                    }
                }
            }
        }
        if (detail.categories.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                detail.categories.forEach { LabelChip(text = it.label, color = it.color) }
            }
        }
    }
}

/**
 * `musicTags.musicTag` → 显示名。
 *
 * 取值与**游戏内选曲界面的分类一一对应**（实测 `musicTags` 的 7 个取值正好就是
 * 那排分类按钮），所以 `vocaloid` 要写成 VIRTUAL SINGER。
 */
/**
 * 小标签。
 *
 * 有 [color] 时只把**文字**染色、底色用半透明的同色 —— 不拿它当实心背景：
 * `piapro` 的官方色是纯白，当底色会在浅色主题上彻底看不见。
 */
@Composable
private fun LabelChip(text: String, color: Color? = null) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(
                color?.copy(alpha = 0.18f) ?: MaterialTheme.colorScheme.surfaceVariant,
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color ?: MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ─────────────────────────────────────────────────────────────
// 乐曲信息
// ─────────────────────────────────────────────────────────────

@Composable
private fun BasicInfo(detail: MusicDetail) {
    SectionTitle("乐曲信息")
    KeyValueRow("编号", "#${detail.id}")
    // 主标题已经是中文时才单独列日文原名，否则会重复显示两遍同一个名字
    if (detail.nameZh != null) KeyValueRow("日文原名", detail.nameJa)
    detail.pronunciation?.let { KeyValueRow("读音", it) }
    CreditRow("作曲", detail.composer, detail.creatorArtistName)
    CreditRow("编曲", detail.arranger, detail.creatorArtistName)
    CreditRow("作词", detail.lyricist, detail.creatorArtistName)
    KeyValueRow("发布时间", formatDate(detail.publishedAt))
    KeyValueRow("解锁条件", detail.releaseCondition)
    KeyValueRow("内部资源名", detail.assetbundleName)
}

/**
 * 曲师一行。
 *
 * `musics` 里作曲/作词/编曲是三个**独立字符串**，`creatorArtistId` 另指向
 * `musicArtists` 的登记名。两者常常相同（644 三个都是「水野あつ」），
 * 所以只在**不一致**时才附上登记名，避免同一行写两遍。
 */
@Composable
private fun CreditRow(label: String, value: String?, artistName: String?) {
    if (value.isNullOrBlank()) return
    val text = if (artistName != null && artistName != value) "$value（$artistName）" else value
    KeyValueRow(label, text)
}

// ─────────────────────────────────────────────────────────────
// 难度
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DifficultySection(rows: List<MusicDifficulty>) {
    SectionTitle("难度")
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        rows.forEach { row ->
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(row.kind.color.copy(alpha = 0.16f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = row.kind.abbr,
                    style = MaterialTheme.typography.labelSmall,
                    color = row.kind.color,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = row.playLevel.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "${row.noteCount} NOTE",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// 演唱版本（纯展示；阶段 3 起才有播放）
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VocalSection(
    vocals: List<MusicVocal>,
    detail: MusicDetail,
    region: ServerRegion,
    nameLanguage: NameLanguage,
    playback: PlaybackState,
    onToggleVocal: (MusicVocal) -> Unit,
    onSeek: (Long) -> Unit,
    onOpenCharacter: (Int) -> Unit,
) {
    SectionTitle("演唱版本")
    vocals.forEach { vocal ->
        val isCurrent = playback.track?.vocalId == vocal.id
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LabelChip(text = vocal.typeLabel)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = vocal.caption,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                // 试听按钮。⚠️ 播放的是 `music/long`（游戏版剪辑）并**跳过开头 blank**，
                // 时间轴也是平移过的（显示 1:41 而不是音频文件的 1:50）
                IconButton(onClick = { onToggleVocal(vocal) }) {
                    when {
                        isCurrent && playback.isBuffering -> CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                        )

                        else -> Icon(
                            // 用 isPlayRequested 而不是 isPlaying：缓冲那一两秒里
                            // 「按了播放」和「点它能暂停」必须是同一个状态，
                            // 否则会出现"显示 ▶ 但点下去是暂停"的错位。
                            imageVector = if (isCurrent && playback.isPlayRequested) {
                                Icons.Default.PauseCircleFilled
                            } else {
                                Icons.Default.PlayCircleFilled
                            },
                            contentDescription = if (isCurrent && playback.isPlayRequested) "暂停" else "试听",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                }
            }
            if (vocal.singers.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    vocal.singers.forEach { singer ->
                        SingerChip(singer = singer, region = region, onOpenCharacter = onOpenCharacter)
                    }
                }
            }
            // 只有正在播的这一条显示进度条 —— 六条都挂一根会变成噪音
            if (isCurrent) {
                Spacer(Modifier.height(4.dp))
                PlaybackProgress(playback = playback, onSeek = onSeek)
            }
        }
        RowDivider()
    }
}

/**
 * 播放进度。
 *
 * 显示的是**平移过的**时间轴（已经减掉开头空白）：644 显示 `1:41`，
 * 而不是音频文件本身的 `1:50`。拖动时换算回播放器位置（见 `data/music/Timeline.kt`）。
 *
 * 总长未知时（media3 的 duration 是异步拿到的）滑块不可拖、时间显示 `--:--`，
 * 否则会出现"总长 0:00 但已经在响"的怪异状态。
 */
@Composable
private fun PlaybackProgress(playback: PlaybackState, onSeek: (Long) -> Unit) {
    val duration = playback.displayDurationMs
    val known = duration > 0
    val fraction = if (known) {
        (playback.displayPositionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = fraction,
            onValueChange = { value -> if (known) onSeek((value * duration).toLong()) },
            enabled = known,
            modifier = Modifier.fillMaxWidth().height(24.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatClock(playback.displayPositionMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            playback.error?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Text(
                text = formatClock(duration),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 演唱者。
 *
 * 游戏内角色**可点**（跳卡牌图鉴并按该角色筛选）；社外角色（可不 / GUMI / flower…）
 * 只做纯文字展示、**不可点** —— 用户明确要求，而且它们在游戏里本来也没有角色页可去。
 */
@Composable
private fun SingerChip(singer: MusicSinger, region: ServerRegion, onOpenCharacter: (Int) -> Unit) {
    if (!singer.isGameCharacter) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(
                text = singer.displayName,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onOpenCharacter(singer.id) }
            .padding(end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RemoteImage(
            url = AssetUrls.characterAvatar(region, singer.id),
            contentDescription = null,
            // 半身像是 376×840 的竖构图：必须 TopCenter，否则圆形头像里是胸口不是脸
            modifier = Modifier.size(28.dp).clip(CircleShape),
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = singer.displayName,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ─────────────────────────────────────────────────────────────
// 相关活动 / 原曲
// ─────────────────────────────────────────────────────────────

@Composable
private fun EventSection(
    events: List<MusicEventRef>,
    region: ServerRegion,
    onOpenEvent: (Int) -> Unit,
) {
    SectionTitle("相关活动")
    events.forEach { event ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenEvent(event.eventId) }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RemoteImage(
                url = event.assetbundleName?.let { AssetUrls.eventLogo(region, it) },
                contentDescription = null,
                modifier = Modifier
                    .size(width = 72.dp, height = 40.dp)
                    .clip(RoundedCornerShape(6.dp)),
                contentScale = ContentScale.Fit,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val start = event.startAt
                val end = event.endAt
                if (start != null && end != null) {
                    Text(
                        text = "${formatDate(start)} ~ ${formatDate(end)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        RowDivider()
    }
}

@Composable
private fun OriginalSection(link: String) {
    val context = LocalContext.current
    SectionTitle("原曲")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { openExternalLink(context, link) }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "在浏览器打开原曲视频",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * 歌词入口。
 *
 * 用「整行可点」而不是按钮：它和「原曲」是同一类"去别处看"的动线，
 * 样式保持一致比多一个实心按钮更安静。
 */
@Composable
private fun LyricsEntry(onClick: () -> Unit) {
    SectionTitle("歌词")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "查看歌词（日文原文 + 中文翻译 + 假名注音）",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * 日期精确到「天」。
 *
 * 和歌曲列表用同一套（系统时区），否则列表与详情会对不上一天。
 * 游戏内的日期严格说是 JST，但列表早就用了系统时区，这里跟随现状而不是单独改一处。
 */
private fun formatDate(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate().toString()
