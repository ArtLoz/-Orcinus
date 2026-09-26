package app.orcinus.shadow.domain.plate

import app.orcinus.shadow.core.model.EnginePlate
import app.orcinus.shadow.core.model.PlateInstanceId
import app.orcinus.shadow.core.model.listPlateOf
import app.orcinus.shadow.core.model.PlateSettingsChoice
import app.orcinus.shadow.core.model.plateSettingsChoice
import app.orcinus.shadow.core.model.withPlateSettingsChoice
import app.orcinus.shadow.core.model.withSettings
import app.orcinus.shadow.core.model.mesh
import app.orcinus.shadow.core.model.ModelSettings
import app.orcinus.shadow.core.model.PlateInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import app.orcinus.shadow.core.model.ModelInspection
import app.orcinus.shadow.core.model.PartPlate
import app.orcinus.shadow.core.model.PlateGrid
import app.orcinus.shadow.core.model.PlateManipulation
import app.orcinus.shadow.core.model.PlateObject
import app.orcinus.shadow.core.model.PlateState
import app.orcinus.shadow.core.model.Point2
import app.orcinus.shadow.core.model.PresetKind
import app.orcinus.shadow.core.model.SettingState
import app.orcinus.shadow.core.model.SliceBasis
import app.orcinus.shadow.core.model.Transform3
import app.orcinus.shadow.core.model.partPlates
import app.orcinus.shadow.core.model.placing
import app.orcinus.shadow.core.model.plateGrid
import app.orcinus.shadow.core.model.plateOf
import app.orcinus.shadow.core.model.withInstances
import app.orcinus.shadow.domain.placed
import app.orcinus.shadow.slicing.api.PlateInspector
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Plater::select_plate(): another plate becomes the current one. Its settings,
 * the codes on its layers and its G-code take the plate's fields, the G-code
 * while nothing it was sliced from changed (Print::apply() on the plate's
 * print), and the objects are judged against its build volume once the engine
 * knows it (EnginePlateSync). The selection is cleared, as a click on a plate
 * deselects everything. Nothing changes while the plate is busy, an object is
 * being placed or a tool keeps a state of its own.
 */
class SelectPlateUseCase(private val repository: PlateRepository) {
    operator fun invoke(index: Int) = repository.update { state ->
        if (!state.canChangePlates || index == state.currentPlate || index !in state.plates.indices) return@update state
        state.withPlates(state.platesLeft(), index).copy(selectedInstances = emptySet(), selectedPart = null, selectedRange = null)
    }
}

/**
 * The toolbar's "Add plate" (Plater::priv::on_action_add_plate): the undo
 * stack takes "add partplate", PartPlateList::create_plate() adds a plate
 * after the last one, and it becomes the current one. When the plates need
 * another column, every plate moves to its place in the new rows and the
 * objects standing on it move with it (update_all_plates_pos_and_size). At most
 * PartPlateList::MAX_PLATES_COUNT plates.
 */
class AddPlateUseCase(private val repository: PlateRepository) {
    operator fun invoke() = repository.update { state ->
        val grid = state.plateGrid
        val count = state.plates.size
        if (!state.canAddPlate || grid == null) return@update state
        val moves = if (PlateGrid.columns(count + 1) != PlateGrid.columns(count)) {
            List(count) { index -> grid.originOf(index, count + 1) - grid.originOf(index, count) }
        } else {
            emptyList()
        }
        state.recorded()
            .copy(objects = state.objectsMoved(moves, unprintable = null))
            .withPlates(state.platesLeft() + PartPlate(), count)
    }
}

/**
 * The plate's "Remove current plate (if not last one)" (Plater::delete_plate):
 * the undo stack takes "delete partplate", and PartPlateList::delete_plate()
 * sends the objects on the plate where no plate is (compute_origin_for_unprintable),
 * moves every later plate one place back with its objects, and all of them
 * when the plates need a column less. The plate's wipe tower position goes
 * with it; the codes on the layers stay with the plate indexes, as
 * Model::plates_custom_gcodes keeps them. The plate before the deleted one
 * becomes current when the current one was deleted or came after it.
 */
class DeletePlateUseCase(private val repository: PlateRepository) {
    operator fun invoke(index: Int) = repository.update { state ->
        if (!state.canDeletePlate || index !in state.plates.indices) return@update state
        state.recorded().withPlateDeleted(index) ?: state
    }
}

