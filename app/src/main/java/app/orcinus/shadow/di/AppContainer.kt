package app.orcinus.shadow.di

import app.orcinus.shadow.domain.plate.SliceAllPlatesUseCase
import app.orcinus.shadow.domain.plate.SliceActionUseCase
import app.orcinus.shadow.domain.plate.SetSliceModeUseCase
import app.orcinus.shadow.domain.plate.SelectSlicedPlateUseCase
import app.orcinus.shadow.domain.plate.AddPrimitiveUseCase
import app.orcinus.shadow.domain.plate.PlateObjectsUseCase
import app.orcinus.shadow.domain.plate.SetPlateSettingsUseCase
import app.orcinus.shadow.domain.plate.LockPlateUseCase
import app.orcinus.shadow.domain.plate.RenamePlateUseCase
import app.orcinus.shadow.domain.plate.MovePlateToFrontUseCase
import app.orcinus.shadow.domain.plate.PlateJobsUseCase
import app.orcinus.shadow.domain.plate.SelectPlateUseCase
import app.orcinus.shadow.domain.plate.AddPlateUseCase
import app.orcinus.shadow.domain.plate.DeletePlateUseCase
import app.orcinus.shadow.domain.plate.EnginePlateSync
import app.orcinus.shadow.domain.plate.ProjectLifecycleUseCase
import app.orcinus.shadow.core.model.PresetNameOutcome
import app.orcinus.shadow.domain.plate.SaveProjectUseCase
import app.orcinus.shadow.domain.plate.CopyProcessSettingsUseCase
import app.orcinus.shadow.domain.plate.ExportObjectMeshUseCase
import app.orcinus.shadow.domain.plate.PasteProcessSettingsUseCase
import app.orcinus.shadow.domain.plate.ReplaceObjectVolumeUseCase
import app.orcinus.shadow.domain.plate.SetFlushOptionUseCase
import app.orcinus.shadow.domain.plate.ApplySimplifyUseCase
import app.orcinus.shadow.domain.plate.OpenSimplifyUseCase
import app.orcinus.shadow.domain.plate.ChangeVolumeTypeUseCase
import app.orcinus.shadow.domain.plate.EditLayerGcodesUseCase
import app.orcinus.shadow.domain.plate.ReplaceAllVolumesUseCase
import app.orcinus.shadow.storage.android.AppDocumentFolders
import app.orcinus.shadow.domain.plate.PreviewSimplifyUseCase
import app.orcinus.shadow.domain.plate.ClonePlateObjectsUseCase
import app.orcinus.shadow.domain.plate.CopyToClipboardUseCase
import app.orcinus.shadow.domain.plate.PasteFromClipboardUseCase
import app.orcinus.shadow.domain.plate.EditPlateObjectUseCase
import app.orcinus.shadow.domain.plate.FillBedWithInstancesUseCase
import app.orcinus.shadow.domain.plate.SeparatePlateInstancesUseCase
import app.orcinus.shadow.domain.plate.SetArrangeSettingsUseCase
import app.orcinus.shadow.domain.plate.DismissPlateNoticeUseCase
import app.orcinus.shadow.domain.plate.AnswerPlateQuestionUseCase
import android.content.Context
import app.orcinus.shadow.BuildConfig
import app.orcinus.shadow.OrcaSlicerService
import app.orcinus.shadow.R
import app.orcinus.shadow.core.model.AppInfo
import app.orcinus.shadow.core.model.ComponentId
import app.orcinus.shadow.core.model.LicenseId
import app.orcinus.shadow.core.model.OutputPath
import app.orcinus.shadow.core.model.PhysicalPrinter
import app.orcinus.shadow.core.model.PrintHostTestOutcome
import app.orcinus.shadow.core.model.PrintHostUploadOutcome
import app.orcinus.shadow.core.model.PrintOptions
import app.orcinus.shadow.core.model.PrinterSlotsOutcome
import app.orcinus.shadow.domain.plate.CustomFilamentsUseCase
import app.orcinus.shadow.domain.plate.CustomPrinterUseCase
import app.orcinus.shadow.domain.plate.DeletePhysicalPrinterUseCase
import app.orcinus.shadow.domain.plate.PrinterPresetNamesUseCase
import app.orcinus.shadow.domain.plate.TestPhysicalPrinterUseCase
import app.orcinus.shadow.domain.plate.GcodeSender
import app.orcinus.shadow.domain.plate.ObservePhysicalPrintersUseCase
import app.orcinus.shadow.domain.plate.SavePhysicalPrinterUseCase
import app.orcinus.shadow.domain.plate.SendGcodeUseCase
import app.orcinus.shadow.network.printhost.PrintHostUploader
import java.io.File
import app.orcinus.shadow.domain.plate.ExportGcodeUseCase
import app.orcinus.shadow.storage.android.AppDocumentExport
import app.orcinus.shadow.core.model.PresetKind
import app.orcinus.shadow.data.notices.AboutLibrariesNoticeCatalog
import app.orcinus.shadow.data.plate.InMemoryPlateRepository
import app.orcinus.shadow.domain.CancelSliceUseCase
import app.orcinus.shadow.domain.DescribeFlatteningPlanesUseCase
import app.orcinus.shadow.domain.GetEngineStatusUseCase
import app.orcinus.shadow.domain.ImportModelUseCase
import app.orcinus.shadow.domain.InspectModelUseCase
import app.orcinus.shadow.domain.PlaceModelUseCase
import app.orcinus.shadow.domain.PlaceModelsUseCase
import app.orcinus.shadow.domain.SliceModelUseCase
import app.orcinus.shadow.domain.about.GetLicenseUseCase
import app.orcinus.shadow.domain.about.GetThirdPartyComponentUseCase
import app.orcinus.shadow.domain.about.GetThirdPartyComponentsUseCase
import app.orcinus.shadow.domain.plate.AddCalibrationCubeToPlateUseCase
import app.orcinus.shadow.domain.plate.AddModelToPlateUseCase
import app.orcinus.shadow.domain.plate.ApplySetupUseCase
import app.orcinus.shadow.domain.plate.CancelPlateSlicingUseCase
import app.orcinus.shadow.domain.plate.AddObjectPartUseCase
import app.orcinus.shadow.domain.plate.AddPlateInstanceUseCase
import app.orcinus.shadow.domain.plate.RemoveObjectPartUseCase
import app.orcinus.shadow.domain.plate.RemoveLastPlateInstancesUseCase
import app.orcinus.shadow.domain.plate.RenamePlateItemUseCase
import app.orcinus.shadow.domain.plate.SetNumberOfInstancesUseCase
import app.orcinus.shadow.domain.plate.RemovePlateInstanceUseCase
import app.orcinus.shadow.domain.plate.DeletePlateObjectUseCase
import app.orcinus.shadow.domain.plate.DismissPlateProblemUseCase
import app.orcinus.shadow.domain.plate.GetSetupFilamentsUseCase
import app.orcinus.shadow.domain.plate.GetSetupPrintersUseCase
import app.orcinus.shadow.domain.plate.ObservePlateUseCase
import app.orcinus.shadow.domain.plate.PlacePlateObjectUseCase
import app.orcinus.shadow.domain.plate.PlacePlateObjectsUseCase
import app.orcinus.shadow.domain.plate.PlateFilamentsUseCase
import app.orcinus.shadow.domain.plate.PlatePresets
import app.orcinus.shadow.domain.plate.PresetSettingsTabs
import app.orcinus.shadow.domain.plate.SelectPresetUseCase
import app.orcinus.shadow.domain.plate.SetPlateObjectAutoDropUseCase
import app.orcinus.shadow.domain.plate.AddLayerRangeUseCase
import app.orcinus.shadow.domain.plate.EditLayerRangeUseCase
import app.orcinus.shadow.domain.plate.RemoveLayerRangeUseCase
import app.orcinus.shadow.domain.plate.SelectLayerRangeUseCase
import app.orcinus.shadow.domain.plate.ExportConfigUseCase
import app.orcinus.shadow.domain.plate.ImportConfigUseCase
import app.orcinus.shadow.domain.plate.SelectObjectPartUseCase
import app.orcinus.shadow.domain.plate.SelectPlateObjectUseCase
import app.orcinus.shadow.domain.plate.SetPlateObjectPrintableUseCase
import app.orcinus.shadow.domain.plate.DescribeFlushVolumesUseCase
import app.orcinus.shadow.domain.plate.MoveWipeTowerUseCase
import app.orcinus.shadow.domain.plate.PaintObjectUseCase
import app.orcinus.shadow.domain.plate.SetFlushVolumesUseCase
import app.orcinus.shadow.domain.plate.SetBedShapeUseCase
import app.orcinus.shadow.domain.plate.UpdateFlushVolumesUseCase
import app.orcinus.shadow.domain.plate.PlateThumbnailRenderer
import app.orcinus.shadow.domain.plate.RenderThumbnailsUseCase
import app.orcinus.shadow.render.scene.ThumbnailRenderer
import app.orcinus.shadow.domain.plate.WipeTowerUpdates
import app.orcinus.shadow.domain.plate.ObjectMeshRetention
import app.orcinus.shadow.domain.plate.UndoRedoPlateUseCase
import app.orcinus.shadow.domain.plate.SetExtruderUseCase
import app.orcinus.shadow.domain.plate.SetSettingsScopeUseCase
import app.orcinus.shadow.domain.plate.SlicePlateUseCase
import app.orcinus.shadow.domain.plate.StartEngineUseCase
import app.orcinus.shadow.feature.about.NoticeViewModel
import app.orcinus.shadow.feature.about.ThirdPartyViewModel
import app.orcinus.shadow.feature.about.navigation.AboutViewModelFactory
import app.orcinus.shadow.feature.prepare.PrepareViewModel
import app.orcinus.shadow.feature.preview.PreviewViewModel
import app.orcinus.shadow.feature.setup.SetupStart
import app.orcinus.shadow.feature.setup.SetupWizardViewModel
import app.orcinus.shadow.feature.settings.PresetSettingsViewModel
import app.orcinus.shadow.feature.sidebar.SidebarViewModel
import app.orcinus.shadow.slicing.service.RemoteSlicerEngine
import app.orcinus.shadow.storage.android.AppConfigFiles
import app.orcinus.shadow.storage.android.AppGcodeOutputs
import app.orcinus.shadow.storage.android.AppPlateCache
import app.orcinus.shadow.storage.android.AppSceneFiles
import app.orcinus.shadow.storage.android.ContentResolverModelFileImporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Composition root of the UI process: adapters, the plate repository, and the
 * use cases every screen gets. Created once per process by the Application.
 */
