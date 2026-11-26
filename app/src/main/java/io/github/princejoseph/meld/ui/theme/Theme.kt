package io.github.princejoseph.meld.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Immutable
data class MeldColorTokens(
    val canvas: Color,
    val surfaceElevated: Color,
    val surfaceInset: Color,
    val inkPrimary: Color,
    val inkSecondary: Color,
    val hairline: Color,
    val vermilion: Color,
    val emerald: Color,
    val progressFill: Color,
    val emeraldFill: Color,
    val dotGrid: Color,
    val isDark: Boolean
)

private val LightMeldTokens = MeldColorTokens(
    canvas = WarmCanvas,
    surfaceElevated = WarmElevated,
    surfaceInset = WarmInset,
    inkPrimary = WarmInkPrimary,
    inkSecondary = WarmInkSecondary,
    hairline = WarmHairline,
    vermilion = WarmVermilion,
    emerald = WarmEmerald,
    progressFill = WarmVermilion.copy(alpha = 0.10f),
    emeraldFill = WarmEmerald.copy(alpha = 0.12f),
    // Background texture only: kept quiet so body text on the canvas stays legible.
    dotGrid = WarmInkSecondary.copy(alpha = 0.16f),
    isDark = false
)

private val DarkMeldTokens = MeldColorTokens(
    canvas = ObsidianCanvas,
    surfaceElevated = ObsidianElevated,
    surfaceInset = ObsidianInset,
    inkPrimary = ObsidianInkPrimary,
    inkSecondary = ObsidianInkSecondary,
    hairline = ObsidianHairline,
    vermilion = ObsidianVermilion,
    emerald = ObsidianEmerald,
    progressFill = ObsidianVermilion.copy(alpha = 0.15f),
    emeraldFill = ObsidianEmerald.copy(alpha = 0.16f),
    // Note: Color.copy(alpha = x) REPLACES alpha, it does not multiply it. The previous
    // ObsidianHairline.copy(alpha = 0.65f) produced 65% white dots that fought the text.
    dotGrid = Color.White.copy(alpha = 0.10f),
    isDark = true
)

val LocalMeldColors = staticCompositionLocalOf { LightMeldTokens }

private val MeldLightColorScheme = lightColorScheme(
    primary = WarmVermilion,
    onPrimary = Color.White,
    secondary = WarmEmerald,
    onSecondary = Color.White,
    background = WarmCanvas,
    onBackground = WarmInkPrimary,
    surface = WarmElevated,
    onSurface = WarmInkPrimary,
    surfaceVariant = WarmInset,
    onSurfaceVariant = WarmInkSecondary,
    outline = WarmHairline
)

private val MeldDarkColorScheme = darkColorScheme(
    primary = ObsidianVermilion,
    onPrimary = Color.White,
    secondary = ObsidianEmerald,
    onSecondary = Color(0xFF061B19),
    background = ObsidianCanvas,
    onBackground = ObsidianInkPrimary,
    surface = ObsidianElevated,
    onSurface = ObsidianInkPrimary,
    surfaceVariant = ObsidianInset,
    onSurfaceVariant = ObsidianInkSecondary,
    outline = ObsidianHairline
)

private val MeldShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp)
)

object MeldTheme {
    val colors: MeldColorTokens
        @Composable get() = LocalMeldColors.current
}

@Composable
fun MeldAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val tokens = if (darkTheme) DarkMeldTokens else LightMeldTokens
    val colorScheme = if (darkTheme) MeldDarkColorScheme else MeldLightColorScheme

    CompositionLocalProvider(LocalMeldColors provides tokens) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MeldTypography,
            shapes = MeldShapes,
            content = content
        )
    }
}