/**
 * The plate's "Lock current plate" and "Unlock current plate"
 * (PartPlateList::lock_plate), which the undo stack takes as "lock partplate":
 * arranging and orienting every plate leave a locked plate's objects alone.
 */
class LockPlateUseCase(private val repository: PlateRepository) {
    operator fun invoke(index: Int) = repository.update { state ->
        if (!state.canChangePlates || index !in state.plates.indices) return@update state
        val plates = state.plates.mapIndexed { at, plate -> if (at == index) plate.copy(locked = !plate.locked) else plate }
        state.recorded().copy(plates = plates)
    }
}

/**
 * PlateNameEditDialog's OK (PartPlate::set_plate_name): the plate takes the
 * name, at most 250 characters as the dialog's field holds, which the view
 * writes over it and the G-code's file name follows.
 */
class RenamePlateUseCase(private val repository: PlateRepository) {
    operator fun invoke(index: Int, name: String) = repository.update { state ->
        if (!state.canChangePlates || index !in state.plates.indices) return@update state
        val plates = state.plates.mapIndexed { at, plate -> if (at == index) plate.copy(name = name.take(MAX_PLATE_NAME)) else plate }
        state.copy(plates = plates)
    }

    companion object {
        /** The length PlateNameEditDialog's field takes. */
        const val MAX_PLATE_NAME = 250
    }
}

/**
 * The plate's "Move plate to the front": the undo stack takes "move plate to
 * the front", and PartPlateList::move_plate_to_index() puts the plate first,
 * the ones before it one place back, each with the objects on it; the plate
 * becomes the current one. Its settings go with it, the wipe tower positions
 * and the codes on the layers stay with the plate indexes, as the project
 * config and Model::plates_custom_gcodes keep them.
 */
class MovePlateToFrontUseCase(private val repository: PlateRepository) {
    operator fun invoke(index: Int) = repository.update { state ->
        val grid = state.plateGrid
        val count = state.plates.size
        if (!state.canChangePlates || index <= 0 || index >= count || grid == null) return@update state
        // The new place of every plate.
        val places = List(count) { plate -> if (plate == index) 0 else if (plate < index) plate + 1 else plate }
        val moves = List(count) { plate -> grid.originOf(places[plate], count) - grid.originOf(plate, count) }
        val left = state.platesLeft()
        val plates = List(count) { at ->
            val moved = left[places.indexOf(at)]
            moved.copy(
                settings = moved.settings.withWipeTowerOf(left[at].settings),
                layerGcodes = left[at].layerGcodes,
            )
        }
        state.recorded()
            .copy(objects = state.objectsMoved(moves, unprintable = null))
            .withPlates(plates, 0)
    }
}

/**
 * The plate's "Auto orient objects on current plate" and "Arrange objects on
 * current plate" (Plater::select_plate_by_hover_id, actions 2 and 3): the
 * plate becomes the current one, and once the engine knows it, OrientJob or
 * ArrangeJob works on it (prepare_partplate). A locked or empty plate is left
 * as it is.
 */
class PlateJobsUseCase(
    private val repository: PlateRepository,
    private val selectPlate: SelectPlateUseCase,
    private val placePlateObjects: PlacePlateObjectsUseCase,
    private val applicationScope: CoroutineScope,
) {
    fun orient(index: Int) = onPlate(index) { state ->
        // prepare_partplate(): an object takes the part of its last copy, as the loop over the copies leaves the flag.
        val selected = state.objects
            .filter { plateObject -> plateObject.instances.lastOrNull()?.let(state::plateOf) == index }
            .mapTo(LinkedHashSet(), PlateObject::mesh)
        placePlateObjects(PlateManipulation.AutoOrient(selected), skipLockedPlates = false)
    }

    fun arrange(index: Int) = onPlate(index) { state -> placePlateObjects(PlateManipulation.ArrangePlate(state.arrangeSettings)) }

    private fun onPlate(index: Int, job: (PlateState) -> Unit) {
        val state = repository.state.value
        if (index !in state.plates.indices || !state.canWorkOnPlate(index)) return
        selectPlate(index)
        applicationScope.launch {
            val ready = repository.settledOn(index) ?: return@launch
            job(ready)
        }
    }
}

/**
 * The state once the plate at [index] is current, the engine knows it and its
 * objects are judged against its build volume; null when it does not come
 * within PLATE_WAIT_MILLIS.
 */
