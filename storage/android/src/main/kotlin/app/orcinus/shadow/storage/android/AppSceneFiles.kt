package app.orcinus.shadow.storage.android

import android.content.Context
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.ThumbnailSize
import app.orcinus.shadow.storage.api.SceneFiles
import java.io.File
import java.util.UUID

/** Scene geometry in the app's no-backup storage: files/scene/plate, objects, and toolpaths. */
class AppSceneFiles(context: Context) : SceneFiles {
    private val root = File(context.applicationContext.noBackupFilesDir, "scene")
    private val plate = File(root, "plate")
    private val objects = File(root, "objects")
    private val toolpaths = File(root, "toolpaths")

    override fun plateDirectory(): ScenePath {
        plate.mkdirs()
        return ScenePath(plate.absolutePath)
    }

    override fun newObjectMesh(): ScenePath {
        objects.mkdirs()
        return ScenePath(File(objects, "${UUID.randomUUID()}.mesh").absolutePath)
    }

    override fun newPaintedMeshes(): ScenePath {
        objects.mkdirs()
        return ScenePath(File(objects, "painted-${UUID.randomUUID()}").absolutePath)
    }

    override fun newImportPrefix(): ScenePath {
        objects.mkdirs()
        return ScenePath(File(objects, "import-${UUID.randomUUID()}").absolutePath)
    }

    override fun deleteImport(prefix: ScenePath) {
        val start = File(prefix.value)
        if (start.parentFile != objects) return
        // A project's information is a folder of its own (LoadedProject.info).
        objects.listFiles()?.filter { it.name.startsWith(start.name + "-") }?.forEach(File::deleteRecursively)
    }

    override fun deleteObjectMesh(mesh: ScenePath) {
        val file = File(mesh.value)
        if (file.parentFile == objects) file.delete()
    }

    override fun deleteAllObjectMeshes() {
        objects.listFiles()?.forEach(File::deleteRecursively)
    }

    override fun newToolpaths(): ScenePath {
        toolpaths.mkdirs()
        return ScenePath(File(toolpaths, "${UUID.randomUUID()}.toolpaths").absolutePath)
    }

    override fun wipeTowerMeshOf(toolpaths: ScenePath): ScenePath = ScenePath(toolpaths.value + WIPE_TOWER_SUFFIX)

    // Not kept by deleteToolpathsExcept(): the G-code holds the thumbnails once it is written.
    override fun thumbnailOf(toolpaths: ScenePath, size: ThumbnailSize): ScenePath =
        ScenePath("${toolpaths.value}.thumbnail-${size.width}x${size.height}.rgba")

    override fun deleteToolpathsExcept(keep: ScenePath?) {
        val kept = setOfNotNull(keep?.let { File(it.value) }, keep?.let { File(wipeTowerMeshOf(it).value) })
        toolpaths.listFiles()?.filter { it !in kept }?.forEach(File::delete)
    }

    private companion object {
        /** The wipe tower of a slice lies beside its toolpaths. */
        const val WIPE_TOWER_SUFFIX = ".tower.mesh"
    }
}
