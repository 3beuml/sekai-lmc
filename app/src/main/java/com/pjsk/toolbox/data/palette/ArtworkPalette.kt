package com.pjsk.toolbox.data.palette

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * 封面取色：从封面里抽一个**种子色**，再派生出播放器的一整份调色板。
 *
 * ## 0.1.43 的转向：不再"永远深色"，改成**按封面亮度自适应**
 *
 * 0.1.41/0.1.42 是"播放器永远深色 + 整屏从封面派生"。用户看过实机后否掉了：
 * 留声器（Gramophone）那种观感是**背景浅、主控制键深**，强行全黑不对味。
 *
 * 现在：**亮封面走浅色方案，暗封面仍走深色方案**（用户 0.1.43 拍板）。
 * 为什么不能一律浅色：卡面里有一整批夜场/暗色构图，浅化之后是灰紫、灰蓝的脏色，
 * 比任何配色都难看 —— 这不是审美问题，是"浅底容错窗口天生窄"。
 *
 * ## 两套方案都是 M3 的角色关系，只是 tone 不同
 *
 * 留声器的观感并不是"把封面颜色变浅铺满"，而是 Material You 浅色方案里
 * `surface`（几乎不染色的中性底）↔ `primary`（同色相的**深饱和**档）这一对关系。
 * 所以这里不写"近似色"，而是给浅/深各一张 **tone 表**，同一支色系分层。
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
 * 播放器调色板。浅色/深色两套**共用这一份字段**，用 [isLight] 区分。
 *
 * ## 每个角色的落点
 *
 * - [accent]：进度条已播段、曲名、上一首/下一首图标、定时器文字（"同色系的强调档"）
 * - [controlFill] / [onControlFill]：播放/暂停的**实心圆**。
 *   注意它两套方案里都是"较深的那一档"（用户原话：暂停键用封面提取色里较深的配色），
 *   所以浅色方案里它是深饱和色，深色方案里它是深容器色 —— 绝不是把 [accent] 直接拿来用。
 * - [accentContainer] / [onAccentContainer]：选中态底（队列当前行、chip）
 * - [gradientTop] / [gradientBottom]：整屏竖向渐变的两端
 * - [surfaceLow]…[surfaceHighest]：容器阶（M3 里"数字越大越靠上层"）
 * - [onSurface] / [onSurfaceVariant]：正文 / 次要文字
 * - [fadedTrack]：未播轨道
 */
data class PlayerPalette(
    /** 种子色本身（诊断用，不直接画东西）。 */
    val seed: Int,
    /** true = 浅色方案（封面偏亮），false = 深色方案（封面偏暗）。 */
    val isLight: Boolean,
    val accent: Int,
    val onAccent: Int,
    val controlFill: Int,
    val onControlFill: Int,
    val accentContainer: Int,
    val onAccentContainer: Int,
    val gradientTop: Int,
    val gradientBottom: Int,
    val surfaceLow: Int,
    val surfaceContainer: Int,
    val surfaceHigh: Int,
    val surfaceHighest: Int,
    val onSurface: Int,
    val onSurfaceVariant: Int,
    val outline: Int,
    val outlineVariant: Int,
    val fadedTrack: Int,
)

// ── 种子筛选阈值（严格档）──
private const val MIN_SATURATION = 0.28f
private const val MIN_LIGHTNESS = 0.10f
private const val MAX_LIGHTNESS = 0.90f
private const val MIN_SHARE = 0.02f

// ── 种子筛选阈值（宽松兜底档）──
private const val LOOSE_SATURATION = 0.08f
private const val LOOSE_MIN_SHARE = 0.004f
private const val LOOSE_MIN_LIGHTNESS = 0.05f
private const val LOOSE_MAX_LIGHTNESS = 0.95f

/**
 * 判浅色的门槛：封面**平均 HSL 亮度** ≥ 这个值就走浅色方案。
 *
 * 0.58 是"偏亮"而不是"过半"：浅色方案对暗封面很不宽容（见文件头），
 * 宁可多判几首走深色，也不要出现灰紫底。
 */
const val LIGHT_COVER_THRESHOLD = 0.58f

