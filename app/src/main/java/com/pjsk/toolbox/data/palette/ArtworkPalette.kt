package com.pjsk.toolbox.data.palette

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * 封面取色：从专辑图里抽一个**种子色**，再派生出播放器要用的几个色调。
 *
 * ## 为什么不直接用封面上出现最多的那个颜色
 *
 * 封面上必然有纯白背景、纯黑描边、大面积灰渐变。直接取"最多的颜色"会得到一个
 * 白的或灰的种子 —— 于是按钮、进度条、标题全变成一坨灰。所以量化之后要**筛**：
 * 丢掉过亮（>0.90）、过暗（<0.10）、低饱和（<0.28）的色簇，再在剩下的里面按
 * 「饱和度 × 出现比例 × 亮度接近 0.55」打分取最高。
 *
 * ## 为什么派生而不是直接拿种子当 primary
 *
 * 种子只用来定**色相和大致饱和度**，亮度由主题决定（浅色主题 primary 取 L≈0.40、
 * 渐变顶取 L≈0.90…）。这样无论封面是亮是暗，界面层次都是稳定的 —— 这是
 * 「跟封面变色的界面」看起来不廉价的关键。
 *
 * ## 这一层是纯函数
 *
 * 只处理 ARGB Int，不碰 Android/Compose，所以能进 `tools/logic-check` 断言：
 * 颜色算错会变成红灯，而不是"看着有点怪"。
 */

/** 从封面里选出的种子。`hue` 0..360，`saturation`/`lightness` 0..1，`share` 是它占的像素比例。 */
data class ArtworkSeed(
    val hue: Float,
    val saturation: Float,
    val lightness: Float,
    val share: Float,
)

/**
 * 播放器最终要用的几个颜色（ARGB）。
 *
 * 刻意只暴露这几个、而不是一整套 ColorScheme：能变的**只有播放器这一屏**，
 * 其余界面继续用全局主题。少一个能变的东西，就少一处对比度出问题的可能。
 */
data class PlayerPaletteArgb(
    /** 选中的种子色本身（诊断用，界面上不直接用它画东西）。 */
    val seed: Int,
    /** 主色：进度条、播放键、定时器高亮。 */
    val primary: Int,
    /** 主色上的图标/文字。 */
    val onPrimary: Int,
    /** 背景渐变的上端（容器色）。 */
    val gradientTop: Int,
    /** 背景渐变的下端（背景色）。 */
    val gradientBottom: Int,
    /** 渐变上的主文字。 */
    val onSurface: Int,
    /** 渐变上的次要文字。 */
    val onSurfaceVariant: Int,
)

// ── 筛选阈值（调这三个数就会改变"哪种封面算合格"）──
private const val MIN_SATURATION = 0.28f
private const val MIN_LIGHTNESS = 0.10f
private const val MAX_LIGHTNESS = 0.90f
private const val MIN_SHARE = 0.02f

/** 对比度下限：WCAG AA 正文 4.5:1。 */
private const val MIN_CONTRAST = 4.5

/**
 * 选种子。**返回 null 表示这张封面不适合取色**（纯白/纯黑/接近灰度），
 * 调用方应当退回主题色 —— 这比硬算出一个灰界面好。
 */
