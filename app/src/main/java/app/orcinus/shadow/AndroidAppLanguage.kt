package app.orcinus.shadow

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import app.orcinus.shadow.domain.preferences.AppLanguage
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * The app's language as Android keeps it: on Android 13 and later its
 * per-app language (LocaleManager), which the system's settings show too and
 * which builds the activities again in it; before, a choice of the app's own,
 * which [wrap] gives the activity's context and [changes] tells the activity
 * to build itself again with.
 */
class AndroidAppLanguage(context: Context) : AppLanguage {
    private val context = context.applicationContext
    private val changed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** A language chosen before Android 13, which the activity takes by being built again. */
    val changes: SharedFlow<Unit> = changed.asSharedFlow()

    override fun select(tag: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(tag)
        } else {
            context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().putString(KEY, tag).apply()
            changed.tryEmit(Unit)
        }
    }

    companion object {
        private const val PREFERENCES = "app_language"
        private const val KEY = "language"

        private fun stored(context: Context): String? =
            context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getString(KEY, null)

        /** Before Android 13: [base] with the language chosen for the app, as AppCompat applies it. */
        fun wrap(base: Context): Context {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
            val tag = stored(base) ?: return base
            val configuration = Configuration(base.resources.configuration)
            configuration.setLocales(LocaleList.forLanguageTags(tag))
            return base.createConfigurationContext(configuration)
        }
    }
}
