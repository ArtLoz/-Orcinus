package app.orcinus.shadow.domain.plate

import app.orcinus.shadow.core.model.ModelSettings
import app.orcinus.shadow.core.model.PaintKind
import app.orcinus.shadow.core.model.PaintPlacement
import app.orcinus.shadow.core.model.PaintStroke
import app.orcinus.shadow.core.model.PaintedMesh
import app.orcinus.shadow.core.model.PaintingOutcome
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.VolumeType
import app.orcinus.shadow.core.model.mesh
import app.orcinus.shadow.core.model.withPainted
import app.orcinus.shadow.core.model.withParts
import app.orcinus.shadow.core.model.withSettings
import app.orcinus.shadow.core.model.withVolume
import app.orcinus.shadow.domain.placed
import app.orcinus.shadow.slicing.api.PlateInspector
import app.orcinus.shadow.storage.api.SceneFiles

/**
 * OrcaSlicer's painting gizmos (GLGizmoPainterBase: colour, supports, seam and
 * fuzzy skin) for a touch screen: the tool of a kind opens on the object the
 * user picked, every drag of a finger paints a stroke, and closing it keeps
 * the painted facets with the object, so the plate is sliced with them. The
 * engine holds the painted mesh while the tool is open, as the desktop gizmo
 * holds its selectors. The plate before the tool opened is kept (UndoRedo's
 * EnteringGizmo), and the painting is one step of Undo once the tool leaves
 * the object painted otherwise.
 *
 * The object shows the paint of the open tool, as the gizmo renders its
 * selectors in place of the volume; colour painting stays shown once its tool
 * closes, and the paint of the other kinds goes with their tool.
 */
