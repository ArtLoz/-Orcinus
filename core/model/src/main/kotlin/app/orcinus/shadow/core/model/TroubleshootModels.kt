package app.orcinus.shadow.core.model

/** A line of the Troubleshoot Center's profile counts (create_item_loaded_profiles()): Active, then System + User. */
data class ProfileCounts(
    val active: Int,
    val system: Int,
    val user: Int,
)

/**
 * TroubleshootDialog's Profiles section: how many printers, filaments and
 * processes are active, system and the user's own, and the overview of the
 * loaded profiles (GetProfilesOverview()) that its "Export..." writes as JSON.
 */
data class ProfilesOverview(
    val printers: ProfileCounts,
    val filaments: ProfileCounts,
    val processes: ProfileCounts,
    val json: String,
    /** "Clean system profiles cache" was confirmed: the engine's next start removes the folder and installs the bundles anew. */
    val systemCleaned: Boolean = false,
)
