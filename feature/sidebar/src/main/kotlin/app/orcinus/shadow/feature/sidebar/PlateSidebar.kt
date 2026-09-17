package app.orcinus.shadow.feature.sidebar

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.orcinus.shadow.core.designsystem.R as DesignR
import app.orcinus.shadow.core.designsystem.component.OrcaComboBox
import app.orcinus.shadow.core.designsystem.component.OrcaComboField
import app.orcinus.shadow.core.designsystem.component.OrcaFilamentSlot
import app.orcinus.shadow.core.designsystem.component.OrcaSidebarSection
import app.orcinus.shadow.core.designsystem.component.OrcaSidebarTitle
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import app.orcinus.shadow.core.designsystem.theme.OrcinusTheme
import app.orcinus.shadow.core.model.ColorRgba
import app.orcinus.shadow.core.model.EngineAvailability
import app.orcinus.shadow.core.model.EngineState
import app.orcinus.shadow.core.model.EngineVersion
import app.orcinus.shadow.core.model.PlateObject
import app.orcinus.shadow.core.model.PlateState
import app.orcinus.shadow.core.model.PresetChoice
import app.orcinus.shadow.core.model.PresetGroup
import app.orcinus.shadow.core.model.PresetListItem
import app.orcinus.shadow.core.model.Presets
import app.orcinus.shadow.core.model.ProfileId
import app.orcinus.shadow.core.model.SlicingProfileSelection
import app.orcinus.shadow.domain.plate.ObservePlateUseCase
import app.orcinus.shadow.domain.plate.SelectPresetUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class SidebarUiState(
    val engine: EngineState,
    /** Null until the engine reported the presets. */
    val presets: Presets? = null,
    /** The first filament's colour, as the plate shows it. */
    val filamentColor: ColorRgba? = null,
    /** Presets can be chosen: the plate is not busy and no placement is settling. */
    val canChoose: Boolean = false,
    /** A preset choice is being applied. */
    val changing: Boolean = false,
)

