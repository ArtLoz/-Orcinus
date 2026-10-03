package app.orcinus.shadow.render.scene

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import app.orcinus.shadow.core.model.BuildVolumeFit
import app.orcinus.shadow.core.model.ColorRgba
import app.orcinus.shadow.core.model.ObjectPart
import app.orcinus.shadow.core.model.PaintedMesh
import app.orcinus.shadow.core.model.PlateDescription
import app.orcinus.shadow.core.model.PlateInstance
import app.orcinus.shadow.core.model.PlateObject
import app.orcinus.shadow.core.model.Point2
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.VolumeType
import app.orcinus.shadow.core.model.WipeTower
import app.orcinus.shadow.render.scene.gl.GlVertexArray
import app.orcinus.shadow.render.scene.math.Affine3
import app.orcinus.shadow.render.scene.math.Box3
import app.orcinus.shadow.render.scene.math.Line3
import app.orcinus.shadow.render.scene.math.Vec3
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.ConcurrentHashMap

// Heights OrcaSlicer draws the plate at, keeping its layers apart
// (src/slic3r/GUI/PartPlate.cpp and 3DBed.cpp).
/**
 * The scene index of the wipe tower, which is no object of the plate: the view
 * reports it as the selection when the tower is picked.
 */
const val WIPE_TOWER_INDEX = -1

/** Orca: the tower's preview is drawn half transparent (load_wipe_tower_preview). */
private const val WIPE_TOWER_ALPHA = 0.66f

/** The colour of a stripe whose filament the plate does not list. */
private val WIPE_TOWER_FALLBACK = ColorRgba(1f, 1f, 1f)

/** load_wipe_tower_preview(): a tower this shallow is not drawn, and a flat one is given a sliver of height. */
private const val WIPE_TOWER_MIN_DEPTH = 0.01
private const val WIPE_TOWER_MIN_HEIGHT = 0.1

/** The triangles of a box over the eight corners boxPositions() writes, wound outwards. */
private val BOX_TRIANGLES = intArrayOf(
    0, 2, 1, 0, 3, 2,
    4, 5, 6, 4, 6, 7,
    0, 1, 5, 0, 5, 4,
    1, 2, 6, 1, 6, 5,
    2, 3, 7, 2, 7, 6,
    3, 0, 4, 3, 4, 7,
)

internal const val GROUND_Z = -0.03f
internal const val GROUND_Z_GRIDLINE = -0.26f
internal const val LOGO_Z = GROUND_Z + 0.02f
internal const val BED_MODEL_Z = -0.41f + GROUND_Z

internal class TextureImage(val width: Int, val height: Int, val rgba: ByteBuffer)

/** The plate, loaded from the engine's description and files, ready for the GPU. */
internal class SceneBed(
    /** Printable area triangles at the logo height: x, y, z, u, v per vertex, as init_model_from_poly() maps the texture. */
    val plateTriangles: FloatBuffer,
    /** x, y, z per vertex. */
    val excludeTriangles: FloatBuffer,
    val thinGridLines: FloatBuffer,
    val boldGridLines: FloatBuffer,
    val model: MeshData?,
    val texture: TextureImage?,
    /** Printable area with the printable height: BuildVolume::bounding_volume(). */
    val buildVolume: Box3,
) {
    /** Bed3D::update_model_offset(): the model's origin at the plate centre, below the texture. */
    val modelOffset = Vec3(buildVolume.center().x, buildVolume.center().y, BED_MODEL_Z.toDouble())

    /** The plate at z = 0, which the plate view frames (GLCanvas3D::zoom_to_plate). */
    val plateBox = Box3(Vec3(buildVolume.min.x, buildVolume.min.y, 0.0), Vec3(buildVolume.max.x, buildVolume.max.y, 0.0))

    /** Bed3D::Axes: the stem length, a tenth of the build volume's largest size. */
    val axisLength = 0.1 * buildVolume.maxSize()

    /** Bed3D's extended bounding box: the plate, its model and the end of the axes (calc_extended_bounding_box()). */
    val extendedBox = (
        model?.bounds?.let { bounds ->
            plateBox.merge(Box3(bounds.min + modelOffset, bounds.max + modelOffset))
        } ?: plateBox
        ).merge(Vec3(axisLength, axisLength, GROUND_Z + axisLength).let { Box3(it, it) })
}

