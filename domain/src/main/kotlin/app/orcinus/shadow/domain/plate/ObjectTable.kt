package app.orcinus.shadow.domain.plate

import app.orcinus.shadow.core.model.ObjectPartId
import app.orcinus.shadow.core.model.PlateInstanceId
import app.orcinus.shadow.core.model.PlateState
import app.orcinus.shadow.core.model.PresetKind
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.SettingsItem
import app.orcinus.shadow.core.model.SettingsRequest
import app.orcinus.shadow.core.model.VolumeType
import app.orcinus.shadow.core.model.extruderNumber
import app.orcinus.shadow.core.model.mesh
import app.orcinus.shadow.core.model.plateOf
import app.orcinus.shadow.core.model.volumeAt
import app.orcinus.shadow.core.model.withSettings
import app.orcinus.shadow.core.model.withVolumeAt

/**
 * ObjectGridTable::GridColType: the columns of OrcaSlicer's Parameter Table
 * (GUI_ObjectTable.cpp) in their order, with the key of the setting their
 * cells show (ObjectGridCol::key) and whether only an object's row has them
 * (ObjectGridCol::b_for_object).
 */
enum class ObjectTableColumn(val key: String, val forObject: Boolean) {
    PRINTABLE("printable", forObject = true),
    PLATE("plate_index", forObject = true),
    NAME("name", forObject = false),
    FILAMENT("extruder", forObject = false),
    LAYER_HEIGHT("layer_height", forObject = true),
    WALL_LOOPS("wall_loops", forObject = false),
    FILL_DENSITY("sparse_infill_density", forObject = false),
    SUPPORT("enable_support", forObject = true),
    BRIM("brim_type", forObject = true),
    OUTER_WALL_SPEED("outer_wall_speed", forObject = false),
    ;

    /**
     * A setting of the process the row's ModelConfig overrides (b_from_config),
     * with its reset column after it; ObjectTablePanel::load_data() hides the
     * reset columns of the printable state and of the filament.
     */
    val isSetting: Boolean get() = ordinal >= LAYER_HEIGHT.ordinal

    /** ObjectGridTable::sort_by_col() sorts by the plate, the name and the filament alone. */
    val sortable: Boolean get() = this == PLATE || this == NAME || this == FILAMENT

    /** ConfigOption::operator==() of two values of the column, as the settings write them ("0.2", "3", "15%", "1", "auto_brim"). */
    fun sameValue(a: String?, b: String?): Boolean = if (isNumber) a?.toSettingNumber() == b?.toSettingNumber() else a == b

    private val isNumber: Boolean get() = this == LAYER_HEIGHT || this == WALL_LOOPS || this == FILL_DENSITY || this == OUTER_WALL_SPEED
}

/**
 * A value of a number setting as the settings write it ("0.2", "15%"), or as
 * a field takes it, with a decimal comma (TextCtrl::get_value()); null for no number.
 */
fun String.toSettingNumber(): Double? = trim().removeSuffix("%").trim().replace(',', '.').toDoubleOrNull()

/**
 * ObjectGridTable::ObjectGridRow: a row of the table, of an object or of one
 * of its volumes. [values] are what the cells of the settings show and
 * [originals] what their reset icons bring back (ori_layer_height and the
 * others): an object's the edited process preset's, a volume's its object's.
 */
data class ObjectTableRow(
    val mesh: ScenePath,
    /** The volume's place in ModelObject::volumes (volume_id); null for the object's own row. */
    val volume: Int? = null,
    /** ModelVolume::type() of a volume's row; null for an object's. */
    val volumeType: VolumeType? = null,
    /** PartPlateList::find_instance_belongs() of the object's first copy; null for "Outside". */
    val plate: Int? = null,
    /** ModelInstance::printable of the object's first copy. */
    val printable: Boolean = true,
    /** The filament the row prints with, from 1: a volume's own, or its object's. */
    val filament: Int = 1,
    /** A volume with a filament of its own, other than its object's. */
    val ownFilament: Boolean = false,
    val values: Map<ObjectTableColumn, String> = emptyMap(),
    val originals: Map<ObjectTableColumn, String> = emptyMap(),
) {
    /** The item of the object list whose ModelConfig the row edits. */
    val item: SettingsItem get() = volume?.let { SettingsItem.Volume(ObjectPartId(mesh, it)) } ?: SettingsItem.Object(mesh)

    /** ObjectGridTable::IsEmptyCell(): a volume's row has no cell in the columns of an object. */
    fun shows(column: ObjectTableColumn): Boolean = volume == null || !column.forObject

    /** GridCellIconRenderer: the reset icon of a setting whose value is not the one it takes. */
    fun modified(column: ObjectTableColumn): Boolean = column.isSetting && shows(column) && !column.sameValue(values[column], originals[column])

    /**
     * ObjectTablePanel::load_data(): the cells that edit, which are all but the
     * plate's; a volume that takes no filament (a negative volume or a support
     * blocker or enforcer) has no filament editor.
     */
    fun editable(column: ObjectTableColumn): Boolean = shows(column) && when (column) {
        ObjectTableColumn.PLATE -> false
        ObjectTableColumn.FILAMENT -> volumeType == null || volumeType == VolumeType.PART || volumeType == VolumeType.MODIFIER
        else -> true
    }
}

