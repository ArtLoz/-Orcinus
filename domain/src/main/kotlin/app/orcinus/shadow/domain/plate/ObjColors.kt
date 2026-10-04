package app.orcinus.shadow.domain.plate

import app.orcinus.shadow.core.model.CameraView
import app.orcinus.shadow.core.model.FlushVolumesChange
import app.orcinus.shadow.core.model.ModelLoadOutcome
import app.orcinus.shadow.core.model.ModelPath
import app.orcinus.shadow.core.model.ObjColorChoice
import app.orcinus.shadow.core.model.ObjColorDialogState
import app.orcinus.shadow.core.model.ObjColorPanel
import app.orcinus.shadow.core.model.ObjColorQuestion
import app.orcinus.shadow.core.model.PaintKind
import app.orcinus.shadow.core.model.PaintedMesh
import app.orcinus.shadow.core.model.PaintingOutcome
import app.orcinus.shadow.core.model.PlateObject
import app.orcinus.shadow.core.model.PresetsOutcome
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.ThumbnailImage
import app.orcinus.shadow.core.model.ThumbnailSize
import app.orcinus.shadow.core.model.withPainted
import app.orcinus.shadow.domain.placed
import app.orcinus.shadow.slicing.api.PlateInspector
import app.orcinus.shadow.slicing.api.PresetManager
import app.orcinus.shadow.storage.api.SceneFiles
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Plater::update_obj_preview_thumbnail(): a picture of one object from
 * [view], in [colors] by filament, written to [file] at [size].
 */
fun interface ObjectPreviewRenderer {
    suspend fun render(plateObject: PlateObject, colors: List<String>, view: CameraView, size: ThumbnailSize, file: ScenePath): ThumbnailImage?
}

/**
 * ObjColorDialog: a load of an OBJ file with colours asks which filaments
 * print them before it goes on. The dialog waits on the plate for its answer,
 * as the desktop app's modal dialog waits; its panel's buttons and combo boxes
 * change it there, and its number of colours has the engine cluster them
 * again. OK adds the filaments of the colours it appended to the plate
 * (ObjColorPanel::send_new_filament_to_ui()) before the load goes on, which
 * paints the object with the chosen filaments; Cancel loads it without colours.
 * Every change of the panel draws the thumbnail of the object in the chosen
 * filaments again (deal_thumbnail()), from the view the dialog chose.
 */
class ObjColorPrompt(
    private val inspector: PlateInspector,
    private val presetManager: PresetManager,
    private val platePresets: PresetsApplier,
    private val flushVolumes: FlushVolumesUpdater,
    private val repository: PlateRepository,
    private val applicationScope: CoroutineScope,
    private val sceneFiles: SceneFiles? = null,
    private val previewRenderer: ObjectPreviewRenderer? = null,
) {
    private var reply: CompletableDeferred<Boolean>? = null
    private var previewJob: Job? = null

    // The files of the thumbnail shown, which the next one replaces.
    private var previewPrefix: ScenePath? = null

    /** The answer for the OBJ file [source], which the dialog opens on [question]. */
    suspend fun ask(source: ModelPath, question: ObjColorQuestion): ObjColorChoice {
        // get_extruder_colors_from_plater_config()
        val colours = repository.state.value.presets?.filamentColors.orEmpty()
        val answer = CompletableDeferred<Boolean>()
        reply = answer
        val panel = if (question.error) null else ObjColorPanel.open(colours, question)
        repository.update { it.copy(objColor = ObjColorDialogState(source, question, panel)) }
        drawPreview()
        val ok: Boolean
        val answered: ObjColorPanel?
        try {
            ok = answer.await()
            answered = repository.state.value.objColor?.panel
        } finally {
            reply = null
            previewJob?.cancel()
            repository.update { it.copy(objColor = null) }
            previewPrefix?.let { sceneFiles?.deleteImport(it) }
            previewPrefix = null
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
            drawPreview()
        }
    }

    /** set_view_angle_type(): the thumbnail from another view. */
    fun setView(view: CameraView) {
        repository.update { state -> state.copy(objColor = state.objColor?.copy(view = view)) }
        drawPreview()
    }

    /** The combo box of the colour [cluster] chose its item [index]. */
    fun select(cluster: Int, index: Int) = updatePanel { it.select(cluster, index) }

    /** "Append": the colours join the filaments the boxes offer, each box its own. */
    fun append() = updatePanel { it.append().first }

    /** "Color match": each box the nearest colour. */
    fun colorMatch() = updatePanel { it.approximateMatch() }

    /** "Reset": the boxes choose nothing and lose the appended colours. */
    fun reset() = updatePanel { it.reset() }

    private fun updatePanel(change: (ObjColorPanel) -> ObjColorPanel) {
        repository.update { state ->
            val dialog = state.objColor ?: return@update state
            val panel = dialog.panel ?: return@update state
            state.copy(objColor = dialog.copy(panel = change(panel)))
        }
        drawPreview()
    }

    /**
     * deal_thumbnail() and generate_thumbnail(): the engine paints the object
     * with the boxes' filaments, and the picture draws it in the plate's
     * filament colours followed by the colours the boxes chose
     * (m_new_add_colors). A newer change cancels a picture still being drawn.
     */
    private fun drawPreview() {
        val files = sceneFiles ?: return
        val renderer = previewRenderer ?: return
        val dialog = repository.state.value.objColor ?: return
        val panel = dialog.panel ?: return
        previewJob?.cancel()
        previewJob = applicationScope.launch {
            val prefix = files.newImportPrefix()
            val outcome = inspector.objColorPreview(dialog.source, ObjColorChoice(panel.clusterMapFilaments), prefix)
            val loaded = (outcome as? ModelLoadOutcome.Success)?.objects?.singleOrNull()?.toPlateObject("")
            // The volume draws the colours painted on it (GLVolume's mmu_segmentation_facets).
            val profiles = repository.state.value.profiles
            val colors = if (loaded == null || profiles == null) null else inspector.paintedColors(loaded.placed(), profiles, ScenePath(prefix.value + "-colors"))
            val painted = (colors as? PaintingOutcome.Success)?.surface?.let { surface ->
                loaded?.withPainted(loaded.painted, surface.meshes.mapIndexed { index, path ->
                    PaintedMesh(surface.states[index], path, PaintKind.COLOR, surface.volumes.getOrElse(index) { 0 })
                })
            } ?: loaded
            val file = ScenePath(prefix.value + "-thumbnail.rgba")
            val image = painted?.let { renderer.render(it, panel.colours + panel.newAddColors, dialog.view, PREVIEW_SIZE, file) }
            var shown = false
            repository.update { state ->
                val current = state.objColor?.takeIf { it.source == dialog.source && image != null } ?: return@update state
                shown = true
                state.copy(objColor = current.copy(preview = image))
            }
            if (!shown) return@launch files.deleteImport(prefix)
            previewPrefix?.let { files.deleteImport(it) }
            previewPrefix = prefix
        }
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

    private companion object {
        /** PartPlate::plate_thumbnail_width and plate_thumbnail_height. */
        val PREVIEW_SIZE = ThumbnailSize(512, 512)
    }
}
