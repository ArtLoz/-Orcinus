package app.orcinus.shadow.render.gcode

/**
 * What OrcaSlicer's preview keeps from one G-code to the next, which every
 * [ToolpathsLayer] of the page shares: the viewer's settings, which
 * libvgcode::Viewer::load() leaves as they were (ViewerImpl::reset() keeps
 * m_settings) — the view type, the feature types and options shown, the time
 * mode —, the extruder count GCodeViewer last applied a default view type for
 * (m_last_extruder_count_default_applied), and the layer slider's span, which
 * Preview::update_layers_slider() keeps at its heights.
 */
class ToolpathsMemory {
    internal var viewType: ToolpathsViewType? = null
    internal var lastExtruderCountDefaultApplied = 0
    internal val roleVisibility = HashMap<ToolpathsRole, Boolean>()
    internal val optionVisibility = HashMap<ToolpathsOption, Boolean>()
    internal var timeMode = ToolpathsTimeMode.Normal

    /** The slider's span the last G-code showed; null before any. */
    internal var span: LayerSpan? = null
}

/**
 * The layer slider's span: the heights of its ends, whether they stood at the
 * slider's ends (IMSlider::is_lower_at_min() and is_higher_at_max()), and
 * the height of its top layer (GetMaxValueD()).
 */
internal data class LayerSpan(val lowZ: Float, val highZ: Float, val lowerAtMin: Boolean, val higherAtMax: Boolean, val maxZ: Float)

/**
 * Preview::update_layers_slider() for new layers [zs] after [previous]: a
 * slider that was empty, or whose top height changed, shows every layer;
 * otherwise an end that stood at the slider's end stays there, and the other
 * goes to the layer of its height (find_close_layer_idx()), or to the end
 * when no layer has it.
 */
internal fun spanAfterReload(previous: LayerSpan?, zs: List<Float>): Pair<Int, Int> {
    val last = (zs.size - 1).coerceAtLeast(0)
    if (zs.isEmpty()) return 0 to 0
    val full = previous == null || kotlin.math.abs(zs.last() - previous.maxZ) > SPAN_EPSILON
    val snapToMin = full || previous!!.lowerAtMin
    val snapToMax = full || previous!!.higherAtMax
    val low = if (snapToMin) 0 else closeLayer(zs, previous!!.lowZ) ?: 0
    val high = if (snapToMax) last else closeLayer(zs, previous!!.highZ) ?: last
    // IMSlider::SetSelectionSpan(): the higher end never stands below the lower.
    return low to maxOf(high, low)
}

/** find_close_layer_idx(): the layer at [z] within the epsilon; null for none. */
private fun closeLayer(zs: List<Float>, z: Float): Int? {
    val index = zs.indices.minByOrNull { kotlin.math.abs(zs[it] - z) } ?: return null
    return index.takeIf { kotlin.math.abs(zs[it] - z) < SPAN_EPSILON }
}

/** epsilon() of Preview::update_layers_slider(), for heights the G-code writes as floats. */
private const val SPAN_EPSILON = 1e-6f
