package app.orcinus.shadow.render.scene

import app.orcinus.shadow.core.model.ColorRgba
import app.orcinus.shadow.core.model.Transform3
import app.orcinus.shadow.render.scene.math.Affine3
import app.orcinus.shadow.render.scene.math.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The rows of the move, rotate and scale windows, as GLCanvas3D::m_sidebar_field begins. */
enum class SidebarField { POSITION, ROTATION, ABSOLUTE_ROTATION, SCALE, SIZE }

/**
 * GLCanvas3D::m_sidebar_field: the field of [axis] in the row [field] of the
 * move, rotate or scale window that is being edited
 * (GizmoObjectManipulation's handle_sidebar_focus_event()). [reference] is
 * the reference system of the window's coordinates: null for the world's,
 * the copy's placement for object coordinates, or the volume's for part
 * coordinates ([local]); [uniformScale] the scale window's "Uniform scale".
 */
data class SidebarHint(
    val field: SidebarField,
    val axis: Int,
    val reference: Transform3? = null,
    val local: Boolean = false,
    val uniformScale: Boolean = false,
)

/**
 * Selection::render_sidebar_position_hints(), render_sidebar_rotation_hints()
 * and render_sidebar_scale_hints() of [hint] about [center] turned by
 * [orient]: arrows along the field's axis, a pair of curved arrows about it,
 * or a pair of arrows either way along it, in the axis's colour (orange for
 * a uniform scale, every axis then), for gouraud_light over the cleared depth.
 */
internal fun sidebarHintMeshes(hint: SidebarHint, center: Vec3, orient: Affine3, uniformScale: Boolean): List<GizmoMesh> {
    val base = Affine3().withTranslation(center) * orient
    fun rotation(x: Double = 0.0, y: Double = 0.0, z: Double = 0.0) = Affine3.assemble(Vec3.ZERO, Vec3(x, y, z), Vec3(1.0, 1.0, 1.0))
    // The arrows point along their Y axis; these turn it to each axis.
    val toAxis = listOf(rotation(z = -0.5 * PI), Affine3(), rotation(x = 0.5 * PI))
    return when (hint.field) {
        SidebarField.POSITION -> listOf(GizmoMesh("sidebar_arrow", SidebarHintMeshes.arrow, base * toAxis[hint.axis], GizmoColors.AXES[hint.axis], EMISSION))
        SidebarField.ROTATION, SidebarField.ABSOLUTE_ROTATION -> {
            val model = base * listOf(rotation(y = 0.5 * PI), rotation(x = -0.5 * PI), Affine3())[hint.axis]
            val color = GizmoColors.AXES[hint.axis]
            listOf(
                GizmoMesh("sidebar_curved_arrow", SidebarHintMeshes.curvedArrow, model, color, EMISSION),
                GizmoMesh("sidebar_curved_arrow", SidebarHintMeshes.curvedArrow, model * rotation(z = PI), color, EMISSION),
            )
        }
        SidebarField.SCALE, SidebarField.SIZE -> (0 until 3).filter { it == hint.axis || uniformScale }.flatMap { axis ->
            val model = base * toAxis[axis]
            val color = if (uniformScale) UNIFORM_SCALE_COLOR else GizmoColors.AXES[axis]
            listOf(
                GizmoMesh("sidebar_arrow", SidebarHintMeshes.arrow, model * Affine3().withTranslation(Vec3(0.0, SCALE_ARROW_OFFSET, 0.0)), color, EMISSION),
                GizmoMesh(
                    "sidebar_arrow",
                    SidebarHintMeshes.arrow,
                    model * Affine3.assemble(Vec3(0.0, -SCALE_ARROW_OFFSET, 0.0), Vec3(0.0, 0.0, PI), Vec3(1.0, 1.0, 1.0)),
                    color,
                    EMISSION,
                ),
            )
        }
    }
}

/** render_sidebar_hints(): gouraud_light's emission_factor. */
private const val EMISSION = 0.05f

/** render_sidebar_scale_hint(): each arrow 5 mm off the centre. */
private const val SCALE_ARROW_OFFSET = 5.0

/** Selection.cpp: UNIFORM_SCALE_COLOR, ColorRGBA::ORANGE(). */
private val UNIFORM_SCALE_COLOR = ColorRgba(0.923f, 0.504f, 0.264f, 1f)

/** Selection's m_arrow and m_curved_arrow. */
internal object SidebarHintMeshes {
    /** straight_arrow(10, 5, 5, 10, 1). */
    val arrow: MeshData by lazy { straightArrow(tipWidth = 10f, tipHeight = 5f, stemWidth = 5f, stemHeight = 10f, thickness = 1f) }

