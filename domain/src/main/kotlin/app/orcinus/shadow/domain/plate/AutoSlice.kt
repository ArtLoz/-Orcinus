package app.orcinus.shadow.domain.plate

import app.orcinus.shadow.core.model.AppConfigKeys
import app.orcinus.shadow.core.model.BuildVolumeFit
import app.orcinus.shadow.core.model.PlateState
import app.orcinus.shadow.domain.preferences.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * "Auto slice after changes" (Plater::priv::schedule_auto_reslice_if_needed()
 * and trigger_auto_reslice_now()), which a change of the settings calls
 * (Plater::on_config_change()). While Preview is shown and the current plate
 * prints something, the plate is sliced again once the delay the Preferences
 * set (auto_slice_change_delay_seconds) has passed with no further change, or
 * at once for 0. A slice that is running is cancelled, and the plate is sliced
 * once it has stopped (on_process_completed()). Plater::reslice() restarts
 * nothing whose G-code still applies, so a plate whose G-code the change left
 * is not sliced again.
 */
class AutoSliceUseCase(
    private val preferences: AppPreferences,
    private val slicePlate: SlicePlateUseCase,
    private val cancelSlicing: CancelPlateSlicingUseCase,
    private val repository: PlateRepository,
    private val applicationScope: CoroutineScope,
) {
    private val lock = Any()
    private var previewShown = false

    // auto_reslice_pending, auto_reslice_after_cancel and auto_reslice_timer.
    private var pending = false
    private var afterCancel = false
    private var timer: Job? = null

    /**
     * Plater::priv::is_preview_shown(): whether the workspace shows Preview.
     * Showing it slices the current plate (set_current_panel()'s do_reslice()).
     */
    fun setPreviewShown(shown: Boolean) {
        val shownNow = synchronized(lock) {
            val was = previewShown
            previewShown = shown
            shown && !was
        }
        if (shownNow) doReslice()
    }

    /**
     * do_reslice(): a plate with objects that fit its build volume, with a
     * printable copy, is sliced unless a slice runs; Plater::reslice() then
     * restarts nothing whose G-code still applies.
     */
    private fun doReslice() {
        val state = repository.state.value
        val fits = state.copies().none { it.inspection.fit == BuildVolumeFit.PARTLY_OUTSIDE }
        if (state.objects.isEmpty() || !fits || !state.hasPrintableInstances() || state.running) return
        if (state.result != null) return
        slicePlate()
    }

    /** schedule_auto_reslice_if_needed(): the settings changed. */
    fun onConfigChange() {
        synchronized(lock) {
            if (!enabled()) return
            val state = repository.state.value
            if (state.objects.isEmpty() || !state.hasPrintableInstances()) return

            if (state.running) {
                // Remember to restart once the current slice stops, and cancel it now.
                if (!afterCancel) {
                    afterCancel = true
                    applicationScope.launch {
                        repository.state.first { !it.running }
                        synchronized(lock) { afterCancel = false }
                        onConfigChange()
                    }
                }
                cancelSlicing()
                return
            }

            val seconds = AppConfigKeys.autoSliceDelaySeconds(preferences[AppConfigKeys.AUTO_SLICE_CHANGE_DELAY_SECONDS])
            if (seconds > 0) {
                pending = true
                timer?.cancel()
                timer = applicationScope.launch {
                    delay(seconds * 1000L)
                    trigger()
                }
                return
            }

            if (pending) return
            pending = true
            timer?.cancel()
            timer = applicationScope.launch { trigger() }
        }
    }

    /** trigger_auto_reslice_now() */
    private fun trigger() {
        synchronized(lock) {
            pending = false
            if (!enabled()) return
        }
        val state = repository.state.value
        if (state.objects.isEmpty() || state.running || !state.hasPrintableInstances()) return
        // Plater::reslice(): the background process restarts only when the change invalidated its G-code.
        if (state.result != null) return
        slicePlate()
    }

    private fun enabled() = preferences.bool(AppConfigKeys.AUTO_SLICE_AFTER_CHANGE) && previewShown
}

/** background_process.running() || m_is_slicing: a slice of the plate, or "Slice all", is running. */
private val PlateState.running: Boolean get() = slicing != null || slicingAll

/**
 * PartPlate::has_printable_instances(): a copy on the current plate that is
 * printed and lies within it.
 */
private fun PlateState.hasPrintableInstances(): Boolean =
    copies().any { it.printable && it.inspection.fit == BuildVolumeFit.INSIDE }
