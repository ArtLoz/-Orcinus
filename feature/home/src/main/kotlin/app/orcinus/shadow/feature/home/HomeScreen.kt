package app.orcinus.shadow.feature.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.orcinus.shadow.core.designsystem.component.OrcaButton
import app.orcinus.shadow.core.designsystem.component.OrcaButtonStyle
import app.orcinus.shadow.core.designsystem.component.OrcaContextMenu
import app.orcinus.shadow.core.designsystem.component.OrcaMenuItem
import app.orcinus.shadow.core.designsystem.layout.OrcaSidebarToggleSpace
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import app.orcinus.shadow.core.model.ExternalDocumentReference
import app.orcinus.shadow.core.ui.orca.orcaString
import app.orcinus.shadow.core.ui.settings.SettingsNoticeDialog
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun HomeRoute(viewModel: HomeViewModel, onShowPrepare: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.showPrepare.collect { onShowPrepare() }
    }
    // The page asks for the list whenever it shows (get_recent_projects).
    LifecycleResumeEffect(viewModel) {
        viewModel.refresh()
        onPauseOrDispose {}
    }
    // GUI_App::load_project()'s file dialog.
    val openPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.openProject(ExternalDocumentReference(uri.toString()))
    }
    HomeScreen(
        state = state,
        onNewProject = viewModel::newProject,
        onOpenProject = { if (viewModel.mayOpenProject()) openPicker.launch(arrayOf("*/*")) },
        onOpenRecent = viewModel::openRecent,
        onRemove = viewModel::remove,
        onClearAll = viewModel::clearAll,
    )
    state.message?.let { SettingsNoticeDialog(it, onDismiss = viewModel::dismissMessage) }
}

/**
 * homepage/index.html: the "Recent" board, the only one the left board has
 * without Orca's cloud, with "New Project" and "Open Project" over the
 * recently opened files, which show their pictures, names and times. A long
 * press opens a file's context menu, as the right button does.
 */
@Composable
private fun HomeScreen(
    state: HomeUiState,
    onNewProject: () -> Unit,
    onOpenProject: () -> Unit,
    onOpenRecent: (RecentFile) -> Unit,
    onRemove: (RecentFile) -> Unit,
    onClearAll: () -> Unit,
) {
    val text = rememberHomeText()
    val colors = OrcaTheme.colors
    val missing = orcaString("File is missing")
    val remove = text("t88", "clear")
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.window),
    ) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = FILE_MIN_WIDTH),
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                // The sidebar's button stands in the corner, as over the other tabs.
                Row(
                    modifier = Modifier
                        .height(OrcaSidebarToggleSpace)
                        .padding(start = OrcaSidebarToggleSpace - 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text("t28", "recent"), color = colors.text, style = OrcaTheme.typography.head18, maxLines = 1)
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier.widthIn(max = MENU_MAX_WIDTH),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MenuItem(R.drawable.homepage_i4, text("t31", "new project"), text("t32", "create new project"), onNewProject, Modifier.weight(1f))
                    MenuItem(R.drawable.homepage_i5, text("t33", "open project"), "3mf", onOpenProject, Modifier.weight(1f))
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = text("t35", "recent open"),
                        color = colors.text,
                        style = OrcaTheme.typography.head16,
                        modifier = Modifier.weight(1f),
                    )
                    // UpdateRecentClearBtnDisplay(): only while there are files.
                    if (state.files.isNotEmpty()) {
                        OrcaButton(text("t12", "Clear all"), onClick = onClearAll, style = OrcaButtonStyle.Regular)
                    }
                }
            }
            items(state.files, key = { it.document.value }) { file ->
                RecentFileItem(
                    file = file,
                    time = if (file.missing) missing else file.modified?.let(::formatTime).orEmpty(),
                    removeText = remove,
                    onOpen = { onOpenRecent(file) },
                    onRemove = { onRemove(file) },
                )
            }
        }
        if (state.loading) {
            LinearProgressIndicator(
                color = colors.accent,
                trackColor = colors.accentSubtle,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
            )
        }
    }
}

/** A .MenuItem of #MenuArea: its picture, its name and what it does. */
@Composable
private fun MenuItem(@DrawableRes icon: Int, title: String, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = OrcaTheme.colors
    val shape = OrcaTheme.shapes.window
    Row(
        modifier = modifier
            .clip(shape)
            .border(1.dp, colors.border, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(painterResource(icon), contentDescription = null, modifier = Modifier.size(MENU_ICON_SIZE))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, color = colors.text, style = OrcaTheme.typography.head15, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(description, color = colors.textDimmed, style = OrcaTheme.typography.body13, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A .FileItem of #FileList: its picture, or the page's own (img/d.png), its name and its time. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RecentFileItem(file: RecentFile, time: String, removeText: String, onOpen: () -> Unit, onRemove: () -> Unit) {
    val colors = OrcaTheme.colors
    var menu by remember { mutableStateOf(false) }
    Box {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(OrcaTheme.shapes.compact)
                .combinedClickable(onClick = onOpen, onLongClick = { menu = true }, onLongClickLabel = removeText),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(THUMBNAIL_SHAPE)
                    .background(colors.buttonBackground),
            ) {
                val thumbnail = file.thumbnail
                if (thumbnail != null) {
                    Image(thumbnail, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Image(painterResource(R.drawable.homepage_d), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
            Text(
                text = file.name,
                color = colors.text,
                style = OrcaTheme.typography.body14,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
            Text(text = time, color = colors.textDimmed, style = OrcaTheme.typography.body12, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        // #recnet_context_menu: "Remove"; "Open Containing Folder" has no place on Android.
        OrcaContextMenu(expanded = menu, position = IntOffset.Zero, onDismissRequest = { menu = false }) {
            OrcaMenuItem(removeText, onClick = {
                menu = false
                onRemove()
            })
        }
    }
}

/** wxDateTime::FormatISOCombined(' ') of the local time. */
private fun formatTime(millis: Long): String = TIME_FORMAT.format(Instant.ofEpochMilli(millis))

private val TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())

/** .FileItem is 184 px wide on the desktop; a phone fits two in a row. */
private val FILE_MIN_WIDTH = 150.dp
private val MENU_MAX_WIDTH = 560.dp
private val MENU_ICON_SIZE = 40.dp
private val THUMBNAIL_SHAPE = RoundedCornerShape(8.dp)
