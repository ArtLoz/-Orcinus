package app.orcinus.shadow.domain.plate

import app.orcinus.shadow.core.model.ExternalDocumentReference
import app.orcinus.shadow.core.model.ModelPath
import app.orcinus.shadow.core.model.PlacedModel
import app.orcinus.shadow.core.model.SelectedCopy
import app.orcinus.shadow.core.model.listPlateOf
import app.orcinus.shadow.core.model.selectedCopies
import app.orcinus.shadow.core.model.selectedObjectMeshes
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
 * The File menu's "Export all objects as one STL" and "as one DRC", and "as
 * STLs" and "as DRCs" (Plater::export_stl(false, false, multi_stls)): every
 * object of the plate, every copy where it stands, merged into the document
 * the user picked; or each object in a file of its own in the folder the user
 * picked, named after the object as get_save_file() names it ("name.stl",
 * then "name(1).stl" and on while the folder has the name). The plate shows
 * why it failed, and OrcaSlicer's notification when only the positive
 * volumes could be written.
 */
class ExportPlateMeshesUseCase(
    private val inspector: PlateInspector,
    private val sceneFiles: SceneFiles,
    private val documents: DocumentExport,
    private val folders: DocumentFolders,
    private val repository: PlateRepository,
) {
    /**
     * Plater::priv::get_export_file_path(): an STL file is named after the
     * project; a Draco file as exportFileBase() names it; [untitled] for none.
     */
    fun suggestedName(format: MeshFormat, untitled: String): String {
        val state = repository.state.value
        val base = when (format) {
            MeshFormat.STL -> state.project.name ?: untitled
            MeshFormat.DRC -> state.exportFileBase() ?: untitled
        }
        return "$base.${format.extension}"
    }

    /**
     * Every object, or with [selection] every copy of the selected objects
     * (export_stl(false, true)), merged into [document]; false when nothing
     * was written.
     */
    suspend fun toDocument(format: MeshFormat, document: ExternalDocumentReference, selection: Boolean = false): Boolean =
        export(format, multi = false, selection) { _, file -> documents.copyTo(file.value, document) }

    /** Every object, or every selected copy, into a file of its own in [folder]; false when not every file was written. */
    suspend fun toFolder(format: MeshFormat, folder: ExternalDocumentReference, selection: Boolean = false): Boolean =
        export(format, multi = true, selection) { outcome, _ ->
            outcome.files.all { mesh ->
                // get_save_file(): the object's name, numbered while the folder has it.
                var name = "${mesh.name}.${format.extension}"
                var number = 1
                while (folders.find(folder, name) != null) name = "${mesh.name}(${number++}).${format.extension}"
                val document = folders.create(folder, name, MESH_MIME_TYPE)
                document != null && documents.copyTo(mesh.path.value, document)
            }
        }

    private suspend fun export(
        format: MeshFormat,
        multi: Boolean,
        selection: Boolean,
        deliver: suspend (MeshExportOutcome.Success, ScenePath) -> Boolean,
    ): Boolean {
        val state = repository.state.value
        val profiles = state.profiles
        // Selection::get_selected_object_instances() of the objects the selection holds whole.
        val copies = if (!selection) {
            emptyList()
        } else {
            state.selectedCopies().map { id -> SelectedCopy(state.objects.indexOfFirst { it.mesh == id.mesh }, id.instance) }
        }
        // MainFrame::can_export_model()
        if (state.objects.isEmpty() || profiles == null || (selection && copies.isEmpty())) return false
        val prefix = sceneFiles.newImportPrefix()
        val file = ScenePath("${prefix.value}-export${if (multi) "" else ".${format.extension}"}")
        try {
            val outcome = try {
                inspector.exportMeshes(state.objects.map { it.placed() }, copies, multi, format, profiles, file)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                MeshExportOutcome.Failure(error.message.orEmpty())
            }
            val problem = when {
                outcome is MeshExportOutcome.Failure -> PlateProblem(PlateProblemKind.EXPORT_FAILED, outcome.message)
                !deliver(outcome as MeshExportOutcome.Success, file) -> PlateProblem(PlateProblemKind.EXPORT_FAILED)
                outcome.warning != null -> PlateProblem(PlateProblemKind.EXPORT_WITHOUT_NEGATIVE_VOLUMES)
                else -> null
            }
            problem?.let { repository.update { state -> state.copy(problem = it) } }
            return problem?.kind != PlateProblemKind.EXPORT_FAILED
        } finally {
            sceneFiles.deleteImport(prefix)
        }
    }

    private companion object {
        const val MESH_MIME_TYPE = "application/octet-stream"
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
    private val stepMeshPrompt: StepMeshPrompt,
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
                    replaceVolume(inspector, stepMeshPrompt, plate.map { it.placed() }, index, volume, source, selection, prefix)
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
 * volume of the object — or of every object a plate item of the object list
 * holds — that was read from a file takes the mesh of the file of the same
 * name in the folder the user picked, one after another, each as a step of
 * Undo ("Replace with 3D file"). OrcaSlicer's "Replaced volumes" says which
 * volumes were replaced and why the others were skipped.
 */
class ReplaceAllVolumesUseCase(
    private val importModel: ImportModelUseCase,
    private val folders: DocumentFolders,
    private val inspector: PlateInspector,
    private val sceneFiles: SceneFiles,
    private val repository: PlateRepository,
    private val applicationScope: CoroutineScope,
    private val stepMeshPrompt: StepMeshPrompt,
) {
    operator fun invoke(copy: PlateInstanceId, folder: ExternalDocumentReference) = replaceAll(listOf(copy.mesh), folder)

    /** The multi-selection menu's item: every volume of the selected objects. */
    fun selected(folder: ExternalDocumentReference) = replaceAll(repository.state.value.selectedObjectMeshes(), folder)

    /** A plate item: the objects whose first copy stands on the plate whole (PartPlate::contain_instance_totally). */
    fun onPlate(index: Int, folder: ExternalDocumentReference) {
        val state = repository.state.value
        replaceAll(state.objects.filter { state.listPlateOf(it) == index }.map(PlateObject::mesh), folder)
    }

    private fun replaceAll(meshes: List<ScenePath>, folder: ExternalDocumentReference) {
        var profiles: SlicingProfileSelection? = null
        repository.update { state ->
            profiles = null
            val targets = meshes.mapNotNull(state.objects::withMesh)
            if (state.busy || state.profiles == null || targets.size != meshes.size || targets.any(PlateObject::placing)) return@update state
            profiles = state.profiles
            state.copy(editing = true, problem = null)
        }
        val selection = profiles ?: return
        applicationScope.launch {
            val status = mutableListOf(OrcaText("Replaced with 3D files from directory:\n"), OrcaText("%s", listOf(folders.displayName(folder) + "\n\n")))
            try {
                for (first in meshes) replaceVolumes(first, folder, selection, status)
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

    /** The volumes of the object, one after another; each replaced one gives the object another mesh file. */
    private suspend fun replaceVolumes(first: ScenePath, folder: ExternalDocumentReference, selection: SlicingProfileSelection, status: MutableList<OrcaText>) {
        var mesh = first
        val volumes = (repository.state.value.objects.withMesh(mesh)?.parts?.size ?: 0) + 1
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
    }

    /** Plater::priv::replace_volume_with_stl(): the mesh file of the object afterwards; null when nothing changed. */
    private suspend fun replace(mesh: ScenePath, volume: Int, document: ExternalDocumentReference, profiles: SlicingProfileSelection): ScenePath? {
        val source = (importModel(document) as? ModelImportOutcome.Success)?.model?.path ?: return null
        val plate = repository.state.value.objects
        val index = plate.indexOfFirst { it.mesh == mesh }
        val old = plate.getOrNull(index) ?: return null
        val prefix = sceneFiles.newImportPrefix()
        val outcome = try {
            replaceVolume(inspector, stepMeshPrompt, plate.map { it.placed() }, index, volume, source, profiles, prefix)
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

/**
 * Plater::priv::replace_volume_with_stl(): a STEP file asks StepMeshDialog
 * first; its Cancel replaces nothing, as the desktop app returns false.
 */
private suspend fun replaceVolume(
    inspector: PlateInspector,
    stepMeshPrompt: StepMeshPrompt,
    plate: List<PlacedModel>,
    index: Int,
    volume: Int,
    source: ModelPath,
    profiles: SlicingProfileSelection,
    prefix: ScenePath,
): ModelLoadOutcome {
    val outcome = inspector.replaceVolume(plate, index, volume, source, profiles, prefix)
    if (outcome !is ModelLoadOutcome.StepMesh) return outcome
    val options = stepMeshPrompt.ask(source, outcome.options) ?: return ModelLoadOutcome.Success(emptyList(), outcome.notices)
    return inspector.replaceVolume(plate, index, volume, source, profiles, prefix, options)
}
