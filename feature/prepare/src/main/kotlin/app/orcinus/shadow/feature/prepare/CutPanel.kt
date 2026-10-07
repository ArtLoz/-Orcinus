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
import app.orcinus.shadow.core.model.CutGroove
import app.orcinus.shadow.core.model.OrcaText
import app.orcinus.shadow.core.model.Transform3
import app.orcinus.shadow.core.model.Vector3
import app.orcinus.shadow.core.ui.orca.orcaString
import app.orcinus.shadow.core.ui.orca.orcaText
import app.orcinus.shadow.render.scene.CutConnectorEvent
import app.orcinus.shadow.render.scene.CutLineEvent
import app.orcinus.shadow.render.scene.CutPlanes
import kotlin.math.abs

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
    /** "Reset cutting plane". */
    val resetPlane: () -> Unit,
    val setKeep: (upper: Boolean, keep: Boolean) -> Unit,
    val setPlaceOnCut: (upper: Boolean, place: Boolean) -> Unit,
    val setFlip: (upper: Boolean, flip: Boolean) -> Unit,
    val setCutToParts: (Boolean) -> Unit,
    /** "Perform cut", the connectors' volumes named after the translated "Connector". */
    val perform: (connectorName: String) -> Unit,
    val cancel: () -> Unit,
    val connectors: CutConnectorActions = CutConnectorActions.NONE,
    /** "Mode" */
    val setKind: (CutKind) -> Unit = {},
    /** The groove's inputs; finished once a slider is let go. */
    val setGroove: (finished: Boolean, change: (CutGroove) -> CutGroove) -> Unit = { _, _ -> },
    /** The resets of the groove's inputs, from the current and the initial grooves. */
    val resetGroove: ((CutGroove, CutGroove) -> CutGroove) -> Unit = {},
    /** The 3D view's millimetres per desktop pixel, which the grooves take their first size from. */
    val pixelSize: (Double) -> Unit = {},
    /** A long press on the object: the piece under the finger goes to the other part. */
    val selectPart: (origin: Vector3, direction: Vector3) -> Unit = { _, _ -> },
    /** "Draw cut line" on or off, and the line a finger draws while it is on. */
    val drawLine: (Boolean) -> Unit = {},
    val line: (CutLineEvent) -> Unit = {},
    /** "Reset" of the planar cut: the plane reset and the connectors removed, in one step of Undo. */
    val reset: () -> Unit = {},
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
    /** A slider let go. */
    val settingsDone: () -> Unit = {},
    /** "Rotation", in radians, which takes the snapshot "Edited: Rotation" as its slider begins to change, and its reset. */
    val setAngle: (Double) -> Unit = {},
    val resetAngle: () -> Unit = {},
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
internal fun CutPanel(mode: CutMode, actions: CutActions, imperial: Boolean, plateSize: Double = 350.0) {
    if (mode.editingConnectors) return CutConnectorsPanel(mode, actions.connectors)
    val colors = OrcaTheme.colors
    val hasConnectors = mode.connectors.isNotEmpty()
    val dovetail = mode.kind == CutKind.DOVETAIL
    val connectorName = orcaString("Connector")
    PaintingPanelFrame(orcaString("Cut"), orcaString("Cancel"), actions.cancel) {
        // render_cut_mode_combo(), which connectors keep on the planar cut.
        CutCombo(
            label = orcaString("Mode"),
            items = listOf(CutKind.PLANAR to orcaString("Planar"), CutKind.DOVETAIL to orcaString("Dovetail")),
            selected = mode.kind,
            enabled = !hasConnectors,
            onSelect = actions.setKind,
        )
        // render_build_size(), in inches with imperial units; its sliders stay in millimetres, as they do in OrcaSlicer.
        val koef = displayKoef(imperial)
        val size = mode.buildVolume?.let { Vector3(it.x * koef, it.y * koef, it.z * koef) }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
            Text(orcaString("Build Volume"), color = colors.onCanvasPanel, style = OrcaTheme.typography.body12, modifier = Modifier.weight(1f))
            Text(
                text = size?.let { String.format(textLocale(), "%.2f x %.2f x %.2f %s", it.x, it.y, it.z, orcaString(if (imperial) "in" else "mm")) }.orEmpty(),
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
                // render_move_center_input()
                PositionField(
                    value = CutPlanes.center(plane).z * displayKoef(imperial),
                    onValue = { actions.setPosition(it * inputKoef(imperial)) },
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
        // The desktop draws the cut line with Shift and a drag (m_shortcuts_cut);
        // here the button makes the next drag of a finger draw it.
        OrcaButton(
            text = orcaString("Draw cut line"),
            style = if (mode.drawingLine) OrcaButtonStyle.Confirm else OrcaButtonStyle.Regular,
            size = OrcaButtonSize.Compact,
            onClick = { actions.drawLine(!mode.drawingLine) },
        )
        HorizontalDivider(color = colors.separator, modifier = Modifier.padding(vertical = 4.dp))
        if (dovetail) {
            CutGrooveInputs(mode, actions, plateSize)
        } else {
            OrcaButton(
                text = orcaString(if (hasConnectors) "Edit connectors" else "Add connectors"),
                size = OrcaButtonSize.Compact,
                enabled = mode.canEditConnectors,
                onClick = actions.connectors.edit,
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
        CutCheck(orcaString("Cut to parts"), mode.keepAsParts, enabled = !hasConnectors && !dovetail && mode.parts == null, actions.setCutToParts)
        HorizontalDivider(color = colors.separator, modifier = Modifier.padding(vertical = 4.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // "Reset cutting plane and remove connectors", of the planar cut.
            if (!dovetail) {
                OrcaButton(
                    text = orcaString("Reset"),
                    size = OrcaButtonSize.Compact,
                    enabled = !mode.planeAtStart || hasConnectors,
                    onClick = actions.reset,
                )
            }
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
        if (described?.validContour == false) {
            add(orcaString("Warning") + ": " + orcaString("Cut plane is placed out of object"))
        } else if (mode.kind == CutKind.DOVETAIL && mode.describedPlane == mode.plane && mode.describedGroove == mode.groove && described?.validGroove == false) {
            add(orcaString("Warning") + ": " + orcaString("Cut plane with groove is invalid"))
        }
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
        // render_slider_two_input() of the depth and the size takes no snapshot.
        CutSlider(orcaString("Depth"), settings.depth, depthMin, meanSize, orcaString("mm")) { value -> actions.setSettings { it.copy(depth = value) } }
        CutSlider(orcaString("Tolerance"), settings.depthTolerance, 0.0, 0.5 * meanSize, orcaString("mm")) { value ->
            actions.setSettings { it.copy(depthTolerance = value) }
        }
        CutSlider(orcaString("Size"), settings.size, 1.0, meanSize, orcaString("mm")) { value -> actions.setSettings { it.copy(size = value) } }
        CutSlider(orcaString("Tolerance"), settings.sizeTolerance, 0.0, 0.5 * meanSize, orcaString("mm")) { value ->
            actions.setSettings { it.copy(sizeTolerance = value) }
        }
        // render_angle_input(): 0 to 180 degrees, with its reset.
        CutResettable(enabled = settings.angle != 0.0, onReset = actions.resetAngle) {
            CutSlider(orcaString("Rotation"), settings.angle?.let(Math::toDegrees), 0.0, 180.0, "°", decimals = 0, onFinished = actions.settingsDone) { value ->
                actions.setAngle(Math.toRadians(value))
            }
        }
        if (settings.type == CutConnectorType.SNAP) {
            // render_snap_specific_input(): percentages of the radius, each with its reset to the gizmo's first proportion.
            CutResettable(enabled = abs(mode.snapBulge - CutMode.SNAP_BULGE) > SNAP_EPSILON, onReset = { actions.setSnap(mode.snapSpace, CutMode.SNAP_BULGE) }) {
                CutSlider(orcaString("Bulge"), mode.snapBulge * 100.0, 5.0, 100.0 * mode.snapSpace, "%", decimals = 0) { value ->
                    actions.setSnap(mode.snapSpace, value / 100.0)
                }
            }
            CutResettable(enabled = abs(mode.snapSpace - CutMode.SNAP_SPACE) > SNAP_EPSILON, onReset = { actions.setSnap(CutMode.SNAP_SPACE, mode.snapBulge) }) {
                CutSlider(orcaString("Space"), mode.snapSpace * 100.0, 10.0, 50.0, "%", decimals = 0) { value ->
                    actions.setSnap(value / 100.0, mode.snapBulge)
                }
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

/**
 * The dovetail cut's inputs of render_cut_plane_input_window(): "Groove" with
 * its depth and width and their tolerances, the flap and groove angles, then
 * "Multiple" with the count, the gap between the grooves and their spacing,
 * each with its reset.
 */
@Composable
private fun CutGrooveInputs(mode: CutMode, actions: CutActions, plateSize: Double) {
    val colors = OrcaTheme.colors
    val groove = mode.groove
    val init = mode.grooveInit
    val meanSize = mode.boundsMin?.let { min ->
        val max = mode.boundsMax ?: min
        ((max.x - min.x) + (max.y - min.y) + (max.z - min.z)) / 9.0
    } ?: 10.0
    val mm = orcaString("mm")
    Text(orcaString("Groove") + ": ", color = ORANGE_LIGHT, style = OrcaTheme.typography.body12)
    // render_groove_two_float_input(): the tolerance up to 30 % of the value, 1.5 mm at most.
    CutResettable(enabled = !(groove.depth == init.depth && groove.depthTolerance == 0.1), onReset = {
        actions.resetGroove { current, first -> current.copy(depth = first.depth, depthTolerance = 0.1) }
    }) {
        CutSlider(orcaString("Depth"), groove.depth, 1.0, meanSize, mm, onFinished = { actions.setGroove(true) { it } }) { value ->
            actions.setGroove(false) { it.copy(depth = value) }
        }
    }
    CutSlider(orcaString("Tolerance"), groove.depthTolerance, 0.0, minOf(minOf(0.3 * groove.depth, 1.5), 0.5 * meanSize), mm,
        onFinished = { actions.setGroove(true) { it } }) { value -> actions.setGroove(false) { it.copy(depthTolerance = value) } }
    CutResettable(enabled = !(groove.width == init.width && groove.widthTolerance == 0.1), onReset = {
        actions.resetGroove { current, first -> current.copy(width = first.width, widthTolerance = 0.1) }
    }) {
        CutSlider(orcaString("Width"), groove.width, 1.0, meanSize, mm, onFinished = { actions.setGroove(true) { it } }) { value ->
            actions.setGroove(false) { it.copy(width = value) }
        }
    }
    CutSlider(orcaString("Tolerance"), groove.widthTolerance, 0.0, minOf(minOf(0.3 * groove.width, 1.5), 0.5 * meanSize), mm,
        onFinished = { actions.setGroove(true) { it } }) { value -> actions.setGroove(false) { it.copy(widthTolerance = value) } }
    // render_groove_angle_input()
    CutResettable(enabled = groove.flapsAngle != init.flapsAngle, onReset = {
        actions.resetGroove { current, first -> current.copy(flapsAngle = first.flapsAngle) }
    }) {
        CutSlider(orcaString("Flap Angle"), Math.toDegrees(groove.flapsAngle), 30.0, 120.0, "°", decimals = 0,
            onFinished = { actions.setGroove(true) { it } }) { value -> actions.setGroove(false) { it.copy(flapsAngle = Math.toRadians(value)) } }
    }
    CutResettable(enabled = groove.angle != init.angle, onReset = {
        actions.resetGroove { current, first -> current.copy(angle = first.angle) }
    }) {
        CutSlider(orcaString("Groove Angle"), Math.toDegrees(groove.angle), 0.0, 15.0, "°", decimals = 0,
            onFinished = { actions.setGroove(true) { it } }) { value -> actions.setGroove(false) { it.copy(angle = Math.toRadians(value)) } }
    }
    Text(orcaString("Multiple") + ": ", color = ORANGE_LIGHT, style = OrcaTheme.typography.body12, modifier = Modifier.padding(top = 4.dp))
    // render_groove_int_input(): 1 to 100, a step of one.
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(orcaString("Count"), color = colors.onCanvasPanel, style = OrcaTheme.typography.body12, modifier = Modifier.weight(1f))
        OrcaButton(text = "−", size = OrcaButtonSize.Compact, enabled = groove.count > 1, onClick = {
            actions.setGroove(true) { it.copy(count = it.count - 1) }
        })
        Text(groove.count.toString(), color = colors.onCanvasPanel, style = OrcaTheme.typography.body12)
        OrcaButton(text = "+", size = OrcaButtonSize.Compact, enabled = groove.count < 100, onClick = {
            actions.setGroove(true) { it.copy(count = it.count + 1) }
        })
        OrcaIconButton(
            icon = DesignR.drawable.orca_toolbar_reset,
            contentDescription = orcaString("Reset"),
            onClick = { actions.resetGroove { current, first -> current.copy(count = first.count) } },
            enabled = groove.count != init.count,
            tint = colors.onCanvasPanel,
        )
    }
    // render_groove_float_input(): up to the plate over the gaps between the grooves.
    val gapMax = plateSize / maxOf(groove.count - 1, 1)
    CutResettable(enabled = groove.count != 1 && groove.gap != init.gap, onReset = {
        actions.resetGroove { current, first -> current.copy(gap = first.gap) }
    }) {
        CutSlider(orcaString("Gap"), groove.gap, 1.0, gapMax, mm, enabled = groove.count != 1,
            onFinished = { actions.setGroove(true) { it } }) { value -> actions.setGroove(false) { it.copy(gap = value) } }
    }
    Row {
        Text(orcaString("Spacing"), color = colors.textDimmed, style = OrcaTheme.typography.body12, modifier = Modifier.weight(1f))
        Text(
            String.format(textLocale(), "%.2f", groove.gap + groove.outerWidth(mode.radius)) + mm,
            color = colors.textDimmed,
            style = OrcaTheme.typography.body12,
        )
    }
}

/** An input of the cut window with its reset button (render_reset_button()). */
@Composable
private fun CutResettable(enabled: Boolean, onReset: () -> Unit, content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) { content() }
        OrcaIconButton(
            icon = DesignR.drawable.orca_toolbar_reset,
            contentDescription = orcaString("Reset"),
            onClick = onReset,
            enabled = enabled,
            tint = OrcaTheme.colors.onCanvasPanel,
        )
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
private fun CutSlider(
    label: String,
    value: Double?,
    min: Double,
    max: Double,
    unit: String,
    decimals: Int = 2,
    enabled: Boolean = true,
    onFinished: () -> Unit = {},
    onChange: (Double) -> Unit,
) {
    val top = maxOf(max, min + 0.01)
    PaintingSlider(
        label = label,
        value = (value ?: min).coerceIn(min, top).toFloat(),
        range = min.toFloat()..top.toFloat(),
        text = value?.let { String.format(textLocale(), "%.${decimals}f %s", it, unit) } ?: " ",
        onChange = { onChange(it.toDouble()) },
        enabled = enabled,
        onFinished = onFinished,
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

/** is_approx()'s EPSILON, which the snaps' resets compare with. */
private const val SNAP_EPSILON = 1e-4

// ImGuiWrapper::COL_ORANGE_LIGHT: ColorRGBA::ORANGE().
private val ORANGE_LIGHT = Color(0.923f, 0.504f, 0.264f)

// UPPER_PART_COLOR and LOWER_PART_COLOR of GLGizmoCut.cpp: ColorRGBA::CYAN() and MAGENTA().
private val UPPER_PART_COLOR = Color(0f, 1f, 1f)
private val LOWER_PART_COLOR = Color(1f, 0f, 1f)
