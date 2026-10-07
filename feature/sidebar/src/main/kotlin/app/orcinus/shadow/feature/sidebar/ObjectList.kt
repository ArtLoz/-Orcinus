package app.orcinus.shadow.feature.sidebar

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import app.orcinus.shadow.core.model.Axis
import app.orcinus.shadow.core.model.EmbossKind
import app.orcinus.shadow.core.model.LayerRange
import app.orcinus.shadow.core.model.LayerRangeEditor
import app.orcinus.shadow.core.model.MeshErrors
import app.orcinus.shadow.core.model.ObjectPart
import app.orcinus.shadow.core.model.OrcaText
import app.orcinus.shadow.core.model.PaintKind
import app.orcinus.shadow.core.model.PresetKind
import app.orcinus.shadow.core.model.hasVariableLayerHeight
import app.orcinus.shadow.core.model.meshErrors
import app.orcinus.shadow.core.ui.orca.orcaText
import app.orcinus.shadow.core.ui.plate.SelectionMenuActions
import app.orcinus.shadow.core.ui.plate.SelectionMenuItems
import app.orcinus.shadow.core.ui.plate.VolumesMenuActions
import app.orcinus.shadow.core.ui.plate.VolumesMenuItems
import app.orcinus.shadow.core.ui.plate.volumesMenuState
import app.orcinus.shadow.core.model.isCut
import app.orcinus.shadow.core.model.hasConnectors
import app.orcinus.shadow.core.model.HandyModel
import app.orcinus.shadow.core.ui.plate.PartPlateMenuActions
import app.orcinus.shadow.core.ui.plate.PartPlateMenuItems
import app.orcinus.shadow.core.ui.plate.PartPlateMenuState
import app.orcinus.shadow.core.model.ObjectEdit
import app.orcinus.shadow.core.ui.plate.volumeName
import app.orcinus.shadow.core.ui.plate.objectMenuState
import app.orcinus.shadow.core.ui.plate.PartMenuActions
import app.orcinus.shadow.core.ui.plate.PartMenuItems
import app.orcinus.shadow.core.ui.plate.RenameItem
import app.orcinus.shadow.core.ui.plate.partMenuState
import app.orcinus.shadow.core.ui.plate.ObjectMenuItems
import app.orcinus.shadow.core.ui.plate.SetAsIndividualItem
import app.orcinus.shadow.core.ui.plate.ObjectMenuActions
import app.orcinus.shadow.core.ui.plate.ChangeFilamentItem
import app.orcinus.shadow.core.ui.plate.MenuFilament
import app.orcinus.shadow.core.ui.plate.ProcessSettingsItems
import app.orcinus.shadow.core.model.FlushOption
import app.orcinus.shadow.core.model.MeshFormat
import app.orcinus.shadow.core.model.SettingsItem
import app.orcinus.shadow.core.model.SettingsItemKind
import app.orcinus.shadow.core.model.Manipulation
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.orcinus.shadow.core.designsystem.R as DesignR
import app.orcinus.shadow.core.designsystem.component.OrcaCheckBox
import app.orcinus.shadow.core.designsystem.component.OrcaContextMenu
import app.orcinus.shadow.core.designsystem.component.OrcaFilamentSlot
import app.orcinus.shadow.core.designsystem.component.OrcaMenuItem
import app.orcinus.shadow.core.designsystem.component.OrcaMenuSeparator
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import app.orcinus.shadow.core.model.LayerRangeId
import app.orcinus.shadow.core.model.ModelSettings
import app.orcinus.shadow.core.model.ObjectPartId
import app.orcinus.shadow.core.model.PlateInstanceId
import app.orcinus.shadow.core.model.PlateObject
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.SettingDefinition
import app.orcinus.shadow.core.model.VolumeType
import app.orcinus.shadow.core.model.extruderNumber
import app.orcinus.shadow.core.model.mesh
import app.orcinus.shadow.core.model.volumeAt
import app.orcinus.shadow.core.ui.R as UiR
import app.orcinus.shadow.core.ui.displayName
import app.orcinus.shadow.core.ui.orca.orcaString
import app.orcinus.shadow.domain.plate.canMoveVolume
import app.orcinus.shadow.domain.plate.isSinking

