package app.orcinus.shadow.core.designsystem.layout

import androidx.compose.material3.adaptive.HingeInfo
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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

    /** The sidebar stands beside the content; otherwise it opens over it, unless a fold puts it below (currentOrcaSidebarDocked()). */
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
 * not tall, and so is the part of a foldable on a table above its fold.
 */
@Composable
fun currentOrcaWindowTall(): Boolean {
    val fold = currentOrcaTabletopFold()
    if (fold != null) return with(LocalDensity.current) { fold.bounds.top.toDp() } >= WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND.dp
    return currentWindowAdaptiveInfoV2().windowSizeClass.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND)
}

/**
 * The fold of a foldable held half open like a laptop on a table, in a window
 * wider than a phone: the canvas stands above it and the sidebar lies below it.
 * Null when the window does not fold across.
 */
@Composable
fun currentOrcaTabletopFold(): HingeInfo? {
    val info = currentWindowAdaptiveInfoV2()
    if (!info.windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)) return null
    return info.windowPosture.hingeList.firstOrNull { !it.isVertical && (!it.isFlat || it.isSeparating) }
}

/** Whether the sidebar stands beside or below the content rather than opening over it. */
@Composable
fun currentOrcaSidebarDocked(): Boolean = currentOrcaWindowLayout().docksSidebar || currentOrcaTabletopFold() != null

/**
 * The width of the docked sidebar: wider on a large window (from 1200 dp), as
 * OrcaSlicer's sidebar is at least 39 em (Sidebar::Sidebar(), msw_rescale()),
 * so the names of the presets and of the plate type fit. A foldable held half
 * open like a book gives the sidebar the page left of the fold and the canvas
 * the other page, so the fold runs between them rather than through the canvas.
 */
@Composable
internal fun currentOrcaSidebarWidth(): Dp {
    val info = currentWindowAdaptiveInfoV2()
    val fold = info.windowPosture.hingeList.firstOrNull { it.isVertical && (!it.isFlat || it.isSeparating) }
    if (fold != null) {
        val page = with(LocalDensity.current) { fold.bounds.left.toDp() }
        if (page >= OrcaTheme.dimensions.sidebarWidth) return page
    }
    return if (info.windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_LARGE_LOWER_BOUND)) {
        OrcaTheme.dimensions.sidebarWidthLarge
    } else {
        OrcaTheme.dimensions.sidebarWidth
    }
}
