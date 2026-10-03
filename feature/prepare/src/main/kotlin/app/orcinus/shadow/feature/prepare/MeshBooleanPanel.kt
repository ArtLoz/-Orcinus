package app.orcinus.shadow.feature.prepare

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.orcinus.shadow.core.designsystem.R as DesignR
import app.orcinus.shadow.core.designsystem.component.OrcaButton
import app.orcinus.shadow.core.designsystem.component.OrcaButtonSize
import app.orcinus.shadow.core.designsystem.component.OrcaButtonStyle
import app.orcinus.shadow.core.designsystem.component.OrcaCheckBox
import app.orcinus.shadow.core.designsystem.component.OrcaIconButton
import app.orcinus.shadow.core.designsystem.component.OrcaSegmentedSwitch
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import app.orcinus.shadow.core.model.MeshBooleanOperation
import app.orcinus.shadow.core.model.PlateObject
import app.orcinus.shadow.core.model.volumeAt
import app.orcinus.shadow.core.ui.orca.orcaString

/** What the mesh boolean tool's window does (GLGizmoMeshBoolean). */
internal class MeshBooleanActions(
    /** The toolbar's "Mesh Boolean". */
    val toggle: () -> Unit,
    /** "Done". */
    val close: () -> Unit,
    /** A finger on a volume of the copy: its mesh file. */
    val pick: (String) -> Unit,
    val setOperation: (MeshBooleanOperation) -> Unit,
    /** The "Select" button of the source's row (SelectSource), or of the tool's. */
    val selectTool: (Boolean) -> Unit,
    /** The "×" beside a picked volume: the source's, or the tool's. */
    val clear: (tool: Boolean) -> Unit,
    /** "Delete input" of the operation shown. */
    val setDeleteInput: (Boolean) -> Unit,
    /** The button of the operation. */
    val apply: () -> Unit,
) {
    companion object {
        val NONE = MeshBooleanActions({}, {}, {}, {}, {}, {}, {}, {})
    }
}

/**
 * GLGizmoMeshBoolean::on_render_input_window(): "Union", "Difference" and
 * "Intersection"; the rows of the two volumes ("Part 1" and "Part 2", or
 * "Subtract from" and "Subtract with"), whose "Select" a finger then picks a
 * volume of the copy for, with the picked one's name and "×"; "Delete input"
 * but for the union; and the operation's button, while both are picked.
 */
@Composable
internal fun MeshBooleanPanel(mode: MeshBooleanMode, plateObject: PlateObject?, actions: MeshBooleanActions) {
    val operations = MeshBooleanOperation.entries
    val difference = mode.operation == MeshBooleanOperation.DIFFERENCE
    PaintingPanelFrame(orcaString("Mesh Boolean"), orcaString("Done"), actions.close) {
        OrcaSegmentedSwitch(
            options = listOf(orcaString("Union"), orcaString("Difference"), orcaString("Intersection")),
            selectedIndex = operations.indexOf(mode.operation),
            onSelect = { actions.setOperation(operations[it]) },
            modifier = Modifier.fillMaxWidth(),
        )
        VolumeRow(
            caption = orcaString(if (difference) "Subtract from" else "Part 1"),
            selecting = !mode.selectingTool,
            name = mode.source?.let { nameOf(plateObject, it) },
            onSelect = { actions.selectTool(false) },
            onClear = { actions.clear(false) },
        )
        VolumeRow(
            caption = orcaString(if (difference) "Subtract with" else "Part 2"),
            selecting = mode.selectingTool,
            name = mode.tool?.let { nameOf(plateObject, it) },
            onSelect = { actions.selectTool(true) },
            onClear = { actions.clear(true) },
        )
        if (mode.operation != MeshBooleanOperation.UNION) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OrcaCheckBox(checked = mode.deleteInput, onCheckedChange = actions.setDeleteInput)
                Text(orcaString("Delete input"), color = OrcaTheme.colors.onCanvasPanel, style = OrcaTheme.typography.body13)
            }
        }
        OrcaButton(
            text = orcaString(
                when (mode.operation) {
                    MeshBooleanOperation.UNION -> "Union"
                    MeshBooleanOperation.DIFFERENCE -> "Difference"
                    MeshBooleanOperation.INTERSECTION -> "Intersection"
                },
            ),
            size = OrcaButtonSize.Compact,
            onClick = actions.apply,
            enabled = mode.source != null && mode.tool != null,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** A row of the window: its caption, "Select" or "1 selected", and the picked volume's name with "×". */
@Composable
private fun VolumeRow(caption: String, selecting: Boolean, name: String?, onSelect: () -> Unit, onClear: () -> Unit) {
    val colors = OrcaTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
        Text(caption, color = colors.onCanvasPanel, style = OrcaTheme.typography.body13, modifier = Modifier.width(CAPTION_WIDTH))
        OrcaButton(
            text = if (name != null) "1 " + orcaString("selected") else orcaString("Select"),
            size = OrcaButtonSize.Compact,
            style = if (selecting) OrcaButtonStyle.Confirm else OrcaButtonStyle.Regular,
            onClick = onSelect,
        )
        if (name != null) {
            Text(
                text = name,
                color = colors.onCanvasPanel,
                style = OrcaTheme.typography.body13,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .weight(1f, fill = false),
            )
            OrcaIconButton(
                icon = DesignR.drawable.orca_im_text_search_close,
                contentDescription = orcaString("Delete"),
                onClick = onClear,
                tint = colors.onCanvasPanel,
            )
        }
    }
}

/** ModelVolume::name of the volume at [index]: the object's own mesh goes by its object's name without one. */
private fun nameOf(plateObject: PlateObject?, index: Int): String =
    plateObject?.volumeAt(index)?.name?.takeIf(String::isNotEmpty)
        ?: plateObject?.let { (it as? PlateObject.ImportedModel)?.file?.displayName ?: it.volume.name }.orEmpty()

private val CAPTION_WIDTH = 96.dp
