package app.orcinus.shadow.domain.plate

import app.orcinus.shadow.core.model.BuildVolumeFit
import app.orcinus.shadow.core.model.BuiltInModel
import app.orcinus.shadow.core.model.EngineAvailability
import app.orcinus.shadow.core.model.EngineState
import app.orcinus.shadow.core.model.ExternalDocumentReference
import app.orcinus.shadow.core.model.Manipulation
import app.orcinus.shadow.core.model.ModelImportOutcome
import app.orcinus.shadow.core.model.ModelInspection
import app.orcinus.shadow.core.model.ModelInspectionOutcome
import app.orcinus.shadow.core.model.ModelSource
import app.orcinus.shadow.core.model.PlacedModel
import app.orcinus.shadow.core.model.PlateDescriptionOutcome
import app.orcinus.shadow.core.model.PlateInspectionOutcome
import app.orcinus.shadow.core.model.PlateManipulation
import app.orcinus.shadow.core.model.PlateObject
import app.orcinus.shadow.core.model.PlateProblem
import app.orcinus.shadow.core.model.PlateProblemKind
import app.orcinus.shadow.core.model.PlateSliceResult
import app.orcinus.shadow.core.model.PlateSlicing
import app.orcinus.shadow.core.model.PlateState
import app.orcinus.shadow.core.model.PresetChoice
import app.orcinus.shadow.core.model.PresetsOutcome
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.SetupFilamentsOutcome
import app.orcinus.shadow.core.model.SetupPrintersOutcome
import app.orcinus.shadow.core.model.SliceFailureCode
import app.orcinus.shadow.core.model.SliceJobId
import app.orcinus.shadow.core.model.SliceOutcome
import app.orcinus.shadow.core.model.SliceRequest
import app.orcinus.shadow.core.model.SlicingProfileSelection
import app.orcinus.shadow.core.model.Transform3
import app.orcinus.shadow.domain.CancelSliceUseCase
import app.orcinus.shadow.domain.GetEngineStatusUseCase
import app.orcinus.shadow.domain.ImportModelUseCase
import app.orcinus.shadow.domain.InspectModelUseCase
import app.orcinus.shadow.domain.PlaceModelUseCase
import app.orcinus.shadow.domain.PlaceModelsUseCase
import app.orcinus.shadow.domain.SliceModelUseCase
import app.orcinus.shadow.domain.SliceProgressObserver
import app.orcinus.shadow.domain.placed
import app.orcinus.shadow.domain.source
import app.orcinus.shadow.slicing.api.PlateInspector
import app.orcinus.shadow.slicing.api.PresetManager
import app.orcinus.shadow.storage.api.GcodeOutputs
import app.orcinus.shadow.storage.api.SceneFiles
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// Plate use cases run long operations in the application scope, so an import
// or a slice outlives the screen that started it. Results land in the
// repository, which every screen observes. Objects are told apart by their
// mesh files.

class ObservePlateUseCase(private val repository: PlateRepository) {
    operator fun invoke(): StateFlow<PlateState> = repository.state
}

/**
 * Prepares the engine and the plate once per process; later calls return at
 * once. Object meshes and toolpaths left by an earlier process are deleted,
 * since its plate is gone. The engine reports the presets its app configuration
 * remembers and describes the plate of the selected printer, unless the Setup
 * Wizard has yet to install one.
 */
class StartEngineUseCase(
    private val getEngineStatus: GetEngineStatusUseCase,
    private val presetManager: PresetManager,
    private val platePresets: PlatePresets,
    private val sceneFiles: SceneFiles,
    private val repository: PlateRepository,
) {
    suspend operator fun invoke() {
        val state = repository.state.value
        if (state.engine.availability == EngineAvailability.READY && state.presets != null) return
        if (state.objects.isEmpty()) {
            sceneFiles.deleteAllObjectMeshes()
            sceneFiles.deleteToolpathsExcept(null)
        }
        val status = getEngineStatus()
        repository.update {
            it.copy(
                engine = EngineState(
                    availability = if (status.ready) EngineAvailability.READY else EngineAvailability.UNAVAILABLE,
                    version = status.version,
                ),
                problem = if (status.ready) it.problem else PlateProblem(PlateProblemKind.ENGINE_UNAVAILABLE, status.message),
            )
        }
        if (!status.ready) return
        platePresets.apply(before = null, outcome = presetManager.presets())
    }
}

