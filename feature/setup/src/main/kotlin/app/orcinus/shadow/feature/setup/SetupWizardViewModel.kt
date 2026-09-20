package app.orcinus.shadow.feature.setup

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.orcinus.shadow.core.model.CreateFilamentOptionsOutcome
import app.orcinus.shadow.core.model.CreateFilamentRequest
import app.orcinus.shadow.core.model.CustomFilament
import app.orcinus.shadow.core.model.CustomFilamentsOutcome
import app.orcinus.shadow.core.model.PresetCreationOutcome
import app.orcinus.shadow.core.model.FilamentPresetsOutcome
import app.orcinus.shadow.core.model.PresetsOutcome
import app.orcinus.shadow.core.model.SettingsDialog
import app.orcinus.shadow.core.model.SetupFilamentsOutcome
import app.orcinus.shadow.core.model.SetupPrinterModel
import app.orcinus.shadow.core.model.SetupPrintersOutcome
import app.orcinus.shadow.domain.plate.ApplySetupUseCase
import app.orcinus.shadow.domain.plate.CustomFilamentsUseCase
import app.orcinus.shadow.domain.plate.GetSetupFilamentsUseCase
import app.orcinus.shadow.domain.plate.GetSetupPrintersUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where the Setup Wizard opens, as GUI_App::run_wizard() starts it. */
enum class SetupStart {
    /** No printer is set up yet: closing the wizard installs OrcaSlicer's default printer. */
    FIRST_RUN,

    /** "Select/Remove printers (system presets)" */
    PRINTERS,

    /** "Add/Remove filaments": the filaments for the installed printers. */
    FILAMENTS,
}

internal enum class SetupPage { PRINTERS, FILAMENTS }

internal enum class SetupNotice {
    /** "At least one printer must be selected." */
    NO_PRINTER,

    /** "At least one filament must be selected." with the offer of the default filaments. */
    NO_FILAMENT,
}

internal data class FilamentPageState(
    val loading: Boolean = true,
    /** The printer models the page offers filaments for. */
    val models: List<SetupPrinterModel> = emptyList(),
    val lines: List<FilamentLine> = emptyList(),
    val checked: Set<Int> = emptySet(),
    /** Indices of the models whose filaments are shown. */
    val machines: Set<Int> = emptySet(),
    val types: List<String> = emptyList(),
    val checkedTypes: Set<String> = emptySet(),
    val vendors: List<String> = emptyList(),
    val checkedVendors: Set<String> = emptySet(),
    val search: String = "",
    val filter: FilamentFilter = FilamentFilter.ALL,
) {
    /** The lines the machine, type, and vendor filters show. */
    val shownLines: List<Int>
        get() = lines.indices.filter { FilamentPage.shown(lines[it], machines, checkedTypes, checkedVendors) }

    /** The shown lines the filter bar keeps. */
    val listedLines: List<Int>
        get() = shownLines.filter { FilamentPage.kept(lines[it], it in checked, search, filter) }
}

internal data class SetupUiState(
    val start: SetupStart,
    val page: SetupPage = if (start == SetupStart.FILAMENTS) SetupPage.FILAMENTS else SetupPage.PRINTERS,
    val loadingPrinters: Boolean = true,
    /** Every printer model, in the printer page's order. */
    val printers: List<SetupPrinterModel> = emptyList(),
    /** Ids of the chosen printer models. */
    val chosen: Set<String> = emptySet(),
    val keyword: String = "",
    val filaments: FilamentPageState = FilamentPageState(),
    val notice: SetupNotice? = null,
    /** The engine installs the choice. */
    val applying: Boolean = false,
    val error: String? = null,
    /** The wizard has done its work and closes. */
    val finished: Boolean = false,
) {
    val vendors: List<PrinterVendor> get() = PrinterPage.vendors(printers, keyword)
}

/**
 * OrcaSlicer's Setup Wizard (GuideFrame) with its printer and filament pages.
 * Finish installs the chosen printers and filaments and selects a printer;
 * closing the wizard on first run installs OrcaSlicer's default printer.
 */
