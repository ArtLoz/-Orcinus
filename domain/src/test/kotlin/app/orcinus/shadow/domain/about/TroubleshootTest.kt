package app.orcinus.shadow.domain.about

import app.orcinus.shadow.core.model.AppInfo
import app.orcinus.shadow.core.model.ExternalDocumentReference
import app.orcinus.shadow.core.model.LicenseId
import app.orcinus.shadow.core.model.PlateObject
import app.orcinus.shadow.core.model.PlateProject
import app.orcinus.shadow.core.model.PlateState
import app.orcinus.shadow.core.model.ProfileCounts
import app.orcinus.shadow.core.model.ProfilesOverview
import app.orcinus.shadow.domain.plate.PlateRepository
import app.orcinus.shadow.slicing.api.Troubleshooting
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking

class TroubleshootTest {
    private val repository = object : PlateRepository {
        private val flow = MutableStateFlow(PlateState())
        override val state: StateFlow<PlateState> = flow

        override fun update(transform: (PlateState) -> PlateState) {
            flow.value = transform(flow.value)
        }
    }
    private var overview: ProfilesOverview? = OVERVIEW
    private val engine = object : Troubleshooting {
        override suspend fun profilesOverview(): ProfilesOverview? = overview

        override suspend fun cleanSystemProfiles(): Boolean = true
    }
    private val written = mutableListOf<Pair<String, ExternalDocumentReference>>()
    private val packed = mutableListOf<Pair<ExternalDocumentReference, ExternalDocumentReference?>>()
    private val files = object : TroubleshootFiles {
        override suspend fun writeText(text: String, document: ExternalDocumentReference): Boolean {
            written += text to document
            return true
        }

        override suspend fun pack(document: ExternalDocumentReference, project: ExternalDocumentReference?): Boolean {
            packed += document to project
            return true
        }
    }
    private val saved = mutableListOf<ExternalDocumentReference>()
    private val useCase = TroubleshootUseCase(
        APP,
        { PHONE },
        engine,
        files,
        repository,
        saveProject = { saved += it; true },
        now = { LocalDateTime.of(2026, 10, 7, 8, 5, 30) },
    )

    @Test
    fun `the system information is the desktop dialog's, with the phone in place of the computer`() = runBlocking {
        val information = useCase.systemInformation()
        assertEquals("Orcinus 0.1.0", information.version)
        assertEquals("2.4.2", information.engine)
        assertEquals("8500fcd", information.build)
        assertEquals("https://github.com/OrcaSlicer/OrcaSlicer/commit/8500fcdccaa10b5099ac20d252af3a7c560046f1", information.buildUrl)
        assertEquals("Android", information.osType)
        assertEquals(
            listOf(
                "Android 16 (API 36)",
                "Google Pixel 8 Pro",
                "Local Build",
                "Google Tensor G3 (arm64-v8a)",
                "11.6 GB RAM",
                "Mali-G715  GLSL:OpenGL ES GLSL ES 3.20",
                "1344x2992-281%  TextScaling-115%",
            ),
            information.lines,
        )
        assertEquals(
            """
            Version   :  Orcinus 0.1.0
            Engine    :  OrcaSlicer 2.4.2 (8500fcd)
            Package   :  Local Build
            Platform  :  Android 16 (API 36)
            Device    :  Google Pixel 8 Pro
            Processor :  Google Tensor G3 (arm64-v8a)
            Memory    :  11.6 GB RAM
            Renderer  :  Mali-G715  GLSL:OpenGL ES GLSL ES 3.20
            Monitors  :  1344x2992-281%  TextScaling-115%
            """.trimIndent(),
            information.text,
        )
    }

    @Test
    fun `memory, screens and the issue's address are written as the desktop dialog writes them`() {
        assertEquals("16.0 GB", ramInfo(16L shl 30))
        assertEquals("11.2 GB", ramInfo(12_000_000_000))
        assertEquals("Unknown", monitorsInfo(emptyList(), 1f))
        assertEquals("1080x2400-263%  1920x1080-100%  TextScaling-100%", monitorsInfo(listOf(DisplayDetails(1080, 2400, 2.625f), DisplayDetails(1920, 1080, 1f)), 1f))
        assertEquals("a%20b%2Fc-_.~%C3%BC%0A", urlEncode("a b/c-_.~ü\n"))

        val information = runBlocking { useCase.systemInformation() }
        assertEquals("https://example.org/orcinus/issues/new?body=" + urlEncode("```\n${information.text}\n```"), useCase.reportIssueUrl(information))
        val withoutSource = TroubleshootUseCase(APP.copy(sourceUrl = null), { PHONE }, engine, files, repository, { true })
        assertNull(withoutSource.reportIssueUrl(information))
    }

    @Test
    fun `pack asks what PackAll() asks, saves the project first, and packs its document`() = runBlocking {
        assertEquals(PackQuestion.NO_PROJECT, useCase.packQuestion())
        assertEquals("Orcinus_PackedDebugInfo_20261007_0805.zip", useCase.packName())
        useCase.pack(TARGET)
        assertEquals(listOf<Pair<ExternalDocumentReference, ExternalDocumentReference?>>(TARGET to null), packed)

        repository.update { it.copy(project = PlateProject(name = "Benchy", document = PROJECT)) }
        assertEquals(PackQuestion.NONE, useCase.packQuestion())

        repository.update { it.copy(objects = listOf(PlateObject.CalibrationCube(instances = emptyList())), project = it.project.copy(otherChanges = true)) }
        assertEquals(PackQuestion.SAVE_CHANGES, useCase.packQuestion())
        useCase.saveProject()
        assertEquals(listOf(PROJECT), saved)
        useCase.pack(TARGET)
        assertEquals(TARGET to PROJECT, packed.last())
    }

    @Test
    fun `the overview of the loaded profiles is exported as the engine has it now`() = runBlocking {
        assertEquals("ProfilesOverview.json", useCase.profilesOverviewName)
        assertTrue(useCase.exportProfilesOverview(TARGET))
        assertEquals(listOf(OVERVIEW.json to TARGET), written)

        overview = null
        assertFalse(useCase.exportProfilesOverview(TARGET))
        assertEquals(1, written.size)
    }

    private companion object {
        val APP = AppInfo(
            name = "Orcinus",
            version = "0.1.0",
            orcaRelease = "2.4.2",
            sourceUrl = "https://example.org/orcinus/",
            license = LicenseId("AGPL-3.0-only"),
            orcaCommit = "8500fcdccaa10b5099ac20d252af3a7c560046f1",
        )
        val PHONE = DeviceDetails(
            androidRelease = "16",
            apiLevel = 36,
            manufacturer = "Google",
            model = "Pixel 8 Pro",
            processor = "Google Tensor G3",
            abi = "arm64-v8a",
            memoryBytes = 12_442_000_000,
            renderer = "Mali-G715",
            shadingLanguage = "OpenGL ES GLSL ES 3.20",
            displays = listOf(DisplayDetails(1344, 2992, 2.8125f)),
            fontScale = 1.15f,
            packageType = "Local Build",
        )
        val OVERVIEW = ProfilesOverview(ProfileCounts(1, 10, 0), ProfileCounts(3, 20, 1), ProfileCounts(5, 30, 2), json = """{"Overview":{}}""")
        val TARGET = ExternalDocumentReference("content://documents/target")
        val PROJECT = ExternalDocumentReference("content://documents/benchy.3mf")
    }
}
