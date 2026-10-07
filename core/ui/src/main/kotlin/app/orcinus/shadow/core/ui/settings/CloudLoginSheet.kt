package app.orcinus.shadow.core.ui.settings

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.orcinus.shadow.core.designsystem.component.OrcaSheet
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import app.orcinus.shadow.core.ui.orca.orcaString
import org.json.JSONObject

/**
 * PrinterCloudAuthDialog ("Login"): the cloud host's login page in a browser
 * of its own. The page hands the token back over window.wx with the command
 * login_token, and any message it sends closes the dialog; [onClose] gets the
 * token, or an empty one when the page gave none or the sheet was closed —
 * the dialog's GetApiKey() then, which the desktop writes into the key field
 * either way. A phone shows it as a sheet, a larger window as the dialog,
 * FromDIP(650) wide.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun CloudLoginSheet(url: String, onClose: (token: String) -> Unit) {
    val colors = OrcaTheme.colors
    val close by rememberUpdatedState(onClose)
    OrcaSheet(onDismissRequest = { close("") }, dialogWidth = LOGIN_WIDTH, skipPartiallyExpanded = true) {
        Column(Modifier.navigationBarsPadding()) {
            Text(
                text = orcaString("Login"),
                color = colors.text,
                style = OrcaTheme.typography.head16,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(LOGIN_HEIGHT),
                factory = { context ->
                    WebView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        webViewClient = WebViewClient()
                        addJavascriptInterface(
                            object {
                                /** OnScriptMessage() */
                                @JavascriptInterface
                                fun postMessage(message: String) {
                                    val token = runCatching {
                                        val root = JSONObject(message)
                                        if (root.optString("command") == "login_token") root.getJSONObject("data").optString("token") else ""
                                    }.getOrDefault("")
                                    post { close(token) }
                                }
                            },
                            "wx",
                        )
                        loadUrl(url)
                    }
                },
                onRelease = WebView::destroy,
            )
        }
    }
}

/** PrinterCloudAuthDialog: FromDIP(wxSize(650, 840)); the height near it on a phone's sheet. */
private val LOGIN_WIDTH = 650.dp
private val LOGIN_HEIGHT = 560.dp
