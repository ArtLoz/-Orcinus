package app.orcinus.shadow.core.designsystem.layout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.orcinus.shadow.core.designsystem.component.OrcaSidebarToggle
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import kotlin.math.roundToInt

private val SidebarTogglePadding = 8.dp

/**
 * The width the sidebar collapse button takes at the start of the canvas,
 * touch target included. OrcaSlicer starts the canvas toolbar after it
 * (GLCanvas3D::get_main_toolbar_offset).
 */
val OrcaSidebarToggleSpace = SidebarTogglePadding * 2 + 48.dp

/**
 * The main window with OrcaSlicer's sidebar.
 *
 * - Expanded: the tab bar spans the top; below it the sidebar is docked beside
 *   the content and collapses, as on desktop, wider on a large window.
 * - Medium: the tab bar spans the top; below it the sidebar opens over the
 *   content, which keeps the whole width, with a scrim over the rest. The
 *   collapse button stays above the scrim at the panel's edge and closes it,
 *   as do a tap on the scrim, Back, and a swipe towards the edge.
 * - Compact: the sidebar is a modal navigation drawer over the whole screen,
 *   tab bar included. It opens with the collapse button and closes with a
 *   swipe, a tap on the scrim, or Back.
 * - A foldable held half open like a laptop on a table, wider than a phone:
 *   the tab bar and the content above the fold, the sidebar docked below it,
 *   so the canvas stands up and the settings lie on the table.
 *
 * Neither drawer opens with a swipe: the drawer takes a sideways drag anywhere
 * on the content, so scrolling a page that leaves the drag to it (the
 * printer's web page) opened it, and the left edge belongs to the system's
 * Back gesture.
 *
 * OrcaSlicer's collapse button sits in the top-left corner of the content in
 * every layout.
 */