/** What the object list asks of the app, as OrcaSlicer's object list does of the plater. */
internal class ObjectListActions(
    /** The multi-selection menu's items, which the row of a selected object offers while several are selected. */
    val selection: SelectionMenuActions? = null,
    /** The multi-selection menu's items, which the row of a selected volume offers while several of its object are selected. */
    val volumes: VolumesMenuActions? = null,
    /**
     * The row was picked: the plate, or a copy of one of its objects
     * (GLCanvas3D's Selection). While several are being picked the copy joins
     * the selection or leaves it.
     */
    val select: (PlateInstanceId?, add: Boolean) -> Unit,
    /** The row of a copy alone, which its menu and its settings row need. */
    val selectAlone: (PlateInstanceId?) -> Unit,
    /** The settings row was picked: the item's settings are shown. */
    val selectSettings: (PlateInstanceId?) -> Unit,
    /** ObjectList::toggle_printable_state(). */
    val setPrintable: (PlateInstanceId, Boolean) -> Unit,
    /** ObjectList::toggle_auto_drop(). */
    val setAutoDrop: (PlateInstanceId, Boolean) -> Unit,
    /** ObjectList::toggle_auto_drop() of an object's row: every copy. */
    val setWholeObjectAutoDrop: (ScenePath, Boolean) -> Unit = { _, _ -> },
    /** Plater::increase_instances(), decrease_instances() and set_number_of_copies(). */
    val addInstance: (ScenePath) -> Unit,
    val removeInstance: (ScenePath) -> Unit,
    val setNumberOfInstances: (ScenePath, Int) -> Unit,
    /** Selection::erase() of one copy of an object. */
    val removeCopy: (PlateInstanceId) -> Unit,
    /** Plater::fill_bed_with_instances() of an object. */
    val fillBed: (ScenePath) -> Unit,
    /** ObjectList::split_instances() of those copies of an object. */
    val setAsIndividual: (ScenePath, Set<Int>) -> Unit,
    /** MenuFactory::instance_menu() over a copy: split_instances() of the selected copies of its object, or of the copy alone. */
    val setAsIndividualOver: (PlateInstanceId) -> Unit = {},
    /** The clone dialog's OK for the whole object. */
    val clone: (ScenePath, count: Int, arrange: Boolean) -> Unit,
    /** Cut and Copy of copies of objects, or of volumes of an object over a copy; Paste over a copy. */
    val copyObjects: (Set<PlateInstanceId>, cut: Boolean) -> Unit,
    val copyVolumes: (PlateInstanceId, Set<Int>, cut: Boolean) -> Unit,
    val paste: (PlateInstanceId) -> Unit,
    /** The object menu's Center, Drop and Mirror of a copy. */
    val manipulate: (PlateInstanceId, Manipulation) -> Unit,
    /** ObjectList::rename_item() of an object and of a volume. */
    val rename: (ScenePath, String) -> Unit,
    val renamePart: (ObjectPartId, String) -> Unit,
    /** The commands that change the meshes of an object, or of its volume at an index. */
    val editObject: (ScenePath, ObjectEdit, Int?) -> Unit,
    /** ObjectList::load_generic_subobject(): a shape joins the object. */
    val addPart: (ScenePath, shape: String, type: VolumeType, name: String) -> Unit,
    /** ObjectList::load_subobject(): opens the file picker for volumes of the kind. */
    val loadPart: (ScenePath, VolumeType) -> Unit = { _, _ -> },
    /** ObjectList::del_subobject_item(): the part leaves the object. */
    val removePart: (ObjectPartId) -> Unit,
    /** ObjectList::part_selection_changed(): the parameter panel edits the part. */
    val selectPart: (ObjectPartId?) -> Unit,
    /**
     * A row picked while several items are being picked, as a Ctrl-click
     * (ObjectList::fix_multiselection_conflicts()): a copy's row ([copyRow])
     * or its object's, a volume's, a height range's.
     */
    val pickCopy: (PlateInstanceId, copyRow: Boolean) -> Unit = { _, _ -> },
    val pickPart: (ObjectPartId) -> Unit = {},
    val pickRange: (LayerRangeId) -> Unit = {},
    val selectPartSettings: (ObjectPartId) -> Unit,
    /** ObjectList::layers_editing() and add_layer_range_after_current(). */
    val addRange: (ScenePath, after: LayerRangeId?) -> Unit,
    val removeRange: (LayerRangeId) -> Unit,
    /** ObjectList::remove() of the selected ranges, and del_layers_from_object() of the "Layers" row. */
    val removeSelectedRanges: () -> Unit = {},
    val removeAllRanges: (ScenePath) -> Unit = {},
    val selectRange: (LayerRangeId?) -> Unit,
    val selectRangeSettings: (LayerRangeId) -> Unit,
    /**
     * The settings icon of a row (ObjectList's colEditing): the item's own
     * settings are reset (TabPrintModel / TabPrintLayer / TabPrintPlate::reset_model_config())
     * on the tab of [PresetKind] the row's item is opened in first.
     */
    val resetSettings: (PresetKind) -> Unit = {},
    /** ObjectList::edit_layer_range(): the range spans other heights. */
    val editRange: (LayerRangeId, bottom: Double, top: Double) -> Unit,
    /** The "Cut connectors" item: picked (its connectors selected), deleted, given a filament. */
    val selectConnectors: (PlateInstanceId) -> Unit = {},
    val deleteConnectors: (ScenePath) -> Unit = {},
    val setConnectorsExtruder: (ScenePath, Int) -> Unit = { _, _ -> },
    /** ObjectList::set_extruder_for_selected_items(): the item prints with that filament. */
    val setObjectExtruder: (ScenePath, Int) -> Unit,
    val setPartExtruder: (ObjectPartId, Int) -> Unit,
    val setRangeExtruder: (LayerRangeId, Int) -> Unit,
    /** Plater::remove_selected(). */
    val delete: (ScenePath) -> Unit,
    /** ObjectList::toggle_printable_state() of an object's row: every copy of it, as one step. */
    val setObjectPrintable: (ScenePath, Boolean) -> Unit = { _, _ -> },
    /** The object menu's Flush Options. */
    val toggleFlushOption: (ScenePath, FlushOption) -> Unit = { _, _ -> },
    /** switch_to_object_process(), copy_settings_to_clipboard() and paste_settings_into_list() of an item. */
    val editProcessSettings: (SettingsItem) -> Unit = {},
    val copyProcessSettings: (SettingsItem) -> Unit = {},
    /** "Edit in Parameter Table" of an item (Plater::PopupObjectTableBySelection): the table opens on its row. */
    val editInParameterTable: (SettingsItem) -> Unit = {},
    /** ObjectList::can_drop() of an object onto another, and ObjectList::OnDrop() of an object and of a volume. */
    val canMoveObject: (from: ScenePath, to: ScenePath) -> Boolean = { _, _ -> false },
    val moveObject: (from: ScenePath, to: ScenePath) -> Unit = { _, _ -> },
    val moveVolume: (ScenePath, from: Int, to: Int) -> Unit = { _, _, _ -> },
    /** LayerRangeEditor's wxEVT_SET_FOCUS: a field of the selected range takes the focus. */
    val focusRangeField: (LayerRangeEditor) -> Unit = {},
    /** ObjectList::copy_layers_to_clipboard() of a range row, of the selected ones, and of the "Layers" row. */
    val copyRange: (LayerRangeId) -> Unit = {},
    val copySelectedRanges: () -> Unit = {},
    val copyRanges: (ScenePath) -> Unit = {},
    val pasteProcessSettings: (SettingsItem) -> Unit = {},
    /** Opens the document picker for the new mesh of a volume of an object (Plater::replace_with_stl). */
    val replaceVolume: (PlateInstanceId, volume: Int) -> Unit = { _, _ -> },
    /** Opens the folder picker whose files replace the volumes of an object (Plater::replace_all_with_stl). */
    val replaceAllVolumes: (PlateInstanceId) -> Unit = {},
    /** Plater::reload_from_disk() of the object's volumes, or of its volume at an index. */
    val reloadFromDisk: (ScenePath, volume: Int?) -> Unit = { _, _ -> },
    /** The plate menu's "Reload All" (Plater::reload_all_from_disk()). */
    val reloadAll: () -> Unit = {},
    /** Opens where an object is exported to (Plater::export_stl), suggesting a file named after it. */
    val exportObject: (ScenePath, MeshFormat, name: String) -> Unit = { _, _, _ -> },
    /** ObjectList::simplify() of the whole object, or of one of its volumes. */
    val simplifyObject: (ScenePath) -> Unit = {},
    val simplifyVolume: (ObjectPartId) -> Unit = {},
    /** create_bbl_part_menu()'s Center, Drop and Mirror of a volume, over the copy the list picks it on. */
    val centerVolume: (ObjectPartId) -> Unit = {},
    val dropVolume: (ObjectPartId) -> Unit = {},
    val mirrorVolume: (ObjectPartId, Axis) -> Unit = { _, _ -> },
    /** ObjectList::set_volume_type(): the volume takes another type. */
    val changeVolumeType: (ObjectPartId, VolumeType) -> Unit = { _, _ -> },
    /** A plate item: the plate becomes current and nothing stays selected (ObjectList::selection_changed). */
    val selectPlate: (Int) -> Unit = {},
    /** The settings row of a plate: the plate becomes current and the panel edits its settings. */
    val selectPlateSettings: (Int) -> Unit = {},
    /** The plate menu (MenuFactory::create_plate_menu), whose items act on the current plate. */
    val selectPlateObjects: () -> Unit = {},
    val selectAllPlates: () -> Unit = {},
    val deletePlateObjects: () -> Unit = {},
    val arrangePlate: (Int) -> Unit = {},
    val orientPlate: (Int) -> Unit = {},
    val deletePlate: (Int) -> Unit = {},
    val lockPlate: (Int) -> Unit = {},
    val renamePlate: (Int, String) -> Unit = { _, _ -> },
    val addPrimitive: (shape: String, name: String) -> Unit = { _, _ -> },
    val addHandyModel: (HandyModel) -> Unit = {},
    /** "Text" and "SVG" of the plate menu's "Add Primitive": the canvas makes an object of its own of them. */
    val addTextObject: () -> Unit = {},
    val addSvgObject: () -> Unit = {},
    /** Opens the document picker of Add Models (Plater::add_file). */
    val addModels: () -> Unit = {},
    /** Opens the folder picker whose files replace the volumes of the plate's objects (Plater::replace_all_with_stl). */
    val replaceAllOnPlate: (Int) -> Unit = {},
    /** ObjectList::invalidate_cut_info_for_selection() of an object. */
    val invalidateCutInfo: (ScenePath) -> Unit = {},
    /** ObjectList::enable_layers_editing(): the variable layer height opens on the object. */
    val editLayers: (ScenePath) -> Unit = {},
    /** The paint columns' click (ObjectList::list_manipulation()): the painting tool of the kind opens on the object, or closes. */
    val paint: (ScenePath, PaintKind) -> Unit = { _, _ -> },
    /** The sinking column's click: "Shift objects to bed" of the object. */
    val shiftToBed: (ScenePath) -> Unit = {},
    /** wxEVT_DATAVIEW_ITEM_ACTIVATED of an object's, a volume's or a copy's row: the 3D view frames the selection. */
    val zoomToSelection: () -> Unit = {},
    /** append_menu_item_edit_text(): the canvas's text tool opens on the volume. */
    val editText: (ObjectPartId) -> Unit = {},
    /** "Text" of "Add part", "Add negative part" or "Add modifier": the canvas places a text on the object. */
    val addText: (ScenePath, VolumeType) -> Unit = { _, _ -> },
    /** append_menu_item_edit_svg(): the canvas's SVG tool opens on the volume. */
    val editSvg: (ObjectPartId) -> Unit = {},
    /** "SVG" of the same submenus: the canvas places an SVG on the object, once a file is picked. */
    val addSvg: (ScenePath, VolumeType) -> Unit = { _, _ -> },
)

/** An item the user renames: an object or one of its volumes, with the name it has. */
internal sealed interface RenameRequest {
    val name: String

    data class Object(val mesh: ScenePath, override val name: String) : RenameRequest

    data class Volume(val id: ObjectPartId, override val name: String) : RenameRequest

    /** "Edit Plate Name" of a plate, which PlateNameEditDialog asks for. */
    data class Plate(val index: Int, override val name: String) : RenameRequest
}

/**
 * The keys of the object list's rows, which its LazyColumn items go by and its
 * folded containers are known by.
 */
internal object ObjectListKeys {
    const val OUTSIDE = "objects:outside"

    fun plate(index: Int) = "objects:plate:$index"

    fun objectRow(mesh: ScenePath) = "objects:${mesh.value}"

    fun connectors(mesh: ScenePath) = "objects:${mesh.value}:cut-connectors"

    fun part(id: ObjectPartId) = "objects:${id.mesh.value}:part:${id.index}"

    fun layers(mesh: ScenePath) = "objects:${mesh.value}:layers"

    fun range(id: LayerRangeId) = "objects:${id.mesh.value}:layer:${id.index}"

    fun instances(mesh: ScenePath) = "objects:${mesh.value}:instances"

    fun copy(id: PlateInstanceId) = "objects:${id.mesh.value}:${id.instance}"
}

/**
 * The tree of the object list (wxDataViewCtrl): which of its containers are
 * folded — a plate, "Outside", an object, its "Instances" and its "Layers" —
 * and where its rows stand in the sidebar's list as it was laid out last,
 * which ObjectList::ensure_current_item_visible() scrolls by. A container the
 * list shows anew is unfolded, as ObjectList expands the items it adds.
 */
@Stable
internal class ObjectListTree(collapsed: Set<String> = emptySet()) {
    var collapsed by mutableStateOf(collapsed)
        private set

    // The items the sidebar's list was given so far, and the index of each row of the object list.
    private var items = 0
    private val rows = HashMap<String, Int>()

    /** wxDataViewCtrl's expander of the container [key]. */
    fun toggle(key: String) {
        collapsed = if (key in collapsed) collapsed - key else collapsed + key
    }

    /** wxDataViewCtrl::ExpandAncestors() of the selected rows: their containers unfold. */
    fun expand(keys: Collection<String>) {
        if (keys.any { it in collapsed }) collapsed = collapsed - keys.toSet()
    }

