package app.orcinus.shadow.feature.preview

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.orcinus.shadow.core.designsystem.R as DesignR
import app.orcinus.shadow.core.designsystem.component.OrcaButton
import app.orcinus.shadow.core.designsystem.component.OrcaButtonSize
import app.orcinus.shadow.core.designsystem.component.OrcaButtonStyle
import app.orcinus.shadow.core.designsystem.component.OrcaChoiceChips
import app.orcinus.shadow.core.designsystem.component.OrcaColorScale
import app.orcinus.shadow.core.designsystem.component.OrcaIconButton
import app.orcinus.shadow.core.designsystem.component.OrcaLegendItem
import app.orcinus.shadow.core.designsystem.component.OrcaLegendSection
import app.orcinus.shadow.core.designsystem.component.OrcaLegendValue
import app.orcinus.shadow.core.designsystem.component.OrcaSheetHandle
import app.orcinus.shadow.core.designsystem.component.OrcaSummaryItem
import app.orcinus.shadow.core.designsystem.component.OrcaSummaryRow
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import app.orcinus.shadow.core.model.ImperialUnits
import app.orcinus.shadow.core.model.LayerGcode
import app.orcinus.shadow.core.model.LayerGcodeType
import app.orcinus.shadow.core.ui.orca.orcaString
import app.orcinus.shadow.render.gcode.ExtruderFilament
import app.orcinus.shadow.render.gcode.FilamentUsage
import app.orcinus.shadow.render.gcode.OptionLegend
import app.orcinus.shadow.render.gcode.ToolpathsMoveType
import app.orcinus.shadow.render.gcode.ToolpathsOption
import app.orcinus.shadow.render.gcode.ToolpathsRole
import app.orcinus.shadow.render.gcode.ToolpathsStatistics
import app.orcinus.shadow.render.gcode.ToolpathsTimeMode
import app.orcinus.shadow.render.gcode.ToolpathsView
import app.orcinus.shadow.render.gcode.ToolpathsViewType

/** The view types, in the order of OrcaSlicer's legend (GCodeViewer::update_by_mode()). */
internal val LegendViewTypes = listOf(
    ToolpathsViewType.FeatureType,
    ToolpathsViewType.Summary,
    ToolpathsViewType.ColorPrint,
    ToolpathsViewType.Speed,
    ToolpathsViewType.ActualSpeed,
    ToolpathsViewType.Acceleration,
    ToolpathsViewType.Jerk,
    ToolpathsViewType.Height,
    ToolpathsViewType.Width,
    ToolpathsViewType.VolumetricFlowRate,
    ToolpathsViewType.ActualVolumetricFlowRate,
    ToolpathsViewType.LayerTimeLinear,
    ToolpathsViewType.LayerTimeLogarithmic,
    ToolpathsViewType.FanSpeed,
    ToolpathsViewType.Temperature,
    ToolpathsViewType.PressureAdvance,
)

/**
 * The preview's legend as a bottom sheet, with OrcaSlicer's content
 * (GCodeViewer::render_legend()): collapsed, the print's time, filament, and
 * layers; expanded, the view types, what the view colours with its time,
 * share, and filament, the move options, and the estimation. Lines of feature
 * types and options show or hide them. [imperial] units (use_inches) write
 * lengths in inches and weights in ounces.
 */
