package app.orcinus.shadow.feature.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.orcinus.shadow.core.designsystem.component.OrcaButton
import app.orcinus.shadow.core.designsystem.component.OrcaButtonStyle
import app.orcinus.shadow.core.designsystem.component.OrcaFilamentSlot
import app.orcinus.shadow.core.designsystem.component.OrcaSheetHandle
import app.orcinus.shadow.core.designsystem.component.OrcaTextField
import app.orcinus.shadow.core.designsystem.component.orcaClickable
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import app.orcinus.shadow.core.model.LayerGcode
import app.orcinus.shadow.core.model.LayerGcodeRules
import app.orcinus.shadow.core.model.LayerGcodeType
import app.orcinus.shadow.core.ui.orca.orcaString
import java.util.Locale

/** What the layer slider's menu does to the layer at a height. */
internal class LayerGcodeActions(
    val addPause: (printZ: Double) -> Unit,
    val addTemplate: (printZ: Double) -> Unit,
    val changeFilament: (printZ: Double, filament: Int) -> Unit,
    val setCustom: (printZ: Double, gcode: String) -> Unit,
    val delete: (printZ: Double) -> Unit,
) {
    companion object {
        val NONE = LayerGcodeActions({}, {}, { _, _ -> }, { _, _ -> }, {})
    }
}

/** IMSlider::draw_custom_label_block(): the word a code shows on the slider. */
@Composable
internal fun layerGcodeLabel(type: LayerGcodeType): String = orcaString(
    when (type) {
        LayerGcodeType.PAUSE_PRINT -> "Pause"
        LayerGcodeType.TEMPLATE -> "Template"
        LayerGcodeType.CUSTOM -> "Custom"
        LayerGcodeType.COLOR_CHANGE, LayerGcodeType.TOOL_CHANGE -> "Color"
    },
)

