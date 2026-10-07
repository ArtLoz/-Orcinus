package app.orcinus.shadow.core.designsystem.layout

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.window.core.layout.WindowSizeClass
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme

/**
 * How much of OrcaSlicer's desktop layout fits the window, by Material's width
 * classes: compact below 600 dp, medium up to 840 dp, expanded from there.
 */
enum class OrcaWindowLayout {
    /** Phone in portrait: panels move into sheets, actions next to the thumb. */
    Compact,

    /**
     * Tablet in portrait, unfolded foldable: the canvas keeps the whole width
     * and the sidebar opens over it; the canvas and the tab bar as on desktop.
     */
    Medium,

    /** Tablet in landscape, desktop window: the sidebar docked beside the canvas, as on desktop. */
    Expanded,
    ;

    /** Wider than a phone: the canvas's toolbars, windows and panels as on desktop, the slice button in the tab bar. */
    val wide: Boolean get() = this != Compact

    /** The sidebar stands beside the content; otherwise it opens over it. */
    val docksSidebar: Boolean get() = this == Expanded
}

@Composable
fun currentOrcaWindowLayout(): OrcaWindowLayout {
    val sizeClass = currentWindowAdaptiveInfoV2().windowSizeClass
    return when {
        sizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND) -> OrcaWindowLayout.Expanded
        sizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) -> OrcaWindowLayout.Medium
        else -> OrcaWindowLayout.Compact
    }
}

/**
 * Whether the window is at least Material's medium height (480 dp), with room
 * for a column of tools beside the canvas; a phone in landscape is wide but
 * not tall.
 */
@Composable
fun currentOrcaWindowTall(): Boolean =
    currentWindowAdaptiveInfoV2().windowSizeClass.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND)

/**
 * The width of the docked sidebar: wider on a large window (from 1200 dp), as
 * OrcaSlicer's sidebar is at least 39 em (Sidebar::Sidebar(), msw_rescale()),
 * so the names of the presets and of the plate type fit.
 */
@Composable
internal fun currentOrcaSidebarWidth(): Dp =
    if (currentWindowAdaptiveInfoV2().windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_LARGE_LOWER_BOUND)) {
        OrcaTheme.dimensions.sidebarWidthLarge
    } else {
        OrcaTheme.dimensions.sidebarWidth
    }
