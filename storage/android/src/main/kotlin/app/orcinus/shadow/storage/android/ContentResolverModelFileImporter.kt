package app.orcinus.shadow.storage.android

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import app.orcinus.shadow.core.model.ExternalDocumentReference
import app.orcinus.shadow.core.model.ImportedModelFile
import app.orcinus.shadow.core.model.ModelImportFailureCode
import app.orcinus.shadow.core.model.ModelImportOutcome
import app.orcinus.shadow.core.model.ModelPath
import app.orcinus.shadow.storage.api.ModelFileImporter
import app.orcinus.shadow.storage.api.ModelSources
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ContentResolverModelFileImporter(
    context: Context,
    /** Where the documents the copies came from are kept, for "Reload from disk"; null keeps none. */
    private val sources: ModelSources? = null,
) : ModelFileImporter {
    private val applicationContext = context.applicationContext

    override suspend fun importModel(
        reference: ExternalDocumentReference,
    ): ModelImportOutcome = importModels(listOf(reference)).single()

    /**
     * Every document is copied into a folder of its own under its own name,
     * so that documents of the same name stay apart; only the files of the
     * latest import are kept. The folders of every import are named anew, so
     * a copy's path tells its document apart for good.
     */
    override suspend fun importModels(
        references: List<ExternalDocumentReference>,
    ): List<ModelImportOutcome> = withContext(Dispatchers.IO) {
        val importsDirectory = File(applicationContext.filesDir, "imports")
        if (!importsDirectory.exists() && !importsDirectory.mkdirs()) {
            return@withContext references.map { readFailure("Unable to create the model import directory") }
        }
        val batch = UUID.randomUUID().toString()
        val folders = references.indices.map { File(importsDirectory, "$batch-$it") }
        // The files of the import before go.
        importsDirectory.listFiles()?.forEach(File::deleteRecursively)
        references.zip(folders) { reference, folder -> copy(reference, folder) }
    }

    private fun copy(reference: ExternalDocumentReference, folder: File): ModelImportOutcome {
        val uri = Uri.parse(reference.value)
        val metadata = try {
            readMetadata(uri)
        } catch (error: SecurityException) {
            return readFailure(error.message ?: "Access to the selected document was denied")
        }
        if (metadata.size != null && metadata.size > MAX_MODEL_BYTES) {
            return ModelImportOutcome.Failure(
                ModelImportFailureCode.FILE_TOO_LARGE,
                "The model file is larger than 512 MB",
            )
        }
        if (!folder.exists() && !folder.mkdirs()) {
            return readFailure("Unable to create the model import directory")
        }

        // OrcaSlicer names the object after the file, as desktop does when it opens
        // the document, so the copy keeps the document's name.
        val destination = File(folder, metadata.displayName.toFileName())
        val temporary = File(folder, TEMPORARY_FILE_NAME)
        return try {
            val input = applicationContext.contentResolver.openInputStream(uri)
                ?: return readFailure("Unable to open the selected document")
            val copiedBytes = input.use { source ->
                temporary.outputStream().buffered().use { target ->
                    copyWithLimit(source, target)
                }
            }
            if (copiedBytes == 0L) {
                temporary.delete()
                return ModelImportOutcome.Failure(
                    ModelImportFailureCode.EMPTY_FILE,
                    "The selected model file is empty",
                )
            }
            Files.move(temporary.toPath(), destination.toPath(), REPLACE_EXISTING, ATOMIC_MOVE)
            sources?.record(destination.absolutePath, reference)

            ModelImportOutcome.Success(
                ImportedModelFile(
                    path = ModelPath(destination.absolutePath),
                    displayName = metadata.displayName,
                ),
            )
        } catch (_: ModelFileTooLargeException) {
            temporary.delete()
            ModelImportOutcome.Failure(
                ModelImportFailureCode.FILE_TOO_LARGE,
                "The model file is larger than 512 MB",
            )
        } catch (error: IOException) {
            temporary.delete()
            readFailure(error.message ?: "Unable to import the selected model file")
        } catch (error: SecurityException) {
            temporary.delete()
            readFailure(error.message ?: "Access to the selected document was denied")
        }
    }

    private fun readMetadata(uri: Uri): DocumentMetadata {
        val fallbackName = uri.lastPathSegment?.substringAfterLast('/') ?: "model.stl"
        applicationContext.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                val name = if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                    cursor.getString(nameIndex)
                } else {
                    fallbackName
                }
                val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    cursor.getLong(sizeIndex)
                } else {
                    null
                }
                return DocumentMetadata(name.safeDisplayName(), size)
            }
        }
        return DocumentMetadata(fallbackName.safeDisplayName(), null)
    }

    private fun String.safeDisplayName(): String =
        take(200).filterNot(Char::isISOControl).ifBlank { "model.stl" }

    /** A single path segment of at most 255 UTF-8 bytes that keeps the extension. */
    private fun String.toFileName(): String {
        val name = replace('/', '_').takeUnless { it == "." || it == ".." || it == TEMPORARY_FILE_NAME } ?: "model.stl"
        val extension = name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
        var base = name.removeSuffix(extension)
        while (base.length > 1 && (base + extension).toByteArray(Charsets.UTF_8).size > MAX_FILE_NAME_BYTES) {
            base = base.dropLast(1)
        }
        return base + extension
    }

    private fun copyWithLimit(
        source: java.io.InputStream,
        target: java.io.OutputStream,
    ): Long {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = source.read(buffer)
            if (read < 0) return total
            total += read
            if (total > MAX_MODEL_BYTES) throw ModelFileTooLargeException()
            target.write(buffer, 0, read)
        }
    }

    private fun readFailure(message: String) = ModelImportOutcome.Failure(
        ModelImportFailureCode.READ_FAILED,
        message,
    )

    private data class DocumentMetadata(
        val displayName: String,
        val size: Long?,
    )

    private class ModelFileTooLargeException : IOException()

    private companion object {
        const val MAX_MODEL_BYTES = 512L * 1024L * 1024L
        const val MAX_FILE_NAME_BYTES = 255
        const val TEMPORARY_FILE_NAME = ".import.part"
    }
}
