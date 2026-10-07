package app.orcinus.shadow.core.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.orcinus.shadow.core.designsystem.R as DesignR
import app.orcinus.shadow.core.designsystem.component.OrcaButton
import app.orcinus.shadow.core.designsystem.component.OrcaCheckBox
import app.orcinus.shadow.core.designsystem.component.OrcaIconButton
import app.orcinus.shadow.core.designsystem.component.OrcaParameterGroupHeader
import app.orcinus.shadow.core.designsystem.component.OrcaSwitch
import app.orcinus.shadow.core.designsystem.component.OrcaTextField
import app.orcinus.shadow.core.designsystem.component.OrcaUnderlineTabs
import app.orcinus.shadow.core.designsystem.icon.orcaIcon
import app.orcinus.shadow.core.designsystem.component.orcaClickable
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import app.orcinus.shadow.core.model.SettingChoice
import app.orcinus.shadow.core.model.SettingDefinition
import app.orcinus.shadow.core.model.SettingState
import app.orcinus.shadow.core.model.SettingType
import app.orcinus.shadow.core.model.SettingWidget
import app.orcinus.shadow.core.model.SettingsLine
import app.orcinus.shadow.core.model.SettingsLineOption
import app.orcinus.shadow.core.model.SettingsPage
import app.orcinus.shadow.core.model.SettingsRequest
import app.orcinus.shadow.core.ui.R
import app.orcinus.shadow.core.ui.orca.orcaString
import app.orcinus.shadow.core.ui.orca.orcaText

/**
 * The row of the tab's pages; the engine toggles the fields of the chosen one.
 * A page with a modified value, or an override on the tab of an object, is
 * named in the colour of a modified value (Tab::update_changed_tree_ui()).
 */
@Composable
fun SettingsPageTabs(view: SettingsView, page: SettingsPage, onRequest: (SettingsRequest) -> Unit) {
    val pages = view.visiblePages
    OrcaUnderlineTabs(
        titles = pages.map { orcaText(it.label) },
        selectedIndex = pages.indexOf(page).coerceAtLeast(0),
        onSelect = { onRequest(SettingsRequest.SelectPage(pages[it].title)) },
        modified = pages.map(SettingsPage::modified),
    )
}

/**
 * The option groups of one page as rows of the screen's list: every group's
 * header and the lines it shows, as ConfigManipulation toggles them. A label
 * tells whether its value is the parent preset's, saved, or modified, and shows
 * the setting's tooltip when tapped; a modified value can be undone. The engine
 * applies every change, as the desktop app does.
 */
