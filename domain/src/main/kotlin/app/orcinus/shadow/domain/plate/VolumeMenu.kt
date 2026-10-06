package app.orcinus.shadow.domain.plate

import app.orcinus.shadow.core.model.Axis
import app.orcinus.shadow.core.model.PlateInstanceId
import app.orcinus.shadow.core.model.PlateState
import app.orcinus.shadow.core.model.Transform3
import app.orcinus.shadow.core.model.VolumeBox
import app.orcinus.shadow.core.model.VolumeDescriptionOutcome
import app.orcinus.shadow.core.model.VolumeManipulation
import app.orcinus.shadow.core.model.inverse
import app.orcinus.shadow.core.model.plateGrid
import app.orcinus.shadow.core.model.times
import app.orcinus.shadow.core.model.volumeAt
import app.orcinus.shadow.domain.placed
import app.orcinus.shadow.slicing.api.PlateInspector
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * MenuFactory::create_bbl_part_menu()'s Center, Drop and Mirror of a volume
 * selected alone (Selection::Volume), over the copy [PlateInstanceId] the list
 * picks it on: the volume moves or mirrors in the world, by its box there
 * (the engine measures it), and the move or mirror is committed as the canvas
 * commits it (do_move(), do_mirror()).
 */
class VolumeMenuUseCase(
    private val inspector: PlateInspector,
    private val repository: PlateRepository,
    private val placeObjectVolume: PlaceObjectVolumeUseCase,
    private val applicationScope: CoroutineScope,
) {
    /** Selection::center(): the volume's box over the centre of the current plate, at its height. */
    fun center(copy: PlateInstanceId, volume: Int) = withBox(copy, volume) { box, state ->
        val center = state.plateGrid?.centerOf(state.currentPlate, state.plates.size) ?: return@withBox null
        translation(center.x - box.center.x, center.y - box.center.y, 0.0) to VolumeManipulation.MOVE
    }

    /** Selection::drop(): the volume's box down or up onto the plate. */
    fun drop(copy: PlateInstanceId, volume: Int) = withBox(copy, volume) { box, _ ->
        val minZ = box.center.z - box.size.z / 2
        if (abs(minZ) < SINKING_Z_THRESHOLD) return@withBox null
        translation(0.0, 0.0, -minZ) to VolumeManipulation.MOVE
    }

    /** Selection::mirror() along the world's [axis] about the centre of the volume's box; do_mirror() rests it as do_scale(). */
    fun mirror(copy: PlateInstanceId, volume: Int, axis: Axis) = withBox(copy, volume) { box, _ ->
        val c = box.center
        val mirror = Transform3(
            List(16) { index ->
                when (index) {
                    0 -> if (axis == Axis.X) -1.0 else 1.0
                    5 -> if (axis == Axis.Y) -1.0 else 1.0
                    10 -> if (axis == Axis.Z) -1.0 else 1.0
                    15 -> 1.0
                    else -> 0.0
                }
            },
        )
        translation(c.x, c.y, c.z) * mirror * translation(-c.x, -c.y, -c.z) to VolumeManipulation.SCALE
    }

    /**
     * The world transformation [change] gives of the volume's box, applied to
     * the volume in the object: inst⁻¹ · change · inst · volume.
     */
    private fun withBox(
        copy: PlateInstanceId,
        volume: Int,
        change: (VolumeBox, PlateState) -> Pair<Transform3, VolumeManipulation>?,
    ) {
        val state = repository.state.value
        val target = state.objects.withMesh(copy.mesh) ?: return
        val instance = target.instances.getOrNull(copy.instance) ?: return
        val placement = target.volumeAt(volume)?.placement ?: return
        val profiles = state.profiles ?: return
        if (state.busy) return
        applicationScope.launch {
            val described = inspector.describeVolume(target.placed(), profiles, instance.inspection.placement, volume)
            val box = (described as? VolumeDescriptionOutcome.Success)?.description?.world ?: return@launch
            val (world, manipulation) = change(box, repository.state.value) ?: return@launch
            val inst = instance.inspection.placement
            placeObjectVolume(copy, volume, inst.inverse() * world * inst * placement, manipulation)
        }
    }

    private fun translation(x: Double, y: Double, z: Double) =
        Transform3(Transform3.IDENTITY.columns.mapIndexed { index, value -> when (index) { 12 -> x; 13 -> y; 14 -> z; else -> value } })

    private companion object {
        const val SINKING_Z_THRESHOLD = 0.001
    }
}