class SetupWizardViewModel(
    start: SetupStart,
    private val getSetupPrinters: GetSetupPrintersUseCase,
    private val getSetupFilaments: GetSetupFilamentsUseCase,
    private val applySetup: ApplySetupUseCase,
    private val customFilaments: CustomFilamentsUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow(SetupUiState(start))
    internal val state: StateFlow<SetupUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            when (val outcome = getSetupPrinters()) {
                is SetupPrintersOutcome.Failure -> mutableState.update { it.copy(loadingPrinters = false, error = outcome.message) }
                is SetupPrintersOutcome.Success -> {
                    val sorted = PrinterPage.sorted(outcome.models)
                    mutableState.update { it.copy(loadingPrinters = false, printers = sorted, chosen = PrinterPage.installed(sorted)) }
                    if (start == SetupStart.FILAMENTS) loadFilaments()
                }
            }
        }
    }

    /**
     * The "Custom Filaments" of the wizard's filament page: the filaments of the
     * user's own, which CreateFilamentPresetDialog adds to and
     * EditFilamentPresetDialog changes.
     */
    internal var custom: List<CustomFilament> by mutableStateOf(emptyList())
        private set

    /** The question the creation or the deletion waits for an answer to. */
    internal var filamentQuestion: SettingsDialog? by mutableStateOf(null)
        private set

    /**
     * Whether CreateFilamentPresetDialog is open. Upstream runs it modally and
     * ends it only once the filament is made, so an answer that refuses a
     * question comes back to the pages the user has filled in.
     */
    internal var creatingFilament: Boolean by mutableStateOf(false)
        private set

    internal fun openCreateFilament() {
        creatingFilament = true
    }

    internal fun closeCreateFilament() {
        creatingFilament = false
        filamentQuestion = null
        pendingCreation = null
        answers = emptyMap()
    }

    internal var filamentProblem: String? by mutableStateOf(null)
        private set

    init {
        viewModelScope.launch { loadCustomFilaments() }
    }

    private suspend fun loadCustomFilaments() {
        custom = (customFilaments.filaments() as? CustomFilamentsOutcome.Success)?.filaments.orEmpty()
    }

    internal suspend fun filamentOptions(type: String, baseFilament: String): CreateFilamentOptionsOutcome =
        customFilaments.options(type, baseFilament)

    internal suspend fun filamentPresets(filamentId: String): FilamentPresetsOutcome = customFilaments.presets(filamentId)

    /** The Create button of the dialog, and the answers the user has given it. */
    internal fun createFilament(request: CreateFilamentRequest) {
        pendingCreation = request
        answers = emptyMap()
        runCreation()
    }

    /** The Delete button of a preset row. */
    internal fun deleteFilamentPreset(preset: String) {
        pendingDeletion = preset
        answers = emptyMap()
        runCreation()
    }

    /** The question's Yes or No: the request runs again with the answer. */
    internal fun answerFilamentQuestion(yes: Boolean) {
        val question = filamentQuestion ?: return
        filamentQuestion = null
        answers = answers + (question.id to yes)
        runCreation()
    }

    internal fun dismissFilamentQuestion() {
        filamentQuestion = null
        pendingCreation = null
        pendingDeletion = null
    }

    internal fun dismissFilamentProblem() {
        filamentProblem = null
    }

    private var pendingCreation: CreateFilamentRequest? = null
    private var pendingDeletion: String? = null
    private var answers: Map<String, Boolean> = emptyMap()

    private fun runCreation() {
        val request = pendingCreation
        val deletion = pendingDeletion
        viewModelScope.launch {
            val outcome = when {
                request != null -> customFilaments.create(request, answers)
                deletion != null -> customFilaments.deletePreset(deletion, answers)
                else -> return@launch
            }
            when (outcome) {
                is PresetCreationOutcome.Question -> filamentQuestion = outcome.question
                is PresetCreationOutcome.Failure -> {
                    filamentProblem = outcome.message
                    pendingCreation = null
                    pendingDeletion = null
                }
                is PresetCreationOutcome.Success -> {
                    pendingCreation = null
                    pendingDeletion = null
                    creatingFilament = false
                    loadCustomFilaments()
                    // The wizard's filament list follows the presets it made.
                    if (state.value.filaments.lines.isNotEmpty()) loadFilaments()
                }
            }
        }
    }

    internal fun setKeyword(keyword: String) = mutableState.update { it.copy(keyword = keyword) }

    internal fun toggleModel(id: String) = mutableState.update { it.copy(chosen = if (id in it.chosen) it.chosen - id else it.chosen + id) }

    internal fun toggleVendor(vendor: PrinterVendor) = mutableState.update { it.copy(chosen = PrinterPage.toggleVendor(it.chosen, vendor.models)) }

    /** GotoFilamentPage() */
    internal fun next() {
        val state = mutableState.value
        if (state.chosen.isEmpty()) {
            mutableState.update { it.copy(notice = SetupNotice.NO_PRINTER) }
            return
        }
        mutableState.update { it.copy(page = SetupPage.FILAMENTS) }
        loadFilaments()
    }

    private fun loadFilaments() {
        val state = mutableState.value
        val models = state.printers.filter { it.id in state.chosen }
        mutableState.update { it.copy(filaments = FilamentPageState(loading = true, models = models)) }
        viewModelScope.launch {
            val outcome = getSetupFilaments(models.map(SetupPrinterModel::id))
            mutableState.update { current ->
                if (current.filaments.models != models) return@update current
                when (outcome) {
                    is SetupFilamentsOutcome.Failure -> current.copy(filaments = current.filaments.copy(loading = false), error = outcome.message)
                    is SetupFilamentsOutcome.Success -> {
                        val lines = FilamentPage.lines(outcome.filaments)
                        val types = FilamentPage.types(lines)
                        val vendors = FilamentPage.vendors(lines)
                        val checked = FilamentPage.checked(outcome.filaments, lines)
                        current.copy(
                            filaments = FilamentPageState(
                                loading = false,
                                models = models,
                                lines = lines,
                                // SortUI(): without a selected filament, the defaults of the printers.
                                checked = checked.ifEmpty { FilamentPage.defaults(lines, models) },
                                machines = models.indices.toSet(),
                                types = types,
                                checkedTypes = types.toSet(),
                                vendors = vendors,
                                checkedVendors = vendors.toSet(),
                            ),
                        )
                    }
                }
            }
        }
    }

    internal fun toggleLine(index: Int) = updateFilaments { it.copy(checked = if (index in it.checked) it.checked - index else it.checked + index) }

    internal fun toggleMachine(index: Int) = updateFilaments { it.copy(machines = it.machines.toggled(index)) }

    /** ChooseAllMachine(): all printers, or none when all are shown. */
    internal fun toggleAllMachines() = updateFilaments { page ->
        page.copy(machines = if (page.machines.size == page.models.size) emptySet() else page.models.indices.toSet())
    }

    internal fun toggleType(type: String) = updateFilaments { it.copy(checkedTypes = it.checkedTypes.toggled(type)) }

    internal fun toggleAllTypes() = updateFilaments { page ->
        page.copy(checkedTypes = if (page.checkedTypes.size == page.types.size) emptySet() else page.types.toSet())
    }

    internal fun toggleFilamentVendor(vendor: String) = updateFilaments { it.copy(checkedVendors = it.checkedVendors.toggled(vendor)) }

    internal fun toggleAllFilamentVendors() = updateFilaments { page ->
        page.copy(checkedVendors = if (page.checkedVendors.size == page.vendors.size) emptySet() else page.vendors.toSet())
    }

    internal fun setSearch(search: String) = updateFilaments { it.copy(search = search, filter = FilamentFilter.ALL) }

    internal fun setFilter(filter: FilamentFilter) = updateFilaments { it.copy(filter = filter) }

    /** SelectAllFilament(): the listed lines are checked or cleared. */
    internal fun checkListed(checked: Boolean) = updateFilaments { page ->
        val listed = page.listedLines.toSet()
        page.copy(checked = if (checked) page.checked + listed else page.checked - listed)
    }

    /** ChooseDefaultFilament() */
    internal fun useDefaultFilaments() {
        updateFilaments { it.copy(checked = FilamentPage.defaults(it.lines, it.models)) }
        dismissNotice()
    }

    internal fun dismissNotice() = mutableState.update { it.copy(notice = null) }

    internal fun dismissError() = mutableState.update { it.copy(error = null) }

    /** FinishGuide() */
    internal fun finish() {
        val state = mutableState.value
        if (state.applying || state.filaments.loading) return
        if (state.filaments.checked.isEmpty()) {
            mutableState.update { it.copy(notice = SetupNotice.NO_FILAMENT) }
            return
        }
        val models = state.filaments.models.map(SetupPrinterModel::id)
        val filaments = FilamentPage.presets(state.filaments.lines, state.filaments.checked)
        apply { applySetup(models, filaments) }
    }

    /**
     * Back: the filament page returns to the printer page; otherwise the wizard
     * closes, and on first run OrcaSlicer's default printer is installed.
     */
    internal fun back() {
        val state = mutableState.value
        when {
            state.applying -> Unit
            state.page == SetupPage.FILAMENTS && state.start != SetupStart.FILAMENTS -> mutableState.update { it.copy(page = SetupPage.PRINTERS, notice = null) }
            state.start == SetupStart.FIRST_RUN -> apply { applySetup.defaults() }
            else -> mutableState.update { it.copy(finished = true) }
        }
    }

    private fun apply(change: suspend () -> PresetsOutcome?) {
        mutableState.update { it.copy(applying = true, error = null) }
        viewModelScope.launch {
            val outcome = change()
            mutableState.update {
                when (outcome) {
                    // The wizard applies its own presets, which have no unsaved changes.
                    is PresetsOutcome.Success, is PresetsOutcome.UnsavedChanges -> it.copy(applying = false, finished = true)
                    is PresetsOutcome.Failure -> it.copy(applying = false, error = outcome.message)
                    // The plate is busy: nothing changed yet.
                    null -> it.copy(applying = false)
                }
            }
        }
    }

    private fun updateFilaments(transform: (FilamentPageState) -> FilamentPageState) =
        mutableState.update { it.copy(filaments = transform(it.filaments)) }

    private fun <T> Set<T>.toggled(value: T): Set<T> = if (value in this) this - value else this + value
}
