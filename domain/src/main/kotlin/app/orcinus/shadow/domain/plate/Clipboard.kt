package app.orcinus.shadow.domain.plate

import app.orcinus.shadow.core.model.CopyPlacement
import app.orcinus.shadow.core.model.ModelLoadOutcome
import app.orcinus.shadow.core.model.ObjectPartId
import app.orcinus.shadow.core.model.PlateClipboard
import app.orcinus.shadow.core.model.PlateInstanceId
import app.orcinus.shadow.core.model.PlateObject
import app.orcinus.shadow.core.model.PlateProblem
import app.orcinus.shadow.core.model.PlateProblemKind
import app.orcinus.shadow.core.model.PlateState
import app.orcinus.shadow.core.model.SlicingProfileSelection
import app.orcinus.shadow.core.model.mesh
import app.orcinus.shadow.core.model.placing
import app.orcinus.shadow.core.model.withInstances
import app.orcinus.shadow.domain.placed
import app.orcinus.shadow.slicing.api.PlateInspector
import app.orcinus.shadow.storage.api.SceneFiles
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Plater::copy_selection_to_clipboard() and cut_selection_to_clipboard():
 * Selection::copy_to_clipboard() keeps copies of objects, or volumes of one
 * copy of an object, as objects of their own whose meshes outlive what they
 * were copied from, in place of what the clipboard held; Cut then erases what
 * it copied (Selection::erase).
 */
class CopyToClipboardUseCase(
    private val inspector: PlateInspector,
    private val sceneFiles: SceneFiles,
    private val repository: PlateRepository,
    private val removeObjectPart: RemoveObjectPartUseCase,
    private val applicationScope: CoroutineScope,
) {
    /** Copy, or Cut when [cut], of the [copies] of objects (Selection::Instance mode). */
    fun objects(copies: Set<PlateInstanceId>, cut: Boolean = false) {
        val copy = begin { state -> state.objects.copiesOf(copies) } ?: return
        applicationScope.launch {
            val kept = keep(copy) { added -> PlateClipboard.Objects(added) }
            if (kept && cut) erase(copies)
        }
    }

    /**
     * Copy, or Cut when [cut], of the [volumes] (ObjectPartId.index) of the
     * object over its copy [id] (Selection::Volume mode). Cut leaves the
     * object's own mesh, which the app cannot take out of it yet.
     */
    fun volumes(id: PlateInstanceId, volumes: Set<Int>, cut: Boolean = false) {
        val copy = begin { state ->
            val target = state.objects.withMesh(id.mesh)
            val instance = target?.instances?.getOrNull(id.instance)
            if (target == null || instance == null || volumes.isEmpty() || volumes.any { it !in 0..target.parts.size }) {
                emptyList()
            } else {
                listOf(target.withInstances(listOf(instance)))
            }
        } ?: return
        applicationScope.launch {
            val kept = keep(copy) { added -> PlateClipboard.Volumes(added.single(), volumes.sorted()) }
            if (kept && cut) volumes.filter { it > 0 }.sortedDescending().forEach { removeObjectPart(ObjectPartId(id.mesh, it)) }
        }
    }

    /** The plate starts copying [sources] of it, unless it is busy or they are none. */
    private fun begin(sources: (PlateState) -> List<PlateObject>): CopyRequest? {
        var request: CopyRequest? = null
        repository.update { state ->
            request = null
            val profiles = state.profiles
            val taken = if (state.busy || profiles == null || state.objects.any(PlateObject::placing)) emptyList() else sources(state)
            if (profiles == null || taken.isEmpty()) return@update state
            request = CopyRequest(state.objects, taken, profiles)
            state.copy(editing = true, problem = null)
        }
        return request
    }

    /**
     * The copies written anew become the clipboard; its former meshes go once
     * nothing refers to them (ObjectMeshRetention). Returns whether they did.
     */
    private suspend fun keep(request: CopyRequest, clipboard: (List<PlateObject>) -> PlateClipboard): Boolean {
        val added = copyObjects(inspector, sceneFiles, repository, request, 1, CopyPlacement.KEEP) { state, added ->
            state.copy(clipboard = clipboard(added))
        }
        return added.isNotEmpty()
    }

    /**
     * Selection::erase() of the copies, as one action ("Cut Selected Objects"):
     * an object whose every copy goes leaves the plate.
     */
    private fun erase(copies: Set<PlateInstanceId>) = repository.update { state ->
        if (state.busy) return@update state
        val taken = copies.groupBy(PlateInstanceId::mesh).mapValues { (_, ids) -> ids.mapTo(HashSet(), PlateInstanceId::instance) }
        val objects = state.objects.mapNotNull { plateObject ->
            val gone = taken[plateObject.mesh] ?: return@mapNotNull plateObject
            val kept = plateObject.instances.filterIndexed { index, _ -> index !in gone }
            if (kept.isEmpty()) null else plateObject.withInstances(kept)
        }
        if (objects == state.objects) return@update state
        state.recorded().copy(
            objects = objects,
            selectedInstances = emptySet(),
            selectedPart = null,
            selectedRange = null,
            result = null,
        )
    }
}

