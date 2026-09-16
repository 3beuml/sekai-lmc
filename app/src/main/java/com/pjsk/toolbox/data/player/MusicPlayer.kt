package com.pjsk.toolbox.data.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.pjsk.toolbox.data.home.HomeMusic
import com.pjsk.toolbox.data.music.fillerMsOf
import com.pjsk.toolbox.data.remote.AssetUrls
import com.pjsk.toolbox.data.music.TIME_UNSET_MS
import com.pjsk.toolbox.data.music.displayDurationMs
import com.pjsk.toolbox.data.music.displayPositionMs
import com.pjsk.toolbox.data.music.playerPositionMs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.io.File

/**
 * 一条可以播放的「轨」＝一个演唱版本。
 *
 * 注意播放单元是**曲**、版本是它的属性（用户拍板）：界面上切版本时
 * 只是换一条轨，播放进度要接上（见 [MusicPlayer.playTrack] 的 `startFromDisplayMs`）。
 */
data class MusicTrack(
    val musicId: Int,
    val vocalId: Int,
    /** 曲名（界面上显示用，已按名称语言选好）。 */
    val title: String,
    /** 版本说明，如「世界ver.」。 */
    val versionLabel: String,
    val audioUrl: String,
    /** 曲绘素材名（`jacket_s_644`）：底部状态栏和全屏播放器都要显示封面。 */
    val jacketAssetbundleName: String,
    /** `musics.fillerSec` 换算成毫秒：开头那段空白，播放要跳过。 */
    val fillerMs: Long,
)

/**
 * 播放模式。对应 ExoPlayer 的 `repeatMode` + `shuffleModeEnabled`：
 *
 * | 模式 | repeatMode | shuffle |
 * | --- | --- | --- |
 * | [SEQUENTIAL] | `REPEAT_MODE_ALL` | 关 |
 * | [REPEAT_ONE] | `REPEAT_MODE_ONE` | 关 |
 * | [SHUFFLE] | `REPEAT_MODE_ALL` | 开 |
 *
 * 「列表播放」用 `ALL` 而不是 `OFF`：列表播到最后**回到第一首继续**，
 * 这才是音乐 App 里"列表播放"的意思（`OFF` 会在末尾停住）。
 */
enum class PlayMode(val label: String) {
    SEQUENTIAL("列表播放"),
    REPEAT_ONE("单曲循环"),
    SHUFFLE("随机播放"),
    ;

    /** 循环到下一个模式（点一次换一个）。 */
    fun next(): PlayMode = entries[(ordinal + 1) % entries.size]
}

/**
 * 定时器（播放器右上角那个闹钟图标）。**默认不设定时器 = 一直播放**。
 *
 * 用户明确要求**不要"关闭"这一项**：所以取消的方式是"再点一次当前选中的那一项"
 * （规则见 [sleepTimerClickAction]，自检里守着）。
 */
data class SleepTimerState(
    /** 选中的固定时长（分钟）；null = 没选固定时长。 */
    val minutes: Int? = null,
    /** 固定时长的**到点时刻**（墙钟毫秒）。 */
    val endsAtMs: Long? = null,
    /** 选了「播完这首歌就停」。 */
    val atTrackEnd: Boolean = false,
) {
    val isActive: Boolean get() = minutes != null || atTrackEnd
}

/**
 * 定时器的可选项。
 *
 * ⚠️ 用**墙钟**（`System.currentTimeMillis`）算到点时刻，不是"播放了多少毫秒" ——
 * 因为用户的意思是"我希望 23:30 之前停"，中途手动暂停也不该让倒计时停下来。
 */
enum class SleepTimerOption(val label: String, val minutes: Int?) {
    MIN_5("5 分钟后", 5),
    MIN_10("10 分钟后", 10),
    MIN_15("15 分钟后", 15),
    MIN_30("30 分钟后", 30),
    MIN_60("60 分钟后", 60),
    TRACK_END("播完这首歌就停", null),
}

/** 点定时器弹层里某一项该做什么。**纯函数**，自检里断言。 */
enum class SleepTimerAction { CLEAR, SET_MINUTES, SET_TRACK_END }

/** 点「自定义」那一行该做什么。 */
enum class CustomTimerAction { OPEN_INPUT, CLEAR }

/** 自定义时长的下限（分钟）。0 / 负数都不合法。 */
const val MIN_CUSTOM_MINUTES = 1

