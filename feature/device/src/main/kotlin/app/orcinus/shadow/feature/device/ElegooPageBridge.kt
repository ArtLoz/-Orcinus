package app.orcinus.shadow.feature.device

import android.webkit.JavascriptInterface
import android.webkit.WebView
import app.orcinus.shadow.core.model.PrintHostUploadOutcome
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * OrcaSlicer's ElegooPrinterWebViewHandler: what Elegoo's LAN page asks of
 * the app over window.wx — open a link in the browser, pick a file, upload it
 * to the printer (stored only, with progress events), tell the printer's
 * serial number. Answers go to the page's HandleStudio(), or to
 * window.postMessage when it has none. A message without a method the handler
 * knows is left alone, as the desktop leaves it.
 */
internal class ElegooPageBridge(
    private val webView: () -> WebView?,
    private val scope: CoroutineScope,
    private val openUrl: (String) -> Unit,
    /** The system's file picker: the path of a copy of the picked file, or null when none was picked. */
    private val pickFile: (onPicked: (String?) -> Unit) -> Unit,
    private val serialNumber: suspend () -> String,
    private val upload: suspend (path: String, onProgress: (Float) -> Unit) -> PrintHostUploadOutcome,
) {
    private val uploading = AtomicBoolean(false)

    /** on_script_message() */
    @JavascriptInterface
    fun postMessage(message: String) {
        val root = runCatching { JSONObject(message) }.getOrNull() ?: return
        val id = root.optString("id")
        var method = root.optString("method")
        var params = root.optJSONObject("params") ?: JSONObject()
        if (method.isEmpty()) {
            method = root.optString("command")
            if (params.length() == 0) root.optJSONObject("data")?.let { params = it }
        }
        when (method) {
            "open", "common_openurl" -> {
                val url = params.optString("url").ifEmpty { root.optString("url") }
                if (url.isNotEmpty()) openUrl(url)
                if (id.isNotEmpty()) respond(id, method, 0, "success")
            }
            "upload_file" -> uploadFile(id, method, params)
            "open_file_dialog" -> pickFile { path ->
                val files = JSONArray()
                if (path != null) files.put(path)
                respond(id, method, 0, "success", JSONObject().put("files", files))
            }
            "get_sn" -> scope.launch {
                // The panel waits ten seconds at most: the known number only, no request.
                respond(id, method, 0, "success", JSONObject().put("sn", serialNumber()))
            }
        }
    }

    /** handle_upload_request(): one upload at a time, its progress as events, its end as the answer. */
    private fun uploadFile(id: String, method: String, params: JSONObject) {
        if (!uploading.compareAndSet(false, true)) {
            respond(id, method, 1, "Upload already in progress")
            return
        }
        val path = params.optString("filePath")
        if (path.isEmpty()) {
            uploading.set(false)
            respond(id, method, 1, "Missing filePath")
            return
        }
        val name = params.optString("fileName").ifEmpty { File(path).name }
        val total = File(path).length()
        scope.launch {
            val outcome = upload(path) { part ->
                val data = JSONObject().put("uploadedBytes", (part * total).toLong()).put("totalBytes", total)
                send("event", id, "upload_progress", 0, "", data)
            }
            uploading.set(false)
            when (outcome) {
                is PrintHostUploadOutcome.Success -> respond(
                    id,
                    method,
                    0,
                    "success",
                    JSONObject().put("success", true).put("filePath", path).put("fileName", name),
                )
                is PrintHostUploadOutcome.Failure -> respond(id, method, 1, outcome.message.ifEmpty { "Upload failed" })
            }
        }
    }

    private fun respond(id: String, method: String, code: Int, message: String, data: JSONObject = JSONObject()) =
        send("response", id, method, code, message, data)

    /** send_ipc_message() */
    private fun send(type: String, id: String, method: String, code: Int, message: String, data: JSONObject) {
        val body = JSONObject().put("type", type)
        if (id.isNotEmpty()) body.put("id", id)
        if (method.isNotEmpty()) body.put("method", method)
        body.put("data", data)
        if (type == "response") {
            body.put("code", code)
            body.put("message", message)
        }
        val payload = body.toString()
        val script = "if (typeof HandleStudio === 'function') { HandleStudio($payload); } else { window.postMessage($payload, '*'); }"
        webView()?.let { view -> view.post { view.evaluateJavascript(script, null) } }
    }
}
