package com.pjsk.toolbox.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.pjsk.toolbox.data.settings.ThemeMode

/**
 * 配色。
 *
 * 主色用**初音绿 `#39C5BB`**（`#39C5BB` 就是需求文档里指定的那支）。
 * 顺带一提，网页版里那个 `--ring / --chart-1` 的 `hsl(174 58% 50%)` 换算出来约 `#35C9BB`，
 * 几乎是同一个颜色，所以两端观感是一致的。
 *
 * 不使用任何官方素材或 Logo，色值都是按观感手调的。
 */
private val MikuGreen = Color(0xFF39C5BB)

private val LightColors = lightColorScheme(
    primary = MikuGreen,
    onPrimary = Color(0xFF00312D),
    primaryContainer = Color(0xFFB8EFE9),
    onPrimaryContainer = Color(0xFF00201D),
    secondary = Color(0xFF4A6360),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCE8E4),
    onSecondaryContainer = Color(0xFF06201D),
    tertiary = Color(0xFFEF7BA0),
    onTertiary = Color.White,
    background = Color(0xFFF5FAF9),
    onBackground = Color(0xFF171D1C),
    surface = Color(0xFFF5FAF9),
    onSurface = Color(0xFF171D1C),
    surfaceVariant = Color(0xFFDAE5E3),
    onSurfaceVariant = Color(0xFF3F4947),
    outline = Color(0xFF6F7977),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
)

/**
 * 深色配色。背景到卡片的两级取自需求文档：`#0f0f1a` → `#1a1a2e`。
 *
 * 深色下主色要**提亮**（`#7FE3DA`）：`#39C5BB` 直接放在 `#0F0F1A` 上对比度不够，
 * 做按钮底色或细字时看不清。
 */
private val DarkColors = darkColorScheme(
    primary = Color(0xFF7FE3DA),
    onPrimary = Color(0xFF00312D),
    primaryContainer = Color(0xFF00504A),
    onPrimaryContainer = Color(0xFFB8EFE9),
    secondary = Color(0xFFB1CCC8),
    onSecondary = Color(0xFF1C3532),
    secondaryContainer = Color(0xFF324B48),
    onSecondaryContainer = Color(0xFFCCE8E4),
    tertiary = Color(0xFFFFB0C8),
    onTertiary = Color(0xFF5A1133),
    background = Color(0xFF0F0F1A),
    onBackground = Color(0xFFE4E1EC),
    surface = Color(0xFF0F0F1A),
    onSurface = Color(0xFFE4E1EC),
    surfaceVariant = Color(0xFF1A1A2E),
    onSurfaceVariant = Color(0xFFC6C4D8),
    outline = Color(0xFF9090A8),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

/**
 * 启动窗口的底色。
 *
 * 为什么要单独定义：Compose 的首帧画出来之前，屏幕上显示的是**窗口背景**
 * （由 `themes.xml` 的 `android:windowBackground` 决定）。
 * 如果那里写死浅色，用户选了「始终深色」启动时就会闪一下白屏。
 * `values-night` 只能跟随**系统**夜间模式，管不了 App 内自己切的主题，
 * 所以 MainActivity 还会按当前设置再覆盖一次窗口底色。
 */
val WindowBackgroundLight = Color(0xFFF5FAF9)
val WindowBackgroundDark = Color(0xFF0F0F1A)

/** 把外观模式解析成「现在到底是不是深色」。 */
@Composable
fun ThemeMode.resolveDark(): Boolean = when (this) {
    ThemeMode.FOLLOW_SYSTEM -> isSystemInDarkTheme()
    ThemeMode.DARK -> true
    ThemeMode.LIGHT -> false
}

/**
 * @param themeMode 外观模式。默认跟随系统；由设置页决定实际取值。
 */
@Composable
fun SekaiLmcTheme(
    themeMode: ThemeMode = ThemeMode.FOLLOW_SYSTEM,
    content: @Composable () -> Unit,
) {
    val darkTheme = themeMode.resolveDark()
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
