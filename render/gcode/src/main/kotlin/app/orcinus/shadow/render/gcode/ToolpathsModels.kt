package app.orcinus.shadow.render.gcode

import app.orcinus.shadow.core.model.Vector3

/** libvgcode::EViewType, in its order. */
enum class ToolpathsViewType {
    Summary,
    FeatureType,
    ColorPrint,
    Speed,
    ActualSpeed,
    Height,
    Width,
    VolumetricFlowRate,
    ActualVolumetricFlowRate,
    LayerTimeLinear,
    LayerTimeLogarithmic,
    FanSpeed,
    Temperature,
    PressureAdvance,
    Acceleration,
    Jerk,
    Tool,
}

/** libvgcode::EGCodeExtrusionRole, in its order. */
enum class ToolpathsRole {
    None,
    Perimeter,
    ExternalPerimeter,
    OverhangPerimeter,
    InternalInfill,
    SolidInfill,
    TopSolidInfill,
    Ironing,
    BridgeInfill,
    GapFill,
    Skirt,
    SupportMaterial,
    SupportMaterialInterface,
    WipeTower,
    Custom,
    BottomSurface,
    InternalBridgeInfill,
    Brim,
    SupportTransition,
    Mixed,
}

/** libvgcode::EOptionType, in its order. */
enum class ToolpathsOption {
    Travels,
    Wipes,
    Retractions,
    Unretractions,
    Seams,
    ToolChanges,
    ColorChanges,
    PausePrints,
    CustomGCodes,
}

/** libvgcode::EMoveType, in its order. */
enum class ToolpathsMoveType {
    Noop,
    Retract,
    Unretract,
    Seam,
    ToolChange,
    ColorChange,
    PausePrint,
    CustomGCode,
    Travel,
    Wipe,
    Extrude,
}

/** Filament in metres and grams. */
data class FilamentUsage(val meters: Double, val grams: Double)

/**
 * Count, time in seconds and distance in millimetres of the moves of one type
 * (m_move_type_counts, m_move_type_times, m_move_type_distances): the time of
 * the normal mode, and of the stealth mode, 0 without one.
 */
data class MoveStatistics(val count: Int, val time: Float, val distance: Float, val stealthTime: Float = 0f) {
    /** m_move_type_times[..][time mode]. */
    fun timeIn(mode: ToolpathsTimeMode): Float = if (mode == ToolpathsTimeMode.Stealth) stealthTime else time
}

/**
 * The legend's figures from the slice (GCodeProcessorResult::print_statistics,
 * PrintStatistics, and the moves), in the normal time mode.
 */
data class ToolpathsStatistics(
    val time: Float,
    /** The stealth mode's time (silent_mode), 0 without one. */
    val stealthTime: Float = 0f,
    val prepareTime: Float,
    val stealthPrepareTime: Float = 0f,
    val filamentPerRole: Map<ToolpathsRole, FilamentUsage>,
    val modelFilament: FilamentUsage,
    val supportFilament: FilamentUsage,
    val flushedFilament: FilamentUsage,
    val wipeTowerFilament: FilamentUsage,
    /** PrintStatistics::total_used_filament, in millimetres. */
    val totalUsedFilament: Double,
    /** Grams. */
    val totalWeight: Double,
    val totalCost: Double,
    /** Millimetres. */
    val totalTravelDistance: Float,
    val totalTravelMoves: Int,
    val totalSeamDistance: Float,
    val totalFilamentChanges: Int,
    val totalExtruderChanges: Int,
    val totalToolChangeTime: Float,
    val moves: Map<ToolpathsMoveType, MoveStatistics>,
    /** The model, support, flushed and wipe tower filament of every extruder the volume maps list, from 0. */
    val filamentPerExtruder: Map<Int, ExtruderFilament> = emptyMap(),
)

/**
 * The filament of an extruder as the ColorPrint legend's columns show it
 * (render_legend()'s model_used_filaments_m and the like); null where the
 * statistics list none of it.
 */
data class ExtruderFilament(
    val model: FilamentUsage?,
    val support: FilamentUsage?,
    val flushed: FilamentUsage?,
    val wipeTower: FilamentUsage?,
)