internal class SceneObject(
    /** Position of the object in the list the view was given. */
    val index: Int,
    /** The mesh file, which identifies the object's geometry. */
    val key: String,
    val mesh: MeshData,
    val world: Affine3,
    val color: ColorRgba,
    /** Centre of the engine's bounding sphere in object coordinates, so it follows the object. */
    private val sphereCenter: Vec3,
    val sphereRadius: Double,
    /** ModelInstance::auto_drop: a manipulation rests the object on the plate. */
    val autoDrop: Boolean = true,
    /** ModelInstance::printable: an object that is not printed is drawn in OrcaSlicer's unprintable colour. */
    val printable: Boolean = true,
    /** GLVolume::is_transparent(): drawn after the opaque volumes, blended (GLVolumeCollection::render). */
    val transparent: Boolean = false,
    /**
     * The triangles painted with a filament, which lie on the object's own
     * surface: they are drawn with a depth bias so the paint wins over it.
     */
    val overlay: Boolean = false,
    /** GLGizmoSimplify's "Show wireframe": the edges of its triangles are drawn over it. */
    val wireframe: Boolean = false,
    /** GLVolume::partly_inside: the copy lies across the current plate's boundary. */
    val partlyInside: Boolean = false,
    /**
     * Drawn as an open painting tool draws the object it paints
     * (GLGizmoPainterBase::render_triangles): in its own colours, not the
     * selection's, with the overhangs the support tool highlights.
     */
    val paintedByTool: Boolean = false,
    /** GLVolume::is_modifier: a part that is not a model part (a modifier, a negative volume, a support blocker or enforcer). */
    val modifier: Boolean = false,
    /**
     * In the assembly view, how far the volume moves in the world for every
     * unit the explosion ratio grows (GLVolume::world_matrix()'s offsets).
     */
    val explosion: Vec3 = Vec3.ZERO,
    /** GLVolume::visible off: the assembly view's "Hide" draws the volume in MODEL_HIDDEN_COL. */
    val hidden: Boolean = false,
) {
    val bounds = mesh.bounds.transformed(world)

    fun withWorld(world: Affine3) = SceneObject(
        index, key, mesh, world, color, sphereCenter, sphereRadius, autoDrop, printable, transparent, overlay, wireframe, partlyInside, paintedByTool, modifier,
        explosion, hidden,
    )

    fun withWireframe(wireframe: Boolean) = SceneObject(
        index, key, mesh, world, color, sphereCenter, sphereRadius, autoDrop, printable, transparent, overlay, wireframe, partlyInside, paintedByTool, modifier,
        explosion, hidden,
    )

    /** The object as the painting tool draws it, in [color]. */
    fun paintedByTool(color: ColorRgba) = SceneObject(
        index, key, mesh, world, color, sphereCenter, sphereRadius, autoDrop, printable, transparent, overlay, wireframe, partlyInside, true, modifier,
        explosion, hidden,
    )

    /**
     * The volume as the assembly view draws it: at [world] with an explosion
     * ratio of 1, spread by [explosion], and never across a plate's boundary.
     * "Hide" makes it [hidden], or [faint] where paint covers it, whose
     * colours GLVolume::render() keeps at the hidden alpha.
     */
    fun inAssembly(world: Affine3, explosion: Vec3, hidden: Boolean, faint: Boolean) = SceneObject(
        index, key, mesh, world, if (faint) color.copy(alpha = VolumeColors.HIDDEN.alpha) else color, sphereCenter, sphereRadius, autoDrop, printable,
        transparent || hidden || faint, overlay, wireframe, false, paintedByTool, modifier, explosion, hidden,
    )

    /** GLVolume::world_matrix() once the explosion ratio grew by [ratio]. */
    fun exploded(ratio: Double): SceneObject =
        if (ratio == 0.0 || explosion.isZero()) this else withWorld(world.withTranslation(world.translation() + explosion * ratio))

    /** The bounding sphere's centre in world coordinates. */
    fun sphereCenter(): Vec3 = world.transformPoint(sphereCenter)

    /** ModelObject::get_instance_min_z(): the lowest point of the mesh in world coordinates. */
    fun minZ(): Double {
        val row = world.linearRow(2)
        val offset = world.translation().z
        val vertices = mesh.vertices
        var lowest = Double.MAX_VALUE
        for (corner in 0 until mesh.cornerCount) {
            val index = corner * MeshFiles.FLOATS_PER_CORNER
            val z = row.x * vertices.get(index) + row.y * vertices.get(index + 1) + row.z * vertices.get(index + 2) + offset
            if (z < lowest) lowest = z
        }
        return lowest
    }

    /**
     * The nearest point where [ray] enters the mesh, in world coordinates,
     * as the scene raycaster finds the volume under the mouse.
     */
    fun raycast(ray: Line3): Vec3? {
        val toObject = world.inverse()
        val origin = toObject.transformPoint(ray.a)
        val direction = toObject.transformPoint(ray.b) - origin
        var nearest = Double.MAX_VALUE
        val vertices = mesh.vertices
        val stride = MeshFiles.FLOATS_PER_CORNER
        for (corner in 0 until mesh.cornerCount step 3) {
            val t = rayTriangle(
                origin,
                direction,
                vertexAt(vertices, corner * stride),
                vertexAt(vertices, (corner + 1) * stride),
                vertexAt(vertices, (corner + 2) * stride),
            ) ?: continue
            if (t < nearest) nearest = t
        }
        return if (nearest == Double.MAX_VALUE) null else world.transformPoint(origin + direction * nearest)
    }

    /**
     * The nearest point where [ray] enters the mesh and the normal of the
     * triangle there, in world coordinates (RaycastManager's hit, whose normal
     * is that of the triangle transformed into the world).
     */
    fun raycastHit(ray: Line3): Pair<Vec3, Vec3>? {
        val toObject = world.inverse()
        val origin = toObject.transformPoint(ray.a)
        val direction = toObject.transformPoint(ray.b) - origin
        var nearest = Double.MAX_VALUE
        var triangle: Triple<Vec3, Vec3, Vec3>? = null
        val vertices = mesh.vertices
        val stride = MeshFiles.FLOATS_PER_CORNER
        for (corner in 0 until mesh.cornerCount step 3) {
            val v0 = vertexAt(vertices, corner * stride)
            val v1 = vertexAt(vertices, (corner + 1) * stride)
            val v2 = vertexAt(vertices, (corner + 2) * stride)
            val t = rayTriangle(origin, direction, v0, v1, v2) ?: continue
            if (t < nearest) {
                nearest = t
                triangle = Triple(v0, v1, v2)
            }
        }
        val (v0, v1, v2) = triangle ?: return null
        // NOTE: Anisotropic scale of transformation cause change of normal
        val w0 = world.transformPoint(v0)
        val normal = (world.transformPoint(v1) - w0).cross(world.transformPoint(v2) - w0).normalized()
        return world.transformPoint(origin + direction * nearest) to normal
    }

    /** The mesh's points in world coordinates, which CameraUtils::create_hull2d() projects. */
    fun worldPoints(): List<Vec3> {
        val vertices = mesh.vertices
        val stride = MeshFiles.FLOATS_PER_CORNER
        return (0 until mesh.cornerCount).map { corner -> world.transformPoint(vertexAt(vertices, corner * stride)) }
    }

    private fun vertexAt(vertices: FloatBuffer, offset: Int) =
        Vec3(vertices.get(offset).toDouble(), vertices.get(offset + 1).toDouble(), vertices.get(offset + 2).toDouble())
}

