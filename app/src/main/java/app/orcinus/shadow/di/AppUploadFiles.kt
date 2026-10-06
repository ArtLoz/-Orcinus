package app.orcinus.shadow.di

import android.content.Context
import app.orcinus.shadow.core.model.OutputPath
import app.orcinus.shadow.domain.plate.UploadFiles
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * BackgroundSlicingProcess::prepare_upload()'s temporary files: the copies the
 * upload queue sends, in the app's cache. The queue lives as long as the
 * process, so what an earlier process left there is removed first, as the
 * queue's thread removes its leftovers when it ends.
 */
internal class AppUploadFiles(context: Context) : UploadFiles {
    private val directory by lazy {
        File(context.cacheDir, "uploads").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    override fun newUpload(suffix: String): OutputPath = OutputPath(File(directory, "upload-${UUID.randomUUID()}$suffix").absolutePath)

    override fun copy(source: OutputPath, target: OutputPath): Boolean = try {
        File(source.value).copyTo(File(target.value), overwrite = true)
        true
    } catch (_: IOException) {
        false
    }

    override fun sizeOf(file: OutputPath): Long = File(file.value).takeIf { it.isFile }?.length() ?: -1

    override fun delete(file: OutputPath) {
        File(file.value).delete()
    }
}
