package app.orcinus.shadow.domain.plate

import app.orcinus.shadow.core.model.AppConfigOutcome
import app.orcinus.shadow.core.model.ArrangeSettings
import app.orcinus.shadow.core.model.CopyPlacement
import app.orcinus.shadow.core.model.ModelLoadOutcome
import app.orcinus.shadow.core.model.PlateInstanceId
import app.orcinus.shadow.core.model.PlateManipulation
import app.orcinus.shadow.core.model.PlateObject
import app.orcinus.shadow.core.model.PlateProblem
import app.orcinus.shadow.core.model.PlateProblemKind
import app.orcinus.shadow.core.model.PlateState
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.SlicingProfileSelection
import app.orcinus.shadow.core.model.currentArrangeSettings
import app.orcinus.shadow.core.model.mesh
import app.orcinus.shadow.core.model.placing
import app.orcinus.shadow.core.model.plateOf
import app.orcinus.shadow.core.model.withArrangeSettings
import app.orcinus.shadow.core.model.withCutId
import app.orcinus.shadow.core.model.withInstances
import app.orcinus.shadow.domain.placed
import app.orcinus.shadow.slicing.api.AppConfigStore
import app.orcinus.shadow.slicing.api.PlateInspector
import app.orcinus.shadow.storage.api.SceneFiles
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The arrange options window (_render_arrange_menu): the settings every
 * arrangement of the plate takes, those of the process preset's print
 * sequence (GLCanvas3D::get_arrange_settings()), which the app configuration
 * keeps in its [arrange] section under the desktop app's keys.
 */
class SetArrangeSettingsUseCase(
    private val repository: PlateRepository,
    private val store: AppConfigStore? = null,
    private val applicationScope: CoroutineScope? = null,
) {
    operator fun invoke(settings: ArrangeSettings) = apply(settings, savesCalibrationRegion = true)

    /**
     * Its Reset: the default options, aligned to the Y axis on an i3 printer;
     * "Avoid extrusion calibration region" is on again but stays as the app
     * configuration kept it.
     */
    fun reset() {
        val i3 = repository.state.value.presets?.i3Structure == true
        apply(ArrangeSettings(alignToYAxis = i3), savesCalibrationRegion = false)
    }

    /**
     * The window over a printer without the lidar shows no "Avoid extrusion
     * calibration region" and turns it off in the options in use, which the
     * app configuration does not keep.
     */
    fun withoutCalibrationRegion() = repository.update { state ->
        val settings = state.currentArrangeSettings
        if (settings.avoidExtrusionCaliRegion) state.withArrangeSettings(settings.copy(avoidExtrusionCaliRegion = false)) else state
    }

    private fun apply(settings: ArrangeSettings, savesCalibrationRegion: Boolean) {
        var kept: Triple<ArrangeSettings, Boolean, Boolean>? = null
        repository.update { state ->
            val clamped = settings.copy(distance = settings.distance.coerceIn(0.0, MAX_ARRANGE_DISTANCE))
            // Its check box writes the option when it changes it.
            val region = savesCalibrationRegion && clamped.avoidExtrusionCaliRegion != state.currentArrangeSettings.avoidExtrusionCaliRegion
            kept = Triple(clamped, state.presets?.sequentialPrint == true, region)
            state.withArrangeSettings(clamped)
        }
        kept?.let { (saved, sequential, region) -> save(saved, sequential, region) }
    }

    /** GLCanvas3D::load_arrange_settings(): the options the app configuration kept. */
    suspend fun load() {
        val store = store ?: return
        val outcome = store.appConfigValues(LOAD_KEYS, SECTION) as? AppConfigOutcome.Success ?: return
        val values = outcome.values
        fun distance(key: String) = values[key]?.toDoubleOrNull()
        fun flag(key: String) = values[key]?.takeIf { it.isNotEmpty() }?.let { it == "1" || it == "true" }
        repository.update { state ->
            state.copy(
                arrangeSettings = state.arrangeSettings.let {
                    it.copy(
                        distance = distance("min_object_distance_fff") ?: it.distance,
                        enableRotation = flag("enable_rotation_fff") ?: it.enableRotation,
                        allowMultiMaterialsOnSamePlate = flag("allow_multi_materials_on_same_plate") ?: it.allowMultiMaterialsOnSamePlate,
                        avoidExtrusionCaliRegion = flag("avoid_extrusion_cali_region") ?: it.avoidExtrusionCaliRegion,
                    )
                },
                arrangeSettingsSeqPrint = state.arrangeSettingsSeqPrint.let {
                    it.copy(
                        distance = distance("min_object_distance_seq_print_fff") ?: it.distance,
                        enableRotation = flag("enable_rotation_seq_print") ?: it.enableRotation,
                    )
                },
            )
        }
    }

    /**
     * The window writes each option it changes, under its key with the
     * print sequence's postfix (the distance and rotation of by-object
     * printing go under keys load_arrange_settings() does not read, as in
     * the desktop app).
     */
    private fun save(settings: ArrangeSettings, sequential: Boolean, savesCalibrationRegion: Boolean) {
        val store = store ?: return
        val scope = applicationScope ?: return
        val postfix = if (sequential) "_fff_seq_print" else "_fff"
        scope.launch {
            store.setAppConfigValue("min_object_distance$postfix", formatDistance(settings.distance), SECTION)
            store.setAppConfigValue("enable_rotation$postfix", if (settings.enableRotation) "1" else "0", SECTION)
            store.setAppConfigValue("allow_multi_materials_on_same_plate", if (settings.allowMultiMaterialsOnSamePlate) "1" else "0", SECTION)
            store.setAppConfigValue("align_to_y_axis", if (settings.alignToYAxis) "1" else "0", SECTION)
            if (savesCalibrationRegion) {
                store.setAppConfigValue("avoid_extrusion_cali_region", if (settings.avoidExtrusionCaliRegion) "1" else "0", SECTION)
            }
        }
    }

    private companion object {
        /** The arrange options' spacing slider ends at 100 mm. */
        const val MAX_ARRANGE_DISTANCE = 100.0

        const val SECTION = "arrange"

        val LOAD_KEYS = listOf(
            "min_object_distance_fff",
            "min_object_distance_seq_print_fff",
            "enable_rotation_fff",
            "enable_rotation_seq_print",
            "allow_multi_materials_on_same_plate",
            "avoid_extrusion_cali_region",
        )

        /** float_to_string_decimal_point(): the shortest decimal that reads back. */
        fun formatDistance(value: Double): String = value.toFloat().toString().removeSuffix(".0")
    }
}

