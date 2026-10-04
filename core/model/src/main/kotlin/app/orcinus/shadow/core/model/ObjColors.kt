package app.orcinus.shadow.core.model

import kotlin.math.cbrt
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * ObjColorDialog open on the OBJ file [source]: its error page when the
 * [question] says so, and otherwise its panel; [clustering] while the engine
 * clusters the colours again. [preview] is the thumbnail of the object in the
 * chosen filaments (generate_thumbnail()), seen from [view]; null until drawn.
 */
data class ObjColorDialogState(
    val source: ModelPath,
    val question: ObjColorQuestion,
    val panel: ObjColorPanel?,
    val clustering: Boolean = false,
    val view: CameraView = CameraView.ISO,
    val preview: ThumbnailImage? = null,
)

/** get_all_camera_view_type(): the views of the thumbnail, as Camera::ViewAngleType lists them, with their names. */
val OBJ_PREVIEW_VIEWS: List<Pair<CameraView, String>> = listOf(
    CameraView.ISO to "isometric",
    CameraView.TOP_FRONT to "top_front",
    CameraView.LEFT to "left",
    CameraView.RIGHT to "right",
    CameraView.TOP to "top",
    CameraView.BOTTOM to "bottom",
    CameraView.FRONT to "front",
    CameraView.REAR to "rear",
)

/**
 * ObjColorPanel: the colours of the file clustered ([clusterColours],
 * "#RRGGBB"), each with a combo box of the filaments to print it with. Every
 * box offers "undefined" (0), the plate's filaments ([colours]), and the
 * colours "Append" added after them ([items] holds the filaments' and the
 * added ones). [selections] are the boxes' choices, [clusterMapFilaments]
 * what the dialog paints with (m_cluster_map_filaments), and [newAddColors]
 * the colour each box chose (m_new_add_colors).
 */
data class ObjColorPanel(
    val colours: List<String>,
    val clusterColours: List<String>,
    /** m_color_num_recommend: the spin box goes from 1 up to it. */
    val recommended: Int,
    /** The spin box's value. */
    val clusterNumber: Int,
    /** m_last_cluster_num: the number the colours were last clustered into from the spin box. */
    val lastClusterNumber: Int = -1,
    val items: List<String> = colours,
    val selections: List<Int> = List(clusterColours.size) { 0 },
    val clusterMapFilaments: List<Int> = List(clusterColours.size) { 0 },
    val newAddColors: List<String> = List(clusterColours.size) { UNDEFINED_COLOR },
    /** m_warning_text shows its note. */
    val note: Boolean = false,
) {
    /** is_ok(): every box has a filament. */
    val isOk: Boolean get() = selections.all { it >= 1 }

    /** The colour of the box's item [index], its tooltip. */
    fun itemColor(index: Int): String = if (index == 0) UNDEFINED_COLOR else items.getOrElse(index - 1) { UNDEFINED_COLOR }

    /** The combo box of the colour [cluster] chose [index]; the thumbnail follows. */
    fun select(cluster: Int, index: Int): ObjColorPanel {
        if (cluster !in clusterMapFilaments.indices) return this
        return copy(
            selections = selections.replaced(cluster, index),
            clusterMapFilaments = clusterMapFilaments.replaced(cluster, index),
            newAddColors = newAddColors.replaced(cluster, itemColor(index)),
        )
    }

    /** deal_reset_btn(): the boxes lose the added colours and choose nothing. */
    fun reset(): ObjColorPanel = copy(
        items = colours,
        selections = List(selections.size) { 0 },
        newAddColors = List(newAddColors.size) { UNDEFINED_COLOR },
        note = false,
    )

    /**
     * deal_add_btn(): every colour joins the boxes after the filaments, at most
     * up to g_max_color in all, and each box chooses its own; past the most,
     * the colours are matched instead. Gives the panel and whether they exceeded it.
     */
    fun append(): Pair<ObjColorPanel, Boolean> {
        if (colours.size > MAX_COLOR) return this to false
        val reset = reset()
        val added = mutableListOf<String>()
        var exceeded = false
        for (colour in clusterColours) {
            if (colours.size + added.size >= MAX_COLOR) {
                exceeded = true
                break
            }
            added += colour
        }
        val first = colours.size + 1
        val appended = reset.copy(
            items = colours + added,
            selections = List(selections.size) { first + it },
            clusterMapFilaments = List(clusterMapFilaments.size) { first + it },
            newAddColors = List(newAddColors.size) { added.getOrElse(it) { UNDEFINED_COLOR } },
        )
        if (exceeded) return appended.approximateMatch() to true
        return appended to false
    }

    /** deal_approximate_match_btn(): each box chooses the item nearest to its colour. */
    fun approximateMatch(): ObjColorPanel {
        val cleared = copy(note = false)
        if (selections.isEmpty() || items.isEmpty()) return cleared
        var panel = cleared
        clusterColours.forEachIndexed { cluster, colour ->
            // std::sort of the distances, the nearest first.
            val nearest = items.indices.minBy { colorDistance(colour, items[it]) } + 1
            panel = panel.select(cluster, nearest)
        }
        return panel
    }

    /** deal_default_strategy(): the colours are added, and matched unless they exceeded the most. */
    fun defaultStrategy(): ObjColorPanel {
        val (appended, exceeded) = append()
        val matched = if (exceeded) appended else appended.approximateMatch()
        return matched.copy(note = true)
    }

    /**
     * deal_algo(number, true) once the engine clustered the colours into
     * [clusters]: the boxes reset, one for each colour, and the default strategy.
     */
    fun reclustered(clusters: List<String>, number: Int): ObjColorPanel = copy(
        clusterColours = clusters,
        clusterNumber = number,
        lastClusterNumber = number,
        selections = List(clusters.size) { 0 },
        clusterMapFilaments = List(clusters.size) { clusterMapFilaments.getOrElse(it) { 0 } },
        newAddColors = List(clusters.size) { UNDEFINED_COLOR },
    ).reset().defaultStrategy()

    /**
     * update_new_add_final_colors(): the filaments to add (send_new_filament_to_ui()),
     * one for every number past the plate's that a box chose, in its colour;
     * null for a number no box chose.
     */
    fun newFilaments(): List<String?> {
        val max = clusterMapFilaments.maxOrNull() ?: 0
        if (max <= colours.size) return emptyList()
        return (colours.size until max).map { index ->
            clusterMapFilaments.indices.lastOrNull { clusterMapFilaments[it] == index + 1 && it < newAddColors.size }?.let(newAddColors::get)
        }
    }

    companion object {
        /** g_max_color: EnforcerBlockerType::ExtruderMax. */
        const val MAX_COLOR = 16

        /** g_undefined_color_in_obj */
        const val UNDEFINED_COLOR = "#00FF00"

        /** The panel as it opens on [question], for the plate's filament [colours]. */
        fun open(colours: List<String>, question: ObjColorQuestion): ObjColorPanel =
            ObjColorPanel(colours, question.clusterColors, question.recommended, question.recommended).defaultStrategy()
    }
}

