package app.orcinus.shadow.core.model

/**
 * GLGizmoBrimEars open on a copy: how far the window lets "Detection radius"
 * go (get_detection_radius_max()), how wide a new ear is
 * (get_brim_default_radius(), a diameter), and whether the object's brim type
 * is "painted", without which the ears take no effect.
 */
data class BrimEarsSetup(val detectionRadiusMax: Double, val defaultHeadDiameter: Double, val painted: Boolean)

sealed interface BrimEarsOutcome {
    data class Success(val setup: BrimEarsSetup) : BrimEarsOutcome

    data class Failure(val message: String) : BrimEarsOutcome
}

/**
 * Where a ray hits the copy's model parts ([position], in the object's
 * coordinates; unproject_on_mesh2()), and where an ear placed there stands
 * ([ear]: on the plate under it).
 */
data class BrimEarHit(val position: Vector3, val ear: Vector3)
