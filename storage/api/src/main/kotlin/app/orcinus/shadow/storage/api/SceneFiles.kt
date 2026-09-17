package app.orcinus.shadow.storage.api

import app.orcinus.shadow.core.model.ScenePath

/**
 * Where the engine writes geometry for the 3D view. The files are derived data:
 * the plate is described again and objects are inspected again whenever needed.
 */
interface SceneFiles {
    /** Directory for the plate's bed model and texture. */
    fun plateDirectory(): ScenePath

    /** A new file for the mesh of an object put on the plate. */
    fun newObjectMesh(): ScenePath

    /** Deletes an object mesh that nothing shows any more. */
    fun deleteObjectMesh(mesh: ScenePath)

    /** Deletes every object mesh; objects do not outlive the process that placed them. */
    fun deleteAllObjectMeshes()
}
