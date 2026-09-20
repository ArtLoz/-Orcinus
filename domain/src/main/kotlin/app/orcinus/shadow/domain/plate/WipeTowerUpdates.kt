package app.orcinus.shadow.domain.plate

import app.orcinus.shadow.core.model.FlushVolumesChange
import app.orcinus.shadow.core.model.FlushVolumesOutcome
import app.orcinus.shadow.core.model.ModelSettings
import app.orcinus.shadow.core.model.PlacedModel
import app.orcinus.shadow.core.model.PlateObject
import app.orcinus.shadow.core.model.SlicingProfileSelection
import app.orcinus.shadow.core.model.WipeTowerOutcome
import app.orcinus.shadow.domain.placed
import app.orcinus.shadow.slicing.api.PlateInspector
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * GLCanvas3D::reload_scene() builds the wipe tower volume again whenever the
 * plate, its presets or its settings change, because its size follows the
 * objects and the filaments printed on the plate. The app does the same off the
 * plate state: every change of the objects, the selected presets or the plate's
 * settings asks the engine what the tower looks like now.
 */
class WipeTowerUpdates(
    private val inspector: PlateInspector,
    private val repository: PlateRepository,
    private val applicationScope: CoroutineScope,
) {
    /** What the tower is described from; the engine is asked again when it changes. */
    private data class Input(
        val objects: List<PlacedModel>,
        val profiles: SlicingProfileSelection?,
        val plateSettings: ModelSettings,
    )

    fun start() {
        applicationScope.launch {
            repository.state
                .map { state -> Input(state.objects.map(PlateObject::placed), state.profiles, state.plateSettings) }
                .distinctUntilChanged()
                .collect { input -> describe(input) }
        }
    }

    private suspend fun describe(input: Input) {
        val profiles = input.profiles
        if (profiles == null || input.objects.isEmpty()) {
            repository.update { state -> if (state.wipeTower == null) state else state.copy(wipeTower = null) }
            return
        }
        val outcome = try {
            inspector.describeWipeTower(input.objects, profiles, input.plateSettings)
        } catch (error: IllegalStateException) {
            WipeTowerOutcome.Failure(error.message.orEmpty())
        }
        // A tower that cannot be described is simply not drawn; the plate is
        // still sliced, and the engine places the tower itself.
        val tower = (outcome as? WipeTowerOutcome.Success)?.tower?.takeIf { it.shown }
        repository.update { state ->
            val shown = if (state.wipeTower == tower) state else state.copy(wipeTower = tower)
            // PartPlateList::set_default_wipe_tower_pos_for_plate(): the desktop
            // app writes the position a new tower takes into the project, so the
            // slicer builds it where the plate shows it; without it the slicer
            // would take wipe_tower_x's own default and print it elsewhere.
            if (tower == null || WIPE_TOWER_X in shown.plateSettings.values) {
                shown
            } else {
                shown.copy(plateSettings = shown.plateSettings.withTowerAt(tower.x, tower.y), result = null)
            }
        }
    }
}

/**
 * GLCanvas3D::WipeTowerInfo::apply_wipe_tower(): the tower dragged across the
 * plate is written into the project as wipe_tower_x and wipe_tower_y, which the
 * app keeps among the settings of the plate. G-code sliced before no longer
 * applies, since the print changes with the tower.
 */
class MoveWipeTowerUseCase(private val repository: PlateRepository) {
    operator fun invoke(x: Double, y: Double) = repository.update { state ->
        val tower = state.wipeTower
        if (state.busy || tower == null) return@update state
        state.copy(
            plateSettings = state.plateSettings.withTowerAt(x, y),
            // The tower is drawn where it was dropped until the engine answers.
            wipeTower = tower.copy(x = x, y = y),
            result = null,
        )
    }

}

private const val WIPE_TOWER_X = "wipe_tower_x"
private const val WIPE_TOWER_Y = "wipe_tower_y"

