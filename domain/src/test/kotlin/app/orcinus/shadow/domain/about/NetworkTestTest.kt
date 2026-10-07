package app.orcinus.shadow.domain.about

import app.orcinus.shadow.core.model.AppInfo
import app.orcinus.shadow.core.model.LicenseId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking

class NetworkTestTest {
    private val reports = mutableListOf<Pair<NetworkTest?, String>>()

    private fun useCase(answer: ProbeAnswer) = NetworkTestUseCase(APP, { PHONE }) { answer }

    @Test
    fun `a page that answers tells its address and passes, as start_test_url() reports`() = runBlocking {
        useCase(ProbeAnswer(200, "<html>", ip = "140.82.121.4")).run(NetworkTest.ORCA) { test, info -> reports += test to info }

        assertEquals(
            listOf(
                NetworkTest.ORCA to "test OrcaSlicer(GitHub) start...",
                null to "[test OrcaSlicer(GitHub)]: url=https://github.com/OrcaSlicer/OrcaSlicer",
                NetworkTest.ORCA to "test OrcaSlicer(GitHub) ip resolved = 140.82.121.4",
                NetworkTest.ORCA to "test OrcaSlicer(GitHub) ok",
            ),
            reports,
        )
    }

    @Test
    fun `an error or a status from 400 fails, with its details in the log alone`() = runBlocking {
        useCase(ProbeAnswer(0, error = "Unable to resolve host")).run(NetworkTest.BING) { test, info -> reports += test to info }
        useCase(ProbeAnswer(503, body = "busy")).run(NetworkTest.BING) { test, info -> reports += test to info }

        assertEquals(
            listOf(
                NetworkTest.BING to "test Bing failed",
                null to "status=0, body=, error=Unable to resolve host",
                NetworkTest.BING to "test Bing failed",
                null to "status=503, body=busy, error=",
            ),
            reports.filter { (_, info) -> "failed" in info || info.startsWith("status=") },
        )
    }

    @Test
    fun `a success other than 200 tells its address without passing`() = runBlocking {
        useCase(ProbeAnswer(204, ip = "13.107.21.200")).run(NetworkTest.BING) { test, info -> reports += test to info }

        assertEquals(NetworkTest.BING to "test Bing ip resolved = 13.107.21.200", reports.last())
    }

    @Test
    fun `the versions are the OrcaSlicer release and the phone's Android`() = runBlocking {
        val test = useCase(ProbeAnswer(200))

        assertEquals("2.4.2", test.version)
        assertEquals("Android 16 (API 36)", test.systemVersion())
    }

    private companion object {
        val APP = AppInfo(
            name = "Orcinus",
            version = "0.1.0",
            orcaRelease = "2.4.2",
            sourceUrl = null,
            license = LicenseId("AGPL-3.0-only"),
            orcaCommit = null,
        )
        val PHONE = DeviceDetails(
            androidRelease = "16",
            apiLevel = 36,
            manufacturer = "Google",
            model = "Pixel 8 Pro",
            processor = "Google Tensor G3",
            abi = "arm64-v8a",
            memoryBytes = 12_442_000_000,
            renderer = null,
            shadingLanguage = null,
            displays = emptyList(),
            fontScale = 1f,
            packageType = "Local Build",
        )
    }
}
