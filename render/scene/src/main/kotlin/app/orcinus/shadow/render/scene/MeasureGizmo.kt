package app.orcinus.shadow.render.scene

import app.orcinus.shadow.core.model.ColorRgba
import app.orcinus.shadow.core.model.MeasureFeature
import app.orcinus.shadow.core.model.MeasureFeatureType
import app.orcinus.shadow.core.model.MeasureHover
import app.orcinus.shadow.core.model.MeasurePlaneMesh
import app.orcinus.shadow.core.model.MeasureSelection
import app.orcinus.shadow.core.model.Measurement
import app.orcinus.shadow.core.model.PlateInstanceId
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.Vector3
import app.orcinus.shadow.render.scene.math.Affine3
import app.orcinus.shadow.render.scene.math.Quaternion
import app.orcinus.shadow.render.scene.math.Vec3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The measuring tool while it is open (GLGizmoMeasure): the [copies] it
 * measures, which the view shows alone, as toggle_selected_volume_visibility()
 * leaves only the selected volumes drawn, and of them only the volumes of
 * these meshes when the selection is a part ([volumes], null for all);
 * whether a finger selects points (the desktop app's Shift); what is under
 * the finger; and the two selections with what they measure, which the view
 * highlights and dimensions. The distance's label reads [units] and offers
 * "Edit to scale" when [editToScale] is set.
 */
data class MeasureView(
    val copies: Set<PlateInstanceId>,
    val volumes: Set<ScenePath>? = null,
    val pointSelection: Boolean = false,
    val hover: MeasureHover? = null,
    val measurement: Measurement = Measurement(),
    /** "use_inches": the labels read inches. */
    val imperial: Boolean = false,
    val units: String = "mm",
    val editToScale: Boolean = false,
    /** What the "Edit to scale" button says to accessibility services. */
    val editToScaleDescription: String = "",
    /** m_editing_distance: the distance's label gives way to the box that edits it. */
    val editingDistance: Boolean = false,
)

/** What a finger does with the measuring tool open, along a ray in world coordinates. */
sealed interface MeasureTouch {
    /** The finger is on the measured volumes: on_render() shows what is under it. */
    data class Explore(val origin: Vector3, val direction: Vector3, val sphereRadius: Double) : MeasureTouch

    /** The finger let go there: on_mouse() for a left press selects what is under it. */
    data class Select(val origin: Vector3, val direction: Vector3, val sphereRadius: Double) : MeasureTouch

    /** Another finger came, so this one selects nothing: nothing is under the mouse any more. */
    data object Leave : MeasureTouch
}

/** GLGizmoMeasure.hpp's colours. */
internal object MeasureColors {
    val SELECTED_1ST = ColorRgba(0.25f, 0.75f, 0.75f, 1f)
    val SELECTED_2ND = ColorRgba(0.75f, 0.25f, 0.75f, 1f)
    val NEUTRAL = ColorRgba(0.5f, 0.5f, 0.5f, 1f)

    // ColorRGBA::GREEN()
    val HOVER = ColorRgba(0f, 1f, 0f, 1f)

    // ColorRGBA::WHITE(), LIGHT_GRAY(), RED(), GREEN() and BLUE(), which render_dimensioning() draws with.
    val WHITE = ColorRgba(1f, 1f, 1f, 1f)
    val LIGHT_GRAY = ColorRgba(0.75f, 0.75f, 0.75f, 1f)
    val RED = ColorRgba(1f, 0f, 0f, 1f)
    val GREEN = ColorRgba(0f, 1f, 0f, 1f)
    val BLUE = ColorRgba(0f, 0f, 1f, 1f)
}

/** GLModel.cpp's smooth_sphere(): a sphere of [radius] about the origin with a normal per vertex. */
internal fun smoothSphere(resolution: Int, radius: Float): MeshData {
    val sectorCount = max(4, resolution)
    val stackCount = sectorCount
    val sectorStep = (2.0 * PI / sectorCount).toFloat()
    val stackStep = (PI / stackCount).toFloat()
    val positions = ArrayList<Float>()
    val normals = ArrayList<Float>()
    fun vertex(x: Float, y: Float, z: Float) {
        positions += listOf(x, y, z)
        val length = sqrt(x * x + y * y + z * z)
        normals += listOf(x / length, y / length, z / length)
    }
    // vertices
    for (i in 0..stackCount) {
        // from pi/2 to -pi/2
        val stackAngle = 0.5 * PI - stackStep * i
        val xy = radius * cos(stackAngle)
        val z = radius * sin(stackAngle)
        if (i == 0 || i == stackCount) {
            vertex(xy.toFloat(), 0f, z.toFloat())
        } else {
            for (j in 0 until sectorCount) {
                // from 0 to 2pi
                val sectorAngle = (sectorStep * j).toDouble()
                vertex((xy * cos(sectorAngle)).toFloat(), (xy * sin(sectorAngle)).toFloat(), z.toFloat())
            }
        }
    }
    // triangles
    val indices = ArrayList<Int>()
    for (i in 0 until stackCount) {
        // Beginning of current stack.
        var k1 = if (i == 0) 0 else 1 + (i - 1) * sectorCount
        val k1First = k1
        // Beginning of next stack.
        var k2 = if (i == 0) 1 else k1 + sectorCount
        val k2First = k2
        for (j in 0 until sectorCount) {
            // 2 triangles per sector excluding first and last stacks
            var k1Next = k1
            var k2Next = k2
            if (i != 0) {
                k1Next = if (j + 1 == sectorCount) k1First else k1 + 1
                indices += listOf(k1, k2, k1Next)
            }
            if (i + 1 != stackCount) {
                k2Next = if (j + 1 == sectorCount) k2First else k2 + 1
                indices += listOf(k1Next, k2, k2Next)
            }
            k1 = k1Next
            k2 = k2Next
        }
    }
    return MeshFiles.fromVertexNormals(positions.toFloatArray(), normals.toFloatArray(), indices.toIntArray())
}

/** GLModel.cpp's smooth_cylinder(): a cylinder of [radius] up the Z axis to [height], with its normals. */
internal fun smoothCylinderMesh(resolution: Int, radius: Float, height: Float): MeshData {
    val sectorCount = max(4, resolution)
    val sectorStep = 2f * PI.toFloat() / sectorCount
    val base = List(sectorCount) { i -> floatArrayOf(radius * cos(sectorStep * i), radius * sin(sectorStep * i), 0f) }
    val positions = ArrayList<Float>()
    val normals = ArrayList<Float>()
    var count = 0
    fun vertex(x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float): Int {
        positions += listOf(x, y, z)
        normals += listOf(nx, ny, nz)
        return count++
    }
    val indices = ArrayList<Int>()
    // stem vertices
    for (v in base) {
        val length = sqrt(v[0] * v[0] + v[1] * v[1])
        vertex(v[0], v[1], 0f, v[0] / length, v[1] / length, 0f)
        vertex(v[0], v[1], height, v[0] / length, v[1] / length, 0f)
    }
    // stem triangles
    for (i in 0 until sectorCount) {
        val v1 = i * 2
        val v2 = if (i < sectorCount - 1) v1 + 2 else 0
        val v3 = v2 + 1
        val v4 = v1 + 1
        indices += listOf(v1, v2, v3, v1, v3, v4)
    }
    // bottom cap vertices
    var capCenter = vertex(0f, 0f, 0f, 0f, 0f, -1f)
    for (v in base) vertex(v[0], v[1], 0f, 0f, 0f, -1f)
    // bottom cap triangles
    for (i in 0 until sectorCount) {
        indices += listOf(capCenter, if (i < sectorCount - 1) capCenter + i + 2 else capCenter + 1, capCenter + i + 1)
    }
    // top cap vertices
    capCenter = vertex(0f, 0f, height, 0f, 0f, 1f)
    for (v in base) vertex(v[0], v[1], height, 0f, 0f, 1f)
    // top cap triangles
    for (i in 0 until sectorCount) {
        indices += listOf(capCenter, capCenter + i + 1, if (i < sectorCount - 1) capCenter + i + 2 else capCenter + 1)
    }
    return MeshFiles.fromVertexNormals(positions.toFloatArray(), normals.toFloatArray(), indices.toIntArray())
}

/**
 * GLModel.cpp's init_torus_data() with no world transformation: a ring of
 * [radius] about [center] around [modelAxis], its tube [thickness] thick.
 * Like OrcaSlicer's, the tube's sections start from the ring's direction in
 * the ring's own plane before it is turned to [modelAxis].
 */
internal fun torusMesh(primaryResolution: Int, secondaryResolution: Int, center: Vec3, radius: Double, thickness: Double, modelAxis: Vec3): MeshData {
    val torusSectorCount = max(4, primaryResolution)
    val sectionSectorCount = max(4, secondaryResolution)
    val torusSectorStep = 2.0 * PI / torusSectorCount
    val sectionSectorStep = 2.0 * PI / sectionSectorCount
    val toWorld = quaternionFromTwoVectors(Vec3.UNIT_Z, modelAxis)
    val positions = ArrayList<Float>()
    val normals = ArrayList<Float>()
    // vertices
    for (i in 0 until torusSectorCount) {
        val sectionAngle = torusSectorStep * i
        val radiusDir = Vec3(cos(sectionAngle), sin(sectionAngle), 0.0)
        val localSectionCenter = radiusDir * radius
        val worldSectionCenter = center + toWorld.rotate(localSectionCenter)
        val localSectionNormal = localSectionCenter.normalized().cross(Vec3.UNIT_Z).normalized()
        val worldSectionNormal = toWorld.rotate(localSectionNormal).normalized()
        val baseV = radiusDir * thickness
        for (j in 0 until sectionSectorCount) {
            val v = Quaternion.angleAxis(sectionSectorStep * j, worldSectionNormal).rotate(baseV)
            val position = worldSectionCenter + v
            val normal = v.normalized()
            positions += listOf(position.x.toFloat(), position.y.toFloat(), position.z.toFloat())
            normals += listOf(normal.x.toFloat(), normal.y.toFloat(), normal.z.toFloat())
        }
    }
    // triangles
    val indices = ArrayList<Int>()
    for (i in 0 until torusSectorCount) {
        val ii = i * sectionSectorCount
        val iiNext = ((i + 1) % torusSectorCount) * sectionSectorCount
        for (j in 0 until sectionSectorCount) {
            val jNext = (j + 1) % sectionSectorCount
            val i0 = ii + j
            val i1 = iiNext + j
            val i2 = iiNext + jNext
            val i3 = ii + jNext
            indices += listOf(i0, i1, i2, i0, i2, i3)
        }
    }
    return MeshFiles.fromVertexNormals(positions.toFloatArray(), normals.toFloatArray(), indices.toIntArray())
}

/**
 * The meshes GLGizmoMeasure draws its features with: m_sphere
 * (smooth_sphere(16, 7.5)) and m_cylinder (smooth_cylinder(16, 5, 1)), which
 * the frame scales by INV_ZOOM; a torus per circle; and a plane's triangles.
 * A hovered circle's torus follows the zoom, as on_render() builds it anew
 * every frame; a selected circle's keeps the tube it was made with, as
 * init_circle_glmodel() keeps it while the selection lasts.
 */
internal class MeasureMeshes {
    val sphere: MeshData by lazy { smoothSphere(16, 7.5f) }
    val cylinder: MeshData by lazy { smoothCylinderMesh(16, 5f, 1f) }
    private val tori = HashMap<String, Triple<MeasureFeature, Double, MeshData>>()
    private val planes = HashMap<String, Pair<MeasurePlaneMesh, MeshData>>()

    fun torus(slot: String, circle: MeasureFeature, thickness: Double): MeshData {
        tori[slot]?.let { (made, madeThickness, mesh) ->
            if (sameCircle(made, circle) && (slot != HOVER_SLOT || madeThickness == thickness)) return mesh
        }
        val mesh = torusMesh(64, 16, circle.pt1.vec(), circle.value, thickness, circle.pt2.vec())
        tori[slot] = Triple(circle, thickness, mesh)
        return mesh
    }

    /** init_plane_data(): the triangles the engine lifted off the mesh, with their normals. */
    fun plane(slot: String, plane: MeasurePlaneMesh): MeshData {
        planes[slot]?.let { (made, mesh) -> if (made === plane) return mesh }
        val vertices = plane.vertices
        val mesh = MeshFiles.fromIndexed(vertices, IntArray(vertices.size / 3) { it })
        planes[slot] = plane to mesh
        return mesh
    }

    /** init_circle_glmodel()'s test of the circle it made the torus for. */
    private fun sameCircle(a: MeasureFeature, b: MeasureFeature): Boolean {
        val eps = 1e-2
        return (a.pt1.vec() - b.pt1.vec()).norm() < eps && (a.pt2.vec() - b.pt2.vec()).norm() < eps && abs(a.value - b.value) < eps
    }
}

/**
 * GLGizmoMeasure::on_render(): the feature under the finger and the two
 * selections over the scene, in the colours that tell them apart. [pixel] is
 * GLGizmoBase::INV_ZOOM, which the spheres, cylinders and tori scale with.
 */
internal fun measureFrame(view: MeasureView, pixel: Double, meshes: MeasureMeshes): GizmoFrame? {
    val first = view.measurement.first
    val second = view.measurement.second
    val sphere = view.hover?.sphere ?: 0
    // Skip feature detection if hovering on a selected point/center
    val current = view.hover?.feature?.takeIf { sphere == 0 }
    if (current == null && first == null) return null
    val pointSelection = view.pointSelection
    val placed = ArrayList<GizmoMesh>()

    fun sphereAt(position: Vector3, color: ColorRgba, emission: Float) {
        placed += GizmoMesh(SPHERE_KEY, meshes.sphere, scaled(Affine3().translated(position.vec()), pixel, pixel, pixel), color, emission)
    }

    // render_feature()
    fun render(feature: MeasureFeature, colors: List<ColorRgba>, hover: Boolean, slot: String) {
        val emission = if (hover) 0.5f else 0.25f
        when (feature.type) {
            MeasureFeatureType.POINT -> sphereAt(feature.pt1, colors.first(), emission)
            MeasureFeatureType.CIRCLE -> {
                // render circle
                placed += GizmoMesh(TORUS_KEY + slot, meshes.torus(slot, feature, 5.0 * pixel), Affine3(), colors.first(), emission)
                // render center
                if (colors.size > 1) sphereAt(feature.pt1, colors.last(), emission)
            }
            MeasureFeatureType.EDGE -> {
                // render edge
                val from = feature.pt1.vec()
                val to = feature.pt2.vec()
                val edge = Affine3.fromPositionOrientation(from, quaternionFromTwoVectors(Vec3.UNIT_Z, to - from))
                placed += GizmoMesh(CYLINDER_KEY, meshes.cylinder, scaled(edge, pixel, pixel, (to - from).norm()), colors.first(), emission)
                // render extra point
                if (colors.size > 1) feature.pt3?.let { sphereAt(it, colors.last(), emission) }
            }
            MeasureFeatureType.PLANE -> feature.planeMesh?.let { mesh ->
                placed += GizmoMesh(PLANE_KEY + slot, meshes.plane(slot, mesh), Affine3(), colors.last(), emission)
            }
        }
    }

    fun hoverSelectionColor() =
        if ((pointSelection && first == null) || (!pointSelection && (first == null || current?.sameAs(first.feature) == true))) {
            MeasureColors.SELECTED_1ST
        } else {
            MeasureColors.SELECTED_2ND
        }

    fun hoveringColor() = if (pointSelection) MeasureColors.HOVER else hoverSelectionColor()

    // hovering over a selected feature: its centre, or the feature with a centre
    fun selectedColors(item: MeasureSelection) = when {
        item.isCenter -> listOf(MeasureColors.NEUTRAL, hoveringColor())
        item.feature.hasCenter -> listOf(hoveringColor(), MeasureColors.NEUTRAL)
        else -> listOf(hoveringColor())
    }

    if (current != null) {
        // render hovered feature
        val colors = when {
            first != null && current.sameAs(first.feature) -> selectedColors(first)
            second != null && current.sameAs(second.feature) -> selectedColors(second)
            else -> when (current.type) {
                MeasureFeatureType.POINT -> listOf(hoverSelectionColor())
                MeasureFeatureType.EDGE, MeasureFeatureType.CIRCLE -> when {
                    first?.isCenter == true && current.sameAs(first.source) -> listOf(MeasureColors.SELECTED_1ST, MeasureColors.NEUTRAL)
                    second?.isCenter == true && current.sameAs(second.source) -> listOf(MeasureColors.SELECTED_2ND, MeasureColors.NEUTRAL)
                    else -> listOf(hoveringColor(), hoveringColor())
                }
                MeasureFeatureType.PLANE -> listOf(hoveringColor())
            }
        }
        render(current, colors, hover = true, slot = HOVER_SLOT)
    }

    // render 1st and 2nd selected feature
    fun renderSelected(item: MeasureSelection, color: ColorRgba, hovered: Boolean, slot: String) {
        val (feature, colors) = when {
            // hovering over a center
            hovered && (item.isCenter || item.feature.hasCenter) -> item.source to listOf(MeasureColors.NEUTRAL, color)
            // hovering over a feature with center
            item.feature.hasCenter -> item.feature to listOf(color, MeasureColors.NEUTRAL)
            else -> item.feature to listOf(color)
        }
        render(feature, colors, hover = hovered, slot = slot)
    }
    if (first != null && (current == null || !current.sameAs(first.feature))) renderSelected(first, MeasureColors.SELECTED_1ST, sphere == 1, "1")
    if (second != null && (current == null || !current.sameAs(second.feature))) renderSelected(second, MeasureColors.SELECTED_2ND, sphere == 2, "2")

    // render point on feature while SHIFT is pressed
    val point = view.hover?.point
    if (pointSelection && current != null && current.type != MeasureFeatureType.POINT && point != null) sphereAt(point, hoverSelectionColor(), 0.5f)

    return GizmoFrame(lines = emptyList(), grabbers = emptyList(), meshes = placed)
}

/**
 * Where on the screen the selections' spheres stand (SEL_SPHERE_1_ID and
 * SEL_SPHERE_2_ID): a selected point or centre, or the centre of a selected
 * feature that has one.
 */
internal fun measureSpheres(measurement: Measurement): List<Vec3> = listOfNotNull(measurement.first, measurement.second).mapNotNull { item ->
    val feature = item.feature
    if (item.isCenter || feature.type == MeasureFeatureType.POINT || feature.hasCenter) feature.offset.vec() else null
}

/** A line of the dimensioning, in pixels from the view's top left; [width] in pixels. */
internal class DimensionSegment(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val color: ColorRgba, val width: Float)

/** An arrowhead of the dimensioning: a white triangle, its corners in pixels. */
internal class DimensionArrow(val points: FloatArray)

/**
 * A label of the dimensioning, its bottom left corner at ([x], [y]): a
 * distance in millimetres, which offers "Edit to scale" when [editable], or
 * an angle in degrees.
 */
internal class DimensionLabel(val x: Float, val y: Float, val value: Double, val angle: Boolean, val editable: Boolean)

/** GLGizmoMeasure::render_dimensioning() for one frame of the camera. */
internal class MeasureDimensions(
    val segments: List<DimensionSegment>,
    val arrows: List<DimensionArrow>,
    val labels: List<DimensionLabel>,
)

/**
 * GLGizmoMeasure::render_dimensioning(): the arcs of an angle, the lines of a
 * distance and of its run along each axis, and their labels, as [project]
 * puts world points on the screen. [density] turns OrcaSlicer's pixels into
 * the screen's; [pixel] is INV_ZOOM, which the arcs' arrowheads scale with.
 */
internal fun measureDimensions(view: MeasureView, project: (Vec3) -> Pair<Double, Double>?, density: Float, pixel: Double): MeasureDimensions? {
    val first = view.measurement.first ?: return null
    val second = view.measurement.second
    if (second == null && first.feature.type != MeasureFeatureType.CIRCLE) return null
    val result = view.measurement.result
    val segments = ArrayList<DimensionSegment>()
    val arrows = ArrayList<DimensionArrow>()
    val labels = ArrayList<DimensionLabel>()
    val triangleHeight = TRIANGLE_HEIGHT * density
    val triangleBase = TRIANGLE_BASE * density
    val stemWidth = 2f * density
    val lineWidth = density

    fun segment(from: Vec3, to: Vec3, color: ColorRgba, width: Float) {
        val a = project(from) ?: return
        val b = project(to) ?: return
        segments += DimensionSegment(a.first.toFloat(), a.second.toFloat(), b.first.toFloat(), b.second.toFloat(), color, width)
    }

    // A white triangle with its tip at (x, y), pointing along (dx, dy).
    fun arrow(x: Double, y: Double, dx: Double, dy: Double) {
        val baseX = x - dx * triangleHeight
        val baseY = y - dy * triangleHeight
        val nx = -dy * 0.5 * triangleBase
        val ny = dx * 0.5 * triangleBase
        arrows += DimensionArrow(floatArrayOf(x.toFloat(), y.toFloat(), (baseX + nx).toFloat(), (baseY + ny).toFloat(), (baseX - nx).toFloat(), (baseY - ny).toFloat()))
    }

    fun pointPoint(v1: Vec3, v2: Vec3, distance: Double, color: ColorRgba, showLabel: Boolean = true, showFirstTri: Boolean = true) {
        if ((v2 - v1).dot(v2 - v1) < 0.000001 || abs(distance) < 0.001) return
        // screen coordinates
        val a = project(v1) ?: return
        val b = project(v2) ?: return
        if (a == b) return
        val length = hypot(b.first - a.first, b.second - a.second)
        val ux = (b.first - a.first) / length
        val uy = (b.second - a.second) / length
        val overlap = length - 2.0 * triangleHeight < 0.0
        // stem
        if (overlap) {
            segments += DimensionSegment(
                (a.first - ux * 2.0 * triangleHeight).toFloat(), (a.second - uy * 2.0 * triangleHeight).toFloat(),
                (b.first + ux * 2.0 * triangleHeight).toFloat(), (b.second + uy * 2.0 * triangleHeight).toFloat(),
                color, stemWidth,
            )
        } else {
            segments += DimensionSegment(a.first.toFloat(), a.second.toFloat(), b.first.toFloat(), b.second.toFloat(), color, stemWidth)
        }
        // arrow 1
        if (showFirstTri) {
            if (overlap) arrow(a.first, a.second, ux, uy) else arrow(a.first, a.second, -ux, -uy)
        }
        // arrow 2
        if (overlap) arrow(b.first, b.second, -ux, -uy) else arrow(b.first, b.second, ux, uy)
        if (showLabel && !view.editingDistance) {
            labels += DimensionLabel(((a.first + b.first) / 2).toFloat(), ((a.second + b.second) / 2).toFloat(), distance, angle = false, editable = view.editToScale)
        }
    }

    fun pointEdge(edge: MeasureFeature) {
        val vProj = result.distanceInfinite?.to?.vec() ?: return
        val e1 = edge.pt1.vec()
        val e2 = edge.pt2.vec()
        val e1e2 = e2 - e1
        val vProje1 = vProj - e1
        val onE1Side = vProje1.dot(e1e2) < -EPSILON
        val onE2Side = !onE1Side && vProje1.norm() > e1e2.norm()
        if (onE1Side || onE2Side) {
            val from = project(if (onE1Side) e1 else e2) ?: return
            val to = project(vProj) ?: return
            if (from != to) segments += DimensionSegment(from.first.toFloat(), from.second.toFloat(), to.first.toFloat(), to.second.toFloat(), MeasureColors.LIGHT_GRAY, lineWidth)
        }
    }

    fun arcEdgeEdge(radius: Double = 0.0) {
        val measured = result.angle ?: return
        val angle = measured.angle
        val center = measured.center.vec()
        val e1 = measured.edge1.first.vec() to measured.edge1.second.vec()
        val e2 = measured.edge2.first.vec() to measured.edge2.second.vec()
        val calcRadius = measured.radius
        if (abs(angle) < EPSILON || abs(calcRadius) < EPSILON) return
        val drawRadius = if (radius > 0.0) radius else calcRadius
        val e1Unit = (e1.second - e1.first).normalized()
        val e2Unit = (e2.second - e2.first).normalized()
        val resolution = max(2, (64 * angle / PI).toInt())
        val step = angle / resolution
        val normal = e1Unit.cross(e2Unit).normalized()
        fun onArc(at: Double) = center + Quaternion.angleAxis(at, normal).rotate(e1Unit) * drawRadius
        // arc
        for (i in 0 until resolution) segment(onArc(step * i), onArc(step * (i + 1)), MeasureColors.WHITE, stemWidth)
        // arrows
        for (endpoint in 1..2) {
            val position = onArc(if (endpoint == 1) 0.0 else step * resolution)
            val tangent = normal.cross(position - center).normalized()
            val direction = if (endpoint == 1) -tangent else tangent
            val base = position - direction * (TRIANGLE_HEIGHT * pixel)
            val side = normal.cross(direction).normalized() * (0.5 * TRIANGLE_BASE * pixel)
            val corners = listOf(position, base + side, base - side).map { project(it) ?: return@map null }
            if (corners.all { it != null }) arrows += DimensionArrow(corners.flatMap { listOf(it!!.first.toFloat(), it.second.toFloat()) }.toFloatArray())
        }
        // edge 1 extension
        val e11e12 = e1.second - e1.first
        val e11center = center - e1.first
        val e11centerLength = e11center.norm()
        if (e11centerLength > EPSILON && e11center.dot(e11e12) < 0.0) {
            segment(center, center + (e1.second - e1.first).normalized() * e11centerLength, MeasureColors.LIGHT_GRAY, lineWidth)
        }
        // edge 2 extension
        val e21centerLength = (center - e2.first).norm()
        if (e21centerLength > EPSILON) {
            val length = if (measured.coplanar && radius > 0.0) e21centerLength else drawRadius
            segment(center, center + (e2.second - e2.first).normalized() * length, MeasureColors.LIGHT_GRAY, lineWidth)
        }
        // label
        project(onArc(step * 0.5 * resolution))?.let { (x, y) ->
            labels += DimensionLabel(x.toFloat(), y.toFloat(), Math.toDegrees(angle), angle = true, editable = false)
        }
    }

    // arc_edge_plane() and arc_plane_plane(): the arc between the edges the measurement found.
    fun arcAlongResult() {
        val calcRadius = result.angle?.radius ?: return
        if (calcRadius == 0.0) return
        arcEdgeEdge(calcRadius)
    }

    var f1 = first.feature
    var f2 = second?.feature ?: MeasureFeature(MeasureFeatureType.POINT, first.feature.pt1, Vector3(0.0, 0.0, 0.0))
    // Order features by type so following conditions are simple.
    if (f1.type > f2.type) {
        val swap = f1
        f1 = f2
        f2 = swap
    }
    // If there is an angle to show, draw the arc:
    when {
        f1.type == MeasureFeatureType.EDGE && f2.type == MeasureFeatureType.EDGE -> arcEdgeEdge()
        f1.type == MeasureFeatureType.EDGE && f2.type == MeasureFeatureType.PLANE -> arcAlongResult()
        f1.type == MeasureFeatureType.PLANE && f2.type == MeasureFeatureType.PLANE -> arcAlongResult()
    }

    val distance = result.distanceInfinite ?: result.distanceStrict
    if (distance != null) {
        // Where needed, draw the extension of the edge to where the dist is measured:
        if (f1.type == MeasureFeatureType.POINT && f2.type == MeasureFeatureType.EDGE) pointEdge(f2)
        // Render the arrow between the points that the backend passed:
        val from = distance.from.vec()
        val to = distance.to.vec()
        if (second != null) {
            val xTo = Vec3(to.x, from.y, from.z)
            pointPoint(from, xTo, xTo.x - from.x, MeasureColors.RED, showLabel = false, showFirstTri = false)
            val yTo = Vec3(xTo.x, to.y, xTo.z)
            pointPoint(xTo, yTo, yTo.y - xTo.y, MeasureColors.GREEN, showLabel = false, showFirstTri = false)
            pointPoint(yTo, to, to.z - yTo.z, MeasureColors.BLUE, showLabel = false, showFirstTri = false)
        }
        pointPoint(from, to, distance.distance, MeasureColors.WHITE)
    }
    return MeasureDimensions(segments, arrows, labels)
}

internal fun Vector3.vec() = Vec3(x, y, z)

/** [transform] with a scale along its own axes after it. */
private fun scaled(transform: Affine3, x: Double, y: Double, z: Double) =
    transform * Affine3(doubleArrayOf(x, 0.0, 0.0, 0.0, 0.0, y, 0.0, 0.0, 0.0, 0.0, z, 0.0, 0.0, 0.0, 0.0, 1.0))

/** GLGizmoMeasure.hpp's TRIANGLE_BASE and TRIANGLE_HEIGHT, the arrowheads' size in pixels. */
private const val TRIANGLE_BASE = 10.0
private const val TRIANGLE_HEIGHT = TRIANGLE_BASE * 1.618033

/** libslic3r's EPSILON */
private const val EPSILON = 1e-4

private const val HOVER_SLOT = "hover"
private const val SPHERE_KEY = "measure-sphere"
private const val CYLINDER_KEY = "measure-cylinder"
private const val TORUS_KEY = "measure-torus-"
private const val PLANE_KEY = "measure-plane-"
