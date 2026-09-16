package com.pjsk.toolbox.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Subject
import androidx.compose.material.icons.filled.SwapHoriz
// 闹钟用**线条**那套（用户要求：简单线条、简约），不要实心
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pjsk.toolbox.PjskApp
import com.pjsk.toolbox.data.music.MusicDetail
import com.pjsk.toolbox.data.music.MusicVocal
import com.pjsk.toolbox.data.music.TIME_UNSET_MS
import com.pjsk.toolbox.data.music.fillerMsOf
import com.pjsk.toolbox.data.music.formatClock
import com.pjsk.toolbox.data.music.fractionOf
import com.pjsk.toolbox.data.music.positionOfFraction
import com.pjsk.toolbox.data.player.CustomTimerAction
import com.pjsk.toolbox.data.player.MAX_CUSTOM_MINUTES
import com.pjsk.toolbox.data.player.MIN_CUSTOM_MINUTES
import com.pjsk.toolbox.data.player.MusicTrack
import com.pjsk.toolbox.data.player.PlayMode
import com.pjsk.toolbox.data.player.SleepTimerAction
import com.pjsk.toolbox.data.player.SleepTimerOption
import com.pjsk.toolbox.data.player.customTimerClickAction
import com.pjsk.toolbox.data.player.formatTimerClock
import com.pjsk.toolbox.data.player.isPresetSleepMinutes
import com.pjsk.toolbox.data.player.parseCustomSleepMinutes
import com.pjsk.toolbox.data.player.sleepTimerClickAction
import com.pjsk.toolbox.data.player.sleepTimerOptionOf
import com.pjsk.toolbox.data.player.sleepTimerRemainingMs
import com.pjsk.toolbox.data.remote.AssetUrls
import com.pjsk.toolbox.data.remote.ServerRegion
import com.pjsk.toolbox.ui.common.RemoteImage
import com.pjsk.toolbox.ui.common.RowDivider
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * 全屏播放器模式。
 *
 * 布局照留声器的 `full_player.xml`，但**只放真的能用的东西**：
 *
 * | 留声器的行 | 我们 |
 * | --- | --- |
 * | 顶部三个圆钮（收起 / 睡眠定时 / 倍速） | **收起** + **定时器（闹钟）**；倍速不做（2 分钟的剪辑用不上） |
 * | 大封面 | 大封面（**点了进歌曲详情**，用户明确要求的动线） |
 * | 曲名 / 艺术家 | 曲名 / 版本 + 演唱者 |
 * | 波浪进度条 | [SquigglySeekBar]（自己重画，见那个文件） |
 * | 位置 \| 音质徽章 \| 时长 | 位置 \| 错误提示 \| 时长（音质徽章对我们没意义） |
 * | 上一首 / 播放 / 下一首 | **上一首 / 播放 / 下一首**（＝队列里的前后，换版本走「版本」钮） |
 * | 五个功能钮 | **歌词 / 版本 / 播放模式 / 列表**，**钉在屏幕最底下** |
 *
 * ## 竖排的骨架（2026-09-15 用户要求重排）
 *
 * ```
 * [⌄ 收起]                    [剩余时间 ⏰ 定时器]   ← 顶部（已让出状态栏）
 *         ┌──────────────┐
 *         │    大封面     │                        ← 只有这一块是弹性的
 *         └──────────────┘
 *          曲名 / 版本·演唱者
 *          ～～～波浪进度条～～～
 *          0:42        1:41
 *              ⏮  ▶  ⏭
 * [歌词] [版本] [播放模式] [列表]                    ← 钉底（手势条 + 12dp）
 * ```
 *
 * - **只有封面那句是弹性的**：屏越高，封面上下留白越多，于是下面的曲名/进度条/播放键
 *   整体下移，四个功能钮贴在屏幕底部 —— 用户要的就是这个"往下走"。
 * - 副作用是**不能再整屏滚动**（滚动列里 `weight` 拿不到有界高度）。
 *   安全性由"封面自己缩"保证：其余元素加起来 400dp 出头，任何手机都放得下。
 * - 顶部/底部补上了系统栏内边距。**原来没做**，整屏 edge-to-edge 的后果是
 *   左上角那个「收起」压在状态栏（时间/电量）底下 —— 加了右上角的闹钟之后更明显。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    region: ServerRegion,
    onCollapse: () -> Unit,
    onOpenDetail: (Int) -> Unit,
    onOpenLyrics: (Int) -> Unit,
    /** 播放模式变了要存起来（下次启动还是这个模式）。 */
    onPlayModeChange: (PlayMode) -> Unit = {},
) {
    val container = (LocalContext.current.applicationContext as PjskApp).container
    val player = container.musicPlayer
    val state by player.state.collectAsState()
    val nameLanguage by container.appSettings.nameLanguage.collectAsState()
    val storedPlayMode by container.appSettings.playMode.collectAsState()

    val track = state.track
    var showVersions by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var showTimer by remember { mutableStateOf(false) }
    // 自定义时长：输入框展开状态 + 用户敲的内容（不持久化，弹层关掉就忘）
    var showCustomInput by remember { mutableStateOf(false) }
    var customText by remember { mutableStateOf("") }
    // null = 还不知道（索引没加载出来），true/false = 确定有没有歌词
    var lyricsAvailable by remember(track?.musicId) { mutableStateOf<Boolean?>(null) }

    // 进入播放器时把**存下来的**播放模式套上去（模式是跨会话保留的）
    LaunchedEffect(Unit) { player.setPlayMode(storedPlayMode) }

    // 正在播的那首歌的完整资料（换版本、封面、演唱者都要用）
    var detail by remember(track?.musicId) { mutableStateOf<MusicDetail?>(null) }
    LaunchedEffect(track?.musicId) {
        val id = track?.musicId ?: return@LaunchedEffect
        detail = container.musicRepository.detail(id)
        lyricsAvailable = container.lyricsRepository.hasLyrics(id)
    }

    // 没在播（刚点了停止）→ 自动收起，不要停在一个空白的全屏页
    LaunchedEffect(track) {
        if (track == null) onCollapse()
    }
    if (track == null) return

    val vocals = detail?.vocals.orEmpty()
    val index = vocals.indexOfFirst { it.id == track.vocalId }
    val current: MusicVocal? = vocals.getOrNull(index)

    /** 把一个演唱版本变成可播放的轨（版本弹层和兜底都用它）。 */
    fun trackOf(vocal: MusicVocal): MusicTrack? {
        val bundle = vocal.assetbundleName.takeIf { it.isNotBlank() } ?: return null
        return MusicTrack(
            musicId = track.musicId,
            vocalId = vocal.id,
            title = detail?.displayNameFor(nameLanguage) ?: track.title,
            versionLabel = vocal.caption,
            audioUrl = AssetUrls.musicAudio(bundle),
            jacketAssetbundleName = detail?.assetbundleName ?: track.jacketAssetbundleName,
            fillerMs = fillerMsOf(detail?.fillerSec),
        )
    }

    /**
     * 换到另一个版本。
     *
     * ⚠️ **队列、播放光标都不动**（用户拍板）：只把当前播放这一项**原地**换成新版本，
     * 进度接上。之前两版分别是"重建队列（=把在听的列表顶掉）"和"跳到队列里这首歌的位置
     * （=点个版本却在播别的歌）"，都被用户否掉了。
     *
     * 这个弹层列的就是**当前这首歌**的版本，所以正常永远走原地换；
     * 兜底分支（理论上走不到）才另起队列，而且**从头播**（沿用别的歌的位置是错的）。
     */
    fun switchTo(target: MusicVocal) {
        val newTrack = trackOf(target) ?: return
        if (player.playVersion(newTrack)) return
        val tracks = vocals.mapNotNull(::trackOf)
        val index = tracks.indexOfFirst { it.vocalId == target.id }
        if (index >= 0) player.playQueue(tracks, index)
    }

    val duration = state.displayDurationMs
    val known = duration > 0 && duration != TIME_UNSET_MS

    val sleepTimer = state.sleepTimer
    val sleepTimerOption = sleepTimerOptionOf(sleepTimer)
    // 剩余时间走的是**墙钟**，和播放位置无关，所以单独每秒刷一次
    //（播放中的 ticker 只管位置，而且暂停时它会停）。
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(sleepTimer.endsAtMs) {
        if (sleepTimer.endsAtMs == null) return@LaunchedEffect
        while (isActive) {
            nowMs = System.currentTimeMillis()
            delay(1_000L)
        }
    }
    val sleepRemaining = sleepTimerRemainingMs(sleepTimer, nowMs)

    /*
     * 整屏的骨架：**只有封面那一块是弹性的**。
     *
     *  - 屏幕够高 → 多余空间落在封面上下，于是下面的曲名/进度条/播放键整体**下移**；
     *  - 屏幕矮 或 系统字体调大 → 封面自己缩小（最多 340dp），
     *    **绝不会把最下面那排按钮挤出屏幕**（其余元素加起来只有 400dp 出头）。
     *
     * ⚠️ 因此这里**不能**再用 `verticalScroll`：滚动列里 `weight` 拿不到有界高度，
     * "把按钮钉在底部"和"整屏可滚"在同一个 Column 里是互斥的。
     */
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            // 顶部让出状态栏：整屏是 edge-to-edge 的，原来没做这一步，
            // 左上角那个「收起」其实压在状态栏（时间/电量）底下。
            .statusBarsPadding()
            .padding(horizontal = 24.dp),
    ) {
        // ── 顶部：左「收起」／右「定时器（闹钟）」──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onCollapse,
                // 留声器顶部那三个圆钮是 52dp，这里照它的尺寸
                modifier = Modifier.size(52.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "收起播放器",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 定时器开着时，在图标左边显示剩余时间（简约：就一个 12:34）
                sleepRemaining?.let { remain ->
                    Text(
                        // 倒计时专用写法：超过 1 小时会变成 1:30:00（不用歌曲那套 m:ss）
                        text = formatTimerClock(remain),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                IconButton(onClick = { showTimer = true }, modifier = Modifier.size(52.dp)) {
                    Icon(
                        // 用户要求：**线条**那套，简单、简约
                        imageVector = Icons.Outlined.Alarm,
                        contentDescription = "定时器",
                        tint = if (sleepTimer.isActive) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }

        // ── 大封面：吃了剩余空间，点了进歌曲详情 ──
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            val side = minOf(340.dp, maxHeight, maxWidth)
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(side)
                    // 圆角 22dp 照留声器的 album_cover_frame
                    .clip(RoundedCornerShape(22.dp))
                    .clickable { onOpenDetail(track.musicId) },
            ) {
                RemoteImage(
                    url = AssetUrls.musicJacket(
                        region,
                        detail?.assetbundleName ?: track.jacketAssetbundleName,
                    ),
                    contentDescription = "查看歌曲详情",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        Spacer(Modifier.height(14.dp))

        // ── 曲名 / 版本 + 演唱者 ──
        Text(
            text = detail?.displayNameFor(nameLanguage) ?: track.title,
            // 字号照留声器的 full_song_name（24sp / weight500）
            fontSize = 24.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        detail?.secondaryNameFor(nameLanguage)?.let { secondary ->
            Spacer(Modifier.height(2.dp))
            Text(
                text = secondary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = buildString {
                append(current?.caption ?: track.versionLabel)
                val singers = current?.singers?.map { it.displayName }.orEmpty()
                if (singers.isNotEmpty()) {
                    append(" · ")
                    append(singers.joinToString("、"))
                }
            },
            // 字号照留声器的 full_song_artist（19sp）
            fontSize = 19.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))

        // ── 波浪进度条 ──
        SquigglySeekBar(
            fraction = fractionOf(state.displayPositionMs, duration),
            enabled = known,
            onSeek = { fraction -> player.seekToDisplay(positionOfFraction(fraction, duration)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = formatClock(state.displayPositionMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.error?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 170.dp),
                )
            }
            Text(
                text = formatClock(duration),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(6.dp))

        // ── transport：上一版本 / 播放暂停 / 下一版本 ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // ⏮/⏭ = **队列里的上一首/下一首**（有队列之后就该是这个意思，和留声器一致）。
            // "换版本"由下面那个「版本」钮负责。
            IconButton(onClick = { player.previous() }, enabled = state.hasQueue) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = "上一首",
                    modifier = Modifier.size(30.dp),
                )
            }
            Spacer(Modifier.width(24.dp))
            FilledIconButton(
                onClick = { player.togglePlayPause() },
                // 90dp 照留声器的 sheet_mid_button
                modifier = Modifier.size(90.dp),
            ) {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (state.isPlaying) "暂停" else "播放",
                    modifier = Modifier.size(46.dp),
                )
            }
            Spacer(Modifier.width(24.dp))
            IconButton(onClick = { player.next() }, enabled = state.hasQueue) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "下一首",
                    modifier = Modifier.size(30.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))

        // ── 功能钮（**钉在屏幕最底下**）──
        //
        // 留声器那排是五个钮均分整行；用户改成**整排居中**：
        // 「歌词 / 版本 / 播放模式 / 列表」按各自的宽度排在**屏幕中间**，左右边距一样。
        // ⚠️ 刻意**不用 `weight(1f)`**：均分是把每个钮塞进 1/N 的格子再靠左对齐，
        // 行会被拉满整宽、看起来偏左（用户反馈的就是这个）；而且**少一个钮时会更明显**。
        // 居中的排法天然不怕少钮 —— 歌词没歌词时、版本只有一个版本时，
        // 剩下的钮会重新居到中间。
        //
        // ⚠️ 「歌词」只在**明确知道这首没有歌词**时才隐藏（查社区索引）。
        // 这次不动它 —— 用户明确说过"歌词索引不用改"。
        //
        // 底部这点空白 = 系统手势条（navigationBarsPadding，主页的底部导航栏也是这么让的）
        // + 12dp。用户要求"参考主页下方的导航栏"，所以不用硬编某个机型的数字。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(top = 12.dp, bottom = 12.dp),
            // 居中 + 均匀间距：几个钮都按自己的宽度排，整体落在屏幕正中
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (lyricsAvailable != false) {
                PlayerAction(label = "歌词", icon = Icons.Default.Subject) {
                    onOpenLyrics(track.musicId)
                }
            }
            PlayerAction(
                label = "版本",
                icon = Icons.Default.SwapHoriz,
                enabled = vocals.size > 1,
            ) { showVersions = true }
            // 点一次换一个模式：列表播放 → 单曲循环 → 随机播放
            PlayerAction(label = state.playMode.label, icon = Icons.Default.Repeat) {
                val mode = state.playMode.next()
                player.setPlayMode(mode)
                onPlayModeChange(mode)
            }
            PlayerAction(label = "列表", icon = Icons.Default.QueueMusic) {
                showQueue = true
            }
        }
    }

    // ── 定时器弹层 ──
    //
    // ⚠️ **故意没有「关闭」这一项**（用户要求）：默认就是"一直播放"。
    // 取消的办法是**再点一次当前选中的那一项**（规则在 sleepTimerClickAction 里，自检守着），
    // 弹层底部写一行小字说明，不然用户设完就撤不掉了。
    if (showTimer) {
        ModalBottomSheet(
            onDismissRequest = { showTimer = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // 选项 + 自定义输入框加起来快一屏高了，给它一个上限并能滚，
                    // 否则小屏（或键盘弹起来时）会被裁掉一块。
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp),
            ) {
                Text(
                    text = "定时器",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 6.dp),
                )
                RowDivider()
                SleepTimerOption.entries.forEach { option ->
                    val selected = option == sleepTimerOption
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            // 点"已经选中的那一项"= 取消；点别的 = 换成它
                            .clickable {
                                when (sleepTimerClickAction(sleepTimer, option)) {
                                    SleepTimerAction.CLEAR -> player.clearSleepTimer()

                                    SleepTimerAction.SET_MINUTES ->
                                        option.minutes?.let { player.setSleepTimer(it) }

                                    SleepTimerAction.SET_TRACK_END ->
                                        player.setSleepTimerAtTrackEnd()
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected, onClick = null)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = option.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                    RowDivider()
                }

                // ── 自定义时长（用户要求：自己输入分钟数）──
                //
                // 规则和上面几项一致：**已经是自定义定时器 → 再点一次取消**；
                // 否则展开输入框。这样"取消"这件事在整个弹层里只有一个说法。
                val customActive = sleepTimer.minutes != null &&
                    !isPresetSleepMinutes(sleepTimer.minutes)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            when (customTimerClickAction(sleepTimer)) {
                                CustomTimerAction.CLEAR -> {
                                    player.clearSleepTimer()
                                    showCustomInput = false
                                }

                                CustomTimerAction.OPEN_INPUT -> {
                                    // 预填上次输入的值，省得每次重敲
                                    if (customText.isBlank()) {
                                        customText = sleepTimer.minutes?.toString().orEmpty()
                                    }
                                    showCustomInput = true
                                }
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = customActive, onClick = null)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (customActive) {
                            "自定义 ${sleepTimer.minutes} 分钟"
                        } else {
                            "自定义（分钟）"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (customActive) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
                if (showCustomInput) {
                    val parsed = parseCustomSleepMinutes(customText)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 46.dp, end = 12.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = customText,
                            onValueChange = { customText = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            label = { Text("分钟数") },
                            // 只给数字键盘：这个输入框只接受 1~600，别的键按了也没用
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                            ),
                            isError = customText.isNotBlank() && parsed == null,
                            supportingText = if (customText.isNotBlank() && parsed == null) {
                                { Text("请输入 $MIN_CUSTOM_MINUTES~$MAX_CUSTOM_MINUTES 之间的分钟数") }
                            } else {
                                null
                            },
                        )
                        Spacer(Modifier.width(8.dp))
                        TextButton(
                            onClick = {
                                parsed?.let {
                                    player.setSleepTimer(it)
                                    showCustomInput = false
                                }
                            },
                            enabled = parsed != null,
                        ) { Text("确定") }
                    }
                }
                RowDivider()
                Text(
                    text = "不选就是一直播放；再点一次已经选中的那一项可以取消。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }

    if (showVersions && vocals.isNotEmpty()) {
        ModalBottomSheet(
            onDismissRequest = { showVersions = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp),
            ) {
                Text(
                    text = "演唱版本（${vocals.size} 个）",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 6.dp),
                )
                RowDivider()
                vocals.forEach { vocal ->
                    val isCurrent = vocal.id == track.vocalId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showVersions = false
                                if (!isCurrent) switchTo(vocal)
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = vocal.caption,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isCurrent) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                            val singers = vocal.singers.joinToString("、") { it.displayName }
                            if (singers.isNotBlank()) {
                                Text(
                                    text = singers,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (isCurrent) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "正在播放",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    RowDivider()
                }
                Text(
                    text = "换版本会保持当前播放进度。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }

    // ── 队列面板：**只显示歌名**（用户要求）──
    if (showQueue && state.queue.isNotEmpty()) {
        ModalBottomSheet(
            onDismissRequest = { showQueue = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp),
            ) {
                Text(
                    text = "播放列表（${state.queue.size} 首）· ${state.playMode.label}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 6.dp),
                )
                RowDivider()
                state.queue.forEachIndexed { queueIndex, item ->
                    val isCurrent = queueIndex == state.currentIndex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showQueue = false
                                if (!isCurrent) player.jumpTo(queueIndex)
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isCurrent) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (isCurrent) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "正在播放",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    RowDivider()
                }
            }
        }
    }
}

/**
 * 一个功能钮：**图标 + 文字**。
 *
 * 文字是必须的 —— 图标（尤其"版本""详情"）光看图标认不出是什么意思，
 * 而播放器面板上没有别的地方能解释它们。
 */
@Composable
private fun PlayerAction(
    label: String,
    icon: ImageVector,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick, enabled = enabled) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(imageVector = icon, contentDescription = label, modifier = Modifier.size(22.dp))
            Text(text = label, style = MaterialTheme.typography.labelSmall)
        }
    }
}
