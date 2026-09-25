package app.orcinus.shadow.domain.plate

import app.orcinus.shadow.core.model.ExternalDocumentReference
import app.orcinus.shadow.core.model.volumeAt
import app.orcinus.shadow.core.model.SettingsDialog
import app.orcinus.shadow.core.model.OrcaText
import app.orcinus.shadow.core.model.DialogIcon
import app.orcinus.shadow.core.model.MeshExportOutcome
import app.orcinus.shadow.core.model.MeshFormat
import app.orcinus.shadow.core.model.ModelImportOutcome
import app.orcinus.shadow.core.model.ModelLoadOutcome
import app.orcinus.shadow.core.model.PlateInstanceId
import app.orcinus.shadow.core.model.PlateObject
import app.orcinus.shadow.core.model.PlateProblem
import app.orcinus.shadow.core.model.PlateProblemKind
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.SlicingProfileSelection
import app.orcinus.shadow.core.model.mesh
import app.orcinus.shadow.core.model.placing
import app.orcinus.shadow.domain.ImportModelUseCase
import app.orcinus.shadow.domain.placed
import app.orcinus.shadow.slicing.api.PlateInspector
import app.orcinus.shadow.storage.api.DocumentExport
import app.orcinus.shadow.storage.api.DocumentFolders
import app.orcinus.shadow.storage.api.SceneFiles
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * "Export as one STL" and "Export as one DRC" (Plater::export_stl): the object
 * with the [mesh] file, whole, is written into the document the user picked.
 * The plate shows why it failed, and OrcaSlicer's notification when only the
 * positive volumes could be written.
 */
class ExportObjectMeshUseCase(
    private val inspector: PlateInspector,
    private val sceneFiles: SceneFiles,
    private val documents: DocumentExport,
    private val repository: PlateRepository,
) {
    suspend operator fun invoke(mesh: ScenePath, format: MeshFormat, document: ExternalDocumentReference): Boolean {
        val state = repository.state.value
        val index = state.objects.indexOfFirst { it.mesh == mesh }
        val profiles = state.profiles
        if (index < 0 || profiles == null) return false
        val prefix = sceneFiles.newImportPrefix()
        val file = ScenePath("${prefix.value}-export.${format.extension}")
        try {
            val outcome = try {
                inspector.exportMesh(state.objects.map { it.placed() }, index, format, profiles, file)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                MeshExportOutcome.Failure(error.message.orEmpty())
            }
            val problem = when {
                outcome is MeshExportOutcome.Failure -> PlateProblem(PlateProblemKind.EXPORT_FAILED, outcome.message)
                !documents.copyTo(file.value, document) -> PlateProblem(PlateProblemKind.EXPORT_FAILED)
                (outcome as MeshExportOutcome.Success).warning != null -> PlateProblem(PlateProblemKind.EXPORT_WITHOUT_NEGATIVE_VOLUMES)
                else -> null
            }
            problem?.let { repository.update { state -> state.copy(problem = it) } }
            return problem?.kind != PlateProblemKind.EXPORT_FAILED
        } finally {
            sceneFiles.deleteImport(prefix)
        }
    }
}

/**
 * "Replace 3D file" (Plater::priv::replace_with_stl): the volume at [volume]
 * (ModelObject::volumes) of the object with the [mesh] file takes the mesh of
 * the document the user picked, as one step of Undo ("Replace with 3D file").
 * The object takes its place in the list and stays selected; the engine's
 * message boxes say why nothing changed.
 */
class ReplaceObjectVolumeUseCase(
    private val importModel: ImportModelUseCase,
    private val inspector: PlateInspector,
    private val sceneFiles: SceneFiles,
    private val repository: PlateRepository,
    private val applicationScope: CoroutineScope,
) {
    operator fun invoke(copy: PlateInstanceId, volume: Int, reference: ExternalDocumentReference) {
        var profiles: SlicingProfileSelection? = null
        repository.update { state ->
            profiles = null
            val target = state.objects.withMesh(copy.mesh)
            if (state.busy || state.profiles == null || target == null || target.placing || volume !in 0..target.parts.size) {
                return@update state
            }
            profiles = state.profiles
            state.copy(editing = true, problem = null)
        }
        val selection = profiles ?: return
        applicationScope.launch {
            val source = when (val imported = importModel(reference)) {
                is ModelImportOutcome.Success -> imported.model.path
                is ModelImportOutcome.Failure -> {
                    repository.update { it.copy(editing = false, problem = PlateProblem(PlateProblemKind.IMPORT_FAILED, imported.message)) }
                    return@launch
                }
            }
            val plate = repository.state.value.objects
            val index = plate.indexOfFirst { it.mesh == copy.mesh }
            val old = plate.getOrNull(index)
            val prefix = sceneFiles.newImportPrefix()
            val outcome = if (old == null) {
                ModelLoadOutcome.Failure("The object is not on the plate")
            } else {
                try {
                    inspector.replaceVolume(plate.map { it.placed() }, index, volume, source, selection, prefix)
                } catch (cancellation: CancellationException) {
                    sceneFiles.deleteImport(prefix)
                    repository.update { it.copy(editing = false) }
                    throw cancellation
                } catch (error: Exception) {
                    ModelLoadOutcome.Failure(error.message.orEmpty())
                }
            }
            val replaced = old?.let { (outcome as? ModelLoadOutcome.Success)?.objects?.singleOrNull()?.toPlateObjectOf(it) }
            if (replaced == null) sceneFiles.deleteImport(prefix)
            repository.update { state ->
                val done = state.copy(editing = false, plateNotices = state.plateNotices + outcome.notices)
                when {
                    outcome is ModelLoadOutcome.Failure -> done.copy(problem = PlateProblem(PlateProblemKind.PLACEMENT_FAILED, outcome.message))
                    replaced == null || state.objects.withMesh(copy.mesh) == null -> done
                    else -> done.recorded().copy(
                        objects = state.objects.map { if (it.mesh == copy.mesh) replaced else it },
                        selectedInstances = setOf(PlateInstanceId(replaced.mesh, copy.instance.coerceAtMost(replaced.instances.lastIndex))),
                        selectedPart = null,
                        selectedRange = null,
                        result = null,
                    )
                }
            }
        }
    }
}