internal suspend fun PlateRepository.settledOn(index: Int): PlateState? = withTimeoutOrNull(PLATE_WAIT_MILLIS) {
    state.first { it.currentPlate == index && !it.busy && it.objects.none(PlateObject::placing) }
}

/** How long a job waits for the engine to know the plate. */
private const val PLATE_WAIT_MILLIS = 10_000L

/** Whether the plate's orient and arrange work: it is not locked and has objects on it (PartPlate::empty()). */
fun PlateState.canWorkOnPlate(index: Int): Boolean =
    plates.getOrNull(index)?.locked == false && copies().any { plateOf(it) == index }

/**
 * Tells the engine the current plate and the number of plates whenever they
 * change, so that its requests judge, place and slice on the plate the app
 * shows (PartPlateList::select_plate() and Plater::priv::update_print_volume_state()).
 * The plate is busy until the engine knows; then the objects are judged
 * against the build volume of the current plate.
 */
class EnginePlateSync(
    private val inspector: PlateInspector,
    private val repository: PlateRepository,
    private val placePlateObjects: PlacePlateObjectsUseCase,
    private val applicationScope: CoroutineScope,
) {
    fun start() {
        applicationScope.launch {
            repository.state
                .map { EnginePlate(it.currentPlate, it.plates.size) }
                .distinctUntilChanged()
                .collect { plate -> tell(plate) }
        }
    }

    private suspend fun tell(plate: EnginePlate) {
        try {
            inspector.selectPlate(plate.index, plate.count)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // A new engine process is told the plate by the adapter before any request reaches it.
        }
        placePlateObjects.judgeOn(plate)
    }
}

/**
 * PartPlateList::delete_plate() of the plate at [index]: its objects go where
 * no plate is (compute_origin_for_unprintable), every later plate moves one
 * place back with its objects, and all of them when the plates need a column
 * less. Its wipe tower position goes with it; the codes on the layers stay
 * with the plate indexes, as Model::plates_custom_gcodes keeps them. The plate
 * before the deleted one becomes current when the current one was deleted or
 * came after it. Null for the last plate.
 */
internal fun PlateState.withPlateDeleted(index: Int): PlateState? {
    val grid = plateGrid ?: return null
    val count = plates.size
    if (count <= 1 || index !in plates.indices) return null
    val moves = List(count) { plate ->
        when {
            plate == index -> grid.unprintableOrigin(count - 1) - grid.originOf(plate, count)
            plate > index -> grid.originOf(plate - 1, count - 1) - grid.originOf(plate, count)
            else -> grid.originOf(plate, count - 1) - grid.originOf(plate, count)
        }
    }
    val unprintable = grid.unprintableOrigin(count - 1) - grid.unprintableOrigin(count)
    val left = platesLeft()
    val kept = left.filterIndexed { plate, _ -> plate != index }
        .mapIndexed { plate, plateLeft -> plateLeft.copy(layerGcodes = left[plate].layerGcodes) }
    val current = when {
        currentPlate == index && index == 0 -> 0
        currentPlate >= index -> currentPlate - 1
        else -> currentPlate
    }
    return copy(objects = objectsMoved(moves, unprintable)).withPlates(kept, current)
}

/**
 * ArrangeJob::finalize() after arranging every plate: plates are added up to
 * the [count] the arrangement needed (PartPlateList::create_plate), then
 * rebuild_plates_after_arrangement() deletes the plates at the end without
 * objects, or without printable ones, passing over locked plates, down to the
 * first plate.
 */
internal fun PlateState.withArrangedPlates(count: Int): PlateState {
    var state = if (count > plates.size) withPlates(platesLeft() + List(count - plates.size) { PartPlate() }, currentPlate) else this
    for (index in state.plates.lastIndex downTo 1) {
        val onPlate = state.copies().filter { state.plateOf(it) == index }
        state = when {
            onPlate.none(PlateInstance::printable) -> state.withPlateDeleted(index) ?: state
            state.plates[index].locked -> continue
            else -> break
        }
    }
    return state
}

/** Plater::can_add_plate(): fewer plates than PartPlateList::MAX_PLATES_COUNT, and nothing else going on. */
val PlateState.canAddPlate: Boolean get() = canChangePlates && plates.size < PlateGrid.MAX_PLATES && plateGrid != null

