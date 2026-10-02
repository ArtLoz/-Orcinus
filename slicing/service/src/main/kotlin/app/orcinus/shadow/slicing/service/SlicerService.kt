package app.orcinus.shadow.slicing.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import app.orcinus.shadow.core.model.BedShape
import app.orcinus.shadow.core.model.BedShapeKind
import app.orcinus.shadow.core.model.ComparedPresets
import app.orcinus.shadow.core.model.ConfigExportKind
import app.orcinus.shadow.core.model.ConfigOverwriteAnswer
import app.orcinus.shadow.core.model.CopyPlacement
import app.orcinus.shadow.core.model.CreateFilamentRequest
import app.orcinus.shadow.core.model.CreatePrinterRequest
import app.orcinus.shadow.core.model.CutGroove
import app.orcinus.shadow.core.model.CutPartSelection
import app.orcinus.shadow.core.model.FilamentPresetChoice
import app.orcinus.shadow.core.model.FlowRateCalibration
import app.orcinus.shadow.core.model.FlushVolumesChange
import app.orcinus.shadow.core.model.MeshExportOutcome
import app.orcinus.shadow.core.model.MeshFormat
import app.orcinus.shadow.core.model.ModelLoad
import app.orcinus.shadow.core.model.ModelPath
import app.orcinus.shadow.core.model.ModelSettings
import app.orcinus.shadow.core.model.ModelSettingsOutcome
import app.orcinus.shadow.core.model.ObjectEdit
import app.orcinus.shadow.core.model.PaintKind
import app.orcinus.shadow.core.model.PaintStroke
import app.orcinus.shadow.core.model.PaintTool
import app.orcinus.shadow.core.model.PaintedFacets
import app.orcinus.shadow.core.model.Point2
import app.orcinus.shadow.core.model.PresetChangeAction
import app.orcinus.shadow.core.model.PresetKind
import app.orcinus.shadow.core.model.ProfileId
import app.orcinus.shadow.core.model.ProjectSaveOutcome
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.SettingsMode
import app.orcinus.shadow.core.model.SimplifyConfig
import app.orcinus.shadow.core.model.SimplifyOutcome
import app.orcinus.shadow.core.model.SliceFailureCode
import app.orcinus.shadow.core.model.SliceJobId
import app.orcinus.shadow.core.model.SliceOutcome
import app.orcinus.shadow.core.model.SliceRequest
import app.orcinus.shadow.core.model.ThumbnailImage
import app.orcinus.shadow.core.model.ThumbnailSize
import app.orcinus.shadow.core.model.Transform3
import app.orcinus.shadow.core.model.Vector3
import app.orcinus.shadow.core.model.VolumeType
import app.orcinus.shadow.core.model.cutConnectors
import app.orcinus.shadow.slicing.api.AppConfigStore
import app.orcinus.shadow.slicing.api.PlateInspector
import app.orcinus.shadow.slicing.api.PresetManager
import app.orcinus.shadow.slicing.api.PresetSettingsEditor
import app.orcinus.shadow.slicing.api.SlicerEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Hosts a slicing engine in its own process, so a crash in native code ends
 * only that process. The app declares a subclass with `android:process` and
 * `foregroundServiceType="specialUse"`; UI code talks to it through
 * [RemoteSlicerEngine].
 *
 * While a job runs the service is started and in the foreground with a progress
 * notification, so slicing continues when the app leaves the screen. It stops
 * itself when the job ends.
 */
