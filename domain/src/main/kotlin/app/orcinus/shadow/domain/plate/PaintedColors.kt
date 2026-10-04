package app.orcinus.shadow.domain.plate

import app.orcinus.shadow.core.model.PaintKind
import app.orcinus.shadow.core.model.PaintedMesh
import app.orcinus.shadow.core.model.PaintingOutcome
import app.orcinus.shadow.core.model.PlateObject
import app.orcinus.shadow.core.model.PlateState
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.SlicingProfileSelection
import app.orcinus.shadow.core.model.mesh
import app.orcinus.shadow.core.model.withPainted
import app.orcinus.shadow.domain.placed
import app.orcinus.shadow.slicing.api.PlateInspector
import app.orcinus.shadow.storage.api.SceneFiles
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * GLVolume draws the colours painted on a volume (mmu_segmentation_facets)
 * whenever the 3D view shows it, not only while the painting tool is open: an
 * object that comes to the plate painted (from a file, an OBJ file's colours,
 * an edit) has the engine write the triangles of each filament, which the view
 * draws over it. A painting tool that is open shows its own.
 */
class PaintedColorUpdates(
    private val inspector: PlateInspector,
    private val sceneFiles: SceneFiles,
    private val repository: PlateRepository,
    private val applicationScope: CoroutineScope,
) {
    // The painting of every object asked for, by its mesh, which a change of either asks again.
    private val asked = mutableSetOf<Pair<ScenePath, List<String>>>()

    fun start() {
        applicationScope.launch {
            repository.state.collect { state -> ask(state) }
        }
    }

    private suspend fun ask(state: PlateState) {
        if (state.history.beforeTool != null) return
        val profiles = state.profiles ?: return
        for (target in state.objects) {
            if (target.paintedMeshes.isNotEmpty() || !target.isPainted()) continue
            val key = target.mesh to target.paintings()
            if (!asked.add(key)) continue
            val meshes = paintedColors(target, profiles) ?: continue
            if (meshes.isEmpty()) continue
            repository.update { current ->
                val now = current.objects.firstOrNull { it.mesh == target.mesh }
                if (now == null || now.paintings() != key.second || now.paintedMeshes.isNotEmpty() || current.history.beforeTool != null) {
                    return@update current
                }
                current.copy(objects = current.objects.map { if (it.mesh == target.mesh) it.withPainted(it.painted, meshes) else it })
            }
        }
    }

    private suspend fun paintedColors(target: PlateObject, profiles: SlicingProfileSelection): List<PaintedMesh>? {
        val outcome = inspector.paintedColors(target.placed(), profiles, sceneFiles.newPaintedMeshes())
        val surface = (outcome as? PaintingOutcome.Success)?.surface ?: return null
        return surface.meshes.mapIndexed { index, path ->
            PaintedMesh(surface.states[index], path, PaintKind.COLOR, surface.volumes.getOrElse(index) { 0 })
        }
    }
}

/** The painting of the object's own mesh and of each part. */
private fun PlateObject.paintings(): List<String> = listOf(painted.value) + parts.map { it.painted.value }

private fun PlateObject.isPainted(): Boolean = paintings().any(String::isNotEmpty)
