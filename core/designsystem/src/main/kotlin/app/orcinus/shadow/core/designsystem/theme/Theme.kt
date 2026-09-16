package app.orcinus.shadow.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

val LocalOrcaColors = staticCompositionLocalOf { OrcaLightColors }
val LocalOrcaTypography = staticCompositionLocalOf { DefaultOrcaTypography }
val LocalOrcaShapes = staticCompositionLocalOf { OrcaShapes() }
val LocalOrcaDimensions = staticCompositionLocalOf { OrcaDimensions() }

/** Access to the current Orca theme values. */
object OrcaTheme {
    val colors: OrcaColors
        @Composable get() = LocalOrcaColors.current
    val typography: OrcaTypography
        @Composable get() = LocalOrcaTypography.current
    val shapes: OrcaShapes
        @Composable get() = LocalOrcaShapes.current
    val dimensions: OrcaDimensions
        @Composable get() = LocalOrcaDimensions.current
}

/**
 * The app theme: OrcaSlicer's colours, type, and shapes, light or dark with the
 * system. Material components inside read the matching Material roles.
 */
@Composable
fun OrcinusTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) OrcaDarkColors else OrcaLightColors
    CompositionLocalProvider(
        LocalOrcaColors provides colors,
        LocalOrcaTypography provides DefaultOrcaTypography,
        LocalOrcaShapes provides OrcaShapes(),
        LocalOrcaDimensions provides OrcaDimensions(),
    ) {
        MaterialTheme(
            colorScheme = colors.toMaterialColorScheme(),
            typography = OrcaMaterialTypography,
            content = content,
        )
    }
}

private fun OrcaColors.toMaterialColorScheme() = if (isDark) {
    darkColorScheme(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accentSelected,
        onPrimaryContainer = text,
        secondary = secondary,
        background = window,
        onBackground = text,
        surface = window,
        onSurface = text,
        onSurfaceVariant = textSide,
        surfaceContainerLowest = window,
        surfaceContainerLow = sidebarTitleBottom,
        surfaceContainer = window,
        surfaceContainerHigh = window,
        surfaceContainerHighest = buttonBackground,
        outline = border,
        outlineVariant = separator,
        error = error,
    )
} else {
    lightColorScheme(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accentSelected,
        onPrimaryContainer = text,
        secondary = secondary,
        background = window,
        onBackground = text,
        surface = window,
        onSurface = text,
        onSurfaceVariant = textSide,
        surfaceContainerLowest = window,
        surfaceContainerLow = sidebarTitleBottom,
        surfaceContainer = window,
        surfaceContainerHigh = window,
        surfaceContainerHighest = buttonBackground,
        outline = border,
        outlineVariant = separator,
        error = error,
    )
}
