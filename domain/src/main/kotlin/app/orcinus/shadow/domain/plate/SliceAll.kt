package app.orcinus.shadow.domain.plate

import app.orcinus.shadow.core.model.BuildVolumeFit
import app.orcinus.shadow.core.model.PlateObject
import app.orcinus.shadow.core.model.PlateState
import app.orcinus.shadow.core.model.SliceMode
import app.orcinus.shadow.core.model.SliceOutcome
import app.orcinus.shadow.core.model.placing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** The slice button's drop-down (MainFrame's m_slice_option_btn): what the button slices from then on. */
class SetSliceModeUseCase(private val repository: PlateRepository) {
    operator fun invoke(mode: SliceMode) = repository.update { it.copy(sliceMode = mode) }
}

/**
 * The slice button (MainFrame::m_slice_btn): "Slice plate" slices the current
 * plate (on_action_slice_plate), "Slice all" every plate (on_action_slice_all),
 * as its drop-down chose.
 */
class SliceActionUseCase(
    private val slicePlate: SlicePlateUseCase,
    private val sliceAllPlates: SliceAllPlatesUseCase,
    private val repository: PlateRepository,
) {
    operator fun invoke() = when (repository.state.value.sliceMode) {
        SliceMode.PLATE -> slicePlate()
        SliceMode.ALL -> sliceAllPlates()
    }
}

/**
 * "Slice all" (Plater::priv::on_action_slice_all): the plates are sliced one
 * after another from the first, each becoming the current plate in its turn
 * (on_process_completed() selects the next one and start_next_slice() slices
 * it). A plate whose G-code still applies, or with nothing to print, is passed
 * over, as restart_background_process() has nothing to do for it; slicing
 * stops at a plate that cannot be sliced (PartPlate::can_slice(), an object
 * laid over its boundary), at a slice that failed and at one the user
 * cancelled, with that plate current.
 */
class SliceAllPlatesUseCase(
    private val slicePlate: SlicePlateUseCase,
    private val repository: PlateRepository,
    private val applicationScope: CoroutineScope,
) {
    operator fun invoke() {
        var started = false
        repository.update { state ->
            started = state.canSliceAll
            if (started) state.copy(slicingAll = true, problem = null) else state
        }
        if (!started) return
        applicationScope.launch {
            try {
                var index = 0
                while (index < repository.state.value.plates.size) {
                    val plate = onPlate(index) ?: break
                    val laidOver = plate.copies().any { it.inspection.fit == BuildVolumeFit.PARTLY_OUTSIDE }
                    val nothingToPrint = plate.copies().none { it.inspection.fit == BuildVolumeFit.INSIDE && it.printable }
                    when {
                        plate.result != null || (nothingToPrint && !laidOver) -> Unit
                        !plate.plateSliceable -> break
                        slicePlate.sliceForAll() !is SliceOutcome.Success -> break
                    }
                    index++
                }
            } finally {
                repository.update { it.copy(slicingAll = false) }
            }
        }
    }

    /**
     * Plater::select_plate() of the plate at [index] for the next turn, the
     * selection cleared, once the engine knows it; null when it does not come.
     */
    private suspend fun onPlate(index: Int): PlateState? {
        repository.update { state ->
            if (state.currentPlate == index || index !in state.plates.indices || state.busy || state.objects.any(PlateObject::placing)) {
                state
            } else {
                state.withPlates(state.platesLeft(), index).copy(selectedInstances = emptySet(), selectedPart = null, selectedRange = null)
            }
        }
        return repository.settledOn(index)
    }
}

/**
 * A plate picked in the preview's plate bar (Plater::select_sliced_plate): it
 * becomes current, and once the engine knows it, a plate without G-code that
 * still applies is sliced when it can be (select_plate() with need_slice).
 */
class SelectSlicedPlateUseCase(
    private val selectPlate: SelectPlateUseCase,
    private val slicePlate: SlicePlateUseCase,
    private val repository: PlateRepository,
    private val applicationScope: CoroutineScope,
) {
    operator fun invoke(index: Int) {
        selectPlate(index)
        if (repository.state.value.currentPlate != index) return
        applicationScope.launch {
            val plate = repository.settledOn(index) ?: return@launch
            if (plate.result == null && plate.canSlice) slicePlate()
        }
    }
}
