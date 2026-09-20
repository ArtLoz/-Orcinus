package app.orcinus.shadow.storage.api

import app.orcinus.shadow.core.model.PlateDescription

/**
 * The plate the app drew last, kept so the 3D view has a bed while the engine
 * loads OrcaSlicer's profiles, which takes seconds. It is derived data: the
 * engine describes the plate again as soon as it is ready, and its answer
 * replaces this one.
 */
interface PlateCache {
    /** The plate of [printer] from an earlier run, or null when there is none. */
    fun read(): CachedPlate?

    fun write(printer: String, description: PlateDescription)

    fun clear()
}

/** A plate description with the printer preset it was described for. */
data class CachedPlate(val printer: String, val description: PlateDescription)
