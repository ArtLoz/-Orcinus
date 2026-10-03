package app.orcinus.shadow.feature.prepare

import android.content.ClipData
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.orcinus.shadow.core.designsystem.component.OrcaButton
import app.orcinus.shadow.core.designsystem.component.OrcaButtonSize
import app.orcinus.shadow.core.designsystem.component.OrcaButtonStyle
import app.orcinus.shadow.core.designsystem.component.OrcaIconButton
import app.orcinus.shadow.core.designsystem.component.OrcaTextField
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import app.orcinus.shadow.core.model.AssemblyAction
import app.orcinus.shadow.core.model.AssemblyMode
import app.orcinus.shadow.core.model.ImperialUnits
import app.orcinus.shadow.core.model.MeasureFeatureType
import app.orcinus.shadow.core.model.MeasureReset
import app.orcinus.shadow.core.model.MeasureSelection
import app.orcinus.shadow.core.model.Measurement
import app.orcinus.shadow.core.model.Vector3
import app.orcinus.shadow.core.ui.orca.orcaString
import app.orcinus.shadow.render.scene.MeasureTouch
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt
import app.orcinus.shadow.core.designsystem.R as DesignR

/** What the measuring tool's window and the 3D view do with it (GLGizmoMeasure). */
internal class MeasureActions(
    /** The toolbar's Measure. */
    val toggle: () -> Unit,
    /** "Done". */
    val close: () -> Unit,
    /** A finger on the 3D view. */
    val touch: (MeasureTouch) -> Unit,
    /** The reset buttons, and "Restart selection". */
    val reset: (MeasureReset) -> Unit,
    /** Back, as the desktop app's Esc. */
    val escape: () -> Unit,
    /** "Select point", as the desktop app's Shift. */
    val setPointSelection: (Boolean) -> Unit,
    /** The distance label's "Edit to scale", with the distance it read in millimetres. */
    val editDistance: (Double) -> Unit,
    /** "Scale all": the distance typed, and the one the label read, in the label's units. */
    val scale: (value: Double, current: Double) -> Unit,
    /** The box's Cancel. */
    val cancelScale: () -> Unit,
) {
    companion object {
        val NONE = MeasureActions({}, {}, {}, {}, {}, {}, {}, { _, _ -> }, {})
    }
}

/**
 * GLGizmoMeasure::on_render_input_window(): the two selections with their
 * reset buttons (show_selection_ui()), and what they measure, each with a
 * button that copies it (show_distance_xyz_ui()); the distance along the
 * axes the tool shows in boxes it does not let edit. The desktop app's keys
 * are buttons here: Shift selects points while "Select point" is on, Delete
 * is "Restart selection", and Esc is the phone's Back.
 */