/**
 * IMSlider::render_add_menu() and render_edit_menu() for the layer at
 * [printZ], which the desktop slider opens with a right click on its handle;
 * a phone lists them in a sheet. A layer without a code offers a pause, G-code
 * of the user's own, the printer's template, a jump to another layer and, with
 * several filaments, a filament change; a layer with one offers to change or
 * delete it. A print by object offers nothing but the jump.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LayerGcodeSheet(
    layerNumber: Int,
    printZ: Double,
    code: LayerGcode?,
    rules: LayerGcodeRules,
    filamentColors: List<Color>,
    actions: LayerGcodeActions,
    onEditCustom: () -> Unit,
    onJumpToLayer: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OrcaTheme.colors
    val enabled = !rules.sequential
    fun run(action: () -> Unit): () -> Unit = {
        onDismiss()
        action()
    }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.window, dragHandle = { OrcaSheetHandle() }) {
        Column(Modifier.navigationBarsPadding()) {
            Text(
                text = String.format(Locale.ROOT, "%d (%.2f)", layerNumber, printZ),
                color = colors.text,
                style = OrcaTheme.typography.head16,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            when (code?.type) {
                null -> {
                    SheetItem(orcaString("Add Pause"), orcaString("Insert a pause command at the beginning of this layer."), enabled, run { actions.addPause(printZ) })
                    SheetItem(orcaString("Add Custom G-code"), orcaString("Insert custom G-code at the beginning of this layer."), enabled, run(onEditCustom))
                    if (rules.hasTemplate) {
                        SheetItem(
                            orcaString("Add Custom Template"),
                            orcaString("Insert template custom G-code at the beginning of this layer."),
                            enabled,
                            run { actions.addTemplate(printZ) },
                        )
                    }
                    SheetItem(orcaString("Jump to Layer"), null, true, run(onJumpToLayer))
                    FilamentItems(filamentColors, enabled && rules.canChangeFilament, current = null) { filament -> onDismiss(); actions.changeFilament(printZ, filament) }
                }
                LayerGcodeType.PAUSE_PRINT -> SheetItem(orcaString("Delete Pause"), null, true, run { actions.delete(printZ) })
                LayerGcodeType.TEMPLATE -> if (rules.hasTemplate) SheetItem(orcaString("Delete Custom Template"), null, true, run { actions.delete(printZ) })
                LayerGcodeType.CUSTOM -> {
                    SheetItem(orcaString("Edit Custom G-code"), null, true, run(onEditCustom))
                    SheetItem(orcaString("Delete Custom G-code"), null, true, run { actions.delete(printZ) })
                }
                LayerGcodeType.TOOL_CHANGE -> if (filamentColors.size > 1) {
                    FilamentItems(filamentColors, rules.canChangeFilament, current = code.extruder) { filament -> onDismiss(); actions.changeFilament(printZ, filament) }
                    SheetItem(orcaString("Delete Filament Change"), null, true, run { actions.delete(printZ) })
                }
                LayerGcodeType.COLOR_CHANGE -> Unit
            }
        }
    }
}

/** The submenu "Change Filament": every filament of the plate with its colour. */
@Composable
private fun FilamentItems(filamentColors: List<Color>, enabled: Boolean, current: Int?, onPick: (Int) -> Unit) {
    if (filamentColors.size <= 1) return
    Text(
        text = orcaString("Change Filament"),
        color = if (enabled) OrcaTheme.colors.textSide else OrcaTheme.colors.textDisabled,
        style = OrcaTheme.typography.body13,
        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
    )
    filamentColors.forEachIndexed { index, color ->
        val filament = index + 1
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .orcaClickable(enabled = enabled, role = Role.Button, onClick = { onPick(filament) })
                .heightIn(min = OrcaTheme.dimensions.minimumTouchTarget)
                .padding(horizontal = 32.dp, vertical = 8.dp),
        ) {
            OrcaFilamentSlot(number = filament, color = color, modifier = Modifier.size(24.dp))
            Text(
                text = orcaString("Filament ") + filament,
                color = if (enabled) OrcaTheme.colors.text else OrcaTheme.colors.textDisabled,
                style = if (filament == current) OrcaTheme.typography.head14 else OrcaTheme.typography.body14,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

@Composable
private fun SheetItem(text: String, hint: String?, enabled: Boolean, onClick: () -> Unit) {
    val colors = OrcaTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .orcaClickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = OrcaTheme.dimensions.minimumTouchTarget)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(text, color = if (enabled) colors.text else colors.textDisabled, style = OrcaTheme.typography.body14)
        // The tooltip of the desktop item, which a finger cannot hover.
        if (hint != null) Text(hint, color = colors.textSide, style = OrcaTheme.typography.body12)
    }
}

/**
 * IMSlider::render_input_custom_gcode(): the G-code the layer starts with,
 * edited in a multi-line field.
 */
@Composable
internal fun CustomGcodeDialog(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val colors = OrcaTheme.colors
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { OrcaButton(orcaString("OK"), onClick = { onConfirm(text) }, enabled = text.isNotBlank()) },
        dismissButton = { OrcaButton(orcaString("Cancel"), onClick = onDismiss, style = OrcaButtonStyle.Regular) },
        title = { Text(orcaString("Custom G-code"), style = OrcaTheme.typography.head16) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(orcaString("Enter Custom G-code used on current layer:"), style = OrcaTheme.typography.body14)
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = OrcaTheme.typography.body13.copy(color = colors.text, fontFamily = FontFamily.Monospace),
                    cursorBrush = SolidColor(colors.accent),
                    minLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .background(colors.window, OrcaTheme.shapes.control)
                        .border(1.dp, colors.border, OrcaTheme.shapes.control)
                        .padding(8.dp),
                )
            }
        },
        containerColor = colors.window,
        titleContentColor = colors.text,
        textContentColor = colors.text,
        shape = OrcaTheme.shapes.window,
    )
}

/** IMSlider::render_go_to_layer_dialog(): the layer number, from 1 to [layerCount]. */
@Composable
internal fun JumpToLayerDialog(layerCount: Int, onDismiss: () -> Unit, onJump: (layer: Int) -> Unit) {
    val colors = OrcaTheme.colors
    var text by rememberSaveable { mutableStateOf("") }
    val number = text.toIntOrNull()?.takeIf { it in 1..layerCount }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { OrcaButton(orcaString("OK"), onClick = { number?.let { onJump(it - 1) } }, enabled = number != null) },
        dismissButton = { OrcaButton(orcaString("Cancel"), onClick = onDismiss, style = OrcaButtonStyle.Regular) },
        title = { Text(orcaString("Jump to Layer"), style = OrcaTheme.typography.head16) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(orcaString("Please enter the layer number") + " (1 - $layerCount):", style = OrcaTheme.typography.body14)
                OrcaTextField(
                    value = text,
                    onValueChange = { value -> text = value.filter(Char::isDigit).take(6) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { number?.let { onJump(it - 1) } }),
                )
            }
        },
        containerColor = colors.window,
        titleContentColor = colors.text,
        textContentColor = colors.text,
        shape = OrcaTheme.shapes.window,
    )
}