/** Moller-Trumbore: the parameter along [direction] where the ray from [origin] enters the triangle, or null. */
internal fun rayTriangle(origin: Vec3, direction: Vec3, v0: Vec3, v1: Vec3, v2: Vec3): Double? {
    val e1 = v1 - v0
    val e2 = v2 - v0
    val p = direction.cross(e2)
    val determinant = e1.dot(p)
    if (determinant > -RAY_EPSILON && determinant < RAY_EPSILON) return null
    val inverse = 1.0 / determinant
    val t0 = origin - v0
    val u = t0.dot(p) * inverse
    if (u < 0.0 || u > 1.0) return null
    val q = t0.cross(e1)
    val v = direction.dot(q) * inverse
    if (v < 0.0 || u + v > 1.0) return null
    val t = e2.dot(q) * inverse
    return if (t > 0.0) t else null
}

private const val RAY_EPSILON = 1e-12

/** How OrcaSlicer colours a volume for rendering (3DScene.cpp). */
internal object VolumeColors {
    private const val FULLY_TRANSPARENT_MATERIAL_THRESHOLD = 0.1f
    private const val FULL_TRANSPARENT_MODIFIED_TO_FIX_ALPHA = 0.3f
    private const val FULL_BLACK_THRESHOLD = 0.2f

