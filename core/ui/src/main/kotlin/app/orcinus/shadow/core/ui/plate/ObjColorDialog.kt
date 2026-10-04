package app.orcinus.shadow.core.ui.plate

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import app.orcinus.shadow.core.designsystem.component.OrcaButton
import app.orcinus.shadow.core.designsystem.component.OrcaButtonStyle
import app.orcinus.shadow.core.designsystem.component.OrcaComboField
import app.orcinus.shadow.core.designsystem.component.OrcaFilamentSlot
import app.orcinus.shadow.core.designsystem.component.OrcaLink
import app.orcinus.shadow.core.designsystem.component.OrcaMenuItem
import app.orcinus.shadow.core.designsystem.component.OrcaSpinInput
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import app.orcinus.shadow.core.model.ObjColorDialogState
import app.orcinus.shadow.core.model.ObjColorPanel
import app.orcinus.shadow.core.model.parseFilamentColor
import app.orcinus.shadow.core.ui.orca.orcaString
import app.orcinus.shadow.core.ui.settings.openInBrowser

/** What ObjColorDialog's controls do, through ObjColorPrompt. */
class ObjColorActions(
    val setClusterNumber: (Int) -> Unit,
    val select: (cluster: Int, index: Int) -> Unit,
    val append: () -> Unit,
    val colorMatch: () -> Unit,
    val reset: () -> Unit,
    /** OK (true) or Cancel (false). */
    val answer: (Boolean) -> Unit,
)

/**
 * ObjColorDialog ("OBJ file import color"): the number of colours the file's
 * are clustered into, between 1 and the recommended one; the plate's
 * filaments; for each colour the filament that prints it ("Matching"), one
 * to choose from the filaments and the colours "Append" added; and the quick
 * settings Append, Color match and Reset. OK waits until every colour has a
 * filament. A file whose MTL file lacks a material, or with faces without a
 * colour, shows only its error, with OK alone.
 *
 * The desktop dialog also shows a thumbnail of the object in the chosen
 * filaments, from a choice of views; that is not ported yet.
 */
@Composable
fun ObjColorDialog(dialog: ObjColorDialogState, actions: ObjColorActions) {
    val context = LocalContext.current
    val panel = dialog.panel
    AlertDialog(
        onDismissRequest = { actions.answer(false) },
        properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text(orcaString("OBJ file import color"), style = OrcaTheme.typography.head16) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (panel == null) {
                    ErrorPage(dialog)
                } else {
                    PanelPage(panel, enabled = !dialog.clustering, actions)
                }
            }
        },
        confirmButton = {
            OrcaButton(
                text = orcaString("OK"),
                onClick = { actions.answer(true) },
                enabled = panel == null || (panel.isOk && !dialog.clustering),
                style = OrcaButtonStyle.Confirm,
            )
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (panel != null) {
                    OrcaLink(orcaString("Wiki Guide"), onClick = { openInBrowser(context, WIKI_GUIDE) })
                    Box(Modifier.width(12.dp))
                    OrcaButton(text = orcaString("Cancel"), onClick = { actions.answer(false) }, style = OrcaButtonStyle.Regular)
                }
            }
        },
        containerColor = OrcaTheme.colors.window,
    )
}

@Composable
private fun ErrorPage(dialog: ObjColorDialogState) {
    val title = if (dialog.question.lostMaterialName.isNotEmpty()) {
        orcaString("MTL file exist error, could not find the material:") + " " + dialog.question.lostMaterialName + "."
    } else {
        orcaString("Some faces don't have color defined.")
    }
    Text(title, style = OrcaTheme.typography.head12)
    Text(orcaString("Please check OBJ or MTL file."), style = OrcaTheme.typography.head12)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PanelPage(panel: ObjColorPanel, enabled: Boolean, actions: ObjColorActions) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(orcaString("Specify number of colors:"), style = OrcaTheme.typography.head14, modifier = Modifier.weight(1f, fill = false))
        OrcaSpinInput(
            value = panel.clusterNumber,
            onValueChange = actions.setClusterNumber,
            range = 1..maxOf(1, panel.recommended),
            decreaseDescription = orcaString("Specify number of colors:"),
            increaseDescription = orcaString("Specify number of colors:"),
            enabled = enabled,
        )
    }
    Text("(" + panel.recommended + " " + orcaString("Recommended ") + ")", style = OrcaTheme.typography.body13, color = OrcaTheme.colors.textSide)
    Text(orcaString("Current filament colors"), style = OrcaTheme.typography.head14)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        panel.colours.forEachIndexed { index, colour -> OrcaFilamentSlot(number = index + 1, color = swatch(colour)) }
    }
    Text(orcaString("Matching"), style = OrcaTheme.typography.head14)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        panel.clusterColours.forEachIndexed { cluster, colour ->
            ClusterRow(panel, cluster, colour, enabled) { index -> actions.select(cluster, index) }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(orcaString("Quick set"), style = OrcaTheme.typography.head12)
        OrcaButton(text = orcaString("Append"), onClick = actions.append, enabled = enabled, style = OrcaButtonStyle.Regular)
        OrcaButton(text = orcaString("Color match"), onClick = actions.colorMatch, enabled = enabled, style = OrcaButtonStyle.Regular)
        OrcaButton(text = orcaString("Reset"), onClick = actions.reset, enabled = enabled, style = OrcaButtonStyle.Regular)
    }
    if (panel.note) {
        Text(
            orcaString("Note") + ": " + orcaString("The color has been selected, you can choose OK \n to continue or manually adjust it."),
            style = OrcaTheme.typography.body13,
            color = OrcaTheme.colors.textSide,
        )
    }
}

/** A colour of the file, an arrow, and the combo box of the filament that prints it. */
@Composable
private fun ClusterRow(panel: ObjColorPanel, cluster: Int, colour: String, enabled: Boolean, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selection = panel.selections.getOrElse(cluster) { 0 }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ColourSwatch(colour)
        Text("—> ", style = OrcaTheme.typography.head12)
        Box {
            OrcaComboField(
                text = "",
                enabled = enabled,
                onClick = { expanded = true },
                leading = { ItemSwatch(panel, selection) },
                modifier = Modifier.width(76.dp),
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, containerColor = OrcaTheme.colors.window) {
                (0..panel.items.size).forEach { index ->
                    OrcaMenuItem(
                        text = "",
                        onClick = {
                            expanded = false
                            onSelect(index)
                        },
                        leading = { ItemSwatch(panel, index) },
                    )
                }
            }
        }
    }
}

/** get_extruder_color_icon() of a combo box's item: the undefined colour is numbered -1. */
@Composable
private fun ItemSwatch(panel: ObjColorPanel, index: Int) {
    OrcaFilamentSlot(number = if (index == 0) -1 else index, color = swatch(panel.itemColor(index)))
}

/** A colour of the file, which the dialog draws without a number. */
@Composable
private fun ColourSwatch(colour: String) {
    Box(Modifier.size(22.dp).background(swatch(colour)))
}

private fun swatch(colour: String): Color =
    parseFilamentColor(colour)?.let { Color(it.red, it.green, it.blue, it.alpha) } ?: Color.Transparent

private const val WIKI_GUIDE = "https://www.orcaslicer.com/wiki/import_export#obj"
