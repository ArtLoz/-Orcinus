package app.orcinus.shadow.feature.prepare

import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.orcinus.shadow.core.designsystem.R as DesignR
import app.orcinus.shadow.core.designsystem.component.OrcaButton
import app.orcinus.shadow.core.designsystem.component.OrcaButtonSize
import app.orcinus.shadow.core.designsystem.component.OrcaButtonStyle
import app.orcinus.shadow.core.designsystem.component.OrcaCanvas
import app.orcinus.shadow.core.designsystem.component.OrcaCanvasTool
import app.orcinus.shadow.core.designsystem.component.OrcaCanvasToolbar
import app.orcinus.shadow.core.designsystem.component.OrcaCanvasToolbarSeparator
import app.orcinus.shadow.core.designsystem.component.OrcaCheckBox
import app.orcinus.shadow.core.designsystem.component.OrcaContextMenu
import app.orcinus.shadow.core.designsystem.component.OrcaGizmoPanel
import app.orcinus.shadow.core.designsystem.component.OrcaIconButton
import app.orcinus.shadow.core.designsystem.component.OrcaMenuCheckItem
import app.orcinus.shadow.core.designsystem.component.OrcaNotification
import app.orcinus.shadow.core.designsystem.component.OrcaNotificationLevel
import app.orcinus.shadow.core.designsystem.component.OrcaNotificationText
import app.orcinus.shadow.core.designsystem.component.OrcaProgressNotification
import app.orcinus.shadow.core.designsystem.component.OrcaTextField
import app.orcinus.shadow.core.designsystem.layout.OrcaSidebarToggleSpace
import app.orcinus.shadow.core.designsystem.layout.OrcaWindowLayout
import app.orcinus.shadow.core.designsystem.layout.currentOrcaWindowLayout
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import app.orcinus.shadow.core.designsystem.theme.OrcinusTheme
import app.orcinus.shadow.core.model.ArrangeSettings
import app.orcinus.shadow.core.model.BoundingSphere
import app.orcinus.shadow.core.model.BuildVolumeFit
import app.orcinus.shadow.core.model.Manipulation
import app.orcinus.shadow.core.model.ModelDimensions
import app.orcinus.shadow.core.model.ModelInspection
import app.orcinus.shadow.core.model.PlateObject
import app.orcinus.shadow.core.model.PlateSlicing
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.SliceJobId
import app.orcinus.shadow.core.model.SliceProgress
import app.orcinus.shadow.core.model.SliceStage
import app.orcinus.shadow.core.model.Transform3
import app.orcinus.shadow.core.model.Vector3
import app.orcinus.shadow.core.ui.displayName
import app.orcinus.shadow.core.ui.sizeText
import app.orcinus.shadow.core.ui.title
import app.orcinus.shadow.render.scene.PlateGizmo
import app.orcinus.shadow.render.scene.PlateView
import java.util.Locale
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

@Composable
internal fun PrepareRoute(
    viewModel: PrepareViewModel,
    onSliceRequested: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.addModel(it.toString()) }
    }
    PrepareScreen(
        state = state,
        layout = currentOrcaWindowLayout(),
        onAddModel = { modelPicker.launch(arrayOf("*/*")) },
        onAddCalibrationCube = viewModel::addCalibrationCube,
        onSelectObject = viewModel::selectObject,
        onPlaceObject = viewModel::placeObject,
        onSetAutoDrop = viewModel::setAutoDrop,
        onToggleGizmo = viewModel::toggleGizmo,
        onCloseGizmo = viewModel::closeGizmo,
        onSetPosition = viewModel::setPosition,
        onAutoOrient = viewModel::autoOrient,
        arrangeActions = ArrangeActions(
            toggle = viewModel::toggleArrangeOptions,
            change = viewModel::setArrangeSettings,
            reset = viewModel::resetArrangeSettings,
            arrange = viewModel::arrange,
        ),
        rotationActions = RotationActions(
            rotateBy = viewModel::rotateBy,
            setRotation = viewModel::setRotation,
            reset = viewModel::resetRotation,
            resetToZero = viewModel::resetRotationToZero,
        ),
        scaleActions = ScaleActions(
            setScale = viewModel::setScale,
            setSize = viewModel::setSize,
            setUniform = viewModel::setUniformScale,
            reset = viewModel::resetScale,
        ),
        onSlice = {
            onSliceRequested()
            viewModel.slice()
        },
        onCancelSlicing = viewModel::cancelSlicing,
        onDismissProblem = viewModel::dismissProblem,
    )
}

