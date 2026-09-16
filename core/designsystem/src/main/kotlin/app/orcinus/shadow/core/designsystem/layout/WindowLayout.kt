package app.orcinus.shadow.core.designsystem.layout

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.window.core.layout.WindowSizeClass

/** How much of OrcaSlicer's desktop layout fits the window. */
enum class OrcaWindowLayout {
    /** Phone in portrait: panels move into sheets, actions next to the thumb. */
    Compact,

    /** Landscape phone, foldable, tablet: sidebar beside the canvas, actions in the tab bar. */
    Wide,
}

@Composable
fun currentOrcaWindowLayout(): OrcaWindowLayout =
    if (currentWindowAdaptiveInfoV2().windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)) {
        OrcaWindowLayout.Wide
    } else {
        OrcaWindowLayout.Compact
    }
