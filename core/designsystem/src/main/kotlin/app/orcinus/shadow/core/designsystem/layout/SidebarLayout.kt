package app.orcinus.shadow.core.designsystem.layout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.orcinus.shadow.core.designsystem.component.OrcaSidebarToggle
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme

/**
 * The main window with OrcaSlicer's sidebar.
 *
 * - Wide: the tab bar spans the top; below it the sidebar is docked beside the
 *   content and collapses, as on desktop.
 * - Compact: the sidebar is a modal navigation drawer over the whole screen,
 *   tab bar included. It opens with the collapse button or a swipe from the
 *   left and closes with a swipe, a tap on the scrim, or Back.
 *
 * OrcaSlicer's collapse button sits in the top-left corner of the content in
 * both layouts.
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
    val contentWithToggle: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize()) {
            content()
            OrcaSidebarToggle(
                contentDescription = toggleDescription,
                onClick = { onSidebarVisibleChange(!sidebarVisible) },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Start))
                    .padding(8.dp),
            )
        }
    }
    when (layout) {
        OrcaWindowLayout.Wide -> Column(modifier.fillMaxSize()) {
            topBar()
            Row(Modifier.weight(1f)) {
                AnimatedVisibility(
                    visible = sidebarVisible,
                    enter = expandHorizontally(expandFrom = Alignment.Start),
                    exit = shrinkHorizontally(shrinkTowards = Alignment.Start),
                ) {
                    Box(
                        Modifier
                            .width(OrcaTheme.dimensions.sidebarWidth)
                            .fillMaxHeight()
                            .background(OrcaTheme.colors.window)
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Start)),
                    ) { sidebar() }
                }
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) { contentWithToggle() }
            }
        }

        OrcaWindowLayout.Compact -> {
            val drawerState = rememberDrawerState(if (sidebarVisible) DrawerValue.Open else DrawerValue.Closed)
            val visible by rememberUpdatedState(sidebarVisible)
            val onVisibleChange by rememberUpdatedState(onSidebarVisibleChange)
            // The caller's flag drives the drawer; swipes, the scrim, and Back report back.
            LaunchedEffect(sidebarVisible) {
                if (sidebarVisible) drawerState.open() else drawerState.close()
            }
            LaunchedEffect(drawerState) {
                snapshotFlow { drawerState.currentValue == DrawerValue.Open }.collect { open ->
                    if (open != visible) onVisibleChange(open)
                }
            }
            ModalNavigationDrawer(
                drawerContent = {
                    ModalDrawerSheet(
                        drawerState = drawerState,
                        drawerContainerColor = OrcaTheme.colors.window,
                        drawerContentColor = OrcaTheme.colors.text,
                    ) { sidebar() }
                },
                modifier = modifier,
                drawerState = drawerState,
            ) {
                Column(Modifier.fillMaxSize()) {
                    topBar()
                    Box(Modifier.weight(1f)) { contentWithToggle() }
                }
            }
        }
    }
}
