package app.orcinus.shadow.network.printhost

import app.orcinus.shadow.core.model.CloudLoginOutcome
import app.orcinus.shadow.core.model.PrintHostTestOutcome
import app.orcinus.shadow.core.model.PrintHostUploadOutcome
import java.io.File
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * OrcaSlicer's SimplyPrint host (SimplyPrint.cpp): an account linked with
 * OAuth (PKCE, S256) through the browser, whose tokens are kept in
 * simplyprint_oauth.json ([credentials]); a refused token is refreshed once
 * and the call made again. The G-code goes up as a temporary file — in 100 MB
 * pieces when it is bigger — and SimplyPrint's panel is opened to import it.
 */
internal class SimplyPrint(
    private val http: HttpClient,
    private val credentials: File?,
    private val callbackPort: Int = CALLBACK_PORT,
) {
    /** is_logged_in(): a credential is kept. */
    fun isLoggedIn(): Boolean = credential() != null

    /** log_out(): the credential file goes. */
    fun logOut() {
        credentials?.delete()
    }

    /**
     * OAuthDialog: [openPage] opens the login page in the browser while the
     * callback server waits for the browser to come back with the code
     * (OAuthJob::process()), which is exchanged for the tokens.
     */
    suspend fun login(openPage: (String) -> Unit): CloudLoginOutcome {
        val verification = randomHex()
        val state = randomHex()
        val query = listOf(
            "client_id" to CLIENT_ID,
            "redirect_uri" to CALLBACK_URL,
            "scope" to CLIENT_SCOPES,
            "response_type" to RESPONSE_TYPE,
            "state" to state,
            "code_challenge" to codeChallenge(verification),
            "code_challenge_method" to "S256",
        )
        var result: CloudLoginOutcome = CloudLoginOutcome.Failure("Unknown error")
        val server = OAuthCallbackServer(callbackPort)
        openPage("$URL_BASE_HOME/panel/oauth2/authorize?" + urlEncode(query))
        server.serve { url ->
            if (!url.contains("/callback")) {
                result = CloudLoginOutcome.Failure("")
                return@serve OAuthCallbackServer.Reply(null, done = true)
            }
            val code = OAuthCallbackServer.urlParam(url, "code")
            val returned = OAuthCallbackServer.urlParam(url, "state")
            if (returned != state) {
                result = CloudLoginOutcome.Failure("The provided state is not correct.")
                return@serve OAuthCallbackServer.Reply(LOGIN_SUCCESS, done = true)
            }
            if (code.isEmpty()) {
                result = CloudLoginOutcome.Failure(
                    if (OAuthCallbackServer.urlParam(url, "error_code") == "user_denied") {
                        "Please give the required permissions when authorizing this application."
                    } else {
                        "Something unexpected happened when trying to log in, please try again."
                    },
                )
                return@serve OAuthCallbackServer.Reply(LOGIN_SUCCESS, done = true)
            }
            // Request the access token from the authorization server.
            val answer = tokenHttp.postFields(
                TOKEN_URL,
                emptyMap(),
                linkedMapOf(
                    "client_id" to CLIENT_ID,
                    "redirect_uri" to CALLBACK_URL,
                    "grant_type" to "authorization_code",
                    "code" to code,
                    "code_verifier" to verification,
                    "scope" to CLIENT_SCOPES,
                ),
            )
            val tokens = parseTokenResponse(answer)
            result = tokens.fold(
                onSuccess = { (access, refresh) ->
                    save(access, refresh)
                    CloudLoginOutcome.Success
                },
                onFailure = { CloudLoginOutcome.Failure(it.message.orEmpty()) },
            )
            OAuthCallbackServer.Reply(LOGIN_SUCCESS, done = true)
        }
        return result
    }

    /** test(): the token's info, which only answers a valid token. */
    suspend fun test(): PrintHostTestOutcome {
        if (credential() == null) return PrintHostTestOutcome.Failure("")
        return apiCall { token -> http.get("$URL_BASE_API/oauth2/TokenInfo", headers(token) + ("Accept" to "application/json")) }.fold(
            onSuccess = { PrintHostTestOutcome.Success("") },
            onFailure = { PrintHostTestOutcome.Failure("") },
        )
    }

    /** upload(): a temporary upload, in pieces above 100 MB, then the panel that imports it. */
    suspend fun upload(gcode: File, name: String, onProgress: ((sent: Long, total: Long) -> Unit)?): PrintHostUploadOutcome {
        if (credential() == null) return PrintHostUploadOutcome.Failure("SimplyPrint account not linked. Go to Connect options to set it up.")
        return if (gcode.length() > MAX_SINGLE_UPLOAD_FILE_SIZE) chunkUpload(gcode, name, onProgress) else tempUpload(gcode, null, name, onProgress)
    }

    /**
     * do_temp_upload(): the file, or the chunk id of a file sent in pieces,
     * becomes a temporary file of the account, whose uuid the panel imports.
     */
    private suspend fun tempUpload(gcode: File?, chunkId: String?, name: String, onProgress: ((sent: Long, total: Long) -> Unit)?): PrintHostUploadOutcome {
        val body = apiCall { token ->
            if (gcode != null) {
                http.postMultipart("$URL_BASE_HOME/api/files/TempUpload", headers(token), emptyMap(), "file", name, gcode, onProgress)
            } else {
                http.postFields("$URL_BASE_HOME/api/files/TempUpload", headers(token), mapOf("chunkId" to chunkId.orEmpty()))
            }
        }.getOrElse { return PrintHostUploadOutcome.Failure(it.message.orEmpty()) }
        val uuid = ((parse(body) as? JsonObject)?.get("uuid") as? JsonPrimitive)?.content
            ?: return PrintHostUploadOutcome.Failure("Unknown error")
        // Launch external browser for file importing after uploading
        return PrintHostUploadOutcome.Success(name, openUrl = "$URL_BASE_HOME/panel?" + urlEncode(listOf("import" to "tmp:$uuid", "filename" to name)))
    }

    /**
     * do_chunk_upload(): pieces of just under 100 MB to ChunkReceive, the
     * first one naming the file and answering the chunk id and a delete token,
     * which throws the pieces away when a later step fails.
     */
    private suspend fun chunkUpload(gcode: File, name: String, onProgress: ((sent: Long, total: Long) -> Unit)?): PrintHostUploadOutcome {
        val size = gcode.length()
        val count = ((size + CHUNK_SIZE - 1) / CHUNK_SIZE).toInt()
        var chunkId = ""
        var deleteToken = ""
        suspend fun cleanUp() {
            if (chunkId.isEmpty()) return
            val query = urlEncode(listOf("id" to chunkId, "temp" to "true", "delete" to deleteToken))
            apiCall { token -> http.get("$CHUNK_RECEIVE_URL?$query", headers(token)) }
        }
        for (index in 0 until count) {
            val query = buildList {
                add("i" to index.toString())
                add("temp" to "true")
                if (index == 0) {
                    add("filename" to name)
                    add("chunks" to count.toString())
                    add("totalsize" to size.toString())
                } else {
                    add("id" to chunkId)
                }
            }
            val offset = index * CHUNK_SIZE
            val length = if (index == count - 1) size - offset else CHUNK_SIZE
            val body = apiCall { token ->
                http.postMultipartPart(
                    "$CHUNK_RECEIVE_URL?" + urlEncode(query),
                    headers(token),
                    emptyMap(),
                    "file",
                    name,
                    gcode,
                    offset,
                    length,
                    onProgress?.let { report -> { sent: Long, _: Long -> report(offset + sent, size) } },
                )
            }.getOrElse {
                cleanUp()
                return PrintHostUploadOutcome.Failure(it.message.orEmpty())
            }
            if (index == 0) {
                val answer = parse(body) as? JsonObject
                val id = (answer?.get("id") as? JsonPrimitive)?.longOrNull
                val token = (answer?.get("delete_token") as? JsonPrimitive)?.content
                if (id == null || token == null) return PrintHostUploadOutcome.Failure("Unknown error")
                chunkId = id.toString()
                deleteToken = token
            }
        }
        val done = tempUpload(null, chunkId, name, onProgress)
        if (done is PrintHostUploadOutcome.Failure) cleanUp()
        return done
    }

    /**
     * do_api_call(): the call with the access token; a 401 refreshes the
     * token (grant_type refresh_token), saves it and makes the call again.
     */
    private suspend fun apiCall(call: suspend (token: String) -> Result<String>): Result<String> {
        val (access, refresh) = credential() ?: return Result.failure(IllegalStateException("No SimplyPrint login"))
        val first = call(access)
        if ((first.exceptionOrNull() as? HttpStatusException)?.status != UNAUTHORIZED) return first
        val renewed = parseTokenResponse(
            tokenHttp.postFields(
                TOKEN_URL,
                emptyMap(),
                linkedMapOf("grant_type" to "refresh_token", "client_id" to CLIENT_ID, "refresh_token" to refresh),
            ),
        ).getOrElse { return Result.failure(it) }
        save(renewed.first, renewed.second)
        return call(renewed.first)
    }

    /** set_auth() and the plugin's user agent. */
    private fun headers(token: String) = mapOf("Authorization" to "Bearer $token", "User-Agent" to "SimplyPrint Orca Plugin")

    /** load_oauth_credential(): the access and refresh tokens, when both are kept. */
    private fun credential(): Pair<String, String>? {
        val file = credentials?.takeIf { it.isFile } ?: return null
        val root = parse(runCatching { file.readText() }.getOrNull().orEmpty()) as? JsonObject ?: return null
        val access = (root["access_token"] as? JsonPrimitive)?.content ?: return null
        val refresh = (root["refresh_token"] as? JsonPrimitive)?.content ?: return null
        return access to refresh
    }

    /** save_oauth_credential() */
    private fun save(access: String, refresh: String) {
        credentials?.writeText(
            buildJsonObject {
                put("access_token", access)
                put("refresh_token", refresh)
            }.toString() + "\n",
        )
    }

    /** The token requests' own timeout of five seconds. */
    private val tokenHttp = if (http is UrlConnectionHttpClient) UrlConnectionHttpClient(TOKEN_TIMEOUT_MILLIS, TOKEN_TIMEOUT_MILLIS) else http

    internal companion object {
        const val URL_BASE_HOME = "https://simplyprint.io"
        const val URL_BASE_API = "https://api.simplyprint.io"
        const val CALLBACK_PORT = 21328
        const val CALLBACK_URL = "http://localhost:21328/callback"
        const val RESPONSE_TYPE = "code"
        const val CLIENT_ID = "simplyprintorcaslicer"
        const val CLIENT_SCOPES = "user.read files.temp_upload"
        const val TOKEN_URL = "$URL_BASE_API/oauth2/Token"
        const val CHUNK_RECEIVE_URL = "$URL_BASE_API/0/files/ChunkReceive"
        const val LOGIN_SUCCESS = "$URL_BASE_HOME/login-success"
        const val MAX_SINGLE_UPLOAD_FILE_SIZE = 100_000_000L
        const val CHUNK_SIZE = MAX_SINGLE_UPLOAD_FILE_SIZE - 1_000_000L
        private const val TOKEN_TIMEOUT_MILLIS = 5_000
        private const val UNAUTHORIZED = 401

        /** generate_verification_code(): 32 random bytes as hex. */
        fun randomHex(): String = ByteArray(32).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }

        /** sha256b64(): the URL-safe base64 of the code's SHA-256, without padding (RFC 7636). */
        fun codeChallenge(verification: String): String =
            Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verification.toByteArray()))

        /** url_encode(): key=value pairs, each part escaped as curl escapes it. */
        fun urlEncode(query: List<Pair<String, String>>): String = query.joinToString("&") { (key, value) ->
            if (value.isEmpty()) escape(key) else escape(key) + "=" + escape(value)
        }

        /** Http::url_encode(): curl_easy_escape, which leaves letters, digits and -._~ alone. */
        fun escape(text: String): String = URLEncoder.encode(text, Charsets.UTF_8)
            .replace("+", "%20").replace("*", "%2A").replace("%7E", "~")

        /** OAuthJob::parse_token_response(): the access and refresh tokens, or why there are none. */
        fun parseTokenResponse(answer: Result<String>): Result<Pair<String, String>> {
            val body = answer.getOrNull() ?: (answer.exceptionOrNull() as? HttpStatusException)?.body
            val root = parse(body.orEmpty()) as? JsonObject ?: return Result.failure(IllegalStateException("Unknown error"))
            if (answer.isFailure) {
                val description = (root["error_description"] as? JsonPrimitive)?.content ?: "Unknown error"
                return Result.failure(IllegalStateException(description))
            }
            val access = (root["access_token"] as? JsonPrimitive)?.content
            val refresh = (root["refresh_token"] as? JsonPrimitive)?.content
            if (access == null || refresh == null) return Result.failure(IllegalStateException("Unknown error"))
            return Result.success(access to refresh)
        }

        private fun parse(body: String) = runCatching { Json.parseToJsonElement(body) }.getOrNull()
    }
}