internal fun LazyListScope.settingsPageItems(ui: SettingsTabUi, view: SettingsView, page: SettingsPage) {
    for (group in page.groups.filter(view::isVisible)) {
        if (group.title.isNotEmpty()) {
            item(key = "group:${page.title}:${group.title}") {
                OrcaParameterGroupHeader(
                    title = orcaString(group.title),
                    icon = orcaIcon(group.icon) ?: DesignR.drawable.orca_param_advanced,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        val lines = group.lines.filter(view::isVisible)
        itemsIndexed(lines, key = { index, line -> "line:${page.title}:${group.title}:$index:${line.options.firstOrNull()?.id.orEmpty()}" }) { _, line ->
            // The setting the search jumped to, while its mark is blinking.
            val marked = ui.highlightShown && line.options.any { it.id == ui.highlighted }
            Box(if (marked) Modifier.background(OrcaTheme.colors.accentSelected) else Modifier) {
                SettingsLineRow(
                    view, line, ui.enabled, ui.state.changing, ui.request, ui.showTooltip, ui.chooseCompatible, ui.openBedShape, ui.editCustomGcode,
                    ui.editRamming,
                )
            }
        }
    }
}

@Composable
private fun SettingsLineRow(
    view: SettingsView,
    line: SettingsLine,
    enabled: Boolean,
    changing: Boolean,
    onRequest: (SettingsRequest) -> Unit,
    onTooltip: (id: String, label: String, wikiUrl: String?) -> Unit,
    onCompatible: (key: String, selected: List<String>) -> Unit,
    onBedShape: () -> Unit,
    onEditGcode: (key: String) -> Unit,
    onRamming: (parameters: String) -> Unit,
) {
    val colors = OrcaTheme.colors
    if (line.separator) {
        HorizontalDivider(color = colors.separator, thickness = 1.dp, modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 4.dp, bottom = 4.dp))
        return
    }
    if (line.widget == SettingWidget.BED_SHAPE) {
        // OptionsGroup::append_line(): a line with a widget shows the widget
        // and none of its options, so the printable area is the dialog's
        // button alone (TabPrinter::create_bed_shape_widget).
        WidgetLine(line.label.ifEmpty { line.options.firstOrNull()?.label.orEmpty() }, enabled, onBedShape)
        return
    }
    if (line.widget == SettingWidget.RAMMING) {
        // TabFilament::build(): the "Set ..." button of RammingDialog, which
        // opens with the first value of filament_ramming_parameters.
        val option = line.options.firstOrNull()
        val parameters = option?.let(view::state)?.value.orEmpty()
        val label = line.label.ifEmpty { option?.let(view::definition)?.label ?: option?.label.orEmpty() }
        WidgetLine(label, enabled) { onRamming(parameters) }
        return
    }
    Column {
    val lineLabel = line.label.takeIf { it.isNotEmpty() && line.options.size > 1 }
    lineLabel?.let {
        // A line of several options: its label, then each option under it with its own label.
        Text(
            text = orcaString(it),
            color = colors.textLabel,
            style = OrcaTheme.typography.body14,
            modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 8.dp),
        )
    }
    for (option in line.options) {
        val definition = view.definition(option) ?: continue
        val state = view.state(option) ?: continue
        SettingOptionRow(
            view = view,
            line = line,
            definition = definition,
            option = option,
            state = state,
            label = if (line.options.size == 1) line.label.ifEmpty { view.label(definition, option) } else view.label(definition, option),
            indent = if (lineLabel != null) 36.dp else 24.dp,
            enabled = enabled,
            changing = changing,
            onRequest = onRequest,
            onTooltip = onTooltip,
            onCompatible = onCompatible,
            onEditGcode = onEditGcode,
        )
    }
    }
}

@Composable
private fun SettingOptionRow(
    view: SettingsView,
    line: SettingsLine,
    definition: SettingDefinition,
    option: SettingsLineOption,
    state: SettingState,
    label: String,
    indent: Dp,
    enabled: Boolean,
    changing: Boolean,
    onRequest: (SettingsRequest) -> Unit,
    onTooltip: (id: String, label: String, wikiUrl: String?) -> Unit,
    onCompatible: (key: String, selected: List<String>) -> Unit,
    onEditGcode: (key: String) -> Unit,
) {
    val colors = OrcaTheme.colors
    val text = orcaString(label)
    val labelColor = when (view.labelColor(state)) {
        SettingLabelColor.MODIFIED -> colors.labelModified
        SettingLabelColor.SYSTEM -> colors.labelSystem
        SettingLabelColor.DEFAULT -> colors.labelDefault
    }
    val kind = SettingsView.fieldKind(definition, state)
    // A filament override is edited once its check box is on; the field
    // otherwise shows the value it would override.
    val fieldEnabled = enabled && state.enabled && !(line.hasOverride && state.isNil)
    val commit: (String) -> Unit = { value -> onRequest(SettingsRequest.Change(state.id, value)) }
    val fullWidth = SettingsView.isFullWidth(definition, option, kind)

    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .padding(start = indent, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (line.hasOverride) {
                // The check box before the label switches the override on (Tab::create_near_label_widget).
                val description = stringResource(R.string.setting_override, text)
                OrcaCheckBox(
                    checked = !state.isNil,
                    onCheckedChange = { onRequest(SettingsRequest.SetOverride(state.id, it)) },
                    enabled = enabled && state.overrideEnabled,
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .semantics { contentDescription = description },
                )
            }
            Text(
                text = text,
                color = labelColor,
                style = OrcaTheme.typography.body14,
                modifier = Modifier
                    .weight(1f)
                    .orcaClickable(role = Role.Button, onClickLabel = stringResource(R.string.setting_tooltip)) { onTooltip(state.id, text, line.wikiUrl) }
                    .padding(vertical = 6.dp),
            )
            if (!fullWidth) {
                Box(Modifier.width(140.dp), contentAlignment = Alignment.CenterEnd) {
                    SettingField(view, definition, option, state, line, kind, fieldEnabled, changing, commit, onCompatible)
                }
            }
            // The undo button of a modified value (OG_CustomCtrl draws a blank bullet otherwise).
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                if (state.modified) {
                    OrcaIconButton(
                        icon = DesignR.drawable.orca_undo,
                        contentDescription = stringResource(R.string.setting_undo, text),
                        onClick = { onRequest(SettingsRequest.Reset(listOf(state.id))) },
                        enabled = enabled,
                    )
                }
            }
            // The edit button OG_CustomCtrl draws after the undo buttons of a
            // custom G-code (Field::has_edit_ui): it opens EditGCodeDialog.
            if (option.editCustomGcode) {
                OrcaIconButton(
                    icon = DesignR.drawable.orca_edit,
                    contentDescription = orcaString("Edit Custom G-code"),
                    onClick = { onEditGcode(state.key) },
                    enabled = enabled,
                )
            }
        }
        if (fullWidth) {
            Box(Modifier.padding(start = indent, end = 12.dp, bottom = 6.dp)) {
                SettingField(view, definition, option, state, line, kind, fieldEnabled, changing, commit, onCompatible)
            }
        }
    }
}