@Composable
fun OrcaSidebarLayout(
    layout: OrcaWindowLayout,
    sidebarVisible: Boolean,
    onSidebarVisibleChange: (Boolean) -> Unit,
    toggleDescription: String,
    topBar: @Composable () -> Unit,
    sidebar: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // The tab bar, the sidebar and the content move between the layouts below
    // rather than being composed anew, so a tablet that turns, or a window that
    // changes class, keeps the screens' view models, open tools and scrolling.
    val currentTopBar by rememberUpdatedState(topBar)
    val currentSidebar by rememberUpdatedState(sidebar)
    val currentContent by rememberUpdatedState(content)
    val movableTopBar = remember { movableContentOf { currentTopBar() } }
    val movableSidebar = remember { movableContentOf { currentSidebar() } }
    val movableContent = remember { movableContentOf { currentContent() } }
    val toggle: @Composable (Modifier) -> Unit = { toggleModifier ->
        OrcaSidebarToggle(
            contentDescription = toggleDescription,
            onClick = { onSidebarVisibleChange(!sidebarVisible) },
            modifier = toggleModifier
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start))
                .padding(SidebarTogglePadding),
        )
    }
    val contentWithToggle: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize()) {
            movableContent()
            toggle(Modifier.align(Alignment.TopStart))
        }
    }
    val tabletopFold = currentOrcaTabletopFold()
    when {
        tabletopFold != null -> Column(modifier.fillMaxSize()) {
            movableTopBar()
            var top by remember { mutableFloatStateOf(0f) }
            BoxWithConstraints(
                Modifier
                    .weight(1f)
                    .onPlaced { top = it.positionInWindow().y },
            ) {
                val density = LocalDensity.current
                val aboveFold = with(density) { (tabletopFold.bounds.top - top).coerceAtLeast(0f).toDp() }
                val belowFold = (maxHeight - aboveFold).coerceAtLeast(0.dp)
                val foldHeight = with(density) { tabletopFold.bounds.height.toDp() }
                Column(Modifier.fillMaxSize()) {
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    ) { contentWithToggle() }
                    AnimatedVisibility(
                        visible = sidebarVisible,
                        enter = expandVertically(),
                        exit = shrinkVertically(),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(belowFold)
                                .background(OrcaTheme.colors.window)
                                .padding(top = foldHeight)
                                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)),
                        ) { movableSidebar() }
                    }
                }
            }
        }

        layout == OrcaWindowLayout.Expanded -> Column(modifier.fillMaxSize()) {
            movableTopBar()
            Row(Modifier.weight(1f)) {
                AnimatedVisibility(
                    visible = sidebarVisible,
                    enter = expandHorizontally(expandFrom = Alignment.Start),
                    exit = shrinkHorizontally(shrinkTowards = Alignment.Start),
                ) {
                    Box(
                        Modifier
                            .width(currentOrcaSidebarWidth())
                            .fillMaxHeight()
                            .background(OrcaTheme.colors.window)
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Start)),
                    ) { movableSidebar() }
                }
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) { contentWithToggle() }
            }
        }

        layout == OrcaWindowLayout.Medium -> Column(modifier.fillMaxSize()) {
            movableTopBar()
            Box(Modifier.weight(1f)) {
                val drawerState = rememberSidebarDrawerState(sidebarVisible, onSidebarVisibleChange)
                var sheetWidth by remember { mutableIntStateOf(0) }
                ModalNavigationDrawer(
                    drawerContent = {
                        ModalDrawerSheet(
                            drawerState = drawerState,
                            modifier = Modifier.onSizeChanged { sheetWidth = it.width },
                            drawerContainerColor = OrcaTheme.colors.window,
                            drawerContentColor = OrcaTheme.colors.text,
                            // Under the tab bar, which keeps clear of the status bar.
                            windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Start),
                        ) { movableSidebar() }
                    },
                    drawerState = drawerState,
                    gesturesEnabled = drawerState.isOpen,
                ) {
                    Box(Modifier.fillMaxSize()) { movableContent() }
                }
                // Over the scrim, at the edge of the panel, which it follows as it opens and closes.
                toggle(
                    Modifier
                        .align(Alignment.TopStart)
                        .offset {
                            val offset = drawerState.currentOffset
                            val shown = when {
                                !offset.isNaN() -> sheetWidth + offset.roundToInt()
                                drawerState.isOpen -> sheetWidth
                                else -> 0
                            }
                            IntOffset(shown.coerceIn(0, sheetWidth), 0)
                        },
                )
            }
        }

        else -> {
            val drawerState = rememberSidebarDrawerState(sidebarVisible, onSidebarVisibleChange)
            ModalNavigationDrawer(
                drawerContent = {
                    ModalDrawerSheet(
                        drawerState = drawerState,
                        drawerContainerColor = OrcaTheme.colors.window,
                        drawerContentColor = OrcaTheme.colors.text,
                    ) { movableSidebar() }
                },
                modifier = modifier,
                drawerState = drawerState,
                gesturesEnabled = drawerState.isOpen,
            ) {
                Column(Modifier.fillMaxSize()) {
                    movableTopBar()
                    Box(Modifier.weight(1f)) { contentWithToggle() }
                }
            }
        }
    }
}

/** The state of a sidebar drawer: the caller's flag drives it; swipes, the scrim, and Back report back. */
@Composable
private fun rememberSidebarDrawerState(visible: Boolean, onVisibleChange: (Boolean) -> Unit): DrawerState {
    val drawerState = rememberDrawerState(if (visible) DrawerValue.Open else DrawerValue.Closed)
    val currentVisible by rememberUpdatedState(visible)
    val currentOnVisibleChange by rememberUpdatedState(onVisibleChange)
    LaunchedEffect(visible) {
        if (visible) drawerState.open() else drawerState.close()
    }
    LaunchedEffect(drawerState) {
        snapshotFlow { drawerState.currentValue == DrawerValue.Open }.collect { open ->
            if (open != currentVisible) currentOnVisibleChange(open)
        }
    }
    return drawerState
}
