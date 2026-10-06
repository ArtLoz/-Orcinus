package app.orcinus.shadow.domain.plate

import app.orcinus.shadow.core.model.AppConfigKeys
import app.orcinus.shadow.core.model.BedFileOutcome
import app.orcinus.shadow.core.model.BedPreview
import app.orcinus.shadow.core.model.BedShape
import app.orcinus.shadow.core.model.BedShapeOutcome
import app.orcinus.shadow.core.model.BedTypeChoice
import app.orcinus.shadow.core.model.BonjourReply
import app.orcinus.shadow.core.model.BuildVolumeFit
import app.orcinus.shadow.core.model.BuiltInModel
import app.orcinus.shadow.core.model.CloudLoginOutcome
import app.orcinus.shadow.core.model.ConfigExportKind
import app.orcinus.shadow.core.model.ConfigExportOptionsOutcome
import app.orcinus.shadow.core.model.ConfigOverwriteAnswer
import app.orcinus.shadow.core.model.ConfigTransferOutcome
import app.orcinus.shadow.core.model.CreateFilamentOptionsOutcome
import app.orcinus.shadow.core.model.CreateFilamentRequest
import app.orcinus.shadow.core.model.CreatePrinterOptionsOutcome
import app.orcinus.shadow.core.model.CreatePrinterRequest
import app.orcinus.shadow.core.model.CrealityHost
import app.orcinus.shadow.core.model.CustomFilamentsOutcome
import app.orcinus.shadow.core.model.DialogIcon
import app.orcinus.shadow.core.model.EngineAvailability
import app.orcinus.shadow.core.model.EnginePlate
import app.orcinus.shadow.core.model.EngineState
import app.orcinus.shadow.core.model.ExternalDocumentReference
import app.orcinus.shadow.core.model.FilamentPresetsOutcome
import app.orcinus.shadow.core.model.FlashforgeDiscoveryOutcome
import app.orcinus.shadow.core.model.FlashforgeSlotsOutcome
import app.orcinus.shadow.core.model.FlushVolumesChange
import app.orcinus.shadow.core.model.HandyModel
import app.orcinus.shadow.core.model.HostPrintersOutcome
import app.orcinus.shadow.core.model.HostStorageOutcome
import app.orcinus.shadow.core.model.ImportBatch
import app.orcinus.shadow.core.model.ImportFiles
import app.orcinus.shadow.core.model.ImportedModelFile
import app.orcinus.shadow.core.model.LayerGcode
import app.orcinus.shadow.core.model.LayerRange
import app.orcinus.shadow.core.model.LayerRangeEditor
import app.orcinus.shadow.core.model.LayerRangeId
import app.orcinus.shadow.core.model.ListClipboard
import app.orcinus.shadow.core.model.LoadProgress
import app.orcinus.shadow.core.model.LoadedObject
import app.orcinus.shadow.core.model.Manipulation
import app.orcinus.shadow.core.model.MeshBooleanPicks
import app.orcinus.shadow.core.model.ModelImportOutcome
import app.orcinus.shadow.core.model.ModelInspection
import app.orcinus.shadow.core.model.ModelInspectionOutcome
import app.orcinus.shadow.core.model.ModelLoad
import app.orcinus.shadow.core.model.ModelLoadOutcome
import app.orcinus.shadow.core.model.ModelPath
import app.orcinus.shadow.core.model.ModelSettings
import app.orcinus.shadow.core.model.ModelSettingsOutcome
import app.orcinus.shadow.core.model.ModelSource
import app.orcinus.shadow.core.model.ObjColorChoice
import app.orcinus.shadow.core.model.ObjectEdit
import app.orcinus.shadow.core.model.ObjectPart
import app.orcinus.shadow.core.model.ObjectPartId
import app.orcinus.shadow.core.model.OrcaText
import app.orcinus.shadow.core.model.OutputPath
import app.orcinus.shadow.core.model.PartPlate
import app.orcinus.shadow.core.model.PendingPlateQuestion
import app.orcinus.shadow.core.model.PendingPresetChange
import app.orcinus.shadow.core.model.PhysicalPrinter
import app.orcinus.shadow.core.model.PlacedModel
import app.orcinus.shadow.core.model.PlateDescription
import app.orcinus.shadow.core.model.PlateDescriptionOutcome
import app.orcinus.shadow.core.model.PlateGrid
import app.orcinus.shadow.core.model.PlateHistory
import app.orcinus.shadow.core.model.PlateInspectionOutcome
import app.orcinus.shadow.core.model.PlateInstance
import app.orcinus.shadow.core.model.PlateInstanceId
import app.orcinus.shadow.core.model.PlateManipulation
import app.orcinus.shadow.core.model.PlateObject
import app.orcinus.shadow.core.model.PlateProblem
import app.orcinus.shadow.core.model.PlateProblemKind
import app.orcinus.shadow.core.model.PlateProject
import app.orcinus.shadow.core.model.PlateRequest
import app.orcinus.shadow.core.model.PlateSliceResult
import app.orcinus.shadow.core.model.PlateSlicing
import app.orcinus.shadow.core.model.PlateState
import app.orcinus.shadow.core.model.Point2
import app.orcinus.shadow.core.model.PresetBundlesOutcome
import app.orcinus.shadow.core.model.PresetChangeAction
import app.orcinus.shadow.core.model.PresetChoice
import app.orcinus.shadow.core.model.PresetCreationOutcome
import app.orcinus.shadow.core.model.PresetKind
import app.orcinus.shadow.core.model.PresetNamesOutcome
import app.orcinus.shadow.core.model.PresetSave
import app.orcinus.shadow.core.model.PresetTransfer
import app.orcinus.shadow.core.model.PresetsOutcome
import app.orcinus.shadow.core.model.PrintHostTestOutcome
import app.orcinus.shadow.core.model.PrintHostUploadOutcome
import app.orcinus.shadow.core.model.PrintOptions
import app.orcinus.shadow.core.model.Printer3dOsListsOutcome
import app.orcinus.shadow.core.model.PrinterConnectionOutcome
import app.orcinus.shadow.core.model.PrinterSlotsOutcome
import app.orcinus.shadow.core.model.ProfileId
import app.orcinus.shadow.core.model.ProfileUpdate
import app.orcinus.shadow.core.model.ProjectContent
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.SettingsDialog
import app.orcinus.shadow.core.model.SettingsScope
import app.orcinus.shadow.core.model.SetupFilamentsOutcome
import app.orcinus.shadow.core.model.SetupPrintersOutcome
import app.orcinus.shadow.core.model.SliceFailureCode
import app.orcinus.shadow.core.model.SliceJobId
import app.orcinus.shadow.core.model.SliceOutcome
import app.orcinus.shadow.core.model.SliceOutputNaming
import app.orcinus.shadow.core.model.SliceRequest
import app.orcinus.shadow.core.model.SlicingProfileSelection
import app.orcinus.shadow.core.model.Transform3
import app.orcinus.shadow.core.model.Vector3
import app.orcinus.shadow.core.model.VolumeType
import app.orcinus.shadow.core.model.currentArrangeSettings
import app.orcinus.shadow.core.model.extruderNumber
import app.orcinus.shadow.core.model.isCut
import app.orcinus.shadow.core.model.lockedPlates
import app.orcinus.shadow.core.model.mesh
import app.orcinus.shadow.core.model.partPlates
import app.orcinus.shadow.core.model.placing
import app.orcinus.shadow.core.model.plateGrid
import app.orcinus.shadow.core.model.plateOf
import app.orcinus.shadow.core.model.plateOrigin
import app.orcinus.shadow.core.model.scalingFactor
import app.orcinus.shadow.core.model.volumeAt
import app.orcinus.shadow.core.model.withArrangeSettings
import app.orcinus.shadow.core.model.withInstance
import app.orcinus.shadow.core.model.withInstances
import app.orcinus.shadow.core.model.withLayerRangeAt
import app.orcinus.shadow.core.model.withLayerRanges
import app.orcinus.shadow.core.model.withName
import app.orcinus.shadow.core.model.withPart
import app.orcinus.shadow.core.model.withPartAt
import app.orcinus.shadow.core.model.withParts
import app.orcinus.shadow.core.model.withScalingFactor
import app.orcinus.shadow.core.model.withSettings
import app.orcinus.shadow.core.model.withVolume
import app.orcinus.shadow.core.model.withVolumeAt
import app.orcinus.shadow.domain.CancelSliceUseCase
import app.orcinus.shadow.domain.GetEngineStatusUseCase
import app.orcinus.shadow.domain.ImportModelUseCase
import app.orcinus.shadow.domain.InspectModelUseCase
import app.orcinus.shadow.domain.PlaceModelUseCase
import app.orcinus.shadow.domain.PlaceModelsUseCase
import app.orcinus.shadow.domain.SliceModelUseCase
import app.orcinus.shadow.domain.SliceProgressObserver
import app.orcinus.shadow.domain.placed
import app.orcinus.shadow.domain.preferences.AppPreferences
import app.orcinus.shadow.domain.source
import app.orcinus.shadow.slicing.api.PlateInspector
import app.orcinus.shadow.slicing.api.PresetManager
import app.orcinus.shadow.slicing.api.PresetSettingsEditor
import app.orcinus.shadow.storage.api.BedFiles
import app.orcinus.shadow.storage.api.CertificateFiles
import app.orcinus.shadow.storage.api.ConfigFiles
import app.orcinus.shadow.storage.api.DocumentExport
import app.orcinus.shadow.storage.api.FileShare
import app.orcinus.shadow.storage.api.GcodeOutputs
import app.orcinus.shadow.storage.api.PlateCache
import app.orcinus.shadow.storage.api.ProjectBackup
import app.orcinus.shadow.storage.api.SceneFiles
import java.util.UUID
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

// Plate use cases run long operations in the application scope, so an import
// or a slice outlives the screen that started it. Results land in the
// repository, which every screen observes. Objects are told apart by their
// mesh files.

class ObservePlateUseCase(private val repository: PlateRepository) {
    operator fun invoke(): StateFlow<PlateState> = repository.state
}

/**
 * Prepares the engine and the plate once per process; later calls return at
 * once. Object meshes and toolpaths left by an earlier process are deleted,
 * since its plate is gone. The engine reports the Preferences and the presets
 * its app configuration remembers and describes the plate of the selected
 * printer, unless the Setup Wizard has yet to install one.
 */
class StartEngineUseCase(
    private val getEngineStatus: GetEngineStatusUseCase,
    private val presetManager: PresetManager,
    private val platePresets: PlatePresets,
    private val sceneFiles: SceneFiles,
    private val plateCache: PlateCache,
    private val repository: PlateRepository,
    private val preferences: AppPreferences,
    /** What follows the start: GUI_App::on_init_inner()'s preset_updater->sync(). */
    private val onStarted: () -> Unit = {},
) {
    suspend operator fun invoke() {
        val state = repository.state.value
        if (state.engine.availability == EngineAvailability.READY && state.presets != null) return
        if (state.objects.isEmpty()) {
            sceneFiles.deleteAllObjectMeshes()
            sceneFiles.deleteToolpathsExcept(emptyList())
        }
        // The engine loads OrcaSlicer's profiles for seconds before it can
        // describe the plate; until then the 3D view shows the plate of the
        // last run, which the engine's answer replaces.
        if (state.plate == null) {
            plateCache.read()?.let { cached ->
                repository.update { if (it.plate == null) it.copy(plate = cached.description, plateFromCache = true) else it }
            }
        }
        val status = getEngineStatus()
        repository.update {
            it.copy(
                engine = EngineState(
                    availability = if (status.ready) EngineAvailability.READY else EngineAvailability.UNAVAILABLE,
                    version = status.version,
                ),
                problem = if (status.ready) it.problem else PlateProblem(PlateProblemKind.ENGINE_UNAVAILABLE, status.message),
            )
        }
        if (!status.ready) return
        preferences.load()
        // GUI_App::on_init_inner(): a configuration that could not be parsed was
        // made anew, which the app says once.
        if (presetManager.takeConfigCorrupted()) {
            repository.update { it.copy(plateNotices = it.plateNotices + CONFIG_CORRUPTED) }
        }
        platePresets.apply(before = null, outcome = presetManager.presets())
        onStarted()
    }

    private companion object {
        val CONFIG_CORRUPTED = SettingsDialog(
            id = "config_corrupted",
            icon = DialogIcon.ERROR,
            title = emptyList(),
            text = listOf(
                OrcaText(
                    "The OrcaSlicer configuration file may be corrupted and cannot be parsed.\nOrcaSlicer has attempted to recreate the " +
                        "configuration file.\nPlease note, application settings will be lost, but printer profiles will not be affected.",
                ),
            ),
            question = false,
            yes = null,
            no = null,
        )
    }
}

/**
 * Brings the plate to the presets the engine reports. G-code sliced with other
 * presets no longer applies. The plate is described again for another printer
 * or filament, whose colour it shows, and the objects' fit is judged against
 * another printer's build volume, as Plater::on_config_change() does; the
 * process tab loads the selection. Presets that still need the Setup Wizard
 * leave the plate undescribed.
 */
fun interface PresetsApplier {
    /** Lets the sidebar and the plate follow [outcome] of a change that started from [before]. */
    suspend fun apply(before: SlicingProfileSelection?, outcome: PresetsOutcome)
}

class PlatePresets(
    private val inspector: PlateInspector,
    private val sceneFiles: SceneFiles,
    private val plateCache: PlateCache,
    private val repository: PlateRepository,
    private val placePlateObjects: PlacePlateObjectsUseCase,
    private val settingsTabs: PresetSettingsTabs,
    /** Plater::on_config_change() after another preset is selected (auto slice after changes). */
    private val onConfigChange: () -> Unit = {},
    /** Tab::on_presets_changed() of the printer tab when another printer is selected (the profile updater's check). */
    private val onPrinterChange: () -> Unit = {},
) : PresetsApplier {
    /** Applies [outcome] of a change that started from the selection [before]. */
    override suspend fun apply(before: SlicingProfileSelection?, outcome: PresetsOutcome) {
        // The plates as they stood for the printer before (Plater::priv::on_select_preset()'s old_plate_pos).
        val previousPlate = repository.state.value.plate
        val presets = when (outcome) {
            is PresetsOutcome.Failure -> {
                repository.update { it.copy(changingPresets = false, problem = PlateProblem(PlateProblemKind.PRESETS_FAILED, outcome.message)) }
                return
            }
            // A selection that asks about unsaved changes is answered before it
            // reaches the plate (SelectPresetUseCase).
            is PresetsOutcome.UnsavedChanges -> outcome.presets
            is PresetsOutcome.Success -> outcome.presets
        }
        var bedTypeChanged = false
        var colorsChanged = false
        repository.update { state ->
            val updated = state.copy(presets = presets, changingPresets = false)
            // Plater::priv::on_select_bed_type(): the G-code of the plates that
            // print on the project's plate type no longer applies to another one.
            bedTypeChanged = state.presets.let { it != null && it.bedType != presets.bedType }
            // Plater::on_config_change() of filament_colour: the G-code export
            // writes the colours (Print's steps_gcode).
            colorsChanged = state.presets.let { it != null && it.filamentColors != presets.filamentColors }
            updated.copy(result = state.result.takeIf { updated.profiles == before && !colorsChanged })
                .let { if (bedTypeChanged) it.withoutResultsOnProjectBedType() else it }
                // Sidebar::reset_bed_type_combox_choices() for another printer:
                // PartPlateList::check_all_plate_local_bed_type().
                .let { if (before != null && before.printer != presets.selection.printer) it.withSupportedBedTypes(presets.bedTypes) else it }
                // Sidebar::update_presets() of the printer: the arrangement aligns to the Y axis on an i3 printer.
                .let { if (before == null || before.printer != presets.selection.printer) it.withArrangeSettings(it.currentArrangeSettings.copy(alignToYAxis = presets.i3Structure)) else it }
        }
        val profiles = repository.state.value.profiles ?: return
        val plateChanged = before?.printer != profiles.printer || before.filament != profiles.filament
        val state = repository.state.value
        if (plateChanged || state.plate == null || state.plateFromCache) {
            // Without a description the 3D view shows no plate; slicing does not depend on it.
            val plate = inspector.describePlate(profiles, sceneFiles.plateDirectory())
            if (plate is PlateDescriptionOutcome.Success) {
                repository.update { if (it.profiles == profiles) it.copy(plate = plate.description, plateFromCache = false) else it }
                // The next run draws this plate while the engine starts.
                plateCache.write(profiles.printer.value, plate.description)
            }
        }
        if (before != null && before.printer != profiles.printer) {
            repository.update { it.withPrinterPlates(previousPlate) }
            placePlateObjects(PlateManipulation.UpdatePrintVolume)
            onPrinterChange()
        }
        settingsTabs.refresh()
        if (before != profiles || bedTypeChanged || colorsChanged) onConfigChange()
    }
}

/**
 * Plater::priv::on_select_preset() of another printer, on the plates laid out
 * for it: update_objects_position_when_select_preset() puts every plate's wipe
 * tower where a new one stands (set_default_wipe_tower_pos_for_plate(), which
 * the tower's description writes), and when the current plate's centre moved
 * ("Model reset by plate center"), the objects whose first copy stood on each
 * plate before move together, keeping their height, until the centre of
 * their box stands over that plate's centre (Selection::center_plate()).
 */
internal fun PlateState.withPrinterPlates(previous: PlateDescription?): PlateState {
    val towersReset = copy(
        plateSettings = ModelSettings(plateSettings.values - WIPE_TOWER_KEYS),
        plates = plates.map { it.copy(settings = ModelSettings(it.settings.values - WIPE_TOWER_KEYS)) },
    )
    val oldGrid = previous?.let { PlateGrid(it.geometry.printableArea) } ?: return towersReset
    val newGrid = plateGrid ?: return towersReset
    val count = plates.size
    if (oldGrid.centerOf(currentPlate, count) == newGrid.centerOf(currentPlate, count)) return towersReset
    val oldHeight = previous.geometry.printableHeight
    var moved = towersReset.objects
    for (plate in 0 until count) {
        val origin = oldGrid.originOf(plate, count)
        // contain_instance(object, 0) on the plates before.
        val group = towersReset.objects.filter { plateObject ->
            plateObject.instances.firstOrNull()?.let { oldGrid.intersects(it.inspection, origin, oldHeight) } == true
        }.map { it.mesh }.toSet()
        val copies = moved.filter { it.mesh in group }.flatMap { it.instances }
        if (copies.isEmpty()) continue
        val minX = copies.minOf { it.inspection.boxCenter.x - it.inspection.dimensions.widthMillimeters / 2 }
        val maxX = copies.maxOf { it.inspection.boxCenter.x + it.inspection.dimensions.widthMillimeters / 2 }
        val minY = copies.minOf { it.inspection.boxCenter.y - it.inspection.dimensions.depthMillimeters / 2 }
        val maxY = copies.maxOf { it.inspection.boxCenter.y + it.inspection.dimensions.depthMillimeters / 2 }
        val center = newGrid.centerOf(plate, count)
        val dx = center.x - (minX + maxX) / 2
        val dy = center.y - (minY + maxY) / 2
        moved = moved.map { plateObject ->
            if (plateObject.mesh !in group) plateObject else plateObject.withInstances(plateObject.instances.map { it.copy(inspection = it.inspection.movedBy(dx, dy)) })
        }
    }
    // Selection::center_plate()'s do_move() takes "Move Object".
    return if (moved == towersReset.objects) towersReset else towersReset.recorded().copy(objects = moved, result = null)
}