@Composable
internal fun MeasurePanel(mode: MeasureMode, actions: MeasureActions, imperial: Boolean) {
    val measurement = mode.measurement
    // m_units and the inches of "use_inches".
    val units = " " + orcaString(if (imperial) "in" else "mm")
    val scale = if (imperial) ImperialUnits.MM_TO_IN else 1.0
    PaintingPanelFrame(orcaString("Measure"), orcaString("Done"), actions.close) {
        // show_selection_ui()
        SelectionRow(
            title = orcaString("Selection") + " 1",
            text = selectionText(measurement.first, units, scale),
            color = SELECTED_1ST_COLOR,
            onReset = { actions.reset(MeasureReset.FIRST) }.takeIf { measurement.first != null },
        )
        SelectionRow(
            title = orcaString("Selection") + " 2",
            text = selectionText(measurement.second, units, scale),
            color = SELECTED_2ND_COLOR,
            onReset = { actions.reset(MeasureReset.SECOND) }.takeIf { measurement.first != null && measurement.second != null },
        )
        // The window drops the tip once both features are selected again.
        if (measurement.showResetFirstTip && measurement.second == null) {
            Text(
                text = orcaString("Feature 1 has been reset, \nfeature 2 has been feature 1"),
                color = OrcaTheme.colors.onCanvasPanel,
                style = OrcaTheme.typography.body12,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        TextCheck(orcaString("Select point"), mode.pointSelection, enabled = true, onChange = actions.setPointSelection)
        if (measurement.first != null) {
            OrcaButton(
                text = orcaString("Restart selection"),
                size = OrcaButtonSize.Compact,
                style = OrcaButtonStyle.Regular,
                onClick = { actions.reset(MeasureReset.ALL) },
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        val rows = measurementRows(measurement, units, scale)
        if (rows.isNotEmpty()) {
            Separator()
            rows.forEach { (label, value) -> MeasureRow(label, value) }
        }
        // add_edit_distance_xyz_box(): disabled while measuring (m_can_set_xyz_distance is false).
        val distance = measurement.second?.let { distanceOf(measurement) }?.let { Vector3(it.x * scale, it.y * scale, it.z * scale) }
        if (distance != null && norm(distance) > 0.01) {
            AxisRow("X:", AXIS_X_COLOR, distance.x)
            AxisRow("Y:", AXIS_Y_COLOR, distance.y)
            AxisRow("Z:", AXIS_Z_COLOR, distance.z)
        }
    }
}

/**
 * render_dimensioning()'s "distance_popup": the distance the label read, in
 * its units, to type another; "Scale all" (or Enter) scales the selection so
 * it reads that (perform_scale()), Cancel (or Esc) leaves it.
 */
@Composable
internal fun MeasureScaleDialog(distance: Double, imperial: Boolean, onScale: (value: Double, current: Double) -> Unit, onCancel: () -> Unit) {
    val colors = OrcaTheme.colors
    val current = if (imperial) distance * ImperialUnits.MM_TO_IN else distance
    val shown = String.format(Locale.ROOT, "%.3f", current)
    var text by rememberSaveable { mutableStateOf(shown) }
    // ImGui::InputDouble() keeps the value it was given until it is edited.
    val value = if (text == shown) current else text.replace(',', '.').toDoubleOrNull()
    val scale = { value?.let { onScale(it, current) } }
    AlertDialog(
        onDismissRequest = onCancel,
        confirmButton = {
            OrcaButton(orcaString("Scale all", "Verb"), onClick = { scale() }, enabled = value != null, style = OrcaButtonStyle.Confirm)
        },
        dismissButton = { OrcaButton(orcaString("Cancel"), onClick = onCancel, style = OrcaButtonStyle.Regular) },
        title = { Text(orcaString("Edit to scale"), style = OrcaTheme.typography.head16) },
        text = {
            OrcaTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                unit = orcaString(if (imperial) "in" else "mm"),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { scale() }),
            )
        },
        containerColor = colors.window,
        titleContentColor = colors.text,
        textContentColor = colors.text,
        shape = OrcaTheme.shapes.window,
    )
}

/** A selection's row: its name in its colour, what it is, and its reset button. */
@Composable
private fun SelectionRow(title: String, text: String, color: Color, onReset: (() -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text(title, color = color, style = OrcaTheme.typography.body13, modifier = Modifier.width(SELECTION_TITLE_WIDTH))
        Text(text, color = color, style = OrcaTheme.typography.body13, modifier = Modifier.weight(1f))
        if (onReset != null) {
            OrcaIconButton(icon = DesignR.drawable.orca_revert_btn, contentDescription = orcaString("Reset"), onClick = onReset, tint = Color.Unspecified)
        } else {
            Box(Modifier.size(RESET_PLACE))
        }
    }
}

/** add_measure_row_to_table(): a measurement, in COL_ORCA, and its copy button. */
@Composable
private fun MeasureRow(label: String, value: String) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(label, color = COL_ORCA, style = OrcaTheme.typography.body12)
            Text(value, color = OrcaTheme.colors.onCanvasPanel, style = OrcaTheme.typography.body13)
        }
        val copy = orcaString("Copy to clipboard")
        OrcaIconButton(
            icon = DesignR.drawable.orca_copy_menu,
            contentDescription = copy,
            onClick = { scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(label, "$label: $value"))) } },
            tint = Color.Unspecified,
        )
    }
}

