package app.orcinus.shadow.domain.plate

import app.orcinus.shadow.core.model.CalibrationMode
import app.orcinus.shadow.core.model.PartPlate
import app.orcinus.shadow.core.model.CalibrationParams
import app.orcinus.shadow.core.model.ModelLoadOutcome
import app.orcinus.shadow.core.model.PlateProblem
import app.orcinus.shadow.core.model.PlateProblemKind
import app.orcinus.shadow.core.model.PresetsOutcome
import app.orcinus.shadow.core.model.SlicingProfileSelection
import app.orcinus.shadow.slicing.api.PlateInspector
import app.orcinus.shadow.slicing.api.PresetManager
import app.orcinus.shadow.storage.api.SceneFiles
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The tests of the Calibration menu (MainFrame's calibration items and
 * Plater::calib_*): a new project named after the test once the user agreed
 * to leave the one before (Plater::new_project), the test's model on the
 * plate with the values of the presets it prints with, and the plate's print
 * told the test (Print::set_calib_params). What the test added is selected,
 * as load_files() selects what it added.
 */
class CalibrateUseCase(
    private val projectLifecycle: ProjectLifecycleUseCase,
    private val inspector: PlateInspector,
    private val presetManager: PresetManager,
    private val platePresets: PresetsApplier,
    private val sceneFiles: SceneFiles,
    private val repository: PlateRepository,
    private val applicationScope: CoroutineScope,
) {
    operator fun invoke(params: CalibrationParams) {
        val test = TESTS[params.mode] ?: return
        applicationScope.launch {
            if (!projectLifecycle.startNewProject(test.projectName)) return@launch
            var profiles: SlicingProfileSelection? = null
            repository.update { state ->
                profiles = state.profiles.takeUnless { state.busy }
                if (profiles == null) state else state.copy(importing = true, problem = null)
            }
            val selection = profiles ?: return@launch
            val prefix = sceneFiles.newImportPrefix()
            val outcome = try {
                inspector.prepareCalibration(params, selection, prefix)
            } catch (cancellation: CancellationException) {
                sceneFiles.deleteImport(prefix)
                repository.update { it.copy(importing = false) }
                throw cancellation
            } catch (error: Exception) {
                ModelLoadOutcome.Failure(error.message.orEmpty())
            }
            if (outcome !is ModelLoadOutcome.Success) sceneFiles.deleteImport(prefix)
            // The test changed the presets it prints with (Tab::reload_config()).
            val presets = if (outcome is ModelLoadOutcome.Success && outcome.presetsChanged) presetManager.presets() else null
            repository.update { state ->
                when (outcome) {
                    is ModelLoadOutcome.Success -> {
                        // An object of no file names its G-code after itself, as a primitive does.
                        val added = outcome.objects.map { loaded -> loaded.toPlateObject(test.modelFile.ifEmpty { loaded.name }) }
                        // PartPlateList::create_plate(): the plates the PA pattern's
                        // handles fill beyond the ones there are.
                        val plates = state.plates + List((outcome.plateCount - state.plates.size).coerceAtLeast(0)) { PartPlate() }
                        state.copy(
                            importing = false,
                            objects = state.objects + added,
                            selectedInstances = added.allCopies(),
                            selectedPart = null,
                            selectedRange = null,
                            plates = plates.mapIndexed { index, plate ->
                                if (index == state.currentPlate) plate.copy(calibration = outcome.calibration ?: params) else plate
                            },
                            paPattern = params.takeIf { it.mode == CalibrationMode.PA_PATTERN },
                            presets = (presets as? PresetsOutcome.Success)?.presets ?: state.presets,
                            plateNotices = state.plateNotices + outcome.notices,
                            result = null,
                        )
                    }
                    is ModelLoadOutcome.Failure -> state.copy(
                        importing = false,
                        problem = PlateProblem(PlateProblemKind.IMPORT_FAILED, outcome.message),
                        plateNotices = state.plateNotices + outcome.notices,
                    )
                    is ModelLoadOutcome.Question -> state.copy(importing = false)
                }
            }
            if (presets != null) platePresets.apply(selection, presets)
        }
    }

    /** A test of the menu: the project it starts and the file of its model under resources/calib. */
    private class Test(val projectName: String, val modelFile: String)

    private companion object {
        val TESTS = mapOf(
            // Plater::calib_temp()
            CalibrationMode.TEMP_TOWER to Test("Nozzle temperature test", "temperature_tower.drc"),
            // calib_max_vol_speed(), calib_retraction() and calib_VFA()
            CalibrationMode.VOL_SPEED_TOWER to Test("Max volumetric speed test", "SpeedTestStructure.drc"),
            CalibrationMode.RETRACTION_TOWER to Test("Retraction", "retraction_tower.drc"),
            CalibrationMode.VFA_TOWER to Test("VFA test", "vfa.drc"),
            // Plater::calib_pa()
            CalibrationMode.PA_TOWER to Test("Pressure Advance Test", "tower_with_seam.drc"),
            CalibrationMode.PA_LINE to Test("Pressure Advance Test", "pressure_advance_test.drc"),
            // The handles are cubes of create_mesh(), which name no file.
            CalibrationMode.PA_PATTERN to Test("Pressure Advance Test", ""),
        )
    }
}