class AppContainer(context: Context) : AboutViewModelFactory {
    private val applicationContext = context.applicationContext

    /** Work that must outlive a screen, such as a running slice. */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val engine = RemoteSlicerEngine(applicationContext, OrcaSlicerService::class.java)
    private val plateRepository = InMemoryPlateRepository()
    private val sceneFiles = AppSceneFiles(applicationContext)
    private val plateCache = AppPlateCache(applicationContext)
    private val inspectModel = InspectModelUseCase(engine)
    private val placePlateObjects = PlacePlateObjectsUseCase(PlaceModelsUseCase(engine), plateRepository, applicationScope)
    // Sidebar::auto_calc_flushing_volumes(), which filament, printer and settings changes ask for.
    private val flushVolumes = UpdateFlushVolumesUseCase(engine, plateRepository)
    private val settingsTabs = PresetSettingsTabs(engine, engine, flushVolumes, plateRepository, applicationScope) { platePresets }
    private val platePresets: PlatePresets = PlatePresets(engine, sceneFiles, plateCache, plateRepository, placePlateObjects, settingsTabs)
    private val selectPreset = SelectPresetUseCase(engine, platePresets, flushVolumes, settingsTabs, plateRepository, applicationScope)
    private val applySetup = ApplySetupUseCase(engine, platePresets, plateRepository, applicationScope)