/**
 * OrcaSlicer's Prepare page: the 3D plate view, edge to edge, with the model
 * toolbar and the notifications over it. The sidebar belongs to the app shell.
 */
@Composable
internal fun PrepareScreen(
    state: PrepareUiState,
    layout: OrcaWindowLayout,
    onAddModel: () -> Unit,
    onAddCalibrationCube: () -> Unit,
    onSelectObject: (Int?) -> Unit,
    onPlaceObject: (Int, Transform3, Manipulation) -> Unit,
    onSetAutoDrop: (index: Int, enabled: Boolean) -> Unit,
    onToggleGizmo: (PlateGizmo) -> Unit,
    onCloseGizmo: () -> Unit,
    onSetPosition: (axis: Int, value: Double) -> Unit,
    onAutoOrient: () -> Unit,
    arrangeActions: ArrangeActions,
    rotationActions: RotationActions,
    scaleActions: ScaleActions,
    onSlice: () -> Unit,
    onCancelSlicing: () -> Unit,
    onDismissProblem: () -> Unit,
) {
    OrcaCanvas(Modifier.fillMaxSize()) {
        var objectMenu by remember { mutableStateOf<ObjectMenu?>(null) }
        // Previews have no OpenGL; they show the canvas colour.
        if (!LocalInspectionMode.current) {
            PlateView(
                plate = state.plate,
                objects = state.sceneObjects,
                selectedObject = state.selectedObject,
                gizmo = state.gizmo,
                flatteningPlanes = state.flatteningPlanes,
                editable = state.canEditPlate,
                onSelectObject = onSelectObject,
                onPlaceObject = onPlaceObject,
                onOpenObjectMenu = { index, position -> objectMenu = ObjectMenu(index, position) },
                contentDescription = stringResource(R.string.plate_view),
                modifier = Modifier.fillMaxSize(),
            )
        }
        objectMenu?.let { menu -> ObjectContextMenu(state, menu, onDismiss = { objectMenu = null }, onSetAutoDrop) }
        // The canvas runs under the system bars; its controls stay clear of them.
        Box(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)),
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp, start = CanvasMargin, end = CanvasMargin),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // The toolbar starts after the sidebar button; the gizmo windows below may use the whole width.
                Box(Modifier.padding(start = OrcaSidebarToggleSpace - CanvasMargin)) {
                    CanvasToolbar(state, onAddModel, onAddCalibrationCube, onAutoOrient, arrangeActions.toggle, onToggleGizmo)
                }
                val position = state.selectedPosition
                val rotation = state.selectedRotation
                val scale = state.selectedScale
                val size = state.selectedSize
                when {
                    state.arrangeOptionsOpen -> ArrangeOptionsPanel(state.arrangeSettings, arrangeActions)
                    state.gizmo == PlateGizmo.SCALE && scale != null && size != null -> ScaleGizmoPanel(state, scale, size, scaleActions, onCloseGizmo)
                    state.gizmo == PlateGizmo.MOVE && position != null -> MoveGizmoPanel(position, onSetPosition, onCloseGizmo)
                    state.gizmo == PlateGizmo.ROTATE && rotation != null -> RotateGizmoPanel(state, rotation, rotationActions, onCloseGizmo)
                }
            }
            when {
                state.importing -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = OrcaTheme.colors.accent)
                    Text(
                        text = stringResource(R.string.importing_model),
                        color = OrcaTheme.colors.textSide,
                        style = OrcaTheme.typography.body14,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }

                state.plate != null && state.plateObject == null -> Text(
                    text = stringResource(R.string.empty_plate),
                    color = OrcaTheme.colors.textSide,
                    style = OrcaTheme.typography.body14,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Notifications(state, onCancelSlicing, onDismissProblem)
                // In a wide window the slice button sits in the tab bar, as on desktop.
                if (layout == OrcaWindowLayout.Compact) {
                    OrcaButton(stringResource(R.string.slice_plate), onClick = onSlice, enabled = state.canSlice)
                }
            }
        }
    }
}

/** The context menu asked for over the object [index], at [position] in the plate view. */
private data class ObjectMenu(val index: Int, val position: Offset)

/**
 * MenuFactory::create_object_menu() for an object on the plate, with the items
 * the app supports so far.
 */
