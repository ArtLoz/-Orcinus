package app.orcinus.shadow.render.gcode

import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.Vector3
import app.orcinus.shadow.render.scene.LayerBounds
import app.orcinus.shadow.render.scene.PlateLayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * The toolpaths of a slice, drawn by OrcaSlicer's libvgcode over the plate as
 * the desktop Preview page draws G-code. Made by [load], which reads the file
 * off the main thread; the plate view it is given draws and releases it.
 *
 * What the viewer shows is changed through this layer, as the desktop legend
 * and sliders change GCodeViewer: the requests are kept here and applied to
 * the viewer on the GL thread before it draws, since libvgcode uploads
 * textures when they change, and [view] reports the result. Kept here, they
 * also survive a new GL context, which gets a new viewer.
 */
class ToolpathsLayer private constructor(
    private val path: ScenePath,
    private var data: Long,
    override val bounds: LayerBounds,
    /** The slice's figures for the legend. */
    val statistics: ToolpathsStatistics,
) : PlateLayer {
    private var viewer = 0L
    private var onChanged: (() -> Unit)? = null

    // Requested, and what the current viewer has.
    private var viewType = ToolpathsViewType.FeatureType
    /** The user picked a view type, which the smart default no longer overrides. */
    private var viewTypeChosen = false
    private var layerRange: Pair<Int, Int>? = null
    private var visibleMoves: Pair<Int, Int>? = null
    private val roleVisibility = HashMap<ToolpathsRole, Boolean>()
    private val optionVisibility = HashMap<ToolpathsOption, Boolean>()
    private var dirty = true
    private var appliedViewType: ToolpathsViewType? = null
    private var appliedLayerRange: Pair<Int, Int>? = null
    private var appliedVisibleMoves: Pair<Int, Int>? = null

    private val mutableView = MutableStateFlow<ToolpathsView?>(null)

    /** What the viewer shows, once it has drawn. */
    val view: StateFlow<ToolpathsView?> = mutableView.asStateFlow()

    override fun setOnChanged(listener: (() -> Unit)?) {
        synchronized(this) { onChanged = listener }
    }

    /** GCodeViewer::set_view_type() from the legend's combo box. */
    fun setViewType(type: ToolpathsViewType) = request {
        viewType = type
        viewTypeChosen = true
    }

    /** GCodeViewer::set_layers_z_range(): the visible layers; every move of the top one shows. */
    fun setLayerRange(lower: Int, upper: Int) = request {
        layerRange = minOf(lower, upper) to maxOf(lower, upper)
        visibleMoves = null
    }

    /** GCodeViewer::update_sequential_view_current() from the moves slider: the top layer's moves up to [index] show. */
    fun setLastVisibleMove(index: Int) = request {
        val moves = mutableView.value?.moves.orEmpty()
        if (moves.isNotEmpty()) visibleMoves = moves.first() to moves[index.coerceIn(0, moves.lastIndex)]
    }

    /** The legend's toggle of a feature type. */
    fun setRoleVisible(role: ToolpathsRole, visible: Boolean) = request { roleVisibility[role] = visible }

    /** The legend's toggle of a move option. */
    fun setOptionVisible(option: ToolpathsOption, visible: Boolean) = request { optionVisibility[option] = visible }

    private fun request(change: () -> Unit) {
        val listener = synchronized(this) {
            change()
            dirty = true
            onChanged
        }
        listener?.invoke()
    }

    @Synchronized
    override fun onContextCreated() {
        // The viewer's GL objects died with the old context; its data is read again.
        destroyViewer()
    }

    @Synchronized
    override fun draw(view: FloatArray, projection: FloatArray) {
        if (viewer == 0L) {
            if (data == 0L) data = NativeToolpaths.read(path.value, FloatArray(BOUNDS_SIZE))
            if (data == 0L) return
            viewer = NativeToolpaths.createViewer()
            if (viewer == 0L) return
            NativeToolpaths.load(viewer, data)
            data = 0L
            dirty = true
        }
        if (dirty) {
            applyRequests()
            dirty = false
        }
        NativeToolpaths.render(viewer, view, projection)
    }

    private fun applyRequests() {
        // GCodeViewer::load(): OrcaSlicer shows a print of several filaments
        // in their colours (ColorPrint) and a print of one by the feature type,
        // unless the user picked a view type.
        if (!viewTypeChosen) {
            val used = NativeToolpaths.snapshot(viewer).usedExtruders.size
            viewType = if (used > 1) ToolpathsViewType.ColorPrint else ToolpathsViewType.FeatureType
        }
        if (appliedViewType != viewType) {
            NativeToolpaths.setViewType(viewer, viewType.ordinal)
            appliedViewType = viewType
        }
        var snapshot = NativeToolpaths.snapshot(viewer)
        var toggled = false
        snapshot.roles.forEachIndexed { index, role ->
            val wanted = ToolpathsRole.entries.getOrNull(role)?.let(roleVisibility::get) ?: return@forEachIndexed
            if (wanted != snapshot.roleVisible[index]) {
                NativeToolpaths.toggleExtrusionRoleVisibility(viewer, role)
                toggled = true
            }
        }
        snapshot.options.forEachIndexed { index, option ->
            val wanted = ToolpathsOption.entries.getOrNull(option)?.let(optionVisibility::get) ?: return@forEachIndexed
            if (wanted != snapshot.optionVisible[index]) {
                NativeToolpaths.toggleOptionVisibility(viewer, option)
                toggled = true
            }
        }
        val layers = layerRange
        if (layers != null && layers != appliedLayerRange) {
            val last = (snapshot.layersZs.size - 1).coerceAtLeast(0)
            NativeToolpaths.setLayersViewRange(viewer, layers.first.coerceIn(0, last), layers.second.coerceIn(0, last))
            appliedLayerRange = layers
            appliedVisibleMoves = null
            toggled = true
        }
        val moves = visibleMoves
        if (moves != null && (moves != appliedVisibleMoves || toggled)) {
            NativeToolpaths.setViewVisibleRange(viewer, moves.first, moves.second)
            appliedVisibleMoves = moves
        }
        snapshot = NativeToolpaths.snapshot(viewer)
        mutableView.value = snapshot.toView()
    }

    @Synchronized
    override fun release() {
        destroyViewer()
        if (data != 0L) {
            NativeToolpaths.destroyData(data)
            data = 0L
        }
    }

    private fun destroyViewer() {
        if (viewer != 0L) {
            NativeToolpaths.destroyViewer(viewer)
            viewer = 0L
        }
        appliedViewType = null
        appliedLayerRange = null
        appliedVisibleMoves = null
        dirty = true
    }

    companion object {
        private const val BOUNDS_SIZE = 6

        /** Reads the toolpaths the engine wrote to [path]; null when the file cannot be read. */
        suspend fun load(path: ScenePath): ToolpathsLayer? {
            // The read finishes even when the caller is cancelled, so the data is freed here, not lost.
            val layer = withContext(Dispatchers.IO + NonCancellable) {
                val box = FloatArray(BOUNDS_SIZE)
                val data = NativeToolpaths.read(path.value, box)
                if (data == 0L) {
                    null
                } else {
                    ToolpathsLayer(
                        path = path,
                        data = data,
                        bounds = LayerBounds(
                            min = Vector3(box[0].toDouble(), box[1].toDouble(), box[2].toDouble()),
                            max = Vector3(box[3].toDouble(), box[4].toDouble(), box[5].toDouble()),
                        ),
                        statistics = NativeToolpaths.statistics(data).toStatistics(),
                    )
                }
            }
            try {
                currentCoroutineContext().ensureActive()
            } catch (cancellation: Exception) {
                layer?.release()
                throw cancellation
            }
            return layer
        }
    }
}

