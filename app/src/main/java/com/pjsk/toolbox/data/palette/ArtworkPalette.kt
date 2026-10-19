package com.pjsk.toolbox.data.palette

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * 封面取色：从封面里抽一个**种子色**，再派生出播放器的一整份调色板。
 *
 * ## 0.1.45 的转向：**恒定浅色**，明暗与封面无关（用户拍板）
 *
 * 0.1.43 曾做"封面平均亮度 ≥0.58 切深色"，0.1.44 把深色表调成近中性 —— 用户两版都不满意：
 * "不喜欢深色模式，好几个封面配合的都是深色模式"。0.1.45 推翻：**删除整条深色路径，
 * 播放器永远是浅色**；种子只提供色相，无论封面多暗。这正是 Gramophone 的结构
 * （`FullBottomSheet.addColorScheme()` 用 `DynamicColorsOptions.setContentBasedSource(bitmap)`
 * 给每首歌生成 M3 scheme，明暗跟主题；只是我们这里恒浅）。
 *
 * 相应地，播放键也从"深色实心圆"改成 **Gramophone 式浅 tonal 圆**
 * （UI 侧用 `FilledTonalIconButton`，吃 `secondaryContainer` 角色）。
 *
 * ## 派生单位是"目标色度"（0.1.44 引入，保留）
 *
 * 每个角色给**目标色度 c（RGB 上 (max-min)/255）+ 明度 L**，种子只提供色相，
 * 饱和度由 `saturationForChroma(c, L)` 反解。这样：
 *  - 底色可以精确停在"看得出色相但不脏"的窄带里；
 *  - **暖色（15°~75°，橙/黄/黄绿）的色度压 0.78 倍**、红/洋红压 0.9 倍 —— 处理 HSL 对暖色偏热的问题；
 *  - 以后调"脏不脏"只改表一个数。
 *
 * ## 兜底链（0.1.42，保留）
 *
 * 严格档挑不出种子 → 宽松档；都挑不出 → 返回 null，调用方回落 App 主题。
 *
 * ## 这一层是纯函数
 *
 * 只处理 ARGB Int，不碰 Android/Compose，所以能进 `tools/logic-check` 断言。
 */

/** 从封面里选出的种子。`hue` 0..360，`saturation`/`lightness` 0..1，`share` 是它占的像素比例。 */
data class ArtworkSeed(
    val hue: Float,
    val saturation: Float,
    val lightness: Float,
    val share: Float,
)

/**
 * 播放器**浅色**调色板（0.1.45 起没有深色版）。
 *
 * ## 角色落点（对照 Gramophone 的 `applyColorScheme()`）
 *
 * - [accent]：进度条已播段、曲名、上一首/下一首图标（Gramophone 在这些位放 `colorPrimary`；
 *   唯一差异：Gramophone 的上一首/下一首是中性 `colorOnSurface`，我们按用户要求保留封面色）
 * - [onAccent]：压在 [accent] 上的前景（时间高亮、chip）
 * - [accentContainer] / [onAccentContainer]：**播放/暂停的浅 tonal 圆** +
 *   选中态底 —— Gramophone 的播放键就是 `colorSecondaryContainer`
 * - [gradientTop] / [gradientBottom]：整屏竖向渐变（**近中性**，只带一丝色相）
 * - [surfaceLow]…[surfaceHighest]：容器阶（M3"数字越大越靠上层"）
 * - [onSurface] / [onSurfaceVariant]：正文 / 次要文字（中性，不发脏）
 * - [outline]：分隔/描边；[outlineVariant]：极淡的描边色
 * - [fadedTrack]：**未播轨道**（Gramophone 的 `COLOR_CONTRAST_FAINTED`：
 *   secondaryContainer 的 chroma ×0.7、lighting ×0.97 —— 这里用同一条种子色相算出来的淡色）
 */
