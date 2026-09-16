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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import app.orcinus.shadow.core.designsystem.component.OrcaComboField
import app.orcinus.shadow.core.designsystem.component.OrcaFilamentSlot
import app.orcinus.shadow.core.designsystem.component.OrcaSidebarSection
import app.orcinus.shadow.core.designsystem.component.OrcaSidebarTitle
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import app.orcinus.shadow.core.designsystem.theme.OrcinusTheme
import app.orcinus.shadow.core.model.EngineAvailability
import app.orcinus.shadow.core.model.EngineState
import app.orcinus.shadow.core.model.EngineVersion
import app.orcinus.shadow.core.model.PlateState
import app.orcinus.shadow.core.model.ProfileId
import app.orcinus.shadow.core.model.SlicingProfileSelection
import app.orcinus.shadow.core.ui.presetAlias
import app.orcinus.shadow.domain.plate.ObservePlateUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class SidebarUiState(
    val profiles: SlicingProfileSelection,
    val engine: EngineState,
)

class SidebarViewModel(observePlate: ObservePlateUseCase) : ViewModel() {
    val state: StateFlow<SidebarUiState> = observePlate()
        .map(PlateState::toSidebarUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), observePlate().value.toSidebarUiState())

    private companion object {
        // Keeps the upstream flow through configuration changes.
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

private fun PlateState.toSidebarUiState() = SidebarUiState(profiles = profiles, engine = engine)

/**
 * OrcaSlicer's sidebar: printer, material, and process of the plate. It is
 * shared by Prepare and Preview, so the app shell places it; [createViewModel]
 * builds its view model from the app's dependencies. [onOpenAbout] opens the
 * About page, which OrcaSlicer keeps in its Help menu.
 */
@Composable
fun PlateSidebar(createViewModel: () -> SidebarViewModel, onOpenAbout: () -> Unit) {
    val state by viewModel { createViewModel() }.state.collectAsStateWithLifecycle()
    PlateSidebarContent(state, onOpenAbout)
}

@Composable
internal fun PlateSidebarContent(state: SidebarUiState, onOpenAbout: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(OrcaTheme.colors.window)
            .verticalScroll(rememberScrollState()),
    ) {
        OrcaSidebarTitle(stringResource(R.string.section_printer), DesignR.drawable.orca_printer)
        OrcaSidebarSection { OrcaComboField(state.profiles.printer.value) }

        OrcaSidebarTitle(stringResource(R.string.section_filament), DesignR.drawable.orca_filament)
        OrcaSidebarSection {
            OrcaComboField(presetAlias(state.profiles.filament.value)) {
                OrcaFilamentSlot(number = 1)
                Spacer(Modifier.width(8.dp))
            }
        }

        OrcaSidebarTitle(stringResource(R.string.section_process), DesignR.drawable.orca_process)
        OrcaSidebarSection { OrcaComboField(state.profiles.process.value) }

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
}

@Preview(name = "Light", widthDp = 320, heightDp = 480)
@Preview(name = "Dark", widthDp = 320, heightDp = 480, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PlateSidebarPreview() = OrcinusTheme {
    PlateSidebarContent(
        SidebarUiState(
            profiles = SlicingProfileSelection(
                ProfileId("Creality K2 Plus 0.4 nozzle"),
                ProfileId("Generic PLA @K2 Plus-all"),
                ProfileId("0.20mm Standard @Creality K2 Plus 0.4 nozzle"),
            ),
            engine = EngineState(EngineAvailability.READY, EngineVersion("orca-upstream/2.4.2")),
        ),
        onOpenAbout = {},
    )
}