    /** circular_arrow(16, 10, 5, 10, 5, 1). */
    val curvedArrow: MeshData by lazy {
        circularArrow(resolution = 16, radius = 10f, tipHeight = 5f, tipWidth = 10f, stemWidth = 5f, thickness = 1f)
    }

    /** GLModel.cpp: straight_arrow(), up the Y axis from the origin. */
    private fun straightArrow(tipWidth: Float, tipHeight: Float, stemWidth: Float, stemHeight: Float, thickness: Float): MeshData {
        val data = Geometry()
        val halfThickness = 0.5f * thickness
        val halfStemWidth = 0.5f * stemWidth
        val halfTipWidth = 0.5f * tipWidth
        val totalHeight = tipHeight + stemHeight

        // top face vertices
        for (z in listOf(halfThickness, -halfThickness)) {
            val normal = floatArrayOf(0f, 0f, if (z > 0f) 1f else -1f)
            data.vertex(halfStemWidth, 0f, z, normal)
            data.vertex(halfStemWidth, stemHeight, z, normal)
            data.vertex(halfTipWidth, stemHeight, z, normal)
            data.vertex(0f, totalHeight, z, normal)
            data.vertex(-halfTipWidth, stemHeight, z, normal)
            data.vertex(-halfStemWidth, stemHeight, z, normal)
            data.vertex(-halfStemWidth, 0f, z, normal)
        }
        // top face triangles
        data.triangles(0, 1, 6, 6, 1, 5, 4, 5, 3, 5, 1, 3, 1, 2, 3)
        // bottom face triangles
        data.triangles(7, 13, 8, 13, 12, 8, 12, 11, 10, 8, 12, 10, 9, 8, 10)

        // side faces vertices
        fun side(x1: Float, y1: Float, x2: Float, y2: Float, normal: FloatArray) {
            data.vertex(x1, y1, -halfThickness, normal)
            data.vertex(x2, y2, -halfThickness, normal)
            data.vertex(x1, y1, halfThickness, normal)
            data.vertex(x2, y2, halfThickness, normal)
        }
        side(halfStemWidth, 0f, halfStemWidth, stemHeight, floatArrayOf(1f, 0f, 0f))
        side(halfStemWidth, stemHeight, halfTipWidth, stemHeight, floatArrayOf(0f, -1f, 0f))
        side(halfTipWidth, stemHeight, 0f, totalHeight, normalized(tipHeight, halfTipWidth))
        side(0f, totalHeight, -halfTipWidth, stemHeight, normalized(-tipHeight, halfTipWidth))
        side(-halfTipWidth, stemHeight, -halfStemWidth, stemHeight, floatArrayOf(0f, -1f, 0f))
        side(-halfStemWidth, stemHeight, -halfStemWidth, 0f, floatArrayOf(-1f, 0f, 0f))
        side(-halfStemWidth, 0f, halfStemWidth, 0f, floatArrayOf(0f, -1f, 0f))

        // side face triangles
        for (i in 0 until 7) {
            val ii = i * 4
            data.triangles(14 + ii, 15 + ii, 17 + ii, 14 + ii, 17 + ii, 16 + ii)
        }
        return data.mesh()
    }