    /** The sidebar's list, whose items are counted as they are given, so that the rows of the object list know their index. */
    fun counted(content: LazyListScope.() -> Unit): LazyListScope.() -> Unit = {
        items = 0
        rows.clear()
        CountingListScope(this).content()
    }

    internal fun add(key: String) {
        rows[key] = items
    }

    private inner class CountingListScope(private val scope: LazyListScope) : LazyListScope by scope {
        override fun item(key: Any?, contentType: Any?, content: @Composable LazyItemScope.() -> Unit) {
            items++
            scope.item(key, contentType, content)
        }

        override fun items(count: Int, key: ((index: Int) -> Any)?, contentType: (index: Int) -> Any?, itemContent: @Composable LazyItemScope.(index: Int) -> Unit) {
            items += count
            scope.items(count, key, contentType, itemContent)
        }
    }

    /** ObjectList::ensure_current_item_visible(): the sidebar scrolls the least it takes for the row of [key] to show whole. */
    suspend fun reveal(listState: LazyListState, key: String) {
        val index = rows[key] ?: return
        val layout = listState.layoutInfo
        val start = layout.viewportStartOffset
        val end = layout.viewportEndOffset
        val shown = layout.visibleItemsInfo.firstOrNull { it.index == index }
        when {
            shown == null -> {
                val above = index < (layout.visibleItemsInfo.firstOrNull()?.index ?: 0)
                listState.scrollToItem(index)
                if (!above) {
                    val size = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }?.size ?: 0
                    listState.scrollBy(-(end - start - size).toFloat())
                }
            }
            shown.offset < start -> listState.scrollBy((shown.offset - start).toFloat())
            shown.offset + shown.size > end -> listState.scrollBy((shown.offset + shown.size - end).toFloat())
        }
    }

    companion object {
        val Saver: Saver<ObjectListTree, ArrayList<String>> = Saver(save = { ArrayList(it.collapsed) }, restore = { ObjectListTree(it.toSet()) })
    }
}

@Composable
internal fun rememberObjectListTree(): ObjectListTree = rememberSaveable(saver = ObjectListTree.Saver) { ObjectListTree() }

/**
 * A row of the object list and the containers it hangs under, which the list
 * unfolds to show it (wxDataViewCtrl::Select() expands the ancestors of an
 * item it selects).
 */
internal data class ObjectListRowPath(val key: String, val ancestors: List<String>)

/** The plate row the object with the [mesh] file hangs under, or "Outside". */
private fun SidebarUiState.plateKeyOf(mesh: ScenePath): String =
    plates.firstOrNull { plate -> plate.objects.any { it.mesh == mesh } }?.let { ObjectListKeys.plate(it.index) } ?: ObjectListKeys.OUTSIDE

/** The row of [target] and its containers. */
internal fun SidebarUiState.rowOf(target: ObjectListTarget): ObjectListRowPath = when (target) {
    is ObjectListTarget.Plate -> ObjectListRowPath(ObjectListKeys.plate(target.index), emptyList())
    ObjectListTarget.Outside -> ObjectListRowPath(ObjectListKeys.OUTSIDE, emptyList())
    is ObjectListTarget.Object -> ObjectListRowPath(ObjectListKeys.objectRow(target.copy.mesh), listOf(plateKeyOf(target.copy.mesh)))
    is ObjectListTarget.Connectors -> childRow(target.copy.mesh, ObjectListKeys.connectors(target.copy.mesh))
    is ObjectListTarget.Part -> childRow(target.id.mesh, ObjectListKeys.part(target.id))
    is ObjectListTarget.Layers -> childRow(target.mesh, ObjectListKeys.layers(target.mesh))
    is ObjectListTarget.Range -> childRow(target.id.mesh, ObjectListKeys.range(target.id), ObjectListKeys.layers(target.id.mesh))
    is ObjectListTarget.Instances -> childRow(target.mesh, ObjectListKeys.instances(target.mesh))
    is ObjectListTarget.Copy -> childRow(target.id.mesh, ObjectListKeys.copy(target.id), ObjectListKeys.instances(target.id.mesh))
}

private fun SidebarUiState.childRow(mesh: ScenePath, key: String, vararg containers: String) =
    ObjectListRowPath(key, listOf(plateKeyOf(mesh), ObjectListKeys.objectRow(mesh)) + containers)

/**
 * ObjectList::update_selections(): the rows of what the plate has selected
 * — a range, a volume, the connectors, or copies, each by its own row when its
 * object has several and by the object's otherwise — the current one last,
 * which the list then keeps in view (ensure_current_item_visible()); nothing
 * while nothing is selected. The object's row picks its first copy, so that
 * copy alone stands for the object's row.
 */
internal fun SidebarUiState.selectionRows(): List<ObjectListRowPath> {
    selectedRange?.let { current -> return (selectedRanges - current + current).map { rowOf(ObjectListTarget.Range(it)) } }
    selectedPart?.let { current -> return (selectedParts - current + current).map { rowOf(ObjectListTarget.Part(it)) } }
    selectedConnectors?.let { return listOf(rowOf(ObjectListTarget.Connectors(it))) }
    return selectedInstances.map { id ->
        val copies = objects.firstOrNull { it.mesh == id.mesh }?.instances?.size ?: 1
        val alone = id.instance == 0 && selectedInstances.none { it.mesh == id.mesh && it.instance != 0 }
        rowOf(if (copies > 1 && !alone) ObjectListTarget.Copy(id) else ObjectListTarget.Object(id))
    }
}

/**
 * OrcaSlicer's object list (GUI_ObjectList, ObjectDataViewModel) in the
 * sidebar: every plate, the objects under it, and "Outside" with the objects
 * that stand on no plate whole, as the desktop app lists them
 * (ObjectList::reload_all_plates), each container folding its rows away. An
 * item with settings of its own has the lock of its editing column
 * (ObjectDataViewModelNode::set_action_icon(), NEW_OBJECT_SETTING).
 *
 * The desktop tree has a column per property; a row on a phone has room for
 * the name, the marks of what the object carries (its variable layer height,
 * its paint, its sinking), the filament, the lock, and the check box of
 * ModelInstance::printable, and its menu is the object menu of the 3D view
 * (MenuFactory). The sidebar's list counts its items ([ObjectListTree.counted]).
 */
internal fun LazyListScope.objectListItems(
    state: SidebarUiState,
    enabled: Boolean,
    /** Plater::is_preview_shown(): show_context_menu() opens no menu, the sinking column takes no click, no row frames the selection. */
    preview: Boolean,
    tree: ObjectListTree,
    /** The drag of a row onto another, which the list's rows share. */
    drag: ObjectListDrag,
    /** Several objects are being picked: a tap adds the object or takes it out. */
    picking: Boolean,
    /** The colour of every filament of the plate, which the filament column shows. */
    filaments: List<Color>,
    /** Every filament of the plate with its preset, which "Change Filament" lists. */
    menuFilaments: List<MenuFilament>,
    /** The kinds of paint on each object, by its mesh (ObjectList::update_info_items()). */
    paintedKinds: Map<ScenePath, Set<PaintKind>>,
    actions: ObjectListActions,
    /** A part of that kind is being added to the object: the sidebar asks which shape. */
    onChooseShape: (ScenePath, VolumeType) -> Unit,
    /** The heights of that range are being edited: the sidebar asks for them. */
    onEditRange: (LayerRangeId) -> Unit,
    /** The object's number of copies is being set: the sidebar asks for it. */
    onAskNumberOfInstances: (ScenePath) -> Unit,
    /** The object is being cloned: the sidebar asks how. */
    onAskClone: (ScenePath) -> Unit,
    /** An item is being renamed: the sidebar asks for the name. */
    onAskRename: (RenameRequest) -> Unit,
) {
    // ObjectList::OnBeginDrag() vetoes a multiple selection; can_drop() and OnDrop().
    drag.enabled = enabled && !picking && state.selectedInstances.size <= 1
    drag.canDrop = { row, target ->
        when {
            row is DragRow.Object && target is DragRow.Object -> actions.canMoveObject(row.mesh, target.mesh)
            row is DragRow.Volume && target is DragRow.Volume && row.mesh == target.mesh ->
                state.objects.firstOrNull { it.mesh == row.mesh }?.canMoveVolume(row.index, target.index) == true
            else -> false
        }
    }
    drag.onDrop = { row, target ->
        if (row is DragRow.Object && target is DragRow.Object) actions.moveObject(row.mesh, target.mesh)
        if (row is DragRow.Volume && target is DragRow.Volume) actions.moveVolume(row.mesh, row.index, target.index)
    }
    val list = ObjectListContext(
        state = state,
        enabled = enabled,
        preview = preview,
        tree = tree,
        picking = picking,
        filaments = filaments,
        menuFilaments = menuFilaments,
        paintedKinds = paintedKinds,
        // ObjectList::update_info_items(): a column shows while an object needs it.
        columns = ObjectColumns(
            height = state.objects.any { it.hasVariableLayerHeight },
            support = paintedKinds.values.any { PaintKind.SUPPORTS in it },
            color = paintedKinds.values.any { PaintKind.COLOR in it },
            sinking = state.objects.any { it.isSinking },
        ),
        actions = actions,
        drag = drag,
        onChooseShape = onChooseShape,
        onEditRange = onEditRange,
        onAskNumberOfInstances = onAskNumberOfInstances,
        onAskClone = onAskClone,
        onAskRename = onAskRename,
    )
    val plateDefinitions = state.plateSettings.tab?.definitions.orEmpty()

    state.plates.forEach { plate ->
        val index = plate.index
        val key = ObjectListKeys.plate(index)
        val expanded = key !in tree.collapsed
        listRow(tree, key) {
            ObjectListRow(
                name = plate.itemName(),
                icons = listOf(DesignR.drawable.orca_plate_settings),
                iconTint = OrcaTheme.colors.textSide,
                depth = 0,
                expanded = expanded.takeIf { plate.objects.isNotEmpty() },
                onExpand = { tree.toggle(key) },
                selected = index == state.currentPlate && state.selectedInstances.isEmpty(),
                hasSettings = plate.overrides.categories(plateDefinitions).isNotEmpty(),
                onResetSettings = {
                    actions.selectPlateSettings(plate.index)
                    actions.resetSettings(PresetKind.PLATE)
                },
                enabled = enabled,
                onClick = { actions.selectPlate(index) },
                // The menu acts on the current plate, which the plate becomes first.
                onLongClick = { actions.selectPlate(index) },
                menu = if (preview) null else { dismiss -> PlateItemMenu(plate, state, enabled, actions, dismiss, onAskRename) },
            )
        }
        if (expanded) objectRows(list, plate.objects)
    }
    // The outside plate, which the list always has.
    val outsideExpanded = ObjectListKeys.OUTSIDE !in tree.collapsed
    listRow(tree, ObjectListKeys.OUTSIDE) {
        ObjectListRow(
            name = orcaString("Outside"),
            icons = listOf(DesignR.drawable.orca_plate_settings),
            iconTint = OrcaTheme.colors.textSide,
            depth = 0,
            expanded = outsideExpanded.takeIf { state.outsideObjects.isNotEmpty() },
            onExpand = { tree.toggle(ObjectListKeys.OUTSIDE) },
            selected = false,
            hasSettings = false,
            onClick = {},
        )
    }
    if (outsideExpanded) objectRows(list, state.outsideObjects)
}

