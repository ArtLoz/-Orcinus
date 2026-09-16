package app.orcinus.shadow.di

import android.content.Context
import app.orcinus.shadow.BuildConfig
import app.orcinus.shadow.OrcaSlicerService
import app.orcinus.shadow.R
import app.orcinus.shadow.core.model.AppInfo
import app.orcinus.shadow.core.model.ComponentId
import app.orcinus.shadow.core.model.LicenseId
import app.orcinus.shadow.data.notices.AboutLibrariesNoticeCatalog
import app.orcinus.shadow.data.plate.InMemoryPlateRepository
import app.orcinus.shadow.domain.CancelSliceUseCase
import app.orcinus.shadow.domain.GetEngineStatusUseCase
import app.orcinus.shadow.domain.ImportModelUseCase
import app.orcinus.shadow.domain.InspectModelUseCase
import app.orcinus.shadow.domain.SliceModelUseCase
import app.orcinus.shadow.domain.about.GetLicenseUseCase
import app.orcinus.shadow.domain.about.GetThirdPartyComponentUseCase
import app.orcinus.shadow.domain.about.GetThirdPartyComponentsUseCase
import app.orcinus.shadow.domain.plate.AddCalibrationCubeToPlateUseCase
import app.orcinus.shadow.domain.plate.AddModelToPlateUseCase
import app.orcinus.shadow.domain.plate.CancelPlateSlicingUseCase
import app.orcinus.shadow.domain.plate.DismissPlateProblemUseCase
import app.orcinus.shadow.domain.plate.ObservePlateUseCase
import app.orcinus.shadow.domain.plate.SlicePlateUseCase
import app.orcinus.shadow.domain.plate.StartEngineUseCase
import app.orcinus.shadow.feature.about.NoticeViewModel
import app.orcinus.shadow.feature.about.ThirdPartyViewModel
import app.orcinus.shadow.feature.about.navigation.AboutViewModelFactory
import app.orcinus.shadow.feature.prepare.PrepareViewModel
import app.orcinus.shadow.feature.preview.PreviewViewModel
import app.orcinus.shadow.feature.sidebar.SidebarViewModel
import app.orcinus.shadow.slicing.nativebridge.NativeSlicerEngine
import app.orcinus.shadow.slicing.service.RemoteSlicerEngine
import app.orcinus.shadow.storage.android.AppGcodeOutputs
import app.orcinus.shadow.storage.android.ContentResolverModelFileImporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Composition root of the UI process: adapters, the plate repository, and the
 * use cases every screen gets. Created once per process by the Application.
 */
class AppContainer(context: Context) : AboutViewModelFactory {
    private val applicationContext = context.applicationContext

    /** Work that must outlive a screen, such as a running slice. */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val engine = RemoteSlicerEngine(applicationContext, OrcaSlicerService::class.java)
    private val plateRepository = InMemoryPlateRepository(NativeSlicerEngine.k2PlusProfiles)

    val observePlate = ObservePlateUseCase(plateRepository)
    val startEngine = StartEngineUseCase(GetEngineStatusUseCase(engine), plateRepository)
    val slicePlate = SlicePlateUseCase(SliceModelUseCase(engine), AppGcodeOutputs(applicationContext), plateRepository, applicationScope)
    private val addModelToPlate = AddModelToPlateUseCase(
        importModel = ImportModelUseCase(ContentResolverModelFileImporter(applicationContext)),
        inspectModel = InspectModelUseCase(engine),
        repository = plateRepository,
        applicationScope = applicationScope,
    )
    private val addCalibrationCube = AddCalibrationCubeToPlateUseCase(plateRepository)
    private val cancelPlateSlicing = CancelPlateSlicingUseCase(CancelSliceUseCase(engine), plateRepository, applicationScope)
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

    fun prepareViewModel() = PrepareViewModel(
        observePlate = observePlate,
        addModelToPlate = addModelToPlate,
        addCalibrationCubeToPlate = addCalibrationCube,
        slicePlate = slicePlate,
        cancelPlateSlicing = cancelPlateSlicing,
        dismissPlateProblem = dismissPlateProblem,
    )

    fun previewViewModel() = PreviewViewModel(observePlate = observePlate, slicePlate = slicePlate)

    fun sidebarViewModel() = SidebarViewModel(observePlate = observePlate)

    override fun thirdPartyViewModel() = ThirdPartyViewModel(GetThirdPartyComponentsUseCase(noticeCatalog))

    override fun componentNoticeViewModel(id: ComponentId) = NoticeViewModel.forComponent(id, GetThirdPartyComponentUseCase(noticeCatalog))

    override fun licenseNoticeViewModel(id: LicenseId) = NoticeViewModel.forLicense(id, GetLicenseUseCase(noticeCatalog))
}
