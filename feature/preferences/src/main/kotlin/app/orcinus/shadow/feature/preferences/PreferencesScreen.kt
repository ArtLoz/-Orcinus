package app.orcinus.shadow.feature.preferences

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.orcinus.shadow.core.designsystem.component.OrcaButton
import app.orcinus.shadow.core.designsystem.component.OrcaButtonStyle
import app.orcinus.shadow.core.designsystem.component.OrcaComboBox
import app.orcinus.shadow.core.designsystem.component.OrcaLink
import app.orcinus.shadow.core.designsystem.component.OrcaPageTopBar
import app.orcinus.shadow.core.designsystem.component.OrcaSpinInput
import app.orcinus.shadow.core.designsystem.component.OrcaSwitch
import app.orcinus.shadow.core.designsystem.component.OrcaTextField
import app.orcinus.shadow.core.designsystem.component.OrcaUnderlineTabs
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import app.orcinus.shadow.core.model.AppConfigKeys
import app.orcinus.shadow.core.model.OrcaText
import app.orcinus.shadow.core.ui.orca.orcaString
import app.orcinus.shadow.core.ui.settings.SettingTooltipDialog
import app.orcinus.shadow.core.ui.settings.openInBrowser
import java.util.Locale

@Composable
internal fun PreferencesRoute(viewModel: PreferencesViewModel, onBack: () -> Unit) {
    val values by viewModel.values.collectAsStateWithLifecycle()
    val problem by viewModel.problem.collectAsStateWithLifecycle()
    PreferencesScreen(values = values, problem = problem, onSet = viewModel::set, onDismissProblem = viewModel::dismissProblem, onBack = onBack)
}

/**
 * PreferencesDialog: its tabs along the top, as the desktop dialog lists them
 * down its side, and every item of the tab with the value it holds. An item's
 * label shows its tooltip when tapped, as the desktop label shows it under the
 * pointer; what changes is written at once.
 */
@Composable
internal fun PreferencesScreen(
    values: Map<String, String>,
    problem: String?,
    onSet: (key: String, value: String) -> Unit,
    onDismissProblem: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = OrcaTheme.colors
    var page by rememberSaveable { mutableIntStateOf(0) }
    var tooltip by remember { mutableStateOf<PreferenceItem?>(null) }
    var confirmingMixedTemperatures by remember { mutableStateOf(false) }
    // Nothing can be changed before the engine has read the configuration.
    val enabled = values.isNotEmpty()
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.window),
    ) {
        OrcaPageTopBar(title = orcaString("Preferences"), backDescription = stringResource(R.string.preferences_back), onBack = onBack)
        OrcaUnderlineTabs(titles = PREFERENCE_PAGES.map { orcaString(it.title) }, selectedIndex = page, onSelect = { page = it })
        LazyColumn(Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))) {
            for (section in PREFERENCE_PAGES[page].sections) {
                item(key = "section:${section.title}") { SectionTitle(orcaString(section.title)) }
                items(section.items, key = { it.key }) { item ->
                    PreferenceRow(item, values[item.key].orEmpty(), enabled, onTooltip = { tooltip = item }) { value ->
                        // create_item_checkbox(): turning the restriction off asks first.
                        if (item.key == AppConfigKeys.ENABLE_HIGH_LOW_TEMP_MIXED_PRINTING && value == "true") {
                            confirmingMixedTemperatures = true
                        } else {
                            onSet(item.key, value)
                        }
                    }
                }
            }
        }
    }
    tooltip?.let { item ->
        SettingTooltipDialog(
            label = orcaString(item.title),
            // An item without a tooltip shows its title (create_item_label()).
            tooltip = listOf(OrcaText(item.tooltip.ifEmpty { item.title })),
            onDismiss = { tooltip = null },
        )
    }
    if (confirmingMixedTemperatures) {
        MixedTemperatureDialog(
            onEnable = {
                confirmingMixedTemperatures = false
                onSet(AppConfigKeys.ENABLE_HIGH_LOW_TEMP_MIXED_PRINTING, "true")
            },
            onDismiss = { confirmingMixedTemperatures = false },
        )
    }
    problem?.let { message ->
        AlertDialog(
            onDismissRequest = onDismissProblem,
            confirmButton = { OrcaButton(orcaString("OK"), onClick = onDismissProblem) },
            text = { Text(message, style = OrcaTheme.typography.body14) },
            containerColor = colors.window,
            textContentColor = colors.text,
            shape = OrcaTheme.shapes.window,
        )
    }
}