/**
 * 自定义时长的上限（分钟）= 10 小时。
 *
 * 定时器是"睡前停"，再长没有意义；顺手挡住 999999 这种输入 ——
 * 不然会算出一个离谱的到点时刻，界面上显示一个只会越来越大的数字。
 */
const val MAX_CUSTOM_MINUTES = 600

/**
 * 这个分钟数是不是**预设项之一**。不是（但又有值）= 用户自定义的时长。
 *
 * 界面靠它决定"自定义"那一行要不要点亮。
 */
fun isPresetSleepMinutes(minutes: Int?): Boolean =
    minutes != null && SleepTimerOption.entries.any { it.minutes == minutes }

/**
 * 点「自定义」那一行要做什么：**已经是自定义定时器 → 取消**（和其他项同一条规则：
 * 再点一次已选中的就取消）；否则 → 展开输入框。
 */
fun customTimerClickAction(timer: SleepTimerState): CustomTimerAction =
    if (timer.minutes != null && !isPresetSleepMinutes(timer.minutes)) {
        CustomTimerAction.CLEAR
    } else {
        CustomTimerAction.OPEN_INPUT
    }

/**
 * 解析用户自己输入的分钟数。**纯函数**，自检里断言。
 *
 * 返回 null = 不合法（空的 / 不是数字 / 0 / 超过 [MAX_CUSTOM_MINUTES]）。
 * 前后空格容忍（手机键盘很容易多敲一个空格）。
 */
fun parseCustomSleepMinutes(text: String): Int? =
    text.trim().toIntOrNull()?.takeIf { it in MIN_CUSTOM_MINUTES..MAX_CUSTOM_MINUTES }

/**
 * 点某一项要做什么：**点的就是当前选中的那一项 → 取消；否则 → 改成这一项**。
 *
 * 这就是"没有「关闭」选项时怎么取消"的答案（用户要求不放关闭项）。
 */
fun sleepTimerClickAction(timer: SleepTimerState, option: SleepTimerOption): SleepTimerAction =
    if (sleepTimerOptionOf(timer) == option) {
        SleepTimerAction.CLEAR
    } else if (option.minutes != null) {
        SleepTimerAction.SET_MINUTES
    } else {
        SleepTimerAction.SET_TRACK_END
    }

/** 当前选中的定时器项；没设定时器 = null（= 一直播放）。 */
fun sleepTimerOptionOf(timer: SleepTimerState): SleepTimerOption? = when {
    timer.atTrackEnd -> SleepTimerOption.TRACK_END
    timer.minutes != null -> SleepTimerOption.entries.firstOrNull { it.minutes == timer.minutes }
    else -> null
}

/**
 * 剩余多少毫秒（固定时长那种）。没设固定时长 = null；已经过了 = 0（不返回负数）。
 */
fun sleepTimerRemainingMs(timer: SleepTimerState, nowMs: Long): Long? =
    timer.endsAtMs?.let { (it - nowMs).coerceAtLeast(0L) }

/**
 * 定时器剩余时间的写法：**不足 1 小时用 `m:ss`**（7:00），**1 小时以上用 `h:mm:ss`**（1:30:00）。
 *
 * ⚠️ 不复用 `formatClock`：那是歌曲时间轴的写法（`m:ss`），会把 60 分钟印成 `60:00`、
 * 把自定义的 10 小时印成 `600:00` —— 没人看得懂。这里才是"倒计时"该有的样子。
 */