    /** GLModel.cpp: circular_arrow(), a quarter turn about Z at [radius] from the origin, its tip at the Y axis. */
    private fun circularArrow(resolution: Int, radius: Float, tipHeight: Float, tipWidth: Float, stemWidth: Float, thickness: Float): MeshData {
        val data = Geometry()
        val halfThickness = 0.5f * thickness
        val halfStemWidth = 0.5f * stemWidth
        val halfTipWidth = 0.5f * tipWidth
        val outerRadius = radius + halfStemWidth
        val innerRadius = radius - halfStemWidth
        val stepAngle = 0.5f * PI.toFloat() / resolution

        // tip
        // top and bottom face vertices
        for (z in listOf(halfThickness, -halfThickness)) {
            val normal = floatArrayOf(0f, 0f, if (z > 0f) 1f else -1f)
            data.vertex(0f, outerRadius, z, normal)
            data.vertex(0f, radius + halfTipWidth, z, normal)
            data.vertex(-tipHeight, radius, z, normal)
            data.vertex(0f, radius - halfTipWidth, z, normal)
            data.vertex(0f, innerRadius, z, normal)
        }
        // top face triangles
        data.triangles(0, 1, 2, 0, 2, 4, 4, 2, 3)
        // bottom face triangles
        data.triangles(5, 7, 6, 5, 9, 7, 9, 8, 7)

        // side faces vertices
        fun side(x1: Float, y1: Float, x2: Float, y2: Float, normal: FloatArray) {
            data.vertex(x1, y1, -halfThickness, normal)
            data.vertex(x2, y2, -halfThickness, normal)
            data.vertex(x1, y1, halfThickness, normal)
            data.vertex(x2, y2, halfThickness, normal)
        }
        side(0f, outerRadius, 0f, radius + halfTipWidth, floatArrayOf(1f, 0f, 0f))
        side(0f, radius + halfTipWidth, -tipHeight, radius, normalized(-halfTipWidth, tipHeight))
        side(-tipHeight, radius, 0f, radius - halfTipWidth, normalized(-halfTipWidth, -tipHeight))
        side(0f, radius - halfTipWidth, 0f, innerRadius, floatArrayOf(1f, 0f, 0f))

        // side face triangles
        for (i in 0 until 4) {
            val ii = i * 4
            data.triangles(10 + ii, 11 + ii, 13 + ii, 10 + ii, 13 + ii, 12 + ii)
        }

        // stem
        // top and bottom face vertices
        for (z in listOf(halfThickness, -halfThickness)) {
            val normal = floatArrayOf(0f, 0f, if (z > 0f) 1f else -1f)
            for (r in listOf(innerRadius, outerRadius)) {
                for (i in 0..resolution) {
                    val angle = i * stepAngle
                    data.vertex(r * sin(angle), r * cos(angle), z, normal)
                }
            }
        }
        // top face triangles
        for (i in 0 until resolution) {
            data.triangles(26 + i, 27 + i, 27 + resolution + i, 27 + i, 28 + resolution + i, 27 + resolution + i)
        }
        // bottom face triangles
        for (i in 0 until resolution) {
            data.triangles(28 + 2 * resolution + i, 29 + 3 * resolution + i, 29 + 2 * resolution + i)
            data.triangles(29 + 2 * resolution + i, 29 + 3 * resolution + i, 30 + 3 * resolution + i)
        }

        // side faces vertices and triangles
        for (z in listOf(-halfThickness, halfThickness)) {
            for (i in 0..resolution) {
                val angle = i * stepAngle
                val c = cos(angle)
                val s = sin(angle)
                data.vertex(innerRadius * s, innerRadius * c, z, floatArrayOf(-s, -c, 0f))
            }
        }
        var firstId = 26 + 4 * (resolution + 1)
        for (i in 0 until resolution) {
            val ii = firstId + i
            data.triangles(ii, ii + 1, ii + resolution + 2, ii, ii + resolution + 2, ii + resolution + 1)
        }

        data.vertex(innerRadius, 0f, -halfThickness, floatArrayOf(0f, -1f, 0f))
        data.vertex(outerRadius, 0f, -halfThickness, floatArrayOf(0f, -1f, 0f))
        data.vertex(innerRadius, 0f, halfThickness, floatArrayOf(0f, -1f, 0f))
        data.vertex(outerRadius, 0f, halfThickness, floatArrayOf(0f, -1f, 0f))
        firstId = 26 + 6 * (resolution + 1)
        data.triangles(firstId, firstId + 1, firstId + 3, firstId, firstId + 3, firstId + 2)

        for (z in listOf(-halfThickness, halfThickness)) {
            for (i in resolution downTo 0) {
                val angle = i * stepAngle
                val c = cos(angle)
                val s = sin(angle)
                data.vertex(outerRadius * s, outerRadius * c, z, floatArrayOf(s, c, 0f))
            }
        }
        firstId = 30 + 6 * (resolution + 1)
        for (i in 0 until resolution) {
            val ii = firstId + i
            data.triangles(ii, ii + 1, ii + resolution + 2, ii, ii + resolution + 2, ii + resolution + 1)
        }
        return data.mesh()
    }

    private fun normalized(x: Float, y: Float): FloatArray {
        val length = kotlin.math.hypot(x, y)
        return floatArrayOf(x / length, y / length, 0f)
    }

    /** GLModel::Geometry of the P3N3 layout. */
    private class Geometry {
        private val positions = ArrayList<Float>()
        private val normals = ArrayList<Float>()
        private val indices = ArrayList<Int>()

        fun vertex(x: Float, y: Float, z: Float, normal: FloatArray) {
            positions += listOf(x, y, z)
            normals += normal.toList()
        }

        fun triangles(vararg corners: Int) {
            indices += corners.toList()
        }

        fun mesh() = MeshFiles.fromVertexNormals(positions.toFloatArray(), normals.toFloatArray(), indices.toIntArray())
    }
}
