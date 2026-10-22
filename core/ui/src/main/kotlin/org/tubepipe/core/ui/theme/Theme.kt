package org.tubepipe.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

// Dark only: docs/design-system.md defines no light scheme.
private val TubepipeColorScheme = darkColorScheme(
    primary = TubepipeColors.Primary,
    onPrimary = TubepipeColors.OnPrimary,
    primaryContainer = TubepipeColors.PrimaryContainer,
    onPrimaryContainer = TubepipeColors.OnPrimaryContainer,
    background = TubepipeColors.Background,
    onBackground = TubepipeColors.OnSurface,
    surface = TubepipeColors.Surface,
    onSurface = TubepipeColors.OnSurface,
    surfaceVariant = TubepipeColors.SurfaceVariant,
    onSurfaceVariant = TubepipeColors.OnSurfaceVariant,
    border = TubepipeColors.FocusBorder,
    borderVariant = TubepipeColors.BorderVariant,
    scrim = TubepipeColors.Scrim,
    error = TubepipeColors.Error,
    errorContainer = TubepipeColors.ErrorContainer,
)

@Composable
fun TubepipeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TubepipeColorScheme,
        typography = TubepipeTypography,
        content = content,
    )
}
