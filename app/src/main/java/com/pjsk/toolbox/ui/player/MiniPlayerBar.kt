package com.pjsk.toolbox.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pjsk.toolbox.data.music.TIME_UNSET_MS
import com.pjsk.toolbox.data.remote.AssetUrls
import com.pjsk.toolbox.data.remote.ServerRegion
import com.pjsk.toolbox.data.player.MusicPlayer
import com.pjsk.toolbox.ui.common.RemoteImage

/**
 * 底部歌曲状态栏。
 *
 * ## 两个刻意的设计（都是为了"常态下不影响美观"）
 *
 * 1. **没有在播的歌时，完全不占高度**（直接 return，什么都不画）。
 *    所以"没在听歌"的时候，界面和加这个功能之前**一模一样**。
 * 2. **悬浮胶囊**而不是通栏：左右各留 12dp、圆角 18dp、60dp 高，
 *    看起来像"浮在内容上的一张卡"，视觉重量比贴边的整条轻得多。
 *
 * 进度用胶囊底边一条 2dp 的细线表示 —— 不展开也知道播到哪了。
 *
 * ⚠️ **暂未实现"滚动时自动收起"**：那需要知道"当前这一屏的列表滚到哪了"，
 * 而每一屏都有自己的 LazyColumn，要把滚动状态逐个上报到外壳来。
 * 先看实际观感要不要，再决定值不值得为它铺这套管道。
 */
@Composable
fun MiniPlayerBar(
    player: MusicPlayer,
    region: ServerRegion,
    onOpenPlayer: () -> Unit,
) {
    val state by player.state.collectAsState()
    val track = state.track ?: return

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 6.dp,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenPlayer)
                    .padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RemoteImage(
                    url = AssetUrls.musicJacket(region, track.jacketAssetbundleName),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = track.versionLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { player.togglePlayPause() }) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (state.isPlaying) "暂停" else "播放",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                // 关掉 = 停止并卸下当前轨，胶囊随之消失
                IconButton(onClick = { player.stop() }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "停止播放",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            // 细进度线。总长未知时给 0（不画一根满格或跳动的线）
            val duration = state.displayDurationMs
            val fraction = if (duration > 0 && duration != TIME_UNSET_MS) {
                (state.displayPositionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                drawStopIndicator = {},
            )
            Box(modifier = Modifier.height(2.dp).fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh))
        }
    }
}