/**
 * Plater::paste_from_clipboard(): objects the clipboard holds join the plate,
 * each in the empty cell nearest to where it was copied from, and are selected
 * (Selection::paste_objects_from_clipboard); volumes it holds join the object
 * of the [target] copy (paste_volumes_from_clipboard), which then needs one.
 */
class PasteFromClipboardUseCase(
    private val inspector: PlateInspector,
    private val sceneFiles: SceneFiles,
    private val repository: PlateRepository,
    private val applicationScope: CoroutineScope,
) {
    operator fun invoke(target: PlateInstanceId? = null) {
        val state = repository.state.value
        when (val clipboard = state.clipboard) {
            is PlateClipboard.Objects -> pasteObjects(clipboard)
            is PlateClipboard.Volumes -> target?.let { pasteVolumes(clipboard, it) }
            null -> Unit
        }
    }

    private fun pasteObjects(clipboard: PlateClipboard.Objects) {
        // Plater::paste_from_clipboard() takes "Paste From Clipboard" first.
        var request: CopyRequest? = null
        repository.update { state ->
            request = null
            val profiles = state.profiles
            if (state.busy || profiles == null || state.objects.any(PlateObject::placing)) return@update state
            request = CopyRequest(state.objects, clipboard.objects, profiles)
            state.copy(editing = true, problem = null)
        }
        val copy = request ?: return
        applicationScope.launch {
            copyObjects(inspector, sceneFiles, repository, copy, 1, CopyPlacement.PASTE) { state, added ->
                // ObjectList::paste_objects_into_list() selects them.
                state.recorded().copy(
                    objects = state.objects + added,
                    selectedInstances = added.allCopies(),
                    selectedPart = null,
                    selectedRange = null,
                    result = null,
                )
            }
        }
    }

    private fun pasteVolumes(clipboard: PlateClipboard.Volumes, target: PlateInstanceId) {
        var request: Pair<List<PlateObject>, SlicingProfileSelection>? = null
        repository.update { state ->
            request = null
            val profiles = state.profiles
            val into = state.objects.withMesh(target.mesh)
            if (state.busy || profiles == null || into == null || into.placing || target.instance !in into.instances.indices) {
                return@update state
            }
            request = state.objects to profiles
            state.copy(editing = true, problem = null)
        }
        val (plate, profiles) = request ?: return
        applicationScope.launch {
            val index = plate.indexOfFirst { it.mesh == target.mesh }
            val into = plate[index]
            val prefix = sceneFiles.newImportPrefix()
            val outcome = try {
                inspector.pasteVolumes(
                    plate.map { it.placed() },
                    index,
                    target.instance,
                    clipboard.source.placed(),
                    clipboard.volumes,
                    sameInputFile = clipboard.source.inputFile() == into.inputFile(),
                    profiles,
                    prefix,
                )
            } catch (cancellation: CancellationException) {
                sceneFiles.deleteImport(prefix)
                repository.update { it.copy(editing = false) }
                throw cancellation
            } catch (error: Exception) {
                ModelLoadOutcome.Failure(error.message.orEmpty())
            }
            val pasted = (outcome as? ModelLoadOutcome.Success)?.objects?.singleOrNull()?.toPlateObjectOf(into)
            if (pasted == null) sceneFiles.deleteImport(prefix)
            repository.update { state ->
                val done = state.copy(editing = false)
                when {
                    outcome is ModelLoadOutcome.Failure -> done.copy(problem = PlateProblem(PlateProblemKind.PLACEMENT_FAILED, outcome.message))
                    pasted == null || state.objects.withMesh(into.mesh) == null -> done
                    else -> {
                        val selected = (outcome as ModelLoadOutcome.Success).selectedVolume
                        done.recorded().copy(
                            objects = state.objects.map { if (it.mesh == into.mesh) pasted else it },
                            // ObjectList::paste_volumes_into_list() selects the pasted volumes.
                            selectedInstances = setOf(PlateInstanceId(pasted.mesh, target.instance)),
                            selectedPart = selected?.let { ObjectPartId(pasted.mesh, it) },
                            selectedRange = null,
                            result = null,
                        )
                    }
                }
            }
        }
    }
}
