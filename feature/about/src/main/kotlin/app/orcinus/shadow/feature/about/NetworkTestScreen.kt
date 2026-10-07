package app.orcinus.shadow.feature.about

import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.orcinus.shadow.core.designsystem.component.OrcaButton
import app.orcinus.shadow.core.designsystem.component.OrcaButtonStyle
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import app.orcinus.shadow.core.ui.orca.orcaString
import app.orcinus.shadow.domain.about.NetworkTest
import app.orcinus.shadow.domain.about.NetworkTestUseCase
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NetworkTestUiState(
    val version: String = "",
    /** Null while it is read. */
    val system: String? = null,
    /** The latest status of each test, which its line shows; none before it ran ("N/A"). */
    val results: Map<NetworkTest, String> = emptyMap(),
    /** "Log Info": every status with its time. */
    val log: String = "",
)

/**
 * NetworkTestDialog: its buttons start the requests, which go on while the
 * page shows; leaving it ends them, as closing the dialog waits for them.
 */
class NetworkTestViewModel(
    private val networkTest: NetworkTestUseCase,
    private val now: () -> LocalDateTime = LocalDateTime::now,
) : ViewModel() {
    private val mutableState = MutableStateFlow(NetworkTestUiState(version = networkTest.version))
    val state: StateFlow<NetworkTestUiState> = mutableState.asStateFlow()

    /** m_in_testing: the tests whose request runs. */
    private val testing = mutableSetOf<NetworkTest>()

    init {
        viewModelScope.launch {
            val system = networkTest.systemVersion()
            mutableState.update { it.copy(system = system) }
        }
    }

    /** "Start Test Multi-Thread" (start_all_job()): both requests at once. */
    fun startAll() {
        start(NetworkTest.ORCA)
        start(NetworkTest.BING)
    }

    /** "Start Test Single-Thread" (start_all_job_sequence()): Bing, then GitHub. */
    fun startSequence() {
        viewModelScope.launch {
            report(null, "start_test_sequence")
            run(NetworkTest.BING)
            run(NetworkTest.ORCA)
            report(null, "end_test_sequence")
        }
    }

    /**
     * "Test OrcaSlicer (GitHub)" (start_test_github_thread()), which waits for
     * a request of GitHub that runs, and "Test bing.com" (start_test_bing_thread()).
     */
    fun start(test: NetworkTest) {
        if (test == NetworkTest.ORCA && test in testing) return
        viewModelScope.launch { run(test) }
    }

    private suspend fun run(test: NetworkTest) {
        testing += test
        try {
            networkTest.run(test, ::report)
        } finally {
            testing -= test
        }
    }

    /** EVT_UPDATE_RESULT: the test's line shows the status, and the log gets it after the time. */
    private fun report(test: NetworkTest?, info: String) {
        val line = now().format(TIME) + ":" + info + "\n"
        mutableState.update { state ->
            state.copy(results = if (test == null) state.results else state.results + (test to info), log = state.log + line)
        }
    }

    private companion object {
        // std::put_time()'s "%a %b %d %H:%M:%S" in the C locale.
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss", Locale.US)
    }
}

/**
 * OrcaSlicer's Network Test (NetworkTestDialog) as a page: its start buttons,
 * the basic information, a line for each test with its own button, and the
 * log. Its "Export Log" and "DNS Server:", which the dialog hides, are left out.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NetworkTestRoute(viewModel: NetworkTestViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = OrcaTheme.colors
    val notAvailable = orcaString("N/A")
    AboutPage(title = orcaString("Network Test"), onBack = onBack) {
        item {
            FlowRow(
                modifier = Modifier
                    .pageContent()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OrcaButton(orcaString("Start Test Multi-Thread"), onClick = viewModel::startAll)
                OrcaButton(orcaString("Start Test Single-Thread"), onClick = viewModel::startSequence, style = OrcaButtonStyle.Regular)
            }
        }
        item { SectionTitle(orcaString("Basic Info")) }
        item { InfoLine(orcaString("OrcaSlicer Version:"), state.version) }
        item { InfoLine(orcaString("System Version:"), state.system ?: notAvailable) }
        // The dialog's grid: GitHub first, then Bing.
        item {
            TestLine(
                button = orcaString("Test OrcaSlicer (GitHub)"),
                title = orcaString("Test OrcaSlicer (GitHub):"),
                result = state.results[NetworkTest.ORCA] ?: notAvailable,
                onTest = { viewModel.start(NetworkTest.ORCA) },
            )
        }
        item {
            TestLine(
                button = orcaString("Test bing.com"),
                title = orcaString("Test bing.com:"),
                result = state.results[NetworkTest.BING] ?: notAvailable,
                onTest = { viewModel.start(NetworkTest.BING) },
            )
        }
        item { SectionTitle(orcaString("Log Info")) }
        item {
            // The text control the dialog appends to, scrolled to its end.
            val vertical = rememberScrollState()
            LaunchedEffect(state.log) { vertical.animateScrollTo(vertical.maxValue) }
            SelectionContainer(
                Modifier
                    .pageContent()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .heightIn(min = LOG_MIN_HEIGHT, max = LOG_MAX_HEIGHT)
                    .border(1.dp, colors.border, OrcaTheme.shapes.compact)
                    .verticalScroll(vertical)
                    .horizontalScroll(rememberScrollState()),
            ) {
                Text(
                    text = state.log,
                    color = colors.text,
                    style = OrcaTheme.typography.body12.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.padding(8.dp),
                )
            }
        }
    }
}

/** A line of "Basic Info": its title and its value. */
@Composable
private fun InfoLine(title: String, value: String) {
    Row(
        modifier = Modifier
            .pageContent()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, color = OrcaTheme.colors.textSide, style = OrcaTheme.typography.body14)
        Text(value, color = OrcaTheme.colors.text, style = OrcaTheme.typography.body14)
    }
}

/**
 * A test's line of the grid: its title over the latest status, and its button
 * under them, as the button's text is too long to share a phone's width.
 */
@Composable
private fun TestLine(button: String, title: String, result: String, onTest: () -> Unit) {
    Column(
        modifier = Modifier
            .pageContent()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Text(title, color = OrcaTheme.colors.text, style = OrcaTheme.typography.body15)
        Text(result, color = OrcaTheme.colors.textSide, style = OrcaTheme.typography.body13, modifier = Modifier.padding(top = 2.dp))
        OrcaButton(button, onClick = onTest, style = OrcaButtonStyle.Regular, modifier = Modifier.padding(top = 8.dp))
    }
}

private val LOG_MIN_HEIGHT = 160.dp
private val LOG_MAX_HEIGHT = 360.dp