@Composable
internal fun ToolpathsSheet(
    view: ToolpathsView,
    statistics: ToolpathsStatistics,
    imperial: Boolean,
    onViewTypeChange: (ToolpathsViewType) -> Unit,
    onRoleVisibleChange: (ToolpathsRole, Boolean) -> Unit,
    onOptionVisibleChange: (ToolpathsOption, Boolean) -> Unit,
    /** show_gcode_window, which the legend's button toggles (GUI_App::toggle_show_gcode_window()). */
    gcodeWindow: Boolean = true,
    onGcodeWindowChange: (Boolean) -> Unit = {},
    /** The plate's codes on the layers, which the "Custom G-code" section lists. */
    layerGcodes: List<LayerGcode> = emptyList(),
    onTimeModeChange: (ToolpathsTimeMode) -> Unit = {},
) {
    // render_legend() takes the estimated time of the time mode, or the viewer's own.
    val modeTime = statistics.timeIn(view.timeMode)
    val totalTime = if (modeTime > 0f) modeTime else view.estimatedTime
    val navigationBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Column(
        Modifier
            .fillMaxWidth()
            // Expanded, the sheet leaves the model in sight above it.
            .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * MAX_SHEET_FRACTION)
            .verticalScroll(rememberScrollState()),
    ) {
        OrcaSheetHandle(Modifier.align(Alignment.CenterHorizontally))
        OrcaSummaryRow(
            listOf(
                OrcaSummaryItem(DesignR.drawable.orca_monitor_item_prediction, LegendFormat.shortTime(totalTime), stringResource(R.string.summary_time)),
                OrcaSummaryItem(
                    DesignR.drawable.orca_filament,
                    LegendFormat.spacedMeters(statistics.totalUsedFilament / 1_000.0, imperial),
                    LegendFormat.compactWeight(statistics.totalWeight, imperial),
                ),
                OrcaSummaryItem(DesignR.drawable.orca_param_layer_height, view.layerZs.size.toString(), stringResource(R.string.summary_layers)),
            ),
        )
        // Collapsed, the sheet ends here, above the navigation bar.
        Spacer(Modifier.height(SUMMARY_BOTTOM + navigationBar))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OrcaChoiceChips(
                items = LegendViewTypes.map { viewTypeName(it) },
                selected = LegendViewTypes.indexOf(view.viewType).coerceAtLeast(0),
                onSelect = { onViewTypeChange(LegendViewTypes[it]) },
                modifier = Modifier.weight(1f),
            )
            // The legend's G-code button beside the view types, lit while the window shows.
            OrcaIconButton(
                icon = DesignR.drawable.orca_im_code,
                contentDescription = stringResource(R.string.gcode_window),
                onClick = { onGcodeWindowChange(!gcodeWindow) },
                tint = if (gcodeWindow) OrcaTheme.colors.accent else OrcaTheme.colors.textSide,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
        when (view.viewType) {
            ToolpathsViewType.FeatureType -> FeatureTypes(view, statistics, totalTime, imperial, onRoleVisibleChange, onOptionVisibleChange)
            ToolpathsViewType.Summary -> Summary(statistics, imperial)
            ToolpathsViewType.ColorPrint -> ColorPrint(view, statistics, imperial)
            ToolpathsViewType.Tool -> Unit
            else -> ColorRange(view, onOptionVisibleChange)
        }
        CustomGcodes(view, layerGcodes)
        Estimation(view, statistics, imperial, onTimeModeChange)
        if (view.viewType == ToolpathsViewType.ColorPrint) {
            Options(view, onOptionVisibleChange)
        }
        Spacer(Modifier.height(16.dp + navigationBar))
    }
}

/** The collapsed sheet's height, its handle and summary included; the page peeks at this. */
internal val SheetPeekHeight = 76.dp

/** Space below the summary that completes [SheetPeekHeight]: the handle takes 20 dp and the summary 36 dp. */
private val SUMMARY_BOTTOM = 20.dp

private const val MAX_SHEET_FRACTION = 0.7f