/**
 * Brings the plate to the presets the engine reports. G-code sliced with other
 * presets no longer applies. The plate is described again for another printer
 * or filament, whose colour it shows, and the objects' fit is judged against
 * another printer's build volume, as Plater::on_config_change() does. Presets
 * that still need the Setup Wizard leave the plate undescribed.
 */
class PlatePresets(
    private val inspector: PlateInspector,
    private val sceneFiles: SceneFiles,
    private val repository: PlateRepository,
    private val placePlateObjects: PlacePlateObjectsUseCase,
) {
    /** Applies [outcome] of a change that started from the selection [before]. */
    suspend fun apply(before: SlicingProfileSelection?, outcome: PresetsOutcome) {
        val presets = when (outcome) {
            is PresetsOutcome.Failure -> {
                repository.update { it.copy(changingPresets = false, problem = PlateProblem(PlateProblemKind.PRESETS_FAILED, outcome.message)) }
                return
            }
            is PresetsOutcome.Success -> outcome.presets
        }
        repository.update { state ->
            val updated = state.copy(presets = presets, changingPresets = false)
            updated.copy(result = state.result.takeIf { updated.profiles == before })
        }
        val profiles = repository.state.value.profiles ?: return
        val plateChanged = before?.printer != profiles.printer || before.filament != profiles.filament
        if (plateChanged || repository.state.value.plate == null) {
            // Without a description the 3D view shows no plate; slicing does not depend on it.
            val plate = inspector.describePlate(profiles, sceneFiles.plateDirectory())
            if (plate is PlateDescriptionOutcome.Success) {
                repository.update { if (it.profiles == profiles) it.copy(plate = plate.description) else it }
            }
        }
        if (before != null && before.printer != profiles.printer) {
            placePlateObjects(PlateManipulation.UpdatePrintVolume)
        }
    }
}

/**
 * Selects a preset as OrcaSlicer's sidebar does, and brings the plate to the
 * presets that come with it. Nothing changes while the plate is busy, while an
 * object's placement is settling, or before a printer is set up.
 */
class SelectPresetUseCase(
    private val presetManager: PresetManager,
    private val platePresets: PlatePresets,
    private val repository: PlateRepository,
    private val applicationScope: CoroutineScope,
) {
    operator fun invoke(choice: PresetChoice) {
        var before: SlicingProfileSelection? = null
        repository.update { state ->
            before = null
            if (state.busy || state.objects.any(PlateObject::placing)) return@update state
            before = state.profiles ?: return@update state
            state.copy(changingPresets = true, problem = null)
        }
        val selection = before ?: return
        applicationScope.launch {
            platePresets.apply(selection, presetManager.selectPreset(choice))
        }
    }
}

/** The printer models the Setup Wizard offers. */
class GetSetupPrintersUseCase(private val presetManager: PresetManager) {
    suspend operator fun invoke(): SetupPrintersOutcome = presetManager.setupPrinters()
}

/** The filaments the Setup Wizard offers for the printer models with the ids [models]. */
class GetSetupFilamentsUseCase(private val presetManager: PresetManager) {
    suspend operator fun invoke(models: List<String>): SetupFilamentsOutcome = presetManager.setupFilaments(models)
}

/**
 * The Setup Wizard's Finish, or its closing while no printer is installed
 * (GuideFrame::run): the engine installs the printers and filaments and selects
 * a printer, and the plate is brought to the new presets. The change runs in
 * the application scope, so it completes when the wizard's screen goes away;
 * the caller gets the engine's answer. Returns null when the plate is busy.
 */
