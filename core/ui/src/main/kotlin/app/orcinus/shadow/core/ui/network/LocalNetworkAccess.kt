package app.orcinus.shadow.core.ui.network

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

/**
 * Android 17 asks the user before an app reaches a device of the local
 * network, which a printer on Wi-Fi is. The function this returns asks when
 * the app has not been allowed yet and runs [then] with whether the user
 * refused once they answered; when the app may, it runs [then] at once.
 */
@Composable
fun rememberLocalNetworkAccess(then: (denied: Boolean) -> Unit): () -> Unit {
    val context = LocalContext.current
    val onAnswer by rememberUpdatedState(then)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> onAnswer(!granted) }
    return {
        if (Build.VERSION.SDK_INT >= LOCAL_NETWORK_SDK &&
            context.checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) != PackageManager.PERMISSION_GRANTED
        ) {
            launcher.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
        } else {
            onAnswer(false)
        }
    }
}

/** Android 17, which guards the local network with ACCESS_LOCAL_NETWORK. */
private const val LOCAL_NETWORK_SDK = 37
