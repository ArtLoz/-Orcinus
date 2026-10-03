package app.orcinus.shadow.slicing.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import app.orcinus.shadow.core.model.AppConfigOutcome
import app.orcinus.shadow.core.model.BedShape
import app.orcinus.shadow.core.model.BedShapeOutcome
import app.orcinus.shadow.core.model.CalibrationParams
import app.orcinus.shadow.core.model.CalibrationPrinterOutcome
import app.orcinus.shadow.core.model.ComparedPresets
import app.orcinus.shadow.core.model.ConfigExportKind
import app.orcinus.shadow.core.model.ConfigExportOptionsOutcome
import app.orcinus.shadow.core.model.ConfigOverwriteAnswer
import app.orcinus.shadow.core.model.ConfigTransferOutcome
import app.orcinus.shadow.core.model.CopyPlacement
import app.orcinus.shadow.core.model.CreateFilamentOptionsOutcome
import app.orcinus.shadow.core.model.CreateFilamentRequest
import app.orcinus.shadow.core.model.CreatePrinterOptionsOutcome
import app.orcinus.shadow.core.model.CreatePrinterRequest
import app.orcinus.shadow.core.model.CustomFilamentsOutcome
import app.orcinus.shadow.core.model.CutConnector
import app.orcinus.shadow.core.model.CutGroove
import app.orcinus.shadow.core.model.CutObjectOutcome
import app.orcinus.shadow.core.model.CutPartSelection
import app.orcinus.shadow.core.model.CutPartsOutcome
import app.orcinus.shadow.core.model.CutPlaneOutcome
import app.orcinus.shadow.core.model.DirtyPresetsOutcome
import app.orcinus.shadow.core.model.EmbossPlacement
import app.orcinus.shadow.core.model.EmbossVolumeOutcome
import app.orcinus.shadow.core.model.EngineStatus
import app.orcinus.shadow.core.model.FilamentPresetChoice
import app.orcinus.shadow.core.model.FilamentPresetsOutcome
import app.orcinus.shadow.core.model.FlatteningPlanesOutcome
import app.orcinus.shadow.core.model.FlowRateCalibration
import app.orcinus.shadow.core.model.FlushVolumesChange
import app.orcinus.shadow.core.model.FlushVolumesOutcome
import app.orcinus.shadow.core.model.FontFace
import app.orcinus.shadow.core.model.GcodePlaceholderInfo
import app.orcinus.shadow.core.model.GcodePlaceholdersOutcome
import app.orcinus.shadow.core.model.LayerEditingOutcome
import app.orcinus.shadow.core.model.LayerGcode
import app.orcinus.shadow.core.model.LayerHeightEdit
import app.orcinus.shadow.core.model.Manipulation
import app.orcinus.shadow.core.model.MeasureHoverOutcome
import app.orcinus.shadow.core.model.MeasureOutcome
import app.orcinus.shadow.core.model.MeasureRay
import app.orcinus.shadow.core.model.MeasureReset
import app.orcinus.shadow.core.model.MeasuredVolume
import app.orcinus.shadow.core.model.MeshExportOutcome
import app.orcinus.shadow.core.model.MeshFormat
import app.orcinus.shadow.core.model.ModelInspectionOutcome
import app.orcinus.shadow.core.model.ModelLoad
import app.orcinus.shadow.core.model.ModelLoadOutcome
import app.orcinus.shadow.core.model.ModelPath
import app.orcinus.shadow.core.model.ModelSettings
import app.orcinus.shadow.core.model.ModelSettingsOutcome
import app.orcinus.shadow.core.model.ModelSettingsRequest
import app.orcinus.shadow.core.model.ModelSource
import app.orcinus.shadow.core.model.ObjectCut
import app.orcinus.shadow.core.model.ObjectEdit
import app.orcinus.shadow.core.model.OrcaText
import app.orcinus.shadow.core.model.PaintKind
import app.orcinus.shadow.core.model.PaintStroke
import app.orcinus.shadow.core.model.PaintedFacets
import app.orcinus.shadow.core.model.PaintingOutcome
import app.orcinus.shadow.core.model.PlacedModel
import app.orcinus.shadow.core.model.PlateDescriptionOutcome
import app.orcinus.shadow.core.model.PlateInspectionOutcome
import app.orcinus.shadow.core.model.PlateManipulation
import app.orcinus.shadow.core.model.PlateValidation
import app.orcinus.shadow.core.model.PresetChangeAction
import app.orcinus.shadow.core.model.PresetChoice
import app.orcinus.shadow.core.model.PresetComparisonOutcome
import app.orcinus.shadow.core.model.PresetCreationOutcome
import app.orcinus.shadow.core.model.PresetKind
import app.orcinus.shadow.core.model.PresetNameOutcome
import app.orcinus.shadow.core.model.PresetNamesOutcome
import app.orcinus.shadow.core.model.PresetSettingsOutcome
import app.orcinus.shadow.core.model.PresetsOutcome
import app.orcinus.shadow.core.model.PrinterConnectionOutcome
import app.orcinus.shadow.core.model.ProfileId
import app.orcinus.shadow.core.model.ProjectPlate
import app.orcinus.shadow.core.model.ProjectSaveOutcome
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.SearchCatalogOutcome
import app.orcinus.shadow.core.model.SettingsMode
import app.orcinus.shadow.core.model.SettingsTabOutcome
import app.orcinus.shadow.core.model.SetupFilamentsOutcome
import app.orcinus.shadow.core.model.SetupPrintersOutcome
import app.orcinus.shadow.core.model.SimplifyConfig
import app.orcinus.shadow.core.model.SimplifyOutcome
import app.orcinus.shadow.core.model.SliceFailureCode
import app.orcinus.shadow.core.model.SliceJobId
import app.orcinus.shadow.core.model.SliceOutcome
import app.orcinus.shadow.core.model.SliceProgress
import app.orcinus.shadow.core.model.SliceRequest
import app.orcinus.shadow.core.model.SliceStage
import app.orcinus.shadow.core.model.SlicedPlates
import app.orcinus.shadow.core.model.SlicingProfileSelection
import app.orcinus.shadow.core.model.StepMeshOptions
import app.orcinus.shadow.core.model.SvgFileEdit
import app.orcinus.shadow.core.model.SvgPreviewOutcome
import app.orcinus.shadow.core.model.TextStyle
import app.orcinus.shadow.core.model.TextStylesOutcome
import app.orcinus.shadow.core.model.EmbossTransform
import app.orcinus.shadow.core.model.ThumbnailImage
import app.orcinus.shadow.core.model.ThumbnailSizesOutcome
import app.orcinus.shadow.core.model.Transform3
import app.orcinus.shadow.core.model.Vector3
import app.orcinus.shadow.core.model.VolumeType
import app.orcinus.shadow.core.model.WipeTowerOutcome
import app.orcinus.shadow.core.model.connectorKinds
import app.orcinus.shadow.core.model.connectorValues
import app.orcinus.shadow.slicing.api.AppConfigStore
import app.orcinus.shadow.slicing.api.EmbossEditor
import app.orcinus.shadow.slicing.api.LayerHeightEditor
import app.orcinus.shadow.slicing.api.PlateInspector
import app.orcinus.shadow.slicing.api.PlateMeasurer
import app.orcinus.shadow.slicing.api.PresetManager
import app.orcinus.shadow.slicing.api.PresetSettingsEditor
import app.orcinus.shadow.slicing.api.SliceProgressListener
import app.orcinus.shadow.slicing.api.SlicerEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * [SlicerEngine] and [PlateInspector] backed by a [SlicerService] in another
 * process. The first call binds to the service. When the engine process dies,
 * running jobs end with [SliceFailureCode.ENGINE_CRASHED] and a new process
 * starts at once; it is told the plate selected before any call reaches it.
 */
