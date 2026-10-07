package app.orcinus.shadow.feature.preview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import app.orcinus.shadow.core.model.FilamentAmount
import app.orcinus.shadow.core.model.FilamentUsage
import app.orcinus.shadow.core.model.SliceStatistics
import app.orcinus.shadow.core.ui.orca.orcaString
import app.orcinus.shadow.domain.plate.PlateSlice
import app.orcinus.shadow.domain.plate.PlateSliceState
import app.orcinus.shadow.domain.plate.AllPlatesStats

/**
 * The all plates stats item of the preview's plate bar
 * (GLCanvas3D::_render_imgui_select_plate_toolbar): before the plates are
 * sliced, "Slice all" or the plate being sliced over how many plates are
 * done, with a bar of the progress; red when a plate cannot be sliced; and
 * "All Plates" "Stats" once they all are. OrcaSlicer draws a picture behind
 * the texts, which a strip under the thumb has no room for.
 */
@Composable
internal fun AllPlatesItem(stats: AllPlatesStats, enabled: Boolean, onClick: () -> Unit) {
    val colors = OrcaTheme.colors
    val (top, bottom) = when (stats.state) {
        PlateSliceState.SLICED -> orcaString("All Plates") to orcaString("Stats")
        PlateSliceState.FAILED -> orcaString("Failed") to "${stats.sliced} / ${stats.total}"
        else -> (stats.slicingPlate?.let { orcaString("Slicing") + ": " + it } ?: orcaString("Slice all")) to "${stats.sliced} / ${stats.total}"
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(horizontal = 2.dp, vertical = 4.dp)
            .height(OrcaTheme.dimensions.minimumTouchTarget - 8.dp)
            .widthIn(min = 72.dp)
            .clip(OrcaTheme.shapes.control)
            .background(if (stats.selected) colors.accentSelected else Color.Transparent)
            .border(1.dp, if (stats.selected) colors.accent else Color.Transparent, OrcaTheme.shapes.control)
            .semantics { selected = stats.selected }
            // A plate that cannot be sliced keeps the item from slicing them all.
            .clickable(enabled = enabled && stats.state != PlateSliceState.FAILED, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(top, color = colors.onCanvasPanel, style = OrcaTheme.typography.body10, maxLines = 1)
        Text(bottom, color = colors.onCanvasPanel, style = OrcaTheme.typography.body10, maxLines = 1)
        if (stats.state != PlateSliceState.SLICED) {
            val bar = if (stats.state == PlateSliceState.FAILED) colors.alert else colors.accent
            val done = if (stats.state == PlateSliceState.FAILED) 1f else stats.progress.coerceIn(0f, 1f)
            Box(
                Modifier
                    .padding(top = 2.dp)
                    .width(56.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.textDisabled),
            ) {
                Box(
                    Modifier
                        .width(56.dp * done)
                        .height(3.dp)
                        .background(bar),
                )
            }
        }
    }
}

/**
 * GLCanvas3D::_render_imgui_select_plate_toolbar()'s marks of a plate's slice
 * state, which OrcaSlicer draws over the plate's thumbnail and the app under
 * its number: dimmed while it is not sliced, the dimming lifting from the top
 * as its slice goes on, a dark red veil with the red "!" when it failed, and
 * plain once it is sliced. The "!" stands in the corner, clear of the number.
 */
@Composable
internal fun BoxScope.PlateSliceMark(slice: PlateSlice) {
    val dark = OrcaTheme.colors.isDark
    // plate_bg and plate_dim
    val background = if (dark) Color(255, 255, 255, 10) else Color(0, 0, 0, 10)
    val dim = if (dark) Color(30, 30, 30, 100) else Color(0, 0, 0, 50)
    val area = Modifier
        .matchParentSize()
        .clip(OrcaTheme.shapes.control)
    when (slice.state) {
        PlateSliceState.UNSLICED -> Box(area.background(dim))
        PlateSliceState.SLICING -> Box(area.background(background)) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(1f - slice.progress.coerceIn(0f, 1f))
                    .background(dim),
            )
        }
        PlateSliceState.FAILED -> {
            Box(area.background(Color(64, 1, 1, 64)))
            val mark = if (dark) Color(60, 44, 48) else Color(202, 186, 186)
            Canvas(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .size(14.dp),
            ) {
                // The circle is 28 of the exclamation mark's units across.
                val unit = size.minDimension / 28f
                drawCircle(Color(225, 74, 74))
                drawRoundRect(mark, topLeft = center + Offset(-2f * unit, -10f * unit), size = Size(4f * unit, 14f * unit), cornerRadius = CornerRadius(2f * unit))
                drawCircle(mark, radius = 2f * unit, center = center + Offset(0f, 8f * unit))
            }
        }
        PlateSliceState.SLICED -> Box(area.background(background))
    }
}

