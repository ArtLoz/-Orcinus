package app.orcinus.shadow.core.model

/**
 * PresetUpdater::sync() and check_vendor_update(): the [vendor] of the edited
 * printer preset, empty for a printer of the user's own, and the [url] its
 * check asks, empty when it is not asked: in stealth mode, without "Update
 * built-in presets automatically.", or when the vendor was asked about before
 * while the app runs. [enabled] is whether checks are made at all.
 */
data class ProfileUpdateRequest(val enabled: Boolean, val vendor: String, val url: String)

/**
 * What the server answered to the check of a vendor: the HTTP [status], 0 when
 * no answer came, its [body], and the [error] that came instead of a success.
 */
data class ProfileCheckAnswer(val status: Int, val body: String, val error: String = "")

/**
 * The newer bundle the server named for a vendor: where it is ([url]) and the
 * file of the engine's cache it is downloaded into ([path]).
 */
data class ProfileDownload(val url: String, val path: String)

/**
 * A configuration package of the cache newer than the installed one
 * (PresetUpdater's Update of a vendor's bundle): its [vendor], the [version]
 * the cache has, the [changelog] kept beside it, and whether it installs
 * without asking ([forced], force_update).
 */
data class ProfileUpdate(val vendor: String, val version: String, val changelog: String, val forced: Boolean)

/**
 * PresetUpdater's waiting updates (has_waiting_updates): the configuration
 * packages it offers to install.
 */
data class ProfileUpdatesNotice(
    val updates: List<ProfileUpdate>,
    /** PresetUpdateAvailable's "Configuration can update now.", until "Detail." or its close button. */
    val notified: Boolean = true,
    /** MsgUpdateConfig, which "Detail." opens, until it is answered. */
    val confirming: Boolean = false,
)
