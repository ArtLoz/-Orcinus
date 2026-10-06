package app.orcinus.shadow.domain.plate

import app.orcinus.shadow.core.model.LayerGcode
import app.orcinus.shadow.core.model.LayerGcodeType
import app.orcinus.shadow.core.model.ModelSettings
import app.orcinus.shadow.core.model.PaintingOutcome
import app.orcinus.shadow.core.model.PlateObject
import app.orcinus.shadow.core.model.PlateState
import app.orcinus.shadow.core.model.mesh
import app.orcinus.shadow.core.model.withFilamentDeletedFromSequences
import app.orcinus.shadow.core.model.withLayerRanges
import app.orcinus.shadow.core.model.withPainted
import app.orcinus.shadow.core.model.withParts
import app.orcinus.shadow.core.model.withSequencesForFilamentCount
import app.orcinus.shadow.core.model.withSettings
import app.orcinus.shadow.core.model.withVolume
import app.orcinus.shadow.domain.placed
import app.orcinus.shadow.slicing.api.PlateInspector
import app.orcinus.shadow.storage.api.SceneFiles

/**
 * The plate follows its filaments as the desktop app's plater does:
 * Plater::on_filaments_delete() when one leaves, and
 * Plater::on_filament_count_change() when there come to be another number of
 * them. The settings of the objects, the plates' filament orders and their
 * filament changes follow at once; the colours painted on the objects follow
 * on the engine, object by object.
 */
class PlateFilamentRenumbering(
    private val inspector: PlateInspector,
    private val sceneFiles: SceneFiles,
    private val repository: PlateRepository,
) {
    /**
     * Sidebar::delete_filament(): [filament] (from 0) has left the [count]
     * filaments that stay, and [replace] (from 0 among them, -1 for none)
     * takes its objects and facets, as "Merge with" asks.
     */
    suspend fun deleted(filament: Int, replace: Int, count: Int) {
        repository.update { it.withFilamentDeleted(filament, replace) }
        renumberPainting(count, deleted = filament + 1, replace = replace + 1)
    }

    /** on_filament_count_change() of [count] filaments, from [before]. */
    suspend fun countChanged(count: Int, before: Int?) {
        repository.update { it.withFilamentCount(count) }
        // ModelVolume::update_extruder_count(): only fewer filaments take colours away.
        if (before != null && count < before) renumberPainting(count, deleted = 0, replace = 0)
    }

    private suspend fun renumberPainting(count: Int, deleted: Int, replace: Int) {
        val state = repository.state.value
        val profiles = state.profiles ?: return
        for (target in state.objects) {
            val paintings = target.paintings()
            if (paintings.all { it.isEmpty }) continue
            val outcome = inspector.renumberPaintedFilaments(target.placed(), count, deleted, replace, profiles, sceneFiles.newPaintedMeshes())
            val surface = (outcome as? PaintingOutcome.Success)?.surface ?: continue
            repository.update { current ->
                val now = current.objects.firstOrNull { it.mesh == target.mesh }
                // The object changed meanwhile: its painting is another one.
                if (now == null || now.paintings() != paintings) return@update current
                val parts = now.parts.mapIndexed { index, part -> surface.partFacets.getOrNull(index)?.let { part.copy(painted = it) } ?: part }
                if (surface.facets == now.painted && parts == now.parts) return@update current
                // The 3D view draws the colours anew (PaintedColorUpdates).
                val renumbered = now.withPainted(surface.facets, emptyList()).withParts(parts)
                current.copy(objects = current.objects.map { if (it.mesh == target.mesh) renumbered else it })
            }
        }
    }
}

/** The painting of the object's own mesh and of each part. */
private fun PlateObject.paintings() = listOf(painted) + parts.map { it.painted }

/**
 * Plater::on_filaments_delete() and PartPlateList::on_filament_deleted() of
 * the plate: filament [filament] (from 0) has left, and [replace] (from 0
 * among the filaments left, -1 for none) takes its place.
 *
 * Orca's object list renumbers the objects, their parts and height ranges
 * (update_filament_values_for_items_when_delete_filament()). Two things of
 * Orca 2.4.2 are not repeated: ModelVolume::update_extruder_count_when_delete_filament()
 * first drops the filament of a part numbered beyond the filaments left, so
 * the last filament's parts would take their object's; and a part's support
 * filament, renumbered, would go to its object. Each part keeps its own,
 * renumbered.
 */
