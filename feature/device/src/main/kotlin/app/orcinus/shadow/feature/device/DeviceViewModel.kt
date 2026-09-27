package app.orcinus.shadow.feature.device

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.orcinus.shadow.core.model.PrintHostUploadOutcome
import app.orcinus.shadow.core.model.PrinterConnection
import app.orcinus.shadow.core.model.PrinterConnectionOutcome
import app.orcinus.shadow.domain.plate.DevicePageUseCase
import app.orcinus.shadow.domain.plate.ObservePlateUseCase
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
 * change (Sidebar::update_all_preset_comboboxes() calls load_printer_url()),
 * and what Elegoo's page asks of the app (ElegooPrinterWebViewHandler).
 */
class DeviceViewModel(
    observePlate: ObservePlateUseCase,
    private val devicePage: DevicePageUseCase,
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

    /** handle_get_sn_request(): the printer's serial number, as far as the app knows it. */
    suspend fun serialNumber(): String = devicePage.serialNumber()

    /** handle_upload_request(): a file the page picked, stored on the printer. */
    suspend fun upload(path: String, onProgress: (Float) -> Unit): PrintHostUploadOutcome = devicePage.upload(path, onProgress)

    private suspend fun load() {
        view.value = when (val outcome = devicePage()) {
            is PrinterConnectionOutcome.Success -> DeviceUiState(connection = outcome.connection)
            is PrinterConnectionOutcome.Failure -> DeviceUiState(problem = outcome.message)
        }
    }
}
