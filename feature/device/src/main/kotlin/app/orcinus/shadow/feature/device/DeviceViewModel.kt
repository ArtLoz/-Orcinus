package app.orcinus.shadow.feature.device

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.orcinus.shadow.core.model.PrinterConnection
import app.orcinus.shadow.core.model.PrinterConnectionOutcome
import app.orcinus.shadow.domain.plate.ObservePlateUseCase
import app.orcinus.shadow.domain.plate.ObservePrinterConnectionUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** What the Device tab shows: the printer's host as its preset holds it, or why it cannot. */
data class DeviceUiState(
    val connection: PrinterConnection? = null,
    val problem: String? = null,
)

/**
 * MainFrame's Device tab for a printer that is not BambuLab's (PrinterWebView):
 * the page of the printer's host, which it loads again whenever the presets
 * change (Sidebar::update_all_preset_comboboxes() calls load_printer_url()).
 */
class DeviceViewModel(
    observePlate: ObservePlateUseCase,
    private val printerConnection: ObservePrinterConnectionUseCase,
) : ViewModel() {
    private val view = MutableStateFlow(DeviceUiState())
    val state: StateFlow<DeviceUiState> = view.asStateFlow()

    init {
        viewModelScope.launch {
            observePlate().map { it.presets to it.connectionSaves }.distinctUntilChanged().collect { load() }
        }
    }

    /** The tab is shown again: its page follows the preset as it stands now. */
    fun reload() {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        view.value = when (val outcome = printerConnection()) {
            is PrinterConnectionOutcome.Success -> DeviceUiState(connection = outcome.connection)
            is PrinterConnectionOutcome.Failure -> DeviceUiState(problem = outcome.message)
        }
    }
}