/** The copy moved by [dx], [dy] across the plate: its placement, box and sphere with it. */
internal fun ModelInspection.movedBy(dx: Double, dy: Double): ModelInspection = copy(
    placement = Transform3(placement.columns.mapIndexed { index, value -> value + when (index) { 12 -> dx; 13 -> dy; else -> 0.0 } }),
    boxCenter = Vector3(boxCenter.x + dx, boxCenter.y + dy, boxCenter.z),
    boundingSphere = boundingSphere.copy(center = Vector3(boundingSphere.center.x + dx, boundingSphere.center.y + dy, boundingSphere.center.z)),
)

/** wipe_tower_x and wipe_tower_y of a plate. */
private val WIPE_TOWER_KEYS = setOf("wipe_tower_x", "wipe_tower_y")

/** The plate type of a plate's own settings (PartPlate::get_bed_type), none for the project's. */
private const val PLATE_BED_TYPE = "curr_bed_type"

/** update_slice_result_valid_state(false) of every plate on the project's plate type (btDefault). */
private fun PlateState.withoutResultsOnProjectBedType(): PlateState = copy(
    result = result.takeIf { PLATE_BED_TYPE in plateSettings.values },
    plates = plates.mapIndexed { index, plate ->
        if (index == currentPlate || PLATE_BED_TYPE in plate.settings.values) plate else plate.copy(result = null, basis = null)
    },
)

/**
 * PartPlateList::check_all_plate_local_bed_type(): a plate whose own plate
 * type the printer lacks prints on the project's (set_bed_type(btDefault)).
 */
private fun PlateState.withSupportedBedTypes(types: List<BedTypeChoice>): PlateState {
    if (types.isEmpty()) return this
    fun ModelSettings.supported(): ModelSettings {
        val own = values[PLATE_BED_TYPE] ?: return this
        return if (types.any { it.value == own }) this else ModelSettings(values - PLATE_BED_TYPE)
    }
    return copy(plateSettings = plateSettings.supported(), plates = plates.map { it.copy(settings = it.settings.supported()) })
}

/**
 * Selects a preset as OrcaSlicer's sidebar does, and brings the plate to the
 * presets that come with it. Nothing changes while the plate is busy, while an
 * object's placement is settling, or before a printer is set up.
 */
/**
 * The filaments of the plate, as OrcaSlicer's sidebar keeps them: another one
 * joins it (Sidebar::add_custom_filament), one leaves it
 * (Sidebar::delete_filament), a slot takes another preset, or a slot is given
 * another colour. The plate follows the change, since it prints with them.
 */
class PlateFilamentsUseCase(
    private val presetManager: PresetManager,
    private val platePresets: PlatePresets,
    private val flushVolumes: FlushVolumesUpdater,
    private val repository: PlateRepository,
    private val applicationScope: CoroutineScope,
    /** Plater::on_filaments_delete() and on_filament_count_change() of the objects and plates. */
    private val renumbering: PlateFilamentRenumbering? = null,
) {
    // The added filament is the last one; Sidebar::add_custom_filament() ends with on_filament_count_change().
    fun add() = change(FlushVolumesChange.FILAMENT_ADDED, index = null, plate = { before, count -> renumbering?.countChanged(count, before) }) {
        presetManager.addFilament()
    }

    /**
     * Sidebar::delete_filament(): the filament at [index] leaves, and
     * [mergeInto] (from 0 among the filaments before, -1 for none) takes its
     * objects, facets and filament changes, as "Merge with" asks
     * (Sidebar::change_filament()).
     */
    fun remove(index: Int, mergeInto: Int = -1) = change(
        FlushVolumesChange.FILAMENT_REMOVED,
        index,
        plate = { _, count -> renumbering?.deleted(index, if (mergeInto > index) mergeInto - 1 else mergeInto, count) },
    ) { presetManager.removeFilament(index) }

    fun select(index: Int, name: ProfileId) = change(FlushVolumesChange.FILAMENT_CHANGED, index) { presetManager.selectFilament(index, name) }

    fun setColor(index: Int, color: String) = change(FlushVolumesChange.COLOR_CHANGED, index) { presetManager.setFilamentColor(index, color) }

    /**
     * Runs [action], brings the plate to the presets it leaves, the objects
     * and plates to the filaments ([plate], with the number of filaments
     * before and after), and then the flushing volumes to the filaments, as
     * the sidebar's handlers end with auto_calc_flushing_volumes().
     */
    private fun change(
        flush: FlushVolumesChange,
        index: Int?,
        plate: suspend (before: Int, count: Int) -> Unit = { _, _ -> },
        action: suspend () -> PresetsOutcome,
    ) {
        var before: SlicingProfileSelection? = null
        repository.update { state ->
            before = null
            if (state.busy || state.objects.any(PlateObject::placing)) return@update state
            before = state.profiles ?: return@update state
            state.copy(changingPresets = true, problem = null)
        }
        val selection = before ?: return
        applicationScope.launch {
            val outcome = action()
            platePresets.apply(before = selection, outcome = outcome)
            if (outcome !is PresetsOutcome.Success) return@launch
            // on_select_preset() of a filament and the colour's update_project_dirty_from_presets().
            repository.update { it.withOtherChanges() }
            val count = repository.state.value.profiles?.allFilaments?.size ?: return@launch
            plate(selection.allFilaments.size, count)
            val changed = index ?: (count - 1)
            flushVolumes.update(flush, changed)
        }
    }
}

class SelectPresetUseCase(
    private val presetManager: PresetManager,
    private val platePresets: PlatePresets,
    private val flushVolumes: FlushVolumesUpdater,
    private val settingsTabs: PresetSettingsTabs,
    private val repository: PlateRepository,
    private val applicationScope: CoroutineScope,
) {
    operator fun invoke(choice: PresetChoice, action: PresetChangeAction = PresetChangeAction.ASK) {
        var before: SlicingProfileSelection? = null
        repository.update { state ->
            before = null
            if (state.busy || state.objects.any(PlateObject::placing)) return@update state
            before = state.profiles ?: return@update state
            state.copy(changingPresets = true, problem = null, presetChange = null)
        }
        val selection = before ?: return
        applicationScope.launch { select(choice, action, selection) }
    }

    /**
     * Plater::priv::on_select_bed_type(): the sidebar's plate type, which the
     * project prints on and OrcaSlicer remembers for the printer.
     */
    fun selectBedType(value: String) {
        var before: SlicingProfileSelection? = null
        repository.update { state ->
            before = null
            if (state.busy || state.objects.any(PlateObject::placing) || state.presets?.bedType == value) return@update state
            before = state.profiles ?: return@update state
            state.copy(changingPresets = true, problem = null, presetChange = null)
        }
        val selection = before ?: return
        applicationScope.launch {
            val outcome = presetManager.selectBedType(value)
            platePresets.apply(selection, outcome)
            if (outcome is PresetsOutcome.Success) repository.update { it.withOtherChanges() }
        }
    }

    /**
     * DiffPresetDialog's Transfer (MainFrame::bind_diff_dialog): the values the
     * tree has selected move from the left preset of every kind into its right
     * one, the printer first, as the desktop app transfers them in turn. The
     * app selects each right preset and keeps the values as unsaved changes.
     */
    fun transfer(transfers: List<PresetTransfer>) {
        var before: SlicingProfileSelection? = null
        repository.update { state ->
            before = null
            if (state.busy || state.objects.any(PlateObject::placing)) return@update state
            before = state.profiles ?: return@update state
            state.copy(changingPresets = true, problem = null, presetChange = null)
        }
        val selection = before ?: return
        val moved = transfers.filter { it.options.isNotEmpty() }
        if (moved.isEmpty()) {
            repository.update { it.copy(changingPresets = false) }
            return
        }
        applicationScope.launch {
            for (transfer in moved) {
                // The presets the previous transfer left behind.
                val start = repository.state.value.profiles ?: selection
                repository.update { it.copy(changingPresets = true) }
                val outcome = presetManager.transferPresetOptions(transfer.kind, transfer.from, transfer.to, transfer.options)
                if (outcome is PresetsOutcome.UnsavedChanges) {
                    repository.update { state ->
                        state.copy(
                            changingPresets = false,
                            presetChange = PendingPresetChange(
                                choice = transfer.choice(),
                                kind = outcome.kind,
                                changes = outcome.changes,
                                canTransfer = outcome.canTransfer,
                                saveName = outcome.saveName,
                                saveNameCopySuffix = outcome.saveNameCopySuffix,
                                saveCanOverwrite = outcome.saveCanOverwrite,
                            ),
                        )
                    }
                    return@launch
                }
                platePresets.apply(start, outcome)
                if (outcome !is PresetsOutcome.Success) return@launch
                updateFlushVolumes(start)
                // The preset now holds other values, which slice differently; Tab::on_presets_changed().
                repository.update { it.copy(result = null).withOtherChanges() }
            }
        }
    }

    /** The preset the transfer selects, as the sidebar would select it. */
    private fun PresetTransfer.choice(): PresetChoice = when (kind) {
        PresetKind.PRINTER -> PresetChoice.Printer(ProfileId(to))
        PresetKind.FILAMENT -> PresetChoice.Filament(ProfileId(to))
        else -> PresetChoice.Process(ProfileId(to))
    }

    /**
     * The Transfer and Discard buttons of the unsaved-changes dialog: the
     * choice that waits runs again with what happens to the changes.
     */
    fun resolve(action: PresetChangeAction) {
        val pending = repository.state.value.presetChange ?: return
        invoke(pending.choice, action)
    }

    /**
     * Its Save button: the changes are saved as [save] says, which leaves the
     * preset unmodified, and the choice that waits runs.
     */
    fun save(save: PresetSave) {
        val pending = repository.state.value.presetChange ?: return
        var selection: SlicingProfileSelection? = null
        repository.update { state ->
            selection = null
            if (state.busy || state.objects.any(PlateObject::placing)) return@update state
            selection = state.profiles ?: return@update state
            state.copy(changingPresets = true, problem = null, presetChange = null)
        }
        val before = selection ?: return
        applicationScope.launch {
            settingsTabs.save(pending.kind, save)
            // The saved preset has no changes left; the selection asks about the next one that has.
            select(pending.choice, PresetChangeAction.ASK, before)
        }
    }

    /** Its Cancel button: nothing is selected, and the changes stay. */
    fun cancelPresetChange() = repository.update { it.copy(presetChange = null) }

    private suspend fun select(choice: PresetChoice, action: PresetChangeAction, before: SlicingProfileSelection) {
        when (val outcome = presetManager.selectPreset(choice, action)) {
            // Tab::may_discard_current_dirty_preset(): the app asks the user
            // what happens to the changes, and selects again with the answer.
            is PresetsOutcome.UnsavedChanges -> repository.update { state ->
                state.copy(
                    changingPresets = false,
                    presetChange = PendingPresetChange(
                        choice = choice,
                        kind = outcome.kind,
                        changes = outcome.changes,
                        canTransfer = outcome.canTransfer,
                        saveName = outcome.saveName,
                        saveNameCopySuffix = outcome.saveNameCopySuffix,
                        saveCanOverwrite = outcome.saveCanOverwrite,
                    ),
                )
            }
            else -> {
                platePresets.apply(before, outcome)
                if (outcome is PresetsOutcome.Success) {
                    // Tab::select_preset()'s on_presets_changed(): update_project_dirty_from_presets().
                    repository.update { it.withOtherChanges() }
                    updateFlushVolumes(before)
                }
            }
        }
    }

    /**
     * Sidebar::edit_filament(): the filament tab selects the preset of filament
     * slot [slot] and edits that slot until its page closes.
     */
    fun editFilament(slot: Int) = invoke(PresetChoice.EditFilament(slot))

    /** ParamsDialog closing (Sidebar::finish_param_edit()): the filament tab edits no slot any more. */
    fun finishFilamentEdit() {
        applicationScope.launch { presetManager.finishFilamentEdit() }
    }

    /**
     * on_select_preset(): another printer works every filament's volumes out
     * again, another filament its own.
     */
    private suspend fun updateFlushVolumes(before: SlicingProfileSelection) {
        val after = repository.state.value.profiles ?: return
        val filaments = after.allFilaments
        val changed = filaments.indices.firstOrNull { filaments[it] != before.allFilaments.getOrNull(it) }
        when {
            after.printer != before.printer -> flushVolumes.update(FlushVolumesChange.PRINTER_CHANGED, -1)
            changed != null -> flushVolumes.update(FlushVolumesChange.FILAMENT_CHANGED, changed)
        }
    }
}

/**
 * OrcaSlicer's CreateFilamentPresetDialog and EditFilamentPresetDialog: the
 * filaments of the user's own. A filament that is created or a preset that is
 * deleted changes the installed presets, so the sidebar and the plate follow
 * them, as the desktop app updates its combo boxes.
 */
class CustomFilamentsUseCase(
    private val presetManager: PresetManager,
    private val platePresets: PlatePresets,
) {
    /** What the create dialog offers for [type] and [baseFilament]. */
    suspend fun options(type: String = "", baseFilament: String = ""): CreateFilamentOptionsOutcome =
        presetManager.createFilamentOptions(type, baseFilament)

    /** Its Create button, with what the user answered so far. */
    suspend fun create(request: CreateFilamentRequest, answers: Map<String, Boolean> = emptyMap()): PresetCreationOutcome =
        presetManager.createFilament(request, answers).also { follow(it) }

    /** The filaments of the user's own, which the app lists to edit. */
    suspend fun filaments(): CustomFilamentsOutcome = presetManager.customFilaments()

    /** What the edit dialog shows for one of them. */
    suspend fun presets(filamentId: String): FilamentPresetsOutcome = presetManager.filamentPresets(filamentId)

    /** Its Delete button. */
    suspend fun deletePreset(preset: String, answers: Map<String, Boolean> = emptyMap()): PresetCreationOutcome =
        presetManager.deleteFilamentPreset(preset, answers).also { follow(it) }

    private suspend fun follow(outcome: PresetCreationOutcome) {
        if (outcome is PresetCreationOutcome.Success) {
            platePresets.apply(before = null, outcome = presetManager.presets())
        }
    }
}

/**
 * OrcaSlicer's CreatePrinterPresetDialog: a printer of the user's own, made
 * from a vendor's preset with the presets it prints with. The installed presets
 * change, so the sidebar and the plate follow them.
 */
class CustomPrinterUseCase(
    private val presetManager: PresetManager,
    private val platePresets: PlatePresets,
) {
    /** What the dialog's pages offer for what they are filled in with. */
    suspend fun options(request: CreatePrinterRequest): CreatePrinterOptionsOutcome = presetManager.createPrinterOptions(request)

    /**
     * The first page's OK: Success with no name when the second page follows.
     * A system printer the user switched to instead is installed, and the
     * sidebar and the plate follow it.
     */
    suspend fun check(request: CreatePrinterRequest, answers: Map<String, Boolean> = emptyMap()): PresetCreationOutcome {
        val outcome = presetManager.checkPrinterPage(request, answers)
        if (outcome is PresetCreationOutcome.Success && outcome.name.isNotEmpty()) {
            platePresets.apply(before = null, outcome = presetManager.presets())
        }
        return outcome
    }

    /** Its Create button, with what the user answered so far. */
    suspend fun create(request: CreatePrinterRequest, answers: Map<String, Boolean> = emptyMap()): PresetCreationOutcome {
        val outcome = presetManager.createPrinter(request, answers)
        if (outcome is PresetCreationOutcome.Success) {
            platePresets.apply(before = null, outcome = presetManager.presets())
        }
        return outcome
    }
}

/** The printer models the Setup Wizard offers. */
class GetSetupPrintersUseCase(private val presetManager: PresetManager) {
    suspend operator fun invoke(): SetupPrintersOutcome = presetManager.setupPrinters()
}

/** The filaments the Setup Wizard offers for the printer models with the ids [models]. */
class GetSetupFilamentsUseCase(private val presetManager: PresetManager) {
    suspend operator fun invoke(models: List<String>): SetupFilamentsOutcome = presetManager.setupFilaments(models)
}

/**
 * The Setup Wizard's Finish, or its closing while no printer is installed
 * (GuideFrame::run): the engine installs the printers and filaments and selects
 * a printer, and the plate is brought to the new presets. The change runs in
 * the application scope, so it completes when the wizard's screen goes away;
 * the caller gets the engine's answer. Returns null when the plate is busy.
 */
class ApplySetupUseCase(
    private val presetManager: PresetManager,
    private val platePresets: PlatePresets,
    private val repository: PlateRepository,
    private val applicationScope: CoroutineScope,
    /**
     * check_and_keep_current_preset_changes() of the wizard: true to keep the
     * presets' unsaved changes, false when they were saved, discarded or none,
     * null for Cancel.
     */
    private val keepPresetChanges: suspend () -> Boolean? = { false },
) {
    /**
     * Installs the printer models with the ids [models] and the [filaments];
     * null while the plate is busy or when the user cancels the question about
     * the presets' unsaved changes.
     */
    suspend operator fun invoke(models: List<String>, filaments: List<String>): PresetsOutcome? {
        // GuideFrame::apply_config(): the wizard that changes the installed
        // printers or filaments asks about the presets' unsaved changes first.
        val keep = if (presetManager.setupChangesInstallation(models, filaments)) keepPresetChanges() ?: return null else false
        return change { presetManager.applySetup(models, filaments, keep) }
    }

    /** OrcaSlicer's default printer and filament, when no printer is installed. */
    suspend fun defaults(): PresetsOutcome? = change { presetManager.applyDefaultSetup() }

    private suspend fun change(apply: suspend () -> PresetsOutcome): PresetsOutcome? {
        var accepted = false
        var before: SlicingProfileSelection? = null
        repository.update { state ->
            accepted = false
            if (state.busy || state.presets == null || state.objects.any(PlateObject::placing)) return@update state
            accepted = true
            before = state.profiles
            state.copy(changingPresets = true, problem = null)
        }
        if (!accepted) return null
        return applicationScope.async {
            apply().also { platePresets.apply(before, it) }
        }.await()
    }
}

/**
 * Plater::priv::load_files() for a document the user picked: the file is
 * copied into app storage, and OrcaSlicer loads its objects, of any type the
 * desktop app imports, and places them beside the objects on the plate. The
 * plate is busy until the objects join the end of its list, and G-code sliced
 * before no longer applies. A question of the load waits on the plate for
 * [answer], as the desktop app's message box waits; the message boxes it only
 * informs with wait there until they are dismissed. A failure leaves the plate
 * as it was and reports the problem.
 *
 * A 3MF file opens as the desktop app's Plater::open_3mf_file() opens it with
 * the Preferences' "Load behaviour": as a project, as geometry only, or as the
 * user chooses in ProjectDropDialog ([openAs]) — always, or by default only
 * when the plate already has objects. A project takes the plate's place with
 * its objects, settings and presets, and Undo starts afresh from it.
 *
 * Several documents picked at once load as Plater::add_file() loads them:
 * model files together, asking whether they make one object of several parts
 * and whether it drops onto the plate; with 3MF files among them, the first
 * 3MF file opens as a single one does, then the other 3MF files load as
 * geometry, and the other files together.
 */
