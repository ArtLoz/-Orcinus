package app.orcinus.shadow.render.scene

import app.orcinus.shadow.core.model.ColorRgba
import app.orcinus.shadow.render.scene.math.Affine3
import app.orcinus.shadow.render.scene.math.Vec3
import kotlin.math.PI
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals

class SelectionBoxTest {
    private val extents = MeshExtents()

    @Test
    fun aTurnedCopyIsBoxedAlongTheWorldOrAlongItsOwnAxes() {
        val turned = Affine3.assemble(Vec3(100.0, 50.0, 10.0), Vec3(0.0, 0.0, PI / 4.0), Vec3(1.0, 1.0, 1.0))
        val cube = volume(cubePositions(-10f, 10f), turned)

        // World coordinates: the box of its points, reaching its corners.
        val (world, worldTransform) = SelectionBoxes.inReference(listOf(cube), null, extents)
        assertEquals(100.0 - 10.0 * sqrt(2.0), world.min.x, 1e-9)
        assertEquals(100.0 + 10.0 * sqrt(2.0), world.max.x, 1e-9)
        assertEquals(0.0, worldTransform.translation().norm(), 1e-12)

        // Object coordinates: along its own turned axes, its own size.
        val (own, ownTransform) = SelectionBoxes.inReference(listOf(cube), turned, extents)
        assertEquals(20.0, own.size().x, 1e-9)
        assertEquals(20.0, own.size().y, 1e-9)
        assertEquals(Vec3(100.0, 50.0, 10.0).x, ownTransform.transformPoint(own.center()).x, 1e-9)
        assertEquals(Vec3(100.0, 50.0, 10.0).y, ownTransform.transformPoint(own.center()).y, 1e-9)
    }

    @Test
    fun aVolumeAloneInItsOwnCoordinatesIsBoxedAboutItsOrigin() {
        // A text aligned to the right: its points lie on one side of its origin.
        val placement = Affine3().translated(Vec3(100.0, 0.0, 0.0))
        val text = volume(cubePositions(0f, 20f), placement)

        val (box, _) = SelectionBoxes.inReference(listOf(text), placement, extents, centre = placement.translation())

        assertEquals(80.0, box.min.x, 1e-9)
        assertEquals(120.0, box.max.x, 1e-9)
        assertEquals(-20.0, box.min.z, 1e-9)
    }

    @Test
    fun aMovedVolumeIsBoxedWhereItStandsNow() {
        val cube = cubePositions(-10f, 10f)
        val first = extents.of(cube, Affine3().translated(Vec3(10.0, 0.0, 0.0)))
        val moved = extents.of(cube, Affine3().translated(Vec3(30.0, 5.0, 0.0)))

        assertEquals(0.0, first.min.x, 1e-9)
        assertEquals(20.0, moved.min.x, 1e-9)
        assertEquals(-5.0, moved.min.y, 1e-9)
    }

    private fun volume(mesh: MeshData, world: Affine3) = SceneObject(0, "volume.mesh", mesh, world, ColorRgba(1f, 1f, 1f), Vec3.ZERO, 1.0)

    /** A cube from [from] to [to] on every axis. */
    private fun cubePositions(from: Float, to: Float): MeshData = MeshFiles.fromIndexed(
        floatArrayOf(
            from, from, from, to, from, from, to, to, from, from, to, from,
            from, from, to, to, from, to, to, to, to, from, to, to,
        ),
        intArrayOf(
            0, 2, 1, 0, 3, 2, 4, 5, 6, 4, 6, 7,
            0, 1, 5, 0, 5, 4, 1, 2, 6, 1, 6, 5,
            2, 3, 7, 2, 7, 6, 3, 0, 4, 3, 4, 7,
        ),
    )
}
