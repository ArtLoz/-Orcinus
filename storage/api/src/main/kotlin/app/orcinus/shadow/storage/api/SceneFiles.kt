package app.orcinus.shadow.storage.api

import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.ThumbnailSize

/**
 * Where the engine writes geometry for the 3D view. The files are derived data:
 * the plate is described again and objects are inspected again whenever needed.
 */
interface SceneFiles {
    /** Directory for the plate's bed model and texture. */
    fun plateDirectory(): ScenePath

    /** A new file for the mesh of an object put on the plate. */
    fun newObjectMesh(): ScenePath

    /** A new prefix for the meshes of a painted object, "<prefix>-<filament>.mesh". */
    fun newPaintedMeshes(): ScenePath

    /** Deletes an object mesh that nothing shows any more. */
    fun deleteObjectMesh(mesh: ScenePath)

    /** Deletes every object mesh; objects do not outlive the process that placed them. */
    fun deleteAllObjectMeshes()

    /** A new file for the toolpaths of a slice. */
    fun newToolpaths(): ScenePath

    /** The wipe tower mesh of the slice whose toolpaths are [toolpaths], kept and deleted with them. */
    fun wipeTowerMeshOf(toolpaths: ScenePath): ScenePath

    /** A thumbnail the G-code of the slice whose toolpaths are [toolpaths] carries; deleted once the slice is done. */
    fun thumbnailOf(toolpaths: ScenePath, size: ThumbnailSize): ScenePath

    /** Deletes every toolpaths file except [keep], the one the plate's result shows. */
    fun deleteToolpathsExcept(keep: ScenePath?)
}
