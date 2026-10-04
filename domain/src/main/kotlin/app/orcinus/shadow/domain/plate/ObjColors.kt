package app.orcinus.shadow.domain.plate

import app.orcinus.shadow.core.model.FlushVolumesChange
import app.orcinus.shadow.core.model.ModelPath
import app.orcinus.shadow.core.model.ObjColorChoice
import app.orcinus.shadow.core.model.ObjColorDialogState
import app.orcinus.shadow.core.model.ObjColorPanel
import app.orcinus.shadow.core.model.ObjColorQuestion
import app.orcinus.shadow.core.model.PresetsOutcome
import app.orcinus.shadow.slicing.api.PlateInspector
import app.orcinus.shadow.slicing.api.PresetManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * ObjColorDialog: a load of an OBJ file with colours asks which filaments
 * print them before it goes on. The dialog waits on the plate for its answer,
 * as the desktop app's modal dialog waits; its panel's buttons and combo boxes
 * change it there, and its number of colours has the engine cluster them
 * again. OK adds the filaments of the colours it appended to the plate
 * (ObjColorPanel::send_new_filament_to_ui()) before the load goes on, which
 * paints the object with the chosen filaments; Cancel loads it without colours.
 */
class ObjColorPrompt(
    private val inspector: PlateInspector,
    private val presetManager: PresetManager,
    private val platePresets: PresetsApplier,
    private val flushVolumes: FlushVolumesUpdater,
    private val repository: PlateRepository,
    private val applicationScope: CoroutineScope,
) {
    private var reply: CompletableDeferred<Boolean>? = null

    /** The answer for the OBJ file [source], which the dialog opens on [question]. */
    suspend fun ask(source: ModelPath, question: ObjColorQuestion): ObjColorChoice {
        // get_extruder_colors_from_plater_config()
        val colours = repository.state.value.presets?.filamentColors.orEmpty()
        val answer = CompletableDeferred<Boolean>()
        reply = answer
        val panel = if (question.error) null else ObjColorPanel.open(colours, question)
        repository.update { it.copy(objColor = ObjColorDialogState(source, question, panel)) }
        val ok: Boolean
        val answered: ObjColorPanel?
        try {
            ok = answer.await()
            answered = repository.state.value.objColor?.panel
        } finally {
            reply = null
            repository.update { it.copy(objColor = null) }
        }
        // The error page's OK ends the dialog as its Cancel.
        if (!ok || answered == null) return ObjColorChoice()
        addFilaments(answered.newFilaments())
        return ObjColorChoice(answered.clusterMapFilaments)
    }

    /** OK, which waits until every combo box has a filament, or Cancel. */
    fun answer(ok: Boolean) {
        if (ok && repository.state.value.objColor?.panel?.isOk == false) return
        reply?.complete(ok)
    }

    /**
     * The spin box's number of colours, held between 1 and the recommended
     * one: the engine clusters the colours into it unless they were last.
     */
    fun setClusterNumber(number: Int) {
        val dialog = repository.state.value.objColor ?: return
        val panel = dialog.panel ?: return
        val clamped = number.coerceIn(1, maxOf(1, panel.recommended))
        updatePanel { it.copy(clusterNumber = clamped) }
        if (clamped == panel.lastClusterNumber) return
        repository.update { state -> state.copy(objColor = state.objColor?.copy(clustering = true)) }
        applicationScope.launch {
            val clusters = inspector.objColorClusters(dialog.source, clamped)
            repository.update { state ->
                val current = state.objColor?.takeIf { it.source == dialog.source } ?: return@update state
                val reclustered = current.panel?.takeIf { clusters.isNotEmpty() }?.reclustered(clusters, clamped) ?: current.panel
                state.copy(objColor = current.copy(panel = reclustered, clustering = false))
            }
        }
    }

    /** The combo box of the colour [cluster] chose its item [index]. */
    fun select(cluster: Int, index: Int) = updatePanel { it.select(cluster, index) }

    /** "Append": the colours join the filaments the boxes offer, each box its own. */
    fun append() = updatePanel { it.append().first }

    /** "Color match": each box the nearest colour. */
    fun colorMatch() = updatePanel { it.approximateMatch() }

    /** "Reset": the boxes choose nothing and lose the appended colours. */
    fun reset() = updatePanel { it.reset() }

    private fun updatePanel(change: (ObjColorPanel) -> ObjColorPanel) = repository.update { state ->
        val dialog = state.objColor ?: return@update state
        val panel = dialog.panel ?: return@update state
        state.copy(objColor = dialog.copy(panel = change(panel)))
    }

    /**
     * Sidebar::add_custom_filament() for each colour, the palette's next one
     * where the dialog has none; each ends with auto_calc_flushing_volumes().
     */
    private suspend fun addFilaments(colours: List<String?>) {
        for (colour in colours) {
            val before = repository.state.value.profiles ?: return
            val outcome = presetManager.addFilament(colour)
            platePresets.apply(before = before, outcome = outcome)
            if (outcome !is PresetsOutcome.Success) return
            val added = repository.state.value.profiles?.allFilaments?.size?.minus(1) ?: return
            flushVolumes.update(FlushVolumesChange.FILAMENT_ADDED, added)
        }
    }
}
