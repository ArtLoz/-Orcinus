package app.orcinus.shadow.network.printhost

import app.orcinus.shadow.core.model.ElegooKind
import app.orcinus.shadow.core.model.FlashforgeSlotsOutcome
import app.orcinus.shadow.core.model.HostPrintersOutcome
import app.orcinus.shadow.core.model.PhysicalPrinter
import app.orcinus.shadow.core.model.PrintHostTestOutcome
import app.orcinus.shadow.core.model.PrintHostType
import app.orcinus.shadow.core.model.PrintHostUploadOutcome
import app.orcinus.shadow.core.model.PrintOptions
import app.orcinus.shadow.core.model.PrinterSlot
import app.orcinus.shadow.core.model.PrinterSlotsOutcome
import java.io.File
import java.net.URLEncoder
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * PrintHost::upload of the desktop app: the G-code is sent to the printer's
 * host, and the host is asked to start printing it when the user wants that.
 *
 * OrcaSlicer builds these requests with libcurl in its GUI layer, which the
 * engine does not hold, so the requests are built here from the same fields:
 * OctoPrint posts to api/files/local, Moonraker to server/files/upload and then
 * to printer/print/start, and Creality's firmware (CrealityPrint) takes the file
 * at upload/<name> and starts it over its WebSocket, feeding every filament
 * from the slot of its material boxes the user chose; an MKS board takes the
 * file over http and prints it with G-code on its TCP console.
 */
