package app.orcinus.shadow.network.printhost

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * The WebSocket exchanges a Creality printer answers on (CrealityPrint's
 * ws_send_and_read): connect, send messages in order, and, when [expect] is
 * given, wait for the first message that contains it. The exchange keeps the
 * uploader testable.
 */
interface WebSocketClient {
    suspend fun exchange(url: String, messages: List<String>, expect: String? = null): Result<String?>
}

/** OkHttp's WebSocket, with the timeouts the desktop app gives the printer. */
class OkHttpWebSocketClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        // The printer pushes its status between answers; the wait for the
        // answer is bounded below instead of every frame.
        .readTimeout(0, TimeUnit.SECONDS)
        .build(),
) : WebSocketClient {
    override suspend fun exchange(url: String, messages: List<String>, expect: String?): Result<String?> =
        withContext(Dispatchers.IO) {
            val opened = CompletableDeferred<Unit>()
            val answer = CompletableDeferred<String?>()
            val socket = client.newWebSocket(
                Request.Builder().url(url).build(),
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        opened.complete(Unit)
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        if (expect != null && text.contains(expect)) answer.complete(text)
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        opened.completeExceptionally(t)
                        // The K1 family closes the connection right after a
                        // command it accepted, so a failure after the messages
                        // went out is not an error of the command.
                        answer.complete(null)
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        answer.complete(null)
                    }
                },
            )
            try {
                withTimeout(CONNECT_TIMEOUT_SECONDS * MILLIS) { opened.await() }
                messages.forEach(socket::send)
                val received = if (expect == null) {
                    null
                } else {
                    try {
                        withTimeout(READ_TIMEOUT_SECONDS * MILLIS) { answer.await() }
                    } catch (_: TimeoutCancellationException) {
                        null
                    }
                }
                Result.success(received)
            } catch (error: TimeoutCancellationException) {
                Result.failure(java.io.IOException("The printer did not answer on $url"))
            } catch (error: Exception) {
                Result.failure(error)
            } finally {
                socket.close(NORMAL_CLOSURE, null)
            }
        }

    private companion object {
        /**
         * ws_connect() and ws_send_and_read(): five seconds to connect, and up
         * to twenty reads of three seconds for the answer among the status the
         * printer pushes.
         */
        const val CONNECT_TIMEOUT_SECONDS = 5L
        const val READ_TIMEOUT_SECONDS = 10L
        const val MILLIS = 1000L
        const val NORMAL_CLOSURE = 1000
    }
}
