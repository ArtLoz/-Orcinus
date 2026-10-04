package app.orcinus.shadow.network.printhost

import app.orcinus.shadow.core.model.CloudLoginOutcome
import app.orcinus.shadow.core.model.CloudPrinterType
import app.orcinus.shadow.core.model.CloudProject
import app.orcinus.shadow.core.model.PhysicalPrinter
import app.orcinus.shadow.core.model.Printer3dOsChoice
import app.orcinus.shadow.core.model.Printer3dOsListsOutcome
import app.orcinus.shadow.core.model.PrintHostTestOutcome
import app.orcinus.shadow.core.model.PrintHostUploadOutcome
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * OrcaSlicer's 3DPrinterOS host (3DPrinterOS.cpp): the cloud's apiglobal
 * forms with the session of a login kept in 3dprinteros_api_cred.json
 * ([credentials]) — not the preset's key, which the host never reads. The
 * login is a token the browser confirms while the app asks the cloud for its
 * session (TokenAuthDialog); an upload names the cloud's printer type and,
 * for a project file, its project, and a print opens the cloud's quick print
 * in the browser.
 */
internal class C3DPrinterOS(
    private val http: HttpClient,
    private val credentials: File?,
    private val retryDelayMillis: Long = RETRY_DELAY_MILLIS,
) {
    /** is_logged_in(): a session is kept. */
    fun isLoggedIn(): Boolean = session() != null

    /** log_out() */
    fun logOut() {
        credentials?.delete()
    }

    /** test(): check_session, and the account's email the dialog names ([PrintHostTestOutcome.Success.description]). */
    suspend fun test(printer: PhysicalPrinter): PrintHostTestOutcome {
        val (session, email) = session() ?: ("" to "")
        val answer = sendForm(printer, "apiglobal/check_session", "session=$session")
        return when (answer.bool("result")) {
            true -> PrintHostTestOutcome.Success(email)
            false -> PrintHostTestOutcome.Failure(answer.string("message") ?: UNREADABLE)
            null -> PrintHostTestOutcome.Failure(UNREADABLE)
        }
    }

    /**
     * login(): a login token from generate_login_token, the page that confirms
     * it opened in the browser ([openPage]), and TokenAuthDialog's last answer
     * read for the session, which is kept with the account's email.
     */
    suspend fun login(printer: PhysicalPrinter, openPage: (String) -> Unit): CloudLoginOutcome {
        val generated = sendForm(printer, "apiglobal/generate_login_token", "app_type=plugin&app_name=" + SimplyPrint.escape("OrcaSlicer"))
        val token = if (generated.bool("result") == true) generated.string("message").orEmpty() else ""
        if (token.isEmpty()) return CloudLoginOutcome.Failure("Error. Can't get api token for authorization")
        openPage(url(printer.host, "noauth/apiglobal_login_with_token/$token"))
        val answer = loginWithToken(printer, token)
        val result = answer.bool("result") ?: return CloudLoginOutcome.Failure(UNREADABLE)
        if (!result) return CloudLoginOutcome.Failure(answer.string("message") ?: UNREADABLE)
        val message = answer["message"] as? JsonObject
        val session = message.string("session")
        val email = message.string("email")
        if (session == null || email == null) return CloudLoginOutcome.Failure(UNREADABLE)
        return if (save(session, email)) CloudLoginOutcome.Success else CloudLoginOutcome.Failure("Error saving session to file")
    }

    /**
     * TokenAuthDialog: login_with_token asked until the cloud answers with a
     * session, says no, or has been asked ten times, half a second apart; its
     * last answer. Every error says "HTTP error: <status>", 0 without one.
     */
    private suspend fun loginWithToken(printer: PhysicalPrinter, token: String): JsonObject {
        var attempt = 0
        while (true) {
            attempt++
            val answer = sendForm(printer, "apiglobal/login_with_token", "token=$token", httpErrors = true)
            val hasSession = (answer["message"] as? JsonObject)?.containsKey("session") == true
            if (answer.bool("result") != true || hasSession || attempt >= MAX_RETRIES) return answer
            delay(retryDelayMillis)
        }
    }

    /**
     * upload()'s first half: the session is checked, and the cloud's projects
     * and printer types (asked for the ones of the printer's model) are read
     * for UploadOptionsDialog.
     */
    suspend fun lists(printer: PhysicalPrinter): Printer3dOsListsOutcome {
        val tested = test(printer)
        if (tested is PrintHostTestOutcome.Failure) return Printer3dOsListsOutcome.Failure(tested.message)
        val session = session()?.first.orEmpty()
        val projects = sendForm(printer, "apiglobal/get_projects", "session=$session")
        val query = buildString {
            append("session=").append(session)
            if (printer.printerModel.isNotEmpty()) {
                append("&description=").append(SimplyPrint.escape(printer.printerModel))
                append("&software_version=").append(SimplyPrint.escape("OrcaSlicer"))
            }
        }
        val types = sendForm(printer, "apiglobal/get_printer_types", query)
        return try {
            Printer3dOsListsOutcome.Success(
                projects = if (projects.requireBool("result")) {
                    projects.array("message").map { CloudProject(it.field("id"), it.field("name")) }
                } else {
                    emptyList()
                },
                printerTypes = if (types.requireBool("result")) {
                    types.array("message").map { CloudPrinterType(it.field("id"), it.field("description")) }
                } else {
                    emptyList()
                },
            )
        } catch (_: IllegalStateException) {
            Printer3dOsListsOutcome.Failure(UNREADABLE)
        }
    }

    /**
     * upload()'s second half, after UploadOptionsDialog: the file posted to
     * apiglobal/upload (into the project chosen, or a new grey one of the
     * name typed), its printer type set with file_update, and for a print the
     * cloud's quick print opened.
     */
    suspend fun upload(
        printer: PhysicalPrinter,
        gcode: File,
        name: String,
        startPrint: Boolean,
        choice: Printer3dOsChoice?,
        onProgress: ((sent: Long, total: Long) -> Unit)?,
    ): PrintHostUploadOutcome {
        // 3DPrinterOS::set_auth(): the printer's HTTPS CA file.
        val http = if (printer.caFile.isNotEmpty()) this.http.withCaFile(printer.caFile) else this.http
        // The dialog was not shown, or was cancelled.
        if (choice == null) return PrintHostUploadOutcome.Failure("Canceled")
        val session = session()?.first.orEmpty()
        val fields = linkedMapOf("session" to session, "upload_type_id" to "7", "upload_soft_name" to "OrcaSlicer", "zip" to "false")
        if (choice.projectId.isNotEmpty()) {
            fields["project_id"] = choice.projectId
        } else if (choice.projectName.isNotEmpty()) {
            fields["project_name"] = choice.projectName
            fields["project_color"] = "grey"
        }
        val body = http.postMultipart(url(printer.host, "apiglobal/upload"), emptyMap(), fields, "file", name, gcode, onProgress)
            .getOrElse { return PrintHostUploadOutcome.Failure(it.message.orEmpty()) }
        val answer = parse(body) ?: return PrintHostUploadOutcome.Failure("Could not parse server response")
        val fileId = when (answer.bool("result")) {
            true -> (answer["message"] as? JsonObject).string("file_id") ?: return PrintHostUploadOutcome.Failure("Error during file upload")
            false -> return PrintHostUploadOutcome.Failure(answer.string("message") ?: "Error during file upload")
            null -> return PrintHostUploadOutcome.Failure("Error during file upload")
        }
        // set printer type for uploaded gcode; the desktop only logs a failure.
        sendForm(
            printer,
            "apiglobal/file_update",
            "session=$session&updates[$fileId][ptype]=${choice.printerTypeId}&updates[$fileId][gtype]=" +
                SimplyPrint.escape("OrcaSlicer") + "&updates[$fileId][zip]=false",
        )
        return PrintHostUploadOutcome.Success(name, openUrl = if (startPrint) url(printer.host, "quickprint?file_id=$fileId") else null)
    }

    /**
     * send_form(): the body as a form post; an answer that is not JSON, or an
     * error, becomes {"result": false, "message": ...}.
     */
    private suspend fun sendForm(printer: PhysicalPrinter, endpoint: String, body: String, httpErrors: Boolean = false): JsonObject {
        // 3DPrinterOS::set_auth(): the printer's HTTPS CA file.
        val http = if (printer.caFile.isNotEmpty()) this.http.withCaFile(printer.caFile) else this.http
        val answer = http.sendBytes(
            url(printer.host, endpoint),
            "POST",
            mapOf("Content-Type" to "application/x-www-form-urlencoded"),
            body.toByteArray(),
        )
        val text = answer.getOrElse { error ->
            val status = (error as? HttpStatusException)?.status
            val message = if (httpErrors) "HTTP error: ${status ?: 0}" else error.message.orEmpty()
            return buildJsonObject {
                put("result", false)
                put("message", message)
            }
        }
        return parse(text) ?: buildJsonObject {
            put("result", false)
            put("message", "Could not parse server response")
        }
    }

    /** load_api_session(): the session and the email, when both are kept. */
    private fun session(): Pair<String, String>? {
        val file = credentials?.takeIf { it.isFile } ?: return null
        val root = parse(runCatching { file.readText() }.getOrNull().orEmpty())
        val session = (root?.get("session") as? JsonPrimitive)?.content
        val email = (root?.get("email") as? JsonPrimitive)?.content
        if (session == null || email == null) {
            // remove corrupted file to avoid repeated failures
            file.delete()
            return null
        }
        return session to email
    }

    /** save_api_session(): written aside first, then put in place. */
    private fun save(session: String, email: String): Boolean {
        val target = credentials ?: return false
        return runCatching {
            val temp = File(target.path + ".tmp")
            temp.writeText(
                buildJsonObject {
                    put("session", session)
                    put("email", email)
                }.toString(),
            )
            if (!temp.renameTo(target)) {
                target.delete()
                check(temp.renameTo(target))
            }
        }.isSuccess
    }

    internal companion object {
        const val MAX_RETRIES = 10
        const val RETRY_DELAY_MILLIS = 500L
        private const val UNREADABLE = "Could not parse server response."

        /** make_url(): https:// in front of an address without a scheme. */
        fun url(host: String, path: String): String = when {
            host.startsWith("http://") || host.startsWith("https://") -> if (host.endsWith('/')) host + path else "$host/$path"
            else -> "https://$host/$path"
        }

        private fun parse(body: String): JsonObject? = runCatching { Json.parseToJsonElement(body) as? JsonObject }.getOrNull()

        private fun JsonObject?.bool(key: String): Boolean? = (this?.get(key) as? JsonPrimitive)?.let { it.booleanOrNull ?: (it.content == "true") }

        private fun JsonObject?.string(key: String): String? = (this?.get(key) as? JsonPrimitive)?.content

        /** ptree's get<bool>(): an answer without it cannot be read. */
        private fun JsonObject.requireBool(key: String): Boolean = bool(key) ?: throw IllegalStateException(key)

        private fun JsonObject?.array(key: String): List<JsonObject> =
            ((this?.get(key) as? JsonArray) ?: throw IllegalStateException(key)).map { it as? JsonObject ?: throw IllegalStateException(key) }

        private fun JsonObject.field(key: String): String = (this[key] as? JsonPrimitive)?.content ?: throw IllegalStateException(key)
    }
}
