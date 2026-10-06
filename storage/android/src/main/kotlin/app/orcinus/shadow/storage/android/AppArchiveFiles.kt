package app.orcinus.shadow.storage.android

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import app.orcinus.shadow.core.model.ArchiveEntry
import app.orcinus.shadow.core.model.ExternalDocumentReference
import app.orcinus.shadow.core.model.ModelPath
import app.orcinus.shadow.core.model.OrcaText
import app.orcinus.shadow.storage.api.ArchiveExtraction
import app.orcinus.shadow.storage.api.ArchiveFiles
import java.io.File
import java.io.IOException
import java.util.zip.ZipException
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The ZIP archives of Plater::preview_zip_archive(): the document is copied
 * into the app's cache, where its central directory is read as miniz reads it,
 * and the files picked are unzipped into a folder of app storage.
 */
class AppArchiveFiles(context: Context) : ArchiveFiles {
    private val applicationContext = context.applicationContext

    override suspend fun entries(archive: ExternalDocumentReference): List<ArchiveEntry>? = withContext(Dispatchers.IO) {
        val file = copied(archive) ?: return@withContext null
        try {
            ZipFile(file).use { zip ->
                zip.entries().asSequence()
                    .filter { !it.isDirectory }
                    .map { ArchiveEntry(it.name.replace('\\', '/'), it.size) }
                    // FileArchiveDialog: a file without an extension, or one of macOS's hidden ones, is left out.
                    .filter { it.path.substringAfterLast('/').contains('.') && !it.path.startsWith("__MACOSX") }
                    .sortedBy { it.path }
                    .toList()
            }
        } catch (error: ZipException) {
            null
        } catch (error: IOException) {
            null
        }
    }

    override suspend fun extract(archive: ExternalDocumentReference, entries: List<ArchiveEntry>): ArchiveExtraction = withContext(Dispatchers.IO) {
        val name = displayName(archive)
        // TRN %1% is archive path
        val failed = ArchiveExtraction.Failure(OrcaText("Loading of a ZIP archive on path %1% has failed.", listOf(name)))
        val file = copied(archive) ?: return@withContext failed
        val folder = File(applicationContext.filesDir, UNZIPPED_FOLDER)
        folder.deleteRecursively()
        if (!folder.mkdirs()) return@withContext failed
        try {
            ZipFile(file).use { zip ->
                val unzipped = mutableListOf<ModelPath>()
                for (wanted in entries) {
                    // The path and the size together find the file, as the desktop app finds it.
                    val entry = zip.entries().asSequence().firstOrNull { it.name.replace('\\', '/') == wanted.path && it.size == wanted.size } ?: continue
                    val target = uniqueFile(folder, wanted.path.substringAfterLast('/'))
                    try {
                        zip.getInputStream(entry).use { input -> target.outputStream().use { input.copyTo(it) } }
                    } catch (error: IOException) {
                        // TRN: First argument = path to file, second argument = error description
                        return@withContext ArchiveExtraction.Failure(OrcaText("Failed to unzip file to %1%: %2%", listOf(target.path, error.message.orEmpty())))
                    }
                    if (!target.isFile) {
                        return@withContext ArchiveExtraction.Failure(
                            OrcaText("Failed to find unzipped file at %1%. Unzipping of file has failed.", listOf(target.path)),
                        )
                    }
                    unzipped += ModelPath(target.absolutePath)
                }
                ArchiveExtraction.Success(unzipped)
            }
        } catch (error: ZipException) {
            failed
        } catch (error: IOException) {
            failed
        }
    }

    /** The archive in the app's cache, copied once for the listing and once more for the unzipping. */
    private fun copied(archive: ExternalDocumentReference): File? {
        val target = File(applicationContext.cacheDir, ARCHIVE_COPY)
        return try {
            val input = applicationContext.contentResolver.openInputStream(Uri.parse(archive.value)) ?: return null
            input.use { source -> target.outputStream().use { source.copyTo(it) } }
            target
        } catch (error: IOException) {
            null
        } catch (error: SecurityException) {
            null
        }
    }

    /** The name the document goes by, which stands for the archive's path in the messages. */
    private fun displayName(archive: ExternalDocumentReference): String {
        val uri = Uri.parse(archive.value)
        val queried = try {
            applicationContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (error: SecurityException) {
            null
        } catch (error: IllegalArgumentException) {
            null
        }
        return queried ?: uri.lastPathSegment?.substringAfterLast('/').orEmpty()
    }

    /** The desktop app's renaming of a file already unzipped: "name(1).ext", "name(2).ext" and so on. */
    private fun uniqueFile(folder: File, fileName: String): File {
        val extension = fileName.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
        val base = fileName.removeSuffix(extension)
        var candidate = File(folder, fileName)
        var version = 0
        while (candidate.exists()) {
            version++
            candidate = File(folder, "$base($version)$extension")
        }
        return candidate
    }

    private companion object {
        const val UNZIPPED_FOLDER = "unzipped"
        const val ARCHIVE_COPY = "archive.zip"
    }
}