abstract class SlicerService<E> : Service() where E : SlicerEngine, E : PlateInspector, E : PresetManager, E : PresetSettingsEditor, E : AppConfigStore {
    private val engine: E by lazy { createEngine() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val jobLock = Any()
    private var activeJobId: SliceJobId? = null
    private lateinit var notifications: SliceNotifications

    /** Creates the engine hosted by this process; called once, on first use. */
    protected abstract fun createEngine(): E

    override fun onCreate() {
        super.onCreate()
        notifications = SliceNotifications(this, javaClass)
        // When the previous process died during a job and Android recreated the
        // bound service before the client dropped the binding, the service is
        // still marked foreground with that job's notification. A new process
        // never has a job.
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            intent.getStringExtra(EXTRA_JOB_ID)?.let { jobId ->
                scope.launch { engine.cancel(SliceJobId(jobId)) }
            }
        }
        synchronized(jobLock) {
            if (activeJobId == null) stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private val binder = object : ISlicerService.Stub() {
        override fun status(): EngineStatusParcel = runBlocking { engine.status() }.toParcel()

        override fun describePlate(profiles: ProfilesParcel, directory: String): PlateDescriptionParcel =
            runBlocking { engine.describePlate(profiles.toProfiles(), ScenePath(directory)) }.toParcel()

        override fun inspect(model: ModelSourceParcel, profiles: ProfilesParcel, meshPath: String, plate: Array<PlacedModelParcel>): InspectionParcel =
            runBlocking { engine.inspect(model.toModelSource(), profiles.toProfiles(), ScenePath(meshPath), plate.toPlacedModels()) }.toParcel()

        override fun load(
            sources: Array<String>,
            profiles: ProfilesParcel,
            plate: Array<PlacedModelParcel>,
            prefix: String,
            answerIds: Array<String>,
            answers: BooleanArray,
            load: String,
            chosen: Boolean,
            stepMeshes: DoubleArray,
            askMulti: Boolean,
        ): ModelLoadParcel = runBlocking {
            engine.load(
                sources.map(::ModelPath),
                profiles.toProfiles(),
                plate.toPlacedModels(),
                ScenePath(prefix),
                answerIds.zip(answers.toList()).toMap(),
                ModelLoad.valueOf(load),
                chosen,
                stepMeshes.toStepMeshes(),
                askMulti,
            )
        }.toParcel()

        override fun edit(
            plate: Array<PlacedModelParcel>,
            index: Int,
            edit: String,
            volume: Int,
            profiles: ProfilesParcel,
            prefix: String,
            answerIds: Array<String>,
            answers: BooleanArray,
            cut: CutParcel?,
        ): ModelLoadParcel = runBlocking {
            engine.edit(
                plate.toPlacedModels(),
                index,
                ObjectEdit.valueOf(edit),
                volume.takeIf { it >= 0 },
                profiles.toProfiles(),
                ScenePath(prefix),
                answerIds.zip(answers.toList()).toMap(),
                cut?.toCut(),
            )
        }.toParcel()

        override fun beginCut(plateObject: PlacedModelParcel, instance: Int, profiles: ProfilesParcel): CutObjectParcel = runBlocking {
            engine.beginCut(arrayOf(plateObject).toPlacedModels().first(), instance, profiles.toProfiles())
        }.toParcel()

        override fun describeCutPlane(
            plane: DoubleArray,
            connectorValues: DoubleArray,
            connectorKinds: IntArray,
            snapSpace: Double,
            snapBulge: Double,
            dovetail: Boolean,
            groove: DoubleArray,
            preview: Boolean,
            meshPrefix: String,
            partsPlane: DoubleArray,
            parts: BooleanArray,
        ): CutPlaneParcel = runBlocking {
            engine.describeCutPlane(
                Transform3(plane.toList()),
                cutConnectors(connectorValues, connectorKinds),
                snapSpace,
                snapBulge,
                CutGroove.of(groove).takeIf { dovetail },
                preview,
                cutParts(partsPlane, parts),
                ScenePath(meshPrefix),
            )
        }.toParcel()

        override fun selectCutPart(
            partsPlane: DoubleArray,
            selected: BooleanArray,
            origin: DoubleArray,
            direction: DoubleArray,
            meshPrefix: String,
        ): CutPartsParcel = runBlocking {
            engine.selectCutPart(
                CutPartSelection(Transform3(partsPlane.toList()), selected.toList()),
                Vector3(origin[0], origin[1], origin[2]),
                Vector3(direction[0], direction[1], direction[2]),
                ScenePath(meshPrefix),
            )
        }.toParcel()

        override fun endCut() = runBlocking { engine.endCut() }

        override fun saveProject(
            path: String,
            plate: Array<PlacedModelParcel>,
            profiles: ProfilesParcel,
            plates: Array<ProjectPlateParcel>,
            projectInfo: String?,
        ): String? = runBlocking {
            engine.saveProject(
                ScenePath(path),
                plate.toPlacedModels(),
                profiles.toProfiles(),
                plates.map { it.toProjectPlate() },
                projectInfo?.let(::ScenePath),
            )
        }.let { outcome -> (outcome as? ProjectSaveOutcome.Failure)?.message }

        override fun exportMesh(
            plate: Array<PlacedModelParcel>,
            index: Int,
            format: String,
            profiles: ProfilesParcel,
            path: String,
        ): MeshExportParcel = runBlocking {
            engine.exportMesh(plate.toPlacedModels(), index, MeshFormat.valueOf(format), profiles.toProfiles(), ScenePath(path))
        }.let { outcome ->
            MeshExportParcel().also {
                when (outcome) {
                    is MeshExportOutcome.Failure -> it.error = outcome.message
                    is MeshExportOutcome.Success -> it.warning = outcome.warning
                }
            }
        }

        override fun simplifyVolume(
            plate: Array<PlacedModelParcel>,
            index: Int,
            volume: Int,
            useCount: Boolean,
            wantedCount: Int,
            decimateRatio: Float,
            maxError: Float,
            profiles: ProfilesParcel,
            path: String,
        ): SimplifyParcel = runBlocking {
            engine.simplifyVolume(
                plate.toPlacedModels(),
                index,
                volume,
                SimplifyConfig(useCount, decimateRatio, wantedCount, maxError),
                profiles.toProfiles(),
                ScenePath(path),
            )
        }.let { outcome ->
            SimplifyParcel().also {
                when (outcome) {
                    is SimplifyOutcome.Failure -> it.error = outcome.message
                    is SimplifyOutcome.Success -> {
                        it.triangles = outcome.triangles
                        it.original = outcome.original
                    }
                }
            }
        }

        override fun setVolumeType(
            plate: Array<PlacedModelParcel>,
            index: Int,
            volume: Int,
            type: String,
            profiles: ProfilesParcel,
            prefix: String,
        ): ModelLoadParcel = runBlocking {
            engine.setVolumeType(plate.toPlacedModels(), index, volume, VolumeType.valueOf(type), profiles.toProfiles(), ScenePath(prefix))
        }.toParcel()

        override fun applySimplify(
            plate: Array<PlacedModelParcel>,
            index: Int,
            volume: Int,
            useCount: Boolean,
            wantedCount: Int,
            decimateRatio: Float,
            maxError: Float,
            profiles: ProfilesParcel,
            prefix: String,
        ): ModelLoadParcel = runBlocking {
            engine.applySimplify(
                plate.toPlacedModels(),
                index,
                volume,
                SimplifyConfig(useCount, decimateRatio, wantedCount, maxError),
                profiles.toProfiles(),
                ScenePath(prefix),
            )
        }.toParcel()

        override fun replaceVolume(
            plate: Array<PlacedModelParcel>,
            index: Int,
            volume: Int,
            source: String,
            profiles: ProfilesParcel,
            prefix: String,
            stepMesh: DoubleArray?,
        ): ModelLoadParcel = runBlocking {
            engine.replaceVolume(plate.toPlacedModels(), index, volume, ModelPath(source), profiles.toProfiles(), ScenePath(prefix), stepMesh?.toStepMeshOptions())
        }.toParcel()

        override fun stepTriangleCount(source: String, linear: Double, angle: Double): Long =
            runBlocking { engine.stepTriangleCount(ModelPath(source), linear, angle) }

        override fun stopStepTriangleCount() = runBlocking { engine.stopStepTriangleCount() }

        override fun releaseStepFile() = runBlocking { engine.releaseStepFile() }

        override fun addPrimitive(
            plate: Array<PlacedModelParcel>,
            shape: String,
            name: String,
            profiles: ProfilesParcel,
            prefix: String,
        ): ModelLoadParcel = runBlocking {
            engine.addPrimitive(plate.toPlacedModels(), shape, name, profiles.toProfiles(), ScenePath(prefix))
        }.toParcel()

        override fun handyModel(file: String): String? = runBlocking { engine.handyModel(file) }?.value

        override fun prepareCalibration(params: CalibrationParcel, profiles: ProfilesParcel, prefix: String): ModelLoadParcel = runBlocking {
            engine.prepareCalibration(params.toCalibrationParams(), profiles.toProfiles(), ScenePath(prefix))
        }.toParcel()

        override fun describeCalibrationPrinter(profiles: ProfilesParcel): CalibrationPrinterParcel = runBlocking {
            engine.describeCalibrationPrinter(profiles.toProfiles())
        }.toParcel()

        override fun prepareFlowRateCalibration(
            linear: Boolean,
            pass: Int,
            topSurfacePattern: String,
            profiles: ProfilesParcel,
            prefix: String,
        ): ModelLoadParcel = runBlocking {
            engine.prepareFlowRateCalibration(FlowRateCalibration(linear, pass, topSurfacePattern), profiles.toProfiles(), ScenePath(prefix))
        }.toParcel()

        override fun pasteVolumes(
            plate: Array<PlacedModelParcel>,
            index: Int,
            instance: Int,
            source: PlacedModelParcel,
            volumes: IntArray,
            sameInputFile: Boolean,
            profiles: ProfilesParcel,
            prefix: String,
        ): ModelLoadParcel = runBlocking {
            engine.pasteVolumes(
                plate.toPlacedModels(),
                index,
                instance,
                arrayOf(source).toPlacedModels().single(),
                volumes.toList(),
                sameInputFile,
                profiles.toProfiles(),
                ScenePath(prefix),
            )
        }.toParcel()

        override fun copy(
            plate: Array<PlacedModelParcel>,
            sources: Array<PlacedModelParcel>,
            count: Int,
            placement: String,
            profiles: ProfilesParcel,
            prefix: String,
        ): ModelLoadParcel = runBlocking {
            engine.copy(
                plate.toPlacedModels(),
                sources.toPlacedModels(),
                count,
                CopyPlacement.valueOf(placement),
                profiles.toProfiles(),
                ScenePath(prefix),
            )
        }.toParcel()

        override fun place(
            plateObject: PlacedModelParcel,
            profiles: ProfilesParcel,
            previous: DoubleArray,
            placement: DoubleArray,
            autoDrop: Boolean,
            manipulation: String,
            faceNormal: DoubleArray?,
        ): InspectionParcel = runBlocking {
            engine.place(
                arrayOf(plateObject).toPlacedModels().first(),
                profiles.toProfiles(),
                Transform3(previous.toList()),
                Transform3(placement.toList()),
                autoDrop,
                manipulationOf(manipulation, faceNormal),
            )
        }.toParcel()

        override fun placeObjects(
            plate: Array<PlacedModelParcel>,
            profiles: ProfilesParcel,
            manipulation: String,
            selected: Array<String>,
            arrangeSettings: ArrangeSettingsParcel?,
            instance: Int,
            lockedPlates: IntArray,
        ): PlateInspectionParcel = runBlocking {
            engine.placeObjects(
                plate.toPlacedModels(),
                profiles.toProfiles(),
                plateManipulationOf(manipulation, selected, arrangeSettings, instance, lockedPlates),
            )
        }.toParcel()

        override fun updateFlushVolumes(
            plate: Array<PlacedModelParcel>,
            profiles: ProfilesParcel,
            plateSettings: ModelSettingsParcel,
            change: String,
            index: Int,
        ): FlushVolumesParcel = runBlocking {
            engine.updateFlushVolumes(
                plate.toPlacedModels(),
                profiles.toProfiles(),
                plateSettings.toModelSettings(),
                FlushVolumesChange.valueOf(change),
                index,
            )
        }.toParcel()

        override fun describeFlushVolumes(
            plate: Array<PlacedModelParcel>,
            profiles: ProfilesParcel,
            plateSettings: ModelSettingsParcel,
        ): FlushVolumesParcel = runBlocking {
            engine.describeFlushVolumes(plate.toPlacedModels(), profiles.toProfiles(), plateSettings.toModelSettings())
        }.toParcel()

        override fun beginPainting(
            plateObject: PlacedModelParcel,
            part: Int,
            kind: String,
            profiles: ProfilesParcel,
            facets: String,
            meshPrefix: String,
        ): PaintingParcel = runBlocking {
            engine.beginPainting(
                plateObject = arrayOf(plateObject).toPlacedModels().first(),
                part = part.takeIf { it >= 0 },
                kind = PaintKind.valueOf(kind),
                profiles = profiles.toProfiles(),
                facets = PaintedFacets(facets),
                meshPrefix = ScenePath(meshPrefix),
            )
        }.toParcel()

        override fun fuzzySkinDisabled(plateObject: PlacedModelParcel, profiles: ProfilesParcel): Boolean = runBlocking {
            engine.fuzzySkinDisabled(arrayOf(plateObject).toPlacedModels().first(), profiles.toProfiles())
        }

        override fun paintStroke(
            origin: DoubleArray,
            direction: DoubleArray,
            state: Int,
            radius: Double,
            tool: String,
            angle: Double,
            overhangAngle: Double,
            starts: Boolean,
            meshPrefix: String,
        ): PaintingParcel = runBlocking {
            engine.paint(
                PaintStroke(
                    origin = Vector3(origin[0], origin[1], origin[2]),
                    direction = Vector3(direction[0], direction[1], direction[2]),
                    state = state,
                    radius = radius,
                    tool = PaintTool.valueOf(tool),
                    angle = angle,
                    overhangAngle = overhangAngle,
                    startsStroke = starts,
                ),
                ScenePath(meshPrefix),
            )
        }.toParcel()

        override fun undoPainting(meshPrefix: String): PaintingParcel = runBlocking { engine.undoPainting(ScenePath(meshPrefix)) }.toParcel()

        override fun redoPainting(meshPrefix: String): PaintingParcel = runBlocking { engine.redoPainting(ScenePath(meshPrefix)) }.toParcel()

        override fun clearPainting(meshPrefix: String): PaintingParcel = runBlocking { engine.clearPainting(ScenePath(meshPrefix)) }.toParcel()

        override fun setGapFill(gapArea: Double, meshPrefix: String): PaintingParcel = runBlocking {
            engine.setGapFill(gapArea.takeIf { it >= 0.0 }, ScenePath(meshPrefix))
        }.toParcel()

        override fun fillGaps(meshPrefix: String): PaintingParcel = runBlocking { engine.fillGaps(ScenePath(meshPrefix)) }.toParcel()

        override fun endPainting(): PaintingParcel = runBlocking { engine.endPainting() }.toParcel()

        override fun describeWipeTower(
            plate: Array<PlacedModelParcel>,
            profiles: ProfilesParcel,
            plateSettings: ModelSettingsParcel,
        ): WipeTowerParcel = runBlocking {
            engine.describeWipeTower(plate.toPlacedModels(), profiles.toProfiles(), plateSettings.toModelSettings())
        }.toParcel()

        override fun flatteningPlanes(plateObject: PlacedModelParcel, profiles: ProfilesParcel, placement: DoubleArray): FlatteningPlanesParcel =
            runBlocking {
                engine.flatteningPlanes(arrayOf(plateObject).toPlacedModels().first(), profiles.toProfiles(), Transform3(placement.toList()))
            }.toParcel()

        override fun addObjectPart(
            plateObject: PlacedModelParcel,
            shape: String,
            type: String,
            profiles: ProfilesParcel,
            meshPath: String,
        ): InspectionParcel = runBlocking {
            engine.addPart(
                arrayOf(plateObject).toPlacedModels().first(),
                shape,
                VolumeType.valueOf(type),
                profiles.toProfiles(),
                ScenePath(meshPath),
            )
        }.toParcel()

        override fun presets(): PresetsParcel = runBlocking { engine.presets() }.toParcel()

        override fun dirtyPresets(): DirtyPresetsParcel = runBlocking { engine.dirtyPresets() }.toParcel()

        override fun discardPresetChanges(): PresetsParcel = runBlocking { engine.discardPresetChanges() }.toParcel()

        override fun resetProjectPresets(): PresetsParcel = runBlocking { engine.resetProjectPresets() }.toParcel()

        override fun selectPreset(kind: String, value: String, action: String): PresetsParcel =
            runBlocking { engine.selectPreset(presetChoiceOf(kind, value), PresetChangeAction.valueOf(action)) }.toParcel()

        override fun setupPrinters(): SetupPrintersParcel = runBlocking { engine.setupPrinters() }.toParcel()

        override fun setupFilaments(models: Array<String>): SetupFilamentsParcel =
            runBlocking { engine.setupFilaments(models.toList()) }.toParcel()

        override fun applySetup(models: Array<String>, filaments: Array<String>): PresetsParcel =
            runBlocking { engine.applySetup(models.toList(), filaments.toList()) }.toParcel()

        override fun applyDefaultSetup(): PresetsParcel = runBlocking { engine.applyDefaultSetup() }.toParcel()

        override fun settingsTab(kind: String): SettingsTabParcel = runBlocking { engine.settingsTab(PresetKind.valueOf(kind)) }.toParcel()

        override fun settings(
            kind: String,
            page: String,
            answerIds: Array<String>,
            answers: BooleanArray,
            models: Array<ModelSettingsParcel>?,
            plate: ModelSettingsParcel?,
            parent: ModelSettingsParcel?,
        ): PresetSettingsParcel =
            runBlocking { engine.settings(PresetKind.valueOf(kind), page, answersOf(answerIds, answers), modelRequestOf(models, plate, parent)) }.toParcel()

        override fun changeSetting(
            kind: String,
            page: String,
            id: String,
            text: String,
            answerIds: Array<String>,
            answers: BooleanArray,
            models: Array<ModelSettingsParcel>?,
            plate: ModelSettingsParcel?,
            parent: ModelSettingsParcel?,
        ): PresetSettingsParcel = runBlocking {
            engine.changeSetting(PresetKind.valueOf(kind), page, id, text, answersOf(answerIds, answers), modelRequestOf(models, plate, parent))
        }.toParcel()

        override fun pasteModelSettings(
            clipboard: ModelSettingsParcel,
            target: ModelSettingsParcel,
            parent: ModelSettingsParcel?,
        ): ModelSettingsOutcomeParcel = runBlocking {
            engine.pasteModelSettings(clipboard.toModelSettings(), target.toModelSettings(), parent?.toModelSettings())
        }.let { outcome ->
            ModelSettingsOutcomeParcel().also {
                when (outcome) {
                    is ModelSettingsOutcome.Failure -> it.error = outcome.message
                    is ModelSettingsOutcome.Success -> it.settings = outcome.settings.toParcel()
                }
            }
        }

        override fun resetSettings(
            kind: String,
            page: String,
            ids: Array<String>,
            answerIds: Array<String>,
            answers: BooleanArray,
            models: Array<ModelSettingsParcel>?,
            plate: ModelSettingsParcel?,
            parent: ModelSettingsParcel?,
        ): PresetSettingsParcel = runBlocking {
            engine.resetSettings(PresetKind.valueOf(kind), page, ids.toList(), answersOf(answerIds, answers), modelRequestOf(models, plate, parent))
        }.toParcel()

        override fun setSettingOverride(
            kind: String,
            page: String,
            id: String,
            enabled: Boolean,
            answerIds: Array<String>,
            answers: BooleanArray,
        ): PresetSettingsParcel =
            runBlocking { engine.setSettingOverride(PresetKind.valueOf(kind), page, id, enabled, answersOf(answerIds, answers)) }.toParcel()

        override fun setCompatiblePresets(
            kind: String,
            page: String,
            key: String,
            presets: Array<String>,
            answerIds: Array<String>,
            answers: BooleanArray,
        ): PresetSettingsParcel = runBlocking {
            engine.setCompatiblePresets(PresetKind.valueOf(kind), page, key, presets.toList(), answersOf(answerIds, answers))
        }.toParcel()

        override fun addFilament(): PresetsParcel = runBlocking { engine.addFilament() }.toParcel()

        override fun removeFilament(index: Int): PresetsParcel = runBlocking { engine.removeFilament(index) }.toParcel()

        override fun selectFilament(index: Int, name: String, action: String): PresetsParcel =
            runBlocking { engine.selectFilament(index, ProfileId(name), PresetChangeAction.valueOf(action)) }.toParcel()

        override fun setFilamentColor(index: Int, color: String): PresetsParcel =
            runBlocking { engine.setFilamentColor(index, color) }.toParcel()

        override fun printerConnection(): PrinterConnectionParcel = runBlocking { engine.printerConnection() }.toParcel()

        override fun savePrinterConnection(keys: Array<String>, values: Array<String>, name: String): PresetSettingsParcel =
            runBlocking { engine.savePrinterConnection(ModelSettings(keys.indices.filter { it < values.size }.associate { keys[it] to values[it] }), name) }
                .toParcel()

        override fun importPresets(paths: Array<String>, answerPresets: Array<String>, answers: LongArray): ConfigTransferParcel =
            runBlocking {
                engine.importPresets(
                    paths.toList(),
                    answerPresets.mapIndexed { index, preset ->
                        preset to ConfigOverwriteAnswer.entries[answers.getOrElse(index) { 0 }.toInt()]
                    }.toMap(),
                )
            }.toParcel()

        override fun createFilamentOptions(type: String, baseFilament: String): CreateFilamentOptionsParcel =
            runBlocking { engine.createFilamentOptions(type, baseFilament) }.toParcel()

        override fun createFilament(
            vendor: String,
            customVendor: Boolean,
            type: String,
            serial: String,
            printers: Array<String>,
            presets: Array<String>,
            answerIds: Array<String>,
            answers: BooleanArray,
        ): PresetCreationParcel = runBlocking {
            engine.createFilament(
                CreateFilamentRequest(
                    vendor = vendor,
                    customVendor = customVendor,
                    type = type,
                    serial = serial,
                    presets = printers.mapIndexedNotNull { index, printer ->
                        presets.getOrNull(index)?.let { FilamentPresetChoice(printer, it) }
                    },
                ),
                answersOf(answerIds, answers),
            )
        }.toParcel()

        override fun createPrinterOptions(
            vendor: String,
            nozzle: String,
            presetVendor: String,
            printerPreset: String,
        ): CreatePrinterOptionsParcel = runBlocking { engine.createPrinterOptions(vendor, nozzle, presetVendor, printerPreset) }.toParcel()

        override fun createPrinter(
            model: String,
            nozzle: String,
            printableArea: DoubleArray,
            maxPrintHeight: Double,
            customTexture: String,
            customModel: String,
            presetVendor: String,
            printerPreset: String,
            filamentPresets: Array<String>,
            processPresets: Array<String>,
            answerIds: Array<String>,
            answers: BooleanArray,
        ): PresetCreationParcel = runBlocking {
            engine.createPrinter(
                CreatePrinterRequest(
                    model = model,
                    nozzle = nozzle,
                    printableArea = (printableArea.indices step 2).mapNotNull { index ->
                        printableArea.getOrNull(index + 1)?.let { Point2(printableArea[index], it) }
                    },
                    maxPrintHeight = maxPrintHeight,
                    customTexture = customTexture,
                    customModel = customModel,
                    presetVendor = presetVendor,
                    printerPreset = printerPreset,
                    filamentPresets = filamentPresets.toList(),
                    processPresets = processPresets.toList(),
                ),
                answersOf(answerIds, answers),
            )
        }.toParcel()

        override fun customFilaments(): CustomFilamentsParcel = runBlocking { engine.customFilaments() }.toParcel()

        override fun filamentPresets(filamentId: String): FilamentPresetsParcel =
            runBlocking { engine.filamentPresets(filamentId) }.toParcel()

        override fun deleteFilamentPreset(preset: String, answerIds: Array<String>, answers: BooleanArray): PresetCreationParcel =
            runBlocking { engine.deleteFilamentPreset(preset, answersOf(answerIds, answers)) }.toParcel()

        override fun configExportOptions(kind: String): ConfigExportOptionsParcel =
            runBlocking { engine.configExportOptions(ConfigExportKind.valueOf(kind)) }.toParcel()

        override fun exportConfigs(kind: String, names: Array<String>, directory: String): ConfigTransferParcel =
            runBlocking { engine.exportConfigs(ConfigExportKind.valueOf(kind), names.toList(), directory) }.toParcel()

        override fun comparePresets(left: Array<String>, right: Array<String>, showAll: Boolean): PresetComparisonParcel =
            runBlocking { engine.comparePresets(left.toCompared(), right.toCompared(), showAll) }.toParcel()

        /** The order the service passes the presets of a side in. */
        private fun Array<String>.toCompared() = ComparedPresets(
            printer = getOrElse(0) { "" },
            print = getOrElse(1) { "" },
            filament = getOrElse(2) { "" },
        )

        override fun transferPresetOptions(kind: String, from: String, to: String, options: Array<String>): PresetsParcel =
            runBlocking { engine.transferPresetOptions(PresetKind.valueOf(kind), from, to, options.toList()) }.toParcel()

        override fun searchCatalog(): SearchCatalogParcel = runBlocking { engine.searchCatalog() }.toParcel()

        override fun gcodePlaceholders(kind: String, key: String): GcodePlaceholdersParcel =
            runBlocking { engine.gcodePlaceholders(PresetKind.valueOf(kind), key) }.toParcel()

        override fun gcodePlaceholder(key: String, presets: Boolean): GcodePlaceholderInfoParcel =
            runBlocking { engine.gcodePlaceholder(key, presets) }.toParcel()

        override fun editCustomGcode(
            kind: String,
            page: String,
            key: String,
            value: String,
            answerIds: Array<String>,
            answers: BooleanArray,
        ): PresetSettingsParcel = runBlocking {
            engine.editCustomGcode(PresetKind.valueOf(kind), page, key, value, answersOf(answerIds, answers))
        }.toParcel()

        override fun setRammingParameters(
            kind: String,
            page: String,
            parameters: String,
            answerIds: Array<String>,
            answers: BooleanArray,
        ): PresetSettingsParcel = runBlocking {
            engine.setRammingParameters(PresetKind.valueOf(kind), page, parameters, answersOf(answerIds, answers))
        }.toParcel()

        override fun bedShape(): BedShapeParcel = runBlocking { engine.bedShape() }.toParcel()

        override fun setBedShape(
            kind: String,
            sizeX: Double,
            sizeY: Double,
            originX: Double,
            originY: Double,
            diameter: Double,
            customPath: String?,
            texture: String,
            model: String,
            answerIds: Array<String>,
            answers: BooleanArray,
        ): PresetSettingsParcel = runBlocking {
            engine.setBedShape(
                shape = BedShape(
                    kind = BedShapeKind.valueOf(kind),
                    sizeX = sizeX,
                    sizeY = sizeY,
                    originX = originX,
                    originY = originY,
                    diameter = diameter,
                    texture = texture,
                    model = model,
                ),
                customPath = customPath?.let(::ModelPath),
                answers = answersOf(answerIds, answers),
            )
        }.toParcel()

        override fun compatiblePresetChoices(kind: String, key: String): PresetNamesParcel =
            runBlocking { engine.compatiblePresetChoices(PresetKind.valueOf(kind), key) }.toParcel()

        override fun setSettingsMode(
            kind: String,
            mode: String,
            models: Array<ModelSettingsParcel>?,
            plate: ModelSettingsParcel?,
            parent: ModelSettingsParcel?,
        ): PresetSettingsParcel = runBlocking {
            engine.setSettingsMode(PresetKind.valueOf(kind), SettingsMode.valueOf(mode), modelRequestOf(models, plate, parent))
        }.toParcel()

        override fun setSettingsVariant(
            kind: String,
            page: String,
            variant: Int,
            answerIds: Array<String>,
            answers: BooleanArray,
            models: Array<ModelSettingsParcel>?,
            plate: ModelSettingsParcel?,
            parent: ModelSettingsParcel?,
        ): PresetSettingsParcel = runBlocking {
            engine.setSettingsVariant(
                PresetKind.valueOf(kind),
                page,
                variant,
                answersOf(answerIds, answers),
                modelRequestOf(models, plate, parent),
            )
        }.toParcel()

        override fun settingTooltip(kind: String, id: String): Array<OrcaTextParcel> =
            runBlocking { engine.settingTooltip(PresetKind.valueOf(kind), id) }.toParcels()

        override fun checkPresetName(kind: String, name: String): PresetNameParcel =
            runBlocking { engine.checkPresetName(PresetKind.valueOf(kind), name) }.toParcel()

        override fun savePreset(kind: String, name: String): PresetSettingsParcel =
            runBlocking { engine.savePreset(PresetKind.valueOf(kind), name) }.toParcel()

        override fun deletePreset(kind: String, answerIds: Array<String>, answers: BooleanArray): PresetSettingsParcel =
            runBlocking { engine.deletePreset(PresetKind.valueOf(kind), answersOf(answerIds, answers)) }.toParcel()

        override fun appConfigValues(keys: Array<String>): AppConfigParcel = runBlocking { engine.appConfigValues(keys.toList()) }.toParcel()

        override fun setAppConfigValue(key: String, value: String): AppConfigParcel = runBlocking { engine.setAppConfigValue(key, value) }.toParcel()

        override fun slice(request: SliceRequestParcel, callback: ISliceCallback) {
            startJob(request.toSliceRequest(), callback)
        }

        override fun cancel(jobId: String): Boolean = runBlocking { engine.cancel(SliceJobId(jobId)) }

        override fun selectPlate(index: Int, count: Int) = runBlocking { engine.selectPlate(index, count) }

        override fun setLanguage(catalog: String) = runBlocking { engine.setLanguage(catalog) }

        override fun validatePlate(plate: Array<PlacedModelParcel>, profiles: ProfilesParcel, plateSettings: ModelSettingsParcel): PlateValidationParcel? =
            runBlocking { engine.validatePlate(plate.toPlacedModels(), profiles.toProfiles(), plateSettings.toModelSettings()) }?.toParcel()

        override fun overhangNormalZ(profiles: ProfilesParcel): Double =
            runBlocking { engine.overhangNormalZ(profiles.toProfiles()) }?.toDouble() ?: Double.NaN

        override fun thumbnailSizes(profiles: ProfilesParcel): ThumbnailSizesParcel =
            runBlocking { engine.thumbnailSizes(profiles.toProfiles()) }.toParcel()
    }

    private fun startJob(request: SliceRequest, callback: ISliceCallback) {
        val accepted = synchronized(jobLock) {
            if (activeJobId != null) {
                false
            } else {
                activeJobId = request.jobId
                enterForeground(request)
                true
            }
        }
        if (!accepted) {
            callback.deliver {
                onFinished(
                    SliceOutcome.Failure(
                        jobId = request.jobId,
                        code = SliceFailureCode.ENGINE_BUSY,
                        message = "Another slicing job is running",
                        recoverable = true,
                    ).toParcel(),
                )
            }
            return
        }

        scope.launch {
            val outcome = try {
                engine.slice(request) { progress ->
                    notifications.update(request, progress)
                    callback.deliver {
                        onProgress(progress.jobId.value, progress.fraction, progress.stage.name, progress.detail)
                    }
                }
            } catch (_: CancellationException) {
                // The service is being destroyed; the engine has already stopped the job.
                SliceOutcome.Cancelled(request.jobId)
            } catch (error: Exception) {
                Log.e(TAG, "Slicing job failed", error)
                SliceOutcome.Failure(
                    jobId = request.jobId,
                    code = SliceFailureCode.SLICING_FAILED,
                    message = error.message ?: error.javaClass.name,
                    recoverable = false,
                )
            }
            synchronized(jobLock) {
                activeJobId = null
                stopForeground(STOP_FOREGROUND_REMOVE)
                notifications.stopped()
                stopSelf()
            }
            callback.deliver { onFinished(outcome.toParcel()) }
        }
    }

    /** The binding app is visible when the user starts a job, so the start is allowed. */
    private fun enterForeground(request: SliceRequest) {
        try {
            startForegroundService(Intent(this, javaClass))
            val notification = notifications.started(request)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(SliceNotifications.NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(SliceNotifications.NOTIFICATION_ID, notification)
            }
        } catch (error: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException: the job still runs while
            // the app stays visible.
            Log.w(TAG, "Slicing without a foreground service", error)
        }
    }

    /** A client that died keeps no claim on the job, which finishes regardless. */
    private inline fun ISliceCallback.deliver(call: ISliceCallback.() -> Unit) {
        try {
            call()
        } catch (error: RemoteException) {
            Log.w(TAG, "Slicing client is gone", error)
        }
    }

    internal companion object {
        const val ACTION_CANCEL = "app.orcinus.shadow.slicing.service.action.CANCEL"
        const val EXTRA_JOB_ID = "app.orcinus.shadow.slicing.service.extra.JOB_ID"
        private const val TAG = "SlicerService"
    }
}
