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
import androidx.compose.ui.platform.LocalConfiguration
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
import app.orcinus.shadow.core.designsystem.layout.OrcaCentredContent
import app.orcinus.shadow.core.designsystem.layout.OrcaListDetailPanes
import app.orcinus.shadow.core.designsystem.layout.OrcaPageList
import app.orcinus.shadow.core.designsystem.layout.OrcaPageWidth
import app.orcinus.shadow.core.designsystem.layout.OrcaWindowLayout
import app.orcinus.shadow.core.designsystem.layout.currentOrcaWindowLayout
import app.orcinus.shadow.core.designsystem.layout.orcaContentWidth
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import app.orcinus.shadow.core.model.AppConfigKeys
import app.orcinus.shadow.core.model.OrcaText
import app.orcinus.shadow.core.ui.orca.ORCA_LANGUAGES
import app.orcinus.shadow.core.ui.orca.OrcaLanguage
import app.orcinus.shadow.core.ui.orca.orcaLanguageOf
import app.orcinus.shadow.core.ui.orca.orcaString
import app.orcinus.shadow.core.ui.settings.SettingTooltipDialog
import app.orcinus.shadow.core.ui.settings.openInBrowser
import java.util.Locale

@Composable
internal fun PreferencesRoute(viewModel: PreferencesViewModel, onBack: () -> Unit, onOpenNetworkTest: () -> Unit = {}) {
    val values by viewModel.values.collectAsStateWithLifecycle()
    val problem by viewModel.problem.collectAsStateWithLifecycle()
    PreferencesScreen(
        values = values,
        problem = problem,
        onSet = viewModel::set,
        onSelectLanguage = viewModel::selectLanguage,
        onDismissProblem = viewModel::dismissProblem,
        onBack = onBack,
        onOpenNetworkTest = onOpenNetworkTest,
    )
}

/**
 * PreferencesDialog: its tabs along the top, or down the side on a large
 * window, and every item of the tab with the value it holds. An item's
 * label shows its tooltip when tapped, as the desktop label shows it under the
 * pointer; what changes is written at once.
 */