/**
 * "Replace all with 3D files" (Plater::priv::replace_all_with_stl): every
 * volume of the object that was read from a file takes the mesh of the file
 * of the same name in the folder the user picked, one after another, each as
 * a step of Undo ("Replace with 3D file"). OrcaSlicer's "Replaced volumes"
 * says which volumes were replaced and why the others were skipped.
 */
class ReplaceAllVolumesUseCase(
    private val importModel: ImportModelUseCase,
    private val folders: DocumentFolders,
    private val inspector: PlateInspector,
    private val sceneFiles: SceneFiles,
    private val repository: PlateRepository,
    private val applicationScope: CoroutineScope,
) {
    operator fun invoke(copy: PlateInstanceId, folder: ExternalDocumentReference) {
        var profiles: SlicingProfileSelection? = null
        repository.update { state ->
            profiles = null
            val target = state.objects.withMesh(copy.mesh)
            if (state.busy || state.profiles == null || target == null || target.placing) return@update state
            profiles = state.profiles
            state.copy(editing = true, problem = null)
        }
        val selection = profiles ?: return
        applicationScope.launch {
            val status = mutableListOf(OrcaText("Replaced with 3D files from directory:\n"), OrcaText("%s", listOf(folders.displayName(folder) + "\n\n")))
            var mesh = copy.mesh
            val volumes = (repository.state.value.objects.withMesh(mesh)?.parts?.size ?: 0) + 1
            try {
                for (index in 0 until volumes) {
                    val target = repository.state.value.objects.withMesh(mesh) ?: break
                    // volumeAt() lists the object's own mesh only once the object has parts.
                    val part = target.volumeAt(index)
                    val inputFile = part?.inputFile ?: target.volume.inputFile.takeIf { index == 0 }.orEmpty()
                    if (inputFile.isEmpty()) continue
                    val name = (part?.name ?: target.volume.name).ifEmpty { (target as? PlateObject.ImportedModel)?.file?.displayName.orEmpty() }
                    val document = folders.find(folder, inputFile.substringAfterLast('/'))
                    if (document == null) {
                        status += OrcaText("✖ Skipped %1%: file does not exist.\n", listOf(name))
                        continue
                    }
                    val replaced = replace(mesh, index, document, selection)
                    if (replaced == null) {
                        status += OrcaText("✖ Skipped %1%: failed to replace.\n", listOf(name))
                        continue
                    }
                    mesh = replaced
                    status += OrcaText("✔ Replaced %1%.\n", listOf(name))
                }
            } finally {
                repository.update { state ->
                    state.copy(
                        editing = false,
                        plateNotices = state.plateNotices + SettingsDialog(
                            id = "replaced_volumes",
                            icon = DialogIcon.INFO,
                            title = listOf(OrcaText("Replaced volumes")),
                            text = status,
                            question = false,
                            yes = null,
                            no = null,
                        ),
                    )
                }
            }
        }
    }

    /** Plater::priv::replace_volume_with_stl(): the mesh file of the object afterwards; null when nothing changed. */
    private suspend fun replace(mesh: ScenePath, volume: Int, document: ExternalDocumentReference, profiles: SlicingProfileSelection): ScenePath? {
        val source = (importModel(document) as? ModelImportOutcome.Success)?.model?.path ?: return null
        val plate = repository.state.value.objects
        val index = plate.indexOfFirst { it.mesh == mesh }
        val old = plate.getOrNull(index) ?: return null
        val prefix = sceneFiles.newImportPrefix()
        val outcome = try {
            inspector.replaceVolume(plate.map { it.placed() }, index, volume, source, profiles, prefix)
        } catch (cancellation: CancellationException) {
            sceneFiles.deleteImport(prefix)
            throw cancellation
        } catch (error: Exception) {
            ModelLoadOutcome.Failure(error.message.orEmpty())
        }
        val replaced = (outcome as? ModelLoadOutcome.Success)?.objects?.singleOrNull()?.toPlateObjectOf(old)
        if (replaced == null) sceneFiles.deleteImport(prefix)
        repository.update { state ->
            val informed = state.copy(plateNotices = state.plateNotices + outcome.notices)
            if (replaced == null || state.objects.withMesh(mesh) == null) {
                informed
            } else {
                informed.recorded().copy(
                    objects = state.objects.map { if (it.mesh == mesh) replaced else it },
                    selectedInstances = state.selectedInstances.mapTo(LinkedHashSet()) {
                        if (it.mesh == mesh) PlateInstanceId(replaced.mesh, it.instance) else it
                    },
                    selectedPart = null,
                    selectedRange = null,
                    result = null,
                )
            }
        }
        return replaced?.mesh
    }
}