/**
 * GCodeViewer::render_all_plates_stats(), which the preview shows instead of
 * the plate once the all plates stats item is picked and every plate with
 * objects is sliced: what the plates use of every filament they print with
 * for the objects, their support, the flushing and the wipe tower — the
 * columns only a filament uses — with their total, then the time and cost of
 * all of them.
 */
@Composable
internal fun AllPlatesStatsPanel(
    statistics: List<SliceStatistics>,
    filamentColors: List<Color>,
    imperial: Boolean,
    modifier: Modifier = Modifier,
    /** The viewer's time mode is the stealth one, whose time of every plate is summed. */
    stealth: Boolean = false,
) {
    val colors = OrcaTheme.colors
    // The filaments of every plate, in order, each summed over the plates.
    val usages = statistics.flatMap(SliceStatistics::filaments)
        .groupBy(FilamentUsage::filament)
        .toSortedMap()
        .map { (filament, plates) ->
            FilamentUsage(
                filament = filament,
                model = plates.fold(FilamentAmount.NONE) { sum, it -> sum + it.model },
                support = plates.fold(FilamentAmount.NONE) { sum, it -> sum + it.support },
                flushed = plates.fold(FilamentAmount.NONE) { sum, it -> sum + it.flushed },
                wipeTower = plates.fold(FilamentAmount.NONE) { sum, it -> sum + it.wipeTower },
            )
        }
    val used = { amount: (FilamentUsage) -> FilamentAmount -> usages.any { amount(it).meters != 0.0 || amount(it).grams != 0.0 } }
    val columns = listOfNotNull(
        ("Model" to FilamentUsage::model).takeIf { used(FilamentUsage::model) },
        ("Support" to FilamentUsage::support).takeIf { used(FilamentUsage::support) },
        ("Flushed" to FilamentUsage::flushed).takeIf { used(FilamentUsage::flushed) },
        ("Tower" to FilamentUsage::wipeTower).takeIf { used(FilamentUsage::wipeTower) },
    )
    // The model's column alone writes its figures on one line; a Total follows the others.
    val beyondModel = columns.any { it.first != "Model" }
    fun cell(amount: FilamentAmount): String =
        LegendFormat.spacedMeters(amount.meters, imperial) + (if (beyondModel) "\n" else "    ") + LegendFormat.compactWeight(amount.grams, imperial)

    Box(modifier.fillMaxSize().background(colors.canvas), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .padding(16.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(colors.canvasPanel)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 10.dp),
        ) {
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
            ) {
                // The filament column, with the swatch of each filament.
                TableColumn(orcaString("Filament"), usages.map { usage -> usage.filament.toString() }) { index ->
                    Box(
                        Modifier
                            .padding(end = 6.dp)
                            .size(10.dp)
                            .background(filamentColors.getOrElse(usages[index].filament - 1) { colors.accent }),
                    )
                }
                columns.forEach { (title, amount) ->
                    TableColumn(orcaString(title), usages.map { cell(amount(it)) })
                }
                if (beyondModel) {
                    TableColumn(orcaString("Total"), usages.map { usage -> cell(columns.fold(FilamentAmount.NONE) { sum, (_, amount) -> sum + amount(usage) }) })
                }
            }
            Text(
                orcaString("Total Estimation"),
                color = colors.onCanvasPanel,
                style = OrcaTheme.typography.body14,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp),
            )
            val time = statistics.sumOf { if (stealth) it.stealthPrintTimeSeconds else it.estimatedPrintTimeSeconds }.toFloat()
            SummaryLine(orcaString("Total time") + ":", LegendFormat.shortTime(time))
            SummaryLine(orcaString("Total cost") + ":", LegendFormat.cost(statistics.sumOf(SliceStatistics::cost)))
        }
    }
}

/** A column of the table: its bold header over a separator, and a cell for every filament. */
@Composable
private fun TableColumn(title: String, cells: List<String>, leading: (@Composable (index: Int) -> Unit)? = null) {
    val colors = OrcaTheme.colors
    Column(Modifier.padding(end = 16.dp)) {
        Text(title, color = colors.onCanvasPanel, style = OrcaTheme.typography.body13, fontWeight = FontWeight.Bold)
        HorizontalDivider(Modifier.padding(vertical = 4.dp).fillMaxWidth(), color = colors.onCanvasPanel.copy(alpha = 0.6f))
        cells.forEachIndexed { index, cell ->
            Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                leading?.invoke(index)
                Text(cell, color = colors.onCanvasPanel, style = OrcaTheme.typography.body13)
            }
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Row(Modifier.padding(horizontal = 12.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = OrcaTheme.colors.onCanvasPanel, style = OrcaTheme.typography.body13)
        Text(value, color = OrcaTheme.colors.onCanvasPanel, style = OrcaTheme.typography.body13)
    }
}