    /**
     * Starts the engine as soon as the process does: binding the service starts
     * the :slicer process, which loads OrcaSlicer's profiles. The app shell
     * calls [startEngine] again when it composes; a started engine returns at once.
     */
    fun startEngineEarly() {
        applicationScope.launch { startEngine() }
        // The engine works on the plate the app shows.
        EnginePlateSync(engine, plateRepository, placePlateObjects, applicationScope).start()
        // The wipe tower follows the plate, as the desktop canvas rebuilds it.
        wipeTowerUpdates.start()
        // Meshes go once neither the plate, its undo/redo stack nor the clipboard needs them.
        applicationScope.launch { ObjectMeshRetention(plateRepository, sceneFiles).run() }
    }

    val observePlate = ObservePlateUseCase(plateRepository)
    val startEngine = StartEngineUseCase(GetEngineStatusUseCase(engine), engine, platePresets, sceneFiles, plateCache, plateRepository)
    // The desktop app renders the G-code thumbnails with its 3D view's
    // renderer; the app's renderer draws them offscreen before it slices.
    private val thumbnailRenderer = ThumbnailRenderer(applicationContext)
    private val plateThumbnails = PlateThumbnailRenderer { objects, plate, origin, colors, sizes, printableOnly, fileFor ->
        thumbnailRenderer.render(objects, plate, origin, colors, sizes, printableOnly, fileFor)
    }
    private val renderThumbnails = RenderThumbnailsUseCase(
        engine = engine,
        renderer = plateThumbnails,
        sceneFiles = sceneFiles,
    )
    val slicePlate = SlicePlateUseCase(
        SliceModelUseCase(engine),
        renderThumbnails,
        AppGcodeOutputs(applicationContext),
        sceneFiles,
        plateRepository,
        applicationScope,
    )
    private val sliceAllPlates = SliceAllPlatesUseCase(slicePlate, plateRepository, applicationScope)
    val sliceAction = SliceActionUseCase(slicePlate, sliceAllPlates, plateRepository)
    val setSliceMode = SetSliceModeUseCase(plateRepository)
    private val saveProject = SaveProjectUseCase(engine, plateThumbnails, sceneFiles, AppDocumentExport(applicationContext), plateRepository, applicationScope)
    val projectLifecycle = ProjectLifecycleUseCase(plateRepository, saveProject, engine, engine, platePresets, applicationScope)

