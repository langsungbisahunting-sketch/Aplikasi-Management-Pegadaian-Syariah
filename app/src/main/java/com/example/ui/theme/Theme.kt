package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AmanahLightColorScheme = lightColorScheme(
    primary = EmeraldPrimary,
    onPrimary = Color.White,
    primaryContainer = EmeraldLightTint,
    onPrimaryContainer = ForestDark,
    secondary = ForestDark,
    onSecondary = Color.White,
    secondaryContainer = YellowPanelBg,
    onSecondaryContainer = YellowText,
    tertiary = YellowText,
    onTertiary = Color.White,
    background = SurfaceSubtle,
    onBackground = TextBody,
    surface = SurfaceWhite,
    onSurface = TextBody,
    surfaceVariant = SurfaceSubtle,
    onSurfaceVariant = TextMuted,
    outline = BorderInput,
    outlineVariant = BorderCard,
    error = DangerText,
    onError = Color.White,
    errorContainer = DangerBg,
    onErrorContainer = DangerText
)

@Composable
fun AmanahGadaiTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = AmanahLightColorScheme,
        typography = Typography,
        content = content
    )
}
