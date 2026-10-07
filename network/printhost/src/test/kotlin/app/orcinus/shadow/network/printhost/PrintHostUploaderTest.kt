package app.orcinus.shadow.network.printhost

import app.orcinus.shadow.core.model.HostPrintersOutcome
import app.orcinus.shadow.core.model.HostStorageOutcome
import app.orcinus.shadow.core.model.ModelSettings
import app.orcinus.shadow.core.model.OrcaText
import app.orcinus.shadow.core.model.PhysicalPrinter
import app.orcinus.shadow.core.model.PrintHostTestOutcome
import app.orcinus.shadow.core.model.PrintHostUploadOutcome
import app.orcinus.shadow.core.model.PrintOptions
import app.orcinus.shadow.core.model.PrinterSlot
import app.orcinus.shadow.core.model.PrinterSlotsOutcome
import java.io.File
import java.io.IOException
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class PrintHostUploaderTest {
    @Test
    fun `OctoPrint takes the file and whether to print in one request`() {
        val http = FakeHttpClient(answer = Result.success("{}"), info = """{"api": "0.1", "text": "OctoPrint 1.9.3"}""")
        val printer = printer("octoprint", "http://192.168.1.50", "abcdef")

        val outcome = runSuspend { PrintHostUploader(http).upload(printer, gcode(), "plate.gcode", startPrint = true) }

        assertEquals(PrintHostUploadOutcome.Success("plate.gcode"), outcome)
        val request = http.multipart.single()
        assertEquals("http://192.168.1.50/api/files/local", request.url)
        assertEquals("abcdef", request.headers["X-Api-Key"])
        assertEquals("true", request.fields["print"])
        assertEquals("plate.gcode", request.fileName)
    }

    @Test
    fun `OctoPrint trusts the printer's HTTPS CA file, Duet does not take one`() {
        val trusted = mutableListOf<String>()
        val http = object : HttpClient by FakeHttpClient(Result.success("{}")) {
            override fun withCaFile(path: String): HttpClient {
                trusted += path
                return this
            }
        }
        val ca = mapOf("printhost_cafile" to "/certificates/home.pem")

        runSuspend { PrintHostUploader(http).upload(printer("octoprint", "https://octopi.local", "key", ca), gcode(), "plate.gcode", startPrint = false) }
        runSuspend { PrintHostUploader(http).test(printer("duet", "duet.local", "", ca)) }

        assertEquals(listOf("/certificates/home.pem"), trusted)
    }

    @Test
    fun `a CA file that cannot be read fails the HTTPS request, as curl's CAINFO does`() {
        val file = File.createTempFile("orcinus", ".pem").also { it.writeText("not a certificate") }

        // The client goes to the IO dispatcher, which runSuspend does not wait for.
        val answer = runBlocking { UrlConnectionHttpClient().withCaFile(file.path).get("https://127.0.0.1:9/", emptyMap()) }

        assertTrue(answer.exceptionOrNull()?.message.orEmpty().startsWith("Problem with the SSL CA cert"))
        file.delete()
    }

    @Test
    fun `Moonraker uploads into the gcodes root and starts the print with the path the host stored`() {
        val http = FakeHttpClient(answer = Result.success("""{"result": {"item": {"path": "orcinus/plate.gcode"}}}"""), info = """{"result": {"klippy_state": "ready"}}""")
        val printer = printer("moonraker", "192.168.1.60", "key")

        val outcome = runSuspend { PrintHostUploader(http).upload(printer, gcode(), "plate.gcode", startPrint = true) }

        assertEquals(PrintHostUploadOutcome.Success("orcinus/plate.gcode"), outcome)
        val upload = http.multipart.single()
        // A host without a scheme is reached over http (OctoPrint::make_url).
        assertEquals("http://192.168.1.60/server/files/upload", upload.url)
        assertEquals("gcodes", upload.fields["root"])
        val start = http.json.single()
        assertEquals("http://192.168.1.60/printer/print/start", start.url)
        assertEquals("""{"filename":"orcinus/plate.gcode"}""", start.body)
    }

    @Test
    fun `Moonraker only uploads when the print is not to start`() {
        val http = FakeHttpClient(answer = Result.success("""{"result": {"item": {"path": "plate.gcode"}}}"""), info = """{"result": {"klippy_state": "ready"}}""")

        val outcome = runSuspend {
            PrintHostUploader(http).upload(printer("moonraker", "http://host/", "k"), gcode(), "plate.gcode", startPrint = false)
        }

        assertEquals(PrintHostUploadOutcome.Success("plate.gcode"), outcome)
        assertTrue(http.json.isEmpty())
        // A host that ends with a slash keeps its one separator.
        assertEquals("http://host/server/files/upload", http.multipart.single().url)
    }

    @Test
    fun `a host that refuses the upload is reported`() {
        val http = FakeHttpClient(answer = Result.failure(HttpStatusException(403, "Forbidden")), info = """{"api": "0.1", "text": "OctoPrint 1.9.3"}""")

        val outcome = runSuspend {
            PrintHostUploader(http).upload(printer("octoprint", "http://host", "k"), gcode(), "plate.gcode", startPrint = true)
        }

        // PrintHost::format_error(): the status and the body, untranslated.
        assertEquals(PrintHostUploadOutcome.Failure("HTTP 403: Forbidden", listOf(OrcaText("HTTP 403: Forbidden"))), outcome)
    }

    @Test
    fun `a K2 takes the file without a folder and prints every filament from the slot chosen for it`() {
        val http = FakeHttpClient(answer = Result.success("{}"), info = """{"model": "F008"}""")
        val socket = FakeWebSocket()
        val slots = listOf(
            PrinterSlot("T1C", "PLA", "#FFFFFF", boxId = 1, materialId = 2),
            PrinterSlot("T1A", "PETG", "#000000", boxId = 1, materialId = 0),
        )

        val outcome = runSuspend {
            PrintHostUploader(http, socket).upload(
                printer("crealityprint", "192.168.1.70", "token"),
                gcode(),
                "my plate.gcode",
                startPrint = true,
                options = PrintOptions(selfTest = true, slots = slots),
            )
        }

        // safe_filename(): the printer stores no spaces.
        assertEquals(PrintHostUploadOutcome.Success("my_plate.gcode"), outcome)
        assertEquals("http://192.168.1.70/info", http.gets.single())
        val upload = http.multipart.single()
        assertEquals("http://192.168.1.70/upload/my_plate.gcode", upload.url)
        assertEquals("Bearer token", upload.headers["Authorization"])
        // A K2 prints from its boxes and takes no folder.
        assertTrue("path" !in upload.fields)
        // start_print(): the slots of the filaments, then the print.
        val exchange = socket.exchanges.single()
        assertEquals("ws://192.168.1.70:9999/", exchange.url)
        assertEquals(2, exchange.messages.size)
        val colorMatch = parse(exchange.messages[0])["params"]!!.jsonObject["colorMatch"]!!.jsonObject
        assertEquals("/mnt/UDISK/printer_data/gcodes/my_plate.gcode", colorMatch["path"]!!.jsonPrimitive.content)
        val first = colorMatch["list"]!!.jsonArray[0].jsonObject
        // The id is the G-code's own tool, not the slot: the firmware matches by it.
        assertEquals("T1A", first["id"]!!.jsonPrimitive.content)
        assertEquals("2", first["materialId"]!!.jsonPrimitive.content)
        assertEquals("T1B", colorMatch["list"]!!.jsonArray[1].jsonObject["id"]!!.jsonPrimitive.content)
        val print = parse(exchange.messages[1])["params"]!!.jsonObject["multiColorPrint"]!!.jsonObject
        assertEquals("1", print["enableSelfTest"]!!.jsonPrimitive.content)
    }

    @Test
    fun `a print from the spool holder only opens the file`() {
        val http = FakeHttpClient(answer = Result.success("{}"), info = """{"model": "F008"}""")
        val socket = FakeWebSocket()

        runSuspend {
            PrintHostUploader(http, socket).upload(
                printer("crealityprint", "http://k2.local/", ""),
                gcode(),
                "plate.gcode",
                startPrint = true,
                options = PrintOptions(slots = listOf(PrinterSlot("T0A", "PLA", "#FFFFFF", boxId = 0, materialId = 0))),
            )
        }

        val messages = socket.exchanges.single().messages
        assertEquals(1, messages.size)
        assertTrue(messages.single().contains("printprt:/mnt/UDISK/printer_data/gcodes/plate.gcode"))
        assertEquals("ws://k2.local:9999/", socket.exchanges.single().url)
    }

    @Test
    fun `a Creality printer without material boxes prints from its own folder`() {
        val http = FakeHttpClient(answer = Result.success("{}"), info = """{"model": "K1C"}""")
        val socket = FakeWebSocket()

        runSuspend {
            PrintHostUploader(http, socket).upload(printer("crealityprint", "10.0.0.2", ""), gcode(), "plate.gcode", startPrint = true)
        }

        assertEquals("", http.multipart.single().fields["path"])
        assertTrue(socket.exchanges.single().messages.single().contains("printprt:/usr/data/printer_data/gcodes/plate.gcode"))
    }

    @Test
    fun `the slots of the material boxes are read as the desktop dialog reads them`() {
        val socket = FakeWebSocket(
            answer = """
                {"boxsInfo": {"materialBoxs": [
                  {"id": 0, "type": 1, "materials": [{"id": 0, "type": "PLA", "color": "#0FFFFFF"}]},
                  {"id": 1, "type": 0, "state": 1, "materials": [
                    {"id": 0, "type": "PLA", "color": "#0112233"},
                    {"id": 1, "type": "PETG", "color": "#0AABBCC"}
                  ]},
                  {"id": 2, "type": 0, "state": 0, "materials": [{"id": 0, "type": "ABS", "color": "#0000000"}]}
                ]}}
            """.trimIndent(),
        )

        val outcome = runSuspend {
            PrintHostUploader(FakeHttpClient(Result.success("{}"), info = """{"model": "F008"}"""), socket).printerSlots(printer("crealityprint", "k2", ""))
        }

        val found = outcome as PrinterSlotsOutcome.Success
        val slots = found.slots
        // A K2 Plus, which prints from its boxes (supports_multi_color_print()).
        assertTrue(found.multiColor)
        assertEquals("K2 Plus", found.modelName)
        // The inactive box 2 is left out; Creality's "#0RRGGBB" becomes "#RRGGBB".
        assertEquals(listOf("T0A", "T1A", "T1B"), slots.map(PrinterSlot::toolId))
        assertEquals("#112233", slots[1].color)
        assertEquals("Ext - PLA", slots[0].label)
        assertEquals("1B - PETG", slots[2].label)
        assertTrue(socket.exchanges.single().messages.single().contains("boxsInfo"))

        // A printer of another platform maps nothing, and its boxes are not asked for.
        val k1 = FakeWebSocket(answer = "{}")
        val other = runSuspend {
            PrintHostUploader(FakeHttpClient(Result.success("{}"), info = """{"model": "K1C"}"""), k1).printerSlots(printer("crealityprint", "k1", ""))
        } as PrinterSlotsOutcome.Success
        assertFalse(other.multiColor)
        assertEquals("unknown (K1C)", other.modelName)
        assertTrue(other.slots.isEmpty())
        assertTrue(k1.exchanges.isEmpty())
    }

    @Test
    fun `a host is tested before the file goes, and a failed test is the upload's error`() {
        // OctoPrint::upload_inner_with_host(): test() first; another host there is a mismatch.
        val klipper = FakeHttpClient(answer = Result.success("{}"), info = """{"api": "0.1", "text": "Klipper"}""")
        val mismatched = runSuspend { PrintHostUploader(klipper).upload(printer("octoprint", "host", ""), gcode(), "plate.gcode", startPrint = true) }
        assertEquals(
            PrintHostUploadOutcome.Failure("Mismatched type of print host: Klipper", listOf(OrcaText("Mismatched type of print host: %s", listOf("Klipper")))),
            mismatched,
        )
        assertTrue(klipper.multipart.isEmpty())

        // Moonraker::upload(): server/info without result.klippy_state.
        val notMoonraker = FakeHttpClient(answer = Result.success("{}"), info = """{"api": "0.1"}""")
        val moonraker = runSuspend { PrintHostUploader(notMoonraker).upload(printer("moonraker", "host", ""), gcode(), "plate.gcode", startPrint = false) }
        assertTrue(moonraker is PrintHostUploadOutcome.Failure && moonraker.message.contains("klippy_state"))
        assertTrue(notMoonraker.multipart.isEmpty())

        // Repetier::upload(): the server's software names another host.
        val other = FakeHttpClient(answer = Result.success("{}"), info = """{"name": "MyPrinter", "software": "Something"}""")
        val repetier = runSuspend { PrintHostUploader(other).upload(printer("repetier", "host", ""), gcode(), "plate.gcode", startPrint = false) }
        assertEquals("Mismatched type of print host: Something", (repetier as PrintHostUploadOutcome.Failure).message)

        // PrusaLink::validate_version_text(): a host without a version text is not PrusaLink.
        val bare = FakeHttpClient(answer = Result.success("{}"), info = """{"api": "2.0"}""")
        val prusa = runSuspend { PrintHostUploader(bare).upload(printer("prusalink", "host", "key"), gcode(), "plate.gcode", startPrint = false) }
        assertEquals("Mismatched type of print host: OctoPrint", (prusa as PrintHostUploadOutcome.Failure).message)
    }

    @Test
    fun `a request that got no answer says why as format_error words it`() {
        assertEquals(listOf(OrcaText(TIMED_OUT)), formatError(java.net.SocketTimeoutException("Read timed out")))
        assertEquals(listOf(OrcaText(UNRESOLVED)), formatError(java.net.UnknownHostException("octopi.local")))
        assertEquals(listOf(OrcaText(INTERRUPTED)), formatError(java.net.SocketException("Connection reset")))
        assertEquals(listOf(OrcaText("HTTP 404: Not Found")), formatError(HttpStatusException(404, "Not Found")))
        assertEquals("Mismatched type of print host: Klipper", english(listOf(OrcaText("Mismatched type of print host: %s", listOf("Klipper")))))
        assertEquals("/usb : read only", english(listOf(OrcaText("%1% : read only", listOf("/usb")))))
    }

    @Test
    fun `an Elegoo printer that is not a Centauri takes OctoPrint's upload with the plate of a gcode 3mf`() {
        val http = FakeHttpClient(answer = Result.success("{}"), info = """{"api": "0.1", "text": "Elegoo Link 1.0"}""")
        val printer = printer("elegoolink", "192.168.1.30", "", mapOf("printer_model" to "Elegoo Neptune 4"))

        val outcome = runSuspend {
            PrintHostUploader(http).upload(printer, gcode(), "plate.gcode.3mf", startPrint = true, options = PrintOptions(use3mf = true, plateIndex = 2))
        }

        assertEquals(PrintHostUploadOutcome.Success("plate.gcode.3mf"), outcome)
        assertEquals("http://192.168.1.30/api/version", http.gets.single())
        assertEquals("2", http.multipart.single().fields["plateindex"])
    }

    @Test
    fun `Duet says what rr_connect's error means, wants 201 from DSF and prints with M32`() {
        val refused = FakeHttpClient(answer = Result.success("{}"), info = """{"err": 1}""")
        val wrong = runSuspend { PrintHostUploader(refused).upload(printer("duet", "duet", "nope"), gcode(), "plate.gcode", startPrint = true) }
        assertEquals(listOf(OrcaText("Wrong password")), (wrong as PrintHostUploadOutcome.Failure).text)
        assertTrue(refused.files.isEmpty())
        val busy = FakeHttpClient(answer = Result.success("{}"), info = """{"err": 2}""")
        assertEquals(
            "Could not get resources to create a new connection",
            (runSuspend { PrintHostUploader(busy).test(printer("duet", "duet", "")) } as PrintHostTestOutcome.Failure).message,
        )

        // A board that does not answer rr_connect is a DSF host, which answers a stored file with 201.
        val dsf = object : HttpClient by FakeHttpClient(Result.success("{}")) {
            val inner = FakeHttpClient(answer = Result.success("{}"), fileStatus = 200)

            override suspend fun get(url: String, headers: Map<String, String>, auth: HttpAuth?): Result<String> =
                if (url.contains("rr_connect")) Result.failure(HttpStatusException(404, "")) else inner.get(url, headers, auth)

            override suspend fun sendFileAnswer(
                url: String,
                method: String,
                headers: Map<String, String>,
                file: File,
                onProgress: ((sent: Long, total: Long) -> Unit)?,
                auth: HttpAuth?,
            ): Result<HttpAnswer> = inner.sendFileAnswer(url, method, headers, file, onProgress, auth)
        }
        val notCreated = runSuspend { PrintHostUploader(dsf).upload(printer("duet", "dsf.local", ""), gcode(), "plate one.gcode", startPrint = true) }
        assertEquals("Unknown error occurred", (notCreated as PrintHostUploadOutcome.Failure).message)
        assertEquals("http://dsf.local/machine/file/gcodes/plate%20one.gcode", dsf.inner.files.single().url)
        assertEquals("PUT", dsf.inner.files.single().method)

        // rr_gcode with M32 and the file of the gcodes folder, escaped.
        val rr = FakeHttpClient(answer = Result.success("""{"err": 0}"""), info = """{"err": 0}""")
        runSuspend { PrintHostUploader(rr).upload(printer("duet", "192.168.1.80", ""), gcode(), "plate one.gcode", startPrint = true) }
        assertTrue(rr.gets.any { it == "http://192.168.1.80/rr_gcode?gcode=M32%20\"0:/gcodes/plate%20one.gcode\"" })
    }

    @Test
    fun `PrusaLink with a login answers the digest instead of giving the key, and PrusaConnect has no storages`() {
        val http = FakeHttpClient(answer = Result.success("{}"), info = """{"api": "2.0", "text": "PrusaLink 0.7.0", "capabilities": {"upload-by-put": true}}""")
        val login = printer(
            "prusalink",
            "host",
            "key",
            mapOf("printhost_authorization_type" to "user", "printhost_user" to "maker", "printhost_password" to "secret"),
        )

        runSuspend { PrintHostUploader(http).upload(login, gcode(), "plate.gcode", startPrint = false) }

        val sent = http.files.single()
        assertEquals(null, sent.headers["X-Api-Key"])
        assertEquals(HttpAuth("maker", "secret"), sent.auth)
        assertEquals(HttpAuth("maker", "secret"), http.getAuth.single())

        val connect = FakeHttpClient(answer = Result.success("{}"))
        assertEquals(
            HostStorageOutcome.Success(emptyList(), emptyList()),
            runSuspend { PrintHostUploader(connect).storage(printer("prusaconnect", "https://connect.prusa3d.com", "key")) },
        )
        assertTrue(connect.gets.isEmpty())
    }

    private fun parse(message: String) = kotlinx.serialization.json.Json.parseToJsonElement(message).jsonObject

    private fun printer(hostType: String, host: String, key: String, extra: Map<String, String> = emptyMap()) = PhysicalPrinter(
        name = "Test",
        settings = ModelSettings(mapOf("host_type" to hostType, "print_host" to host, "printhost_apikey" to key) + extra),
    )

    private fun gcode(): File = File.createTempFile("orcinus", ".gcode").also {
        it.writeText("; test\nG1 X0 Y0\n")
        it.deleteOnExit()
    }

    /** The requests the uploader made, and the one answer it gets. */
    @Test
    fun `the test button asks each host what it is`() {
        // OctoPrint::test(): api/version, and the text has to start with OctoPrint.
        val octo = FakeHttpClient(answer = Result.success("{}"), info = """{"api": "0.1", "text": "OctoPrint 1.9.3"}""")
        val ok = runSuspend { PrintHostUploader(octo).test(printer("octoprint", "http://192.168.1.50", "abcdef")) }
        assertEquals(PrintHostTestOutcome.Success("OctoPrint 1.9.3"), ok)
        assertEquals("http://192.168.1.50/api/version", octo.gets.single())

        // Another host answering there is not OctoPrint (validate_version_text).
        val other = FakeHttpClient(answer = Result.success("{}"), info = """{"api": "0.1", "text": "Klipper"}""")
        val mismatched = runSuspend { PrintHostUploader(other).test(printer("octoprint", "host", "")) }
        assertTrue(mismatched is PrintHostTestOutcome.Failure && mismatched.message.contains("Klipper"))

        // Moonraker::test(): server/info with result.klippy_state.
        val moonraker = FakeHttpClient(answer = Result.success("{}"), info = """{"result": {"klippy_state": "ready"}}""")
        val state = runSuspend { PrintHostUploader(moonraker).test(printer("moonraker", "192.168.1.60", "key")) }
        assertEquals(PrintHostTestOutcome.Success("ready"), state)
        assertEquals("http://192.168.1.60/server/info", moonraker.gets.single())

        // An OctoPrint answering where Moonraker is expected is a mismatch.
        val wrong = FakeHttpClient(answer = Result.success("{}"), info = """{"api": "0.1"}""")
        assertTrue(runSuspend { PrintHostUploader(wrong).test(printer("moonraker", "host", "")) } is PrintHostTestOutcome.Failure)

        // CrealityPrint::test(): info, which reports the model.
        val creality = FakeHttpClient(answer = Result.success("{}"), info = """{"model": "F008"}""")
        assertEquals(
            PrintHostTestOutcome.Success("F008"),
            runSuspend { PrintHostUploader(creality).test(printer("crealityprint", "192.168.8.180", "")) },
        )
    }

    @Test
    fun `a host that does not answer is reported with its error`() {
        val http = object : HttpClient by FakeHttpClient(Result.success("{}")) {
            override suspend fun get(url: String, headers: Map<String, String>, auth: HttpAuth?) =
                Result.failure<String>(IOException("failed to connect"))
        }

        val outcome = runSuspend { PrintHostUploader(http).test(printer("octoprint", "host", "")) }

        assertTrue(outcome is PrintHostTestOutcome.Failure && outcome.message.contains("failed to connect"))
    }

    @Test
    fun `PrusaLink puts the file when the host takes it by put`() {
        val http = FakeHttpClient(
            answer = Result.success("{}"),
            info = """{"api": "2.0", "text": "PrusaLink 0.7.0", "capabilities": {"upload-by-put": "true"}}""",
        )

        val outcome = runSuspend {
            PrintHostUploader(http).upload(printer("prusalink", "http://192.168.1.70", "key"), gcode(), "plate.gcode", startPrint = true)
        }

        assertEquals(PrintHostUploadOutcome.Success("plate.gcode"), outcome)
        val sent = http.files.single()
        assertEquals("http://192.168.1.70/api/v1/files/local/plate.gcode", sent.url)
        assertEquals("PUT", sent.method)
        assertEquals("text/x.gcode", sent.headers["Content-Type"])
        assertEquals("?1", sent.headers["Overwrite"])
        assertEquals("?1", sent.headers["Print-After-Upload"])
        assertEquals("key", sent.headers["X-Api-Key"])
    }

    @Test
    fun `PrusaLink posts the file to a host that does not take it by put`() {
        val http = FakeHttpClient(answer = Result.success("{}"), info = """{"api": "2.0", "text": "OctoPrint 1.9.3"}""")

        val outcome = runSuspend {
            PrintHostUploader(http).upload(printer("prusalink", "host", "key"), gcode(), "plate.gcode", startPrint = false)
        }

        assertEquals(PrintHostUploadOutcome.Success("plate.gcode"), outcome)
        assertTrue(http.files.isEmpty())
        assertEquals("http://host/api/files/local", http.multipart.single().url)
    }

    @Test
    fun `the upload path's folder goes where each host takes it, with the storage and the group the dialog chose`() {
        val octo = FakeHttpClient(answer = Result.success("{}"), info = """{"api": "0.1", "text": "OctoPrint 1.9.3"}""")
        runSuspend { PrintHostUploader(octo).upload(printer("octoprint", "host", "key"), gcode(), "parts/plate.gcode", startPrint = false) }
        assertEquals("parts", octo.multipart.single().fields["path"])
        assertEquals("plate.gcode", octo.multipart.single().fileName)

        // PrusaLink's PUT: the storage, then every element of the path escaped on its own.
        val link = FakeHttpClient(answer = Result.success("{}"), info = """{"api": "2.0", "text": "PrusaLink 0.7.0", "capabilities": {"upload-by-put": "true"}}""")
        runSuspend {
            PrintHostUploader(link).upload(printer("prusalink", "host", "key"), gcode(), "my parts/plate one.gcode", false, PrintOptions(storage = "/usb"))
        }
        assertEquals("http://host/api/v1/files/usb/my%20parts/plate%20one.gcode", link.files.single().url)

        // Moonraker: the root chosen, the file by its name alone.
        val moon = FakeHttpClient(answer = Result.success("{}"), info = """{"result": {"klippy_state": "ready"}}""")
        runSuspend { PrintHostUploader(moon).upload(printer("moonraker", "host", "key"), gcode(), "parts/plate.gcode", false, PrintOptions(storage = "timelapse")) }
        assertEquals("timelapse", moon.multipart.single().fields["root"])
        assertEquals("plate.gcode", moon.multipart.single().fileName)

        // Repetier: a group of its own; its default group ("#") is not sent.
        val repetier = FakeHttpClient(answer = Result.success("{}"))
        val server = printer("repetier", "host", "key", mapOf("printhost_port" to "mk3"))
        runSuspend { PrintHostUploader(repetier).upload(server, gcode(), "plate.gcode", false, PrintOptions(group = "Parts")) }
        runSuspend { PrintHostUploader(repetier).upload(server, gcode(), "plate.gcode", false, PrintOptions(group = "#")) }
        assertEquals("Parts", repetier.multipart[0].fields["group"])
        assertTrue("group" !in repetier.multipart[1].fields)
    }

    @Test
    fun `PrusaLink offers its writable storages with free space, and fails when it has none`() {
        val listing = """{"storage_list": [
            {"name": "USB", "path": "/usb", "read_only": false, "free_space": "1000", "available": true},
            {"name": "SD", "path": "/sdcard", "ro": true, "available": true},
            {"name": "Gone", "path": "/gone", "available": false}]}"""
        val http = FakeHttpClient(answer = Result.success("{}"), info = listing)
        assertEquals(
            HostStorageOutcome.Success(listOf("/usb"), listOf("USB")),
            runSuspend { PrintHostUploader(http).storage(printer("prusalink", "host", "key")) },
        )

        val full = FakeHttpClient(answer = Result.success("{}"), info = """{"storage_list": [{"name": "SD", "path": "/sdcard", "ro": true}]}""")
        val failed = runSuspend { PrintHostUploader(full).storage(printer("prusalink", "host", "key")) }
        assertEquals(
            HostStorageOutcome.Failure("Upload has failed. There is no suitable storage found at host.\n\nStorages found: \n/sdcard : read only\n"),
            failed,
        )

        // Moonraker: the roots a file can be written to.
        val roots = FakeHttpClient(
            answer = Result.success("{}"),
            info = """{"result": [{"name": "gcodes", "permissions": "rw"}, {"name": "config", "permissions": "r"}]}""",
        )
        assertEquals(HostStorageOutcome.Success(listOf("gcodes"), listOf("gcodes")), runSuspend { PrintHostUploader(roots).storage(printer("moonraker", "host", "")) })
    }

    @Test
    fun `Duet connects, uploads into the gcodes folder and prints the file with M32`() {
        val http = FakeHttpClient(answer = Result.success("""{"err": 0}"""), info = """{"err": 0}""")

        val outcome = runSuspend {
            PrintHostUploader(http).upload(printer("duet", "192.168.1.80", ""), gcode(), "plate.gcode", startPrint = true)
        }

        assertEquals(PrintHostUploadOutcome.Success("plate.gcode"), outcome)
        // Duet::get_connect_url(): the board's own password when none is given.
        assertTrue(http.gets.first().startsWith("http://192.168.1.80/rr_connect?password=reprap&time="))
        val sent = http.files.single()
        assertTrue(sent.url.startsWith("http://192.168.1.80/rr_upload?name=0:/gcodes/plate.gcode&time="))
        assertEquals("POST", sent.method)
        assertTrue(http.gets.any { it.contains("rr_gcode?gcode=M32") && it.contains("plate.gcode") })
        assertTrue(http.gets.last().endsWith("rr_disconnect"))
    }

    @Test
    fun `Duet takes its password from the key field`() {
        val http = FakeHttpClient(answer = Result.success("""{"err": 0}"""), info = """{"err": 0}""")

        runSuspend { PrintHostUploader(http).test(printer("duet", "192.168.1.80", "secret")) }

        assertTrue(http.gets.first().startsWith("http://192.168.1.80/rr_connect?password=secret&time="))
    }

    @Test
    fun `MKS posts the file and prints it with M23 and M24 on its console`() {
        val http = FakeHttpClient(answer = Result.success("""{"err": 0}"""))
        val console = FakeConsole()
        val uploader = PrintHostUploader(http, console = console, mksStartDelayMillis = 0)

        val tested = runSuspend { uploader.test(printer("mks", "192.168.1.40", "")) }
        val outcome = runSuspend { uploader.upload(printer("mks", "192.168.1.40", ""), gcode(), "plate one.gcode", startPrint = true) }

        assertTrue(tested is PrintHostTestOutcome.Success)
        assertEquals(PrintHostUploadOutcome.Success("plate one.gcode"), outcome)
        val sent = http.files.single()
        assertEquals("http://192.168.1.40/upload?X-Filename=plate%20one.gcode", sent.url)
        assertEquals("POST", sent.method)
        assertEquals(
            listOf(
                FakeConsole.Run("192.168.1.40", 8080, listOf("M105")),
                FakeConsole.Run("192.168.1.40", 8080, listOf("M23 plate one.gcode", "M24")),
            ),
            console.runs,
        )
    }

    @Test
    fun `MKS stops at an error code of the board`() {
        val console = FakeConsole()
        val uploader = PrintHostUploader(FakeHttpClient(answer = Result.success("""{"err": 1}""")), console = console, mksStartDelayMillis = 0)

        val outcome = runSuspend { uploader.upload(printer("mks", "host", ""), gcode(), "plate.gcode", startPrint = true) }

        assertTrue(outcome is PrintHostUploadOutcome.Failure)
        assertTrue(console.runs.isEmpty())
    }

    @Test
    fun `PrusaConnect posts to_print in the language of the app`() {
        val http = FakeHttpClient(answer = Result.success("{}"), info = """{"api": "2.0", "text": "PrusaLink 2.1"}""")

        val outcome = runSuspend {
            PrintHostUploader(http).upload(printer("prusaconnect", "https://connect.prusa3d.com", "key"), gcode(), "plate.gcode", startPrint = true)
        }

        assertEquals(PrintHostUploadOutcome.Success("plate.gcode"), outcome)
        val request = http.multipart.single()
        assertEquals("https://connect.prusa3d.com/api/files/local", request.url)
        assertEquals("True", request.fields["to_print"])
        assertEquals(null, request.fields["print"])
        assertEquals(java.util.Locale.getDefault().language.take(2), request.headers["Accept-Language"])
    }

    @Test
    fun `Repetier lists the printers of the server by their slug`() {
        val http = FakeHttpClient(
            answer = Result.success("{}"),
            info = """{"data": [{"name": "Left", "slug": "Left_i3"}, {"name": "Right", "slug": "Right_i3"}]}""",
        )

        val outcome = runSuspend { PrintHostUploader(http).printers(printer("repetier", "192.168.1.90", "key")) }

        assertEquals(HostPrintersOutcome.Success(listOf("Left_i3", "Right_i3")), outcome)
        assertEquals("http://192.168.1.90/printer/list", http.gets.single())
        // A server that says what went wrong is reported with its words.
        val refused = FakeHttpClient(answer = Result.success("{}"), info = """{"error": "Access denied"}""")
        assertEquals(
            HostPrintersOutcome.Failure("Access denied"),
            runSuspend { PrintHostUploader(refused).printers(printer("repetier", "host", "")) },
        )
    }

    @Test
    fun `Repetier posts to the job of the printer when it is to print at once`() {
        val http = FakeHttpClient(answer = Result.success("{}"), info = """{"software": "Repetier-Server"}""")
        val printer = printer("repetier", "http://192.168.1.90", "key", mapOf("printhost_port" to "Printer1"))

        val outcome = runSuspend { PrintHostUploader(http).upload(printer, gcode(), "plate.gcode", startPrint = true) }

        assertEquals(PrintHostUploadOutcome.Success("plate.gcode"), outcome)
        val request = http.multipart.single()
        assertEquals("http://192.168.1.90/printer/job/Printer1", request.url)
        assertEquals("upload", request.fields["a"])
        assertEquals("true", request.fields["autostart"])
        assertEquals("key", request.headers["X-Api-Key"])
    }

    @Test
    fun `Repetier posts to the models of the printer when it only stores`() {
        val http = FakeHttpClient(answer = Result.success("{}"))
        val printer = printer("repetier", "host", "key", mapOf("printhost_port" to "Printer1"))

        runSuspend { PrintHostUploader(http).upload(printer, gcode(), "plate.gcode", startPrint = false) }

        assertEquals("http://host/printer/model/Printer1", http.multipart.single().url)
        assertTrue(http.multipart.single().fields["autostart"] == null)
    }

    @Test
    fun `ESP3D shortens the name and prints it with M23 and M24`() {
        val http = FakeHttpClient(answer = Result.success("ok"))

        val outcome = runSuspend {
            PrintHostUploader(http, esp3dStartDelayMillis = 0).upload(printer("esp3d", "192.168.1.95", ""), gcode(), "plate one.gcode", startPrint = true)
        }

        // get_short_name(): the stem cut to 8 characters, the extension to 3, as they are.
        assertEquals(PrintHostUploadOutcome.Success("plate on.gco"), outcome)
        assertEquals("http://192.168.1.95/upload_serial", http.multipart.single().url)
        assertEquals("plate on.gco", http.multipart.single().fileName)
        // ESP3D::upload() does not test the board first.
        assertEquals(listOf("http://192.168.1.95/command?plain=M23%20plate%20on.gco", "http://192.168.1.95/command?plain=M24"), http.gets)
        assertEquals("a.b.gcod.3mf", PrintHostUploader.shortName("parts/a.b.gcode.3mf"))
        assertEquals("noextens", PrintHostUploader.shortName("noextension"))
    }

    @Test
    fun `FlashAir tests and prepares the card before the file and never prints`() {
        val http = FakeHttpClient(answer = Result.success("SUCCESS"), routes = mapOf("command.cgi?op=118" to "1", "upload.cgi" to "SUCCESS"))

        val outcome = runSuspend {
            PrintHostUploader(http).upload(printer("flashair", "192.168.1.99", ""), gcode(), "plate.gcode", startPrint = true)
        }

        assertEquals(PrintHostUploadOutcome.Success("plate.gcode"), outcome)
        assertEquals("http://192.168.1.99/command.cgi?op=118", http.gets[0])
        assertTrue(http.gets[1].contains("upload.cgi?WRITEPROTECT=ON&FTIME=0x"))
        assertEquals("http://192.168.1.99/upload.cgi?UPDIR=/", http.gets[2])
        assertEquals("http://192.168.1.99/upload.cgi", http.multipart.single().url)

        // A folder gets its leading slash; an answer without SUCCESS stops the upload.
        val refused = FakeHttpClient(answer = Result.success("SUCCESS"), routes = mapOf("command.cgi?op=118" to "1", "UPDIR" to "ERROR", "upload.cgi" to "SUCCESS"))
        val failed = runSuspend { PrintHostUploader(refused).upload(printer("flashair", "card", ""), gcode(), "parts/plate.gcode", startPrint = false) }
        assertEquals(listOf(OrcaText("Unknown error occurred")), (failed as PrintHostUploadOutcome.Failure).text)
        assertEquals("http://card/upload.cgi?UPDIR=/parts", refused.gets[2])
        assertTrue(refused.multipart.isEmpty())
        // FlashAir::timestamp_str(): the FAT date and time as "%#x".
        assertEquals("0x58a63905", PrintHostUploader.fatTime(java.time.LocalDateTime.of(2024, 5, 6, 7, 8, 10)))
    }

    @Test
    fun `the test button knows the hosts that took OctoPrint's API apart`() {
        // AstroBox::validate_version_text()
        val astro = FakeHttpClient(answer = Result.success("{}"), info = """{"api": "0.1", "text": "AstroBox 1.0"}""")
        assertEquals(
            PrintHostTestOutcome.Success("AstroBox 1.0"),
            runSuspend { PrintHostUploader(astro).test(printer("astrobox", "host", "")) },
        )
        // A PrusaLink host answering where AstroBox is expected is a mismatch.
        val prusa = FakeHttpClient(answer = Result.success("{}"), info = """{"api": "2.0", "text": "PrusaLink 0.7.0"}""")
        assertTrue(runSuspend { PrintHostUploader(prusa).test(printer("astrobox", "host", "")) } is PrintHostTestOutcome.Failure)
        // …and the same answer is right for PrusaLink itself.
        assertTrue(runSuspend { PrintHostUploader(prusa).test(printer("prusalink", "host", "")) } is PrintHostTestOutcome.Success)
        // validate_repetier(): a rebranded server still says Repetier-Server.
        val repetier = FakeHttpClient(answer = Result.success("{}"), info = """{"name": "MyPrinter", "software": "Repetier-Server"}""")
        assertTrue(runSuspend { PrintHostUploader(repetier).test(printer("repetier", "host", "")) } is PrintHostTestOutcome.Success)
        val other = FakeHttpClient(answer = Result.success("{}"), info = """{"name": "MyPrinter", "software": "Something"}""")
        assertTrue(runSuspend { PrintHostUploader(other).test(printer("repetier", "host", "")) } is PrintHostTestOutcome.Failure)
        // FlashAir::test(): op 118 answers 1 while uploads are allowed.
        val card = FakeHttpClient(answer = Result.success("{}"), info = "0")
        assertTrue(runSuspend { PrintHostUploader(card).test(printer("flashair", "host", "")) } is PrintHostTestOutcome.Failure)
    }

    @Test
    fun `a digest challenge is answered as curl answers it`() {
        val header = digestHeader(
            challenge = """Digest realm="Printer API", nonce="abc123", qop="auth", opaque="xyz"""",
            method = "PUT",
            path = "/api/v1/files/local/plate.gcode",
            auth = HttpAuth("maker", "secret"),
            cnonce = "0a4f113b",
        )

        assertTrue(header != null && header.startsWith("Digest username=\"maker\""))
        assertTrue(header!!.contains("""realm="Printer API""""))
        assertTrue(header.contains("""uri="/api/v1/files/local/plate.gcode""""))
        assertTrue(header.contains("qop=auth, nc=00000001, cnonce=\"0a4f113b\""))
        assertTrue(header.contains("""opaque="xyz""""))
        // The response is the MD5 chain of RFC 2617.
        val ha1 = md5Of("maker:Printer API:secret")
        val ha2 = md5Of("PUT:/api/v1/files/local/plate.gcode")
        val expected = md5Of("$ha1:abc123:00000001:0a4f113b:auth:$ha2")
        assertTrue(header.contains("""response="$expected""""))
    }

    private fun md5Of(text: String): String =
        java.security.MessageDigest.getInstance("MD5").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    private class FakeHttpClient(
        private val answer: Result<String>,
        private val info: String = "{}",
        /** GETs whose URL holds a key are answered with its value instead of [info]. */
        private val routes: Map<String, String> = emptyMap(),
        /** The status a file sent as the whole body is answered with. */
        private val fileStatus: Int = 200,
    ) : HttpClient {
        val multipart = mutableListOf<Multipart>()
        val json = mutableListOf<Json>()
        val gets = mutableListOf<String>()

        val files = mutableListOf<Sent>()
        val getAuth = mutableListOf<HttpAuth?>()
        val bytes = mutableListOf<Pair<String, String>>()

        override suspend fun get(url: String, headers: Map<String, String>, auth: HttpAuth?): Result<String> {
            gets += url
            getAuth += auth
            return Result.success(routes.entries.firstOrNull { url.contains(it.key) }?.value ?: info)
        }

        data class Multipart(val url: String, val headers: Map<String, String>, val fields: Map<String, String>, val fileName: String)

        data class Json(val url: String, val body: String)

        /** A file sent as the whole body (Duet, PrusaLink). */
        data class Sent(val url: String, val method: String, val headers: Map<String, String>, val auth: HttpAuth? = null)

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
            multipart += Multipart(url, headers, fields, fileName)
            onProgress?.invoke(file.length(), file.length())
            return answer
        }

        override suspend fun sendFile(
            url: String,
            method: String,
            headers: Map<String, String>,
            file: File,
            onProgress: ((sent: Long, total: Long) -> Unit)?,
            auth: HttpAuth?,
        ): Result<String> {
            files += Sent(url, method, headers, auth)
            onProgress?.invoke(file.length(), file.length())
            return answer
        }

        override suspend fun sendFileAnswer(
            url: String,
            method: String,
            headers: Map<String, String>,
            file: File,
            onProgress: ((sent: Long, total: Long) -> Unit)?,
            auth: HttpAuth?,
        ): Result<HttpAnswer> = sendFile(url, method, headers, file, onProgress, auth).map { HttpAnswer(fileStatus, it) }

        override suspend fun postJson(url: String, headers: Map<String, String>, body: String, auth: HttpAuth?): Result<String> {
            json += Json(url, body)
            return answer
        }

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
        ): Result<String> = answer

        override suspend fun sendBytes(url: String, method: String, headers: Map<String, String>, body: ByteArray): Result<String> {
            bytes += url to body.decodeToString()
            return answer
        }

        override suspend fun postFields(url: String, headers: Map<String, String>, fields: Map<String, String>): Result<String> =
            Result.success("{}")
    }

    /** The console exchanges the uploader made, all answered "ok". */
    private class FakeConsole : ConsoleClient {
        val runs = mutableListOf<Run>()

        data class Run(val host: String, val port: Int, val messages: List<String>)

        override suspend fun run(host: String, port: Int, messages: List<SerialMessage>, queueDelayMillis: Long): Result<Unit> {
            runs += Run(host, port, messages.map { it.message })
            return Result.success(Unit)
        }
    }

    /** The WebSocket exchanges the uploader made, and the one answer it gets. */
    private class FakeWebSocket(private val answer: String? = null) : WebSocketClient {
        val exchanges = mutableListOf<Exchange>()

        data class Exchange(val url: String, val messages: List<String>, val expect: String?)

        override suspend fun exchange(url: String, messages: List<String>, expect: String?): Result<String?> {
            exchanges += Exchange(url, messages, expect)
            return Result.success(if (expect == null) null else answer)
        }
    }

    private fun <T> runSuspend(block: suspend () -> T): T {
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
