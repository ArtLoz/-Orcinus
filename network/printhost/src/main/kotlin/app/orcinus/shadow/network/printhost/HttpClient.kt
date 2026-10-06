package app.orcinus.shadow.network.printhost

import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.net.HttpURLConnection
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

/** An answer of the host with an HTTP status that is not a success, which a host may act on (a 401 refreshes a token). */
class HttpStatusException(val status: Int, val body: String, message: String) : IOException(message)

/** The user and password a host asks for when it does not take a key (atUserPassword). */
data class HttpAuth(val user: String, val password: String)

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
}

/** The platform's own HTTP, which needs no dependency of its own. */
class UrlConnectionHttpClient(
    private val connectTimeoutMillis: Int = 15_000,
    private val readTimeoutMillis: Int = 120_000,
    /** printhost_cafile: the certificates HTTPS trusts instead of the system's; null for the system's. */
    private val caFile: String? = null,
) : HttpClient {
    override fun withCaFile(path: String): HttpClient = UrlConnectionHttpClient(connectTimeoutMillis, readTimeoutMillis, path)

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
    ): Result<String> = cancellable { aborter ->
        val boundary = "orcinus-${UUID.randomUUID()}"
        request(url, "POST", headers, "multipart/form-data; boundary=$boundary", streamed = true, auth = auth, aborter = aborter) { output ->
            val writer = output.bufferedWriter()
            for ((name, value) in fields) {
                writer.write("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n")
            }
            writer.write(
                "--$boundary\r\nContent-Disposition: form-data; name=\"$fileField\"; filename=\"$fileName\"\r\n" +
                    "Content-Type: application/octet-stream\r\n\r\n",
            )
            writer.flush()
            copy(file, output, onProgress)
            writer.write("\r\n--$boundary--\r\n")
            writer.flush()
        }
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
    ): Result<String> = cancellable { aborter ->
        val boundary = "orcinus-${UUID.randomUUID()}"
        request(url, "POST", headers, "multipart/form-data; boundary=$boundary", streamed = true, aborter = aborter) { output ->
            val writer = output.bufferedWriter()
            for ((name, value) in fields) {
                writer.write("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n")
            }
            writer.write(
                "--$boundary\r\nContent-Disposition: form-data; name=\"$fileField\"; filename=\"$fileName\"\r\n" +
                    "Content-Type: application/octet-stream\r\n\r\n",
            )
            writer.flush()
            copy(file, output, onProgress, offset, length)
            writer.write("\r\n--$boundary--\r\n")
            writer.flush()
        }
    }

    override suspend fun sendBytes(url: String, method: String, headers: Map<String, String>, body: ByteArray): Result<String> =
        cancellable { aborter ->
            request(url, method, headers, contentType = null, aborter = aborter) { output -> output.write(body) }
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
            }
        }

    override suspend fun sendFile(
        url: String,
        method: String,
        headers: Map<String, String>,
        file: File,
        onProgress: ((sent: Long, total: Long) -> Unit)?,
        auth: HttpAuth?,
    ): Result<String> = cancellable { aborter ->
        request(url, method, headers, contentType = null, streamed = true, auth = auth, aborter = aborter) { output ->
            copy(file, output, onProgress)
        }
    }

    override suspend fun postJson(url: String, headers: Map<String, String>, body: String, auth: HttpAuth?): Result<String> =
        cancellable { aborter ->
            request(url, "POST", headers, "application/json", auth = auth, aborter = aborter) { output -> output.write(body.toByteArray()) }
        }

    override suspend fun get(url: String, headers: Map<String, String>, auth: HttpAuth?): Result<String> = cancellable { aborter ->
        request(url, "GET", headers, contentType = null, auth = auth, aborter = aborter, writeBody = null)
    }

    override suspend fun download(url: String, target: File): Result<Unit> = try {
        cancellable { aborter -> perform(url, "GET", emptyMap(), contentType = null, streamed = false, aborter = aborter, writeBody = null, sink = target).result() }
            .map { }
            .onFailure { target.delete() }
    } catch (cancelled: CancellationException) {
        target.delete()
        throw cancelled
    }

    /**
     * Http::cancel(): a request whose coroutine is cancelled is aborted, its
     * connection closed under the read or write it blocks in — a login that
     * waits on a long answer, an upload — instead of running on unseen.
     */
    private suspend fun cancellable(work: (Aborter) -> Result<String>): Result<String> = coroutineScope {
        val aborter = Aborter()
        val running = async(Dispatchers.IO) { work(aborter) }
        try {
            running.await()
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
     * Http::on_progress: the file goes out in pieces, so the screen can say how
     * far it got, and it never sits in memory whole.
     */
    private fun copy(
        file: File,
        output: OutputStream,
        onProgress: ((sent: Long, total: Long) -> Unit)?,
        offset: Long = 0,
        length: Long = file.length(),
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
        /** Whether the body is a file, which is sent in pieces instead of buffered. */
        streamed: Boolean = false,
        auth: HttpAuth? = null,
        aborter: Aborter,
        writeBody: ((OutputStream) -> Unit)?,
    ): Result<String> {
        val answer = perform(url, method, headers, contentType, streamed, aborter, writeBody)
        // Http::auth_digest: a host that asks for digest is answered with the
        // second request that carries the computed response.
        val challenge = (answer as? Answer.Unauthorized)?.challenge
        if (auth != null && challenge != null && challenge.startsWith("Digest ", ignoreCase = true)) {
            val header = digestHeader(challenge, method, URL(url).path, auth)
                ?: return Result.failure(IOException("The host asked for an authorization the app cannot give"))
            return perform(url, method, headers + ("Authorization" to header), contentType, streamed, aborter, writeBody).result()
        }
        return answer.result()
    }

    private fun perform(
        url: String,
        method: String,
        headers: Map<String, String>,
        contentType: String?,
        streamed: Boolean,
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
            connection.readTimeout = readTimeoutMillis
            contentType?.let { connection.setRequestProperty("Content-Type", it) }
            headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            if (writeBody != null) {
                connection.doOutput = true
                // A G-code file is tens of megabytes: it goes out in pieces
                // instead of being held in memory whole.
                if (streamed) connection.setChunkedStreamingMode(UPLOAD_BUFFER)
                connection.outputStream.use(writeBody)
            }
            val code = connection.responseCode
            when {
                code in 200..299 && sink != null -> {
                    connection.inputStream.use { input -> sink.outputStream().use { input.copyTo(it) } }
                    Answer.Ok("")
                }
                code in 200..299 -> Answer.Ok(connection.inputStream.use { it.readBytes().decodeToString() })
                else -> {
                    val body = errorBody(connection)
                    val failure = HttpStatusException(code, body, "HTTP $code" + if (body.isBlank()) "" else ": ${body.take(MAX_ERROR_LENGTH)}")
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
        data class Ok(val body: String) : Answer

        data class Unauthorized(val challenge: String, val failure: HttpStatusException) : Answer

        data class Failed(val error: Throwable) : Answer

        fun result(): Result<String> = when (this) {
            is Ok -> Result.success(body)
            is Unauthorized -> Result.failure(failure)
            is Failed -> Result.failure(error)
        }
    }

    private companion object {
        const val MAX_ERROR_LENGTH = 200
        const val UPLOAD_BUFFER = 64 * 1024
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
