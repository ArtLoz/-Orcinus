package app.orcinus.shadow.domain.plate

import app.orcinus.shadow.core.model.DialogIcon
import app.orcinus.shadow.core.model.ExternalDocumentReference
import app.orcinus.shadow.core.model.ModelImportOutcome
import app.orcinus.shadow.core.model.ModelLoadOutcome
import app.orcinus.shadow.core.model.ModelPath
import app.orcinus.shadow.core.model.ObjColorChoice
import app.orcinus.shadow.core.model.OrcaText
import app.orcinus.shadow.core.model.PlateObject
import app.orcinus.shadow.core.model.ReloadPrompt
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.SettingsDialog
import app.orcinus.shadow.core.model.SlicingProfileSelection
import app.orcinus.shadow.core.model.isCut
import app.orcinus.shadow.core.model.mesh
import app.orcinus.shadow.core.model.placing
import app.orcinus.shadow.core.model.reloadableVolumes
import app.orcinus.shadow.core.model.volumeInputFile
import app.orcinus.shadow.domain.ImportModelUseCase
import app.orcinus.shadow.domain.placed
import app.orcinus.shadow.slicing.api.PlateInspector
import app.orcinus.shadow.storage.api.DocumentAccess
import app.orcinus.shadow.storage.api.ModelSources
import app.orcinus.shadow.storage.api.SceneFiles
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Plater::priv::reload_from_disk() and reload_all_from_disk(): the volumes
 * that came from a file read it again. A volume's file is a copy the app made
 * of a document, which is read again where the app can still read it, or a
 * file of the device (OrcaSlicer's own models). For a file it can no longer
 * read the user picks one ("Please select a file"): one of the same name
 * stands for it, one of another name replaces the volumes of it when the user
 * agrees ("Do you want to replace it ?", replace_volume_with_stl()); without
 * a pick nothing is reloaded. Each file is read once and every volume it
 * holds a match for takes the new mesh; one Undo step takes it all, and
 * "Error during reload" names the volumes the files had nothing for. A file
 * that cannot be read ends the reload there, as on the desktop.
 */
class ReloadFromDiskUseCase(
    private val importModel: ImportModelUseCase,
    private val sources: ModelSources,
    private val documents: DocumentAccess,
    private val inspector: PlateInspector,
    private val sceneFiles: SceneFiles,
    private val repository: PlateRepository,
    private val applicationScope: CoroutineScope,
    private val objColorPrompt: ObjColorPrompt? = null,
) {
    private var picked: CompletableDeferred<ExternalDocumentReference?>? = null
    private var replacing: CompletableDeferred<Boolean>? = null

    /** "Reload from disk" of the object menu: the object's volumes, or of the part menu: the volume at [volume]. */
    operator fun invoke(mesh: ScenePath, volume: Int? = null) = reload { objects ->
        val index = objects.indexOfFirst { it.mesh == mesh }
        val target = objects.getOrNull(index) ?: return@reload emptyList()
        target.reloadableVolumes().filter { volume == null || it == volume }.map { index to it }
    }

    /** "Reload All" of the plate menu (reload_all_from_disk()): every object's volumes. */
    fun all() = reload { objects -> objects.flatMapIndexed { index, target -> target.reloadableVolumes().map { index to it } } }

    /** "Please select a file": the document the user picked; null for Cancel. */
    fun pick(document: ExternalDocumentReference?) {
        picked?.complete(document)
    }

    /** "Do you want to replace it ?" */
    fun replace(yes: Boolean) {
        replacing?.complete(yes)
    }

    private fun reload(volumesOf: (List<PlateObject>) -> List<Pair<Int, Int>>) {
        var request: Triple<List<PlateObject>, SlicingProfileSelection, List<Pair<Int, Int>>>? = null
        repository.update { state ->
            request = null
            val profiles = state.profiles
            // Plater::priv::can_reload_from_disk(): nothing of a cut is reloaded.
            val selected = volumesOf(state.objects).distinct().sortedWith(compareBy({ it.first }, { it.second }))
            if (state.busy || profiles == null || selected.isEmpty() || state.objects.any(PlateObject::placing) ||
                selected.any { (index, _) -> state.objects[index].isCut }
            ) {
                return@update state
            }
            request = Triple(state.objects, profiles, selected)
            state.copy(editing = true, problem = null)
        }
        val (plate, profiles, selected) = request ?: return
        applicationScope.launch {
            var objects = plate
            val notices = mutableListOf<SettingsDialog>()
            val written = mutableListOf<ScenePath>()
            try {
                val files = selected.map { (index, volume) -> plate[index].volumeInputFile(volume) }
                val inputs = mutableListOf<ReloadSource>()
                val missing = mutableListOf<String>()
                for (file in files.distinct().sorted()) {
                    val source = sourceOf(file)
                    if (source != null) inputs += source else missing += file
                }
                val replacements = mutableListOf<Pair<String, ExternalDocumentReference>>()
                while (missing.isNotEmpty()) {
                    // ask user to select the missing file
                    val search = missing.last()
                    val name = search.substringAfterLast('/')
                    val document = ask<ExternalDocumentReference?>(ReloadPrompt.PickFile(name)) { picked = it } ?: return@launch
                    val pickedName = documents.describe(document)?.name.orEmpty()
                    if (pickedName.equals(name, ignoreCase = true)) {
                        inputs += ReloadSource.Document(document)
                    } else if (ask<Boolean>(ReloadPrompt.Replace(name)) { replacing = it } == true) {
                        replacements += search to document
                    }
                    missing.removeAt(missing.lastIndex)
                }

                val failed = mutableListOf<String>()
                // load one file at a time
                reading@ for (input in inputs.distinct()) {
                    val path = when (input) {
                        is ReloadSource.File -> input.path
                        is ReloadSource.Document -> (importModel(input.document) as? ModelImportOutcome.Success)?.model?.path ?: break@reading
                    }
                    // obj_color_fun: ObjColorDialog once for the file, which every object reads alike.
                    var objColor: ObjColorChoice? = null
                    for ((index, volumes) in selected.groupBy({ it.first }, { it.second })) {
                        val prefix = sceneFiles.newImportPrefix()
                        val outcome = try {
                            var reloaded = inspector.reloadVolumes(objects.map { it.placed() }, index, volumes, path, profiles, prefix, objColor)
                            if (reloaded is ModelLoadOutcome.ObjColors) {
                                objColor = objColorPrompt?.ask(path, reloaded.question) ?: ObjColorChoice()
                                reloaded = inspector.reloadVolumes(objects.map { it.placed() }, index, volumes, path, profiles, prefix, objColor)
                            }
                            reloaded
                        } catch (cancellation: CancellationException) {
                            sceneFiles.deleteImport(prefix)
                            throw cancellation
                        } catch (error: Exception) {
                            ModelLoadOutcome.Failure(error.message.orEmpty())
                        }
                        // error while loading
                        if (outcome !is ModelLoadOutcome.Success) {
                            sceneFiles.deleteImport(prefix)
                            break@reading
                        }
                        failed += outcome.failed
                        val reloaded = outcome.objects.singleOrNull()?.toPlateObjectOf(objects[index])
                        if (reloaded == null) {
                            sceneFiles.deleteImport(prefix)
                            continue
                        }
                        written += prefix
                        objects = objects.mapIndexed { at, target -> if (at == index) reloaded else target }
                    }
                }

                for ((search, document) in replacements) {
                    val path = (importModel(document) as? ModelImportOutcome.Success)?.model?.path ?: continue
                    for ((index, volume) in selected) {
                        if (!objects[index].volumeInputFile(volume).equals(search, ignoreCase = true)) continue
                        val prefix = sceneFiles.newImportPrefix()
                        val outcome = try {
                            inspector.replaceVolume(objects.map { it.placed() }, index, volume, path, profiles, prefix)
                        } catch (cancellation: CancellationException) {
                            sceneFiles.deleteImport(prefix)
                            throw cancellation
                        } catch (error: Exception) {
                            ModelLoadOutcome.Failure(error.message.orEmpty())
                        }
                        notices += outcome.notices
                        val replaced = (outcome as? ModelLoadOutcome.Success)?.objects?.singleOrNull()?.toPlateObjectOf(objects[index])
                        if (replaced == null) {
                            sceneFiles.deleteImport(prefix)
                            break
                        }
                        written += prefix
                        objects = objects.mapIndexed { at, target -> if (at == index) replaced else target }
                    }
                }

                if (failed.isNotEmpty()) {
                    notices += SettingsDialog(
                        id = "reload_failed",
                        icon = DialogIcon.WARNING,
                        title = listOf(OrcaText("Error during reload")),
                        // The desktop app names a file by its path; the app's files are copies it made.
                        text = listOf(OrcaText("Unable to reload:"), OrcaText("%s", listOf("\n" + failed.joinToString("") { it.substringAfterLast('/') + "\n" }))),
                        question = false,
                        yes = null,
                        no = null,
                    )
                }
            } finally {
                // The colours kept for ObjColorDialog go with the reload.
                inspector.releaseObjColors()
                val changed = objects != plate
                repository.update { state ->
                    val done = state.copy(editing = false, plateNotices = state.plateNotices + notices)
                    // Every object as the plate had it is still there, unless it went meanwhile.
                    if (!changed || plate.any { before -> state.objects.none { it.mesh == before.mesh } }) {
                        done
                    } else {
                        val meshes = plate.zip(objects).associate { (before, after) -> before.mesh to after.mesh }
                        done.recorded().copy(
                            objects = objects,
                            selectedInstances = state.selectedInstances.mapTo(LinkedHashSet()) { it.copy(mesh = meshes[it.mesh] ?: it.mesh) },
                            selectedPart = state.selectedPart?.let { it.copy(mesh = meshes[it.mesh] ?: it.mesh) },
                            selectedRange = null,
                            result = null,
                        )
                    }
                }
                if (changed && repository.state.value.objects != objects) written.forEach(sceneFiles::deleteImport)
            }
        }
    }

    /**
     * Where the file a volume came from is read again: the document the app
     * copied it from while the app can read it, or the file itself on the
     * device; null for a file the app can no longer read (fs::exists()).
     */
    private fun sourceOf(file: String): ReloadSource? {
        val document = sources.documentOf(file) ?: return ReloadSource.File(ModelPath(file)).takeIf { sources.isFile(file) }
        return ReloadSource.Document(document).takeIf { documents.describe(document) != null }
    }

    /** A question of the reload waits on the plate for its answer. */
    private suspend fun <T> ask(prompt: ReloadPrompt, keep: (CompletableDeferred<T>?) -> Unit): T? {
        val answer = CompletableDeferred<T>()
        keep(answer)
        repository.update { it.copy(reloadPrompt = prompt) }
        return try {
            answer.await()
        } finally {
            keep(null)
            repository.update { if (it.reloadPrompt == prompt) it.copy(reloadPrompt = null) else it }
        }
    }

    private sealed interface ReloadSource {
        data class Document(val document: ExternalDocumentReference) : ReloadSource

        data class File(val path: ModelPath) : ReloadSource
    }
}
