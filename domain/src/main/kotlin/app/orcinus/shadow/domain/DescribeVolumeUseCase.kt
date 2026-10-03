package app.orcinus.shadow.domain

import app.orcinus.shadow.core.model.PlateInstance
import app.orcinus.shadow.core.model.PlateObject
import app.orcinus.shadow.core.model.SlicingProfileSelection
import app.orcinus.shadow.core.model.VolumeDescriptionOutcome
import app.orcinus.shadow.slicing.api.PlateInspector

/**
 * Selection::get_bounding_sphere() and the boxes of the volume [volume]
 * (ModelObject::volumes) of [plateObject] selected alone, where its copy
 * [instance] stands.
 */
class DescribeVolumeUseCase(
    private val inspector: PlateInspector,
) {
    suspend operator fun invoke(
        plateObject: PlateObject,
        instance: PlateInstance,
        volume: Int,
        profiles: SlicingProfileSelection,
    ): VolumeDescriptionOutcome = inspector.describeVolume(plateObject.placed(), profiles, instance.inspection.placement, volume)
}