/** Plater::can_delete_plate(): more than one plate. */
val PlateState.canDeletePlate: Boolean get() = canChangePlates && plates.size > 1 && plateGrid != null

internal val PlateState.canChangePlates: Boolean
    get() = !busy && !slicingAll && objects.none(PlateObject::placing) && history.beforeTool == null && simplifyTarget == null

/** The plates as they are now, the current one with what its G-code was sliced from, as another becomes current. */
internal fun PlateState.platesLeft(): List<PartPlate> = partPlates().mapIndexed { index, plate ->
    if (index == currentPlate) plate.copy(basis = result?.let { sliceBasis() }) else plate
}

/**
 * The state with [plates] and the one at [index] current: its settings, with
 * the project's flushing volumes, its layer codes, and its G-code while
 * nothing it was sliced from changed.
 */
internal fun PlateState.withPlates(plates: List<PartPlate>, index: Int): PlateState {
    val current = plates[index]
    val applies = current.result != null && (current.basis == null || current.basis == sliceBasis())
    return copy(
        plates = plates,
        currentPlate = index,
        plateSettings = current.settings.withFlushVolumesOf(plateSettings),
        layerGcodes = current.layerGcodes,
        result = current.result?.takeIf { applies },
    )
}

/** What G-code sliced now would be sliced from besides the plate itself. */
internal fun PlateState.sliceBasis() = SliceBasis(objects.map { it.placed() }, profiles, presetValues())

/** The values of the edited presets, which the desktop app reloads the scene after every change of. */
internal fun PlateState.presetValues(): List<List<String>?> =
    PRESET_TABS.map { kind -> settingsTabs[kind]?.settings?.settings?.map(SettingState::value) }

/** The tabs of the presets the plate is sliced and its tower described with. */
private val PRESET_TABS = listOf(PresetKind.PRINT, PresetKind.FILAMENT, PresetKind.PRINTER)

/**
 * PartPlate::set_pos_and_size() with its instances: every copy moves as the
 * plate it stands on moves ([moves], by plate), and a copy on no plate as the
 * area for them does ([unprintable]); by whole millimetres, as the desktop app
 * moves them.
 */
private fun PlateState.objectsMoved(moves: List<Point2>, unprintable: Point2?): List<PlateObject> {
    if (moves.isEmpty() && unprintable == null) return objects
    return objects.map { plateObject ->
        plateObject.withInstances(
            plateObject.instances.map { instance ->
                val move = plateOf(instance)?.let(moves::getOrNull) ?: unprintable
                if (move == null) instance else instance.copy(inspection = instance.inspection.moved(move.x.toInt(), move.y.toInt()))
            },
        )
    }
}

/** The copy shifted by [dx] and [dy] on the plate. */
private fun ModelInspection.moved(dx: Int, dy: Int): ModelInspection {
    if (dx == 0 && dy == 0) return this
    val columns = placement.columns.toMutableList()
    columns[12] += dx
    columns[13] += dy
    return copy(
        placement = Transform3(columns),
        boxCenter = boxCenter.copy(x = boxCenter.x + dx, y = boxCenter.y + dy),
        boundingSphere = boundingSphere.copy(center = boundingSphere.center.copy(x = boundingSphere.center.x + dx, y = boundingSphere.center.y + dy)),
    )
}

private operator fun Point2.minus(other: Point2) = Point2(x - other.x, y - other.y)

/** The settings with the wipe tower position of [other] (wipe_tower_x and wipe_tower_y of the project, by plate index). */
private fun ModelSettings.withWipeTowerOf(other: ModelSettings): ModelSettings =
    ModelSettings(values - WIPE_TOWER_KEYS + other.values.filterKeys { it in WIPE_TOWER_KEYS })

private val WIPE_TOWER_KEYS = setOf("wipe_tower_x", "wipe_tower_y")

/**
 * PlateSettingsDialog's OK for the current plate (Plater::open_platesettings_dialog):
 * the plate takes the bed type, print sequence, filament sequences and spiral
 * vase mode chosen. Enabling spiral vase mode on a plate that does not print
 * in it yet (PartPlate::set_spiral_vase_mode) needs the user's agreement to
 * the settings it asks for ([vaseSettingsAgreed]), which the objects on the
 * plate then take (set_vase_mode_related_object_config); without it the mode
 * stays as it was. G-code sliced before no longer applies once the plate
 * prints differently.
 */