internal fun PlateState.withFilamentDeleted(filament: Int, replace: Int): PlateState {
    val deleted = filament + 1
    fun Int.renumbered() = if (this > deleted) this - 1 else this
    // An object or part on the deleted filament takes the replacement, or the first filament.
    val replacement = if (replace == -1) 1 else replace + 1

    fun ModelSettings.supportsRenumbered(): ModelSettings = SUPPORT_FILAMENT_KEYS.fold(this) { settings, key ->
        when (val value = settings.int(key)) {
            null -> settings
            deleted -> settings.without(key)
            else -> settings.with(key, value.renumbered())
        }
    }

    fun ModelSettings.volumeRenumbered(): ModelSettings {
        val supports = supportsRenumbered()
        return when (val extruder = supports.int(EXTRUDER)) {
            null -> supports
            deleted -> supports.with(EXTRUDER, replacement)
            else -> supports.with(EXTRUDER, extruder.renumbered())
        }
    }

    fun PlateObject.renumbered(): PlateObject {
        val extruder = when (val own = settings.int(EXTRUDER)) {
            null -> 1
            deleted -> replacement
            else -> own.renumbered()
        }
        val ranges = layerRanges.map { range ->
            // A range on the deleted filament takes the replacement, or its object's.
            when (val value = range.settings.int(EXTRUDER)) {
                null -> range
                deleted -> range.copy(settings = range.settings.with(EXTRUDER, if (replace == -1) 0 else replace + 1))
                else -> range.copy(settings = range.settings.with(EXTRUDER, value.renumbered()))
            }
        }
        return withSettings(settings.supportsRenumbered().with(EXTRUDER, extruder))
            .withVolume(volume.copy(settings = volume.settings.volumeRenumbered()))
            .withParts(parts.map { it.copy(settings = it.settings.volumeRenumbered()) })
            .withLayerRanges(ranges)
    }

    // The filament changes of the plates' layers (Model::plates_custom_gcodes):
    // those to the deleted filament go, or go to the replacement, and those to
    // a later one come one lower. Orca 2.4.2 sets the replacement on the first
    // element std::remove_if() left behind and renumbers it again; this keeps
    // what that code means.
    fun List<LayerGcode>.renumbered(): List<LayerGcode> = mapNotNull { gcode ->
        when {
            gcode.type != LayerGcodeType.TOOL_CHANGE -> gcode
            gcode.extruder == deleted -> if (replace == -1) null else gcode.copy(extruder = replace + 1)
            gcode.extruder > deleted -> gcode.copy(extruder = gcode.extruder - 1)
            else -> gcode
        }
    }

    return copy(
        objects = objects.map { it.renumbered() },
        plateSettings = plateSettings.withFilamentDeletedFromSequences(deleted),
        layerGcodes = layerGcodes.renumbered(),
        plates = plates.map { plate ->
            plate.copy(settings = plate.settings.withFilamentDeletedFromSequences(deleted), layerGcodes = plate.layerGcodes.renumbered())
        },
    )
}

/**
 * Plater::on_filament_count_change() of [count] filaments:
 * ObjectList::update_filament_values_for_items() gives an object without a
 * filament, or beyond them, the first one and takes away its support
 * filaments beyond them, and those of the volumes of an object with parts;
 * ModelVolume::update_extruder_count() takes away the filament of a volume
 * beyond them; and every plate's filament orders are as long as the filaments
 * (PartPlate::update_first_layer_print_sequence()).
 */
internal fun PlateState.withFilamentCount(count: Int): PlateState {
    fun ModelSettings.withoutSupportsBeyond(): ModelSettings =
        SUPPORT_FILAMENT_KEYS.fold(this) { settings, key -> if ((settings.int(key) ?: 0) > count) settings.without(key) else settings }

    fun ModelSettings.volumeLimited(withParts: Boolean): ModelSettings {
        val supports = if (withParts) withoutSupportsBeyond() else this
        return if ((supports.int(EXTRUDER) ?: 0) > count) supports.without(EXTRUDER) else supports
    }

    fun PlateObject.limited(): PlateObject {
        val extruder = settings.int(EXTRUDER)?.takeIf { it <= count } ?: 1
        val withParts = parts.isNotEmpty()
        return withSettings(settings.withoutSupportsBeyond().with(EXTRUDER, extruder))
            .withVolume(volume.copy(settings = volume.settings.volumeLimited(withParts)))
            .withParts(parts.map { it.copy(settings = it.settings.volumeLimited(withParts)) })
    }

    return copy(
        objects = objects.map { it.limited() },
        plateSettings = plateSettings.withSequencesForFilamentCount(count),
        plates = plates.map { it.copy(settings = it.settings.withSequencesForFilamentCount(count)) },
    )
}

private fun ModelSettings.int(key: String): Int? = values[key]?.toIntOrNull()

private fun ModelSettings.with(key: String, value: Int): ModelSettings =
    if (values[key] == value.toString()) this else ModelSettings(values + (key to value.toString()))

private fun ModelSettings.without(key: String): ModelSettings = if (key in values) ModelSettings(values - key) else this

private const val EXTRUDER = "extruder"

/** The support filaments an object or a volume may choose for itself. */
private val SUPPORT_FILAMENT_KEYS = listOf("support_filament", "support_interface_filament")
