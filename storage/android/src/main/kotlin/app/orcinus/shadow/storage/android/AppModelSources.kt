package app.orcinus.shadow.storage.android

import android.content.Context
import app.orcinus.shadow.core.model.ExternalDocumentReference
import app.orcinus.shadow.storage.api.DocumentAccess
import app.orcinus.shadow.storage.api.ModelSources
import java.io.File

/**
 * The documents of the latest [MAX_SOURCES] model copies, kept across starts
 * with the app's access to them; a document leaves with the last copy of it.
 */
class AppModelSources(context: Context, private val documents: DocumentAccess) : ModelSources {
    private val store = context.applicationContext.getSharedPreferences(STORE, Context.MODE_PRIVATE)

    /** Where ContentResolverModelFileImporter copies documents. */
    private val imports = File(context.applicationContext.filesDir, "imports")

    override fun documentOf(file: String): ExternalDocumentReference? = synchronized(this) {
        store.getString(file, null)?.substringAfter(SEPARATOR)?.let(::ExternalDocumentReference)
    }

    override fun record(file: String, document: ExternalDocumentReference) = synchronized(this) {
        documents.keep(document, HOLDER)
        val entries = store.all.mapNotNull { (key, value) -> (value as? String)?.let { key to it } }.toMap() +
            (file to "${System.currentTimeMillis()}$SEPARATOR${document.value}")
        val gone = entries.entries.sortedByDescending { it.value.substringBefore(SEPARATOR).toLongOrNull() ?: 0L }.drop(MAX_SOURCES)
        val editor = store.edit().putString(file, entries.getValue(file))
        gone.forEach { editor.remove(it.key) }
        editor.apply()
        val kept = entries.keys - gone.map { it.key }.toSet()
        gone.map { it.value.substringAfter(SEPARATOR) }.distinct()
            .filter { uri -> kept.none { entries.getValue(it).substringAfter(SEPARATOR) == uri } }
            .forEach { documents.release(ExternalDocumentReference(it), HOLDER) }
    }

    /** A copy the app made is no file of the device: its document is, or nothing once the app forgot it. */
    override fun isFile(file: String): Boolean =
        file.startsWith("/") && !File(file).absolutePath.startsWith(imports.absolutePath + File.separator) && File(file).isFile

    private companion object {
        const val STORE = "model_sources"
        const val SEPARATOR = '\n'
        const val HOLDER = "model_sources"
        const val MAX_SOURCES = 100
    }
}