fun pickArtworkSeed(pixels: IntArray): ArtworkSeed? {
    if (pixels.isEmpty()) return null

    // 每通道量化到 32 级（5 bit）→ 最多 32768 个桶，实际远少于此
    val count = HashMap<Int, Int>(256)
    val sumR = HashMap<Int, Long>(256)
    val sumG = HashMap<Int, Long>(256)
    val sumB = HashMap<Int, Long>(256)
    var counted = 0
    for (argb in pixels) {
        // 半透明像素参与平均会污染颜色，直接跳过
        if (((argb ushr 24) and 0xFF) < 0x80) continue
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        val key = ((r shr 3) shl 10) or ((g shr 3) shl 5) or (b shr 3)
        count[key] = (count[key] ?: 0) + 1
        sumR[key] = (sumR[key] ?: 0L) + r
        sumG[key] = (sumG[key] ?: 0L) + g
        sumB[key] = (sumB[key] ?: 0L) + b
        counted++
    }
    if (counted == 0) return null

    var best: ArtworkSeed? = null
    var bestScore = 0.0
    for ((key, n) in count) {
        val r = (sumR[key]!! / n).toFloat()
        val g = (sumG[key]!! / n).toFloat()
        val b = (sumB[key]!! / n).toFloat()
        val hsl = rgbToHsl(r, g, b)
        val share = n.toFloat() / counted
        if (hsl[1] < MIN_SATURATION) continue
        if (hsl[2] < MIN_LIGHTNESS || hsl[2] > MAX_LIGHTNESS) continue
        if (share < MIN_SHARE) continue
        // 打分：出现得多、够鲜艳、亮度不极端
        val score = sqrt(share.toDouble()) * hsl[1] * (1.0 - abs(hsl[2] - 0.55) * 0.8)
        if (score > bestScore) {
            bestScore = score
            best = ArtworkSeed(hue = hsl[0], saturation = hsl[1], lightness = hsl[2], share = share)
        }
    }
    return best
}

/**
 * 由像素直接得到播放器调色板；封面不适合取色时返回 null。
 *
 * @param dark 深色主题。深色主题的派生方向和浅色**相反**（primary 要提亮、
 *   背景要压暗），不能用同一套亮度档。
 */
fun playerPaletteFromPixels(pixels: IntArray, dark: Boolean): PlayerPaletteArgb? {
    val seed = pickArtworkSeed(pixels) ?: return null
    val h = seed.hue
    val s = seed.saturation

    return if (dark) {
        val primary = hslToArgb(h, min(s, 0.78f), 0.80f)
        val top = hslToArgb(h, s * 0.35f, 0.20f)
        val bottom = hslToArgb(h, s * 0.22f, 0.08f)
        PlayerPaletteArgb(
            seed = hslToArgb(h, s, seed.lightness),
            primary = primary,
            onPrimary = ensureContrast(hslToArgb(h, min(s, 0.6f), 0.16f), primary, MIN_CONTRAST),
            gradientTop = top,
            gradientBottom = bottom,
            onSurface = ensureContrast(hslToArgb(h, s * 0.08f, 0.93f), bottom, MIN_CONTRAST),
            onSurfaceVariant = ensureContrast(hslToArgb(h, s * 0.14f, 0.82f), top, 3.0),
        )
    } else {
        val primary = hslToArgb(h, s.coerceIn(0.35f, 0.88f), 0.40f)
        val top = hslToArgb(h, s * 0.42f, 0.90f)
        val bottom = hslToArgb(h, s * 0.18f, 0.97f)
        PlayerPaletteArgb(
            seed = hslToArgb(h, s, seed.lightness),
            primary = primary,
            onPrimary = ensureContrast(whiteOrBlack(primary), primary, MIN_CONTRAST),
            gradientTop = top,
            gradientBottom = bottom,
            onSurface = ensureContrast(hslToArgb(h, s * 0.10f, 0.11f), bottom, MIN_CONTRAST),
            onSurfaceVariant = ensureContrast(hslToArgb(h, s * 0.12f, 0.33f), top, 3.0),
        )
    }
}

// ── 颜色小工具（都是纯函数、都被自检覆盖）──

