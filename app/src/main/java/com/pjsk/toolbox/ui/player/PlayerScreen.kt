package com.pjsk.toolbox.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.Image
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.pjsk.toolbox.data.card.Card
import com.pjsk.toolbox.data.card.CardArtPlan
import androidx.compose.ui.draw.blur
import com.pjsk.toolbox.data.player.CoverAspect
import com.pjsk.toolbox.data.player.CoverCandidate
import com.pjsk.toolbox.data.player.CoverPick
import com.pjsk.toolbox.data.player.expandCoverVariants
import com.pjsk.toolbox.data.player.matchesCoverFilter
import com.pjsk.toolbox.data.player.pickCover
import com.pjsk.toolbox.data.player.PlayerCoverMode
import com.pjsk.toolbox.data.player.pickCover
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
import com.pjsk.toolbox.data.palette.PlayerPaletteArgb
import com.pjsk.toolbox.data.palette.playerPaletteFromPixels
import com.pjsk.toolbox.ui.theme.resolveDark
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.platform.LocalContext
import com.pjsk.toolbox.ui.common.RemoteImage
import com.pjsk.toolbox.ui.common.RowDivider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

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
    // 收藏列表：切版本时给收藏歌记版本（非收藏歌点版本只当场换、不记）
    val favorites by container.appSettings.favoriteSongs.collectAsState()

    val track = state.track
    var showVersions by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var showCoverSheet by remember { mutableStateOf(false) }
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

    // ── 封面来源：歌曲曲绘 / 自定义卡面（随机） ──
    // ── 封面来源：歌曲曲绘 / 自定义卡面（按筛选条件随机） ──
    //
    // 规则（用户拍板）：
    //  - 随机**只在切歌时**重新抽，切版本不动（沿用"切版本不换色"那条规则）；
    //  - 默认方形；16:9 是可选，且会配一层模糊铺底（否则上下空出一大块，很空）；
    //  - 条件命中为空 / 卡表里找不到 → **回退曲绘**，不显示空白。
    val coverMode by container.appSettings.playerCoverMode.collectAsState()
    val coverFilter by container.appSettings.playerCoverFilter.collectAsState()
    val coverAspect by container.appSettings.playerCoverAspect.collectAsState()
    val allCards by remember { container.cardRepository.cards }.collectAsState(initial = emptyList())
    val allCharacters by remember { container.cardRepository.characters }.collectAsState(initial = emptyList())
    val cardsById = remember(allCards) { allCards.associateBy { it.id } }
    // 条件 → 候选（纯内存过滤，1400 张卡 <10ms；用 remember 避免每帧重算）
    val coverCandidates = remember(allCards, coverFilter) {
        allCards.mapNotNull { card ->
            CoverCandidate(
                id = card.id,
                characterId = card.characterId,
                rarityKey = card.rarityKey,
                trainedAvailable = card.artPlan != CardArtPlan.NORMAL_ONLY,
            ).takeIf { matchesCoverFilter(it, coverFilter) }
        }
    }
    val coverPicks = remember(coverCandidates, coverFilter.variant) {
        expandCoverVariants(coverCandidates, coverFilter.variant)
    }
    var coverPick by remember { mutableStateOf<CoverPick?>(null) }
    // 一个跨重组存活的 Random：每次新建 Random() 在同一毫秒可能拿到相同种子，
    // 表现是"随机"连着给出同一张。
    val coverRandom = remember { kotlin.random.Random(System.nanoTime()) }
    // ⚠️ key 里必须有 `coverPicks`（条件一变就重抽）。漏了它就会出现
    // "改了筛选条件、封面却还是上一张" —— 和控分那里踩过的坑同一类。
    LaunchedEffect(track.musicId, coverMode, coverPicks) {
        if (coverMode != PlayerCoverMode.CARD_ART || coverPicks.isEmpty()) {
            coverPick = null
            return@LaunchedEffect
        }
        coverPick = pickCover(coverPicks, previousCardId = coverPick?.cardId, random = coverRandom)
    }
    val currentPick = coverPick
    val coverCard: Card? = currentPick?.let { cardsById[it.cardId] }
    val coverUrl: String = if (coverCard != null && currentPick != null) {
        AssetUrls.cardImage(
            region = region,
            assetbundleName = coverCard.assetbundleName,
            // 版本由**筛选条件**决定（觉醒前 / 觉醒后 / 都要），不再写死"觉醒后优先"
            trained = currentPick.trained,
            size = AssetUrls.CardSize.LARGE,
        )
    } else {
        AssetUrls.musicJacket(region, detail?.assetbundleName ?: track.jacketAssetbundleName)
    }
    // 取色缓存的键**必须跟着封面走**：卡面模式下同一首歌每次可能显示不同卡，
    // 用 musicId 当 key 会出现"封面换了、颜色还是上一张的"。
    val coverKey = if (coverCard != null && currentPick != null) {
        "card-${coverCard.id}-" + (if (currentPick.trained) "tr" else "nm")
    } else {
        "song-${track.musicId}"
    }
    // 16:9 只在"确实有卡面"时生效：曲绘本身就是方的，套 16:9 只会把它裁扁
    val wideCover = coverAspect == CoverAspect.WIDE && coverCard != null

    // ── 封面取色（Step 3）──
    //
    // 缓存刻意允许 value 为 null：null 表示"这张封面算过、但不适合取色"，
    // 与"还没算过"是两回事 —— 否则每次重组都会重算一遍失败的结果。
    val appContext = LocalContext.current.applicationContext
    val themeMode by container.appSettings.themeMode.collectAsState()
    val darkTheme = themeMode.resolveDark()
    val paletteCache = remember { LinkedHashMap<String, PlayerPaletteArgb?>() }
    var coverPalette by remember { mutableStateOf<PlayerPaletteArgb?>(null) }

    LaunchedEffect(coverKey, darkTheme, coverUrl) {
        val id = coverKey
        if (paletteCache.containsKey(id)) {
            coverPalette = paletteCache[id]
            return@LaunchedEffect
        }
        // 先把上一张的颜色摘掉，避免"这张还没算完、却在用上张颜色"的错觉
        coverPalette = null
        val extracted = withContext(Dispatchers.IO) {
            runCatching {
                // ⚠️ 两个参数都是必须的：
                //  - size(72, 72)：量化只要几十见方就够，别让 Coil 解一张 2520×1440 的原图；
                //  - allowHardware(false)：**硬件位图读不了像素**（getPixels 会拿到全黑），
                //    这是 Coil的默认值 —— 这一条不写，现象是"取色永远返回黑色"。
                val request = ImageRequest.Builder(appContext)
                    .data(coverUrl)
                    .size(72, 72)
                    .allowHardware(false)
                    .build()
                val bitmap = (appContext.imageLoader.execute(request) as? SuccessResult)
                    ?.drawable
                    ?.toBitmap()
                    ?: return@runCatching null
                val w = bitmap.width
                val h = bitmap.height
                if (w <= 0 || h <= 0) return@runCatching null
                val pixels = IntArray(w * h)
                bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
                playerPaletteFromPixels(pixels, darkTheme)
            }.getOrNull()
        }
        paletteCache[id] = extracted
        // 只留最近 12 张：一张调色板不到 100 字节，但没必要无限涨
        while (paletteCache.size > 12) {
            paletteCache.remove(paletteCache.keys.first())
        }
        // 诊断日志：取色是"算错也不报错"的那类逻辑，出问题只能靠这行看
        // （`adb logcat -d -s PlayerPalette:I`）。null = 这张封面不适合取色，走主题色。
        android.util.Log.i(
            "PlayerPalette",
            "cover=$id dark=$darkTheme " +
                "seed=${extracted?.seed?.let { "#%06X".format(it and 0xFFFFFF) } ?: "none"} " +
                "primary=${extracted?.primary?.let { "#%06X".format(it and 0xFFFFFF) } ?: "theme"} " +
                "top=${extracted?.gradientTop?.let { "#%06X".format(it and 0xFFFFFF) } ?: "theme"}",
        )
        coverPalette = extracted
    }

    // 颜色切换用**渐变**而不是硬切：硬切会让整屏"啪"一下换色，很廉价。
    // 取不到封面时（coverPalette == null）回落全局主题色，动画自然过渡。
    val baseColors = MaterialTheme.colorScheme
    val animSpec = tween<Color>(durationMillis = 450)
    val animPrimary by animateColorAsState(coverPalette?.primary?.let { Color(it) } ?: baseColors.primary, animSpec)
    val animOnPrimary by animateColorAsState(coverPalette?.onPrimary?.let { Color(it) } ?: baseColors.onPrimary, animSpec)
    val animTop by animateColorAsState(
        coverPalette?.gradientTop?.let { Color(it) } ?: baseColors.surfaceContainerHigh,
        animSpec,
    )
    val animBottom by animateColorAsState(
        coverPalette?.gradientBottom?.let { Color(it) } ?: baseColors.background,
        animSpec,
    )
    val animOnSurface by animateColorAsState(coverPalette?.onSurface?.let { Color(it) } ?: baseColors.onSurface, animSpec)
    val animOnSurfaceVariant by animateColorAsState(
        coverPalette?.onSurfaceVariant?.let { Color(it) } ?: baseColors.onSurfaceVariant,
        animSpec,
    )
    // 只覆盖播放器真正用到的那些角色，**不整份替换** ColorScheme：
    // 换整份意味着 tertiary/error 之类也跟着偏色，而那些地方没人会检查对比度。
    val playerColors = baseColors.copy(
        primary = animPrimary,
        onPrimary = animOnPrimary,
        background = animBottom,
        surface = animBottom,
        surfaceContainerHigh = animTop,
        surfaceContainerHighest = animTop,
        onSurface = animOnSurface,
        onSurfaceVariant = animOnSurfaceVariant,
    )

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
     *
     * ## 收藏歌记版本（2026-10-04 加）
     *
     * 切到的这首歌如果在收藏里，就把这个版本记进 `appSettings.preferredVocals` ——
     * 以后这首歌无论从哪播（列表 ▶、收藏列表、详情页另起队列）都用这个版本，
     * 优先级高于顶栏全局态。非收藏歌点版本只当场换、不记。
     *
     * ⚠️ 记忆写在切换之前：这样无论 `playVersion` 走成（原地换）还是落进兜底
     * （另起队列），记忆都写进去了。`trackOf` 返回 null（没素材名）会提前 return，
     * 那种没音源的版本也不该记。
     */
    fun switchTo(target: MusicVocal) {
        val newTrack = trackOf(target) ?: return
        // 收藏歌记版本：切了以后这首收藏歌各处播都用这个版本
        if (track.musicId in favorites) {
            container.appSettings.setPreferredVocal(track.musicId, target.id)
        }
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
    // 只把**播放器这一屏**包进封面配色里（`playerColors`）：
    // 下面的弹层（版本/队列/定时器）故意留在全局主题 —— 它们是"列表」，不是专辑封面，
    // 跟着封面色变反而会让「这弹层属于播放器还是属于 App」变得暧昧。
    MaterialTheme(colorScheme = playerColors) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            // 背景从「一个纯色 surface」改成 **M3 容器色阶的竖向渐变**。
            //
            // 为什么：Material You 的观感有一半来自"同一支色系里分层"，而不是"整屏一个颜色"。
            // 上面深一档（surfaceContainerHigh）压住封面那张图，往下渐到 background，
            // 于是封面看起来像"浮在页面上"而不是"贴在一块白板上"。
            //
            // ⚠️ 这一层是**将来封面取色的落点**（Step 3）：换成由封面种子色派生出的
            // 两个容器色即可，版式一行都不用动。现在先走主题色，保证没有封面/取色失败时也不难看。
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                        MaterialTheme.colorScheme.background,
                    ),
                ),
            )
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
        //
        // 两种比例各用各的：
        //  - 曲绘 = **正方形**（原来的样子，1:1 的曲绘不留白）；
        //  - 卡面 = **16:9 横幅**（卡面原始 2520×1440，硬塞进正方形要裁掉左右各 21%）。
        //    340dp 宽时只有 191dp 高，比正方形**更矮**，底部按钮的空间反而更宽松。
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            val cardMode = coverCard != null
            // 卡面模式下按设置选比例；曲绘永远是正方形（它本身就是方的）
            val cardWide = cardMode && wideCover
            val coverWidth = if (cardWide) {
                minOf(340.dp, maxWidth, maxHeight * 16f / 9f)
            } else {
                minOf(340.dp, maxHeight, maxWidth)
            }
            val coverHeight = if (cardWide) coverWidth * 9f / 16f else coverWidth

            // 16:9 时**同一张图模糊铺满这一整块**：否则 340×191 的横幅浮在 ~380dp 高的
            // 区域里，上下各空 95dp，看起来就是"很空"（用户实测反馈）。
            // 铺底顺便让封面取色的效果更明显 —— 整个上半屏都是封面色。
            // ⚠️ Modifier.blur 要 API 31+；低版本它是空操作，所以**必须**再叠一层渐变遮罩，
            //    否则低版本上会看到一张没模糊的图怼在后面。
            if (cardWide) {
                Box(modifier = Modifier.matchParentSize()) {
                    RemoteImage(
                        url = coverUrl,
                        contentDescription = null,
                        modifier = Modifier.matchParentSize().blur(56.dp),
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.35f),
                                        MaterialTheme.colorScheme.background.copy(alpha = 0.92f),
                                    ),
                                ),
                            ),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(coverWidth, coverHeight)
                    // 圆角 22dp → **28dp**：M3 里"大圆角 + 有落差"是封面的标准处理，
                    // 22dp 在小屏上偏"方"，加大一档更接近 Google 系播放器的观感。
                    .shadow(
                        elevation = 18.dp,
                        shape = RoundedCornerShape(28.dp),
                        clip = false,
                        // 阴影跟着主题色走（默认是纯黑，M3 里阴影也该带一点主色调）
                        ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    )
                    .clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .clickable { onOpenDetail(track.musicId) },
            ) {
                RemoteImage(
                    url = coverUrl,
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

        // ── 波浪进度条（用户 0.1.37 拍板：换回波浪，线加粗）──
        //
        // 0.1.36 曾换成 M3 Slider，用户看过之后决定换回波浪条 —— 它是这套 UI 的招牌。
        // 线宽从写死的 3px 改成 **2dp**（见 SquigglySeekBar 的参数注释）：
        // 原来那个 3f 是像素，在 520dpi 上只有 0.92dp，细得几乎看不见。
        //
        // 交互语义维持原样：拖动只更新本地预览，**松手才 seek 一次**。
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
            // 封面来源（曲绘 / 自定义卡面）。放在最后：它是"装饰性"设置，
            // 不该挤在歌词/版本/模式这些日常操作中间。
            PlayerAction(
                label = if (coverMode == PlayerCoverMode.CARD_ART) "卡面" else "封面",
                icon = Icons.Default.Image,
            ) { showCoverSheet = true }
        }
    }
    }

    // ── 定时器弹层 ──
    //
    // ⚠️ **故意没有「关闭」这一项**（用户要求）：默认就是"一直播放"。
    // 取消的办法是**再点一次当前选中的那一项**（规则在 sleepTimerClickAction 里，自检守着），
    // 弹层底部写一行小字说明，不然用户设完就撤不掉了。
    // ── 封面来源弹层（曲绘 / 自定义卡面）──
    // 和其他几个弹层一样放在 MaterialTheme(playerColors) 之外：这是"列表类"界面，
    // 跟着封面色变反而会让"这属于播放器还是属于 App"变得暧昧。
    if (showCoverSheet) {
        PlayerCoverSheet(
            mode = coverMode,
            aspect = coverAspect,
            filter = coverFilter,
            cards = allCards,
            characters = allCharacters,
            nameLanguage = nameLanguage,
            region = region,
            onModeChange = { container.appSettings.setPlayerCoverMode(it) },
            onAspectChange = { container.appSettings.setPlayerCoverAspect(it) },
            onFilterChange = { container.appSettings.setPlayerCoverFilter(it) },
            onDismiss = { showCoverSheet = false },
        )
    }

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


