package app.orcinus.shadow.domain.plate

import app.orcinus.shadow.core.model.DialogIcon
import app.orcinus.shadow.core.model.ExternalDocumentReference
import app.orcinus.shadow.core.model.GcodeLoadOutcome
import app.orcinus.shadow.core.model.ImportedModelFile
import app.orcinus.shadow.core.model.ModelImportOutcome
import app.orcinus.shadow.core.model.ModelPath
import app.orcinus.shadow.core.model.OrcaText
import app.orcinus.shadow.core.model.OutputPath
import app.orcinus.shadow.core.model.PlateSliceResult
import app.orcinus.shadow.core.model.PlateState
import app.orcinus.shadow.core.model.PreviewOnly
import app.orcinus.shadow.core.model.PreviewOnlyKind
import app.orcinus.shadow.core.model.SettingsDialog
import app.orcinus.shadow.core.model.SliceJobId
import app.orcinus.shadow.core.model.partPlates
import app.orcinus.shadow.domain.ImportModelUseCase
import app.orcinus.shadow.slicing.api.PlateInspector
import app.orcinus.shadow.slicing.api.PresetManager
import app.orcinus.shadow.storage.api.GcodeOutputs
import app.orcinus.shadow.storage.api.SceneFiles
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Plater::load_gcode(): a G-code file opens on its own as a new project in the
 * preview-only mode of G-code (m_only_gcode). The Preview shows its moves, with
 * the time, filament and cost of its own configuration and the presets it
 * names; the project prints on its plate type and goes by the file's name. And
 * PartPlateList::load_gcode_files() of a project of no objects whose plates
 * carry their G-code (m_exported_file): each plate's G-code is its slice
 * result, which Print::export_gcode_from_previous_file() reads for the preview.
 * Either G-code is the plate's to export, send and print.
 */
