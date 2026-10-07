package app.orcinus.shadow.core.ui.shortcuts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.orcinus.shadow.core.designsystem.component.OrcaButton
import app.orcinus.shadow.core.designsystem.component.OrcaButtonStyle
import app.orcinus.shadow.core.designsystem.component.OrcaChoiceChips
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import app.orcinus.shadow.core.model.OrcaKeyboardShortcuts
import app.orcinus.shadow.core.model.ShortcutGroup
import app.orcinus.shadow.core.ui.orca.LocalOrcaCatalog
import app.orcinus.shadow.core.ui.orca.orcaString

/**
 * KBShortcutsDialog: "Keyboard Shortcuts", a page of each group of
 * fill_shortcuts() — its buttons down the left of a large window, as on the
 * desktop, or across the top of a phone's — with the keys of each row in bold
 * and what they do beside them, both through OrcaSlicer's catalogue.
 */
@Composable
fun KeyboardShortcutsDialog(onDismiss: () -> Unit) {
    val colors = OrcaTheme.colors
    val catalog = LocalOrcaCatalog.current
    val groups = OrcaKeyboardShortcuts.groups
    var selected by rememberSaveable { mutableIntStateOf(0) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier
                .padding(DialogMargin)
                .widthIn(max = DialogWidth)
                .heightIn(max = DialogHeight)
                .fillMaxWidth(),
            shape = OrcaTheme.shapes.window,
            color = colors.window,
            contentColor = colors.text,
            shadowElevation = 8.dp,
        ) {
            Column(Modifier.padding(vertical = 12.dp)) {
                Text(
                    text = orcaString("Keyboard Shortcuts"),
                    color = colors.text,
                    style = OrcaTheme.typography.head16,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .semantics { heading() },
                )
                BoxWithConstraints(Modifier.weight(1f, fill = false)) {
                    val titles = groups.map { catalog.translate(it.title) }
                    if (maxWidth >= SideBySideWidth) {
                        Row(Modifier.padding(top = 8.dp)) {
                            // m_panel_selects: the pages' buttons, the selected one in ORCA's light teal.
                            Column(
                                Modifier
                                    .width(SelectsWidth)
                                    .fillMaxHeight()
                                    .background(colors.sideTabBar),
                            ) {
                                titles.forEachIndexed { index, title ->
                                    Text(
                                        text = title,
                                        color = colors.text,
                                        style = if (index == selected) OrcaTheme.typography.head13 else OrcaTheme.typography.body13,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(if (index == selected) colors.accentSelected else colors.sideTabBar)
                                            .selectable(selected = index == selected, role = Role.Tab) { selected = index }
                                            .padding(horizontal = 22.dp, vertical = 10.dp),
                                    )
                                }
                            }
                            ShortcutRows(groups[selected], Modifier.weight(1f))
                        }
                    } else {
                        Column(Modifier.padding(top = 8.dp)) {
                            OrcaChoiceChips(items = titles, selected = selected, onSelect = { selected = it })
                            ShortcutRows(groups[selected], Modifier.fillMaxWidth())
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp), contentAlignment = Alignment.CenterEnd) {
                    OrcaButton(text = orcaString("Close"), onClick = onDismiss, style = OrcaButtonStyle.Regular)
                }
            }
        }
    }
}

/** create_page(): the keys, then what they do, wrapped at about 600 pixels. */
@Composable
private fun ShortcutRows(group: ShortcutGroup, modifier: Modifier) {
    val colors = OrcaTheme.colors
    val catalog = LocalOrcaCatalog.current
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(group.rows) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                // _(shortcut): the keys as a whole, which a catalogue translates when it names them alone ("Del", "Esc").
                Text(
                    text = catalog.translate(OrcaKeyboardShortcuts.label(row.keys)),
                    color = colors.text,
                    style = OrcaTheme.typography.body13.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.width(KeysWidth),
                )
                Text(
                    text = catalog.translate(row.description),
                    color = colors.text,
                    style = OrcaTheme.typography.body13,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** The dialog's own size, about the desktop's 870 × 500 page with its buttons beside it. */
private val DialogWidth = 760.dp
private val DialogHeight = 600.dp
private val DialogMargin = 24.dp

/** Below this the pages' buttons stand above the page, as chips. */
private val SideBySideWidth = 560.dp
private val SelectsWidth = 150.dp
private val KeysWidth = 150.dp
