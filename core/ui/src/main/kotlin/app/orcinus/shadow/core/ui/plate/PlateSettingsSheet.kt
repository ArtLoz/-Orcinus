package app.orcinus.shadow.core.ui.plate

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.offset
import app.orcinus.shadow.core.designsystem.R as DesignR
import app.orcinus.shadow.core.designsystem.component.OrcaButton
import app.orcinus.shadow.core.designsystem.component.OrcaButtonStyle
import app.orcinus.shadow.core.designsystem.component.OrcaComboField
import app.orcinus.shadow.core.designsystem.component.OrcaIconButton
import app.orcinus.shadow.core.designsystem.component.OrcaMenuItem
import app.orcinus.shadow.core.designsystem.component.OrcaSheetHandle
import app.orcinus.shadow.core.designsystem.component.OrcaTextField
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import app.orcinus.shadow.core.model.BedTypeChoice
import app.orcinus.shadow.core.model.LayerSequence
import app.orcinus.shadow.core.model.PlateSettingsChoice
import app.orcinus.shadow.core.ui.orca.orcaString
import kotlin.math.roundToInt

/**
 * PlateSettingsDialog, as a sheet: the plate's name, its bed type (the ones
 * the printer model supports, chosen for a Bambu Lab printer alone), its print
 * sequence and spiral vase mode, each "Same as Global" or its own, and the
 * order the filaments print in on the first layer and on ranges of the other
 * layers, "Auto" or customized by dragging the filaments into their order, as
 * the dialog's DragCanvas does. OK asks first before spiral vase mode is
 * enabled ([spiralOn] is the plate's mode now), as PartPlate::set_spiral_vase_mode()
 * does; the name is kept however the sheet closes, as the dialog keeps it.
 * [onlyLayerSequence] shows the filament sequences alone, as the dialog the
 * plate tab's "Customize" opens ("only_layer_sequence").
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlateSettingsSheet(
    name: String,
    choice: PlateSettingsChoice,
    bedTypes: List<BedTypeChoice>,
    filamentColors: List<Color>,
    spiralOn: Boolean,
    /** printer_structure is I3: the question adds that such printers make no timelapse. */
    i3: Boolean,
    onlyLayerSequence: Boolean = false,
    onDismiss: (name: String) -> Unit,
    onConfirm: (name: String, choice: PlateSettingsChoice, vaseSettingsAgreed: Boolean) -> Unit,
) {
    val colors = OrcaTheme.colors
    val filaments = filamentColors.size.coerceAtLeast(1)
    val plainOrder = (1..filaments).toList()
    var plateName by remember { mutableStateOf(name) }
    var bedType by remember { mutableStateOf(choice.bedType?.takeIf { value -> bedTypes.any { it.value == value } }) }
    var printSequence by remember { mutableStateOf(choice.printSequence) }
    var spiral by remember { mutableStateOf(choice.spiralMode) }
    var firstLayer by remember { mutableStateOf(choice.firstLayerSequence) }
    var otherLayers by remember { mutableStateOf(choice.otherLayersSequence) }
    var invalidLayer by remember { mutableStateOf(false) }
    var askingSpiral by remember { mutableStateOf<PlateSettingsChoice?>(null) }

    fun confirm() {
        val ranges = otherLayers
        // OtherLayersSeqPanel's EVT_SET_BED_TYPE_CONFIRM: every range needs its layers.
        if (ranges != null && ranges.any { it.begin < LayerSequence.FIRST_LAYER || it.end < LayerSequence.FIRST_LAYER }) {
            invalidLayer = true
            return
        }
        val chosen = PlateSettingsChoice(bedType, printSequence, spiral, firstLayer, ranges?.sortedWith(LAYER_ORDER))
        if (spiral == true && !spiralOn) askingSpiral = chosen else onConfirm(plateName, chosen, false)
    }

    ModalBottomSheet(
        onDismissRequest = { onDismiss(plateName) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.window,
        dragHandle = { OrcaSheetHandle() },
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(orcaString("Plate Settings"), color = colors.text, style = OrcaTheme.typography.head16)
            if (!onlyLayerSequence) {
                SettingRow(orcaString("Plate name")) {
                    OrcaTextField(value = plateName, onValueChange = { plateName = it.take(PLATE_NAME_LENGTH) }, modifier = Modifier.fillMaxWidth())
                }
                SettingRow(orcaString("Bed type")) {
                    ChoiceField(
                        options = listOf(orcaString("Same as Global Plate Type")) + bedTypes.map { orcaString(it.label) },
                        selected = bedTypes.indexOfFirst { it.value == bedType } + 1,
                        // PlateSettingsDialog disables it for a printer of another vendor than Bambu Lab.
                        enabled = bedTypes.isNotEmpty(),
                        onSelect = { bedType = bedTypes.getOrNull(it - 1)?.value },
                    )
                }
                SettingRow(orcaString("Print sequence")) {
                    ChoiceField(
                        options = listOf(orcaString("Same as Global Print Sequence"), orcaString("By Layer"), orcaString("By Object")),
                        selected = PRINT_SEQUENCES.indexOf(printSequence).coerceAtLeast(0),
                        onSelect = { printSequence = PRINT_SEQUENCES[it] },
                    )
                }
                SettingRow(orcaString("Spiral vase")) {
                    ChoiceField(
                        options = listOf(orcaString("Same as Global"), orcaString("Enable"), orcaString("Disable")),
                        selected = when (spiral) {
                            null -> 0
                            true -> 1
                            false -> 2
                        },
                        onSelect = { spiral = listOf(null, true, false)[it] },
                    )
                }
            }
            SettingRow(orcaString("First layer filament sequence")) {
                ChoiceField(
                    options = listOf(orcaString("Auto"), orcaString("Customize")),
                    selected = if (firstLayer == null) 0 else 1,
                    onSelect = { firstLayer = if (it == 0) null else firstLayer ?: plainOrder },
                )
            }
            firstLayer?.let { order ->
                FilamentOrder(order.normalized(filaments), filamentColors) { firstLayer = it }
            }
            SettingRow(orcaString("Other layer filament sequence")) {
                ChoiceField(
                    options = listOf(orcaString("Auto"), orcaString("Customize")),
                    selected = if (otherLayers == null) 0 else 1,
                    onSelect = {
                        otherLayers = if (it == 0) null else otherLayers ?: listOf(LayerSequence(LayerSequence.FIRST_LAYER, LayerSequence.END_LAYER, plainOrder))
                    },
                )
            }
            otherLayers?.let { ranges ->
                ranges.forEachIndexed { index, range ->
                    LayerRangeRow(
                        range = range.copy(filaments = range.filaments.normalized(filaments)),
                        colors = filamentColors,
                        onChange = { changed -> otherLayers = ranges.mapIndexed { at, it -> if (at == index) changed else it } },
                    )
                }
                // OtherLayersSeqPanel's add and delete buttons: a range from no layer to the end, and the last one gone.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    OrcaIconButton(DesignR.drawable.orca_add, orcaString("Add"), onClick = {
                        otherLayers = ranges + LayerSequence(-1, LayerSequence.END_LAYER, plainOrder)
                    })
                    OrcaIconButton(DesignR.drawable.orca_delete, orcaString("Delete"), enabled = ranges.size > 1, onClick = {
                        otherLayers = ranges.dropLast(1)
                    })
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.End) {
                OrcaButton(orcaString("Cancel"), onClick = { onDismiss(plateName) }, style = OrcaButtonStyle.Regular)
                Spacer(Modifier.width(8.dp))
                OrcaButton(orcaString("OK"), onClick = ::confirm)
            }
        }
    }

    if (invalidLayer) {
        AlertDialog(
            onDismissRequest = { invalidLayer = false },
            confirmButton = { OrcaButton(orcaString("OK"), onClick = { invalidLayer = false }) },
            text = { Text(orcaString("Please input layer value (>= 2)."), style = OrcaTheme.typography.body14) },
            containerColor = colors.window,
            textContentColor = colors.text,
            shape = OrcaTheme.shapes.window,
        )
    }
    askingSpiral?.let { chosen ->
        // ConfigManipulation::show_spiral_mode_settings_dialog() for the plate.
        val text = buildString {
            append(orcaString("Spiral mode only works when wall loops is 1, support is disabled, clumping detection by probing is disabled, top shell layers is 0, sparse infill density is 0 and timelapse type is traditional."))
            if (i3) append(orcaString(" But machines with I3 structure will not generate timelapse videos."))
            append("\n\n")
            append(orcaString("Change these settings automatically?\nYes - Change these settings and enable spiral mode automatically\nNo  - Give up using spiral mode this time"))
        }
        AlertDialog(
            onDismissRequest = { askingSpiral = null },
            confirmButton = {
                OrcaButton(orcaString("Yes"), onClick = {
                    askingSpiral = null
                    onConfirm(plateName, chosen, true)
                })
            },
            dismissButton = {
                OrcaButton(orcaString("No"), style = OrcaButtonStyle.Regular, onClick = {
                    askingSpiral = null
                    onConfirm(plateName, chosen, false)
                })
            },
            text = { Text(text, style = OrcaTheme.typography.body14) },
            containerColor = colors.window,
            textContentColor = colors.text,
            shape = OrcaTheme.shapes.window,
        )
    }
}

