package app.orcinus.shadow.domain.plate

import app.orcinus.shadow.core.model.ImportedModelFile
import app.orcinus.shadow.core.model.LoadedObject
import app.orcinus.shadow.core.model.ModelLoadOutcome
import app.orcinus.shadow.core.model.ObjectCut
import app.orcinus.shadow.core.model.ObjectEdit
import app.orcinus.shadow.core.model.PendingPlateQuestion
import app.orcinus.shadow.core.model.PlateInstanceId
import app.orcinus.shadow.core.model.PlateObject
import app.orcinus.shadow.core.model.PlateProblem
import app.orcinus.shadow.core.model.PlateProblemKind
import app.orcinus.shadow.core.model.PlateRequest
import app.orcinus.shadow.core.model.PresetKind
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.SettingsDialog
import app.orcinus.shadow.core.model.SettingsRequest
import app.orcinus.shadow.core.model.mesh
import app.orcinus.shadow.core.model.withName
import app.orcinus.shadow.domain.placed
import app.orcinus.shadow.slicing.api.PlateInspector
import app.orcinus.shadow.storage.api.SceneFiles
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The object menu's commands that change the meshes of an object (split to
 * objects or parts, fix, convert units): OrcaSlicer changes the object and
 * writes the meshes of what came of it, which then take its place, or join
 * the end of the plate's list when the command loads them anew
 * (load_model_objects). The objects that came of it are selected, as the
 * desktop app selects them. The plate is busy meanwhile; a question waits on
 * the plate for [answer]; the message boxes it showed wait to be dismissed.
 */
class EditPlateObjectUseCase(
    private val inspector: PlateInspector,
    private val sceneFiles: SceneFiles,
    private val repository: PlateRepository,
    private val applicationScope: CoroutineScope,
) {
    /** [edit] of the object with the [mesh] file, or of its volume at [volume] (ObjectPartId.index). */
    operator fun invoke(mesh: ScenePath, edit: ObjectEdit, volume: Int? = null) = start(PlateRequest.Edit(mesh, edit, volume))

    /**
     * GLGizmoCut3D::perform_cut(): the object with the [mesh] file cut by
     * [cut]; the parts it keeps join the end of the plate's list, selected.
     */
    fun cut(mesh: ScenePath, cut: ObjectCut) = start(PlateRequest.Edit(mesh, ObjectEdit.CUT, cut = cut))

    private fun start(request: PlateRequest.Edit) {
        var started = false
        repository.update { state ->
            started = !state.busy && state.profiles != null && state.objects.withMesh(request.mesh) != null
            if (started) state.copy(editing = true, problem = null) else state
        }
        if (!started) return
        applicationScope.launch { run(request, emptyMap(), emptyList()) }
    }

    /** The answer to the question the command asked: it runs again with every answer so far. */
    fun answer(yes: Boolean) {
        var pending: PendingPlateQuestion? = null
        repository.update { state ->
            pending = state.plateQuestion?.takeIf { it.request is PlateRequest.Edit }
            if (pending == null) state else state.copy(plateQuestion = null)
        }
        val question = pending ?: return
        val request = question.request as PlateRequest.Edit
        applicationScope.launch { run(request, question.answers + (question.question.id to yes), question.shown) }
    }

    private suspend fun run(request: PlateRequest.Edit, answers: Map<String, Boolean>, shown: List<SettingsDialog>) {
        val state = repository.state.value
        val index = state.objects.indexOfFirst { it.mesh == request.mesh }
        val profiles = state.profiles
        if (index < 0 || profiles == null) return finish(request, ModelLoadOutcome.Failure("The object is not on the plate"), answers, shown)
        val prefix = sceneFiles.newImportPrefix()
        val outcome = try {
            inspector.edit(state.objects.map { it.placed() }, index, request.edit, request.volume, profiles, prefix, answers, request.cut)
        } catch (cancellation: CancellationException) {
            sceneFiles.deleteImport(prefix)
            throw cancellation
        } catch (error: Exception) {
            ModelLoadOutcome.Failure(error.message.orEmpty())
        }
        if (outcome !is ModelLoadOutcome.Success || outcome.objects.isEmpty()) sceneFiles.deleteImport(prefix)
        finish(request, outcome, answers, shown)
    }

    private fun finish(request: PlateRequest.Edit, outcome: ModelLoadOutcome, answers: Map<String, Boolean>, shown: List<SettingsDialog>) {
        repository.update { state ->
            val notices = outcome.notices.filterNot { it in shown }
            val informed = state.copy(plateNotices = state.plateNotices + notices)
            when (outcome) {
                is ModelLoadOutcome.Question -> informed.copy(
                    plateQuestion = PendingPlateQuestion(request, outcome.question, answers, shown + notices),
                )
                is ModelLoadOutcome.Failure -> informed.copy(
                    editing = false,
                    problem = PlateProblem(PlateProblemKind.PLACEMENT_FAILED, outcome.message),
                )
                is ModelLoadOutcome.Success -> {
                    val old = state.objects.withMesh(request.mesh)
                    if (old == null || outcome.objects.isEmpty()) return@update informed.copy(editing = false)
                    val edited = outcome.objects.map { it.toPlateObjectOf(old) }
                    val placed = if (outcome.appended) {
                        state.objects.filterNot { it.mesh == old.mesh } + edited
                    } else {
                        state.objects.map { if (it.mesh == old.mesh) edited.single() else it }
                    }
                    // perform_cut() ends with synchronize_model_after_cut().
                    val cutId = edited.firstNotNullOfOrNull { it.cutId }.takeIf { request.edit == ObjectEdit.CUT }
                    val objects = if (cutId != null) placed.synchronizedAfterCut(cutId) else placed
                    // The old object's meshes stay with the snapshot taken before the edit.
                    informed.recorded().copy(
                        editing = false,
                        objects = objects,
                        // Plater::priv::split_object() selects the new objects.
                        selectedInstances = edited.allCopies(),
                        selectedPart = null,
                        selectedRange = null,
                        result = null,
                    )
                }
            }
        }
    }
}

