package com.pjsk.toolbox.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * 波浪形进度条（对应留声器里那条 `SquigglyProgress`）。
 *
 * ## 为什么自己画
 *
 * 留声器那条是 **AOSP 衍生的自定义 `Drawable`**（`SeekBar` 的子类）。它那边是 View 体系，
 * 这边全是 Compose，抄不过来 —— 只能照它的**观感**重画一遍：
 * 已播部分是正弦波，未播部分是直线。波长 27dp / 振幅 2.7dp 是照它的取值。
 *
 * ## 拖动策略：**松手才 seek**
 *
 * 拖动过程中只更新本地预览（并显示预览时间），**松手才真正 seek**。
 * 理由：音频是**流式**的，拖动时每帧都 seek 会让播放器不断丢弃缓冲区、
 * 重新发 Range 请求，表现是"拖的时候一直在卡"。松手一次到位最稳。
 *
 * @param fraction 当前进度（0~1），由调用方从播放状态算好
 * @param onSeek [fraction] 松手/点击时回调（0~1）
 */
@Composable
fun SquigglySeekBar(
    fraction: Float,
    enabled: Boolean,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    waveLength: Dp = 27.dp,
    amplitude: Dp = 2.7.dp,
) {
    // null = 没在拖；否则是拖动中的预览分数
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val shown = (dragFraction ?: fraction).coerceIn(0f, 1f)

    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
    val progressColor = MaterialTheme.colorScheme.primary
    val thumbColor = MaterialTheme.colorScheme.primary
    val density = LocalDensity.current
    val waveLengthPx = with(density) { waveLength.toPx() }
    val amplitudePx = with(density) { amplitude.toPx() }

    Canvas(
        modifier = modifier
            .height(32.dp)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragFraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                    },
                    onHorizontalDrag = { change, _ ->
                        dragFraction = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        dragFraction?.let(onSeek)
                        dragFraction = null
                    },
                    onDragCancel = { dragFraction = null },
                )
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    onSeek((offset.x / size.width.toFloat()).coerceIn(0f, 1f))
                }
            },
    ) {
        val centerY = size.height / 2f
        val progressWidth = size.width * shown

        // 未播部分：直线
        drawLine(
            color = trackColor,
            start = Offset(progressWidth, centerY),
            end = Offset(size.width, centerY),
            strokeWidth = 3f,
        )

        // 已播部分：正弦波
        if (progressWidth > 0f) {
            val path = Path()
            path.moveTo(0f, centerY)
            var x = 0f
            // 1px 一步，够平滑；整条最多几百个点，开销可以忽略
            while (x <= progressWidth) {
                val y = centerY + sin(2.0 * PI * x / waveLengthPx).toFloat() * amplitudePx
                path.lineTo(x, y)
                x += 1f
            }
            // 收尾对齐到准确位置，免得波尾因为步进取整短一截
            path.lineTo(progressWidth, centerY)
            drawPath(path = path, color = progressColor, style = Stroke(width = 3f))
        }

        // 拖动把手
        drawCircle(
            color = thumbColor,
            radius = if (dragFraction != null) 9f else 6f,
            center = Offset(progressWidth, centerY),
        )
    }
}
