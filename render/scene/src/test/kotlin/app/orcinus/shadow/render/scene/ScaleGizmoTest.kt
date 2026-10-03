package app.orcinus.shadow.render.scene

import app.orcinus.shadow.core.model.CoordinateSystem
import app.orcinus.shadow.core.model.Transform3
import app.orcinus.shadow.core.model.Vector3
import app.orcinus.shadow.render.scene.math.Affine3
import app.orcinus.shadow.render.scene.math.Box3
import app.orcinus.shadow.render.scene.math.Line3
import app.orcinus.shadow.render.scene.math.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals

class ScaleGizmoTest {
    private val cube = Box3(Vec3(165.0, 165.0, 0.0), Vec3(185.0, 185.0, 20.0))
    private val gizmo = ScaleGizmo(cube, pixel = 0.5)

    @Test
    fun grabbersSitOnTheBottomSidesTheTopAndTheBottomCorners() {
        assertVec(Vec3(185.0, 175.0, 0.0), gizmo.grabberCenter(1))
        assertVec(Vec3(175.0, 165.0, 0.0), gizmo.grabberCenter(2))
        assertVec(Vec3(175.0, 175.0, 20.0), gizmo.grabberCenter(5))
        assertVec(Vec3(185.0, 185.0, 0.0), gizmo.grabberCenter(8))
        // The bottom centre is never drawn.
        assertEquals(9, gizmo.frame(dragged = null, pixelScale = 1f).grabbers.size)
    }

    @Test
    fun draggingTheXGrabberTwiceAsFarFromTheBottomCentreDoublesTheRatio() {
        val start = gizmo.grabberCenter(1)
        val bottom = gizmo.grabberCenter(4)
        // A ray from above hitting the plate plane 10 mm further along X.
        val ray = Line3(Vec3(195.0, 175.0, 100.0), Vec3(195.0, 175.0, -100.0))

        assertEquals(2.0, ScaleGizmo.ratio(1, start, bottom, ray), 1e-9)
    }

    @Test
    fun theWindowScalesAboutTheBoxCentreRelativeToTheUnscaledSize() {
        val placement = Transform3(Affine3().translated(Vec3(175.0, 175.0, 10.0)).elements().toList())

        val scaled = ObjectTransforms.scaled(
            placement = placement,
            factors = Vector3(1.5, 1.5, 1.5),
            unscaledSize = Vector3(20.0, 20.0, 20.0),
            size = Vector3(20.0, 20.0, 20.0),
            center = Vector3(175.0, 175.0, 10.0),
        )

        assertEquals(1.5, scaled.columns[0], 1e-9)
        assertEquals(175.0, scaled.columns[12], 1e-9)
        assertEquals(10.0, scaled.columns[14], 1e-9)
    }

    @Test
    fun aVolumeScaledAlongTheWorldsXInAQuarterTurnedCopyGrowsAlongTheObjectsYAboutItsOrigin() {
        // The copy turned a quarter about Z: the world's X is the object's -Y.
        val turned = Transform3(listOf(0.0, 1.0, 0.0, 0.0, -1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 100.0, 120.0, 10.0, 1.0))
        val volume = Transform3(Affine3().translated(Vec3(5.0, 0.0, 0.0)).elements().toList())

        val scaled = ObjectTransforms.volumeScaled(volume, turned, Vector3(2.0, 1.0, 1.0), CoordinateSystem.WORLD)

        assertEquals(listOf(1.0, 2.0, 1.0), listOf(scaled.columns[0], scaled.columns[5], scaled.columns[10]).map { Math.round(it * 1e9) / 1e9 })
        assertEquals(5.0, scaled.columns[12], 1e-9)
        assertEquals(0.0, scaled.columns[13], 1e-9)
        // Along its own axes, its own scale grows; shown back as Transformation::get_scaling_factor().
        val own = ObjectTransforms.volumeScaled(volume, turned, Vector3(2.0, 3.0, 4.0), CoordinateSystem.LOCAL)
        val factors = AssemblyTransforms.scalingFactor(Transform3((Affine3(turned.columns.toDoubleArray()).withTranslation(Vec3.ZERO) * Affine3(own.columns.toDoubleArray())).elements().toList()))
        assertEquals(2.0, factors.x, 1e-9)
        assertEquals(3.0, factors.y, 1e-9)
        assertEquals(4.0, factors.z, 1e-9)
    }

    @Test
    fun aMirroredVolumeKeepsItsScalingFactorsPositive() {
        val mirrored = Transform3(Affine3.assemble(Vec3.ZERO, Vec3(0.0, 0.0, 0.3), Vec3(-2.0, 3.0, 4.0)).elements().toList())

        val factors = AssemblyTransforms.scalingFactor(mirrored)

        assertEquals(2.0, factors.x, 1e-9)
        assertEquals(3.0, factors.y, 1e-9)
        assertEquals(4.0, factors.z, 1e-9)
    }

    private fun assertVec(expected: Vec3, actual: Vec3) {
        assertEquals(expected.x, actual.x, 1e-9)
        assertEquals(expected.y, actual.y, 1e-9)
        assertEquals(expected.z, actual.z, 1e-9)
    }
}