@Composable
private fun SettingField(
    view: SettingsView,
    definition: SettingDefinition,
    option: SettingsLineOption,
    state: SettingState,
    line: SettingsLine,
    kind: SettingFieldKind,
    enabled: Boolean,
    changing: Boolean,
    onCommit: (String) -> Unit,
    onCompatible: (key: String, selected: List<String>) -> Unit,
) {
    if (line.widget == SettingWidget.COMPATIBLE_PRINTERS || line.widget == SettingWidget.COMPATIBLE_PRINTS) {
        CompatiblePresetsField(state, enabled) { onCompatible(state.key, state.listValues) }
        return
    }
    when (kind) {
        // TabPrintModel: a value the selected objects disagree on is half checked.
        SettingFieldKind.CHECK_BOX -> OrcaSwitch(
            checked = state.value == "1",
            onCheckedChange = { onCommit(if (it) "1" else "0") },
            enabled = enabled,
            mixed = state.mixed,
        )
        SettingFieldKind.CHOICE -> ChoiceField(view.choices(definition, state), state.value, enabled, onCommit)
        SettingFieldKind.OPEN_CHOICE -> CommitTextField(
            value = state.value,
            onCommit = onCommit,
            enabled = enabled,
            changing = changing,
            unit = orcaString(definition.sidetext).takeIf(String::isNotEmpty),
            keyboardType = KeyboardType.Decimal,
            choices = view.choices(definition, state),
        )
        SettingFieldKind.NUMBER -> CommitTextField(
            value = state.value,
            onCommit = onCommit,
            enabled = enabled,
            changing = changing,
            unit = orcaString(definition.sidetext).takeIf(String::isNotEmpty),
            // A value that may be a percentage needs the % sign.
            keyboardType = when (definition.type) {
                SettingType.FLOAT_OR_PERCENT, SettingType.FLOATS_OR_PERCENTS -> KeyboardType.Ascii
                SettingType.INT, SettingType.INTS -> KeyboardType.Number
                else -> KeyboardType.Decimal
            },
        )
        SettingFieldKind.TEXT -> if (SettingsView.isMultiline(definition, option)) {
            MultilineTextField(
                value = state.value,
                onCommit = onCommit,
                enabled = enabled,
                changing = changing,
                code = SettingsView.isCode(definition, option),
                lines = (option.height.takeIf { it > 0 } ?: definition.height).takeIf { it > 0 },
            )
        } else {
            CommitTextField(state.value, onCommit, enabled, changing, unit = null, keyboardType = KeyboardType.Text)
        }
        // PointCtrl edits one point, as of each extruder ("extruder_offset#0");
        // a list of them is edited as the text the desktop app shows for it
        // ("0x0, 350x0, ...").
        SettingFieldKind.POINT -> if (definition.type == SettingType.POINT || '#' in state.id) {
            PointField(state.value, enabled, changing, onCommit)
        } else {
            CommitTextField(state.value, onCommit, enabled, changing, unit = null, keyboardType = KeyboardType.Ascii)
        }
        SettingFieldKind.COLOR -> ColorField(state.value, enabled, onCommit)
        SettingFieldKind.LEGEND -> Text(state.value, color = OrcaTheme.colors.text, style = OrcaTheme.typography.body13)
        SettingFieldKind.UNSUPPORTED -> Text(state.value, color = OrcaTheme.colors.textDisabled, style = OrcaTheme.typography.body13)
    }
}

