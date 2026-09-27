package app.orcinus.shadow.feature.device

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Message
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import app.orcinus.shadow.core.ui.R as UiR

@Composable
internal fun DeviceRoute(viewModel: DeviceViewModel) {
    LaunchedEffect(viewModel) { viewModel.reload() }
    val state by viewModel.state.collectAsStateWithLifecycle()
    DeviceScreen(state)
}

/**
 * MainFrame's Device tab: for a BambuLab printer BambuLab's own monitor, which
 * needs BambuLab's network plugin the app does not have, and for every other
 * printer PrinterWebView, the page of its host — or OrcaSlicer's page that
 * asks for a connection while the printer preset has none.
 */
@Composable
internal fun DeviceScreen(state: DeviceUiState) {
    val colors = OrcaTheme.colors
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.window),
    ) {
        val connection = state.connection
        when {
            state.problem != null -> Notice(state.problem)
            connection == null -> CircularProgressIndicator(color = colors.accent, modifier = Modifier.align(Alignment.Center))
            connection.bambuDeviceTab -> Notice(stringResource(R.string.device_bambu_monitor))
            else -> LocalNetworkAccess(needed = connection.webUi.startsWith("http", ignoreCase = true)) { denied ->
                Column(Modifier.fillMaxSize()) {
                    if (denied) Notice(stringResource(UiR.string.printer_host_local_network))
                    PrinterWebView(connection.webUi, connection.apiKey)
                }
            }
        }
    }
}

@Composable
private fun Notice(text: String) {
    Text(
        text = text,
        color = OrcaTheme.colors.text,
        style = OrcaTheme.typography.body14,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    )
}

/**
 * Android 17 asks the user before an app reaches a device of the local
 * network, which a printer's page is; the page loads once the user answered.
 */
@Composable
private fun LocalNetworkAccess(needed: Boolean, content: @Composable (denied: Boolean) -> Unit) {
    val context = LocalContext.current
    val asked = needed && Build.VERSION.SDK_INT >= LOCAL_NETWORK_SDK &&
        context.checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) != PackageManager.PERMISSION_GRANTED
    var answered by rememberSaveable { mutableStateOf(!asked) }
    var denied by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        denied = !granted
        answered = true
    }
    LaunchedEffect(asked) {
        if (asked && !answered) launcher.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
    }
    if (answered) content(denied)
}

/**
 * PrinterWebView: the page in a browser of its own, with scripts and the
 * page's storage, as the printers' pages (Fluidd, Mainsail and the makers'
 * own) need. Its requests carry the host's API key (SendAPIKey()), a page
 * opening a window of its own opens in the browser (OnNewWindow()), and Back
 * goes back through the pages it went to.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun PrinterWebView(url: String, apiKey: String) {
    val context = LocalContext.current
    var webView by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    val documentStartScript = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
    BackHandler(enabled = canGoBack) { webView?.goBack() }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { viewContext ->
            WebView(viewContext).apply {
                // The page's 100vh is the view's height, which "wrap content" would leave at none.
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                // OrcaSlicer's page asking for a connection is a file of its resources.
                settings.allowFileAccess = true
                settings.setSupportMultipleWindows(true)
                // globalapi.js posts to window.wx, which PrinterWebView's script
                // message handler takes (and PrinterWebViewHandler leaves alone).
                addJavascriptInterface(ScriptMessages, "wx")
                webViewClient = object : WebViewClient() {
                    override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                        canGoBack = view.canGoBack()
                    }

                    override fun onPageFinished(view: WebView, url: String?) {
                        // Without scripts at the start of a document, the key is
                        // given to the page once it loaded, as SendAPIKey() adds it.
                        val key = view.tag as? String
                        if (!documentStartScript && !key.isNullOrEmpty()) view.evaluateJavascript(apiKeyScript(key), null)
                    }
                }
                webChromeClient = object : WebChromeClient() {
                    override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean {
                        val link = view.hitTestResult.extra
                        if (!link.isNullOrEmpty()) {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
                            } catch (_: ActivityNotFoundException) {
                                // No browser: the link is not opened, as wxLaunchDefaultBrowser fails.
                            }
                        }
                        return false
                    }
                }
                webView = this
            }
        },
        onRelease = WebView::destroy,
    )
    // load_url(): the page again, with the key its requests carry.
    DisposableEffect(webView, url, apiKey) {
        val view = webView
        var script: ScriptHandler? = null
        if (view != null) {
            view.tag = apiKey
            if (documentStartScript && apiKey.isNotEmpty()) {
                script = WebViewCompat.addDocumentStartJavaScript(view, apiKeyScript(apiKey), setOf("*"))
            }
            view.loadUrl(url)
        }
        onDispose { script?.remove() }
    }
}

/** PrinterWebView's script message handler "wx", which takes the pages' messages and does nothing with them. */
private object ScriptMessages {
    @JavascriptInterface
    fun postMessage(@Suppress("UNUSED_PARAMETER") message: String) = Unit
}

/** PrinterWebView::SendAPIKey(): every fetch() of the page carries X-API-Key. */
private fun apiKeyScript(apiKey: String): String {
    val key = apiKey.replace("\\", "\\\\").replace("'", "\\'")
    return """
        // Check if window.fetch exists before overriding
        if (window.fetch) {
            const originalFetch = window.fetch;
            window.fetch = function(input, init = {}) {
                init.headers = init.headers || {};
                init.headers['X-API-Key'] = '$key';
                return originalFetch(input, init);
            };
        }
    """.trimIndent()
}

private const val LOCAL_NETWORK_SDK = 37