/** A row of the list, which the tree counts as it lays the rows out. */
private fun LazyListScope.listRow(tree: ObjectListTree, key: String, content: @Composable LazyItemScope.() -> Unit) {
    tree.add(key)
    item(key = key, content = content)
}

/** The columns of an object's row (colHeight, colSupportPaint, colColorPaint, colSinking) the list shows. */
private data class ObjectColumns(val height: Boolean, val support: Boolean, val color: Boolean, val sinking: Boolean)

/** What the rows of the objects are laid out with. */
private class ObjectListContext(
    val state: SidebarUiState,
    val enabled: Boolean,
    val preview: Boolean,
    val tree: ObjectListTree,
    val picking: Boolean,
    val filaments: List<Color>,
    val menuFilaments: List<MenuFilament>,
    val paintedKinds: Map<ScenePath, Set<PaintKind>>,
    val columns: ObjectColumns,
    val actions: ObjectListActions,
    val drag: ObjectListDrag,
    val onChooseShape: (ScenePath, VolumeType) -> Unit,
    val onEditRange: (LayerRangeId) -> Unit,
    val onAskNumberOfInstances: (ScenePath) -> Unit,
    val onAskClone: (ScenePath) -> Unit,
    val onAskRename: (RenameRequest) -> Unit,
) {
    val objectDefinitions = (state.objectSettings.tab ?: state.processSettings.tab)?.definitions.orEmpty()
    val partDefinitions = (state.partSettings.tab ?: state.processSettings.tab)?.definitions.orEmpty()
    val rangeDefinitions = (state.rangeSettings.tab ?: state.processSettings.tab)?.definitions.orEmpty()

    /** The process preset's layer height, which a range's own equal to its object's leaves no lock for. */
    val processLayerHeight = state.processSettings.settings?.settings?.firstOrNull { it.key == LAYER_HEIGHT }?.value

    /** A row's double tap frames the selection, but in Preview and while rows are picked. */
    val activate: (() -> Unit)? = actions.zoomToSelection.takeUnless { preview || picking }
}

/** The menu of a plate item, which a finger held on the plate in the 3D view opens too. */
@Composable
private fun PlateItemMenu(
    plate: ObjectListPlate,
    state: SidebarUiState,
    enabled: Boolean,
    actions: ObjectListActions,
    dismiss: () -> Unit,
    onAskRename: (RenameRequest) -> Unit,
) {
    PartPlateMenuItems(
        state = PartPlateMenuState(
            index = plate.index,
            current = enabled && plate.index == state.currentPlate,
            occupied = plate.occupied,
            locked = plate.locked,
            deletable = state.canDeletePlate,
            anyObjects = state.objects.isNotEmpty(),
            enabled = enabled,
        ),
        actions = PartPlateMenuActions(
            selectPlateObjects = actions.selectPlateObjects,
            selectAllPlates = actions.selectAllPlates,
            deletePlateObjects = actions.deletePlateObjects,
            arrangePlate = actions.arrangePlate,
            reloadAll = actions.reloadAll,
            orientPlate = actions.orientPlate,
            deletePlate = actions.deletePlate,
            addPrimitive = actions.addPrimitive,
            addHandyModel = actions.addHandyModel,
            addModels = actions.addModels,
            replaceAllOnPlate = actions.replaceAllOnPlate,
            lockPlate = actions.lockPlate,
            rename = { index -> onAskRename(RenameRequest.Plate(index, plate.name)) },
            addText = actions.addTextObject,
            addSvg = actions.addSvgObject,
        ),
        dismiss = dismiss,
    )
}

