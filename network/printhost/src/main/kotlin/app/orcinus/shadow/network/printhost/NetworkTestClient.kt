package app.orcinus.shadow.network.printhost

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Connection
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/** What a page answered NetworkTestDialog's request with: [status] 0 and the [error] when none came. */
data class NetworkTestAnswer(val status: Int, val body: String, val error: String, val ip: String?)

/**
 * NetworkTestDialog::start_test_url()'s request: a GET with ten seconds to
 * connect (Http's DEFAULT_TIMEOUT_CONNECT) and for the whole of it
 * (timeout_max(10)), following redirects, from http to https as well, as
 * curl's FOLLOWLOCATION does; [NetworkTestAnswer.ip] is the address the last
 * connection went to (CURLINFO_PRIMARY_IP).
 */
class NetworkTestClient(private val client: OkHttpClient = OkHttpClient()) {
    suspend fun get(url: String): NetworkTestAnswer {
        var ip: String? = null
        val call = client.newBuilder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .eventListener(object : EventListener() {
                override fun connectionAcquired(call: Call, connection: Connection) {
                    ip = connection.socket().inetAddress?.hostAddress
                }
            })
            .build()
            .newCall(
                try {
                    Request.Builder().url(url).build()
                } catch (error: IllegalArgumentException) {
                    return NetworkTestAnswer(0, "", error.message.orEmpty(), null)
                },
            )
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resume(NetworkTestAnswer(0, "", e.message ?: e.toString(), ip))
                }

                override fun onResponse(call: Call, response: Response) {
                    val answer = response.use {
                        try {
                            NetworkTestAnswer(it.code, it.body?.string().orEmpty(), "", ip)
                        } catch (error: IOException) {
                            NetworkTestAnswer(0, "", error.message ?: error.toString(), ip)
                        }
                    }
                    continuation.resume(answer)
                }
            })
        }
    }

    private companion object {
        const val TIMEOUT_SECONDS = 10L
    }
}