class AddModelToPlateUseCase(
    private val importModel: ImportModelUseCase,
    private val inspector: PlateInspector,
    private val sceneFiles: SceneFiles,
    private val repository: PlateRepository,
    private val placePlateObjects: PlacePlateObjectsUseCase,
    private val presetManager: PresetManager,
    private val platePresets: PresetsApplier,
    private val confirmClose: ProjectCloseConfirmation,
    private val applicationScope: CoroutineScope,
    private val preferences: AppPreferences,
    private val stepMeshPrompt: StepMeshPrompt,
    private val editPlateObject: EditPlateObjectUseCase,
    private val recentProjects: RecentProjects? = null,
    private val objColorPrompt: ObjColorPrompt? = null,
) {
    operator fun invoke(reference: ExternalDocumentReference) = invoke(listOf(reference))

    /**
     * Plater::add_file() of the documents the user picked at once; without
     * [addFile], Plater::load_files() of files handed over, which joins no
     * model to the recent files and names no project.
     */
    operator fun invoke(references: List<ExternalDocumentReference>, addFile: Boolean = true) {
        if (references.isEmpty() || !start()) return
        applicationScope.launch {
            val picked = mutableListOf<Pair<ExternalDocumentReference, ImportedModelFile>>()
            for ((reference, imported) in references.zip(importModel(references))) {
                when (imported) {
                    is ModelImportOutcome.Failure -> return@launch finish(ModelLoadOutcome.Failure(imported.message))
                    is ModelImportOutcome.Success -> picked += reference to imported.model
                }
            }
            val projects = picked.filter { (_, model) -> model.path.value.endsWith(".3mf", ignoreCase = true) }
            val models = picked.filterNot { it in projects }
            // add_file(): with "Add STL/STEP files to recent files list" the models join the recent files once they load.
            val recentModels = if (addFile && preferences[AppConfigKeys.RECENT_MODELS] == "true") models.map { it.first } else emptyList()
            when {
                // load_files()'s pattern_bundle: with an AMF file among several, every
                // file loads one by one, asking nothing of one object of parts.
                projects.isEmpty() && models.size > 1 && models.any { (_, model) -> BUNDLE_FILES.matches(model.path.value) } -> {
                    val (document, model) = picked.first()
                    val loads = models.map { (reference, file) -> ImportFiles(listOf(file.path), recentModels = listOf(reference).filter { it in recentModels }) }
                    load(loads.first(), ImportBatch(rest = loads.drop(1), document = document, displayName = model.displayName, namesProject = addFile), emptyMap(), emptyList())
                }
                // LoadFilesType::SingleOther, and MultipleOther: the files load together,
                // asking whether they make one object; a plate without a name takes the first one's.
                projects.isEmpty() -> {
                    val (document, model) = picked.first()
                    val files = ImportFiles(models.map { it.second.path }, askMulti = models.size > 1, recentModels = recentModels)
                    load(files, ImportBatch(document = document, displayName = model.displayName, namesProject = addFile), emptyMap(), emptyList())
                }
                // Single3MF, Multiple3MF and Multiple3MFOther: the first 3MF file opens
                // as open_3mf_file() opens it, then the other 3MF files and the other
                // files load as geometry. The other 3MF files load one by one, as the
                // engine loads a 3MF file only on its own.
                else -> {
                    val (document, project) = projects.first()
                    val rest = projects.drop(1).map { ImportFiles(it.second.path) } +
                        listOfNotNull(models.takeIf { it.isNotEmpty() }?.let { others -> ImportFiles(others.map { it.second.path }, recentModels = recentModels) })
                    open3mf(project.path, ImportBatch(rest = rest, document = document, displayName = project.displayName))
                }
            }
        }
    }

    /**
     * Plater::preview_zip_archive()'s loads of the files unzipped from an
     * archive, which stay in app storage: one project among them opens as a
     * 3MF file dropped on the window does, and the models load after it as
     * geometry; with no project, or several, every file loads as geometry,
     * the projects one by one and the models together. None asks whether the
     * models make one object of several parts.
     */
    fun loadUnzipped(projects: List<ModelPath>, models: List<ModelPath>) {
        if ((projects.isEmpty() && models.isEmpty()) || !start()) return
        // pattern_bundle: with an AMF file among them, the models load one by one.
        val modelFiles = if (models.size > 1 && models.any { BUNDLE_FILES.matches(it.value) }) {
            models.map(::ImportFiles)
        } else {
            listOfNotNull(models.takeIf { it.isNotEmpty() }?.let { ImportFiles(it) })
        }
        applicationScope.launch {
            val single = projects.singleOrNull()
            if (single != null) {
                open3mf(single, ImportBatch(rest = modelFiles, displayName = single.value.substringAfterLast('/')))
            } else {
                val loads = projects.map(::ImportFiles) + modelFiles
                load(loads.first(), ImportBatch(rest = loads.drop(1)), emptyMap(), emptyList())
            }
        }
    }

    /** open_3mf_file(): "Ask When Relevant" asks only over a plate with objects. */
    private suspend fun open3mf(path: ModelPath, picked: ImportBatch) {
        val setting = preferences[AppConfigKeys.PROJECT_LOAD_BEHAVIOUR]
        val relevant = repository.state.value.objects.isNotEmpty() && setting == AppConfigKeys.ASK_WHEN_RELEVANT
        // determine_load_type()
        when (if (relevant) AppConfigKeys.ALWAYS_ASK else setting) {
            AppConfigKeys.LOAD_GEOMETRY_ONLY -> load(ImportFiles(path), picked, emptyMap(), emptyList())
            AppConfigKeys.ALWAYS_ASK -> repository.update { it.copy(projectDrop = path, projectDropBatch = picked) }
            else -> openProject(path, picked)
        }
    }

    /** Open Project: Plater::load_project() of the file the user picked. */
    fun openProject(reference: ExternalDocumentReference) = loadProject(reference)

    /**
     * MainFrame::open_recent_project(): Plater::load_project() of a file of the
     * home page's list, a 3MF file or a model file, which the list holds with
     * "Add STL/STEP files to recent files list".
     */
    fun openRecent(reference: ExternalDocumentReference) = loadProject(reference)

    private fun loadProject(reference: ExternalDocumentReference) {
        if (!start()) return
        applicationScope.launch {
            when (val imported = importModel(reference)) {
                is ModelImportOutcome.Failure -> finish(ModelLoadOutcome.Failure(imported.message))
                is ModelImportOutcome.Success ->
                    loadProject(imported.model.path, ImportBatch(document = reference, displayName = imported.model.displayName))
            }
        }
    }

    /**
     * Plater::load_project(): once the project before let it go
     * (close_with_confirm), determine_load_type() of the Preferences' "Load
     * behaviour" decides how the file loads: as geometry only, as the user
     * chooses in ProjectDropDialog ([openAs]), or otherwise as a project, with
     * "Ask When Relevant" too. Either way it takes the plate's place
     * ([replacePlate]) and the project goes by the file's name.
     */
    private suspend fun loadProject(path: ModelPath, picked: ImportBatch) {
        if (!confirmClose.confirm(newProject = false)) {
            repository.update { it.copy(importing = false) }
            return
        }
        val batch = picked.copy(loadProject = true)
        when (preferences[AppConfigKeys.PROJECT_LOAD_BEHAVIOUR]) {
            AppConfigKeys.LOAD_GEOMETRY_ONLY -> replacePlate(path, batch.copy(load = ModelLoad.GEOMETRY))
            AppConfigKeys.ALWAYS_ASK -> repository.update { it.copy(projectDrop = path, projectDropBatch = batch) }
            else -> replacePlate(path, batch.copy(load = ModelLoad.PROJECT))
        }
    }

    /**
     * load_project()'s reset() and load_files(): the plate is emptied
     * (Plater::priv::reset(), which takes away the presets the project before
     * brought) and the file loads. A 3MF file that opens as a project takes
     * those presets away itself as it brings its own.
     */
    private suspend fun replacePlate(path: ModelPath, batch: ImportBatch) {
        if (batch.load != ModelLoad.PROJECT || !path.value.endsWith(".3mf", ignoreCase = true)) {
            val before = repository.state.value.profiles
            val presets = presetManager.resetProjectPresets()
            (presets as? PresetsOutcome.Success)?.let { reset -> repository.update { it.copy(presets = reset.presets) } }
            platePresets.apply(before, presets)
        }
        load(ImportFiles(path), batch, emptyMap(), emptyList())
    }

    /**
     * Plater::load_project() of a backup with its origin (LoadStrategy::Restore):
     * the project loads under the name of the document it came from, or as
     * "Untitled" when it had none, and stays unsaved. The app has just
     * started, so no project before asks anything.
     */
    suspend fun restoreProject(backup: ProjectBackup) {
        if (!start()) return
        val document = backup.origin.document
        val batch = ImportBatch(
            load = ModelLoad.PROJECT,
            chosen = true,
            document = document,
            // The project's name is its document's without ".3mf" (projectNameOf).
            displayName = backup.origin.name?.takeIf { document != null }?.let { "$it.3mf" },
            restore = true,
        )
        load(ImportFiles(backup.file), batch, emptyMap(), emptyList())
    }

    /**
     * Plater::load_project(): the questions of the project before, then the
     * load. A project the user keeps from opening leaves the other files of
     * the batch to load, as add_file() goes on with them.
     */
    private suspend fun openProject(path: ModelPath, picked: ImportBatch, chosen: Boolean = false) {
        if (!confirmClose.confirm(newProject = false)) {
            if (picked.rest.isEmpty()) repository.update { it.copy(importing = false) } else load(picked.rest.first(), picked.next(picked.loaded), emptyMap(), emptyList())
            return
        }
        load(ImportFiles(path), picked.copy(load = ModelLoad.PROJECT, chosen = chosen), emptyMap(), emptyList())
    }

    /**
     * ProjectDropDialog's choice for the 3MF file that waits; null cancels its
     * load, and the other files of the batch load still, as add_file() goes on.
     * The file load_project() asked about takes the plate's place either way,
     * the project before having let it go already.
     */
    fun openAs(load: ModelLoad?) {
        var source: ModelPath? = null
        var picked = ImportBatch()
        repository.update { state ->
            source = state.projectDrop
            picked = state.projectDropBatch
            when {
                source == null -> state
                load == null -> state.copy(projectDrop = null, projectDropBatch = ImportBatch(), importing = picked.rest.isNotEmpty())
                else -> state.copy(projectDrop = null, projectDropBatch = ImportBatch())
            }
        }
        val path = source ?: return
        if (load == null) {
            if (picked.rest.isNotEmpty()) applicationScope.launch { load(picked.rest.first(), picked.next(picked.loaded), emptyMap(), emptyList()) }
            return
        }
        applicationScope.launch {
            when {
                picked.loadProject -> replacePlate(path, picked.copy(load = load, chosen = true))
                load == ModelLoad.PROJECT -> openProject(path, picked, chosen = true)
                else -> load(ImportFiles(path), picked.copy(load = load, chosen = true), emptyMap(), emptyList())
            }
        }
    }

    /**
     * MenuFactory::append_submenu_add_handy_model(): the files of [model] load
     * one after another as Plater::load_files() loads several, the plate is
     * then arranged for the models that ask for it (ArrangeJob from the menu),
     * and Orca String Hell ends with OrcaSlicer's suggestion.
     */
    fun handy(model: HandyModel) {
        if (!start()) return
        applicationScope.launch {
            val files = model.files.map { inspector.handyModel(it) }
            val first = files.firstOrNull()
            if (first == null || files.any { it == null }) {
                return@launch finish(ModelLoadOutcome.Failure("${model.label} is not among OrcaSlicer's handy models"))
            }
            val batch = ImportBatch(
                rest = files.drop(1).filterNotNull().map(::ImportFiles),
                arrange = model.arrangeAfterImport,
                suggestTopSurface = model.suggestsTopSurface,
            )
            load(ImportFiles(first), batch, emptyMap(), emptyList())
        }
    }

    /** The plate starts loading unless it is busy or has no presets yet. */
    private fun start(): Boolean {
        var started = false
        repository.update { state ->
            started = !state.busy && state.profiles != null
            if (started) state.copy(importing = true, problem = null) else state
        }
        return started
    }

    /**
     * The answer to the question the load asked, with the state of its check
     * box when it has one: the files load again with every answer so far.
     */
    fun answer(yes: Boolean, checked: Boolean = false) {
        var pending: PendingPlateQuestion? = null
        repository.update { state ->
            pending = state.plateQuestion?.takeIf { it.request is PlateRequest.Import }
            if (pending == null) state else state.copy(plateQuestion = null)
        }
        val question = pending ?: return
        val request = question.request as PlateRequest.Import
        val dialog = question.question
        val answers = question.answers + (dialog.id to yes) + listOfNotNull(dialog.checkbox?.let { dialog.checkboxAnswer to checked })
        applicationScope.launch { load(request.files, request.batch, answers, question.shown) }
    }

    /**
     * The Cancel of load_files()'s ProgressDialog: the load stops at its next
     * update, adds nothing and tells of no error, and the files of the batch
     * after it do not load.
     */
    fun cancelLoad() {
        if (repository.state.value.loadProgress != null) inspector.cancelLoad()
    }

    private suspend fun load(files: ImportFiles, batch: ImportBatch, answers: Map<String, Boolean>, shown: List<SettingsDialog>) {
        val state = repository.state.value
        val profiles = state.profiles ?: return finish(ModelLoadOutcome.Failure("No printer is set up"))
        val prefix = sceneFiles.newImportPrefix()
        // Plater::load_project() resets the plate before the project loads.
        val plate = if (batch.load == ModelLoad.PROJECT || batch.loadProject) emptyList() else state.objects.map { it.placed() }
        // load_files()'s ProgressDialog names a restored backup after its origin (real_filename).
        val restoredName = batch.displayName?.takeIf { batch.restore }
        val outcome = try {
            inspector.load(files.files, profiles, plate, prefix, answers, batch.load, batch.chosen, batch.stepMeshes, files.askMulti, batch.objColors) { percent, file ->
                repository.update { it.copy(loadProgress = LoadProgress(percent, restoredName ?: file)) }
            }
        } catch (cancellation: CancellationException) {
            sceneFiles.deleteImport(prefix)
            throw cancellation
        } catch (error: Exception) {
            ModelLoadOutcome.Failure(error.message.orEmpty())
        }
        if (outcome !is ModelLoadOutcome.Success) sceneFiles.deleteImport(prefix)
        // The progress dialog closes while a dialog of the load asks.
        if (outcome is ModelLoadOutcome.StepMesh || outcome is ModelLoadOutcome.ObjColors) repository.update { it.copy(loadProgress = null) }
        if (outcome is ModelLoadOutcome.StepMesh) {
            // StepMeshDialog for the STEP file that asked. Its Cancel fails that file
            // alone, with no error (is_user_cancel): the other files of the load
            // load, and the loads after it.
            val options = stepMeshPrompt.ask(files.files.getOrElse(outcome.file) { files.first }, outcome.options)
            if (options == null) {
                val others = files.files.filterIndexed { index, _ -> index != outcome.file }
                return when {
                    others.isNotEmpty() -> load(files.copy(files = others), batch, answers, shown)
                    batch.rest.isNotEmpty() -> load(batch.rest.first(), batch.next(batch.loaded), emptyMap(), emptyList())
                    else -> repository.update { it.copy(importing = false) }
                }
            }
            return load(files, batch.copy(stepMeshes = batch.stepMeshes + (outcome.file to options)), answers, shown)
        }
        if (outcome is ModelLoadOutcome.ObjColors) {
            // obj_color_fun: ObjColorDialog for the OBJ file that asked; its Cancel loads the file without colours.
            val source = files.files.getOrElse(outcome.file) { files.first }
            val choice = objColorPrompt?.ask(source, outcome.question) ?: ObjColorChoice()
            return load(files, batch.copy(objColors = batch.objColors + (outcome.file to choice)), answers, shown)
        }
        // The presets the load selected (a project's, or more filaments for a
        // 3MF file's objects) reach the plate together with its objects, so no
        // request asks the engine for the presets before.
        val presets = if (outcome is ModelLoadOutcome.Success && outcome.presetsChanged) presetManager.presets() else null
        finish(outcome, files, batch, answers, shown, presets)
    }

    /**
     * The load asks again what it asked before, and shows its message boxes
     * again: each shows once. A load of a batch goes on with the next one,
     * and the last one ends the batch. Files the user wants as objects that
     * keep their places loaded as one object, which is then split into them
     * (split_object() after load_model_objects()).
     */
    private fun finish(
        outcome: ModelLoadOutcome,
        files: ImportFiles? = null,
        batch: ImportBatch = ImportBatch(),
        answers: Map<String, Boolean> = emptyMap(),
        shown: List<SettingsDialog> = emptyList(),
        presets: PresetsOutcome? = null,
    ) {
        var next: ImportBatch? = null
        var done = false
        var before: SlicingProfileSelection? = null
        var split: ScenePath? = null
        repository.update { state ->
            next = null
            done = false
            split = null
            before = state.profiles
            val notices = outcome.notices.filterNot { it in shown }
            val informed = state.copy(
                plateNotices = state.plateNotices + notices,
                presets = (presets as? PresetsOutcome.Success)?.presets ?: state.presets,
                loadProgress = null,
            )
            when (outcome) {
                is ModelLoadOutcome.Success -> {
                    // ModelObject::input_file: the document the objects came from; a
                    // restored backup's objects come from its origin (load_files()'s real_filename).
                    // The objects of several files each come from their own.
                    val inputName = batch.displayName?.takeIf { batch.restore } ?: files?.first?.value.orEmpty().substringAfterLast('/')
                    val added = outcome.objects.map { it.toPlateObject(it.inputFile.ifEmpty { inputName }) }
                    // load_files() selects every object it added.
                    val loaded = batch.loaded + added.allCopies()
                    if (batch.rest.isNotEmpty()) next = batch.next(loaded) else done = true
                    if (outcome.splitToObjects) split = added.singleOrNull()?.mesh
                    val project = outcome.project
                    if (project != null) {
                        // Plater::load_project(): the project takes the plate's place with
                        // its plates, the first one current (load_from_3mf_structure), and
                        // its "Load Project" snapshot (a ProjectSeparator) clears Undo; it
                        // goes by the file's name, is saved into it again and is not dirty.
                        val plates = project.plates.map { plate ->
                            PartPlate(name = plate.name, locked = plate.locked, settings = plate.settings, layerGcodes = plate.layerGcodes)
                        }.ifEmpty { listOf(PartPlate()) }
                        informed.copy(
                            importing = batch.rest.isNotEmpty(),
                            objects = added,
                            // load_files() selects what it added only without load_config.
                            selectedInstances = emptySet(),
                            selectedPart = null,
                            selectedRange = null,
                            simplifyTarget = null,
                            plates = plates,
                            currentPlate = 0,
                            plateSettings = plates.first().settings,
                            layerGcodes = plates.first().layerGcodes,
                            // Plater::load_project()
                            paPattern = null,
                            history = PlateHistory(),
                            // Plater::priv::reset()
                            projectResets = state.projectResets + 1,
                            result = null,
                            // ParamsPanel::switch_to_object_if_has_object_configs()
                            settingsScope = if (informed.hasObjectConfigs(added)) SettingsScope.OBJECT else state.settingsScope,
                        ).let { loaded ->
                            loaded.copy(
                                project = loaded.projectBaseline().copy(
                                    name = batch.displayName?.let(::projectNameOf),
                                    document = batch.document,
                                    info = project.info,
                                ).let { opened ->
                                    // dirty_state.update_from_undo_redo_stack(true): a restored project is unsaved.
                                    if (batch.restore) opened.copy(baseline = ProjectContent()) else opened
                                },
                            )
                        }
                    } else if (batch.load == ModelLoad.PROJECT || batch.loadProject) {
                        // Plater::load_project() of a file that brings no project, such
                        // as a model file of the recent files or a 3MF file as geometry
                        // only: the reset plate holds the file's objects alone
                        // (Plater::priv::reset()), Undo starts afresh, and the project
                        // goes by the file's name. Orca saves the project of a model
                        // file beside it as a 3MF file; the document of a model takes
                        // no project, so Save asks for one.
                        val settings = state.projectConfigSettings()
                        val document = batch.document?.takeIf { batch.displayName?.endsWith(".3mf", ignoreCase = true) == true }
                        informed.copy(
                            importing = batch.rest.isNotEmpty(),
                            objects = added,
                            // Plater::load_project() loads with load_config: nothing is selected.
                            selectedInstances = emptySet(),
                            selectedPart = null,
                            selectedRange = null,
                            simplifyTarget = null,
                            plates = listOf(PartPlate(settings = settings)),
                            currentPlate = 0,
                            plateSettings = settings,
                            layerGcodes = emptyList(),
                            paPattern = null,
                            history = PlateHistory(),
                            projectResets = state.projectResets + 1,
                            result = null,
                            project = PlateProject(name = batch.displayName?.let(::projectNameOf), document = document),
                        ).let { loaded ->
                            val opened = loaded.projectBaseline()
                            loaded.copy(project = if (batch.restore) opened.copy(baseline = ProjectContent()) else opened)
                        }
                    } else {
                        // Plater::add_file(): an untitled plate takes the name of the
                        // first model file it loads, a 3MF file's geometry aside.
                        val name = batch.displayName?.takeIf { batch.namesProject && added.isNotEmpty() }
                        val named = if (name != null && state.project.name == null) {
                            informed.copy(project = state.project.copy(name = projectNameOf(name)))
                        } else {
                            informed
                        }
                        // load_files(): "Import Object", once for all its files; the
                        // plate's print leaves the calibration it carried.
                        (if (added.isEmpty() || batch.loaded.isNotEmpty()) named else named.recorded()).copy(
                            importing = batch.rest.isNotEmpty(),
                            plates = state.plates.mapIndexed { index, plate -> if (index == state.currentPlate) plate.copy(calibration = null) else plate },
                            objects = state.objects + added,
                            selectedInstances = loaded,
                            selectedPart = null,
                            selectedRange = null,
                            result = if (added.isEmpty()) state.result else null,
                        )
                    }
                }
                is ModelLoadOutcome.Question -> informed.copy(
                    plateQuestion = PendingPlateQuestion(
                        PlateRequest.Import(checkNotNull(files), batch),
                        outcome.question,
                        answers,
                        shown + notices,
                    ),
                )
                // load() asked StepMeshDialog before.
                is ModelLoadOutcome.StepMesh, is ModelLoadOutcome.ObjColors -> informed.copy(importing = false)
                // load_files() returned empty_result: nothing of the load joins the
                // plate, the batch ends there, and no error tells of it.
                is ModelLoadOutcome.Cancelled -> informed.copy(importing = false)
                is ModelLoadOutcome.Failure -> informed.copy(
                    importing = false,
                    problem = PlateProblem(PlateProblemKind.IMPORT_FAILED, outcome.message),
                )
            }
        }
        // The plate follows the presets as any change of them: its description, the fit of its objects, the tabs.
        if (presets != null) {
            applicationScope.launch { platePresets.apply(before, presets) }
        }
        // load_model_objects() closes UpdatedItemsInfo, then add_object_to_list()
        // of the last object it loaded tells of a part of a cut with connectors
        // (ObjectList::update_info_items()).
        if (outcome is ModelLoadOutcome.Success && outcome.objects.isNotEmpty()) {
            val last = outcome.objects.last()
            val cutPart = last.cutId != null && last.parts.isNotEmpty() && last.parts.any { it.cutInfo.connector }
            repository.update { state ->
                if (cutPart) state.copy(cutPartsLoaded = 1, cutPartsLoads = state.cutPartsLoads + 1) else state.copy(cutPartsLoaded = 0)
            }
        }
        // load_files(): GLGizmoSimplify::add_simplify_suggestion_notification() of the
        // objects it loaded; those of objects that are gone no longer show
        // (remove_simplify_suggestion_of_released_objects()).
        if (outcome is ModelLoadOutcome.Success) {
            val big = outcome.objects.filter(LoadedObject::suggestsSimplify).map { it.instances.first().inspection.mesh }
            if (big.isNotEmpty()) {
                repository.update { state ->
                    val living = state.simplifySuggestions.filter { mesh -> state.objects.withMesh(mesh) != null }
                    state.copy(simplifySuggestions = (living + big).distinct())
                }
            }
        }
        // set_project_filename() of a project that opened adds it to the recent files
        // (add_to_recent_projects()), as add_file() adds its models that loaded.
        if (outcome is ModelLoadOutcome.Success && outcome.objects.isNotEmpty()) {
            if (batch.load == ModelLoad.PROJECT || batch.loadProject) {
                batch.document?.let { recentProjects?.add(listOf(it)) }
            } else {
                files?.recentModels?.let { recentProjects?.add(it) }
            }
        }
        val following = next
        if (following != null) {
            applicationScope.launch { load(batch.rest.first(), following, emptyMap(), emptyList()) }
        } else if (done) {
            val state = repository.state.value
            // load_files(): split_object(idx, new_model_auto_drop), which is false here.
            split?.let { editPlateObject(it, ObjectEdit.SPLIT_TO_OBJECTS_KEEPING_Z) }
            if (batch.arrange && state.objects.isNotEmpty()) placePlateObjects(PlateManipulation.ArrangePlate(state.currentArrangeSettings))
            if (batch.suggestTopSurface) suggestTopSurface()
        }
    }

    /**
     * The batch for its next load, with the copies [loaded] so far: add_file()
     * loads the files after the first as geometry, which name nothing.
     */
    private fun ImportBatch.next(loaded: Set<PlateInstanceId>) = copy(
        rest = rest.drop(1),
        loaded = loaded,
        stepMeshes = emptyMap(),
        load = ModelLoad.GEOMETRY,
        chosen = false,
        displayName = null,
        loadProject = false,
    )

    /**
     * ParamsPanel::notify_object_config_changed(): an object of [objects] or a
     * volume of one overrides settings that fall into a category
     * (SettingsFactory::get_bundle()); with one filament the "Extruders" and
     * "Wipe options" ones do not count (is_improper_category()).
     */
    private fun PlateState.hasObjectConfigs(objects: List<PlateObject>): Boolean {
        val definitions = settingsTabs[PresetKind.PRINT]?.tab?.definitions.orEmpty()
        val oneFilament = (presets?.selection?.allFilaments?.size ?: 1) == 1
        fun ModelSettings.counts() = values.keys.any { key ->
            val category = definitions[key]?.category.orEmpty()
            category.isNotEmpty() && !(oneFilament && (category == "Extruders" || category == "Wipe options"))
        }
        return objects.any { plateObject ->
            plateObject.settings.counts() || plateObject.volume.settings.counts() || plateObject.parts.any { it.settings.counts() }
        }
    }

    /**
     * The handy model Orca String Hell has text embossed on its top: with
     * "Only one wall on top surfaces" on and a "One Wall Threshold" above 0,
     * OrcaSlicer suggests setting the threshold to 0.
     */
    private fun suggestTopSurface() = repository.update { state ->
        val values = state.settingsTabs[PresetKind.PRINT]?.settings?.settings.orEmpty().associate { it.key to it.value }
        val oneWallTop = values[ONLY_ONE_WALL_TOP] == "1"
        val threshold = values[MIN_WIDTH_TOP_SURFACE]?.removeSuffix("%")?.toDoubleOrNull() ?: 0.0
        if (!oneWallTop || threshold <= 0.0 || state.plateQuestion != null) return@update state
        state.copy(plateQuestion = PendingPlateQuestion(PlateRequest.TopSurfaceSuggestion, TOP_SURFACE_SUGGESTION, emptyMap(), emptyList()))
    }

    companion object {
        /** pattern_bundle of Plater::priv: files that load one by one. */
        private val BUNDLE_FILES = Regex(".*[.](amf|amf[.]xml|zip[.]amf|3mf)", RegexOption.IGNORE_CASE)

        const val ONLY_ONE_WALL_TOP = "only_one_wall_top"
        const val MIN_WIDTH_TOP_SURFACE = "min_width_top_surface"

        /** OrcaSlicer's MessageDialog after loading Orca String Hell. */
        val TOP_SURFACE_SUGGESTION = SettingsDialog(
            id = "top_surface_suggestion",
            icon = DialogIcon.WARNING,
            title = listOf(OrcaText("Suggestion")),
            text = listOf(
                OrcaText(
                    "This model features text embossment on the top surface. For optimal results, it is " +
                        "advisable to set the 'One Wall Threshold (min_width_top_surface)' " +
                        "to 0 for the 'Only One Wall on Top Surfaces' to work best.\n" +
                        "Yes - Change these settings automatically\n" +
                        "No  - Do not change these settings for me",
                ),
            ),
            question = true,
            yes = null,
            no = null,
        )
    }
}