/** The rows of [objects], each with the rows that hang under it while it is unfolded. */
private fun LazyListScope.objectRows(list: ObjectListContext, objects: List<PlateObject>) {
    val state = list.state
    val actions = list.actions
    val enabled = list.enabled
    val tree = list.tree
    val picking = list.picking
    objects.forEach { plateObject ->
        val mesh = plateObject.mesh
        val copies = plateObject.instances.size > 1
        val ids = plateObject.instances.indices.map { PlateInstanceId(mesh, it) }
        val selectionMenu = state.selectionMenu
        val inSelection = ids.any { it in state.selectedInstances }
        // A volume, a range or the connectors of the object take the highlight in its place.
        val itemsSelected = state.selectedPart != null || state.selectedRange != null || state.selectedConnectors != null
        // ObjectList::update_info_items(): a part of a cut with connectors has their item.
        val connectors = plateObject.isCut && plateObject.hasConnectors && plateObject.parts.isNotEmpty()
        val volumes = plateObject.listsVolumes()
        val key = ObjectListKeys.objectRow(mesh)
        val expanded = key !in tree.collapsed
        listRow(tree, key) {
            ObjectListRow(
                name = plateObject.displayName(),
                // ObjectDataViewModel::AddObject(): a part of a cut has the lock.
                icons = listOfNotNull(DesignR.drawable.orca_cut_.takeIf { plateObject.isCut }),
                depth = 1,
                expanded = expanded.takeIf { connectors || volumes || plateObject.layerRanges.isNotEmpty() || copies },
                onExpand = { tree.toggle(key) },
                // ObjectList::update_item_error_icon() of the object's stats; a tap repairs it.
                warning = meshWarning(plateObject.meshErrors.copy(openEdges = plateObject.instances.first().inspection.openEdges)) {
                    actions.selectAlone(ids.first())
                    actions.editObject(mesh, ObjectEdit.FIX, null)
                },
                selected = inSelection && !itemsSelected,
                hasSettings = plateObject.settings.categories(list.objectDefinitions).isNotEmpty(),
                onResetSettings = {
                    actions.selectSettings(ids.first())
                    actions.resetSettings(PresetKind.OBJECT)
                },
                // The eye of an object switches every copy of it, and shows
                // the middle state while they differ.
                printable = plateObject.instances.map { it.printable }.distinct().singleOrNull(),
                showPrintable = true,
                enabled = enabled,
                // The column is only there when the printer prints with
                // several filaments (ObjectList::set_filament_column_hidden).
                extruder = list.filaments.takeIf { it.size > 1 }?.let { colors ->
                    ExtruderColumn(plateObject.extruderNumber, colors, allowDefault = false) {
                        actions.setObjectExtruder(mesh, it)
                    }
                },
                columns = list.columnsOf(plateObject),
                onClick = { if (picking) actions.pickCopy(ids.first(), false) else actions.select(ids.first(), false) },
                onDoubleClick = list.activate,
                onPrintable = { printable -> actions.setObjectPrintable(mesh, printable) },
                // ObjectList::show_context_menu(): a row of a selection of several objects keeps it.
                onLongClick = { if (selectionMenu == null || !inSelection) actions.selectAlone(ids.first()) },
                drag = RowDrag(DragRow.Object(mesh), list.drag),
                menu = list.menu { dismiss ->
                    val selectionActions = actions.selection
                    if (selectionMenu != null && inSelection && selectionActions != null) {
                        SelectionMenuItems(selectionMenu.copy(filaments = list.menuFilaments.takeIf { it.size > 1 }.orEmpty()), selectionActions, dismiss)
                        return@menu
                    }
                    val name = plateObject.displayName()
                    // ObjectList's in-place rename, which a phone offers from the row's menu.
                    RenameItem(enabled) {
                        dismiss()
                        list.onAskRename(RenameRequest.Object(mesh, name))
                    }
                    // The row selects the whole object (Selection::add_object).
                    ObjectMenuItems(
                        objectMenuState(
                            plateObject,
                            plateObject.instances.first(),
                            state.plate,
                            enabled,
                            wholeObject = true,
                            clipboard = state.clipboard,
                            simplifying = state.simplifying,
                            filaments = list.menuFilaments,
                            flushing = state.flushing,
                            settingsClipboard = state.settingsClipboard,
                            listClipboard = state.listClipboard,
                        ),
                        actions.menuOf(
                            id = ids.first(),
                            name = name,
                            copies = plateObject.instances.indices.toSet(),
                            delete = { actions.delete(mesh) },
                            invalidateCutInfo = { actions.invalidateCutInfo(mesh) },
                            // can_edit_text(): the row selects every copy, so one copy of an object of a text alone.
                            editText = ObjectPartId(mesh, 0)
                                .takeIf { plateObject.instances.size == 1 && plateObject.parts.isEmpty() && plateObject.volume.emboss?.kind == EmbossKind.TEXT }
                                ?.let { id -> { actions.editText(id) } },
                            // can_edit_svg(), as can_edit_text().
                            editSvg = ObjectPartId(mesh, 0)
                                .takeIf { plateObject.instances.size == 1 && plateObject.parts.isEmpty() && plateObject.volume.emboss?.kind == EmbossKind.SVG }
                                ?.let { id -> { actions.editSvg(id) } },
                            onChooseShape = list.onChooseShape,
                            onAskNumberOfInstances = list.onAskNumberOfInstances,
                            onAskClone = list.onAskClone,
                        ),
                        dismiss,
                    )
                },
            )
        }
        if (!expanded) return@forEach
        // Selecting it selects the connectors; its menu deletes them
        // (del_info_item()) or gives them a filament, as the desktop's Delete
        // key and the selection's menu do.
        if (connectors) listRow(tree, ObjectListKeys.connectors(mesh)) {
            ObjectListRow(
                name = orcaString("Cut connectors"),
                icons = listOf(DesignR.drawable.orca_cut_connectors),
                depth = 2,
                selected = state.selectedConnectors?.mesh == mesh,
                hasSettings = false,
                enabled = enabled,
                onClick = { actions.selectConnectors(ids.first()) },
                onLongClick = { actions.selectConnectors(ids.first()) },
                menu = list.menu { dismiss ->
                    OrcaMenuItem(
                        text = orcaString("Delete"),
                        enabled = enabled,
                        onClick = {
                            dismiss()
                            actions.deleteConnectors(mesh)
                        },
                    )
                    ChangeFilamentItem(
                        list.menuFilaments,
                        withDefault = false,
                        enabled = enabled,
                        onPick = { filament ->
                            dismiss()
                            actions.setConnectorsExtruder(mesh, filament)
                        },
                    )
                },
            )
        }
        // ObjectList::add_volumes_to_object_in_list(): once the object has
        // parts, every volume has a row, its own mesh first, but for the
        // connectors of a cut.
        if (volumes) (0..plateObject.parts.size).forEach { at ->
            val part = plateObject.volumeAt(at) ?: return@forEach
            if (plateObject.isCut && part.cutInfo.connector) return@forEach
            volumeRow(list, plateObject, part, ObjectPartId(mesh, at))
        }
        if (plateObject.layerRanges.isNotEmpty()) {
            // ObjectDataViewModel::AddLayersRoot(): the ranges hang under a row of their own.
            val layersKey = ObjectListKeys.layers(mesh)
            val layersExpanded = layersKey !in tree.collapsed
            listRow(tree, layersKey) {
                ObjectListRow(
                    name = orcaString("Layers"),
                    icons = listOf(DesignR.drawable.orca_height_range_modifier),
                    depth = 2,
                    expanded = layersExpanded,
                    onExpand = { tree.toggle(layersKey) },
                    selected = false,
                    hasSettings = false,
                    enabled = enabled,
                    onClick = { if (!picking) actions.addRange(mesh, null) },
                    // The desktop list copies its ranges and deletes them with the
                    // keyboard (copy_layers_to_clipboard(), del_layers_from_object()),
                    // which a phone offers from the row's menu.
                    menu = list.menu { dismiss ->
                        OrcaMenuItem(
                            text = orcaString("Copy"),
                            enabled = enabled,
                            onClick = {
                                dismiss()
                                actions.copyRanges(mesh)
                            },
                        )
                        OrcaMenuItem(
                            text = stringResource(UiR.string.object_menu_delete),
                            enabled = enabled,
                            onClick = {
                                dismiss()
                                actions.removeAllRanges(mesh)
                            },
                        )
                    },
                )
            }
            if (layersExpanded) plateObject.layerRanges.forEachIndexed { at, range -> rangeRow(list, plateObject, range, LayerRangeId(mesh, at)) }
        }
        // ObjectDataViewModel::AddInstanceChild(): the copies of an object hang
        // under "Instances"; one copy needs no row.
        if (copies) {
            val instancesKey = ObjectListKeys.instances(mesh)
            val instancesExpanded = instancesKey !in tree.collapsed
            listRow(tree, instancesKey) {
                ObjectListRow(
                    name = stringResource(R.string.object_instances),
                    icons = emptyList(),
                    depth = 2,
                    expanded = instancesExpanded,
                    onExpand = { tree.toggle(instancesKey) },
                    selected = false,
                    hasSettings = false,
                    enabled = enabled,
                    // update_selections_on_canvas() of the row: its object.
                    onClick = { if (picking) actions.pickCopy(ids.first(), false) else actions.select(ids.first(), false) },
                )
            }
            if (instancesExpanded) plateObject.instances.indices.forEach { index -> copyRow(list, plateObject, ids[index]) }
        }
    }
}

/** The row of a volume of [plateObject]: its own mesh, or a part. */
private fun LazyListScope.volumeRow(list: ObjectListContext, plateObject: PlateObject, part: ObjectPart, partId: ObjectPartId) {
    val state = list.state
    val actions = list.actions
    val enabled = list.enabled
    val mesh = partId.mesh
    val at = partId.index
    listRow(list.tree, ObjectListKeys.part(partId)) {
        ObjectListRow(
            name = plateObject.volumeName(at),
            icons = volumeIcons(plateObject, part),
            depth = 2,
            warning = meshWarning(part.meshErrors) {
                actions.selectPart(partId)
                actions.editObject(mesh, ObjectEdit.FIX, at)
            },
            drag = RowDrag(DragRow.Volume(mesh, at), list.drag),
            selected = partId in state.selectedParts,
            // ObjectList::add_settings_item(): a negative volume and a support blocker or enforcer take no settings.
            hasSettings = (part.type == VolumeType.PART || part.type == VolumeType.MODIFIER) && part.settings.categories(list.partDefinitions).isNotEmpty(),
            onResetSettings = {
                actions.selectPartSettings(partId)
                actions.resetSettings(PresetKind.PART)
            },
            enabled = enabled,
            extruder = list.filaments
                .takeIf { it.size > 1 && (part.type == VolumeType.PART || part.type == VolumeType.MODIFIER) }
                ?.let { colors ->
                    // UpdateExtruderAndColorIcon(): a part of the model without a
                    // filament of its own shows its object's, and its combo box
                    // offers no "default", which a modifier alone has
                    // (set_has_default_extruder(), GetDefaultExtruderIdx()).
                    val own = part.settings.extruderNumber
                    val shown = if (own == 0 && part.type == VolumeType.PART) plateObject.extruderNumber else own
                    ExtruderColumn(shown, colors, allowDefault = part.type == VolumeType.MODIFIER) {
                        actions.setPartExtruder(partId, it)
                    }
                },
            onClick = { if (list.picking) actions.pickPart(partId) else actions.selectPart(partId) },
            onDoubleClick = list.activate,
            menu = list.menu { dismiss ->
                // ObjectList::show_context_menu() of a row among several selected volumes: multi_selection_menu().
                val volumesActions = actions.volumes
                if (state.selectedParts.size > 1 && partId in state.selectedParts && volumesActions != null) {
                    VolumesMenuItems(
                        volumesMenuState(
                            plateObject,
                            state.selectedParts.map { it.index },
                            enabled,
                            filaments = list.menuFilaments,
                            settingsClipboard = state.settingsClipboard,
                        ),
                        volumesActions,
                        dismiss,
                    )
                    return@menu
                }
                val volumeName = plateObject.volumeName(at)
                val partMenu = partMenuState(
                    plateObject,
                    at,
                    plateObject.instances.first(),
                    enabled,
                    clipboard = state.clipboard,
                    simplifying = state.simplifying,
                    filaments = list.menuFilaments,
                    settingsClipboard = state.settingsClipboard,
                ) ?: return@menu
                // The list picks the volume over the object's first copy.
                val first = PlateInstanceId(mesh, 0)
                val item = SettingsItem.Volume(partId)
                PartMenuItems(
                    partMenu,
                    PartMenuActions(
                        rename = { list.onAskRename(RenameRequest.Volume(partId, volumeName)) },
                        cut = { actions.copyVolumes(first, setOf(at), true) },
                        copy = { actions.copyVolumes(first, setOf(at), false) },
                        paste = { actions.paste(first) },
                        editText = { actions.editText(partId) },
                        editSvg = { actions.editSvg(partId) },
                        delete = { actions.removePart(partId) },
                        edit = { actions.editObject(mesh, it, at) },
                        simplify = { actions.simplifyVolume(partId) },
                        center = { actions.centerVolume(partId) },
                        drop = { actions.dropVolume(partId) },
                        mirror = { actions.mirrorVolume(partId, it) },
                        editProcessSettings = { actions.editProcessSettings(item) },
                        copyProcessSettings = { actions.copyProcessSettings(item) },
                        pasteProcessSettings = { actions.pasteProcessSettings(item) },
                        changeType = { actions.changeVolumeType(partId, it) },
                        reloadFromDisk = { actions.reloadFromDisk(mesh, at) },
                        replace = { actions.replaceVolume(first, at) },
                        setFilament = { actions.setPartExtruder(partId, it) },
                        editInParameterTable = { actions.editInParameterTable(item) },
                    ),
                    dismiss,
                )
            },
        )
    }
}

