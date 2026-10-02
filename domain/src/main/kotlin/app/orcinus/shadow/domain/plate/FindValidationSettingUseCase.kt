package app.orcinus.shadow.domain.plate

import app.orcinus.shadow.core.model.PresetKind
import app.orcinus.shadow.core.model.SearchCatalogOutcome
import app.orcinus.shadow.core.model.SearchOption

/**
 * "Jump to" of a validation message that names a setting (opt_key):
 * resolve_validation_option_type() takes the printer's tab when it has the
 * setting, then the filament's, and the process's otherwise, and
 * Sidebar::jump_to_option() opens that tab on it. OrcaSlicer asks the tabs'
 * config definition, which holds every setting, so it always takes the
 * printer's tab and finds nothing there for a process setting; the app takes
 * the tab that shows the setting, by the settings search's catalogue.
 */
class FindValidationSettingUseCase(private val settingsTabs: PresetSettingsTabs) {
    suspend operator fun invoke(key: String): SearchOption? {
        val options = (settingsTabs.searchCatalog() as? SearchCatalogOutcome.Success)?.options ?: return null
        return KIND_ORDER.firstNotNullOfOrNull { kind -> options.firstOrNull { it.kind == kind && it.key == key } }
    }

    private companion object {
        val KIND_ORDER = listOf(PresetKind.PRINTER, PresetKind.FILAMENT, PresetKind.PRINT)
    }
}
