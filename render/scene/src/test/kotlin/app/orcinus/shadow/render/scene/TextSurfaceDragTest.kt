package app.orcinus.shadow.render.scene

import app.orcinus.shadow.render.scene.math.Affine3
import app.orcinus.shadow.render.scene.math.Vec3
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TextSurfaceDragTest {
    @Test
    fun theShortestRotationTurnsOneVectorIntoTheOther() {
        assertClose(Vec3.UNIT_X, quaternionFromTwoVectors(Vec3.UNIT_Z, Vec3.UNIT_X).rotate(Vec3.UNIT_Z))
        // Opposite vectors turn half round about an axis across them.
        assertClose(-Vec3.UNIT_Z, quaternionFromTwoVectors(Vec3.UNIT_Z, -Vec3.UNIT_Z).rotate(Vec3.UNIT_Z))
    }

    @Test
    fun aTextDraggedOntoASideFacesOutOfItWithItsUpUpwards() {
        val volume = volumeTransformation(Affine3(), Vec3.UNIT_X, Vec3(10.0, 0.0, 5.0), null, Affine3(), null, UP_LIMIT)
        assertClose(Vec3(10.0, 0.0, 5.0), volume.translation())
        assertClose(Vec3.UNIT_X, Vec3(volume[0, 2], volume[1, 2], volume[2, 2]))
        // suggest_up() of a side: the up is the world's Z.
        assertClose(Vec3.UNIT_Z, Vec3(volume[0, 1], volume[1, 1], volume[2, 1]))
        assertNull(calcUp(volume, UP_LIMIT))
    }

    @Test
    fun theAngleTheDragKeepsIsTheAngleCalcUpMeasures() {
        val volume = volumeTransformation(Affine3(), Vec3.UNIT_X, Vec3(10.0, 0.0, 5.0), null, Affine3(), 0.5, UP_LIMIT)
        val angle = assertNotNull(calcUp(volume, UP_LIMIT))
        assertEquals(0.5, angle, 1e-9)
    }

    @Test
    fun theCopysTransformationIsTakenOut() {
        val instance = Affine3.assemble(Vec3(100.0, 50.0, 0.0), Vec3.ZERO, Vec3(1.0, 1.0, 1.0))
        val volume = volumeTransformation(instance, Vec3.UNIT_X, Vec3(110.0, 50.0, 5.0), null, instance.inverse(), null, UP_LIMIT)
        assertClose(Vec3(10.0, 0.0, 5.0), volume.translation())
    }

    private fun assertClose(expected: Vec3, actual: Vec3) {
        assertEquals(expected.x, actual.x, 1e-9)
        assertEquals(expected.y, actual.y, 1e-9)
        assertEquals(expected.z, actual.z, 1e-9)
    }
}
