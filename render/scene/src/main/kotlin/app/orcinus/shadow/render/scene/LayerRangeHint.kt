package app.orcinus.shadow.render.scene

import app.orcinus.shadow.core.model.ColorRgba
import app.orcinus.shadow.core.model.LayerRangeEditor
import app.orcinus.shadow.render.scene.math.Box3

/**
 * The height range the object list edits (GLCanvas3D::m_sidebar_field of a
 * range): from [bottom] to [top] of the copies at [indexes], with the field
 * of the range that has the focus ([field]).
 */
data class LayerRangeHint(val bottom: Double, val top: Double, val field: LayerRangeEditor, val indexes: Set<Int>)

/**
 * Selection::render_sidebar_layers_hints(): a plane at either height, 10 mm
 * wider than [box] on every side; the plane of the focused field is solid,
 * the other see-through. The far one comes first, as the desktop app orders
 * them for the transparency.
 */
internal fun layerRangePlanes(hint: LayerRangeHint, box: Box3, lookingDownward: Boolean): List<GizmoFace> {
    val x1 = box.min.x.toFloat() - MARGIN
    val y1 = box.min.y.toFloat() - MARGIN
    val x2 = box.max.x.toFloat() + MARGIN
    val y2 = box.max.y.toFloat() + MARGIN
    fun plane(height: Double, solid: Boolean): GizmoFace {
        val z = height.toFloat()
        return GizmoFace(
            floatArrayOf(x1, y1, z, x2, y1, z, x2, y2, z, x2, y2, z, x1, y2, z, x1, y1, z),
            if (solid) SOLID_PLANE_COLOR else TRANSPARENT_PLANE_COLOR,
        )
    }
    val bottom = plane(hint.bottom, hint.field == LayerRangeEditor.MIN_Z)
    val top = plane(hint.top, hint.field == LayerRangeEditor.MAX_Z)
    return if (lookingDownward) listOf(bottom, top) else listOf(top, bottom)
}

private const val MARGIN = 10f

// SOLID_PLANE_COLOR and TRANSPARENT_PLANE_COLOR of Selection.cpp.
private val SOLID_PLANE_COLOR = ColorRgba(0f, 174f / 255f, 66f / 255f, 1f)
private val TRANSPARENT_PLANE_COLOR = ColorRgba(0.8f, 0.8f, 0.8f, 0.5f)
