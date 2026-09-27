package app.orcinus.shadow.network.printhost

import app.orcinus.shadow.core.model.ElegooKind
import app.orcinus.shadow.core.model.ElegooOptions
import app.orcinus.shadow.core.model.PhysicalPrinter
import app.orcinus.shadow.core.model.PrintHostTestOutcome
import app.orcinus.shadow.core.model.PrintHostUploadOutcome
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.URI
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * OrcaSlicer's ElegooLink host (ElegooLink.cpp) for the Centauri printers:
 * a Centauri or Centauri Carbon (CC) takes the file in pieces of 1 MB at
 * uploadFile/upload and starts it over its WebSocket on port 3030; a Centauri
 * Carbon 2 (CC2) answers system/info with its serial number and takes the file
 * in pieces with PUT at upload, carrying its access code (default 123456).
 * Any other Elegoo printer speaks OctoPrint's API, which the uploader sends.
 */
internal class ElegooLink(
    private val http: HttpClient,
    private val webSocket: WebSocketClient,
    /** The one-second pauses of loopUpload() and checkResult() around the printer's status. */
    private val startDelayMillis: Long = START_DELAY_MILLIS,
) {
    /** ElegooLink::test() for a Centauri: elegoo_test() or elegoo_cc2_test(). */
    suspend fun test(printer: PhysicalPrinter): PrintHostTestOutcome = when (printer.elegooKind) {
        ElegooKind.CC2 -> cc2Test(printer).fold(
            onSuccess = { PrintHostTestOutcome.Success(it) },
            onFailure = { PrintHostTestOutcome.Failure(it.message.orEmpty()) },
        )
        else -> ccTest(printer).fold(
            onSuccess = { PrintHostTestOutcome.Success("") },
            onFailure = { PrintHostTestOutcome.Failure(it.message.orEmpty()) },
        )
    }

    /** upload_inner_with_host() for a Centauri: the printer is tested, then the file goes in pieces. */
    suspend fun upload(
        printer: PhysicalPrinter,
        gcode: File,
        name: String,
        startPrint: Boolean,
        options: ElegooOptions?,
        onProgress: ((sent: Long, total: Long) -> Unit)?,
    ): PrintHostUploadOutcome {
        if (printer.elegooKind == ElegooKind.CC2) {
            cc2Test(printer).getOrElse { return PrintHostUploadOutcome.Failure(it.message.orEmpty()) }
            return uploadCc2(printer, gcode, name, onProgress)
        }
        ccTest(printer).getOrElse { return PrintHostUploadOutcome.Failure(it.message.orEmpty()) }
        return uploadCc(printer, gcode, name, startPrint, options ?: ElegooOptions(), onProgress)
    }

    /**
     * get_sn(): the serial number of a Centauri Carbon 2 the app already knows,
     * from its last test or page; the Device tab's panel asks for it.
     */
    fun knownSerialNumber(printer: PhysicalPrinter): String =
        if (printer.elegooKind == ElegooKind.CC2) serialNumbers[cacheKey(printer)].orEmpty() else ""

    /**
     * get_print_host_webui(): the serial number of a Centauri Carbon 2, known
     * or asked of the printer (system/info, which the panel subscribes to the
     * printer's topics with).
     */
    suspend fun serialNumber(printer: PhysicalPrinter): String {
        knownSerialNumber(printer).takeIf { it.isNotEmpty() }?.let { return it }
        if (printer.elegooKind != ElegooKind.CC2) return ""
        val token = token(printer)
        val body = lookupHttp.get(
            "http://${hostHeaderValue(printer.host)}/system/info?X-Token=" + PrintHostUploader.urlEncoded(token),
            mapOf("X-Token" to token, "Accept" to "application/json"),
        ).getOrNull() ?: return ""
        val serial = parseCc2Response(body, wantSerialNumber = true).getOrNull().orEmpty()
        if (serial.isNotEmpty()) serialNumbers[cacheKey(printer)] = serial
        return serial
    }

    /** elegoo_test(): the printer's page names ELEGOO, in any case. */
    private suspend fun ccTest(printer: PhysicalPrinter): Result<Unit> {
        val body = http.get(PrintHostUploader.makeUrl(printer.host, ""), authHeaders(printer)).getOrElse { return Result.failure(it) }
        return if (body.contains("ELEGOO", ignoreCase = true)) Result.success(Unit) else Result.failure(IOException("ElegooLink not detected"))
    }

    /**
     * elegoo_cc2_test(): system/info with the access code, which answers
     * error_code 0 and the printer's serial number, remembered for its page.
     */
    private suspend fun cc2Test(printer: PhysicalPrinter): Result<String> {
        val token = token(printer)
        val body = http.get(
            PrintHostUploader.makeUrl(printer.host, "system/info?X-Token=" + PrintHostUploader.urlEncoded(token)),
            mapOf("X-Token" to token, "Accept" to "application/json"),
        ).getOrElse { return Result.failure(it) }
        // format_error(body, ..., status) of an answer it cannot use is the HTTP status and the body.
        val serial = parseCc2Response(body, wantSerialNumber = true).getOrElse { return Result.failure(IOException("HTTP 200: $body")) }
        serialNumbers[cacheKey(printer)] = serial
        return Result.success(serial)
    }

    /**
     * loopUpload(): the file in pieces of 1 MB, each a form with its MD5, its
     * offset, the upload's UUID and the whole size, which the printer answers
     * with code 000000; then, to print, the printer's status is awaited over
     * the WebSocket and the print started.
     */
    private suspend fun uploadCc(
        printer: PhysicalPrinter,
        gcode: File,
        name: String,
        startPrint: Boolean,
        options: ElegooOptions,
        onProgress: ((sent: Long, total: Long) -> Unit)?,
    ): PrintHostUploadOutcome {
        val size = gcode.length()
        val uuid = UUID.randomUUID().toString()
        val md5 = md5Of(gcode).uppercase(Locale.ROOT)
        val url = PrintHostUploader.makeUrl(printer.host, "uploadFile/upload")
        for ((index, part) in pieces(size).withIndex()) {
            val body = http.postMultipartPart(
                url = url,
                headers = authHeaders(printer),
                fields = linkedMapOf(
                    "Check" to "1",
                    "S-File-MD5" to md5,
                    "Offset" to part.first.toString(),
                    "Uuid" to uuid,
                    "TotalSize" to size.toString(),
                ),
                fileField = "File",
                fileName = name,
                file = gcode,
                offset = part.first,
                length = part.last - part.first + 1,
                onProgress = onProgress?.let { report -> { sent: Long, _: Long -> report(index * PIECE + sent, size) } },
            ).getOrElse { return PrintHostUploadOutcome.Failure(it.message.orEmpty()) }
            ccPieceError(body)?.let { return PrintHostUploadOutcome.Failure(it) }
        }
        if (!startPrint) return PrintHostUploadOutcome.Success(name)
        val started = webSocket.converse("ws://${hostName(printer.host)}:$SOCKET_PORT/websocket") { talk ->
            delay(startDelayMillis)
            checkResult(talk) ?: run {
                delay(startDelayMillis)
                print(talk, options, name)
            }
        }
        val error = started.getOrElse { failure ->
            return PrintHostUploadOutcome.Failure(
                if (failure is WebSocketRefused) {
                    "The file has been transferred, but some unknown errors occurred. Please check the device page for the file and try to start printing again."
                } else {
                    "\n" + failure.message.orEmpty()
                },
            )
        }
        return if (error == null) PrintHostUploadOutcome.Success(name) else PrintHostUploadOutcome.Failure(error)
    }

    /**
     * checkResult(): the printer's status is asked until it is no longer
     * checking the file (status 8), for up to 60 seconds. Null when it may
     * print, otherwise why not.
     */
    private suspend fun checkResult(talk: WebSocketConversation): String? {
        val start = System.currentTimeMillis()
        var needWrite = true
        while (System.currentTimeMillis() - start < CHECK_TIMEOUT_MILLIS) {
            if (needWrite) {
                talk.send(command(GET_STATUS) {})
                needWrite = false
            }
            val response = talk.receive(CHECK_TIMEOUT_MILLIS - (System.currentTimeMillis() - start)) ?: continue
            val root = runCatching { Json.parseToJsonElement(response) as? JsonObject }.getOrNull()
                ?: return "Start print failed\nError parsing response"
            val status = root["Status"] as? JsonObject ?: continue
            val current = status["CurrentStatus"] as? JsonArray ?: return null
            if (current.none { (it as? JsonPrimitive)?.intOrNull == CHECKING_FILE }) return null
            // 8 is the printer checking the file: wait and ask again.
            needWrite = true
            delay(startDelayMillis)
        }
        return "Start print timeout"
    }

    /**
     * print(): start the file with the dialog's time-lapse, bed leveling and
     * plate side (1 for the smooth side B, 0 otherwise), and wait up to 30
     * seconds for the printer's Ack.
     */
    private suspend fun print(talk: WebSocketConversation, options: ElegooOptions, name: String): String? {
        talk.send(
            command(START_PRINT) {
                put("Filename", "/local/$name")
                put("StartLayer", 0)
                put("Calibration_switch", if (options.heatedBedLeveling) 1 else 0)
                put("PrintPlatformType", if (options.bedType == ElegooOptions.BED_TYPE_PC) 1 else 0)
                put("Tlp_Switch", if (options.timeLapse) 1 else 0)
            },
        )
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < PRINT_TIMEOUT_MILLIS) {
            val response = talk.receive(PRINT_TIMEOUT_MILLIS - (System.currentTimeMillis() - start)) ?: continue
            val root = runCatching { Json.parseToJsonElement(response) as? JsonObject }.getOrNull()
                ?: return "Start print failed\nError parsing response"
            val data = root["Data"] as? JsonObject ?: continue
            if ((data["Cmd"] as? JsonPrimitive)?.intOrNull != START_PRINT) continue
            val ack = ((data["Data"] as? JsonObject)?.get("Ack") as? JsonPrimitive)?.intOrNull ?: return "Error code not found"
            if (ack == 0) return null
            return startPrintError(ack) + " " + "Error code: %d".replace("%d", ack.toString())
        }
        return "Start print timeout"
    }

    /**
     * loopUploadCC2(): the file in pieces of 1 MB, each the whole body of a
     * PUT with its byte range, the file's name and MD5 and the access code.
     * The printer only stores it; a Centauri Carbon 2 does not start prints.
     */
    private suspend fun uploadCc2(
        printer: PhysicalPrinter,
        gcode: File,
        name: String,
        onProgress: ((sent: Long, total: Long) -> Unit)?,
    ): PrintHostUploadOutcome {
        val size = gcode.length()
        if (size <= 0) return PrintHostUploadOutcome.Failure("The file is empty or could not be read.")
        val md5 = md5Of(gcode).lowercase(Locale.ROOT)
        val token = token(printer)
        val url = PrintHostUploader.makeUrl(printer.host, "upload")
        RandomAccessFile(gcode, "r").use { input ->
            for (part in pieces(size)) {
                val chunk = ByteArray((part.last - part.first + 1).toInt())
                input.seek(part.first)
                input.readFully(chunk)
                val body = http.sendBytes(
                    url = url,
                    method = "PUT",
                    headers = linkedMapOf(
                        "Accept" to "application/json",
                        "Content-Type" to "application/octet-stream",
                        "Content-Range" to "bytes ${part.first}-${part.last}/$size",
                        "X-File-Name" to name,
                        "X-File-MD5" to md5,
                        "X-Token" to token,
                    ),
                    body = chunk,
                ).getOrElse { return PrintHostUploadOutcome.Failure(it.message ?: "CC2 upload failed") }
                parseCc2Response(body, wantSerialNumber = false).getOrElse { return PrintHostUploadOutcome.Failure("HTTP 200: $body") }
                onProgress?.invoke(part.last + 1, size)
            }
        }
        return PrintHostUploadOutcome.Success(name)
    }

    /** A short timeout for the page's lookup, as get_print_host_webui() gives it (3 s to connect, 5 s in all). */
    private val lookupHttp = if (http is UrlConnectionHttpClient) UrlConnectionHttpClient(LOOKUP_CONNECT_MILLIS, LOOKUP_MAX_MILLIS) else http

    private fun token(printer: PhysicalPrinter) = printer.apiKey.ifEmpty { DEFAULT_TOKEN }

    private fun cacheKey(printer: PhysicalPrinter) = hostHeaderValue(printer.host) + ":" + token(printer)

    /** OctoPrint::set_auth(): the key the host expects. */
    private fun authHeaders(printer: PhysicalPrinter): Map<String, String> =
        if (printer.apiKey.isBlank()) emptyMap() else mapOf("X-Api-Key" to printer.apiKey)

    internal companion object {
        const val PIECE = 1_048_576L
        const val SOCKET_PORT = 3030
        const val DEFAULT_TOKEN = "123456"
        const val START_DELAY_MILLIS = 1_000L
        private const val CHECK_TIMEOUT_MILLIS = 60_000L
        private const val PRINT_TIMEOUT_MILLIS = 30_000L
        private const val LOOKUP_CONNECT_MILLIS = 3_000
        private const val LOOKUP_MAX_MILLIS = 5_000
        private const val GET_STATUS = 0
        private const val START_PRINT = 128
        private const val CHECKING_FILE = 8

        /** s_sn_cache: the serial numbers of the Centauri Carbon 2 printers, by address and access code. */
        private val serialNumbers = ConcurrentHashMap<String, String>()

        /** The byte ranges of the pieces of a file of [size] bytes. */
        fun pieces(size: Long): List<LongRange> =
            (0 until (size + PIECE - 1) / PIECE).map { index -> index * PIECE..minOf(size, (index + 1) * PIECE) - 1 }

        /** uploadPart(): a piece the printer refused, with its code and the fields it names; null when taken. */
        fun ccPieceError(body: String): String? {
            val root = runCatching { Json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return "Error parsing response"
            val code = (root["code"] as? JsonPrimitive)?.content ?: return "Error parsing response"
            if (code == "000000") return null
            val lines = (root["messages"] as? JsonArray).orEmpty().mapNotNull { element ->
                val message = element as? JsonObject ?: return@mapNotNull null
                "${(message["field"] as? JsonPrimitive)?.content.orEmpty()}:${(message["message"] as? JsonPrimitive)?.content.orEmpty()}"
            }
            return "Error code: %1%".replace("%1%", code) + "\n" + lines.joinToString("") { "$it\n" }
        }

        /**
         * parse_cc2_response(): error_code 0, and with [wantSerialNumber] the
         * printer's system_info.sn; otherwise the printer's message and code.
         */
        fun parseCc2Response(body: String, wantSerialNumber: Boolean): Result<String> {
            val root = runCatching { Json.parseToJsonElement(body) as? JsonObject }.getOrNull()
                ?: return Result.failure(IOException("Error parsing response"))
            val code = (root["error_code"] as? JsonPrimitive)?.intOrNull ?: -1
            if (code != 0) {
                val message = (root["message"] as? JsonPrimitive)?.content.orEmpty().ifEmpty { "Printer returned an error" }
                return Result.failure(IOException("$message ($code)"))
            }
            if (!wantSerialNumber) return Result.success("")
            val info = root["system_info"] as? JsonObject ?: return Result.failure(IOException("Missing system_info in response"))
            val serial = (info["sn"] as? JsonPrimitive)?.content.orEmpty()
            return if (serial.isEmpty()) Result.failure(IOException("Missing printer serial number in response")) else Result.success(serial)
        }

        /** ElegooLinkStartPrintAck: what the printer's refusal means, as OrcaSlicer's msgid. */
        fun startPrintError(ack: Int): String = when (ack) {
            1 -> "The printer is busy, Please check the device page for the file and try to start printing again."
            2 -> "The file is lost, please check and try again."
            3 -> "The file is corrupted, please check and try again."
            4, 5, 6 -> "Transmission abnormality, please check and try again."
            7 -> "The file does not match the printer, please check and try again."
            else -> "Unknown error"
        }

        /**
         * The SDCP command of print() and checkResult(): the command number
         * with its data, a request ID, the time in milliseconds, and From 1.
         */
        fun command(cmd: Int, data: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit): String = buildJsonObject {
            put("Id", "")
            putJsonObject("Data") {
                put("Cmd", cmd)
                putJsonObject("Data", data)
                put("RequestID", UUID.randomUUID().toString())
                put("MainboardID", "")
                put("TimeStamp", System.currentTimeMillis())
                put("From", 1)
            }
        }.toString()

        /** Http::get_host_header_value(): the host of an address, with its port when it names one. */
        fun hostHeaderValue(address: String): String {
            val url = if (address.contains("//")) address else "http://$address"
            var authority = url.substringAfter("//").substringBefore('/').substringBefore('?').substringBefore('#')
            authority = authority.substringAfterLast('@')
            return authority
        }

        /** Http::get_host_from_url(): the host of an address alone. */
        fun hostName(address: String): String {
            val url = if (address.contains("//")) address else "http://$address"
            return runCatching { URI(url).host }.getOrNull() ?: hostHeaderValue(address).substringBefore(':')
        }

        /** bbl_calc_md5(): the file's MD5 in hex. */
        fun md5Of(file: File): String {
            val digest = MessageDigest.getInstance("MD5")
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