fun formatTimerClock(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val total = ms / 1000
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

/** 播放状态。UI 只读这个。 */
data class PlaybackState(
    val track: MusicTrack? = null,
    /** 播放队列（"接下来会播什么"）。 */
    val queue: List<MusicTrack> = emptyList(),
    /** 当前播放项在 [queue] 里的下标；-1 = 没有。 */
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    /**
     * **"用户想让它播"**（ExoPlayer 的 `playWhenReady`），不是"现在真的有声音"。
     *
     * 列表行上那个 ▶/⏸ 用它来判断：用 [isPlaying] 的话，点下去到出声之间有一两秒
     * 缓冲期，图标会在 ▶ 和 ⏸ 之间闪一下；而且缓冲期点"暂停"会因为 `isPlaying == false`
     * 被当成"继续播放"，等于按了没反应。播完之后（STATE_ENDED）算 false —— 那时该显示 ▶
     * 让人重新播。
     */
    val isPlayRequested: Boolean = false,
    /** **显示**位置（已经减掉开头空白）。 */
    val displayPositionMs: Long = 0L,
    /** **显示**总长（已经减掉开头空白）；未知时为 [TIME_UNSET_MS]。 */
    val displayDurationMs: Long = TIME_UNSET_MS,
    val error: String? = null,
    val playMode: PlayMode = PlayMode.SEQUENTIAL,
    /** 定时器（闹钟图标）。默认 [SleepTimerState] 全空 = 一直播放。 */
    val sleepTimer: SleepTimerState = SleepTimerState(),
) {
    val hasQueue: Boolean get() = queue.size > 1
}

/**
 * 列表行右侧那个播放键的三种形态。
 *
 * | 形态 | 图标 | 点下去 |
 * | --- | --- | --- |
 * | [IDLE] | ▶ | 整份列表入队，从这首开始播 |
 * | [PLAYING] | ⏸ | 暂停 |
 * | [PAUSED] | ▶ | 继续播（不回到开头） |
 */
enum class RowPlayState { IDLE, PLAYING, PAUSED }

/**
 * 这一行的播放键该是哪种形态。**纯函数**，自检里断言过。
 *
 * 判据用 `isPlayRequested`（"用户想让它播"）而不是"现在是否真的有声音"：
 * 缓冲那一两秒里图标不该闪，而且缓冲时点暂停必须真的停下。
 * 匹配用 `musicId` —— 播放单元是**曲**，版本只是属性。
 */
fun rowPlayState(
    playingMusicId: Int?,
    musicId: Int,
    isPlayRequested: Boolean,
): RowPlayState = when {
    playingMusicId != musicId -> RowPlayState.IDLE
    isPlayRequested -> RowPlayState.PLAYING
    else -> RowPlayState.PAUSED
}

/**
 * 点某个"演唱版本"时该怎么落地。**纯函数**，自检里断言。
 *
 * 规则是用户拍板的（2026-09-16 两轮才定下来）：
 *
 * 1. **切版本不许动队列**（第一轮反馈）；
 * 2. **切版本也不许改变"现在播的是哪一首"**（第二轮反馈：
 *    「在播放器的详细界面里，点击版本后跳转到其他歌里了」）。
 *
 * 所以只剩两条路：**就是这首歌 → 原地换；不是这首歌 → 交给调用方另起队列**。
 * ⚠️ 中间那版还有个 `JUMP_TO_QUEUE_ITEM`（"跳到队列里这首歌的位置"），
 * 正是用户第二轮报的那个行为，**已删掉**。
 */
enum class VersionPlayAction {
    /** 这首歌**就是当前播放项** → 原地换版本，进度接上，队列一个字不动。 */
    SWAP_CURRENT,

    /** 不是当前播放项（或者读不到当前项）→ 调用方自己决定（详情页会另起一份队列）。 */
    NEW_QUEUE,
}

/**
 * 决定走哪条路。**纯函数**，自检里断言。
 *
 * ⚠️ 为什么两边都要对：只看我们队列的下标不够 —— 万一我们的 `queue` 和 ExoPlayer 的
 * 播放列表错位，就会把"**别的歌**"那一项换掉，表现就是"切个版本跳成另一首歌"。
 * 播放器自己的 `mediaId`（`"musicId-vocalId"`）是它那边的真相，必须一起对上才敢换。
 */
fun versionPlayAction(
    /** 我们队列里"当前项"指向的歌；null = 没有当前项。 */
    queueMusicIdAtCurrent: Int?,
    /** **播放器自己**当前媒体项指向的歌（从 `mediaId` 解出来）；null = 读不到。 */
    playerMusicId: Int?,
    /** 用户点的那个版本属于哪首歌。 */
    musicId: Int,
): VersionPlayAction =
    if (queueMusicIdAtCurrent == musicId && playerMusicId == musicId) {
        VersionPlayAction.SWAP_CURRENT
    } else {
        VersionPlayAction.NEW_QUEUE
    }

/**
 * 从 `MediaItem.mediaId`（形如 `"644-6441"` = musicId-vocalId）里解出 musicId。
 *
 * 解不出来一律返回 null —— 调用方按"不是当前这首歌"处理（宁可另起队列，也别乱换）。
 */
fun musicIdOfMediaId(mediaId: String?): Int? =
    mediaId?.substringBefore('-')?.toIntOrNull()

/**
 * 把队列里第 [index] 项换成另一个版本（= 换 `MusicTrack`，**不动长度、不动其它项**）。
 *
 * **纯函数**，自检里断言："换完长度不变、下标不变、其它项一个都没动"——
 * 这是"切版本不许动队列"的底线。[index] 越界时原样返回。
 */
fun List<MusicTrack>.withVersionAt(index: Int, track: MusicTrack): List<MusicTrack> =
    if (index !in indices) this else toMutableList().also { it[index] = track }


/**
 * 把一个歌曲列表变成「播放队列 + 起点下标」。**纯函数**，方便自检。
 *
 * ⚠️ 两个坑都在这里处理掉：
 *  1. `toTrack()` 可能返回 null（那首歌没有音源行）→ 过滤后**下标会错位**，
 *     所以起点是按 `musicId` 在**过滤后的列表**里找的，不是用原始下标。
 *  2. 找不到（点的那首歌恰好没有音源）→ 返回 null，调用方不播。
 */
fun buildQueueFrom(list: List<HomeMusic>, clickedMusicId: Int): Pair<List<MusicTrack>, Int>? {
    val tracks = list.mapNotNull { it.toTrack() }
    if (tracks.isEmpty()) return null
    val index = tracks.indexOfFirst { it.musicId == clickedMusicId }
    if (index < 0) return null
    return tracks to index
}

/**
 * 播放器工厂。
 *
 * 持有一个共享的 [SimpleCache]（**同一个目录只能有一个实例**，它会锁目录），
 * 所以整个进程里只该有一个工厂 —— 挂在 `AppContainer` 上。
 *
 * 缓存和下载是两回事：这里缓存的是"听过的音频流"，用户不感知、也不占相册/音乐库。
 */
class MusicPlayerFactory(
    private val context: Context,
    private val httpClient: OkHttpClient,
) {
    private val cache: SimpleCache by lazy {
        SimpleCache(
            File(context.cacheDir, "media_cache"),
            LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES),
        )
    }

    /** 建一个播放器实例。调用方负责在离开页面时 [MusicPlayer.release]。 */
    fun create(): MusicPlayer {
        // 复用项目已有的 OkHttpClient（超时/重定向策略一致），
        // 再套一层磁盘缓存：听过的歌第二次零流量，断网也能听。
        val upstream = OkHttpDataSource.Factory(httpClient)
        val dataSourceFactory = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstream)
            // 缓存写失败（比如磁盘满）时不要卡住播放，直接用网络
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val player = ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
        // 交给系统处理音频焦点：来电/别的 App 放歌时自动压低或暂停
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            /* handleAudioFocus = */ true,
        )
        return MusicPlayer(context, player)
    }

    private companion object {
        /**
         * 磁盘缓存上限。一首游戏版剪辑约 1.7 MB（128 kbps），
         * 256 MB 大约能装 150 首 —— 听过的都是零流量，且不会吃满手机存储。
         */
        const val MAX_CACHE_BYTES = 256L * 1024 * 1024
    }
}

