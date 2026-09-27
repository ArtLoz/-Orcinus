package app.orcinus.shadow.network.printhost

import app.orcinus.shadow.core.model.FlashforgeDiscoveredPrinter
import app.orcinus.shadow.core.model.FlashforgeDiscoveryOutcome
import app.orcinus.shadow.core.model.FlashforgeMaterialSlot
import app.orcinus.shadow.core.model.FlashforgeOptions
import app.orcinus.shadow.core.model.FlashforgeSlotsOutcome
import app.orcinus.shadow.core.model.PhysicalPrinter
import app.orcinus.shadow.core.model.PrintHostTestOutcome
import app.orcinus.shadow.core.model.PrintHostUploadOutcome
import java.io.File
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.StandardProtocolFamily
import java.net.StandardSocketOptions
import java.net.URI
import java.nio.ByteBuffer
import java.nio.channels.DatagramChannel
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * OrcaSlicer's Flashforge host (Flashforge.cpp), which reaches a printer in
 * one of two ways. With the printer's serial number and check code it talks to
 * the local API on port 8898: detail tells how the printer is and what its
 * material station holds, uploadGcode takes the file with the send dialog's
 * choices. Otherwise it talks to the serial console on port 8899, the way
 * FlashPrint does: ~M601 opens it, ~M28 announces the file, the file follows
 * in pieces of 4 KB, ~M29 saves it, and ~M23 prints it.
 */