/**
 * 选种子（**严格档**）。返回 null 表示这张封面没有一个"又大又艳"的色块。
 *
 * 打分 = √(出现比例) × 饱和度 × (1 - |亮度-0.55|×0.8)：
 * 既不能只取"面积最大但灰"的背景，也不能取"鲜艳但只有几个像素"的点缀。
 */
fun pickArtworkSeed(pixels: IntArray): ArtworkSeed? =
    pickSeed(pixels, MIN_SATURATION, MIN_SHARE, MIN_LIGHTNESS, MAX_LIGHTNESS)

/**
 * 选种子（**宽松档兜底**）。
 *
 * 实机统计（0.1.42）：连续跳歌 10 首里 8 首严格档返回 null → 播放器直接掉回默认色，
 * 客观上就是"很多封面没取色"。原因很朴素：卡面是整幅插画，量化到 32 级后
 * **单桶占比很难到 2%**，而饱和度 0.28 也把柔和的画面挡在外面。
 */
fun pickArtworkSeedLoose(pixels: IntArray): ArtworkSeed? =
    pickSeed(pixels, LOOSE_SATURATION, LOOSE_MIN_SHARE, LOOSE_MIN_LIGHTNESS, LOOSE_MAX_LIGHTNESS)

private fun pickSeed(
    pixels: IntArray,
    minSaturation: Float,
    minShare: Float,
    minLightness: Float,
    maxLightness: Float,
): ArtworkSeed? {
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
        if (hsl[1] < minSaturation) continue
        if (hsl[2] < minLightness || hsl[2] > maxLightness) continue
        if (share < minShare) continue
        val score = sqrt(share.toDouble()) * hsl[1] * (1.0 - abs(hsl[2] - 0.55) * 0.8)
        if (score > bestScore) {
            bestScore = score
            best = ArtworkSeed(hue = hsl[0], saturation = hsl[1], lightness = hsl[2], share = share)
        }
    }
    return best
}

/**
 * 封面整体的平均 HSL 亮度（0..1）—— "这张封面亮不亮"的判据。
 *
 * ⚠️ 不能用 [pickArtworkSeed] 的 `lightness`：种子挑的是"又大又艳"的那一桶，
 * 一张 90% 白底 + 一点深蓝的卡，种子会是白的没错，但一张 80% 黑底 + 亮蓝高光的卡
 * 种子也会是亮蓝 —— 用种子亮度判明暗会把后者误判成浅色。所以单独算平均。
 */
fun averageLightness(pixels: IntArray): Float {
    var sum = 0.0
    var n = 0
    for (argb in pixels) {
        if (((argb ushr 24) and 0xFF) < 0x80) continue
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        sum += (max(r, max(g, b)) + min(r, min(g, b))) / 510.0
        n++
    }
    return if (n == 0) 0f else (sum / n).toFloat()
}

/**
 * 由封面像素生成播放器调色板；连宽松档都挑不出种子时才返回 null
 * （调用方回退到 App 当前主题的配色方案）。
 *
 * 明暗由 [averageLightness] 决定，见 [LIGHT_COVER_THRESHOLD]。
 */
fun playerPaletteFromPixels(pixels: IntArray): PlayerPalette? {
    // 严格档挑不出来就放宽再来一次：实机上一半以上的卡面需要这一档
    val seed = pickArtworkSeed(pixels) ?: pickArtworkSeedLoose(pixels) ?: return null
    val light = averageLightness(pixels) >= LIGHT_COVER_THRESHOLD
    return if (light) lightPalette(seed) else darkPalette(seed)
}

// ── 深色方案的 tone 表（0.1.41 上过真机，数值不动）──
private const val D_ACCENT_L = 0.78f
private const val D_ACCENT_MIN_S = 0.26f
private const val D_ON_ACCENT_L = 0.16f
private const val D_CONTROL_L = 0.30f
private const val D_ON_CONTROL_L = 0.90f
private const val D_ACCENT_CONTAINER_L = 0.30f
private const val D_ON_ACCENT_CONTAINER_L = 0.90f
private const val D_GRADIENT_TOP_L = 0.20f
private const val D_GRADIENT_BOTTOM_L = 0.07f
private const val D_SURFACE_LOW_L = 0.11f
private const val D_SURFACE_L = 0.14f
private const val D_SURFACE_HIGH_L = 0.18f
private const val D_SURFACE_HIGHEST_L = 0.22f
private const val D_ON_SURFACE_L = 0.96f
private const val D_ON_SURFACE_VARIANT_L = 0.80f
private const val D_OUTLINE_L = 0.55f
private const val D_OUTLINE_VARIANT_L = 0.30f
private const val D_FADED_L = 0.45f