/** create_item_title(): the heading of a group of items. */
@Composable
private fun SectionTitle(title: String) {
    val colors = OrcaTheme.colors
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = title,
            color = colors.text,
            style = OrcaTheme.typography.head14,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp),
        )
        HorizontalDivider(color = colors.separator, thickness = 1.dp)
    }
}

/** An item: its label, which shows the tooltip, and its control. */
@Composable
private fun PreferenceRow(item: PreferenceItem, value: String, enabled: Boolean, onTooltip: () -> Unit, onChange: (String) -> Unit) {
    val colors = OrcaTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = orcaString(item.title),
            color = colors.text,
            style = OrcaTheme.typography.body14,
            modifier = Modifier
                .weight(1f)
                .clickable(role = Role.Button, onClick = onTooltip)
                .padding(vertical = 8.dp),
        )
        Spacer(Modifier.width(12.dp))
        when (item) {
            is PreferenceItem.Check -> OrcaSwitch(
                checked = AppConfigKeys.bool(value),
                onCheckedChange = { onChange(if (it) "true" else "false") },
                enabled = enabled,
            )
            is PreferenceItem.Choice -> {
                val labels = item.labels.map { orcaString(it) }
                OrcaComboBox(
                    items = labels.indices.toList(),
                    selected = item.selected(value),
                    label = { labels[it] },
                    onSelect = { onChange(item.valueOf(it)) },
                    enabled = enabled,
                    modifier = Modifier.width(CONTROL_WIDTH),
                )
            }
            is PreferenceItem.Spin -> OrcaSpinInput(
                // stoi() of the value, which SpinInput shows within its range.
                value = (value.toIntOrNull() ?: 0).coerceIn(item.range),
                onValueChange = { onChange(it.toString()) },
                range = item.range,
                decreaseDescription = stringResource(R.string.preferences_decrease),
                increaseDescription = stringResource(R.string.preferences_increase),
                unit = orcaString(item.unit),
                enabled = enabled,
                modifier = Modifier.width(CONTROL_WIDTH),
            )
            is PreferenceItem.Decimal -> DecimalField(item, value, enabled, onChange)
        }
    }
}

/**
 * create_camera_orbit_mult_input(): a number the field writes when it is done
 * or loses the focus, kept within the item's range with two decimals; text
 * that is not a number writes nothing.
 */
@Composable
private fun DecimalField(item: PreferenceItem.Decimal, value: String, enabled: Boolean, onChange: (String) -> Unit) {
    val focusManager = LocalFocusManager.current
    var text by remember(value) { mutableStateOf(value) }
    var focused by remember { mutableStateOf(false) }
    fun commit() {
        val number = text.trim().toDoubleOrNull() ?: return
        onChange(String.format(Locale.ROOT, "%.2f", number.coerceIn(item.min, item.max)))
    }
    OrcaTextField(
        value = text,
        onValueChange = { text = it },
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        modifier = Modifier
            .width(CONTROL_WIDTH)
            .onFocusChanged { state ->
                if (focused && !state.isFocused) commit()
                focused = state.isFocused
            },
    )
}

/**
 * create_item_checkbox() for "Remove mixed temperature restriction": the
 * warning, with its link to Bambu Lab's wiki; only Yes turns the restriction off.
 */
@Composable
private fun MixedTemperatureDialog(onEnable: () -> Unit, onDismiss: () -> Unit) {
    val colors = OrcaTheme.colors
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { OrcaButton(orcaString("Yes"), onClick = onEnable) },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OrcaButton(orcaString("No"), onClick = onDismiss, style = OrcaButtonStyle.Regular)
                OrcaButton(orcaString("Cancel"), onClick = onDismiss, style = OrcaButtonStyle.Regular)
            }
        },
        title = { Text(orcaString("Bed Temperature Difference Warning"), style = OrcaTheme.typography.head16) },
        text = {
            Column {
                Text(
                    orcaString(
                        "Using filaments with significantly different temperatures may cause:\n" +
                            "• Extruder clogging\n" +
                            "• Nozzle damage\n" +
                            "• Layer adhesion issues\n\n" +
                            "Continue with enabling this feature?",
                    ),
                    style = OrcaTheme.typography.body14,
                )
                // The wiki in Chinese for a Chinese app, in English otherwise.
                OrcaLink(orcaString("Click Wiki for help."), onClick = { openInBrowser(context, MIXED_TEMPERATURE_WIKI) })
            }
        },
        containerColor = colors.window,
        titleContentColor = colors.text,
        textContentColor = colors.text,
        shape = OrcaTheme.shapes.window,
    )
}

private val CONTROL_WIDTH = 150.dp
private const val MIXED_TEMPERATURE_WIKI = "https://wiki.bambulab.com/en/filament-acc/filament/h2d-filament-config-limit"
