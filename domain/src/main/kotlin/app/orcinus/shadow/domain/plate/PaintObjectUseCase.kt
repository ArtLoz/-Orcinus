package app.orcinus.shadow.domain.plate

import app.orcinus.shadow.core.model.PaintKind
import app.orcinus.shadow.core.model.PaintPlacement
import app.orcinus.shadow.core.model.PaintStroke
import app.orcinus.shadow.core.model.PaintedMesh
import app.orcinus.shadow.core.model.PaintingOutcome
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.mesh
import app.orcinus.shadow.core.model.withPainted
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

    /** Opens the tool of [kind] on the object [mesh] names, or on one of its parts, on the copy of [placement]. */
    suspend fun begin(mesh: ScenePath, kind: PaintKind = PaintKind.COLOR, part: Int? = null, placement: PaintPlacement = PaintPlacement()): PaintingOutcome {
        val state = repository.state.value
        val target = state.objects.firstOrNull { it.mesh == mesh }
        val profiles = state.profiles
        if (target == null || profiles == null || state.busy) {
            return PaintingOutcome.Failure("The plate is not ready to be painted")
        }
        val prefix = sceneFiles.newPaintedMeshes()
        val outcome = inspector.beginPainting(target.placed(), part, kind, profiles, target.painted, prefix, placement)
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
            // LeavingGizmoNoAction: nothing painted, nothing to undo.
            if (outcome !is PaintingOutcome.Success || target == null || outcome.surface.facets == target.painted) return@update closed
            val painted = target.withPainted(outcome.surface.facets, shown ?: target.paintedMeshes)
            (if (before != null) closed.recorded(before) else closed)
                .copy(objects = state.objects.map { if (it.mesh == mesh) painted else it }, result = null)
        }
        return outcome
    }

    /** The painted triangles of the object, which the 3D view draws in the colours of their kind and state. */
    private fun show(mesh: ScenePath, outcome: PaintingOutcome.Success) {
        val meshes = outcome.surface.states.zip(outcome.surface.meshes) { state, path -> PaintedMesh(state, path, kind) }
        repository.update { state ->
            val target = state.objects.firstOrNull { it.mesh == mesh } ?: return@update state
            if (target.paintedMeshes == meshes) return@update state
            val painted = target.withPainted(target.painted, meshes)
            state.copy(objects = state.objects.map { if (it.mesh == mesh) painted else it })
        }
    }
}