@Composable
private fun ObjectContextMenu(
    state: PrepareUiState,
    menu: ObjectMenu,
    onDismiss: () -> Unit,
    onSetAutoDrop: (index: Int, enabled: Boolean) -> Unit,
) {
    val target = state.sceneObjects.getOrNull(menu.index)
    OrcaContextMenu(
        expanded = target != null,
        position = IntOffset(menu.position.x.roundToInt(), menu.position.y.roundToInt()),
        onDismissRequest = onDismiss,
    ) {
        if (target == null) return@OrcaContextMenu
        // append_menu_item_auto_drop(): checked while the object drops onto the plate.
        OrcaMenuCheckItem(
            text = stringResource(R.string.object_menu_auto_drop),
            checked = target.autoDrop,
            enabled = state.canEditPlate,
            onClick = {
                onDismiss()
                onSetAutoDrop(menu.index, !target.autoDrop)
            },
        )
    }
}

/** Notifications of the canvas: problems, progress, the object info. */
@Composable
private fun Notifications(
    state: PrepareUiState,
    onCancelSlicing: () -> Unit,
    onDismissProblem: () -> Unit,
) {
    if (state.objectClashed) {
        // GLCanvas3D::EWarning::ObjectClashed
        OrcaNotification(level = OrcaNotificationLevel.Error) {
            OrcaNotificationText(stringResource(R.string.object_clashed))
        }
    }
    state.problem?.let { problem ->
        OrcaNotification(
            action = {
                Text(
                    text = stringResource(R.string.dismiss),
                    color = OrcaTheme.colors.accent,
                    style = OrcaTheme.typography.body13,
                    modifier = Modifier
                        .padding(8.dp)
                        .clickable(role = Role.Button, onClick = onDismissProblem),
                )
            },
        ) {
            OrcaNotificationText(problem.title(), emphasized = true)
            problem.detail?.takeIf { it.isNotBlank() }?.let { OrcaNotificationText(it) }
        }
    }
    val slicing = state.slicing
    if (slicing != null) {
        val fraction = slicing.progress?.fraction ?: 0f
        OrcaProgressNotification(
            title = if (slicing.cancelling) {
                stringResource(R.string.slicing_cancelling)
            } else {
                stringResource(R.string.slicing_progress, (fraction * 100).roundToInt())
            },
            detail = slicing.progress?.detail,
            progress = fraction,
            cancelLabel = stringResource(R.string.cancel),
            onCancel = onCancelSlicing,
        )
    } else {
        state.plateObject?.let { ObjectInfo(it) }
    }
}

@Composable
private fun ObjectInfo(plateObject: PlateObject) {
    OrcaNotification {
        OrcaNotificationText(stringResource(R.string.object_name, plateObject.displayName()), emphasized = true)
        OrcaNotificationText(stringResource(R.string.object_size, plateObject.inspection.dimensions.sizeText()))
        OrcaNotificationText(stringResource(R.string.object_triangles, plateObject.inspection.facetCount))
    }
}

/**
 * OrcaSlicer's toolbars over the 3D view, in its order: the main toolbar
 * (GLCanvas3D::_init_main_toolbar), a separator, the gizmos a single-filament
 * FFF printer shows (GLGizmosManager::get_selectable_idxs), a separator, and
 * the assembly view toolbar. The calibration cube follows Add. Tools whose
 * features the app does not have yet stay disabled.
 */
