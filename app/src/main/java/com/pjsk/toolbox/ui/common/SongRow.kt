package com.pjsk.toolbox.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pjsk.toolbox.data.home.HomeMusic
import com.pjsk.toolbox.data.music.MusicUnitTag
import com.pjsk.toolbox.data.player.RowPlayState
import com.pjsk.toolbox.data.player.rowPlayState
import com.pjsk.toolbox.data.remote.AssetUrls
import com.pjsk.toolbox.data.remote.ServerRegion
import com.pjsk.toolbox.util.parseHexColor

/**
 * 歌曲列表的一行。**三处共用**：歌曲 Tab、分类详情页、人物详情页。
 *
 * 抽出来是因为这几个列表必须长得一模一样 —— 各写一份的结果是
 * 「同一首歌在几个页面显示的副标题不一样」，而这种不一致几乎没人会发现。
 *
 * ## 右侧两个按钮
 *
 *  - **▶ / ⏸**：没在播 → 播**默认版本**（`musicVocals` 里 `seq` 最小的那个）；
 *    **正在播的就是这一首 → 变成 ⏸，点它是暂停/继续**（用户要求）。
 *    判据是 [isPlayRequested] 而不是 [isPlaying]：缓冲那一两秒里图标不该闪，
 *    而且缓冲时点暂停必须真的停下（见 `MusicPlayer.togglePlayPause`）。
 *    匹配用 `musicId` —— 播放单元是**曲**，版本只是属性，所以在详情页换过版本回到列表，
 *    这一行仍然是 ⏸。
 *    没有音源行时**置灰而不是消失** —— 消失会让行宽忽宽忽窄，列表看起来在抖。
 *  - **★ 收藏**：实心 = 已收藏。
 *
 * **发布时间在这一行去掉了**（用户要求）—— 那个位置让给这两个按钮；
 * 时间在歌曲详情页仍然有。
 */
@Composable
fun SongRow(
    music: HomeMusic,
    region: ServerRegion,
    unitColors: Map<String, String>,
    isFavorite: Boolean,
    /** 正在播的那首的 `musicId`；没在播 = null。 */
    playingMusicId: Int?,
    /** 正在播的那首是否"想让它播"（false = 用户暂停了）。 */
    isPlayRequested: Boolean,
    onToggleFavorite: () -> Unit,
    onPlay: () -> Unit,
    onClick: () -> Unit,
) {
    // 「暂停」只在"当前这首 + 想让它播"时出现；暂停后回到 ▶（含义变成"继续播放"）
    val playState = rowPlayState(playingMusicId, music.id, isPlayRequested)
    val showPause = playState == RowPlayState.PLAYING
    val playable = music.defaultVocal != null
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RemoteImage(
            url = AssetUrls.musicJacket(region, music.assetbundleName),
            contentDescription = null,
            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(6.dp)),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = music.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 组合用官方组合色点小圆点，而不是给整行染色 ——
                // 一首歌可能挂多个组合标签（实测 27% 的曲子有 2 个以上），染色会变大花脸
                music.unitTags.take(3).forEach { tag ->
                    val color = unitDotColor(tag, unitColors)
                    if (color != null) {
                        Box(
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .size(7.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(color),
                        )
                    }
                }
                val subtitle = music.composer
                    ?: MusicUnitTag.of(music.unitTags.firstOrNull())?.label
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        IconButton(onClick = onPlay, enabled = playable) {
            Icon(
                imageVector = if (showPause) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = when {
                    !playable -> "无音源"
                    showPause -> "暂停"
                    playState == RowPlayState.PAUSED -> "继续播放"
                    else -> "试听"
                },
                tint = if (playable) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                },
            )
        }
        IconButton(onClick = onToggleFavorite) {
            Icon(
                imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = if (isFavorite) "取消收藏" else "收藏",
                tint = if (isFavorite) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

/**
 * 组合色小圆点。查不到颜色就返回 null（`other` 没有官方色，不显示圆点）。
 *
 * `piapro`（VIRTUAL SINGER）的官方色是**纯白** —— 在浅色主题上和背景一个颜色，
 * 画出来等于没有，所以过滤掉。
 */
fun unitDotColor(tag: String, unitColors: Map<String, String>): Color? {
    val profileKey = MusicUnitTag.of(tag)?.unitProfileKey ?: return null
    val hex = unitColors[profileKey] ?: return null
    val parsed = parseHexColor(hex) ?: return null
    return if (parsed == Color.White) null else parsed
}

/** 日期只到「天」，与歌曲详情页用同一套（系统时区）。 */
fun formatShortDate(epochMillis: Long): String =
    java.time.Instant.ofEpochMilli(epochMillis)
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalDate()
        .toString()

/**
 * 歌曲列表的通用容器：统一行距、分隔线和空状态，几个页面共用。
 *
 * 「试听」和「收藏」的具体行为由调用方给（[onPlay] / [onToggleFavorite]），
 * 因为这个容器不知道自己处在哪个页面 —— 但几个列表长得一样这件事必须由它保证。
 */
@Composable
fun SongListColumn(
    musics: List<HomeMusic>,
    region: ServerRegion,
    unitColors: Map<String, String>,
    favoriteIds: Set<Int>,
    emptyTitle: String,
    emptyDescription: String,
    onToggleFavorite: (Int) -> Unit,
    onPlay: (HomeMusic) -> Unit,
    onOpenDetail: (Int) -> Unit,
    /** 正在播的那首的 `musicId`（没在播 = null）；那行的 ▶ 会变成 ⏸。 */
    playingMusicId: Int? = null,
    /** 正在播的那首是否"想让它播"。 */
    isPlayRequested: Boolean = false,
) {
    if (musics.isEmpty()) {
        EmptyState(title = emptyTitle, description = emptyDescription)
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        // ⚠️ 顶部**不留内边距**（用户要求）：第一行要紧贴着上面那条 Tab 栏。
        // 行自己还有 10dp 的上下内边距，所以视觉上不会贴到字上。
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.Top,
    ) {
        items(musics, key = { it.id }) { music ->
            SongRow(
                music = music,
                region = region,
                unitColors = unitColors,
                isFavorite = music.id in favoriteIds,
                playingMusicId = playingMusicId,
                isPlayRequested = isPlayRequested,
                onToggleFavorite = { onToggleFavorite(music.id) },
                onPlay = { onPlay(music) },
                onClick = { onOpenDetail(music.id) },
            )
            RowDivider()
        }
    }
}
