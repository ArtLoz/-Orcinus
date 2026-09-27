package app.orcinus.shadow.network.printhost

import app.orcinus.shadow.core.model.CloudProject
import app.orcinus.shadow.core.model.HostPrintersOutcome
import app.orcinus.shadow.core.model.ModelSettings
import app.orcinus.shadow.core.model.ObicoHost
import app.orcinus.shadow.core.model.PhysicalPrinter
import app.orcinus.shadow.core.model.PrintHostTestOutcome
import app.orcinus.shadow.core.model.PrintHostUploadOutcome
import app.orcinus.shadow.core.model.PrintOptions
import app.orcinus.shadow.core.model.Printer3dOsChoice
import app.orcinus.shadow.core.model.Printer3dOsListsOutcome
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

    @Test
    fun `SimplyPrint's code challenge is RFC 7636's`() {
        // RFC 7636, appendix B.
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", SimplyPrint.codeChallenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
        assertEquals("import=tmp%3Aab-1&filename=my%20cube.gcode", SimplyPrint.urlEncode(listOf("import" to "tmp:ab-1", "filename" to "my cube.gcode")))
    }

    @Test
    fun `SimplyPrint refreshes a refused token and opens its panel to import the upload`() {
        val credentials = File.createTempFile("simplyprint", ".json").also { it.deleteOnExit() }
        val http = CloudHttp(
            answers = mapOf(
                "oauth2/Token" to """{"access_token":"new","refresh_token":"r2"}""",
                "api/files/TempUpload" to """{"uuid":"ab-1"}""",
            ),
        )
        val uploader = PrintHostUploader(http, simplyPrintCredentials = credentials)
        val printer = PhysicalPrinter("Test", ModelSettings(mapOf("host_type" to "simplyprint", "print_host" to "https://simplyprint.io/panel")))

        // Without a login there is nothing to test, and the upload says so.
        credentials.delete()
        assertEquals(PrintHostTestOutcome.Failure(""), run { uploader.test(printer) })
        assertTrue(!run { uploader.isLoggedIn(printer) })

        credentials.writeText("""{"access_token":"old","refresh_token":"r1"}""")
        http.refused = "old"
        assertTrue(run { uploader.test(printer) } is PrintHostTestOutcome.Success)
        assertEquals(listOf("Bearer old", "Bearer new"), http.gets.map { it.second["Authorization"] })
        assertEquals(mapOf("grant_type" to "refresh_token", "client_id" to "simplyprintorcaslicer", "refresh_token" to "r1"), http.forms.single().second)
        assertTrue(credentials.readText().contains("\"new\""))

        val outcome = run { uploader.upload(printer, gcode(), "cube.gcode", startPrint = false) }
        assertEquals(PrintHostUploadOutcome.Success("cube.gcode", "https://simplyprint.io/panel?import=tmp%3Aab-1&filename=cube.gcode"), outcome)
        assertEquals("https://simplyprint.io/api/files/TempUpload", http.posts.single().first)

        run { uploader.logOut(printer) }
        assertTrue(!credentials.exists())
    }

    @Test
    fun `SimplyPrint's login waits for the browser at its callback and keeps the tokens`() {
        val credentials = File.createTempFile("simplyprint", ".json").also { it.delete(); it.deleteOnExit() }
        val http = CloudHttp(answers = mapOf("oauth2/Token" to """{"access_token":"a1","refresh_token":"r1"}"""))
        val port = java.net.ServerSocket(0).use { it.localPort }
        val simplyPrint = SimplyPrint(http, credentials, callbackPort = port)
        var location: String? = null
        var browser: Thread? = null

        val outcome = kotlinx.coroutines.runBlocking {
            simplyPrint.login { page ->
                // The browser comes back with the state of the page it was sent to.
                val state = OAuthCallbackServer.urlParam(java.net.URLDecoder.decode(page, Charsets.UTF_8), "state")
                browser = Thread {
                    Thread.sleep(300)
                    val connection = java.net.URL("http://127.0.0.1:$port/callback?code=c0de&state=$state").openConnection() as java.net.HttpURLConnection
                    connection.instanceFollowRedirects = false
                    location = connection.getHeaderField("Location")
                    connection.disconnect()
                }.also { it.start() }
            }
        }
        browser?.join()

        assertEquals(app.orcinus.shadow.core.model.CloudLoginOutcome.Success, outcome)
        assertEquals("https://simplyprint.io/login-success", location)
        val exchange = http.forms.single()
        assertEquals("https://api.simplyprint.io/oauth2/Token", exchange.first)
        assertEquals("authorization_code", exchange.second["grant_type"])
        assertEquals("c0de", exchange.second["code"])
        assertTrue(simplyPrint.isLoggedIn())
    }

    @Test
    fun `3DPrinterOS logs in with a token the browser confirms and keeps the session`() {
        val credentials = File.createTempFile("3dprinteros", ".json").also { it.delete(); it.deleteOnExit() }
        val http = CloudHttp(answers = mapOf("generate_login_token" to """{"result":true,"message":"t0k"}""", "check_session" to """{"result":true}"""))
        // The cloud has no session for the token until the browser confirms it.
        http.queued["login_with_token"] = ArrayDeque(
            listOf("""{"result":true,"message":"wait"}""", """{"result":true,"message":{"session":"s1","email":"me@example.com"}}"""),
        )
        val uploader = PrintHostUploader(http, printer3dOsCredentials = credentials, printer3dOsRetryMillis = 0)
        val printer = printer3dOs()
        val pages = mutableListOf<String>()

        assertEquals(app.orcinus.shadow.core.model.CloudLoginOutcome.Success, run { uploader.cloudLogin(printer) { pages += it } })
        assertEquals(listOf("https://cloud.3dprinteros.com/noauth/apiglobal_login_with_token/t0k"), pages)
        assertEquals(
            listOf(
                "https://cloud.3dprinteros.com/apiglobal/generate_login_token" to "app_type=plugin&app_name=OrcaSlicer",
                "https://cloud.3dprinteros.com/apiglobal/login_with_token" to "token=t0k",
                "https://cloud.3dprinteros.com/apiglobal/login_with_token" to "token=t0k",
            ),
            http.sent,
        )
        assertTrue(run { uploader.isLoggedIn(printer) })

        // The test sends the session kept and names the account.
        http.sent.clear()
        assertEquals(PrintHostTestOutcome.Success("me@example.com"), run { uploader.test(printer) })
        assertEquals("session=s1", http.sent.single().second)

        run { uploader.logOut(printer) }
        assertTrue(!credentials.exists())
    }

    @Test
    fun `3DPrinterOS gives up on a token after ten answers without a session`() {
        val http = CloudHttp(
            answers = mapOf("generate_login_token" to """{"result":true,"message":"t0k"}""", "login_with_token" to """{"result":true,"message":"wait"}"""),
        )
        val credentials = File.createTempFile("3dprinteros", ".json").also { it.delete(); it.deleteOnExit() }
        val uploader = PrintHostUploader(http, printer3dOsCredentials = credentials, printer3dOsRetryMillis = 0)

        // TokenAuthDialog stops at its tenth answer, which login() cannot read a session from.
        val outcome = run { uploader.cloudLogin(printer3dOs()) {} }
        assertEquals(app.orcinus.shadow.core.model.CloudLoginOutcome.Failure("Could not parse server response."), outcome)
        assertEquals(10, http.sent.count { it.first.endsWith("login_with_token") })
        assertTrue(!credentials.exists())
    }

    @Test
    fun `3DPrinterOS uploads into the project and printer type chosen and opens its quick print`() {
        val credentials = File.createTempFile("3dprinteros", ".json").also { it.deleteOnExit() }
        credentials.writeText("""{"session":"s1","email":"me@example.com"}""")
        val http = CloudHttp(
            answers = mapOf(
                "check_session" to """{"result":true}""",
                "get_projects" to """{"result":true,"message":[{"id":"5","name":"Box"}]}""",
                "get_printer_types" to """{"result":true,"message":[{"id":"9","description":"Creality K2 Plus"},{"id":"10","description":"Creality K1"}]}""",
                "apiglobal/upload" to """{"result":true,"message":{"file_id":"77"}}""",
            ),
        )
        val uploader = PrintHostUploader(http, printer3dOsCredentials = credentials)
        val printer = printer3dOs(model = "Creality K1")

        val lists = run { uploader.printer3dOsLists(printer) } as Printer3dOsListsOutcome.Success
        assertEquals("session=s1&description=Creality%20K1&software_version=OrcaSlicer", http.sent.last().second)
        assertEquals(listOf(CloudProject("5", "Box")), lists.projects)
        // Several types: the one of the model is chosen, and the dialog still asks to check it.
        assertEquals(1, lists.initialPrinterType(printer.printerModel))
        assertTrue(lists.asksForPrinterType)
        assertEquals(Printer3dOsChoice("5", "Box", "10"), lists.choice("Box", lists.printerTypes[1]))
        assertEquals(Printer3dOsChoice("", "Shelf", "9"), lists.choice("Shelf", lists.printerTypes[0]))

        http.sent.clear()
        val outcome = run {
            uploader.upload(printer, gcode(), "cube.gcode", startPrint = true, PrintOptions(printer3dOs = lists.choice("Shelf", lists.printerTypes[0])))
        }
        assertEquals(PrintHostUploadOutcome.Success("cube.gcode", "https://cloud.3dprinteros.com/quickprint?file_id=77"), outcome)
        assertEquals(
            mapOf(
                "session" to "s1",
                "upload_type_id" to "7",
                "upload_soft_name" to "OrcaSlicer",
                "zip" to "false",
                "project_name" to "Shelf",
                "project_color" to "grey",
            ),
            http.posts.single().second,
        )
        assertEquals(
            "https://cloud.3dprinteros.com/apiglobal/file_update" to
                "session=s1&updates[77][ptype]=9&updates[77][gtype]=OrcaSlicer&updates[77][zip]=false",
            http.sent.single(),
        )
    }

    private fun printer3dOs(model: String = "") = PhysicalPrinter(
        name = "Test",
        settings = ModelSettings(mapOf("host_type" to "3dprinteros", "print_host" to "https://cloud.3dprinteros.com", "printer_model" to model)),
    )

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
            // A token the server no longer takes is answered 401.
            if (refused != null && headers["Authorization"] == "Bearer $refused") return Result.failure(HttpStatusException(401, "", "HTTP 401"))
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

        /** The forms 3DPrinterOS posted, and the answers its endpoints give in turn before the usual one. */
        val sent = mutableListOf<Pair<String, String>>()
        val queued = mutableMapOf<String, ArrayDeque<String>>()

        override suspend fun sendBytes(url: String, method: String, headers: Map<String, String>, body: ByteArray): Result<String> {
            sent += url to body.decodeToString()
            queued.entries.firstOrNull { url.endsWith(it.key) }?.value?.removeFirstOrNull()?.let { return Result.success(it) }
            return answer(url)
        }

        val forms = mutableListOf<Pair<String, Map<String, String>>>()
        var refused: String? = null

        override suspend fun postFields(url: String, headers: Map<String, String>, fields: Map<String, String>): Result<String> {
            forms += url to fields
            return answer(url)
        }
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
