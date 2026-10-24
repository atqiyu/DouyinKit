package com.atqiyu.douyinkit.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val DarkColors = darkColorScheme(
    primary = BrandPink,
    onPrimary = PaperSurface,
    primaryContainer = ColorTokens.DarkPinkContainer,
    onPrimaryContainer = ColorTokens.DarkPinkOnContainer,
    secondary = BrandCyan,
    onSecondary = Ink,
    secondaryContainer = ColorTokens.DarkCyanContainer,
    onSecondaryContainer = ColorTokens.DarkCyanOnContainer,
    tertiary = ColorTokens.DarkTertiary,
    onTertiary = Ink,
    background = Ink,
    onBackground = InkOnSurface,
    surface = Ink,
    onSurface = InkOnSurface,
    surfaceVariant = InkSurfaceVariant,
    onSurfaceVariant = InkOnSurfaceVariant,
    surfaceContainer = InkSurface,
    surfaceContainerHigh = ColorTokens.DarkSurfaceHigh,
    surfaceContainerHighest = ColorTokens.DarkSurfaceHighest,
    outline = InkOutline,
    outlineVariant = ColorTokens.DarkOutlineVariant,
    error = ColorTokens.DarkError,
    onError = Ink,
)

private val LightColors = lightColorScheme(
    primary = BrandPinkDark,
    onPrimary = PaperSurface,
    primaryContainer = ColorTokens.LightPinkContainer,
    onPrimaryContainer = ColorTokens.LightPinkOnContainer,
    secondary = ColorTokens.LightCyan,
    onSecondary = PaperSurface,
    secondaryContainer = ColorTokens.LightCyanContainer,
    onSecondaryContainer = ColorTokens.LightCyanOnContainer,
    tertiary = ColorTokens.LightTertiary,
    onTertiary = PaperSurface,
    background = Paper,
    onBackground = PaperOnSurface,
    surface = Paper,
    onSurface = PaperOnSurface,
    surfaceVariant = PaperSurfaceVariant,
    onSurfaceVariant = PaperOnSurfaceVariant,
    surfaceContainer = PaperSurface,
    surfaceContainerHigh = ColorTokens.LightSurfaceHigh,
    surfaceContainerHighest = ColorTokens.LightSurfaceHighest,
    outline = PaperOutline,
    outlineVariant = ColorTokens.LightOutlineVariant,
    error = ColorTokens.LightError,
    onError = PaperSurface,
)

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun DouyinKitTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}

private object ColorTokens {
    val DarkPinkContainer = androidx.compose.ui.graphics.Color(0xFF3B0E1B)
    val DarkPinkOnContainer = androidx.compose.ui.graphics.Color(0xFFFFD9E2)
    val DarkCyanContainer = androidx.compose.ui.graphics.Color(0xFF003B3A)
    val DarkCyanOnContainer = androidx.compose.ui.graphics.Color(0xFFB9FFFC)
    val DarkTertiary = androidx.compose.ui.graphics.Color(0xFFFFD27A)
    val DarkSurfaceHigh = androidx.compose.ui.graphics.Color(0xFF1C1C24)
    val DarkSurfaceHighest = androidx.compose.ui.graphics.Color(0xFF262630)
    val DarkOutlineVariant = androidx.compose.ui.graphics.Color(0xFF2A2A34)
    val DarkError = androidx.compose.ui.graphics.Color(0xFFFFB4AB)

    val LightPinkContainer = androidx.compose.ui.graphics.Color(0xFFFFD9E2)
    val LightPinkOnContainer = androidx.compose.ui.graphics.Color(0xFF3B0E1B)
    val LightCyan = androidx.compose.ui.graphics.Color(0xFF007F7B)
    val LightCyanContainer = androidx.compose.ui.graphics.Color(0xFFB9FFFC)
    val LightCyanOnContainer = androidx.compose.ui.graphics.Color(0xFF00302F)
    val LightTertiary = androidx.compose.ui.graphics.Color(0xFF8A5A00)
    val LightSurfaceHigh = androidx.compose.ui.graphics.Color(0xFFFFFFFF)
    val LightSurfaceHighest = androidx.compose.ui.graphics.Color(0xFFE9E6EF)
    val LightOutlineVariant = androidx.compose.ui.graphics.Color(0xFFE6E2EC)
    val LightError = androidx.compose.ui.graphics.Color(0xFFBA1A1A)
}
