package app.orcinus.shadow.render.gcode

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

/** Count, normal-mode time in seconds, and distance in millimetres of the moves of one type. */
data class MoveStatistics(val count: Int, val time: Float, val distance: Float)

/**
 * The legend's figures from the slice (GCodeProcessorResult::print_statistics,
 * PrintStatistics, and the moves), in the normal time mode.
 */
data class ToolpathsStatistics(
    val time: Float,
    val prepareTime: Float,
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
)
