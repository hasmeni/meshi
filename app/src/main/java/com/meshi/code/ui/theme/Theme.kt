package com.meshi.code.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.meshi.code.prefs.ThemeMode

// Slightly larger corner radii for an organic, hand-crafted feel
private val SketchShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small      = RoundedCornerShape(8.dp),
    medium     = RoundedCornerShape(12.dp),
    large      = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

private val SketchDarkColorScheme = darkColorScheme(
    primary              = SkDarkPrimary,
    onPrimary            = SkDarkOnPrimary,
    primaryContainer     = SkDarkPrimaryContainer,
    onPrimaryContainer   = SkDarkOnPrimaryContainer,
    secondary            = SkDarkSecondary,
    onSecondary          = SkDarkOnSecondary,
    secondaryContainer   = SkDarkSecondaryContainer,
    onSecondaryContainer = SkDarkOnSecondaryContainer,
    background           = SkDarkBackground,
    surface              = SkDarkSurface,
    surfaceVariant       = SkDarkSurfaceVariant,
    onBackground         = SkDarkOnBackground,
    onSurface            = SkDarkOnSurface,
    onSurfaceVariant     = SkDarkOnSurfaceVariant,
    outline              = SkDarkOutline,
    error                = SkDarkError,
    onError              = SkDarkOnError,
    errorContainer       = SkDarkErrorContainer,
    onErrorContainer     = SkDarkOnErrorContainer,
)

private val SketchLightColorScheme = lightColorScheme(
    primary              = SkLightPrimary,
    onPrimary            = SkLightOnPrimary,
    primaryContainer     = SkLightPrimaryContainer,
    onPrimaryContainer   = SkLightOnPrimaryContainer,
    secondary            = SkLightSecondary,
    onSecondary          = SkLightOnSecondary,
    secondaryContainer   = SkLightSecondaryContainer,
    onSecondaryContainer = SkLightOnSecondaryContainer,
    background           = SkLightBackground,
    surface              = SkLightSurface,
    surfaceVariant       = SkLightSurfaceVariant,
    onBackground         = SkLightOnBackground,
    onSurface            = SkLightOnSurface,
    onSurfaceVariant     = SkLightOnSurfaceVariant,
    outline              = SkLightOutline,
    error                = SkLightError,
    onError              = SkLightOnError,
    errorContainer       = SkLightErrorContainer,
    onErrorContainer     = SkLightOnErrorContainer,
)

@Composable
fun MeshiTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit,
) {
    val useDark = when (themeMode) {
        ThemeMode.DARK   -> true
        ThemeMode.LIGHT  -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (useDark) SketchDarkColorScheme else SketchLightColorScheme,
        typography  = SketchTypography,
        shapes      = SketchShapes,
        content     = content,
    )
}
