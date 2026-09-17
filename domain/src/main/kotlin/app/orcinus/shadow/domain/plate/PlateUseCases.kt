package app.orcinus.shadow.domain.plate

import app.orcinus.shadow.core.model.BuiltInModel
import app.orcinus.shadow.core.model.EngineAvailability
import app.orcinus.shadow.core.model.EngineState
import app.orcinus.shadow.core.model.ExternalDocumentReference
import app.orcinus.shadow.core.model.Manipulation
import app.orcinus.shadow.core.model.ModelImportOutcome
import app.orcinus.shadow.core.model.ModelInspection
import app.orcinus.shadow.core.model.ModelInspectionOutcome
import app.orcinus.shadow.core.model.ModelSource
import app.orcinus.shadow.core.model.PlateDescriptionOutcome
import app.orcinus.shadow.core.model.PlateObject
import app.orcinus.shadow.core.model.PlateProblem
import app.orcinus.shadow.core.model.PlateProblemKind
import app.orcinus.shadow.core.model.PlateSliceResult
import app.orcinus.shadow.core.model.PlateSlicing
import app.orcinus.shadow.core.model.PlateState
import app.orcinus.shadow.core.model.ScenePath
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
import app.orcinus.shadow.domain.SliceModelUseCase
import app.orcinus.shadow.domain.SliceProgressObserver
import app.orcinus.shadow.domain.source
import app.orcinus.shadow.slicing.api.PlateInspector
import app.orcinus.shadow.storage.api.GcodeOutputs
import app.orcinus.shadow.storage.api.SceneFiles
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// Plate use cases run long operations in the application scope, so an import
// or a slice outlives the screen that started it. Results land in the
// repository, which every screen observes.

class ObservePlateUseCase(private val repository: PlateRepository) {
    operator fun invoke(): StateFlow<PlateState> = repository.state
}

/**
 * Prepares the engine and the plate once per process; later calls return at
 * once. Object meshes left by an earlier process are deleted, since its plate
 * is gone, and the engine describes the plate of the selected printer.
 */
class StartEngineUseCase(
    private val getEngineStatus: GetEngineStatusUseCase,
    private val inspector: PlateInspector,
    private val sceneFiles: SceneFiles,
    private val repository: PlateRepository,
) {
    suspend operator fun invoke() {
        if (repository.state.value.engine.availability == EngineAvailability.READY) return
        if (repository.state.value.plateObject == null) sceneFiles.deleteAllObjectMeshes()
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
        // Without a description the 3D view shows no plate; slicing does not depend on it.
        val plate = inspector.describePlate(repository.state.value.profiles, sceneFiles.plateDirectory())
        if (plate is PlateDescriptionOutcome.Success) {
            repository.update { it.copy(plate = plate.description) }
        }
    }
}

/** Imports a document and puts it on the plate, as OrcaSlicer loads and places it, in place of the previous model. */
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

/** Puts OrcaSlicer's 20 mm calibration cube on the plate in place of the previous model. */
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
 * Replaces the object on the plate. The plate is busy while OrcaSlicer loads
 * and places the new object in the application scope; the new object's mesh
 * file replaces the previous object's, and a failure leaves the plate as it
 * was and reports the problem.
 */