class SidebarViewModel(
    observePlate: ObservePlateUseCase,
    private val selectPreset: SelectPresetUseCase,
) : ViewModel() {
    val state: StateFlow<SidebarUiState> = observePlate()
        .map(PlateState::toSidebarUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), observePlate().value.toSidebarUiState())

    fun choose(choice: PresetChoice) = selectPreset(choice)

    private companion object {
        // Keeps the upstream flow through configuration changes.
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

private fun PlateState.toSidebarUiState() = SidebarUiState(
    engine = engine,
    presets = presets,
    filamentColor = plate?.filamentColor,
    canChoose = profiles != null && !busy && objects.none(PlateObject::placing),
    changing = changingPresets,
)

/** Which Setup Wizard page a preset list opens: its printers or its filaments. */
enum class PresetWizardPage { PRINTERS, FILAMENTS }

/**
 * OrcaSlicer's sidebar: printer, nozzle, material, and process of the plate,
 * each chosen from the lists of OrcaSlicer's preset combo boxes. It is shared
 * by Prepare and Preview, so the app shell places it; [createViewModel] builds
 * its view model from the app's dependencies. [onOpenWizard] opens the Setup
 * Wizard, and [onOpenAbout] the About page, which OrcaSlicer keeps in its Help menu.
 */
@Composable
fun PlateSidebar(
    createViewModel: () -> SidebarViewModel,
    onOpenWizard: (PresetWizardPage) -> Unit,
    onOpenAbout: () -> Unit,
) {
    val viewModel = viewModel { createViewModel() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    PlateSidebarContent(state, onChoose = viewModel::choose, onOpenWizard = onOpenWizard, onOpenAbout = onOpenAbout)
}

private enum class PresetList { PRINTERS, FILAMENTS, PROCESSES }

@Composable
internal fun PlateSidebarContent(
    state: SidebarUiState,
    onChoose: (PresetChoice) -> Unit,
    onOpenWizard: (PresetWizardPage) -> Unit,
    onOpenAbout: () -> Unit,
) {
    var openList by rememberSaveable { mutableStateOf<PresetList?>(null) }
    val presets = state.presets
    val enabled = state.canChoose && presets != null
    Column(
        Modifier
            .fillMaxSize()
            .background(OrcaTheme.colors.window)
            .verticalScroll(rememberScrollState()),
    ) {
        OrcaSidebarTitle(stringResource(R.string.section_printer), DesignR.drawable.orca_printer)
        OrcaSidebarSection {
            OrcaComboField(
                text = presets?.printers?.selectedLabel(presets.selection.printer.value).orEmpty(),
                enabled = enabled,
                onClick = { openList = PresetList.PRINTERS },
            )
            if (presets != null && presets.nozzleDiameters.isNotEmpty()) {
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.nozzle_diameter),
                        color = OrcaTheme.colors.textLabel,
                        style = OrcaTheme.typography.body14,
                        modifier = Modifier.weight(1f),
                    )
                    OrcaComboBox(
                        items = presets.nozzleDiameters,
                        selected = presets.nozzleDiameter,
                        label = { it },
                        onSelect = { onChoose(PresetChoice.NozzleDiameter(it)) },
                        enabled = enabled && presets.nozzleDiameters.size > 1,
                        modifier = Modifier.width(96.dp),
                    )
                }
            }
        }

        OrcaSidebarTitle(stringResource(R.string.section_filament), DesignR.drawable.orca_filament)
        OrcaSidebarSection {
            OrcaComboField(
                text = presets?.filaments?.selectedLabel(presets.selection.filament.value).orEmpty(),
                enabled = enabled,
                onClick = { openList = PresetList.FILAMENTS },
            ) {
                OrcaFilamentSlot(number = 1, color = state.filamentColor?.let { Color(it.red, it.green, it.blue, it.alpha) } ?: OrcaTheme.colors.accent)
                Spacer(Modifier.width(8.dp))
            }
        }

        OrcaSidebarTitle(stringResource(R.string.section_process), DesignR.drawable.orca_process)
        OrcaSidebarSection {
            OrcaComboField(
                text = presets?.processes?.selectedLabel(presets.selection.process.value).orEmpty(),
                enabled = enabled,
                onClick = { openList = PresetList.PROCESSES },
            )
        }

        Text(
            text = when (state.engine.availability) {
                EngineAvailability.STARTING -> stringResource(R.string.engine_starting)
                EngineAvailability.READY -> stringResource(R.string.engine_ready, state.engine.version?.value.orEmpty())
                EngineAvailability.UNAVAILABLE -> stringResource(R.string.engine_unavailable)
            },
            color = OrcaTheme.colors.textSide,
            style = OrcaTheme.typography.body12,
            modifier = Modifier.padding(12.dp),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onOpenAbout)
                .heightIn(min = OrcaTheme.dimensions.minimumTouchTarget)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(DesignR.drawable.orca_help), contentDescription = null, tint = OrcaTheme.colors.textSide, modifier = Modifier.size(OrcaTheme.dimensions.icon))
            Text(stringResource(R.string.about), color = OrcaTheme.colors.text, style = OrcaTheme.typography.body14, modifier = Modifier.padding(start = 8.dp))
        }
    }

    if (presets != null) {
        when (openList) {
            PresetList.PRINTERS -> PresetListSheet(
                title = stringResource(R.string.section_printer),
                items = presets.printers,
                onDismiss = { openList = null },
                onChoose = { item ->
                    openList = null
                    onChoose(if (item.group == PresetGroup.SYSTEM) PresetChoice.PrinterModel(item.name) else PresetChoice.Printer(ProfileId(item.name)))
                },
                action = stringResource(R.string.select_printers),
                onAction = {
                    openList = null
                    onOpenWizard(PresetWizardPage.PRINTERS)
                },
            )
            PresetList.FILAMENTS -> PresetListSheet(
                title = stringResource(R.string.section_filament),
                items = presets.filaments,
                onDismiss = { openList = null },
                onChoose = { item ->
                    openList = null
                    onChoose(PresetChoice.Filament(ProfileId(item.name)))
                },
                action = stringResource(R.string.select_filaments),
                onAction = {
                    openList = null
                    onOpenWizard(PresetWizardPage.FILAMENTS)
                },
            )
            PresetList.PROCESSES -> PresetListSheet(
                title = stringResource(R.string.section_process),
                items = presets.processes,
                onDismiss = { openList = null },
                onChoose = { item ->
                    openList = null
                    onChoose(PresetChoice.Process(ProfileId(item.name)))
                },
            )
            null -> Unit
        }
    }
}

/** The label the combo box shows for its selected entry. */
private fun List<PresetListItem>.selectedLabel(fallback: String): String = firstOrNull(PresetListItem::selected)?.label ?: fallback

@Preview(name = "Light", widthDp = 320, heightDp = 480)
@Preview(name = "Dark", widthDp = 320, heightDp = 480, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PlateSidebarPreview() = OrcinusTheme {
    val selection = SlicingProfileSelection(
        ProfileId("Creality K2 Plus 0.4 nozzle"),
        ProfileId("Generic PLA @K2 Plus-all"),
        ProfileId("0.20mm Standard @Creality K2 Plus 0.4 nozzle"),
    )
    PlateSidebarContent(
        SidebarUiState(
            engine = EngineState(EngineAvailability.READY, EngineVersion("orca-upstream/2.4.2")),
            presets = Presets(
                selection = selection,
                setupRequired = false,
                printers = listOf(PresetListItem("Creality K2 Plus", "Creality K2 Plus", PresetGroup.SYSTEM, "", selected = true)),
                filaments = listOf(PresetListItem(selection.filament.value, "Generic PLA", PresetGroup.SYSTEM, "Generic", selected = true)),
                processes = listOf(PresetListItem(selection.process.value, selection.process.value, PresetGroup.SYSTEM, "", selected = true)),
                nozzleDiameters = listOf("0.2", "0.4", "0.6", "0.8"),
                nozzleDiameter = "0.4",
            ),
            canChoose = true,
        ),
        onChoose = {},
        onOpenWizard = {},
        onOpenAbout = {},
    )
}