/**
 * MenuFactory's "Add Primitive" over empty space (ObjectList::load_shape_object):
 * a shape of create_mesh() joins the plate as an object named [name], in the
 * empty cell nearest to its centre, and is selected ("Add Primitive").
 */
class AddPrimitiveUseCase(
    private val inspector: PlateInspector,
    private val sceneFiles: SceneFiles,
    private val repository: PlateRepository,
    private val applicationScope: CoroutineScope,
) {
    operator fun invoke(shape: String, name: String) {
        var request: Pair<List<PlateObject>, SlicingProfileSelection>? = null
        repository.update { state ->
            request = null
            val profiles = state.profiles
            if (state.busy || profiles == null || state.objects.any(PlateObject::placing)) return@update state
            request = state.objects to profiles
            state.copy(importing = true, problem = null)
        }
        val (plate, profiles) = request ?: return
        applicationScope.launch {
            val prefix = sceneFiles.newImportPrefix()
            val outcome = try {
                inspector.addPrimitive(plate.map { it.placed() }, shape, name, profiles, prefix)
            } catch (cancellation: CancellationException) {
                sceneFiles.deleteImport(prefix)
                repository.update { it.copy(importing = false) }
                throw cancellation
            } catch (error: Exception) {
                ModelLoadOutcome.Failure(error.message.orEmpty())
            }
            // An object of no file names its G-code after itself.
            val added = (outcome as? ModelLoadOutcome.Success)?.objects.orEmpty().map { it.toPlateObject(name) }
            if (added.isEmpty()) sceneFiles.deleteImport(prefix)
            repository.update { state ->
                when {
                    added.isNotEmpty() -> state.recorded().copy(
                        importing = false,
                        objects = state.objects + added,
                        // paste_objects_into_list() selects it.
                        selectedInstances = added.allCopies(),
                        selectedPart = null,
                        selectedRange = null,
                        result = null,
                    )
                    else -> state.copy(
                        importing = false,
                        problem = PlateProblem(PlateProblemKind.IMPORT_FAILED, (outcome as? ModelLoadOutcome.Failure)?.message),
                    )
                }
            }
        }
    }
}

/** Adds OrcaSlicer's 20 mm calibration cube to the plate. */
class AddCalibrationCubeToPlateUseCase(
    inspectModel: InspectModelUseCase,
    sceneFiles: SceneFiles,
    repository: PlateRepository,
    applicationScope: CoroutineScope,
) {
    private val loader = PlateObjectLoader(inspectModel, sceneFiles, repository, applicationScope)

    operator fun invoke() = loader.load { inspect ->
        inspect(ModelSource.BuiltIn(BuiltInModel.CALIBRATION_CUBE_20_MM)).map { PlateObject.CalibrationCube(listOf(PlateInstance(it))) }
    }
}

/**
 * Adds an object to the plate, as Plater::priv::load_model_objects() does. The
 * plate is busy while OrcaSlicer loads the object and places it among the
 * objects on the plate in the application scope. The object joins the end of
 * the plate's list, and G-code sliced before no longer applies; a failure
 * leaves the plate as it was, without the new mesh file, and reports the problem.
 */
private class PlateObjectLoader(
    private val inspectModel: InspectModelUseCase,
    private val sceneFiles: SceneFiles,
    private val repository: PlateRepository,
    private val applicationScope: CoroutineScope,
) {
    fun load(block: suspend (inspect: suspend (ModelSource) -> Result<ModelInspection>) -> Result<PlateObject>) {
        var request: Pair<SlicingProfileSelection, List<PlacedModel>>? = null
        repository.update { state ->
            request = null
            val profiles = state.profiles
            if (state.busy || profiles == null) return@update state
            request = profiles to state.objects.map { it.placed() }
            state.copy(importing = true, problem = null)
        }
        val (selection, plate) = request ?: return
        applicationScope.launch {
            val meshes = mutableListOf<ScenePath>()
            val loaded = try {
                block { source ->
                    val mesh = sceneFiles.newObjectMesh().also(meshes::add)
                    when (val inspected = inspectModel(source, selection, mesh, plate)) {
                        is ModelInspectionOutcome.Success -> Result.success(inspected.inspection)
                        is ModelInspectionOutcome.Failure -> Result.failure(IllegalArgumentException(inspected.message))
                    }
                }
            } catch (cancellation: CancellationException) {
                meshes.forEach(sceneFiles::deleteObjectMesh)
                throw cancellation
            } catch (error: Exception) {
                Result.failure(error)
            }

            repository.update { state ->
                loaded.fold(
                    onSuccess = {
                        state.recorded().copy(
                            importing = false,
                            objects = state.objects + it,
                            // load_model_objects() selects the object it added.
                            selectedInstances = listOf(it).allCopies(),
                            selectedPart = null,
                            selectedRange = null,
                            result = null,
                        )
                    },
                    onFailure = { state.copy(importing = false, problem = PlateProblem(PlateProblemKind.IMPORT_FAILED, it.message)) },
                )
            }
            val shown = loaded.getOrNull()?.mesh
            meshes.filter { it != shown }.forEach(sceneFiles::deleteObjectMesh)
        }
    }
}

/**
 * Commits a manipulation of an object on the plate, as OrcaSlicer's canvas
 * does after a move, rotation, or scale, or after its orientation tools. The
 * object stands at the new placement at once and G-code sliced for the old one
 * no longer applies; OrcaSlicer then settles the placement (resting the object
 * on the plate as the manipulation requires, or finding its orientation) and
 * reports its size and whether it fits the build volume. Until then the plate
 * cannot be sliced.
 *
 * The object is the one with the [mesh] file, so a placement that arrives
 * after the object was deleted changes nothing; nor does one while the plate
 * is busy, or a move, rotation, or scale that leaves the placement as it was.
 * An answer for a placement that a newer one replaced is ignored.
 */
class PlacePlateObjectUseCase(
    private val placeModel: PlaceModelUseCase,
    private val repository: PlateRepository,
    private val applicationScope: CoroutineScope,
) {
    /**
     * [record] takes a snapshot before the change, as the canvas does before it
     * commits a manipulation; an action that places copies it has already
     * recorded leaves it out. [gizmoAction] is the snapshot's type: the move
     * and rotate windows' values take a GizmoAction snapshot.
     */
    operator fun invoke(
        id: PlateInstanceId,
        placement: Transform3,
        manipulation: Manipulation = Manipulation.Move,
        record: Boolean = true,
        gizmoAction: Boolean = false,
    ) {
        var request: Triple<PlateObject, Transform3, SlicingProfileSelection>? = null
        repository.update { state ->
            request = null
            val target = state.objects.withMesh(id.mesh)
            val copy = target?.instances?.getOrNull(id.instance)
            val profiles = state.profiles
            val unchanged = copy?.inspection?.placement == placement && manipulation.keepsUnchangedPlacement()
            if (target == null || copy == null || profiles == null || state.busy || unchanged) {
                return@update state
            }
            val moved = target.with(id.instance, copy.inspection.copy(placement = placement), placing = true)
            request = Triple(moved, copy.inspection.placement, profiles)
            // G-code no longer applies once the copy stands elsewhere.
            (if (record) state.recorded(gizmoAction = gizmoAction) else state)
                .copy(objects = state.objects.replaced(moved), result = state.result.takeIf { placement == copy.inspection.placement })
        }
        val (target, previous, profiles) = request ?: return
        val autoDrop = target.instances[id.instance].autoDrop
        applicationScope.launch {
            val outcome = try {
                placeModel(target.placed(), profiles, previous, placement, autoDrop, manipulation, id.instance)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                ModelInspectionOutcome.Failure(error.message.orEmpty())
            }
            repository.update { state ->
                val current = state.objects.withMesh(id.mesh)
                val copy = current?.instances?.getOrNull(id.instance)
                if (current == null || copy == null || copy.inspection.placement != placement) {
                    return@update state
                }
                when (outcome) {
                    is ModelInspectionOutcome.Success -> {
                        // Selection::synchronize_unselected_instances(): the other copies the
                        // manipulation changed, unless they were moved since.
                        val followed = outcome.synchronized.filter { (index, _) ->
                            index != id.instance && current.instances.getOrNull(index)?.inspection?.placement == target.instances.getOrNull(index)?.inspection?.placement
                        }
                        state.copy(
                            objects = state.objects.replaced(
                                current.with(id.instance, outcome.inspection, placing = false).let { placed ->
                                    // Selection::scale_and_translate(): the copy's place in the assembly view takes its new scale.
                                    val assemble = copy.assemble?.takeIf { manipulation == Manipulation.Scale }
                                        ?.withScalingFactor(outcome.inspection.placement.scalingFactor())
                                    if (assemble == null) placed else placed.withInstance(id.instance, placed.instances[id.instance].copy(assemble = assemble))
                                }.let { placed ->
                                    followed.entries.fold(placed) { synchronized, (index, inspection) ->
                                        synchronized.withInstance(index, synchronized.instances[index].copy(inspection = inspection))
                                    }
                                },
                            ),
                            result = state.result.takeIf { outcome.inspection.placement == previous && followed.isEmpty() },
                        )
                    }
                    is ModelInspectionOutcome.Failure -> state.copy(
                        objects = state.objects.replaced(current.with(id.instance, copy.inspection, placing = false)),
                        problem = PlateProblem(PlateProblemKind.PLACEMENT_FAILED, outcome.message),
                    )
                }
            }
        }
    }

    /** A manipulation of the copy where it stands: the object menu's Center, Drop and Mirror. */
    operator fun invoke(id: PlateInstanceId, manipulation: Manipulation) {
        val copy = repository.state.value.objects.withMesh(id.mesh)?.instances?.getOrNull(id.instance) ?: return
        invoke(id, copy.inspection.placement, manipulation)
    }

    /** Moves, rotations, and scales that end where they began change nothing; the other manipulations still act. */
    private fun Manipulation.keepsUnchangedPlacement() = this == Manipulation.Move || this == Manipulation.Rotate || this == Manipulation.Scale
}

/**
 * OrientJob and ArrangeJob: OrcaSlicer places several objects of the plate at
 * once. A job starts from settled placements; the objects it places stand
 * where they were until it answers, and the plate cannot be sliced meanwhile.
 * The answer applies to every object that still stands where it stood when the
 * job began, so an object the user moved or deleted meanwhile keeps the user's
 * change. G-code sliced before no longer applies once an object moved.
 */