private class PlateObjectLoader(
    private val inspectModel: InspectModelUseCase,
    private val sceneFiles: SceneFiles,
    private val repository: PlateRepository,
    private val applicationScope: CoroutineScope,
) {
    fun load(block: suspend (inspect: suspend (ModelSource) -> Result<ModelInspection>) -> Result<PlateObject>) {
        var profiles: SlicingProfileSelection? = null
        repository.update { state ->
            profiles = null
            if (state.busy) return@update state
            profiles = state.profiles
            state.copy(importing = true, problem = null)
        }
        val selection = profiles ?: return
        applicationScope.launch {
            val meshes = mutableListOf<ScenePath>()
            val loaded = try {
                block { source ->
                    val mesh = sceneFiles.newObjectMesh().also(meshes::add)
                    when (val inspected = inspectModel(source, selection, mesh)) {
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

            var replaced: PlateObject? = null
            repository.update { state ->
                loaded.fold(
                    onSuccess = {
                        replaced = state.plateObject
                        state.copy(importing = false, plateObject = it, result = null)
                    },
                    onFailure = {
                        replaced = null
                        state.copy(importing = false, problem = PlateProblem(PlateProblemKind.IMPORT_FAILED, it.message))
                    },
                )
            }
            val shown = loaded.getOrNull()?.inspection?.mesh
            (meshes + listOfNotNull(replaced?.inspection?.mesh))
                .filter { it != shown }
                .forEach(sceneFiles::deleteObjectMesh)
        }
    }
}

/**
 * Commits a manipulation of the object on the plate, as OrcaSlicer's canvas
 * does after a move, rotation, or scale, or after its orientation tools. The
 * object stands at the new placement at once and G-code sliced for the old one
 * no longer applies; OrcaSlicer then settles the placement (resting the object
 * on the plate as the manipulation requires, or finding its orientation) and
 * reports its size and whether it fits the build volume. Until then the plate
 * cannot be sliced.
 *
 * The object is the one with the [mesh] file, so a placement that arrives
 * after the object was replaced changes nothing; nor does one while the plate
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
            val target = state.plateObject
            val unchanged = target?.inspection?.placement == placement && manipulation.keepsUnchangedPlacement()
            if (target == null || target.inspection.mesh != mesh || state.busy || unchanged) {
                return@update state
            }
            val moved = target.with(target.inspection.copy(placement = placement), placing = true)
            request = Triple(moved, target.inspection.placement, state.profiles)
            // G-code no longer applies once the object stands elsewhere.
            state.copy(plateObject = moved, result = state.result.takeIf { placement == target.inspection.placement })
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
                val current = state.plateObject
                if (current == null || current.inspection.mesh != mesh || current.inspection.placement != placement) {
                    return@update state
                }
                when (outcome) {
                    is ModelInspectionOutcome.Success -> state.copy(
                        plateObject = current.with(outcome.inspection, placing = false),
                        result = state.result.takeIf { outcome.inspection.placement == previous },
                    )
                    is ModelInspectionOutcome.Failure -> state.copy(
                        plateObject = current.with(current.inspection, placing = false),
                        problem = PlateProblem(PlateProblemKind.PLACEMENT_FAILED, outcome.message),
                    )
                }
            }
        }
    }

    /** Moves, rotations, and scales that end where they began change nothing; the orientation and arrange tools still act. */
    private fun Manipulation.keepsUnchangedPlacement() = this == Manipulation.Move || this == Manipulation.Rotate || this == Manipulation.Scale

    private fun PlateObject.with(inspection: ModelInspection, placing: Boolean): PlateObject = when (this) {
        is PlateObject.ImportedModel -> copy(inspection = inspection, placing = placing)
        is PlateObject.CalibrationCube -> copy(inspection = inspection, placing = placing)
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
            val target = state.plateObject
            if (target == null || target.inspection.mesh != mesh || state.busy || target.autoDrop == autoDrop) return@update state
            val updated = when (target) {
                is PlateObject.ImportedModel -> target.copy(autoDrop = autoDrop)
                is PlateObject.CalibrationCube -> target.copy(autoDrop = autoDrop)
            }
            changed = updated
            state.copy(plateObject = updated)
        }
        val updated = changed ?: return
        if (autoDrop) placePlateObject(mesh, updated.inspection.placement, Manipulation.EnsureOnBed)
    }
}

/** Slices the model on the plate with the selected profiles into the plate's G-code file. */
class SlicePlateUseCase(
    private val sliceModel: SliceModelUseCase,
    private val outputs: GcodeOutputs,
    private val repository: PlateRepository,
    private val applicationScope: CoroutineScope,
) {
    operator fun invoke() {
        val jobId = SliceJobId(UUID.randomUUID().toString())
        var started: PlateState? = null
        repository.update { state ->
            started = null
            if (!state.canSlice) return@update state
            started = state
            state.copy(slicing = PlateSlicing(jobId), problem = null)
        }
        val state = started ?: return
        val target = state.plateObject ?: return

        applicationScope.launch {
            val request = SliceRequest(
                jobId = jobId,
                model = target.source(),
                output = outputs.outputFor(target.outputName()),
                printerProfile = state.profiles.printer,
                filamentProfile = state.profiles.filament,
                processProfile = state.profiles.process,
                placement = target.inspection.placement,
                autoDrop = target.autoDrop,
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
            repository.update { it.withOutcome(target, outcome) }
        }
    }

    private fun PlateObject.outputName(): String = when (this) {
        is PlateObject.ImportedModel -> file.displayName.substringBeforeLast('.')
        is PlateObject.CalibrationCube -> "calibration-cube-20mm"
    }

    private fun PlateState.withOutcome(target: PlateObject, outcome: SliceOutcome): PlateState {
        if (slicing?.jobId != outcome.jobId) return this  // a newer job replaced this one
        return when (outcome) {
            is SliceOutcome.Success -> copy(
                slicing = null,
                result = PlateSliceResult(outcome.jobId, target, outcome.gcodePath, outcome.statistics),
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