class PrintHostUploader(
    private val http: HttpClient = UrlConnectionHttpClient(),
    private val webSocket: WebSocketClient = OkHttpWebSocketClient(),
    private val console: ConsoleClient = TcpConsole(),
    /** MKS::start_print(): the board does not take G-code right after an upload. */
    private val mksStartDelayMillis: Long = MKS_START_DELAY_MILLIS,
    /** Flashforge's serial console waits this long before it saves the file. */
    flashforgeSaveDelayMillis: Long = Flashforge.SAVE_DELAY_MILLIS,
    /** A Centauri is given this long before its status is asked and the print started. */
    elegooStartDelayMillis: Long = ElegooLink.START_DELAY_MILLIS,
) {
    private val flashforge = Flashforge(http, console, flashforgeSaveDelayMillis)
    private val elegoo = ElegooLink(http, webSocket, elegooStartDelayMillis)

    /**
     * [printer] is where it goes, [gcode] what is sent, [name] the name the
     * host stores it under, and [startPrint] whether printing starts at once.
     */
    suspend fun upload(
        printer: PhysicalPrinter,
        gcode: File,
        name: String,
        startPrint: Boolean,
        /** What a printer with material boxes is told besides (CrealityPrintHostSendDialog). */
        options: PrintOptions = PrintOptions(),
        /** Http::on_progress: how much of the file has gone out. */
        onProgress: ((sent: Long, total: Long) -> Unit)? = null,
    ): PrintHostUploadOutcome {
        val type = printer.hostType ?: return PrintHostUploadOutcome.Failure("The printer has no host the app can send to")
        if (printer.host.isBlank()) return PrintHostUploadOutcome.Failure("The printer has no address")
        if (!gcode.isFile) return PrintHostUploadOutcome.Failure("The G-code file is gone")
        return when (type) {
            PrintHostType.OCTOPRINT -> uploadToOctoPrint(printer, gcode, name, startPrint, onProgress)
            PrintHostType.MOONRAKER -> uploadToMoonraker(printer, gcode, name, startPrint, onProgress)
            PrintHostType.CREALITY_PRINT -> uploadToCreality(printer, gcode, name, startPrint, options, onProgress)
            PrintHostType.PRUSA_LINK -> uploadToPrusaLink(printer, gcode, name, startPrint, onProgress, connect = false)
            PrintHostType.PRUSA_CONNECT -> uploadToPrusaLink(printer, gcode, name, startPrint, onProgress, connect = true)
            PrintHostType.MKS -> uploadToMks(printer, gcode, name, startPrint, onProgress)
            PrintHostType.DUET -> uploadToDuet(printer, gcode, name, startPrint, onProgress)
            PrintHostType.REPETIER -> uploadToRepetier(printer, gcode, name, startPrint, onProgress)
            // AstroBox took OctoPrint's API, and the same request works for it.
            PrintHostType.ASTROBOX -> uploadToOctoPrint(printer, gcode, name, startPrint, onProgress)
            PrintHostType.ESP3D -> uploadToEsp3d(printer, gcode, name, startPrint, onProgress)
            PrintHostType.FLASHAIR -> uploadToFlashAir(printer, gcode, name, onProgress)
            PrintHostType.FLASHFORGE -> flashforge.upload(printer, gcode, name, startPrint, options.flashforge)
            // ElegooLink: OctoPrint's upload for a printer other than a Centauri.
            PrintHostType.ELEGOO_LINK -> if (printer.elegooKind == ElegooKind.OTHER) {
                uploadToOctoPrint(printer, gcode, name, startPrint, onProgress)
            } else {
                elegoo.upload(printer, gcode, name, startPrint && printer.canStartPrint, options.elegoo, onProgress)
            }
            PrintHostType.OBICO, PrintHostType.SIMPLYPRINT,
            PrintHostType.PRINTER_3D_OS -> PrintHostUploadOutcome.Failure(UNSUPPORTED)
        }
    }

    /**
     * PrintHost::test(): the host is asked what it is, so the dialog can say
     * whether the address and the key are right before anything is sent.
     * OctoPrint answers api/version, Moonraker server/info, and Creality's
     * firmware info; each answer also has to look like that host's answer.
     */
    suspend fun test(printer: PhysicalPrinter): PrintHostTestOutcome {
        val type = printer.hostType ?: return PrintHostTestOutcome.Failure("The printer has no host the app can send to")
        if (printer.host.isBlank()) return PrintHostTestOutcome.Failure("The printer has no address")
        return when (type) {
            PrintHostType.OCTOPRINT -> {
                val body = http.get(makeUrl(printer.host, "api/version"), authHeaders(printer))
                    .getOrElse { return PrintHostTestOutcome.Failure(it.message ?: NO_ANSWER) }
                val version = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull()
                    ?: return PrintHostTestOutcome.Failure(UNREADABLE)
                // OctoPrint::test(): no "api" field means it is not OctoPrint.
                if (version["api"] == null) return PrintHostTestOutcome.Failure(mismatched(type.name))
                val text = version["text"]?.jsonPrimitive?.contentOrNull
                // OctoPrint::validate_version_text()
                if (text != null && !text.startsWith("OctoPrint")) return PrintHostTestOutcome.Failure(mismatched(text))
                PrintHostTestOutcome.Success(text.orEmpty())
            }
            PrintHostType.MOONRAKER -> {
                val body = http.get(makeUrl(printer.host, "server/info"), authHeaders(printer))
                    .getOrElse { return PrintHostTestOutcome.Failure(it.message ?: NO_ANSWER) }
                val state = runCatching {
                    Json.parseToJsonElement(body).jsonObject["result"]?.jsonObject?.get("klippy_state")?.jsonPrimitive?.contentOrNull
                }.getOrNull()
                    // Moonraker::test(): an answer without result.klippy_state
                    // is some other host, not a Moonraker that is busy.
                    ?: return PrintHostTestOutcome.Failure("The host responded but it doesn't look like Moonraker (missing result.klippy_state).")
                PrintHostTestOutcome.Success(state)
            }
            PrintHostType.CREALITY_PRINT -> {
                val body = http.get(makeUrl(printer.host, "info"), bearer(printer))
                    .getOrElse { return PrintHostTestOutcome.Failure(it.message ?: NO_ANSWER) }
                // CrealityPrint::test(): the model it reports, which also says
                // whether it prints from material boxes.
                val model = runCatching { Json.parseToJsonElement(body).jsonObject["model"]?.jsonPrimitive?.contentOrNull }.getOrNull()
                PrintHostTestOutcome.Success(model.orEmpty())
            }
            // PrusaLink::test(): PrusaLink or an OctoPrint firmware; the login
            // goes as a key or as a digest. PrusaConnect answers the same.
            PrintHostType.PRUSA_LINK, PrintHostType.PRUSA_CONNECT ->
                versionTest(printer, listOf("PrusaLink", "OctoPrint"), login(printer))
            // MKS::test(): the board's console answers M105.
            PrintHostType.MKS -> console.run(printer.host, MKS_CONSOLE_PORT, listOf(SerialMessage("M105"))).fold(
                onSuccess = { PrintHostTestOutcome.Success("") },
                onFailure = { PrintHostTestOutcome.Failure(it.message ?: NO_ANSWER) },
            )
            // AstroBox::test(): OctoPrint's api/version again, with its own name.
            PrintHostType.ASTROBOX -> versionTest(printer, listOf("AstroBox"), null)
            PrintHostType.DUET -> {
                // Duet::test(): connecting is the test.
                duetConnect(printer)?.let { PrintHostTestOutcome.Success(it.name) }
                    ?: PrintHostTestOutcome.Failure(NO_ANSWER)
            }
            PrintHostType.REPETIER -> {
                val body = http.get(makeUrl(printer.host, "printer/info"), authHeaders(printer))
                    .getOrElse { return PrintHostTestOutcome.Failure(it.message ?: NO_ANSWER) }
                val info = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull()
                    ?: return PrintHostTestOutcome.Failure(UNREADABLE)
                // validate_repetier(): "software" is the reliable one, since a
                // Repetier server can be rebranded and rename itself.
                val software = info["software"]?.jsonPrimitive?.contentOrNull
                val named = info["name"]?.jsonPrimitive?.contentOrNull
                val ours = if (software != null) software == "Repetier-Server" else named == null || named.startsWith("Repetier")
                if (!ours) {
                    PrintHostTestOutcome.Failure(mismatched(software ?: named.orEmpty()))
                } else {
                    PrintHostTestOutcome.Success(software ?: named.orEmpty())
                }
            }
            PrintHostType.ESP3D -> {
                // ESP3D::test(): any answer to M105 means the board is there.
                http.get(makeUrl(printer.host, "command?plain=" + urlEncoded("M105")), emptyMap())
                    .fold(
                        onSuccess = { PrintHostTestOutcome.Success("") },
                        onFailure = { PrintHostTestOutcome.Failure(it.message ?: NO_ANSWER) },
                    )
            }
            PrintHostType.FLASHAIR -> {
                // FlashAir::test(): op 118 answers 1 while uploads are allowed.
                val body = http.get(makeUrl(printer.host, "command.cgi?op=118"), emptyMap())
                    .getOrElse { return PrintHostTestOutcome.Failure(it.message ?: NO_ANSWER) }
                if (body.startsWith("1")) {
                    PrintHostTestOutcome.Success("")
                } else {
                    PrintHostTestOutcome.Failure("Upload not enabled on FlashAir card.")
                }
            }
            PrintHostType.FLASHFORGE -> flashforge.test(printer)
            // ElegooLink::test(): OctoPrint's test, whose version text any Elegoo firmware passes, or a Centauri's own.
            PrintHostType.ELEGOO_LINK -> if (printer.elegooKind == ElegooKind.OTHER) {
                versionTest(printer, listOf(""), null)
            } else {
                elegoo.test(printer)
            }
            PrintHostType.OBICO, PrintHostType.SIMPLYPRINT,
            PrintHostType.PRINTER_3D_OS -> PrintHostTestOutcome.Failure(UNSUPPORTED)
        }
    }

    /**
     * ElegooLink::get_sn() when [lookUp] is false: the serial number of a
     * Centauri Carbon 2 the app already knows; with [lookUp], as
     * get_print_host_webui() does, it is asked of the printer when unknown.
     * Empty for any other printer.
     */
    suspend fun serialNumber(printer: PhysicalPrinter, lookUp: Boolean): String =
        if (printer.hostType != PrintHostType.ELEGOO_LINK) "" else if (lookUp) elegoo.serialNumber(printer) else elegoo.knownSerialNumber(printer)

    /**
     * Flashforge::fetch_material_slots(): the slots of a Flashforge printer's
     * material station, which the send dialog maps the filaments to.
     */
    suspend fun flashforgeSlots(printer: PhysicalPrinter): FlashforgeSlotsOutcome = flashforge.materialSlots(printer)

    /**
     * Repetier::get_printers(): the printers of the server, by the slug the
     * upload goes to (printhost_port), which the dialog's Refresh button lists.
     */
    suspend fun printers(printer: PhysicalPrinter): HostPrintersOutcome {
        if (printer.hostType != PrintHostType.REPETIER) return HostPrintersOutcome.Success(emptyList())
        val body = http.get(makeUrl(printer.host, "printer/list"), authHeaders(printer))
            .getOrElse { return HostPrintersOutcome.Failure(it.message ?: NO_ANSWER) }
        val answer = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: return HostPrintersOutcome.Failure("Parsing of host response failed.\nMessage body: \"$body\"")
        answer["error"]?.jsonPrimitive?.contentOrNull?.let { return HostPrintersOutcome.Failure(it) }
        val printers = runCatching {
            answer.getValue("data").jsonArray.map { it.jsonObject.getValue("slug").jsonPrimitive.content }
        }.getOrElse { return HostPrintersOutcome.Failure("Enumeration of host printers failed.\nMessage body: \"$body\"") }
        return HostPrintersOutcome.Success(printers)
    }

    /**
     * OctoPrint::test() and the hosts that took its API: api/version has to
     * carry an "api" field and a text that names the host.
     */
    private suspend fun versionTest(printer: PhysicalPrinter, names: List<String>, auth: HttpAuth?): PrintHostTestOutcome {
        val body = http.get(makeUrl(printer.host, "api/version"), authHeaders(printer), auth = auth)
            .getOrElse { return PrintHostTestOutcome.Failure(it.message ?: NO_ANSWER) }
        val version = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: return PrintHostTestOutcome.Failure(UNREADABLE)
        if (version["api"] == null) return PrintHostTestOutcome.Failure(mismatched(names.first()))
        val text = version["text"]?.jsonPrimitive?.contentOrNull
        if (text != null && names.none { text.startsWith(it) }) return PrintHostTestOutcome.Failure(mismatched(text))
        return PrintHostTestOutcome.Success(text.orEmpty())
    }

    /**
     * CrealityPrint::query_boxes_info(): the slots of the printer's material
     * boxes and what is loaded in each. An inactive CFS box is left out; the
     * spool holder always prints.
     */
    suspend fun printerSlots(printer: PhysicalPrinter): PrinterSlotsOutcome {
        if (printer.hostType != PrintHostType.CREALITY_PRINT) return PrinterSlotsOutcome.Success(emptyList())
        val query = buildJsonObject {
            put("method", "get")
            putJsonObject("params") { put("boxsInfo", 1) }
        }.toString()
        val answer = webSocket.exchange(crealitySocket(printer.host), listOf(query), expect = "boxsInfo")
            .getOrElse { return PrinterSlotsOutcome.Failure(it.message ?: "The printer did not answer") }
            ?: return PrinterSlotsOutcome.Failure("The printer did not report its material boxes")
        return try {
            PrinterSlotsOutcome.Success(parseSlots(answer))
        } catch (error: SerializationException) {
            PrinterSlotsOutcome.Failure(error.message ?: "The printer's answer could not be read")
        } catch (error: IllegalArgumentException) {
            PrinterSlotsOutcome.Failure(error.message ?: "The printer's answer could not be read")
        }
    }

    /**
     * CrealityPrint::upload(): the printer is asked what it is first (test()),
     * since a K2 prints from its material boxes and takes the file without a
     * folder; the file is posted to upload/<name>, and the print is started
     * over the WebSocket.
     */
    private suspend fun uploadToCreality(
        printer: PhysicalPrinter,
        gcode: File,
        name: String,
        startPrint: Boolean,
        options: PrintOptions,
        onProgress: ((sent: Long, total: Long) -> Unit)?,
    ): PrintHostUploadOutcome {
        val headers = bearer(printer)
        val info = http.get(makeUrl(printer.host, "info"), headers).getOrElse { return failure(it) }
        val model = runCatching { Json.parseToJsonElement(info).jsonObject["model"]?.jsonPrimitive?.contentOrNull }.getOrNull()
        val multiColor = model in MULTI_COLOR_MODELS
        // safe_filename(): the printer stores no spaces.
        val stored = name.replace(' ', '_')
        val upload = http.postMultipart(
            url = makeUrl(printer.host, "upload/" + urlEncoded(stored)),
            headers = headers,
            fields = if (multiColor) emptyMap() else mapOf("path" to ""),
            fileField = "file",
            fileName = stored,
            file = gcode,
            onProgress = onProgress,
        )
        upload.getOrElse { return failure(it) }
        if (!startPrint) return PrintHostUploadOutcome.Success(stored)
        val started = webSocket.exchange(crealitySocket(printer.host), crealityStart(stored, multiColor, options))
        return started.fold(onSuccess = { PrintHostUploadOutcome.Success(stored) }, onFailure = ::failure)
    }

    /** OctoPrint::upload_inner_with_host(): one request carries the file and whether to print. */
    private suspend fun uploadToOctoPrint(
        printer: PhysicalPrinter,
        gcode: File,
        name: String,
        startPrint: Boolean,
        onProgress: ((sent: Long, total: Long) -> Unit)?,
    ): PrintHostUploadOutcome {
        val response = http.postMultipart(
            url = makeUrl(printer.host, "api/files/local"),
            headers = authHeaders(printer),
            fields = mapOf("print" to startPrint.toString(), "path" to ""),
            fileField = "file",
            fileName = name,
            file = gcode,
            onProgress = onProgress,
        )
        return response.fold(onSuccess = { PrintHostUploadOutcome.Success(name) }, onFailure = ::failure)
    }

    /**
     * Moonraker::upload(): the file goes to the gcodes root, and the host
     * answers with the path it stored it under, which starts the print.
     */
    private suspend fun uploadToMoonraker(
        printer: PhysicalPrinter,
        gcode: File,
        name: String,
        startPrint: Boolean,
        onProgress: ((sent: Long, total: Long) -> Unit)?,
    ): PrintHostUploadOutcome {
        val upload = http.postMultipart(
            url = makeUrl(printer.host, "server/files/upload"),
            headers = authHeaders(printer),
            fields = mapOf("root" to MOONRAKER_ROOT),
            fileField = "file",
            fileName = name,
            file = gcode,
            onProgress = onProgress,
        )
        val body = upload.getOrElse { return failure(it) }
        // The server confirms the storage-relative path in result.item.path;
        // an answer without it keeps the name the file was sent under.
        val stored = body.jsonValue("path") ?: name
        if (!startPrint) return PrintHostUploadOutcome.Success(stored)

        val started = http.postJson(
            url = makeUrl(printer.host, "printer/print/start"),
            headers = authHeaders(printer),
            body = """{"filename":"${stored.jsonEscaped()}"}""",
        )
        return started.fold(onSuccess = { PrintHostUploadOutcome.Success(stored) }, onFailure = ::failure)
    }

    /**
     * PrusaLink::upload_inner_with_host(): PrusaLink 0.7 and newer take the
     * file with PUT into api/v1/files, older ones and OctoPrint firmwares with
     * the POST of api/files; the host says which in capabilities.upload-by-put.
     * PrusaConnect ([connect]) posts to_print instead of print, in the
     * language of the app (PrusaConnect::set_http_post_header_args()).
     */
    private suspend fun uploadToPrusaLink(
        printer: PhysicalPrinter,
        gcode: File,
        name: String,
        startPrint: Boolean,
        onProgress: ((sent: Long, total: Long) -> Unit)?,
        connect: Boolean,
    ): PrintHostUploadOutcome {
        val version = http.get(makeUrl(printer.host, "api/version"), authHeaders(printer), auth = login(printer))
            .getOrElse { return failure(it) }
        val usePut = runCatching {
            Json.parseToJsonElement(version).jsonObject["capabilities"]?.jsonObject?.get("upload-by-put")?.jsonPrimitive?.content == "true"
        }.getOrDefault(false)
        if (!usePut && connect) {
            // post_inner() with PrusaConnect's own fields.
            val response = http.postMultipart(
                url = makeUrl(printer.host, "api/files/local"),
                headers = authHeaders(printer) + ("Accept-Language" to Locale.getDefault().language.take(2)),
                fields = buildMap {
                    if (startPrint) put("to_print", "True")
                    put("path", "")
                },
                fileField = "file",
                fileName = name,
                file = gcode,
                onProgress = onProgress,
                auth = login(printer),
            )
            return response.fold(onSuccess = { PrintHostUploadOutcome.Success(name) }, onFailure = ::failure)
        }
        if (!usePut) {
            return uploadToOctoPrint(printer, gcode, name, startPrint, onProgress)
        }
        // put_inner(): the name is escaped into the url, and the headers say
        // what to do with the file.
        val headers = buildMap {
            putAll(authHeaders(printer))
            put("Content-Type", "text/x.gcode")
            put("Overwrite", "?1")
            // PrusaLink takes any string as true, so the header is set only to print.
            if (startPrint) put("Print-After-Upload", "?1")
        }
        val answer = http.sendFile(
            url = makeUrl(printer.host, "api/v1/files/local/" + urlEncoded(name)),
            method = "PUT",
            headers = headers,
            file = gcode,
            onProgress = onProgress,
            auth = login(printer),
        )
        return answer.fold(onSuccess = { PrintHostUploadOutcome.Success(name) }, onFailure = ::failure)
    }

    /**
     * MKS::upload(): the file is the whole body of a post to upload, and the
     * board answers err 0 when it took it; M23 and M24 on its console, a while
     * later, print it.
     */
    private suspend fun uploadToMks(
        printer: PhysicalPrinter,
        gcode: File,
        name: String,
        startPrint: Boolean,
        onProgress: ((sent: Long, total: Long) -> Unit)?,
    ): PrintHostUploadOutcome {
        // get_upload_url(): the address is taken as it is, behind http://.
        val body = http.sendFile("http://${printer.host}/upload?X-Filename=" + urlEncoded(name), "POST", emptyMap(), gcode, onProgress)
            .getOrElse { return failure(it) }
        // get_err_code_from_body()
        val error = runCatching { Json.parseToJsonElement(body).jsonObject["err"]?.jsonPrimitive?.intOrNull ?: 0 }
            .getOrElse { return PrintHostUploadOutcome.Failure(UNREADABLE) }
        if (error != 0) return PrintHostUploadOutcome.Failure("Unknown error occurred")
        if (!startPrint) return PrintHostUploadOutcome.Success(name)
        delay(mksStartDelayMillis)
        val started = console.run(printer.host, MKS_CONSOLE_PORT, listOf(SerialMessage("M23 $name"), SerialMessage("M24")))
        return started.fold(onSuccess = { PrintHostUploadOutcome.Success(name) }, onFailure = ::failure)
    }

    /**
     * Duet::upload(): the board is connected to first (rr_connect with the
     * password, or a DuetSoftwareFramework host that answers machine/status),
     * the file goes into 0:/gcodes, and M32 starts the print.
     */
    private suspend fun uploadToDuet(
        printer: PhysicalPrinter,
        gcode: File,
        name: String,
        startPrint: Boolean,
        onProgress: ((sent: Long, total: Long) -> Unit)?,
    ): PrintHostUploadOutcome {
        val connection = duetConnect(printer) ?: return PrintHostUploadOutcome.Failure("Could not connect to Duet")
        val dsf = connection == DuetConnection.DSF
        val stored = urlEncoded(name)
        val answer = if (dsf) {
            http.sendFile(makeUrl(printer.host, "machine/file/gcodes/" + stored), "PUT", emptyMap(), gcode, onProgress)
        } else {
            http.sendFile(makeUrl(printer.host, "rr_upload?name=0:/gcodes/" + stored + "&time=" + timestamp()), "POST", emptyMap(), gcode, onProgress)
        }
        val body = answer.getOrElse { return failure(it) }
        // get_err_code_from_body(): the board answers with err 0 when it took the file.
        if (!dsf && (body.jsonValue("err") ?: "0") != "0") {
            return PrintHostUploadOutcome.Failure("Unknown error occurred")
        }
        if (!startPrint) {
            duetDisconnect(printer, dsf)
            return PrintHostUploadOutcome.Success(name)
        }
        val started = if (dsf) {
            http.postJson(makeUrl(printer.host, "machine/code"), emptyMap(), DUET_START.format(name))
        } else {
            http.get(makeUrl(printer.host, "rr_gcode?gcode=" + urlEncoded(DUET_START.format(name))), emptyMap())
        }
        duetDisconnect(printer, dsf)
        return started.fold(onSuccess = { PrintHostUploadOutcome.Success(name) }, onFailure = ::failure)
    }

    /** Duet::connect(): rr_connect, or the DSF host when the board does not answer it. */
    private suspend fun duetConnect(printer: PhysicalPrinter): DuetConnection? {
        // Duet::Duet(): the password is the key field's (printhost_apikey).
        val password = printer.apiKey.ifBlank { DUET_DEFAULT_PASSWORD }
        val answer = http.get(makeUrl(printer.host, "rr_connect?password=" + urlEncoded(password) + "&time=" + timestamp()), emptyMap())
        answer.getOrNull()?.let { body ->
            return if ((body.jsonValue("err") ?: "0") == "0") DuetConnection.RR else null
        }
        return if (http.get(makeUrl(printer.host, "machine/status"), emptyMap()).isSuccess) DuetConnection.DSF else null
    }

    /** Duet::disconnect(): the rr board keeps one session at a time. */
    private suspend fun duetDisconnect(printer: PhysicalPrinter, dsf: Boolean) {
        if (dsf) return
        http.get(makeUrl(printer.host, "rr_disconnect"), emptyMap())
    }

    /**
     * Repetier::upload(): the file is posted to the job of the printer when it
     * is to be printed, and to its models otherwise.
     */
    private suspend fun uploadToRepetier(
        printer: PhysicalPrinter,
        gcode: File,
        name: String,
        startPrint: Boolean,
        onProgress: ((sent: Long, total: Long) -> Unit)?,
    ): PrintHostUploadOutcome {
        val port = printer.port
        val path = if (startPrint) "printer/job/" + port else "printer/model/" + port
        val fields = buildMap {
            if (startPrint) {
                put("name", name)
                // See PrusaSlicer #7807: the server prints it only with this.
                put("autostart", "true")
            }
            put("a", "upload")
        }
        val answer = http.postMultipart(
            url = makeUrl(printer.host, path),
            headers = authHeaders(printer),
            fields = fields,
            fileField = "filename",
            fileName = name,
            file = gcode,
            onProgress = onProgress,
        )
        return answer.fold(onSuccess = { PrintHostUploadOutcome.Success(name) }, onFailure = ::failure)
    }

    /** ESP3D::upload(): the file goes to the board's serial upload, then M23/M24 print it. */
    private suspend fun uploadToEsp3d(
        printer: PhysicalPrinter,
        gcode: File,
        name: String,
        startPrint: Boolean,
        onProgress: ((sent: Long, total: Long) -> Unit)?,
    ): PrintHostUploadOutcome {
        // get_short_name(): the board's file system takes short names.
        val short = shortName(name)
        val answer = http.postMultipart(
            url = makeUrl(printer.host, "upload_serial"),
            headers = mapOf("Connection" to "keep-alive"),
            fields = emptyMap(),
            fileField = "file",
            fileName = short,
            file = gcode,
            onProgress = onProgress,
        )
        answer.getOrElse { return failure(it) }
        if (!startPrint) return PrintHostUploadOutcome.Success(short)
        http.get(makeUrl(printer.host, "command?plain=" + urlEncoded("M23 " + short)), emptyMap())
            .getOrElse { return failure(it) }
        val start = http.get(makeUrl(printer.host, "command?plain=" + urlEncoded("M24")), emptyMap())
        return start.fold(onSuccess = { PrintHostUploadOutcome.Success(short) }, onFailure = ::failure)
    }

    /**
     * FlashAir::upload(): the card is told to take a file and where to put it,
     * and then the file is posted. The card only stores; it never prints.
     */
    private suspend fun uploadToFlashAir(
        printer: PhysicalPrinter,
        gcode: File,
        name: String,
        onProgress: ((sent: Long, total: Long) -> Unit)?,
    ): PrintHostUploadOutcome {
        http.get(makeUrl(printer.host, "upload.cgi?WRITEPROTECT=ON&FTIME=" + timestamp()), emptyMap())
            .getOrElse { return failure(it) }
        http.get(makeUrl(printer.host, "upload.cgi?UPDIR=/"), emptyMap()).getOrElse { return failure(it) }
        val answer = http.postMultipart(
            url = makeUrl(printer.host, "upload.cgi"),
            headers = emptyMap(),
            fields = emptyMap(),
            fileField = "file",
            fileName = name,
            file = gcode,
            onProgress = onProgress,
        )
        return answer.fold(onSuccess = { PrintHostUploadOutcome.Success(name) }, onFailure = ::failure)
    }

    /** Duet::connect(): which of the two hosts answered. */
    private enum class DuetConnection { RR, DSF }

    /** PrusaLink::set_auth(): atUserPassword answers the host's digest challenge. */
    private fun login(printer: PhysicalPrinter): HttpAuth? =
        if (printer.usesUserPassword && printer.user.isNotBlank()) HttpAuth(printer.user, printer.password) else null

    /** CrealityPrint::set_auth(): the key as a bearer token. */
    private fun bearer(printer: PhysicalPrinter): Map<String, String> =
        if (printer.apiKey.isBlank()) emptyMap() else mapOf("Authorization" to "Bearer ${printer.apiKey}")

    /** OctoPrint::set_auth() and Moonraker::set_auth(): the key the host expects. */
    private fun authHeaders(printer: PhysicalPrinter): Map<String, String> =
        if (printer.apiKey.isBlank()) emptyMap() else mapOf("X-Api-Key" to printer.apiKey)

    private fun failure(error: Throwable): PrintHostUploadOutcome =
        PrintHostUploadOutcome.Failure(error.message ?: "The printer did not answer")

    /** Http::format_error() of a host that did not answer at all. */
    private fun mismatched(what: String): String = "Mismatched type of print host: $what"

    internal companion object {
        private const val NO_ANSWER = "The printer did not answer"
        private const val UNREADABLE = "Could not parse server response."
        private const val UNSUPPORTED = "The app cannot reach this kind of host yet"

        /** MKS::MKS(): the port of the board's G-code console. */
        const val MKS_CONSOLE_PORT = 8080

        /** MKS::start_print()'s pause after an upload. */
        const val MKS_START_DELAY_MILLIS = 1_500L

        /** Duet::get_connect_url(): the board's own default. */
        const val DUET_DEFAULT_PASSWORD = "reprap"

        /** Duet::start_print(): M32 prints the file of the gcodes folder. */
        const val DUET_START = "M32 \"0:/gcodes/%s\""

        /** Duet::timestamp_str(): the board wants the time of the request. */
        fun timestamp(): String =
            java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("'time='yyyy-MM-dd'T'HH:mm:ss"))
                .removePrefix("time=")

        /** ESP3D::get_short_name(): the board's file system keeps 8.3 names. */
        fun shortName(name: String): String {
            val dot = name.lastIndexOf('.')
            val stem = (if (dot > 0) name.substring(0, dot) else name).filter { it.isLetterOrDigit() }
            val extension = if (dot > 0) name.substring(dot + 1).take(3) else ""
            val short = stem.take(8).uppercase(Locale.ROOT)
            return if (extension.isEmpty()) short else short + "." + extension.uppercase(Locale.ROOT)
        }

        /** Moonraker's storage root for G-code, as the desktop app uploads into. */
        const val MOONRAKER_ROOT = "gcodes"

        /** supports_multi_color_print(): the printers of the K2 platform, which print from CFS boxes. */
        val MULTI_COLOR_MODELS = setOf("F008", "F012", "F021", "F022")

        /** The port Creality's firmware answers the slicer on. */
        const val CREALITY_SOCKET_PORT = 9999

        /** ws_connect(): the printer's own address, on the WebSocket port. */
        fun crealitySocket(host: String): String {
            val address = host.removePrefix("http://").removePrefix("https://").substringBefore('/').substringBefore(':')
            return "ws://$address:$CREALITY_SOCKET_PORT/"
        }

        /**
         * CrealityPrint::start_print(): a K2 is told which slot feeds every
         * filament (colorMatch) and then to print (multiColorPrint); a print
         * from the spool holder, and a printer without boxes, only opens the
         * file.
         */
        fun crealityStart(stored: String, multiColor: Boolean, options: PrintOptions): List<String> {
            if (!multiColor) {
                return listOf(
                    buildJsonObject {
                        put("method", "set")
                        putJsonObject("params") { put("opGcodeFile", "printprt:/usr/data/printer_data/gcodes/$stored") }
                    }.toString(),
                )
            }
            val path = "/mnt/UDISK/printer_data/gcodes/$stored"
            val selfTest = if (options.selfTest) 1 else 0
            // A filament fed from the spool holder prints alone; the printer
            // then opens the file without a colour match.
            if (options.slots.any { it.isSpoolHolder }) {
                return listOf(
                    buildJsonObject {
                        put("method", "set")
                        putJsonObject("params") {
                            put("opGcodeFile", "printprt:$path")
                            put("enableSelfTest", selfTest)
                        }
                    }.toString(),
                )
            }
            val colorMatch = buildJsonObject {
                put("method", "set")
                putJsonObject("params") {
                    putJsonObject("colorMatch") {
                        put("path", path)
                        putJsonArray("list") {
                            options.slots.forEachIndexed { index, slot ->
                                addJsonObject {
                                    // The G-code's own tool (T1A for the first
                                    // filament), which the firmware matches by.
                                    put("id", "T1" + ('A' + index))
                                    put("type", slot.type)
                                    put("color", slot.color)
                                    put("boxId", slot.boxId)
                                    put("materialId", slot.materialId)
                                }
                            }
                        }
                    }
                }
            }.toString()
            val print = buildJsonObject {
                put("method", "set")
                putJsonObject("params") {
                    putJsonObject("multiColorPrint") {
                        put("gcode", path)
                        put("enableSelfTest", selfTest)
                    }
                }
            }.toString()
            return listOf(colorMatch, print)
        }

        /** The slots of CrealityPrintHostSendDialog, from the printer's boxsInfo answer. */
        fun parseSlots(answer: String): List<PrinterSlot> {
            val boxes = Json.parseToJsonElement(answer).jsonObject["boxsInfo"]?.jsonObject?.get("materialBoxs")?.jsonArray
                ?: return emptyList()
            return boxes.flatMap { element ->
                val box = element.jsonObject
                val boxId = box.int("id") ?: return@flatMap emptyList()
                val type = box.int("type") ?: 0
                // Skip inactive CFS boxes (type 0 with state != 1); the spool
                // holder (type 1) is always there.
                if (type == 0 && (box.int("state") ?: 0) != 1) return@flatMap emptyList()
                box["materials"]?.jsonArray.orEmpty().mapNotNull { material ->
                    val slot = material.jsonObject
                    val slotId = slot.int("id") ?: return@mapNotNull null
                    PrinterSlot(
                        toolId = "T$boxId" + ('A' + slotId),
                        type = slot["type"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                        // Creality writes "#0RRGGBB"; the slicer's colours are "#RRGGBB".
                        color = (slot["color"]?.jsonPrimitive?.contentOrNull ?: "#FFFFFF").let { color ->
                            if (color.length == 8 && color.startsWith("#")) "#" + color.substring(2) else color
                        },
                        boxId = boxId,
                        materialId = slotId,
                    )
                }
            }
        }

        private fun JsonObject.int(key: String): Int? = this[key]?.jsonPrimitive?.intOrNull

        /** Http::url_encode(): the name as one segment of the path. */
        fun urlEncoded(name: String): String = URLEncoder.encode(name, Charsets.UTF_8).replace("+", "%20")

        /** OctoPrint::make_url(): a host without a scheme is reached over http. */
        fun makeUrl(host: String, path: String): String {
            val base = if (host.startsWith("http://") || host.startsWith("https://")) host else "http://$host"
            return if (base.endsWith("/")) base + path else "$base/$path"
        }

        /** The value of a JSON string field anywhere in the answer, without a parser. */
        fun String.jsonValue(field: String): String? {
            val marker = "\"$field\""
            val at = indexOf(marker).takeIf { it >= 0 } ?: return null
            val colon = indexOf(':', at + marker.length).takeIf { it >= 0 } ?: return null
            val open = indexOf('"', colon + 1).takeIf { it >= 0 } ?: return null
            val builder = StringBuilder()
            var index = open + 1
            while (index < length) {
                val character = this[index]
                when {
                    character == '\\' && index + 1 < length -> {
                        builder.append(this[index + 1])
                        index += 2
                    }
                    character == '"' -> return builder.toString()
                    else -> {
                        builder.append(character)
                        index++
                    }
                }
            }
            return null
        }

        fun String.jsonEscaped(): String = replace("\\", "\\\\").replace("\"", "\\\"")
    }
}
