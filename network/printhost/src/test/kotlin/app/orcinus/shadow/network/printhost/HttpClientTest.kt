package app.orcinus.shadow.network.printhost

import app.orcinus.shadow.core.model.OrcaText
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.util.Collections
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class HttpClientTest {
    @Test
    fun `a cancelled request is aborted instead of waiting for the answer`() {
        // A server that takes the request and keeps it, as 3DPrinterOS's login_with_token does.
        ServerSocket(0).use { server ->
            val held = Thread {
                runCatching { server.accept().use { Thread.sleep(10_000) } }
            }.also { it.start() }
            val client = UrlConnectionHttpClient()
            val finished = runBlocking {
                val request = async { client.sendBytes("http://127.0.0.1:${server.localPort}/wait", "POST", emptyMap(), "token=t".toByteArray()) }
                delay(300)
                val started = System.nanoTime()
                // The cancel waits for the request's thread, which the closed connection frees at once.
                withTimeout(5_000) {
                    request.cancel()
                    request.join()
                }
                System.nanoTime() - started
            }
            assertTrue(finished < 5_000_000_000L)
            held.interrupt()
        }
    }

    @Test
    fun `a file goes out with its length, not in chunks, as curl sends it`() {
        val requests = serve { exchange -> exchange.answer(200, "{}") }
        val file = gcode(200_000)
        try {
            val client = UrlConnectionHttpClient()
            runBlocking {
                client.postMultipart(requests.url("api/files/local"), emptyMap(), mapOf("print" to "false", "path" to ""), "file", "plate.gcode", file).getOrThrow()
                client.sendFile(requests.url("api/v1/files/local/plate.gcode"), "PUT", emptyMap(), file).getOrThrow()
            }
            val (form, put) = requests.received
            // The form: its whole size known in advance, the file whole inside it.
            assertEquals(null, form.headers["Transfer-encoding"])
            assertEquals(form.body.size.toString(), form.headers["Content-length"])
            assertTrue(form.body.decodeToString().contains(file.readText()))
            // set_put_body(): CURLOPT_INFILESIZE, the file's size.
            assertEquals(null, put.headers["Transfer-encoding"])
            assertEquals(file.length().toString(), put.headers["Content-length"])
            assertTrue(put.body.contentEquals(file.readBytes()))
        } finally {
            requests.stop()
            file.delete()
        }
    }

    @Test
    fun `a digest login probes with an empty body before the file goes, as curl does`() {
        val requests = serve { exchange ->
            if (exchange.requestHeaders.getFirst("Authorization") == null) {
                exchange.responseHeaders.add("WWW-Authenticate", """Digest realm="Printer API", nonce="abc123", qop="auth"""")
                exchange.answer(401, "")
            } else {
                exchange.answer(201, "")
            }
        }
        val file = gcode(100_000)
        try {
            val answer = runBlocking {
                UrlConnectionHttpClient().sendFileAnswer(requests.url("api/v1/files/usb/plate.gcode"), "PUT", emptyMap(), file, auth = HttpAuth("maker", "secret"))
            }.getOrThrow()

            assertEquals(201, answer.status)
            val (probe, upload) = requests.received
            assertEquals(0, probe.body.size)
            assertEquals(null, probe.headers["Authorization"])
            assertTrue(upload.headers["Authorization"].orEmpty().startsWith("Digest username=\"maker\""))
            assertTrue(upload.headers["Authorization"].orEmpty().contains("uri=\"/api/v1/files/usb/plate.gcode\""))
            assertEquals(file.length().toInt(), upload.body.size)
        } finally {
            requests.stop()
            file.delete()
        }
    }

    @Test
    fun `a host that asks for no login is sent the whole request after the probe`() {
        val requests = serve { exchange -> exchange.answer(200, "ok") }
        val file = gcode(1_000)
        try {
            runBlocking { UrlConnectionHttpClient().sendFile(requests.url("upload"), "PUT", emptyMap(), file, auth = HttpAuth("maker", "secret")) }.getOrThrow()
            assertEquals(listOf(0, 1_000), requests.received.map { it.body.size })
        } finally {
            requests.stop()
            file.delete()
        }
    }

    @Test
    fun `an answer that is not a success is format_error's status and body`() {
        val requests = serve { exchange -> exchange.answer(409, "File exists") }
        try {
            val failure = runBlocking { UrlConnectionHttpClient().get(requests.url("api/version"), emptyMap()) }.exceptionOrNull()
            assertEquals("HTTP 409: File exists", failure?.message)
            assertEquals(listOf(OrcaText("HTTP 409: File exists")), formatError(failure!!))
        } finally {
            requests.stop()
        }
    }

    @Test
    fun `a request that outlasts timeout_max is aborted with curl's timeout`() {
        // A host that takes the request and never answers.
        ServerSocket(0).use { server ->
            val held = Thread {
                runCatching { server.accept().use { Thread.sleep(10_000) } }
            }.also { it.start() }
            val started = System.nanoTime()
            val failure = runBlocking {
                UrlConnectionHttpClient().withTimeouts(UrlConnectionHttpClient.DEFAULT_TIMEOUT_CONNECT_MILLIS, 500)
                    .get("http://127.0.0.1:${server.localPort}/info", emptyMap())
            }.exceptionOrNull()
            assertTrue(System.nanoTime() - started < 5_000_000_000L)
            assertTrue(failure is SocketTimeoutException)
            assertEquals(listOf(OrcaText(TIMED_OUT)), formatError(failure))
            held.interrupt()
        }
    }

    /** A request the local server took: its headers (as the server names them) and its body. */
    private class Received(val headers: Map<String, String>, val body: ByteArray)

    private class Server(private val server: HttpServer, val received: List<Received>) {
        fun url(path: String) = "http://127.0.0.1:${server.address.port}/$path"

        fun stop() = server.stop(0)
    }

    /** An HTTP server on this machine whose every request [handle] answers. */
    private fun serve(handle: (HttpExchange) -> Unit): Server {
        val received = Collections.synchronizedList(mutableListOf<Received>())
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val body = exchange.requestBody.readBytes()
            received += Received(exchange.requestHeaders.mapValues { it.value.first() }, body)
            handle(exchange)
        }
        server.start()
        return Server(server, received)
    }

    private fun HttpExchange.answer(status: Int, body: String) {
        val bytes = body.toByteArray()
        sendResponseHeaders(status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
        if (bytes.isNotEmpty()) responseBody.use { it.write(bytes) } else close()
    }

    private fun gcode(size: Int): File = File.createTempFile("orcinus", ".gcode").also { file ->
        file.writeText(buildString { while (length < size) append("G1 X").append(length % 200).append(" Y0\n") }.take(size))
    }
}