/** A combo box of fixed entries (Choice). */
@Composable
private fun ChoiceField(
    choices: List<SettingChoice>,
    value: String,
    enabled: Boolean,
    onCommit: (String) -> Unit,
) {
    val colors = OrcaTheme.colors
    var expanded by remember { mutableStateOf(false) }
    val selected = choices.firstOrNull { it.value == value }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = OrcaTheme.dimensions.parameterControlHeight)
                .background(if (enabled) colors.window else colors.controlDisabledBackground, OrcaTheme.shapes.control)
                .border(1.dp, colors.border, OrcaTheme.shapes.control)
                .orcaClickable(enabled = enabled, role = Role.DropdownList) { expanded = true }
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = selected?.let { orcaString(it.label) } ?: value,
                color = if (enabled) colors.text else colors.textDisabled,
                style = OrcaTheme.typography.body13,
                maxLines = 2,
                modifier = Modifier.weight(1f),
            )
            Icon(
                painterResource(DesignR.drawable.orca_drop_down),
                contentDescription = null,
                tint = colors.textSide,
                modifier = Modifier.size(OrcaTheme.dimensions.iconSmall),
            )
        }
        ChoiceMenu(choices, value, expanded, { expanded = false }, onCommit)
    }
}

@Composable
private fun ChoiceMenu(
    choices: List<SettingChoice>,
    value: String,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onCommit: (String) -> Unit,
) {
    val colors = OrcaTheme.colors
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss, containerColor = colors.window, shape = OrcaTheme.shapes.control) {
        choices.forEach { choice ->
            DropdownMenuItem(
                text = { Text(orcaString(choice.label), color = colors.text, style = OrcaTheme.typography.body14) },
                onClick = {
                    onDismiss()
                    if (choice.value != value) onCommit(choice.value)
                },
                modifier = if (choice.value == value) Modifier.background(colors.accentSelected) else Modifier,
            )
        }
    }
}

/**
 * A text field that gives its text to the engine when editing ends: the IME
 * action, or the focus moving away. Until then it keeps what the user typed;
 * otherwise it shows the setting's value. With [choices], the arrow at the end
 * of the field opens the values of an open combo box.
 */
@Composable
private fun CommitTextField(
    value: String,
    onCommit: (String) -> Unit,
    enabled: Boolean,
    changing: Boolean,
    unit: String?,
    keyboardType: KeyboardType,
    choices: List<SettingChoice> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val colors = OrcaTheme.colors
    val focusManager = LocalFocusManager.current
    var text by remember { mutableStateOf(value) }
    var focused by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(value, changing, focused) {
        if (!focused && !changing) text = value
    }
    Box(modifier) {
        OrcaTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged {
                    if (focused && !it.isFocused && text != value) onCommit(text)
                    focused = it.isFocused
                },
            unit = unit,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            trailing = if (choices.isEmpty()) null else {
                {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .orcaClickable(enabled = enabled, role = Role.DropdownList) { expanded = true }
                            .padding(horizontal = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painterResource(DesignR.drawable.orca_drop_down),
                            contentDescription = stringResource(R.string.setting_values),
                            tint = if (enabled) colors.textSide else colors.textDisabledOnBox,
                            modifier = Modifier.size(OrcaTheme.dimensions.iconSmall),
                        )
                    }
                }
            },
        )
        // A value picked from the list is the field's text too, so the focus
        // leaving the field does not send back what it showed before.
        ChoiceMenu(choices, value, expanded, { expanded = false }) { picked ->
            text = picked
            onCommit(picked)
        }
    }
}

/** A text field of several lines, for G-code and notes; the text goes to the engine when the focus leaves. */
@Composable
private fun MultilineTextField(
    value: String,
    onCommit: (String) -> Unit,
    enabled: Boolean,
    changing: Boolean,
    code: Boolean,
    lines: Int?,
) {
    val colors = OrcaTheme.colors
    var text by remember { mutableStateOf(value) }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(value, changing, focused) {
        if (!focused && !changing) text = value
    }
    BasicTextField(
        value = text,
        onValueChange = { text = it },
        enabled = enabled,
        textStyle = OrcaTheme.typography.body13.copy(
            color = if (enabled) colors.text else colors.textDisabled,
            fontFamily = if (code) FontFamily.Monospace else FontFamily.Default,
        ),
        cursorBrush = SolidColor(colors.accent),
        minLines = ((lines ?: 5) / 3).coerceIn(2, 8),
        modifier = Modifier
            .fillMaxWidth()
            .background(if (enabled) colors.window else colors.controlDisabledBackground, OrcaTheme.shapes.control)
            .border(1.dp, if (focused) colors.accent else colors.border, OrcaTheme.shapes.control)
            .padding(8.dp)
            .onFocusChanged {
                if (focused && !it.isFocused && text != value) onCommit(text)
                focused = it.isFocused
            },
    )
}