@Composable
private fun FeatureTypes(
    view: ToolpathsView,
    statistics: ToolpathsStatistics,
    totalTime: Float,
    imperial: Boolean,
    onRoleVisibleChange: (ToolpathsRole, Boolean) -> Unit,
    onOptionVisibleChange: (ToolpathsOption, Boolean) -> Unit,
) {
    fun share(time: Float) = if (totalTime > 0f) time / totalTime else 0f
    fun detail(time: Float) = if (time > 0f) "${LegendFormat.shortTime(time)} · ${LegendFormat.percent(share(time))}%" else null

    OrcaLegendSection(viewTypeName(ToolpathsViewType.FeatureType)) {
        view.roles.forEach { role ->
            val used = statistics.filamentPerRole[role.role] ?: FilamentUsage(0.0, 0.0)
            OrcaLegendItem(
                color = rgb(role.color),
                title = roleName(role.role),
                detail = detail(role.time),
                share = share(role.time),
                usage = LegendFormat.meters(used.meters, imperial),
                // used_filament_per_role() gives ounces in imperial units, which
                // format_compact_weight() divides by oz_to_g once more; as OrcaSlicer shows them.
                usageDetail = LegendFormat.compactWeight(if (imperial) used.grams / ImperialUnits.OZ_TO_G else used.grams, imperial),
                visible = role.visible,
                onToggle = { onRoleVisibleChange(role.role, !role.visible) },
            )
        }
    }
    OrcaLegendSection(stringResource(R.string.header_options)) {
        view.options.forEach { option ->
            val name = optionName(option.option) ?: return@forEach
            if (option.option == ToolpathsOption.Travels) {
                // The travel line: time and share, distance and number of moves.
                OrcaLegendItem(
                    color = rgb(option.color),
                    title = name,
                    detail = detail(view.travelsTime),
                    share = share(view.travelsTime),
                    usage = LegendFormat.distance(statistics.totalTravelDistance, imperial),
                    usageDetail = LegendFormat.compactCount(statistics.totalTravelMoves.toLong()),
                    visible = option.visible,
                    onToggle = { onOptionVisibleChange(option.option, !option.visible) },
                )
            } else {
                // append_option_item()'s option_stats().
                val moves = moveTypeOf(option.option)?.let(statistics.moves::get)
                val seconds = if (option.option == ToolpathsOption.ToolChanges) statistics.totalToolChangeTime else moves?.time ?: 0f
                val distance = when (option.option) {
                    ToolpathsOption.Seams -> if (statistics.totalSeamDistance > 0f) statistics.totalSeamDistance else moves?.distance ?: 0f
                    ToolpathsOption.Wipes, ToolpathsOption.Retractions, ToolpathsOption.Unretractions -> moves?.distance ?: 0f
                    else -> null
                }
                val count = LegendFormat.compactCount((moves?.count ?: 0).toLong())
                OrcaLegendItem(
                    color = rgb(option.color),
                    title = name,
                    detail = detail(seconds),
                    share = share(seconds),
                    usage = distance?.let { LegendFormat.distance(it, imperial) } ?: count,
                    usageDetail = if (distance != null) count else null,
                    visible = option.visible,
                    onToggle = { onOptionVisibleChange(option.option, !option.visible) },
                )
            }
        }
    }
}

@Composable
private fun ColorRange(view: ToolpathsView, onOptionVisibleChange: (ToolpathsOption, Boolean) -> Unit) {
    val (title, decimals) = when (view.viewType) {
        ToolpathsViewType.Height -> stringResource(R.string.title_height) to 2
        ToolpathsViewType.Width -> stringResource(R.string.title_width) to 2
        ToolpathsViewType.Speed -> stringResource(R.string.title_speed) to 0
        ToolpathsViewType.ActualSpeed -> stringResource(R.string.title_actual_speed) to 0
        ToolpathsViewType.Acceleration -> stringResource(R.string.title_acceleration) to 0
        ToolpathsViewType.Jerk -> stringResource(R.string.title_jerk) to 1
        ToolpathsViewType.FanSpeed -> stringResource(R.string.title_fan_speed) to 0
        ToolpathsViewType.Temperature -> stringResource(R.string.title_temperature) to 0
        ToolpathsViewType.PressureAdvance -> stringResource(R.string.view_pressure_advance) to 3
        ToolpathsViewType.VolumetricFlowRate -> stringResource(R.string.title_flow) to 2
        ToolpathsViewType.ActualVolumetricFlowRate -> stringResource(R.string.title_actual_flow) to 2
        ToolpathsViewType.LayerTimeLinear -> stringResource(R.string.view_layer_time) to 1
        ToolpathsViewType.LayerTimeLogarithmic -> stringResource(R.string.view_layer_time_log) to 1
        else -> "" to 0
    }
    OrcaLegendSection(title) {
        // The range runs highest first; the scale reads from the lowest.
        val ascending = view.range.reversed()
        if (ascending.isNotEmpty()) {
            OrcaColorScale(
                colors = ascending.map { rgb(it.color) },
                lowest = LegendFormat.decimal(ascending.first().value, decimals),
                middle = if (ascending.size > 2) LegendFormat.decimal(ascending[ascending.size / 2].value, decimals) else null,
                highest = LegendFormat.decimal(ascending.last().value, decimals),
            )
        }
    }
    if (view.viewType in listOf(ToolpathsViewType.Speed, ToolpathsViewType.ActualSpeed, ToolpathsViewType.Acceleration, ToolpathsViewType.Jerk)) {
        // Speeds offer the travel moves below their range.
        view.options.firstOrNull { it.option == ToolpathsOption.Travels }?.let { travel ->
            OrcaLegendSection(stringResource(R.string.header_options)) {
                OptionItem(travel, stringResource(R.string.option_travel), onOptionVisibleChange)
            }
        }
    }
}

