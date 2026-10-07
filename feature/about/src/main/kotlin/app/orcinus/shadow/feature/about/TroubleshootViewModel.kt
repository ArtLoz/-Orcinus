package app.orcinus.shadow.feature.about

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.orcinus.shadow.core.model.AppConfigKeys
import app.orcinus.shadow.core.model.ExternalDocumentReference
import app.orcinus.shadow.core.model.ProfilesOverview
import app.orcinus.shadow.domain.about.PackQuestion
import app.orcinus.shadow.domain.about.SystemInformation
import app.orcinus.shadow.domain.about.TroubleshootUseCase
import app.orcinus.shadow.domain.preferences.AppPreferences
import app.orcinus.shadow.domain.preferences.SetPreferenceUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A message box of the Troubleshoot Center that waits for the user. */
sealed interface TroubleshootPrompt {
    /** PackAll(): "The current project has unsaved changes, save it before continue?" */
    data object SaveChanges : TroubleshootPrompt

    /** PackAll(): "No project file on current session. Only logs will be included to package" */
    data object NoProject : TroubleshootPrompt

    /** RebuildSystemProfiles(): "Restart Required" and "Do you want to continue?" */
    data object CleanSystemProfiles : TroubleshootPrompt

    /** RebuildSystemProfiles(): "Failed to delete system folder..." */
    data object CleanFailed : TroubleshootPrompt

    /** ExportAsJson() and ExportAsZip(): "Export successful", or why not. */
    data class Exported(val written: Boolean) : TroubleshootPrompt
}

/** A file dialog the Troubleshoot Center waits to open, with the name it offers. */
data class TroubleshootPicker(val target: Target, val name: String) {
    enum class Target { PACK, PROFILES_OVERVIEW }
}

data class TroubleshootUiState(
    /** Null while it is gathered. */
    val system: SystemInformation? = null,
    /** "Report issue", with the system information; null without a public source. */
    val reportIssueUrl: String? = null,
    /** m_sys_panel_mode: every line of the system information, or its type alone. */
    val systemShown: Boolean = true,
    /** Null until the engine answers. */
    val profiles: ProfilesOverview? = null,
    val prompt: TroubleshootPrompt? = null,
    val picker: TroubleshootPicker? = null,
    /** A save or an export runs. */
    val busy: Boolean = false,
)

/** TroubleshootDialog: what it shows, and its buttons. */
class TroubleshootViewModel(
    private val troubleshoot: TroubleshootUseCase,
    preferences: AppPreferences,
    private val setPreference: SetPreferenceUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow(TroubleshootUiState())
    val state: StateFlow<TroubleshootUiState> = mutableState.asStateFlow()

    /** create_item_log_level_combo(): the level the Preferences keep; null until the engine has answered. */
    val logLevel: StateFlow<String?> = preferences.values
        .map { values -> if (values.isEmpty()) null else values[AppConfigKeys.LOG_SEVERITY_LEVEL].orEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        viewModelScope.launch {
            val system = troubleshoot.systemInformation()
            mutableState.update { it.copy(system = system, reportIssueUrl = troubleshoot.reportIssueUrl(system)) }
        }
        refreshProfiles()
    }

    /** The Hide and Show button. */
    fun toggleSystem() = mutableState.update { it.copy(systemShown = !it.systemShown) }

    /** The level's combo box writes the Preferences' value, which the engine logs with at once. */
    fun setLogLevel(level: String) = setPreference(AppConfigKeys.LOG_SEVERITY_LEVEL, level)

    /** "Pack...": PackAll()'s questions, then its file dialog. */
    fun pack() {
        when (troubleshoot.packQuestion()) {
            PackQuestion.NONE -> pick(TroubleshootPicker.Target.PACK)
            PackQuestion.SAVE_CHANGES -> mutableState.update { it.copy(prompt = TroubleshootPrompt.SaveChanges) }
            PackQuestion.NO_PROJECT -> mutableState.update { it.copy(prompt = TroubleshootPrompt.NoProject) }
        }
    }

    /** The save question's Yes saves the project before the pack goes on; No and Cancel leave it. */
    fun answerSaveChanges(save: Boolean) {
        mutableState.update { it.copy(prompt = null) }
        if (!save) return
        viewModelScope.launch {
            mutableState.update { it.copy(busy = true) }
            troubleshoot.saveProject()
            mutableState.update { it.copy(busy = false) }
            pick(TroubleshootPicker.Target.PACK)
        }
    }

    /** OK of "No project file on current session": the log is packed alone. */
    fun packWithoutProject() {
        mutableState.update { it.copy(prompt = null) }
        pick(TroubleshootPicker.Target.PACK)
    }

    /** "Export..." of "Loaded profiles overview". */
    fun exportProfilesOverview() = pick(TroubleshootPicker.Target.PROFILES_OVERVIEW)

    /** The file dialog has opened. */
    fun pickerShown() = mutableState.update { it.copy(picker = null) }

    /** The document the file dialog of [target] gave; null when it was cancelled. */
    fun picked(target: TroubleshootPicker.Target, document: ExternalDocumentReference?) {
        if (document == null) return
        viewModelScope.launch {
            mutableState.update { it.copy(busy = true) }
            val written = when (target) {
                TroubleshootPicker.Target.PACK -> troubleshoot.pack(document)
                TroubleshootPicker.Target.PROFILES_OVERVIEW -> troubleshoot.exportProfilesOverview(document)
            }
            mutableState.update { it.copy(busy = false, prompt = TroubleshootPrompt.Exported(written)) }
        }
    }

    /** "Clean": RebuildSystemProfiles()'s question first. */
    fun cleanSystemProfiles() = mutableState.update { it.copy(prompt = TroubleshootPrompt.CleanSystemProfiles) }

    fun answerCleanSystemProfiles(clean: Boolean) {
        mutableState.update { it.copy(prompt = null) }
        if (!clean) return
        viewModelScope.launch {
            if (troubleshoot.cleanSystemProfiles()) {
                refreshProfiles()
            } else {
                mutableState.update { it.copy(prompt = TroubleshootPrompt.CleanFailed) }
            }
        }
    }

    fun dismissPrompt() = mutableState.update { it.copy(prompt = null) }

    private fun pick(target: TroubleshootPicker.Target) {
        val name = when (target) {
            TroubleshootPicker.Target.PACK -> troubleshoot.packName()
            TroubleshootPicker.Target.PROFILES_OVERVIEW -> troubleshoot.profilesOverviewName
        }
        mutableState.update { it.copy(picker = TroubleshootPicker(target, name)) }
    }

    private fun refreshProfiles() {
        viewModelScope.launch {
            val profiles = troubleshoot.profilesOverview()
            mutableState.update { it.copy(profiles = profiles) }
        }
    }
}
