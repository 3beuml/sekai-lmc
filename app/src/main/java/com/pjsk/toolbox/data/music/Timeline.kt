package com.pjsk.toolbox.data.music

/**
 * 「跳过开头空白」的时间轴换算。
 *
 * ## 为什么需要换算，而不是直接 seek
 *
 * 游戏音源 `music/long/<ab>/<ab>.mp3` 的**开头有一段空白**（`musics.fillerSec`），
 * 实测 717 首全部大于 0、范围 4.50~11.07 秒、中位数 9 秒。
 * 参考站（pjsk.moe）的做法就是播 long、然后 seek 到 `fillerSec`。
 *
 * 但**只 seek 不换算**会得到很别扭的界面：进度条一上来就在 0:08，
 * 总时长显示 1:50，而这首歌实际只有 1:41 的内容。所以整条时间轴都要平移：
 *
 * ```
 * 显示位置 = 播放器位置 - fillerSec
 * 显示总长 = 播放器总长 - fillerSec
 * 拖动到 x → seek 到 x + fillerSec
 * ```
 *
 * 实测对照 644：音频 109.87 秒、filler 7.93 秒 → 显示 1:41，和参考站一致。
 *
 * ## 为什么是纯函数
 *
 * 这层算错**不会报错**，只会让进度条飘、让"跳到 xx 秒"偏 —— 在真机上极难发现。
 * 写成纯函数就能进 `tools/logic-check.ps1` 直接断言，所以这里刻意**不依赖 media3**。
 */
/** media3 里"时长未知"的哨兵值。这里复制一份常量，避免纯函数层依赖 media3。 */
const val TIME_UNSET_MS = -1L

/** `musics.fillerSec`（秒，浮点）→ 毫秒。缺失/异常一律当 0（不跳过）。 */
fun fillerMsOf(fillerSec: Double?): Long {
    val value = fillerSec ?: return 0L
    if (value.isNaN() || value <= 0.0) return 0L
    // 上限保护：真有脏数据（比如误写成秒数之外的量纲）时，不要把整首歌都跳掉
    return (value * 1000.0).toLong().coerceAtMost(60_000L)
}

/** 播放器位置 → 显示位置。负数（还没跳过空白）一律显示 0。 */
fun displayPositionMs(playerPositionMs: Long, fillerMs: Long): Long {
    if (playerPositionMs < 0) return 0L
    return (playerPositionMs - fillerMs).coerceAtLeast(0L)
}

/**
 * 播放器总长 → 显示总长。未知（<0）原样返回 [TIME_UNSET_MS]。
 *
 * ⚠️ media3 的 `player.duration` 是**异步**拿到的：刚加载时是未知，
 * 界面必须能显示"还不知道多长"，否则进度条会先闪一下再跳。
 */
fun displayDurationMs(playerDurationMs: Long, fillerMs: Long): Long {
    if (playerDurationMs < 0) return TIME_UNSET_MS
    return (playerDurationMs - fillerMs).coerceAtLeast(0L)
}

/** 显示位置（用户拖到的位置）→ 该 seek 到的播放器位置。 */
fun playerPositionMs(displayMs: Long, fillerMs: Long): Long =
    (if (displayMs < 0) 0L else displayMs) + fillerMs

/**
 * `mm:ss`。未知时长显示 `--:--` —— 不显示 `0:00`，那会让人以为这首是空的。
 */
fun formatClock(ms: Long): String {
    if (ms < 0) return "--:--"
    val total = ms / 1000
    val minutes = total / 60
    val seconds = total % 60
    return "%d:%02d".format(minutes, seconds)
}

/**
 * 进度条的**分数**（0~1）→ 该 seek 到的显示位置。
 *
 * 时长未知（≤ 0）时返回 0：进度条这时是禁用的，但多一层保护总比
 * 让 `fraction * 0` 之外的脏数据漏进去好。
 */
fun positionOfFraction(fraction: Float, durationMs: Long): Long {
    if (durationMs <= 0) return 0L
    val clamped = fraction.coerceIn(0f, 1f)
    return (clamped * durationMs).toLong().coerceIn(0L, durationMs)
}

/** 显示位置 → 进度条分数（0~1）。时长未知时返回 0。 */
fun fractionOf(positionMs: Long, durationMs: Long): Float {
    if (durationMs <= 0) return 0f
    return (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
}