    /** SavePresetDialog's check of a name, which the project's questions ask too. */
    suspend fun checkPresetName(kind: PresetKind, name: String): PresetNameOutcome = engine.checkPresetName(kind, name)

    val addModelToPlate = AddModelToPlateUseCase(
        importModel = ImportModelUseCase(ContentResolverModelFileImporter(applicationContext)),
        inspector = engine,
        sceneFiles = sceneFiles,
        repository = plateRepository,
        placePlateObjects = placePlateObjects,
        presetManager = engine,
        platePresets = platePresets,
        confirmClose = projectLifecycle,
        applicationScope = applicationScope,
    )
    private val addPrimitive = AddPrimitiveUseCase(engine, sceneFiles, plateRepository, applicationScope)
    private val addCalibrationCube = AddCalibrationCubeToPlateUseCase(inspectModel, sceneFiles, plateRepository, applicationScope)
    private val cancelPlateSlicing = CancelPlateSlicingUseCase(CancelSliceUseCase(engine), plateRepository, applicationScope)
    // Sidebar: the filaments the plate prints with.
    private val plateFilaments = PlateFilamentsUseCase(engine, platePresets, flushVolumes, plateRepository, applicationScope)
    private val selectPlateObject = SelectPlateObjectUseCase(plateRepository)
    private val selectObjectPart = SelectObjectPartUseCase(plateRepository)
    private val setBedShape = SetBedShapeUseCase(settingsTabs, engine, platePresets)
    private val configFiles = AppConfigFiles(applicationContext)
    // PrintHost::upload: the sliced G-code goes to the printer over the network.
    private val gcodeSender = object : GcodeSender {
        private val uploader = PrintHostUploader()

        override suspend fun send(
            printer: PhysicalPrinter,
            gcode: OutputPath,
            startPrint: Boolean,
            options: PrintOptions,
            onProgress: (Float) -> Unit,
        ): PrintHostUploadOutcome {
            val file = File(gcode.value)
            return uploader.upload(printer, file, file.name, startPrint, options) { sent, total ->
                if (total > 0) onProgress(sent.toFloat() / total)
            }
        }

        override suspend fun slots(printer: PhysicalPrinter): PrinterSlotsOutcome = uploader.printerSlots(printer)

        override suspend fun test(printer: PhysicalPrinter): PrintHostTestOutcome = uploader.test(printer)
    }
    val physicalPrinters = ObservePhysicalPrintersUseCase(engine)
    val savePhysicalPrinter = SavePhysicalPrinterUseCase(engine)
    val deletePhysicalPrinter = DeletePhysicalPrinterUseCase(engine)
    val printerPresetNames = PrinterPresetNamesUseCase(engine)
    val testPhysicalPrinter = TestPhysicalPrinterUseCase(gcodeSender)
    val sendGcode = SendGcodeUseCase(gcodeSender, plateRepository)
    val exportGcode = ExportGcodeUseCase(AppDocumentExport(applicationContext), plateRepository)
    private val importConfig = ImportConfigUseCase(engine, engine, configFiles, platePresets)
    private val exportConfig = ExportConfigUseCase(engine, configFiles)
    private val addLayerRange = AddLayerRangeUseCase(plateRepository)
    private val removeLayerRange = RemoveLayerRangeUseCase(plateRepository)
    private val selectLayerRange = SelectLayerRangeUseCase(plateRepository)
    private val editLayerRange = EditLayerRangeUseCase(plateRepository)
    private val setExtruder = SetExtruderUseCase(plateRepository)
    private val moveWipeTower = MoveWipeTowerUseCase(plateRepository)
    private val selectPlate = SelectPlateUseCase(plateRepository)
    private val deletePlate = DeletePlateUseCase(plateRepository)
    private val lockPlate = LockPlateUseCase(plateRepository)
    private val renamePlate = RenamePlateUseCase(plateRepository)
    private val plateJobs by lazy { PlateJobsUseCase(plateRepository, selectPlate, placePlateObjects, applicationScope) }
    private val wipeTowerUpdates = WipeTowerUpdates(engine, plateRepository, applicationScope)
    private val setSettingsScope = SetSettingsScopeUseCase(plateRepository)
    // The object list of the sidebar and the object menu of the 3D view share them.
    private val placePlateObject = PlacePlateObjectUseCase(PlaceModelUseCase(engine), plateRepository, applicationScope)
    private val setPlateObjectAutoDrop = SetPlateObjectAutoDropUseCase(plateRepository, placePlateObject)
    private val setPlateObjectPrintable = SetPlateObjectPrintableUseCase(plateRepository)
    private val deletePlateObject = DeletePlateObjectUseCase(plateRepository)
    private val addPlateInstance = AddPlateInstanceUseCase(plateRepository, placePlateObject, selectPlateObject)
    private val addObjectPart = AddObjectPartUseCase(engine, sceneFiles, plateRepository, applicationScope)
    private val removeObjectPart = RemoveObjectPartUseCase(plateRepository)
    private val removePlateInstance = RemovePlateInstanceUseCase(plateRepository, deletePlateObject)
    private val removeLastPlateInstances = RemoveLastPlateInstancesUseCase(plateRepository, deletePlateObject)
    private val setNumberOfInstances = SetNumberOfInstancesUseCase(plateRepository, addPlateInstance, removeLastPlateInstances, deletePlateObject)
    private val renamePlateItem = RenamePlateItemUseCase(plateRepository)
    private val editPlateObject = EditPlateObjectUseCase(engine, sceneFiles, plateRepository, applicationScope)
    private val clonePlateObjects = ClonePlateObjectsUseCase(engine, sceneFiles, plateRepository, placePlateObjects, applicationScope)
    private val separatePlateInstances = SeparatePlateInstancesUseCase(engine, sceneFiles, plateRepository, applicationScope)
    private val fillBedWithInstances = FillBedWithInstancesUseCase(plateRepository, placePlateObjects)
    private val copyToClipboard = CopyToClipboardUseCase(
        engine,
        sceneFiles,
        plateRepository,
        removeObjectPart,
        applicationScope,
    )
    private val pasteFromClipboard = PasteFromClipboardUseCase(engine, sceneFiles, plateRepository, applicationScope)
    private val undoRedoPlate = UndoRedoPlateUseCase(plateRepository, placePlateObjects, settingsTabs, applicationScope)
    val answerPlateQuestion = AnswerPlateQuestionUseCase(plateRepository, addModelToPlate, editPlateObject, settingsTabs)
    private val setFlushOption = SetFlushOptionUseCase(plateRepository, settingsTabs, applicationScope)
    private val openSimplify = OpenSimplifyUseCase(plateRepository)
    private val replaceAllVolumes = ReplaceAllVolumesUseCase(
        ImportModelUseCase(ContentResolverModelFileImporter(applicationContext)),
        AppDocumentFolders(applicationContext),
        engine,
        sceneFiles,
        plateRepository,
        applicationScope,
    )
    private val copyProcessSettings = CopyProcessSettingsUseCase(plateRepository)
    private val pasteProcessSettings = PasteProcessSettingsUseCase(engine, plateRepository, settingsTabs, applicationScope)
    private val exportObjectMesh = ExportObjectMeshUseCase(engine, sceneFiles, AppDocumentExport(applicationContext), plateRepository)
    private val replaceObjectVolume = ReplaceObjectVolumeUseCase(
        ImportModelUseCase(ContentResolverModelFileImporter(applicationContext)),
        engine,
        sceneFiles,
        plateRepository,
        applicationScope,
    )
    val dismissPlateNotice = DismissPlateNoticeUseCase(plateRepository)
    private val dismissPlateProblem = DismissPlateProblemUseCase(plateRepository)

