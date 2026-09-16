package app.orcinus.shadow.feature.prepare

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.orcinus.shadow.core.model.ExternalDocumentReference
import app.orcinus.shadow.core.model.PlateState
import app.orcinus.shadow.domain.plate.AddCalibrationCubeToPlateUseCase
import app.orcinus.shadow.domain.plate.AddModelToPlateUseCase
import app.orcinus.shadow.domain.plate.CancelPlateSlicingUseCase
import app.orcinus.shadow.domain.plate.DismissPlateProblemUseCase
import app.orcinus.shadow.domain.plate.ObservePlateUseCase
import app.orcinus.shadow.domain.plate.SlicePlateUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class PrepareViewModel(
    observePlate: ObservePlateUseCase,
    private val addModelToPlate: AddModelToPlateUseCase,
    private val addCalibrationCubeToPlate: AddCalibrationCubeToPlateUseCase,
    private val slicePlate: SlicePlateUseCase,
    private val cancelPlateSlicing: CancelPlateSlicingUseCase,
    private val dismissPlateProblem: DismissPlateProblemUseCase,
) : ViewModel() {
    val state: StateFlow<PrepareUiState> = observePlate()
        .map(PlateState::toPrepareUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), observePlate().value.toPrepareUiState())

    fun addModel(reference: String) = addModelToPlate(ExternalDocumentReference(reference))

    fun addCalibrationCube() = addCalibrationCubeToPlate()

    fun slice() = slicePlate()

    fun cancelSlicing() = cancelPlateSlicing()

    fun dismissProblem() = dismissPlateProblem()

    private companion object {
        // Keeps the upstream flow through configuration changes.
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