class PlacePlateObjectsUseCase(
    private val placeModels: PlaceModelsUseCase,
    private val repository: PlateRepository,
    private val applicationScope: CoroutineScope,
) {
    /**
     * [skipLockedPlates] off when the job works on the current plate alone
     * (OrientJob's prepare_partplate()), which leaves no plate out.
     */
    operator fun invoke(asked: PlateManipulation, skipLockedPlates: Boolean = true) = start(asked, skipLockedPlates) { it }

    /**
     * EnginePlateSync: the engine knows [plate], which the state takes, and in
     * the same step the objects start being judged against the build volume of
     * the current plate, so the plate never looks settled before they are.
     */
    fun judgeOn(plate: EnginePlate) = start(PlateManipulation.UpdatePrintVolume, skipLockedPlates = true) { state ->
        state.takeIf { EnginePlate(it.currentPlate, it.plates.size) == plate && it.enginePlate != plate }?.copy(enginePlate = plate)
    }

    /** Starts the job on the state [settle] makes of the plate's; nothing changes when it makes none. */
    private fun start(asked: PlateManipulation, skipLockedPlates: Boolean, settle: (PlateState) -> PlateState?) {
        var request: Triple<List<PlateObject>, Set<ScenePath>, SlicingProfileSelection>? = null
        var manipulation = asked
        repository.update { current ->
            request = null
            val state = settle(current) ?: return@update current
            val locked = if (skipLockedPlates) state.lockedPlates() else emptySet()
            // The plates' wipe towers, which arranging keeps clear of.
            val plateSettings = state.partPlates().map { it.settings }
            manipulation = when (asked) {
                is PlateManipulation.AutoOrient -> asked.copy(lockedPlates = locked)
                is PlateManipulation.Arrange -> asked.copy(lockedPlates = locked, plateSettings = plateSettings)
                is PlateManipulation.ArrangePlate -> asked.copy(lockedPlates = locked, plateSettings = plateSettings)
                is PlateManipulation.FillBed -> asked.copy(lockedPlates = locked, plateSettings = plateSettings)
                PlateManipulation.UpdatePrintVolume -> asked
            }
            val profiles = state.profiles
            if (state.busy || profiles == null || state.objects.isEmpty() || state.objects.any(PlateObject::placing)) return@update state
            // ArrangeJob and OrientJob warn when the locked plates leave them nothing to work on.
            state.lockedWarning(asked, locked)?.let { return@update state.copy(problem = PlateProblem(it)) }
            val targets = manipulation.targets(state.objects)
            request = Triple(state.objects, targets, profiles)
            // The jobs take "Arrange" and "Orient"; judging the fit for another printer changes no placement.
            (if (manipulation == PlateManipulation.UpdatePrintVolume) state else state.recorded()).copy(
                objects = state.objects.map { target ->
                    if (target.mesh !in targets) target else target.withInstances(target.instances.map { it.copy(placing = true) })
                },
            )
        }
        val (plate, targets, profiles) = request ?: return
        applicationScope.launch {
            val outcome = try {
                placeModels(plate.map { it.placed() }, profiles, manipulation)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                PlateInspectionOutcome.Failure(error.message.orEmpty())
            }
            repository.update { state ->
                var moved = false
                val objects = state.objects.map { current ->
                    val index = plate.indexOfFirst { it.mesh == current.mesh }
                    val before = plate.getOrNull(index)
                    val placements = before?.instances?.map { it.inspection.placement }
                    if (before == null || before.mesh !in targets || current.instances.map { it.inspection.placement } != placements) {
                        return@map current
                    }
                    val placed = (outcome as? PlateInspectionOutcome.Success)?.inspections?.getOrNull(index)
                    // FillBedJob adds copies, which are printed and drop by themselves as a new ModelInstance does.
                    val added = placed.orEmpty().drop(current.instances.size).map { PlateInstance(it) }
                    if (added.isNotEmpty()) moved = true
                    current.withInstances(
                        current.instances.mapIndexed { copy, instance ->
                            val inspection = placed?.getOrNull(copy)
                            if (inspection != null && inspection.placement != before.instances[copy].inspection.placement) moved = true
                            instance.copy(inspection = inspection ?: instance.inspection, placing = false)
                        } + added,
                    )
                }
                // rebuild_plates_after_arrangement(): the objects sorted by their first
                // copy's arrange order; one added meanwhile stays after them.
                val order = (outcome as? PlateInspectionOutcome.Success)?.objectOrder.orEmpty()
                val sorted = if (order.size == plate.size) {
                    val rank = order.withIndex().associate { (position, index) -> plate[index].mesh to position }
                    objects.sortedBy { rank[it.mesh] ?: Int.MAX_VALUE }
                } else {
                    objects
                }
                val placed = state.copy(
                    objects = sorted,
                    result = state.result.takeIf { !moved },
                    problem = if (outcome is PlateInspectionOutcome.Failure) {
                        PlateProblem(PlateProblemKind.PLACEMENT_FAILED, outcome.message)
                    } else {
                        state.problem
                    },
                )
                // ArrangeJob::finalize(): the plates added for what the others
                // did not hold, then rebuild_plates_after_arrangement() recycles
                // the empty ones at the end.
                val plates = (outcome as? PlateInspectionOutcome.Success)?.plates
                if (manipulation is PlateManipulation.Arrange && plates != null) placed.withArrangedPlates(plates) else placed
            }
        }
    }

    /**
     * OrientJob places the selected objects, or all of them when none of the plate's is selected;
     * ArrangeJob and FillBedJob place all, and another printer judges the fit of all.
     */
    private fun PlateManipulation.targets(objects: List<PlateObject>): Set<ScenePath> {
        val meshes = objects.mapTo(LinkedHashSet(), PlateObject::mesh)
        return when (this) {
            is PlateManipulation.AutoOrient -> selected.intersect(meshes).ifEmpty { meshes }
            is PlateManipulation.Arrange, PlateManipulation.UpdatePrintVolume,
            is PlateManipulation.ArrangePlate, is PlateManipulation.FillBed,
            -> meshes
        }
    }
}

/**
 * ObjectList::toggle_auto_drop() for the object with the [mesh] file: with auto
 * drop off, manipulations leave the object where the user puts it, above the
 * plate included; turning it on again rests the object on the plate.
 */
class SetPlateObjectAutoDropUseCase(
    private val repository: PlateRepository,
    private val placePlateObject: PlacePlateObjectUseCase,
) {
    operator fun invoke(id: PlateInstanceId, autoDrop: Boolean) {
        var changed: PlateInstance? = null
        repository.update { state ->
            changed = null
            val target = state.objects.withMesh(id.mesh)
            val copy = target?.instances?.getOrNull(id.instance)
            if (target == null || copy == null || state.busy || copy.autoDrop == autoDrop) return@update state
            val updated = copy.copy(autoDrop = autoDrop)
            changed = updated
            state.recorded().copy(objects = state.objects.replaced(target.withInstance(id.instance, updated)))
        }
        val updated = changed ?: return
        if (autoDrop) placePlateObject(id, updated.inspection.placement, Manipulation.EnsureOnBed, record = false)
    }

    /**
     * ObjectList::toggle_auto_drop() of the object's row: every copy takes it,
     * and turned on, the object rests on the plate (ModelObject::ensure_on_bed(),
     * which the app does copy by copy).
     */
    fun wholeObject(mesh: ScenePath, autoDrop: Boolean) {
        var changed: PlateObject? = null
        repository.update { state ->
            changed = null
            val target = state.objects.withMesh(mesh)
            if (target == null || state.busy || target.instances.all { it.autoDrop == autoDrop }) return@update state
            val updated = target.withInstances(target.instances.map { it.copy(autoDrop = autoDrop) })
            changed = updated
            state.recorded().copy(objects = state.objects.replaced(updated))
        }
        val updated = changed ?: return
        if (autoDrop) {
            updated.instances.forEachIndexed { index, copy ->
                placePlateObject(PlateInstanceId(mesh, index), copy.inspection.placement, Manipulation.EnsureOnBed, record = false)
            }
        }
    }
}

/**
 * ObjectList::toggle_printable_state() for one copy of an object: a copy that
 * is not printable stays on the plate and is left out of the print, so the
 * G-code sliced before it no longer applies.
 */
class SetPlateObjectPrintableUseCase(private val repository: PlateRepository) {
    operator fun invoke(id: PlateInstanceId, printable: Boolean) = repository.update { state ->
        val target = state.objects.withMesh(id.mesh)
        val copy = target?.instances?.getOrNull(id.instance)
        if (target == null || copy == null || state.busy || copy.printable == printable) return@update state
        state.recorded().copy(
            objects = state.objects.replaced(target.withInstance(id.instance, copy.copy(printable = printable))),
            result = null,
        )
    }

    /** The object's own row: every copy of it, as one step of Undo. */
    fun all(mesh: ScenePath, printable: Boolean) = repository.update { state ->
        val target = state.objects.withMesh(mesh)
        if (target == null || state.busy || target.instances.all { it.printable == printable }) return@update state
        state.recorded().copy(
            objects = state.objects.replaced(target.withInstances(target.instances.map { it.copy(printable = printable) })),
            result = null,
        )
    }
}

/**
 * GLCanvas3D's Selection: the object the user picked on the plate, which the
 * object's settings follow, as the parameter panel follows the object list.
 * With no object picked the settings are the plate's, whose row OrcaSlicer's
 * object list shows above the objects.
 */
class SelectPlateObjectUseCase(private val repository: PlateRepository) {
    /**
     * Picks the copy [id] alone, or nothing when it is null. With [add] the
     * copy joins the selection or leaves it, as a Ctrl-click of the desktop
     * app does.
     */
    operator fun invoke(id: PlateInstanceId?, add: Boolean = false) = repository.update { state ->
        val target = id?.takeIf { state.objects.withMesh(it.mesh)?.instances?.size ?: 0 > it.instance }
        val selected = when {
            target == null -> emptySet()
            !add -> setOf(target)
            target in state.selectedInstances -> state.selectedInstances - target
            else -> state.selectedInstances + target
        }
        if (selected == state.selectedInstances && state.selectedPart == null && state.selectedRange == null && state.selectedConnectors == null) {
            state
        } else {
            // The desktop list has one selection: picking an object drops the
            // part, the height range and the connectors under it.
            state.copy(selectedInstances = selected, selectedPart = null, selectedRange = null, selectedConnectors = null)
        }
    }

    /**
     * GLCanvas3D::_update_selection_from_hover() of a rectangle let go with
     * Ctrl held: the copies [ids] join the selection, which keeps what it held.
     */
    fun add(ids: Set<PlateInstanceId>) = repository.update { state ->
        val added = ids.filter { id -> state.objects.withMesh(id.mesh)?.instances?.size ?: 0 > id.instance }
        val selected = state.selectedInstances + added
        if (selected == state.selectedInstances && state.selectedPart == null) {
            state
        } else {
            state.copy(selectedInstances = selected, selectedPart = null, selectedRange = null, selectedConnectors = null)
        }
    }

    /**
     * ObjectList::fix_cut_selection() while the scale gizmo is open: the first
     * copy of a part of a cut in the selection takes the same copy of every
     * part of that cut (CutObjectBase::has_same_id()) in place of the rest.
     */
    fun withCutParts() = repository.update { state ->
        if (state.selectedPart != null) return@update state
        for (id in state.selectedInstances) {
            val cut = state.objects.withMesh(id.mesh)?.cutId ?: continue
            val parts = state.objects
                .filter { it.cutId?.hasSameId(cut) == true && id.instance < it.instances.size }
                .mapTo(LinkedHashSet()) { PlateInstanceId(it.mesh, id.instance) }
            return@update if (parts == state.selectedInstances) state else state.copy(selectedInstances = parts, selectedRange = null, selectedConnectors = null)
        }
        state
    }
}

/**
 * ObjectList::set_extruder_for_selected_items(): which filament of the plate
 * prints an object, one of its parts or one of its height ranges. The desktop
 * app only lets a part of the model or a modifier take one, refuses a filament
 * the plate does not have, and gives an object that takes a filament the last
 * word: the parts under it lose theirs. G-code sliced before no longer applies.
 */
class SetExtruderUseCase(private val repository: PlateRepository) {
    /** The object itself, which its volumes of the model then follow, its own mesh included. */
    operator fun invoke(mesh: ScenePath, extruder: Int) = write(mesh, extruder) { target -> objectWith(target, extruder) }

    /** One volume of an object (an itVolume row): its own mesh or one of its parts. */
    operator fun invoke(id: ObjectPartId, extruder: Int) = write(id.mesh, extruder) { target -> volumeWith(target, id.index, extruder) }

    /**
     * The selected items at once, as the assembly view's filament buttons give
     * them one (Plater::fill_color()): the selected part, or every selected
     * object, after one "Change Filaments" snapshot.
     */
    fun selected(extruder: Int) = repository.update { state ->
        val filaments = state.profiles?.allFilaments?.size ?: 0
        val parts = state.selectedParts()
        val meshes = if (parts.isNotEmpty()) listOf(parts.first().mesh) else state.selectedInstances.map { it.mesh }.distinct()
        if (state.busy || extruder > filaments || extruder < 0 || meshes.isEmpty()) return@update state
        val edited = meshes.mapNotNull { mesh ->
            val target = state.objects.withMesh(mesh) ?: return@mapNotNull null
            // Of several volumes, those that take no filament are left alone.
            if (parts.isNotEmpty()) parts.fold(target) { done, part -> volumeWith(done, part.index, extruder) ?: done } else objectWith(target, extruder)
        }
        val recorded = state.recorded()
        if (edited.isEmpty()) recorded else recorded.copy(objects = edited.fold(state.objects) { objects, updated -> objects.replaced(updated) }, result = null)
    }

    private fun objectWith(target: PlateObject, extruder: Int): PlateObject =
        // "default" on an object is filament 1, as the desktop app writes it.
        target.withSettings(target.settings.withExtruder(if (extruder == 0) 1 else extruder))
            .withVolume(target.volume.copy(settings = target.volume.settings.withExtruder(0)))
            .withParts(
                target.parts.map { part ->
                    if (part.type == VolumeType.PART) part.copy(settings = part.settings.withExtruder(0)) else part
                },
            )

    private fun volumeWith(target: PlateObject, index: Int, extruder: Int): PlateObject? {
        val part = target.volumeAt(index) ?: return null
        if (part.type != VolumeType.PART && part.type != VolumeType.MODIFIER) return null
        // "default" on a part of the model is the filament of its object; a
        // modifier keeps the default it was given.
        val number = if (extruder == 0 && part.type == VolumeType.PART) target.extruderNumber else extruder
        return target.withVolumeAt(index, part.copy(settings = part.settings.withExtruder(number)))
    }

    /**
     * The "Cut connectors" item of a part of a cut (set_extruder_for_selected_items()):
     * every connector of the object takes the filament as it is.
     */
    fun connectors(mesh: ScenePath, extruder: Int) = write(mesh, extruder) { target ->
        if (target.parts.none { it.cutInfo.connector }) return@write null
        target.withParts(target.parts.map { if (it.cutInfo.connector) it.copy(settings = it.settings.withExtruder(extruder)) else it })
    }

    /** One height range of an object (an itLayer row). */
    operator fun invoke(id: LayerRangeId, extruder: Int) = write(id.mesh, extruder) { target ->
        val range = target.layerRanges.getOrNull(id.index) ?: return@write null
        target.withLayerRangeAt(id.index, range.copy(settings = range.settings.withExtruder(extruder)))
    }

    private fun write(mesh: ScenePath, extruder: Int, edit: (PlateObject) -> PlateObject?) = repository.update { state ->
        val target = state.objects.withMesh(mesh)
        val filaments = state.profiles?.allFilaments?.size ?: 0
        if (target == null || state.busy || extruder > filaments || extruder < 0) return@update state
        val updated = edit(target) ?: return@update state
        // ObjectList::update_extruder_in_config(): "Change Filaments".
        state.recorded().copy(objects = state.objects.replaced(updated), result = null)
    }

    private fun ModelSettings.withExtruder(extruder: Int): ModelSettings =
        ModelSettings(if (extruder <= 0) values - EXTRUDER_KEY else values + (EXTRUDER_KEY to extruder.toString()))

    private companion object {
        const val EXTRUDER_KEY = "extruder"
    }
}

/**
 * The object list selects one part of an object (its itVolume row), whose own
 * settings the parameter panel then edits; the object it belongs to is selected
 * with it, as ObjectList::part_selection_changed() selects both.
 */
class SelectObjectPartUseCase(private val repository: PlateRepository) {
    /** The part [id], with the copy [instance] it is picked on (the object list's, the first). */
    operator fun invoke(id: ObjectPartId?, instance: Int = 0) = repository.update { state ->
        val target = id?.takeIf { state.objects.withMesh(it.mesh)?.volumeAt(it.index) != null }
        if (target == null) {
            if (state.selectedPart == null) state else state.copy(selectedPart = null, selectedPartGroup = emptySet())
        } else {
            state.copy(selectedInstances = setOf(PlateInstanceId(target.mesh, instance)), selectedPart = target, selectedPartGroup = emptySet(), selectedRange = null)
        }
    }

    /**
     * A Ctrl-click on the row of the volume [id]: it joins the volumes of its
     * object the list holds selected, or leaves them. A volume of another
     * object, or with no volume selected, is selected alone, as
     * ObjectList::fix_multiselection_conflicts() keeps the volumes of one
     * object only.
     */
    fun toggle(id: ObjectPartId) = repository.update { state ->
        if (state.objects.withMesh(id.mesh)?.volumeAt(id.index) == null) return@update state
        val selected = state.selectedParts()
        val current = state.selectedPart
        if (current == null || current.mesh != id.mesh) {
            val instance = state.selectedInstances.firstOrNull { it.mesh == id.mesh }?.instance ?: 0
            return@update state.copy(selectedInstances = setOf(PlateInstanceId(id.mesh, instance)), selectedPart = id, selectedPartGroup = emptySet(), selectedRange = null)
        }
        val group = if (id in selected) selected - id else selected + id
        when {
            group.isEmpty() -> state.copy(selectedPart = null, selectedPartGroup = emptySet())
            group.size == 1 -> state.copy(selectedPart = group.single(), selectedPartGroup = emptySet())
            else -> state.copy(selectedPart = current.takeIf { it in group } ?: group.first(), selectedPartGroup = group.toSet(), selectedRange = null)
        }
    }
}

/**
 * The tools of the 3D view as the plate knows them: whether one is open,
 * which some commands of the plate wait for, and the mesh boolean tool's
 * state, which the snapshots of the undo stack keep.
 */
class SetGizmoOpenUseCase(private val repository: PlateRepository) {
    operator fun invoke(open: Boolean) = repository.update { state -> if (state.gizmoOpen == open) state else state.copy(gizmoOpen = open) }

    fun meshBoolean(picks: MeshBooleanPicks?) = repository.update { state -> if (state.meshBooleanTool == picks) state else state.copy(meshBooleanTool = picks) }
}

/**
 * The switch over the settings (ParamsPanel::m_mode_region): the presets, or
 * the settings of the plate and of the selected object.
 */
class SetSettingsScopeUseCase(private val repository: PlateRepository) {
    operator fun invoke(scope: SettingsScope) = repository.update { state ->
        if (state.settingsScope == scope) state else state.copy(settingsScope = scope)
    }
}

