package app.orcinus.shadow.core.ui.plate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.orcinus.shadow.core.designsystem.R as DesignR
import app.orcinus.shadow.core.designsystem.component.OrcaButton
import app.orcinus.shadow.core.designsystem.component.OrcaButtonSize
import app.orcinus.shadow.core.designsystem.component.OrcaButtonStyle
import app.orcinus.shadow.core.designsystem.component.OrcaCanvasRoundButton
import app.orcinus.shadow.core.designsystem.component.OrcaRadioButton
import app.orcinus.shadow.core.designsystem.component.OrcaSheetHandle
import app.orcinus.shadow.core.designsystem.component.OrcaSwitch
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import app.orcinus.shadow.core.model.AppConfigKeys
import app.orcinus.shadow.core.model.CameraView
import app.orcinus.shadow.core.model.CanvasPreferences
import app.orcinus.shadow.core.ui.orca.orcaString

/**
 * GLCanvas3D::_render_canvas_toolbar(): the menu button and, while "Zoom
 * button" is on, the zoom button under it, which frames the selection or the
 * plate. A phone has no menu bar, so the menu, a sheet, also holds what the
 * desktop app's View menu does: the camera's views
 * (add_common_view_menu_items(), [onView] with null for "Default View") and
 * the projection, above the canvas menu's items. What an item changes
 * OrcaSlicer.conf keeps ([onSet]).
 */
@Composable
fun CanvasViewButtons(
    canvas: CanvasPreferences,
    onView: (CameraView?) -> Unit,
    onSet: (key: String, value: String) -> Unit,
    onZoom: () -> Unit,
    modifier: Modifier = Modifier,
    /** The preview's canvas, on which the items of the Prepare page's alone are disabled. */
    preview: Boolean = false,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OrcaCanvasRoundButton(DesignR.drawable.orca_canvas_menu, orcaString("View"), onClick = { open = true })
        if (canvas.zoomButton) {
            OrcaCanvasRoundButton(DesignR.drawable.orca_canvas_zoom, orcaString("Fit camera to scene or selected object."), onClick = onZoom)
        }
    }
    if (open) {
        CanvasViewSheet(
            canvas = canvas,
            preview = preview,
            onView = { view ->
                open = false
                onView(view)
            },
            onSet = onSet,
            onDismiss = { open = false },
        )
    }
}

/**
 * ImGuizmo's FaceLabels as GLCanvas3D::_render_3d_navigator() names them, in
 * ImGuizmo::FACES order: back, top, right, front, bottom and left.
 */
@Composable
fun navigatorFaceLabels(): List<String> = listOf(
    orcaString("Back"),
    orcaString("Top"),
    orcaString("Right", "Camera"),
    orcaString("Front"),
    orcaString("Bottom"),
    orcaString("Left", "Camera"),
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun CanvasViewSheet(
    canvas: CanvasPreferences,
    preview: Boolean,
    onView: (CameraView?) -> Unit,
    onSet: (key: String, value: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OrcaTheme.colors
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.window, dragHandle = { OrcaSheetHandle() }) {
        Column(
            Modifier
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                orcaString("View"),
                color = colors.text,
                style = OrcaTheme.typography.head16,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            // add_common_view_menu_items()
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                VIEWS.forEach { (label, context, view) ->
                    OrcaButton(
                        text = orcaString(label, context),
                        onClick = { onView(view) },
                        style = OrcaButtonStyle.Regular,
                        size = OrcaButtonSize.Compact,
                    )
                }
            }
            HorizontalDivider(color = colors.separator)
            // Use Perspective View, Use Orthogonal View and Auto Perspective.
            RadioRow(orcaString("Use Perspective View"), canvas.perspective) { onSet(AppConfigKeys.USE_PERSPECTIVE_CAMERA, "true") }
            RadioRow(orcaString("Use Orthogonal View"), !canvas.perspective) { onSet(AppConfigKeys.USE_PERSPECTIVE_CAMERA, "false") }
            SwitchRow(
                orcaString("Auto Perspective"),
                orcaString("Automatically switch between orthographic and perspective when changing from top/bottom/side views."),
                canvas.autoPerspective,
            ) { onSet(AppConfigKeys.AUTO_PERSPECTIVE, it.toString()) }
            HorizontalDivider(color = colors.separator)
            // The canvas menu (CanvasToolbarMenu), in its order.
            SwitchRow(orcaString("3D Navigator"), null, canvas.navigator) { onSet(AppConfigKeys.SHOW_3D_NAVIGATOR, it.toString()) }
            SwitchRow(orcaString("Zoom button"), null, canvas.zoomButton) { onSet(AppConfigKeys.SHOW_CANVAS_ZOOM_BUTTON, it.toString()) }
            HorizontalDivider(color = colors.separator)
            // Plater::priv::is_view3D_overhang_shown(): off while the preview is shown.
            SwitchRow(orcaString("Overhangs"), null, canvas.overhang && !preview, enabled = !preview) {
                onSet(AppConfigKeys.SHOW_OVERHANG, it.toString())
            }
            SwitchRow(orcaString("Outline"), null, canvas.outline, enabled = !preview) { onSet(AppConfigKeys.SHOW_OUTLINE, it.toString()) }
            HorizontalDivider(color = colors.separator)
            SwitchRow(orcaString("Axes"), null, canvas.axes) { onSet(AppConfigKeys.SHOW_AXES, it.toString()) }
            SwitchRow(orcaString("Gridlines"), null, canvas.gridlines) { onSet(AppConfigKeys.SHOW_PLATE_GRIDLINES, it.toString()) }
            HorizontalDivider(color = colors.separator)
            // Plater::priv::are_view3D_labels_shown(): off while the preview is shown.
            SwitchRow(orcaString("Labels"), null, canvas.labels && !preview, enabled = !preview) { onSet(AppConfigKeys.SHOW_LABELS, it.toString()) }
            Spacer(Modifier.padding(bottom = 8.dp))
        }
    }
}

@Composable
private fun RadioRow(title: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 16.dp),
    ) {
        OrcaRadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Text(title, color = OrcaTheme.colors.text, style = OrcaTheme.typography.body14)
    }
}

@Composable
private fun SwitchRow(title: String, detail: String?, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = if (enabled) OrcaTheme.colors.text else OrcaTheme.colors.textDisabled, style = OrcaTheme.typography.body14)
            if (detail != null) Text(detail, color = OrcaTheme.colors.textSide, style = OrcaTheme.typography.body12)
        }
        Spacer(Modifier.width(12.dp))
        OrcaSwitch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

/** add_common_view_menu_items(): the label, its context, and the view; "Default View" has none. */
private val VIEWS = listOf(
    Triple("Default View", "", null),
    Triple("Top", "", CameraView.TOP),
    Triple("Bottom", "", CameraView.BOTTOM),
    Triple("Front", "", CameraView.FRONT),
    Triple("Rear", "", CameraView.REAR),
    Triple("Left", "Camera", CameraView.LEFT),
    Triple("Right", "Camera", CameraView.RIGHT),
)
