package app.orcinus.shadow.domain.plate

import app.orcinus.shadow.core.model.ArrangeSettings
import app.orcinus.shadow.core.model.CopyPlacement
import app.orcinus.shadow.core.model.ModelLoadOutcome
import app.orcinus.shadow.core.model.PlateInstanceId
import app.orcinus.shadow.core.model.PlateManipulation
import app.orcinus.shadow.core.model.PlateObject
import app.orcinus.shadow.core.model.PlateProblem
import app.orcinus.shadow.core.model.PlateProblemKind
import app.orcinus.shadow.core.model.PlateState
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.SlicingProfileSelection
import app.orcinus.shadow.core.model.mesh
import app.orcinus.shadow.core.model.placing
import app.orcinus.shadow.core.model.withCutId
import app.orcinus.shadow.core.model.withInstances
import app.orcinus.shadow.domain.placed
import app.orcinus.shadow.slicing.api.PlateInspector
import app.orcinus.shadow.storage.api.SceneFiles
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** The arrange options window (_render_arrange_menu): the settings every arrangement of the plate takes. */
class SetArrangeSettingsUseCase(private val repository: PlateRepository) {
    operator fun invoke(settings: ArrangeSettings) = repository.update { state ->
        state.copy(arrangeSettings = settings.copy(distance = settings.distance.coerceIn(0.0, MAX_ARRANGE_DISTANCE)))
    }

    private companion object {
        /** The arrange options' spacing slider ends at 100 mm. */
        const val MAX_ARRANGE_DISTANCE = 100.0
    }
}

/**
 * The clone dialog's OK (CloneDialog): Selection::copy_to_clipboard() of the
 * [copies] and paste_from_clipboard() [count] times. Each paste puts a new
 * object for every cloned one into the empty cell of the plate nearest to it
 * and selects them, and with [arrange] ("Auto arrange plate after cloning")
 * the plate is arranged afterwards, as ArrangeJob from the menu arranges it.
 */
class ClonePlateObjectsUseCase(
    private val inspector: PlateInspector,
    private val sceneFiles: SceneFiles,
    private val repository: PlateRepository,
    private val placePlateObjects: PlacePlateObjectsUseCase,
    private val applicationScope: CoroutineScope,
) {
    operator fun invoke(copies: Set<PlateInstanceId>, count: Int, arrange: Boolean) {
        var request: CopyRequest? = null
        repository.update { state ->
            request = null
            val profiles = state.profiles
            // Selection::copy_to_clipboard(): the clones are no part of a cut.
            val sources = state.objects.copiesOf(copies).map { it.withCutId(null) }
            if (state.busy || profiles == null || count <= 0 || sources.isEmpty() || state.objects.any(PlateObject::placing)) {
                return@update state
            }
            request = CopyRequest(state.objects, sources, profiles)
            state.copy(editing = true, problem = null)
        }
        val copy = request ?: return
        applicationScope.launch {
            val added = copyObjects(inspector, sceneFiles, repository, copy, count, CopyPlacement.PASTE) { state, added ->
                // Every paste selects its objects, so the last one's stay selected.
                state.recorded().copy(
                    objects = state.objects + added,
                    selectedInstances = added.takeLast(copy.sources.size).allCopies(),
                    selectedPart = null,
                    selectedRange = null,
                    // G-code sliced before no longer applies once more objects print.
                    result = null,
                )
            }
            if (added.isNotEmpty() && arrange) {
                placePlateObjects(PlateManipulation.ArrangePlate(repository.state.value.arrangeSettings))
            }
        }
    }
}

/**
 * ObjectList::split_instances(), "Set as an individual object": the chosen
 * [instances] of the object with the [mesh] file leave it as one new object
 * (instances_to_separated_object), or, when they are all of its copies, every
 * copy but the first becomes an object of its own, the last one first
 * (instances_to_separated_objects). The object keeps its other copies and is
 * selected, as the object list selects it once their rows are gone.
 */