@Composable
private fun Summary(statistics: ToolpathsStatistics, imperial: Boolean) {
    OrcaLegendSection(viewTypeName(ToolpathsViewType.Summary)) {
        OrcaLegendValue(
            stringResource(R.string.total),
            "${LegendFormat.spacedMeters(statistics.totalUsedFilament / 1_000.0, imperial)} / ${LegendFormat.compactWeight(statistics.totalWeight, imperial)}",
        )
        OrcaLegendValue(stringResource(R.string.cost), LegendFormat.cost(statistics.totalCost))
        OrcaLegendValue(stringResource(R.string.total_time), LegendFormat.shortTime(statistics.time))
    }
}

@Composable
private fun ColorPrint(view: ToolpathsView, statistics: ToolpathsStatistics, imperial: Boolean) {
    // render_legend(): the columns any used extruder has filament listed in,
    // each extruder's filament in them, and their sum when there is more than the model's.
    val columns = listOf<Pair<Int, (ExtruderFilament) -> FilamentUsage?>>(
        R.string.header_model to ExtruderFilament::model,
        R.string.header_support to ExtruderFilament::support,
        R.string.header_flushed to ExtruderFilament::flushed,
        R.string.header_tower to ExtruderFilament::wipeTower,
    ).filter { (_, of) -> view.usedExtruders.any { extruder -> statistics.filamentPerExtruder[extruder]?.let(of) != null } }
    val none = FilamentUsage(0.0, 0.0)
    val usages = view.usedExtruders.map { extruder ->
        columns.map { (_, of) -> statistics.filamentPerExtruder[extruder]?.let(of) ?: none }
    }
    val titles = columns.map { stringResource(it.first) }
    @Composable
    fun FilamentRow(color: Color, title: String, usage: List<FilamentUsage>) {
        val total = FilamentUsage(usage.sumOf { it.meters }, usage.sumOf { it.grams })
        OrcaLegendItem(
            color = color,
            title = title,
            // With more than the model's column, each column's filament, then their total.
            detail = usage.takeIf { columns.size > 1 }
                ?.mapIndexed { index, part -> "${titles[index]} ${LegendFormat.spacedMeters(part.meters, imperial)}" }
                ?.joinToString(" · "),
            share = null,
            usage = LegendFormat.spacedMeters(total.meters, imperial),
            usageDetail = LegendFormat.compactWeight(total.grams, imperial),
            visible = null,
            onToggle = null,
        )
    }
    OrcaLegendSection(viewTypeName(ToolpathsViewType.ColorPrint)) {
        view.usedExtruders.forEachIndexed { index, extruder ->
            FilamentRow(view.toolColors.getOrNull(extruder)?.let(::rgb) ?: Color.Gray, (extruder + 1).toString(), usages[index])
        }
        // The sum of all rows, for more than one extruder.
        if (view.usedExtruders.size > 1) {
            val sums = columns.indices.map { column -> FilamentUsage(usages.sumOf { it[column].meters }, usages.sumOf { it[column].grams }) }
            FilamentRow(Color.Transparent, stringResource(R.string.header_total), sums)
        }
        OrcaLegendValue(stringResource(R.string.filament_change_times), LegendFormat.compactCount(statistics.totalFilamentChanges.toLong()))
        OrcaLegendValue(stringResource(R.string.tool_changes), LegendFormat.compactCount(statistics.totalExtruderChanges.toLong()))
        OrcaLegendValue(stringResource(R.string.cost), LegendFormat.cost(statistics.totalCost))
    }
}

@Composable
private fun Options(view: ToolpathsView, onOptionVisibleChange: (ToolpathsOption, Boolean) -> Unit) {
    OrcaLegendSection(stringResource(R.string.header_options)) {
        view.options.forEach { option ->
            optionName(option.option)?.let { OptionItem(option, it, onOptionVisibleChange) }
        }
    }
}

@Composable
private fun OptionItem(option: OptionLegend, name: String, onOptionVisibleChange: (ToolpathsOption, Boolean) -> Unit) {
    OrcaLegendItem(
        color = rgb(option.color),
        title = name,
        detail = null,
        share = null,
        usage = null,
        usageDetail = null,
        visible = option.visible,
        onToggle = { onOptionVisibleChange(option.option, !option.visible) },
    )
}

/**
 * render_legend()'s "Custom G-code" section: every code of the plate, its
 * kind, its layer (find_close_layer_idx(), from 1) and the time the print
 * takes before that layer in the time mode shown.
 */