class ApplySetupUseCase(
    private val presetManager: PresetManager,
    private val platePresets: PlatePresets,
    private val repository: PlateRepository,
    private val applicationScope: CoroutineScope,
) {
    /** Installs the printer models with the ids [models] and the [filaments]. */
    suspend operator fun invoke(models: List<String>, filaments: List<String>): PresetsOutcome? =
        change { presetManager.applySetup(models, filaments) }

    /** OrcaSlicer's default printer and filament, when no printer is installed. */
    suspend fun defaults(): PresetsOutcome? = change { presetManager.applyDefaultSetup() }

    private suspend fun change(apply: suspend () -> PresetsOutcome): PresetsOutcome? {
        var accepted = false
        var before: SlicingProfileSelection? = null
        repository.update { state ->
            accepted = false
            if (state.busy || state.presets == null || state.objects.any(PlateObject::placing)) return@update state
            accepted = true
            before = state.profiles
            state.copy(changingPresets = true, problem = null)
        }
        if (!accepted) return null
        return applicationScope.async {
            apply().also { platePresets.apply(before, it) }
        }.await()
    }
}

/** Imports a document and adds it to the plate, as OrcaSlicer loads a model and places it beside the objects there. */
class AddModelToPlateUseCase(
    private val importModel: ImportModelUseCase,
    inspectModel: InspectModelUseCase,
    sceneFiles: SceneFiles,
    repository: PlateRepository,
    applicationScope: CoroutineScope,
) {
    private val loader = PlateObjectLoader(inspectModel, sceneFiles, repository, applicationScope)

    operator fun invoke(reference: ExternalDocumentReference) = loader.load { inspect ->
        when (val imported = importModel(reference)) {
            is ModelImportOutcome.Failure -> Result.failure(IllegalArgumentException(imported.message))
            is ModelImportOutcome.Success -> inspect(ModelSource.LocalFile(imported.model.path))
                .map { PlateObject.ImportedModel(imported.model, it) }
        }
    }
}

/** Adds OrcaSlicer's 20 mm calibration cube to the plate. */
class AddCalibrationCubeToPlateUseCase(
    inspectModel: InspectModelUseCase,
    sceneFiles: SceneFiles,
    repository: PlateRepository,
    applicationScope: CoroutineScope,
) {
    private val loader = PlateObjectLoader(inspectModel, sceneFiles, repository, applicationScope)

    operator fun invoke() = loader.load { inspect ->
        inspect(ModelSource.BuiltIn(BuiltInModel.CALIBRATION_CUBE_20_MM)).map { PlateObject.CalibrationCube(it) }
    }
}

/**
 * Adds an object to the plate, as Plater::priv::load_model_objects() does. The
 * plate is busy while OrcaSlicer loads the object and places it among the
 * objects on the plate in the application scope. The object joins the end of
 * the plate's list, and G-code sliced before no longer applies; a failure
 * leaves the plate as it was, without the new mesh file, and reports the problem.
 */
private class PlateObjectLoader(
    private val inspectModel: InspectModelUseCase,
    private val sceneFiles: SceneFiles,
    private val repository: PlateRepository,
    private val applicationScope: CoroutineScope,
) {
    fun load(block: suspend (inspect: suspend (ModelSource) -> Result<ModelInspection>) -> Result<PlateObject>) {
        var request: Pair<SlicingProfileSelection, List<PlacedModel>>? = null
        repository.update { state ->
            request = null
            val profiles = state.profiles
            if (state.busy || profiles == null) return@update state
            request = profiles to state.objects.map { it.placed() }
            state.copy(importing = true, problem = null)
        }
        val (selection, plate) = request ?: return
        applicationScope.launch {
            val meshes = mutableListOf<ScenePath>()
            val loaded = try {
                block { source ->
                    val mesh = sceneFiles.newObjectMesh().also(meshes::add)
                    when (val inspected = inspectModel(source, selection, mesh, plate)) {
                        is ModelInspectionOutcome.Success -> Result.success(inspected.inspection)
                        is ModelInspectionOutcome.Failure -> Result.failure(IllegalArgumentException(inspected.message))
                    }
                }
            } catch (cancellation: CancellationException) {
                meshes.forEach(sceneFiles::deleteObjectMesh)
                throw cancellation
            } catch (error: Exception) {
                Result.failure(error)
            }

            repository.update { state ->
                loaded.fold(
                    onSuccess = { state.copy(importing = false, objects = state.objects + it, result = null) },
                    onFailure = { state.copy(importing = false, problem = PlateProblem(PlateProblemKind.IMPORT_FAILED, it.message)) },
                )
            }
            val shown = loaded.getOrNull()?.inspection?.mesh
            meshes.filter { it != shown }.forEach(sceneFiles::deleteObjectMesh)
        }
    }
}