/**
 * ObjectGridTable::construct_object_configs(): a row for every object of the
 * plate in its order and, once an object has several volumes, a row for each
 * of them after it. A setting the row's item does not override shows what it
 * takes: a volume its object's, and an object the edited process preset's,
 * as the process tab shows it.
 */
fun PlateState.objectTableRows(): List<ObjectTableRow> {
    val process = SETTINGS.associateWith { presetValue(PresetKind.PRINT, it.key).orEmpty() }
    return objects.flatMap { plateObject ->
        val first = plateObject.instances.first()
        val plate = plateOf(first)
        val values = SETTINGS.associateWith { plateObject.settings.values[it.key] ?: process.getValue(it) }
        val objectRow = ObjectTableRow(
            mesh = plateObject.mesh,
            plate = plate,
            printable = first.printable,
            // An object without a filament prints with the first one.
            filament = plateObject.extruderNumber,
            values = values,
            originals = process,
        )
        val volumes = if (plateObject.parts.isEmpty()) emptyList() else (0..plateObject.parts.size).mapNotNull { at ->
            val volume = plateObject.volumeAt(at) ?: return@mapNotNull null
            // A volume's filament 0, or its object's, is no filament of its own.
            val own = volume.settings.extruderNumber
            ObjectTableRow(
                mesh = plateObject.mesh,
                volume = at,
                volumeType = volume.type,
                plate = plate,
                printable = first.printable,
                filament = if (own > 0) own else objectRow.filament,
                ownFilament = own > 0 && own != objectRow.filament,
                values = SETTINGS.associateWith { volume.settings.values[it.key] ?: values.getValue(it) },
                originals = values,
            )
        }
        listOf(objectRow) + volumes
    }
}

/** The columns of the settings, whose values the rows hold. */
private val SETTINGS = ObjectTableColumn.entries.filter(ObjectTableColumn::isSetting)

/**
 * The order the table shows its objects in, which each sort starts from, as
 * sort_row_data() sorts the rows as they stand; and ObjectGridTable's
 * m_sort_col: the column last sorted ascending, which a second sort by it
 * turns descending and forgets, PRINTABLE after sort_by_default().
 */
data class ObjectTableOrder(val objects: List<ScenePath> = emptyList(), val sortColumn: ObjectTableColumn? = null) {
    /** The rows in this order, each object's volumes after it; objects the order has yet to know follow in the plate's. */
    fun arrange(rows: List<ObjectTableRow>): List<ObjectTableRow> {
        val position = objects.withIndex().associate { (index, mesh) -> mesh to index }
        return rows.sortedObjects(compareBy { position[it.mesh] ?: Int.MAX_VALUE })
    }

    /** ObjectGridTable::sort_by_default(): by plate, "Outside" last, then by name. */
    fun byDefault(rows: List<ObjectTableRow>, name: (ObjectTableRow) -> String): ObjectTableOrder =
        ObjectTableOrder(arrange(rows).sortedObjects(compareBy<ObjectTableRow> { it.plateNumber }.thenBy(name)).objectMeshes(), ObjectTableColumn.PRINTABLE)

    /**
     * ObjectGridTable::sort_by_col(): ascending by [column], or descending
     * when it was the last one sorted ascending. The plate's ties go by name;
     * the filaments compare as text (std::to_string), as the desktop app does.
     */
    fun by(column: ObjectTableColumn, rows: List<ObjectTableRow>, name: (ObjectTableRow) -> String): ObjectTableOrder {
        val ascending: Comparator<ObjectTableRow> = when (column) {
            ObjectTableColumn.NAME -> compareBy(name)
            ObjectTableColumn.FILAMENT -> compareBy { it.filament.toString() }
            ObjectTableColumn.PLATE -> compareBy<ObjectTableRow> { it.plateNumber }.thenBy(name)
            else -> return this
        }
        val descending = column == sortColumn
        val sorted = arrange(rows).sortedObjects(if (descending) ascending.reversed() else ascending)
        return ObjectTableOrder(sorted.objectMeshes(), if (descending) null else column)
    }

    private fun List<ObjectTableRow>.objectMeshes(): List<ScenePath> = filter { it.volume == null }.map(ObjectTableRow::mesh)

    private companion object {
        /** ObjectGridTable::plate_outside sorts as plate 100. */
        const val OUTSIDE = 100

        val ObjectTableRow.plateNumber: Int get() = plate?.plus(1) ?: OUTSIDE
    }
}