private fun darkPalette(seed: ArtworkSeed): PlayerPalette {
    val h = seed.hue
    val s = seed.saturation
    // 低饱和封面（宽松档来的）如果照搬饱和度，强调色会变成灰白，看着和"没取色"一样。
    // 只给**强调色**垫一个下限：背景阶仍然跟着真实饱和度走，保持中性不出戏。
    val accentS = max(s, D_ACCENT_MIN_S)

    val accent = ensureContrast(hslToArgb(h, min(accentS, 0.85f), D_ACCENT_L), hslToArgb(h, s * 0.28f, D_GRADIENT_BOTTOM_L), 4.5)
    val accentContainer = hslToArgb(h, s * 0.55f, D_ACCENT_CONTAINER_L)
    val surfaceContainer = hslToArgb(h, s * 0.30f, D_SURFACE_L)

    return PlayerPalette(
        seed = hslToArgb(h, s, seed.lightness),
        isLight = false,
        accent = accent,
        onAccent = ensureContrast(hslToArgb(h, accentS * 0.45f, D_ON_ACCENT_L), accent, 4.5),
        controlFill = accentContainer,
        onControlFill = ensureContrast(hslToArgb(h, s * 0.30f, D_ON_CONTROL_L), accentContainer, 4.5),
        accentContainer = accentContainer,
        onAccentContainer = ensureContrast(hslToArgb(h, s * 0.30f, D_ON_ACCENT_CONTAINER_L), accentContainer, 4.5),
        gradientTop = hslToArgb(h, s * 0.45f, D_GRADIENT_TOP_L),
        gradientBottom = hslToArgb(h, s * 0.28f, D_GRADIENT_BOTTOM_L),
        surfaceLow = hslToArgb(h, s * 0.30f, D_SURFACE_LOW_L),
        surfaceContainer = surfaceContainer,
        surfaceHigh = hslToArgb(h, s * 0.32f, D_SURFACE_HIGH_L),
        surfaceHighest = hslToArgb(h, s * 0.34f, D_SURFACE_HIGHEST_L),
        onSurface = ensureContrast(hslToArgb(h, s * 0.04f, D_ON_SURFACE_L), surfaceContainer, 4.5),
        onSurfaceVariant = ensureContrast(hslToArgb(h, s * 0.10f, D_ON_SURFACE_VARIANT_L), surfaceContainer, 4.0),
        outline = hslToArgb(h, s * 0.15f, D_OUTLINE_L),
        outlineVariant = hslToArgb(h, s * 0.20f, D_OUTLINE_VARIANT_L),
        fadedTrack = hslToArgb(h, s * 0.18f, D_FADED_L),
    )
}

// ── 浅色方案的 tone 表（0.1.43 新增）──
//
// ⚠️ 踩过的坑：HSL 的**高亮度会把饱和度压没**。
// 第一版用 L=0.975 + 饱和度 0.135，算出来是 #F8F8F9 —— RGB 差只有 2/255，等于纯白，
// 用户要的"背景就是封面的浅色"完全看不出来。
// HSL 里色度 c = (1-|2L-1|)×s，L=0.975 时乘数只剩 0.05；所以浅底必须
// **把 L 降到 0.90~0.95 区间、饱和度提到 0.4~0.5**，才谈得上"看得见但不脏"。
// （对应 M3 light surface 的 tone 88~96 + chroma 6~10。）
//
//  - [accent] 是**深饱和**档（L≈0.36），它同时承担"进度条已播段 + 曲名 + 上下首图标"。
//  - [controlFill] 比 accent 更深一档 = 用户要的"较深的封面配色"。
private const val L_ACCENT_L = 0.36f
private const val L_ACCENT_MIN_S = 0.45f
private const val L_ON_ACCENT_L = 0.99f
private const val L_CONTROL_L = 0.33f
private const val L_CONTROL_MIN_S = 0.55f
private const val L_ON_CONTROL_L = 0.99f
private const val L_ACCENT_CONTAINER_L = 0.88f
private const val L_ON_ACCENT_CONTAINER_L = 0.16f
private const val L_GRADIENT_TOP_L = 0.955f
private const val L_GRADIENT_BOTTOM_L = 0.905f
private const val L_SURFACE_LOW_L = 0.945f
private const val L_SURFACE_L = 0.925f
private const val L_SURFACE_HIGH_L = 0.895f
private const val L_SURFACE_HIGHEST_L = 0.855f
private const val L_ON_SURFACE_L = 0.14f
private const val L_ON_SURFACE_VARIANT_L = 0.38f
private const val L_OUTLINE_L = 0.52f
private const val L_OUTLINE_VARIANT_L = 0.80f
private const val L_FADED_L = 0.70f

