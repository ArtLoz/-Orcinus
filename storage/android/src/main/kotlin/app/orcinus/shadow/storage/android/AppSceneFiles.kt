package app.orcinus.shadow.storage.android

import android.content.Context
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.storage.api.SceneFiles
import java.io.File
import java.util.UUID

/** Scene geometry in the app's no-backup storage: files/scene/plate and files/scene/objects. */
class AppSceneFiles(context: Context) : SceneFiles {
    private val root = File(context.applicationContext.noBackupFilesDir, "scene")
    private val plate = File(root, "plate")
    private val objects = File(root, "objects")

    override fun plateDirectory(): ScenePath {
        plate.mkdirs()
        return ScenePath(plate.absolutePath)
    }

    override fun newObjectMesh(): ScenePath {
        objects.mkdirs()
        return ScenePath(File(objects, "${UUID.randomUUID()}.mesh").absolutePath)
    }

    override fun deleteObjectMesh(mesh: ScenePath) {
        val file = File(mesh.value)
        if (file.parentFile == objects) file.delete()
    }

    override fun deleteAllObjectMeshes() {
        objects.listFiles()?.forEach(File::delete)
    }
}
