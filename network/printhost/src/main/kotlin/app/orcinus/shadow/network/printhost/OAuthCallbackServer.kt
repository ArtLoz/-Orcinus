package app.orcinus.shadow.network.printhost

import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URLDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * OrcaSlicer's HttpServer as OAuthJob runs it: a server on IPv4 (as the
 * desktop's acceptor, but on 127.0.0.1 alone, which the phone's own browser
 * comes back to) that waits for the browser to come back to its callback URL, and
 * answers it with what [handle] returns for the (URL-decoded) path — a
 * redirect to the host's page after a login, "404 Not Found" for anything
 * else. The desktop listens on its port for the whole login; so does this,
 * until [handle] says it is done or the coroutine is cancelled (the dialog's
 * Cancel).
 */
internal class OAuthCallbackServer(private val port: Int) {
    /** A response, and whether the login is over with it. */
    data class Reply(val redirect: String?, val done: Boolean)

    suspend fun serve(handle: suspend (url: String) -> Reply) = withContext(Dispatchers.IO) {
        ServerSocket(port, BACKLOG, InetAddress.getByName("127.0.0.1")).use { server ->
            server.soTimeout = POLL_MILLIS
            while (true) {
                ensureActive()
                val socket = try {
                    server.accept()
                } catch (_: SocketTimeoutException) {
                    continue
                }
                val done = socket.use { client -> answer(client, handle) }
                if (done) break
            }
        }
    }

    /** session::read_first_line() and read_next_line(): the request line and its headers, then the response. */
    private suspend fun answer(client: Socket, handle: suspend (url: String) -> Reply): Boolean {
        client.soTimeout = READ_MILLIS
        val reader = client.getInputStream().bufferedReader(Charsets.ISO_8859_1)
        val requestLine = try {
            reader.readLine()
        } catch (_: IOException) {
            null
        } ?: return false
        while (true) {
            val header = try {
                reader.readLine()
            } catch (_: IOException) {
                null
            } ?: break
            if (header.isEmpty()) break
        }
        val parts = requestLine.split(' ')
        // Ignore http OPTIONS
        if (parts.firstOrNull() == "OPTIONS" || parts.size < 2) return false
        val url = URLDecoder.decode(parts[1], Charsets.UTF_8)
        val reply = handle(url)
        client.getOutputStream().write(response(reply.redirect).toByteArray(Charsets.UTF_8))
        client.getOutputStream().flush()
        return reply.done
    }

    internal companion object {
        private const val BACKLOG = 4
        private const val POLL_MILLIS = 500
        private const val READ_MILLIS = 5_000

        /** ResponseRedirect::write_response() and ResponseNotFound::write_response(). */
        fun response(redirect: String?): String {
            if (redirect == null) {
                val html = "<html><body><h1>404 Not Found</h1><p>There's nothing here.</p></body></html>"
                return "HTTP/1.1 404 Not Found\ncontent-type: text/html\ncontent-length: ${html.length}\n\n$html"
            }
            val html = "<html><head><meta charset=\"utf-8\">" +
                "<meta http-equiv=\"refresh\" content=\"0;url=$redirect\">" +
                "<style>body{font-family:Arial,sans-serif;background:#f7f7f7;color:#222;margin:32px;}" +
                "a.button{display:inline-block;padding:10px 16px;margin-top:12px;background:#0f8bff;color:#fff;text-decoration:none;border-radius:6px;}" +
                "</style></head><body><div class=\"container\">" +
                "<h2>Authentication complete</h2>" +
                "<p>You can return to OrcaSlicer. If your browser does not redirect automatically, use the button below.</p>" +
                "<a class=\"button\" href=\"$redirect\">Continue</a>" +
                "<script>setTimeout(function(){try{window.close();}catch(e){}},1500);</script>" +
                "</div></body></html>"
            return "HTTP/1.1 302 Found\nLocation: $redirect\ncontent-type: text/html\ncontent-length: ${html.toByteArray(Charsets.UTF_8).size}\n\n$html"
        }

        /** url_get_param(): the value of [key] in [url], as the desktop reads it. */
        fun urlParam(url: String, key: String): String {
            var start = url.indexOf(key)
            if (start < 0) return ""
            val eq = url.indexOf('=', start)
            if (eq < 0) return ""
            if (url.substring(start, eq) != key) return ""
            start += key.length + 1
            val end = url.indexOf('&', start).takeIf { it >= 0 } ?: url.length
            return url.substring(start, end)
        }
    }
}