/** PointCtrl: the x and y of a point, which the engine reads as "XxY". */
@Composable
private fun PointField(value: String, enabled: Boolean, changing: Boolean, onCommit: (String) -> Unit) {
    val colors = OrcaTheme.colors
    val coordinates = value.split('x', limit = 2)
    val x = coordinates.getOrElse(0) { "" }.trim()
    val y = coordinates.getOrElse(1) { "" }.trim()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.setting_point_x), color = colors.textSide, style = OrcaTheme.typography.body13)
        CommitTextField(
            value = x,
            onCommit = { onCommit("${it}x$y") },
            enabled = enabled,
            changing = changing,
            unit = null,
            keyboardType = KeyboardType.Decimal,
            modifier = Modifier.weight(1f),
        )
        Text(stringResource(R.string.setting_point_y), color = colors.textSide, style = OrcaTheme.typography.body13)
        CommitTextField(
            value = y,
            onCommit = { onCommit("${x}x$it") },
            enabled = enabled,
            changing = changing,
            unit = null,
            keyboardType = KeyboardType.Decimal,
            modifier = Modifier.weight(1f),
        )
    }
}

/** ColourPicker: the colour of the setting, which the engine reads as "#RRGGBB". */
@Composable
private fun ColorField(value: String, enabled: Boolean, onCommit: (String) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    val colors = OrcaTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = OrcaTheme.dimensions.parameterControlHeight)
            .background(parseColor(value) ?: colors.window, OrcaTheme.shapes.control)
            .border(1.dp, colors.border, OrcaTheme.shapes.control)
            .orcaClickable(enabled = enabled, role = Role.Button, onClickLabel = stringResource(R.string.setting_color)) { picking = true },
    )
    if (picking) {
        SettingColorDialog(
            value = value,
            onPick = {
                picking = false
                if (it != value) onCommit(it)
            },
            onDismiss = { picking = false },
        )
    }
}

/**
 * A line the tab shows a widget in (Tab::create_line_with_widget): its label
 * and the "Set ..." button of its dialog, as the printable area
 * (TabPrinter::create_bed_shape_widget) and the ramming parameters have.
 */
@Composable
private fun WidgetLine(label: String, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .padding(start = 24.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = orcaString(label),
            color = OrcaTheme.colors.labelDefault,
            style = OrcaTheme.typography.body14,
            modifier = Modifier.weight(1f),
        )
        OrcaButton(text = orcaString("Set") + " ...", onClick = onClick, enabled = enabled)
    }
}

/** The presets the edited preset is compatible with; every preset when the list is empty. */
@Composable
private fun CompatiblePresetsField(state: SettingState, enabled: Boolean, onChoose: () -> Unit) {
    val colors = OrcaTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = OrcaTheme.dimensions.parameterControlHeight)
            .background(if (enabled) colors.window else colors.controlDisabledBackground, OrcaTheme.shapes.control)
            .border(1.dp, colors.border, OrcaTheme.shapes.control)
            .orcaClickable(enabled = enabled, role = Role.Button, onClickLabel = stringResource(R.string.setting_compatible_choose)) { onChoose() }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (state.listValues.isEmpty()) {
                stringResource(R.string.setting_compatible_all)
            } else {
                stringResource(R.string.setting_compatible_count, state.listValues.size)
            },
            color = if (enabled) colors.text else colors.textDisabled,
            style = OrcaTheme.typography.body13,
            modifier = Modifier.weight(1f),
        )
        Icon(
            painterResource(DesignR.drawable.orca_drop_down),
            contentDescription = null,
            tint = colors.textSide,
            modifier = Modifier.size(OrcaTheme.dimensions.iconSmall),
        )
    }
}

/** "#RRGGBB" as OrcaSlicer writes a colour; null when the value is not one. */
internal fun parseColor(value: String): Color? {
    val hex = value.removePrefix("#")
    if (hex.length < 6) return null
    val rgb = hex.take(6).toLongOrNull(radix = 16) ?: return null
    return Color(0xFF000000L or rgb)
}