    /** GLVolume::MODEL_HIDDEN_COL, which the assembly view's "Hide" draws a volume in. */
    val HIDDEN = ColorRgba(0f, 0f, 0f, 0.3f)

    /** GLVolume::UNPRINTABLE_COLOR, which an object that is not printed is drawn in. */
    private val UNPRINTABLE = ColorRgba(0f, 0f, 0f, 0.5f)

    /** The colours of the parts of an object (GLVolume of 3DScene.cpp). */
    private val MODEL_MODIFIER = ColorRgba(1f, 1f, 0f, 0.6f)
    private val MODEL_NEGATIVE = ColorRgba(0.3f, 0.3f, 0.3f, 0.4f)
    private val SUPPORT_ENFORCER = ColorRgba(0.3f, 0.3f, 1f, 0.4f)
    private val SUPPORT_BLOCKER = ColorRgba(1f, 0.3f, 0.3f, 0.4f)

    /** color_from_model_volume(): a part prints in the filament's colour, the others in their own. */
    fun of(type: VolumeType, color: ColorRgba): ColorRgba = when (type) {
        VolumeType.PART -> color
        VolumeType.NEGATIVE -> MODEL_NEGATIVE
        VolumeType.MODIFIER -> MODEL_MODIFIER
        VolumeType.SUPPORT_BLOCKER -> SUPPORT_BLOCKER
        VolumeType.SUPPORT_ENFORCER -> SUPPORT_ENFORCER
    }

    /** GLVolume::set_render_color(): the filament colour, brightened when selected. */
    fun render(color: ColorRgba, selected: Boolean, printable: Boolean = true): ColorRgba {
        if (!printable) {
            return if (selected) brighten(UNPRINTABLE) else UNPRINTABLE
        }
        val base = adjustForRendering(color)
        return if (selected) brighten(base) else base
    }

    /** adjust_color_for_rendering(): fully transparent becomes faint white, black becomes dark grey. */
    fun adjustForRendering(color: ColorRgba): ColorRgba = when {
        color.alpha < FULLY_TRANSPARENT_MATERIAL_THRESHOLD -> ColorRgba(1f, 1f, 1f, FULL_TRANSPARENT_MODIFIED_TO_FIX_ALPHA)
        color.red < FULL_BLACK_THRESHOLD && color.green < FULL_BLACK_THRESHOLD && color.blue < FULL_BLACK_THRESHOLD ->
            ColorRgba(FULL_BLACK_THRESHOLD, FULL_BLACK_THRESHOLD, FULL_BLACK_THRESHOLD, color.alpha)
        else -> color
    }

    /** GLVolume::brighten_color(): lightness raised by 0.25 in HSL. */
    fun brighten(color: ColorRgba): ColorRgba {
        val r = color.red
        val g = color.green
        val b = color.blue
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val lightness = (max + min) / 2f
        var hue = 0f
        var saturation = 0f
        if (max != min) {
            val delta = max - min
            saturation = if (lightness > 0.5f) delta / (2f - max - min) else delta / (max + min)
            hue = when (max) {
                r -> (g - b) / delta + (if (g < b) 6f else 0f)
                g -> (b - r) / delta + 2f
                else -> (r - g) / delta + 4f
            } / 6f
        }
        val l = minOf(lightness + 0.25f, 1f)
        if (saturation == 0f) return ColorRgba(l, l, l, color.alpha)
        val q = if (l < 0.5f) l * (1f + saturation) else l + saturation - l * saturation
        val p = 2f * l - q
        return ColorRgba(hueToRgb(p, q, hue + 1f / 3f), hueToRgb(p, q, hue), hueToRgb(p, q, hue - 1f / 3f), color.alpha)
    }

    private fun hueToRgb(p: Float, q: Float, hue: Float): Float {
        var t = hue
        if (t < 0f) t += 1f
        if (t > 1f) t -= 1f
        return when {
            t < 1f / 6f -> p + (q - p) * 6f * t
            t < 1f / 2f -> q
            t < 2f / 3f -> p + (q - p) * (2f / 3f - t) * 6f
            else -> p
        }
    }
}

/** Meshes already read, by file: moving an object changes its placement, not its mesh. */
internal class MeshCache {
    private val meshes = ConcurrentHashMap<String, MeshData>()

    /** The realistic view's smooth normals; another value reads every mesh again. */
    @Volatile
    var smoothNormals = false
        set(value) {
            if (field != value) meshes.clear()
            field = value
        }

