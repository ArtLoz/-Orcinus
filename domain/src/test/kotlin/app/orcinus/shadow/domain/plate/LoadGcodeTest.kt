package app.orcinus.shadow.domain.plate

import app.orcinus.shadow.core.model.ExternalDocumentReference
import app.orcinus.shadow.core.model.GcodeLoadOutcome
import app.orcinus.shadow.core.model.GcodeSettingsIds
import app.orcinus.shadow.core.model.ImportedModelFile
import app.orcinus.shadow.core.model.ModelImportOutcome
import app.orcinus.shadow.core.model.ModelPath
import app.orcinus.shadow.core.model.OutputPath
import app.orcinus.shadow.core.model.PartPlate
import app.orcinus.shadow.core.model.PlateState
import app.orcinus.shadow.core.model.PreviewOnly
import app.orcinus.shadow.core.model.PreviewOnlyKind
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.SliceStatistics
import app.orcinus.shadow.core.model.ThumbnailSize
import app.orcinus.shadow.core.model.partPlates
import app.orcinus.shadow.domain.ImportModelUseCase
import app.orcinus.shadow.slicing.api.PlateInspector
import app.orcinus.shadow.slicing.api.PresetManager
import app.orcinus.shadow.storage.api.ModelFileImporter
import app.orcinus.shadow.storage.api.SceneFiles
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking

class LoadGcodeTest {
    @Test
    fun `a G-code file opens on its own in the preview, named after it, with its G-code as the plate's result`() = runBlocking {
        val loads = mutableListOf<GcodeLoad>()
        val repository = Repository(PlateState())
        val started = mutableListOf<PreviewOnly>()
        val useCase = useCase(repository, loads, newProject = { previewOnly ->
            started += previewOnly
            repository.update { it.copy(previewOnly = previewOnly) }
            true
        })

        useCase.load(BENCHY)

        assertEquals(listOf(PreviewOnly(PreviewOnlyKind.GCODE, "benchy.gcode", BENCHY)), started)
        // load_gcode(): the file on the first of one plate, its plate type the project's.
        assertEquals(listOf(GcodeLoad(OutputPath("/gcode/benchy.gcode"), 0, 1, true)), loads)
        val state = repository.state.value
        val result = state.result!!
        assertEquals(OutputPath("/gcode/benchy.gcode"), result.gcode)
        assertEquals(ScenePath("/toolpaths/0"), result.toolpaths)
        assertEquals(ScenePath("/toolpaths/0.slice.json"), result.sliceInfo)
        assertEquals("benchy.gcode", result.outputName)
        assertEquals(SETTINGS, result.settingsIds)
        assertEquals("benchy", state.project.name)
        assertEquals(false, state.importing)
        assertTrue(state.plateNotices.isEmpty())
    }

    @Test
    fun `a file without valid G-code shows the desktop's message and leaves the plate without G-code`() = runBlocking {
        val repository = Repository(PlateState())
        val useCase = useCase(repository, mutableListOf(), valid = false)

        useCase.load(BENCHY)

        val state = repository.state.value
        assertNull(state.result)
        assertNull(state.project.name)
        assertEquals("gcode_invalid", state.plateNotices.single().id)
        assertEquals("does not contain valid G-code.", state.plateNotices.single().text.last().msgid)
    }

    @Test
    fun `the G-code file the preview shows opens no second time, and a file that is no gcode file not at all`() = runBlocking {
        val loads = mutableListOf<GcodeLoad>()
        val shown = PlateState(previewOnly = PreviewOnly(PreviewOnlyKind.GCODE, "benchy.gcode", BENCHY))
        val useCase = useCase(Repository(shown), loads, newProject = { error("no new project") })

        useCase.load(BENCHY)
        useCase.load(ExternalDocumentReference("content://other.g"), ImportedModelFile(ModelPath("/imports/other.g"), "other.g"))

        assertTrue(loads.isEmpty())
    }

    @Test
    fun `an exported file's plates take their G-code, each read where its plate stands`() = runBlocking {
        val loads = mutableListOf<GcodeLoad>()
        val repository = Repository(PlateState(plates = listOf(PartPlate(), PartPlate(), PartPlate()), currentPlate = 1, importing = true))
        val useCase = useCase(repository, loads)

        useCase.loadExportedPlates(listOf(null, OutputPath("/scene/p-plate-2.gcode"), OutputPath("/scene/p-plate-3.gcode")), "part.gcode.3mf")

        assertEquals(
            listOf(GcodeLoad(OutputPath("/scene/p-plate-2.gcode"), 1, 3, false), GcodeLoad(OutputPath("/scene/p-plate-3.gcode"), 2, 3, false)),
            loads,
        )
        val plates = repository.state.value.partPlates()
        assertNull(plates[0].result)
        assertEquals(OutputPath("/scene/p-plate-2.gcode"), plates[1].result?.gcode)
        assertEquals("part_plate_2.gcode", plates[1].result?.outputName)
        assertEquals("part_plate_3.gcode", plates[2].result?.outputName)
        assertEquals(false, repository.state.value.importing)
    }

