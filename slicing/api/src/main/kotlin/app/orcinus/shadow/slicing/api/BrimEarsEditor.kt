package app.orcinus.shadow.slicing.api

import app.orcinus.shadow.core.model.BrimEarHit
import app.orcinus.shadow.core.model.BrimEarsOutcome
import app.orcinus.shadow.core.model.BrimPoint
import app.orcinus.shadow.core.model.ClippingPlane
import app.orcinus.shadow.core.model.PlacedModel
import app.orcinus.shadow.core.model.SlicingProfileSelection
import app.orcinus.shadow.core.model.Vector3

/**
 * OrcaSlicer's brim ears tool (GLGizmoBrimEars): the engine keeps the first
 * layer and the model parts of the copy from [beginBrimEars] to
 * [endBrimEars]; the ears are the object's ([PlacedModel.brimPoints]).
 */
interface BrimEarsEditor {
    /** The tool opens on the copy [instance] of the object at [index] of [plate]. */
    suspend fun beginBrimEars(plate: List<PlacedModel>, index: Int, instance: Int, profiles: SlicingProfileSelection): BrimEarsOutcome

    /**
     * Where a ray hits the copy's model parts, and the ear a press there places;
     * null for no hit. The ray passes by what the tool's "Section view" clips
     * ([clipping]; null while it clips nothing).
     */
    suspend fun hitBrimEars(origin: Vector3, direction: Vector3, clipping: ClippingPlane?): BrimEarHit?

    /** auto_generate(): [points] with the ears along the first layer's corners added. */
    suspend fun generateBrimEars(points: List<BrimPoint>, maxAngle: Double, detectionRadius: Double, headDiameter: Double): List<BrimPoint>

    /** find_single(): the indexes of the ears that touch neither the first layer nor an ear that does. */
    suspend fun checkBrimEars(points: List<BrimPoint>): List<Int>

    suspend fun endBrimEars()
}
