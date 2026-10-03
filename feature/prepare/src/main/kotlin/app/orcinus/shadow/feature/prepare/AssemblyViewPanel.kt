package app.orcinus.shadow.feature.prepare

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.orcinus.shadow.core.designsystem.component.OrcaCanvasTool
import app.orcinus.shadow.core.designsystem.component.OrcaCanvasToolbar
import app.orcinus.shadow.core.designsystem.component.OrcaContextMenu
import app.orcinus.shadow.core.designsystem.component.OrcaFilamentSlot
import app.orcinus.shadow.core.designsystem.component.OrcaGizmoPanel
import app.orcinus.shadow.core.designsystem.component.OrcaMenuItem
import app.orcinus.shadow.core.designsystem.component.OrcaMenuSeparator
import app.orcinus.shadow.core.designsystem.component.OrcaSubmenu
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import app.orcinus.shadow.core.model.Vector3
import app.orcinus.shadow.core.model.extruderNumber
import app.orcinus.shadow.core.ui.orca.orcaString
import java.util.Locale
import kotlin.math.roundToInt
import app.orcinus.shadow.core.designsystem.R as DesignR

/** What the assembly view's controls do (AssembleView). */
internal class AssemblyViewActions(
    /** The toolbar's "Assembly View". */
    val open: () -> Unit,
    /** "Return", and the phone's Back. */
    val back: () -> Unit,
    val setExplosionRatio: (Double) -> Unit,
    /** The menu's "Hide" (false) and "Show" (true) of the selected copies. */
    val setVisible: (Boolean) -> Unit,
) {
    companion object {
        val NONE = AssemblyViewActions({}, {}, {}, {})
    }
}

/**
 * The assembly view's toolbars: "Return" to the 3D view
 * (GLCanvas3D::_render_return_toolbar()), then the gizmos
 * GLGizmosManager::get_selectable_idxs() offers it: Move, Rotate, Measure,
 * Assemble and Color Painting. Those the app does not have there yet stay
 * disabled.
 */
@Composable
internal fun AssemblyViewToolbar(actions: AssemblyViewActions) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        AssemblyReturnButton(actions.back)
        OrcaCanvasToolbar {
            OrcaCanvasTool(DesignR.drawable.orca_toolbar_move, stringResource(R.string.gizmo_move), onClick = {}, enabled = false)
            OrcaCanvasTool(DesignR.drawable.orca_toolbar_rotate, stringResource(R.string.gizmo_rotate), onClick = {}, enabled = false)
            OrcaCanvasTool(DesignR.drawable.orca_toolbar_measure, stringResource(R.string.gizmo_measure), onClick = {}, enabled = false)
            OrcaCanvasTool(DesignR.drawable.orca_toolbar_assembly, stringResource(R.string.gizmo_assembly), onClick = {}, enabled = false)
            OrcaCanvasTool(DesignR.drawable.orca_mmu_segmentation, stringResource(R.string.gizmo_color_painting), onClick = {}, enabled = false)
        }
    }
}

/** _render_return_toolbar(): "Return" after its arrow, a pill in the toolbar's colours. */
@Composable
private fun AssemblyReturnButton(onClick: () -> Unit) {
    val colors = OrcaTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .shadow(2.dp, CircleShape)
            .clip(CircleShape)
            .background(colors.canvasPanel)
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 14.dp),
    ) {
        Icon(
            painter = painterResource(DesignR.drawable.orca_assemble_return),
            contentDescription = null,
            tint = colors.onCanvasPanel,
            modifier = Modifier.size(width = 10.dp, height = 20.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(orcaString("Return"), color = colors.onCanvasPanel, style = OrcaTheme.typography.body14)
    }
}

/**
 * GLCanvas3D::_render_assemble_control() and _render_assemble_info() at the
 * bottom of the assembly view: "Explosion Ratio", which spreads the volumes as
 * the slider moves, and while something is selected, the volume and the size
 * of the selection's box ([selection]), in millimetres as they are.
 */
@Composable
internal fun AssemblyViewPanel(mode: AssemblyViewMode, selection: Vector3?, actions: AssemblyViewActions, modifier: Modifier = Modifier) {
    val colors = OrcaTheme.colors
    OrcaGizmoPanel(modifier.widthIn(max = ASSEMBLY_PANEL_WIDTH).fillMaxWidth()) {
        PaintingSlider(
            label = orcaString("Explosion Ratio"),
            value = mode.explosionRatio.toFloat(),
            range = 1f..3f,
            text = String.format(Locale.ROOT, "%.2f", mode.explosionRatio),
            onChange = { actions.setExplosionRatio(it.toDouble()) },
        )
        if (selection != null) {
            Text(
                text = orcaString("Assembly Info"),
                color = colors.onCanvasPanel,
                style = OrcaTheme.typography.head14,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )
            AssemblyInfoRow(orcaString("Volume:"), String.format(Locale.ROOT, "%.2f", selection.x * selection.y * selection.z))
            AssemblyInfoRow(orcaString("Size:"), String.format(Locale.ROOT, "%.2f x %.2f x %.2f", selection.x, selection.y, selection.z))
        }
    }
}

@Composable
private fun AssemblyInfoRow(caption: String, value: String) {
    val colors = OrcaTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(caption, color = colors.onCanvasPanel, style = OrcaTheme.typography.body12, modifier = Modifier.width(ASSEMBLY_CAPTION_WIDTH))
        Text(value, color = colors.onCanvasPanel, style = OrcaTheme.typography.body12)
    }
}

