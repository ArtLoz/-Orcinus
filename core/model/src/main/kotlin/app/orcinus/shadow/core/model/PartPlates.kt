package app.orcinus.shadow.core.model

import kotlin.math.round
import kotlin.math.sqrt

/**
 * A plate of the project besides the objects that stand on it (PartPlate): its
 * own settings (PartPlate::config, with the project's wipe_tower_x and
 * wipe_tower_y at its index), the codes on its layers (Model::plates_custom_gcodes
 * at its index) and the G-code it was last sliced into (its Print and
 * GCodeResult), which applies while nothing it was sliced from changed ([basis]).
 */
data class PartPlate(
    /** PartPlate::m_name; empty for a plate the user did not name. */
    val name: String = "",
    /** PartPlate::m_locked: arranging and orienting every plate leave its objects alone. */
    val locked: Boolean = false,
    val settings: ModelSettings = ModelSettings(),
    val layerGcodes: List<LayerGcode> = emptyList(),
    val result: PlateSliceResult? = null,
    /** What [result] was sliced from, which Print::apply() finds unchanged when the plate is selected again. */
    val basis: SliceBasis? = null,
    /**
     * The calibration the plate's print carries (Print::set_calib_params):
     * the Calibration menu sets it, and a file loaded onto the plate clears it
     * (Plater::priv::load_files).
     */
    val calibration: CalibrationParams? = null,
)

/**
 * What the G-code of a plate is sliced from besides the plate itself: the
 * objects as the engine loads them (whatever plate judged their fit), the
 * presets and their values.
 */
data class SliceBasis(
    val objects: List<PlacedModel>,
    val profiles: SlicingProfileSelection?,
    val presetValues: List<List<String>?>,
)

/** The current plate and the number of plates, as the engine was told them (PlateInspector.selectPlate). */
data class EnginePlate(val index: Int = 0, val count: Int = 1)

/**
 * PartPlateList's layout of the plates for a printer's printable area: every
 * plate is as wide and deep as the area in whole millimetres (reset_size), with
 * a fifth of that between plates (LOGICAL_PART_PLATE_GAP). The first plate
 * stands at the origin, the next ones to its right, row after row towards the
 * front, in rows of [columns] plates (compute_colum_count).
 */
class PlateGrid(printableArea: List<Point2>) {
    private val minX = printableArea.minOfOrNull(Point2::x) ?: 0.0
    private val minY = printableArea.minOfOrNull(Point2::y) ?: 0.0
    private val maxX = printableArea.maxOfOrNull(Point2::x) ?: 0.0
    private val maxY = printableArea.maxOfOrNull(Point2::y) ?: 0.0

    /** m_plate_width and m_plate_depth. */
    val width: Int = (maxX - minX).toInt()
    val depth: Int = (maxY - minY).toInt()

    /** plate_stride_x() and plate_stride_y(). */
    val strideX: Double = width * (1.0 + LOGICAL_PART_PLATE_GAP)
    val strideY: Double = depth * (1.0 + LOGICAL_PART_PLATE_GAP)

    /** compute_shape_position(): where the plate at [index] stands while the plates stand in [columns]. */
    fun origin(index: Int, columns: Int): Point2 =
        Point2((index % columns) * strideX, -(index / columns) * strideY)

    /** The origin of the plate at [index] of [count] plates. */
    fun originOf(index: Int, count: Int): Point2 = origin(index, columns(count))

    /** compute_origin_for_unprintable(): where the objects of no plate go with [count] plates. */
    fun unprintableOrigin(count: Int): Point2 {
        val columns = columns(count)
        val max = columns * columns
        return if (count == max) origin(max + columns - 1, columns + 1) else origin(count, columns)
    }

    /**
     * PartPlate::get_build_volume() of the plate at [origin]: its printable
     * area up to [height], grown by BuildVolume::SceneEpsilon, as min and max corners.
     */
    fun box(origin: Point2, height: Double): Pair<Vector3, Vector3> = Pair(
        Vector3(minX + origin.x - SCENE_EPSILON, minY + origin.y - SCENE_EPSILON, -SCENE_EPSILON),
        Vector3(maxX + origin.x + SCENE_EPSILON, maxY + origin.y + SCENE_EPSILON, height + SCENE_EPSILON),
    )

    /** Whether the copy [inspection] describes crosses the plate at [origin] (PartPlate::intersect_instance). */
    fun intersects(inspection: ModelInspection, origin: Point2, height: Double): Boolean {
        val (min, max) = box(origin, height)
        val center = inspection.boxCenter
        val half = inspection.dimensions
        // BoundingBox3Base::intersects()
        return min.x < center.x + half.widthMillimeters / 2 && max.x > center.x - half.widthMillimeters / 2 &&
            min.y < center.y + half.depthMillimeters / 2 && max.y > center.y - half.depthMillimeters / 2 &&
            min.z < center.z + half.heightMillimeters / 2 && max.z > center.z - half.heightMillimeters / 2
    }