/**
 * 把列表行变成可播放的轨。
 *
 * 播的是**默认版本**（`musicVocals` 里 `seq` 最小的那个，实测 96% 是原曲版/虚拟歌手版，
 * 和游戏内默认一致）。列表行没有音源信息时返回 null —— 界面上那个 ▶ 会置灰。
 */
fun HomeMusic.toTrack(): MusicTrack? {
    val vocal = defaultVocal ?: return null
    return MusicTrack(
        musicId = id,
        vocalId = vocal.id,
        title = title,
        versionLabel = vocal.caption,
        audioUrl = AssetUrls.musicAudio(vocal.assetbundleName),
        jacketAssetbundleName = assetbundleName,
        fillerMs = fillerMsOf(fillerSec),
    )
}

/**
 * 一条轨 → ExoPlayer 的 `MediaItem`。
 *
 * ⚠️ **必须填 metadata**：通知栏和锁屏上显示的就是这三个字段。
 * 只给一个 URL 的话，系统会老老实实把 `https://storage.sekai.best/...` 当标题显示出来。
 *
 * 封面（`artworkUri`）走的是**系统服务自己下载**，所以固定日服桶
 * （三个桶的曲绘是同一张图，见 [AssetUrls.musicJacketJp]）。
 */
private fun MusicTrack.toMediaItem(): MediaItem = MediaItem.Builder()
    .setUri(audioUrl)
    .setMediaId("$musicId-$vocalId")
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            // 副标题放版本：通知栏「曲名 / 世界ver.」，用户能看出在放哪个版本
            .setArtist(versionLabel)
            .setArtworkUri(Uri.parse(AssetUrls.musicJacketJp(jacketAssetbundleName)))
            .build()
    )
    .build()

