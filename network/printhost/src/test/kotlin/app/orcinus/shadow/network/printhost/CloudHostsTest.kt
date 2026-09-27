package app.orcinus.shadow.network.printhost

import app.orcinus.shadow.core.model.HostPrintersOutcome
import app.orcinus.shadow.core.model.ModelSettings
import app.orcinus.shadow.core.model.ObicoHost
import app.orcinus.shadow.core.model.PhysicalPrinter
import app.orcinus.shadow.core.model.PrintHostTestOutcome
import app.orcinus.shadow.core.model.PrintHostUploadOutcome
import java.io.File
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CloudHostsTest {
    @Test
    fun `Obico is reached with the token as a bearer and prints on the printer chosen`() {
        val http = CloudHttp(answers = mapOf("api/v1/printers/" to """[{"name":"Prusa","id":12},{"name":"Voron","id":13}]"""))
        val uploader = PrintHostUploader(http)
        val none = obico(token = "")
        val linked = obico(token = "abc", port = "Voron [13]")

        // Without a token there is nothing to test: the dialog logs in instead.
        assertEquals(PrintHostTestOutcome.Failure(""), run { uploader.test(none) })
        assertTrue(http.gets.isEmpty())
        assertTrue(run { uploader.test(linked) } is PrintHostTestOutcome.Success)
        assertEquals("https://app.obico.io/api/v1/version/", http.gets.single().first)
        assertEquals("Bearer abc", http.gets.single().second["Authorization"])

        assertEquals(HostPrintersOutcome.Success(listOf("Prusa [12]", "Voron [13]")), run { uploader.printers(linked) })

        val outcome = run { uploader.upload(linked, gcode(), "cube.gcode", startPrint = true) }
        assertEquals(PrintHostUploadOutcome.Success("cube.gcode"), outcome)
        val post = http.posts.single()
        assertEquals("https://app.obico.io/api/v1/g_code_files/", post.first)
        // The desktop sends printhost_port as it keeps it, "Name [id]".
        assertEquals(mapOf("print" to "true", "path" to "", "printer_id" to "Voron [13]", "filename" to "cube.gcode"), post.second)
    }

    @Test
    fun `the dialog builds Obico's pages from the server and the printer`() {
        assertEquals(
            "https://app.obico.io/o/authorize?response_type=token&client_id=OrcaSlicer&hide_navbar=true",
            ObicoHost.loginUrl("https://app.obico.io"),
        )
        assertEquals("http://obico.local/api/v1/version/", ObicoHost.url("obico.local", "api/v1/version/"))
        assertEquals("https://app.obico.io/printers/13/control", ObicoHost.webUi("https://app.obico.io", "Voron [13]"))
        assertEquals("", ObicoHost.webUi("https://app.obico.io", "Voron"))
    }

    private fun obico(token: String, port: String = "") = PhysicalPrinter(
        name = "Test",
        settings = ModelSettings(
            mapOf("host_type" to "obico", "print_host" to "https://app.obico.io", "printhost_apikey" to token, "printhost_port" to port),
        ),
    )

    private fun gcode(): File = File.createTempFile("orcinus", ".gcode").also {
        it.writeText("G1 X0\n")
        it.deleteOnExit()
    }

    private class CloudHttp(private val answers: Map<String, String> = emptyMap()) : HttpClient {
        val gets = mutableListOf<Pair<String, Map<String, String>>>()
        val posts = mutableListOf<Pair<String, Map<String, String>>>()

        private fun answer(url: String) = Result.success(answers.entries.firstOrNull { url.endsWith(it.key) }?.value ?: "{}")

        override suspend fun get(url: String, headers: Map<String, String>, auth: HttpAuth?): Result<String> {
            gets += url to headers
            return answer(url)
        }

        override suspend fun postMultipart(
            url: String,
            headers: Map<String, String>,
            fields: Map<String, String>,
            fileField: String,
            fileName: String,
            file: File,
            onProgress: ((sent: Long, total: Long) -> Unit)?,
            auth: HttpAuth?,
        ): Result<String> {
            posts += url to fields
            return answer(url)
        }

        override suspend fun sendFile(
            url: String,
            method: String,
            headers: Map<String, String>,
            file: File,
            onProgress: ((sent: Long, total: Long) -> Unit)?,
            auth: HttpAuth?,
        ): Result<String> = answer(url)

        override suspend fun postJson(url: String, headers: Map<String, String>, body: String, auth: HttpAuth?): Result<String> = answer(url)

        override suspend fun postMultipartPart(
            url: String,
            headers: Map<String, String>,
            fields: Map<String, String>,
            fileField: String,
            fileName: String,
            file: File,
            offset: Long,
            length: Long,
            onProgress: ((sent: Long, total: Long) -> Unit)?,
        ): Result<String> = answer(url)

        override suspend fun sendBytes(url: String, method: String, headers: Map<String, String>, body: ByteArray): Result<String> = answer(url)
    }

    private fun <T> run(block: suspend () -> T): T {
        var completion: Result<T>? = null
        block.startCoroutine(object : Continuation<T> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<T>) {
                completion = result
            }
        })
        return checkNotNull(completion) { "The call did not finish" }.getOrThrow()
    }
}