/** The row of a height range of [plateObject]. */
private fun LazyListScope.rangeRow(list: ObjectListContext, plateObject: PlateObject, range: LayerRange, rangeId: LayerRangeId) {
    val state = list.state
    val actions = list.actions
    val enabled = list.enabled
    val mesh = rangeId.mesh
    val selected = rangeId in state.selectedRanges
    // Several ranges of the object selected together: Delete and Copy take them all, as the desktop's keys do.
    val together = selected && state.selectedRanges.size > 1
    listRow(list.tree, ObjectListKeys.range(rangeId)) {
        ObjectListRow(
            name = stringResource(R.string.object_layer_range, range.bottom, range.top),
            icons = listOf(DesignR.drawable.orca_height_range_layer),
            depth = 3,
            selected = selected,
            hasSettings = range.settingsCategories(plateObject, list.processLayerHeight, list.rangeDefinitions).isNotEmpty(),
            onResetSettings = {
                actions.selectRangeSettings(rangeId)
                actions.resetSettings(PresetKind.LAYER)
            },
            enabled = enabled,
            extruder = list.filaments.takeIf { it.size > 1 }?.let { colors ->
                ExtruderColumn(range.settings.extruderNumber, colors, allowDefault = true) {
                    actions.setRangeExtruder(rangeId, it)
                }
            },
            onClick = { if (list.picking) actions.pickRange(rangeId) else actions.selectRange(rangeId) },
            // The menu acts on the row's range, or on the ranges selected with it.
            onLongClick = { if (!selected) actions.selectRange(rangeId) },
            menu = list.menu { dismiss ->
                OrcaMenuItem(
                    text = stringResource(R.string.object_menu_edit_range),
                    enabled = enabled,
                    onClick = {
                        dismiss()
                        list.onEditRange(rangeId)
                    },
                )
                // ObjectList::add_layer_range_after_current()
                OrcaMenuItem(
                    text = stringResource(R.string.object_menu_add_range_after),
                    enabled = enabled,
                    onClick = {
                        dismiss()
                        actions.addRange(mesh, rangeId)
                    },
                )
                OrcaMenuItem(
                    text = stringResource(UiR.string.object_menu_delete),
                    enabled = enabled,
                    onClick = {
                        dismiss()
                        if (together) actions.removeSelectedRanges() else actions.removeRange(rangeId)
                    },
                )
                // ObjectList::copy_to_clipboard() of the range, by the keyboard on the desktop.
                OrcaMenuItem(
                    text = orcaString("Copy"),
                    enabled = enabled,
                    onClick = {
                        dismiss()
                        if (together) actions.copySelectedRanges() else actions.copyRange(rangeId)
                    },
                )
                // The desktop list copies a range's settings with the keyboard
                // (ObjectList::copy_to_clipboard of its settings row), which a
                // phone offers from the row's menu.
                OrcaMenuSeparator()
                val item = SettingsItem.Layer(rangeId)
                ProcessSettingsItems(
                    enabled = enabled,
                    canPaste = enabled && state.settingsClipboard?.kind == SettingsItemKind.LAYER,
                    edit = { dismiss(); actions.editProcessSettings(item) },
                    copy = { dismiss(); actions.copyProcessSettings(item) },
                    paste = { dismiss(); actions.pasteProcessSettings(item) },
                )
            },
        )
    }
}

/** The row of a copy of [plateObject], under its "Instances". */
private fun LazyListScope.copyRow(list: ObjectListContext, plateObject: PlateObject, id: PlateInstanceId) {
    val state = list.state
    val actions = list.actions
    val enabled = list.enabled
    val selected = id in state.selectedInstances
    val selectionMenu = state.selectionMenu
    listRow(list.tree, ObjectListKeys.copy(id)) {
        ObjectListRow(
            name = stringResource(R.string.object_instance_name, id.instance + 1),
            icons = emptyList(),
            depth = 3,
            selected = selected && state.selectedPart == null && state.selectedRange == null && state.selectedConnectors == null,
            hasSettings = false,
            printable = plateObject.instances[id.instance].printable,
            showPrintable = true,
            enabled = enabled,
            onClick = { if (list.picking) actions.pickCopy(id, true) else actions.select(id, false) },
            onDoubleClick = list.activate,
            onPrintable = { actions.setPrintable(id, it) },
            // ObjectList::show_context_menu() of a copy among others selected keeps them.
            onLongClick = { if (!selected || state.selectedInstances.size <= 1) actions.selectAlone(id) },
            menu = list.menu { dismiss ->
                // multi_selection_menu() of copies of several objects.
                val selectionActions = actions.selection
                if (selectionMenu != null && selected && selectionActions != null) {
                    SelectionMenuItems(selectionMenu.copy(filaments = list.menuFilaments.takeIf { it.size > 1 }.orEmpty()), selectionActions, dismiss)
                    return@menu
                }
                // MenuFactory::instance_menu(): "Set as an individual object", of the
                // copies of the object selected with it (selected_instances_of_same_object());
                // Delete stands in for the Delete key, which erases the copy (Selection::erase).
                SetAsIndividualItem(wholeObject = false, enabled = enabled) {
                    dismiss()
                    actions.setAsIndividualOver(id)
                }
                OrcaMenuSeparator()
                OrcaMenuItem(
                    text = stringResource(UiR.string.object_menu_delete),
                    enabled = enabled,
                    onClick = {
                        dismiss()
                        actions.removeCopy(id)
                    },
                )
            },
        )
    }
}

/** A mark of an object's column with its tooltip and its click; null for the column's empty cell. */
private class RowIcon(val icon: Int, val description: String, val onClick: (() -> Unit)?)

/**
 * The cells of an object's columns the list shows (update_info_items()): its
 * variable layer height (colHeight), its support and colour painting
 * (colSupportPaint, colColorPaint) and its sinking (colSinking), each with its
 * click (ObjectList::list_manipulation()).
 */
@Composable
private fun ObjectListContext.columnsOf(plateObject: PlateObject): List<RowIcon?> {
    val mesh = plateObject.mesh
    val kinds = paintedKinds[mesh].orEmpty()
    return buildList {
        if (columns.height) {
            add(RowIcon(DesignR.drawable.orca_obj_variable_layer_height, orcaString("Variable layer height")) { actions.editLayers(mesh) }.takeIf { plateObject.hasVariableLayerHeight })
        }
        if (columns.support) {
            add(
                RowIcon(DesignR.drawable.orca_objlist_support_painting, orcaString("Click the icon to edit support painting of the object")) {
                    actions.paint(mesh, PaintKind.SUPPORTS)
                }.takeIf { PaintKind.SUPPORTS in kinds },
            )
        }
        if (columns.color) {
            add(
                RowIcon(DesignR.drawable.orca_objlist_color_painting, orcaString("Click the icon to edit color painting of the object")) {
                    actions.paint(mesh, PaintKind.COLOR)
                }.takeIf { PaintKind.COLOR in kinds },
            )
        }
        if (columns.sinking) {
            // The Preview's canvas reloads no scene, so the click does nothing there.
            val shift: (() -> Unit)? = if (preview) null else ({ actions.shiftToBed(mesh) })
            add(RowIcon(DesignR.drawable.orca_objlist_sinking, orcaString("Click the icon to shift this object to the bed"), shift).takeIf { plateObject.isSinking })
        }
    }
}

/** The menu of a row, which Preview does not open (ObjectList::show_context_menu()). */
private fun ObjectListContext.menu(content: @Composable (dismiss: () -> Unit) -> Unit): (@Composable (dismiss: () -> Unit) -> Unit)? =
    content.takeUnless { preview }

/**
 * What the filament column of a row shows and sets: the filament the item
 * prints with, where 0 is the "default" the desktop list shows for an item that
 * follows what it belongs to.
 */
internal data class ExtruderColumn(
    val number: Int,
    val filaments: List<Color>,
    /** A modifier or a range may follow its object; an object or a part of the model always shows a filament. */
    val allowDefault: Boolean,
    val onPick: (Int) -> Unit,
)

/**
 * OrcaSlicer's filament column (colFilament): the desktop cell opens a combo
 * box of the plate's filaments over the row (ObjectList::extruder_editing).
 * Here the cell is a swatch in the colour of the filament, which opens that
 * list where a finger can reach it.
 */
