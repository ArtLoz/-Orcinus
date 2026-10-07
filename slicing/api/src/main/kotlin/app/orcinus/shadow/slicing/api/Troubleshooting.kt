package app.orcinus.shadow.slicing.api

import app.orcinus.shadow.core.model.ProfileCounts
import app.orcinus.shadow.core.model.ProfilesOverview

/** What OrcaSlicer's Troubleshoot Center (TroubleshootDialog) asks of the engine: its Profiles section. */
interface Troubleshooting {
    /** create_item_loaded_profiles() and GetProfilesOverview(); null while the engine is not ready. */
    suspend fun profilesOverview(): ProfilesOverview?

    /**
     * RebuildSystemProfiles(): the system folder of the data directory goes
     * at the engine's next start, which installs the bundles of the resources
     * anew; false when that could not be arranged.
     */
    suspend fun cleanSystemProfiles(): Boolean
}

/**
 * The overview as the engine's bridge gives it (profiles_overview()): the
 * JSON, whether the system profiles were cleaned ("1" or empty), then Active,
 * System and User of the printers, filaments and processes; null for none.
 */
fun profilesOverviewOf(values: List<String>): ProfilesOverview? {
    if (values.size != PROFILES_OVERVIEW_FIELDS) return null
    val counts = values.drop(2).map { it.toIntOrNull() ?: 0 }
    return ProfilesOverview(
        printers = ProfileCounts(counts[0], counts[1], counts[2]),
        filaments = ProfileCounts(counts[3], counts[4], counts[5]),
        processes = ProfileCounts(counts[6], counts[7], counts[8]),
        json = values[0],
        systemCleaned = values[1].isNotEmpty(),
    )
}

/** The values [profilesOverviewOf] reads, as the service passes them on. */
fun ProfilesOverview.bridgeValues(): List<String> =
    listOf(json, if (systemCleaned) "1" else "") +
        listOf(printers, filaments, processes).flatMap { listOf(it.active, it.system, it.user) }.map(Int::toString)

private const val PROFILES_OVERVIEW_FIELDS = 11
