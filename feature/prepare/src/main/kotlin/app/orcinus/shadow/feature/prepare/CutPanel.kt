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
import app.orcinus.shadow.core.designsystem.component.OrcaComboBox
import app.orcinus.shadow.core.designsystem.component.OrcaIconButton
import app.orcinus.shadow.core.designsystem.component.textLocale
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import app.orcinus.shadow.core.model.CutConnectorShape
import app.orcinus.shadow.core.model.CutConnectorStyle
import app.orcinus.shadow.core.model.CutConnectorType
import app.orcinus.shadow.core.model.OrcaText
import app.orcinus.shadow.core.model.Transform3
import app.orcinus.shadow.core.ui.orca.orcaString
import app.orcinus.shadow.core.ui.orca.orcaText
import app.orcinus.shadow.render.scene.CutConnectorEvent
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
    /** "Perform cut", the connectors' volumes named after the translated "Connector". */
    val perform: (connectorName: String) -> Unit,
    val cancel: () -> Unit,
    val connectors: CutConnectorActions = CutConnectorActions.NONE,
) {
    companion object {
        val NONE = CutActions({}, { _, _ -> }, {}, {}, {}, { _, _ -> }, { _, _ -> }, { _, _ -> }, {}, {}, {})
    }
}

/** What the connectors' window of the cut gizmo does (render_connectors_input_window()). */
internal class CutConnectorActions(
    /** "Add connectors" or "Edit connectors". */
    val edit: () -> Unit,
    val confirm: () -> Unit,
    val cancel: () -> Unit,
    /** "Remove connectors" */
    val removeAll: () -> Unit,
    val flipPlane: () -> Unit,
    /** A finger on the section or on a connector in the 3D view. */
    val event: (CutConnectorEvent) -> Unit,
    /** Ctrl+A and Delete of the desktop, as buttons. */
    val selectAll: () -> Unit,
    val deleteSelected: () -> Unit,
    val setSettings: ((CutConnectorSettings) -> CutConnectorSettings) -> Unit,
    /** "Space" and "Bulge" of the snaps. */
    val setSnap: (space: Double, bulge: Double) -> Unit,
) {
    companion object {
        val NONE = CutConnectorActions({}, {}, {}, {}, {}, {}, {}, {}, {}, { _, _ -> })
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
    if (mode.editingConnectors) return CutConnectorsPanel(mode, actions.connectors)
    val colors = OrcaTheme.colors
    val hasConnectors = mode.connectors.isNotEmpty()
    val connectorName = orcaString("Connector")
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
        OrcaButton(
            text = orcaString(if (hasConnectors) "Edit connectors" else "Add connectors"),
            size = OrcaButtonSize.Compact,
            enabled = mode.canEditConnectors,
            onClick = actions.connectors.edit,
        )
        HorizontalDivider(color = colors.separator, modifier = Modifier.padding(vertical = 4.dp))
        Text(orcaString("After cut") + ": ", color = colors.onCanvasPanel, style = OrcaTheme.typography.body12)
        CutPartLine(
            label = orcaString("Upper part"),
            marker = UPPER_PART_COLOR,
            keep = mode.keepUpper,
            placeOnCut = mode.placeOnCutUpper,
            flip = mode.flipUpper,
            keepAsParts = mode.keepAsParts,
            hasConnectors = hasConnectors,
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
            hasConnectors = hasConnectors,
            onKeep = { actions.setKeep(false, it) },
            onPlaceOnCut = { actions.setPlaceOnCut(false, it) },
            onFlip = { actions.setFlip(false, it) },
        )
        CutCheck(orcaString("Cut to parts"), mode.keepAsParts, enabled = !hasConnectors, actions.setCutToParts)
        HorizontalDivider(color = colors.separator, modifier = Modifier.padding(vertical = 4.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // "Reset cutting plane and remove connectors"
            OrcaButton(
                text = orcaString("Reset"),
                size = OrcaButtonSize.Compact,
                enabled = !mode.planeAtStart || hasConnectors,
                onClick = {
                    actions.resetPlane()
                    if (hasConnectors) actions.connectors.removeAll()
                },
            )
            Spacer(Modifier.weight(1f))
            OrcaButton(
                text = orcaString("Perform cut"),
                style = OrcaButtonStyle.Confirm,
                size = OrcaButtonSize.Compact,
                enabled = mode.canPerform,
                onClick = { actions.perform(connectorName) },
            )
        }
        CutWarnings(mode)
    }
}

/** render_input_window_warning() */
@Composable
private fun CutWarnings(mode: CutMode) {
    val described = mode.described
    val warnings = buildList {
        if (mode.invalidConnectors.isNotEmpty() && described != null) {
            var text = orcaString("Warning") + ": " + orcaString("Invalid connectors detected") + ":"
            if (described.outsideCutContour > 0) {
                text += "\n - " + orcaText(
                    OrcaText(
                        msgid = "%1\$d connector is out of cut contour",
                        msgidPlural = "%1\$d connectors are out of cut contour",
                        count = described.outsideCutContour.toLong(),
                        args = listOf(described.outsideCutContour.toString()),
                    ),
                )
            }
            if (described.outsideBoundingBox > 0) {
                text += "\n - " + orcaText(
                    OrcaText(
                        msgid = "%1\$d connector is out of object",
                        msgidPlural = "%1\$d connectors are out of object",
                        count = described.outsideBoundingBox.toLong(),
                        args = listOf(described.outsideBoundingBox.toString()),
                    ),
                )
            }
            if (described.overlap) text += "\n - " + orcaString("Some connectors are overlapped")
            add(text)
        }
        if (!mode.keepUpper && !mode.keepLower) add(orcaString("Warning") + ": " + orcaString("Select at least one object to keep after cutting."))
        if (described?.validContour == false) add(orcaString("Warning") + ": " + orcaString("Cut plane is placed out of object"))
    }
    for (warning in warnings) {
        Text(
            text = warning,
            color = OrcaTheme.colors.warning,
            style = OrcaTheme.typography.body12,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/**
 * render_connectors_input_window(): "Connectors" with "Remove connectors" and
 * "Flip cut plane", the type, style and shape, the depth and the size with
 * their tolerances, the rotation, the snaps' bulge and space, then "Confirm
 * connectors" and "Cancel". A touch on the section places a connector; the
 * keyboard's Ctrl+A and Delete are buttons here.
 */
@Composable
private fun CutConnectorsPanel(mode: CutMode, actions: CutConnectorActions) {
    val colors = OrcaTheme.colors
    val settings = mode.connectorSettings
    // render_slider_two_input(): up to a ninth of the bounding box's sides together.
    val meanSize = mode.boundsMin?.let { min ->
        val max = mode.boundsMax ?: min
        ((max.x - min.x) + (max.y - min.y) + (max.z - min.z)) / 9.0
    } ?: 10.0
    PaintingPanelFrame(orcaString("Connectors"), orcaString("Confirm connectors"), actions.confirm) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OrcaIconButton(
                icon = DesignR.drawable.orca_toolbar_reset,
                contentDescription = orcaString("Remove connectors"),
                onClick = actions.removeAll,
                enabled = mode.connectors.isNotEmpty(),
                tint = colors.onCanvasPanel,
            )
            OrcaButton(
                text = orcaString("Flip cut plane"),
                size = OrcaButtonSize.Compact,
                enabled = mode.connectors.isNotEmpty(),
                onClick = actions.flipPlane,
            )
        }
        Text(orcaString("Type"), color = colors.onCanvasPanel, style = OrcaTheme.typography.body12)
        PaintingChoices(
            listOf(
                CutConnectorType.PLUG to orcaString("Plug"),
                CutConnectorType.DOWEL to orcaString("Dowel"),
                CutConnectorType.SNAP to orcaString("Snap"),
            ),
            selected = settings.type,
            onSelect = { type -> actions.setSettings { it.copy(type = type) } },
        )
        CutCombo(
            label = orcaString("Style"),
            items = listOf(CutConnectorStyle.PRISM to orcaString("Prism"), CutConnectorStyle.FRUSTUM to orcaString("Frustum")),
            selected = settings.style,
            enabled = settings.type == CutConnectorType.PLUG,
            onSelect = { style -> actions.setSettings { it.copy(style = style) } },
        )
        CutCombo(
            label = orcaString("Shape"),
            items = listOf(
                CutConnectorShape.TRIANGLE to orcaString("Triangle"),
                CutConnectorShape.SQUARE to orcaString("Square"),
                CutConnectorShape.HEXAGON to orcaString("Hexagon"),
                CutConnectorShape.CIRCLE to orcaString("Circle"),
            ),
            selected = settings.shape,
            enabled = settings.type != CutConnectorType.SNAP,
            onSelect = { shape -> actions.setSettings { it.copy(shape = shape) } },
        )
        val depthMin = if (settings.type == CutConnectorType.SNAP) (settings.size ?: 1.0) else 1.0
        CutSlider(orcaString("Depth"), settings.depth, depthMin, meanSize, orcaString("mm")) { value -> actions.setSettings { it.copy(depth = value) } }
        CutSlider(orcaString("Tolerance"), settings.depthTolerance, 0.0, 0.5 * meanSize, orcaString("mm")) { value ->
            actions.setSettings { it.copy(depthTolerance = value) }
        }
        CutSlider(orcaString("Size"), settings.size, 1.0, meanSize, orcaString("mm")) { value -> actions.setSettings { it.copy(size = value) } }
        CutSlider(orcaString("Tolerance"), settings.sizeTolerance, 0.0, 0.5 * meanSize, orcaString("mm")) { value ->
            actions.setSettings { it.copy(sizeTolerance = value) }
        }
        // render_angle_input(): 0 to 180 degrees, with its reset.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                CutSlider(orcaString("Rotation"), settings.angle?.let(Math::toDegrees), 0.0, 180.0, "°", decimals = 0) { value ->
                    actions.setSettings { it.copy(angle = Math.toRadians(value)) }
                }
            }
            OrcaIconButton(
                icon = DesignR.drawable.orca_toolbar_reset,
                contentDescription = orcaString("Reset"),
                onClick = { actions.setSettings { it.copy(angle = 0.0) } },
                enabled = settings.angle != 0.0,
                tint = colors.onCanvasPanel,
            )
        }
        if (settings.type == CutConnectorType.SNAP) {
            // render_snap_specific_input(): percentages of the radius.
            CutSlider(orcaString("Bulge"), mode.snapBulge * 100.0, 5.0, 100.0 * mode.snapSpace, "%", decimals = 0) { value ->
                actions.setSnap(mode.snapSpace, value / 100.0)
            }
            CutSlider(orcaString("Space"), mode.snapSpace * 100.0, 10.0, 50.0, "%", decimals = 0) { value ->
                actions.setSnap(value / 100.0, mode.snapBulge)
            }
        }
        HorizontalDivider(color = colors.separator, modifier = Modifier.padding(vertical = 4.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OrcaButton(
                text = orcaString("Select all connectors"),
                size = OrcaButtonSize.Compact,
                enabled = mode.connectors.isNotEmpty(),
                onClick = actions.selectAll,
            )
            OrcaButton(
                text = orcaString("Remove connector"),
                size = OrcaButtonSize.Compact,
                enabled = mode.selectedConnectors.isNotEmpty(),
                onClick = actions.deleteSelected,
            )
        }
        Row {
            Spacer(Modifier.weight(1f))
            OrcaButton(text = orcaString("Cancel"), size = OrcaButtonSize.Compact, onClick = actions.cancel)
        }
        CutWarnings(mode)
    }
}

