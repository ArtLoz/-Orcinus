package app.orcinus.shadow.feature.preferences

import androidx.lifecycle.ViewModel
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
) : ViewModel() {
    /** Empty until the engine has answered, which leaves the items disabled. */
    val values: StateFlow<Map<String, String>> = preferences.values

    private val failure = MutableStateFlow<String?>(null)

    /** Why the last value could not be written. */
    val problem: StateFlow<String?> = failure.asStateFlow()

    fun set(key: String, value: String) = setPreference(key, value) { failure.value = it }

    fun dismissProblem() {
        failure.value = null
    }
}
