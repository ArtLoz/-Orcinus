package app.orcinus.shadow

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.animation.PathInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.animation.doOnEnd
import androidx.core.splashscreen.SplashScreen
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.splashscreen.SplashScreenViewProvider
import androidx.lifecycle.lifecycleScope
import app.orcinus.shadow.core.designsystem.theme.OrcinusTheme
import app.orcinus.shadow.core.model.EngineAvailability
import app.orcinus.shadow.di.AppContainer
import app.orcinus.shadow.core.ui.orca.LocalOrcaCatalog
import app.orcinus.shadow.core.ui.orca.orcaLanguageOf
import app.orcinus.shadow.core.ui.orca.rememberOrcaCatalog
import app.orcinus.shadow.ui.OrcinusApp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class MainActivity : ComponentActivity() {
    // Slicing continues in the background with a progress notification; the
    // job runs either way if the user declines.
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    // Before Android 13 the app's language is the activity's own configuration.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AndroidAppLanguage.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // The launch screen is installed before the content, and stays while
        // the engine starts in its own process.
        val splash = installSplashScreen()
        // Transparent system bars: light status icons over OrcaSlicer's dark tab
        // bar, navigation icons that follow the theme over the canvas.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        val container = (application as OrcinusApplication).container
        keepSplashWhileEngineStarts(splash, container)
        // A language chosen before Android 13 takes effect as the activity is built again.
        lifecycleScope.launch { container.appLanguage.changes.collect { recreate() } }
        // The engine's own messages follow the language the activity is built in.
        container.setEngineLanguage(orcaLanguageOf(resources.configuration.locales[0]).catalog)
        setContent {
            OrcinusTheme {
                // OrcaSlicer's own texts, such as its settings, come from its catalogue.
                CompositionLocalProvider(LocalOrcaCatalog provides rememberOrcaCatalog()) {
                    OrcinusApp(container = container, onSliceRequested = ::requestNotificationPermission)
                }
            }
        }
    }

    /**
     * The icon prints itself while OrcaSlicer loads its profiles in the slicer
     * process. The screen goes as soon as the engine has answered, and never
     * hangs on it: a slow start (the first run after an install reads every
     * profile) gives up waiting, since the app draws the last plate meanwhile.
     */
    private fun keepSplashWhileEngineStarts(splash: SplashScreen, container: AppContainer) {
        val shown = SystemClock.uptimeMillis()
        var ready = false
        lifecycleScope.launch {
            withTimeoutOrNull(SPLASH_LIMIT_MILLIS) {
                container.observePlate().first { it.engine.availability != EngineAvailability.STARTING }
            }
            // The printing is not cut in half when the engine is quick.
            val left = SPLASH_ICON_MILLIS - (SystemClock.uptimeMillis() - shown)
            if (left > 0) delay(left)
            ready = true
        }
        splash.setKeepOnScreenCondition { !ready }
        splash.setOnExitAnimationListener(::animateSplashAway)
    }

    /** The icon grows a little and the screen fades into the app behind it. */
    private fun animateSplashAway(splash: SplashScreenViewProvider) {
        val view = splash.view
        val icon = splash.iconView
        val easing = PathInterpolator(0.4f, 0f, 1f, 1f)
        val animation = AnimatorSet().apply {
            duration = SPLASH_EXIT_MILLIS
            interpolator = easing
            playTogether(
                ObjectAnimator.ofFloat(icon, View.SCALE_X, 1f, SPLASH_EXIT_SCALE),
                ObjectAnimator.ofFloat(icon, View.SCALE_Y, 1f, SPLASH_EXIT_SCALE),
                ObjectAnimator.ofFloat(view, View.ALPHA, 1f, 0f),
            )
            doOnEnd { splash.remove() }
        }
        animation.start()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private companion object {
        /** windowSplashScreenAnimationDuration: how long the icon prints itself. */
        const val SPLASH_ICON_MILLIS = 800L

        /** Waiting longer than this shows the app with the plate of the last run. */
        const val SPLASH_LIMIT_MILLIS = 2_500L

        const val SPLASH_EXIT_MILLIS = 220L
        const val SPLASH_EXIT_SCALE = 1.12f
    }
}
