package app.orcinus.shadow

import android.content.Context
import android.os.Bundle
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * Firebase Analytics and Crashlytics of a build that has the Firebase project's
 * configuration (app/google-services.json). The manifest keeps both off; they
 * collect while Stealth mode is off, as Stealth mode stops every online request
 * of the app, and Firebase keeps the choice across starts and processes. A build
 * without the configuration has no Firebase app, and nothing is collected.
 */
class Telemetry private constructor(
    private val analytics: FirebaseAnalytics?,
    private val crashlytics: FirebaseCrashlytics?,
) {
    /** Collection follows [allowed]: on while true, off while false, unchanged while null (not read yet). */
    fun follow(allowed: Flow<Boolean?>, scope: CoroutineScope) {
        if (analytics == null && crashlytics == null) return
        scope.launch {
            allowed.filterNotNull().distinctUntilChanged().collect { on ->
                analytics?.setAnalyticsCollectionEnabled(on)
                crashlytics?.setCrashlyticsCollectionEnabled(on)
            }
        }
    }

    /** A page of the main window shown, by a name that stays the same in every language. */
    fun screen(name: String) {
        analytics?.logEvent(
            FirebaseAnalytics.Event.SCREEN_VIEW,
            Bundle().apply {
                putString(FirebaseAnalytics.Param.SCREEN_NAME, name)
                putString(FirebaseAnalytics.Param.SCREEN_CLASS, name)
            },
        )
    }

    companion object {
        /** The telemetry of the app's main process. */
        fun start(context: Context): Telemetry {
            if (firebaseApp(context) == null) return Telemetry(null, null)
            return Telemetry(FirebaseAnalytics.getInstance(context), FirebaseCrashlytics.getInstance())
        }

        /**
         * Firebase starts by itself in the main process alone; the :slicer
         * process, where the engine's native crashes happen, starts it here, so
         * Crashlytics reports them too.
         */
        fun startInSlicerProcess(context: Context) {
            firebaseApp(context)
        }

        private fun firebaseApp(context: Context): FirebaseApp? =
            FirebaseApp.getApps(context).firstOrNull() ?: FirebaseApp.initializeApp(context)
    }
}
