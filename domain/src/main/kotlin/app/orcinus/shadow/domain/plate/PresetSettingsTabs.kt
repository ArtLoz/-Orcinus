package app.orcinus.shadow.domain.plate

import app.orcinus.shadow.core.model.BedShape
import app.orcinus.shadow.core.model.BedShapeOutcome
import app.orcinus.shadow.core.model.ComparedPresets
import app.orcinus.shadow.core.model.FilamentTemperatureWarning
import app.orcinus.shadow.core.model.GcodePlaceholderInfo
import app.orcinus.shadow.core.model.FlushVolumesChange
import app.orcinus.shadow.core.model.GcodePlaceholdersOutcome
import app.orcinus.shadow.core.model.ModelPath
import app.orcinus.shadow.core.model.PresetComparisonOutcome
import app.orcinus.shadow.core.model.PresetSave
import app.orcinus.shadow.core.model.SearchCatalogOutcome
import app.orcinus.shadow.core.model.ModelSettings
import app.orcinus.shadow.core.model.withLayerRangeAt
import app.orcinus.shadow.core.model.withVolumeAt
import app.orcinus.shadow.core.model.ModelSettingsRequest
import app.orcinus.shadow.core.model.OrcaText
import app.orcinus.shadow.core.model.PendingSettingsQuestion
import app.orcinus.shadow.core.model.PlateObject
import app.orcinus.shadow.core.model.PlateState
import app.orcinus.shadow.core.model.PresetKind
import app.orcinus.shadow.core.model.PresetNameOutcome
import app.orcinus.shadow.core.model.PresetNamesOutcome
import app.orcinus.shadow.core.model.PresetSettings
import app.orcinus.shadow.core.model.PresetSettingsOutcome
import app.orcinus.shadow.core.model.PresetsOutcome
import app.orcinus.shadow.core.model.SettingsRequest
import app.orcinus.shadow.core.model.SettingsTabOutcome
import app.orcinus.shadow.core.model.SettingsTabState
import app.orcinus.shadow.core.model.mesh
import app.orcinus.shadow.core.model.withSettings
import app.orcinus.shadow.core.model.volumeAt
import app.orcinus.shadow.slicing.api.PresetManager
import app.orcinus.shadow.slicing.api.PresetSettingsEditor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * OrcaSlicer's settings tabs: the engine edits the selected presets, and the
 * plate state mirrors the tabs. Requests run one after another in the
 * application scope. A question stops a request until it is answered, and the
 * request then runs again with every answer so far. A change of the settings
 * discards the G-code sliced before it; the sidebar's preset lists follow a
 * preset's label, and a saved or deleted preset. A tab is described when the
 * app first asks for it, and then again whenever the presets change.
 */