private fun <T> List<T>.replaced(index: Int, value: T): List<T> = mapIndexed { at, item -> if (at == index) value else item }

/**
 * calc_color_distance() of two wxColours: DeltaE76 of their Lab values, from
 * RGB2Lab() of slic3r/Utils/ColorSpaceConvert.cpp, carried over unchanged,
 * which takes the colours' 0..255 components as they are.
 */
fun colorDistance(first: String, second: String): Float {
    val (l1, a1, b1) = rgbToLab(parseHtmlColor(first))
    val (l2, a2, b2) = rgbToLab(parseHtmlColor(second))
    return sqrt((l1 - l2).toDouble().pow(2) + (a1 - a2).toDouble().pow(2) + (b1 - b2).toDouble().pow(2)).toFloat()
}

private fun parseHtmlColor(color: String): Triple<Int, Int, Int> {
    val hex = color.removePrefix("#").padEnd(6, '0')
    return Triple(hex.substring(0, 2).toInt(16), hex.substring(2, 4).toInt(16), hex.substring(4, 6).toInt(16))
}

private fun pivotRgb(n: Double): Double = (if (n > 0.04045) ((n + 0.055) / 1.055).pow(2.4) else n / 12.92) * 100.0

private fun pivotXyz(n: Double): Double = if (n > 0.008856) cbrt(n) else 7.787 * n + 16.0 / 116.0

private fun rgbToLab(color: Triple<Int, Int, Int>): Triple<Float, Float, Float> {
    // RGB2XYZ()
    val r = pivotRgb(color.first.toDouble()).toFloat()
    val g = pivotRgb(color.second.toDouble()).toFloat()
    val b = pivotRgb(color.third.toDouble()).toFloat()
    val x = 0.412453f * r + 0.357580f * g + 0.180423f * b
    val y = 0.212671f * r + 0.715160f * g + 0.072169f * b
    val z = 0.019334f * r + 0.119193f * g + 0.950227f * b
    // XYZ2Lab()
    val px = pivotXyz(x / 95.047)
    val py = pivotXyz(y / 100.000)
    val pz = pivotXyz(z / 108.883)
    return Triple((116.0 * py - 16.0).toFloat(), (500.0 * (px - py)).toFloat(), (200.0 * (py - pz)).toFloat())
}
