package app.orcinus.shadow.render.scene

import app.orcinus.shadow.core.model.CoordinateSystem
import app.orcinus.shadow.core.model.Transform3
import app.orcinus.shadow.core.model.Vector3
import app.orcinus.shadow.render.scene.math.Affine3
import app.orcinus.shadow.render.scene.math.Vec3

/**
 * The changes OrcaSlicer's gizmo windows make to an instance transformation in
 * world coordinates (GizmoObjectManipulation with Selection::rotate), so the
 * windows and the gizmos on the canvas manipulate objects alike.
 */
object ObjectTransforms {
    /** Selection::rotate() of a single instance: [degrees] about the world [axis] through [pivot]. */
    fun rotated(placement: Transform3, axis: Int, degrees: Double, pivot: Vector3): Transform3 {
        val start = Affine3(placement.columns.toDoubleArray())
        val turned = RotateGizmo.rotated(start, axis, Math.toRadians(degrees), Vec3(pivot.x, pivot.y, pivot.z))
        return Transform3(turned.elements().toList())
    }

    /**
     * GizmoObjectManipulation::do_scale() in world coordinates: the object
     * scaled so its size is [factors] times [unscaledSize] per axis, about
     * [center], the centre of its bounding box of [size].
     */
    fun scaled(placement: Transform3, factors: Vector3, unscaledSize: Vector3, size: Vector3, center: Vector3): Transform3 {
        val relative = Vec3(
            unscaledSize.x * factors.x / size.x,
            unscaledSize.y * factors.y / size.y,
            unscaledSize.z * factors.z / size.z,
        )
        val start = Affine3(placement.columns.toDoubleArray())
        return Transform3(ScaleGizmo.scaled(start, relative, Vec3(center.x, center.y, center.z)).elements().toList())
    }

    /**
     * Selection::scale_and_translate() of a copy in "Object coordinates"
     * (TransformationType::Instance): the copy scaled by [factors] along its
     * own axes, about [center], the centre of its bounding box (the
     * selection's dragging centre).
     */
    fun scaledInObject(placement: Transform3, factors: Vector3, center: Vector3): Transform3 {
        val start = Affine3(placement.columns.toDoubleArray())
        val linear = start.withTranslation(Vec3.ZERO)
        val scale = Affine3.assemble(Vec3.ZERO, Vec3.ZERO, Vec3(factors.x, factors.y, factors.z))
        val pivot = Vec3(center.x, center.y, center.z)
        val scaled = Affine3().translated(pivot) * linear * scale * linear.inverse() * Affine3().translated(-pivot) * start
        return Transform3(scaled.elements().toList())
    }

    /**
     * Selection::scale_and_translate() of a volume selected alone, which is
     * independent: its transformation [matrix] in the object scaled by
     * [factors] about its origin, along its own axes, its copy's (standing at
     * [placement]) or the world's, as [coordinates] say.
     */
    fun volumeScaled(matrix: Transform3, placement: Transform3, factors: Vector3, coordinates: CoordinateSystem): Transform3 {
        val volume = Affine3(matrix.columns.toDoubleArray())
        val scale = Affine3.assemble(Vec3.ZERO, Vec3.ZERO, Vec3(factors.x, factors.y, factors.z))
        val origin = volume.translation()
        val scaled = when (coordinates) {
            CoordinateSystem.LOCAL -> volume * scale
            CoordinateSystem.INSTANCE -> Affine3().translated(origin) * scale * Affine3().translated(-origin) * volume
            CoordinateSystem.WORLD -> {
                val linear = Affine3(placement.columns.toDoubleArray()).withTranslation(Vec3.ZERO)
                Affine3().translated(origin) * linear.inverse() * scale * linear * Affine3().translated(-origin) * volume
            }
        }
        return Transform3(scaled.elements().toList())
    }

    /**
     * GizmoObjectManipulation::reset_rotation_value(true): the rotation and
     * scale of [source], the rotation tool's placement when it opened, at the
     * position of [placement].
     */
    fun withLinearPartOf(placement: Transform3, source: Transform3): Transform3 =
        Transform3(source.columns.toMutableList().also { columns -> (12..14).forEach { columns[it] = placement.columns[it] } })
}
