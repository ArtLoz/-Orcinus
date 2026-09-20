package app.orcinus.shadow.feature.sidebar

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.orcinus.shadow.core.designsystem.component.OrcaButton
import app.orcinus.shadow.core.designsystem.component.OrcaButtonStyle
import app.orcinus.shadow.core.designsystem.component.OrcaFilamentSlot
import app.orcinus.shadow.core.designsystem.component.OrcaSheetHandle
import app.orcinus.shadow.core.designsystem.component.OrcaTextField
import app.orcinus.shadow.core.designsystem.component.textLocale
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import app.orcinus.shadow.core.model.FlushVolumes
import app.orcinus.shadow.core.ui.orca.orcaString
import java.util.Locale
import kotlin.math.roundToInt

/**
 * OrcaSlicer's WipingDialog: how much filament goes into the wipe tower when
 * the print changes from one filament to another, a volume per pair. The
 * desktop dialog is a table with a row and a column per filament; a phone shows
 * the same table, scrolled sideways when the plate has many filaments, with the
 * filament of every row and column marked by its colour.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FlushVolumesSheet(
    volumes: FlushVolumes,
    /** The colour of every filament of the plate, as the sidebar shows them. */
    colors: List<Color>,
    onDismiss: () -> Unit,
    onApply: (matrix: List<Double>, multipliers: List<Double>) -> Unit,
) {
    val theme = OrcaTheme.colors
    // The edited table, as text while it is being typed into.
    var cells by remember(volumes) { mutableStateOf(volumes.matrix.map(::formatVolume)) }
    var multipliers by remember(volumes) { mutableStateOf(volumes.multipliers.map(::formatMultiplier)) }
    val filaments = volumes.filaments
    // A printer with one nozzle has a single table, as the desktop dialog shows.
    val nozzle = 0

    val apply = {
        val matrix = cells.map { it.replace(',', '.').toDoubleOrNull() ?: 0.0 }
        val factors = multipliers.map { it.replace(',', '.').toDoubleOrNull()?.coerceIn(MIN_MULTIPLIER, MAX_MULTIPLIER) ?: 1.0 }
        onApply(matrix, factors)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = theme.window,
        dragHandle = { OrcaSheetHandle() },
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
        ) {
            Text(
                text = orcaString("Flushing volumes"),
                color = theme.text,
                style = OrcaTheme.typography.head16,
                modifier = Modifier.padding(vertical = 4.dp),
            )
            Text(
                text = orcaString("Flushing volume (mm³) for each filament pair."),
                color = theme.textSide,
                style = OrcaTheme.typography.body12,
            )
            Text(
                text = String.format(textLocale(), orcaString("Suggestion: Flushing Volume in range [%d, %d]"), 0, SUGGESTED_MAX),
                color = theme.textSide,
                style = OrcaTheme.typography.body12,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                Text(
                    text = orcaString("Multiplier"),
                    color = theme.text,
                    style = OrcaTheme.typography.body14,
                    modifier = Modifier.weight(1f),
                )
                OrcaTextField(
                    value = multipliers.getOrElse(nozzle) { "1.00" },
                    onValueChange = { typed -> multipliers = multipliers.replaced(nozzle, typed) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    modifier = Modifier.width(120.dp),
                )
            }

            // The table: a column per filament printed into, a row per filament
            // printed from, as the desktop dialog lays it out.
            Column(Modifier.horizontalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.width(HEADER_WIDTH))
                    repeat(filaments) { to -> FilamentHeader(to, colors) }
                }
                repeat(filaments) { from ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 6.dp),
                    ) {
                        Box(Modifier.width(HEADER_WIDTH), contentAlignment = Alignment.CenterStart) {
                            FilamentHeader(from, colors)
                        }
                        repeat(filaments) { to ->
                            val at = nozzle * filaments * filaments + from * filaments + to
                            if (from == to) {
                                // A filament is never flushed into itself.
                                Text(
                                    text = "—",
                                    color = theme.textDisabled,
                                    style = OrcaTheme.typography.body14,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.width(CELL_WIDTH),
                                )
                            } else {
                                OrcaTextField(
                                    value = cells.getOrElse(at) { "0" },
                                    onValueChange = { typed -> cells = cells.replaced(at, typed) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                                    keyboardActions = KeyboardActions.Default,
                                    modifier = Modifier
                                        .width(CELL_WIDTH)
                                        .padding(horizontal = 2.dp),
                                )
                            }
                        }
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
            ) {
                // The desktop dialog works the volumes out from the filament
                // colours again (CalcFlushingVolumes). The table holds the
                // volumes themselves; the multiplier is applied when the print
                // changes filament, not here.
                OrcaButton(
                    text = orcaString("Re-calculate"),
                    style = OrcaButtonStyle.Regular,
                    onClick = { cells = volumes.automatic.map(::formatVolume) },
                    modifier = Modifier.weight(1f),
                )
                OrcaButton(
                    text = orcaString("OK"),
                    onClick = apply,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** The filament of a row or a column: its number over its colour. */
@Composable
private fun FilamentHeader(index: Int, colors: List<Color>) {
    Box(
        Modifier
            .width(CELL_WIDTH)
            .heightIn(min = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        OrcaFilamentSlot(
            number = index + 1,
            color = colors.getOrElse(index) { OrcaTheme.colors.accent },
            modifier = Modifier.size(22.dp),
        )
    }
}

private fun List<String>.replaced(at: Int, value: String): List<String> =
    mapIndexed { index, existing -> if (index == at) value else existing }

/** A volume as the desktop dialog shows it: whole cubic millimetres. */
private fun formatVolume(value: Double): String = value.roundToInt().toString()

private fun formatMultiplier(value: Double): String = String.format(Locale.US, "%.2f", value)

/** The range the desktop dialog suggests for a volume. */
private const val SUGGESTED_MAX = 700

/** The multiplier the desktop dialog allows (g_min_flush_multiplier, g_max_flush_multiplier). */
private const val MIN_MULTIPLIER = 0.0
private const val MAX_MULTIPLIER = 3.0

private val CELL_WIDTH = 72.dp
private val HEADER_WIDTH = 40.dp
