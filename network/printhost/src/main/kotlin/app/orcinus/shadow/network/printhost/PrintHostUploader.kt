package app.orcinus.shadow.network.printhost

import app.orcinus.shadow.core.model.CloudLoginOutcome
import app.orcinus.shadow.core.model.ElegooKind
import app.orcinus.shadow.core.model.FlashforgeSlotsOutcome
import app.orcinus.shadow.core.model.HostPrintersOutcome
import app.orcinus.shadow.core.model.HostStorageOutcome
import app.orcinus.shadow.core.model.ObicoHost
import app.orcinus.shadow.core.model.OrcaText
import app.orcinus.shadow.core.model.PhysicalPrinter
import app.orcinus.shadow.core.model.PrintHostTestOutcome
import app.orcinus.shadow.core.model.PrintHostType
import app.orcinus.shadow.core.model.PrintHostUploadOutcome
import app.orcinus.shadow.core.model.PrintOptions
import app.orcinus.shadow.core.model.Printer3dOsListsOutcome
import app.orcinus.shadow.core.model.PrinterSlot
import app.orcinus.shadow.core.model.PrinterSlotsOutcome
import java.io.File
import java.net.URLEncoder
import java.time.LocalDateTime
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
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
 * file over http and prints it with G-code on its TCP console. Most hosts are
 * tested before the file goes, as their upload() does, and a failed test is
 * the upload's error.
 */
