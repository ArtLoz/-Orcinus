package app.orcinus.shadow.render.scene

import app.orcinus.shadow.core.model.PlateInstanceId

/**
 * GLGizmoMeshBoolean while it is open on the copy [copy]: the volumes it took
 * as the source and the tool, by their mesh files, which on_render() frames
 * in white and in Orca's green; a finger on a volume of the copy picks it
 * (gizmo_event()).
 */
data class MeshBooleanView(
    val copy: PlateInstanceId,
    val source: String? = null,
    val tool: String? = null,
)
