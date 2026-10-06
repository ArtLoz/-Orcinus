package app.orcinus.shadow.network.printhost

import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.util.UUID
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

/**
 * An answer of the host with an HTTP status that is not a success, which a host
 * may act on (a 401 refreshes a token); its message is format_error()'s
 * "HTTP <status>: <body>".
 */
class HttpStatusException(val status: Int, val body: String, message: String = "HTTP $status: $body") : IOException(message)

/** The user and password a host asks for when it does not take a key (atUserPassword). */
data class HttpAuth(val user: String, val password: String)

/** What a host answered a request with: its HTTP status and its body, as Http's on_complete() gets them. */
data class HttpAnswer(val status: Int, val body: String)

/**
 * What the print host requests need of the network, which keeps the uploader
 * testable: a multipart post that carries a file, a post or put of the file
 * itself, and a post of a JSON body. The answer's body comes back on success.
 */
interface HttpClient {
    suspend fun postMultipart(
        url: String,
        headers: Map<String, String>,
        fields: Map<String, String>,
        fileField: String,
        fileName: String,
        file: File,
        /** Http::on_progress: how much of the file has gone out, of its whole size. */
        onProgress: ((sent: Long, total: Long) -> Unit)? = null,
        auth: HttpAuth? = null,
    ): Result<String>

    /**
     * Http::set_post_body / set_put_body: the file is the whole body, as Duet's
     * rr_upload and PrusaLink's api/v1/files take it.
     */
    suspend fun sendFile(
        url: String,
        method: String,
        headers: Map<String, String>,
        file: File,
        onProgress: ((sent: Long, total: Long) -> Unit)? = null,
        auth: HttpAuth? = null,
    ): Result<String>

    /** sendFile() with the status of the answer besides its body, which Duet's DSF checks for 201. */
    suspend fun sendFileAnswer(
        url: String,
        method: String,
        headers: Map<String, String>,
        file: File,
        onProgress: ((sent: Long, total: Long) -> Unit)? = null,
        auth: HttpAuth? = null,
    ): Result<HttpAnswer> = sendFile(url, method, headers, file, onProgress, auth).map { HttpAnswer(HTTP_OK, it) }

    suspend fun postJson(url: String, headers: Map<String, String>, body: String, auth: HttpAuth? = null): Result<String>

    /**
     * Http::form_add_file(path, name, offset, length): a multipart post whose
     * file is [length] bytes of [file] from [offset], as ElegooLink sends a file
     * in pieces.
     */
    suspend fun postMultipartPart(
        url: String,
        headers: Map<String, String>,
        fields: Map<String, String>,
        fileField: String,
        fileName: String,
        file: File,
        offset: Long,
        length: Long,
        onProgress: ((sent: Long, total: Long) -> Unit)? = null,
    ): Result<String>

    /** Http::set_post_body(std::string): [body] as the whole request, with [method]. */
    suspend fun sendBytes(url: String, method: String, headers: Map<String, String>, body: ByteArray): Result<String>

    /** Http::form_add() without a file: a multipart form of [fields] alone, as OAuth's token requests post. */
    suspend fun postFields(url: String, headers: Map<String, String>, fields: Map<String, String>): Result<String>

    suspend fun get(url: String, headers: Map<String, String>, auth: HttpAuth? = null): Result<String>

    /**
     * Http::get() of a file: the answer's body written to [target] as it
     * comes, as the profile updater saves a bundle. Nothing is left of a
     * download that failed.
     */
    suspend fun download(url: String, target: File): Result<Unit> = Result.failure(UnsupportedOperationException("download"))

    /**
     * Http::ca_file(): the same client trusting the certificates of the file
     * at [path] for HTTPS instead of the system's, as curl's CAINFO does.
     */
    fun withCaFile(path: String): HttpClient = this

    /**
     * Http::timeout_connect() and Http::timeout_max(): the same client with
     * [connectMillis] to connect and [maxMillis] for a whole request (0 for
     * no limit), as a host sets them for some of its requests.
     */
    fun withTimeouts(connectMillis: Int, maxMillis: Int): HttpClient = this