@Composable
private fun FilamentColumn(column: ExtruderColumn, enabled: Boolean) {
    var open by remember { mutableStateOf(false) }
    val colors = OrcaTheme.colors
    Box {
        if (column.number == 0) {
            // "default": the item takes the filament of what it belongs to.
            Box(
                Modifier
                    .padding(end = 4.dp)
                    .size(22.dp)
                    .border(1.dp, colors.border)
                    .clickable(enabled = enabled) { open = true },
                contentAlignment = Alignment.Center,
            ) {
                Text("—", color = colors.textSide, style = OrcaTheme.typography.body13)
            }
        } else {
            OrcaFilamentSlot(
                number = column.number,
                color = column.filaments.getOrElse(column.number - 1) { colors.accent },
                modifier = Modifier
                    .padding(end = 4.dp)
                    .clickable(enabled = enabled) { open = true },
            )
        }
        if (open) {
            OrcaContextMenu(expanded = true, position = IntOffset.Zero, onDismissRequest = { open = false }) {
                if (column.allowDefault) {
                    OrcaMenuItem(
                        text = orcaString("default"),
                        enabled = enabled,
                        onClick = {
                            open = false
                            column.onPick(0)
                        },
                    )
                }
                column.filaments.forEachIndexed { index, color ->
                    OrcaMenuItem(
                        text = (index + 1).toString(),
                        enabled = enabled,
                        leading = { OrcaFilamentSlot(number = index + 1, color = color) },
                        onClick = {
                            open = false
                            column.onPick(index + 1)
                        },
                    )
                }
            }
        }
    }
}

/**
 * ObjectDataViewModel::UpdateBitmapForNode() of a volume: the lock of a part
 * of a cut on its model parts and negative volumes, then its type's bitmap,
 * a text's or an SVG's by its type (get_text_volume_bitmaps(),
 * get_svg_volume_bitmaps()) and any other's by its type (get_volume_bitmaps()).
 */
private fun volumeIcons(plateObject: PlateObject, part: ObjectPart): List<Int> {
    val lock = DesignR.drawable.orca_cut_.takeIf { plateObject.isCut && (part.type == VolumeType.PART || part.type == VolumeType.NEGATIVE) }
    val type = when (part.emboss?.kind) {
        EmbossKind.TEXT -> when (part.type) {
            VolumeType.NEGATIVE -> DesignR.drawable.orca_add_text_negative
            VolumeType.MODIFIER -> DesignR.drawable.orca_add_text_modifier
            else -> DesignR.drawable.orca_add_text_part
        }
        EmbossKind.SVG -> when (part.type) {
            VolumeType.NEGATIVE -> DesignR.drawable.orca_svg_negative
            VolumeType.MODIFIER -> DesignR.drawable.orca_svg_modifier
            else -> DesignR.drawable.orca_svg_part
        }
        null -> when (part.type) {
            VolumeType.PART -> DesignR.drawable.orca_menu_add_part
            VolumeType.NEGATIVE -> DesignR.drawable.orca_menu_add_negative
            VolumeType.MODIFIER -> DesignR.drawable.orca_menu_add_modifier
            VolumeType.SUPPORT_BLOCKER -> DesignR.drawable.orca_menu_support_blocker
            VolumeType.SUPPORT_ENFORCER -> DesignR.drawable.orca_menu_support_enforcer
        }
    }
    return listOfNotNull(lock, type)
}

/** What the object menu does for the object of the copy [id], selected with its [copies], in the list. */
private fun ObjectListActions.menuOf(
    id: PlateInstanceId,
    name: String,
    copies: Set<Int>,
    delete: () -> Unit,
    invalidateCutInfo: () -> Unit,
    editText: (() -> Unit)?,
    editSvg: (() -> Unit)?,
    onChooseShape: (ScenePath, VolumeType) -> Unit,
    onAskNumberOfInstances: (ScenePath) -> Unit,
    onAskClone: (ScenePath) -> Unit,
) = ObjectMenuActions(
    cut = { copyObjects(copies.mapTo(LinkedHashSet()) { PlateInstanceId(id.mesh, it) }, true) },
    copy = { copyObjects(copies.mapTo(LinkedHashSet()) { PlateInstanceId(id.mesh, it) }, false) },
    paste = { paste(id) },
    addInstance = { addInstance(id.mesh) },
    removeInstance = { removeInstance(id.mesh) },
    setNumberOfInstances = { onAskNumberOfInstances(id.mesh) },
    fillBedWithInstances = { fillBed(id.mesh) },
    setAsIndividual = { setAsIndividual(id.mesh, copies) },
    clone = { onAskClone(id.mesh) },
    center = { manipulate(id, Manipulation.Center) },
    drop = { manipulate(id, Manipulation.Drop) },
    mirror = { manipulate(id, Manipulation.Mirror(it)) },
    delete = delete,
    addPart = { onChooseShape(id.mesh, it) },
    addHeightRange = { addRange(id.mesh, null) },
    setAutoDrop = { setWholeObjectAutoDrop(id.mesh, it) },
    edit = { editObject(id.mesh, it, null) },
    simplify = { simplifyObject(id.mesh) },
    setPrintable = { setObjectPrintable(id.mesh, it) },
    setFilament = { setObjectExtruder(id.mesh, it) },
    toggleFlushOption = { toggleFlushOption(id.mesh, it) },
    editProcessSettings = { editProcessSettings(SettingsItem.Object(id.mesh)) },
    copyProcessSettings = { copyProcessSettings(SettingsItem.Object(id.mesh)) },
    pasteProcessSettings = { pasteProcessSettings(SettingsItem.Object(id.mesh)) },
    reloadFromDisk = { reloadFromDisk(id.mesh, null) },
    replace = { replaceVolume(id, 0) },
    replaceAll = { replaceAllVolumes(id) },
    export = { exportObject(id.mesh, it, name) },
    invalidateCutInfo = invalidateCutInfo,
    editText = editText,
    editSvg = editSvg,
    editInParameterTable = { editInParameterTable(SettingsItem.Object(id.mesh)) },
)

/**
 * can_add_volumes_to_object(): an object with parts lists its volumes; a part
 * of a cut only while more than its own solid mesh is left besides the connectors.
 */
internal fun PlateObject.listsVolumes(): Boolean {
    if (parts.isEmpty()) return false
    if (!isCut) return true
    val listed = (0..parts.size).mapNotNull { volumeAt(it) }.filterNot { it.cutInfo.connector }
    return listed.any { it.type != VolumeType.PART } || listed.size > 1
}

/** ObjectDataViewModel::AddPlate(): "Plate N", and the plate's name after it. */
@Composable
internal fun ObjectListPlate.itemName(): String = "${orcaString("Plate")} ${index + 1}" + if (name.isEmpty()) "" else " ($name)"

/**
 * SettingsFactory::get_bundle(): what an item's overrides are named after.
 * Only the settings the tabs hold fall into a category, so an item that
 * overrides nothing else — the filament it prints with, which has a column of
 * its own — has no lock on its row.
 */
internal fun ModelSettings.categories(definitions: Map<String, SettingDefinition>): List<String> =
    values.keys.mapNotNull { definitions[it]?.category?.takeIf(String::isNotEmpty) }.distinct()

/**
 * ObjectList::add_settings_item() of a height range: its layer_height counts
 * only while it is not its object's own (TabPrintModel's config: the object's
 * value, or the process preset's [processLayerHeight]).
 */
private fun LayerRange.settingsCategories(owner: PlateObject, processLayerHeight: String?, definitions: Map<String, SettingDefinition>): List<String> {
    val own = settings.values[LAYER_HEIGHT]?.toDoubleOrNull()
    val objects = (owner.settings.values[LAYER_HEIGHT] ?: processLayerHeight)?.toDoubleOrNull()
    val counted = if (own != null && own == objects) ModelSettings(settings.values - LAYER_HEIGHT) else settings
    return counted.categories(definitions)
}

private const val LAYER_HEIGHT = "layer_height"

/**
 * ObjectList::get_mesh_errors_info() of an item: its tooltip, and the repair a
 * tap on its warning icon runs; null for a mesh that is manifold and was not
 * repaired.
 */
private class MeshWarning(val tooltip: String, val onRepair: () -> Unit)

@Composable
private fun meshWarning(errors: MeshErrors, onRepair: () -> Unit): MeshWarning? {
    if (errors.manifold && !errors.repaired) return null
    val tooltip = buildString {
        if (errors.repaired) {
            val count = errors.repairedCount
            append(orcaText(OrcaText("%1\$d error repaired", listOf(count.toString()), msgidPlural = "%1\$d errors repaired", count = count.toLong())))
            append('\n')
        }
        if (!errors.manifold) {
            append(orcaString("Remaining errors")).append(":\n\t")
            val edges = errors.openEdges
            append(orcaText(OrcaText("%1\$d non-manifold edge", listOf(edges.toString()), msgidPlural = "%1\$d non-manifold edges", count = edges)))
            append('\n')
        }
        append('\n').append(orcaString("Click the icon to repair model object"))
    }
    return MeshWarning(tooltip, onRepair)
}

