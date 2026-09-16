package app.orcinus.shadow.feature.preview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.orcinus.shadow.core.model.PlateSliceResult
import app.orcinus.shadow.core.model.PlateState
import app.orcinus.shadow.domain.plate.ObservePlateUseCase
import app.orcinus.shadow.domain.plate.SlicePlateUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class PreviewUiState(
    val result: PlateSliceResult?,
    val canSlice: Boolean,
)

class PreviewViewModel(
    observePlate: ObservePlateUseCase,
    private val slicePlate: SlicePlateUseCase,
) : ViewModel() {
    val state: StateFlow<PreviewUiState> = observePlate()
        .map(PlateState::toPreviewUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), observePlate().value.toPreviewUiState())

    fun slice() = slicePlate()

    private companion object {
        // Keeps the upstream flow through configuration changes.
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

private fun PlateState.toPreviewUiState() = PreviewUiState(result = result, canSlice = canSlice)