    /**
     * Whether the copy lies inside the plate at [origin] whole
     * (PartPlate::check_outside()): of a copy sinking below the plate only the
     * part above it counts, as ModelInstance::calc_print_volume_state() takes
     * it against the rectangular plate.
     */
    fun contains(inspection: ModelInspection, origin: Point2, height: Double): Boolean {
        val (plateMin, max) = box(origin, height)
        val center = inspection.boxCenter
        val half = inspection.dimensions
        val min = Vector3(center.x - half.widthMillimeters / 2, center.y - half.depthMillimeters / 2, center.z - half.heightMillimeters / 2)
        val top = Vector3(center.x + half.widthMillimeters / 2, center.y + half.depthMillimeters / 2, center.z + half.heightMillimeters / 2)
        // Not considering outside if sinking.
        val bottom = if (top.z > plateMin.z) plateMin.z + min.z else plateMin.z
        val insideXy = min.x >= plateMin.x && top.x <= max.x && min.y >= plateMin.y && top.y <= max.y
        if (min.z < -SINKING_Z_THRESHOLD) {
            val crosses = plateMin.x < top.x && max.x > min.x && plateMin.y < top.y && max.y > min.y && bottom < top.z && max.z > min.z
            // BuildVolume::object_state() without the plate's bottom.
            return crosses && insideXy && top.z <= max.z
        }
        return insideXy && min.z >= bottom && top.z <= max.z
    }

    companion object {
        /** PartPlateList::MAX_PLATES_COUNT */
        const val MAX_PLATES = 36

        private const val LOGICAL_PART_PLATE_GAP = 1.0 / 5.0
        private const val SCENE_EPSILON = 1e-4

        /** SINKING_Z_THRESHOLD of libslic3r, as a distance below the plate. */
        private const val SINKING_Z_THRESHOLD = 0.001

        /** compute_colum_count(): as close to a square as the plates fill. */
        fun columns(count: Int): Int {
            val value = sqrt(count.toFloat())
            val rounded = round(value)
            return if (value > rounded) rounded.toInt() + 1 else rounded.toInt()
        }
    }
}

/** The layout of the plates for the printer the plate was described for; null until then. */
val PlateState.plateGrid: PlateGrid? get() = plate?.let { PlateGrid(it.geometry.printableArea) }

/** Where every plate stands, in their order; the first alone at the origin until the plate is described. */
fun PlateState.plateOrigins(): List<Point2> {
    val grid = plateGrid ?: return List(plates.size) { Point2(0.0, 0.0) }
    return plates.indices.map { grid.originOf(it, plates.size) }
}

/** Where the current plate stands. */
val PlateState.plateOrigin: Point2 get() = plateOrigins().getOrElse(currentPlate) { Point2(0.0, 0.0) }

/** The plates arranging and orienting leave alone, by index. */
fun PlateState.lockedPlates(): Set<Int> = plates.indices.filterTo(LinkedHashSet()) { plates[it].locked }

/**
 * The plates with the current one as the plate holds it now: [PlateState]
 * keeps the current plate's settings, layer codes and G-code in its own fields.
 */
fun PlateState.partPlates(): List<PartPlate> = plates.mapIndexed { index, plate ->
    if (index == currentPlate) plate.copy(settings = plateSettings, layerGcodes = layerGcodes, result = result) else plate
}

/**
 * PartPlateList::is_all_slice_results_ready_for_print(): every plate a
 * printable copy stands on is sliced, and some plate is; a plate whose copies
 * are all unprintable needs no G-code.
 */
fun PlateState.allSliceResultsReady(): Boolean {
    val plates = partPlates()
    val copies = copies()
    var ready = false
    for (index in plates.indices) {
        val onPlate = copies.filter { plateOf(it) == index }
        if (onPlate.any { it.printable } && plates[index].result == null) return false
        if (plates[index].result != null) ready = true
    }
    return ready
}

/** PartPlateList::get_nonempty_plate_list(): the plates a copy stands on, by index. */
fun PlateState.nonemptyPlates(): List<Int> = plates.indices.filter { index -> copies().any { plateOf(it) == index } }

/**
 * PartPlateList::find_instance_belongs() and notify_instance_update(): the
 * plate a copy stands on, the first it crosses; null for one on none.
 */