/**
 * ObjectList::load_generic_subobject(): one of OrcaSlicer's shapes joins the
 * object as a part, a negative volume, a modifier, or a support blocker or
 * enforcer. The engine places it beside the object and writes its mesh for the
 * 3D view; every copy of the object gets it, and G-code sliced before no
 * longer applies. Nothing is added while the plate is busy.
 */
class AddObjectPartUseCase(
    private val inspector: PlateInspector,
    private val sceneFiles: SceneFiles,
    private val repository: PlateRepository,
    private val applicationScope: CoroutineScope,
    /** do_not_show_modifer_tips: the "Add Modifier" tip was turned off. */
    private val modifierTipsOff: () -> Boolean = { true },
) {
    operator fun invoke(mesh: ScenePath, shape: String, type: VolumeType, name: String = "") {
        var request: Pair<PlateObject, SlicingProfileSelection>? = null
        repository.update { state ->
            request = null
            val target = state.objects.withMesh(mesh)
            val profiles = state.profiles
            if (target == null || profiles == null || state.busy) return@update state
            request = target to profiles
            state
        }
        val (target, profiles) = request ?: return
        applicationScope.launch {
            val partMesh = sceneFiles.newObjectMesh()
            val outcome = try {
                inspector.addPart(target.placed(), shape, type, profiles, partMesh)
            } catch (cancellation: CancellationException) {
                sceneFiles.deleteObjectMesh(partMesh)
                throw cancellation
            } catch (error: Exception) {
                ModelInspectionOutcome.Failure(error.message.orEmpty())
            }
            repository.update { state ->
                val current = state.objects.withMesh(mesh)
                when {
                    current == null -> state
                    outcome is ModelInspectionOutcome.Success -> {
                        val part = ObjectPart(
                            shape = shape,
                            type = type,
                            mesh = partMesh,
                            placement = outcome.inspection.placement,
                            settings = newVolumeSettings(current, type),
                            name = name,
                        )
                        // reorder_volumes_and_get_selection(): ModelObject::sort_volumes(true)
                        // orders the volumes by type (parts, negative volumes,
                        // modifiers, blockers, enforcers), keeping the order of
                        // each type, and the list selects the new volume.
                        val sorted = (current.parts + part).sortedBy { it.type.ordinal }
                        val instance = state.selectedInstances.firstOrNull { it.mesh == mesh }?.instance ?: 0
                        state.recorded().copy(
                            objects = state.objects.replaced(current.withParts(sorted)),
                            selectedInstances = setOf(PlateInstanceId(mesh, instance)),
                            selectedPart = ObjectPartId(mesh, sorted.indexOf(part) + 1),
                            selectedPartGroup = emptySet(),
                            selectedRange = null,
                            result = null,
                            // TipsDialog "Add Modifier", until "Don't show again" turns it off.
                            plateNotices = if (modifierTipsOff()) state.plateNotices else state.plateNotices + ADD_MODIFIER_TIP,
                        )
                    }
                    else -> state.copy(
                        problem = PlateProblem(PlateProblemKind.PLACEMENT_FAILED, (outcome as ModelInspectionOutcome.Failure).message),
                    )
                }
            }
            if (outcome !is ModelInspectionOutcome.Success || repository.state.value.objects.withMesh(mesh) == null) {
                sceneFiles.deleteObjectMesh(partMesh)
            }
        }
    }
}

/** ObjectList::load_generic_subobject()'s TipsDialog. */
internal val ADD_MODIFIER_TIP = SettingsDialog(
    id = AppConfigKeys.DO_NOT_SHOW_MODIFIER_TIPS,
    icon = DialogIcon.INFO,
    title = listOf(OrcaText("Add Modifier")),
    text = listOf(OrcaText("Switch to per-object setting mode to edit modifier settings.")),
    question = false,
    yes = OrcaText("OK"),
    no = null,
    checkbox = OrcaText("Don't show again"),
)

/**
 * load_generic_subobject() and load_modifier(): a new part prints with the
 * object's filament ("extruder" of the object), any other volume with none of
 * its own (0, which the app keeps as no value).
 */
internal fun newVolumeSettings(target: PlateObject, type: VolumeType): ModelSettings =
    ModelSettings(listOfNotNull(target.settings.values[EXTRUDER_SETTING]?.takeIf { type == VolumeType.PART }?.let { EXTRUDER_SETTING to it }).toMap())

private const val EXTRUDER_SETTING = "extruder"

/**
 * ObjectList::del_subobject_item(): the part leaves the object ("Delete part"),
 * and G-code sliced with it no longer applies; its mesh stays while Undo can
 * bring the part back. Once the object is its own mesh alone, the settings of
 * that mesh become the object's (ObjectList::del_subobject_from_object).
 */
class RemoveObjectPartUseCase(private val repository: PlateRepository) {
    /**
     * Selection::erase() of several volumes of one object
     * (ObjectList::delete_from_model_and_list()): from the last on, each part
     * leaves the object as del_subobject_from_object() lets it, as one step of
     * Undo (Plater::remove_selected()); a solid part or a negative volume of a
     * part of a cut stops the rest with its question. Returns whether the
     * object's own mesh is still to go, which the engine takes out
     * (EditPlateObjectUseCase.deleteOwnVolume()).
     */
    fun all(ids: List<ObjectPartId>): Boolean {
        var ownVolume = false
        repository.update { state ->
            ownVolume = false
            val mesh = ids.firstOrNull()?.mesh ?: return@update state
            val target = state.objects.withMesh(mesh)
            if (target == null || state.busy || ids.any { it.mesh != mesh }) return@update state
            var updated: PlateObject = target
            var question: PendingPlateQuestion? = null
            for (index in ids.map { it.index }.filter { it > 0 }.distinct().sortedDescending()) {
                val part = updated.parts.getOrNull(index - 1) ?: continue
                question = updated.cutVolumeQuestion(part.type)
                if (question != null) break
                updated = updated.withoutPart(index)
            }
            ownVolume = question == null && ids.any { it.index == 0 }
            val asked = if (question != null) state.copy(plateQuestion = question) else state
            if (updated == target) return@update asked
            asked.recorded().copy(
                selectedPart = null,
                selectedPartGroup = emptySet(),
                objects = state.objects.replaced(updated),
                result = null,
            )
        }
        return ownVolume
    }

    operator fun invoke(id: ObjectPartId) {
        repository.update { state ->
            val target = state.objects.withMesh(id.mesh)
            // The object's own mesh goes through the engine (EditPlateObjectUseCase.deleteOwnVolume()).
            val part = target?.parts?.getOrNull(id.index - 1)
            if (target == null || part == null || state.busy) return@update state
            // A solid part or a negative volume of a part of a cut stays; the user is asked to invalidate the cut first.
            target.cutVolumeQuestion(part.type)?.let { return@update state.copy(plateQuestion = it) }
            val updated = target.withoutPart(id.index)
            state.recorded().copy(
                // The volumes after it move up, and none is listed once the object is its own mesh alone.
                selectedPart = state.selectedPart?.takeUnless { it.mesh == id.mesh && (it.index >= id.index || updated.parts.isEmpty()) },
                selectedPartGroup = emptySet(),
                objects = state.objects.replaced(updated),
                result = null,
            )
        }
    }
}

/**
 * The part at [index] (ObjectPartId.index) gone from the object; once the
 * object is its own mesh alone, the settings of that mesh become the object's
 * (ObjectList::del_subobject_from_object).
 */
private fun PlateObject.withoutPart(index: Int): PlateObject {
    val kept = parts.filterIndexed { at, _ -> at != index - 1 }
    return if (kept.isEmpty()) {
        withParts(kept)
            .withSettings(ModelSettings(settings.values + volume.settings.values))
            .withVolume(volume.copy(settings = ModelSettings()))
    } else {
        withParts(kept)
    }
}

/**
 * Export G-code of the desktop app's File menu: the G-code of the last slice is
 * written into the document the user picked. The plate must have been sliced,
 * since that is the file that is saved.
 */
class ExportGcodeUseCase(
    private val documents: DocumentExport,
    private val repository: PlateRepository,
) {
    /**
     * The name the file is offered under: the slice's Print::output_filename(),
     * or the G-code file's own name when the slice gave none.
     */
    fun suggestedName(): String? = repository.state.value.result?.let { result -> result.outputName.ifEmpty { java.io.File(result.gcode.value).name } }

    /**
     * Plater::export_gcode() and Plater::send_gcode() stop with this message,
     * when filename_format could not name the G-code; null when it could.
     */
    fun nameError(): String? = repository.state.value.result?.outputNameError?.ifEmpty { null }

    /** The G-code into [document]; the export's notification names the file it wrote. */
    suspend operator fun invoke(document: ExternalDocumentReference): Boolean {
        val result = repository.state.value.result ?: return false
        // gcode_add_line_number() of BackgroundSlicingProcess::export_gcode().
        val copied = if (result.addLineNumber) documents.copyNumberedTo(result.gcode.value, document) else documents.copyTo(result.gcode.value, document)
        if (!copied) return false
        val name = documents.displayName(document) ?: suggestedName().orEmpty()
        repository.update { it.copy(exportFinished = name) }
        return true
    }
}

/**
 * Android's share sheet for the G-code of the last slice, offered under the
 * name the slice gave it; null when the plate has none to share.
 */
class ShareGcodeUseCase(
    private val files: FileShare,
    private val repository: PlateRepository,
) {
    suspend operator fun invoke(): ExternalDocumentReference? {
        val result = repository.state.value.result ?: return null
        val gcode = result.gcode.value
        return files.shareable(gcode, result.outputName.ifEmpty { java.io.File(gcode).name })
    }
}

/**
 * The printer's host as the edited printer preset holds it, which
 * PhysicalPrinterDialog edits, sending G-code goes to and the Device tab shows
 * the page of.
 */
class ObservePrinterConnectionUseCase(private val engine: PresetSettingsEditor) {
    suspend operator fun invoke(): PrinterConnectionOutcome = engine.printerConnection()
}

/**
 * MainFrame's Device tab (PrinterWebView): the page of the printer's host,
 * and what Elegoo's LAN page of a Centauri Carbon 2 asks of the app
 * (ElegooPrinterWebViewHandler). The engine names that page with the printer's
 * access code and address; ElegooLink::get_print_host_webui() adds the
 * printer's serial number, which takes a request to the printer, the panel's
 * id, the app's language ([language], "ru_RU") and, in developer mode, the
 * page's own developer switch.
 */
class DevicePageUseCase(
    private val engine: PresetSettingsEditor,
    private val uploader: GcodeSender,
    private val preferences: AppPreferences,
    private val language: () -> String,
) {
    suspend operator fun invoke(): PrinterConnectionOutcome {
        val outcome = engine.printerConnection()
        val connection = (outcome as? PrinterConnectionOutcome.Success)?.connection ?: return outcome
        if (!connection.webUi.contains(ELEGOO_PAGE)) return outcome
        val serial = uploader.serialNumber(connection.printer(""), lookUp = true)
        val url = buildString {
            append(connection.webUi)
            if (serial.isNotEmpty()) append("&sn=").append(serial)
            append("&id=elegoo_123456")
            language().takeIf { it.isNotEmpty() }?.let { append("&lang=").append(it) }
            if (preferences.bool(AppConfigKeys.DEVELOPER_MODE)) append("&dev=true")
        }
        return PrinterConnectionOutcome.Success(connection.copy(webUi = url))
    }

    /** handle_get_sn_request(): the serial number the app knows, without asking the printer. */
    suspend fun serialNumber(): String {
        val connection = (engine.printerConnection() as? PrinterConnectionOutcome.Success)?.connection ?: return ""
        return uploader.serialNumber(connection.printer(""), lookUp = false)
    }

    /**
     * handle_upload_request(): a file the page picked goes to the printer's
     * host, only stored (PrintHostPostUploadAction::None), under its own name.
     */
    suspend fun upload(path: String, onProgress: (Float) -> Unit): PrintHostUploadOutcome {
        val connection = (engine.printerConnection() as? PrinterConnectionOutcome.Success)?.connection
            ?: return PrintHostUploadOutcome.Failure("Could not get a valid Printer Host reference")
        return uploader.send(connection.printer(""), OutputPath(path), startPrint = false, options = PrintOptions(), onProgress = onProgress)
    }

    private companion object {
        const val ELEGOO_PAGE = "/web/elegoolink/lan_service_web/index.html"
    }
}

/** PhysicalPrinterDialog's Test button. */
class TestPhysicalPrinterUseCase(private val uploader: GcodeSender) {
    suspend operator fun invoke(printer: PhysicalPrinter): PrintHostTestOutcome = uploader.test(printer)
}

/**
 * The login of a cloud host outside the app (OAuthDialog), which the Test
 * button starts when the host did not answer, and its Log Out button.
 */
class CloudLoginUseCase(private val uploader: GcodeSender) {
    /** [openPage] opens the host's login page in the browser; the login is kept when it succeeds. */
    suspend operator fun invoke(printer: PhysicalPrinter, openPage: (String) -> Unit): CloudLoginOutcome = uploader.cloudLogin(printer, openPage)

    suspend fun isLoggedIn(printer: PhysicalPrinter): Boolean = uploader.isLoggedIn(printer)

    suspend fun logOut(printer: PhysicalPrinter) = uploader.logOut(printer)
}

/** PhysicalPrinterDialog::update_printers(), its Refresh button: the printers of a server that serves several. */
class ListHostPrintersUseCase(private val uploader: GcodeSender) {
    suspend operator fun invoke(printer: PhysicalPrinter): HostPrintersOutcome = uploader.printers(printer)
}

/**
 * PhysicalPrinterDialog's Browse button, which a host that finds itself on the
 * network offers (PrintHost::has_auto_discovery()): a Creality printer is
 * looked for with Creality's own scan (CrealityDiscoveryDialog), every other
 * host with a Bonjour lookup of OctoPrint's service (BonjourDialog).
 */
class BrowsePrintHostsUseCase(private val discovery: PrintHostDiscovery) {
    /**
     * BonjourDialog::show_and_lookup(): three rounds of four seconds, asking
     * for the TXT version and model. The flow gives the services found so far
     * as the dialog lists them — once each, without the resin printers (the
     * model SL1), which an FFF printer is not — and ends with the lookup.
     */
    fun lookup(): Flow<List<BonjourReply>> = flow {
        val replies = sortedSetOf(BONJOUR_ORDER)
        emit(emptyList())
        discovery.lookup("octoprint", setOf("version", "model"), retries = 3, timeoutSeconds = 4).collect { reply ->
            if (reply.txtData["model"] == "SL1") return@collect
            // The dialog inserts every reply of its ordered set at the top.
            if (replies.add(reply)) emit(replies.toList().asReversed())
        }
    }

    /** CrealityDiscoveryDialog::run_discovery(): the K-series printers, with the model each reports. */
    suspend fun scanCreality(): List<CrealityHost> = discovery.scanCreality()

    /** Flashforge::discover_printers(): the Flashforge printers that answer the broadcast. */
    suspend fun discoverFlashforge(): FlashforgeDiscoveryOutcome = discovery.discoverFlashforge()

    private companion object {
        /** BonjourReply::operator<: by the address (IPv4 before IPv6), then the full address, then the service's name. */
        val BONJOUR_ORDER: Comparator<BonjourReply> = compareBy<BonjourReply>({ it.ip.contains(':') }, { ipv4Value(it.ip) }, { it.ip })
            .thenBy { it.fullAddress }
            .thenBy { it.serviceName }

        fun ipv4Value(ip: String): Long =
            ip.split('.').takeIf { it.size == 4 }?.fold(0L) { value, part -> (value shl 8) + (part.toLongOrNull() ?: 0L) } ?: 0L
    }
}

/** What finds the printers of the local network, which the app builds from the platform's sockets. */
interface PrintHostDiscovery {
    /**
     * Bonjour::lookup(): the services of [service] that answer, as they
     * answer, over [retries] rounds of [timeoutSeconds].
     */
    fun lookup(service: String, txtKeys: Set<String>, retries: Int, timeoutSeconds: Int): Flow<BonjourReply>

    /** CrealityHostDiscovery::scan(): the K-series printers, each asked its model. */
    suspend fun scanCreality(): List<CrealityHost>

    /** Flashforge::discover_printers(). */
    suspend fun discoverFlashforge(): FlashforgeDiscoveryOutcome
}

/**
 * PrintHost::upload: the G-code of the last slice is sent to the printer, and
 * the printer is asked to start printing it when the user wants that. The plate
 * must have been sliced, since that is the file that is sent.
 */
class SendGcodeUseCase(
    private val uploader: GcodeSender,
    private val repository: PlateRepository,
    /** Plater::send_gcode(): the .gcode.3mf of a printer that takes one (use_3mf). */
    private val saveProject: SaveProjectUseCase? = null,
    private val sceneFiles: SceneFiles? = null,
) {
    suspend operator fun invoke(
        printer: PhysicalPrinter,
        startPrint: Boolean,
        options: PrintOptions = PrintOptions(),
        /** Http::on_progress: what part of the file has gone out, from 0 to 1. */
        onProgress: (Float) -> Unit = {},
    ): PrintHostUploadOutcome {
        val result = repository.state.value.result
            ?: return PrintHostUploadOutcome.Failure("There is no sliced G-code to send")
        if (!options.use3mf || saveProject == null || sceneFiles == null) {
            return uploader.send(printer, result.gcode, startPrint, options, onProgress)
        }
        val prefix = sceneFiles.newImportPrefix()
        val file = ScenePath("${prefix.value}-upload.gcode.3mf")
        try {
            if (!saveProject.writeForUpload(file)) return PrintHostUploadOutcome.Failure("Abnormal print file data. Please slice again")
            return uploader.send(printer, OutputPath(file.value), startPrint, options, onProgress)
        } finally {
            sceneFiles.deleteImport(prefix)
        }
    }

    /**
     * CrealityPrintHostSendDialog: the slots of the printer's material boxes,
     * which the filaments of the plate are fed from; empty for a printer
     * without boxes.
     */
    suspend fun printerSlots(printer: PhysicalPrinter): PrinterSlotsOutcome = uploader.slots(printer)

    /**
     * Plater::send_gcode_legacy() for a Flashforge printer on its local API:
     * the slots of its material station, read before the dialog opens.
     */
    suspend fun flashforgeSlots(printer: PhysicalPrinter): FlashforgeSlotsOutcome = uploader.flashforgeSlots(printer)

    /**
     * C3DPrinterOS::upload() before its UploadOptionsDialog: the session is
     * checked, and the cloud's projects and printer types are read.
     */
    suspend fun printer3dOsLists(printer: PhysicalPrinter): Printer3dOsListsOutcome = uploader.printer3dOsLists(printer)

    /**
     * Plater::send_gcode_legacy() before its dialog opens: the groups of a
     * Repetier server and the storages of the host (get_groups(), get_storage()).
     */
    suspend fun groups(printer: PhysicalPrinter): List<String> = uploader.groups(printer)

    suspend fun storage(printer: PhysicalPrinter): HostStorageOutcome = uploader.storage(printer)

    /** PhysicalPrinterDialog's Test button: whether the host answers and is what it says it is. */
    suspend fun testPrinter(printer: PhysicalPrinter): PrintHostTestOutcome = uploader.test(printer)
}

/** What sends the file, which the app builds from the platform's HTTP. */
interface GcodeSender {
    suspend fun send(
        printer: PhysicalPrinter,
        gcode: OutputPath,
        startPrint: Boolean,
        options: PrintOptions,
        onProgress: (Float) -> Unit = {},
    ): PrintHostUploadOutcome

