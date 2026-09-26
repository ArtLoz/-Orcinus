package app.orcinus.shadow.domain.plate

import app.orcinus.shadow.core.model.BuildVolumeFit
import app.orcinus.shadow.core.model.SliceStatistics
import app.orcinus.shadow.core.model.nonemptyPlates
import app.orcinus.shadow.core.model.partPlates
import app.orcinus.shadow.core.model.plateGrid
import app.orcinus.shadow.core.model.plateOf
import app.orcinus.shadow.core.model.plateOrigins
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
            // _update_select_plate_toolbar_stats_item(true): the statistics of all plates show once there is an item for them.
            if (started) state.copy(slicingAll = true, problem = null, allPlatesStats = state.allPlatesStats || state.nonemptyPlates().size > 1) else state
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

/**
 * The all plates stats item of the preview's plate bar
 * (GLCanvas3D::_render_imgui_select_plate_toolbar): picked, it slices every
 * plate (EVT_GLTOOLBAR_SLICE_ALL), whose statistics then show, unless
 * something is being sliced already.
 */
class ShowAllPlatesStatsUseCase(
    private val sliceAllPlates: SliceAllPlatesUseCase,
    private val repository: PlateRepository,
) {
    operator fun invoke() {
        if (repository.state.value.canSliceAll) sliceAllPlates()
    }
}

/** IMToolbarItem::SliceState of the all plates stats item. */
enum class AllPlatesSliceState {
    UNSLICED,
    SLICING,
    SLICED,

    /** A plate cannot be sliced (SLICE_FAILED): an object is laid over its boundary. */
    FAILED,
}

/**
 * The all plates stats item of the preview's plate bar: with more than one
 * plate with objects on it (_update_select_plate_toolbar_stats_item), how many
 * of them have G-code that still applies, and once all of them have, what
 * each was sliced into, for GCodeViewer::render_all_plates_stats().
 */
data class AllPlatesStats(
    val state: AllPlatesSliceState,
    val sliced: Int,
    val total: Int,
    /** The plate being sliced, from 1, while slicing runs. */
    val slicingPlate: Int?,
    /** The share of the slicing done, 0 to 1, over the plates with objects. */
    val progress: Float,
    val selected: Boolean,
    /** The statistics of the plates with objects, once they are all sliced. */
    val statistics: List<SliceStatistics>,
)

/** The all plates stats item of the plates; null while there is no item. */
fun PlateState.allPlatesStats(): AllPlatesStats? {
    val grid = plateGrid ?: return null
    val height = plate?.geometry?.printableHeight ?: return null
    val nonempty = nonemptyPlates()
    if (nonempty.size <= 1) return null
    val basis = sliceBasis()
    // PartPlate::is_slice_result_valid(): the current plate's G-code, and another plate's while what it was sliced from stays.
    val results = partPlates().mapIndexed { index, plate ->
        if (index == currentPlate) result else plate.result?.takeIf { plate.basis == null || plate.basis == basis }
    }
    val origins = plateOrigins()
    // SLICE_FAILED: no G-code, and a copy on the plate is not inside it whole (PartPlate::can_slice()).
    val failed = nonempty.any { index ->
        results[index] == null && copies().any { plateOf(it) == index && !grid.contains(it.inspection, origins[index], height) }
    }
    val sliced = nonempty.count { results[it] != null }
    val running = slicing?.let { job -> currentPlate.takeIf { it in nonempty }?.let { job.progress?.fraction ?: 0f } }
    return AllPlatesStats(
        state = when {
            failed -> AllPlatesSliceState.FAILED
            sliced == 0 -> AllPlatesSliceState.UNSLICED
            sliced == nonempty.size -> AllPlatesSliceState.SLICED
            else -> AllPlatesSliceState.SLICING
        },
        sliced = sliced,
        total = nonempty.size,
        slicingPlate = if (slicing != null) currentPlate + 1 else null,
        progress = (sliced + (running ?: 0f)) / nonempty.size,
        selected = allPlatesStats,
        statistics = if (sliced == nonempty.size) nonempty.mapNotNull { results[it]?.statistics } else emptyList(),
    )
}
