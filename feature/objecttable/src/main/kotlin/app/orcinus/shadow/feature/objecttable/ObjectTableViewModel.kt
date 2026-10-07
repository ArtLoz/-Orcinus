package app.orcinus.shadow.feature.objecttable

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.orcinus.shadow.core.model.BedShapeOutcome
import app.orcinus.shadow.core.model.ColorRgba
import app.orcinus.shadow.core.model.GcodePlaceholderInfo
import app.orcinus.shadow.core.model.GcodePlaceholdersOutcome
import app.orcinus.shadow.core.model.OrcaText
import app.orcinus.shadow.core.model.PlateObject
import app.orcinus.shadow.core.model.PlateState
import app.orcinus.shadow.core.model.PresetKind
import app.orcinus.shadow.core.model.PresetNameOutcome
import app.orcinus.shadow.core.model.PresetNamesOutcome
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.SettingsItem
import app.orcinus.shadow.core.model.SettingsRequest
import app.orcinus.shadow.core.model.SettingsTabState
import app.orcinus.shadow.core.model.parseFilamentColor
import app.orcinus.shadow.domain.plate.ObjectTableColumn
import app.orcinus.shadow.domain.plate.ObjectTableRow
import app.orcinus.shadow.domain.plate.ObjectTableUseCase
import app.orcinus.shadow.domain.plate.ObservePlateUseCase
import app.orcinus.shadow.domain.plate.PresetSettingsTabs
import app.orcinus.shadow.domain.plate.objectTableRows
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** What the Parameter Table shows. */
data class ObjectTableUiState(
    /** The rows in the plate's order (ObjectGridTable::construct_object_configs()). */
    val rows: List<ObjectTableRow> = emptyList(),
    /** The objects of the plate, which name the rows. */
    val objects: List<PlateObject> = emptyList(),
    /** ObjectTablePanel::init_filaments_and_colors(): every filament of the plate. */
    val filaments: List<ObjectTableFilament> = emptyList(),
    /** The table edits: the presets are known and the plate is not busy. */
    val enabled: Boolean = false,
    /**
     * The tabs of an object and of a volume, which the side panel shows for
     * the selected row and whose message boxes the table's edits raise.
     */
    val objectTab: SettingsTabState = SettingsTabState(PresetKind.OBJECT),
    val partTab: SettingsTabState = SettingsTabState(PresetKind.PART),
)

/** A filament of the plate as the filament column offers it: "N: preset" and its colour, none when it cannot be read. */
data class ObjectTableFilament(val name: String, val color: ColorRgba?)

class ObjectTableViewModel(
    observePlate: ObservePlateUseCase,
    private val table: ObjectTableUseCase,
    private val settingsTabs: PresetSettingsTabs,
) : ViewModel() {
    val state: StateFlow<ObjectTableUiState> = observePlate()
        .map(PlateState::toObjectTableUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), observePlate().value.toObjectTableUiState())

    init {
        // A row takes what its object does not override from the process
        // preset, which the process tab describes.
        if (observePlate().value.settingsTabs[PresetKind.PRINT]?.settings == null) {
            settingsTabs.request(PresetKind.PRINT, SettingsRequest.Describe)
        }
    }

    fun select(row: ObjectTableRow) = table.select(row.item)

    fun change(row: ObjectTableRow, column: ObjectTableColumn, text: String) = table.change(row.item, column, text)

    fun reset(row: ObjectTableRow, column: ObjectTableColumn) = table.reset(row.item, column)

    fun setPrintable(mesh: ScenePath, printable: Boolean) = table.setPrintable(mesh, printable)

    fun setFilament(row: ObjectTableRow, filament: Int) = table.setFilament(row.item, filament)

    fun rename(row: ObjectTableRow, name: String) = table.rename(row.item, name)

    /** The side panel's requests, which stay on the row's item (ObjectTableSettings edits the row's config). */
    fun requestSettings(item: SettingsItem, request: SettingsRequest) = settingsTabs.request(item, request)

    fun answerSettingsQuestion(kind: PresetKind, yes: Boolean) = settingsTabs.answer(kind, yes)

    fun dismissSettingsNotice(kind: PresetKind) = settingsTabs.dismissNotice(kind)

    suspend fun settingTooltip(kind: PresetKind, id: String): List<OrcaText> = settingsTabs.tooltip(kind, id)

    suspend fun checkPresetName(kind: PresetKind, name: String): PresetNameOutcome = settingsTabs.checkPresetName(kind, name)

    suspend fun compatiblePresetChoices(kind: PresetKind, key: String): PresetNamesOutcome = settingsTabs.compatiblePresetChoices(kind, key)

    suspend fun bedShape(): BedShapeOutcome = settingsTabs.bedShape()

    suspend fun gcodePlaceholders(kind: PresetKind, key: String): GcodePlaceholdersOutcome = settingsTabs.gcodePlaceholders(kind, key)

    suspend fun gcodePlaceholder(key: String, presets: Boolean): GcodePlaceholderInfo = settingsTabs.gcodePlaceholder(key, presets)

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

private fun PlateState.toObjectTableUiState() = ObjectTableUiState(
    rows = objectTableRows(),
    objects = objects,
    // m_filaments_name: "N: " and the preset of the filament; m_filaments_colors: filament_colour.
    filaments = profiles?.allFilaments.orEmpty().mapIndexed { index, preset ->
        ObjectTableFilament("${index + 1}: ${preset.value}", presets?.filamentColors?.getOrNull(index)?.let(::parseFilamentColor))
    },
    enabled = profiles != null && !busy,
    objectTab = settingsTabs[PresetKind.OBJECT] ?: SettingsTabState(PresetKind.OBJECT),
    partTab = settingsTabs[PresetKind.PART] ?: SettingsTabState(PresetKind.PART),
)