/**
 * Commits a manipulation of an object on the plate, as OrcaSlicer's canvas
 * does after a move, rotation, or scale, or after its orientation tools. The
 * object stands at the new placement at once and G-code sliced for the old one
 * no longer applies; OrcaSlicer then settles the placement (resting the object
 * on the plate as the manipulation requires, or finding its orientation) and
 * reports its size and whether it fits the build volume. Until then the plate
 * cannot be sliced.
 *
 * The object is the one with the [mesh] file, so a placement that arrives
 * after the object was deleted changes nothing; nor does one while the plate
 * is busy, or a move, rotation, or scale that leaves the placement as it was.
 * An answer for a placement that a newer one replaced is ignored.
 */
class PlacePlateObjectUseCase(
    private val placeModel: PlaceModelUseCase,
    private val repository: PlateRepository,
    private val applicationScope: CoroutineScope,
) {
    operator fun invoke(mesh: ScenePath, placement: Transform3, manipulation: Manipulation = Manipulation.Move) {
        var request: Triple<PlateObject, Transform3, SlicingProfileSelection>? = null
        repository.update { state ->
            request = null
            val target = state.objects.withMesh(mesh)
            val profiles = state.profiles
            val unchanged = target?.inspection?.placement == placement && manipulation.keepsUnchangedPlacement()
            if (target == null || profiles == null || state.busy || unchanged) {
                return@update state
            }
            val moved = target.with(target.inspection.copy(placement = placement), placing = true)
            request = Triple(moved, target.inspection.placement, profiles)
            // G-code no longer applies once the object stands elsewhere.
            state.copy(objects = state.objects.replaced(moved), result = state.result.takeIf { placement == target.inspection.placement })
        }
        val (target, previous, profiles) = request ?: return
        applicationScope.launch {
            val outcome = try {
                placeModel(target.source(), profiles, mesh, previous, placement, target.autoDrop, manipulation)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                ModelInspectionOutcome.Failure(error.message.orEmpty())
            }
            repository.update { state ->
                val current = state.objects.withMesh(mesh)
                if (current == null || current.inspection.placement != placement) {
                    return@update state
                }
                when (outcome) {
                    is ModelInspectionOutcome.Success -> state.copy(
                        objects = state.objects.replaced(current.with(outcome.inspection, placing = false)),
                        result = state.result.takeIf { outcome.inspection.placement == previous },
                    )
                    is ModelInspectionOutcome.Failure -> state.copy(
                        objects = state.objects.replaced(current.with(current.inspection, placing = false)),
                        problem = PlateProblem(PlateProblemKind.PLACEMENT_FAILED, outcome.message),
                    )
                }
            }
        }
    }

    /** Moves, rotations, and scales that end where they began change nothing; the other manipulations still act. */
    private fun Manipulation.keepsUnchangedPlacement() = this == Manipulation.Move || this == Manipulation.Rotate || this == Manipulation.Scale
}

/**
 * OrientJob and ArrangeJob: OrcaSlicer places several objects of the plate at
 * once. A job starts from settled placements; the objects it places stand
 * where they were until it answers, and the plate cannot be sliced meanwhile.
 * The answer applies to every object that still stands where it stood when the
 * job began, so an object the user moved or deleted meanwhile keeps the user's
 * change. G-code sliced before no longer applies once an object moved.
 */