/**
 * A row of the object list: the expander of a container, the item's icons and
 * name, the marks of its columns, its filament, its settings lock, and its
 * printable check box.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ObjectListRow(
    name: String,
    /** The bitmaps before the name (ObjectDataViewModelNode's m_bmp), in their own colours but for [iconTint]. */
    icons: List<Int>,
    selected: Boolean,
    hasSettings: Boolean,
    /** The level of the tree the row stands at: 0 for a plate. */
    depth: Int,
    iconTint: Color = Color.Unspecified,
    /** Whether the container is unfolded; null for a row with nothing under it. */
    expanded: Boolean? = null,
    onExpand: () -> Unit = {},
    /** colEditing's click: the item's settings reset; null leaves the icon a mark. */
    onResetSettings: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    /** The mesh's errors (ObjectDataViewModel's warning icon); null for none. */
    warning: MeshWarning? = null,
    /** Null on a row of several copies that differ; absent on a row without the check box. */
    printable: Boolean? = null,
    showPrintable: Boolean = false,
    /** The filament column (colFilament), which only the rows that take one have. */
    extruder: ExtruderColumn? = null,
    /** The cells of the object's columns the list shows; null for an empty one. */
    columns: List<RowIcon?> = emptyList(),
    enabled: Boolean = true,
    onClick: () -> Unit,
    /** wxEVT_DATAVIEW_ITEM_ACTIVATED: a second tap on the row; null for a row it does nothing on. */
    onDoubleClick: (() -> Unit)? = null,
    onLongClick: () -> Unit = {},
    onPrintable: (Boolean) -> Unit = {},
    /** ObjectList::OnBeginDrag(): the row a finger may drag onto another; null for one that stays. */
    drag: RowDrag? = null,
    /** The row's menu; null for none, as Preview opens none. */
    menu: (@Composable (dismiss: () -> Unit) -> Unit)? = null,
) {
    val colors = OrcaTheme.colors
    var menuOpen by remember { mutableStateOf(false) }
    var top by remember { mutableFloatStateOf(0f) }
    val haptics = LocalHapticFeedback.current
    val currentClick by rememberUpdatedState(onClick)
    val currentDoubleClick by rememberUpdatedState(onDoubleClick)
    val currentLongClick by rememberUpdatedState(onLongClick)
    val currentDrag by rememberUpdatedState(drag)
    val currentHasMenu by rememberUpdatedState(menu != null)
    val row = drag?.row
    val shared = drag?.drag
    if (row != null && shared != null) {
        DisposableEffect(row, shared) { onDispose { shared.bounds.remove(row) } }
    }
    val dropTarget = row != null && shared?.target == row
    Box(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (row != null && shared?.dragged == row) 0.5f else 1f)
                .background(if (selected || dropTarget) colors.accentSelected else Color.Transparent)
                .then(if (dropTarget) Modifier.border(2.dp, colors.accent) else Modifier)
                .onGloballyPositioned { coordinates ->
                    val bounds = coordinates.boundsInWindow()
                    top = bounds.top
                    if (row != null) shared?.bounds?.set(row, bounds)
                }
                .semantics {
                    role = Role.Button
                    onClick { currentClick(); true }
                    onLongClick {
                        currentLongClick()
                        if (currentHasMenu) menuOpen = true
                        true
                    }
                }
                // A tap selects the row, a second one soon after activates it, and a
                // long press opens its menu, which acts on the object it was opened
                // over; a row held and moved goes onto the row it is dropped on
                // (OnBeginDrag(), OnDropPossible(), OnDrop()).
                .pointerInput(row) {
                    // When the last tap lifted, which a second one counts from.
                    var lastTap = 0L
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val held = awaitLongPressOrCancellation(down.id)
                        if (held == null) {
                            val up = currentEvent.changes.firstOrNull { it.id == down.id }
                            if (up != null && up.changedToUpIgnoreConsumed() && !up.isConsumed) {
                                currentClick()
                                val activate = currentDoubleClick
                                if (activate != null && down.uptimeMillis - lastTap <= viewConfiguration.doubleTapTimeoutMillis) {
                                    lastTap = 0L
                                    activate()
                                } else {
                                    lastTap = up.uptimeMillis
                                }
                            }
                            return@awaitEachGesture
                        }
                        currentLongClick()
                        val dragging = currentDrag?.takeIf { it.drag.enabled }
                        if (dragging == null) {
                            if (currentHasMenu) menuOpen = true
                            return@awaitEachGesture
                        }
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        var moved = false
                        dragging.drag.dragged = dragging.row
                        val completed = drag(held.id) { change ->
                            if (!moved && (change.position - held.position).getDistance() > viewConfiguration.touchSlop) moved = true
                            if (moved) {
                                val under = dragging.drag.rowAt(top + change.position.y)
                                dragging.drag.target = under?.takeIf { it != dragging.row && dragging.drag.canDrop(dragging.row, it) }
                            }
                            change.consume()
                        }
                        val target = dragging.drag.target
                        dragging.drag.dragged = null
                        dragging.drag.target = null
                        when {
                            !moved -> if (currentHasMenu) menuOpen = true
                            completed && target != null -> dragging.drag.onDrop(dragging.row, target)
                        }
                    }
                }
                .heightIn(min = 40.dp)
                .padding(start = (4 + depth * 16).dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The tree's expander, which folds the rows under a container away.
            Box(
                modifier = Modifier
                    .width(24.dp)
                    .heightIn(min = 40.dp)
                    .then(if (expanded != null) Modifier.clickable(role = Role.Button, onClick = onExpand) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                if (expanded != null) {
                    Icon(
                        painterResource(DesignR.drawable.orca_hms_arrow),
                        contentDescription = stringResource(if (expanded) R.string.object_list_collapse else R.string.object_list_expand),
                        tint = colors.textSide,
                        modifier = Modifier
                            .size(12.dp)
                            .rotate(if (expanded) 90f else 0f),
                    )
                }
            }
            if (warning != null) {
                // ObjectList::list_manipulation(): a click on the warning icon repairs the item.
                Icon(
                    painterResource(DesignR.drawable.orca_obj_warning),
                    contentDescription = warning.tooltip,
                    tint = Color.Unspecified,
                    modifier = Modifier
                        .clickable(enabled = enabled, role = Role.Button, onClick = warning.onRepair)
                        .padding(end = 8.dp)
                        .size(OrcaTheme.dimensions.iconSmall),
                )
            }
            icons.forEach { icon ->
                Icon(
                    painterResource(icon),
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .size(OrcaTheme.dimensions.iconSmall),
                )
            }
            Text(
                text = name,
                color = if (enabled) colors.text else colors.textDisabled,
                style = OrcaTheme.typography.body14,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 2.dp, end = 8.dp),
            )
            columns.forEach { cell ->
                if (cell == null) {
                    Spacer(Modifier.width(COLUMN_WIDTH))
                } else {
                    Box(
                        modifier = Modifier
                            .width(COLUMN_WIDTH)
                            .heightIn(min = 40.dp)
                            .then(cell.onClick?.let { Modifier.clickable(enabled = enabled, role = Role.Button, onClick = it) } ?: Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(painterResource(cell.icon), contentDescription = cell.description, tint = Color.Unspecified, modifier = Modifier.size(OrcaTheme.dimensions.iconSmall))
                    }
                }
            }
            if (extruder != null) {
                FilamentColumn(extruder, enabled)
            }
            if (hasSettings) {
                // ObjectDataViewModelNode::set_action_icon(): the item has settings of
                // its own, which a tap on its lock resets; a touch target of its own
                // keeps the filament column beside it from taking the tap.
                Icon(
                    painterResource(DesignR.drawable.orca_lock_normal),
                    contentDescription = orcaString("Click the icon to reset all settings of the object"),
                    tint = Color.Unspecified,
                    modifier = Modifier
                        .then(
                            if (onResetSettings != null) {
                                Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onResetSettings)
                            } else {
                                Modifier
                            },
                        )
                        .padding(horizontal = 6.dp, vertical = 8.dp)
                        .size(OrcaTheme.dimensions.iconSmall),
                )
            }
            if (showPrintable) {
                OrcaCheckBox(
                    checked = printable,
                    onCheckedChange = onPrintable,
                    enabled = enabled,
                )
            }
        }
        if (menuOpen && menu != null) {
            OrcaContextMenu(expanded = true, position = IntOffset.Zero, onDismissRequest = { menuOpen = false }) {
                menu { menuOpen = false }
            }
        }
    }
}

/** The width of a column's cell, a finger's reach. */
private val COLUMN_WIDTH = 32.dp

/** A row the list lets a finger drag (ObjectList::OnBeginDrag()): an object, or a volume of one. */
internal sealed interface DragRow {
    data class Object(val mesh: ScenePath) : DragRow

    data class Volume(val mesh: ScenePath, val index: Int) : DragRow
}

/**
 * The drag of a row of the object list: where the rows stand in the window,
 * the row a finger holds, and the row under it that it may drop on
 * (ObjectList::m_dragged_data). The list sets whether rows may be dragged and
 * the rules of [canDrop] and [onDrop].
 */
@Stable
internal class ObjectListDrag {
    val bounds = HashMap<DragRow, Rect>()
    var dragged by mutableStateOf<DragRow?>(null)
    var target by mutableStateOf<DragRow?>(null)
    var enabled = true
    var canDrop: (row: DragRow, target: DragRow) -> Boolean = { _, _ -> false }
    var onDrop: (row: DragRow, target: DragRow) -> Unit = { _, _ -> }

    /** The row at the height [y] of the window. */
    fun rowAt(y: Float): DragRow? = bounds.entries.firstOrNull { (_, rect) -> y >= rect.top && y < rect.bottom }?.key
}

/** A draggable row: which one it is, and the drag the list's rows share. */
internal class RowDrag(val row: DragRow, val drag: ObjectListDrag)
