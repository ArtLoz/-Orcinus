package app.orcinus.shadow.feature.about

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.orcinus.shadow.core.model.ComponentId
import app.orcinus.shadow.core.model.License
import app.orcinus.shadow.core.model.LicenseId
import app.orcinus.shadow.core.model.ThirdPartyComponent
import app.orcinus.shadow.domain.about.GetLicenseUseCase
import app.orcinus.shadow.domain.about.GetThirdPartyComponentUseCase
import app.orcinus.shadow.domain.about.GetThirdPartyComponentsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ThirdPartyUiState {
    data object Loading : ThirdPartyUiState

    data class Ready(val components: List<ThirdPartyComponent>) : ThirdPartyUiState
}

class ThirdPartyViewModel(getComponents: GetThirdPartyComponentsUseCase) : ViewModel() {
    private val mutableState = MutableStateFlow<ThirdPartyUiState>(ThirdPartyUiState.Loading)
    val state: StateFlow<ThirdPartyUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch { mutableState.value = ThirdPartyUiState.Ready(getComponents()) }
    }
}

/** A license as the notice page shows it: its text split into paragraphs that wrap to the screen. */
data class LicenseTextUi(
    val name: String,
    val url: String?,
    val paragraphs: List<String>,
)

sealed interface NoticeUiState {
    data object Loading : NoticeUiState

    data object Missing : NoticeUiState

    data class Ready(
        val title: String,
        val version: String? = null,
        val website: String? = null,
        val description: String? = null,
        val licenses: List<LicenseTextUi>,
    ) : NoticeUiState
}

/** The notice of one component or the text of one license. */
class NoticeViewModel private constructor(load: suspend () -> NoticeUiState.Ready?) : ViewModel() {
    private val mutableState = MutableStateFlow<NoticeUiState>(NoticeUiState.Loading)
    val state: StateFlow<NoticeUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            mutableState.value = withContext(Dispatchers.Default) { load() } ?: NoticeUiState.Missing
        }
    }

    companion object {
        fun forComponent(id: ComponentId, getComponent: GetThirdPartyComponentUseCase) = NoticeViewModel {
            getComponent(id)?.let { component ->
                NoticeUiState.Ready(
                    title = component.name,
                    version = component.version,
                    website = component.website,
                    description = component.description,
                    licenses = component.licenses.map(License::toUi),
                )
            }
        }

        fun forLicense(id: LicenseId, getLicense: GetLicenseUseCase) = NoticeViewModel {
            getLicense(id)?.let { license -> NoticeUiState.Ready(title = license.name, licenses = listOf(license.toUi())) }
        }
    }
}

private fun License.toUi() = LicenseTextUi(name = name, url = url, paragraphs = text?.let(::licenseParagraphs).orEmpty())

private val listItem = Regex("""^(?:[-*•]|\(?[0-9]{1,2}[.)]|\(?[a-zA-Z][.)]|\([ivx]{1,4}\))\s""")
private val alignedColumns = Regex("""\S {3,}\S""")
private val centred = Regex("""^ {8,}\S""")

/**
 * Splits a plain-text license into paragraphs that wrap to the screen width.
 * License files are hard-wrapped near 80 columns, which reads badly on a phone,
 * so lines of a paragraph are joined; list items keep their own lines, and a
 * paragraph laid out in columns or centred, such as a license title, keeps its
 * lines.
 */
internal fun licenseParagraphs(text: String): List<String> =
    text.replace("\r\n", "\n")
        .split(Regex("""\n[ \t]*\n"""))
        .map { paragraph -> paragraph.lines().map(String::trimEnd).filter(String::isNotBlank) }
        .filter { it.isNotEmpty() }
        .map { lines ->
            if (lines.any { alignedColumns.containsMatchIn(it.trim()) }) {
                lines.joinToString("\n")
            } else if (lines.all { centred.containsMatchIn(it) }) {
                lines.joinToString("\n") { it.trim() }
            } else {
                lines.map(String::trim).reduce { joined, line ->
                    if (listItem.containsMatchIn(line)) "$joined\n$line" else "$joined $line"
                }
            }
        }