class PlacePlateObjectsUseCase(
    private val placeModels: PlaceModelsUseCase,
    private val repository: PlateRepository,
    private val applicationScope: CoroutineScope,
) {
    operator fun invoke(manipulation: PlateManipulation) {
        var request: Triple<List<PlateObject>, Set<ScenePath>, SlicingProfileSelection>? = null
        repository.update { state ->
            request = null
            val profiles = state.profiles
            if (state.busy || profiles == null || state.objects.isEmpty() || state.objects.any(PlateObject::placing)) return@update state
            val targets = manipulation.targets(state.objects)
            request = Triple(state.objects, targets, profiles)
            state.copy(objects = state.objects.map { if (it.inspection.mesh in targets) it.with(it.inspection, placing = true) else it })
        }
        val (plate, targets, profiles) = request ?: return
        applicationScope.launch {
            val outcome = try {
                placeModels(plate.map { it.placed() }, profiles, manipulation)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                PlateInspectionOutcome.Failure(error.message.orEmpty())
            }
            repository.update { state ->
                var moved = false
                val objects = state.objects.map { current ->
                    val index = plate.indexOfFirst { it.inspection.mesh == current.inspection.mesh }
                    val before = plate.getOrNull(index)
                    if (before == null || before.inspection.mesh !in targets || current.inspection.placement != before.inspection.placement) {
                        return@map current
                    }
                    val placed = (outcome as? PlateInspectionOutcome.Success)?.inspections?.getOrNull(index)
                    if (placed != null && placed.placement != before.inspection.placement) moved = true
                    current.with(placed ?: current.inspection, placing = false)
                }
                state.copy(
                    objects = objects,
                    result = state.result.takeIf { !moved },
                    problem = if (outcome is PlateInspectionOutcome.Failure) {
                        PlateProblem(PlateProblemKind.PLACEMENT_FAILED, outcome.message)
                    } else {
                        state.problem
                    },
                )
            }
        }
    }

    /**
     * OrientJob places the selected objects, or all of them when none of the plate's is selected;
     * ArrangeJob places all, and another printer judges the fit of all.
     */
    private fun PlateManipulation.targets(objects: List<PlateObject>): Set<ScenePath> {
        val meshes = objects.mapTo(LinkedHashSet()) { it.inspection.mesh }
        return when (this) {
            is PlateManipulation.AutoOrient -> selected.intersect(meshes).ifEmpty { meshes }
            is PlateManipulation.Arrange, PlateManipulation.UpdatePrintVolume -> meshes
        }
    }
}

/**
 * ObjectList::toggle_auto_drop() for the object with the [mesh] file: with auto
 * drop off, manipulations leave the object where the user puts it, above the
 * plate included; turning it on again rests the object on the plate.
 */
class SetPlateObjectAutoDropUseCase(
    private val repository: PlateRepository,
    private val placePlateObject: PlacePlateObjectUseCase,
) {
    operator fun invoke(mesh: ScenePath, autoDrop: Boolean) {
        var changed: PlateObject? = null
        repository.update { state ->
            changed = null
            val target = state.objects.withMesh(mesh)
            if (target == null || state.busy || target.autoDrop == autoDrop) return@update state
            val updated = when (target) {
                is PlateObject.ImportedModel -> target.copy(autoDrop = autoDrop)
                is PlateObject.CalibrationCube -> target.copy(autoDrop = autoDrop)
            }
            changed = updated
            state.copy(objects = state.objects.replaced(updated))
        }
        val updated = changed ?: return
        if (autoDrop) placePlateObject(mesh, updated.inspection.placement, Manipulation.EnsureOnBed)
    }
}

/**
 * Plater::remove_selected() for the object with the [mesh] file: the object
 * leaves the plate with its mesh file, and G-code sliced with it no longer
 * applies. Nothing is deleted while the plate is busy.
 */
class DeletePlateObjectUseCase(
    private val sceneFiles: SceneFiles,
    private val repository: PlateRepository,
) {
    operator fun invoke(mesh: ScenePath) {
        var deleted = false
        repository.update { state ->
            deleted = false
            if (state.busy || state.objects.withMesh(mesh) == null) return@update state
            deleted = true
            state.copy(objects = state.objects.filterNot { it.inspection.mesh == mesh }, result = null)
        }
        if (deleted) sceneFiles.deleteObjectMesh(mesh)
    }
}

