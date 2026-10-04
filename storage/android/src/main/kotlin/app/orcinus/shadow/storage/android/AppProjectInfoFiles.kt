package app.orcinus.shadow.storage.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.provider.OpenableColumns
import app.orcinus.shadow.core.model.AuxiliaryFile
import app.orcinus.shadow.core.model.AuxiliaryFolder
import app.orcinus.shadow.core.model.AuxiliaryRename
import app.orcinus.shadow.core.model.ExternalDocumentReference
import app.orcinus.shadow.core.model.OrcaText
import app.orcinus.shadow.core.model.ProjectInfo
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.storage.api.ProjectInfoFiles
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/**
 * The project's information as the engine keeps it between a load and the
 * next save (keep_project_info() and restore_project_info() of
 * project_3mf.cpp): info.json with the model's designer, information and
 * profile, and the Auxiliaries folder, which the 3MF file carries as it is.
 */
class AppProjectInfoFiles(context: Context) : ProjectInfoFiles {
    private val resolver = context.applicationContext.contentResolver
    private val objects = File(File(context.applicationContext.noBackupFilesDir, "scene"), "objects")

    override fun newDirectory(): ScenePath {
        val directory = File(objects, "project-${UUID.randomUUID()}")
        File(directory, AUXILIARIES).mkdirs()
        return ScenePath(directory.absolutePath)
    }

    override fun read(directory: ScenePath): ProjectInfo {
        val info = readJson(directory)
        val design = info.optJSONObject("design_info")
        val model = info.optJSONObject("model_info")
        val profile = info.optJSONObject("profile_info")
        return ProjectInfo(
            designer = design?.optString("Designer").orEmpty(),
            modelInfo = model != null,
            modelName = model?.optString("model_name").orEmpty(),
            license = model?.optString("license").orEmpty(),
            description = model?.optString("description").orEmpty(),
            coverFile = model?.optString("cover_file").orEmpty(),
            origin = model?.optString("origin").orEmpty(),
            copyrightAuthor = copyrightAuthor(model?.optString("copyright").orEmpty()),
            profileName = profile?.optString("ProfileTile").orEmpty(),
            profileAuthor = profile?.optString("ProfileUserName").orEmpty(),
            profileDescription = profile?.optString("ProfileDescription").orEmpty(),
            profileCover = profile?.optString("ProfileCover").orEmpty(),
            files = AuxiliaryFolder.entries.associateWith { folder -> filesOf(folderOf(directory, folder)) },
        )
    }

    override fun setDesigner(directory: ScenePath, designer: String) = edit(directory) { info ->
        val design = info.optJSONObject("design_info") ?: JSONObject().also { info.put("design_info", it) }
        design.put("Designer", designer)
        design.put("DesignerUserId", "")
    }

    override fun setModelName(directory: ScenePath, name: String) = editModel(directory) { it.put("model_name", name) }

    override fun setLicense(directory: ScenePath, license: String) = editModel(directory) { it.put("license", license) }

    override fun setDescription(directory: ScenePath, description: String) = editModel(directory) { it.put("description", description) }