data class PlayerPalette(
    /** 种子色本身（诊断用，不直接画东西）。 */
    val seed: Int,
    val accent: Int,
    val onAccent: Int,
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

/** 强调色的目标色度区间：下限保证"有封面感"，上限治"荧光橙"。 */
private const val ACCENT_CHROMA_MIN = 0.09f
private const val ACCENT_CHROMA_MAX = 0.17f

// ── 浅色方案 tone 表（0.1.45：恒浅色，每行 = 明度 + 目标色度）──
private const val GRADIENT_TOP_L = 0.955f
private const val GRADIENT_TOP_C = 0.035f
private const val GRADIENT_BOTTOM_L = 0.905f
private const val GRADIENT_BOTTOM_C = 0.075f
private const val SURFACE_LOW_L = 0.945f
private const val SURFACE_LOW_C = 0.060f
private const val SURFACE_L = 0.925f
private const val SURFACE_C = 0.070f
private const val SURFACE_HIGH_L = 0.895f
private const val SURFACE_HIGH_C = 0.075f
private const val SURFACE_HIGHEST_L = 0.855f
private const val SURFACE_HIGHEST_C = 0.080f
private const val ACCENT_L = 0.34f
private const val ON_ACCENT_L = 0.99f
private const val ACCENT_CONTAINER_L = 0.88f
private const val ACCENT_CONTAINER_C = 0.070f
private const val ON_ACCENT_CONTAINER_L = 0.16f
private const val ON_SURFACE_L = 0.15f
private const val ON_SURFACE_VARIANT_L = 0.40f
private const val OUTLINE_L = 0.52f
private const val OUTLINE_VARIANT_L = 0.80f
private const val FADED_TRACK_L = 0.85f

private fun palette(seed: ArtworkSeed): PlayerPalette {
    val h = seed.hue
    val accentC = (ACCENT_CHROMA_MIN + 0.08f * seed.saturation).coerceIn(ACCENT_CHROMA_MIN, ACCENT_CHROMA_MAX)

    val gradientTop = tone(GRADIENT_TOP_L, GRADIENT_TOP_C, h)
    val gradientBottom = tone(GRADIENT_BOTTOM_L, GRADIENT_BOTTOM_C, h)
    val surfaceContainer = tone(SURFACE_L, SURFACE_C, h)
    val accentContainer = tone(ACCENT_CONTAINER_L, ACCENT_CONTAINER_C, h)
    val accent = ensureContrast(tone(ACCENT_L, accentC, h), gradientBottom, 4.5)

    // 未播轨道 = 照 Gramophone 的 COLOR_CONTRAST_FAINTED：accentContainer 的 chroma×0.7、lighting×0.97
    val fadedChroma = ACCENT_CONTAINER_C * 0.7f
    val fadedLightness = ACCENT_CONTAINER_L * 0.97f

    return PlayerPalette(
        seed = hslToArgb(h, seed.saturation, seed.lightness),
        accent = accent,
        onAccent = ensureContrast(tone(ON_ACCENT_L, 0.005f, h), accent, 4.5),
        accentContainer = accentContainer,
        onAccentContainer = ensureContrast(tone(ON_ACCENT_CONTAINER_L, 0.02f, h), accentContainer, 4.5),
        gradientTop = gradientTop,
        gradientBottom = gradientBottom,
        surfaceLow = tone(SURFACE_LOW_L, SURFACE_LOW_C, h),
        surfaceContainer = surfaceContainer,
        surfaceHigh = tone(SURFACE_HIGH_L, SURFACE_HIGH_C, h),
        surfaceHighest = tone(SURFACE_HIGHEST_L, SURFACE_HIGHEST_C, h),
        onSurface = ensureContrast(tone(ON_SURFACE_L, 0.010f, h), surfaceContainer, 4.5),
        onSurfaceVariant = ensureContrast(tone(ON_SURFACE_VARIANT_L, 0.020f, h), surfaceContainer, 4.0),
        outline = tone(OUTLINE_L, 0.025f, h),
        outlineVariant = tone(OUTLINE_VARIANT_L, 0.025f, h),
        fadedTrack = tone(fadedLightness, fadedChroma, h),
    )
}

// ── 色度 → 饱和度的换算与色相修正 ──

/**
 * HSL 的色度 `c = (1-|2L-1|)×s` → 由目标色度**反解**需要的饱和度。
 *
 * 高明度/低明度时"分母"（1-|2L-1|）变小，同样 c 需要很大的 s —— 反解自动处理了这件事
 * （0.1.43 第一版没做这一步，L=0.975 时算出 #F8F8F9 等于纯白）。
 * s 超过 1 说明目标 L 下给不出这个 c，封顶 1（宁可色度略低于目标，也不要炸掉）。
 */
private fun saturationForChroma(chroma: Float, lightness: Float): Float {
    val span = 1f - abs(2f * lightness - 1f)
    if (span <= 0.02f) return 0f
    return (chroma / span).coerceIn(0f, 1f)
}

/**
 * 暖色在同样色度下看起来更热 —— 这是 HSL / RGB 的通病，Material You 里
 * 各色相的 chroma 上限也不同。这里把 15°~75°（橙/黄/黄绿）压 0.78 倍、
 * 330°~15°（红/洋红）压 0.9 倍。0.1.44 用户拍板："橙色不要那么冲"。
 */
private fun dampedChroma(chroma: Float, hue: Float): Float {
    val h = ((hue % 360f) + 360f) % 360f
    return when {
        h in 15f..75f -> chroma * 0.78f
        h >= 330f || h < 15f -> chroma * 0.90f
        else -> chroma
    }
}

/** 组装一个"明度 + 目标色度（带暖色压低）+ 种子色相"的颜色，等于整份派生的基础算子。 */
private fun tone(lightness: Float, chroma: Float, hue: Float): Int =
    hslToArgb(hue, saturationForChroma(dampedChroma(chroma, hue), lightness), lightness)

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
 * 实机统计（0.1.42）：连跳 10 首有 8 首严格档返回 null → 播放器掉回默认色。
 * 原因：卡面是整幅插画，量化到 32 级后单桶占比很难到 2%，饱和度 0.28 也挡掉柔和画面。
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
 * 由封面像素生成播放器调色板；连宽松档都挑不出种子时才返回 null
 * （调用方回退到 App 主题的浅色方案）。
 *
 * 0.1.45 起不再判封面明暗 —— **无论封面多暗都生成浅色**（用户拍板），
 * 种子只贡献色相，这是 Gramophone 的结构（seed → M3 scheme，明暗跟主题）。
 */
fun playerPaletteFromPixels(pixels: IntArray): PlayerPalette? {
    // 严格档挑不出来就放宽再来一次：实机上一半以上的卡面需要这一档
    val seed = pickArtworkSeed(pixels) ?: pickArtworkSeedLoose(pixels) ?: return null
    return palette(seed)
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
