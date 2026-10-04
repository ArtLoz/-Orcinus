package app.orcinus.shadow.storage.android

import android.content.Context
import app.orcinus.shadow.core.model.ExternalDocumentReference
import app.orcinus.shadow.storage.api.BedFiles
import java.io.File

/** Copies in files/bed. */
class AppBedFiles(context: Context) : BedFiles {
    private val applicationContext = context.applicationContext

    override suspend fun keep(document: ExternalDocumentReference): String? = applicationContext.keepDocumentCopy(document, "bed")

    override fun exists(path: String): Boolean = File(path).exists()

    override fun size(path: String): Long = File(path).length()
}
