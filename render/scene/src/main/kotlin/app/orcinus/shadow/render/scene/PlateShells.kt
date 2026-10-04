package app.orcinus.shadow.render.scene

import app.orcinus.shadow.core.model.ColorRgba
import app.orcinus.shadow.core.model.PlateInstance
import app.orcinus.shadow.core.model.PlateObject
import app.orcinus.shadow.core.model.VolumeType
import app.orcinus.shadow.core.model.extruderNumber
import app.orcinus.shadow.render.scene.math.Affine3
import app.orcinus.shadow.render.scene.math.Vec3

/**
 * GCodeViewer's shells: the objects the print holds (load_shells()), which the
 * preview draws see-through before the toolpaths (render_shells()).
 */
data class PlateShells(
    /** The objects of the print, each with the height its raft lifts it to (SlicingParameters::object_print_z_min). */
    val objects: List<Pair<PlateObject, Double>>,
    /**
     * Their alpha: load_shells() gives them 0.5, and set_shell_transparence()
     * 0.2 once the G-code preview is loaded.
     */
    val alpha: Float,
)

/** The scene index of a shell, which is no object the view picks. */
internal const val SHELL_INDEX = -3

internal object ShellLoader {
    /**
     * GLVolumeCollection::load_object() of every printable copy, without its
     * modifiers, negative volumes, blockers and enforcers; each volume in the
     * colour of its filament (update_colors_by_extruder(), the first
     * filament's for one out of range) at the shells' alpha, and raised by
     * its object's raft.
     */
    fun load(shells: PlateShells, filamentColors: List<ColorRgba>, fallback: ColorRgba, meshes: MeshCache): List<SceneObject> =
        shells.objects.flatMap { (plateObject, lift) ->
            val raise = Affine3().translated(Vec3(0.0, 0.0, lift))
            // ModelVolume::extruder_id(): the volume's own filament, or its object's.
            val colorOf = { extruder: Int ->
                val filament = extruder.takeIf { it > 0 } ?: plateObject.extruderNumber
                (filamentColors.getOrNull(filament - 1) ?: filamentColors.firstOrNull() ?: fallback).copy(alpha = shells.alpha)
            }
            plateObject.instances.filter(PlateInstance::printable).flatMap { instance ->
                val own = SceneLoader.loadObject(SHELL_INDEX, plateObject, instance, colorOf(plateObject.volume.settings.extruderNumber), meshes)
                val parts = plateObject.parts.filter { it.type == VolumeType.PART }.map { part ->
                    SceneLoader.loadPart(SHELL_INDEX, part, instance, colorOf(part.settings.extruderNumber), meshes)
                }
                (listOf(own) + parts).map { it.withWorld(raise * it.world) }
            }
        }
}
