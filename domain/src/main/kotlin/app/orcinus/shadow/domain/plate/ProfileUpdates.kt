package app.orcinus.shadow.domain.plate

import app.orcinus.shadow.core.model.ProfileCheckAnswer
import app.orcinus.shadow.core.model.ProfileUpdatesNotice
import app.orcinus.shadow.slicing.api.ProfileUpdater
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The network OrcaSlicer's PresetUpdater asks: the check of a vendor's profiles, and the bundle it names. */
interface ProfileUpdateSource {
    /** Http::get() of the check at [url]. */
    suspend fun check(url: String): ProfileCheckAnswer

    /** Http::get() of the bundle at [url], written to the file [target]; false when it was not. */
    suspend fun download(url: String, target: String): Boolean
}

/**
 * OrcaSlicer's PresetUpdater for the system profiles (sync_vendor_config()
 * and config_update()). At start-up and when another printer is selected, the
 * printer's vendor is asked about once while the app runs; a newer bundle the
 * server names is cached, then installed at once when it is forced, or
 * offered with "Configuration can update now." otherwise. Installing asks
 * about the presets' unsaved changes and loads the presets anew
 * (reload_configs_update_gui()).
 *
 * The desktop app checks the vendor of a printer selected in stealth mode or
 * without "Update built-in presets automatically." too; here both settings
 * keep every check from being made, as they keep the one at start-up.
 */
class ProfileUpdatesUseCase(
    private val updater: ProfileUpdater,
    private val source: ProfileUpdateSource,
    private val repository: PlateRepository,
    private val platePresets: PresetsApplier,
    /** GUI_App::check_and_save_current_preset_changes() of the configuration updates; false for Cancel. */
    private val savePresetChanges: suspend () -> Boolean,
    private val applicationScope: CoroutineScope,
) {
    private val started = AtomicBoolean(false)

    /** One update is checked or installed at a time, as the desktop app's main thread does it. */
    private val updating = Mutex()

    /** GUI_App::on_init_inner()'s preset_updater->sync(): the vendor of the printer the app starts with. */
    fun syncAtStartup() {
        if (!started.compareAndSet(false, true)) return
        applicationScope.launch { sync(startup = true) }
    }

    /** Tab::on_presets_changed() of the printer tab: check_vendor_update() of the printer's vendor. */
    fun printerChanged() {
        applicationScope.launch { sync(startup = false) }
    }

    /**
     * MsgUpdateConfig's OK ([install]) or Cancel. OK installs the bundles
     * (on_update_notification_confirm()) and loads the presets anew.
     */
    fun answer(install: Boolean) {
        if (repository.state.value.profileUpdates == null) return
        repository.update { it.copy(profileUpdates = null) }
        if (!install) return
        applicationScope.launch {
            updating.withLock {
                if (updater.performProfileUpdates()) reload()
            }
        }
    }

    /** sync_vendor_config(): the check, the download, and the bundle cached. */
    private suspend fun sync(startup: Boolean) {
        val request = updater.profileUpdateRequest(startup)
        if (request.url.isEmpty()) return
        val download = updater.profileUpdateAnswer(request.vendor, source.check(request.url)) ?: return
        if (!source.download(download.url, download.path)) return
        if (!updater.cacheProfileUpdate(request.vendor)) return
        // GUI_App::check_config_updates_from_updater(): check_updates(false).
        updating.withLock { configUpdate() }
    }

    /** PresetUpdater::config_update() with SHOW_NOTIFICATION. */
    private suspend fun configUpdate() {
        val updates = updater.profileUpdates()
        if (updates.isEmpty()) return
        if (updates.any { it.forced }) {
            // The desktop app closes when the question about unsaved changes is
            // cancelled (R_INCOMPAT_EXIT); here the bundles stay installed and
            // load with the presets the next time.
            if (!updater.performProfileUpdates() || !reload()) return
            repository.update { it.copy(profileUpdatesInstalled = it.profileUpdatesInstalled + updates) }
            return
        }
        repository.update { it.copy(profileUpdates = ProfileUpdatesNotice(updates)) }
    }

    /** reload_configs_update_gui(): false when the question about unsaved changes was cancelled. */
    private suspend fun reload(): Boolean {
        // The question waits for any other question of the app to be answered first.
        repository.state.first { it.projectPrompt == null }
        if (!savePresetChanges()) return false
        // Plater::set_bed_shape(): the plate is described again, as at start-up.
        platePresets.apply(before = null, outcome = updater.reloadSystemPresets())
        return true
    }
}
