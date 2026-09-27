package app.orcinus.shadow.network.printhost

import app.orcinus.shadow.core.model.ElegooKind
import app.orcinus.shadow.core.model.ElegooOptions
import app.orcinus.shadow.core.model.ModelSettings
import app.orcinus.shadow.core.model.PhysicalPrinter
import app.orcinus.shadow.core.model.PrintHostTestOutcome
import app.orcinus.shadow.core.model.PrintHostUploadOutcome
import app.orcinus.shadow.core.model.PrintOptions
import java.io.File
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class ElegooLinkTest {
    @Test
    fun `the printer model tells Elegoo's three protocols apart`() {
        assertEquals(ElegooKind.CC, ElegooKind.of("Elegoo Centauri Carbon"))
        assertEquals(ElegooKind.CC, ElegooKind.of("Elegoo Centauri"))
        assertEquals(ElegooKind.CC2, ElegooKind.of("Elegoo Centauri Carbon 2"))
        assertEquals(ElegooKind.CC2, ElegooKind.of("Elegoo Centauri 2 "))
        assertEquals(ElegooKind.OTHER, ElegooKind.of("Elegoo Neptune 4 Pro"))
    }

    @Test
    fun `a Centauri Carbon takes the file in pieces and starts it over its WebSocket`() {
        val http = ElegooHttp(page = "<title>ELEGOO</title>", piece = """{"code":"000000"}""")
        val socket = ScriptedSocket(
            """{"Status":{"CurrentStatus":[8]}}""",
            """{"Status":{"CurrentStatus":[0]}}""",
            """{"Data":{"Cmd":128,"Data":{"Ack":0}}}""",
        )
        val uploader = PrintHostUploader(http, socket, elegooStartDelayMillis = 0)
        val printer = printer("Elegoo Centauri Carbon")
        val options = ElegooOptions(timeLapse = true, heatedBedLeveling = false, bedType = ElegooOptions.BED_TYPE_PC)

        val outcome = run { uploader.upload(printer, gcode(2_500_000), "cube.gcode", startPrint = true, options = PrintOptions(elegoo = options)) }

        assertEquals(PrintHostUploadOutcome.Success("cube.gcode"), outcome)
        assertEquals(listOf("http://192.168.1.20/"), http.gets)
        assertEquals(listOf(0L to 1_048_576L, 1_048_576L to 1_048_576L, 2_097_152L to 402_848L), http.parts.map { it.offset to it.length })
        val first = http.parts.first()
        assertEquals("http://192.168.1.20/uploadFile/upload", first.url)
        assertEquals("1", first.fields["Check"])
        assertEquals("2500000", first.fields["TotalSize"])
        assertEquals(http.parts.map { it.fields["Uuid"] }.distinct().size, 1)
        // bbl_calc_md5() writes upper-case hex.
        assertEquals(first.fields["S-File-MD5"], first.fields["S-File-MD5"]?.uppercase())
        assertEquals("ws://192.168.1.20:3030/websocket", socket.url)
        // The status is asked again while the printer checks the file (8), then the print starts.
        assertEquals(listOf(0, 0, 128), socket.sent.map { Json.parseToJsonElement(it).jsonObject["Data"]!!.jsonObject["Cmd"]!!.jsonPrimitive.int })
        val start = Json.parseToJsonElement(socket.sent.last()).jsonObject["Data"]!!.jsonObject["Data"]!!.jsonObject
        assertEquals("/local/cube.gcode", start["Filename"]!!.jsonPrimitive.content)
        assertEquals(1, start["Tlp_Switch"]!!.jsonPrimitive.int)
        assertEquals(0, start["Calibration_switch"]!!.jsonPrimitive.int)
        assertEquals(1, start["PrintPlatformType"]!!.jsonPrimitive.int)
    }

    @Test
    fun `a Centauri Carbon that refuses the print says why`() {
        val http = ElegooHttp(page = "ELEGOO", piece = """{"code":"000000"}""")
        val socket = ScriptedSocket("""{"Status":{"CurrentStatus":[0]}}""", """{"Data":{"Cmd":128,"Data":{"Ack":1}}}""")

        val outcome = run { PrintHostUploader(http, socket, elegooStartDelayMillis = 0).upload(printer("Elegoo Centauri Carbon"), gcode(10), "cube.gcode", startPrint = true) }

        assertEquals(
            PrintHostUploadOutcome.Failure("The printer is busy, Please check the device page for the file and try to start printing again. Error code: 1"),
            outcome,
        )
        // uploadPart(): a refused piece names its code and fields.
        assertEquals(
            "Error code: 100002\nFile:bad\n",
            ElegooLink.ccPieceError("""{"code":"100002","messages":[{"field":"File","message":"bad"}]}"""),
        )
    }

    @Test
    fun `a Centauri Carbon 2 answers its serial number and takes the file by PUT`() {
        val http = ElegooHttp(info = """{"error_code":0,"system_info":{"sn":"F01ABC"}}""", put = """{"error_code":0}""")
        val uploader = PrintHostUploader(http, ScriptedSocket())
        val printer = printer("Elegoo Centauri Carbon 2", host = "192.168.1.21")

        val tested = run { uploader.test(printer) }
        val outcome = run { uploader.upload(printer, gcode(1_500_000), "cube.gcode", startPrint = true) }

        assertEquals(PrintHostTestOutcome.Success("F01ABC"), tested)
        assertEquals("http://192.168.1.21/system/info?X-Token=123456", http.gets.first())
        assertEquals("123456", http.getHeaders.first()["X-Token"])
        assertEquals(PrintHostUploadOutcome.Success("cube.gcode"), outcome)
        assertEquals(listOf("bytes 0-1048575/1500000", "bytes 1048576-1499999/1500000"), http.puts.map { it.headers["Content-Range"] })
        val md5 = http.puts.first().headers["X-File-MD5"].orEmpty()
        assertEquals(md5.lowercase(), md5)
        assertEquals("http://192.168.1.21/upload", http.puts.first().url)
        // The page asks the serial number the test learnt, without a request.
        assertEquals("F01ABC", run { uploader.serialNumber(printer, lookUp = false) })
        // A Centauri Carbon 2 only stores the file.
        assertTrue(!printer.canStartPrint)
    }

    private fun printer(model: String, host: String = "192.168.1.20") = PhysicalPrinter(
        name = "Test",
        settings = ModelSettings(mapOf("host_type" to "elegoolink", "print_host" to host, "printer_model" to model)),
    )

    private fun gcode(size: Int): File = File.createTempFile("orcinus", ".gcode").also {
        it.writeBytes(ByteArray(size) { index -> (index % 251).toByte() })
        it.deleteOnExit()
    }

    private class ElegooHttp(
        private val page: String = "",
        private val piece: String = "{}",
        private val info: String = "{}",
        private val put: String = "{}",
    ) : HttpClient {
        val gets = mutableListOf<String>()
        val getHeaders = mutableListOf<Map<String, String>>()
        val parts = mutableListOf<Part>()
        val puts = mutableListOf<Put>()

        data class Part(val url: String, val fields: Map<String, String>, val offset: Long, val length: Long)

        data class Put(val url: String, val headers: Map<String, String>, val size: Int)

        override suspend fun get(url: String, headers: Map<String, String>, auth: HttpAuth?): Result<String> {
            gets += url
            getHeaders += headers
            return Result.success(if (url.contains("system/info")) info else page)
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
        ): Result<String> {
            parts += Part(url, fields, offset, length)
            return Result.success(piece)
        }

        override suspend fun sendBytes(url: String, method: String, headers: Map<String, String>, body: ByteArray): Result<String> {
            puts += Put(url, headers, body.size)
            return Result.success(put)
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
        ): Result<String> = Result.success("{}")

        override suspend fun sendFile(
            url: String,
            method: String,
            headers: Map<String, String>,
            file: File,
            onProgress: ((sent: Long, total: Long) -> Unit)?,
            auth: HttpAuth?,
        ): Result<String> = Result.success("{}")

        override suspend fun postJson(url: String, headers: Map<String, String>, body: String, auth: HttpAuth?): Result<String> =
            Result.success("{}")
    }

    /** A WebSocket that answers every receive with the next of its [answers]. */
    private class ScriptedSocket(vararg answers: String) : WebSocketClient {
        private val queue = ArrayDeque(answers.toList())
        val sent = mutableListOf<String>()
        var url: String? = null

        override suspend fun exchange(url: String, messages: List<String>, expect: String?): Result<String?> = Result.success(null)

        override suspend fun <T> converse(url: String, talk: suspend (WebSocketConversation) -> T): Result<T> {
            this.url = url
            return Result.success(
                talk(
                    object : WebSocketConversation {
                        override fun send(text: String) {
                            sent += text
                        }

                        override suspend fun receive(timeoutMillis: Long): String? = queue.removeFirstOrNull()
                    },
                ),
            )
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