/**
 * The clone dialog's OK (CloneDialog): Selection::copy_to_clipboard() of the
 * [copies] and paste_from_clipboard() [count] times. Each paste puts a new
 * object for every cloned one into the empty cell of the plate nearest to it
 * and selects them, and with [arrange] ("Auto arrange plate after cloning")
 * the plate is arranged afterwards, as ArrangeJob from the menu arranges it.
 */
class ClonePlateObjectsUseCase(
    private val inspector: PlateInspector,
    private val sceneFiles: SceneFiles,
    private val repository: PlateRepository,
    private val placePlateObjects: PlacePlateObjectsUseCase,
    private val applicationScope: CoroutineScope,
) {
    operator fun invoke(copies: Set<PlateInstanceId>, count: Int, arrange: Boolean) {
        var request: CopyRequest? = null
        repository.update { state ->
            request = null
            val profiles = state.profiles
            // Selection::copy_to_clipboard(): the clones are no part of a cut.
            val sources = state.objects.copiesOf(copies).map { it.withCutId(null) }
            if (state.busy || profiles == null || count <= 0 || sources.isEmpty() || state.objects.any(PlateObject::placing)) {
                return@update state
            }
            request = CopyRequest(state.objects, sources, profiles)
            state.copy(editing = true, problem = null)
        }
        val copy = request ?: return
        applicationScope.launch {
            val added = copyObjects(inspector, sceneFiles, repository, copy, count, CopyPlacement.PASTE) { state, added ->
                // Every paste selects its objects, so the last one's stay selected.
                state.recorded().copy(
                    objects = state.objects + added,
                    selectedInstances = added.takeLast(copy.sources.size).allCopies(),
                    selectedPart = null,
                    selectedRange = null,
                    // G-code sliced before no longer applies once more objects print.
                    result = null,
                ).joiningPlates(added)
            }
            if (added.isNotEmpty() && arrange) {
                placePlateObjects(PlateManipulation.ArrangePlate(repository.state.value.currentArrangeSettings))
            }
        }
    }
}

/**
 * ObjectList::split_instances(), "Set as an individual object": the chosen
 * [instances] of the object with the [mesh] file leave it as one new object
 * (instances_to_separated_object), or, when they are all of its copies, every
 * copy but the first becomes an object of its own, the last one first
 * (instances_to_separated_objects). The object keeps its other copies and is
 * selected, as the object list selects it once their rows are gone.
 */
