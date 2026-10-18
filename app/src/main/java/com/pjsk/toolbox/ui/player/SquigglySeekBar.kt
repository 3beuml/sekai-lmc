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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
 * ## 演进
 *
 *  0.1.36 曾一度换成 M3 `Slider`；0.1.37 用户拍板换回波浪（线加粗到 2dp）；
 *  0.1.43 再把"当前位置"从圆点改成**竖条**，已播段比未播粗一档。
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
 * （换成 M3 Slider 时这条语义**原样保留**了：`onValueChangeFinished` 里才 seek。）
 *
 * @param fraction 当前进度（0~1），由调用方从播放状态算好
 * @param onSeek [fraction] 松手/点击时回调（0~1）
 * @param trackWidth 未播部分的线宽。**0.1.37 起从写死的 3px 改成 2dp** —— 原来那个 3f 是**像素**，
 *   在 520dpi 的机器上只有 0.92dp，细到几乎看不见；用户反馈"把线稍微增粗一点"，
 *   这里顺手改成 dp（与密度无关，换机型不会又变回一条发丝线）。
 * @param progressWidth 已播部分的线宽（0.1.43 比未播**粗一档**，让"听到哪了"一眼可见）。
 * @param thumbWidth / @param thumbHeight 当前位置的**竖条**尺寸（0.1.43 从圆点改成条，
 *   用户参考留声器："当前位置用条来表示"）。竖条**固定在中线**上下居中，
 *   不跟着正弦波起伏 —— 否则会穿模，看着像线断了。
 * @param thumbScaleDragging 拖动时竖条的放大倍数（手指反馈）。
 */
@Composable
fun SquigglySeekBar(
    fraction: Float,
    enabled: Boolean,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    waveLength: Dp = 27.dp,
    amplitude: Dp = 2.7.dp,
    trackWidth: Dp = 2.dp,
    progressWidth: Dp = 3.5.dp,
    thumbWidth: Dp = 4.dp,
    thumbHeight: Dp = 18.dp,
    thumbScaleDragging: Float = 1.25f,
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
    val trackWidthPx = with(density) { trackWidth.toPx() }
    val progressWidthPx = with(density) { progressWidth.toPx() }
    val thumbWidthPx = with(density) { thumbWidth.toPx() }
    val thumbHeightPx = with(density) { thumbHeight.toPx() }

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
            strokeWidth = trackWidthPx,
        )

        // 已播部分：正弦波（比未播粗一档）
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
            drawPath(path = path, color = progressColor, style = Stroke(width = progressWidthPx))
        }

        // 当前位置：**竖条**（0.1.43 用户拍板，替代原来的圆点）
        //
        // 高度固定、上下居中，两端做圆角；拖动时整体放大给手指反馈。
        val scale = if (dragFraction != null) thumbScaleDragging else 1f
        val barW = thumbWidthPx * scale
        val barH = thumbHeightPx * scale
        // 贴着两端时别让竖条跑出画面（否则最后一秒会被裁掉一半）
        val barCenterX = progressWidth.coerceIn(barW / 2f, size.width - barW / 2f)
        drawRoundRect(
            color = thumbColor,
            topLeft = Offset(barCenterX - barW / 2f, centerY - barH / 2f),
            size = Size(barW, barH),
            cornerRadius = CornerRadius(barW / 2f, barW / 2f),
        )
    }
}
