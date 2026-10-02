package app.orcinus.shadow.storage.android

import android.content.Context
import androidx.core.content.FileProvider
import app.orcinus.shadow.core.model.ExternalDocumentReference
import app.orcinus.shadow.storage.api.FileShare
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The file is copied into the app's cache under the name it is offered by, and
 * the app's FileProvider ([authority]) offers the copy; only the latest shared
 * file is kept, since the app it went to reads it right away.
 */
class AppFileShare(context: Context, private val authority: String) : FileShare {
    private val applicationContext = context.applicationContext

    override suspend fun shareable(path: String, name: String): ExternalDocumentReference? = withContext(Dispatchers.IO) {
        val source = File(path)
        if (!source.isFile) return@withContext null
        val folder = File(applicationContext.cacheDir, SHARED_FOLDER)
        folder.deleteRecursively()
        if (!folder.mkdirs()) return@withContext null
        val target = File(folder, name.replace('/', '_').ifBlank { source.name })
        try {
            source.copyTo(target, overwrite = true)
        } catch (_: IOException) {
            return@withContext null
        }
        ExternalDocumentReference(FileProvider.getUriForFile(applicationContext, authority, target).toString())
    }

    private companion object {
        /** The cache folder the provider's paths name (shared_files.xml). */
        const val SHARED_FOLDER = "shared"
    }
}
