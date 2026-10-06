package app.orcinus.shadow.slicing.api

import app.orcinus.shadow.core.model.PresetsOutcome
import app.orcinus.shadow.core.model.ProfileCheckAnswer
import app.orcinus.shadow.core.model.ProfileDownload
import app.orcinus.shadow.core.model.ProfileUpdate
import app.orcinus.shadow.core.model.ProfileUpdateRequest

/**
 * OrcaSlicer's PresetUpdater for the system profiles of a vendor: the engine
 * says what to ask the server, keeps the bundle the server names in its cache
 * (data_dir/ota/profiles), and installs from there the bundles newer than the
 * installed ones. Asking the server and downloading are the app's.
 */
interface ProfileUpdater {
    /**
     * sync() at start-up ([startup], which also removes the downloads a check
     * left behind) and check_vendor_update() when another printer is selected.
     */
    suspend fun profileUpdateRequest(startup: Boolean): ProfileUpdateRequest

    /**
     * sync_vendor_config() with the server's [answer] to the check of [vendor]:
     * the newer bundle it names, with the vendor's cache cleared for it; null
     * when it names none.
     */
    suspend fun profileUpdateAnswer(vendor: String, answer: ProfileCheckAnswer): ProfileDownload?

    /** extract_file(): the bundle downloaded for [vendor] unpacked into the cache; false when it could not be. */
    suspend fun cacheProfileUpdate(vendor: String): Boolean

    /** get_config_updates(): the bundles of the cache newer than the installed ones. */
    suspend fun profileUpdates(): List<ProfileUpdate>

    /** perform_updates(): the bundles of the cache copied over the installed ones; false when a copy failed. */
    suspend fun performProfileUpdates(): Boolean

    /**
     * reload_configs_update_gui() after its question about unsaved changes: the
     * presets loaded again with the installed bundles, and every tab with them.
     */
    suspend fun reloadSystemPresets(): PresetsOutcome
}
