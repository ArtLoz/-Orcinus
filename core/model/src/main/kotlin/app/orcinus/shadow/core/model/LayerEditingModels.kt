package app.orcinus.shadow.core.model

/** LayerHeightEditActionType: what a press on the variable layer height bar does to the band of layers around it. */
enum class LayerHeightEdit {
    /** "Remove detail": thicker layers. */
    INCREASE,

    /** "Add detail": thinner layers. */
    DECREASE,

    /** "Reset to base": the layers go back toward the plain layer height. */
    REDUCE,

    /** "Smoothing" */
    SMOOTH,
}

/**
 * GLCanvas3D::LayersEditing of an object: its layer height [profile] (z and
 * layer height pairs), the [layers] it makes (generate_object_layers(): the
 * bottom and top of every layer from the bed up), the object's height
 * ([objectMaxZ]), the slicing parameters the bar and the colours of the
 * layers scale with, and whether the profile is the plain one Reset has
 * nothing to undo ([fixed], check_object_layers_fixed()).
 */
data class LayerEditing(
    val profile: List<Double>,
    val layers: List<Double>,
    val objectMaxZ: Double,
    val layerHeight: Double,
    val minLayerHeight: Double,
    val maxLayerHeight: Double,
    val objectPrintZHeight: Double,
    val fixed: Boolean,
)

sealed interface LayerEditingOutcome {
    data class Success(val editing: LayerEditing) : LayerEditingOutcome

    data class Failure(val message: String) : LayerEditingOutcome
}
