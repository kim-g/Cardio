package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val CardioDarkColorScheme = darkColorScheme(
    primary = ArterialCrimson,
    onPrimary = TextPrimary,
    primaryContainer = StudioGlassElevated,
    onPrimaryContainer = ArterialCrimsonGlow,
    secondary = VenousCobalt,
    onSecondary = TextPrimary,
    secondaryContainer = StudioGlassElevated,
    onSecondaryContainer = VenousCobaltGlow,
    tertiary = EcgMint,
    onTertiary = StudioObsidian,
    background = StudioObsidian,
    onBackground = TextPrimary,
    surface = StudioGlassSurface,
    onSurface = TextPrimary,
    surfaceVariant = StudioDeepNavy,
    onSurfaceVariant = TextSecondary,
    outline = StudioBorder,
    outlineVariant = StudioBorderBright
)

@Composable
fun Cardio3DTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = CardioDarkColorScheme,
        typography = CardioTypography,
        content = content
    )
}