/** ObjectGridTable::sort_row_data(): the object rows sorted, each followed by its volumes as they stood. */
private fun List<ObjectTableRow>.sortedObjects(comparator: Comparator<ObjectTableRow>): List<ObjectTableRow> {
    val volumes = filter { it.volume != null }.groupBy(ObjectTableRow::mesh)
    return filter { it.volume == null }.sortedWith(comparator).flatMap { listOf(it) + volumes[it.mesh].orEmpty() }
}

/**
 * What OrcaSlicer's Parameter Table does to the plate (ObjectGridTable): a
 * selected cell selects its row's object or volume in the object list
 * (OnSelectCell()), and an edit writes into the row's ModelConfig. A setting
 * goes through the settings tab of the row's item, which checks the value and
 * corrects the others as the table's side panel does with its ConfigManipulation
 * (ObjectTableSettings::update_config_values()); every edit is one step of
 * Undo, as the side panel's "Change Option" is.
 */
class ObjectTableUseCase(
    private val repository: PlateRepository,
    private val settingsTabs: PresetSettingsTabs,
    private val selectPlateObject: SelectPlateObjectUseCase,
    private val selectObjectPart: SelectObjectPartUseCase,
    private val setPlateObjectPrintable: SetPlateObjectPrintableUseCase,
    private val renamePlateItem: RenamePlateItemUseCase,
) {
    /** OnSelectCell(): ObjectList::select_items() of the row's object, over its first copy as the list picks it, or of its volume. */
    fun select(item: SettingsItem) {
        when (item) {
            is SettingsItem.Object -> selectPlateObject(PlateInstanceId(item.mesh))
            is SettingsItem.Volume -> selectObjectPart(item.id)
            is SettingsItem.Layer -> Unit
        }
    }

    /**
     * SetValue() of a setting's cell: a value the row takes anyway is no
     * override of its own (update_value_to_config() erases the key), any other
     * is written; a density outside 0 to 100 % is refused. The tab reads
     * [text] as its field does, and says when it is no value.
     */
    fun change(item: SettingsItem, column: ObjectTableColumn, text: String) {
        if (!column.isSetting) return
        val row = repository.state.value.objectTableRows().firstOrNull { it.item == item }?.takeIf { it.shows(column) } ?: return
        val value = text.trim()
        if (column == ObjectTableColumn.FILL_DENSITY && value.toSettingNumber()?.let { it < 0 || it > 100 } == true) return
        if (column.sameValue(value, row.values[column])) return
        val request = if (column.sameValue(value, row.originals[column])) SettingsRequest.Reset(listOf(column.key)) else SettingsRequest.Change(column.key, value)
        settingsTabs.request(item, request)
    }

    /** OnCellLeftClick() of a reset icon: the setting goes back to the one the row takes, its override erased. */
    fun reset(item: SettingsItem, column: ObjectTableColumn) {
        val row = repository.state.value.objectTableRows().firstOrNull { it.item == item } ?: return
        if (row.modified(column)) settingsTabs.request(item, SettingsRequest.Reset(listOf(column.key)))
    }

    /** update_value_to_object() of the printable column: the object's first copy (instances[0]). */
    fun setPrintable(mesh: ScenePath, printable: Boolean) = setPlateObjectPrintable(PlateInstanceId(mesh), printable)

    /** update_value_to_object() of the name column; a volume's name loses the spaces it is indented by. */
    fun rename(item: SettingsItem, name: String) {
        when (item) {
            is SettingsItem.Object -> renamePlateItem(item.mesh, name)
            is SettingsItem.Volume -> renamePlateItem(item.id, name.trimStart(' '))
            is SettingsItem.Layer -> Unit
        }
    }

    /**
     * SetValue() of the filament column, one step of Undo: an object always
     * takes the filament into its config (update_filament_to_config()), and
     * a volume of it whose own filament is that one loses it
     * (update_volume_values_from_object()); a part or a modifier takes it as
     * its own. The filaments offered are the plate's.
     */
    fun setFilament(item: SettingsItem, filament: Int) = repository.update { state ->
        val owner = state.objects.withMesh(item.mesh)
        val filaments = state.profiles?.allFilaments?.size ?: 0
        if (owner == null || state.busy || filament < 1 || filament > filaments) return@update state
        val updated = when (item) {
            is SettingsItem.Object -> (0..owner.parts.size).fold(owner.withSettings(owner.settings.withExtruder(filament))) { done, at ->
                val volume = done.volumeAt(at)
                if (volume == null || volume.settings.extruderNumber != filament) done else done.withVolumeAt(at, volume.copy(settings = volume.settings.withExtruder(0)))
            }
            is SettingsItem.Volume -> owner.volumeAt(item.id.index)
                ?.takeIf { it.type == VolumeType.PART || it.type == VolumeType.MODIFIER }
                ?.let { owner.withVolumeAt(item.id.index, it.copy(settings = it.settings.withExtruder(filament))) }
            is SettingsItem.Layer -> null
        }
        if (updated == null || updated == owner) return@update state
        state.recorded().copy(objects = state.objects.replaced(updated), result = null)
    }
}
