package app.orcinus.shadow.feature.prepare

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.orcinus.shadow.core.designsystem.R as DesignR
import app.orcinus.shadow.core.designsystem.component.OrcaButton
import app.orcinus.shadow.core.designsystem.component.OrcaButtonSize
import app.orcinus.shadow.core.designsystem.component.OrcaButtonStyle
import app.orcinus.shadow.core.designsystem.component.OrcaCheckBox
import app.orcinus.shadow.core.designsystem.component.OrcaIconButton
import app.orcinus.shadow.core.designsystem.component.textLocale
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import app.orcinus.shadow.core.model.Transform3
import app.orcinus.shadow.core.ui.orca.orcaString
import app.orcinus.shadow.render.scene.CutPlanes

/** What the cut gizmo does while it is open (GLGizmoCut3D). */
internal class CutActions(
    /** The toolbar's Cut: the gizmo opens on the selected copy, or closes. */
    val toggle: () -> Unit,
    /** A grabber of the 3D view moved or turned the plane; finished once the finger let go. */
    val setPlane: (Transform3, Boolean) -> Unit,
    /** A tap on the plane outside the section turns it over. */
    val flip: () -> Unit,
    /** "Cut position": the height of the plane's centre. */
    val setPosition: (Double) -> Unit,
    /** "Reset cutting plane", and "Reset". */
    val resetPlane: () -> Unit,
    val setKeep: (upper: Boolean, keep: Boolean) -> Unit,
    val setPlaceOnCut: (upper: Boolean, place: Boolean) -> Unit,
    val setFlip: (upper: Boolean, flip: Boolean) -> Unit,
    val setCutToParts: (Boolean) -> Unit,
    val perform: () -> Unit,
    val cancel: () -> Unit,
) {
    companion object {
        val NONE = CutActions({}, { _, _ -> }, {}, {}, {}, { _, _ -> }, { _, _ -> }, { _, _ -> }, {}, {}, {})
    }
}

/**
 * GLGizmoCut3D::render_cut_plane_input_window() for the planar cut: the build
 * volume the plane cuts, the cut position with the plane's reset, "After
 * cut" for the upper and the lower part with their colours on the model, "Cut
 * to parts", then "Reset" and "Perform cut", and the warnings of
 * render_input_window_warning(). "Cancel" closes the gizmo from its title.
 */
