package app.orcinus.shadow.network.printhost

import java.net.ServerSocket
import kotlin.test.Test
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
            val client = UrlConnectionHttpClient(readTimeoutMillis = 60_000)
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
}
