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
    /** What the page keeps from the G-code before, and keeps for the next one. */
    private val memory: ToolpathsMemory,
) : PlateLayer {
    private var viewer = 0L
    private var onChanged: (() -> Unit)? = null

    // Requested, and what the current viewer has; the viewer's settings start as the G-code before left them.
    private var viewType = memory.viewType ?: ToolpathsViewType.FeatureType
    private var timeMode = memory.timeMode
    private var layerRange: Pair<Int, Int>? = null
    private var visibleMoves: Pair<Int, Int>? = null
    private val roleVisibility = HashMap(memory.roleVisibility)
    private val optionVisibility = HashMap(memory.optionVisibility)
    /** GCodeViewer::reset_visible() waits for the feature types the viewer has. */
    private var resetVisible = false
    /** What GCodeViewer::load() and update_layers_slider() do once for this G-code. */
    private var loaded = false
    private var dirty = true
    private var appliedViewType: ToolpathsViewType? = null
    private var appliedTimeMode: ToolpathsTimeMode? = null
    private var appliedLayerRange: Pair<Int, Int>? = null
    private var appliedVisibleMoves: Pair<Int, Int>? = null

    private val mutableView = MutableStateFlow<ToolpathsView?>(null)

    /** What the viewer shows, once it has drawn. */
    val view: StateFlow<ToolpathsView?> = mutableView.asStateFlow()

    override fun setOnChanged(listener: (() -> Unit)?) {
        synchronized(this) { onChanged = listener }
    }

    /**
     * GCodeViewer::set_view_type() from the legend's combo box, and
     * reset_visible(): the feature types show again for their own view.
     */
    fun setViewType(type: ToolpathsViewType) = request {
        viewType = type
        if (type == ToolpathsViewType.FeatureType) resetVisible = true
    }

    /** The legend's "Show stealth mode" and "Show normal mode" (Viewer::set_time_mode()). */
    fun setTimeMode(mode: ToolpathsTimeMode) = request { timeMode = mode }

    /**
     * GCodeViewer::load_as_gcode(): the stealth mode the G-code before was
     * shown in gives way to the normal one when this G-code tells no other
     * time ([otherTimeShown] false).
     */
    fun keepTimeMode(otherTimeShown: Boolean) = request { if (!otherTimeShown) timeMode = ToolpathsTimeMode.Normal }

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

    /**
     * GCodeViewer::export_toolpaths_to_obj(): the extrusions the viewer shows
     * now, in their colours, written to [path] with the materials beside it
     * (".mtl"); false before the viewer has drawn, or when it cannot be written.
     */
    @Synchronized
    fun exportToObj(path: String): Boolean {
        if (viewer == 0L) return false
        java.io.File(path).parentFile?.mkdirs()
        return NativeToolpaths.exportToObj(viewer, path)
    }

    /** m_show_marker of the G-code this layer shows. */
    private var markerShown = false
    private var speedProfile: ActualSpeedProfile? = null

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
        if (!loaded) {
            loaded = true
            val first = NativeToolpaths.snapshot(viewer)
            // GCodeViewer::load(): a print of several filaments shows in their
            // colours (ColorPrint) and a print of one by the feature type, once
            // the count of filaments changes; within the same count the user's
            // view type stays.
            synchronized(memory) {
                val count = if (first.usedExtruders.size > 1) 2 else 1
                if (memory.lastExtruderCountDefaultApplied != count) {
                    viewType = if (count == 2) ToolpathsViewType.ColorPrint else ToolpathsViewType.FeatureType
                    memory.lastExtruderCountDefaultApplied = count
                }
                // update_layers_slider(): the span stays at its heights while the top height does.
                if (layerRange == null) layerRange = spanAfterReload(memory.span, first.layersZs.toList())
            }
        }
        if (appliedViewType != viewType) {
            NativeToolpaths.setViewType(viewer, viewType.ordinal)
            appliedViewType = viewType
        }
        if (appliedTimeMode != timeMode) {
            NativeToolpaths.setTimeMode(viewer, timeMode.ordinal)
            appliedTimeMode = timeMode
        }
        var snapshot = NativeToolpaths.snapshot(viewer)
        if (resetVisible) {
            resetVisible = false
            snapshot.roles.forEach { role -> ToolpathsRole.entries.getOrNull(role)?.let { roleVisibility[it] = true } }
        }
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
        remember(snapshot)
        // GCodeViewer::render(): m_show_marker stays on from the first time the
        // visible moves end before the last, until the G-code is loaded again.
        markerShown = markerShown || !snapshot.atEnd
        // The widget keeps the data it was last given (set_actual_speed_data()).
        snapshot.speedProfile()?.let { speedProfile = it }
        mutableView.value = snapshot.toView(markerShown).copy(speedProfile = speedProfile)
    }

    /** What the next G-code starts from: the viewer's settings and the slider's span now. */
    private fun remember(snapshot: NativeToolpathsSnapshot) = synchronized(memory) {
        memory.viewType = viewType
        memory.timeMode = timeMode
        memory.roleVisibility.clear()
        memory.roleVisibility.putAll(roleVisibility)
        memory.optionVisibility.clear()
        memory.optionVisibility.putAll(optionVisibility)
        val zs = snapshot.layersZs
        if (zs.isNotEmpty()) {
            val lower = snapshot.layersRange.getOrElse(0) { 0 }.coerceIn(0, zs.lastIndex)
            val upper = snapshot.layersRange.getOrElse(1) { zs.lastIndex }.coerceIn(0, zs.lastIndex)
            memory.span = LayerSpan(zs[lower], zs[upper], lower == 0, upper == zs.lastIndex, zs.last())
        }
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
        appliedTimeMode = null
        appliedLayerRange = null
        appliedVisibleMoves = null
        dirty = true
    }

    companion object {
        private const val BOUNDS_SIZE = 6

        /**
         * Reads the toolpaths the engine wrote to [path], which start from what
         * [memory] kept of the G-code before; null when the file cannot be read.
         */
        suspend fun load(path: ScenePath, memory: ToolpathsMemory = ToolpathsMemory()): ToolpathsLayer? {
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
                        memory = memory,
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
        stealthTime = time.getOrElse(1) { 0f },
        prepareTime = prepareTime.firstOrNull() ?: 0f,
        stealthPrepareTime = prepareTime.getOrElse(1) { 0f },
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
        filamentPerExtruder = extruders.indices.filter { (it + 1) * 8 <= extruderFilament.size }.associate { index ->
            fun column(at: Int) = if (extruderListed[index] and (1 shl at) != 0) usage(index * 8 + at * 2, extruderFilament) else null
            extruders[index] to ExtruderFilament(column(0), column(1), column(2), column(3))
        },
    )
}