@Composable
private fun CustomGcodes(view: ToolpathsView, codes: List<LayerGcode>) {
    if (codes.isEmpty()) return
    OrcaLegendSection(orcaString("Custom G-code")) {
        OrcaLegendValue(orcaString("Layer"), orcaString("Time"))
        codes.forEach { code ->
            val layer = view.layerZs.indexOfFirst { kotlin.math.abs(it - code.printZ) < LAYER_EPSILON }
            val before = view.layerTimes.take(layer.coerceAtLeast(0)).sum()
            val kind = when (code.type) {
                LayerGcodeType.PAUSE_PRINT -> "Pause"
                LayerGcodeType.TEMPLATE -> "Template"
                LayerGcodeType.TOOL_CHANGE -> "Tool Change"
                LayerGcodeType.CUSTOM -> "Custom"
                else -> "Unknown"
            }
            OrcaLegendValue("${orcaString(kind)} · ${layer + 1}", LegendFormat.shortTime(before))
        }
    }
}

/** find_close_layer_idx()'s epsilon, for the heights the G-code writes. */
private const val LAYER_EPSILON = 1e-4f

/** The print's time in [mode] (PrintEstimatedStatistics::modes). */
private fun ToolpathsStatistics.timeIn(mode: ToolpathsTimeMode) = if (mode == ToolpathsTimeMode.Stealth) stealthTime else time

/**
 * can_show_mode_button(): the G-code tells more than one time (the modes'
 * times above 0 that short_time() writes apart).
 */
private fun ToolpathsStatistics.hasOtherTimeMode(): Boolean =
    listOf(time, stealthTime).filter { it > 0f }.map(LegendFormat::shortTime).distinct().size > 1

/** GCodeViewer::load_as_gcode(): whether the time mode shown may stay for this G-code. */
internal fun ToolpathsStatistics.keepsTimeMode(): Boolean = hasOtherTimeMode()