/** A label over its control, as the dialog's grid lines them up. */
@Composable
private fun SettingRow(label: String, control: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = OrcaTheme.colors.textLabel, style = OrcaTheme.typography.body14)
        control()
    }
}

/** A read-only ComboBox of the dialog. */
@Composable
private fun ChoiceField(options: List<String>, selected: Int, onSelect: (Int) -> Unit, enabled: Boolean = true) {
    var open by remember { mutableStateOf(false) }
    Box {
        OrcaComboField(text = options.getOrElse(selected) { options.first() }, enabled = enabled, onClick = { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = OrcaTheme.colors.window) {
            options.forEachIndexed { index, option ->
                OrcaMenuItem(option, onClick = {
                    open = false
                    onSelect(index)
                })
            }
        }
    }
}

/**
 * OtherLayersSeqPanel's line: "Layer" [begin] "to" [End or a layer], and the
 * order of the filaments on those layers.
 */
@Composable
private fun LayerRangeRow(range: LayerSequence, colors: List<Color>, onChange: (LayerSequence) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(orcaString("Layer"), color = OrcaTheme.colors.text, style = OrcaTheme.typography.body14)
            LayerField(range.begin, Modifier.width(88.dp)) { onChange(range.copy(begin = it)) }
            Text(orcaString("to"), color = OrcaTheme.colors.text, style = OrcaTheme.typography.body14)
            // LayerNumberTextInput: "End" or a layer of the user's own.
            Box(Modifier.width(120.dp)) {
                ChoiceField(
                    options = listOf(orcaString("End"), orcaString("Customize")),
                    selected = if (range.end == LayerSequence.END_LAYER) 0 else 1,
                    onSelect = { onChange(range.copy(end = if (it == 0) LayerSequence.END_LAYER else -1)) },
                )
            }
            if (range.end != LayerSequence.END_LAYER) {
                LayerField(range.end, Modifier.width(88.dp)) { onChange(range.copy(end = it)) }
            }
        }
        FilamentOrder(range.filaments, colors) { onChange(range.copy(filaments = it)) }
    }
}

