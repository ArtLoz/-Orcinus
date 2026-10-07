package app.orcinus.shadow.feature.about

import android.content.ClipData
import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.orcinus.shadow.core.designsystem.component.OrcaButton
import app.orcinus.shadow.core.designsystem.component.OrcaButtonSize
import app.orcinus.shadow.core.designsystem.component.OrcaButtonStyle
import app.orcinus.shadow.core.designsystem.component.OrcaComboBox
import app.orcinus.shadow.core.designsystem.component.OrcaLink
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import app.orcinus.shadow.core.designsystem.theme.OrcinusTheme
import app.orcinus.shadow.core.model.AppConfigKeys
import app.orcinus.shadow.core.model.ExternalDocumentReference
import app.orcinus.shadow.core.model.ProfileCounts
import app.orcinus.shadow.core.model.ProfilesOverview
import app.orcinus.shadow.core.ui.orca.orcaString
import app.orcinus.shadow.core.ui.settings.openInBrowser
import app.orcinus.shadow.domain.about.SystemInformation
import kotlinx.coroutines.launch

/** What the Troubleshoot Center's buttons do. */
internal class TroubleshootActions(
    val toggleSystem: () -> Unit,
    val copySystem: (String) -> Unit,
    val openUrl: (String) -> Unit,
    val pack: () -> Unit,
    val cleanSystemProfiles: () -> Unit,
    val exportProfilesOverview: () -> Unit,
    val setLogLevel: (String) -> Unit,
)

/**
 * OrcaSlicer's Troubleshoot Center (GUI_App::troubleshoot()) as a page: its
 * message boxes over it, and its file dialogs the system's document picker.
 * No of the save question closes the page so the project can be reviewed, as
 * the desktop dialog closes.
 */
@Composable
internal fun TroubleshootRoute(viewModel: TroubleshootViewModel, logo: @Composable () -> Unit, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val logLevel by viewModel.logLevel.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val packPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ZIP_MIME_TYPE)) { uri ->
        viewModel.picked(TroubleshootPicker.Target.PACK, uri?.let { ExternalDocumentReference(it.toString()) })
    }
    val overviewPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(JSON_MIME_TYPE)) { uri ->
        viewModel.picked(TroubleshootPicker.Target.PROFILES_OVERVIEW, uri?.let { ExternalDocumentReference(it.toString()) })
    }
    LaunchedEffect(state.picker) {
        val picker = state.picker ?: return@LaunchedEffect
        viewModel.pickerShown()
        when (picker.target) {
            TroubleshootPicker.Target.PACK -> packPicker.launch(picker.name)
            TroubleshootPicker.Target.PROFILES_OVERVIEW -> overviewPicker.launch(picker.name)
        }
    }
    val title = stringResource(R.string.troubleshoot_title)
    TroubleshootScreen(
        state = state,
        logLevel = logLevel,
        logo = logo,
        actions = TroubleshootActions(
            toggleSystem = viewModel::toggleSystem,
            copySystem = { text -> scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(title, text))) } },
            openUrl = { openInBrowser(context, it) },
            pack = viewModel::pack,
            cleanSystemProfiles = viewModel::cleanSystemProfiles,
            exportProfilesOverview = viewModel::exportProfilesOverview,
            setLogLevel = viewModel::setLogLevel,
        ),
        onBack = onBack,
    )
    when (val prompt = state.prompt) {
        TroubleshootPrompt.SaveChanges -> TroubleshootMessage(
            title = orcaString("Save"),
            text = orcaString("The current project has unsaved changes, save it before continue?") + "\n\n" +
                stringResource(R.string.troubleshoot_review_project),
            buttons = listOf(
                orcaString("Yes") to { viewModel.answerSaveChanges(true) },
                orcaString("No") to {
                    viewModel.answerSaveChanges(false)
                    onBack()
                },
                orcaString("Cancel") to { viewModel.answerSaveChanges(false) },
            ),
            onDismiss = { viewModel.answerSaveChanges(false) },
        )
        TroubleshootPrompt.NoProject -> TroubleshootMessage(
            title = null,
            text = stringResource(R.string.troubleshoot_no_project),
            buttons = listOf(orcaString("OK") to viewModel::packWithoutProject),
            onDismiss = viewModel::dismissPrompt,
        )
        TroubleshootPrompt.CleanSystemProfiles -> TroubleshootMessage(
            title = orcaString("Restart Required"),
            text = stringResource(R.string.troubleshoot_clean_next_start) + "\n" + orcaString("Do you want to continue?"),
            buttons = listOf(
                orcaString("OK") to { viewModel.answerCleanSystemProfiles(true) },
                orcaString("Cancel") to { viewModel.answerCleanSystemProfiles(false) },
            ),
            onDismiss = { viewModel.answerCleanSystemProfiles(false) },
        )
        TroubleshootPrompt.CleanFailed -> TroubleshootMessage(
            title = orcaString("Error"),
            text = stringResource(R.string.troubleshoot_clean_failed),
            buttons = listOf(orcaString("OK") to viewModel::dismissPrompt),
            onDismiss = viewModel::dismissPrompt,
            error = true,
        )
        is TroubleshootPrompt.Exported -> TroubleshootMessage(
            title = null,
            text = if (prompt.written) orcaString("Export successful") else stringResource(R.string.troubleshoot_export_failed),
            buttons = listOf(orcaString("OK") to viewModel::dismissPrompt),
            onDismiss = viewModel::dismissPrompt,
            error = !prompt.written,
        )
        null -> Unit
    }
}

