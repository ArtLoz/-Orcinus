package app.orcinus.shadow.network.printhost

import app.orcinus.shadow.core.model.FlashforgeMapping
import app.orcinus.shadow.core.model.FlashforgeMaterialSlot
import app.orcinus.shadow.core.model.FlashforgeOptions
import app.orcinus.shadow.core.model.FlashforgeSend
import app.orcinus.shadow.core.model.FlashforgeSlotsOutcome
import app.orcinus.shadow.core.model.ModelSettings
import app.orcinus.shadow.core.model.PhysicalPrinter
import app.orcinus.shadow.core.model.PrintHostTestOutcome
import app.orcinus.shadow.core.model.PrintHostUploadOutcome
import app.orcinus.shadow.core.model.PrintOptions
import app.orcinus.shadow.core.model.SentFilament
import java.io.File
import java.util.Base64
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FlashforgeTest {
    @Test
    fun `the serial console announces the file, sends it in pieces, saves it and prints it`() {
        val console = RecordingConsole()
        val uploader = PrintHostUploader(RecordingHttp(), console = console, flashforgeSaveDelayMillis = 0)
        val printer = printer(host = "192.168.1.30", extra = mapOf("gcode_flavor" to "klipper"))
        val gcode = gcode(5000)

        val tested = run { uploader.test(printer) }
        val outcome = run { uploader.upload(printer, gcode, "Мой куб.gcode", startPrint = true) }

        assertTrue(tested is PrintHostTestOutcome.Success)
        // sanitize_flashforge_filename(): every byte but ASCII letters, digits, '.', '_' and '-' becomes '_'.
        val stored = "_____________.gcode"
        assertEquals(PrintHostUploadOutcome.Success(stored), outcome)
        val runs = console.runs
        assertEquals(listOf("~M601 S1\r\n"), runs[0].messages)
        // connect(): a Klipper firmware is told so with ~M640.
        assertEquals(listOf("~M601 S1\r\n", "~M115\r\n", "~M640\r\n", "~M119\r\n"), runs[1].messages)
        assertEquals("~M28 5000 0:/user/$stored", runs[2].messages.first())
        assertEquals(listOf(4096, 904), runs[2].messages.drop(1).map { it.length })
        assertTrue(runs[2].types.drop(1).all { it == SerialMessage.Type.DATA })
        assertEquals(listOf("~M29\r\n"), runs[3].messages)
        assertEquals(listOf("~M23 0:/user/$stored"), runs[4].messages)
        assertTrue(runs.all { it.host == "192.168.1.30" && it.port == 8899 })
    }

    @Test
    fun `the local API takes the file with the dialog's choices`() {
        val http = RecordingHttp(answer = """{"code": 0, "message": "Success"}""")
        val uploader = PrintHostUploader(http, console = RecordingConsole())
        val printer = printer(host = "http://192.168.1.31/", extra = mapOf("flashforge_serial_number" to "SNMOMC9900001", "printhost_apikey" to "1234"))
        val mapping = FlashforgeMapping(toolId = 0, slotId = 2, materialName = "PLA", toolMaterialColor = "#FF0000", slotMaterialColor = "#FE0000")
        val options = FlashforgeOptions(levelingBeforePrint = true, timeLapseVideo = false, useMaterialStation = true, mappings = listOf(mapping))

        val outcome = run { uploader.upload(printer, gcode(10), "plate.gcode", startPrint = true, options = PrintOptions(flashforge = options)) }

        assertEquals(PrintHostUploadOutcome.Success("plate.gcode"), outcome)
        val request = http.multipart.single()
        // make_http_url(): the host of the URL, on port 8898.
        assertEquals("http://192.168.1.31:8898/uploadGcode", request.url)
        assertEquals("gcodeFile", request.fileField)
        assertEquals("SNMOMC9900001", request.headers["serialNumber"])
        assertEquals("1234", request.headers["checkCode"])
        assertEquals("10", request.headers["fileSize"])
        assertEquals("true", request.headers["printNow"])
        assertEquals("true", request.headers["levelingBeforePrint"])
        assertEquals("false", request.headers["timeLapseVideo"])
        assertEquals("true", request.headers["useMatlStation"])
        assertEquals("1", request.headers["gcodeToolCnt"])
        assertEquals(
            """[{"materialName":"PLA","slotId":2,"slotMaterialColor":"#FE0000","toolId":0,"toolMaterialColor":"#FF0000"}]""",
            String(Base64.getDecoder().decode(request.headers["materialMappings"])),
        )
        // The printer refusing is reported with its code and words.
        val refused = PrintHostUploader(RecordingHttp(answer = """{"code": 3, "msg": "busy"}"""), console = RecordingConsole())
        assertEquals(
            PrintHostUploadOutcome.Failure("Flashforge local API error 3: busy"),
            run { refused.upload(printer, gcode(10), "plate.gcode", startPrint = false) },
        )
    }

    @Test
    fun `the local API reports the slots of the material station`() {
        val http = RecordingHttp(
            answer = """{"code":0,"detail":{"hasMatlStation":true,"matlStationInfo":{"slotCnt":4,"slotInfos":[
                {"slotId":1,"hasFilament":true,"materialName":"PLA","materialColor":"#FFFFFF"},
                {"slotId":2,"hasFilament":false,"materialName":"","materialColor":""}]}}}""",
        )
        val printer = printer(extra = mapOf("flashforge_serial_number" to "SN1", "printhost_apikey" to "code"))

        val outcome = run { PrintHostUploader(http, console = RecordingConsole()).flashforgeSlots(printer) }

        assertEquals(
            FlashforgeSlotsOutcome.Success(
                listOf(FlashforgeMaterialSlot(1, true, "PLA", "#FFFFFF"), FlashforgeMaterialSlot(2, false, "", "")),
                supportsMaterialStation = true,
            ),
            outcome,
        )
        assertEquals("http://192.168.1.30:8898/detail", http.json.single().first)
        assertEquals("""{"serialNumber":"SN1","checkCode":"code"}""", http.json.single().second)
    }

    @Test
    fun `a discovery answer names the printer and its serial number`() {
        val answer = ByteArray(0xC4)
        "Adventurer 5M".toByteArray().copyInto(answer, 0)
        "SNMOMC9900001".toByteArray().copyInto(answer, 0x92)

        val printer = Flashforge.parseDiscoveryResponse(answer, "192.168.1.30")

        assertEquals("Adventurer 5M", printer?.name)
        assertEquals("SNMOMC9900001", printer?.serialNumber)
        assertNull(Flashforge.parseDiscoveryResponse(ByteArray(0x40), "192.168.1.30"))
    }

    @Test
    fun `the send dialog feeds every filament from a loaded slot of its kind`() {
        val slots = listOf(
            FlashforgeMaterialSlot(1, true, "PLA Silk", "#FF0000"),
            FlashforgeMaterialSlot(2, true, "PLA", "#00FF00"),
            FlashforgeMaterialSlot(3, true, "PLA", "#FF1010"),
            FlashforgeMaterialSlot(4, false, "", ""),
        )
        val filaments = listOf(SentFilament("#FF0000", "PLA", tool = 0), SentFilament("#000000", "PETG", tool = 1))

        // normalize_material(): silk is its own kind; ASA counts as ABS.
        assertEquals("SILK", FlashforgeSend.normalizeMaterial("PLA Silk"))
        assertEquals("ABS", FlashforgeSend.normalizeMaterial("asa"))
        val assigned = FlashforgeSend.autoAssign(filaments, slots)
        // The red PLA takes the red PLA slot, not the red silk one; nothing holds PETG.
        assertEquals(listOf(3, null), assigned)
        assertEquals(
            "Each project material must be assigned to an IFS slot before printing.",
            FlashforgeSend.validate(useMaterialStation = true, filaments, assigned, slots),
        )
        assertEquals(
            "This plate uses multiple materials. Enable IFS and assign each tool to a printer slot.",
            FlashforgeSend.validate(useMaterialStation = false, filaments, assigned, slots),
        )
        assertNull(FlashforgeSend.validate(useMaterialStation = true, filaments.take(1), assigned.take(1), slots))
        assertEquals(listOf(FlashforgeMapping(0, 3, "PLA", "#FF0000", "#FF1010")), FlashforgeSend.mappings(filaments, assigned, slots))
    }

    private fun printer(host: String = "192.168.1.30", extra: Map<String, String> = emptyMap()) = PhysicalPrinter(
        name = "Test",
        settings = ModelSettings(mapOf("host_type" to "flashforge", "print_host" to host) + extra),
    )

    private fun gcode(size: Int): File = File.createTempFile("orcinus", ".gcode").also {
        it.writeBytes(ByteArray(size) { 'G'.code.toByte() })
        it.deleteOnExit()
    }

    private class RecordingConsole : ConsoleClient {
        val runs = mutableListOf<Run>()

        data class Run(val host: String, val port: Int, val messages: List<String>, val types: List<SerialMessage.Type>)

        override suspend fun run(host: String, port: Int, messages: List<SerialMessage>, queueDelayMillis: Long): Result<Unit> {
            runs += Run(host, port, messages.map { it.message }, messages.map { it.type })
            return Result.success(Unit)
        }
    }

    private class RecordingHttp(private val answer: String = "{}") : HttpClient {
        val multipart = mutableListOf<Multipart>()
        val json = mutableListOf<Pair<String, String>>()

        data class Multipart(val url: String, val headers: Map<String, String>, val fileField: String)

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
            multipart += Multipart(url, headers, fileField)
            return Result.success(answer)
        }

        override suspend fun sendFile(
            url: String,
            method: String,
            headers: Map<String, String>,
            file: File,
            onProgress: ((sent: Long, total: Long) -> Unit)?,
            auth: HttpAuth?,
        ): Result<String> = Result.success(answer)

        override suspend fun postJson(url: String, headers: Map<String, String>, body: String, auth: HttpAuth?): Result<String> {
            json += url to body
            return Result.success(answer)
        }

        override suspend fun get(url: String, headers: Map<String, String>, auth: HttpAuth?): Result<String> = Result.success(answer)
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