/**
 * LayerNumberTextInput's text: digits, empty for no layer yet; a layer below
 * MIN_LAYER_VALUE counts as none while it is typed, and takes that value once
 * the field loses focus, as commit_layer_number_from_gui() clamps it.
 */
@Composable
private fun LayerField(layer: Int, modifier: Modifier, onChange: (Int) -> Unit) {
    var text by remember { mutableStateOf(if (layer >= LayerSequence.FIRST_LAYER) layer.toString() else "") }
    OrcaTextField(
        value = text,
        onValueChange = { value ->
            text = value.filter(Char::isDigit).take(9)
            onChange(text.toIntOrNull()?.takeIf { it >= LayerSequence.FIRST_LAYER }?.coerceAtMost(LayerSequence.END_LAYER - 1) ?: -1)
        },
        modifier = modifier.onFocusChanged { focus ->
            val typed = text.toIntOrNull()
            if (!focus.isFocused && typed != null) {
                val clamped = typed.coerceIn(LayerSequence.FIRST_LAYER, LayerSequence.END_LAYER - 1)
                text = clamped.toString()
                onChange(clamped)
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}

/**
 * DragCanvas: the filaments as squares of their colours with their numbers, in
 * the order they print; one is dragged along the row to its place.
 */
@Composable
private fun FilamentOrder(order: List<Int>, colors: List<Color>, onChange: (List<Int>) -> Unit) {
    val step = with(LocalDensity.current) { (SLOT_SIZE + SLOT_GAP).toPx() }
    var dragged by remember { mutableIntStateOf(-1) }
    var offset by remember { mutableFloatStateOf(0f) }
    var current by remember { mutableStateOf(order) }
    LaunchedEffect(order) { if (dragged < 0) current = order }
    val changed by rememberUpdatedState(onChange)
    // One gesture over the row: the square under the finger follows it, and
    // the others make room as it passes their middle.
    Row(
        horizontalArrangement = Arrangement.spacedBy(SLOT_GAP),
        modifier = Modifier.pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { position ->
                    dragged = (position.x / step).toInt().coerceIn(0, current.lastIndex)
                    offset = 0f
                },
                onDragEnd = {
                    val moved = dragged >= 0
                    dragged = -1
                    offset = 0f
                    if (moved) changed(current)
                },
                onDragCancel = {
                    dragged = -1
                    offset = 0f
                },
            ) { change, amount ->
                change.consume()
                val from = dragged
                if (from < 0) return@detectDragGestures
                offset += amount.x
                val to = (from + (offset / step).roundToInt()).coerceIn(0, current.lastIndex)
                if (to != from) {
                    current = current.toMutableList().apply { add(to, removeAt(from)) }
                    offset -= (to - from) * step
                    dragged = to
                }
            }
        },
    ) {
        current.forEachIndexed { index, filament ->
            val color = colors.getOrElse(filament - 1) { OrcaTheme.colors.accent }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .offset { IntOffset(if (index == dragged) offset.roundToInt() else 0, 0) }
                    .size(SLOT_SIZE)
                    .alpha(if (index == dragged) 0.8f else 1f)
                    .background(color, OrcaTheme.shapes.control)
                    .border(1.dp, OrcaTheme.colors.border, OrcaTheme.shapes.control),
            ) {
                Text(
                    text = filament.toString(),
                    color = if (color.luminance() > 0.5f) Color.Black else Color.White,
                    style = OrcaTheme.typography.body14,
                )
            }
        }
    }
}

/** The filaments of a sequence, every one of the plate once, in the order given. */
private fun List<Int>.normalized(count: Int): List<Int> =
    (filter { it in 1..count }.distinct() + (1..count)).distinct()

/** LayerSeqInfo::operator<: by the first layer, then the last, the ranges without layers last. */
private val LAYER_ORDER = Comparator<LayerSequence> { a, b ->
    when {
        a.begin < LayerSequence.FIRST_LAYER && b.begin < LayerSequence.FIRST_LAYER -> 0
        a.begin < LayerSequence.FIRST_LAYER -> 1
        b.begin < LayerSequence.FIRST_LAYER -> -1
        a.begin != b.begin -> a.begin.compareTo(b.begin)
        else -> a.end.compareTo(b.end)
    }
}

/** The print sequences the dialog lists after "Same as Global Print Sequence". */
private val PRINT_SEQUENCES = listOf(null, "by layer", "by object")

private val SLOT_SIZE = 36.dp
private val SLOT_GAP = 8.dp

/** The length PlateSettingsDialog's name field takes. */
private const val PLATE_NAME_LENGTH = 250