class PrintHostUploader(
    private val http: HttpClient = UrlConnectionHttpClient(),
    private val webSocket: WebSocketClient = OkHttpWebSocketClient(),
    private val console: ConsoleClient = TcpConsole(),
    /** MKS::start_print(): the board does not take G-code right after an upload. */
    private val mksStartDelayMillis: Long = MKS_START_DELAY_MILLIS,
    /** ESP3D::start_print(): the same pause, since the board locks its serial during the transfer. */
    private val esp3dStartDelayMillis: Long = ESP3D_START_DELAY_MILLIS,
    /** Flashforge's serial console waits this long before it saves the file. */
    flashforgeSaveDelayMillis: Long = Flashforge.SAVE_DELAY_MILLIS,
    /** A Centauri is given this long before its status is asked and the print started. */
    elegooStartDelayMillis: Long = ElegooLink.START_DELAY_MILLIS,
    /** simplyprint_oauth.json: where SimplyPrint's login is kept; none keeps no login. */
    simplyPrintCredentials: File? = null,
    /** 3dprinteros_api_cred.json: where 3DPrinterOS's session is kept. */
    printer3dOsCredentials: File? = null,
    /** TokenAuthDialog's pause before it asks the cloud for the session again. */
    printer3dOsRetryMillis: Long = C3DPrinterOS.RETRY_DELAY_MILLIS,
) {
    private val simplyPrint = SimplyPrint(http, simplyPrintCredentials)
    private val printer3dOs = C3DPrinterOS(http, printer3dOsCredentials, printer3dOsRetryMillis)
    private val flashforge = Flashforge(http, console, flashforgeSaveDelayMillis)
    private val elegoo = ElegooLink(http, webSocket, elegooStartDelayMillis)

    /**
     * The hosts' set_auth(): OctoPrint and the hosts built on it, Moonraker,
     * Repetier, CrealityPrint and Obico trust the printer's HTTPS CA file
     * (printhost_cafile) when it has one.
     */
    private fun httpFor(printer: PhysicalPrinter): HttpClient =
        if (printer.caFile.isNotEmpty() && printer.hostType in CA_FILE_HOSTS) http.withCaFile(printer.caFile) else http

    /**
     * [printer] is where it goes, [gcode] what is sent, [name] the upload path
     * of PrintHostSendDialog ("folder/plate.gcode"; each host takes its folder
     * and its file name as Orca's does), and [startPrint] whether printing
     * starts at once.
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
        // upload_path.filename(): the hosts that store the file by its name alone.
        val fileName = fileNameOf(name)
        return when (type) {
            PrintHostType.OCTOPRINT -> uploadToOctoPrint(printer, OCTOPRINT_VERSION, gcode, name, startPrint, onProgress, options.plateIndex)
            PrintHostType.MOONRAKER -> uploadToMoonraker(printer, gcode, fileName, startPrint, options.storage, onProgress, options.plateIndex)
            PrintHostType.CREALITY_PRINT -> uploadToCreality(printer, gcode, name, startPrint, options, onProgress)
            PrintHostType.PRUSA_LINK -> uploadToPrusaLink(printer, gcode, name, startPrint, options.storage, onProgress, connect = false)
            PrintHostType.PRUSA_CONNECT -> uploadToPrusaLink(printer, gcode, name, startPrint, options.storage, onProgress, connect = true)
            PrintHostType.MKS -> uploadToMks(printer, gcode, name, startPrint, onProgress)
            PrintHostType.DUET -> uploadToDuet(printer, gcode, name, startPrint, onProgress)
            PrintHostType.REPETIER -> uploadToRepetier(printer, gcode, fileName, startPrint, options.group, onProgress)
            // AstroBox::upload(): OctoPrint's request, after its own test, without a plate.
            PrintHostType.ASTROBOX -> uploadToOctoPrint(printer, ASTROBOX_VERSION, gcode, name, startPrint, onProgress)
            PrintHostType.ESP3D -> uploadToEsp3d(printer, gcode, name, startPrint, onProgress)
            PrintHostType.FLASHAIR -> uploadToFlashAir(printer, gcode, name, onProgress)
            PrintHostType.FLASHFORGE -> flashforge.upload(printer, gcode, fileName, startPrint, options.flashforge)
            // ElegooLink: OctoPrint's upload_inner_with_host() for a printer other than a Centauri.
            PrintHostType.ELEGOO_LINK -> if (printer.elegooKind == ElegooKind.OTHER) {
                uploadToOctoPrint(printer, ELEGOO_VERSION, gcode, name, startPrint, onProgress, options.plateIndex)
            } else {
                elegoo.upload(printer, gcode, fileName, startPrint && printer.canStartPrint, options.elegoo, onProgress)
            }
            PrintHostType.OBICO -> uploadToObico(printer, gcode, name, startPrint, onProgress)
            PrintHostType.SIMPLYPRINT -> simplyPrint.upload(gcode, fileName, onProgress)
            PrintHostType.PRINTER_3D_OS -> printer3dOs.upload(printer, gcode, fileName, startPrint, options.printer3dOs, onProgress, options.use3mf)
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
            PrintHostType.OCTOPRINT -> versionTest(printer, OCTOPRINT_VERSION).outcome
            PrintHostType.MOONRAKER -> moonrakerTest(printer)
            PrintHostType.CREALITY_PRINT -> crealityTest(printer)
            // PrusaLink::test(): PrusaLink or an OctoPrint firmware; the login
            // goes as a key or as a digest. PrusaConnect answers the same.
            PrintHostType.PRUSA_LINK, PrintHostType.PRUSA_CONNECT -> versionTest(printer, PRUSALINK_VERSION).outcome
            // MKS::test(): the board's console answers M105.
            PrintHostType.MKS -> console.run(printer.host, MKS_CONSOLE_PORT, listOf(SerialMessage("M105"))).fold(
                onSuccess = { PrintHostTestOutcome.Success("") },
                onFailure = { PrintHostTestOutcome.Failure(it.message.orEmpty()) },
            )
            PrintHostType.ASTROBOX -> versionTest(printer, ASTROBOX_VERSION).outcome
            // Duet::test(): connecting is the test, and the board is let go again.
            PrintHostType.DUET -> {
                val connected = duetConnect(printer)
                val connection = connected.connection ?: return testFailure(connected.error)
                duetDisconnect(printer, connection)
                PrintHostTestOutcome.Success(connection.name)
            }
            PrintHostType.REPETIER -> repetierTest(printer)
            // ESP3D::test(): any answer to M105 means the board is there.
            PrintHostType.ESP3D -> http.get(makeUrl(printer.host, "command?plain=" + urlEncoded("M105")), emptyMap()).fold(
                onSuccess = { PrintHostTestOutcome.Success("") },
                onFailure = { testFailure(it) },
            )
            PrintHostType.FLASHAIR -> flashAirTest(printer)
            PrintHostType.FLASHFORGE -> flashforge.test(printer)
            // ElegooLink::test(): OctoPrint's test, whose version text any Elegoo firmware passes, or a Centauri's own.
            PrintHostType.ELEGOO_LINK -> if (printer.elegooKind == ElegooKind.OTHER) {
                versionTest(printer, ELEGOO_VERSION).outcome
            } else {
                elegoo.test(printer)
            }
            PrintHostType.OBICO -> obicoTest(printer)
            PrintHostType.SIMPLYPRINT -> simplyPrint.test()
            PrintHostType.PRINTER_3D_OS -> printer3dOs.test(printer)
        }
    }

    /**
     * The Test button's login of a cloud host that logs in outside the app:
     * SimplyPrint's OAuthDialog, which [openPage] opens the login page of.
     */
    suspend fun cloudLogin(printer: PhysicalPrinter, openPage: (String) -> Unit): CloudLoginOutcome = when (printer.hostType) {
        PrintHostType.SIMPLYPRINT -> simplyPrint.login(openPage)
        PrintHostType.PRINTER_3D_OS -> printer3dOs.login(printer, openPage)
        else -> CloudLoginOutcome.Failure("")
    }

    /** 3DPrinterOS's session check and the lists of its UploadOptionsDialog. */
    suspend fun printer3dOsLists(printer: PhysicalPrinter): Printer3dOsListsOutcome = printer3dOs.lists(printer)

    /** is_logged_in(): whether the cloud host keeps a login, which the dialog offers to log out of. */
    fun isLoggedIn(printer: PhysicalPrinter): Boolean = when (printer.hostType) {
        PrintHostType.SIMPLYPRINT -> simplyPrint.isLoggedIn()
        PrintHostType.PRINTER_3D_OS -> printer3dOs.isLoggedIn()
        else -> false
    }

    /** log_out() */
    fun logOut(printer: PhysicalPrinter) {
        if (printer.hostType == PrintHostType.SIMPLYPRINT) simplyPrint.logOut()
        if (printer.hostType == PrintHostType.PRINTER_3D_OS) printer3dOs.logOut()
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
        val http = httpFor(printer)
        if (printer.hostType == PrintHostType.OBICO) return obicoPrinters(printer)
        if (printer.hostType != PrintHostType.REPETIER) return HostPrintersOutcome.Success(emptyList())
        val body = http.get(makeUrl(printer.host, "printer/list"), authHeaders(printer))
            .getOrElse { return HostPrintersOutcome.Failure(english(formatError(it))) }
        val answer = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: return HostPrintersOutcome.Failure("Parsing of host response failed.\nMessage body: \"$body\"")
        answer["error"]?.jsonPrimitive?.contentOrNull?.let { return HostPrintersOutcome.Failure(it) }
        val printers = runCatching {
            answer.getValue("data").jsonArray.map { it.jsonObject.getValue("slug").jsonPrimitive.content }
        }.getOrElse { return HostPrintersOutcome.Failure("Enumeration of host printers failed.\nMessage body: \"$body\"") }
        return HostPrintersOutcome.Success(printers)
    }

    /**
     * Repetier::get_groups(): the model groups of the server's printer, "#"
     * for its default group; none for another host or when the server does
     * not answer.
     */
    suspend fun groups(printer: PhysicalPrinter): List<String> {
        if (printer.hostType != PrintHostType.REPETIER) return emptyList()
        val http = httpFor(printer)
        val body = http.postFields(makeUrl(printer.host, "printer/api/" + printer.port), authHeaders(printer), mapOf("a" to "listModelGroups"))
            .getOrElse { return emptyList() }
        return runCatching {
            Json.parseToJsonElement(body).jsonObject.getValue("groupNames").jsonArray.map { it.jsonPrimitive.content }
        }.getOrDefault(emptyList())
    }

    /**
     * PrintHost::get_storage(): where the file can go. PrusaLink lists its
     * storages (api/v1/storage) that are not read only and have free space,
     * and fails the upload when it lists none (or cannot be reached at all);
     * Moonraker lists its roots that can be written (server/files/roots). The
     * other hosts have none, PrusaConnect among them (PrusaConnect::get_storage()).
     */
    suspend fun storage(printer: PhysicalPrinter): HostStorageOutcome = when (printer.hostType) {
        PrintHostType.PRUSA_LINK -> prusaLinkStorage(printer)
        PrintHostType.MOONRAKER -> moonrakerRoots(printer)
        else -> HostStorageOutcome.Success(emptyList(), emptyList())
    }

    private suspend fun prusaLinkStorage(printer: PhysicalPrinter): HostStorageOutcome {
        val http = httpFor(printer)
        val headers = keyHeaders(printer) + ("Accept-Language" to Locale.getDefault().language.take(2))
        val answer = http.get(makeUrl(printer.host, "api/v1/storage"), headers, auth = login(printer))
        var errorMessage = ""
        // A printer that answers with an error may not have the endpoint, which is no error;
        // one that does not answer at all is.
        var res = true
        val storages = mutableListOf<PrusaLinkStorage>()
        answer.fold(
            onSuccess = { body ->
                runCatching {
                    val list = Json.parseToJsonElement(body).jsonObject["storage_list"]?.jsonArray ?: error("no storage_list")
                    list.forEach { item ->
                        val entry = item.jsonObject
                        val path = entry["path"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                        if (entry["available"]?.jsonPrimitive?.booleanOrNull == false) return@forEach
                        // PrusaLink 0.7.0RC2 keeps read_only under "ro".
                        val readOnly = entry["read_only"]?.jsonPrimitive?.booleanOrNull ?: entry["ro"]?.jsonPrimitive?.booleanOrNull ?: false
                        val space = entry["free_space"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 1L
                        storages += PrusaLinkStorage(path, entry["name"]?.jsonPrimitive?.contentOrNull.orEmpty(), readOnly, space)
                    }
                }.onFailure { res = false }
            },
            onFailure = { error ->
                errorMessage = "\n\n" + (error.message ?: NO_ANSWER)
                res = error !is HttpStatusException
            },
        )
        val usable = storages.filter { !it.readOnly && it.freeSpace > 0 }
        if (res && usable.isEmpty()) {
            if (storages.isNotEmpty()) {
                errorMessage = "\n\nStorages found: \n" + storages.joinToString("") { (if (it.readOnly) "${it.path} : read only" else "${it.path} : no free space") + "\n" }
            }
            return HostStorageOutcome.Failure("Upload has failed. There is no suitable storage found at ${printer.host}.$errorMessage")
        }
        return HostStorageOutcome.Success(usable.map { it.path }, usable.map { it.name })
    }

    private data class PrusaLinkStorage(val path: String, val name: String, val readOnly: Boolean, val freeSpace: Long)

    /** Moonraker::get_storage(): the roots whose permissions let a file in. */
    private suspend fun moonrakerRoots(printer: PhysicalPrinter): HostStorageOutcome {
        val http = httpFor(printer)
        val body = http.get(makeUrl(printer.host, "server/files/roots"), authHeaders(printer))
            .getOrElse { return HostStorageOutcome.Success(emptyList(), emptyList()) }
        val roots = runCatching {
            Json.parseToJsonElement(body).jsonObject["result"]?.jsonArray.orEmpty().mapNotNull { item ->
                val entry = item.jsonObject
                val root = entry["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val permissions = entry["permissions"]?.jsonPrimitive?.contentOrNull.orEmpty()
                root.takeIf { it.isNotEmpty() && 'w' in permissions }
            }
        }.getOrDefault(emptyList())
        return HostStorageOutcome.Success(roots, roots)
    }

    /** OctoPrint::validate_version_text() and its overrides: [valid] judges the "text" of api/version, [name] is the host's own. */
    private class VersionRule(val name: String, val valid: (String?) -> Boolean)

    /** What versionTest() found: the test's outcome and, when it passed, the answer. */
    private class VersionAnswer(val outcome: PrintHostTestOutcome, val answer: JsonObject? = null)

    /**
     * OctoPrint::test() and the hosts that took its API: api/version has to
     * carry an "api" field and a text that names the host. An answer without
     * "api" fails without a message, as Orca's does.
     */
    private suspend fun versionTest(printer: PhysicalPrinter, rule: VersionRule): VersionAnswer {
        val body = httpFor(printer).get(makeUrl(printer.host, "api/version"), keyHeaders(printer), auth = login(printer))
            .getOrElse { return VersionAnswer(testFailure(it)) }
        val version = parseObject(body) ?: return VersionAnswer(testFailure(orcaError(UNREADABLE)))
        if (version["api"] == null) return VersionAnswer(PrintHostTestOutcome.Failure(""))
        val text = version.string("text")
        if (!rule.valid(text)) return VersionAnswer(testFailure(orcaError(MISMATCHED, text ?: rule.name)))
        return VersionAnswer(PrintHostTestOutcome.Success(text.orEmpty()), version)
    }

    /**
     * Moonraker::test(): server/info has to carry result.klippy_state; the
     * state itself does not matter.
     */
    private suspend fun moonrakerTest(printer: PhysicalPrinter): PrintHostTestOutcome {
        val body = httpFor(printer).get(makeUrl(printer.host, "server/info"), authHeaders(printer)).getOrElse { return testFailure(it) }
        val answer = try {
            Json.parseToJsonElement(body)
        } catch (error: SerializationException) {
            return testFailure(orcaError("Could not parse Moonraker server response: %s", error.message.orEmpty()))
        }
        val state = ((answer as? JsonObject)?.get("result") as? JsonObject)?.string("klippy_state")
            ?: return testFailure(orcaError("The host responded but it doesn't look like Moonraker (missing result.klippy_state)."))
        return PrintHostTestOutcome.Success(state)
    }

    /**
     * CrealityPrint::test(): info within five seconds, whose model also says
     * whether the printer prints from material boxes.
     */
    private suspend fun crealityTest(printer: PhysicalPrinter): PrintHostTestOutcome {
        val body = httpFor(printer).withTimeouts(UrlConnectionHttpClient.DEFAULT_TIMEOUT_CONNECT_MILLIS, CREALITY_TEST_MAX_MILLIS)
            .get(makeUrl(printer.host, "info"), bearer(printer))
            .getOrElse { return testFailure(it) }
        return PrintHostTestOutcome.Success(parseObject(body)?.string("model").orEmpty())
    }

    /**
     * Repetier::test(): printer/info, whose "software" is the reliable name
     * (validate_repetier()), since a Repetier server can be rebranded.
     */
    private suspend fun repetierTest(printer: PhysicalPrinter): PrintHostTestOutcome {
        val body = httpFor(printer).get(makeUrl(printer.host, "printer/info"), authHeaders(printer)).getOrElse { return testFailure(it) }
        val info = parseObject(body) ?: return testFailure(orcaError(UNREADABLE))
        val software = info.string("software")
        val named = info.string("name")
        val ours = if (software != null) software == "Repetier-Server" else named == null || named.startsWith("Repetier")
        if (!ours) return testFailure(orcaError(MISMATCHED, software ?: named ?: "Repetier"))
        return PrintHostTestOutcome.Success(software ?: named.orEmpty())
    }

    /** FlashAir::test(): op 118 answers 1 while uploads are allowed. */
    private suspend fun flashAirTest(printer: PhysicalPrinter): PrintHostTestOutcome {
        val body = http.get(makeUrl(printer.host, "command.cgi?op=118"), emptyMap()).getOrElse { return testFailure(it) }
        return if (body.startsWith("1")) PrintHostTestOutcome.Success("") else testFailure(orcaError("Upload not enabled on FlashAir card."))
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
     * CrealityPrint::upload(): the printer is tested first, which also says
     * what it is, since a K2 prints from its material boxes and takes the file
     * without a folder; the file is posted to upload/<name>, and the print is
     * started over the WebSocket.
     */
    private suspend fun uploadToCreality(
        printer: PhysicalPrinter,
        gcode: File,
        name: String,
        startPrint: Boolean,
        options: PrintOptions,
        onProgress: ((sent: Long, total: Long) -> Unit)?,
    ): PrintHostUploadOutcome {
        val http = httpFor(printer)
        val tested = crealityTest(printer)
        if (tested is PrintHostTestOutcome.Failure) return tested.asUpload()
        val multiColor = (tested as PrintHostTestOutcome.Success).description in MULTI_COLOR_MODELS
        // safe_filename(): the printer stores no spaces.
        val stored = fileNameOf(name).replace(' ', '_')
        val upload = http.postMultipart(
            url = makeUrl(printer.host, "upload/" + urlEncoded(stored)),
            headers = bearer(printer),
            fields = if (multiColor) emptyMap() else mapOf("path" to parentOf(name)),
            fileField = "file",
            fileName = stored,
            file = gcode,
            onProgress = onProgress,
        )
        upload.getOrElse { return uploadFailure(it) }
        if (!startPrint) return PrintHostUploadOutcome.Success(stored)
        val started = webSocket.exchange(crealitySocket(printer.host), crealityStart(stored, multiColor, options))
        return started.fold(onSuccess = { PrintHostUploadOutcome.Success(stored) }, onFailure = { PrintHostUploadOutcome.Failure(it.message ?: NO_ANSWER) })
    }

    /**
     * OctoPrint::upload_inner_with_host(): the host is tested ([rule]), then
     * one request carries the file and whether to print.
     */
    private suspend fun uploadToOctoPrint(
        printer: PhysicalPrinter,
        rule: VersionRule,
        gcode: File,
        name: String,
        startPrint: Boolean,
        onProgress: ((sent: Long, total: Long) -> Unit)?,
        /** The 1-based plate of a .gcode.3mf, 0 for G-code. */
        plateIndex: Int = 0,
    ): PrintHostUploadOutcome {
        val tested = versionTest(printer, rule).outcome
        if (tested is PrintHostTestOutcome.Failure) return tested.asUpload()
        val response = httpFor(printer).postMultipart(
            url = makeUrl(printer.host, "api/files/local"),
            headers = authHeaders(printer),
            // The folder of the upload path, and the file's name; a .gcode.3mf names its plate.
            fields = buildMap {
                put("print", startPrint.toString())
                put("path", parentOf(name))
                if (plateIndex > 0) put("plateindex", plateIndex.toString())
            },
            fileField = "file",
            fileName = fileNameOf(name),
            file = gcode,
            onProgress = onProgress,
        )
        return response.fold(onSuccess = { PrintHostUploadOutcome.Success(name) }, onFailure = ::uploadFailure)
    }

    /**
     * Moonraker::upload(): the host is tested, the file goes to the gcodes
     * root, and the host answers with the path it stored it under, which
     * starts the print.
     */
    private suspend fun uploadToMoonraker(
        printer: PhysicalPrinter,
        gcode: File,
        name: String,
        startPrint: Boolean,
        /** The root the dialog chose; empty for "gcodes". */
        storage: String,
        onProgress: ((sent: Long, total: Long) -> Unit)?,
        /** The 1-based plate of a .gcode.3mf, 0 for G-code. */
        plateIndex: Int = 0,
    ): PrintHostUploadOutcome {
        val tested = moonrakerTest(printer)
        if (tested is PrintHostTestOutcome.Failure) return tested.asUpload()
        val http = httpFor(printer)
        val upload = http.postMultipart(
            url = makeUrl(printer.host, "server/files/upload"),
            headers = authHeaders(printer),
            fields = buildMap {
                put("root", storage.ifEmpty { MOONRAKER_ROOT })
                if (plateIndex > 0) put("plateindex", plateIndex.toString())
            },
            fileField = "file",
            fileName = name,
            file = gcode,
            onProgress = onProgress,
        )
        val body = upload.getOrElse { return uploadFailure(it) }
        // The server confirms the storage-relative path in result.item.path;
        // an answer without it keeps the name the file was sent under.
        val stored = ((parseObject(body)?.get("result") as? JsonObject)?.get("item") as? JsonObject)?.string("path") ?: name
        if (!startPrint) return PrintHostUploadOutcome.Success(stored)

        val started = http.postJson(
            url = makeUrl(printer.host, "printer/print/start"),
            headers = authHeaders(printer),
            body = buildJsonObject { put("filename", stored) }.toString(),
        )
        return started.fold(onSuccess = { PrintHostUploadOutcome.Success(stored) }, onFailure = ::uploadFailure)
    }

    /**
     * PrusaLink::upload_inner_with_host(): the host is tested first
     * (test_with_method_check()); PrusaLink 0.7 and newer take the file with
     * PUT into api/v1/files, older ones and OctoPrint firmwares with the POST
     * of api/files; the host says which in capabilities.upload-by-put.
     * PrusaConnect ([connect]) posts to_print instead of print, in the
     * language of the app (PrusaConnect::set_http_post_header_args()).
     */
    private suspend fun uploadToPrusaLink(
        printer: PhysicalPrinter,
        gcode: File,
        name: String,
        startPrint: Boolean,
        /** The storage the dialog chose; empty for "/local". */
        storage: String,
        onProgress: ((sent: Long, total: Long) -> Unit)?,
        connect: Boolean,
    ): PrintHostUploadOutcome {
        val http = httpFor(printer)
        val tested = versionTest(printer, PRUSALINK_VERSION)
        val outcome = tested.outcome
        if (outcome is PrintHostTestOutcome.Failure) return outcome.asUpload()
        val usePut = ((tested.answer?.get("capabilities") as? JsonObject)?.get("upload-by-put") as? JsonPrimitive)?.booleanOrNull ?: false
        // upload_inner_with_host(): the storage the file goes into.
        val files = (if (usePut) "api/v1/files" else "api/files") + storage.ifEmpty { "/local" }
        if (!usePut) {
            // post_inner() with set_http_post_header_args() of PrusaLink or of PrusaConnect.
            val response = http.postMultipart(
                url = makeUrl(printer.host, files),
                headers = keyHeaders(printer) + if (connect) mapOf("Accept-Language" to Locale.getDefault().language.take(2)) else emptyMap(),
                fields = buildMap {
                    if (connect) {
                        if (startPrint) put("to_print", "True")
                    } else {
                        put("print", startPrint.toString())
                    }
                    put("path", parentOf(name))
                },
                fileField = "file",
                fileName = fileNameOf(name),
                file = gcode,
                onProgress = onProgress,
                auth = login(printer),
            )
            return response.fold(onSuccess = { PrintHostUploadOutcome.Success(name) }, onFailure = ::uploadFailure)
        }
        // put_inner(): the name is escaped into the url, and the headers say
        // what to do with the file.
        val headers = buildMap {
            putAll(keyHeaders(printer))
            put("Content-Type", "text/x.gcode")
            put("Overwrite", "?1")
            // PrusaLink takes any string as true, so the header is set only to print.
            if (startPrint) put("Print-After-Upload", "?1")
        }
        val answer = http.sendFile(
            // put_inner(): every element of the path escaped on its own.
            url = makeUrl(printer.host, files + "/" + escapePathByElement(name)),
            method = "PUT",
            headers = headers,
            file = gcode,
            onProgress = onProgress,
            auth = login(printer),
        )
        return answer.fold(onSuccess = { PrintHostUploadOutcome.Success(name) }, onFailure = ::uploadFailure)
    }

    /**
     * Obico::test(): the server's api/v1/version/ with the token as a bearer;
     * without a token there is nothing to test, and the dialog logs in.
     */
    private suspend fun obicoTest(printer: PhysicalPrinter): PrintHostTestOutcome {
        val http = httpFor(printer)
        if (printer.apiKey.isEmpty()) return PrintHostTestOutcome.Failure("")
        return http.get(ObicoHost.url(printer.host, "api/v1/version/"), obicoAuth(printer)).fold(
            onSuccess = { PrintHostTestOutcome.Success("") },
            onFailure = { testFailure(it) },
        )
    }

    /**
     * Obico::get_printers(): the account's printers as "Name [id]". A request
     * that fails leaves the list empty, as the desktop dialog does; an answer
     * it cannot read is reported.
     */
    private suspend fun obicoPrinters(printer: PhysicalPrinter): HostPrintersOutcome {
        val http = httpFor(printer)
        val body = http.get(ObicoHost.url(printer.host, "api/v1/printers/"), obicoAuth(printer))
            .getOrElse { return HostPrintersOutcome.Success(emptyList()) }
        val answer = runCatching { Json.parseToJsonElement(body) }.getOrNull()
            ?: return HostPrintersOutcome.Failure("Parsing of host response failed.\nMessage body: \"$body\"")
        (answer as? JsonObject)?.get("error")?.jsonPrimitive?.contentOrNull?.let { return HostPrintersOutcome.Failure(it) }
        val printers = runCatching {
            answer.jsonArray.map { item ->
                val entry = item.jsonObject
                entry.getValue("name").jsonPrimitive.content + " [" + entry.getValue("id").jsonPrimitive.content + "]"
            }
        }.getOrElse { return HostPrintersOutcome.Failure("Enumeration of host printers failed.\nMessage body: \"$body\"") }
        return HostPrintersOutcome.Success(printers)
    }

    /**
     * Obico::upload(): the token is tested first, then the file is posted to
     * g_code_files with the printer chosen (printhost_port, as the dialog
     * keeps it) and whether to print.
     */
    private suspend fun uploadToObico(
        printer: PhysicalPrinter,
        gcode: File,
        name: String,
        startPrint: Boolean,
        onProgress: ((sent: Long, total: Long) -> Unit)?,
    ): PrintHostUploadOutcome {
        val http = httpFor(printer)
        val tested = obicoTest(printer)
        if (tested is PrintHostTestOutcome.Failure) return tested.asUpload()
        val answer = http.postMultipart(
            url = ObicoHost.url(printer.host, "api/v1/g_code_files/"),
            headers = obicoAuth(printer),
            fields = linkedMapOf("print" to startPrint.toString(), "path" to parentOf(name), "printer_id" to printer.port, "filename" to fileNameOf(name)),
            fileField = "file",
            fileName = fileNameOf(name),
            file = gcode,
            onProgress = onProgress,
        )
        return answer.fold(onSuccess = { PrintHostUploadOutcome.Success(name) }, onFailure = ::uploadFailure)
    }

    /** Obico::set_auth(): the token as a bearer. */
    private fun obicoAuth(printer: PhysicalPrinter): Map<String, String> = mapOf("Authorization" to "Bearer ${printer.apiKey}")

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
            .getOrElse { return uploadFailure(it) }
        // get_err_code_from_body()
        val error = errorCode(body).getOrElse { return uploadFailure(orcaError(UNREADABLE)) }
        if (error != 0) return uploadFailure(orcaError(UNKNOWN_ERROR))
        if (!startPrint) return PrintHostUploadOutcome.Success(name)
        delay(mksStartDelayMillis)
        val started = console.run(printer.host, MKS_CONSOLE_PORT, listOf(SerialMessage("M23 $name"), SerialMessage("M24")))
        // console.error_message(), as it is.
        return started.fold(onSuccess = { PrintHostUploadOutcome.Success(name) }, onFailure = { PrintHostUploadOutcome.Failure(it.message.orEmpty()) })
    }

    /**
     * Duet::upload(): the board is connected to first (rr_connect with the
     * password, or a DuetSoftwareFramework host that answers machine/status),
     * the file goes into 0:/gcodes, M32 starts the print, and the board is
     * let go again.
     */
    private suspend fun uploadToDuet(
        printer: PhysicalPrinter,
        gcode: File,
        name: String,
        startPrint: Boolean,
        onProgress: ((sent: Long, total: Long) -> Unit)?,
    ): PrintHostUploadOutcome {
        val connected = duetConnect(printer)
        val connection = connected.connection ?: return uploadFailure(connected.error)
        try {
            val dsf = connection == DuetConnection.DSF
            // get_upload_url(): the whole upload path, escaped.
            val answer = if (dsf) {
                http.sendFileAnswer(makeUrl(printer.host, "machine/file/gcodes/" + urlEncoded(name)), "PUT", emptyMap(), gcode, onProgress)
            } else {
                http.sendFileAnswer(makeUrl(printer.host, "rr_upload?name=0:/gcodes/" + urlEncoded(name) + "&time=" + timestamp()), "POST", emptyMap(), gcode, onProgress)
            }.getOrElse { return uploadFailure(it) }
            // A DSF host answers 201 Created; the rr board err 0 (get_err_code_from_body()).
            val error = if (dsf) (if (answer.status == HTTP_CREATED) 0 else 1) else errorCode(answer.body).getOrElse { return PrintHostUploadOutcome.Failure(it.message.orEmpty()) }
            if (error != 0) return uploadFailure(orcaError(UNKNOWN_ERROR))
            if (!startPrint) return PrintHostUploadOutcome.Success(name)
            // start_print(): M32 with the file of the gcodes folder.
            val started = if (dsf) {
                http.sendBytes(makeUrl(printer.host, "machine/code"), "POST", emptyMap(), "M32 \"0:/gcodes/$name\"".toByteArray())
            } else {
                http.get(makeUrl(printer.host, "rr_gcode?gcode=M32%20\"0:/gcodes/" + urlEncoded(name) + "\""), emptyMap())
            }
            return started.fold(onSuccess = { PrintHostUploadOutcome.Success(name) }, onFailure = ::uploadFailure)
        } finally {
            duetDisconnect(printer, connection)
        }
    }

    /** What Duet::connect() made of the board: the connection, or why there is none. */
    private class DuetConnected(val connection: DuetConnection?, val error: List<OrcaText> = emptyList())

    /**
     * Duet::connect(): rr_connect with the password, whose err says what went
     * wrong, or the DSF host when the board does not answer it.
     */
    private suspend fun duetConnect(printer: PhysicalPrinter): DuetConnected {
        // Duet::Duet(): the password is the key field's (printhost_apikey).
        val password = printer.apiKey.ifEmpty { DUET_DEFAULT_PASSWORD }
        val answer = http.get(makeUrl(printer.host, "rr_connect?password=" + urlEncoded(password) + "&time=" + timestamp()), emptyMap())
        val body = answer.getOrElse {
            return http.get(makeUrl(printer.host, "machine/status"), emptyMap()).fold(
                onSuccess = { DuetConnected(DuetConnection.DSF) },
                onFailure = { DuetConnected(null, formatError(it)) },
            )
        }
        val code = errorCode(body).getOrElse { return DuetConnected(null, listOf(OrcaText(it.message.orEmpty()))) }
        return when (code) {
            0 -> DuetConnected(DuetConnection.RR)
            1 -> DuetConnected(null, orcaError("Wrong password"))
            2 -> DuetConnected(null, orcaError("Could not get resources to create a new connection"))
            else -> DuetConnected(null, orcaError(UNKNOWN_ERROR))
        }
    }

    /** Duet::disconnect(): the rr board keeps one session at a time; a DSF host has none. */
    private suspend fun duetDisconnect(printer: PhysicalPrinter, connection: DuetConnection) {
        if (connection != DuetConnection.RR) return
        http.get(makeUrl(printer.host, "rr_disconnect"), emptyMap())
    }

    /**
     * Repetier::upload(): the server is tested, then the file is posted to the
     * job of the printer when it is to be printed, and to its models otherwise.
     */
    private suspend fun uploadToRepetier(
        printer: PhysicalPrinter,
        gcode: File,
        name: String,
        startPrint: Boolean,
        /** The dialog's group; "#" (its "Default") and none send no group. */
        group: String,
        onProgress: ((sent: Long, total: Long) -> Unit)?,
    ): PrintHostUploadOutcome {
        val tested = repetierTest(printer)
        if (tested is PrintHostTestOutcome.Failure) return tested.asUpload()
        val port = printer.port
        val path = if (startPrint) "printer/job/" + port else "printer/model/" + port
        val fields = buildMap {
            if (group.isNotEmpty() && group != REPETIER_DEFAULT_GROUP) put("group", group)
            if (startPrint) {
                put("name", name)
                // See PrusaSlicer #7807: the server prints it only with this.
                put("autostart", "true")
            }
            put("a", "upload")
        }
        val answer = httpFor(printer).postMultipart(
            url = makeUrl(printer.host, path),
            headers = authHeaders(printer),
            fields = fields,
            fileField = "filename",
            fileName = name,
            file = gcode,
            onProgress = onProgress,
        )
        return answer.fold(onSuccess = { PrintHostUploadOutcome.Success(name) }, onFailure = ::uploadFailure)
    }

    /**
     * ESP3D::upload(): the file goes to the board's serial upload under its
     * 8.3 name, then, after a pause, M23 and M24 print it. The board is not
     * tested first. The progress stays a byte short of the whole file until
     * the board answers, so the upload does not look done before M24.
     */
    private suspend fun uploadToEsp3d(
        printer: PhysicalPrinter,
        gcode: File,
        name: String,
        startPrint: Boolean,
        onProgress: ((sent: Long, total: Long) -> Unit)?,
    ): PrintHostUploadOutcome {
        val short = shortName(name)
        val answer = http.postMultipart(
            url = makeUrl(printer.host, "upload_serial"),
            headers = mapOf("Connection" to "keep-alive"),
            fields = emptyMap(),
            fileField = "file",
            fileName = short,
            file = gcode,
            onProgress = onProgress?.let { report -> { sent: Long, total: Long -> report((sent - 1).coerceAtLeast(0), total) } },
        )
        answer.getOrElse { return uploadFailure(it) }
        if (!startPrint) return PrintHostUploadOutcome.Success(short)
        delay(esp3dStartDelayMillis)
        // start_print(): the error of M23 or M24 as curl gives it, empty for an HTTP status.
        http.get(makeUrl(printer.host, "command?plain=" + urlEncoded("M23 $short")), emptyMap())
            .getOrElse { return PrintHostUploadOutcome.Failure(curlError(it)) }
        val start = http.get(makeUrl(printer.host, "command?plain=" + urlEncoded("M24")), emptyMap())
        return start.fold(onSuccess = { PrintHostUploadOutcome.Success(short) }, onFailure = { PrintHostUploadOutcome.Failure(curlError(it)) })
    }

    /**
     * FlashAir::upload(): the card is tested, told the file's time and to
     * protect itself from writes of its own, and where the file goes; then
     * the file is posted. Every answer has to say SUCCESS. The card only
     * stores; it never prints.
     */
    private suspend fun uploadToFlashAir(
        printer: PhysicalPrinter,
        gcode: File,
        name: String,
        onProgress: ((sent: Long, total: Long) -> Unit)?,
    ): PrintHostUploadOutcome {
        val tested = flashAirTest(printer)
        if (tested is PrintHostTestOutcome.Failure) return tested.asUpload()
        // The folder of the upload path with a leading slash, which uploads to the root need.
        val folder = parentOf(name).let { if (it.startsWith("/")) it else "/$it" }
        for (step in listOf("upload.cgi?WRITEPROTECT=ON&FTIME=" + fatTime(), "upload.cgi?UPDIR=$folder")) {
            val body = http.get(makeUrl(printer.host, step), emptyMap()).getOrElse { return uploadFailure(it) }
            if (!body.contains(FLASHAIR_SUCCESS, ignoreCase = true)) return uploadFailure(orcaError(UNKNOWN_ERROR))
        }
        val answer = http.postMultipart(
            url = makeUrl(printer.host, "upload.cgi"),
            headers = emptyMap(),
            fields = emptyMap(),
            fileField = "file",
            fileName = fileNameOf(name),
            file = gcode,
            onProgress = onProgress,
        )
        val body = answer.getOrElse { return uploadFailure(it) }
        return if (body.contains(FLASHAIR_SUCCESS, ignoreCase = true)) PrintHostUploadOutcome.Success(name) else uploadFailure(orcaError(UNKNOWN_ERROR))
    }

    /** Duet::connect(): which of the two hosts answered. */
    private enum class DuetConnection { RR, DSF }

    /** PrusaLink::set_auth(): atUserPassword answers the host's digest challenge instead of giving the key. */
    private fun login(printer: PhysicalPrinter): HttpAuth? =
        if (printer.hostType in PRUSA_HOSTS && printer.usesUserPassword) HttpAuth(printer.user, printer.password) else null

    /** set_auth() of the hosts that give the key: PrusaLink only with atKeyPassword. */
    private fun keyHeaders(printer: PhysicalPrinter): Map<String, String> =
        if (printer.hostType in PRUSA_HOSTS && printer.usesUserPassword) emptyMap() else authHeaders(printer)

    /** CrealityPrint::set_auth(): the key as a bearer token. */
    private fun bearer(printer: PhysicalPrinter): Map<String, String> =
        if (printer.apiKey.isBlank()) emptyMap() else mapOf("Authorization" to "Bearer ${printer.apiKey}")

    /** OctoPrint::set_auth() and Moonraker::set_auth(): the key the host expects. */
    private fun authHeaders(printer: PhysicalPrinter): Map<String, String> =
        if (printer.apiKey.isBlank()) emptyMap() else mapOf("X-Api-Key" to printer.apiKey)

    internal companion object {
        private const val UNREADABLE = "Could not parse server response."
        private const val MISMATCHED = "Mismatched type of print host: %s"
        private const val UNKNOWN_ERROR = "Unknown error occurred"

        private val OCTOPRINT_VERSION = VersionRule("OctoPrint") { it == null || it.startsWith("OctoPrint") }
        private val ASTROBOX_VERSION = VersionRule("AstroBox") { it == null || it.startsWith("AstroBox") }

        /** PrusaLink::validate_version_text(): a version text is required. */
        private val PRUSALINK_VERSION = VersionRule("OctoPrint") { it != null && (it.startsWith("PrusaLink") || it.startsWith("OctoPrint")) }

        /** ElegooLink::validate_version_text(): any text. */
        private val ELEGOO_VERSION = VersionRule("ElegooLink") { true }

        private val PRUSA_HOSTS = setOf(PrintHostType.PRUSA_LINK, PrintHostType.PRUSA_CONNECT)

        /** CrealityPrint::test()'s timeout_max(5). */
        const val CREALITY_TEST_MAX_MILLIS = 5_000

        /** DuetSoftwareFramework answers a stored file with 201 Created. */
        private const val HTTP_CREATED = 201

        /** What every answer of a FlashAir card says when it did what it was asked. */
        private const val FLASHAIR_SUCCESS = "SUCCESS"

        /** MKS::MKS(): the port of the board's G-code console. */
        const val MKS_CONSOLE_PORT = 8080

        /** MKS::start_print()'s pause after an upload. */
        const val MKS_START_DELAY_MILLIS = 1_500L

        /** ESP3D::start_print()'s pause before M23. */
        const val ESP3D_START_DELAY_MILLIS = 1_500L

        /** Duet::get_connect_url(): the board's own default. */
        const val DUET_DEFAULT_PASSWORD = "reprap"

        /** Duet::timestamp_str(): the board wants the local time of the request. */
        fun timestamp(): String = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss").format(LocalDateTime.now())

        /**
         * FlashAir::timestamp_str(): the local time as a FAT date and time,
         * "%#x" — the years since 1980, month, day, hours, minutes and seconds
         * halved.
         */
        fun fatTime(now: LocalDateTime = LocalDateTime.now()): String {
            val time = ((now.year - 1980).toLong() shl 25) or
                (now.monthValue.toLong() shl 21) or
                (now.dayOfMonth.toLong() shl 16) or
                (now.hour.toLong() shl 11) or
                (now.minute.toLong() shl 5) or
                (now.second.toLong() shr 1)
            return if (time == 0L) "0" else "0x" + java.lang.Long.toHexString(time)
        }

        /**
         * ESP3D::get_short_name(): the board's file system keeps 8.3 names, so
         * the stem of the upload path is cut to 8 characters and its last
         * extension to 3.
         */
        fun shortName(path: String): String {
            val name = fileNameOf(path)
            val dot = name.lastIndexOf('.')
            val plain = dot < 0 || name == "." || name == ".."
            val stem = (if (plain) name else name.substring(0, dot)).take(8)
            val extension = (if (plain) "" else name.substring(dot + 1)).take(3)
            return if (extension.isEmpty()) stem else "$stem.$extension"
        }

        /** get_err_code_from_body(): the err of the board's JSON answer, 0 without one. */
        fun errorCode(body: String): Result<Int> = runCatching {
            val answer = Json.parseToJsonElement(body)
            ((answer as? JsonObject)?.get("err") as? JsonPrimitive)?.let { it.intOrNull ?: it.content.toInt() } ?: 0
        }

        /** The error string curl gives on_error(): empty for an answer with an HTTP status. */
        private fun curlError(error: Throwable): String = if (error is HttpStatusException) "" else error.message.orEmpty()

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

        /** ptree::get_optional<std::string>(): a value that is not an object or an array, as text. */
        private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeUnless { it is kotlinx.serialization.json.JsonNull }?.content

        /** pt::read_json() of an answer that has to be an object; null when it is not. */
        private fun parseObject(body: String): JsonObject? = try {
            Json.parseToJsonElement(body) as? JsonObject
        } catch (_: SerializationException) {
            null
        }

        /** Http::url_encode(): the name as one segment of the path. */
        fun urlEncoded(name: String): String = URLEncoder.encode(name, Charsets.UTF_8).replace("+", "%20")

        /** boost::filesystem::path::filename() of an upload path. */
        fun fileNameOf(path: String): String = path.substringAfterLast('/')

        /** boost::filesystem::path::parent_path(): "" without a folder, "/" for a file at the root. */
        fun parentOf(path: String): String = when (val slash = path.lastIndexOf('/')) {
            -1 -> ""
            0 -> "/"
            else -> path.substring(0, slash)
        }

        /** escape_path_by_element() of PrusaLink's PUT: every folder and the file escaped on its own. */
        fun escapePathByElement(path: String): String = path.split('/').filter { it.isNotEmpty() }.joinToString("/") { urlEncoded(it) }

        /** Repetier's name of a printer's default model group, which Orca shows as "Default". */
        const val REPETIER_DEFAULT_GROUP = "#"

        /** The hosts whose requests take Http::ca_file(m_cafile); ElegooLink's other printers go through OctoPrint. */
        private val CA_FILE_HOSTS = setOf(
            PrintHostType.OCTOPRINT,
            PrintHostType.PRUSA_LINK,
            PrintHostType.PRUSA_CONNECT,
            PrintHostType.ASTROBOX,
            PrintHostType.REPETIER,
            PrintHostType.MOONRAKER,
            PrintHostType.CREALITY_PRINT,
            PrintHostType.OBICO,
            PrintHostType.ELEGOO_LINK,
        )

        /** OctoPrint::make_url(): a host without a scheme is reached over http. */
        fun makeUrl(host: String, path: String): String {
            val base = if (host.startsWith("http://") || host.startsWith("https://")) host else "http://$host"
            return if (base.endsWith("/")) base + path else "$base/$path"
        }
    }
}