/** HSL → ARGB。`h` 0..360，`s`/`l` 0..1。 */
fun hslToArgb(hue: Float, saturation: Float, lightness: Float): Int {
    val h = ((hue % 360f) + 360f) % 360f
    val s = saturation.coerceIn(0f, 1f)
    val l = lightness.coerceIn(0f, 1f)
    val c = (1f - abs(2f * l - 1f)) * s
    val x = c * (1f - abs((h / 60f) % 2f - 1f))
    val m = l - c / 2f
    val (r1, g1, b1) = when {
        h < 60f -> Triple(c, x, 0f)
        h < 120f -> Triple(x, c, 0f)
        h < 180f -> Triple(0f, c, x)
        h < 240f -> Triple(0f, x, c)
        h < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    val r = ((r1 + m) * 255f).roundToInt().coerceIn(0, 255)
    val g = ((g1 + m) * 255f).roundToInt().coerceIn(0, 255)
    val b = ((b1 + m) * 255f).roundToInt().coerceIn(0, 255)
    return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
}

/** ARGB → [hue(0..360), saturation(0..1), lightness(0..1)]。 */
fun rgbToHsl(r: Float, g: Float, b: Float): FloatArray {
    val rn = r / 255f
    val gn = g / 255f
    val bn = b / 255f
    val maxC = max(rn, max(gn, bn))
    val minC = min(rn, min(gn, bn))
    val l = (maxC + minC) / 2f
    val delta = maxC - minC
    if (delta == 0f) return floatArrayOf(0f, 0f, l)
    val s = delta / (1f - abs(2f * l - 1f))
    val h = when (maxC) {
        rn -> 60f * (((gn - bn) / delta) % 6f)
        gn -> 60f * (((bn - rn) / delta) + 2f)
        else -> 60f * (((rn - gn) / delta) + 4f)
    }
    return floatArrayOf(((h % 360f) + 360f) % 360f, s, l)
}

/** WCAG 相对亮度。 */
fun relativeLuminance(argb: Int): Double {
    fun channel(value: Int): Double {
        val c = value / 255.0
        return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
    }
    val r = channel((argb shr 16) and 0xFF)
    val g = channel((argb shr 8) and 0xFF)
    val b = channel(argb and 0xFF)
    return 0.2126 * r + 0.7152 * g + 0.0722 * b
}

/** WCAG 对比度（1..21）。 */
fun contrastRatio(a: Int, b: Int): Double {
    val la = relativeLuminance(a)
    val lb = relativeLuminance(b)
    val lighter = max(la, lb)
    val darker = min(la, lb)
    return (lighter + 0.05) / (darker + 0.05)
}

/** 反差更大的那个前景（黑或白）。 */
fun whiteOrBlack(background: Int): Int =
    if (contrastRatio(0xFFFFFFFF.toInt(), background) >= contrastRatio(0xFF000000.toInt(), background)) {
        0xFFFFFFFF.toInt()
    } else {
        0xFF000000.toInt()
    }

/**
 * 把前景的亮度往"远离背景"的方向推，直到对比度达标。
 *
 * 为什么需要：种子色的色相/饱和度是不可控的，同一套固定亮度档在某些色相上
 * 会算出对比度不足的组合（例如黄绿色 L=0.40 的 primary 配白字只有 3.1:1）。
 * 与其为每种色相调参数，不如算出结果后**兜底推一把** —— 最多 25 步，每步 0.02。
 */
fun ensureContrast(foreground: Int, background: Int, minRatio: Double): Int {
    if (contrastRatio(foreground, background) >= minRatio) return foreground
    val hsl = rgbToHsl(
        ((foreground shr 16) and 0xFF).toFloat(),
        ((foreground shr 8) and 0xFF).toFloat(),
        (foreground and 0xFF).toFloat(),
    )
    val backgroundLum = relativeLuminance(background)
    // 背景亮 → 前景往暗走；背景暗 → 前景往亮走
    val step = if (backgroundLum > 0.5) -0.04f else 0.04f
    var lightness = hsl[2]
    repeat(25) {
        lightness = (lightness + step).coerceIn(0f, 1f)
        val candidate = hslToArgb(hsl[0], hsl[1], lightness)
        if (contrastRatio(candidate, background) >= minRatio) return candidate
    }
    // 走到头还不达标（极端色相）→ 直接给纯黑/纯白，至少保证能读
    return whiteOrBlack(background)
}