    suspend fun slots(printer: PhysicalPrinter): PrinterSlotsOutcome

    /** Flashforge::fetch_material_slots(). */
    suspend fun flashforgeSlots(printer: PhysicalPrinter): FlashforgeSlotsOutcome

    /** 3DPrinterOS's session check, projects and printer types. */
    suspend fun printer3dOsLists(printer: PhysicalPrinter): Printer3dOsListsOutcome

    /** PrintHost::get_groups(): the groups of a Repetier server. */
    suspend fun groups(printer: PhysicalPrinter): List<String> = emptyList()

    /** PrintHost::get_storage(): the storages the file can go into. */
    suspend fun storage(printer: PhysicalPrinter): HostStorageOutcome = HostStorageOutcome.Success(emptyList(), emptyList())

    /** PrintHost::test(): whether the host at the printer's address answers. */
    suspend fun test(printer: PhysicalPrinter): PrintHostTestOutcome

    /** PrintHost::get_printers(): the printers of a server that serves several. */
    suspend fun printers(printer: PhysicalPrinter): HostPrintersOutcome

    /**
     * ElegooLink::get_sn(): the serial number of a Centauri Carbon 2, the one
     * the app knows, or with [lookUp] asked of the printer when it does not.
     */
    suspend fun serialNumber(printer: PhysicalPrinter, lookUp: Boolean): String

    /** A cloud host's login outside the app, which [openPage] opens the page of. */
    suspend fun cloudLogin(printer: PhysicalPrinter, openPage: (String) -> Unit): CloudLoginOutcome

    /** PrintHost::is_logged_in() */
    suspend fun isLoggedIn(printer: PhysicalPrinter): Boolean

    /** PrintHost::log_out() */
    suspend fun logOut(printer: PhysicalPrinter)
}


/**
 * Import Configs of the desktop app's File menu: the presets a document the
 * user picked holds are added to the installed ones, and the plate follows
 * them, since a preset of the same name is replaced. The document is copied in
 * first, because the engine reads plain files.
 */
class ImportConfigUseCase(
    private val engine: PresetSettingsEditor,
    private val presetManager: PresetManager,
    private val configFiles: ConfigFiles,
    private val platePresets: PlatePresets,
) {
    /**
     * The documents the user picked are copied in and imported together, as the
     * desktop app's file dialog takes several files. [answers] is what the user
     * has said about the presets that are already there; an import that meets
     * one it has no answer for comes back with it (ConfigTransferOutcome.Overwrite),
     * and runs again once it is answered.
     */
    suspend operator fun invoke(
        references: List<ExternalDocumentReference>,
        answers: Map<String, ConfigOverwriteAnswer> = emptyMap(),
    ): ConfigTransferOutcome {
        val paths = references.mapNotNull { configFiles.copyIn(it) }
        if (paths.isEmpty()) return ConfigTransferOutcome.Failure("The selected document could not be read")
        val outcome = engine.importPresets(paths, answers)
        if (outcome !is ConfigTransferOutcome.Failure) {
            // The installed presets changed, so the sidebar and the plate follow them.
            platePresets.apply(before = null, outcome = presetManager.presets())
        }
        return outcome
    }
}

/**
 * PresetBundleDialog, which the desktop app's top menu opens as "Preset
 * Bundle": the preset bundles the user has, and its "Delete bundle", after
 * which the sidebar and the plate follow the presets as after an import.
 */
class PresetBundlesUseCase(
    private val engine: PresetSettingsEditor,
    private val presetManager: PresetManager,
    private val platePresets: PlatePresets,
) {
    suspend fun list(): PresetBundlesOutcome = engine.presetBundles()

    suspend fun delete(id: String): PresetBundlesOutcome {
        val outcome = engine.deletePresetBundle(id)
        if (outcome is PresetBundlesOutcome.Success) {
            // The presets were loaded anew (OnFSWatch()), so the sidebar and the plate follow them.
            platePresets.apply(before = null, outcome = presetManager.presets())
        }
        return outcome
    }
}

/**
 * Export Preset Bundle of the desktop app's File menu: the user's own presets
 * of the selection are written as files and copied into the folder the user
 * picked. System presets are not exported, as the desktop app does not export
 * them either.
 */
class ExportConfigUseCase(
    private val engine: PresetSettingsEditor,
    private val configFiles: ConfigFiles,
) {
    /** ExportConfigsDialog: the printers or filaments it offers for [kind]. */
    suspend fun options(kind: ConfigExportKind): ConfigExportOptionsOutcome = engine.configExportOptions(kind)

    /**
     * Its OK: the entries chosen by name are written as the files the desktop
     * app writes, and those go into the folder the user picked.
     */
    suspend operator fun invoke(kind: ConfigExportKind, names: List<String>, folder: ExternalDocumentReference): ConfigTransferOutcome {
        val outcome = engine.exportConfigs(kind, names, configFiles.exportDirectory())
        if (outcome !is ConfigTransferOutcome.Success) return outcome
        val written = configFiles.copyOut(outcome.names, folder)
        return if (written == outcome.names.size) {
            outcome
        } else {
            ConfigTransferOutcome.Failure("Only $written of ${outcome.names.size} files could be written")
        }
    }
}

/**
 * TabPrinter::create_bed_shape_widget(): the shape of the printable area, which
 * BedShapeDialog sets. The printer preset then holds another plate, so the
 * plate is described again and the objects on it are judged against the new
 * build volume, as Plater::on_config_change() does.
 */
class SetBedShapeUseCase(
    private val settingsTabs: PresetSettingsTabs,
    private val presetManager: PresetManager,
    private val platePresets: PlatePresets,
) {
    suspend operator fun invoke(shape: BedShape) {
        if (!settingsTabs.setBedShape(shape)) return
        platePresets.apply(before = null, outcome = presetManager.presets())
    }
}

/** PhysicalPrinterDialog's Browse button for the HTTPS CA file: the path of the kept copy. */
class PrintHostCertificateUseCase(private val files: CertificateFiles) {
    suspend fun keep(document: ExternalDocumentReference): String? = files.keep(document)
}

/**
 * BedShapePanel's files: load_stl() reads the shape of an STL file;
 * load_texture() and load_model() take a PNG or SVG texture and an STL model,
 * which the app keeps a copy of for the preset to name; and Bed_2D's grid.
 */
class BedShapeFilesUseCase(
    private val files: BedFiles,
    private val editor: PresetSettingsEditor,
) {
    /** load_stl(): the shape of the STL file [document], or Orca's message why it has none. */
    suspend fun loadShape(document: ExternalDocumentReference): BedShapeOutcome {
        val path = files.keep(document) ?: return BedShapeOutcome.Failure("Error! Invalid model")
        return editor.loadBedShape(ModelPath(path))
    }

    /**
     * load_texture() ([texture]) and load_model(): the path of the kept file,
     * or Orca's "Invalid file format." for a file of another type.
     */
    suspend fun keep(document: ExternalDocumentReference, texture: Boolean): BedFileOutcome {
        val path = files.keep(document) ?: return BedFileOutcome.Failure("Invalid file format.")
        val accepted = if (texture) path.endsWith(".png", ignoreCase = true) || path.endsWith(".svg", ignoreCase = true) else path.endsWith(".stl", ignoreCase = true)
        return if (accepted) BedFileOutcome.Kept(path) else BedFileOutcome.Failure("Invalid file format.")
    }

    /**
     * CreatePrinterPresetDialog's load_texture() ([texture]) and
     * load_model_stl(): as [keep], and a file over STL_SVG_MAX_FILE_SIZE_MB is
     * not taken.
     */
    suspend fun keepForPrinter(document: ExternalDocumentReference, texture: Boolean): BedFileOutcome {
        val kept = keep(document, texture)
        if (kept !is BedFileOutcome.Kept) return kept
        return if (files.size(kept.path).toDouble() / 1024 / 1024 > STL_SVG_MAX_FILE_SIZE_MB) {
            BedFileOutcome.TooLarge(STL_SVG_MAX_FILE_SIZE_MB)
        } else {
            kept
        }
    }

    suspend fun preview(points: List<Point2>): BedPreview = editor.bedPreview(points)

    fun exists(path: String): Boolean = files.exists(path)

    private companion object {
        /** FileHelp.hpp */
        const val STL_SVG_MAX_FILE_SIZE_MB = 3
    }
}

/**
 * ObjectList::layers_editing() and add_layer_range_after_current(): a height
 * range of the object, which prints with a layer height of its own. The first
 * range the desktop app adds is 0 to 2 mm; every later one starts where the
 * range it follows ends and is 2 mm high, unless a range comes after it (the
 * next by its heights): a range that touches it splits in two, and a gap up to
 * it is filled, each only with room for the printer's least layer heights
 * (get_min_layer_height()); a next range that overlaps it leaves nothing to
 * add. The range starts with the settings of get_default_layer_config(): the
 * layer height of the object or of the process, and the object's filament.
 * G-code sliced before no longer applies.
 */
class AddLayerRangeUseCase(private val repository: PlateRepository, private val editor: PresetSettingsEditor) {
    /** [after] is the range the new one follows; null adds the first one. */
    suspend operator fun invoke(mesh: ScenePath, after: LayerRangeId? = null): LayerRangeId? {
        val owner = repository.state.value.objects.withMesh(mesh) ?: return null
        val defaults = (editor.defaultLayerConfig(owner.settings) as? ModelSettingsOutcome.Success)?.settings ?: return null
        var added: LayerRangeId? = null
        repository.update { state ->
            added = null
            val target = state.objects.withMesh(mesh)
            if (target == null || state.busy) return@update state
            val ranges = target.layerRanges
            val current = after?.let { ranges.getOrNull(it.index) }
            val next = after?.let { ranges.getOrNull(it.index + 1) }
            val newMin = state.minLayerHeight(0)
            val range = when {
                // ObjectList::layers_editing(): the first range of an object.
                current == null -> if (ranges.isEmpty()) LayerRange(0.0, FIRST_RANGE_HEIGHT, defaults) else null
                // Adding a range after the last one is always possible.
                next == null -> LayerRange(current.top, current.top + FIRST_RANGE_HEIGHT, defaults)
                current.top > next.bottom -> null
                // Splitting the next range in two, which needs room for a layer of each.
                next.bottom == current.top -> {
                    val delta = next.top - next.bottom
                    val oldMin = state.minLayerHeight(next.settings.values["extruder"]?.toIntOrNull() ?: 0)
                    if (delta < oldMin + newMin - EPSILON) {
                        null
                    } else {
                        val middle = if (newMin > 0.5 * delta) next.top - newMin else next.bottom + maxOf(oldMin, 0.5 * delta)
                        LayerRange(current.top, middle, defaults)
                    }
                }
                // Filling the gap between this range and the next one.
                else -> LayerRange(current.top, next.bottom, defaults).takeIf { next.bottom - current.top >= newMin - EPSILON }
            } ?: return@update state
            val kept = if (current != null && next != null && next.bottom == current.top) {
                // The next range keeps its settings and starts where the new one ends.
                ranges.map { if (it === next) it.copy(bottom = range.top) else it }
            } else {
                ranges
            }
            val updated = target.withLayerRanges(kept + range)
            added = LayerRangeId(mesh, updated.layerRanges.indexOfFirst { it.bottom == range.bottom && it.top == range.top })
            state.recorded().copy(objects = state.objects.replaced(updated), result = null)
        }
        return added
    }

    /**
     * ObjectList::layers_editing() ("Height range Modifier"): an object without
     * ranges takes its first, 0 to 2 mm; one with ranges keeps them. The desktop
     * app then selects the "Layers" row, whose panel edits every range; a phone
     * edits each range from its own row, so the first range stands in for it.
     */
    suspend fun layersEditing(mesh: ScenePath): LayerRangeId? {
        val ranges = repository.state.value.objects.withMesh(mesh)?.layerRanges ?: return null
        return if (ranges.isEmpty()) invoke(mesh) else LayerRangeId(mesh, 0)
    }

    /** get_min_layer_height(): the printer's min_layer_height of the 1-based extruder, the first for 0. */
    private fun PlateState.minLayerHeight(extruder: Int): Double {
        val heights = presets?.minLayerHeights.orEmpty()
        return heights.getOrNull(maxOf(0, extruder - 1)) ?: heights.firstOrNull() ?: 0.0
    }

    private companion object {
        /** The 2 mm of ObjectList::layers_editing() and add_layer_range_after_current(). */
        const val FIRST_RANGE_HEIGHT = 2.0

        const val EPSILON = 1e-4
    }
}

/** ObjectList::del_layer_range(): the range leaves the object. */
class RemoveLayerRangeUseCase(private val repository: PlateRepository) {
    operator fun invoke(id: LayerRangeId) = repository.update { state ->
        val target = state.objects.withMesh(id.mesh)
        if (target == null || target.layerRanges.getOrNull(id.index) == null || state.busy) return@update state
        state.recorded().copy(
            selectedRange = state.selectedRange?.takeUnless { it.mesh == id.mesh && it.index >= id.index },
            objects = state.objects.replaced(target.withLayerRanges(target.layerRanges.filterIndexed { at, _ -> at != id.index })),
            result = null,
        )
    }
}

/**
 * ObjectList::edit_layer_range(): the range keeps its settings and spans other
 * heights, as the two fields of ObjectLayers take them, the bottom first: a
 * bottom at or above the top lifts the top half a millimetre above it, and a
 * top below the bottom is refused. Ranges may overlap; a range of the very same
 * heights gives way to the edited one, as the desktop app's map of ranges keeps
 * one per span. The fields refuse a negative height ("Invalid numeric.").
 */
class EditLayerRangeUseCase(private val repository: PlateRepository) {
    operator fun invoke(id: LayerRangeId, bottom: Double, top: Double) = repository.update { state ->
        val target = state.objects.withMesh(id.mesh)
        val range = target?.layerRanges?.getOrNull(id.index)
        if (target == null || range == null || state.busy || bottom < 0.0 || top < 0.0) return@update state
        var span = range.bottom to range.top
        // The "Min Z" field.
        if (abs(bottom - span.first) >= EPSILON) span = bottom to if (bottom < span.second) span.second else bottom + TOP_ABOVE_BOTTOM
        // The "Max Z" field, of the range the bottom left.
        if (abs(top - span.second) >= EPSILON && span.first <= top) span = span.first to top
        if (span == range.bottom to range.top) return@update state
        val moved = range.copy(bottom = span.first, top = span.second)
        val others = target.layerRanges.filterIndexed { at, other -> at != id.index && !(other.bottom == moved.bottom && other.top == moved.top) }
        val updated = target.withLayerRanges(others + moved)
        state.recorded().copy(
            selectedRange = LayerRangeId(id.mesh, updated.layerRanges.indexOfFirst { it === moved }),
            objects = state.objects.replaced(updated),
            result = null,
        )
    }

    private companion object {
        const val EPSILON = 1e-4

        /** ObjectLayers' "Min Z" field: max_z = min_z + 0.5 when the bottom passes the top. */
        const val TOP_ABOVE_BOTTOM = 0.5
    }
}

/**
 * ArrangeJob::prepare_all() and OrientJob::prepare_selection(): the copies on
 * the [locked] plates stay out; with none of the others to arrange, or with
 * every selected copy on a locked plate, the job warns.
 */
private fun PlateState.lockedWarning(asked: PlateManipulation, locked: Set<Int>): PlateProblemKind? {
    fun onLocked(copy: PlateInstance) = plateOf(copy)?.let { it in locked } == true
    return when (asked) {
        is PlateManipulation.Arrange -> {
            val copies = objects.flatMap { it.instances }
            when {
                copies.any { it.printable && !onLocked(it) } -> null
                copies.any(::onLocked) -> PlateProblemKind.SELECTION_LOCKED_ARRANGE
                else -> PlateProblemKind.NO_ARRANGEABLE_OBJECTS
            }
        }
        is PlateManipulation.AutoOrient -> {
            val selected = objects.filter { it.mesh in asked.selected }.flatMap { it.instances }
            PlateProblemKind.SELECTION_LOCKED_ORIENT.takeIf { selected.isNotEmpty() && selected.all(::onLocked) }
        }
        else -> null
    }
}

/**
 * The object list selects one height range of an object (its itLayer row),
 * whose own settings the parameter panel then edits; the object it belongs to
 * is selected with it, as the desktop list selects both. The 3D view shows the
 * range, none of its fields focused yet (ObjectLayers::reset_selection()).
 */
class SelectLayerRangeUseCase(private val repository: PlateRepository) {
    operator fun invoke(id: LayerRangeId?) = repository.update { state ->
        val target = id?.takeIf { state.objects.withMesh(it.mesh)?.layerRanges?.size ?: 0 > it.index }
        if (target == null) {
            if (state.selectedRange == null) state else state.copy(selectedRange = null)
        } else {
            state.copy(
                selectedInstances = setOf(PlateInstanceId(target.mesh)),
                selectedPart = null,
                selectedRange = target,
                layerRangeEditor = LayerRangeEditor.LAYER_HEIGHT,
            )
        }
    }

    /** LayerRangeEditor's wxEVT_SET_FOCUS: a field of the selected range takes the focus. */
    fun focus(field: LayerRangeEditor) = repository.update { state ->
        if (state.selectedRange == null || state.layerRangeEditor == field) state else state.copy(layerRangeEditor = field)
    }
}

/**
 * ObjectList::copy_layers_to_clipboard(): Copy over the height ranges of the
 * object list. The "Layers" row takes every range of its object in place of
 * what the clipboard kept; a range row adds its range to them, in place of
 * one of the same heights.
 */
class CopyLayerRangesUseCase(private val repository: PlateRepository) {
    operator fun invoke(id: LayerRangeId) = repository.update { state ->
        val range = state.objects.withMesh(id.mesh)?.layerRanges?.getOrNull(id.index) ?: return@update state
        val kept = state.listClipboard?.ranges.orEmpty().filterNot { it.bottom == range.bottom && it.top == range.top }
        state.copy(listClipboard = ListClipboard(holdsRanges = true, ranges = (kept + range).sortedWith(RANGE_ORDER)))
    }

    /** The "Layers" row: every range of the object with the [mesh] file. */
    fun all(mesh: ScenePath) = repository.update { state ->
        val ranges = state.objects.withMesh(mesh)?.layerRanges?.takeIf { it.isNotEmpty() } ?: return@update state
        state.copy(listClipboard = ListClipboard(holdsRanges = true, ranges = ranges.sortedWith(RANGE_ORDER)))
    }
}

/** t_layer_config_ranges' order: by the bottom, then by the top of a range. */
internal val RANGE_ORDER: Comparator<LayerRange> = compareBy(LayerRange::bottom, LayerRange::top)

/**
 * Plater::increase_instances(): [count] more copies of the object, each offset
 * from its last copy by another 5% of the largest side of the bed, which the
 * engine then places as it places a moved copy. The last new copy is
 * selected, as the desktop app selects it. A copy that is not printable keeps
 * the object from getting more (Plater::can_increase_instances).
 */