class PresetSettingsTabs(
    private val editor: PresetSettingsEditor,
    private val presetManager: PresetManager,
    private val flushVolumes: FlushVolumesUpdater,
    private val repository: PlateRepository,
    private val applicationScope: CoroutineScope,
    /**
     * Plater::on_config_change(), which Tab::update() calls after a change of
     * the values or of the preset a tab edits (auto slice after changes).
     */
    private val onConfigChange: () -> Unit = {},
    /** Plater::on_filament_count_change() of the objects and plates. */
    private val filamentRenumbering: PlateFilamentRenumbering? = null,
    /**
     * Sidebar::on_filament_count_change(): the filament slots follow the
     * extruder count of the printer. The plate is built after this class, so it
     * is reached through a provider.
     */
    private val platePresets: () -> PresetsApplier = { PresetsApplier { _, _ -> } },
) {
    private val requests = Mutex()

    /** Describes the tabs the app has opened again; the presets call it when they change. */
    suspend fun refresh() {
        for (kind in openTabs()) {
            run(kind, SettingsRequest.Describe, emptyMap())
        }
    }

    /** Runs [request] unless the plate cannot change now: a slice, an import, or a preset change is running. */
    fun request(kind: PresetKind, request: SettingsRequest) {
        val state = repository.state.value
        if (state.profiles == null || state.busy || state.tab(kind).question != null) return
        // The settings of an object need the objects the plate has selected.
        if (kind == PresetKind.OBJECT && state.selectedInstances.isEmpty()) return
        applicationScope.launch { run(kind, request, emptyMap()) }
    }

    /** Answers the pending question of the tab, and runs its request again. */
    fun answer(kind: PresetKind, yes: Boolean) {
        var question: PendingSettingsQuestion? = null
        repository.update { state ->
            question = state.tab(kind).question
            state.withTab(kind) { copy(question = null) }
        }
        val pending = question ?: return
        applicationScope.launch { run(kind, pending.request, pending.answers + (pending.dialog.id to yes)) }
    }

    /**
     * Saves the edited preset as [save] says and waits for it, as the Save
     * button of the unsaved-changes dialog does before the preset is selected.
     */
    suspend fun save(kind: PresetKind, save: PresetSave) {
        run(kind, SettingsRequest.Save(save), emptyMap())
    }

    /**
     * Tab::validate_filament_temperature_pairs(): what the Temperature Safety
     * Check says of the edited filament preset before it is saved or its page
     * closes; null when it says nothing.
     */
    suspend fun filamentTemperatureWarning(): FilamentTemperatureWarning? = editor.filamentTemperatureWarning()

    /** Its "Don't warn again for this preset". */
    suspend fun suppressFilamentTemperatureWarning(preset: String) = editor.suppressFilamentTemperatureWarning(preset)

    /** The first notice of the tab was dismissed. */
    fun dismissNotice(kind: PresetKind) = repository.update { state -> state.withTab(kind) { copy(notices = notices.drop(1)) } }

    suspend fun tooltip(kind: PresetKind, id: String): List<OrcaText> = editor.settingTooltip(kind, id)

    suspend fun checkPresetName(kind: PresetKind, name: String): PresetNameOutcome = editor.checkPresetName(kind, name)

    suspend fun compatiblePresetChoices(kind: PresetKind, key: String): PresetNamesOutcome = editor.compatiblePresetChoices(kind, key)

    /** Search::OptionsSearcher: every setting the preset tabs show. */
    suspend fun searchCatalog(): SearchCatalogOutcome = editor.searchCatalog()

    /**
     * DiffPresetDialog: the presets each side of the dialog can select, and what
     * the ones it selects differ in; [showAll] lists the incompatible presets too.
     */
    suspend fun comparePresets(left: ComparedPresets, right: ComparedPresets, showAll: Boolean): PresetComparisonOutcome =
        editor.comparePresets(left, right, showAll)

    /** EditGCodeDialog: the custom G-code [key] of the tab of [kind], and the placeholders it lists. */
    suspend fun gcodePlaceholders(kind: PresetKind, key: String): GcodePlaceholdersOutcome = editor.gcodePlaceholders(kind, key)

    /** What EditGCodeDialog says about the placeholder [key]; [presets] for one of the "Presets" group. */
    suspend fun gcodePlaceholder(key: String, presets: Boolean): GcodePlaceholderInfo = editor.gcodePlaceholder(key, presets)

    /** BedShapeDialog: the printable area of the edited printer. */
    suspend fun bedShape(): BedShapeOutcome = editor.bedShape()

    /**
     * The shape the dialog was closed with, written into the edited printer
     * preset; the printer tab then shows it. Whether it succeeded is what the
     * caller needs, since the plate follows the new area (SetBedShapeUseCase).
     */
    suspend fun setBedShape(shape: BedShape): Boolean {
        val outcome = editor.setBedShape(shape)
        when (outcome) {
            is PresetSettingsOutcome.Failure ->
                repository.update { state -> state.withTab(PresetKind.PRINTER) { copy(problem = outcome.message) } }
            is PresetSettingsOutcome.Question -> repository.update { state ->
                state.withTab(PresetKind.PRINTER) {
                    copy(question = PendingSettingsQuestion(outcome.question, SettingsRequest.Describe, emptyMap()))
                }
            }
            is PresetSettingsOutcome.Success -> apply(PresetKind.PRINTER, outcome)
        }
        return outcome is PresetSettingsOutcome.Success
    }

    /**
     * The process tab, which the sidebar always shows, and the tabs of the
     * filament and the printer once their page has opened one.
     */
    private fun openTabs(): List<PresetKind> =
        listOf(PresetKind.PRINT) + repository.state.value.settingsTabs.values
            .filter { it.kind != PresetKind.PRINT && it.settings != null }
            .filter { it.kind != PresetKind.OBJECT || repository.state.value.selectedInstances.isNotEmpty() }
            .map { it.kind }

    private suspend fun run(kind: PresetKind, request: SettingsRequest, answers: Map<String, Boolean>) =
        requests.withLock { perform(kind, request, answers) }

    /** The request itself, which another request of the same run may follow. */
    private suspend fun perform(kind: PresetKind, request: SettingsRequest, answers: Map<String, Boolean>) {
        if (repository.state.value.profiles == null) return
        repository.update { state -> state.withTab(kind) { copy(changing = true, problem = null) } }
        if (repository.state.value.tab(kind).tab == null) {
            when (val tab = editor.settingsTab(kind)) {
                is SettingsTabOutcome.Failure -> {
                    repository.update { state -> state.withTab(kind) { copy(changing = false, problem = tab.message) } }
                    return
                }
                is SettingsTabOutcome.Success -> repository.update { state -> state.withTab(kind) { copy(tab = tab.tab) } }
            }
        }
        val page = repository.state.value.tab(kind).page
        // The tab of an object or of the plate is described with the overrides
        // the plate keeps, since the engine holds no plate of its own.
        val model = repository.state.value.modelRequest(kind)
        val outcome = when (request) {
            SettingsRequest.Describe -> editor.settings(kind, page, answers, model)
            is SettingsRequest.SelectPage -> editor.settings(kind, request.page, answers, model)
            is SettingsRequest.Change -> editor.changeSetting(kind, page, request.id, request.text, answers, model)
            is SettingsRequest.Reset -> editor.resetSettings(kind, page, request.ids, answers, model)
            is SettingsRequest.SetOverride -> editor.setSettingOverride(kind, page, request.id, request.enabled, answers)
            is SettingsRequest.SetCompatible -> editor.setCompatiblePresets(kind, page, request.key, request.presets, answers)
            is SettingsRequest.SetMode -> editor.setSettingsMode(kind, request.mode, model)
            is SettingsRequest.SetVariant -> editor.setSettingsVariant(kind, page, request.variant, answers, model)
            is SettingsRequest.EditCustomGcode -> editor.editCustomGcode(kind, page, request.key, request.gcode, answers)
            is SettingsRequest.SetRammingParameters -> editor.setRammingParameters(kind, page, request.parameters, answers)
            is SettingsRequest.Save -> editor.savePreset(kind, request.save.name, request.save.detach, request.save.toProject)
            is SettingsRequest.SaveConnection -> editor.savePrinterConnection(request.settings, request.name)
            SettingsRequest.Delete -> editor.deletePreset(kind, answers)
        }
        when (outcome) {
            is PresetSettingsOutcome.Failure ->
                repository.update { state -> state.withTab(kind) { copy(changing = false, problem = outcome.message) } }
            is PresetSettingsOutcome.Question -> repository.update { state ->
                val repeated = if (outcome.loadsSelection) SettingsRequest.Describe else request
                val kept = if (outcome.loadsSelection) emptyMap() else answers
                state.withTab(kind) { copy(changing = false, question = PendingSettingsQuestion(outcome.question, repeated, kept)) }
            }
            is PresetSettingsOutcome.Success -> {
                apply(kind, outcome)
                if (request is SettingsRequest.SaveConnection) repository.update { it.copy(connectionSaves = it.connectionSaves + 1) }
                // Tab::update_dirty() and on_presets_changed() of a preset's tab end
                // with update_project_dirty_from_presets() once its values change.
                if (kind in PRESET_KINDS && request.editsPreset()) repository.update { it.withOtherChanges() }
                // Tab::on_value_change(): the long retractions change how much a
                // filament change flushes.
                if (request is SettingsRequest.Change && request.id.substringBefore('#') in LONG_RETRACTION_KEYS) {
                    flushVolumes.update(FlushVolumesChange.LONG_RETRACTION_CHANGED, -1)
                }
                // Tab::on_value_change(): get_tab(TYPE_PRINT)->update(), since
                // these printer settings change what the process tab shows.
                if (request is SettingsRequest.Change && request.id.substringBefore('#') in PROCESS_TAB_KEYS) {
                    perform(PresetKind.PRINT, SettingsRequest.Describe, emptyMap())
                }
                // GUI_App::update_mode(): the mode is the app's, so every tab shows its settings anew.
                if (request is SettingsRequest.SetMode) {
                    for (other in openTabs() - kind) perform(other, SettingsRequest.Describe, emptyMap())
                }
                // Tab::on_value_change(): set_num_filaments() gave the plate as
                // many filaments as the printer has extruders, and
                // Sidebar::on_filament_count_change() rebuilds their combo boxes.
                if (request is SettingsRequest.Change && request.id.substringBefore('#') == EXTRUDERS_COUNT_KEY) {
                    val before = repository.state.value.profiles
                    platePresets().apply(before, presetManager.presets())
                    // MainFrame::on_value_changed(): Plater::on_filament_count_change() of the extruders.
                    repository.state.value.profiles?.allFilaments?.size?.let { count ->
                        filamentRenumbering?.countChanged(count, before?.allFilaments?.size)
                    }
                }
                // Plater::on_config_change(): the bed is built anew when a printer
                // setting of its shape changes, and when a reset takes the printer
                // back to its saved preset, which may hold another bed.
                if (kind == PresetKind.PRINTER &&
                    (request is SettingsRequest.Reset || (request is SettingsRequest.Change && request.id.substringBefore('#') in BED_SHAPE_KEYS))
                ) {
                    platePresets().apply(null, presetManager.presets())
                }
                // TabPrintPlate::on_value_change(): "Customize" of a filament sequence,
                // which the plate takes in the filaments' order, posts
                // EVT_OPEN_PLATESETTINGSDIALOG with "only_layer_sequence".
                if (kind == PresetKind.PLATE && request is SettingsRequest.Change &&
                    request.id.substringBefore('#') in LAYER_SEQUENCE_CHOICES && request.text == CUSTOMIZE
                ) {
                    repository.update { it.copy(layerSequencePrompt = true) }
                }
            }
        }
    }

    private suspend fun apply(kind: PresetKind, outcome: PresetSettingsOutcome.Success) {
        var before: PresetSettings? = null
        var valuesChanged = false
        repository.update { state ->
            before = state.tab(kind).settings
            // Tab::on_value_change() of an object, a part, a range or the plate: "Change Option".
            val changed = state.withModelSettings(kind, outcome.settings.modelSettings)
            val model = changed.objects != state.objects || changed.plateSettings != state.plateSettings
            valuesChanged = model || changesSlicing(state.tab(kind).settings, outcome.settings)
            (if (model) state.recorded() else state)
                .withTab(kind) {
                    copy(settings = outcome.settings, page = outcome.settings.activePage, changing = false, notices = notices + outcome.notices)
                }
                // What the object or the plate overrides after the request.
                .withModelSettings(kind, outcome.settings.modelSettings)
                .copy(result = state.result.takeUnless { !state.keepsGcode && changesSlicing(before, outcome.settings) })
        }
        val previous = before
        if (previous == null || previous.preset != outcome.settings.preset || previous.label != outcome.settings.label) {
            val presets = presetManager.presets() as? PresetsOutcome.Success ?: return
            repository.update { state ->
                state.copy(presets = presets.presets).let { updated ->
                    updated.copy(result = state.result.takeIf { state.keepsGcode || updated.profiles == state.profiles })
                }
            }
        }
        // A tab only described for the first time changes nothing.
        if (valuesChanged || (previous != null && previous.preset != outcome.settings.preset)) onConfigChange()
    }

    /** A request that changes the values or the presets of a tab, rather than what it shows. */
    private fun SettingsRequest.editsPreset(): Boolean = when (this) {
        is SettingsRequest.Change, is SettingsRequest.Reset, is SettingsRequest.SetOverride, is SettingsRequest.SetCompatible,
        is SettingsRequest.SetRammingParameters, is SettingsRequest.EditCustomGcode, is SettingsRequest.Save, SettingsRequest.Delete -> true
        else -> false
    }

    /** The same preset with other values slices differently. */
    private fun changesSlicing(before: PresetSettings?, after: PresetSettings): Boolean =
        before != null && before.preset == after.preset &&
            before.settings.associate { it.id to it.value } != after.settings.associate { it.id to it.value }

    /** What the tab of an object or of the plate is described with. */
    private fun PlateState.modelRequest(kind: PresetKind): ModelSettingsRequest = when (kind) {
        PresetKind.PLATE -> ModelSettingsRequest(settings = listOf(plateSettings), plate = plateSettings)
        PresetKind.OBJECT -> ModelSettingsRequest(settings = selectedObjects().map(PlateObject::settings), plate = plateSettings)
        // A part's settings sit on the ones of the object it belongs to
        // (TabPrintPart::m_parent_tab is the model tab); several volumes of
        // it are edited at once, as the tab takes several model configs.
        PresetKind.PART -> selectedPartOwner?.let { owner ->
            val parts = selectedParts().mapNotNull { owner.volumeAt(it.index) }
            ModelSettingsRequest(settings = parts.map { it.settings }, plate = plateSettings, parent = owner.settings)
        }?.takeIf { it.settings.isNotEmpty() } ?: ModelSettingsRequest()
        // A height range sits on the settings of its object in the same way.
        PresetKind.LAYER -> selectedLayerRange?.let { range ->
            ModelSettingsRequest(
                settings = listOf(range.settings),
                plate = plateSettings,
                parent = selectedRangeOwner?.settings ?: ModelSettings(),
            )
        } ?: ModelSettingsRequest()
        else -> ModelSettingsRequest()
    }

    /** The overrides the request answered with, kept with the objects or the plate. */
    private fun PlateState.withModelSettings(kind: PresetKind, settings: List<ModelSettings>?): PlateState = when {
        settings == null -> this
        kind == PresetKind.PLATE -> copy(plateSettings = settings.firstOrNull() ?: plateSettings)
        kind == PresetKind.OBJECT -> {
            // The answers come in the order the request carried the objects.
            val answered = selectedObjects().map(PlateObject::mesh).zip(settings).toMap()
            copy(objects = objects.map { answered[it.mesh]?.let(it::withSettings) ?: it })
        }
        kind == PresetKind.LAYER -> {
            val id = selectedRange
            val range = selectedLayerRange
            val answered = settings.firstOrNull()
            if (id == null || range == null || answered == null) this
            else copy(
                objects = objects.map { plateObject ->
                    if (plateObject.mesh == id.mesh) plateObject.withLayerRangeAt(id.index, range.copy(settings = answered)) else plateObject
                },
            )
        }
        kind == PresetKind.PART -> {
            // The answers come in the order the request carried the volumes.
            val owner = selectedPartOwner
            val answered = selectedParts().zip(settings)
            if (owner == null || answered.isEmpty()) this
            else copy(
                objects = objects.replaced(
                    answered.fold(owner) { plateObject, (id, values) ->
                        plateObject.volumeAt(id.index)?.let { plateObject.withVolumeAt(id.index, it.copy(settings = values)) } ?: plateObject
                    },
                ),
            )
        }
        else -> this
    }

    private fun PlateState.tab(kind: PresetKind): SettingsTabState = settingsTabs[kind] ?: SettingsTabState(kind)

    private companion object {
        /** The tabs of the printer, filament and process presets, whose changes the project keeps (put_other_changes()). */
        val PRESET_KINDS = setOf(PresetKind.PRINTER, PresetKind.FILAMENT, PresetKind.PRINT)

        /** The printer's and a filament's long retractions when the filament is cut. */
        val LONG_RETRACTION_KEYS = setOf("long_retractions_when_cut", "filament_long_retractions_when_cut")

        /** The printer settings the process tab is shown again after. */
        val PROCESS_TAB_KEYS = setOf("single_extruder_multi_material", "purge_in_prime_tower")

        /** The printer setting the plate's filament slots follow. */
        const val EXTRUDERS_COUNT_KEY = "extruders_count"

        /** Plater::on_config_change()'s keys of bed_shape_changed. */
        val BED_SHAPE_KEYS = setOf(
            "printable_area",
            "bed_exclude_area",
            "bed_custom_texture",
            "bed_custom_model",
            "extruder_clearance_height_to_lid",
            "extruder_clearance_height_to_rod",
        )

        /** The plate's choices of a filament sequence, and their LayerSeq::flsCustomize. */
        val LAYER_SEQUENCE_CHOICES = setOf("first_layer_sequence_choice", "other_layers_sequence_choice")
        const val CUSTOMIZE = "Customize"
    }

    private fun PlateState.withTab(kind: PresetKind, change: SettingsTabState.() -> SettingsTabState) =
        copy(settingsTabs = settingsTabs + (kind to tab(kind).change()))
}
