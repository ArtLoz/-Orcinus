package app.orcinus.shadow.di

import android.content.Context
import android.net.wifi.WifiManager
import app.orcinus.shadow.AndroidAppLanguage
import app.orcinus.shadow.BuildConfig
import app.orcinus.shadow.NetworkWork
import app.orcinus.shadow.OrcaSlicerService
import app.orcinus.shadow.R
import app.orcinus.shadow.core.model.AppInfo
import app.orcinus.shadow.core.model.BonjourReply
import app.orcinus.shadow.core.model.CloudLoginOutcome
import app.orcinus.shadow.core.model.ComponentId
import app.orcinus.shadow.core.model.CrealityHost
import app.orcinus.shadow.core.model.FlashforgeDiscoveryOutcome
import app.orcinus.shadow.core.model.FlashforgeSlotsOutcome
import app.orcinus.shadow.core.model.HostPrintersOutcome
import app.orcinus.shadow.core.model.LicenseId
import app.orcinus.shadow.core.model.OutputPath
import app.orcinus.shadow.core.model.PhysicalPrinter
import app.orcinus.shadow.core.model.PresetKind
import app.orcinus.shadow.core.model.PresetNameOutcome
import app.orcinus.shadow.core.model.PrintHostTestOutcome
import app.orcinus.shadow.core.model.PrintHostUploadOutcome
import app.orcinus.shadow.core.model.PrintOptions
import app.orcinus.shadow.core.model.Printer3dOsListsOutcome
import app.orcinus.shadow.core.model.PrinterSlotsOutcome
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
import app.orcinus.shadow.domain.plate.AddLayerRangeUseCase
import app.orcinus.shadow.domain.plate.AddModelToPlateUseCase
import app.orcinus.shadow.domain.plate.AddObjectPartUseCase
import app.orcinus.shadow.domain.plate.AddPlateInstanceUseCase
import app.orcinus.shadow.domain.plate.AddPlateUseCase
import app.orcinus.shadow.domain.plate.AddPrimitiveUseCase
import app.orcinus.shadow.domain.plate.AnswerPlateQuestionUseCase
import app.orcinus.shadow.domain.plate.ApplySetupUseCase
import app.orcinus.shadow.domain.plate.AutoSliceUseCase
import app.orcinus.shadow.domain.plate.ApplySimplifyUseCase
import app.orcinus.shadow.domain.plate.BrowsePrintHostsUseCase
import app.orcinus.shadow.domain.plate.CalibrateUseCase
import app.orcinus.shadow.domain.plate.CloudLoginUseCase
import app.orcinus.shadow.domain.plate.DevicePageUseCase
import app.orcinus.shadow.domain.plate.CancelPlateSlicingUseCase
import app.orcinus.shadow.domain.plate.ChangeVolumeTypeUseCase
import app.orcinus.shadow.domain.plate.ClonePlateObjectsUseCase
import app.orcinus.shadow.domain.plate.CopyProcessSettingsUseCase
import app.orcinus.shadow.domain.plate.CopyToClipboardUseCase
import app.orcinus.shadow.domain.plate.CustomFilamentsUseCase
import app.orcinus.shadow.domain.plate.CustomPrinterUseCase
import app.orcinus.shadow.domain.plate.CutObjectUseCase
import app.orcinus.shadow.domain.plate.DeletePlateObjectUseCase
import app.orcinus.shadow.domain.plate.DeletePlateUseCase
import app.orcinus.shadow.domain.plate.DescribeCalibrationPrinterUseCase
import app.orcinus.shadow.domain.plate.DescribeFlushVolumesUseCase
import app.orcinus.shadow.domain.plate.DismissPlateNoticeUseCase
import app.orcinus.shadow.domain.plate.DismissPlateProblemUseCase
import app.orcinus.shadow.domain.plate.EditLayerGcodesUseCase
import app.orcinus.shadow.domain.plate.EditLayerRangeUseCase
import app.orcinus.shadow.domain.plate.EditPlateObjectUseCase
import app.orcinus.shadow.domain.plate.EnablePaintedFuzzySkinUseCase
import app.orcinus.shadow.domain.plate.EnginePlateSync
import app.orcinus.shadow.domain.plate.ExportConfigUseCase
import app.orcinus.shadow.domain.plate.ExportGcodeUseCase
import app.orcinus.shadow.domain.plate.ExportObjectMeshUseCase
import app.orcinus.shadow.domain.plate.FillBedWithInstancesUseCase
import app.orcinus.shadow.domain.plate.GcodeSender
import app.orcinus.shadow.domain.plate.GetSetupFilamentsUseCase
import app.orcinus.shadow.domain.plate.GetSetupPrintersUseCase
import app.orcinus.shadow.domain.plate.ImportConfigUseCase
import app.orcinus.shadow.domain.plate.InvalidateCutInfoUseCase
import app.orcinus.shadow.domain.plate.ListHostPrintersUseCase
import app.orcinus.shadow.domain.plate.LockPlateUseCase
import app.orcinus.shadow.domain.plate.MovePlateToFrontUseCase
import app.orcinus.shadow.domain.plate.MoveWipeTowerUseCase
import app.orcinus.shadow.domain.plate.ObjectMeshRetention
import app.orcinus.shadow.domain.plate.ObservePlateUseCase
import app.orcinus.shadow.domain.plate.ObservePrinterConnectionUseCase
import app.orcinus.shadow.domain.plate.OpenSimplifyUseCase
import app.orcinus.shadow.domain.plate.PaintObjectUseCase
import app.orcinus.shadow.domain.plate.PasteFromClipboardUseCase
import app.orcinus.shadow.domain.plate.PasteProcessSettingsUseCase
import app.orcinus.shadow.domain.plate.PlacePlateObjectUseCase
import app.orcinus.shadow.domain.plate.PlacePlateObjectsUseCase
import app.orcinus.shadow.domain.plate.PlateFilamentsUseCase
import app.orcinus.shadow.domain.plate.PlateJobsUseCase
import app.orcinus.shadow.domain.plate.PlateObjectsUseCase
import app.orcinus.shadow.domain.plate.PlatePresets
import app.orcinus.shadow.domain.plate.PlateThumbnailRenderer
import app.orcinus.shadow.domain.plate.PresetSettingsTabs
import app.orcinus.shadow.domain.plate.PrintHostDiscovery
import app.orcinus.shadow.domain.plate.PreviewSimplifyUseCase
import app.orcinus.shadow.domain.plate.ProjectBackupUseCase
import app.orcinus.shadow.domain.plate.ProjectLifecycleUseCase
import app.orcinus.shadow.domain.plate.RemoveLastPlateInstancesUseCase
import app.orcinus.shadow.domain.plate.RemoveLayerRangeUseCase
import app.orcinus.shadow.domain.plate.RemoveObjectPartUseCase
import app.orcinus.shadow.domain.plate.RemovePlateInstanceUseCase
import app.orcinus.shadow.domain.plate.RenamePlateItemUseCase
import app.orcinus.shadow.domain.plate.RenamePlateUseCase
import app.orcinus.shadow.domain.plate.RenderThumbnailsUseCase
import app.orcinus.shadow.domain.plate.ReplaceAllVolumesUseCase
import app.orcinus.shadow.domain.plate.ReplaceObjectVolumeUseCase
import app.orcinus.shadow.domain.plate.SaveProjectUseCase
import app.orcinus.shadow.domain.plate.SelectLayerRangeUseCase
import app.orcinus.shadow.domain.plate.SelectObjectPartUseCase
import app.orcinus.shadow.domain.plate.SelectPlateObjectUseCase
import app.orcinus.shadow.domain.plate.SelectPlateUseCase
import app.orcinus.shadow.domain.plate.SelectPresetUseCase
import app.orcinus.shadow.domain.plate.SelectSlicedPlateUseCase
import app.orcinus.shadow.domain.plate.SendGcodeUseCase
import app.orcinus.shadow.domain.plate.SeparatePlateInstancesUseCase
import app.orcinus.shadow.domain.plate.SetArrangeSettingsUseCase
import app.orcinus.shadow.domain.plate.SetBedShapeUseCase
import app.orcinus.shadow.domain.plate.SetExtruderUseCase
import app.orcinus.shadow.domain.plate.SetFlushOptionUseCase
import app.orcinus.shadow.domain.plate.SetFlushVolumesUseCase
import app.orcinus.shadow.domain.plate.SetNumberOfInstancesUseCase
import app.orcinus.shadow.domain.plate.SetPlateObjectAutoDropUseCase
import app.orcinus.shadow.domain.plate.SetPlateObjectPrintableUseCase
import app.orcinus.shadow.domain.plate.SetPlateSettingsUseCase
import app.orcinus.shadow.domain.plate.SetSettingsScopeUseCase
import app.orcinus.shadow.domain.plate.SetSliceModeUseCase
import app.orcinus.shadow.domain.plate.ShowAllPlatesStatsUseCase
import app.orcinus.shadow.domain.plate.SliceActionUseCase
import app.orcinus.shadow.domain.plate.SliceAllPlatesUseCase
import app.orcinus.shadow.domain.plate.SlicePlateUseCase
import app.orcinus.shadow.domain.plate.StartEngineUseCase
import app.orcinus.shadow.domain.plate.StepMeshPrompt
import app.orcinus.shadow.domain.plate.TestPhysicalPrinterUseCase
import app.orcinus.shadow.domain.plate.UndoRedoPlateUseCase
import app.orcinus.shadow.domain.plate.UpdateFlushVolumesUseCase
import app.orcinus.shadow.domain.plate.OverhangUpdates
import app.orcinus.shadow.domain.plate.PrintSequenceUpdates
import app.orcinus.shadow.domain.plate.WipeTowerUpdates
import app.orcinus.shadow.domain.preferences.AppPreferences
import app.orcinus.shadow.domain.preferences.SetPreferenceUseCase
import app.orcinus.shadow.feature.about.NoticeViewModel
import app.orcinus.shadow.feature.about.ThirdPartyViewModel
import app.orcinus.shadow.feature.about.navigation.AboutViewModelFactory
import app.orcinus.shadow.feature.device.DeviceViewModel
import app.orcinus.shadow.feature.preferences.PreferencesViewModel
import app.orcinus.shadow.feature.prepare.PrepareViewModel
import app.orcinus.shadow.feature.preview.PreviewViewModel
import app.orcinus.shadow.feature.settings.PresetSettingsViewModel
import app.orcinus.shadow.feature.setup.SetupStart
import app.orcinus.shadow.feature.setup.SetupWizardViewModel
import app.orcinus.shadow.feature.sidebar.SidebarViewModel
import app.orcinus.shadow.network.printhost.Bonjour
import app.orcinus.shadow.network.printhost.CrealityHostDiscovery
import app.orcinus.shadow.network.printhost.FlashforgeDiscovery
import app.orcinus.shadow.network.printhost.PrintHostUploader
import app.orcinus.shadow.render.scene.ThumbnailRenderer
import app.orcinus.shadow.slicing.service.RemoteSlicerEngine
import app.orcinus.shadow.storage.android.AppConfigFiles
import app.orcinus.shadow.storage.android.AppDocumentExport
import app.orcinus.shadow.storage.android.AppDocumentFolders
import app.orcinus.shadow.storage.android.AppGcodeOutputs
import app.orcinus.shadow.storage.android.AppPlateCache
import app.orcinus.shadow.storage.android.AppProjectBackupFiles
import app.orcinus.shadow.storage.android.AppSceneFiles
import app.orcinus.shadow.storage.android.ContentResolverModelFileImporter
import java.io.File
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
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

    // OrcaSlicer's Preferences, which the engine keeps in its app configuration.
    private val appPreferences = AppPreferences(engine)
    private val plateRepository = InMemoryPlateRepository()
    private val sceneFiles = AppSceneFiles(applicationContext)
    private val plateCache = AppPlateCache(applicationContext)
    private val inspectModel = InspectModelUseCase(engine)
    private val placePlateObjects = PlacePlateObjectsUseCase(PlaceModelsUseCase(engine), plateRepository, applicationScope)
    // Sidebar::auto_calc_flushing_volumes(), which filament, printer and settings changes ask for.
    private val flushVolumes = UpdateFlushVolumesUseCase(engine, plateRepository)
    // Plater::on_config_change() asks "Auto slice after changes", which is built with the slice below.
    private val settingsTabs = PresetSettingsTabs(
        engine,
        engine,
        flushVolumes,
        plateRepository,
        applicationScope,
        platePresets = { platePresets },
        onConfigChange = { autoSlice.onConfigChange() },
    )
    private val platePresets: PlatePresets =
        PlatePresets(engine, sceneFiles, plateCache, plateRepository, placePlateObjects, settingsTabs) { autoSlice.onConfigChange() }
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
        // The overhangs' angle follows the support settings of the edited presets.
        OverhangUpdates(engine, plateRepository, applicationScope).start()
        // The labels' print order follows the plate while they are shown.
        PrintSequenceUpdates(engine, plateRepository, appPreferences, applicationScope).start()
        // Meshes go once neither the plate, its undo/redo stack nor the clipboard needs them.
        applicationScope.launch { ObjectMeshRetention(plateRepository, sceneFiles).run() }
        // "Auto backup", and the restore of a project an earlier run left.
        projectBackup.start()
    }

    val observePlate = ObservePlateUseCase(plateRepository)
    val startEngine = StartEngineUseCase(GetEngineStatusUseCase(engine), engine, platePresets, sceneFiles, plateCache, plateRepository, appPreferences)
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
    val projectLifecycle = ProjectLifecycleUseCase(plateRepository, saveProject, engine, engine, platePresets, applicationScope, appPreferences)

    /** SavePresetDialog's check of a name, which the project's questions ask too. */
    suspend fun checkPresetName(kind: PresetKind, name: String): PresetNameOutcome = engine.checkPresetName(kind, name)

    // StepMeshDialog, which a load or a replacement of a STEP file waits for.
    val stepMeshPrompt = StepMeshPrompt(engine, appPreferences, plateRepository)

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
        preferences = appPreferences,
        stepMeshPrompt = stepMeshPrompt,
    )
    private val addPrimitive = AddPrimitiveUseCase(engine, sceneFiles, plateRepository, applicationScope)
    private val addCalibrationCube = AddCalibrationCubeToPlateUseCase(inspectModel, sceneFiles, plateRepository, applicationScope)
    private val cancelPlateSlicing = CancelPlateSlicingUseCase(CancelSliceUseCase(engine), plateRepository, applicationScope)
    val autoSlice = AutoSliceUseCase(appPreferences, slicePlate, cancelPlateSlicing, plateRepository, applicationScope)
    val projectBackup = ProjectBackupUseCase(
        engine,
        AppProjectBackupFiles(applicationContext),
        appPreferences,
        plateRepository,
        restore = addModelToPlate::restoreProject,
        applicationScope = applicationScope,
    )
    // Sidebar: the filaments the plate prints with.
    private val plateFilaments = PlateFilamentsUseCase(engine, platePresets, flushVolumes, plateRepository, applicationScope)
    private val selectPlateObject = SelectPlateObjectUseCase(plateRepository)
    private val selectObjectPart = SelectObjectPartUseCase(plateRepository)
    private val setBedShape = SetBedShapeUseCase(settingsTabs, engine, platePresets)
    private val configFiles = AppConfigFiles(applicationContext)
    // An upload and a login in the browser go on after the app's screen is left.
    private val networkWork = NetworkWork(applicationContext)

    // PrintHost::upload: the sliced G-code goes to the printer over the network.
    private val gcodeSender = object : GcodeSender {
        // SimplyPrint and 3DPrinterOS keep their logins in the app's own files, as the desktop keeps them in its data directory.
        private val uploader = PrintHostUploader(
            simplyPrintCredentials = File(applicationContext.filesDir, "simplyprint_oauth.json"),
            printer3dOsCredentials = File(applicationContext.filesDir, "3dprinteros_api_cred.json"),
        )

        override suspend fun send(
            printer: PhysicalPrinter,
            gcode: OutputPath,
            startPrint: Boolean,
            options: PrintOptions,
            onProgress: (Float) -> Unit,
        ): PrintHostUploadOutcome {
            val file = File(gcode.value)
            return networkWork.keep(applicationContext.getString(R.string.network_notification_send, printer.name)) {
                uploader.upload(printer, file, file.name, startPrint, options) { sent, total ->
                    if (total > 0) onProgress(sent.toFloat() / total)
                }
            }
        }

        override suspend fun slots(printer: PhysicalPrinter): PrinterSlotsOutcome = uploader.printerSlots(printer)

        override suspend fun flashforgeSlots(printer: PhysicalPrinter): FlashforgeSlotsOutcome = uploader.flashforgeSlots(printer)

        override suspend fun printer3dOsLists(printer: PhysicalPrinter): Printer3dOsListsOutcome = uploader.printer3dOsLists(printer)

        override suspend fun test(printer: PhysicalPrinter): PrintHostTestOutcome = uploader.test(printer)

        override suspend fun printers(printer: PhysicalPrinter): HostPrintersOutcome = uploader.printers(printer)

        override suspend fun serialNumber(printer: PhysicalPrinter, lookUp: Boolean): String = uploader.serialNumber(printer, lookUp)

        override suspend fun cloudLogin(printer: PhysicalPrinter, openPage: (String) -> Unit): CloudLoginOutcome {
            val host = printer.hostType?.label.orEmpty()
            return networkWork.keep(applicationContext.getString(R.string.network_notification_login, host)) {
                uploader.cloudLogin(printer, openPage)
            }
        }

        override suspend fun isLoggedIn(printer: PhysicalPrinter): Boolean = withContext(Dispatchers.IO) { uploader.isLoggedIn(printer) }

        override suspend fun logOut(printer: PhysicalPrinter) = withContext(Dispatchers.IO) { uploader.logOut(printer) }
    }
    val printerConnection = ObservePrinterConnectionUseCase(engine)

    // The Device tab's page; Elegoo's LAN page takes the app's language as OrcaSlicer's code, "ru_RU".
    private val devicePage = DevicePageUseCase(engine, gcodeSender, appPreferences) {
        Locale.getDefault().let { locale -> if (locale.country.isEmpty()) locale.language else "${locale.language}_${locale.country}" }
    }
    val testPhysicalPrinter = TestPhysicalPrinterUseCase(gcodeSender)
    private val listHostPrinters = ListHostPrintersUseCase(gcodeSender)
    private val cloudLogin = CloudLoginUseCase(gcodeSender)
    // PhysicalPrinterDialog's Browse button: the printers of the local network.
    private val browsePrintHosts = BrowsePrintHostsUseCase(
        object : PrintHostDiscovery {
            override fun lookup(service: String, txtKeys: Set<String>, retries: Int, timeoutSeconds: Int): Flow<BonjourReply> {
                val lookup = Bonjour(service, txtKeys = txtKeys, timeoutSeconds = timeoutSeconds, retries = retries).lookup()
                // Wi-Fi drops the multicast replies of mDNS unless the app holds a multicast lock.
                val wifi = applicationContext.getSystemService(WifiManager::class.java) ?: return lookup
                val lock = wifi.createMulticastLock("Bonjour").apply { setReferenceCounted(true) }
                return lookup
                    .onStart { lock.acquire() }
                    .onCompletion { if (lock.isHeld) lock.release() }
            }

            override suspend fun scanCreality(): List<CrealityHost> = CrealityHostDiscovery().scan()

            override suspend fun discoverFlashforge(): FlashforgeDiscoveryOutcome {
                // Wi-Fi drops broadcast answers as it drops multicast ones, unless the app holds the lock.
                val lock = applicationContext.getSystemService(WifiManager::class.java)?.createMulticastLock("Flashforge")
                lock?.acquire()
                return try {
                    FlashforgeDiscovery().discover()
                } finally {
                    if (lock?.isHeld == true) lock.release()
                }
            }
        },
    )
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
    private val invalidateCutInfo = InvalidateCutInfoUseCase(plateRepository)
    val answerPlateQuestion = AnswerPlateQuestionUseCase(
        plateRepository,
        addModelToPlate,
        editPlateObject,
        settingsTabs,
        deletePlateObject,
        copyToClipboard,
        invalidateCutInfo,
    )
    private val setFlushOption = SetFlushOptionUseCase(plateRepository, settingsTabs, applicationScope)
    private val openSimplify = OpenSimplifyUseCase(plateRepository)
    private val replaceAllVolumes = ReplaceAllVolumesUseCase(
        ImportModelUseCase(ContentResolverModelFileImporter(applicationContext)),
        AppDocumentFolders(applicationContext),
        engine,
        sceneFiles,
        plateRepository,
        applicationScope,
        stepMeshPrompt,
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
        stepMeshPrompt,
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
            invalidateCutInfo = invalidateCutInfo,
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
            enablePaintedFuzzySkin = EnablePaintedFuzzySkinUseCase(plateRepository, settingsTabs, applicationScope),
            cutObject = CutObjectUseCase(engine, sceneFiles, plateRepository),
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
            preferences = appPreferences,
            setPreference = setPreference,
        )
    }

    fun previewViewModel() = PreviewViewModel(
        observePlate = observePlate,
        sliceAction = sliceAction,
        setSliceMode = setSliceMode,
        selectSlicedPlate = SelectSlicedPlateUseCase(selectPlate, slicePlate, plateRepository, applicationScope),
        showAllPlatesStats = ShowAllPlatesStatsUseCase(sliceAllPlates, plateRepository),
        printerConnection = printerConnection,
        sendGcode = sendGcode,
        exportGcode = exportGcode,
        editLayerGcodes = EditLayerGcodesUseCase(plateRepository),
        preferences = appPreferences,
        setPreference = setPreference,
    )

    fun deviceViewModel() = DeviceViewModel(observePlate, devicePage, appPreferences)

    /** An item of the Preferences or of a canvas's View menu, written into OrcaSlicer.conf. */
    private val setPreference by lazy { SetPreferenceUseCase(appPreferences, settingsTabs, engine, platePresets, plateRepository, applicationScope) }

    /** The language the app shows, which Android keeps for it. */
    val appLanguage = AndroidAppLanguage(applicationContext)

    fun preferencesViewModel() = PreferencesViewModel(appPreferences, setPreference, appLanguage)

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
        invalidateCutInfo = invalidateCutInfo,
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
        printerConnection = printerConnection,
        testPhysicalPrinter = testPhysicalPrinter,
        browsePrintHosts = browsePrintHosts,
        listHostPrinters = listHostPrinters,
        cloudLogin = cloudLogin,
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
        calibrateUseCase = CalibrateUseCase(projectLifecycle, engine, engine, platePresets, sceneFiles, plateRepository, applicationScope),
        describeCalibrationPrinterUseCase = DescribeCalibrationPrinterUseCase(engine, plateRepository),
        addModelToPlate = addModelToPlate,
        selectPlate = selectPlate,
        plateObjects = PlateObjectsUseCase(plateRepository),
        plateJobs = plateJobs,
        deletePlate = deletePlate,
        lockPlate = lockPlate,
        renamePlate = renamePlate,
        addPrimitive = addPrimitive,
        preferences = appPreferences,
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