class AddPlateInstanceUseCase(
    private val repository: PlateRepository,
    private val placePlateObject: PlacePlateObjectUseCase,
    private val selectPlateObject: SelectPlateObjectUseCase,
) {
    operator fun invoke(mesh: ScenePath, count: Int = 1) {
        var added: List<Pair<PlateInstanceId, Transform3>> = emptyList()
        repository.update { state ->
            added = emptyList()
            val target = state.objects.withMesh(mesh)
            val last = target?.instances?.lastOrNull()
            val plate = state.plate
            // Plater::can_increase_instances(): every copy printed, and no part of a cut.
            if (target == null || last == null || plate == null || state.busy || count <= 0 ||
                !target.instances.all(PlateInstance::printable) || target.isCut
            ) {
                return@update state
            }
            // GLCanvas3D::get_size_proportional_to_max_bed_size(0.05)
            val area = plate.geometry.printableArea
            val width = (area.maxOfOrNull { it.x } ?: 0.0) - (area.minOfOrNull { it.x } ?: 0.0)
            val depth = (area.maxOfOrNull { it.y } ?: 0.0) - (area.minOfOrNull { it.y } ?: 0.0)
            val offsetBase = 0.05 * maxOf(width, depth)
            added = (1..count).map { step ->
                val columns = last.inspection.placement.columns.toMutableList()
                columns[12] += offsetBase * step
                columns[13] += offsetBase * step
                PlateInstanceId(mesh, target.instances.size + step - 1) to Transform3(columns)
            }
            // A new copy stands where the last one does until the engine
            // places it, so its placement below is a change the engine is asked for.
            val copies = List(count) { last.copy(placing = true) }
            // G-code sliced before no longer applies once more copies print.
            state.recorded().copy(objects = state.objects.replaced(target.withInstances(target.instances + copies)), result = null)
        }
        if (added.isEmpty()) return
        selectPlateObject(added.last().first)
        added.forEach { (instance, placement) -> placePlateObject(instance, placement, Manipulation.Move, record = false) }
    }
}

/**
 * Plater::decrease_instances(): the last [count] copies of the object leave the
 * plate, and the object goes when that is all of them. The last copy left is
 * selected.
 */
class RemoveLastPlateInstancesUseCase(
    private val repository: PlateRepository,
    private val deletePlateObject: DeletePlateObjectUseCase,
) {
    operator fun invoke(mesh: ScenePath, count: Int = 1) {
        var deleteObject = false
        repository.update { state ->
            deleteObject = false
            val target = state.objects.withMesh(mesh)
            // Plater::can_decrease_instances(): no part of a cut.
            if (target == null || state.busy || target.instances.size <= 1 || count <= 0 || target.isCut) return@update state
            if (target.instances.size <= count) {
                deleteObject = true
                return@update state
            }
            val kept = target.instances.dropLast(count)
            state.recorded().copy(
                objects = state.objects.replaced(target.withInstances(kept)),
                selectedInstances = setOf(PlateInstanceId(mesh, kept.lastIndex)),
                selectedPart = null,
                selectedRange = null,
                result = null,
            )
        }
        if (deleteObject) deletePlateObject(mesh)
    }
}

/**
 * Plater::set_number_of_copies(): the object gets as many copies as the user
 * asked for, from 0 to 1000; none takes the object off the plate.
 */
class SetNumberOfInstancesUseCase(
    private val repository: PlateRepository,
    private val addPlateInstance: AddPlateInstanceUseCase,
    private val removeLastPlateInstances: RemoveLastPlateInstancesUseCase,
    private val deletePlateObject: DeletePlateObjectUseCase,
) {
    operator fun invoke(mesh: ScenePath, number: Int) {
        val target = repository.state.value.objects.withMesh(mesh) ?: return
        if (number !in 0..MAX_COPIES) return
        val difference = number - target.instances.size
        when {
            // set_number_of_copies() of 0: decrease_instances() of every copy,
            // which keeps an object of one copy, or a part of a cut.
            number == 0 -> removeLastPlateInstances(mesh, target.instances.size)
            difference > 0 -> addPlateInstance(mesh, difference)
            difference < 0 -> removeLastPlateInstances(mesh, -difference)
        }
    }

    companion object {
        /** GetNumberFromUser(..., 0, 1000, ...) */
        const val MAX_COPIES = 1000
    }
}

/**
 * ObjectList::rename_item(): an object or one of its volumes takes the name
 * the user entered. An empty name changes nothing, and neither does a name
 * with a character a file name cannot hold (Plater::has_illegal_filename_characters).
 */
class RenamePlateItemUseCase(private val repository: PlateRepository) {
    /** The object itself; update_name_in_model() names its only volume too. */
    operator fun invoke(mesh: ScenePath, name: String) = rename(mesh, name) { target ->
        target.withName(name).let { if (it.parts.isEmpty()) it.withVolume(it.volume.copy(name = name)) else it }
    }

    /** One volume of an object (an itVolume row). */
    operator fun invoke(id: ObjectPartId, name: String) = rename(id.mesh, name) { target ->
        when {
            id.index == 0 && target.parts.isNotEmpty() -> target.withVolume(target.volume.copy(name = name))
            else -> target.parts.getOrNull(id.index - 1)?.let { part -> target.withPartAt(id.index - 1, part.copy(name = name)) }
        }
    }

    private fun rename(mesh: ScenePath, name: String, edit: (PlateObject) -> PlateObject?) = repository.update { state ->
        val target = state.objects.withMesh(mesh)
        if (target == null || state.busy || name.isEmpty() || hasIllegalCharacters(name)) return@update state
        val renamed = edit(target) ?: return@update state
        state.recorded().copy(objects = state.objects.replaced(renamed))
    }

    companion object {
        /** Plater::has_illegal_filename_characters() */
        const val ILLEGAL_CHARACTERS = "<>:/\\|?*\""

        fun hasIllegalCharacters(name: String): Boolean = name.any { it in ILLEGAL_CHARACTERS }
    }
}

/**
 * Plater::decrease_instances(): the copy leaves the plate; the object goes with
 * its last copy, as the desktop app removes it.
 */
class RemovePlateInstanceUseCase(
    private val repository: PlateRepository,
    private val deletePlateObject: DeletePlateObjectUseCase,
) {
    /**
     * delete_from_model_and_list() of copies of several objects, none of them
     * all the copies of its object, as one step of Undo.
     */
    fun all(copies: List<PlateInstanceId>) = repository.update { state ->
        if (state.busy) return@update state
        val gone = copies.groupBy({ it.mesh }, { it.instance })
        val objects = state.objects.map { plateObject ->
            val removed = gone[plateObject.mesh]?.toSet() ?: return@map plateObject
            if (removed.size >= plateObject.instances.size) return@map plateObject
            plateObject.withInstances(plateObject.instances.filterIndexed { index, _ -> index !in removed })
        }
        if (objects == state.objects) return@update state
        state.recorded().copy(
            objects = objects,
            selectedInstances = state.selectedInstances.filterNot { it.mesh in gone }.toSet(),
            result = null,
        )
    }

    operator fun invoke(id: PlateInstanceId) {
        var deleteObject = false
        repository.update { state ->
            deleteObject = false
            val target = state.objects.withMesh(id.mesh)
            if (target == null || state.busy || id.instance !in target.instances.indices) return@update state
            if (target.instances.size == 1) {
                deleteObject = true
                return@update state
            }
            val kept = target.instances.filterIndexed { index, _ -> index != id.instance }
            state.recorded().copy(
                objects = state.objects.replaced(target.withInstances(kept)),
                // The copies after it move up, so the selection starts afresh.
                selectedInstances = state.selectedInstances.filterNot { it.mesh == id.mesh }.toSet(),
                result = null,
            )
        }
        if (deleteObject) deletePlateObject(id.mesh)
    }
}

/**
 * Plater::remove_selected() for the object with the [mesh] file ("Delete
 * Selected Objects"): the object leaves the plate, and G-code sliced with it no
 * longer applies; its meshes stay while Undo can bring it back. Nothing is
 * deleted while the plate is busy.
 */
class DeletePlateObjectUseCase(private val repository: PlateRepository) {
    operator fun invoke(mesh: ScenePath) = delete(listOf(mesh), confirmed = false, recorded = false)

    /**
     * ObjectList::delete_from_model_and_list() of the objects with the
     * [meshes] files, in their order, as one step of Undo: they go last first,
     * and a part of a cut asks before it goes, as delete_object_from_model()
     * does for each. Delete takes the cut off its other parts, which then go
     * without asking; Cancel stops there, and the objects gone before stay gone.
     */
    fun all(meshes: List<ScenePath>, recorded: Boolean = false) = delete(meshes.reversed(), confirmed = false, recorded = recorded)

    /** The warning about a part of a cut answered: Delete goes on, Cancel keeps the object and stops. */
    fun answer(yes: Boolean) {
        val request = repository.state.value.plateQuestion?.request as? PlateRequest.DeleteCutObject ?: return
        repository.update { it.copy(plateQuestion = null) }
        if (yes) delete(listOf(request.mesh) + request.after, confirmed = true, recorded = request.recorded)
    }

    /** [confirmed]: the first of [meshes] was asked about; [recorded]: the step of Undo is taken. */
    private fun delete(meshes: List<ScenePath>, confirmed: Boolean, recorded: Boolean) = repository.update { state ->
        if (state.busy) return@update state
        var current = state
        var taken = recorded
        for ((index, mesh) in meshes.withIndex()) {
            val target = current.objects.withMesh(mesh) ?: continue
            // Plater::priv::delete_object_from_model(): a part of a cut warns
            // first, and the other parts of the cut lose it with it.
            val cutId = target.cutId
            if (cutId != null && !(confirmed && index == 0)) {
                return@update current.copy(plateQuestion = deleteCutObjectQuestion(PlateRequest.DeleteCutObject(mesh, meshes.drop(index + 1), taken)))
            }
            if (!taken) {
                current = current.recorded()
                taken = true
            }
            val kept = if (cutId != null) current.objects.withoutCut(cutId) else current.objects
            current = current.copy(
                objects = kept.filterNot { it.mesh == mesh },
                // The settings follow the selection, which the deleted object leaves.
                selectedInstances = current.selectedInstances.filterNot { it.mesh == mesh }.toSet(),
                selectedPart = current.selectedPart?.takeUnless { it.mesh == mesh },
                selectedRange = current.selectedRange?.takeUnless { it.mesh == mesh },
                result = null,
            )
        }
        current
    }
}

/** Slices the objects on the plate with the selected profiles into the plate's G-code file. */
class SlicePlateUseCase(
    private val sliceModel: SliceModelUseCase,
    private val renderThumbnails: RenderThumbnailsUseCase,
    private val outputs: GcodeOutputs,
    private val sceneFiles: SceneFiles,
    private val repository: PlateRepository,
    private val applicationScope: CoroutineScope,
    /** ModelInfo::model_name of the project whose information sits in the directory; "" for none. */
    private val projectModelName: suspend (ScenePath) -> String = { "" },
) {
    operator fun invoke() {
        val job = start { it.canSlice } ?: return
        applicationScope.launch { job() }
    }

    /** "Slice all"'s turn at the current plate: its outcome once it ends; null when it could not start. */
    internal suspend fun sliceForAll(): SliceOutcome? = start { it.plateSliceable }?.invoke()

    /** The slice of the current plate when it is [ready] for one, which the returned job runs to its outcome. */
    private fun start(ready: (PlateState) -> Boolean): (suspend () -> SliceOutcome)? {
        val jobId = SliceJobId(UUID.randomUUID().toString())
        var started: PlateState? = null
        repository.update { state ->
            started = null
            if (!ready(state) || state.profiles == null) return@update state
            started = state
            state.copy(slicing = PlateSlicing(jobId), problem = null)
        }
        val plate = started ?: return null
        val objects = plate.objects
        val profiles = plate.profiles ?: return null
        val plateSettings = plate.plateSettings
        val layerGcodes = plate.layerGcodes
        // Plater::priv::get_export_gcode_filename(): the plate's name follows the
        // name, or with several plates, its number.
        val plateName = plate.plates.getOrNull(plate.currentPlate)?.name.orEmpty()
        val calibration = plate.plates.getOrNull(plate.currentPlate)?.calibration
        val paPattern = plate.paPattern
        val plateSuffix = when {
            plateName.isNotEmpty() -> "_$plateName"
            plate.plates.size > 1 -> "_plate_${plate.currentPlate + 1}"
            else -> ""
        }
        // Plater::export_gcode(): the project's name once the project has a file.
        val filenameBase = plate.project.name?.takeIf { plate.project.document != null }.orEmpty()
        val projectInfo = plate.project.info

        return {
            val toolpaths = sceneFiles.newToolpaths()
            val thumbnails = renderThumbnails(objects, plate.plate, plate.plateOrigin, plate.presets?.filamentColors.orEmpty(), profiles, toolpaths)
            val request = SliceRequest(
                jobId = jobId,
                objects = objects.map { it.placed() },
                output = outputs.outputFor(objects.outputName() + plateSuffix),
                toolpaths = toolpaths,
                wipeTower = sceneFiles.wipeTowerMeshOf(toolpaths),
                sliceInfo = sceneFiles.sliceInfoOf(toolpaths),
                printerProfile = profiles.printer,
                filamentProfile = profiles.filament,
                filamentProfiles = profiles.allFilaments,
                processProfile = profiles.process,
                plateSettings = plateSettings,
                thumbnails = thumbnails,
                layerGcodes = layerGcodes,
                calibration = calibration,
                paPattern = paPattern,
                naming = SliceOutputNaming(filenameBase, plateName, projectInfo?.let { projectModelName(it) }.orEmpty()),
            )
            val outcome = try {
                sliceModel(request, SliceProgressObserver { progress ->
                    repository.update { it.withJob(jobId) { job -> job.copy(progress = progress) } }
                })
            } catch (cancellation: CancellationException) {
                repository.update { it.withJob(jobId) { null } }
                throw cancellation
            } catch (error: Exception) {
                SliceOutcome.Failure(jobId, SliceFailureCode.SLICING_FAILED, error.message.orEmpty(), recoverable = true)
            }
            // Plater::_calib_pa_pattern_gen_gcode(): the plate keeps the codes the PA pattern gave it.
            val sliced = (outcome as? SliceOutcome.Success)?.patternGcodes ?: layerGcodes
            repository.update { state ->
                val kept = if (sliced !== layerGcodes && state.slicing?.jobId == outcome.jobId) state.copy(layerGcodes = sliced) else state
                kept.withOutcome(objects, sliced, outcome)
            }
            // Only the toolpaths of the plates' results stay; a failed or replaced job leaves none.
            sceneFiles.deleteToolpathsExcept(repository.state.value.partPlates().mapNotNull { it.result?.toolpaths })
            outcome
        }
    }

    /**
     * PrintBase::update_object_placeholders(): the G-code is named after the
     * file of the first object the plate prints (input_filename_base).
     */
    private fun List<PlateObject>.outputName(): String = when (val named = first { object_ -> object_.instances.any { it.inspection.fit == BuildVolumeFit.INSIDE } }) {
        is PlateObject.ImportedModel -> named.inputName.substringBeforeLast('.')
        is PlateObject.CalibrationCube -> CALIBRATION_CUBE
    }

    private fun PlateState.withOutcome(objects: List<PlateObject>, sliced: List<LayerGcode>, outcome: SliceOutcome): PlateState {
        if (slicing?.jobId != outcome.jobId) return this  // a newer job replaced this one
        return when (outcome) {
            is SliceOutcome.Success -> copy(
                slicing = null,
                slicesCompleted = slicesCompleted + 1,
                result = PlateSliceResult(
                    outcome.jobId,
                    objects,
                    outcome.gcodePath,
                    outcome.statistics,
                    outcome.toolpaths,
                    outcome.wipeTower,
                    sliceInfo = outcome.sliceInfo,
                    layerGcodes = sliced,
                    layerGcodeRules = outcome.layerGcodeRules,
                    outputName = outcome.outputName,
                    outputNameError = outcome.outputNameError,
                    notices = outcome.notices,
                    printReady = outcome.printReady,
                    postProcessSkipped = outcome.postProcessSkipped,
                    primeTowerOutside = outcome.primeTowerOutside,
                    addLineNumber = outcome.addLineNumber,
                ),
                // IMSlider::SetTicksValues(): the codes this print does not allow go.
                layerGcodes = layerGcodes.allowedBy(outcome.layerGcodeRules, sliderSpiralVase),
                sliderSpiralVase = outcome.layerGcodeRules.spiralVase,
            )

            is SliceOutcome.Failure -> copy(
                slicing = null,
                problem = PlateProblem(
                    kind = if (outcome.code == SliceFailureCode.ENGINE_CRASHED) PlateProblemKind.ENGINE_CRASHED else PlateProblemKind.SLICE_FAILED,
                    detail = outcome.message,
                ),
            )

            is SliceOutcome.Cancelled -> copy(slicing = null, problem = PlateProblem(PlateProblemKind.SLICE_CANCELLED))
        }
    }
}

class CancelPlateSlicingUseCase(
    private val cancelSlice: CancelSliceUseCase,
    private val repository: PlateRepository,
    private val applicationScope: CoroutineScope,
) {
    operator fun invoke() {
        val jobId = repository.state.value.slicing?.jobId ?: return
        applicationScope.launch {
            if (cancelSlice(jobId)) {
                repository.update { it.withJob(jobId) { job -> job.copy(cancelling = true) } }
            }
        }
    }
}

class DismissPlateProblemUseCase(private val repository: PlateRepository) {
    operator fun invoke() {
        repository.update { it.copy(problem = null) }
    }

    /** The close button of the advice to arrange a plate printed by object (BBLSeqPrintInfo). */
    fun seqPrintInfo() {
        repository.update { if (it.seqPrintInfo) it.copy(seqPrintInfo = false) else it }
    }

    /** The export's notification closes, by its button or after its time. */
    fun exportFinished() {
        repository.update { if (it.exportFinished != null) it.copy(exportFinished = null) else it }
    }

    /** The close button of the advice to simplify the object of [mesh]. */
    fun simplifySuggestion(mesh: ScenePath) {
        repository.update { if (mesh in it.simplifySuggestions) it.copy(simplifySuggestions = it.simplifySuggestions - mesh) else it }
    }

    /**
     * "Configuration can update now." closes: by its "Detail.", which opens
     * MsgUpdateConfig ([detail]), or by its close button.
     */
    fun profileUpdates(detail: Boolean) {
        repository.update { state -> state.profileUpdates?.let { state.copy(profileUpdates = it.copy(notified = false, confirming = detail)) } ?: state }
    }

    /** The close button of "Configuration package: ... updated to ...". */
    fun profileUpdateInstalled(update: ProfileUpdate) {
        repository.update { if (update in it.profileUpdatesInstalled) it.copy(profileUpdatesInstalled = it.profileUpdatesInstalled - update) else it }
    }
}

/** Changes the running job when it is still [jobId]; a null result ends it. */
private inline fun PlateState.withJob(jobId: SliceJobId, change: (PlateSlicing) -> PlateSlicing?): PlateState {
    val job = slicing?.takeIf { it.jobId == jobId } ?: return this
    return copy(slicing = change(job))
}

internal fun List<PlateObject>.withMesh(mesh: ScenePath): PlateObject? = firstOrNull { it.mesh == mesh }

/** The list with [plateObject] in place of the object with its mesh file. */
internal fun List<PlateObject>.replaced(plateObject: PlateObject): List<PlateObject> =
    map { if (it.mesh == plateObject.mesh) plateObject else it }

/** The object with one of its copies placed anew, and whether it is still being placed. */
private fun PlateObject.with(index: Int, inspection: ModelInspection, placing: Boolean): PlateObject {
    val copy = instances.getOrNull(index) ?: return this
    return withInstance(index, copy.copy(inspection = inspection, placing = placing))
}