@Composable
private fun CanvasToolbar(
    state: PrepareUiState,
    onAddModel: () -> Unit,
    onAddCalibrationCube: () -> Unit,
    onAutoOrient: () -> Unit,
    onToggleArrange: () -> Unit,
    onToggleGizmo: (PlateGizmo) -> Unit,
) {
    @Composable
    fun gizmo(icon: Int, name: Int, gizmo: PlateGizmo?) = OrcaCanvasTool(
        icon = icon,
        contentDescription = stringResource(name),
        onClick = { gizmo?.let(onToggleGizmo) },
        enabled = gizmo != null && state.canManipulate,
        selected = gizmo != null && state.gizmo == gizmo,
    )

    @Composable
    fun unavailable(icon: Int, name: Int) = OrcaCanvasTool(icon, stringResource(name), onClick = {}, enabled = false)

    OrcaCanvasToolbar {
        OrcaCanvasTool(DesignR.drawable.orca_toolbar_open, stringResource(R.string.add_model), onAddModel, enabled = state.canEditPlate)
        OrcaCanvasTool(DesignR.drawable.orca_tab_3d_active, stringResource(R.string.add_calibration_cube), onAddCalibrationCube, enabled = state.canEditPlate)
        unavailable(DesignR.drawable.orca_toolbar_add_plate, R.string.toolbar_add_plate)
        OrcaCanvasTool(DesignR.drawable.orca_toolbar_orient, stringResource(R.string.toolbar_orient), onAutoOrient, enabled = state.canArrange)
        OrcaCanvasTool(
            icon = DesignR.drawable.orca_toolbar_arrange,
            contentDescription = stringResource(R.string.toolbar_arrange),
            onClick = onToggleArrange,
            enabled = state.canArrange,
            selected = state.arrangeOptionsOpen,
        )
        OrcaCanvasToolbarSeparator()
        unavailable(DesignR.drawable.orca_instance_add, R.string.toolbar_add_instance)
        unavailable(DesignR.drawable.orca_instance_remove, R.string.toolbar_remove_instance)
        unavailable(DesignR.drawable.orca_split_objects, R.string.toolbar_split_objects)
        unavailable(DesignR.drawable.orca_split_parts, R.string.toolbar_split_parts)
        unavailable(DesignR.drawable.orca_toolbar_variable_layer_height, R.string.toolbar_variable_layer_height)
        OrcaCanvasToolbarSeparator()
        gizmo(DesignR.drawable.orca_toolbar_move, R.string.gizmo_move, PlateGizmo.MOVE)
        gizmo(DesignR.drawable.orca_toolbar_rotate, R.string.gizmo_rotate, PlateGizmo.ROTATE)
        gizmo(DesignR.drawable.orca_toolbar_scale, R.string.gizmo_scale, PlateGizmo.SCALE)
        gizmo(DesignR.drawable.orca_toolbar_flatten, R.string.gizmo_lay_on_face, PlateGizmo.LAY_ON_FACE)
        gizmo(DesignR.drawable.orca_toolbar_cut, R.string.gizmo_cut, null)
        gizmo(DesignR.drawable.orca_toolbar_meshboolean, R.string.gizmo_mesh_boolean, null)
        gizmo(DesignR.drawable.orca_toolbar_support, R.string.gizmo_support_painting, null)
        gizmo(DesignR.drawable.orca_toolbar_seam, R.string.gizmo_seam_painting, null)
        gizmo(DesignR.drawable.orca_toolbar_fuzzy_skin_paint, R.string.gizmo_fuzzy_skin_painting, null)
        gizmo(DesignR.drawable.orca_toolbar_text, R.string.gizmo_emboss, null)
        gizmo(DesignR.drawable.orca_toolbar_measure, R.string.gizmo_measure, null)
        gizmo(DesignR.drawable.orca_toolbar_assembly, R.string.gizmo_assembly, null)
        gizmo(DesignR.drawable.orca_toolbar_brimears, R.string.gizmo_brim_ears, null)
        OrcaCanvasToolbarSeparator()
        unavailable(DesignR.drawable.orca_toolbar_assemble, R.string.toolbar_assembly_view)
    }
}

/**
 * GizmoObjectManipulation::do_render_move_window() in world coordinates: the
 * object's position per axis, applied when an input is done, and the button
 * that closes the gizmo.
 */
@Composable
private fun MoveGizmoPanel(
    position: ObjectPosition,
    onSetPosition: (axis: Int, value: Double) -> Unit,
    onDone: () -> Unit,
) {
    OrcaGizmoPanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(PositionLabelWidth))
            AxisHeaders()
        }
        GizmoValueRow(
            label = stringResource(R.string.gizmo_position),
            values = listOf(position.x, position.y, position.z),
            unit = stringResource(R.string.unit_mm),
            labelWidth = PositionLabelWidth,
            onValue = onSetPosition,
        )
        GizmoPanelFooter(onDone)
    }
}

/** What the arrange options window does. */
internal class ArrangeActions(
    val toggle: () -> Unit,
    val change: (ArrangeSettings) -> Unit,
    val reset: () -> Unit,
    val arrange: () -> Unit,
)

/**
 * GLCanvas3D::_render_arrange_menu() for FFF printers: the spacing slider and
 * its input, auto spacing at 0, the rotation, materials, and Y-axis options,
 * and the Arrange and Reset buttons. The calibration-region option is only for
 * printers with a lidar and does not show.
 */