    private data class GcodeLoad(val gcode: OutputPath, val plateIndex: Int, val plateCount: Int, val applyBedType: Boolean)

    private class Repository(initial: PlateState) : PlateRepository {
        private val flow = MutableStateFlow(initial)
        override val state: StateFlow<PlateState> = flow
        override fun update(transform: (PlateState) -> PlateState) = flow.update(transform)
    }

    private fun useCase(
        repository: PlateRepository,
        loads: MutableList<GcodeLoad>,
        valid: Boolean = true,
        newProject: suspend (PreviewOnly) -> Boolean = { true },
    ) = LoadGcodeUseCase(
        importModel = ImportModelUseCase(
            object : ModelFileImporter {
                override suspend fun importModel(reference: ExternalDocumentReference) =
                    ModelImportOutcome.Success(ImportedModelFile(ModelPath("/imports/benchy.gcode"), "benchy.gcode"))
            },
        ),
        inspector = GcodeInspector(loads, valid),
        presetManager = unused(PresetManager::class.java),
        platePresets = { _, _ -> error("the plate type stays") },
        outputs = { name -> OutputPath("/gcode/$name.gcode") },
        sceneFiles = SceneFilesFake(),
        repository = repository,
        applicationScope = CoroutineScope(Dispatchers.Unconfined),
        newProject = newProject,
        keepFile = { _, _ -> true },
    )

    /** The engine's load_gcode(), which answers at once; nothing else is asked of it. */
    private class GcodeInspector(
        private val loads: MutableList<GcodeLoad>,
        private val valid: Boolean,
    ) : PlateInspector by unused(PlateInspector::class.java) {
        override suspend fun loadGcode(
            gcode: OutputPath,
            toolpaths: ScenePath,
            sliceInfo: ScenePath?,
            plateIndex: Int,
            plateCount: Int,
            applyBedType: Boolean,
        ): GcodeLoadOutcome {
            loads += GcodeLoad(gcode, plateIndex, plateCount, applyBedType)
            return GcodeLoadOutcome.Success(
                statistics = SliceStatistics(layerCount = if (valid) 100 else 0, estimatedPrintTimeSeconds = 600, filamentMillimeters = 1500.0),
                toolpaths = toolpaths.takeIf { valid },
                sliceInfo = sliceInfo?.takeIf { valid },
                settingsIds = SETTINGS,
                valid = valid,
            )
        }
    }


    private class SceneFilesFake : SceneFiles {
        private var toolpaths = 0

        override fun plateDirectory() = ScenePath("/plate")
        override fun newObjectMesh() = ScenePath("/objects/mesh")
        override fun newPaintedMeshes() = ScenePath("/objects/painted")
        override fun newCutMeshes() = ScenePath("/objects/cut")
        override fun newImportPrefix() = ScenePath("/objects/import")
        override fun deleteImport(prefix: ScenePath) = Unit
        override fun deleteObjectMesh(mesh: ScenePath) = Unit
        override fun deleteAllObjectMeshes() = Unit
        override fun newToolpaths() = ScenePath("/toolpaths/${toolpaths++}")
        override fun wipeTowerMeshOf(toolpaths: ScenePath) = ScenePath(toolpaths.value + ".tower.mesh")
        override fun sliceInfoOf(toolpaths: ScenePath) = ScenePath(toolpaths.value + ".slice.json")
        override fun thumbnailOf(toolpaths: ScenePath, size: ThumbnailSize) = ScenePath(toolpaths.value + ".rgba")
        override fun deleteToolpathsExcept(keep: Collection<ScenePath>) = Unit
        override fun svgPreview() = ScenePath("/svg/preview.png")
        override fun newSavedSvg(name: String) = ScenePath("/svg/$name")
    }

    private companion object {
        /** An engine none of whose requests the use case is to make. */
        fun <T> unused(type: Class<T>): T =
            type.cast(Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, _ -> error("unexpected ${method.name}") })

        val BENCHY = ExternalDocumentReference("content://benchy.gcode")
        val SETTINGS = GcodeSettingsIds("Creality K2 Plus 0.4 nozzle", "0.20mm Standard @Creality K2 Plus 0.4 nozzle", listOf("Generic PLA @K2 Plus-all"))
    }
}
