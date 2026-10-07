package app.orcinus.shadow

import android.Manifest
import android.annotation.SuppressLint
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.KeyboardShortcutGroup
import android.view.Menu
import android.view.View
import android.view.animation.PathInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.core.animation.doOnEnd
import androidx.core.content.IntentCompat
import androidx.core.splashscreen.SplashScreen
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.splashscreen.SplashScreenViewProvider
import androidx.lifecycle.lifecycleScope
import app.orcinus.shadow.core.designsystem.theme.OrcinusTheme
import app.orcinus.shadow.core.model.EngineAvailability
import app.orcinus.shadow.core.model.ExternalDocumentReference
import app.orcinus.shadow.core.ui.LocalToolpathsExport
import app.orcinus.shadow.core.ui.R as CoreUiR
import app.orcinus.shadow.di.AppContainer
import app.orcinus.shadow.core.ui.orca.LocalOrcaCatalog
import app.orcinus.shadow.core.ui.orca.OrcaCatalog
import app.orcinus.shadow.core.ui.orca.orcaLanguageOf
import app.orcinus.shadow.core.ui.orca.rememberOrcaCatalog
import app.orcinus.shadow.core.ui.plate.ProvideOrcaNotificationLabels
import app.orcinus.shadow.core.ui.shortcuts.KeyboardShortcuts
import app.orcinus.shadow.core.ui.shortcuts.LocalKeyboardShortcuts
import app.orcinus.shadow.domain.plate.EngineLanguage
import app.orcinus.shadow.domain.shortcuts.ResolveShortcutUseCase
import app.orcinus.shadow.ui.OrcinusApp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class MainActivity : ComponentActivity() {
    // Slicing continues in the background with a progress notification; the
    // job runs either way if the user declines.
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    /** The files handed over that the workspace has not taken yet. */
    private val openedDocuments = MutableStateFlow<List<ExternalDocumentReference>>(emptyList())

    /** OrcaSlicer's shortcuts, which the window hands every key to first, as MainFrame's wxEVT_CHAR_HOOK takes it. */
    private val shortcuts = KeyboardShortcuts(ResolveShortcutUseCase()::invoke)

    /** The catalogue the content shows OrcaSlicer's texts in, for Android's list of the shortcuts. */
    private var catalog = OrcaCatalog.EMPTY

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
        // A window built again keeps the files it took before.
        if (savedInstanceState == null) takeDocuments(intent)
        // A language chosen before Android 13 takes effect as the activity is built again.
        lifecycleScope.launch { container.appLanguage.changes.collect { recreate() } }
        // The engine's own messages follow the language the activity is built in.
        container.setEngineLanguage(
            EngineLanguage(orcaLanguageOf(resources.configuration.locales[0]).catalog, getString(CoreUiR.string.calibration_cube_name)),
        )
        setContent {
            OrcinusTheme {
                val orcaCatalog = rememberOrcaCatalog()
                SideEffect { catalog = orcaCatalog }
                // OrcaSlicer's own texts, such as its settings, come from its catalogue.
                CompositionLocalProvider(
                    LocalOrcaCatalog provides orcaCatalog,
                    // The preview offers the File menu its toolpaths while it shows them.
                    LocalToolpathsExport provides remember { mutableStateOf(null) },
                    LocalKeyboardShortcuts provides shortcuts,
                ) {
                    // The notifications' close button, "More" and minimize button in the app's language.
                    ProvideOrcaNotificationLabels {
                        OrcinusApp(
                            container = container,
                            onSliceRequested = ::requestNotificationPermission,
                            openedDocuments = openedDocuments,
                            onDocumentsTaken = { openedDocuments.value = emptyList() },
                        )
                    }
                }
            }
        }
    }

    /**
     * Every key goes to OrcaSlicer's shortcuts before the views, as the char
     * hook of MainFrame takes it before the focused control: a key a page
     * does goes no further, any other one on to the views. The window's own
     * dialogs take their keys themselves. A text field being edited
     * (onCheckIsTextEditor()) keeps the keys but MainFrame's.
     */
    // androidx.core's ComponentActivity restricts its own override of the platform's
    // Activity.dispatchKeyEvent(), which this one overrides as the platform documents.
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val press = event.toKeyPress()
        if (press != null && shortcuts.dispatch(press, typing = currentFocus?.onCheckIsTextEditor() == true)) return true
        return super.dispatchKeyEvent(event)
    }

    /** Android's list of the app's shortcuts (Meta+/): KBShortcutsDialog's pages. */
    override fun onProvideKeyboardShortcuts(data: MutableList<KeyboardShortcutGroup>, menu: Menu?, deviceId: Int) {
        super.onProvideKeyboardShortcuts(data, menu, deviceId)
        data += systemShortcutGroups(catalog)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        takeDocuments(intent)
    }

    /**
     * The files another app opens with the app (ACTION_VIEW) or shares with it
     * (ACTION_SEND, ACTION_SEND_MULTIPLE), which the workspace loads as the
     * desktop app loads files handed over to it (GUI_App::MacOpenFiles()).
     */
    private fun takeDocuments(intent: Intent?) {
        val uris = when (intent?.action) {
            Intent.ACTION_VIEW -> listOfNotNull(intent.data)
            Intent.ACTION_SEND -> listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
            Intent.ACTION_SEND_MULTIPLE -> IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            else -> emptyList()
        }
        if (uris.isNotEmpty()) openedDocuments.value = uris.map { ExternalDocumentReference(it.toString()) }
    }

    /**
     * The icon prints itself while OrcaSlicer loads its profiles in the slicer
     * process. The screen goes as soon as the engine has answered, and never
     * hangs on it: a slow start (the first run after an install reads every
     * profile) gives up waiting, since the app draws the last plate meanwhile.
     * GUI_App::on_init_inner() shows its splash screen only while the
     * Preferences' "Show splash screen" is on; OrcaSlicer.conf is read once
     * the engine runs, so the launch goes by the value read last.
     */
    private fun keepSplashWhileEngineStarts(splash: SplashScreen, container: AppContainer) {
        val launch = getSharedPreferences(LAUNCH_PREFERENCES, MODE_PRIVATE)
        lifecycleScope.launch {
            container.showSplashScreen.filterNotNull().collect { shown -> launch.edit().putBoolean(SHOW_SPLASH_SCREEN, shown).apply() }
        }
        if (!launch.getBoolean(SHOW_SPLASH_SCREEN, true)) return
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

        /** The launch's own preferences: the "Show splash screen" OrcaSlicer.conf held last. */
        const val LAUNCH_PREFERENCES = "launch"
        const val SHOW_SPLASH_SCREEN = "show_splash_screen"
    }
}