@Composable
private fun ArrangeOptionsPanel(settings: ArrangeSettings, actions: ArrangeActions) {
    val colors = OrcaTheme.colors
    OrcaGizmoPanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.arrange_spacing), color = colors.onCanvasPanel, style = OrcaTheme.typography.body13)
            Slider(
                value = settings.distance.toFloat(),
                onValueChange = { actions.change(settings.copy(distance = it.toDouble())) },
                valueRange = 0f..100f,
                colors = SliderDefaults.colors(thumbColor = colors.accent, activeTrackColor = colors.accent, inactiveTrackColor = colors.border),
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .weight(1f),
            )
            PositionField(
                value = settings.distance,
                onValue = { actions.change(settings.copy(distance = it)) },
                modifier = Modifier.width(PositionFieldWidth),
            )
        }
        Text(stringResource(R.string.arrange_spacing_auto), color = colors.textSide, style = OrcaTheme.typography.body12)
        Box(
            Modifier
                .padding(vertical = 8.dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.canvasPanelSeparator),
        )
        ArrangeOption(stringResource(R.string.arrange_rotation), settings.enableRotation) {
            actions.change(settings.copy(enableRotation = it))
        }
        ArrangeOption(stringResource(R.string.arrange_multi_materials), settings.allowMultiMaterialsOnSamePlate) {
            actions.change(settings.copy(allowMultiMaterialsOnSamePlate = it))
        }
        // No alignment to the Y axis while rotation is allowed.
        ArrangeOption(stringResource(R.string.arrange_align_y), settings.alignToYAxis && !settings.enableRotation, enabled = !settings.enableRotation) {
            actions.change(settings.copy(alignToYAxis = it))
        }
        Box(
            Modifier
                .padding(vertical = 8.dp)
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.canvasPanelSeparator),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OrcaButton(stringResource(R.string.arrange), onClick = actions.arrange, size = OrcaButtonSize.Compact)
            OrcaButton(stringResource(R.string.arrange_reset), onClick = actions.reset, size = OrcaButtonSize.Compact, style = OrcaButtonStyle.Regular)
        }
    }
}

@Composable
private fun ArrangeOption(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OrcaCheckBox(checked = checked, onCheckedChange = onChange, enabled = enabled)
        Text(
            text = label,
            color = if (enabled) OrcaTheme.colors.onCanvasPanel else OrcaTheme.colors.textDisabled,
            style = OrcaTheme.typography.body13,
        )
    }
}

/** What the rotation window does. */
internal class RotationActions(
    val rotateBy: (axis: Int, degrees: Double) -> Unit,
    val setRotation: (axis: Int, degrees: Double) -> Unit,
    val reset: () -> Unit,
    val resetToZero: () -> Unit,
)

/**
 * GizmoObjectManipulation::do_render_rotate_window(): a relative rotation per
 * axis, applied when an input is done and shown as zero again, the rotation of
 * the object per axis, and the buttons that reset the rotation to when the
 * tool opened and to none.
 */
@Composable
private fun RotateGizmoPanel(
    state: PrepareUiState,
    rotation: Vector3,
    actions: RotationActions,
    onDone: () -> Unit,
) {
    val colors = OrcaTheme.colors
    OrcaGizmoPanel {
        Text(
            text = stringResource(R.string.gizmo_world_coordinates),
            color = colors.onCanvasPanel,
            style = OrcaTheme.typography.body13,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(RotationLabelWidth))
            AxisHeaders()
        }
        GizmoValueRow(
            label = stringResource(R.string.gizmo_rotate_relative),
            values = listOf(0.0, 0.0, 0.0),
            unit = stringResource(R.string.unit_degrees),
            labelWidth = RotationLabelWidth,
            onValue = actions.rotateBy,
            reset = GizmoReset(DesignR.drawable.orca_toolbar_reset, stringResource(R.string.gizmo_reset_rotation), state.canResetRotation, actions.reset),
        )
        GizmoValueRow(
            label = stringResource(R.string.gizmo_rotate_absolute),
            values = listOf(rotation.x, rotation.y, rotation.z),
            unit = stringResource(R.string.unit_degrees),
            labelWidth = RotationLabelWidth,
            onValue = actions.setRotation,
            reset = GizmoReset(
                DesignR.drawable.orca_toolbar_reset_zero,
                stringResource(R.string.gizmo_reset_rotation_zero),
                state.canResetRotationToZero,
                actions.resetToZero,
            ),
        )
        GizmoPanelFooter(onDone)
    }
}

