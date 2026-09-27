package app.orcinus.shadow.domain.plate

import app.orcinus.shadow.core.model.CutConnector
import app.orcinus.shadow.core.model.CutGroove
import app.orcinus.shadow.core.model.CutObjectOutcome
import app.orcinus.shadow.core.model.CutPartSelection
import app.orcinus.shadow.core.model.CutPartsOutcome
import app.orcinus.shadow.core.model.CutPlaneOutcome
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.Transform3
import app.orcinus.shadow.core.model.Vector3
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

    /**
     * What the gizmo shows of [plane], ObjectCut.plane, with [connectors] on it
     * and the snaps' proportions, or with the dovetail cut's [groove], whose
     * parts are worked out too while [preview]; the section leaves out the
     * contours the pieces of a right click, [parts], join over.
     */
    suspend fun describe(
        plane: Transform3,
        connectors: List<CutConnector>,
        snapSpace: Double,
        snapBulge: Double,
        groove: CutGroove? = null,
        preview: Boolean = false,
        parts: CutPartSelection? = null,
    ): CutPlaneOutcome {
        val prefix = meshes ?: return CutPlaneOutcome.Failure("The cut gizmo is not open")
        return inspector.describeCutPlane(plane, connectors, snapSpace, snapBulge, groove, preview, parts, prefix)
    }

    /**
     * A right click on the object (process_contours() and toggle_selection()):
     * its pieces split by [CutPartSelection.plane] as they go now, the one the
     * ray from [origin] along [direction] meets first turned over.
     */
    suspend fun selectPart(parts: CutPartSelection, origin: Vector3, direction: Vector3): CutPartsOutcome {
        val prefix = meshes ?: return CutPartsOutcome.Failure("The cut gizmo is not open")
        return inspector.selectCutPart(parts, origin, direction, prefix)
    }

    /** Closes the gizmo: the engine lets the object go, and the meshes it wrote are gone. */
    suspend fun end() {
        val prefix = meshes ?: return
        meshes = null
        inspector.endCut()
        sceneFiles.deleteImport(prefix)
    }
}