internal class Flashforge(
    private val http: HttpClient,
    private val console: ConsoleClient,
    /** The pause before ~M29, which the printer needs after the last piece. */
    private val saveDelayMillis: Long = SAVE_DELAY_MILLIS,
) {
    /** Flashforge::test(): the local API's detail, or the serial console's ~M601. */
    suspend fun test(printer: PhysicalPrinter): PrintHostTestOutcome {
        if (printer.usesFlashforgeLocalApi) {
            return localApi(printer, "detail").fold(
                onSuccess = { PrintHostTestOutcome.Success("") },
                onFailure = { PrintHostTestOutcome.Failure(it.message.orEmpty()) },
            )
        }
        return console.run(printer.host, CONSOLE_PORT, listOf(CONTROL)).fold(
            onSuccess = { PrintHostTestOutcome.Success("") },
            onFailure = { PrintHostTestOutcome.Failure(it.message.orEmpty()) },
        )
    }

    /** Flashforge::upload(). */
    suspend fun upload(printer: PhysicalPrinter, gcode: File, name: String, startPrint: Boolean, options: FlashforgeOptions?): PrintHostUploadOutcome =
        if (printer.usesFlashforgeLocalApi) {
            uploadLocalApi(printer, gcode, name, startPrint, options ?: FlashforgeOptions())
        } else {
            uploadSerial(printer, gcode, name, startPrint)
        }

    /**
     * Flashforge::fetch_material_slots(): the slots of the printer's material
     * station (matlStationInfo.slotInfos of detail), and whether it reports
     * one: hasMatlStation, a slot count or any slot at all.
     */
    suspend fun materialSlots(printer: PhysicalPrinter): FlashforgeSlotsOutcome {
        if (printer.serialNumber.isEmpty() || printer.apiKey.isEmpty()) {
            return FlashforgeSlotsOutcome.Failure("Flashforge local API requires both serial number and access code.")
        }
        val body = localApi(printer, "detail").getOrElse { return FlashforgeSlotsOutcome.Failure(it.message.orEmpty()) }
        val parsed = parseJson(body) as? JsonObject ?: return FlashforgeSlotsOutcome.Failure(INVALID_JSON)
        val detail = parsed["detail"] as? JsonObject ?: parsed
        val station = (detail["matlStationInfo"] ?: detail["MatlStationInfo"]) as? JsonObject
        val slotInfos = (station?.get("slotInfos") ?: station?.get("SlotInfos"))?.let { runCatching { it.jsonArray }.getOrNull() }.orEmpty()
        var reports = (jsonInt(detail["hasMatlStation"]) ?: jsonInt(detail["HasMatlStation"]))?.let { it != 0 } ?: false
        (jsonInt(station?.get("slotCnt")) ?: jsonInt(station?.get("SlotCnt")))?.let { reports = reports || it > 0 }
        if (slotInfos.isNotEmpty()) reports = true
        val slots = slotInfos.mapIndexed { index, element ->
            val slot = element as? JsonObject ?: JsonObject(emptyMap())
            FlashforgeMaterialSlot(
                slotId = (slot["slotId"] as? JsonPrimitive)?.intOrNull ?: (index + 1),
                hasFilament = (slot["hasFilament"] as? JsonPrimitive)?.booleanOrNull ?: false,
                materialName = (slot["materialName"] as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty(),
                materialColor = (slot["materialColor"] as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty(),
            )
        }
        return FlashforgeSlotsOutcome.Success(slots, reports)
    }

    /** Flashforge::upload() over the serial console. */
    private suspend fun uploadSerial(printer: PhysicalPrinter, gcode: File, name: String, startPrint: Boolean): PrintHostUploadOutcome {
        // connect(): the console is opened and told which firmware it talks to.
        val connect = if (printer.gcodeFlavor == "klipper") CONNECT_KLIPPER else CONNECT_LEGACY
        console.run(printer.host, CONSOLE_PORT, listOf(CONTROL, DEVICE_INFO, connect, STATUS))
        val stored = sanitizeFilename(name, gcode.extension.let { if (it.isEmpty()) ".gcode" else ".$it" })
        // The file goes as raw bytes; ISO 8859-1 carries every byte through a String unchanged.
        val content = gcode.readBytes().toString(Charsets.ISO_8859_1)
        val messages = buildList {
            add(SerialMessage("~M28 ${gcode.length()} 0:/user/$stored"))
            content.chunked(BUFFER_SIZE).forEach { add(SerialMessage(it, SerialMessage.Type.DATA)) }
        }
        console.run(printer.host, CONSOLE_PORT, messages).getOrElse { return PrintHostUploadOutcome.Failure(it.message.orEmpty()) }
        console.run(printer.host, CONSOLE_PORT, listOf(SAVE_FILE), queueDelayMillis = saveDelayMillis)
            .getOrElse { return PrintHostUploadOutcome.Failure(it.message.orEmpty()) }
        if (!startPrint) return PrintHostUploadOutcome.Success(stored)
        // start_print()
        return console.run(printer.host, CONSOLE_PORT, listOf(SerialMessage("~M23 0:/user/$stored"))).fold(
            onSuccess = { PrintHostUploadOutcome.Success(stored) },
            onFailure = { PrintHostUploadOutcome.Failure(it.message.orEmpty()) },
        )
    }

    /** Flashforge::upload_local_api(): uploadGcode, with the dialog's choices as headers. */
    private suspend fun uploadLocalApi(
        printer: PhysicalPrinter,
        gcode: File,
        name: String,
        startPrint: Boolean,
        options: FlashforgeOptions,
    ): PrintHostUploadOutcome {
        val stored = sanitizeFilename(name, gcode.extension.let { if (it.isEmpty()) ".gcode" else ".$it" })
        val headers = linkedMapOf(
            "serialNumber" to printer.serialNumber,
            "checkCode" to printer.apiKey,
            "fileSize" to gcode.length().toString(),
            "printNow" to startPrint.toString(),
            "levelingBeforePrint" to options.levelingBeforePrint.toString(),
            "flowCalibration" to "false",
            "firstLayerInspection" to "false",
            "timeLapseVideo" to options.timeLapseVideo.toString(),
            "useMatlStation" to options.useMaterialStation.toString(),
            "gcodeToolCnt" to (if (options.useMaterialStation) options.mappings.size else 0).toString(),
            "materialMappings" to Base64.getEncoder().encodeToString(materialMappings(options).toByteArray()),
        )
        val body = http.postMultipart(
            url = localApiUrl(printer.host, "uploadGcode"),
            headers = headers,
            fields = emptyMap(),
            fileField = "gcodeFile",
            fileName = stored,
            file = gcode,
        ).getOrElse { return PrintHostUploadOutcome.Failure(it.message.orEmpty()) }
        validateLocalApiResponse(body)?.let { return PrintHostUploadOutcome.Failure(it) }
        return PrintHostUploadOutcome.Success(stored)
    }

    /** request_local_api_json(): the printer's serial number and check code, posted to [path]. */
    private suspend fun localApi(printer: PhysicalPrinter, path: String): Result<String> {
        val request = buildJsonObject {
            put("serialNumber", printer.serialNumber)
            put("checkCode", printer.apiKey)
        }.toString()
        val body = http.postJson(localApiUrl(printer.host, path), emptyMap(), request).getOrElse { return Result.failure(it) }
        return validateLocalApiResponse(body)?.let { Result.failure(IOException(it)) } ?: Result.success(body)
    }

    internal companion object {
        const val CONSOLE_PORT = 8899
        const val LOCAL_API_PORT = 8898
        const val BUFFER_SIZE = 4096
        const val SAVE_DELAY_MILLIS = 3_000L
        const val INVALID_JSON = "Flashforge returned an invalid JSON response."

        val CONTROL = SerialMessage("~M601 S1\r\n")
        val CONNECT_KLIPPER = SerialMessage("~M640\r\n")
        val CONNECT_LEGACY = SerialMessage("~M650\r\n")
        val DEVICE_INFO = SerialMessage("~M115\r\n")
        val STATUS = SerialMessage("~M119\r\n")
        val SAVE_FILE = SerialMessage("~M29\r\n")

        /** make_http_url(): the address without its scheme, path or port, on the local API's port. */
        fun localApiUrl(host: String, path: String): String = "http://${hostName(host)}:$LOCAL_API_PORT/$path"

        /** extract_host_name(): a bare host up to its first slash, or the host of a URL. */
        fun hostName(host: String): String {
            if (!host.contains("://")) return host.substringBefore('/')
            return runCatching { URI(host).host }.getOrNull() ?: host
        }

        /**
         * sanitize_flashforge_filename(): the file's own name, where every byte
         * but ASCII letters, digits, '.', '_' and '-' becomes '_'.
         */
        fun sanitizeFilename(name: String, fallbackExtension: String = ""): String {
            val base = name.substringAfterLast('/').substringAfterLast('\\')
            if (base.isEmpty()) return "print$fallbackExtension"
            return base.toByteArray(Charsets.UTF_8).joinToString("") { byte ->
                val c = (byte.toInt() and 0xff).toChar()
                if (c in '0'..'9' || c in 'A'..'Z' || c in 'a'..'z' || c == '.' || c == '_' || c == '-') c.toString() else "_"
            }
        }

        /**
         * validate_local_api_response(): an object whose code (or err) is 0;
         * otherwise the printer's message. Null when the answer is good.
         */
        fun validateLocalApiResponse(body: String): String? {
            val parsed = parseJson(body) as? JsonObject ?: return INVALID_JSON
            val code = jsonInt(parsed["code"]) ?: jsonInt(parsed["err"]) ?: return null
            if (code == 0) return null
            val message = listOf("message", "msg").firstNotNullOfOrNull { key ->
                (parsed[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
            }?.ifEmpty { null } ?: "Request failed"
            return "Flashforge local API error $code: $message"
        }

        /**
         * extendedInfo()'s materialMappings: the JSON of the mappings, with its
         * keys in nlohmann::json's own order, sorted.
         */
        fun materialMappings(options: FlashforgeOptions): String = buildJsonArray {
            if (options.useMaterialStation) {
                options.mappings.forEach { mapping ->
                    addJsonObject {
                        put("materialName", mapping.materialName)
                        put("slotId", mapping.slotId)
                        put("slotMaterialColor", mapping.slotMaterialColor)
                        put("toolId", mapping.toolId)
                        put("toolMaterialColor", mapping.toolMaterialColor)
                    }
                }
            }
        }.toString()

        /** try_parse_json_int(): a number, a boolean or a string holding an integer. */
        fun jsonInt(value: JsonElement?): Int? {
            val primitive = value as? JsonPrimitive ?: return null
            if (!primitive.isString) {
                primitive.intOrNull?.let { return it }
                primitive.booleanOrNull?.let { return if (it) 1 else 0 }
                return null
            }
            return primitive.content.trim().takeIf { it.isNotEmpty() }?.toLongOrNull()?.toInt()
        }

        private fun parseJson(body: String): JsonElement? = runCatching { Json.parseToJsonElement(body) }.getOrNull()

        /** FLASHFORGE_DISCOVERY_MESSAGE, which the printers answer on port 48899. */
        val DISCOVERY_MESSAGE: ByteArray =
            intArrayOf(0x77, 0x77, 0x77, 0x2e, 0x75, 0x73, 0x72, 0x22, 0x65, 0x36, 0xc0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
                .map { it.toByte() }.toByteArray()

        const val DISCOVERY_PORT = 48899
        const val DISCOVERY_LISTEN_PORT = 18007

        /**
         * parse_discovery_response(): the printer's name in the first 32 bytes
         * and its serial number at 0x92, up to their first zero byte.
         */
        fun parseDiscoveryResponse(response: ByteArray, ipAddress: String): FlashforgeDiscoveredPrinter? {
            if (response.size < RESPONSE_SIZE) return null
            val name = nullTerminated(response, 0, 32)
            val serial = nullTerminated(response, SERIAL_OFFSET, 32)
            if (name.isEmpty() && serial.isEmpty()) return null
            return FlashforgeDiscoveredPrinter(name, serial, ipAddress)
        }

        private fun nullTerminated(data: ByteArray, from: Int, length: Int): String {
            val text = String(data, from, length, Charsets.ISO_8859_1)
            return text.substringBefore('\u0000').trim()
        }

        private const val RESPONSE_SIZE = 0xC4
        private const val SERIAL_OFFSET = 0x92
    }
}

/**
 * Flashforge::discover_printers(): the discovery message is broadcast to the
 * networks of the machine (and the usual home ones) from port 18007, and the
 * printers answer there. A round ends after 10 seconds, or 1.5 seconds after
 * the last answer once one came; up to three rounds run until one finds a
 * printer.
 */
class FlashforgeDiscovery(
    private val timeoutMillis: Long = 10_000,
    private val idleTimeoutMillis: Long = 1_500,
    private val maxRetries: Int = 3,
) {
    suspend fun discover(): FlashforgeDiscoveryOutcome = withContext(Dispatchers.IO) {
        val addresses = broadcastAddresses()
        val byIp = sortedMapOf<String, FlashforgeDiscoveredPrinter>()
        try {
            for (attempt in 0 until maxRetries.coerceAtLeast(1)) {
                DatagramChannel.open(StandardProtocolFamily.INET).use { socket ->
                    socket.setOption(StandardSocketOptions.SO_BROADCAST, true)
                    socket.setOption(StandardSocketOptions.SO_REUSEADDR, true)
                    socket.bind(InetSocketAddress(InetAddress.getByName("0.0.0.0"), Flashforge.DISCOVERY_LISTEN_PORT))
                    for (address in addresses) {
                        runCatching {
                            socket.send(ByteBuffer.wrap(Flashforge.DISCOVERY_MESSAGE), InetSocketAddress(address, Flashforge.DISCOVERY_PORT))
                        }
                    }
                    socket.configureBlocking(false)
                    val start = System.currentTimeMillis()
                    var lastReply = start
                    val buffer = ByteBuffer.allocate(512)
                    while (true) {
                        val now = System.currentTimeMillis()
                        if (now - start >= timeoutMillis) break
                        if (byIp.isNotEmpty() && now - lastReply >= idleTimeoutMillis) break
                        buffer.clear()
                        val from = socket.receive(buffer) as? InetSocketAddress
                        if (from == null) {
                            delay(POLL_MILLIS)
                            continue
                        }
                        buffer.flip()
                        val response = ByteArray(buffer.remaining()).also { buffer.get(it) }
                        val ip = from.address.hostAddress.orEmpty()
                        Flashforge.parseDiscoveryResponse(response, ip)?.let {
                            byIp[ip] = it
                            lastReply = System.currentTimeMillis()
                        }
                        ensureActive()
                    }
                }
                if (byIp.isNotEmpty()) break
            }
        } catch (error: IOException) {
            return@withContext FlashforgeDiscoveryOutcome.Failure(error.message.orEmpty())
        }
        val printers = byIp.values.sortedWith(compareBy({ it.name }, { it.ipAddress }))
        if (printers.isEmpty()) {
            FlashforgeDiscoveryOutcome.Failure("No Flashforge printers were discovered on the local network.")
        } else {
            FlashforgeDiscoveryOutcome.Success(printers)
        }
    }

    /**
     * get_discovery_broadcast_addresses(): 255.255.255.255, 192.168.0.255,
     * 192.168.1.255 and the .255 of every IPv4 address of the machine.
     */
    private fun broadcastAddresses(): List<InetAddress> {
        val addresses = sortedSetOf("255.255.255.255", "192.168.0.255", "192.168.1.255")
        runCatching {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty().flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .map { it.address }
                .filter { (it[0].toInt() and 0xff) != 127 }
                .forEach { bytes -> addresses += "${bytes[0].toInt() and 0xff}.${bytes[1].toInt() and 0xff}.${bytes[2].toInt() and 0xff}.255" }
        }
        return addresses.map { InetAddress.getByName(it) }
    }

    private companion object {
        const val POLL_MILLIS = 50L
    }
}