/**
 * TroubleshootDialog's two columns one after the other: the logo, the
 * version and the system information first, then Information, Profiles
 * and More.
 */
@Composable
internal fun TroubleshootScreen(
    state: TroubleshootUiState,
    logLevel: String?,
    logo: @Composable () -> Unit,
    actions: TroubleshootActions,
    onBack: () -> Unit,
) {
    AboutPage(title = stringResource(R.string.troubleshoot_title), onBack = onBack) {
        if (state.busy) {
            item { LinearProgressIndicator(color = OrcaTheme.colors.accent, modifier = Modifier.pageContent()) }
        }
        item { SystemHeader(state.system, state.systemShown, logo, actions) }

        item { SectionTitle(orcaString("Information")) }
        item { BodyText(stringResource(R.string.troubleshoot_info_needed)) }
        item { BodyText(stringResource(R.string.troubleshoot_info_pack)) }
        item { BodyText(stringResource(R.string.troubleshoot_info_examples)) }
        item {
            Row(
                modifier = Modifier
                    .pageContent()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                state.reportIssueUrl?.let { url -> OrcaLink(stringResource(R.string.troubleshoot_report_issue), onClick = { actions.openUrl(url) }) }
                Spacer(Modifier.weight(1f))
                OrcaButton(stringResource(R.string.troubleshoot_pack) + DOTS, onClick = actions.pack, style = OrcaButtonStyle.Regular, enabled = !state.busy)
            }
        }

        item { SectionTitle(stringResource(R.string.troubleshoot_profiles)) }
        item {
            val cleaned = state.profiles?.systemCleaned == true
            ActionRow(
                title = stringResource(R.string.troubleshoot_clean_system),
                details = stringResource(if (cleaned) R.string.troubleshoot_clean_pending else R.string.troubleshoot_clean_system_tip),
            ) {
                OrcaButton(
                    stringResource(R.string.troubleshoot_clean),
                    onClick = actions.cleanSystemProfiles,
                    style = OrcaButtonStyle.Regular,
                    enabled = state.profiles != null && !cleaned,
                )
            }
        }
        item {
            ActionRow(title = stringResource(R.string.troubleshoot_loaded_profiles), details = stringResource(R.string.troubleshoot_loaded_profiles_tip)) {
                OrcaButton(
                    orcaString("Export") + DOTS,
                    onClick = actions.exportProfilesOverview,
                    style = OrcaButtonStyle.Regular,
                    enabled = state.profiles != null && !state.busy,
                )
            }
        }
        item { ProfileCountsTable(state.profiles) }

        item { SectionTitle(orcaString("More")) }
        item {
            val labels = AppConfigKeys.LOG_SEVERITY_LEVELS.associateWith { orcaString(it) }
            ActionRow(title = stringResource(R.string.troubleshoot_log_level)) {
                OrcaComboBox(
                    items = AppConfigKeys.LOG_SEVERITY_LEVELS,
                    selected = logLevel.orEmpty(),
                    label = { labels[it] ?: it },
                    onSelect = actions.setLogLevel,
                    enabled = logLevel != null,
                    modifier = Modifier.width(COMBO_WIDTH),
                )
            }
        }
    }
}

