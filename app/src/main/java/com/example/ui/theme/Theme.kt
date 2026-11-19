package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val AuraDarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = OnElectricCyan,
    primaryContainer = ElectricCyanContainer,
    onPrimaryContainer = OnElectricCyanContainer,
    secondary = StudioAmber,
    onSecondary = OnStudioAmber,
    secondaryContainer = StudioAmberContainer,
    onSecondaryContainer = OnStudioAmberContainer,
    tertiary = LiveCoral,
    onTertiary = Color.White,
    tertiaryContainer = LiveCoralContainer,
    onTertiaryContainer = Color(0xFFFFD9DF),
    background = ObsidianNight,
    onBackground = CrispOffWhite,
    surface = StudioSurface,
    onSurface = CrispOffWhite,
    surfaceVariant = StudioCard,
    onSurfaceVariant = MutedSlate,
    surfaceContainerLow = StudioSurface,
    surfaceContainer = StudioCard,
    surfaceContainerHigh = StudioCardElevated,
    outline = StudioBorder,
    outlineVariant = Color(0xFF1E293B),
    error = LiveCoral,
    onError = Color.White
)

private val AuraLightColorScheme = lightColorScheme(
    primary = DeepTealPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB3F0FF),
    onPrimaryContainer = Color(0xFF001F26),
    secondary = WarmGoldSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDEA8),
    onSecondaryContainer = Color(0xFF281800),
    background = LightCanvas,
    onBackground = Color(0xFF0F172A),
    surface = LightSurface,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = LightCard,
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFCBD5E1)
)

val AuraShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun AuraWaveTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) AuraDarkColorScheme else AuraLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AuraTypography,
        shapes = AuraShapes,
        content = content
    )
}
