package app.orcinus.shadow.domain.about

import app.orcinus.shadow.core.model.AppInfo

/** Port: NetworkTestDialog's request of a page (Http::get() with its callbacks). */
fun interface NetworkProbe {
    suspend fun get(url: String): ProbeAnswer
}

/**
 * What the page answered: its HTTP [status] and [body], or [status] 0 with
 * the [error] when the request failed; [ip] is the address it connected to.
 */
data class ProbeAnswer(val status: Int, val body: String = "", val error: String = "", val ip: String? = null)

/** NetworkTestDialog's jobs: the name its statuses give a test, and the page it requests. */
enum class NetworkTest(val title: String, val url: String) {
    /** TEST_ORCA_JOB */
    ORCA("OrcaSlicer(GitHub)", "https://github.com/OrcaSlicer/OrcaSlicer"),

    /** TEST_BING_JOB */
    BING("Bing", "http://www.bing.com"),
}

/**
 * OrcaSlicer's Network Test (NetworkTestDialog), which Help's "Open Network
 * Test" and the Preferences' "Network test" open: the app's version and the
 * system's, and the requests of GitHub and Bing the user starts, whose
 * statuses come as the dialog words them, untranslated.
 */
class NetworkTestUseCase(
    private val appInfo: AppInfo,
    private val device: DeviceInformation,
    private val probe: NetworkProbe,
) {
    /** get_studio_version(): the OrcaSlicer release the engine is. */
    val version: String get() = appInfo.orcaRelease

    /** get_os_info() of the phone. */
    suspend fun systemVersion(): String = device.describe().let { "Android ${it.androidRelease} (API ${it.apiLevel})" }

    /**
     * start_test_url(): [report] hears every status update_status() gives,
     * of the [test] (whose line shows the latest) or of the log alone (null).
     */
    suspend fun run(test: NetworkTest, report: (NetworkTest?, String) -> Unit) {
        report(test, "test ${test.title} start...")
        report(null, "[test ${test.title}]: url=${test.url}")
        val answer = probe.get(test.url)
        // Http::priv::http_perform(): a success completes and tells its address, an error or a status from 400 fails.
        when {
            answer.status in SUCCESS -> {
                answer.ip?.let { report(test, "test ${test.title} ip resolved = $it") }
                if (answer.status == HTTP_OK) report(test, "test ${test.title} ok")
            }
            answer.status == 0 || answer.status >= HTTP_ERROR -> {
                report(test, "test ${test.title} failed")
                report(null, "status=${answer.status}, body=${answer.body}, error=${answer.error}")
            }
        }
    }

    private companion object {
        val SUCCESS = 200..299
        const val HTTP_OK = 200
        const val HTTP_ERROR = 400
    }
}