class SeparatePlateInstancesUseCase(
    private val inspector: PlateInspector,
    private val sceneFiles: SceneFiles,
    private val repository: PlateRepository,
    private val applicationScope: CoroutineScope,
) {
    operator fun invoke(mesh: ScenePath, instances: Set<Int>) {
        var request: CopyRequest? = null
        var kept: PlateObject? = null
        repository.update { state ->
            request = null
            val profiles = state.profiles
            val target = state.objects.withMesh(mesh)
            // Plater::can_set_instance_to_object(): an object with more than one copy.
            if (state.busy || profiles == null || target == null || target.placing || target.instances.size <= 1 ||
                instances.isEmpty() || instances.any { it !in target.instances.indices }
            ) {
                return@update state
            }
            val all = instances.size == target.instances.size
            val sources = if (all) {
                (target.instances.lastIndex downTo 1).map { target.withInstances(listOf(target.instances[it])) }
            } else {
                listOf(target.withInstances(target.instances.filterIndexed { index, _ -> index in instances }))
            }
            kept = target.withInstances(if (all) target.instances.take(1) else target.instances.filterIndexed { index, _ -> index !in instances })
            request = CopyRequest(state.objects, sources, profiles)
            state.copy(editing = true, problem = null)
        }
        val copy = request ?: return
        val remaining = kept ?: return
        applicationScope.launch {
            copyObjects(inspector, sceneFiles, repository, copy, 1, CopyPlacement.KEEP) { state, added ->
                state.recorded().copy(
                    objects = state.objects.replaced(remaining) + added,
                    selectedInstances = listOf(remaining).allCopies(),
                    selectedPart = null,
                    selectedRange = null,
                    result = null,
                )
            }
        }
    }

    /**
     * "Set as an individual object" of the menu over the copy [id]
     * (ObjectList::split_instances() of the selection): the copies of its
     * object the selection holds when it holds that copy and no other
     * object's, every copy of a whole object becoming an object of its own;
     * the copy alone otherwise.
     */
    fun overCopy(id: PlateInstanceId) {
        val selected = repository.state.value.selectedInstances
        val copies = if (id in selected && selected.all { it.mesh == id.mesh }) selected.mapTo(HashSet(), PlateInstanceId::instance) else setOf(id.instance)
        invoke(id.mesh, copies)
    }
}

/**
 * "Fill bed with instances", also the clone dialog's Fill: FillBedJob adds
 * copies of the object with the [mesh] file, modelled on its copy [instance]
 * (null for the whole object), while the free area of the plate holds more,
 * and then arranges the plate.
 */
class FillBedWithInstancesUseCase(
    private val repository: PlateRepository,
    private val placePlateObjects: PlacePlateObjectsUseCase,
    private val applicationScope: CoroutineScope? = null,
) {
    operator fun invoke(mesh: ScenePath, instance: Int? = null) {
        val state = repository.state.value
        val target = state.objects.withMesh(mesh) ?: return
        // Plater::can_increase_instances()
        if (!target.instances.all { it.printable }) return
        // FillBedJob::prepare(): select_plate_by_obj() of the copy (the first
        // for the whole object) makes its plate the current one first.
        val plate = target.instances.getOrNull(instance ?: 0)?.let(state::plateOf)
        val scope = applicationScope
        if (plate == null || plate == state.currentPlate || scope == null || !state.canChangePlates) {
            placePlateObjects(PlateManipulation.FillBed(mesh, instance, state.currentArrangeSettings))
            return
        }
        repository.update { it.withPlates(it.platesLeft(), plate) }
        scope.launch {
            // The engine knows the plate before the job starts on it.
            val ready = repository.state.first { !it.busy }
            if (ready.currentPlate == plate) placePlateObjects(PlateManipulation.FillBed(mesh, instance, ready.currentArrangeSettings))
        }
    }
}

/** What copy_objects() is asked for: the plate as it was, the objects copied, and the presets. */
internal data class CopyRequest(
    val plate: List<PlateObject>,
    val sources: List<PlateObject>,
    val profiles: SlicingProfileSelection,
)

/** The objects of [copies], in the plate's order, each with only its copies among them, in its order. */
internal fun List<PlateObject>.copiesOf(copies: Set<PlateInstanceId>): List<PlateObject> = mapNotNull { plateObject ->
    val taken = plateObject.instances.filterIndexed { index, _ -> PlateInstanceId(plateObject.mesh, index) in copies }
    if (taken.isEmpty()) null else plateObject.withInstances(taken)
}

/**
 * copy_objects() for [request], which ends the edit of the plate begun for it:
 * [apply] takes the new objects, named after the document of the object each
 * was copied from. Returns them, or none when the engine failed, whose message
 * the plate shows.
 */
internal suspend fun copyObjects(
    inspector: PlateInspector,
    sceneFiles: SceneFiles,
    repository: PlateRepository,
    request: CopyRequest,
    count: Int,
    placement: CopyPlacement,
    apply: (PlateState, List<PlateObject>) -> PlateState,
): List<PlateObject> {
    val prefix = sceneFiles.newImportPrefix()
    val outcome = try {
        inspector.copy(request.plate.map { it.placed() }, request.sources.map { it.placed() }, count, placement, request.profiles, prefix)
    } catch (cancellation: CancellationException) {
        sceneFiles.deleteImport(prefix)
        repository.update { it.copy(editing = false) }
        throw cancellation
    } catch (error: Exception) {
        ModelLoadOutcome.Failure(error.message.orEmpty())
    }
    val loaded = (outcome as? ModelLoadOutcome.Success)?.objects.orEmpty()
    if (loaded.isEmpty()) sceneFiles.deleteImport(prefix)
    // The copies come round after round, each in the order of the objects copied.
    val added = loaded.mapIndexed { index, copy ->
        val source = request.sources[index % request.sources.size]
        copy.toPlateObjectOf(source)
    }
    repository.update { state ->
        val done = state.copy(editing = false)
        when {
            outcome is ModelLoadOutcome.Failure -> done.copy(problem = PlateProblem(PlateProblemKind.PLACEMENT_FAILED, outcome.message))
            added.isNotEmpty() -> apply(done, added)
            else -> done
        }
    }
    return added
}