    operator fun get(path: String): MeshData = meshes.getOrPut(path) { MeshFiles.read(File(path), smoothNormals) }

    /** Forgets the meshes of objects no longer in the scene. */
    fun retain(paths: Set<String>) {
        meshes.keys.retainAll(paths)
    }
}

/** Reads what the engine wrote for the 3D view. Runs off the main thread. */
internal object SceneLoader {
    fun loadBed(plate: PlateDescription, smoothNormals: Boolean = false): SceneBed {
        val geometry = plate.geometry
        return SceneBed(
            plateTriangles = texturedTriangles(geometry.plateTriangles),
            excludeTriangles = flatPoints(geometry.excludeTriangles, GROUND_Z),
            thinGridLines = flatPoints(geometry.thinGridLines, GROUND_Z_GRIDLINE),
            boldGridLines = flatPoints(geometry.boldGridLines, GROUND_Z_GRIDLINE),
            model = geometry.bedModel?.let { MeshFiles.read(File(it.value), smoothNormals) },
            texture = geometry.bedTexture?.let { decodeTexture(File(it.value)) },
            buildVolume = Box3.of(geometry.printableArea.map { Vec3(it.x, it.y, 0.0) })
                .let { Box3(it.min, Vec3(it.max.x, it.max.y, geometry.printableHeight)) },
        )
    }

    /**
     * One copy of an object as the scene draws it: the object's mesh with the
     * copy's transformation, which every copy of an object shares
     * (ModelObject::instances).
     */
    /** One part of an object as the scene draws it: its mesh, inside the copy. */
    fun loadPart(index: Int, part: ObjectPart, instance: PlateInstance, color: ColorRgba, meshes: MeshCache): SceneObject {
        val world = Affine3(instance.inspection.placement.columns.toDoubleArray()) * Affine3(part.placement.columns.toDoubleArray())
        val mesh = meshes[part.mesh.value]
        return SceneObject(
            index = index,
            key = part.mesh.value,
            mesh = mesh,
            world = world,
            color = VolumeColors.of(part.type, color),
            sphereCenter = mesh.bounds.center(),
            sphereRadius = 0.5 * mesh.bounds.maxSize(),
            autoDrop = instance.autoDrop,
            printable = instance.printable,
            partlyInside = instance.inspection.fit == BuildVolumeFit.PARTLY_OUTSIDE,
            modifier = part.type != VolumeType.PART,
        )
    }

    fun loadObject(index: Int, plateObject: PlateObject, instance: PlateInstance, color: ColorRgba, meshes: MeshCache): SceneObject {
        val inspection = instance.inspection
        val world = Affine3(inspection.placement.columns.toDoubleArray())
        val sphere = inspection.boundingSphere
        return SceneObject(
            index = index,
            key = inspection.mesh.value,
            mesh = meshes[inspection.mesh.value],
            world = world,
            color = color,
            sphereCenter = world.inverse().transformPoint(Vec3(sphere.center.x, sphere.center.y, sphere.center.z)),
            sphereRadius = sphere.radius,
            autoDrop = instance.autoDrop,
            printable = instance.printable,
            partlyInside = inspection.fit == BuildVolumeFit.PARTLY_OUTSIDE,
        )
    }

    /**
     * The triangles of an object painted with one filament
     * (GLGizmoMmuSegmentation): the engine writes them as a mesh of their own,
     * which the view draws over the object in that filament's colour.
     */
    fun loadPaintedMesh(
        index: Int,
        painted: PaintedMesh,
        instance: PlateInstance,
        color: ColorRgba,
        meshes: MeshCache,
    ): SceneObject {
        val world = Affine3(instance.inspection.placement.columns.toDoubleArray())
        val mesh = meshes[painted.mesh.value]
        return SceneObject(
            index = index,
            key = painted.mesh.value,
            mesh = mesh,
            world = world,
            color = color,
            sphereCenter = mesh.bounds.center(),
            sphereRadius = 0.5 * mesh.bounds.maxSize(),
            autoDrop = instance.autoDrop,
            printable = instance.printable,
            overlay = true,
            partlyInside = instance.inspection.fit == BuildVolumeFit.PARTLY_OUTSIDE,
        )
    }

