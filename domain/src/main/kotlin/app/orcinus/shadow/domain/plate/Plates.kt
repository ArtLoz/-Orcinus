package app.orcinus.shadow.domain.plate

import app.orcinus.shadow.core.model.EnginePlate
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
        val grid = state.plateGrid
        val count = state.plates.size
        if (!state.canDeletePlate || index !in state.plates.indices || grid == null) return@update state
        val moves = List(count) { plate ->
            when {
                plate == index -> grid.unprintableOrigin(count - 1) - grid.originOf(plate, count)
                plate > index -> grid.originOf(plate - 1, count - 1) - grid.originOf(plate, count)
                else -> grid.originOf(plate, count - 1) - grid.originOf(plate, count)
            }
        }
        val unprintable = grid.unprintableOrigin(count - 1) - grid.unprintableOrigin(count)
        val left = state.platesLeft()
        val plates = left.filterIndexed { plate, _ -> plate != index }
            .mapIndexed { plate, kept -> kept.copy(layerGcodes = left[plate].layerGcodes) }
        val current = when {
            state.currentPlate == index && index == 0 -> 0
            state.currentPlate >= index -> state.currentPlate - 1
            else -> state.currentPlate
        }
        state.recorded()
            .copy(objects = state.objectsMoved(moves, unprintable))
            .withPlates(plates, current)
    }
}

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
        var judge = false
        repository.update { state ->
            judge = false
            if (EnginePlate(state.currentPlate, state.plates.size) != plate || state.enginePlate == plate) return@update state
            judge = state.objects.isNotEmpty()
            state.copy(enginePlate = plate)
        }
        if (judge) placePlateObjects(PlateManipulation.UpdatePrintVolume)
    }
}

/** Plater::can_add_plate(): fewer plates than PartPlateList::MAX_PLATES_COUNT, and nothing else going on. */
val PlateState.canAddPlate: Boolean get() = canChangePlates && plates.size < PlateGrid.MAX_PLATES && plateGrid != null

/** Plater::can_delete_plate(): more than one plate. */
val PlateState.canDeletePlate: Boolean get() = canChangePlates && plates.size > 1 && plateGrid != null

private val PlateState.canChangePlates: Boolean
    get() = !busy && objects.none(PlateObject::placing) && history.beforeTool == null && simplifyTarget == null

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
