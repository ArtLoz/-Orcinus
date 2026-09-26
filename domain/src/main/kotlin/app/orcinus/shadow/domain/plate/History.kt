package app.orcinus.shadow.domain.plate

import app.orcinus.shadow.core.model.PlateClipboard
import app.orcinus.shadow.core.model.PartPlate
import app.orcinus.shadow.core.model.partPlates
import app.orcinus.shadow.core.model.PlateHistory
import app.orcinus.shadow.core.model.PlateInstanceId
import app.orcinus.shadow.core.model.PlateManipulation
import app.orcinus.shadow.core.model.PlateObject
import app.orcinus.shadow.core.model.PlateSnapshot
import app.orcinus.shadow.core.model.PlateState
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.mesh
import app.orcinus.shadow.core.model.withInstances
import app.orcinus.shadow.storage.api.SceneFiles
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** The plate as the undo/redo stack keeps it: settled, as a snapshot is taken between actions. */
internal fun PlateState.snapshot() = PlateSnapshot(
    objects = objects.map { plateObject -> plateObject.withInstances(plateObject.instances.map { it.copy(placing = false) }) },
    selectedInstances = selectedInstances,
    selectedPart = selectedPart,
    selectedRange = selectedRange,
    plates = partPlates().map { PartPlate(name = it.name, locked = it.locked, settings = it.settings) },
    currentPlate = currentPlate,
)

/**
 * Plater::TakeSnapshot before an action changes the plate: [before], the plate
 * as it is unless an open tool kept an earlier state, joins the undo stack, and
 * the states Undo left are gone (UndoRedo::Stack::take_snapshot drops the
 * snapshots after the active one).
 */
internal fun PlateState.recorded(before: PlateSnapshot = snapshot()) =
    copy(history = history.copy(undo = history.undo + before, redo = emptyList()))

/**
 * Plater::undo() and redo(): the plate goes back to the state before the last
 * action, or forward to the one Undo left, with the selection it had then.
 * Snapshots of a mere selection change are not kept, since OrcaSlicer skips
 * them anyway (undo-redo until a modifying snapshot). The objects are then
 * judged against the build volume of the printer selected now, and the
 * settings tabs describe what the plate holds again.
 */
class UndoRedoPlateUseCase(
    private val repository: PlateRepository,
    private val placePlateObjects: PlacePlateObjectsUseCase,
    private val settingsTabs: PresetSettingsTabs,
    private val applicationScope: CoroutineScope,
) {
    fun undo() = move(undo = true) { history, current ->
        history.undo.lastOrNull()?.let { target -> target to history.copy(undo = history.undo.dropLast(1), redo = listOf(current) + history.redo) }
    }

    fun redo() = move(undo = false) { history, current ->
        history.redo.firstOrNull()?.let { target -> target to history.copy(undo = history.undo + current, redo = history.redo.drop(1)) }
    }

    private fun move(undo: Boolean, step: (PlateHistory, PlateSnapshot) -> Pair<PlateSnapshot, PlateHistory>?) {
        var moved = false
        repository.update { state ->
            moved = false
            val possible = if (undo) state.canUndo else state.canRedo
            if (!possible) return@update state
            val (target, history) = step(state.history, state.snapshot()) ?: return@update state
            moved = true
            state.restored(target, history)
        }
        if (!moved) return
        // Plater::priv::undo_redo_to() reloads the scene, which judges every copy against the build volume.
        if (repository.state.value.objects.isNotEmpty()) placePlateObjects(PlateManipulation.UpdatePrintVolume)
        applicationScope.launch { settingsTabs.refresh() }
    }

    private fun PlateState.restored(target: PlateSnapshot, history: PlateHistory): PlateState {
        val copies = target.objects.flatMapTo(HashSet()) { plateObject -> plateObject.instances.indices.map { PlateInstanceId(plateObject.mesh, it) } }
        val now = platesLeft()
        val plates = target.plates.mapIndexed { index, kept ->
            val plate = now.getOrNull(index)
            val settings = kept.settings.withFlushVolumesOf(plateSettings)
            PartPlate(
                name = kept.name,
                locked = kept.locked,
                settings = settings,
                // The codes on the layers are the model's, by plate index, which the stack leaves alone.
                layerGcodes = plate?.layerGcodes.orEmpty(),
                // G-code sliced before applies only while the plate prints the same.
                result = plate?.result?.takeIf { objects == target.objects && plate.settings == settings },
                basis = plate?.basis,
            )
        }
        return copy(
            objects = target.objects,
            selectedInstances = target.selectedInstances.filterTo(LinkedHashSet()) { it in copies },
            selectedPart = target.selectedPart,
            selectedRange = target.selectedRange,
            history = history,
        ).withPlates(plates, target.currentPlate.coerceIn(plates.indices))
    }
}

/**
 * The meshes of objects stay on disk while the plate, its undo/redo stack or
 * the clipboard refers to them, as the desktop app keeps a volume's mesh while
 * a snapshot holds it; a mesh nothing refers to any more is deleted. Files an
 * import or an edit is still writing are nobody's yet and are left alone.
 */
class ObjectMeshRetention(
    private val repository: PlateRepository,
    private val sceneFiles: SceneFiles,
) {
    suspend fun run() {
        var kept = emptySet<ScenePath>()
        repository.state.map { it.referencedMeshes() }.distinctUntilChanged().collect { referenced ->
            (kept - referenced).forEach(sceneFiles::deleteObjectMesh)
            kept = referenced
        }
    }
}

/** Every mesh file the plate, its history and its clipboard refer to. */
internal fun PlateState.referencedMeshes(): Set<ScenePath> = buildSet {
    objects.forEach { addAll(it.files()) }
    (history.undo + history.redo + listOfNotNull(history.beforeTool)).forEach { snapshot -> snapshot.objects.forEach { addAll(it.files()) } }
    when (val kept = clipboard) {
        is PlateClipboard.Objects -> kept.objects.forEach { addAll(it.files()) }
        is PlateClipboard.Volumes -> addAll(kept.source.files())
        null -> Unit
    }
}

/** The files an object is drawn and loaded from, its painted facets among them. */
internal fun PlateObject.files(): Set<ScenePath> = buildSet {
    instances.forEach { add(it.inspection.mesh) }
    parts.forEach { part ->
        add(part.mesh)
        part.source?.let { add(ScenePath(it.value)) }
        part.painted.file?.let(::add)
    }
    paintedMeshes.forEach { add(it.mesh) }
    painted.file?.let(::add)
    if (this@files is PlateObject.ImportedModel) add(ScenePath(file.path.value))
}