/** The left column of the dialog: logo, version, the build's commit, and the system information with Hide/Show and Copy. */
@Composable
private fun SystemHeader(system: SystemInformation?, shown: Boolean, logo: @Composable () -> Unit, actions: TroubleshootActions) {
    val colors = OrcaTheme.colors
    Column(
        modifier = Modifier
            .pageContent()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        logo()
        if (system == null) {
            Loading()
            return@Column
        }
        Text(system.version, color = colors.text, style = OrcaTheme.typography.head20, modifier = Modifier.padding(top = 12.dp))
        Text(stringResource(R.string.app_engine, system.engine), color = colors.textSide, style = OrcaTheme.typography.body14)
        val build = system.build
        val buildUrl = system.buildUrl
        if (build != null && buildUrl != null) {
            OrcaButton(build, onClick = { actions.openUrl(buildUrl) }, style = OrcaButtonStyle.Regular, size = OrcaButtonSize.Compact, modifier = Modifier.padding(top = 4.dp))
        }
        (if (shown) system.lines else listOf(system.osType)).forEach { line ->
            Text(
                text = line,
                color = colors.text,
                style = OrcaTheme.typography.body13,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OrcaButton(orcaString(if (shown) "Hide" else "Show"), onClick = actions.toggleSystem, style = OrcaButtonStyle.Regular)
            Spacer(Modifier.weight(1f))
            OrcaButton(orcaString("Copy"), onClick = { actions.copySystem(system.text) }, style = OrcaButtonStyle.Regular)
        }
    }
}

/** A line of the right column: its label, what it does, and its control. */
@Composable
private fun ActionRow(title: String, details: String? = null, control: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .pageContent()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier
                .weight(1f)
                .padding(end = 12.dp),
        ) {
            Text(title, color = OrcaTheme.colors.text, style = OrcaTheme.typography.body15)
            details?.let { Text(it, color = OrcaTheme.colors.textSide, style = OrcaTheme.typography.body12, modifier = Modifier.padding(top = 2.dp)) }
        }
        control()
    }
}

/** create_item_loaded_profiles(): Active / System + User of the printers, filaments and processes. */
@Composable
private fun ProfileCountsTable(profiles: ProfilesOverview?) {
    if (profiles == null) {
        Loading()
        return
    }
    val colors = OrcaTheme.colors
    Column(
        Modifier
            .pageContent()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        CountsRow(
            label = "",
            cells = listOf(stringResource(R.string.troubleshoot_active), "", orcaString("System"), "", orcaString("User")),
            header = true,
        )
        HorizontalDivider(color = colors.separator, thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))
        CountsRow(stringResource(R.string.troubleshoot_printers), profiles.printers.cells())
        CountsRow(orcaString("Filaments"), profiles.filaments.cells())
        CountsRow(stringResource(R.string.troubleshoot_processes), profiles.processes.cells())
    }
}

private fun ProfileCounts.cells(): List<String> = listOf(active.toString(), "/", system.toString(), "+", user.toString())