    val appInfo = AppInfo(
        name = applicationContext.getString(R.string.app_name),
        version = BuildConfig.VERSION_NAME,
        orcaRelease = BuildConfig.ORCA_RELEASE,
        sourceUrl = BuildConfig.SOURCE_URL.ifBlank { null },
        license = LicenseId(BuildConfig.LICENSE_ID),
    )

    // Generated at build time by the AboutLibraries plugin (see app/build.gradle.kts).
    private val noticeCatalog = AboutLibrariesNoticeCatalog(
        readDefinitions = { applicationContext.resources.openRawResource(R.raw.aboutlibraries).use { it.readBytes().decodeToString() } },
    )

    fun prepareViewModel(): PrepareViewModel {
        return PrepareViewModel(
            observePlate = observePlate,
            addModelToPlate = addModelToPlate,
            addPrimitive = addPrimitive,
            addCalibrationCubeToPlate = addCalibrationCube,
            placePlateObject = placePlateObject,
            placePlateObjects = placePlateObjects,
            setPlateObjectAutoDrop = setPlateObjectAutoDrop,
            addPlateInstance = addPlateInstance,
            removePlateInstance = removePlateInstance,
            removeLastPlateInstances = removeLastPlateInstances,
            setNumberOfInstances = setNumberOfInstances,
            addObjectPart = addObjectPart,
            addLayerRange = addLayerRange,
            selectLayerRange = selectLayerRange,
            setSettingsScope = setSettingsScope,
            editPlateObject = editPlateObject,
            clonePlateObjects = clonePlateObjects,
            separatePlateInstances = separatePlateInstances,
            fillBedWithInstances = fillBedWithInstances,
            setArrangeSettings = SetArrangeSettingsUseCase(plateRepository),
            copyToClipboard = copyToClipboard,
            pasteFromClipboard = pasteFromClipboard,
            undoRedoPlate = undoRedoPlate,
            deletePlateObject = deletePlateObject,
            describeFlatteningPlanes = DescribeFlatteningPlanesUseCase(engine),
            selectPlateObject = selectPlateObject,
            moveTower = moveWipeTower,
            paintObject = PaintObjectUseCase(engine, sceneFiles, plateRepository),
            sliceAction = sliceAction,
            setSliceMode = setSliceMode,
            cancelPlateSlicing = cancelPlateSlicing,
            dismissPlateProblem = dismissPlateProblem,
            setPlateObjectPrintable = setPlateObjectPrintable,
            setExtruder = setExtruder,
            setFlushOption = setFlushOption,
            copyProcessSettings = copyProcessSettings,
            pasteProcessSettings = pasteProcessSettings,
            exportObjectMesh = exportObjectMesh,
            replaceObjectVolume = replaceObjectVolume,
            openSimplify = openSimplify,
            previewSimplify = PreviewSimplifyUseCase(engine, sceneFiles, plateRepository),
            applySimplifyUseCase = ApplySimplifyUseCase(engine, sceneFiles, plateRepository, applicationScope),
            replaceAllVolumesUseCase = replaceAllVolumes,
            selectPlate = selectPlate,
            addPlate = AddPlateUseCase(plateRepository),
            deletePlate = deletePlate,
            lockPlate = lockPlate,
            renamePlate = renamePlate,
            movePlateToFront = MovePlateToFrontUseCase(plateRepository),
            plateJobs = plateJobs,
            setPlateSettings = SetPlateSettingsUseCase(plateRepository),
        )
    }