class PaintObjectUseCase(
    private val inspector: PlateInspector,
    private val sceneFiles: SceneFiles,
    private val repository: PlateRepository,
) {
    /** Where the painted triangles are written while the tool is open. */
    private var meshPrefix: ScenePath? = null
    private var painting: ScenePath? = null
    private var kind = PaintKind.COLOR

    /** What the object showed before a tool of another kind than colour opened. */
    private var shownBefore: List<PaintedMesh> = emptyList()

    /** Opens the tool of [kind] on the object [mesh] names, every model part of it, on the copy of [placement]. */
    suspend fun begin(mesh: ScenePath, kind: PaintKind = PaintKind.COLOR, placement: PaintPlacement = PaintPlacement()): PaintingOutcome {
        val state = repository.state.value
        val target = state.objects.firstOrNull { it.mesh == mesh }
        val profiles = state.profiles
        if (target == null || profiles == null || state.busy) {
            return PaintingOutcome.Failure("The plate is not ready to be painted")
        }
        val prefix = sceneFiles.newPaintedMeshes()
        val outcome = inspector.beginPainting(target.placed(), kind, profiles, prefix, placement)
        if (outcome is PaintingOutcome.Success) {
            meshPrefix = prefix
            painting = mesh
            this.kind = kind
            shownBefore = target.paintedMeshes
            repository.update { it.copy(history = it.history.copy(beforeTool = it.snapshot())) }
            show(mesh, outcome)
        }
        return outcome
    }

    /** One touch of a finger on the model; the painted triangles come back at once. */
    suspend fun stroke(stroke: PaintStroke): PaintingOutcome {
        val prefix = meshPrefix ?: return PaintingOutcome.Failure("The painting tool is not open")
        val outcome = inspector.paint(stroke, prefix)
        val mesh = painting
        if (outcome is PaintingOutcome.Success && mesh != null) {
            show(mesh, outcome)
        }
        return outcome
    }

    /**
     * GLGizmoFuzzySkin's warning: fuzzy skin is disabled for the object [mesh]
     * names, by its own settings or else by the process preset, so the fuzzy
     * skin painted on it does not take effect.
     */
    suspend fun fuzzySkinDisabled(mesh: ScenePath): Boolean {
        val state = repository.state.value
        val target = state.objects.firstOrNull { it.mesh == mesh } ?: return false
        val profiles = state.profiles ?: return false
        return inspector.fuzzySkinDisabled(target.placed(), profiles)
    }

    /** The tool's own Undo and Redo of a stroke (the gizmo's undo/redo stack). */
    suspend fun undo(): PaintingOutcome = step { prefix -> inspector.undoPainting(prefix) }

    suspend fun redo(): PaintingOutcome = step { prefix -> inspector.redoPainting(prefix) }

    /** "Erase all": the tool's kind of paint comes off the object, which the tool's Undo brings back. */
    suspend fun clear(): PaintingOutcome = step { prefix -> inspector.clearPainting(prefix) }

    /**
     * GLGizmoMmuSegmentation::remap_filament_assignments(): the facets painted
     * with filament i + 1 take filament [remap] [i] + 1 on every model part,
     * which the tool's Undo brings back, and the filament of each model part
     * follows its own (ModelVolume::extruder_id()): its own setting where it
     * has one, else the object's, the volumes in turn, each reading the
     * object's as the ones before left it.
     */
    suspend fun remap(remap: List<Int>): PaintingOutcome {
        if (remap.withIndex().all { (source, target) -> source == target }) return PaintingOutcome.Failure("Nothing to remap")
        val outcome = step { prefix -> inspector.remapPainting(remap, prefix) }
        val mesh = painting ?: return outcome
        if (outcome !is PaintingOutcome.Success) return outcome
        repository.update { state ->
            val target = state.objects.firstOrNull { it.mesh == mesh } ?: return@update state
            var objectSettings = target.settings
            // The filament a model part follows, and where it is set.
            fun remapped(own: ModelSettings): Pair<ModelSettings, Boolean> {
                val extruder = own.extruderNumber.takeIf { it > 0 } ?: objectSettings.extruderNumber
                val current = if (extruder > 0) extruder - 1 else 0
                val destination = remap.getOrNull(current)?.takeIf { it != current } ?: return own to false
                if (own.extruderNumber != 0) return own.withExtruder(destination + 1) to true
                objectSettings = objectSettings.withExtruder(destination + 1)
                return own to false
            }
            val volume = target.volume.copy(settings = remapped(target.volume.settings).first)
            val parts = target.parts.map { part -> if (part.type == VolumeType.PART) part.copy(settings = remapped(part.settings).first) else part }
            val edited = target.withSettings(objectSettings).withVolume(volume).withParts(parts)
            if (edited == target) state else state.copy(objects = state.objects.map { if (it.mesh == mesh) edited else it })
        }
        return outcome
    }

    /**
     * The gap fill tool with its gap area, in square millimetres: the object
     * shows its paint as the gap fill would leave it; null leaves the tool.
     */
    suspend fun setGapFill(gapArea: Double?): PaintingOutcome = step { prefix -> inspector.setGapFill(gapArea, prefix) }

    /** "Perform" of the gap fill, which the tool's Undo brings back. */
    suspend fun fillGaps(): PaintingOutcome = step { prefix -> inspector.fillGaps(prefix) }

    private suspend fun step(action: suspend (ScenePath) -> PaintingOutcome): PaintingOutcome {
        val prefix = meshPrefix ?: return PaintingOutcome.Failure("The painting tool is not open")
        val outcome = action(prefix)
        val mesh = painting
        if (outcome is PaintingOutcome.Success && mesh != null) {
            show(mesh, outcome)
        }
        return outcome
    }

    /**
     * Closes the tool: the painted facets are kept with the object, which the
     * slicer then prints with, and G-code sliced before no longer applies.
     */
    suspend fun end(): PaintingOutcome {
        val mesh = painting ?: return PaintingOutcome.Failure("The painting tool is not open")
        val outcome = inspector.endPainting()
        meshPrefix = null
        painting = null
        // Colour painting stays shown; the paint of another kind goes with its tool.
        val shown = if (kind == PaintKind.COLOR) null else shownBefore
        shownBefore = emptyList()
        repository.update { state ->
            val before = state.history.beforeTool
            val target = state.objects.firstOrNull { it.mesh == mesh }
            val objects = state.objects.map { if (it.mesh == mesh && shown != null) it.withPainted(it.painted, shown) else it }
            val closed = state.copy(history = state.history.copy(beforeTool = null), objects = objects)
            // LeavingGizmoNoAction: nothing painted, and no filament remapped, nothing to undo.
            if (outcome !is PaintingOutcome.Success || target == null) return@update closed
            val partFacets = outcome.surface.partFacets
            val parts = target.parts.mapIndexed { index, part -> partFacets.getOrNull(index)?.let { part.copy(painted = it) } ?: part }
            val unpainted = outcome.surface.facets == target.painted && parts == target.parts
            val remapped = before?.objects?.firstOrNull { it.mesh == mesh }?.let { opened ->
                opened.settings != target.settings || opened.volume.settings != target.volume.settings ||
                    opened.parts.map { it.settings } != target.parts.map { it.settings }
            } ?: false
            if (unpainted && !remapped) return@update closed
            val painted = target.withPainted(outcome.surface.facets, shown ?: target.paintedMeshes).withParts(parts)
            // reduce_noisy_snapshots(): the session stays one GizmoAction snapshot, from before its first stroke.
            (if (before != null) closed.recorded(before, gizmoAction = true) else closed)
                .copy(objects = state.objects.map { if (it.mesh == mesh) painted else it }, result = null)
        }
        return outcome
    }

    /** The painted triangles of the object, which the 3D view draws in the colours of their kind and state. */
    private fun show(mesh: ScenePath, outcome: PaintingOutcome.Success) {
        val meshes = outcome.surface.meshes.mapIndexed { index, path ->
            PaintedMesh(outcome.surface.states[index], path, kind, outcome.surface.volumes.getOrElse(index) { 0 })
        }
        repository.update { state ->
            val target = state.objects.firstOrNull { it.mesh == mesh } ?: return@update state
            if (target.paintedMeshes == meshes) return@update state
            val painted = target.withPainted(target.painted, meshes)
            state.copy(objects = state.objects.map { if (it.mesh == mesh) painted else it })
        }
    }
}

/** The volume's or object's "extruder" setting; 0 takes it off. */
private fun ModelSettings.withExtruder(extruder: Int): ModelSettings =
    ModelSettings(if (extruder <= 0) values - "extruder" else values + ("extruder" to extruder.toString()))