/** A feature type the viewer draws, with its colour (0xRRGGBB) and normal-mode time in seconds. */
data class RoleLegend(val role: ToolpathsRole, val color: Int, val visible: Boolean, val time: Float)

/** A move option the viewer can draw, with its colour (0xRRGGBB). */
data class OptionLegend(val option: ToolpathsOption, val color: Int, val visible: Boolean)

/** A value of a colour range legend and its colour (0xRRGGBB). */
data class RangeLegend(val value: Float, val color: Int)

/**
 * What the viewer shows: the view type, the layers with the visible range,
 * the legend of the view type, and the moves of the top visible layer with
 * the ones that are visible.
 */
data class ToolpathsView(
    val viewType: ToolpathsViewType,
    /** Print height of every layer, in millimetres. */
    val layerZs: List<Float>,
    val lowerLayer: Int,
    val upperLayer: Int,
    val roles: List<RoleLegend>,
    val options: List<OptionLegend>,
    /** Normal-mode seconds of the travel moves, and of every move. */
    val travelsTime: Float,
    val estimatedTime: Float,
    /** Highest value first, as the desktop legend lists a range. */
    val range: List<RangeLegend>,
    /** Vertex id of every move of the top visible layer, one per G-code line. */
    val moves: List<Int>,
    /** Index in [moves] of the last visible move. */
    val lastVisibleMove: Int,
    /** Colour (0xRRGGBB) of every tool, and the tools the G-code uses. */
    val toolColors: List<Int>,
    val usedExtruders: List<Int>,
    /** The G-code line of the current move (the viewer's current vertex), from 1; 0 for none. */
    val currentLine: Int = 0,
    /**
     * GCodeViewer's tool marker: the current vertex, once the move slider has
     * left the end of the moves since the G-code was loaded (m_show_marker);
     * null while it hides.
     */
    val marker: Vector3? = null,
    /** The move the marker's position window describes; null with the marker hidden. */
    val vertex: ToolpathsVertex? = null,
    /** The actual speed along the current move's G-code line, as last worked out; null before. */
    val speedProfile: ActualSpeedProfile? = null,
    /** libvgcode::ETimeMode the times are in: normal, or stealth. */
    val timeMode: ToolpathsTimeMode = ToolpathsTimeMode.Normal,
    /** Seconds of every layer in [timeMode] (get_layers_estimated_times()). */
    val layerTimes: List<Float> = emptyList(),
    /** GCodeViewer::can_export_toolpaths(): an extrusion among the visible moves. */
    val canExportToolpaths: Boolean = false,
)

/** libvgcode::ETimeMode, in its order. */
enum class ToolpathsTimeMode {
    Normal,
    Stealth,
}

/**
 * GCodeViewer::SequentialView::ActualSpeedImguiWidget's data: the points of a
 * move's G-code line, the actual speed range of the G-code and its levels,
 * each with its colour (0xRRGGBB).
 */
data class ActualSpeedProfile(
    val points: List<ActualSpeedPoint>,
    val lowest: Float,
    val highest: Float,
    val levels: List<Pair<Float, Int>>,
)

/** A point of the profile: millimetres along the line, the speed there, and whether it is internal. */
data class ActualSpeedPoint(val position: Float, val speed: Float, val internal: Boolean)

/** libvgcode's PathVertex as Marker::render_position_window() describes it. */
data class ToolpathsVertex(
    val position: Vector3,
    val type: ToolpathsMoveType,
    /** Its extrusion role; null for one the app does not know. */
    val role: ToolpathsRole?,
    val extrusion: Boolean,
    val width: Float,
    val height: Float,
    val feedrate: Float,
    val acceleration: Float,
    val jerk: Float,
    val volumetricRate: Float,
    val fanSpeed: Float,
    val temperature: Float,
    val pressureAdvance: Float,
    val layerDuration: Float,
    /** Layer, extruder and colour, from 0. */
    val layer: Int,
    val extruder: Int,
    val color: Int,
    /** get_estimated_time_at(), and the move's own time in the time mode. */
    val estimatedTime: Float,
    val time: Float,
)