class RemoteSlicerEngine(
    context: Context,
    private val serviceClass: Class<out SlicerService<*>>,
) : SlicerEngine, PlateInspector, PresetManager, PresetSettingsEditor, AppConfigStore, LayerHeightEditor, EmbossEditor, PlateMeasurer {
    private val applicationContext = context.applicationContext
    private val lock = Any()

    /** Completes when connected; null while unbound. Guarded by [lock]. */
    private var connected: CompletableDeferred<ISlicerService>? = null

    /** The plate selected last (index and count), and the service that knows it. Guarded by [lock]. */
    private var plate: Pair<Int, Int>? = null
    private var plateKnownBy: ISlicerService? = null

    /** Lets a call through once the service it goes to knows [plate]. */
    private val plateTelling = Mutex()

    /** The language of the engine's messages, and the service that knows it; a new process is told again. Guarded by [lock]. */
    private var language: String? = null
    private var languageKnownBy: ISlicerService? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val service = ISlicerService.Stub.asInterface(binder)
            synchronized(lock) {
                val current = connected ?: return
                if (!current.complete(service)) connected = CompletableDeferred(service)
            }
        }

        // The engine process died. Android would restart a bound service by
        // itself, but backs off for 30 minutes after a second crash, so the
        // binding is replaced with a new one, which starts a process at once.
        // The new SlicerService clears the foreground state left by the dead
        // one; the engine itself loads only on the next call.
        override fun onServiceDisconnected(name: ComponentName) = reconnect()

        override fun onBindingDied(name: ComponentName) = reconnect()

        private fun reconnect() {
            synchronized(lock) {
                val current = connected ?: return
                connected = null
                applicationContext.unbindService(this)
                // Calls still waiting for the first connection keep waiting.
                bindLocked(current.takeUnless { it.isCompleted } ?: CompletableDeferred())
            }
        }
    }

    override suspend fun status(): EngineStatus = withContext(Dispatchers.IO) {
        try {
            service().status().toEngineStatus()
        } catch (error: RemoteException) {
            throw IllegalStateException(PROCESS_DIED, error)
        }
    }

    override suspend fun describePlate(
        profiles: SlicingProfileSelection,
        directory: ScenePath,
    ): PlateDescriptionOutcome = withContext(Dispatchers.IO) {
        try {
            service().describePlate(profiles.toParcel(), directory.value).toPlateDescriptionOutcome()
        } catch (_: RemoteException) {
            PlateDescriptionOutcome.Failure(PROCESS_DIED)
        }
    }

    override suspend fun inspect(
        model: ModelSource,
        profiles: SlicingProfileSelection,
        mesh: ScenePath,
        plate: List<PlacedModel>,
    ): ModelInspectionOutcome = withContext(Dispatchers.IO) {
        try {
            service().inspect(model.toParcel(), profiles.toParcel(), mesh.value, plate.toParcels()).toInspectionOutcome()
        } catch (_: RemoteException) {
            ModelInspectionOutcome.Failure(PROCESS_DIED)
        }
    }

    override suspend fun load(
        sources: List<ModelPath>,
        profiles: SlicingProfileSelection,
        plate: List<PlacedModel>,
        prefix: ScenePath,
        answers: Map<String, Boolean>,
        load: ModelLoad,
        chosen: Boolean,
        stepMeshes: Map<Int, StepMeshOptions>,
        askMulti: Boolean,
    ): ModelLoadOutcome = remote({ ModelLoadOutcome.Failure(it) }) {
        load(
            sources.map(ModelPath::value).toTypedArray(),
            profiles.toParcel(),
            plate.toParcels(),
            prefix.value,
            answers.keys.toTypedArray(),
            answers.values.toBooleanArray(),
            load.name,
            chosen,
            stepMeshes.toArray(sources.size),
            askMulti,
        ).toModelLoadOutcome()
    }

    override suspend fun edit(
        plate: List<PlacedModel>,
        index: Int,
        edit: ObjectEdit,
        volume: Int?,
        profiles: SlicingProfileSelection,
        prefix: ScenePath,
        answers: Map<String, Boolean>,
        cut: ObjectCut?,
    ): ModelLoadOutcome = remote({ ModelLoadOutcome.Failure(it) }) {
        edit(
            plate.toParcels(),
            index,
            edit.name,
            volume ?: -1,
            profiles.toParcel(),
            prefix.value,
            answers.keys.toTypedArray(),
            answers.values.toBooleanArray(),
            cut?.toParcel(),
        ).toModelLoadOutcome()
    }

    override suspend fun beginCut(plateObject: PlacedModel, instance: Int, profiles: SlicingProfileSelection): CutObjectOutcome =
        remote({ CutObjectOutcome.Failure(it) }) { beginCut(listOf(plateObject).toParcels().first(), instance, profiles.toParcel()).toOutcome() }

    override suspend fun describeCutPlane(
        plane: Transform3,
        connectors: List<CutConnector>,
        snapSpace: Double,
        snapBulge: Double,
        groove: CutGroove?,
        preview: Boolean,
        parts: CutPartSelection?,
        meshPrefix: ScenePath,
    ): CutPlaneOutcome = remote({ CutPlaneOutcome.Failure(it) }) {
        describeCutPlane(
            plane.columns.toDoubleArray(),
            connectors.connectorValues(),
            connectors.connectorKinds(),
            snapSpace,
            snapBulge,
            groove != null,
            (groove ?: CutGroove()).values(),
            preview,
            meshPrefix.value,
            parts?.plane?.columns?.toDoubleArray() ?: DoubleArray(0),
            parts?.selected?.toBooleanArray() ?: BooleanArray(0),
        ).toOutcome()
    }

    override suspend fun selectCutPart(parts: CutPartSelection, origin: Vector3, direction: Vector3, meshPrefix: ScenePath): CutPartsOutcome =
        remote({ CutPartsOutcome.Failure(it) }) {
            selectCutPart(
                parts.plane.columns.toDoubleArray(),
                parts.selected.toBooleanArray(),
                doubleArrayOf(origin.x, origin.y, origin.z),
                doubleArrayOf(direction.x, direction.y, direction.z),
                meshPrefix.value,
            ).toOutcome()
        }

    override suspend fun endCut() = remote({}) { endCut() }

    override suspend fun describeFonts(paths: List<String>): List<FontFace> =
        remote({ emptyList() }) { describeFonts(paths.toTypedArray()).map(FontFaceParcel::toFontFace) }

    override suspend fun createText(
        plate: List<PlacedModel>,
        placement: EmbossPlacement,
        type: VolumeType,
        text: String,
        style: TextStyle,
        profiles: SlicingProfileSelection,
        prefix: ScenePath,
    ): ModelLoadOutcome = remote({ ModelLoadOutcome.Failure(it) }) {
        createText(plate.toParcels(), placement.toParcel(), type.name, text, style.toParcel(), profiles.toParcel(), prefix.value).toModelLoadOutcome()
    }

    override suspend fun updateText(
        plate: List<PlacedModel>,
        index: Int,
        volume: Int,
        text: String,
        style: TextStyle,
        placement: Transform3?,
        profiles: SlicingProfileSelection,
        prefix: ScenePath,
    ): ModelLoadOutcome = remote({ ModelLoadOutcome.Failure(it) }) {
        updateText(
            plate.toParcels(),
            index,
            volume,
            text,
            style.toParcel(),
            placement?.columns?.toDoubleArray(),
            profiles.toParcel(),
            prefix.value,
        ).toModelLoadOutcome()
    }

    override suspend fun createSvg(
        plate: List<PlacedModel>,
        placement: EmbossPlacement,
        type: VolumeType,
        svg: ModelPath,
        profiles: SlicingProfileSelection,
        prefix: ScenePath,
    ): ModelLoadOutcome = remote({ ModelLoadOutcome.Failure(it) }) {
        createSvg(plate.toParcels(), placement.toParcel(), type.name, svg.value, profiles.toParcel(), prefix.value).toModelLoadOutcome()
    }

    override suspend fun updateSvg(
        plate: List<PlacedModel>,
        index: Int,
        volume: Int,
        depth: Double,
        useSurface: Boolean,
        svg: ModelPath?,
        placement: Transform3?,
        profiles: SlicingProfileSelection,
        prefix: ScenePath,
    ): ModelLoadOutcome = remote({ ModelLoadOutcome.Failure(it) }) {
        updateSvg(
            plate.toParcels(),
            index,
            volume,
            depth,
            useSurface,
            svg?.value,
            placement?.columns?.toDoubleArray(),
            profiles.toParcel(),
            prefix.value,
        ).toModelLoadOutcome()
    }

    override suspend fun describeEmboss(plate: List<PlacedModel>, index: Int, volume: Int, profiles: SlicingProfileSelection): EmbossVolumeOutcome =
        remote({ EmbossVolumeOutcome.Failure(it) }) { describeEmboss(plate.toParcels(), index, volume, profiles.toParcel()).toEmbossVolumeOutcome() }

    override suspend fun transformEmboss(
        plate: List<PlacedModel>,
        index: Int,
        instance: Int,
        volume: Int,
        transform: EmbossTransform,
        text: String,
        style: TextStyle,
        reEmboss: Boolean,
        profiles: SlicingProfileSelection,
        prefix: ScenePath,
    ): ModelLoadOutcome = remote({ ModelLoadOutcome.Failure(it) }) {
        transformEmboss(
            plate.toParcels(),
            index,
            instance,
            volume,
            transform.rotate,
            transform.move,
            transform.cameraPosition?.let { doubleArrayOf(it.x, it.y, it.z) } ?: DoubleArray(0),
            transform.cameraForward?.let { doubleArrayOf(it.x, it.y, it.z) } ?: DoubleArray(0),
            transform.perspective,
            transform.keepUp,
            transform.scale?.let { doubleArrayOf(it.x, it.y, it.z) } ?: DoubleArray(0),
            transform.mirror?.ordinal ?: -1,
            text,
            style.toParcel(),
            reEmboss,
            profiles.toParcel(),
            prefix.value,
        ).toModelLoadOutcome()
    }

    override suspend fun previewSvg(
        plate: List<PlacedModel>,
        index: Int,
        volume: Int,
        picture: ScenePath,
        maxSize: Int,
        profiles: SlicingProfileSelection,
    ): SvgPreviewOutcome = remote({ SvgPreviewOutcome.Failure(it) }) {
        previewSvg(plate.toParcels(), index, volume, picture.value, maxSize, profiles.toParcel()).toSvgPreviewOutcome()
    }

    override suspend fun editSvgFile(
        plate: List<PlacedModel>,
        index: Int,
        volume: Int,
        edit: SvgFileEdit,
        path: String,
        profiles: SlicingProfileSelection,
        prefix: ScenePath,
    ): ModelLoadOutcome = remote({ ModelLoadOutcome.Failure(it) }) {
        editSvgFile(plate.toParcels(), index, volume, edit.name, path, profiles.toParcel(), prefix.value).toModelLoadOutcome()
    }

    override suspend fun renameTextStyle(
        plate: List<PlacedModel>,
        index: Int,
        oldName: String,
        newName: String,
        profiles: SlicingProfileSelection,
        prefix: ScenePath,
    ): ModelLoadOutcome = remote({ ModelLoadOutcome.Failure(it) }) {
        renameTextStyle(plate.toParcels(), index, oldName, newName, profiles.toParcel(), prefix.value).toModelLoadOutcome()
    }

    override suspend fun textStyles(): TextStylesOutcome =
        remote({ TextStylesOutcome.Failure(it) }) { textStyles().toTextStylesOutcome() }

    override suspend fun storeTextStyles(styles: List<TextStyle>, active: Int?): TextStylesOutcome =
        remote({ TextStylesOutcome.Failure(it) }) { storeTextStyles(styles.map { it.toParcel() }.toTypedArray(), active ?: -1).toTextStylesOutcome() }

    override suspend fun begin(plate: List<PlacedModel>, index: Int, profiles: SlicingProfileSelection, plateSettings: ModelSettings) =
        remote({ LayerEditingOutcome.Failure(it) }) { beginLayerEditing(plate.toParcels(), index, profiles.toParcel(), plateSettings.toParcel()).toLayerEditingOutcome() }

    override suspend fun edit(action: LayerHeightEdit, z: Double, strength: Double, bandWidth: Double) =
        remote({ LayerEditingOutcome.Failure(it) }) { editLayerHeights(action.name, z, strength, bandWidth).toLayerEditingOutcome() }

    override suspend fun adaptive(quality: Double) =
        remote({ LayerEditingOutcome.Failure(it) }) { adaptiveLayerHeights(quality).toLayerEditingOutcome() }

    override suspend fun smooth(radius: Int, keepMin: Boolean) =
        remote({ LayerEditingOutcome.Failure(it) }) { smoothLayerHeights(radius, keepMin).toLayerEditingOutcome() }

    override suspend fun reset() = remote({ LayerEditingOutcome.Failure(it) }) { resetLayerHeights().toLayerEditingOutcome() }

    override suspend fun accept() = remote({ LayerEditingOutcome.Failure(it) }) { acceptLayerHeights().toLayerEditingOutcome() }

    override suspend fun end() = remote({}) { endLayerEditing() }

    override suspend fun beginMeasure(plate: List<PlacedModel>, volumes: List<MeasuredVolume>, profiles: SlicingProfileSelection): MeasureOutcome =
        remote({ MeasureOutcome.Failure(it) }) { beginMeasure(plate.toParcels(), volumes.toTriples(), profiles.toParcel()).toMeasureOutcome() }

    override suspend fun hoverMeasure(ray: MeasureRay): MeasureHoverOutcome = remote({ MeasureHoverOutcome.Failure(it) }) {
        hoverMeasure(ray.origin.toDoubles(), ray.direction.toDoubles(), ray.pointSelection, ray.onlySelectPlane, ray.sphereRadius).toMeasureHoverOutcome()
    }

    override suspend fun selectMeasure(ray: MeasureRay): MeasureOutcome = remote({ MeasureOutcome.Failure(it) }) {
        selectMeasure(ray.origin.toDoubles(), ray.direction.toDoubles(), ray.pointSelection, ray.onlySelectPlane, ray.sphereRadius).toMeasureOutcome()
    }

    override suspend fun resetMeasure(reset: MeasureReset): MeasureOutcome =
        remote({ MeasureOutcome.Failure(it) }) { resetMeasure(reset.name).toMeasureOutcome() }

    override suspend fun endMeasure() = remote({}) { endMeasure() }

    override suspend fun saveProject(
        path: ScenePath,
        plate: List<PlacedModel>,
        profiles: SlicingProfileSelection,
        plates: List<ProjectPlate>,
        projectInfo: ScenePath?,
        currentPlate: Int,
        sliced: SlicedPlates,
    ): ProjectSaveOutcome = withContext(Dispatchers.IO) {
        try {
            val error = service().saveProject(
                path.value,
                plate.toParcels(),
                profiles.toParcel(),
                plates.map { it.toParcel() }.toTypedArray(),
                projectInfo?.value,
                currentPlate,
                sliced.name,
            )
            error?.let(ProjectSaveOutcome::Failure) ?: ProjectSaveOutcome.Success
        } catch (_: RemoteException) {
            ProjectSaveOutcome.Failure(PROCESS_DIED)
        }
    }

    override suspend fun exportMesh(
        plate: List<PlacedModel>,
        index: Int,
        format: MeshFormat,
        profiles: SlicingProfileSelection,
        path: ScenePath,
    ): MeshExportOutcome = withContext(Dispatchers.IO) {
        try {
            val parcel = service().exportMesh(plate.toParcels(), index, format.name, profiles.toParcel(), path.value)
            parcel.error?.let(MeshExportOutcome::Failure) ?: MeshExportOutcome.Success(parcel.warning)
        } catch (_: RemoteException) {
            MeshExportOutcome.Failure(PROCESS_DIED)
        }
    }

    override suspend fun simplifyVolume(
        plate: List<PlacedModel>,
        index: Int,
        volume: Int,
        config: SimplifyConfig,
        profiles: SlicingProfileSelection,
        path: ScenePath,
    ): SimplifyOutcome = remote(SimplifyOutcome::Failure) {
        val parcel = simplifyVolume(
            plate.toParcels(),
            index,
            volume,
            config.useCount,
            config.wantedCount,
            config.decimateRatio,
            config.maxError,
            profiles.toParcel(),
            path.value,
        )
        parcel.error?.let(SimplifyOutcome::Failure) ?: SimplifyOutcome.Success(parcel.triangles, parcel.original)
    }

    override suspend fun setVolumeType(
        plate: List<PlacedModel>,
        index: Int,
        volume: Int,
        type: VolumeType,
        profiles: SlicingProfileSelection,
        prefix: ScenePath,
    ): ModelLoadOutcome = remote({ ModelLoadOutcome.Failure(it) }) {
        setVolumeType(plate.toParcels(), index, volume, type.name, profiles.toParcel(), prefix.value).toModelLoadOutcome()
    }

    override suspend fun applySimplify(
        plate: List<PlacedModel>,
        index: Int,
        volume: Int,
        config: SimplifyConfig,
        profiles: SlicingProfileSelection,
        prefix: ScenePath,
    ): ModelLoadOutcome = remote({ ModelLoadOutcome.Failure(it) }) {
        applySimplify(
            plate.toParcels(),
            index,
            volume,
            config.useCount,
            config.wantedCount,
            config.decimateRatio,
            config.maxError,
            profiles.toParcel(),
            prefix.value,
        ).toModelLoadOutcome()
    }

    override suspend fun replaceVolume(
        plate: List<PlacedModel>,
        index: Int,
        volume: Int,
        source: ModelPath,
        profiles: SlicingProfileSelection,
        prefix: ScenePath,
        stepMesh: StepMeshOptions?,
    ): ModelLoadOutcome = remote({ ModelLoadOutcome.Failure(it) }) {
        replaceVolume(plate.toParcels(), index, volume, source.value, profiles.toParcel(), prefix.value, stepMesh?.toArray()).toModelLoadOutcome()
    }

    override suspend fun stepTriangleCount(source: ModelPath, linearDeflection: Double, angleDeflection: Double): Long =
        remote({ 0L }) { stepTriangleCount(source.value, linearDeflection, angleDeflection) }

    override suspend fun stopStepTriangleCount() = remote({}) { stopStepTriangleCount() }

    override suspend fun releaseStepFile() = remote({}) { releaseStepFile() }

    override suspend fun addPrimitive(
        plate: List<PlacedModel>,
        shape: String,
        name: String,
        profiles: SlicingProfileSelection,
        prefix: ScenePath,
    ): ModelLoadOutcome = remote({ ModelLoadOutcome.Failure(it) }) {
        addPrimitive(plate.toParcels(), shape, name, profiles.toParcel(), prefix.value).toModelLoadOutcome()
    }

    override suspend fun prepareCalibration(
        params: CalibrationParams,
        profiles: SlicingProfileSelection,
        prefix: ScenePath,
    ): ModelLoadOutcome = remote({ ModelLoadOutcome.Failure(it) }) {
        prepareCalibration(params.toParcel(), profiles.toParcel(), prefix.value).toModelLoadOutcome()
    }

    override suspend fun describeCalibrationPrinter(profiles: SlicingProfileSelection): CalibrationPrinterOutcome =
        remote({ CalibrationPrinterOutcome.Failure(it) }) { describeCalibrationPrinter(profiles.toParcel()).toOutcome() }

    override suspend fun prepareFlowRateCalibration(
        test: FlowRateCalibration,
        profiles: SlicingProfileSelection,
        prefix: ScenePath,
    ): ModelLoadOutcome = remote({ ModelLoadOutcome.Failure(it) }) {
        prepareFlowRateCalibration(test.linear, test.pass, test.topSurfacePattern, profiles.toParcel(), prefix.value).toModelLoadOutcome()
    }

    override suspend fun handyModel(file: String): ModelPath? = withContext(Dispatchers.IO) {
        try {
            service().handyModel(file)?.let(::ModelPath)
        } catch (_: RemoteException) {
            null
        }
    }

    override suspend fun pasteVolumes(
        plate: List<PlacedModel>,
        index: Int,
        instance: Int,
        source: PlacedModel,
        volumes: List<Int>,
        sameInputFile: Boolean,
        profiles: SlicingProfileSelection,
        prefix: ScenePath,
    ): ModelLoadOutcome = remote({ ModelLoadOutcome.Failure(it) }) {
        pasteVolumes(
            plate.toParcels(),
            index,
            instance,
            listOf(source).toParcels().single(),
            volumes.toIntArray(),
            sameInputFile,
            profiles.toParcel(),
            prefix.value,
        ).toModelLoadOutcome()
    }

    override suspend fun copy(
        plate: List<PlacedModel>,
        sources: List<PlacedModel>,
        count: Int,
        placement: CopyPlacement,
        profiles: SlicingProfileSelection,
        prefix: ScenePath,
    ): ModelLoadOutcome = remote({ ModelLoadOutcome.Failure(it) }) {
        copy(plate.toParcels(), sources.toParcels(), count, placement.name, profiles.toParcel(), prefix.value).toModelLoadOutcome()
    }

    override suspend fun place(
        plateObject: PlacedModel,
        profiles: SlicingProfileSelection,
        previous: Transform3,
        placement: Transform3,
        autoDrop: Boolean,
        manipulation: Manipulation,
    ): ModelInspectionOutcome = withContext(Dispatchers.IO) {
        try {
            service().place(
                listOf(plateObject).toParcels().first(),
                profiles.toParcel(),
                previous.columns.toDoubleArray(),
                placement.columns.toDoubleArray(),
                autoDrop,
                manipulation.parcelName(),
                manipulation.parcelFaceNormal(),
            ).toInspectionOutcome()
        } catch (_: RemoteException) {
            ModelInspectionOutcome.Failure(PROCESS_DIED)
        }
    }

    override suspend fun placeObjects(
        plate: List<PlacedModel>,
        profiles: SlicingProfileSelection,
        manipulation: PlateManipulation,
    ): PlateInspectionOutcome = withContext(Dispatchers.IO) {
        try {
            service().placeObjects(
                plate.toParcels(),
                profiles.toParcel(),
                manipulation.parcelName(),
                manipulation.parcelSelected(),
                manipulation.parcelArrangeSettings(),
                manipulation.parcelInstance(),
                manipulation.lockedPlates.sorted().toIntArray(),
            ).toPlateInspectionOutcome()
        } catch (_: RemoteException) {
            PlateInspectionOutcome.Failure(PROCESS_DIED)
        }
    }

    override suspend fun updateFlushVolumes(
        plate: List<PlacedModel>,
        profiles: SlicingProfileSelection,
        plateSettings: ModelSettings,
        change: FlushVolumesChange,
        index: Int,
    ): FlushVolumesOutcome = withContext(Dispatchers.IO) {
        try {
            service().updateFlushVolumes(plate.toParcels(), profiles.toParcel(), plateSettings.toParcel(), change.name, index).toOutcome()
        } catch (_: RemoteException) {
            FlushVolumesOutcome.Failure(PROCESS_DIED)
        }
    }

    override suspend fun describeFlushVolumes(
        plate: List<PlacedModel>,
        profiles: SlicingProfileSelection,
        plateSettings: ModelSettings,
    ): FlushVolumesOutcome = withContext(Dispatchers.IO) {
        try {
            service().describeFlushVolumes(plate.toParcels(), profiles.toParcel(), plateSettings.toParcel()).toOutcome()
        } catch (_: RemoteException) {
            FlushVolumesOutcome.Failure(PROCESS_DIED)
        }
    }

    override suspend fun beginPainting(
        plateObject: PlacedModel,
        part: Int?,
        kind: PaintKind,
        profiles: SlicingProfileSelection,
        facets: PaintedFacets,
        meshPrefix: ScenePath,
    ): PaintingOutcome = withContext(Dispatchers.IO) {
        try {
            service().beginPainting(
                listOf(plateObject).toParcels().first(),
                part ?: -1,
                kind.name,
                profiles.toParcel(),
                facets.value,
                meshPrefix.value,
            ).toOutcome()
        } catch (_: RemoteException) {
            PaintingOutcome.Failure(PROCESS_DIED)
        }
    }

    override suspend fun fuzzySkinDisabled(plateObject: PlacedModel, profiles: SlicingProfileSelection): Boolean =
        remote({ false }) { fuzzySkinDisabled(listOf(plateObject).toParcels().first(), profiles.toParcel()) }

    override suspend fun paint(stroke: PaintStroke, meshPrefix: ScenePath): PaintingOutcome = withContext(Dispatchers.IO) {
        try {
            service().paintStroke(
                doubleArrayOf(stroke.origin.x, stroke.origin.y, stroke.origin.z),
                doubleArrayOf(stroke.direction.x, stroke.direction.y, stroke.direction.z),
                stroke.state,
                stroke.radius,
                stroke.tool.name,
                stroke.angle,
                stroke.overhangAngle,
                stroke.startsStroke,
                meshPrefix.value,
            ).toOutcome()
        } catch (_: RemoteException) {
            PaintingOutcome.Failure(PROCESS_DIED)
        }
    }

    override suspend fun undoPainting(meshPrefix: ScenePath): PaintingOutcome = withContext(Dispatchers.IO) {
        try {
            service().undoPainting(meshPrefix.value).toOutcome()
        } catch (_: RemoteException) {
            PaintingOutcome.Failure(PROCESS_DIED)
        }
    }

    override suspend fun redoPainting(meshPrefix: ScenePath): PaintingOutcome = withContext(Dispatchers.IO) {
        try {
            service().redoPainting(meshPrefix.value).toOutcome()
        } catch (_: RemoteException) {
            PaintingOutcome.Failure(PROCESS_DIED)
        }
    }

    override suspend fun clearPainting(meshPrefix: ScenePath): PaintingOutcome =
        remote(PaintingOutcome::Failure) { clearPainting(meshPrefix.value).toOutcome() }

    override suspend fun setGapFill(gapArea: Double?, meshPrefix: ScenePath): PaintingOutcome =
        remote(PaintingOutcome::Failure) { setGapFill(gapArea ?: -1.0, meshPrefix.value).toOutcome() }

    override suspend fun fillGaps(meshPrefix: ScenePath): PaintingOutcome =
        remote(PaintingOutcome::Failure) { fillGaps(meshPrefix.value).toOutcome() }

    override suspend fun endPainting(): PaintingOutcome = withContext(Dispatchers.IO) {
        try {
            service().endPainting().toOutcome()
        } catch (_: RemoteException) {
            PaintingOutcome.Failure(PROCESS_DIED)
        }
    }

    override suspend fun describeWipeTower(
        plate: List<PlacedModel>,
        profiles: SlicingProfileSelection,
        plateSettings: ModelSettings,
    ): WipeTowerOutcome = withContext(Dispatchers.IO) {
        try {
            service().describeWipeTower(plate.toParcels(), profiles.toParcel(), plateSettings.toParcel()).toOutcome()
        } catch (_: RemoteException) {
            WipeTowerOutcome.Failure(PROCESS_DIED)
        }
    }

    override suspend fun flatteningPlanes(
        plateObject: PlacedModel,
        profiles: SlicingProfileSelection,
        placement: Transform3,
    ): FlatteningPlanesOutcome = withContext(Dispatchers.IO) {
        try {
            service().flatteningPlanes(listOf(plateObject).toParcels().first(), profiles.toParcel(), placement.columns.toDoubleArray()).toOutcome()
        } catch (_: RemoteException) {
            FlatteningPlanesOutcome.Failure(PROCESS_DIED)
        }
    }

    override suspend fun addPart(
        plateObject: PlacedModel,
        shape: String,
        type: VolumeType,
        profiles: SlicingProfileSelection,
        mesh: ScenePath,
    ): ModelInspectionOutcome = remote(ModelInspectionOutcome::Failure) {
        addObjectPart(listOf(plateObject).toParcels().first(), shape, type.name, profiles.toParcel(), mesh.value).toInspectionOutcome()
    }

    override suspend fun presets(): PresetsOutcome = remote(PresetsOutcome::Failure) { presets().toPresetsOutcome() }

    override suspend fun dirtyPresets(): DirtyPresetsOutcome = remote(DirtyPresetsOutcome::Failure) { dirtyPresets().toDirtyPresetsOutcome() }

    override suspend fun discardPresetChanges(): PresetsOutcome = remote(PresetsOutcome::Failure) { discardPresetChanges().toPresetsOutcome() }

    override suspend fun resetProjectPresets(): PresetsOutcome = remote(PresetsOutcome::Failure) { resetProjectPresets().toPresetsOutcome() }

    override suspend fun selectPreset(choice: PresetChoice, action: PresetChangeAction): PresetsOutcome =
        remote(PresetsOutcome::Failure) { selectPreset(choice.parcelKind(), choice.parcelValue(), action.name).toPresetsOutcome() }

    override suspend fun setupPrinters(): SetupPrintersOutcome =
        remote(SetupPrintersOutcome::Failure) { setupPrinters().toSetupPrintersOutcome() }

    override suspend fun setupFilaments(models: List<String>): SetupFilamentsOutcome =
        remote(SetupFilamentsOutcome::Failure) { setupFilaments(models.toTypedArray()).toSetupFilamentsOutcome() }

    override suspend fun applySetup(models: List<String>, filaments: List<String>): PresetsOutcome =
        remote(PresetsOutcome::Failure) { applySetup(models.toTypedArray(), filaments.toTypedArray()).toPresetsOutcome() }

    override suspend fun applyDefaultSetup(): PresetsOutcome = remote(PresetsOutcome::Failure) { applyDefaultSetup().toPresetsOutcome() }

    /** Calls the service on the IO dispatcher; a dead engine process fails the call. */
    override suspend fun settingsTab(kind: PresetKind): SettingsTabOutcome =
        remote(SettingsTabOutcome::Failure) { settingsTab(kind.name).toSettingsTabOutcome() }

    override suspend fun settings(
        kind: PresetKind,
        page: String,
        answers: Map<String, Boolean>,
        model: ModelSettingsRequest,
    ): PresetSettingsOutcome = remote(PresetSettingsOutcome::Failure) {
        settings(
            kind.name,
            page,
            answers.keys.toTypedArray(),
            answers.values.toBooleanArray(),
            model.settings.map(ModelSettings::toParcel).toTypedArray(),
            model.plate.toParcel(),
            model.parent.toParcel(),
        ).toPresetSettingsOutcome()
    }

    override suspend fun changeSetting(
        kind: PresetKind,
        page: String,
        id: String,
        text: String,
        answers: Map<String, Boolean>,
        model: ModelSettingsRequest,
    ): PresetSettingsOutcome = remote(PresetSettingsOutcome::Failure) {
        changeSetting(
            kind.name,
            page,
            id,
            text,
            answers.keys.toTypedArray(),
            answers.values.toBooleanArray(),
            model.settings.map(ModelSettings::toParcel).toTypedArray(),
            model.plate.toParcel(),
            model.parent.toParcel(),
        ).toPresetSettingsOutcome()
    }

    override suspend fun pasteModelSettings(
        clipboard: ModelSettings,
        target: ModelSettings,
        parent: ModelSettings?,
    ): ModelSettingsOutcome = remote(ModelSettingsOutcome::Failure) {
        val parcel = pasteModelSettings(clipboard.toParcel(), target.toParcel(), parent?.toParcel())
        parcel.error?.let(ModelSettingsOutcome::Failure) ?: ModelSettingsOutcome.Success(parcel.settings.toModelSettings())
    }

    override suspend fun resetSettings(
        kind: PresetKind,
        page: String,
        ids: List<String>,
        answers: Map<String, Boolean>,
        model: ModelSettingsRequest,
    ): PresetSettingsOutcome = remote(PresetSettingsOutcome::Failure) {
        resetSettings(
            kind.name,
            page,
            ids.toTypedArray(),
            answers.keys.toTypedArray(),
            answers.values.toBooleanArray(),
            model.settings.map(ModelSettings::toParcel).toTypedArray(),
            model.plate.toParcel(),
            model.parent.toParcel(),
        ).toPresetSettingsOutcome()
    }

    override suspend fun setSettingOverride(
        kind: PresetKind,
        page: String,
        id: String,
        enabled: Boolean,
        answers: Map<String, Boolean>,
    ): PresetSettingsOutcome = remote(PresetSettingsOutcome::Failure) {
        setSettingOverride(kind.name, page, id, enabled, answers.keys.toTypedArray(), answers.values.toBooleanArray()).toPresetSettingsOutcome()
    }

    override suspend fun setCompatiblePresets(
        kind: PresetKind,
        page: String,
        key: String,
        presets: List<String>,
        answers: Map<String, Boolean>,
    ): PresetSettingsOutcome = remote(PresetSettingsOutcome::Failure) {
        setCompatiblePresets(kind.name, page, key, presets.toTypedArray(), answers.keys.toTypedArray(), answers.values.toBooleanArray())
            .toPresetSettingsOutcome()
    }

    override suspend fun createFilamentOptions(type: String, baseFilament: String): CreateFilamentOptionsOutcome =
        remote(CreateFilamentOptionsOutcome::Failure) { createFilamentOptions(type, baseFilament).toCreateFilamentOptionsOutcome() }

    override suspend fun createFilament(request: CreateFilamentRequest, answers: Map<String, Boolean>): PresetCreationOutcome =
        remote(PresetCreationOutcome::Failure) {
            createFilament(
                request.vendor,
                request.customVendor,
                request.type,
                request.serial,
                request.presets.map(FilamentPresetChoice::printer).toTypedArray(),
                request.presets.map(FilamentPresetChoice::preset).toTypedArray(),
                answers.keys.toTypedArray(),
                answers.values.toBooleanArray(),
            ).toPresetCreationOutcome()
        }

    override suspend fun createPrinterOptions(
        vendor: String,
        nozzle: String,
        presetVendor: String,
        printerPreset: String,
    ): CreatePrinterOptionsOutcome = remote(CreatePrinterOptionsOutcome::Failure) {
        createPrinterOptions(vendor, nozzle, presetVendor, printerPreset).toCreatePrinterOptionsOutcome()
    }

    override suspend fun createPrinter(request: CreatePrinterRequest, answers: Map<String, Boolean>): PresetCreationOutcome =
        remote(PresetCreationOutcome::Failure) {
            createPrinter(
                request.model,
                request.nozzle,
                request.printableArea.flatMap { listOf(it.x, it.y) }.toDoubleArray(),
                request.maxPrintHeight,
                request.customTexture,
                request.customModel,
                request.presetVendor,
                request.printerPreset,
                request.filamentPresets.toTypedArray(),
                request.processPresets.toTypedArray(),
                answers.keys.toTypedArray(),
                answers.values.toBooleanArray(),
            ).toPresetCreationOutcome()
        }

    override suspend fun customFilaments(): CustomFilamentsOutcome =
        remote(CustomFilamentsOutcome::Failure) { customFilaments().toCustomFilamentsOutcome() }

    override suspend fun filamentPresets(filamentId: String): FilamentPresetsOutcome =
        remote(FilamentPresetsOutcome::Failure) { filamentPresets(filamentId).toFilamentPresetsOutcome() }

    override suspend fun deleteFilamentPreset(preset: String, answers: Map<String, Boolean>): PresetCreationOutcome =
        remote(PresetCreationOutcome::Failure) {
            deleteFilamentPreset(preset, answers.keys.toTypedArray(), answers.values.toBooleanArray()).toPresetCreationOutcome()
        }

    override suspend fun addFilament(): PresetsOutcome = remote(PresetsOutcome::Failure) { addFilament().toPresetsOutcome() }

    override suspend fun removeFilament(index: Int): PresetsOutcome =
        remote(PresetsOutcome::Failure) { removeFilament(index).toPresetsOutcome() }

    override suspend fun selectFilament(index: Int, name: ProfileId, action: PresetChangeAction): PresetsOutcome =
        remote(PresetsOutcome::Failure) { selectFilament(index, name.value, action.name).toPresetsOutcome() }

    override suspend fun setFilamentColor(index: Int, color: String): PresetsOutcome =
        remote(PresetsOutcome::Failure) { setFilamentColor(index, color).toPresetsOutcome() }

    override suspend fun printerConnection(): PrinterConnectionOutcome =
        remote(PrinterConnectionOutcome::Failure) { printerConnection().toPrinterConnectionOutcome() }

    override suspend fun savePrinterConnection(settings: ModelSettings, name: String): PresetSettingsOutcome =
        remote(PresetSettingsOutcome::Failure) {
            savePrinterConnection(settings.values.keys.toTypedArray(), settings.values.values.toTypedArray(), name).toPresetSettingsOutcome()
        }

    override suspend fun importPresets(paths: List<String>, answers: Map<String, ConfigOverwriteAnswer>): ConfigTransferOutcome =
        remote(ConfigTransferOutcome::Failure) {
            importPresets(
                paths.toTypedArray(),
                answers.keys.toTypedArray(),
                answers.values.map { it.ordinal.toLong() }.toLongArray(),
            ).toConfigTransferOutcome()
        }

    override suspend fun configExportOptions(kind: ConfigExportKind): ConfigExportOptionsOutcome =
        remote(ConfigExportOptionsOutcome::Failure) { configExportOptions(kind.name).toConfigExportOptionsOutcome() }

    override suspend fun exportConfigs(kind: ConfigExportKind, names: List<String>, directory: String): ConfigTransferOutcome =
        remote(ConfigTransferOutcome::Failure) { exportConfigs(kind.name, names.toTypedArray(), directory).toConfigTransferOutcome() }

    override suspend fun comparePresets(left: ComparedPresets, right: ComparedPresets, showAll: Boolean): PresetComparisonOutcome =
        remote(PresetComparisonOutcome::Failure) { comparePresets(left.names(), right.names(), showAll).toPresetComparisonOutcome() }

    /** The order the service passes the presets of a side in. */
    private fun ComparedPresets.names() = arrayOf(printer, print, filament)

    override suspend fun transferPresetOptions(kind: PresetKind, from: String, to: String, options: List<String>): PresetsOutcome =
        remote(PresetsOutcome::Failure) { transferPresetOptions(kind.name, from, to, options.toTypedArray()).toPresetsOutcome() }

    override suspend fun searchCatalog(): SearchCatalogOutcome =
        remote(SearchCatalogOutcome::Failure) { searchCatalog().toSearchCatalogOutcome() }

    override suspend fun gcodePlaceholders(kind: PresetKind, key: String): GcodePlaceholdersOutcome =
        remote(GcodePlaceholdersOutcome::Failure) { gcodePlaceholders(kind.name, key).toGcodePlaceholdersOutcome() }

    override suspend fun gcodePlaceholder(key: String, presets: Boolean): GcodePlaceholderInfo =
        remote({ GcodePlaceholderInfo(emptyList(), "", emptyList(), undefined = true) }) {
            gcodePlaceholder(key, presets).toGcodePlaceholderInfo()
        }

    override suspend fun editCustomGcode(
        kind: PresetKind,
        page: String,
        key: String,
        value: String,
        answers: Map<String, Boolean>,
    ): PresetSettingsOutcome = remote(PresetSettingsOutcome::Failure) {
        editCustomGcode(kind.name, page, key, value, answers.keys.toTypedArray(), answers.values.toBooleanArray()).toPresetSettingsOutcome()
    }

    override suspend fun setRammingParameters(
        kind: PresetKind,
        page: String,
        parameters: String,
        answers: Map<String, Boolean>,
    ): PresetSettingsOutcome = remote(PresetSettingsOutcome::Failure) {
        setRammingParameters(kind.name, page, parameters, answers.keys.toTypedArray(), answers.values.toBooleanArray()).toPresetSettingsOutcome()
    }

    override suspend fun bedShape(): BedShapeOutcome = remote(BedShapeOutcome::Failure) { bedShape().toBedShapeOutcome() }

    override suspend fun setBedShape(shape: BedShape, customPath: ModelPath?, answers: Map<String, Boolean>): PresetSettingsOutcome =
        remote(PresetSettingsOutcome::Failure) {
            setBedShape(
                shape.kind.name,
                shape.sizeX,
                shape.sizeY,
                shape.originX,
                shape.originY,
                shape.diameter,
                customPath?.value,
                shape.texture,
                shape.model,
                answers.keys.toTypedArray(),
                answers.values.toBooleanArray(),
            ).toPresetSettingsOutcome()
        }

    override suspend fun compatiblePresetChoices(kind: PresetKind, key: String): PresetNamesOutcome =
        remote(PresetNamesOutcome::Failure) { compatiblePresetChoices(kind.name, key).toPresetNamesOutcome() }

    override suspend fun setSettingsMode(kind: PresetKind, mode: SettingsMode, model: ModelSettingsRequest): PresetSettingsOutcome =
        remote(PresetSettingsOutcome::Failure) {
            setSettingsMode(
                kind.name,
                mode.name,
                model.settings.map(ModelSettings::toParcel).toTypedArray(),
                model.plate.toParcel(),
                model.parent.toParcel(),
            ).toPresetSettingsOutcome()
        }

    override suspend fun setSettingsVariant(
        kind: PresetKind,
        page: String,
        variant: Int,
        answers: Map<String, Boolean>,
        model: ModelSettingsRequest,
    ): PresetSettingsOutcome =
        remote(PresetSettingsOutcome::Failure) {
            setSettingsVariant(
                kind.name,
                page,
                variant,
                answers.keys.toTypedArray(),
                answers.values.toBooleanArray(),
                model.settings.map(ModelSettings::toParcel).toTypedArray(),
                model.plate.toParcel(),
                model.parent.toParcel(),
            ).toPresetSettingsOutcome()
        }

    override suspend fun settingTooltip(kind: PresetKind, id: String): List<OrcaText> =
        remote({ emptyList() }) { settingTooltip(kind.name, id).toTexts() }

    override suspend fun checkPresetName(kind: PresetKind, name: String): PresetNameOutcome =
        remote(PresetNameOutcome::Failure) { checkPresetName(kind.name, name).toPresetNameOutcome() }

    override suspend fun savePreset(kind: PresetKind, name: String): PresetSettingsOutcome =
        remote(PresetSettingsOutcome::Failure) { savePreset(kind.name, name).toPresetSettingsOutcome() }

    override suspend fun deletePreset(kind: PresetKind, answers: Map<String, Boolean>): PresetSettingsOutcome =
        remote(PresetSettingsOutcome::Failure) {
            deletePreset(kind.name, answers.keys.toTypedArray(), answers.values.toBooleanArray()).toPresetSettingsOutcome()
        }

    override suspend fun appConfigValues(keys: List<String>): AppConfigOutcome =
        remote(AppConfigOutcome::Failure) { appConfigValues(keys.toTypedArray()).toAppConfigOutcome() }

    override suspend fun setAppConfigValue(key: String, value: String): AppConfigOutcome =
        remote(AppConfigOutcome::Failure) { setAppConfigValue(key, value).toAppConfigOutcome() }

    private suspend fun <T> remote(failure: (String) -> T, call: ISlicerService.() -> T): T = withContext(Dispatchers.IO) {
        try {
            service().call()
        } catch (_: RemoteException) {
            failure(PROCESS_DIED)
        }
    }

    override suspend fun slice(
        request: SliceRequest,
        progressListener: SliceProgressListener,
    ): SliceOutcome {
        val service = service()
        val outcome = CompletableDeferred<SliceOutcome>()
        val crashed = SliceOutcome.Failure(
            jobId = request.jobId,
            code = SliceFailureCode.ENGINE_CRASHED,
            message = PROCESS_DIED,
            recoverable = true,
        )
        val callback = object : ISliceCallback.Stub() {
            override fun onProgress(jobId: String, fraction: Float, stage: String, detail: String?) {
                progressListener.onProgress(SliceProgress(SliceJobId(jobId), fraction, SliceStage.valueOf(stage), detail))
            }

            override fun onFinished(result: SliceOutcomeParcel) {
                outcome.complete(result.toSliceOutcome())
            }
        }
        val binder = service.asBinder()
        val deathRecipient = IBinder.DeathRecipient { outcome.complete(crashed) }
        try {
            binder.linkToDeath(deathRecipient, 0)
        } catch (_: RemoteException) {
            return crashed
        }
        return try {
            withContext(Dispatchers.IO) { service.slice(request.toParcel(), callback) }
            outcome.await()
        } catch (_: RemoteException) {
            crashed
        } catch (cancellation: CancellationException) {
            withContext(NonCancellable + Dispatchers.IO) {
                try {
                    service.cancel(request.jobId.value)
                } catch (_: RemoteException) {
                    // The engine process is gone, and the job with it.
                }
            }
            throw cancellation
        } finally {
            binder.unlinkToDeath(deathRecipient, 0)
        }
    }

    override suspend fun setLanguage(catalog: String) {
        synchronized(lock) {
            if (language == catalog) return
            language = catalog
            languageKnownBy = null
        }
        // Reaching the service tells it the language.
        try {
            service()
        } catch (_: IllegalStateException) {
            // Not bound: the next call tells the language.
        }
    }

    override suspend fun selectPlate(index: Int, count: Int) {
        synchronized(lock) {
            plate = index to count
            plateKnownBy = null
        }
        // Reaching the service tells it the plate.
        try {
            service()
        } catch (_: IllegalStateException) {
            // Not bound: the next call tells the plate.
        }
    }

    override suspend fun validatePlate(plate: List<PlacedModel>, profiles: SlicingProfileSelection, plateSettings: ModelSettings): PlateValidation? =
        remote({ null }) { validatePlate(plate.toParcels(), profiles.toParcel(), plateSettings.toParcel()) }?.toPlateValidation()

    override suspend fun overhangNormalZ(profiles: SlicingProfileSelection): Float? =
        remote({ Double.NaN }) { overhangNormalZ(profiles.toParcel()) }.takeUnless(Double::isNaN)?.toFloat()

    override suspend fun thumbnailSizes(profiles: SlicingProfileSelection): ThumbnailSizesOutcome =
        remote(ThumbnailSizesOutcome::Failure) { thumbnailSizes(profiles.toParcel()).toThumbnailSizesOutcome() }

    override suspend fun cancel(jobId: SliceJobId): Boolean {
        // Cancelling never starts the engine process. A completed connection
        // always holds a service: failed binds reset it to null.
        val current = synchronized(lock) { connected?.takeIf { it.isCompleted } } ?: return false
        return withContext(Dispatchers.IO) {
            try {
                current.await().cancel(jobId.value)
            } catch (_: RemoteException) {
                false
            }
        }
    }

    private suspend fun service(): ISlicerService {
        val deferred = synchronized(lock) {
            connected ?: CompletableDeferred<ISlicerService>().also(::bindLocked)
        }
        val service = deferred.await()
        plateTelling.withLock {
            val selected = synchronized(lock) { plate?.takeIf { plateKnownBy !== service } } ?: return@withLock
            val told = withContext(Dispatchers.IO) {
                try {
                    service.selectPlate(selected.first, selected.second)
                    true
                } catch (_: RemoteException) {
                    false
                }
            }
            synchronized(lock) { if (told && plate == selected) plateKnownBy = service }
        }
        val catalog = synchronized(lock) { language?.takeIf { languageKnownBy !== service } }
        if (catalog != null) {
            val told = withContext(Dispatchers.IO) {
                try {
                    service.setLanguage(catalog)
                    true
                } catch (_: RemoteException) {
                    false
                }
            }
            synchronized(lock) { if (told && language == catalog) languageKnownBy = service }
        }
        return service
    }

    private fun bindLocked(deferred: CompletableDeferred<ISlicerService>) {
        connected = deferred
        val intent = Intent(applicationContext, serviceClass)
        if (!applicationContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)) {
            applicationContext.unbindService(connection)
            connected = null
            deferred.completeExceptionally(IllegalStateException("Cannot bind ${serviceClass.name}"))
        }
    }

    private companion object {
        const val PROCESS_DIED = "The slicing engine process terminated unexpectedly"
    }
}