/** What the scale window does. */
internal class ScaleActions(
    val setScale: (axis: Int, percent: Double) -> Unit,
    val setSize: (axis: Int, millimeters: Double) -> Unit,
    val setUniform: (Boolean) -> Unit,
    val reset: () -> Unit,
)

/**
 * GizmoObjectManipulation::do_render_scale_input_window() in world
 * coordinates: scale ratios per axis with the button that resets them, the
 * size per axis, and whether scaling keeps the proportions.
 */
@Composable
private fun ScaleGizmoPanel(
    state: PrepareUiState,
    scale: Vector3,
    size: Vector3,
    actions: ScaleActions,
    onDone: () -> Unit,
) {
    val canReset = listOf(scale.x, scale.y, scale.z).let { ratios -> sqrt(ratios.sumOf { (it / 100.0 - 1.0).pow(2) }) > 0.001 }
    OrcaGizmoPanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(PositionLabelWidth))
            AxisHeaders()
        }
        GizmoValueRow(
            label = stringResource(R.string.gizmo_scale_ratio),
            values = listOf(scale.x, scale.y, scale.z),
            unit = stringResource(R.string.unit_percent),
            labelWidth = PositionLabelWidth,
            onValue = actions.setScale,
            reset = GizmoReset(DesignR.drawable.orca_toolbar_reset, stringResource(R.string.gizmo_reset_scale), canReset, actions.reset),
        )
        GizmoValueRow(
            label = stringResource(R.string.gizmo_size),
            values = listOf(size.x, size.y, size.z),
            unit = stringResource(R.string.unit_mm),
            labelWidth = PositionLabelWidth,
            onValue = actions.setSize,
            reset = GizmoReset(DesignR.drawable.orca_toolbar_reset, "", visible = false, onClick = {}),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 6.dp),
        ) {
            OrcaCheckBox(checked = state.uniformScale, onCheckedChange = actions.setUniform)
            Text(
                text = stringResource(R.string.gizmo_uniform_scale),
                color = OrcaTheme.colors.onCanvasPanel,
                style = OrcaTheme.typography.body13,
            )
        }
        GizmoPanelFooter(onDone)
    }
}

/** A reset button at the end of a gizmo window row, shown only when there is something to reset. */
private class GizmoReset(val icon: Int, val description: String, val visible: Boolean, val onClick: () -> Unit)

@Composable
private fun AxisHeaders() {
    AxisNames.forEachIndexed { axis, name ->
        Text(
            text = name,
            color = AxisColors[axis],
            style = OrcaTheme.typography.body13,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(start = 6.dp)
                .width(PositionFieldWidth),
        )
    }
}