    override fun import(directory: ScenePath, folder: AuxiliaryFolder, document: ExternalDocumentReference): Boolean {
        val uri = Uri.parse(document.value)
        val name = displayName(uri) ?: uri.lastPathSegment?.substringAfterLast('/') ?: return false
        val target = folderOf(directory, folder).also { it.mkdirs() }
        var file = File(target, name)
        if (file.exists()) {
            // The file's name with the time of day ("%T", its colons made underscores).
            val time = SimpleDateFormat("HH_mm_ss", Locale.ROOT).format(Date())
            val extension = name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
            file = File(target, name.replace(extension, "") + "_" + time + extension)
        }
        val copied = runCatching {
            resolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } } ?: error("unreadable")
        }.isSuccess
        if (!copied) file.delete()
        return copied
    }

    override fun rename(file: ScenePath, name: String): AuxiliaryRename {
        val old = File(file.value)
        UNUSABLE_SYMBOLS.firstOrNull { it in name }?.let {
            return invalid(OrcaText("Name is invalid;"), OrcaText("%s", listOf("\n")), OrcaText("illegal characters:"), OrcaText("%s", listOf(" $UNUSABLE_SYMBOLS")))
        }
        if (SUFFIX_MODIFIED in name) {
            return invalid(OrcaText("Name is invalid;"), OrcaText("%s", listOf("\n")), OrcaText("illegal suffix:"), OrcaText("%s", listOf("\n\t$SUFFIX_MODIFIED")))
        }
        val extension = old.name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
        val renamed = File(old.parentFile, name + extension)
        if (renamed.exists()) return invalid(OrcaText("The name \"%1%\" already exists.", listOf(name)))
        if (name.isEmpty()) return invalid(OrcaText("The name is not allowed to be empty."))
        if (name.startsWith(' ')) return invalid(OrcaText("The name is not allowed to start with space character."))
        if (name.endsWith(' ')) return invalid(OrcaText("The name is not allowed to end with space character."))
        return if (old.renameTo(renamed)) AuxiliaryRename.Renamed else invalid(OrcaText("%s", listOf(name)))
    }

    override fun delete(directory: ScenePath, file: ScenePath) {
        val removed = File(file.value)
        removed.delete()
        val info = readJson(directory)
        val model = info.optJSONObject("model_info") ?: return
        // A cover is the file of that name, whatever its folder.
        if (model.optString("cover_file") == removed.name) {
            THUMBNAILS.forEach { (picture, _) -> File(thumbnailsOf(directory), picture).delete() }
            model.put("cover_file", "")
            writeJson(directory, info)
        }
    }

    override fun setCover(directory: ScenePath, file: ScenePath) {
        val picture = File(file.value)
        editModel(directory) { it.put("cover_file", picture.name) }
        val thumbnails = thumbnailsOf(directory).also { it.mkdirs() }
        val source = BitmapFactory.decodeFile(picture.absolutePath) ?: return
        try {
            THUMBNAILS.forEach { (name, size) ->
                val image = coverImage(source, size.first, size.second)
                File(thumbnails, name).outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
                image.recycle()
            }
        } finally {
            source.recycle()
        }
    }

    /**
     * generate_image() with GERNERATE_IMAGE_RESIZE: the picture scaled until
     * it covers the whole size, then its middle.
     */
    private fun coverImage(source: Bitmap, width: Int, height: Int): Bitmap {
        val factor = minOf(source.height / height.toFloat(), source.width / width.toFloat())
        val targetWidth = (source.width / factor).toInt()
        val targetHeight = (source.height / factor).toInt()
        val image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val left = (width - targetWidth) / 2
        val top = (height - targetHeight) / 2
        Canvas(image).drawBitmap(source, null, Rect(left, top, left + targetWidth, top + targetHeight), Paint(Paint.FILTER_BITMAP_FLAG))
        return image
    }

    private fun editModel(directory: ScenePath, change: (JSONObject) -> Unit) = edit(directory) { info ->
        // ensure_model_info()
        change(info.optJSONObject("model_info") ?: JSONObject().also { info.put("model_info", it) })
    }

    private fun edit(directory: ScenePath, change: (JSONObject) -> Unit) {
        val info = readJson(directory)
        change(info)
        writeJson(directory, info)
    }

    private fun readJson(directory: ScenePath): JSONObject {
        val file = File(directory.value, INFO_FILE)
        if (!file.exists()) return JSONObject()
        return runCatching { JSONObject(file.readText()) }.getOrElse { JSONObject() }
    }

    private fun writeJson(directory: ScenePath, info: JSONObject) {
        File(directory.value).mkdirs()
        File(directory.value, INFO_FILE).writeText(info.toString())
    }

    private fun folderOf(directory: ScenePath, folder: AuxiliaryFolder) = File(File(directory.value, AUXILIARIES), folder.directory)

    private fun thumbnailsOf(directory: ScenePath) = File(File(directory.value, AUXILIARIES), ".thumbnails")

    /** The files of a folder, which Reload() makes when it is missing. */
    private fun filesOf(folder: File): List<AuxiliaryFile> {
        folder.mkdirs()
        return folder.listFiles().orEmpty().filter(File::isFile).sortedBy(File::getName).map {
            AuxiliaryFile(ScenePath(it.absolutePath), it.name, it.length())
        }
    }

    private fun displayName(uri: Uri): String? =
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (column >= 0 && cursor.moveToFirst()) cursor.getString(column) else null
        }

    private fun invalid(vararg message: OrcaText) = AuxiliaryRename.Invalid(message.toList())

    private companion object {
        const val INFO_FILE = "info.json"
        const val AUXILIARIES = "Auxiliaries"

        /** unusable_symbols of AuFile::on_input_enter(). */
        const val UNUSABLE_SYMBOLS = "<>[]:/\\|?*\""

        /** PresetCollection::get_suffix_modified() */
        const val SUFFIX_MODIFIED = " (modified)"

        /** The cover's pictures: _3MF_COVER_SIZE, PRINTER_THUMBNAIL_SMALL_SIZE and PRINTER_THUMBNAIL_MIDDLE_SIZE. */
        val THUMBNAILS = listOf(
            "thumbnail_3mf.png" to (240 to 240),
            "thumbnail_small.png" to (252 to 188),
            "thumbnail_middle.png" to (680 to 680),
        )

        /** on_reload(): the author is the last "author" of the copyright's list. */
        fun copyrightAuthor(copyright: String): String {
            if (copyright.isEmpty()) return ""
            val list = runCatching { JSONArray(copyright) }.getOrNull() ?: return ""
            var author = ""
            for (index in 0 until list.length()) {
                list.optJSONObject(index)?.takeIf { it.has("author") }?.let { author = it.optString("author") }
            }
            return author
        }
    }
}