/** render_combo() of the connectors' window; nothing shows while the selected connectors differ. */
@Composable
private fun <T> CutCombo(label: String, items: List<Pair<T, String>>, selected: T?, enabled: Boolean, onSelect: (T) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Text(label, color = OrcaTheme.colors.onCanvasPanel, style = OrcaTheme.typography.body12, modifier = Modifier.width(96.dp))
        OrcaComboBox(
            items = items.map { it.first as T? },
            selected = selected,
            label = { item -> items.firstOrNull { it.first == item }?.second ?: " " },
            onSelect = { item -> item?.let(onSelect) },
            enabled = enabled,
            modifier = Modifier.weight(1f),
        )
    }
}

/** A slider of the connectors' window with its value; an undefined value (UndefFloat) shows none. */
@Composable
private fun CutSlider(label: String, value: Double?, min: Double, max: Double, unit: String, decimals: Int = 2, onChange: (Double) -> Unit) {
    val top = maxOf(max, min + 0.01)
    PaintingSlider(
        label = label,
        value = (value ?: min).coerceIn(min, top).toFloat(),
        range = min.toFloat()..top.toFloat(),
        text = value?.let { String.format(textLocale(), "%.${decimals}f %s", it, unit) } ?: " ",
        onChange = { onChange(it.toDouble()) },
    )
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
    hasConnectors: Boolean,
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
        // With connectors both parts are kept.
        CutCheck(orcaString("Keep"), if (hasConnectors) true else keep, enabled = !hasConnectors && !keepAsParts, onKeep)
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