class SeparatePlateInstancesUseCase(
    private val inspector: PlateInspector,
    private val sceneFiles: SceneFiles,
    private val repository: PlateRepository,
    private val applicationScope: CoroutineScope,
) {
    operator fun invoke(mesh: ScenePath, instances: Set<Int>) {
        var request: CopyRequest? = null
        var kept: PlateObject? = null
        repository.update { state ->
            request = null
            val profiles = state.profiles
            val target = state.objects.withMesh(mesh)
            // Plater::can_set_instance_to_object(): an object with more than one copy.
            if (state.busy || profiles == null || target == null || target.placing || target.instances.size <= 1 ||
                instances.isEmpty() || instances.any { it !in target.instances.indices }
            ) {
                return@update state
            }
            val all = instances.size == target.instances.size
            val sources = if (all) {
                (target.instances.lastIndex downTo 1).map { target.withInstances(listOf(target.instances[it])) }
            } else {
                listOf(target.withInstances(target.instances.filterIndexed { index, _ -> index in instances }))
            }
            kept = target.withInstances(if (all) target.instances.take(1) else target.instances.filterIndexed { index, _ -> index !in instances })
            request = CopyRequest(state.objects, sources, profiles)
            state.copy(editing = true, problem = null)
        }
        val copy = request ?: return
        val remaining = kept ?: return
        applicationScope.launch {
            copyObjects(inspector, sceneFiles, repository, copy, 1, CopyPlacement.KEEP) { state, added ->
                state.recorded().copy(
                    objects = state.objects.replaced(remaining) + added,
                    selectedInstances = listOf(remaining).allCopies(),
                    selectedPart = null,
                    selectedRange = null,
                    result = null,
                )
            }
        }
    }
}

/**
 * "Fill bed with instances", also the clone dialog's Fill: FillBedJob adds
 * copies of the object with the [mesh] file, modelled on its copy [instance]
 * (null for the whole object), while the free area of the plate holds more,
 * and then arranges the plate.
 */
class FillBedWithInstancesUseCase(
    private val repository: PlateRepository,
    private val placePlateObjects: PlacePlateObjectsUseCase,
) {
    operator fun invoke(mesh: ScenePath, instance: Int? = null) {
        val state = repository.state.value
        val target = state.objects.withMesh(mesh) ?: return
        // Plater::can_increase_instances()
        if (!target.instances.all { it.printable }) return
        placePlateObjects(PlateManipulation.FillBed(mesh, instance, state.arrangeSettings))
    }
}

/** What copy_objects() is asked for: the plate as it was, the objects copied, and the presets. */
internal data class CopyRequest(
    val plate: List<PlateObject>,
    val sources: List<PlateObject>,
    val profiles: SlicingProfileSelection,
)

/** The objects of [copies], in the plate's order, each with only its copies among them, in its order. */
internal fun List<PlateObject>.copiesOf(copies: Set<PlateInstanceId>): List<PlateObject> = mapNotNull { plateObject ->
    val taken = plateObject.instances.filterIndexed { index, _ -> PlateInstanceId(plateObject.mesh, index) in copies }
    if (taken.isEmpty()) null else plateObject.withInstances(taken)
}

/**
 * copy_objects() for [request], which ends the edit of the plate begun for it:
 * [apply] takes the new objects, named after the document of the object each
 * was copied from. Returns them, or none when the engine failed, whose message
 * the plate shows.
 */
internal suspend fun copyObjects(
    inspector: PlateInspector,
    sceneFiles: SceneFiles,
    repository: PlateRepository,
    request: CopyRequest,
    count: Int,
    placement: CopyPlacement,
    apply: (PlateState, List<PlateObject>) -> PlateState,
): List<PlateObject> {
    val prefix = sceneFiles.newImportPrefix()
    val outcome = try {
        inspector.copy(request.plate.map { it.placed() }, request.sources.map { it.placed() }, count, placement, request.profiles, prefix)
    } catch (cancellation: CancellationException) {
        sceneFiles.deleteImport(prefix)
        repository.update { it.copy(editing = false) }
        throw cancellation
    } catch (error: Exception) {
        ModelLoadOutcome.Failure(error.message.orEmpty())
    }
    val loaded = (outcome as? ModelLoadOutcome.Success)?.objects.orEmpty()
    if (loaded.isEmpty()) sceneFiles.deleteImport(prefix)
    // The copies come round after round, each in the order of the objects copied.
    val added = loaded.mapIndexed { index, copy ->
        val source = request.sources[index % request.sources.size]
        copy.toPlateObjectOf(source)
    }
    repository.update { state ->
        val done = state.copy(editing = false)
        when {
            outcome is ModelLoadOutcome.Failure -> done.copy(problem = PlateProblem(PlateProblemKind.PLACEMENT_FAILED, outcome.message))
            added.isNotEmpty() -> apply(done, added)
            else -> done
        }
    }
    return added
}
