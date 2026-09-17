package app.orcinus.shadow.slicing.api

import app.orcinus.shadow.core.model.EngineStatus
import app.orcinus.shadow.core.model.FlatteningPlanesOutcome
import app.orcinus.shadow.core.model.Manipulation
import app.orcinus.shadow.core.model.ModelInspectionOutcome
import app.orcinus.shadow.core.model.ModelSource
import app.orcinus.shadow.core.model.PlacedModel
import app.orcinus.shadow.core.model.PlateDescriptionOutcome
import app.orcinus.shadow.core.model.PlateInspectionOutcome
import app.orcinus.shadow.core.model.PlateManipulation
import app.orcinus.shadow.core.model.PresetChoice
import app.orcinus.shadow.core.model.PresetsOutcome
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.SetupFilamentsOutcome
import app.orcinus.shadow.core.model.SetupPrintersOutcome
import app.orcinus.shadow.core.model.SliceJobId
import app.orcinus.shadow.core.model.SliceOutcome
import app.orcinus.shadow.core.model.SliceProgress
import app.orcinus.shadow.core.model.SliceRequest
import app.orcinus.shadow.core.model.SlicingProfileSelection
import app.orcinus.shadow.core.model.Transform3

fun interface SliceProgressListener {
    fun onProgress(progress: SliceProgress)
}

/**
 * Stable port into a slicing engine. No OrcaSlicer, JNI, or Android type may
 * appear in this contract. Implementations may run the engine in another
 * process, so every call can suspend.
 */
interface SlicerEngine {
    /** Prepares the engine on first use and reports whether it can slice. */
    suspend fun status(): EngineStatus

    /**
     * Suspends until the job ends. Cancelling the calling coroutine cancels
     * the job. Progress may arrive on any thread.
     */
    suspend fun slice(
        request: SliceRequest,
        progressListener: SliceProgressListener,
    ): SliceOutcome

    /** Returns true only when the active job accepted the cancellation. */
    suspend fun cancel(jobId: SliceJobId): Boolean
}

/**
 * The plate and its models as the engine sees them, for the 3D view: the plate
 * of the selected printer, and models placed on it the way slicing places them.
 * Geometry is written into files the caller chooses.
 */
interface PlateInspector {
    /** Describes the plate and writes its bed model and texture into [directory]. */
    suspend fun describePlate(profiles: SlicingProfileSelection, directory: ScenePath): PlateDescriptionOutcome

    /**
     * Loads [model], writes its mesh to [mesh], and places it as OrcaSlicer
     * places an object added to the plate that already holds [plate].
     */
    suspend fun inspect(
        model: ModelSource,
        profiles: SlicingProfileSelection,
        mesh: ScenePath,
        plate: List<PlacedModel>,
    ): ModelInspectionOutcome

    /**
     * Commits [manipulation] of [model], whose mesh is in [mesh], from
     * [previous] to the instance transformation [placement], as OrcaSlicer
     * does; with [autoDrop] off the object is never moved onto the plate.
     * Reports the placed object.
     */
    suspend fun place(
        model: ModelSource,
        profiles: SlicingProfileSelection,
        mesh: ScenePath,
        previous: Transform3,
        placement: Transform3,
        autoDrop: Boolean,
        manipulation: Manipulation,
    ): ModelInspectionOutcome

    /** Commits [manipulation] of the objects on the plate, [plate], and reports every object as placed. */
    suspend fun placeObjects(
        plate: List<PlacedModel>,
        profiles: SlicingProfileSelection,
        manipulation: PlateManipulation,
    ): PlateInspectionOutcome

    /** The faces [model], whose mesh is in [mesh], can lie on with the instance transformation [placement]. */
    suspend fun flatteningPlanes(
        model: ModelSource,
        profiles: SlicingProfileSelection,
        mesh: ScenePath,
        placement: Transform3,
    ): FlatteningPlanesOutcome
}

/**
 * OrcaSlicer's presets as its sidebar and Setup Wizard offer them. The engine
 * keeps the installed printers and filaments and the selection in its app
 * configuration, which outlives the process.
 */
interface PresetManager {
    /** The presets the sidebar offers for the selection the engine remembers. */
    suspend fun presets(): PresetsOutcome

    /** Selects a preset as the sidebar does and remembers the selection. */
    suspend fun selectPreset(choice: PresetChoice): PresetsOutcome

    /** Every printer model the Setup Wizard offers. */
    suspend fun setupPrinters(): SetupPrintersOutcome

    /** The filaments the Setup Wizard offers for the printer models with the ids [models]. */
    suspend fun setupFilaments(models: List<String>): SetupFilamentsOutcome

    /**
     * The Setup Wizard's Finish: installs the printer models with the ids
     * [models], each with all its nozzle diameters, and the [filaments] in place
     * of the installed ones, and selects the printer it added first.
     */
    suspend fun applySetup(models: List<String>, filaments: List<String>): PresetsOutcome

    /** The Setup Wizard closed while no printer is installed: OrcaSlicer's default printer and filament. */
    suspend fun applyDefaultSetup(): PresetsOutcome
}
