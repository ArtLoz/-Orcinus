package app.orcinus.shadow.render.scene

import app.orcinus.shadow.core.model.ColorRgba
import app.orcinus.shadow.core.model.PlateInstanceId
import app.orcinus.shadow.core.model.Vector3
import app.orcinus.shadow.render.scene.math.Affine3
import app.orcinus.shadow.render.scene.math.Line3
import kotlin.math.abs
import kotlin.math.hypot

/**
 * The brim ears tool while it is open on the copy [copy] (GLGizmoBrimEars):
 * its ears and the ear the finger would place ([hover], on the model where the
 * finger is), which the view draws as render_points() does, and its "Section
 * view" ([section]; null for none), which clips the copy but not the ears; a
 * finger on the copy places ears, and on an ear selects, drags or removes it.
 */
data class BrimEarsView(
    val copy: PlateInstanceId,
    val ears: List<BrimEarView>,
    val hover: BrimEarView? = null,
    val section: PaintSectionView? = null,
)

/** An ear as render_points() draws it: its centre in the world, its radius, and its state. */
data class BrimEarView(val center: Vector3, val radius: Double, val state: BrimEarState)

/**
 * render_points()'s colours: an ear, a selected one, one that touches
 * nothing (is_error), the ear the mouse would place (is_hover), and the ear
 * the finger holds (m_hover_id).
 */
enum class BrimEarState { NORMAL, SELECTED, ERROR, HOVER, HELD }

/** What a finger does with the brim ears tool open, along a ray in world coordinates. */
sealed interface BrimEarsTouch {
    /** The finger is on the copy: gizmo_event(Moving) shows the ear it would place. */
    data class Explore(val origin: Vector3, val direction: Vector3) : BrimEarsTouch

    /** The finger let go on the copy: gizmo_event(LeftDown) places an ear under it. */
    data class Place(val origin: Vector3, val direction: Vector3) : BrimEarsTouch

    /** Another finger came, so this one places nothing. */
    data object Leave : BrimEarsTouch

    /** A tap on the ear at [index]: on_start_dragging() selects it alone. */
    data class Select(val index: Int) : BrimEarsTouch

    /** The ear at [index] dragged along the ray (on_dragging()). */
    data class Drag(val index: Int, val origin: Vector3, val direction: Vector3) : BrimEarsTouch

    /** The finger let the dragged ear go (on_stop_dragging()). */
    data class Dropped(val index: Int) : BrimEarsTouch

    /** A long press on the ear at [index], as the right click: it goes. */
    data class Delete(val index: Int) : BrimEarsTouch
}

/** GLGizmoBrimEars.cpp's colours, and the cyan of the ear under the mouse. */
internal object BrimEarColors {
    val DEF = ColorRgba(0.7f, 0.7f, 0.7f, 1f)
    val SELECTED = ColorRgba(0f, 0.5f, 0.5f, 1f)
    val ERR = ColorRgba(1f, 0.3f, 0.3f, 0.5f)
    val HOVER = ColorRgba(0.7f, 0.7f, 0.7f, 0.5f)
    val HELD = ColorRgba(0f, 1f, 1f, 1f)

    fun of(state: BrimEarState) = when (state) {
        BrimEarState.NORMAL -> DEF
        BrimEarState.SELECTED -> SELECTED
        BrimEarState.ERROR -> ERR
        BrimEarState.HOVER -> HOVER
        BrimEarState.HELD -> HELD
    }
}

/** GLGizmoBrimEars's m_cylinder: smooth_cylinder(16, 1, 1). */
private val brimEarCylinder: MeshData by lazy { smoothCylinderMesh(16, 1f, 1f) }

/**
 * render_points(): every ear a disc of its radius and 0.2 mm high, standing
 * up whatever the copy's rotation, in gouraud_light with emission 0.5 and
 * the scene's depth.
 */
internal fun brimEarsFrame(view: BrimEarsView): GizmoFrame {
    val meshes = (view.ears + listOfNotNull(view.hover)).map { ear ->
        val world = Affine3().translated(ear.center.vec()) *
            Affine3(doubleArrayOf(ear.radius, 0.0, 0.0, 0.0, 0.0, ear.radius, 0.0, 0.0, 0.0, 0.0, EAR_HEIGHT, 0.0, 0.0, 0.0, 0.0, 1.0))
        GizmoMesh(EAR_KEY, brimEarCylinder, world, BrimEarColors.of(ear.state), emission = 0.5f)
    }
    return GizmoFrame(lines = emptyList(), grabbers = emptyList(), sceneMeshes = meshes)
}

/**
 * The ear a ray picks (its grabber), the nearest along the ray: the ray
 * crosses its disc within [slop] millimetres of its edge.
 */
internal fun brimEarAt(view: BrimEarsView, ray: Line3, slop: Double): Int? {
    val direction = ray.unitVector()
    if (abs(direction.z) < 1e-9) return null
    return view.ears.withIndex().mapNotNull { (index, ear) ->
        val along = (ear.center.z + EAR_HEIGHT / 2 - ray.a.z) / direction.z
        if (along < 0.0) return@mapNotNull null
        val crossing = ray.a + direction * along
        if (hypot(crossing.x - ear.center.x, crossing.y - ear.center.y) <= ear.radius + slop) index to along else null
    }.minByOrNull { it.second }?.first
}

/** render_points()'s scale of the cylinder's height. */
private const val EAR_HEIGHT = 0.2
private const val EAR_KEY = "brim-ear"