/** A box of the distance along an axis, which the measuring tool shows but does not let edit. */
@Composable
private fun AxisRow(axis: String, color: Color, value: Double) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text(axis, color = color, style = OrcaTheme.typography.body13, modifier = Modifier.width(AXIS_LABEL_WIDTH))
        Text(
            text = String.format(Locale.ROOT, "%.2f", value),
            color = OrcaTheme.colors.textDimmed,
            style = OrcaTheme.typography.body13,
            modifier = Modifier
                .weight(1f)
                .background(OrcaTheme.colors.border.copy(alpha = 0.3f), OrcaTheme.shapes.control)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun Separator() {
    Box(
        Modifier
            .padding(vertical = 6.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(OrcaTheme.colors.border),
    )
}

/** format_item_text(): what a selection is, with a circle's diameter or an edge's length. */
@Composable
private fun selectionText(item: MeasureSelection?, units: String, scale: Double): String {
    if (item == null) return orcaString("None")
    val feature = item.feature
    var text = when {
        item.source.sameAs(feature) -> orcaString(surfaceFeatureName(feature.type))
        item.isCenter -> orcaString(centerName(item.source.type))
        else -> orcaString(pointName(item.source.type))
    }
    when (feature.type) {
        MeasureFeatureType.CIRCLE -> text += " (" + orcaString("Diameter") + ": " + formatDouble(2.0 * feature.value * scale) + units + ")"
        MeasureFeatureType.EDGE -> {
            val length = norm(Vector3(feature.pt2.x - feature.pt1.x, feature.pt2.y - feature.pt1.y, feature.pt2.z - feature.pt1.z))
            text += " (" + orcaString("Length") + ": " + formatDouble(length * scale) + units + ")"
        }
        else -> Unit
    }
    return text
}

/** show_distance_xyz_ui()'s rows: the angle and the distances the two selections measure. */
@Composable
private fun measurementRows(measurement: Measurement, units: String, scale: Double): List<Pair<String, String>> {
    if (measurement.second == null) return emptyList()
    val result = measurement.result
    val rows = ArrayList<Pair<String, String>>()
    result.angle?.let { rows += orcaString("Angle") to formatDouble(Math.toDegrees(it.angle)) + "°" }
    val strict = result.distanceStrict
    val infinite = result.distanceInfinite
    val showStrict = strict != null && (infinite == null || abs(strict.distance - infinite.distance) > EPSILON)
    infinite?.let { rows += orcaString(if (showStrict) "Perpendicular distance" else "Distance") to formatDouble(it.distance * scale) + units }
    if (showStrict) rows += orcaString("Direct distance") to formatDouble(strict.distance * scale) + units
    result.distanceXyz?.takeIf { norm(it) > EPSILON }?.let { xyz ->
        // format_vec3()
        rows += orcaString("Distance XYZ") to String.format(Locale.ROOT, "X: %.3f, Y: %.3f, Z: %.3f", xyz.x * scale, xyz.y * scale, xyz.z * scale)
    }
    return rows
}

/** update_measurement_result()'s m_distance: along the axes, or between the ends of the distance. */
private fun distanceOf(measurement: Measurement): Vector3? {
    val result = measurement.result
    result.distanceXyz?.takeIf { norm(it) > EPSILON }?.let { return it }
    val distance = result.distanceInfinite ?: result.distanceStrict ?: return Vector3(0.0, 0.0, 0.0)
    return Vector3(distance.to.x - distance.from.x, distance.to.y - distance.from.y, distance.to.z - distance.from.z)
}

/** surface_feature_type_as_string() */
private fun surfaceFeatureName(type: MeasureFeatureType) = when (type) {
    MeasureFeatureType.POINT -> "Vertex"
    MeasureFeatureType.EDGE -> "Edge"
    MeasureFeatureType.CIRCLE -> "Circle"
    MeasureFeatureType.PLANE -> "Plane"
}

/** point_on_feature_type_as_string() */
private fun pointName(type: MeasureFeatureType) = when (type) {
    MeasureFeatureType.POINT -> "Vertex"
    MeasureFeatureType.EDGE -> "Point on edge"
    MeasureFeatureType.CIRCLE -> "Point on circle"
    MeasureFeatureType.PLANE -> "Point on plane"
}

/** center_on_feature_type_as_string(), which only a circle or an edge with a centre asks. */
private fun centerName(type: MeasureFeatureType) = if (type == MeasureFeatureType.CIRCLE) "Center of circle" else "Center of edge"

/** GLGizmoMeasure::format_double() */
private fun formatDouble(value: Double) = String.format(Locale.ROOT, "%.3f", value)

private fun norm(v: Vector3) = sqrt(v.x * v.x + v.y * v.y + v.z * v.z)

/** libslic3r's EPSILON */
private const val EPSILON = 1e-4

/** GLGizmoMeasure.hpp's SELECTED_1ST_COLOR and SELECTED_2ND_COLOR, which the selections' rows are written in. */
private val SELECTED_1ST_COLOR = Color(0.25f, 0.75f, 0.75f)
private val SELECTED_2ND_COLOR = Color(0.75f, 0.25f, 0.75f)

/** ImGuiWrapper::COL_ORCA: ColorRGBA::ORCA(). */
private val COL_ORCA = Color(0f, 150f / 255f, 136f / 255f)

/** ColorRGBA::X(), Y() and Z(), the axes' colours. */
private val AXIS_X_COLOR = Color(255 / 255f, 60 / 255f, 91 / 255f)
private val AXIS_Y_COLOR = Color(100 / 255f, 200 / 255f, 24 / 255f)
private val AXIS_Z_COLOR = Color(47 / 255f, 136 / 255f, 233 / 255f)

private val SELECTION_TITLE_WIDTH = 88.dp
private val AXIS_LABEL_WIDTH = 32.dp
private val RESET_PLACE = 40.dp

/** What the assembly tool's window does besides the measuring tool's (GLGizmoAssembly). */
internal class AssemblyActions(
    /** The toolbar's Assemble. */
    val toggle: () -> Unit,
    val setMode: (AssemblyMode) -> Unit,
    val assemble: (AssemblyAction, List<Double>) -> Unit,
    /** "Flip by Face 2". */
    val flip: () -> Unit,
) {
    companion object {
        val NONE = AssemblyActions({}, {}, { _, _ -> }, {})
    }
}

/**
 * GLGizmoAssembly::on_render_input_window(): "Mode", the selections as
 * fixed and moving with the mode's tip, face to face "Center coincidence"
 * and "Parallel" (show_face_face_assembly_common()), "Flip by Face 2",
 * "Parallel distance" and "Rotate around center"
 * (show_face_face_assembly_senior()), point to point the direct distance and
 * the distance along the axes, which moves the second volume (the second
 * object only along X and Y, as objects stand on the plate), "Done", and the
 * window's warnings. The desktop app's Shift is "Select point", point to point.
 */
@Composable
internal fun AssemblyPanel(mode: MeasureMode, actions: MeasureActions, assembly: AssemblyActions, imperial: Boolean) {
    val assemblyMode = mode.assembly ?: return
    val measurement = mode.measurement
    val faceToFace = assemblyMode == AssemblyMode.FACE_FACE
    val units = " " + orcaString(if (imperial) "in" else "mm")
    val scale = if (imperial) ImperialUnits.MM_TO_IN else 1.0
    PaintingPanelFrame(orcaString("Assemble"), orcaString("Done"), actions.close) {
        // render_assembly_mode_combo()
        TextRow(orcaString("Mode")) {
            OrcaButton(
                text = orcaString("Face and face assembly"),
                size = OrcaButtonSize.Compact,
                style = if (faceToFace) OrcaButtonStyle.Confirm else OrcaButtonStyle.Regular,
                onClick = { assembly.setMode(AssemblyMode.FACE_FACE) },
            )
            OrcaButton(
                text = orcaString("Point and point assembly"),
                size = OrcaButtonSize.Compact,
                style = if (faceToFace) OrcaButtonStyle.Regular else OrcaButtonStyle.Confirm,
                onClick = { assembly.setMode(AssemblyMode.POINT_POINT) },
                modifier = Modifier.padding(start = 6.dp),
            )
        }
        // show_selection_ui() of the assembly
        Text(
            text = orcaString(
                if (faceToFace) "Select 2 faces on objects and \n make objects assemble together." else "Select 2 points or circles on objects and \n specify distance between them.",
            ),
            color = OrcaTheme.colors.onCanvasPanel,
            style = OrcaTheme.typography.body12,
            modifier = Modifier.padding(top = 6.dp),
        )
        val kind = orcaString(if (faceToFace) "Face" else "Point")
        SelectionRow(
            title = kind + " 1" + orcaString(" (Fixed)"),
            text = selectionText(measurement.first, units, scale),
            color = SELECTED_1ST_COLOR,
            onReset = { actions.reset(MeasureReset.FIRST) }.takeIf { measurement.first != null },
        )
        SelectionRow(
            title = kind + " 2" + orcaString(" (Moving)"),
            text = selectionText(measurement.second, units, scale),
            color = SELECTED_2ND_COLOR,
            onReset = { actions.reset(MeasureReset.SECOND) }.takeIf { measurement.first != null && measurement.second != null },
        )
        if (measurement.showResetFirstTip && measurement.second == null) {
            Text(
                text = orcaString("Feature 1 has been reset, \nfeature 2 has been feature 1"),
                color = OrcaTheme.colors.onCanvasPanel,
                style = OrcaTheme.typography.body12,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        // Face to face the features are the planes, which point selection does not pick.
        if (!faceToFace) TextCheck(orcaString("Select point"), mode.pointSelection, enabled = true, onChange = actions.setPointSelection)
        if (measurement.first != null) {
            OrcaButton(
                text = orcaString("Restart selection"),
                size = OrcaButtonSize.Compact,
                style = OrcaButtonStyle.Regular,
                onClick = { actions.reset(MeasureReset.ALL) },
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        val faces = faceToFace && measurement.hitVolumes == 2 &&
            measurement.first?.feature?.type == MeasureFeatureType.PLANE && measurement.second?.feature?.type == MeasureFeatureType.PLANE
        val action = measurement.assembly
        if (faces) {
            // show_face_face_assembly_common()
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                OrcaButton(
                    text = orcaString("Center coincidence"),
                    size = OrcaButtonSize.Compact,
                    style = OrcaButtonStyle.Confirm,
                    onClick = { assembly.assemble(AssemblyAction.CENTER_COINCIDENCE, emptyList()) },
                    enabled = action.canSetToCenterCoincidence,
                )
                OrcaButton(
                    text = orcaString("Parallel"),
                    size = OrcaButtonSize.Compact,
                    style = OrcaButtonStyle.Regular,
                    onClick = { assembly.assemble(AssemblyAction.PARALLEL, emptyList()) },
                    enabled = action.canSetToParallel,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
        Separator()
        if (faces) {
            // show_face_face_assembly_senior()
            TextCheck(orcaString("Flip by Face 2"), mode.flipVolume2, enabled = true) { assembly.flip() }
            if (action.hasParallelDistance) {
                TextRow(orcaString("Parallel distance:")) {
                    PositionField(
                        value = action.parallelDistance,
                        onValue = { assembly.assemble(AssemblyAction.PARALLEL_DISTANCE, listOf(it)) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (action.canAroundCenterOfFaces) {
                TextRow(orcaString("Rotate around center:")) {
                    PositionField(
                        value = 0.0,
                        onValue = { degrees -> if (abs(degrees) > EPSILON) assembly.assemble(AssemblyAction.AROUND_CENTER, listOf(degrees)) },
                        modifier = Modifier.weight(1f),
                    )
                    Text("°", color = OrcaTheme.colors.onCanvasPanel, style = OrcaTheme.typography.body12, modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
        if (!faceToFace && measurement.second != null) {
            // show_distance_xyz_ui() of the assembly point to point: the direct distance, and the boxes along the axes.
            val result = measurement.result
            val strict = result.distanceStrict
            val infinite = result.distanceInfinite
            if (strict != null && (infinite == null || abs(strict.distance - infinite.distance) > EPSILON)) {
                MeasureRow(orcaString("Direct distance"), formatDouble(strict.distance * scale) + units)
            }
            val distance = distanceOf(measurement)?.let { Vector3(it.x * scale, it.y * scale, it.z * scale) }
            if (distance != null && norm(distance) > 0.01) {
                val oneVolume = measurement.hitVolumes == 1
                // add_edit_distance_xyz_box(): a changed box moves the second volume along its axis by the change.
                // As the desktop app, the change is in the label's units, which it moves by in millimetres.
                AxisBox("X:", AXIS_X_COLOR, distance.x, enabled = !oneVolume && measurement.canSetXyzDistance) {
                    assembly.assemble(AssemblyAction.DISTANCE, listOf(it - distance.x, 0.0, 0.0))
                }
                AxisBox("Y:", AXIS_Y_COLOR, distance.y, enabled = !oneVolume && measurement.canSetXyzDistance) {
                    assembly.assemble(AssemblyAction.DISTANCE, listOf(0.0, it - distance.y, 0.0))
                }
                AxisBox("Z:", AXIS_Z_COLOR, distance.z, enabled = !oneVolume && measurement.sameObject && measurement.canSetXyzDistance) {
                    assembly.assemble(AssemblyAction.DISTANCE, listOf(0.0, 0.0, it - distance.z))
                }
            }
        }
        // render_input_window_warning()
        val warnings = buildList {
            if (measurement.hitVolumes == 1) add(orcaString("Warning: please select two different meshes."))
            if (measurement.wrongFeatureTip) {
                add(orcaString(if (faceToFace) "Warning: please select Plane's feature." else "Warning: please select Point's or Circle's feature."))
            }
            if (measurement.hitVolumes == 2 && !measurement.sameObject) {
                add(
                    orcaString("Warning") + ": " +
                        orcaString("It is recommended to assemble objects first,\nbecause they are restricted to the bed \nand only parts can be lifted."),
                )
            }
        }
        if (warnings.isNotEmpty()) {
            Separator()
            warnings.forEach { Text(it, color = OrcaTheme.colors.warning, style = OrcaTheme.typography.body12) }
        }
    }
}

/** A box of the distance along an axis, which the assembly tool lets edit where it can move the volume so. */
@Composable
private fun AxisBox(axis: String, color: Color, value: Double, enabled: Boolean, onValue: (Double) -> Unit) {
    if (!enabled) return AxisRow(axis, color, value)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Text(axis, color = color, style = OrcaTheme.typography.body13, modifier = Modifier.width(AXIS_LABEL_WIDTH))
        PositionField(value = value, onValue = onValue, modifier = Modifier.weight(1f))
    }
}
