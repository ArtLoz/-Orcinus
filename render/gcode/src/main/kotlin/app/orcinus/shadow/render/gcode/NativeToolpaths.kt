package app.orcinus.shadow.render.gcode

/** OrcaSlicer's libvgcode, as toolpaths_bridge.cpp exposes it. Handles are native pointers; 0 is none. */
internal object NativeToolpaths {
    init {
        System.loadLibrary("orcinus_toolpaths")
    }

    /** Reads a toolpaths file; [bounds] receives min x, y, z then max x, y, z. Returns 0 when it cannot be read. */
    external fun read(path: String, bounds: FloatArray): Long

    /** The legend's figures of data that was read and not yet loaded. */
    external fun statistics(data: Long): NativeToolpathsStatistics

    external fun destroyData(data: Long)

    /** On the GL thread: a viewer for the current context, or 0 when libvgcode cannot run in it. */
    external fun createViewer(): Long

    /** On the GL thread: the viewer takes [data], which is no longer valid afterwards. */
    external fun load(viewer: Long, data: Long)

    /** On the GL thread: draws with column-major [view] and [projection] matrices. */
    external fun render(viewer: Long, view: FloatArray, projection: FloatArray)

    /** On the GL thread of the viewer's context, or after that context is gone. */
    external fun destroyViewer(viewer: Long)

    // What the viewer shows. On the GL thread: the changes upload textures.

    /** libvgcode::EViewType. */
    external fun setViewType(viewer: Long, type: Int)

    external fun setLayersViewRange(viewer: Long, min: Int, max: Int)

    /** Vertex ids of the first and last visible move. */
    external fun setViewVisibleRange(viewer: Long, first: Int, last: Int)

    /** libvgcode::EGCodeExtrusionRole. */
    external fun toggleExtrusionRoleVisibility(viewer: Long, role: Int)

    /** libvgcode::EOptionType. */
    external fun toggleOptionVisibility(viewer: Long, option: Int)

    external fun snapshot(viewer: Long): NativeToolpathsSnapshot

    /** GCodeViewer::export_toolpaths_to_obj(): false when the OBJ file or its materials cannot be written. */
    external fun exportToObj(viewer: Long, path: String): Boolean
}

/** Constructed by the native bridge; see Statistics in toolpaths_file.hpp. */
internal class NativeToolpathsStatistics(
    /** Per libvgcode::ETimeMode. */
    @JvmField val time: FloatArray,
    @JvmField val prepareTime: FloatArray,
    /** libvgcode::EGCodeExtrusionRole of every used_filament_per_role entry. */
    @JvmField val roles: IntArray,
    /** Metres and grams per entry of [roles]. */
    @JvmField val roleFilament: DoubleArray,
    /** Metres and grams of the model, support, flushed, and wipe tower filament. */
    @JvmField val filament: DoubleArray,
    @JvmField val totalUsedFilament: Double,
    @JvmField val totalWeight: Double,
    @JvmField val totalCost: Double,
    @JvmField val totalTravelDistance: Float,
    @JvmField val totalTravelMoves: Int,
    @JvmField val totalSeamDistance: Float,
    @JvmField val totalFilamentChanges: Int,
    @JvmField val totalExtruderChanges: Int,
    @JvmField val totalToolChangeTime: Float,
    /** Per libvgcode::EMoveType. */
    @JvmField val moveCounts: IntArray,
    /** Per libvgcode::EMoveType, then per time mode. */
    @JvmField val moveTimes: FloatArray,
    @JvmField val moveDistances: FloatArray,
)

/** Constructed by the native bridge: what the viewer shows after the commands so far. */
internal class NativeToolpathsSnapshot(
    @JvmField val viewType: Int,
    @JvmField val layersZs: FloatArray,
    /** Lower and upper visible layer. */
    @JvmField val layersRange: IntArray,
    @JvmField val roles: IntArray,
    /** RGB per role. */
    @JvmField val roleColors: IntArray,
    @JvmField val roleVisible: BooleanArray,
    /** Seconds per role, in the normal time mode. */
    @JvmField val roleTimes: FloatArray,
    @JvmField val options: IntArray,
    @JvmField val optionColors: IntArray,
    @JvmField val optionVisible: BooleanArray,
    @JvmField val travelsTime: Float,
    @JvmField val estimatedTime: Float,
    /** The colour range legend of the view type, when it has one. */
    @JvmField val rangeValues: FloatArray,
    @JvmField val rangePalette: IntArray,
    /** Vertex ids of the first and last visible move. */
    @JvmField val visibleRange: IntArray,
    /** Vertex id of every move of the top layer, one per G-code line. */
    @JvmField val moves: IntArray,
    /** G-code line id of every move. */
    @JvmField val moveLines: IntArray,
    /** RGB per tool. */
    @JvmField val toolColors: IntArray,
    @JvmField val usedExtruders: IntArray,
    /** G-code line id of the current move (get_current_vertex()), 0 for none. */
    @JvmField val currentLine: Int,
    /** The current vertex's position, empty for none; whether the visible range ends where the full one does. */
    @JvmField val markerPosition: FloatArray,
    @JvmField val atEnd: Boolean,
    /**
     * The vertex the position window describes: x, y, z, width, height,
     * feedrate, acceleration, jerk, volumetric rate, fan speed, temperature,
     * pressure advance, layer duration, estimated time at it, its own time;
     * and its move type, extrusion role, whether it extrudes, layer, extruder
     * and colour. Empty for none.
     */
    @JvmField val vertexValues: FloatArray,
    @JvmField val vertexKinds: IntArray,
)