class SetPlateSettingsUseCase(private val repository: PlateRepository) {
    operator fun invoke(choice: PlateSettingsChoice, vaseSettingsAgreed: Boolean) = repository.update { state ->
        if (!state.canChangePlates) return@update state
        val before = state.plateSettings.plateSettingsChoice()
        val enabling = choice.spiralMode == true && !state.spiralVaseMode()
        val spiral = when {
            // get_spiral_vase_mode(): a plate that already prints in the mode keeps its settings as they are.
            choice.spiralMode == true && !enabling -> before.spiralMode
            enabling && !vaseSettingsAgreed -> before.spiralMode
            else -> choice.spiralMode
        }
        val settings = state.plateSettings.withPlateSettingsChoice(choice.copy(spiralMode = spiral))
        val objects = if (enabling && vaseSettingsAgreed) state.objectsForSpiralVase() else state.objects
        if (settings == state.plateSettings && objects == state.objects) return@update state
        state.copy(plateSettings = settings, objects = objects, result = null)
    }
}

/** PartPlate::get_spiral_vase_mode() of the current plate: its own, or the process preset's. */
fun PlateState.spiralVaseMode(): Boolean =
    plateSettings.plateSettingsChoice().spiralMode ?: (presetValue(PresetKind.PRINT, "spiral_mode") == "1")

/** A value of the edited preset of [kind], as its tab shows it. */
fun PlateState.presetValue(kind: PresetKind, key: String): String? =
    settingsTabs[kind]?.settings?.settings?.firstOrNull { it.key == key }?.value

/**
 * PartPlate::set_vase_mode_related_object_config(): the objects on the current
 * plate take the settings spiral vase mode needs where the process preset, or
 * the object itself, has others.
 */
private fun PlateState.objectsForSpiralVase(): List<PlateObject> {
    val applying = VASE_MODE_SETTINGS.filter { (key, value) -> presetValue(PresetKind.PRINT, key) != value }
    return objects.map { plateObject ->
        if (plateObject.instances.none { plateOf(it) == currentPlate }) return@map plateObject
        val own = VASE_MODE_SETTINGS.filter { (key, value) -> plateObject.settings.values[key]?.let { it != value } == true }
        plateObject.withSettings(ModelSettings(plateObject.settings.values + applying + own))
    }
}

/** The settings of set_vase_mode_related_object_config(), as a project writes them. */
private val VASE_MODE_SETTINGS = mapOf(
    "wall_loops" to "1",
    "top_shell_layers" to "0",
    "sparse_infill_density" to "0%",
    "enable_support" to "0",
    "enforce_support_layers" to "0",
    "detect_thin_wall" to "0",
    "timelapse_type" to "0",
    "overhang_reverse" to "0",
)

/**
 * The plate menu of the object list (MenuFactory::create_plate_menu) for the
 * current plate: "Select All" takes the objects the list shows under it
 * (Selection::add_curr_plate), "Select All Plates" every object
 * (Selection::add_all), and "Delete All" the objects whose first copy stands
 * on it, as one step of Undo (Selection::remove_curr_plate).
 */
class PlateObjectsUseCase(private val repository: PlateRepository) {
    fun selectCurrentPlate() = select { state -> state.objects.filter { state.listPlateOf(it) == state.currentPlate } }

    fun selectAll() = select { state -> state.objects }

    fun deleteCurrentPlate() = repository.update { state ->
        if (state.busy) return@update state
        val deleted = state.objects.filter { plateObject -> plateObject.instances.firstOrNull()?.let(state::plateOf) == state.currentPlate }
        if (deleted.isEmpty()) return@update state
        val meshes = deleted.mapTo(HashSet(), PlateObject::mesh)
        state.recorded().copy(
            objects = state.objects.filterNot { it.mesh in meshes },
            selectedInstances = emptySet(),
            selectedPart = null,
            selectedRange = null,
            result = null,
        )
    }

    private fun select(objects: (PlateState) -> List<PlateObject>) = repository.update { state ->
        val copies = objects(state).flatMapTo(LinkedHashSet()) { plateObject ->
            plateObject.instances.indices.map { PlateInstanceId(plateObject.mesh, it) }
        }
        state.copy(selectedInstances = copies, selectedPart = null, selectedRange = null)
    }
}