/** wipe_tower_x and wipe_tower_y of the project, as the plate keeps them. */
private fun ModelSettings.withTowerAt(x: Double, y: Double): ModelSettings =
    ModelSettings(values + mapOf(WIPE_TOWER_X to towerCoordinate(x), WIPE_TOWER_Y to towerCoordinate(y)))

private fun towerCoordinate(value: Double): String = String.format(Locale.ROOT, "%.3f", value)

/**
 * WipingDialog: the flushing volumes of the plate, asked for when the sheet
 * that shows them opens. They are not part of the plate state, since nothing
 * else needs them.
 */
class DescribeFlushVolumesUseCase(
    private val inspector: PlateInspector,
    private val repository: PlateRepository,
) {
    suspend operator fun invoke(): FlushVolumesOutcome {
        val state = repository.state.value
        val profiles = state.profiles ?: return FlushVolumesOutcome.Failure("No printer is set up")
        return inspector.describeFlushVolumes(state.objects.map(PlateObject::placed), profiles, state.plateSettings)
    }
}

/**
 * open_flushing_dialog(): the volumes the dialog submits are written into the
 * project, which the app keeps among the settings of the plate. G-code sliced
 * before no longer applies, since the print changes with them.
 */
class SetFlushVolumesUseCase(private val repository: PlateRepository) {
    operator fun invoke(matrix: List<Double>, multipliers: List<Double>) = repository.update { state ->
        if (state.busy) return@update state
        state.copy(plateSettings = state.plateSettings.withFlushVolumes(matrix, multipliers), result = null)
    }
}

/** Sidebar::auto_calc_flushing_volumes(), which the plate's changes of filaments and printer ask for. */
fun interface FlushVolumesUpdater {
    /** The flushing volumes after [change] to the filament at [index]; -1 for every filament. */
    suspend fun update(change: FlushVolumesChange, index: Int)
}

/**
 * Sidebar::auto_calc_flushing_volumes() where the desktop app calls it: after a
 * filament joins the plate, leaves it, gets another colour or another preset,
 * after another printer is selected, and after the long retraction settings
 * change. The engine brings the project's matrix to the plate's filaments
 * (PresetBundle::update_multi_material_filament_presets) and works out again
 * what the desktop app's "Auto flush after changing..." preference asks for;
 * the plate keeps the volumes when they changed, since they are the project's.
 */
class UpdateFlushVolumesUseCase(
    private val inspector: PlateInspector,
    private val repository: PlateRepository,
) : FlushVolumesUpdater {
    override suspend fun update(change: FlushVolumesChange, index: Int) {
        val state = repository.state.value
        val profiles = state.profiles ?: return
        val outcome = inspector.updateFlushVolumes(state.objects.map(PlateObject::placed), profiles, state.plateSettings, change, index)
        if (outcome !is FlushVolumesOutcome.Success || !outcome.updated) return
        repository.update { current ->
            // The plate moved on to other presets meanwhile; their own change updates it.
            if (current.profiles != profiles) return@update current
            current.copy(
                plateSettings = current.plateSettings.withFlushVolumes(outcome.volumes.matrix, outcome.volumes.multipliers),
                result = null,
            )
        }
    }
}

/** flush_volumes_matrix and flush_multiplier of the project, as the plate keeps them. */
private fun ModelSettings.withFlushVolumes(matrix: List<Double>, multipliers: List<Double>): ModelSettings =
    ModelSettings(
        values + mapOf(
            FLUSH_MATRIX_KEY to matrix.joinToString(",", transform = ::flushValue),
            FLUSH_MULTIPLIER_KEY to multipliers.joinToString(",", transform = ::flushValue),
        ),
    )

private fun flushValue(value: Double): String = String.format(Locale.ROOT, "%.2f", value)

private const val FLUSH_MATRIX_KEY = "flush_volumes_matrix"
private const val FLUSH_MULTIPLIER_KEY = "flush_multiplier"