/** Slices the objects on the plate with the selected profiles into the plate's G-code file. */
class SlicePlateUseCase(
    private val sliceModel: SliceModelUseCase,
    private val outputs: GcodeOutputs,
    private val sceneFiles: SceneFiles,
    private val repository: PlateRepository,
    private val applicationScope: CoroutineScope,
) {
    operator fun invoke() {
        val jobId = SliceJobId(UUID.randomUUID().toString())
        var started: Pair<List<PlateObject>, SlicingProfileSelection>? = null
        repository.update { state ->
            started = null
            val profiles = state.profiles
            if (!state.canSlice || profiles == null) return@update state
            started = state.objects to profiles
            state.copy(slicing = PlateSlicing(jobId), problem = null)
        }
        val (objects, profiles) = started ?: return

        applicationScope.launch {
            val request = SliceRequest(
                jobId = jobId,
                objects = objects.map { it.placed() },
                output = outputs.outputFor(objects.outputName()),
                toolpaths = sceneFiles.newToolpaths(),
                printerProfile = profiles.printer,
                filamentProfile = profiles.filament,
                processProfile = profiles.process,
            )
            val outcome = try {
                sliceModel(request, SliceProgressObserver { progress ->
                    repository.update { it.withJob(jobId) { job -> job.copy(progress = progress) } }
                })
            } catch (cancellation: CancellationException) {
                repository.update { it.withJob(jobId) { null } }
                throw cancellation
            } catch (error: Exception) {
                SliceOutcome.Failure(jobId, SliceFailureCode.SLICING_FAILED, error.message.orEmpty(), recoverable = true)
            }
            repository.update { it.withOutcome(objects, outcome) }
            // Only the toolpaths of the result on the plate stay; a failed or replaced job leaves none.
            sceneFiles.deleteToolpathsExcept(repository.state.value.result?.toolpaths)
        }
    }

    /** PrintBase::update_object_placeholders(): the G-code is named after the first object the plate prints. */
    private fun List<PlateObject>.outputName(): String = when (val named = first { it.inspection.fit == BuildVolumeFit.INSIDE }) {
        is PlateObject.ImportedModel -> named.file.displayName.substringBeforeLast('.')
        is PlateObject.CalibrationCube -> "calibration-cube-20mm"
    }

    private fun PlateState.withOutcome(objects: List<PlateObject>, outcome: SliceOutcome): PlateState {
        if (slicing?.jobId != outcome.jobId) return this  // a newer job replaced this one
        return when (outcome) {
            is SliceOutcome.Success -> copy(
                slicing = null,
                result = PlateSliceResult(outcome.jobId, objects, outcome.gcodePath, outcome.statistics, outcome.toolpaths),
            )

            is SliceOutcome.Failure -> copy(
                slicing = null,
                problem = PlateProblem(
                    kind = if (outcome.code == SliceFailureCode.ENGINE_CRASHED) PlateProblemKind.ENGINE_CRASHED else PlateProblemKind.SLICE_FAILED,
                    detail = outcome.message,
                ),
            )

            is SliceOutcome.Cancelled -> copy(slicing = null, problem = PlateProblem(PlateProblemKind.SLICE_CANCELLED))
        }
    }
}

class CancelPlateSlicingUseCase(
    private val cancelSlice: CancelSliceUseCase,
    private val repository: PlateRepository,
    private val applicationScope: CoroutineScope,
) {
    operator fun invoke() {
        val jobId = repository.state.value.slicing?.jobId ?: return
        applicationScope.launch {
            if (cancelSlice(jobId)) {
                repository.update { it.withJob(jobId) { job -> job.copy(cancelling = true) } }
            }
        }
    }
}

class DismissPlateProblemUseCase(private val repository: PlateRepository) {
    operator fun invoke() {
        repository.update { it.copy(problem = null) }
    }
}

/** Changes the running job when it is still [jobId]; a null result ends it. */
private inline fun PlateState.withJob(jobId: SliceJobId, change: (PlateSlicing) -> PlateSlicing?): PlateState {
    val job = slicing?.takeIf { it.jobId == jobId } ?: return this
    return copy(slicing = change(job))
}

private fun List<PlateObject>.withMesh(mesh: ScenePath): PlateObject? = firstOrNull { it.inspection.mesh == mesh }

/** The list with [plateObject] in place of the object with its mesh file. */
private fun List<PlateObject>.replaced(plateObject: PlateObject): List<PlateObject> =
    map { if (it.inspection.mesh == plateObject.inspection.mesh) plateObject else it }

private fun PlateObject.with(inspection: ModelInspection, placing: Boolean): PlateObject = when (this) {
    is PlateObject.ImportedModel -> copy(inspection = inspection, placing = placing)
    is PlateObject.CalibrationCube -> copy(inspection = inspection, placing = placing)
}
