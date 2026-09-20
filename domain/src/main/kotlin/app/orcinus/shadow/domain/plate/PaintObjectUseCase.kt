package app.orcinus.shadow.domain.plate

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
 * OrcaSlicer's colour painting gizmo (GLGizmoMmuSegmentation) for a touch
 * screen: the tool opens on the object the user picked, every drag of a finger
 * paints a stroke, and closing it keeps the painted facets with the object, so
 * the plate is sliced with them. The engine holds the painted mesh while the
 * tool is open, as the desktop gizmo holds its selectors.
 */
class PaintObjectUseCase(
    private val inspector: PlateInspector,
    private val sceneFiles: SceneFiles,
    private val repository: PlateRepository,
) {
    /** Where the painted triangles are written while the tool is open. */
    private var meshPrefix: ScenePath? = null
    private var painting: ScenePath? = null

    /** Opens the tool on the object [mesh] names, or on one of its parts. */
    suspend fun begin(mesh: ScenePath, part: Int? = null): PaintingOutcome {
        val state = repository.state.value
        val target = state.objects.firstOrNull { it.mesh == mesh }
        val profiles = state.profiles
        if (target == null || profiles == null || state.busy) {
            return PaintingOutcome.Failure("The plate is not ready to be painted")
        }
        val prefix = sceneFiles.newPaintedMeshes()
        val outcome = inspector.beginPainting(target.placed(), part, profiles, target.painted, prefix)
        if (outcome is PaintingOutcome.Success) {
            meshPrefix = prefix
            painting = mesh
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
     * Closes the tool: the painted facets are kept with the object, which the
     * slicer then prints with, and G-code sliced before no longer applies.
     */
    suspend fun end(): PaintingOutcome {
        val mesh = painting ?: return PaintingOutcome.Failure("The painting tool is not open")
        val outcome = inspector.endPainting()
        meshPrefix = null
        painting = null
        if (outcome is PaintingOutcome.Success) {
            repository.update { state ->
                val target = state.objects.firstOrNull { it.mesh == mesh } ?: return@update state
                val painted = target.withPainted(outcome.surface.facets, target.paintedMeshes)
                state.copy(objects = state.objects.map { if (it.mesh == mesh) painted else it }, result = null)
            }
        }
        return outcome
    }

    /** The painted triangles of the object, which the 3D view draws in the filament colours. */
    private fun show(mesh: ScenePath, outcome: PaintingOutcome.Success) {
        val meshes = outcome.surface.filaments.zip(outcome.surface.meshes) { filament, path -> PaintedMesh(filament, path) }
        repository.update { state ->
            val target = state.objects.firstOrNull { it.mesh == mesh } ?: return@update state
            if (target.paintedMeshes == meshes) return@update state
            val painted = target.withPainted(target.painted, meshes)
            state.copy(objects = state.objects.map { if (it.mesh == mesh) painted else it })
        }
    }
}
