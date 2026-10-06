package app.orcinus.shadow.network.printhost

import app.orcinus.shadow.core.model.ProfileCheckAnswer
import java.io.File

/**
 * The requests of OrcaSlicer's PresetUpdater::priv::sync_vendor_config(): the
 * check of a vendor's system profiles at check-version.orcaslicer.com and the
 * download of the bundle its answer names, each given 5 seconds to connect
 * (timeout_connect(5)).
 */
class ProfileUpdateClient(private val http: HttpClient = UrlConnectionHttpClient(connectTimeoutMillis = CONNECT_TIMEOUT_MILLIS)) {
    /** The answer to the check at [url]: its status and body, or the error that came instead (on_error). */
    suspend fun check(url: String): ProfileCheckAnswer = http.get(url, emptyMap()).fold(
        onSuccess = { body -> ProfileCheckAnswer(HTTP_OK, body) },
        onFailure = { error ->
            when (error) {
                is HttpStatusException -> ProfileCheckAnswer(error.status, error.body, error.message.orEmpty())
                else -> ProfileCheckAnswer(0, "", error.message ?: error.toString())
            }
        },
    )

    /** The bundle at [url] written to [target]; false when it could not be. */
    suspend fun download(url: String, target: File): Boolean = http.download(url, target).isSuccess

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 5_000
        const val HTTP_OK = 200
    }
}