fun PlateState.plateOf(instance: PlateInstance): Int? {
    val grid = plateGrid ?: return 0
    val height = plate?.geometry?.printableHeight ?: return 0
    return plateOrigins().indexOfFirst { grid.intersects(instance.inspection, it, height) }.takeIf { it >= 0 }
}

/**
 * PartPlate::contain_instance_totally() of the object's first copy: the plate
 * the object list shows the object under, the one its first copy stands on
 * whole; null for "Outside".
 */
fun PlateState.listPlateOf(plateObject: PlateObject): Int? {
    val first = plateObject.instances.firstOrNull() ?: return null
    val plate = plateOf(first) ?: return null
    val grid = plateGrid ?: return plate
    val height = this.plate?.geometry?.printableHeight ?: return plate
    return plate.takeIf { grid.contains(first.inspection, plateOrigins()[it], height) }
}

/**
 * What PlateSettingsDialog chooses for a plate (PartPlate::config): its bed
 * type (curr_bed_type), print sequence ("by layer", "by object") and spiral
 * vase mode, each null for "Same as Global", and the filament sequence of the
 * first layer and of the layer ranges after it, null for "Auto".
 */
data class PlateSettingsChoice(
    val bedType: String? = null,
    val printSequence: String? = null,
    val spiralMode: Boolean? = null,
    val firstLayerSequence: List<Int>? = null,
    val otherLayersSequence: List<LayerSequence>? = null,
)

/**
 * A range of layers with the order its filaments print in (LayerPrintSequence):
 * from layer [begin] to [end], both counted from 1, [END_LAYER] for the last one.
 */
data class LayerSequence(val begin: Int, val end: Int, val filaments: List<Int>) {
    companion object {
        /** PlateSettingsDialog's MIN_LAYER_VALUE: the ranges start from the second layer. */
        const val FIRST_LAYER = 2

        /** MAX_LAYER_VALUE: "End". */
        const val END_LAYER = Int.MAX_VALUE - 1
    }
}

/** The dialog's choices as the plate's settings hold them (PartPlate's getters). */
fun ModelSettings.plateSettingsChoice(): PlateSettingsChoice {
    fun ints(key: String) = values[key]?.split(',')?.mapNotNull { it.trim().toIntOrNull() }
    val others = ints(OTHER_LAYERS_SEQUENCE)?.let { flat ->
        // get_other_layers_print_sequence(): the ranges share the flat list evenly.
        val ranges = values[OTHER_LAYERS_SEQUENCE_NUMS]?.toIntOrNull()?.takeIf { it > 0 } ?: return@let null
        val size = flat.size / ranges
        if (size < 2) return@let null
        flat.chunked(size).take(ranges).map { LayerSequence(it[0], it[1], it.drop(2)) }
    }
    return PlateSettingsChoice(
        bedType = values[BED_TYPE],
        printSequence = values[PRINT_SEQUENCE],
        spiralMode = values[SPIRAL_MODE]?.let { it == "1" || it == "true" },
        firstLayerSequence = ints(FIRST_LAYER_SEQUENCE),
        otherLayersSequence = others,
    )
}

/**
 * The settings with the dialog's [choice]: set_bed_type(), set_print_seq(),
 * set_first_layer_print_sequence() and set_other_layers_print_sequence()
 * write a value or erase the key for "Same as Global" and "Auto".
 */
fun ModelSettings.withPlateSettingsChoice(choice: PlateSettingsChoice): ModelSettings {
    val updated = values.toMutableMap()
    fun put(key: String, value: String?) {
        if (value == null) updated.remove(key) else updated[key] = value
    }
    put(BED_TYPE, choice.bedType)
    put(PRINT_SEQUENCE, choice.printSequence)
    put(SPIRAL_MODE, choice.spiralMode?.let { if (it) "1" else "0" })
    put(FIRST_LAYER_SEQUENCE, choice.firstLayerSequence?.takeUnless { it.isEmpty() || it == listOf(0) }?.joinToString(","))
    val others = choice.otherLayersSequence?.takeIf { it.isNotEmpty() }
    put(OTHER_LAYERS_SEQUENCE, others?.flatMap { listOf(it.begin, it.end) + it.filaments }?.joinToString(","))
    put(OTHER_LAYERS_SEQUENCE_NUMS, others?.size?.toString())
    return ModelSettings(updated)
}

private const val BED_TYPE = "curr_bed_type"
private const val PRINT_SEQUENCE = "print_sequence"
private const val SPIRAL_MODE = "spiral_mode"
private const val FIRST_LAYER_SEQUENCE = "first_layer_print_sequence"
private const val OTHER_LAYERS_SEQUENCE = "other_layers_print_sequence"
private const val OTHER_LAYERS_SEQUENCE_NUMS = "other_layers_print_sequence_nums"