/**
 * 列表行上那个 ▶ 的统一行为（用户拍板）：**点的是正在播的那首 → 暂停/继续；
 * 点别的 → 整份列表入队、从那首开始播**。
 *
 * 抽在这里而不是在三个页面里各写一遍：三个列表（歌曲 Tab / 分类详情 / 人物详情）
 * 必须行为一致，而这种"某处没跟上"的差异几乎没人会发现。
 *
 * 匹配用 `musicId`（播放单元是**曲**，版本只是它的属性）—— 所以从详情页切成
 * "世界ver." 回到列表，那首歌的图标仍然是 ⏸。
 */
fun MusicPlayer.playFromList(list: List<HomeMusic>, musicId: Int) {
    if (state.value.track?.musicId == musicId) {
        togglePlayPause()
        return
    }
    buildQueueFrom(list, musicId)?.let { (tracks, index) -> playQueue(tracks, index) }
}

/**
 * 单条轨的播放器。
 *
 * ## 为什么现在不做成全局的
 *
 * 阶段 6 会加底部状态栏、阶段 7 加全屏播放器，那时它才需要"跨页面存活"。
 * 现在做成 **页面级**（详情页 `DisposableEffect` 里 release）是刻意的：
 * 全局播放器 + 没有任何控制入口 = 用户离开页面后声音还在响却停不掉。
 */
