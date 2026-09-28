package app.orcinus.shadow.feature.preferences

import androidx.lifecycle.ViewModel
import app.orcinus.shadow.core.model.AppConfigKeys
import app.orcinus.shadow.core.ui.orca.OrcaLanguage
import app.orcinus.shadow.domain.preferences.AppLanguage
import app.orcinus.shadow.domain.preferences.AppPreferences
import app.orcinus.shadow.domain.preferences.SetPreferenceUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * PreferencesDialog: the values of the app configuration, which an item writes
 * the moment it changes, as the desktop dialog's controls do.
 */
class PreferencesViewModel(
    preferences: AppPreferences,
    private val setPreference: SetPreferenceUseCase,
    private val appLanguage: AppLanguage,
) : ViewModel() {
    /** Empty until the engine has answered, which leaves the items disabled. */
    val values: StateFlow<Map<String, String>> = preferences.values

    private val failure = MutableStateFlow<String?>(null)

    /** Why the last value could not be written. */
    val problem: StateFlow<String?> = failure.asStateFlow()

    fun set(key: String, value: String) = setPreference(key, value) { failure.value = it }

    /**
     * The Language combo box's choice once confirmed: OrcaSlicer.conf keeps its
     * name, and the app is shown in it (GUI_App::switch_language()).
     */
    fun selectLanguage(language: OrcaLanguage) {
        setPreference(AppConfigKeys.LANGUAGE, language.canonicalName) { failure.value = it }
        appLanguage.select(language.tag)
    }

    fun dismissProblem() {
        failure.value = null
    }
}