private fun NativeToolpathsSnapshot.toView(markerShown: Boolean): ToolpathsView {
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
        currentLine = currentLine,
        marker = markerPosition.takeIf { markerShown && it.size == 3 }?.let { Vector3(it[0].toDouble(), it[1].toDouble(), it[2].toDouble()) },
        vertex = takeIf { markerShown }?.vertex(),
        timeMode = ToolpathsTimeMode.entries.getOrElse(timeMode) { ToolpathsTimeMode.Normal },
        layerTimes = layerTimes.toList(),
        canExportToolpaths = visibleExtrusion,
    )
}

private fun NativeToolpathsSnapshot.vertex(): ToolpathsVertex? {
    if (vertexValues.size < VERTEX_VALUES || vertexKinds.size < VERTEX_KINDS) return null
    val values = vertexValues
    return ToolpathsVertex(
        position = Vector3(values[0].toDouble(), values[1].toDouble(), values[2].toDouble()),
        type = ToolpathsMoveType.entries.getOrElse(vertexKinds[0]) { ToolpathsMoveType.Noop },
        role = ToolpathsRole.entries.getOrNull(vertexKinds[1]),
        extrusion = vertexKinds[2] != 0,
        width = values[3],
        height = values[4],
        feedrate = values[5],
        acceleration = values[6],
        jerk = values[7],
        volumetricRate = values[8],
        fanSpeed = values[9],
        temperature = values[10],
        pressureAdvance = values[11],
        layerDuration = values[12],
        estimatedTime = values[13],
        time = values[14],
        layer = vertexKinds[3],
        extruder = vertexKinds[4],
        color = vertexKinds[5],
    )
}

private fun NativeToolpathsSnapshot.speedProfile(): ActualSpeedProfile? {
    if (speedProfile.isEmpty() || speedRange.size < 2) return null
    return ActualSpeedProfile(
        points = List(speedProfile.size / 3) { ActualSpeedPoint(speedProfile[it * 3], speedProfile[it * 3 + 1], speedProfile[it * 3 + 2] != 0f) },
        lowest = speedRange[0],
        highest = speedRange[1],
        levels = speedLevels.indices.map { speedLevels[it] to speedLevelColors.getOrElse(it) { 0 } },
    )
}

private const val VERTEX_VALUES = 15
private const val VERTEX_KINDS = 6
