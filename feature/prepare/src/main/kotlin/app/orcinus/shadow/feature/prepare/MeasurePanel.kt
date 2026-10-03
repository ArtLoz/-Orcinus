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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.unit.dp
import app.orcinus.shadow.core.designsystem.component.OrcaButton
import app.orcinus.shadow.core.designsystem.component.OrcaButtonSize
import app.orcinus.shadow.core.designsystem.component.OrcaButtonStyle
import app.orcinus.shadow.core.designsystem.component.OrcaIconButton
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
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
) {
    companion object {
        val NONE = MeasureActions({}, {}, {}, {}, {}, {})
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
