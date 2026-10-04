package app.orcinus.shadow.domain.preferences

import app.orcinus.shadow.core.model.AppConfigOutcome
import app.orcinus.shadow.slicing.api.AppConfigStore

/**
 * The last choices of the send dialogs (FlashforgePrintHostSendDialog,
 * ElegooPrintHostSendDialog), which OrcaSlicer.conf keeps in its "recent"
 * section: the dialogs open with them and write them when they upload.
 */
class RecentSendChoicesUseCase(private val store: AppConfigStore) {
    /** AppConfig::get("recent", key) of every key; empty for a key never set. */
    suspend fun get(keys: List<String>): Map<String, String> =
        (store.appConfigValues(keys, AppConfigStore.RECENT_SECTION) as? AppConfigOutcome.Success)?.values.orEmpty()

    /** AppConfig::set("recent", key, value) of every one. */
    suspend fun set(values: Map<String, String>) {
        values.forEach { (key, value) -> store.setAppConfigValue(key, value, AppConfigStore.RECENT_SECTION) }
    }
}