/**
 * MenuFactory::assemble_object_menu() for the copy at [index], held at
 * [position]: "Hide" or "Show" of the selection, "Delete", and its filament
 * (append_menu_item_change_extruder()).
 */
@Composable
internal fun AssemblyObjectMenu(
    state: PrepareUiState,
    index: Int,
    position: Offset,
    onDismiss: () -> Unit,
    onDelete: (Int) -> Unit,
    onSetFilament: (index: Int, filament: Int) -> Unit,
    actions: AssemblyViewActions,
) {
    val copy = state.sceneCopies.getOrNull(index)
    val mode = state.assemblyView
    OrcaContextMenu(
        expanded = copy != null && mode != null,
        position = IntOffset(position.x.roundToInt(), position.y.roundToInt()),
        onDismissRequest = onDismiss,
    ) {
        if (copy == null || mode == null) return@OrcaContextMenu
        // append_menu_item_set_visible(): "Hide" while a selected volume shows.
        val shown = state.selectedObjects.mapNotNull { state.sceneCopies.getOrNull(it)?.id }.any { it !in mode.hidden }
        OrcaMenuItem(
            text = orcaString(if (shown) "Hide" else "Show"),
            onClick = {
                onDismiss()
                actions.setVisible(!shown)
            },
        )
        // append_menu_item_delete(): Plater::can_delete().
        OrcaMenuItem(
            text = orcaString("Delete"),
            enabled = state.canEditPlate,
            onClick = {
                onDismiss()
                onDelete(index)
            },
        )
        OrcaMenuSeparator()
        ChangeExtruderItem(
            filaments = state.filamentNames.zip(state.filamentColors) { name, color -> name to Color(color.red, color.green, color.blue, color.alpha) },
            current = copy.plateObject.extruderNumber,
            enabled = state.canEditPlate,
            onPick = { filament ->
                onDismiss()
                onSetFilament(index, filament)
            },
        )
    }
}

/**
 * append_menu_item_change_extruder() for one selected object: "Change
 * filament" with "Default" and every filament of the plate, the object's own
 * marked "(current)" and disabled. Nothing while the plate has one filament.
 */
@Composable
private fun ChangeExtruderItem(filaments: List<Pair<String, Color>>, current: Int, enabled: Boolean, onPick: (Int) -> Unit) {
    if (filaments.size <= 1) return
    OrcaSubmenu(text = orcaString("Change filament"), enabled = enabled) {
        for (filament in 0..filaments.size) {
            val active = filament == current
            // The icon of "Default" is the first filament's.
            val (name, color) = filaments[(filament - 1).coerceAtLeast(0)]
            val text = if (filament == 0) orcaString("Default") else name
            OrcaMenuItem(
                text = if (active) text + " (" + orcaString("current") + ")" else text,
                enabled = enabled && !active,
                onClick = { onPick(filament) },
                leading = { OrcaFilamentSlot(number = filament.coerceAtLeast(1), color = color) },
            )
        }
    }
}

/** The bottom panel of the assembly view is a phone's width at most. */
private val ASSEMBLY_PANEL_WIDTH = 420.dp

/** _render_assemble_info()'s caption column: "Total Volume:" and three spaces. */
private val ASSEMBLY_CAPTION_WIDTH = 72.dp
