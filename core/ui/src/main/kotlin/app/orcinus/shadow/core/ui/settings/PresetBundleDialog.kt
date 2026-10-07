package app.orcinus.shadow.core.ui.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.orcinus.shadow.core.designsystem.component.OrcaButton
import app.orcinus.shadow.core.designsystem.component.OrcaButtonSize
import app.orcinus.shadow.core.designsystem.component.OrcaButtonStyle
import app.orcinus.shadow.core.designsystem.component.OrcaContextMenu
import app.orcinus.shadow.core.designsystem.component.OrcaFullScreenDialog
import app.orcinus.shadow.core.designsystem.component.OrcaMenuItem
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import app.orcinus.shadow.core.model.DialogIcon
import app.orcinus.shadow.core.model.OrcaText
import app.orcinus.shadow.core.model.PresetBundleInfo
import app.orcinus.shadow.core.model.PresetBundleType
import app.orcinus.shadow.core.model.PresetBundlesOutcome
import app.orcinus.shadow.core.model.SettingsDialog
import app.orcinus.shadow.core.ui.orca.orcaString
import kotlinx.coroutines.launch

/**
 * OrcaSlicer's PresetBundleDialog, which its top menu opens as "Preset
 * Bundle": the bundles the user has — those an import brought (an
 * .orca_bundle or .orca_printer with its bundle_structure.json) and those
 * OrcaCloud keeps — with their name, type, version and update, and under them
 * the presets of the selected one, the first at the start. A long press on a
 * bundle opens the page's context menu (its right click), whose "Delete
 * bundle" asks first. The page's own words stay English, as the desktop page
 * does not translate them.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PresetBundleDialog(
    load: suspend () -> PresetBundlesOutcome,
    delete: suspend (String) -> PresetBundlesOutcome,
    onDismiss: () -> Unit,
) {
    val colors = OrcaTheme.colors
    val scope = rememberCoroutineScope()
    var outcome by remember { mutableStateOf<PresetBundlesOutcome?>(null) }
    LaunchedEffect(Unit) { outcome = load() }
    val bundles = (outcome as? PresetBundlesOutcome.Success)?.bundles.orEmpty()
    var selectedId by remember { mutableStateOf<String?>(null) }
    // autoSelectFirstBundle(): the bundle selected before while it is there, else the first.
    val selected = bundles.firstOrNull { it.id == selectedId } ?: bundles.firstOrNull()
    var menu by remember { mutableStateOf<PresetBundleInfo?>(null) }
    var deleting by remember { mutableStateOf<PresetBundleInfo?>(null) }
    var failed by remember { mutableStateOf(false) }

    // PresetBundleDialog::PresetBundleDialog(): SetSize(FromDIP(wxSize(820, 660))), its two
    // panes one over the other as there.
    OrcaFullScreenDialog(onDismissRequest = onDismiss, width = 860.dp, height = 700.dp) {
        Column(
            Modifier
                .fillMaxSize()
                .background(colors.window),
        ) {
            FullScreenDialogTopBar(
                title = orcaString("Preset Bundle"),
                okEnabled = false,
                onCancel = onDismiss,
                onOk = {},
                showOk = false,
            )
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)),
            ) {
                // The top pane: Name, Type, Version and Update.
                HeaderRow {
                    Cell(STATUS_WIDTH) {}
                    HeaderText("Name", Modifier.weight(1f))
                    HeaderText("Type", Modifier.width(TYPE_WIDTH))
                    HeaderText("Version", Modifier.width(VERSION_WIDTH))
                    HeaderText("Update", Modifier.width(UPDATE_WIDTH))
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (val current = outcome) {
                        null -> CircularProgressIndicator(color = colors.accent, modifier = Modifier.padding(16.dp))
                        is PresetBundlesOutcome.Failure ->
                            Text(current.message, color = colors.error, style = OrcaTheme.typography.body13, modifier = Modifier.padding(16.dp))
                        is PresetBundlesOutcome.Success -> LazyColumn(Modifier.fillMaxSize()) {
                            items(current.bundles, key = { it.id }) { bundle ->
                                BundleRow(
                                    bundle = bundle,
                                    selected = bundle.id == selected?.id,
                                    onSelect = { selectedId = bundle.id },
                                    onMenu = {
                                        selectedId = bundle.id
                                        // The menu of a subscribed bundle unsubscribes it, which needs OrcaCloud;
                                        // "Open folder in explorer" has no explorer to open on a phone.
                                        if (bundle.type != PresetBundleType.SUBSCRIBED) menu = bundle
                                    },
                                )
                                // Under the row it was opened on.
                                if (menu?.id == bundle.id) {
                                    OrcaContextMenu(expanded = true, position = IntOffset.Zero, onDismissRequest = { menu = null }) {
                                        OrcaMenuItem("Delete bundle", onClick = {
                                            menu = null
                                            deleting = bundle
                                        })
                                    }
                                }
                            }
                        }
                    }
                }
                HorizontalDivider(color = colors.separator, thickness = 1.dp)
                // The bottom pane: the selected bundle's printers, filaments and processes.
                HeaderRow {
                    HeaderText("Name", Modifier.weight(1f))
                    HeaderText("Type", Modifier.width(TYPE_WIDTH))
                }
                val rows = selected?.let { bundle ->
                    bundle.printers.map { it to "Printer" } + bundle.filaments.map { it to "Filament" } + bundle.processes.map { it to "Process" }
                }.orEmpty()
                LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                    items(rows.size) { index ->
                        val (name, type) = rows[index]
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                            BodyText(name, Modifier.weight(1f))
                            BodyText(type, Modifier.width(TYPE_WIDTH))
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.End) {
                    OrcaButton("Close", onClick = onDismiss, style = OrcaButtonStyle.Regular)
                }
            }
        }
    }

    deleting?.let { bundle ->
        // PresetBundleDialog::DeleteBundle(): wxYES_NO | wxNO_DEFAULT | wxICON_WARNING.
        SettingsQuestionDialog(
            SettingsDialog(
                id = "delete_bundle",
                icon = DialogIcon.WARNING,
                title = listOf(OrcaText("Delete Bundle")),
                text = listOf(OrcaText("Delete selected bundle from folder and all presets loaded from it?")),
                question = true,
                yes = null,
                no = null,
            ),
        ) { yes ->
            deleting = null
            if (yes) {
                scope.launch {
                    when (val result = delete(bundle.id)) {
                        is PresetBundlesOutcome.Success -> outcome = result
                        is PresetBundlesOutcome.Failure -> failed = true
                    }
                }
            }
        }
    }
    if (failed) {
        SettingsNoticeDialog(
            SettingsDialog(
                id = "remove_bundle",
                icon = DialogIcon.ERROR,
                title = listOf(OrcaText("Remove Bundle")),
                text = listOf(OrcaText("Failed to remove bundle.")),
                question = false,
                yes = null,
                no = null,
            ),
            onDismiss = { failed = false },
        )
    }
}

/**
 * renderTop()'s row: the status icon (an update, or a bundle OrcaCloud
 * refuses), the name, the type, the version and "Update", which only OrcaCloud
 * makes available. A tap selects the bundle, a long press opens its menu.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BundleRow(
    bundle: PresetBundleInfo,
    selected: Boolean,
    onSelect: () -> Unit,
    onMenu: () -> Unit,
) {
    val colors = OrcaTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) colors.accentSubtle else Color.Transparent)
            .combinedClickable(onClick = onSelect, onLongClick = onMenu)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cell(STATUS_WIDTH) {
            when {
                bundle.unauthorized -> StatusIcon("!", UNAUTHORIZED_TOOLTIP, colors.error)
                bundle.updateAvailable -> StatusIcon("↑", UPDATE_TOOLTIP, colors.accent)
            }
        }
        BodyText(bundle.name, Modifier.weight(1f))
        BodyText(bundle.type.label, Modifier.width(TYPE_WIDTH))
        BodyText(bundle.version, Modifier.width(VERSION_WIDTH))
        Box(Modifier.width(UPDATE_WIDTH)) {
            // sendUpdateBundleCommand() asks OrcaCloud, for which the app has no
            // account, so no bundle here ever has an update to take.
            OrcaButton(
                text = "Update",
                onClick = {},
                enabled = !bundle.unauthorized && bundle.updateAvailable,
                size = OrcaButtonSize.Compact,
                style = OrcaButtonStyle.Regular,
            )
        }
    }
}

@Composable
private fun HeaderRow(content: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(OrcaTheme.colors.tabBar)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
private fun Cell(width: Dp, content: @Composable () -> Unit) {
    Box(Modifier.width(width), contentAlignment = Alignment.CenterStart) { content() }
}

@Composable
private fun HeaderText(text: String, modifier: Modifier) {
    Text(
        text = text,
        color = OrcaTheme.colors.onTabBar,
        style = OrcaTheme.typography.body13.copy(fontWeight = FontWeight.Bold),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
private fun BodyText(text: String, modifier: Modifier) {
    Text(
        text = text,
        color = OrcaTheme.colors.text,
        style = OrcaTheme.typography.body13,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
private fun StatusIcon(mark: String, tooltip: String, color: Color) {
    Text(
        text = mark,
        color = color,
        style = OrcaTheme.typography.body13.copy(fontWeight = FontWeight.Bold),
        modifier = Modifier.semantics { contentDescription = tooltip },
    )
}

/** index.js's tooltips of the status icons. */
private const val UPDATE_TOOLTIP = "Update available"
private const val UNAUTHORIZED_TOOLTIP = "Unauthorized bundle"

private val STATUS_WIDTH = 16.dp
private val TYPE_WIDTH = 72.dp
private val VERSION_WIDTH = 64.dp
private val UPDATE_WIDTH = 76.dp