/**
 * 浅色底色的染色量：种子的饱和度 × factor，再封顶 cap。
 *
 * cap 是"别显脏"的上限 —— 浅粉/浅黄染狠了会像泛黄的白衬衫。
 */
private fun lightTint(seedSaturation: Float, factor: Float, cap: Float): Float =
    min(seedSaturation * factor, cap)

private fun lightPalette(seed: ArtworkSeed): PlayerPalette {
    val h = seed.hue
    val s = seed.saturation
    val accentS = max(s, L_ACCENT_MIN_S)
    val controlS = max(s, L_CONTROL_MIN_S)

    val gradientTop = hslToArgb(h, lightTint(s, 0.75f, 0.38f), L_GRADIENT_TOP_L)
    val gradientBottom = hslToArgb(h, lightTint(s, 0.85f, 0.48f), L_GRADIENT_BOTTOM_L)
    val surfaceContainer = hslToArgb(h, lightTint(s, 0.85f, 0.46f), L_SURFACE_L)
    val accent = ensureContrast(hslToArgb(h, accentS, L_ACCENT_L), gradientBottom, 4.5)
    val controlFill = ensureContrast(hslToArgb(h, controlS, L_CONTROL_L), gradientTop, 4.5)
    val accentContainer = hslToArgb(h, lightTint(s, 0.80f, 0.50f), L_ACCENT_CONTAINER_L)

    return PlayerPalette(
        seed = hslToArgb(h, s, seed.lightness),
        isLight = true,
        accent = accent,
        onAccent = ensureContrast(hslToArgb(h, s * 0.20f, L_ON_ACCENT_L), accent, 4.5),
        controlFill = controlFill,
        // 图标压在实心圆上：底色已经是深档，字必须是亮的
        onControlFill = ensureContrast(hslToArgb(h, s * 0.15f, L_ON_CONTROL_L), controlFill, 4.5),
        accentContainer = accentContainer,
        onAccentContainer = ensureContrast(hslToArgb(h, s * 0.80f, L_ON_ACCENT_CONTAINER_L), accentContainer, 4.5),
        gradientTop = gradientTop,
        gradientBottom = gradientBottom,
        surfaceLow = hslToArgb(h, lightTint(s, 0.80f, 0.42f), L_SURFACE_LOW_L),
        surfaceContainer = surfaceContainer,
        surfaceHigh = hslToArgb(h, lightTint(s, 0.85f, 0.46f), L_SURFACE_HIGH_L),
        surfaceHighest = hslToArgb(h, lightTint(s, 0.80f, 0.44f), L_SURFACE_HIGHEST_L),
        onSurface = ensureContrast(hslToArgb(h, s * 0.06f, L_ON_SURFACE_L), surfaceContainer, 4.5),
        onSurfaceVariant = ensureContrast(hslToArgb(h, s * 0.12f, L_ON_SURFACE_VARIANT_L), surfaceContainer, 4.0),
        outline = hslToArgb(h, s * 0.18f, L_OUTLINE_L),
        outlineVariant = hslToArgb(h, s * 0.20f, L_OUTLINE_VARIANT_L),
        fadedTrack = hslToArgb(h, s * 0.25f, L_FADED_L),
    )
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
 * 为什么需要：种子的色相/饱和度不可控，同一套固定 tone 在某些色相上会算出
 * 对比度不足的组合（例如黄绿色在 L=0.40 配白字只有 3.1:1）。
 * 与其为每种色相调参数，不如算出结果后**兜底推一把** —— 最多 25 步，每步 0.04。
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
