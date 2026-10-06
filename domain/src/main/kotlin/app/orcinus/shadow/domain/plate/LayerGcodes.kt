package app.orcinus.shadow.domain.plate

import app.orcinus.shadow.core.model.LayerGcode
import app.orcinus.shadow.core.model.LayerGcodeRules
import app.orcinus.shadow.core.model.LayerGcodeType
import app.orcinus.shadow.core.model.PlateState
import kotlin.math.abs

/**
 * The layer slider's codes (IMSlider's TickCodeInfo, Plater's
 * EVT_CUSTOMEVT_TICKSCHANGED): a pause, a filament change, the printer's
 * template or G-code of the user's own where a layer starts. They stay with
 * the plate and reach the next slice; the G-code sliced before no longer
 * matches them, but the preview keeps showing it, as the desktop app does.
 */
class EditLayerGcodesUseCase(private val repository: PlateRepository) {
    /**
     * IMSlider::add_code_as_tick(): a code of [type] on the layer at
     * [printZ]; a filament change there takes [filament], and a pause or a
     * template on a layer that has a code already changes nothing.
     */
    fun add(printZ: Double, type: LayerGcodeType, filament: Int = 0) = repository.update { state ->
        val rules = state.result?.layerGcodeRules ?: return@update state
        if (rules.sequential || state.busy) return@update state
        if (type == LayerGcodeType.TEMPLATE && !rules.hasTemplate) return@update state
        if (type == LayerGcodeType.TOOL_CHANGE && (!rules.canChangeFilament || state.filamentCount() <= 1)) return@update state
        val extruder = if (filament > 0) filament else state.onlyExtruder()
        val existing = state.layerGcodes.firstOrNull { it.onLayer(printZ) }
        val code = when {
            existing == null -> LayerGcode(printZ, type, extruder, color = if (type == LayerGcodeType.TOOL_CHANGE) state.filamentColor(extruder) else "")
            // TickCodeInfo::switch_code_for_tick(): a filament change switches to the other filament.
            type == LayerGcodeType.TOOL_CHANGE && existing.type == LayerGcodeType.TOOL_CHANGE ->
                existing.copy(extruder = extruder, color = state.filamentColor(extruder))
            else -> return@update state
        }
        state.copy(layerGcodes = (state.layerGcodes - listOfNotNull(existing) + code).sortedBy(LayerGcode::printZ))
    }

    /** IMSlider::add_custom_gcode(): [gcode] of the user's own on the layer, in place of the code it had. */
    fun addCustom(printZ: Double, gcode: String) = repository.update { state ->
        val rules = state.result?.layerGcodeRules ?: return@update state
        if (rules.sequential || state.busy) return@update state
        val code = LayerGcode(printZ, LayerGcodeType.CUSTOM, state.onlyExtruder(), extra = gcode)
        state.copy(layerGcodes = (state.layerGcodes.filterNot { it.onLayer(printZ) } + code).sortedBy(LayerGcode::printZ))
    }

    /** IMSlider::delete_tick() */
    fun delete(printZ: Double) = repository.update { state ->
        if (state.busy || state.layerGcodes.none { it.onLayer(printZ) }) state else state.copy(layerGcodes = state.layerGcodes.filterNot { it.onLayer(printZ) })
    }

    /** std::max(1, m_only_extruder): the filament the plate prints with, the first one without any. */
    private fun PlateState.onlyExtruder(): Int = flushing.filaments.firstOrNull()?.takeIf { filamentCount() > 1 } ?: 1

    private fun PlateState.filamentCount(): Int = profiles?.allFilaments?.size ?: 1

    /** TickCodeInfo::get_color_for_tick() of a filament change: the filament's colour. */
    private fun PlateState.filamentColor(filament: Int): String = presets?.filamentColors?.getOrNull(filament - 1).orEmpty()
}

/** Whether the code stands on the layer at [printZ] (the slider keeps one code per tick). */
fun LayerGcode.onLayer(printZ: Double): Boolean = abs(this.printZ - printZ) < LAYER_EPSILON

/**
 * IMSlider::SetTicksValues() for a new slice: a print by object keeps no
 * codes, and one that cannot change filament keeps no filament change.
 */
internal fun List<LayerGcode>.allowedBy(rules: LayerGcodeRules, lastSpiralVase: Boolean): List<LayerGcode> {
    // Preview::check_layers_slider_values(): the codes past the last layer.
    val onLayers = filterNot { it.printZ - LAYER_EPSILON > rules.topZ }
    return when {
        // last_spiral_vase_status: a print that turned into a spiral vase or out of one keeps none.
        rules.spiralVase != lastSpiralVase -> emptyList()
        rules.sequential -> emptyList()
        !rules.canChangeFilament -> onLayers.filterNot { it.type == LayerGcodeType.TOOL_CHANGE }
        else -> onLayers
    }
}

private const val LAYER_EPSILON = 1e-4