@Composable
internal fun PreferencesScreen(
    values: Map<String, String>,
    problem: String?,
    onSet: (key: String, value: String) -> Unit,
    onSelectLanguage: (OrcaLanguage) -> Unit,
    onDismissProblem: () -> Unit,
    onBack: () -> Unit,
    /** "Network test"'s "Test...": NetworkTestDialog. */
    onOpenNetworkTest: () -> Unit = {},
) {
    val colors = OrcaTheme.colors
    var page by rememberSaveable { mutableIntStateOf(0) }
    var tooltip by remember { mutableStateOf<PreferenceItem?>(null) }
    var confirmingMixedTemperatures by remember { mutableStateOf(false) }
    var confirmingLanguage by remember { mutableStateOf<OrcaLanguage?>(null) }
    // The app's language as Android gives it, which a chosen language is.
    val language = orcaLanguageOf(LocalConfiguration.current.locales[0])
    // Nothing can be changed before the engine has read the configuration.
    val enabled = values.isNotEmpty()
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.window),
    ) {
        OrcaPageTopBar(title = orcaString("Preferences"), backDescription = stringResource(R.string.preferences_back), onBack = onBack)
        val titles = PREFERENCE_PAGES.map { orcaString(it.title) }
        // The items of the page, no wider than PreferencesDialog lays them out
        // (DESIGN_TITLE_SIZE beside DESIGN_LARGE_COMBOBOX_SIZE).
        val pageItems: @Composable (Modifier) -> Unit = { modifier ->
            OrcaCentredContent(OrcaPageWidth.Settings, modifier) { padding ->
                LazyColumn(contentPadding = padding) {
                    for (section in PREFERENCE_PAGES[page].sections) {
                        item(key = "section:${section.title}") { SectionTitle(orcaString(section.title)) }
                        items(section.items, key = { it.key }) { item ->
                            PreferenceRow(item, values, enabled, language, onTooltip = { tooltip = item }, onOpen = onOpenNetworkTest) { key, value ->
                                when {
                                    // create_item_checkbox(): turning the restriction off asks first.
                                    key == AppConfigKeys.ENABLE_HIGH_LOW_TEMP_MIXED_PRINTING && value == "true" -> confirmingMixedTemperatures = true
                                    // create_item_language_combobox(): another language asks first.
                                    key == AppConfigKeys.LANGUAGE -> ORCA_LANGUAGES.firstOrNull { it.catalog == value }
                                        ?.takeIf { it != language }
                                        ?.let { confirmingLanguage = it }
                                    else -> onSet(key, value)
                                }
                            }
                        }
                    }
                }
            }
        }
        val insets = Modifier
            .weight(1f)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
        if (currentOrcaWindowLayout() == OrcaWindowLayout.Expanded) {
            // A large window lists the tabs (m_pref_tabs) down the side of the items.
            OrcaListDetailPanes(list = { OrcaPageList(titles = titles, selectedIndex = page, onSelect = { page = it }) }, modifier = insets) {
                pageItems(Modifier.weight(1f))
            }
        } else {
            OrcaUnderlineTabs(titles = titles, selectedIndex = page, onSelect = { page = it }, modifier = Modifier.orcaContentWidth(OrcaPageWidth.Settings))
            pageItems(insets)
        }
    }
    tooltip?.let { item ->
        SettingTooltipDialog(
            label = orcaString(item.title),
            tooltip = when (item) {
                // The delay field's tooltip, which the desktop field shows under the pointer, follows the checkbox's.
                is PreferenceItem.CheckSeconds -> listOf(OrcaText(item.tooltip), OrcaText(PARAGRAPH), OrcaText(item.secondsTooltip))
                // An item without a tooltip shows its title (create_item_label()).
                else -> listOf(OrcaText(item.tooltip.ifEmpty { item.title }))
            },
            onDismiss = { tooltip = null },
            // create_item_label(): the label of an item with a page of the wiki opens it.
            wikiUrl = item.wiki.takeIf(String::isNotEmpty)?.let { WIKI + it },
        )
    }
    confirmingLanguage?.let { chosen ->
        LanguageDialog(
            onContinue = {
                confirmingLanguage = null
                onSelectLanguage(chosen)
            },
            onCancel = { confirmingLanguage = null },
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

/** An item: its label, which shows the tooltip, and its control, which writes the key it names. */
@Composable
private fun PreferenceRow(
    item: PreferenceItem,
    values: Map<String, String>,
    enabled: Boolean,
    language: OrcaLanguage,
    onTooltip: () -> Unit,
    /** The button of an item that opens a dialog of its own. */
    onOpen: () -> Unit,
    onChange: (key: String, value: String) -> Unit,
) {
    val colors = OrcaTheme.colors
    val value = values[item.key].orEmpty()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier
                .weight(1f)
                .clickable(role = Role.Button, onClick = onTooltip)
                .padding(vertical = 8.dp),
        ) {
            Text(text = orcaString(item.title), color = colors.text, style = OrcaTheme.typography.body14)
            // create_item_spinctrl()'s second title, beside the input on the desktop.
            if (item is PreferenceItem.Spin && item.note.isNotEmpty()) {
                Text(text = orcaString(item.note), color = colors.textSide, style = OrcaTheme.typography.body12)
            }
        }
        Spacer(Modifier.width(12.dp))
        when (item) {
            is PreferenceItem.Check -> OrcaSwitch(
                checked = AppConfigKeys.bool(value),
                onCheckedChange = { onChange(item.key, if (it) "true" else "false") },
                enabled = enabled,
            )
            is PreferenceItem.CheckSeconds -> {
                val checked = AppConfigKeys.bool(value)
                // The field shows "0" while no seconds are set.
                SecondsField(values[item.secondsKey].orEmpty().ifEmpty { "0" }, enabled && checked) { onChange(item.secondsKey, it) }
                Spacer(Modifier.width(12.dp))
                OrcaSwitch(
                    checked = checked,
                    onCheckedChange = { onChange(item.key, if (it) "true" else "false") },
                    enabled = enabled,
                )
            }
            is PreferenceItem.Choice -> {
                val labels = item.labels.mapIndexed { index, label -> orcaString(label) + item.suffixes?.get(index).orEmpty() }
                OrcaComboBox(
                    items = labels.indices.toList(),
                    selected = item.selected(value),
                    label = { labels[it] },
                    onSelect = { onChange(item.key, item.valueOf(it)) },
                    enabled = enabled,
                    modifier = Modifier.width(CONTROL_WIDTH),
                )
            }
            is PreferenceItem.Spin -> OrcaSpinInput(
                // stoi() of the value, which SpinInput shows within its range.
                value = (value.toIntOrNull() ?: 0).coerceIn(item.range),
                onValueChange = { onChange(item.key, it.toString()) },
                range = item.range,
                decreaseDescription = stringResource(R.string.preferences_decrease),
                increaseDescription = stringResource(R.string.preferences_increase),
                unit = orcaString(item.unit),
                enabled = enabled,
                modifier = Modifier.width(CONTROL_WIDTH),
            )
            is PreferenceItem.Decimal -> DecimalField(item, value, enabled) { onChange(item.key, it) }
            is PreferenceItem.Digits -> DigitsField(value, enabled) { onChange(item.key, it) }
            is PreferenceItem.Language -> OrcaComboBox(
                items = ORCA_LANGUAGES.indices.toList(),
                selected = ORCA_LANGUAGES.indexOf(language),
                label = { ORCA_LANGUAGES[it].name },
                onSelect = { onChange(item.key, ORCA_LANGUAGES[it].catalog) },
                enabled = enabled,
                modifier = Modifier.width(CONTROL_WIDTH),
            )
            is PreferenceItem.Clear -> OrcaButton(
                text = orcaString("Clear"),
                onClick = { onChange(item.key, "") },
                enabled = enabled,
                style = OrcaButtonStyle.Regular,
            )
            is PreferenceItem.Open -> OrcaButton(
                text = orcaString(item.button) + " " + DOTS,
                onClick = onOpen,
                style = OrcaButtonStyle.Regular,
            )
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

/** create_item_input(): digits only (wxFILTER_DIGITS), written as they are when it is done or loses the focus. */
@Composable
private fun DigitsField(value: String, enabled: Boolean, onChange: (String) -> Unit) {
    val focusManager = LocalFocusManager.current
    var text by remember(value) { mutableStateOf(value) }
    var focused by remember { mutableStateOf(false) }
    OrcaTextField(
        value = text,
        onValueChange = { typed -> text = typed.filter(Char::isDigit) },
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        modifier = Modifier
            .width(CONTROL_WIDTH)
            .onFocusChanged { state ->
                if (focused && !state.isFocused && text != value) onChange(text)
                focused = state.isFocused
            },
    )
}

/**
 * The seconds of create_item_auto_reslice() and create_item_backup(): digits only (wxFILTER_DIGITS), written
 * when it is done or loses the focus as a whole number of seconds, 0 for text
 * that is none; "sec" beside it.
 */
@Composable
private fun SecondsField(value: String, enabled: Boolean, onChange: (String) -> Unit) {
    val focusManager = LocalFocusManager.current
    var text by remember(value) { mutableStateOf(value) }
    var focused by remember { mutableStateOf(false) }
    OrcaTextField(
        value = text,
        onValueChange = { typed -> text = typed.filter(Char::isDigit) },
        unit = orcaString("sec"),
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
        modifier = Modifier
            .width(SECONDS_WIDTH)
            .onFocusChanged { state ->
                if (focused && !state.isFocused) {
                    // wxString::ToLong(), and "%ld" of what it read.
                    val seconds = text.toLongOrNull() ?: 0L
                    text = seconds.toString()
                    onChange(text)
                }
                focused = state.isFocused
            },
    )
}

/**
 * create_item_language_combobox()'s question before another language: the
 * app's screen is built again in it. Its title is OrcaSlicer's L(), which
 * leaves it untranslated.
 */
@Composable
private fun LanguageDialog(onContinue: () -> Unit, onCancel: () -> Unit) {
    val colors = OrcaTheme.colors
    AlertDialog(
        onDismissRequest = onCancel,
        confirmButton = { OrcaButton(orcaString("OK"), onClick = onContinue) },
        dismissButton = { OrcaButton(orcaString("Cancel"), onClick = onCancel, style = OrcaButtonStyle.Regular) },
        title = { Text("Language selection", style = OrcaTheme.typography.head16) },
        text = {
            Text(
                orcaString("Switching the language requires application restart.\n") + "\n" + orcaString("Do you want to continue?"),
                style = OrcaTheme.typography.body14,
            )
        },
        containerColor = colors.window,
        titleContentColor = colors.text,
        textContentColor = colors.text,
        shape = OrcaTheme.shapes.window,
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
    // The wiki in Chinese for an app in Chinese, in English otherwise.
    val region = if ("zh" in orcaLanguageOf(LocalConfiguration.current.locales[0]).canonicalName) "zh" else "en"
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
                OrcaLink(orcaString("Click Wiki for help."), onClick = { openInBrowser(context, MIXED_TEMPERATURE_WIKI.format(region)) })
            }
        },
        containerColor = colors.window,
        titleContentColor = colors.text,
        textContentColor = colors.text,
        shape = OrcaTheme.shapes.window,
    )
}

private val CONTROL_WIDTH = 150.dp
private val SECONDS_WIDTH = 97.dp

/** The break between the two tooltips of an item. */
private const val PARAGRAPH = "\n\n"
private const val MIXED_TEMPERATURE_WIKI = "https://wiki.bambulab.com/%s/filament-acc/filament/h2d-filament-config-limit"

/** create_item_label(): the wiki's pages, which an item names. */
private const val WIKI = "https://www.orcaslicer.com/wiki/"

private const val DOTS = "..."