/** The question of a load or of an edit of the plate answered, which goes on with it. */
class AnswerPlateQuestionUseCase(
    private val repository: PlateRepository,
    private val addModelToPlate: AddModelToPlateUseCase,
    private val editPlateObject: EditPlateObjectUseCase,
    private val settingsTabs: PresetSettingsTabs,
    private val deletePlateObject: DeletePlateObjectUseCase,
    private val copyToClipboard: CopyToClipboardUseCase,
    private val invalidateCutInfo: InvalidateCutInfoUseCase,
) {
    operator fun invoke(yes: Boolean) = when (repository.state.value.plateQuestion?.request) {
        is PlateRequest.Import -> addModelToPlate.answer(yes)
        is PlateRequest.Edit -> editPlateObject.answer(yes)
        PlateRequest.TopSurfaceSuggestion -> suggestion(yes)
        is PlateRequest.DeleteCutObject -> deletePlateObject.answer(yes)
        is PlateRequest.EraseCutObjects -> copyToClipboard.answer(yes)
        is PlateRequest.InvalidateCut -> invalidateCutInfo.answer(yes)
        null -> Unit
    }

    /** Yes: min_width_top_surface of the edited process preset becomes 0, as the desktop dialog sets it. */
    private fun suggestion(yes: Boolean) {
        repository.update { it.copy(plateQuestion = null) }
        if (yes) settingsTabs.request(PresetKind.PRINT, SettingsRequest.Change(AddModelToPlateUseCase.MIN_WIDTH_TOP_SURFACE, "0"))
    }
}

/** The first message box of a change of the plate was dismissed. */
class DismissPlateNoticeUseCase(private val repository: PlateRepository) {
    operator fun invoke() = repository.update { state ->
        if (state.plateNotices.isEmpty()) state else state.copy(plateNotices = state.plateNotices.drop(1))
    }
}

/** An object that came of a model file or of an edit, as the plate keeps it. */
internal fun LoadedObject.toPlateObject(inputName: String) = PlateObject.ImportedModel(
    file = ImportedModelFile(source, name),
    inputName = inputName,
    instances = instances,
    settings = settings,
    parts = parts,
    frame = frame,
    volume = volume,
    painted = painted,
    layerRanges = layerRanges,
    cutId = cutId,
)

/**
 * An object the engine wrote anew from [source]: named after the document
 * [source] came from, and still without a name of its own when [source] was
 * the calibration cube the app names, which the engine calls by its own name.
 */
internal fun LoadedObject.toPlateObjectOf(source: PlateObject): PlateObject {
    val written = toPlateObject(source.inputFile())
    return if (source is PlateObject.CalibrationCube && source.name == null && name == CALIBRATION_CUBE) written.withName("") else written
}

/** What the engine names the calibration cube (ModelObject::name) and the G-code printed from it. */
internal const val CALIBRATION_CUBE = "calibration-cube-20mm"

/**
 * ModelObject::input_file: the document the object came from, which names its
 * G-code; an object made of the calibration cube is named as the cube.
 */
internal fun PlateObject.inputFile(): String = (this as? PlateObject.ImportedModel)?.inputName ?: CALIBRATION_CUBE

/** Every copy of the objects, as Selection::add_object() selects an object whole. */
internal fun List<PlateObject>.allCopies(): Set<PlateInstanceId> =
    flatMapTo(LinkedHashSet()) { plateObject -> plateObject.instances.indices.map { PlateInstanceId(plateObject.mesh, it) } }