    companion object {
        const val HTTP_OK = 200
    }
}

/** The platform's own HTTP, which needs no dependency of its own. */
class UrlConnectionHttpClient(
    /** Http::timeout_connect(): curl's CURLOPT_CONNECTTIMEOUT. */
    private val connectTimeoutMillis: Int = DEFAULT_TIMEOUT_CONNECT_MILLIS,
    /** Http::timeout_max(): curl's CURLOPT_TIMEOUT for the whole request; 0 for none (DEFAULT_TIMEOUT_MAX). */
    private val maxMillis: Int = 0,
    /** printhost_cafile: the certificates HTTPS trusts instead of the system's; null for the system's. */
    private val caFile: String? = null,
) : HttpClient {
    override fun withCaFile(path: String): HttpClient = UrlConnectionHttpClient(connectTimeoutMillis, maxMillis, path)

    override fun withTimeouts(connectMillis: Int, maxMillis: Int): HttpClient = UrlConnectionHttpClient(connectMillis, maxMillis, caFile)

    /** The certificates of [caFile], read when the first HTTPS request needs them. */
    private val caSocketFactory: Result<SSLSocketFactory>? by lazy {
        caFile?.let { path ->
            runCatching {
                val certificates = File(path).inputStream().use { CertificateFactory.getInstance("X.509").generateCertificates(it) }
                val store = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null, null) }
                certificates.forEachIndexed { index, certificate -> store.setCertificateEntry("ca$index", certificate) }
                val trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(store) }
                SSLContext.getInstance("TLS").apply { init(null, trust.trustManagers, null) }.socketFactory
            }
        }
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
    ): Result<String> = postForm(url, headers, fields, fileField, fileName, file, 0, file.length(), onProgress, auth)

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
    ): Result<String> = postForm(url, headers, fields, fileField, fileName, file, offset, length, onProgress, auth = null)

    private suspend fun postForm(
        url: String,
        headers: Map<String, String>,
        fields: Map<String, String>,
        fileField: String,
        fileName: String,
        file: File,
        offset: Long,
        length: Long,
        onProgress: ((sent: Long, total: Long) -> Unit)?,
        auth: HttpAuth?,
    ): Result<String> = cancellable { aborter ->
        val form = Form("orcinus-${UUID.randomUUID()}", fields, fileField, fileName)
        request(url, "POST", headers, form.contentType, length = form.head.size + length + form.tail.size, auth = auth, aborter = aborter) { output ->
            output.write(form.head)
            copy(file, output, onProgress, offset, length)
            output.write(form.tail)
        }.result()
    }

    override suspend fun sendBytes(url: String, method: String, headers: Map<String, String>, body: ByteArray): Result<String> =
        cancellable { aborter ->
            request(url, method, headers, contentType = null, aborter = aborter) { output -> output.write(body) }.result()
        }

    override suspend fun postFields(url: String, headers: Map<String, String>, fields: Map<String, String>): Result<String> =
        cancellable { aborter ->
            val boundary = "orcinus-${UUID.randomUUID()}"
            request(url, "POST", headers, "multipart/form-data; boundary=$boundary", aborter = aborter) { output ->
                val writer = output.bufferedWriter()
                for ((name, value) in fields) {
                    writer.write("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n")
                }
                writer.write("--$boundary--\r\n")
                writer.flush()
            }.result()
        }

    override suspend fun sendFile(
        url: String,
        method: String,
        headers: Map<String, String>,
        file: File,
        onProgress: ((sent: Long, total: Long) -> Unit)?,
        auth: HttpAuth?,
    ): Result<String> = sendFileAnswer(url, method, headers, file, onProgress, auth).map { it.body }

    override suspend fun sendFileAnswer(
        url: String,
        method: String,
        headers: Map<String, String>,
        file: File,
        onProgress: ((sent: Long, total: Long) -> Unit)?,
        auth: HttpAuth?,
    ): Result<HttpAnswer> = cancellable { aborter ->
        // set_put_body(): CURLOPT_INFILESIZE, the file's size; set_post_body(): the file as the post's fields.
        val size = file.length()
        request(url, method, headers, contentType = null, length = size, auth = auth, aborter = aborter) { output ->
            copy(file, output, onProgress, 0, size)
        }.answer()
    }

    override suspend fun postJson(url: String, headers: Map<String, String>, body: String, auth: HttpAuth?): Result<String> =
        cancellable { aborter ->
            request(url, "POST", headers, "application/json", auth = auth, aborter = aborter) { output -> output.write(body.toByteArray()) }.result()
        }

    override suspend fun get(url: String, headers: Map<String, String>, auth: HttpAuth?): Result<String> = cancellable { aborter ->
        request(url, "GET", headers, contentType = null, auth = auth, aborter = aborter, writeBody = null).result()
    }

    override suspend fun download(url: String, target: File): Result<Unit> = try {
        cancellable { aborter -> perform(url, "GET", emptyMap(), contentType = null, length = null, aborter = aborter, writeBody = null, sink = target).result() }
            .map { }
            .onFailure { target.delete() }
    } catch (cancelled: CancellationException) {
        target.delete()
        throw cancelled
    }

    /**
     * Http::cancel(): a request whose coroutine is cancelled is aborted, its
     * connection closed under the read or write it blocks in — a login that
     * waits on a long answer, an upload — instead of running on unseen. One
     * that outlasts [maxMillis] is aborted the same way and fails with curl's
     * "Timeout was reached".
     */
    private suspend fun <T> cancellable(work: (Aborter) -> Result<T>): Result<T> = coroutineScope {
        val aborter = Aborter()
        val running = async(Dispatchers.IO) { work(aborter) }
        try {
            if (maxMillis <= 0) {
                running.await()
            } else {
                withTimeoutOrNull(maxMillis.toLong()) { running.await() } ?: run {
                    aborter.abort()
                    Result.failure(SocketTimeoutException(TIMEOUT_REACHED))
                }
            }
        } catch (cancelled: CancellationException) {
            aborter.abort()
            throw cancelled
        }
    }

    /** The connection a cancelled request closes. */
    private class Aborter {
        @Volatile private var connection: HttpURLConnection? = null

        @Volatile var aborted = false
            private set

        fun opened(connection: HttpURLConnection) {
            this.connection = connection
            if (aborted) connection.disconnect()
        }

        fun abort() {
            aborted = true
            connection?.disconnect()
        }
    }

    /**
     * curl's multipart form (Http::form_add, form_add_file): the fields and the
     * head of the file's part, the file, then the closing boundary, whose size
     * is known before it goes out, as curl knows it.
     */
    private class Form(boundary: String, fields: Map<String, String>, fileField: String, fileName: String) {
        val contentType = "multipart/form-data; boundary=$boundary"

        val head: ByteArray = buildString {
            for ((name, value) in fields) {
                append("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n")
            }
            append("--$boundary\r\nContent-Disposition: form-data; name=\"$fileField\"; filename=\"$fileName\"\r\n")
            append("Content-Type: application/octet-stream\r\n\r\n")
        }.toByteArray()

        val tail: ByteArray = "\r\n--$boundary--\r\n".toByteArray()
    }

    /**
     * Http::on_progress: the file goes out in pieces, so the screen can say how
     * far it got, and it never sits in memory whole.
     */
    private fun copy(
        file: File,
        output: OutputStream,
        onProgress: ((sent: Long, total: Long) -> Unit)?,
        offset: Long,
        length: Long,
    ) {
        val total = length
        var sent = 0L
        onProgress?.invoke(0, total)
        file.inputStream().use { input ->
            var skipped = 0L
            while (skipped < offset) {
                val step = input.skip(offset - skipped)
                if (step <= 0) break
                skipped += step
            }
            val buffer = ByteArray(UPLOAD_BUFFER)
            while (sent < total) {
                val read = input.read(buffer, 0, minOf(buffer.size.toLong(), total - sent).toInt())
                if (read < 0) break
                output.write(buffer, 0, read)
                sent += read
                onProgress?.invoke(sent, total)
            }
        }
    }

    private fun request(
        url: String,
        method: String,
        headers: Map<String, String>,
        contentType: String?,
        /** The size of a body that is a file, which goes out in pieces with that length instead of being buffered. */
        length: Long? = null,
        auth: HttpAuth? = null,
        aborter: Aborter,
        writeBody: ((OutputStream) -> Unit)?,
    ): Answer {
        if (auth != null && writeBody != null) {
            // Http::auth_digest(): curl sends no body before it has answered the
            // host's challenge, and asks with an empty one first.
            val probe = perform(url, method, headers, contentType, length = null, aborter = aborter, writeBody = {})
            // A host that asks for nothing is sent the whole request (Curl_http_auth_act()).
            if (probe is Answer.Ok) return perform(url, method, headers, contentType, length, aborter, writeBody)
            return answered(probe, url, method, headers, contentType, length, auth, aborter, writeBody)
        }
        val answer = perform(url, method, headers, contentType, length, aborter, writeBody)
        return answered(answer, url, method, headers, contentType, length, auth, aborter, writeBody)
    }

    /**
     * Http::auth_digest: a host that asks for digest is answered with a
     * second request that carries the computed response.
     */
    private fun answered(
        answer: Answer,
        url: String,
        method: String,
        headers: Map<String, String>,
        contentType: String?,
        length: Long?,
        auth: HttpAuth?,
        aborter: Aborter,
        writeBody: ((OutputStream) -> Unit)?,
    ): Answer {
        val challenge = (answer as? Answer.Unauthorized)?.challenge
        if (auth != null && challenge != null && challenge.startsWith("Digest ", ignoreCase = true)) {
            val header = digestHeader(challenge, method, URL(url).file, auth)
                ?: return Answer.Failed(IOException("The host asked for an authorization the app cannot give"))
            return perform(url, method, headers + ("Authorization" to header), contentType, length, aborter, writeBody)
        }
        return answer
    }

    private fun perform(
        url: String,
        method: String,
        headers: Map<String, String>,
        contentType: String?,
        length: Long?,
        aborter: Aborter,
        writeBody: ((OutputStream) -> Unit)?,
        /** The file a success's body is written to instead of being read as text. */
        sink: File? = null,
    ): Answer {
        if (aborter.aborted) return Answer.Failed(IOException("Canceled"))
        val connection = try {
            (URL(url).openConnection() as HttpURLConnection)
        } catch (error: IOException) {
            return Answer.Failed(error)
        } catch (error: IllegalArgumentException) {
            return Answer.Failed(error)
        }
        aborter.opened(connection)
        return try {
            if (connection is HttpsURLConnection) {
                // curl's "error setting certificate verify locations" for a file it cannot read.
                caSocketFactory?.let { factory ->
                    connection.sslSocketFactory = factory.getOrElse { error ->
                        return Answer.Failed(IOException("Problem with the SSL CA cert (path? access rights?): $caFile", error))
                    }
                }
            }
            connection.requestMethod = method
            connection.connectTimeout = connectTimeoutMillis
            // DEFAULT_TIMEOUT_MAX: curl waits for the answer as long as it takes, unless the request has a limit.
            connection.readTimeout = maxMillis
            contentType?.let { connection.setRequestProperty("Content-Type", it) }
            headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            if (writeBody != null) {
                connection.doOutput = true
                // A G-code file is tens of megabytes: it goes out in pieces
                // instead of being held in memory whole, with its length
                // (CURLOPT_INFILESIZE, the form's size) rather than in
                // chunks, which the servers of printer boards may not take.
                if (length != null) connection.setFixedLengthStreamingMode(length)
                connection.outputStream.use(writeBody)
            }
            val code = connection.responseCode
            when {
                code in 200..299 && sink != null -> {
                    connection.inputStream.use { input -> sink.outputStream().use { input.copyTo(it) } }
                    Answer.Ok(code, "")
                }
                code in 200..299 -> Answer.Ok(code, connection.inputStream.use { it.readBytes().decodeToString() })
                else -> {
                    val failure = HttpStatusException(code, errorBody(connection))
                    if (code == HttpURLConnection.HTTP_UNAUTHORIZED) {
                        Answer.Unauthorized(connection.getHeaderField("WWW-Authenticate").orEmpty(), failure)
                    } else {
                        Answer.Failed(failure)
                    }
                }
            }
        } catch (error: IOException) {
            Answer.Failed(error)
        } finally {
            connection.disconnect()
        }
    }

    /** The body of an answer that is not a success. */
    private fun errorBody(connection: HttpURLConnection): String = try {
        connection.errorStream?.use { it.readBytes().decodeToString() }.orEmpty()
    } catch (_: IOException) {
        ""
    }

    private sealed interface Answer {
        data class Ok(val status: Int, val body: String) : Answer

        data class Unauthorized(val challenge: String, val failure: HttpStatusException) : Answer

        data class Failed(val error: Throwable) : Answer

        fun answer(): Result<HttpAnswer> = when (this) {
            is Ok -> Result.success(HttpAnswer(status, body))
            is Unauthorized -> Result.failure(failure)
            is Failed -> Result.failure(error)
        }

        fun result(): Result<String> = answer().map { it.body }
    }

    companion object {
        /** Http::priv::DEFAULT_TIMEOUT_CONNECT: ten seconds. */
        const val DEFAULT_TIMEOUT_CONNECT_MILLIS = 10_000

        /** curl's text for CURLE_OPERATION_TIMEDOUT, which PrintHost::format_error() words for the user. */
        const val TIMEOUT_REACHED = "Timeout was reached"

        private const val UPLOAD_BUFFER = 64 * 1024
    }
}

