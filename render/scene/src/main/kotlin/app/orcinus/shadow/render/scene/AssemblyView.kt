package app.orcinus.shadow.render.scene

import app.orcinus.shadow.core.model.PlateInstance
import app.orcinus.shadow.core.model.PlateInstanceId
import app.orcinus.shadow.core.model.Transform3
import app.orcinus.shadow.render.scene.math.Affine3
import app.orcinus.shadow.render.scene.math.Vec3

/**
 * OrcaSlicer's assembly view (AssembleView, a GLCanvas3D of the
 * CanvasAssembleView type) while the plate view shows it: the model parts of
 * every copy where its assemble transformation puts them, spread by the
 * [explosionRatio] (GLVolume::explosion_ratio), with no plate, wipe tower,
 * labels or clearance, and a camera of its own. [hidden] are the copies its
 * menu's "Hide" made faint (GLCanvas3D::set_selected_visible()).
 */
data class AssemblyView(
    val explosionRatio: Double = 1.0,
    val hidden: Set<PlateInstanceId> = emptySet(),
)

/**
 * Where the volumes of a copy stand in the assembly view: GLVolume::world_matrix()
 * with the copy's assemble transformation for its instance transformation
 * (ModelInstance::get_assemble_transformation(), the identity while the copy
 * has none). The copy's offset to the assembly and the volume's own offset in
 * the object, times the explosion ratio less one, move each volume after its
 * transformation; [ownVolume] is the transformation of the object's own mesh
 * (its first ModelVolume).
 */
internal class AssemblyPlacement(instance: PlateInstance, ownVolume: Transform3) {
    private val assemble = instance.assemble?.let(::affine) ?: Affine3()
    private val fromPlate = assemble * affine(instance.inspection.placement).inverse()
    private val offsetToAssembly = instance.offsetToAssembly?.let { Vec3(it.x, it.y, it.z) } ?: Vec3.ZERO
    private val own = affine(ownVolume)

    /**
     * The volume the 3D view drew as [sceneObject], whose transformation in the
     * object is [volume]: the object's own mesh, and the paint over it, go by
     * the object's own volume.
     */
    fun place(sceneObject: SceneObject, volume: Transform3? = null, hidden: Boolean = false, faint: Boolean = false): SceneObject {
        val transformation = volume?.let(::affine) ?: own
        val explosion = (assemble * transformation).transformVector(offsetToAssembly + transformation.translation())
        return sceneObject.inAssembly(fromPlate * sceneObject.world, explosion, hidden, faint)
    }

    private companion object {
        fun affine(transform: Transform3) = Affine3(transform.columns.toDoubleArray())
    }
}