private fun NativeToolpathsStatistics.toStatistics(): ToolpathsStatistics {
    fun usage(offset: Int, values: DoubleArray) = FilamentUsage(values[offset], values[offset + 1])
    return ToolpathsStatistics(
        time = time.firstOrNull() ?: 0f,
        prepareTime = prepareTime.firstOrNull() ?: 0f,
        filamentPerRole = roles.withIndex()
            .mapNotNull { (index, role) -> ToolpathsRole.entries.getOrNull(role)?.let { it to usage(index * 2, roleFilament) } }
            .toMap(),
        modelFilament = usage(0, filament),
        supportFilament = usage(2, filament),
        flushedFilament = usage(4, filament),
        wipeTowerFilament = usage(6, filament),
        totalUsedFilament = totalUsedFilament,
        totalWeight = totalWeight,
        totalCost = totalCost,
        totalTravelDistance = totalTravelDistance,
        totalTravelMoves = totalTravelMoves,
        totalSeamDistance = totalSeamDistance,
        totalFilamentChanges = totalFilamentChanges,
        totalExtruderChanges = totalExtruderChanges,
        totalToolChangeTime = totalToolChangeTime,
        moves = ToolpathsMoveType.entries
            .filter { it.ordinal < moveCounts.size }
            .associateWith { MoveStatistics(moveCounts[it.ordinal], moveTimes[it.ordinal * 2], moveDistances[it.ordinal]) },
    )
}

private fun NativeToolpathsSnapshot.toView(): ToolpathsView {
    // GCodeViewer::render_legend()'s append_range(): highest value first.
    val range = when {
        rangeValues.isEmpty() || rangePalette.isEmpty() -> emptyList()
        rangeValues.size == 1 -> listOf(RangeLegend(rangeValues[0], rangePalette[0]))
        rangeValues.size == 2 -> listOf(RangeLegend(rangeValues[1], rangePalette.last()), RangeLegend(rangeValues[0], rangePalette[0]))
        else -> (minOf(rangeValues.size, rangePalette.size) - 1 downTo 0).map { RangeLegend(rangeValues[it], rangePalette[it]) }
    }
    val visibleLast = visibleRange.getOrElse(1) { 0 }
    val lastVisibleMove = moves.indexOfLast { it <= visibleLast }.coerceAtLeast(0)
    return ToolpathsView(
        viewType = ToolpathsViewType.entries.getOrElse(viewType) { ToolpathsViewType.FeatureType },
        layerZs = layersZs.toList(),
        lowerLayer = layersRange.getOrElse(0) { 0 },
        upperLayer = layersRange.getOrElse(1) { 0 },
        roles = roles.indices.mapNotNull { index ->
            ToolpathsRole.entries.getOrNull(roles[index])?.let { RoleLegend(it, roleColors[index], roleVisible[index], roleTimes[index]) }
        },
        options = options.indices.mapNotNull { index ->
            ToolpathsOption.entries.getOrNull(options[index])?.let { OptionLegend(it, optionColors[index], optionVisible[index]) }
        },
        travelsTime = travelsTime,
        estimatedTime = estimatedTime,
        range = range,
        moves = moves.toList(),
        lastVisibleMove = lastVisibleMove,
        toolColors = toolColors.toList(),
        usedExtruders = usedExtruders.toList(),
    )
}