@Composable
private fun Estimation(view: ToolpathsView, statistics: ToolpathsStatistics, imperial: Boolean, onTimeModeChange: (ToolpathsTimeMode) -> Unit) {
    val featureType = view.viewType == ToolpathsViewType.FeatureType
    val stealth = view.timeMode == ToolpathsTimeMode.Stealth
    val time = statistics.timeIn(view.timeMode)
    val prepareTime = if (stealth) statistics.stealthPrepareTime else statistics.prepareTime
    val modes = statistics.hasOtherTimeMode()
    // The title names the normal mode while another one can be shown.
    val title = stringResource(if (featureType) R.string.total_estimation else R.string.time_estimation) +
        if (modes && !stealth) " [${orcaString("Normal mode")}]" else ""
    OrcaLegendSection(title) {
        if (featureType) {
            fun length(millimeters: Double, grams: Double) =
                "${LegendFormat.spacedMeters(millimeters / 1_000.0, imperial)} · ${LegendFormat.compactWeight(grams, imperial)}"
            OrcaLegendValue(stringResource(R.string.total_filament), length(statistics.totalUsedFilament, statistics.totalWeight))
            // The model's filament: the total without support, flushed, and tower filament.
            val excluded = listOf(statistics.supportFilament, statistics.flushedFilament, statistics.wipeTowerFilament)
            OrcaLegendValue(
                stringResource(R.string.model_filament),
                length(statistics.totalUsedFilament - excluded.sumOf { it.meters } * 1_000.0, statistics.totalWeight - excluded.sumOf { it.grams }),
            )
            OrcaLegendValue(stringResource(R.string.cost), LegendFormat.cost(statistics.totalCost))
        }
        if (prepareTime != 0f) {
            OrcaLegendValue(stringResource(R.string.prepare_time), LegendFormat.shortTime(prepareTime))
        }
        OrcaLegendValue(stringResource(R.string.model_printing_time), LegendFormat.shortTime(time - prepareTime))
        OrcaLegendValue(stringResource(R.string.total_time), LegendFormat.shortTime(time), emphasized = true)
        // show_mode_button()
        if (modes) {
            OrcaButton(
                text = orcaString(if (stealth) "Show normal mode" else "Show stealth mode"),
                onClick = { onTimeModeChange(if (stealth) ToolpathsTimeMode.Normal else ToolpathsTimeMode.Stealth) },
                style = OrcaButtonStyle.Regular,
                size = OrcaButtonSize.Compact,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
    }
}

private fun rgb(color: Int) = Color(0xFF000000.toInt() or color)

/** The move type an option shows, for its statistics. */
private fun moveTypeOf(option: ToolpathsOption): ToolpathsMoveType? = when (option) {
    ToolpathsOption.Wipes -> ToolpathsMoveType.Wipe
    ToolpathsOption.Retractions -> ToolpathsMoveType.Retract
    ToolpathsOption.Unretractions -> ToolpathsMoveType.Unretract
    ToolpathsOption.Seams -> ToolpathsMoveType.Seam
    ToolpathsOption.ToolChanges -> ToolpathsMoveType.ToolChange
    else -> null
}

/** get_view_type_string(). */
@Composable
internal fun viewTypeName(type: ToolpathsViewType): String = stringResource(
    when (type) {
        ToolpathsViewType.Summary -> R.string.view_summary
        ToolpathsViewType.FeatureType -> R.string.view_feature_type
        ToolpathsViewType.ColorPrint -> R.string.view_color_print
        ToolpathsViewType.Speed -> R.string.view_speed
        ToolpathsViewType.ActualSpeed -> R.string.view_actual_speed
        ToolpathsViewType.Acceleration -> R.string.view_acceleration
        ToolpathsViewType.Jerk -> R.string.view_jerk
        ToolpathsViewType.Height -> R.string.view_height
        ToolpathsViewType.Width -> R.string.view_width
        ToolpathsViewType.VolumetricFlowRate -> R.string.view_flow
        ToolpathsViewType.ActualVolumetricFlowRate -> R.string.view_actual_flow
        ToolpathsViewType.LayerTimeLinear -> R.string.view_layer_time
        ToolpathsViewType.LayerTimeLogarithmic -> R.string.view_layer_time_log
        ToolpathsViewType.FanSpeed -> R.string.view_fan_speed
        ToolpathsViewType.Temperature -> R.string.view_temperature
        ToolpathsViewType.PressureAdvance -> R.string.view_pressure_advance
        ToolpathsViewType.Tool -> R.string.view_color_print
    },
)

/** ExtrusionEntity::role_to_string(). */
@Composable
internal fun roleName(role: ToolpathsRole): String = stringResource(
    when (role) {
        ToolpathsRole.None -> R.string.role_none
        ToolpathsRole.Perimeter -> R.string.role_inner_wall
        ToolpathsRole.ExternalPerimeter -> R.string.role_outer_wall
        ToolpathsRole.OverhangPerimeter -> R.string.role_overhang_wall
        ToolpathsRole.InternalInfill -> R.string.role_sparse_infill
        ToolpathsRole.SolidInfill -> R.string.role_internal_solid_infill
        ToolpathsRole.TopSolidInfill -> R.string.role_top_surface
        ToolpathsRole.Ironing -> R.string.role_ironing
        ToolpathsRole.BridgeInfill -> R.string.role_bridge
        ToolpathsRole.GapFill -> R.string.role_gap_infill
        ToolpathsRole.Skirt -> R.string.role_skirt
        ToolpathsRole.SupportMaterial -> R.string.role_support
        ToolpathsRole.SupportMaterialInterface -> R.string.role_support_interface
        ToolpathsRole.WipeTower -> R.string.role_prime_tower
        ToolpathsRole.Custom -> R.string.role_custom
        ToolpathsRole.BottomSurface -> R.string.role_bottom_surface
        ToolpathsRole.InternalBridgeInfill -> R.string.role_internal_bridge
        ToolpathsRole.Brim -> R.string.role_brim
        ToolpathsRole.SupportTransition -> R.string.role_support_transition
        ToolpathsRole.Mixed -> R.string.role_multiple
    },
)

/** The options append_option_item() names; the others have no line in the legend. */
@Composable
private fun optionName(option: ToolpathsOption): String? = when (option) {
    ToolpathsOption.Travels -> stringResource(R.string.option_travel)
    ToolpathsOption.Wipes -> stringResource(R.string.option_wipe)
    ToolpathsOption.Retractions -> stringResource(R.string.option_retract)
    ToolpathsOption.Unretractions -> stringResource(R.string.option_unretract)
    ToolpathsOption.Seams -> stringResource(R.string.option_seams)
    ToolpathsOption.ToolChanges -> stringResource(R.string.option_filament_changes)
    else -> null
}
