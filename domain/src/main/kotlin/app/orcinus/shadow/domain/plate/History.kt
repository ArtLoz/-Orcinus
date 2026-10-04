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
    layerEditing = layerEditing,
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
            // Plater::priv::undo_redo_to(): the variable layer height is on after
            // the jump as it was when the snapshot was taken, where it is allowed.
            layerEditing = target.layerEditing,
        ).withPlates(plates, target.currentPlate.coerceIn(plates.indices)).let { restored ->
            if (restored.layerEditing && restored.layerEditingObject() == null) restored.copy(layerEditing = false) else restored
        }
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

/**
 * UndoRedo::Stack::release_least_recently_used(), which runs once a snapshot is
 * taken and once the plate moved through the stack: while the stack holds more
 * than its limit — a tenth of the device's memory, at most 1 GiB
 * (StackImpl::m_memory_limit) — its oldest snapshot goes; the snapshot just
 * before the active state stays, as the desktop does not release the last one
 * to undo. The app keeps the stack's meshes as files, and their size is what
 * the stack holds.
 */
class UndoStackMemoryLimit(
    private val repository: PlateRepository,
    private val sceneFiles: SceneFiles,
    private val limitBytes: Long,
) {
    suspend fun run() {
        repository.state.map { it.history }.distinctUntilChanged().collect(::release)
    }

    private fun release(history: PlateHistory) {
        val others = history.redo + listOfNotNull(history.beforeTool)
        var undo = history.undo
        // m_snapshots: the undo snapshots, the active state and the redo ones.
        while (memsize(undo + others) > limitBytes && undo.size + 1 + history.redo.size >= 3 && undo.size > 1) {
            undo = undo.drop(1)
        }
        val released = history.undo.size - undo.size
        if (released == 0) return
        repository.update { state ->
            // The stack moved on meanwhile: the next change of it is judged again.
            if (state.history.undo.firstOrNull() !== history.undo.first()) return@update state
            state.copy(history = state.history.copy(undo = state.history.undo.drop(released)))
        }
    }

    /** StackImpl::memsize(): every mesh the snapshots hold, once. */
    private fun memsize(snapshots: List<PlateSnapshot>): Long =
        snapshots.flatMapTo(HashSet()) { snapshot -> snapshot.objects.flatMap { it.files() } }.sumOf(sceneFiles::sizeOf)

    companion object {
        /** StackImpl(): min(total_physical_memory() / 10, 1 GiB). */
        fun limitFor(totalMemoryBytes: Long): Long = minOf(totalMemoryBytes / 10, 1L * 16384 * 65536)
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
        part.emboss?.let { add(it.file) }
    }
    paintedMeshes.forEach { add(it.mesh) }
    painted.file?.let(::add)
    volume.emboss?.let { add(it.file) }
    if (this@files is PlateObject.ImportedModel) add(ScenePath(file.path.value))
}