    /**
     * GLVolumeCollection::load_wipe_tower_preview(): the tower is a box as wide
     * and deep as the engine reports, standing on the plate at its front left
     * corner, turned by wipe_tower_rotation_angle and drawn half transparent.
     * The desktop app stripes it with the colour of every filament printed on
     * the plate; one volume takes the colour of the first, since the scene
     * moves a volume as a whole.
     */
    fun loadWipeTower(
        tower: WipeTower,
        colors: List<ColorRgba>,
        built: ScenePath? = null,
        origin: Point2 = Point2(0.0, 0.0),
        smoothNormals: Boolean = false,
    ): SceneObject? {
        if (built == null && tower.depth < WIPE_TOWER_MIN_DEPTH) return null
        val height = if (tower.height == 0.0) WIPE_TOWER_MIN_HEIGHT else tower.height
        val world = Affine3.assemble(
            Vec3(tower.x + origin.x, tower.y + origin.y, 0.0),
            Vec3(0.0, 0.0, Math.toRadians(tower.rotation)),
            Vec3(1.0, 1.0, 1.0),
        )
        // load_real_wipe_tower_preview(): once the plate is sliced, the tower
        // the slice built, with its ribs and brim, stands where the box stood.
        val mesh = built?.let { runCatching { MeshFiles.read(File(it.value), smoothNormals) }.getOrNull() }
            ?: MeshFiles.fromIndexed(boxPositions(tower.width, tower.depth, height), BOX_TRIANGLES, smoothNormals)
        val filament = tower.filaments.firstOrNull() ?: 1
        val color = colors.getOrNull(filament - 1) ?: colors.firstOrNull() ?: WIPE_TOWER_FALLBACK
        return SceneObject(
            index = WIPE_TOWER_INDEX,
            key = built?.value ?: "wipe-tower:${tower.width}:${tower.depth}:$height",
            mesh = mesh,
            world = world,
            color = color.copy(alpha = WIPE_TOWER_ALPHA),
            sphereCenter = mesh.bounds.center(),
            sphereRadius = 0.5 * mesh.bounds.maxSize(),
            transparent = true,
        )
    }

    /** make_cube(): a box standing at the origin. */
    private fun boxPositions(width: Double, depth: Double, height: Double): FloatArray {
        val x = width.toFloat()
        val y0 = 0f
        val y1 = depth.toFloat()
        val z = height.toFloat()
        return floatArrayOf(
            0f, y0, 0f, x, y0, 0f, x, y1, 0f, 0f, y1, 0f,
            0f, y0, z, x, y0, z, x, y1, z, 0f, y1, z,
        )
    }

    private fun flatPoints(points: List<Point2>, z: Float): FloatBuffer =
        GlVertexArray.floatBuffer(FloatArray(points.size * 3) { index ->
            val point = points[index / 3]
            when (index % 3) {
                0 -> point.x.toFloat()
                1 -> point.y.toFloat()
                else -> z
            }
        })

    /**
     * init_model_from_poly() in 3DBed.cpp: texture coordinates span the
     * triangles' bounding box, with v running downwards so the texture's top
     * row lies at the back of the plate.
     */
    private fun texturedTriangles(points: List<Point2>): FloatBuffer {
        if (points.isEmpty()) return GlVertexArray.floatBuffer(FloatArray(0))
        val minX = points.minOf { it.x }
        val minY = points.minOf { it.y }
        val sizeX = points.maxOf { it.x } - minX
        val sizeY = points.maxOf { it.y } - minY
        if (sizeX <= 0.0 || sizeY <= 0.0) return GlVertexArray.floatBuffer(FloatArray(0))
        return GlVertexArray.floatBuffer(FloatArray(points.size * 5) { index ->
            val point = points[index / 5]
            when (index % 5) {
                0 -> point.x.toFloat()
                1 -> point.y.toFloat()
                2 -> LOGO_Z
                3 -> ((point.x - minX) / sizeX).toFloat()
                else -> (-(point.y - minY) / sizeY).toFloat()
            }
        })
    }

    /** Decodes without premultiplying: OrcaSlicer's printbed shader blends straight alpha. */
    private fun decodeTexture(file: File): TextureImage? {
        val options = BitmapFactory.Options().apply {
            inPremultiplied = false
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null
        return try {
            val rgba = ByteBuffer.allocateDirect(bitmap.byteCount).order(ByteOrder.nativeOrder())
            bitmap.copyPixelsToBuffer(rgba)
            rgba.flip()
            TextureImage(bitmap.width, bitmap.height, rgba)
        } finally {
            bitmap.recycle()
        }
    }
}
