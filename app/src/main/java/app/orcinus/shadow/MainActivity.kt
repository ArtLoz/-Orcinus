package app.orcinus.shadow

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import app.orcinus.shadow.core.designsystem.theme.OrcinusTheme
import app.orcinus.shadow.ui.OrcinusApp

class MainActivity : ComponentActivity() {
    // Slicing continues in the background with a progress notification; the
    // job runs either way if the user declines.
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        // Transparent system bars: light status icons over OrcaSlicer's dark tab
        // bar, navigation icons that follow the theme over the canvas.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        val container = (application as OrcinusApplication).container
        setContent {
            OrcinusTheme {
                OrcinusApp(container = container, onSliceRequested = ::requestNotificationPermission)
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
