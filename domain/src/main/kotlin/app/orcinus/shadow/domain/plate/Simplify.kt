package app.orcinus.shadow.domain.plate

import app.orcinus.shadow.core.model.DialogIcon
import app.orcinus.shadow.core.model.LoadedObject
import app.orcinus.shadow.core.model.ModelLoadOutcome
import app.orcinus.shadow.core.model.ObjectPartId
import app.orcinus.shadow.core.model.OrcaText
import app.orcinus.shadow.core.model.PlateInstanceId
import app.orcinus.shadow.core.model.PlateProblem
import app.orcinus.shadow.core.model.PlateProblemKind
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.SettingsDialog
import app.orcinus.shadow.core.model.SimplifyConfig
import app.orcinus.shadow.core.model.SimplifyOutcome
import app.orcinus.shadow.core.model.SlicingProfileSelection
import app.orcinus.shadow.core.model.mesh
import app.orcinus.shadow.core.model.placing
import app.orcinus.shadow.core.model.volumeAt
import app.orcinus.shadow.domain.placed
import app.orcinus.shadow.slicing.api.PlateInspector
import app.orcinus.shadow.storage.api.SceneFiles
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The object menu's "Simplify Model" (ObjectList::simplify): the Simplify
 * gizmo opens on one volume, the only selection it works on
 * (GLGizmoSimplify's get_model_volume()); over an object of several volumes,
 * or of several copies selected together, OrcaSlicer says so instead.
 */
class OpenSimplifyUseCase(private val repository: PlateRepository) {
    /** The object of [copy], selected as the canvas selects a copy, or whole ([wholeObject]) as the object list does. */
    fun ofObject(copy: PlateInstanceId, wholeObject: Boolean) = repository.update { state ->
        val target = state.objects.withMesh(copy.mesh)
        if (target == null || state.simplifyTarget != null) return@update state
        if (target.parts.isNotEmpty() || (wholeObject && target.instances.size > 1)) {
            return@update state.copy(plateNotices = state.plateNotices + SINGLE_PART_ONLY)
        }
        state.copy(
            simplifyTarget = ObjectPartId(copy.mesh, 0),
            selectedInstances = if (wholeObject) listOf(target).allCopies() else setOf(copy),
            selectedPart = null,
            selectedRange = null,
            // GLGizmoSimplify::on_render_input_window(): remove_simplify_suggestion_with_id().
            simplifySuggestions = state.simplifySuggestions - copy.mesh,
        )
    }

    /** A volume of an object, as its row of the object list selects it. */
    fun ofVolume(id: ObjectPartId) = repository.update { state ->
        val target = state.objects.withMesh(id.mesh)
        if (target?.volumeAt(id.index) == null || state.simplifyTarget != null) return@update state
        state.copy(
            simplifyTarget = id,
            selectedInstances = setOf(PlateInstanceId(id.mesh)),
            selectedPart = id,
            selectedRange = null,
            simplifySuggestions = state.simplifySuggestions - id.mesh,
        )
    }

    /**
     * "Simplify model" of the advice to simplify an object: the selection
     * becomes the object (Selection::add_object()) and the gizmo opens on it.
     */
    fun suggested(mesh: ScenePath) = ofObject(PlateInstanceId(mesh), wholeObject = true)

    /** GLGizmoSimplify::close(): Cancel, or the volume is gone. */
    fun close() = repository.update { state -> if (state.simplifyTarget == null) state else state.copy(simplifyTarget = null) }

    /**
     * GLGizmosManager::check_gizmos_closed_except(): another tool of the
     * canvas is open, so the gizmo does not open, and OrcaSlicer says why.
     */
    fun refuse() = repository.update { state ->
        if (state.simplifyTarget == null) state else state.copy(simplifyTarget = null, plateNotices = state.plateNotices + TOOLS_OPEN)
    }

    private companion object {
        /** The notification of check_gizmos_closed_except(). */
        val TOOLS_OPEN = SettingsDialog(
            id = "gizmos_open",
            icon = DialogIcon.INFO,
            title = emptyList(),
            text = listOf(OrcaText("Error: Please close all toolbar menus first")),
            question = false,
            yes = null,
            no = null,
        )

        /** GLGizmoSimplify::on_render_input_window() when the selection is not a single volume. */
        val SINGLE_PART_ONLY = SettingsDialog(
            id = "simplify_single_part",
            icon = DialogIcon.INFO,
            title = listOf(OrcaText("Error")),
            text = listOf(OrcaText("Simplification is currently only allowed when a single part is selected")),
            question = false,
            yes = null,
            no = null,
        )
    }
}

/** A decimated mesh the Simplify gizmo draws in place of its volume, in a file of its own. */
data class SimplifyPreview(
    val mesh: ScenePath,
    /** The triangles of the decimated mesh, and of the volume. */
    val triangles: Long,
    val original: Long,
    internal val prefix: ScenePath,
)

/**
 * GLGizmoSimplify::process(): the volume's mesh decimated for the gizmo's
 * preview. The file the engine writes stays until [discard] drops it.
 */
class PreviewSimplifyUseCase(
    private val inspector: PlateInspector,
    private val sceneFiles: SceneFiles,
    private val repository: PlateRepository,
) {
    suspend operator fun invoke(volume: ObjectPartId, config: SimplifyConfig): SimplifyPreview? {
        val state = repository.state.value
        val index = state.objects.indexOfFirst { it.mesh == volume.mesh }
        val profiles = state.profiles
        if (index < 0 || profiles == null) return null
        val prefix = sceneFiles.newImportPrefix()
        val mesh = ScenePath("${prefix.value}-simplified.mesh")
        val outcome = try {
            inspector.simplifyVolume(state.objects.map { it.placed() }, index, volume.index, config, profiles, mesh)
        } catch (cancellation: CancellationException) {
            sceneFiles.deleteImport(prefix)
            throw cancellation
        } catch (error: Exception) {
            SimplifyOutcome.Failure(error.message.orEmpty())
        }
        if (outcome !is SimplifyOutcome.Success) {
            sceneFiles.deleteImport(prefix)
            return null
        }
        return SimplifyPreview(mesh, outcome.triangles, outcome.original, prefix)
    }

    fun discard(preview: SimplifyPreview) = sceneFiles.deleteImport(preview.prefix)
}

/**
 * GLGizmoSimplify::apply_simplify(): the volume takes its decimated mesh, as
 * one step of Undo ("Simplify"), the object rests on the plate, and the gizmo
 * closes. The selection stays on the object; the engine's notices say what
 * happened to its painting.
 */
class ApplySimplifyUseCase(
    private val inspector: PlateInspector,
    private val sceneFiles: SceneFiles,
    private val repository: PlateRepository,
    private val applicationScope: CoroutineScope,
) {
    operator fun invoke(volume: ObjectPartId, config: SimplifyConfig) {
        var profiles: SlicingProfileSelection? = null
        repository.update { state ->
            profiles = null
            val target = state.objects.withMesh(volume.mesh)
            if (state.busy || state.profiles == null || target == null || target.placing || state.simplifyTarget != volume) {
                return@update state
            }
            profiles = state.profiles
            state.copy(editing = true, problem = null)
        }
        val selection = profiles ?: return
        applicationScope.launch {
            val plate = repository.state.value.objects
            val index = plate.indexOfFirst { it.mesh == volume.mesh }
            val old = plate.getOrNull(index)
            val prefix = sceneFiles.newImportPrefix()
            val outcome = if (old == null) {
                ModelLoadOutcome.Failure("The object is not on the plate")
            } else {
                try {
                    inspector.applySimplify(plate.map { it.placed() }, index, volume.index, config, selection, prefix)
                } catch (cancellation: CancellationException) {
                    sceneFiles.deleteImport(prefix)
                    repository.update { it.copy(editing = false) }
                    throw cancellation
                } catch (error: Exception) {
                    ModelLoadOutcome.Failure(error.message.orEmpty())
                }
            }
            val simplified = old?.let { (outcome as? ModelLoadOutcome.Success)?.objects?.singleOrNull()?.toPlateObjectOf(it) }
            if (simplified == null) sceneFiles.deleteImport(prefix)
            repository.update { state ->
                val done = state.copy(editing = false, plateNotices = state.plateNotices + outcome.notices)
                when {
                    outcome is ModelLoadOutcome.Failure -> done.copy(problem = PlateProblem(PlateProblemKind.PLACEMENT_FAILED, outcome.message))
                    simplified == null || state.objects.withMesh(volume.mesh) == null -> done
                    else -> {
                        val mesh = simplified.mesh
                        done.recorded().copy(
                            objects = state.objects.map { if (it.mesh == volume.mesh) simplified else it },
                            // The selection follows the object to its new files.
                            selectedInstances = state.selectedInstances.mapTo(LinkedHashSet()) {
                                if (it.mesh == volume.mesh) PlateInstanceId(mesh, it.instance) else it
                            },
                            selectedPart = state.selectedPart?.let { if (it.mesh == volume.mesh) ObjectPartId(mesh, it.index) else it },
                            simplifyTarget = null,
                            result = null,
                        )
                    }
                }
            }
        }
    }
}

/**
 * add_simplify_suggestion_notification()'s is_big_object(): an object of a
 * single volume with a million triangles or more.
 */
internal fun LoadedObject.suggestsSimplify(): Boolean =
    parts.isEmpty() && (instances.firstOrNull()?.inspection?.facetCount ?: 0L) >= SIMPLIFY_SUGGESTION_TRIANGLES

/** add_simplify_suggestion_notification()'s triangles_to_suggest_simplify. */
private const val SIMPLIFY_SUGGESTION_TRIANGLES = 1_000_000L
