package app.orcinus.shadow.domain.plate

import app.orcinus.shadow.core.model.BuiltInModel
import app.orcinus.shadow.core.model.EngineAvailability
import app.orcinus.shadow.core.model.EngineState
import app.orcinus.shadow.core.model.ExternalDocumentReference
import app.orcinus.shadow.core.model.ModelImportOutcome
import app.orcinus.shadow.core.model.ModelInspectionOutcome
import app.orcinus.shadow.core.model.ModelSource
import app.orcinus.shadow.core.model.PlateObject
import app.orcinus.shadow.core.model.PlateProblem
import app.orcinus.shadow.core.model.PlateProblemKind
import app.orcinus.shadow.core.model.PlateSliceResult
import app.orcinus.shadow.core.model.PlateSlicing
import app.orcinus.shadow.core.model.PlateState
import app.orcinus.shadow.core.model.SliceFailureCode
import app.orcinus.shadow.core.model.SliceJobId
import app.orcinus.shadow.core.model.SliceOutcome
import app.orcinus.shadow.core.model.SliceRequest
import app.orcinus.shadow.domain.CancelSliceUseCase
import app.orcinus.shadow.domain.GetEngineStatusUseCase
import app.orcinus.shadow.domain.ImportModelUseCase
import app.orcinus.shadow.domain.InspectModelUseCase
import app.orcinus.shadow.domain.SliceModelUseCase
import app.orcinus.shadow.domain.SliceProgressObserver
import app.orcinus.shadow.storage.api.GcodeOutputs
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

/** Prepares the engine once; later calls return at once. */
class StartEngineUseCase(
    private val getEngineStatus: GetEngineStatusUseCase,
    private val repository: PlateRepository,
) {
    suspend operator fun invoke() {
        if (repository.state.value.engine.availability == EngineAvailability.READY) return
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
    }
}

/** Imports a document, checks it with Orca, and puts it on the plate in place of the previous model. */
class AddModelToPlateUseCase(
    private val importModel: ImportModelUseCase,
    private val inspectModel: InspectModelUseCase,
    private val repository: PlateRepository,
    private val applicationScope: CoroutineScope,
) {
    operator fun invoke(reference: ExternalDocumentReference) {
        var accepted = false
        repository.update { state ->
            accepted = !state.busy
            if (accepted) state.copy(importing = true, problem = null) else state
        }
        if (!accepted) return
        applicationScope.launch {
            val placed = try {
                load(reference)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                Result.failure(error)
            }
            repository.update { state ->
                placed.fold(
                    onSuccess = { state.copy(importing = false, plateObject = it, result = null) },
                    onFailure = { state.copy(importing = false, problem = PlateProblem(PlateProblemKind.IMPORT_FAILED, it.message)) },
                )
            }
        }
    }

    private suspend fun load(reference: ExternalDocumentReference): Result<PlateObject> {
        val file = when (val imported = importModel(reference)) {
            is ModelImportOutcome.Failure -> return Result.failure(IllegalArgumentException(imported.message))
            is ModelImportOutcome.Success -> imported.model
        }
        return when (val inspected = inspectModel(file.path)) {
            is ModelInspectionOutcome.Failure -> Result.failure(IllegalArgumentException(inspected.message))
            is ModelInspectionOutcome.Success -> Result.success(PlateObject.ImportedModel(file, inspected.inspection))
        }
    }
}

class AddCalibrationCubeToPlateUseCase(private val repository: PlateRepository) {
    operator fun invoke() {
        repository.update {
            if (it.busy) it else it.copy(plateObject = PlateObject.CalibrationCube, result = null, problem = null)
        }
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
                model = when (target) {
                    is PlateObject.ImportedModel -> ModelSource.LocalFile(target.file.path)
                    PlateObject.CalibrationCube -> ModelSource.BuiltIn(BuiltInModel.CALIBRATION_CUBE_20_MM)
                },
                output = outputs.outputFor(target.outputName()),
                printerProfile = state.profiles.printer,
                filamentProfile = state.profiles.filament,
                processProfile = state.profiles.process,
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
        PlateObject.CalibrationCube -> "calibration-cube-20mm"
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
