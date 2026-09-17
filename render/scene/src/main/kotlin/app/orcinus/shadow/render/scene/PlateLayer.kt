package app.orcinus.shadow.render.scene

import app.orcinus.shadow.core.model.Vector3

/**
 * Something the plate view draws in its OpenGL ES context after the bed, as
 * OrcaSlicer's preview draws the G-code toolpaths (GLCanvas3D::_render_gcode()).
 *
 * The view owns a layer it is given: [onContextCreated], [draw] and [release]
 * come from its GL thread, or from the main thread once that thread has ended,
 * and a layer the view no longer shows is released. A released layer may be
 * drawn again, when the view comes back, and then prepares itself anew.
 */
interface PlateLayer {
    /** The space the layer draws in, so the camera's clipping planes keep it. */
    val bounds: LayerBounds

    /** A new GL context: what the layer made in an earlier one is gone. The view has made nothing in it yet. */
    fun onContextCreated()

    /** Draws with the camera's column-major view and projection matrices. */
    fun draw(view: FloatArray, projection: FloatArray)

    /** Frees what the layer holds. */
    fun release()

    /** [listener] asks the view to draw again after the layer changed; it may be called from any thread. */
    fun setOnChanged(listener: (() -> Unit)?) {}
}

/** An axis-aligned box in world coordinates, in millimetres. */
data class LayerBounds(val min: Vector3, val max: Vector3)