    fun previewViewModel() = PreviewViewModel(
        observePlate = observePlate,
        sliceAction = sliceAction,
        setSliceMode = setSliceMode,
        selectSlicedPlate = SelectSlicedPlateUseCase(selectPlate, slicePlate, plateRepository, applicationScope),
        physicalPrinters = physicalPrinters,
        savePhysicalPrinter = savePhysicalPrinter,
        deletePhysicalPrinter = deletePhysicalPrinter,
        printerPresetNames = printerPresetNames,
        sendGcode = sendGcode,
        exportGcode = exportGcode,
        editLayerGcodes = EditLayerGcodesUseCase(plateRepository),
    )

    fun sidebarViewModel() = SidebarViewModel(
        observePlate = observePlate,
        selectPreset = selectPreset,
        settingsTabs = settingsTabs,
        customPrinter = CustomPrinterUseCase(engine, platePresets),
        setBedShape = setBedShape,
        selectPlateObject = selectPlateObject,
        selectObjectPart = selectObjectPart,
        addLayerRange = addLayerRange,
        removeLayerRange = removeLayerRange,
        selectLayerRange = selectLayerRange,
        editLayerRange = editLayerRange,
        setExtruder = setExtruder,
        describeFlush = DescribeFlushVolumesUseCase(engine, plateRepository),
        setFlush = SetFlushVolumesUseCase(plateRepository),
        importConfig = importConfig,
        exportConfig = exportConfig,
        setSettingsScope = setSettingsScope,
        setPlateObjectPrintable = setPlateObjectPrintable,
        setPlateObjectAutoDrop = setPlateObjectAutoDrop,
        plateFilaments = plateFilaments,
        addPlateInstance = addPlateInstance,
        addObjectPart = addObjectPart,
        removeObjectPart = removeObjectPart,
        removePlateInstance = removePlateInstance,
        clonePlateObjects = clonePlateObjects,
        separatePlateInstances = separatePlateInstances,
        fillBedWithInstances = fillBedWithInstances,
        copyToClipboard = copyToClipboard,
        pasteFromClipboard = pasteFromClipboard,
        removeLastPlateInstances = removeLastPlateInstances,
        setNumberOfInstances = setNumberOfInstances,
        placePlateObject = placePlateObject,
        renamePlateItem = renamePlateItem,
        editPlateObject = editPlateObject,
        deletePlateObject = deletePlateObject,
        physicalPrinters = physicalPrinters,
        savePhysicalPrinter = savePhysicalPrinter,
        deletePhysicalPrinter = deletePhysicalPrinter,
        testPhysicalPrinter = testPhysicalPrinter,
        printerPresetNames = printerPresetNames,
        setFlushOption = setFlushOption,
        copySettings = copyProcessSettings,
        pasteSettings = pasteProcessSettings,
        exportObjectMesh = exportObjectMesh,
        replaceObjectVolume = replaceObjectVolume,
        openSimplify = openSimplify,
        changeVolumeType = ChangeVolumeTypeUseCase(engine, sceneFiles, plateRepository, applicationScope),
        replaceAllVolumesUseCase = replaceAllVolumes,
        saveProject = saveProject,
        projectLifecycle = projectLifecycle,
        addModelToPlate = addModelToPlate,
        selectPlate = selectPlate,
        plateObjects = PlateObjectsUseCase(plateRepository),
        plateJobs = plateJobs,
        deletePlate = deletePlate,
        lockPlate = lockPlate,
        renamePlate = renamePlate,
        addPrimitive = addPrimitive,
    )

    fun presetSettingsViewModel(kind: PresetKind) = PresetSettingsViewModel(
        kind = kind,
        observePlate = observePlate,
        selectPreset = selectPreset,
        settingsTabs = settingsTabs,
        setBedShape = setBedShape,
    )

    fun setupWizardViewModel(start: SetupStart) = SetupWizardViewModel(
        start = start,
        getSetupPrinters = GetSetupPrintersUseCase(engine),
        getSetupFilaments = GetSetupFilamentsUseCase(engine),
        applySetup = applySetup,
        customFilaments = CustomFilamentsUseCase(engine, platePresets),
    )

    override fun thirdPartyViewModel() = ThirdPartyViewModel(GetThirdPartyComponentsUseCase(noticeCatalog))

    override fun componentNoticeViewModel(id: ComponentId) = NoticeViewModel.forComponent(id, GetThirdPartyComponentUseCase(noticeCatalog))

    override fun licenseNoticeViewModel(id: LicenseId) = NoticeViewModel.forLicense(id, GetLicenseUseCase(noticeCatalog))
}