/**
 * RFC 2617 digest, which libcurl does for OrcaSlicer (CURLAUTH_DIGEST): the
 * answer to a challenge, or null when the host asks for something else than
 * MD5. Only the qop values curl itself sends are answered.
 */
internal fun digestHeader(challenge: String, method: String, path: String, auth: HttpAuth, cnonce: String = UUID.randomUUID().toString().take(16)): String? {
    val parts = challenge.removePrefix("Digest ").removePrefix("digest ").splitParameters()
    val realm = parts["realm"] ?: return null
    val nonce = parts["nonce"] ?: return null
    val algorithm = parts["algorithm"]?.uppercase() ?: "MD5"
    if (algorithm != "MD5") return null
    val qop = parts["qop"]?.split(",")?.map { it.trim() }?.firstOrNull { it == "auth" }
    val ha1 = md5("${auth.user}:$realm:${auth.password}")
    val ha2 = md5("$method:$path")
    val nc = "00000001"
    val response = if (qop == null) md5("$ha1:$nonce:$ha2") else md5("$ha1:$nonce:$nc:$cnonce:$qop:$ha2")
    return buildString {
        append("Digest username=\"${auth.user}\", realm=\"$realm\", nonce=\"$nonce\", uri=\"$path\", response=\"$response\"")
        parts["opaque"]?.let { append(", opaque=\"$it\"") }
        if (qop != null) append(", qop=$qop, nc=$nc, cnonce=\"$cnonce\"")
    }
}

/** The comma-separated name="value" pairs of a challenge. */
private fun String.splitParameters(): Map<String, String> {
    val values = mutableMapOf<String, String>()
    var index = 0
    while (index < length) {
        val equals = indexOf('=', index)
        if (equals < 0) break
        val name = substring(index, equals).trim().lowercase()
        var value: String
        if (getOrNull(equals + 1) == '"') {
            val end = indexOf('"', equals + 2)
            if (end < 0) break
            value = substring(equals + 2, end)
            index = (indexOf(',', end) + 1).takeIf { it > 0 } ?: length
        } else {
            val end = indexOf(',', equals + 1).takeIf { it >= 0 } ?: length
            value = substring(equals + 1, end).trim()
            index = end + 1
        }
        values[name] = value
    }
    return values
}

private fun md5(text: String): String =
    MessageDigest.getInstance("MD5").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