@Composable
private fun GizmoValueRow(
    label: String,
    values: List<Double>,
    unit: String,
    labelWidth: Dp,
    onValue: (axis: Int, value: Double) -> Unit,
    reset: GizmoReset? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
        Text(
            text = label,
            color = OrcaTheme.colors.onCanvasPanel,
            style = OrcaTheme.typography.body13,
            modifier = Modifier.width(labelWidth),
        )
        for (axis in 0 until 3) {
            PositionField(
                value = values[axis],
                onValue = { onValue(axis, it) },
                modifier = Modifier
                    .padding(start = 6.dp)
                    .width(PositionFieldWidth),
            )
        }
        Text(
            text = unit,
            color = OrcaTheme.colors.textSide,
            style = OrcaTheme.typography.body13,
            modifier = Modifier
                .padding(start = 6.dp)
                .width(UnitWidth),
        )
        if (reset != null) {
            // An invisible button keeps the rows aligned, as the window does.
            Box(Modifier.size(ResetButtonSize), contentAlignment = Alignment.Center) {
                if (reset.visible) {
                    OrcaIconButton(reset.icon, reset.description, reset.onClick, tint = Color.Unspecified)
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.GizmoPanelFooter(onDone: () -> Unit) {
    Box(
        Modifier
            .padding(vertical = 8.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(OrcaTheme.colors.canvasPanelSeparator),
    )
    OrcaButton(
        text = stringResource(R.string.gizmo_done),
        onClick = onDone,
        size = OrcaButtonSize.Compact,
        modifier = Modifier.align(Alignment.End),
    )
}

/**
 * ImGui::BBLInputDouble with "%.2f": the value with two decimals, applied when
 * the input is done or loses focus. Text that is not a number is dropped.
 */
@Composable
private fun PositionField(
    value: Double,
    onValue: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shown = String.format(Locale.ROOT, "%.2f", value)
    var text by remember(shown) { mutableStateOf(shown) }
    val focusManager = LocalFocusManager.current
    fun apply() {
        text.replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() && text != shown }?.let(onValue)
        text = shown
    }
    OrcaTextField(
        value = text,
        onValueChange = { text = it },
        // The small font of OrcaSlicer's gizmo windows keeps -9999.99 visible.
        textStyle = OrcaTheme.typography.body12,
        modifier = modifier.onFocusChanged { if (!it.isFocused && text != shown) apply() },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = {
            apply()
            focusManager.clearFocus()
        }),
    )
}

private val AxisNames = listOf("X", "Y", "Z")

// ColorRGBA::X(), Y(), Z(), which the move window uses for the axis names.
private val AxisColors = listOf(Color(255, 60, 91), Color(100, 200, 24), Color(47, 136, 233))
private val CanvasMargin = 12.dp

// A gizmo window fits a phone: label, three fields, unit, and reset button in 360 dp.
private val PositionLabelWidth = 64.dp
private val RotationLabelWidth = 76.dp
private val PositionFieldWidth = 60.dp
private val UnitWidth = 24.dp
private val ResetButtonSize = 28.dp

private val PreviewArrangeActions = ArrangeActions({}, {}, {}, {})
private val PreviewRotationActions = RotationActions({ _, _ -> }, { _, _ -> }, {}, {})
private val PreviewScaleActions = ScaleActions({ _, _ -> }, { _, _ -> }, {}, {})

private val PreviewInspection = ModelInspection(
    facetCount = 12,
    dimensions = ModelDimensions(20.0, 20.0, 20.0),
    boxCenter = Vector3(0.0, 0.0, 10.0),
    mesh = ScenePath("preview.mesh"),
    placement = Transform3(List(16) { if (it % 5 == 0) 1.0 else 0.0 }),
    fit = BuildVolumeFit.INSIDE,
    boundingSphere = BoundingSphere(Vector3(0.0, 0.0, 10.0), 17.32),
    rotationDegrees = Vector3(0.0, 0.0, 0.0),
    unscaledDimensions = ModelDimensions(20.0, 20.0, 20.0),
)

private val PreviewState = PrepareUiState(
    plate = null,
    importing = false,
    plateObject = PlateObject.CalibrationCube(PreviewInspection),
    sceneObjects = listOf(PlateObject.CalibrationCube(PreviewInspection)),
    selectedObject = null,
    gizmo = null,
    flatteningPlanes = emptyList(),
    arrangeOptionsOpen = false,
    arrangeSettings = ArrangeSettings(),
    selectedPosition = null,
    selectedRotation = null,
    canResetRotation = false,
    canResetRotationToZero = false,
    selectedScale = null,
    selectedSize = null,
    uniformScale = true,
    objectClashed = false,
    slicing = PlateSlicing(SliceJobId("preview"), SliceProgress(SliceJobId("preview"), 0.45f, SliceStage.SLICING, "Generating infill toolpath")),
    problem = null,
    canEditPlate = false,
    canSlice = false,
)

@Preview(name = "Compact", widthDp = 400, heightDp = 800)
@Preview(name = "Compact dark", widthDp = 400, heightDp = 800, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PrepareCompactPreview() = OrcinusTheme {
    PrepareScreen(PreviewState, OrcaWindowLayout.Compact, {}, {}, {}, { _, _, _ -> }, { _, _ -> }, {}, {}, { _, _ -> }, {}, PreviewArrangeActions, PreviewRotationActions, PreviewScaleActions, {}, {}, {})
}

@Preview(name = "Wide", widthDp = 1000, heightDp = 640)
@Composable
private fun PrepareWidePreview() = OrcinusTheme {
    PrepareScreen(
        PreviewState.copy(
            slicing = null,
            selectedObject = 0,
            gizmo = PlateGizmo.MOVE,
            selectedPosition = ObjectPosition(175.0, 175.0, 10.0),
            canEditPlate = true,
            canSlice = true,
        ),
        OrcaWindowLayout.Wide, {}, {}, {}, { _, _, _ -> }, { _, _ -> }, {}, {}, { _, _ -> }, {}, PreviewArrangeActions, PreviewRotationActions, PreviewScaleActions, {}, {}, {},
    )
}