class LoadGcodeUseCase(
    private val importModel: ImportModelUseCase,
    private val inspector: PlateInspector,
    private val presetManager: PresetManager,
    private val platePresets: PresetsApplier,
    private val outputs: GcodeOutputs,
    private val sceneFiles: SceneFiles,
    private val repository: PlateRepository,
    private val applicationScope: CoroutineScope,
    /** Plater::new_project(false, true) into the preview-only mode; false when the user stayed with the project before. */
    private val newProject: suspend (PreviewOnly) -> Boolean,
    /** The imported G-code copied to where the plate keeps it; false when it could not be. */
    private val keepFile: suspend (ModelPath, OutputPath) -> Boolean,
) {
    operator fun invoke(reference: ExternalDocumentReference) {
        applicationScope.launch { load(reference) }
    }

    /**
     * Plater::load_gcode(filename) of the document [reference], already
     * copied into app storage as [imported] when the caller read it.
     */
    suspend fun load(reference: ExternalDocumentReference, imported: ImportedModelFile? = null) {
        val opened = repository.state.value.previewOnly
        // m_last_loaded_gcode == filename && m_only_gcode
        if (opened?.kind == PreviewOnlyKind.GCODE && opened.document == reference) return
        val file = imported ?: when (val outcome = importModel(reference)) {
            is ModelImportOutcome.Success -> outcome.model
            is ModelImportOutcome.Failure -> return notice(errorDialog(outcome.message))
        }
        val name = file.displayName
        if (!isGcodeFile(name)) return
        // BSS: create a new project when load_gcode, force close previous one
        if (!newProject(PreviewOnly(PreviewOnlyKind.GCODE, name, reference))) return
        repository.update { it.copy(importing = true, problem = null) }
        val before = repository.state.value.profiles
        val gcode = outputs.outputFor(name.dropLast(GCODE_EXTENSION.length))
        val outcome = if (keepFile(file.path, gcode)) {
            process(gcode, plateIndex = 0, plateCount = 1, applyBedType = true)
        } else {
            GcodeLoadOutcome.Failure("Unable to copy the G-code file $name")
        }
        // on_bed_type_change(): the presets show the G-code's plate type.
        if (outcome is GcodeLoadOutcome.Success && outcome.bedTypeChanged) platePresets.apply(before, presetManager.presets())
        repository.update { state ->
            // The new project starts on the G-code's plate type, which leaves it clean.
            val loaded = state.copy(importing = false, project = state.project.copy(bedType = state.presets?.bedType ?: state.project.bedType))
            when (outcome) {
                // show_error()
                is GcodeLoadOutcome.Failure -> loaded.copy(plateNotices = state.plateNotices + errorDialog(outcome.message))
                // The project stays "Untitled" (set_project_filename(DEFAULT_PROJECT_NAME)).
                is GcodeLoadOutcome.Success -> if (!outcome.valid) {
                    loaded.copy(plateNotices = state.plateNotices + invalidGcode(name))
                } else {
                    loaded.copy(
                        result = outcome.toResult(gcode, name),
                        // set_project_filename(filename)
                        project = loaded.project.copy(name = projectNameOf(name)),
                    )
                }
            }
        }
        keepToolpathsOfResults()
    }

    /**
     * PartPlateList::load_gcode_files() of the exported file Plater::load_project()
     * opened: the G-code of every plate that carries one ([plates], by index),
     * read with its moves standing on that plate, becomes its slice result; the
     * preview shows a plate once its G-code is read. [fileName] is the file's
     * name, which the G-code is offered under.
     */
    suspend fun loadExportedPlates(plates: List<OutputPath?>, fileName: String) {
        try {
            for ((index, gcode) in plates.withIndex()) {
                if (gcode == null) continue
                when (val outcome = process(gcode, index, plates.size, applyBedType = false)) {
                    // export_gcode_from_previous_file()'s RuntimeError, a critical error of the
                    // background process: show_error() (Plater::priv::on_process_completed()).
                    is GcodeLoadOutcome.Failure ->
                        notice(errorDialog("Failed to process the G-code file ${gcode.value} from previous 3mf\n${outcome.message}"))
                    is GcodeLoadOutcome.Success -> if (outcome.valid) {
                        val result = outcome.toResult(gcode, exportedName(fileName, index, plates.size))
                        // The codes on the plate's layers are the ones its G-code was sliced with.
                        repository.update { state ->
                            state.withPlateResult(index, result.copy(layerGcodes = state.partPlates().getOrNull(index)?.layerGcodes.orEmpty()))
                        }
                    }
                }
            }
        } finally {
            repository.update { it.copy(importing = false) }
        }
        keepToolpathsOfResults()
    }

    private suspend fun process(gcode: OutputPath, plateIndex: Int, plateCount: Int, applyBedType: Boolean): GcodeLoadOutcome {
        val toolpaths = sceneFiles.newToolpaths()
        return try {
            inspector.loadGcode(gcode, toolpaths, sceneFiles.sliceInfoOf(toolpaths), plateIndex, plateCount, applyBedType)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            GcodeLoadOutcome.Failure(error.message.orEmpty())
        }
    }

    /** Only the toolpaths of the plates' results stay. */
    private fun keepToolpathsOfResults() {
        sceneFiles.deleteToolpathsExcept(repository.state.value.partPlates().mapNotNull { it.result?.toolpaths })
    }

    private fun notice(dialog: SettingsDialog) = repository.update { it.copy(plateNotices = it.plateNotices + dialog) }

    private companion object {
        const val GCODE_EXTENSION = ".gcode"
        const val THREE_MF_EXTENSION = ".3mf"

        /** GCODEVIEWER_APP_NAME, which the desktop app writes as it is. */
        const val GCODEVIEWER_APP_NAME = "OrcaSlicer G-code Viewer"

        /** is_gcode_file(): load_gcode() opens a file that ends in ".gcode", and nothing else. */
        fun isGcodeFile(name: String) = name.endsWith(GCODE_EXTENSION, ignoreCase = true)

        fun errorDialog(message: String) =
            SettingsDialog("gcode_load_error", DialogIcon.ERROR, emptyList(), listOf(OrcaText("%s", listOf(message))), question = false, yes = null, no = null)

        /** load_gcode()'s message when the preview has no layers to show. */
        fun invalidGcode(name: String) = SettingsDialog(
            id = "gcode_invalid",
            icon = DialogIcon.WARNING,
            title = listOf(OrcaText("%s", listOf("$GCODEVIEWER_APP_NAME - ")), OrcaText("Error occurs while loading G-code file")),
            text = listOf(OrcaText("The selected file"), OrcaText("%s", listOf(":\n$name\n")), OrcaText("does not contain valid G-code.")),
            question = false,
            yes = null,
            no = null,
        )

        /**
         * The name a plate's G-code from an exported file is offered under:
         * the file's without ".3mf" and ".gcode", with the plate's number when
         * the file has several, as Plater::priv::get_export_gcode_filename()
         * names a plate's.
         */
        fun exportedName(fileName: String, index: Int, count: Int): String {
            val base = fileName.removeSuffix(THREE_MF_EXTENSION).let { if (it.endsWith(GCODE_EXTENSION, ignoreCase = true)) it.dropLast(GCODE_EXTENSION.length) else it }
            return base + (if (count > 1) "_plate_${index + 1}" else "") + GCODE_EXTENSION
        }
    }
}

/** The plate's result of a G-code file read for the preview, offered under [name]. */
private fun GcodeLoadOutcome.Success.toResult(gcode: OutputPath, name: String) = PlateSliceResult(
    jobId = SliceJobId(UUID.randomUUID().toString()),
    objects = emptyList(),
    gcode = gcode,
    statistics = statistics,
    toolpaths = toolpaths,
    sliceInfo = sliceInfo,
    outputName = name,
    settingsIds = settingsIds,
)

/** The state with [result] as the slice result of the plate at [index], the current one or another. */
private fun PlateState.withPlateResult(index: Int, result: PlateSliceResult): PlateState = when {
    index == currentPlate -> copy(result = result)
    index in plates.indices -> copy(plates = plates.mapIndexed { at, plate -> if (at == index) plate.copy(result = result, basis = null) else plate })
    else -> this
}

/**
 * BackgroundSlicingProcess::apply() in the G-code viewer mode: no change of
 * the settings resets the G-code of a file the preview shows on its own, nor
 * the G-code of an exported file's plates.
 */
internal val PlateState.keepsGcode: Boolean get() = previewOnly != null