class MusicPlayer(
    private val context: Context,
    private val player: ExoPlayer,
) {

    /**
     * 交给 [PlaybackService] 包成 `MediaSession`（通知栏 / 锁屏 / 蓝牙耳机控制）。
     *
     * 刻意**共享同一个 ExoPlayer 实例**：进程里只该有一个播放器 ——
     * 服务和界面各持一个的话，通知栏的播放键和界面上的播放键会各说各话。
     */
    val exoPlayer: ExoPlayer get() = player

    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var ticker: Job? = null

    /** 定时器的倒计时任务（见 [setSleepTimer]）。 */
    private var sleepTimerJob: Job? = null

    /**
     * 我们自己留着队列对象（ExoPlayer 那边只存 MediaItem）。
     *
     * 下标和 ExoPlayer 的 `currentMediaItemIndex` **一一对应** —— 即使开了随机播放，
     * 那个 index 指的也是"在播放列表里的位置"，不是随机顺序里的位置，
     * 所以拿它回查 [queue] 是对的。
     */
    private var queue: List<MusicTrack> = emptyList()

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            push()
            when (playbackState) {
                Player.STATE_READY -> startTicker()
                Player.STATE_ENDED -> {
                    stopTicker()
                    // 播完停在末尾：不自动重播，也不清掉当前轨（用户可能想再点一次）
                    push()
                }

                else -> push()
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) startTicker() else stopTicker()
            push()
        }

        /**
         * 自动前进到下一首（或手动切歌）时触发。
         *
         * ⚠️ **每首歌的开头空白秒数不一样**，而 `setMediaItems` 只能给**第一条**指定
         * 起始位置，后面的都会从 0 开始 —— 所以在这里补一次 seek 跳过那一首自己的空白。
         * 位置就在曲首的静音段上，用户不会察觉这次 seek。
         *
         * ⚠️ **`PLAYLIST_CHANGED` 不补**：那种 transition 是**我们自己**引起的
         * （`playQueue` 的 `setMediaItems`、切版本的 `replaceMediaItem`），
         * 起始位置调用方已经指定好了 —— 补一次就会把"接着上次进度播"和
         * "换版本保持进度"一起打回去（这是 2026-09-16 修掉的隐患）。
         */
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
                val filler = queue.getOrNull(player.currentMediaItemIndex)?.fillerMs ?: 0L
                if (filler > 0L) player.seekTo(filler)
            }
            /*
             * 「播完这首歌就停」在这里判：**不靠"位置快到末尾了"这种猜法**。
             *
             *  - AUTO：这首放完、自动切到下一首 → 就是"播完这首"
             *  - REPEAT：单曲循环重播 → 也是"这首播完了"
             *    （⚠️ 少了这一条的话，"单曲循环 + 播完这首"会永远不停）
             */
            if (_state.value.sleepTimer.atTrackEnd &&
                (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                    reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT)
            ) {
                clearSleepTimer()
                player.pause()
            }
            push()
        }

        override fun onPlayerError(error: PlaybackException) {
            stopTicker()
            _state.update {
                it.copy(
                    isPlaying = false,
                    isBuffering = false,
                    // 直接把底层原因报出来：403/404 和"没网"是完全不同的问题，不该被合并成一句"播放失败"
                    error = error.cause?.message ?: error.message ?: "播放失败",
                )
            }
        }
    }

    init {
        player.addListener(listener)
    }

    /**
     * 播放一个队列。
     *
     * @param startIndex 从队列的第几首开始
     * @param startFromDisplayMs 从**显示位置**的哪一刻开始。
     *   歌曲详情页换版本时把当前进度传进来 —— 这就是"版本是属性、切换不断播"的落点：
     *   同一个剪辑的不同版本，内容位置是对应的。
     */
    fun playQueue(tracks: List<MusicTrack>, startIndex: Int, startFromDisplayMs: Long = 0L) {
        if (tracks.isEmpty()) return
        val index = startIndex.coerceIn(0, tracks.lastIndex)
        queue = tracks
        val startAt = playerPositionMs(startFromDisplayMs, tracks[index].fillerMs)

        _state.value = PlaybackState(
            track = tracks[index],
            queue = tracks,
            currentIndex = index,
            isPlaying = true,
            isBuffering = true,
            isPlayRequested = true,
            displayPositionMs = startFromDisplayMs.coerceAtLeast(0),
            displayDurationMs = TIME_UNSET_MS,
            playMode = _state.value.playMode,
            // 定时器是"用户设的闹钟"，不是播放状态的一部分 —— 换歌/停止都不该把它清掉
            sleepTimer = _state.value.sleepTimer,
        )
        // ExoPlayer 自己存整个播放列表：自动续播、上一首/下一首、循环、随机都由它处理，
        // 不用我们手写一套状态机（留声器也是这么做的）
        player.setMediaItems(tracks.map { it.toMediaItem() }, index, startAt)
        player.prepare()
        player.play()
        // ⚠️ 必须**在 play() 之后**起服务：MediaSessionService 判断"是否该进前台"看的是
        // playWhenReady，先起服务的话它会因为"没在播"而不调 startForeground，
        // 5 秒后系统就抛 "did not then call Service.startForeground()"。
        ensurePlaybackService()
    }

    /** 播放/暂停当前轨。播完之后再点 = 从头重播。 */
    fun togglePlayPause() {
        when {
            player.playbackState == Player.STATE_ENDED -> {
                player.seekTo(0L)
                player.play()
                ensurePlaybackService()
            }

            // ⚠️ 判断用 playWhenReady 而不是 isPlaying：缓冲期 isPlaying 还是 false，
            // 用后者的话「缓冲时点暂停」会被当成「继续播放」，按了没反应。
            player.playWhenReady -> player.pause()

            else -> {
                player.play()
                // 服务可能已经被系统回收（比如通知被划掉），恢复播放时要重新起
                ensurePlaybackService()
            }
        }
    }

    fun next() {
        if (queue.size > 1) player.seekToNextMediaItem()
    }

    fun previous() {
        if (queue.size > 1) player.seekToPreviousMediaItem()
    }

    /** 跳到队列里的第 [index] 项。队列面板点某一条时用它。 */
    fun jumpTo(index: Int) {
        if (index in queue.indices) player.seekTo(index, 0L)
    }

    /**
     * 播放某个**演唱版本** —— **队列的内容、顺序、光标都不动**（用户拍板）。
     *
     * **只有"这首歌就是当前正在播的那一首"时才生效**；不是的话返回 `false`，
     * 由调用方决定（详情页会另起一份队列）。
     * ⚠️ 用户明确要求：**点版本永远不该让"在播的那首歌"变成别的歌**
     * （旧版本有个"跳到队列里那首歌的位置"的分支，正是被否掉的那个行为）。
     *
     * ⚠️ 换版本只换 `MediaItem`，**不重建播放列表**：重建会把队列、随机顺序、
     * 已缓冲的下一首全部丢掉（`setMediaItems` 就是这么干的）。
     *
     * @return false = 这首歌不是当前播放项（调用方自己处理）
     */
    fun playVersion(track: MusicTrack): Boolean {
        val index = player.currentMediaItemIndex
        val action = versionPlayAction(
            queueMusicIdAtCurrent = queue.getOrNull(index)?.musicId,
            // 播放器自己那边的真相：mediaId 是 "musicId-vocalId"
            playerMusicId = musicIdOfMediaId(player.currentMediaItem?.mediaId),
            musicId = track.musicId,
        )
        if (action != VersionPlayAction.SWAP_CURRENT) return false
        if (index !in queue.indices) return false
        // 已经就是这个版本：什么都不用做
        if (queue[index].vocalId == track.vocalId) return true

        // ⚠️ 位置要在重建之前取：重建播放列表会把位置归零。
        // 而各版本的开头空白是**同一首歌共用的**（`musics.fillerSec` 按曲不按版本），
        // 所以原始位置可以直接照搬，不需要重新换算。
        val keepPosition = player.currentPosition

        val newQueue = queue.withVersionAt(index, track)
        queue = newQueue
        /*
         * ⚠️ **不能用 `replaceMediaItem`**（2026-09-16 真机日志查出来的坑）：
         * 它是"移除旧项 + 插入新项"，而 `mediaId` 换了版本就变了 –
         * ExoPlayer 在 **单曲循环 / 随机播放** 下要"重新找回当前项"，而且是**按 mediaId 找**，
         * 找不到就退回到别处 → 真机上从第 3 项跳到了第 452 项（另一首歌）。
         * 列表播放不用找回，所以那种模式下看不出问题。
         *
         * `setMediaItems(列表, 下标, 位置)` 没有"找回"这一步：下标和位置都是我们**显式给的**。
         * （日志里验证过：这条路径精确落在指定的下标上。）
         */
        player.setMediaItems(newQueue.map { it.toMediaItem() }, index, keepPosition)
        player.prepare()
        // 守卫：把"不许跳到别的歌"这条不变量写进代码，而不是指望第三方库。
        // 最坏情况是"什么也不做"，不会引入新问题。
        if (player.currentMediaItemIndex != index) {
            Log.i(
                TAG_DIAG,
                "⚠️ 换版本后下标被挪走：want=$index now=${player.currentMediaItemIndex} " +
                    "item=${player.currentMediaItem?.mediaId}（已 seek 回 $index）",
            )
            player.seekTo(index, keepPosition)
        }
        push()
        return true
    }

    /** 换播放模式。列表播到最后会回到第一首（REPEAT_MODE_ALL），不是停住。 */
    fun setPlayMode(mode: PlayMode) {
        player.repeatMode = when (mode) {
            PlayMode.SEQUENTIAL -> Player.REPEAT_MODE_ALL
            PlayMode.REPEAT_ONE -> Player.REPEAT_MODE_ONE
            PlayMode.SHUFFLE -> Player.REPEAT_MODE_ALL
        }
        player.shuffleModeEnabled = mode == PlayMode.SHUFFLE
        _state.update { it.copy(playMode = mode) }
    }

    /** 拖动进度条：[displayMs] 是**显示位置**。 */
    fun seekToDisplay(displayMs: Long) {
        val filler = _state.value.track?.fillerMs ?: 0L
        player.seekTo(playerPositionMs(displayMs, filler))
        _state.update { it.copy(displayPositionMs = displayMs.coerceAtLeast(0)) }
    }

    // ─────────────────────────────────────────────────────────────
    // 定时器（播放器右上角的闹钟）
    // ─────────────────────────────────────────────────────────────
    //
    // 放在 MusicPlayer 里而不是播放器页面里：收起播放器、回列表、锁屏都要照常计时，
    // 页面一销毁倒计时就没了。

    /** 设定"再过 [minutes] 分钟结束"。 */
    fun setSleepTimer(minutes: Int) {
        val endsAt = System.currentTimeMillis() + minutes * 60_000L
        _state.update {
            it.copy(sleepTimer = SleepTimerState(minutes = minutes, endsAtMs = endsAt))
        }
        startSleepTimer(endsAt)
    }

    /** 设定"播完这首歌就停"。 */
    fun setSleepTimerAtTrackEnd() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _state.update { it.copy(sleepTimer = SleepTimerState(atTrackEnd = true)) }
    }

    /** 取消定时器（= 回到"一直播放"）。 */
    fun clearSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _state.update { it.copy(sleepTimer = SleepTimerState()) }
    }

    private fun startSleepTimer(endsAtMs: Long) {
        sleepTimerJob?.cancel()
        sleepTimerJob = scope.launch {
            // 循环到点而不是一次 delay(剩余)：设备进 doze / 时钟被改时，一次性的
            // delay 会和墙钟漂移，循环每秒对一次墙钟最稳。
            while (true) {
                val remain = endsAtMs - System.currentTimeMillis()
                if (remain <= 0L) break
                delay(minOf(remain, TICK_MS_SLEEP))
            }
            sleepTimerJob = null
            _state.update { it.copy(sleepTimer = SleepTimerState()) }
            // 到点的动作是**暂停**：队列留着，用户点一下就能接着听
            //（"停止"会把队列和通知栏一起清掉，睡前误触一下就没法接着听了）。
            player.pause()
        }
    }

    fun pause() {
        player.pause()
    }

    /**
     * 停止并卸下整个队列 —— 底部状态栏随之消失。
     *
     * 必须 `clearMediaItems()`：只 `stop()` 的话 `currentMediaItem` 还在，
     * 状态栏会留着一条"停在那儿的歌"，看起来像卡住了。
     */
    fun stop() {
        stopTicker()
        queue = emptyList()
        player.stop()
        player.clearMediaItems()
        _state.value = PlaybackState(
            playMode = _state.value.playMode,
            sleepTimer = _state.value.sleepTimer,
        )
        // 显式停服务：不然那条通知什么时候消失要交给 media3 自己判断，
        // 用户点了「✕」却还留着一条通知是很明显的破绽。
        // （服务销毁只会 release MediaSession，不会动我们这个 ExoPlayer。）
        context.stopService(Intent(context, PlaybackService::class.java))
    }

    fun release() {
        stopTicker()
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        scope.cancel()
        player.removeListener(listener)
        player.release()
    }

    /**
     * 确保播放服务活着 —— 它在播放中会被 media3 提成**前台服务**，
     * 所以切到别的 App、锁屏都不会被杀（这就是"后台播放"）。
     *
     * ## 为什么用 `startService` 而不是 `startForegroundService`
     *
     * media3 的通知管理器**自己**会调 `startForegroundService` + `startForeground`
     * （它内部有个 `startSelfIntent` 专干这个）。我们这里只是"让服务存在"，
     * 一旦用了 `startForegroundService`，就等于签下了"5 秒内必须 startForeground"的契约，
     * 万一内部控制器连接慢一步，系统会直接崩（`did not then call Service.startForeground()`）。
     * 用 `startService` 没有这个契约，最坏情况只是"没通知"，不会崩。
     *
     * 只在"用户刚让它开始播"的地方调：Android 12 起从后台起服务会抛异常，
     * 而这些调用点都在界面上。
     */
    private fun ensurePlaybackService() {
        context.startService(Intent(context, PlaybackService::class.java))
    }

    /** 每 250ms 把位置刷进 state。播放/缓冲时才跑，暂停时停掉（省电）。 */
    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (isActive) {
                push()
                delay(TICK_MS)
            }
        }
    }

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
        push()
    }

    private fun push() {
        val index = player.currentMediaItemIndex
        val current = queue.getOrNull(index)
        val filler = current?.fillerMs ?: 0L
        val duration = player.duration
        _state.update {
            it.copy(
                // 当前轨以 ExoPlayer 为准：随机播放/自动续播时我们这边的下标会变
                track = current ?: it.track,
                queue = queue,
                currentIndex = if (current != null) index else it.currentIndex,
                isPlaying = player.isPlaying,
                isBuffering = player.playbackState == Player.STATE_BUFFERING,
                isPlayRequested = player.playWhenReady &&
                    player.playbackState != Player.STATE_ENDED,
                displayPositionMs = displayPositionMs(player.currentPosition, filler),
                displayDurationMs = if (duration == C.TIME_UNSET) {
                    TIME_UNSET_MS
                } else {
                    displayDurationMs(duration, filler)
                },
            )
        }
    }

    private companion object {
        const val TICK_MS = 250L

        /** 定时器每秒对一次墙钟（见 [startSleepTimer]）。 */
        const val TICK_MS_SLEEP = 1_000L

        /**
         * 诊断日志的 tag。
         *
         * 为什么用 `Log.i` 而不是 `Log.d`：这台机器（MEIZU 21 Note / Android 16）
         * **把 debug 级日志过滤掉了**，`Log.d` 在 logcat 里根本看不到（踩过）。
         */
        const val TAG_DIAG = "MusicPlayer"
    }
}
