package app.orcinus.shadow.domain.plate

import app.orcinus.shadow.core.model.CutConnector
import app.orcinus.shadow.core.model.CutObjectOutcome
import app.orcinus.shadow.core.model.CutPlaneOutcome
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.Transform3
import app.orcinus.shadow.core.model.mesh
import app.orcinus.shadow.domain.placed
import app.orcinus.shadow.slicing.api.PlateInspector
import app.orcinus.shadow.storage.api.SceneFiles

/**
 * OrcaSlicer's cut gizmo (GLGizmoCut3D) sees its plane through the engine:
 * while it is open on a copy the engine keeps the object, as the gizmo's
 * clippers keep its meshes, and describes every position of the plane — the
 * size of what it cuts, whether it goes through the object, and the outline
 * and section of the cut, as meshes for the 3D view. The cut itself is
 * [EditPlateObjectUseCase.cut].
 */
class CutObjectUseCase(
    private val inspector: PlateInspector,
    private val sceneFiles: SceneFiles,
    private val repository: PlateRepository,
) {
    /** Where the meshes of the open gizmo are written; null while it is closed. */
    private var meshes: ScenePath? = null

    /** Opens the gizmo on the copy at [instance] of the object with the [mesh] file. */
    suspend fun begin(mesh: ScenePath, instance: Int): CutObjectOutcome {
        val state = repository.state.value
        val target = state.objects.firstOrNull { it.mesh == mesh }
        val profiles = state.profiles
        if (target == null || profiles == null || state.busy || instance !in target.instances.indices) {
            return CutObjectOutcome.Failure("The plate is not ready to be cut")
        }
        end()
        meshes = sceneFiles.newCutMeshes()
        return inspector.beginCut(target.placed(), instance, profiles)
    }

    /** What the gizmo shows of [plane], ObjectCut.plane, with [connectors] on it and the snaps' proportions. */
    suspend fun describe(plane: Transform3, connectors: List<CutConnector>, snapSpace: Double, snapBulge: Double): CutPlaneOutcome {
        val prefix = meshes ?: return CutPlaneOutcome.Failure("The cut gizmo is not open")
        return inspector.describeCutPlane(plane, connectors, snapSpace, snapBulge, prefix)
    }

    /** Closes the gizmo: the engine lets the object go, and the meshes it wrote are gone. */
    suspend fun end() {
        val prefix = meshes ?: return
        meshes = null
        inspector.endCut()
        sceneFiles.deleteImport(prefix)
    }
}