@Composable
private fun CountsRow(label: String, cells: List<String>, header: Boolean = false) {
    val colors = OrcaTheme.colors
    val style = if (header) OrcaTheme.typography.body12 else OrcaTheme.typography.body14
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = colors.text, style = OrcaTheme.typography.body14, modifier = Modifier.weight(1f))
        cells.forEachIndexed { index, cell ->
            // Every other cell is the "/" or "+" between the counts.
            val separator = index % 2 == 1
            Box(Modifier.width(if (separator) SEPARATOR_WIDTH else COUNT_WIDTH), contentAlignment = Alignment.Center) {
                Text(
                    text = cell,
                    color = if (header || separator) colors.textSide else colors.text,
                    style = if (separator) OrcaTheme.typography.body12 else style,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

/** A MessageDialog of the dialog: three buttons stack in the desktop's order, two stand side by side. */
@Composable
private fun TroubleshootMessage(
    title: String?,
    text: String,
    buttons: List<Pair<String, () -> Unit>>,
    onDismiss: () -> Unit,
    error: Boolean = false,
) {
    val colors = OrcaTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = false),
        title = title?.let { { Text(it, style = OrcaTheme.typography.head16) } },
        text = { Text(text, color = if (error) colors.error else colors.text, style = OrcaTheme.typography.body14) },
        confirmButton = {
            if (buttons.size > 2) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    buttons.forEachIndexed { index, (label, onClick) ->
                        OrcaButton(label, onClick = onClick, style = if (index == 0) OrcaButtonStyle.Confirm else OrcaButtonStyle.Regular)
                    }
                }
            } else {
                buttons.firstOrNull()?.let { (label, onClick) -> OrcaButton(label, onClick = onClick) }
            }
        },
        dismissButton = buttons.getOrNull(1)?.takeIf { buttons.size == 2 }?.let { (label, onClick) ->
            { OrcaButton(label, onClick = onClick, style = OrcaButtonStyle.Regular) }
        },
        containerColor = colors.window,
        titleContentColor = colors.text,
        textContentColor = colors.text,
        shape = OrcaTheme.shapes.window,
    )
}

// GUI_App's dots, which follow the label of a button that opens a file dialog.
private const val DOTS = "..."
private const val ZIP_MIME_TYPE = "application/zip"
private const val JSON_MIME_TYPE = "application/json"
private val COMBO_WIDTH = 140.dp
private val COUNT_WIDTH = 64.dp
private val SEPARATOR_WIDTH = 16.dp

private val PreviewSystem = SystemInformation(
    version = "Orcinus 0.1.0",
    engine = "2.4.2",
    build = "8500fcd",
    buildUrl = "https://github.com/OrcaSlicer/OrcaSlicer/commit/8500fcd",
    lines = listOf("Android 16 (API 36)", "Google Pixel 8 Pro", "Local Build", "Google Tensor G3 (arm64-v8a)", "11.6 GB RAM", "Mali-G715  GLSL:OpenGL ES GLSL ES 3.20", "1344x2992-350%  TextScaling-100%"),
    osType = "Android",
    text = "",
)

@Preview(name = "Troubleshoot Center", widthDp = 400, heightDp = 1400)
@Preview(name = "Troubleshoot Center dark", widthDp = 400, heightDp = 1400, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TroubleshootPreview() = OrcinusTheme {
    TroubleshootScreen(
        state = TroubleshootUiState(
            system = PreviewSystem,
            reportIssueUrl = "https://example.org/issues/new",
            profiles = ProfilesOverview(ProfileCounts(1, 12, 1), ProfileCounts(48, 950, 2), ProfileCounts(15, 420, 0), json = "{}"),
        ),
        logLevel = "warning",
        logo = {},
        actions = TroubleshootActions({}, {}, {}, {}, {}, {}, {}),
        onBack = {},
    )
}
