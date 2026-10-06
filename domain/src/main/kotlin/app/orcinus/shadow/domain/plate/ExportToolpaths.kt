package app.orcinus.shadow.domain.plate

import app.orcinus.shadow.core.model.ExternalDocumentReference
import app.orcinus.shadow.core.model.PlateObject
import app.orcinus.shadow.core.model.PlateState
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.domain.placed
import app.orcinus.shadow.storage.api.DocumentExport
import app.orcinus.shadow.storage.api.SceneFiles

/**
 * "Export toolpaths as OBJ" (Plater::export_toolpaths_to_obj()): the toolpaths
 * the preview shows written as an OBJ file, with their colours as materials in
 * a file beside it, which the OBJ file names (mtllib). The desktop app asks
 * for the OBJ file and writes the materials next to it; a phone's document
 * picker grants the one document picked, so the materials are saved in a
 * second one, offered under the name the OBJ file gives them.
 */
class ExportToolpathsUseCase(
    private val documents: DocumentExport,
    private val sceneFiles: SceneFiles,
    private val repository: PlateRepository,
) {
    /** The materials of toolpaths written into an OBJ file, waiting for the document they go into. */
    class Materials internal constructor(internal val prefix: ScenePath, internal val path: String, val name: String)

    /**
     * Plater::priv::get_export_file(FT_OBJ): exportFileBase() with ".obj";
     * [untitled] for none.
     */
    fun suggestedName(untitled: String): String {
        val base = repository.state.value.exportFileBase() ?: untitled
        return "$base.obj"
    }

    /**
     * Writes the toolpaths with [write], which writes an OBJ file at the path
     * it is given and its materials beside it, into [document]: the materials
     * are named after the document as it was named. Null when the OBJ file
     * could not be written; otherwise the materials, for [saveMaterials].
     */
    suspend fun writeToolpaths(document: ExternalDocumentReference, untitled: String, write: suspend (path: String) -> Boolean): Materials? {
        val name = documents.displayName(document) ?: suggestedName(untitled)
        val base = name.removeSuffix(".obj").removeSuffix(".OBJ")
        val prefix = sceneFiles.newImportPrefix()
        val obj = "${prefix.value}-toolpaths/$base.obj"
        val materials = "${prefix.value}-toolpaths/$base.mtl"
        if (!write(obj) || !documents.copyTo(obj, document)) {
            sceneFiles.deleteImport(prefix)
            return null
        }
        return Materials(prefix, materials, "$base.mtl")
    }

    /** Saves the [materials] into [document], or drops them for null; false when they could not be written. */
    suspend fun saveMaterials(materials: Materials, document: ExternalDocumentReference?): Boolean {
        try {
            return document != null && documents.copyTo(materials.path, document)
        } finally {
            sceneFiles.deleteImport(materials.prefix)
        }
    }
}

/** ModelObject::get_export_filename(): the object's name, which the engine gives the calibration cube when it has none. */
internal fun PlateObject.exportName(): String = placed().name.ifEmpty { CALIBRATION_CUBE }

/**
 * Plater::priv::get_export_file_path() of a file named after the plate: the
 * project's name, or else the selected object's
 * (ModelObject::get_export_filename()), the first object's with a printable
 * copy (Model::propose_export_file_name_and_path()), or the first object's,
 * without its extension; null for none, which the desktop app calls "Untitled".
 */
internal fun PlateState.exportFileBase(): String? {
    project.name?.let { return it }
    val selected = selectedInstances.map { it.mesh }.distinct().singleOrNull()?.let { objects.withMesh(it) }
    val named = selected ?: objects.firstOrNull { plateObject -> plateObject.instances.any { it.printable } } ?: objects.firstOrNull()
    return named?.exportName()?.let { name -> if ('.' in name) name.substringBeforeLast('.') else name }?.ifEmpty { null }
}