@Composable
internal fun CutPanel(mode: CutMode, actions: CutActions) {
    val colors = OrcaTheme.colors
    PaintingPanelFrame(orcaString("Cut"), orcaString("Cancel"), actions.cancel) {
        // render_build_size()
        val size = mode.buildVolume
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
            Text(orcaString("Build Volume"), color = colors.onCanvasPanel, style = OrcaTheme.typography.body12, modifier = Modifier.weight(1f))
            Text(
                text = size?.let { String.format(textLocale(), "%.2f x %.2f x %.2f %s", it.x, it.y, it.z, orcaString("mm")) }.orEmpty(),
                color = colors.onCanvasPanel,
                style = OrcaTheme.typography.body12,
            )
        }
        // "Cut position" with the Z of the plane's centre and "Reset cutting plane".
        val plane = mode.plane
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(orcaString("Cut position"), color = colors.onCanvasPanel, style = OrcaTheme.typography.body12, modifier = Modifier.weight(1f))
            Text("Z:", color = colors.onCanvasPanel, style = OrcaTheme.typography.body12, modifier = Modifier.padding(end = 4.dp))
            if (plane != null) {
                PositionField(
                    value = CutPlanes.center(plane).z,
                    onValue = actions.setPosition,
                    modifier = Modifier.width(PositionFieldWidth),
                )
            }
            OrcaIconButton(
                icon = DesignR.drawable.orca_toolbar_reset,
                contentDescription = orcaString("Reset cutting plane"),
                onClick = actions.resetPlane,
                enabled = !mode.planeAtStart,
                tint = colors.onCanvasPanel,
            )
        }
        HorizontalDivider(color = colors.separator, modifier = Modifier.padding(vertical = 4.dp))
        Text(orcaString("After cut") + ": ", color = colors.onCanvasPanel, style = OrcaTheme.typography.body12)
        CutPartLine(
            label = orcaString("Upper part"),
            marker = UPPER_PART_COLOR,
            keep = mode.keepUpper,
            placeOnCut = mode.placeOnCutUpper,
            flip = mode.flipUpper,
            keepAsParts = mode.keepAsParts,
            onKeep = { actions.setKeep(true, it) },
            onPlaceOnCut = { actions.setPlaceOnCut(true, it) },
            onFlip = { actions.setFlip(true, it) },
        )
        CutPartLine(
            label = orcaString("Lower part"),
            marker = LOWER_PART_COLOR,
            keep = mode.keepLower,
            placeOnCut = mode.placeOnCutLower,
            flip = mode.flipLower,
            keepAsParts = mode.keepAsParts,
            onKeep = { actions.setKeep(false, it) },
            onPlaceOnCut = { actions.setPlaceOnCut(false, it) },
            onFlip = { actions.setFlip(false, it) },
        )
        PaintingCheck(orcaString("Cut to parts"), mode.keepAsParts, actions.setCutToParts)
        HorizontalDivider(color = colors.separator, modifier = Modifier.padding(vertical = 4.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // "Reset cutting plane and remove connectors"
            OrcaButton(
                text = orcaString("Reset"),
                size = OrcaButtonSize.Compact,
                enabled = !mode.planeAtStart,
                onClick = actions.resetPlane,
            )
            Spacer(Modifier.weight(1f))
            OrcaButton(
                text = orcaString("Perform cut"),
                style = OrcaButtonStyle.Confirm,
                size = OrcaButtonSize.Compact,
                enabled = mode.canPerform,
                onClick = actions.perform,
            )
        }
        // render_input_window_warning()
        val warnings = buildList {
            if (!mode.keepUpper && !mode.keepLower) add(orcaString("Select at least one object to keep after cutting."))
            if (mode.described?.validContour == false) add(orcaString("Cut plane is placed out of object"))
        }
        for (warning in warnings) {
            Text(
                text = orcaString("Warning") + ": " + warning,
                color = colors.warning,
                style = OrcaTheme.typography.body12,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/**
 * render_part_action_line(): the part's colour and name, then "Keep", which
 * stays on while the halves become parts; "Place on cut" and "Flip", which
 * rule each other out, only for a part that is kept.
 */
@Composable
private fun CutPartLine(
    label: String,
    marker: Color,
    keep: Boolean,
    placeOnCut: Boolean,
    flip: Boolean,
    keepAsParts: Boolean,
    onKeep: (Boolean) -> Unit,
    onPlaceOnCut: (Boolean) -> Unit,
    onFlip: (Boolean) -> Unit,
) {
    // The desktop line holds the three check boxes after the name; a phone
    // keeps "Keep" beside the name and the two ways to lay the part below it.
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp).fillMaxWidth()) {
        Box(
            Modifier
                .size(12.dp)
                .background(marker),
        )
        Text(
            text = label,
            color = OrcaTheme.colors.onCanvasPanel,
            style = OrcaTheme.typography.body12,
            modifier = Modifier
                .padding(start = 6.dp)
                .weight(1f),
        )
        CutCheck(orcaString("Keep"), keep, enabled = !keepAsParts, onKeep)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(start = 18.dp)) {
        CutCheck(orcaString("Place on cut"), placeOnCut, enabled = keep && !keepAsParts, onPlaceOnCut)
        CutCheck(orcaString("Flip"), flip, enabled = keep && !keepAsParts, onFlip)
    }
}

/** A check box of the cut window with its label, the whole item its touch target. */
@Composable
private fun CutCheck(text: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(vertical = 2.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onChange),
    ) {
        OrcaCheckBox(checked = checked, onCheckedChange = null, enabled = enabled)
        Text(
            text = text,
            color = if (enabled) OrcaTheme.colors.onCanvasPanel else OrcaTheme.colors.textDimmed,
            style = OrcaTheme.typography.body12,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

// UPPER_PART_COLOR and LOWER_PART_COLOR of GLGizmoCut.cpp: ColorRGBA::CYAN() and MAGENTA().
private val UPPER_PART_COLOR = Color(0f, 1f, 1f)
private val LOWER_PART_COLOR = Color(1f, 0f, 1f)
