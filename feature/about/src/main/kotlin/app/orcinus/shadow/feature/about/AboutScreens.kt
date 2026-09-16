package app.orcinus.shadow.feature.about

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.orcinus.shadow.core.designsystem.component.OrcaLink
import app.orcinus.shadow.core.designsystem.component.OrcaListRow
import app.orcinus.shadow.core.designsystem.component.OrcaPageTopBar
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import app.orcinus.shadow.core.designsystem.theme.OrcinusTheme
import app.orcinus.shadow.core.model.AppInfo
import app.orcinus.shadow.core.model.ComponentId
import app.orcinus.shadow.core.model.LicenseId

/**
 * The About page, in the spirit of OrcaSlicer's About dialog. It also carries
 * the notices section 5 of the GNU AGPL asks an interactive program to show:
 * the copyright, the lack of warranty, the license and how to read it, and
 * where the source code is.
 */
@Composable
internal fun AboutScreen(
    appInfo: AppInfo,
    logo: @Composable () -> Unit,
    onOpenLicense: (LicenseId) -> Unit,
    onOpenThirdParty: () -> Unit,
    onBack: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    AboutPage(title = stringResource(R.string.about_title), onBack = onBack) {
        item {
            Column(
                modifier = Modifier
                    .pageContent()
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                logo()
                Text(appInfo.name, color = OrcaTheme.colors.text, style = OrcaTheme.typography.head20, modifier = Modifier.padding(top = 12.dp))
                Text(stringResource(R.string.app_version, appInfo.version), color = OrcaTheme.colors.textSide, style = OrcaTheme.typography.body14)
                Text(stringResource(R.string.app_engine, appInfo.orcaRelease), color = OrcaTheme.colors.textSide, style = OrcaTheme.typography.body14)
                Text(
                    text = stringResource(R.string.app_summary),
                    color = OrcaTheme.colors.text,
                    style = OrcaTheme.typography.body14,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }

        item { SectionTitle(stringResource(R.string.section_license)) }
        item { BodyText(stringResource(R.string.license_notice)) }
        item {
            OrcaListRow(title = stringResource(R.string.license_text), onClick = { onOpenLicense(appInfo.license) }, modifier = Modifier.pageContent())
        }
        appInfo.sourceUrl?.let { sourceUrl ->
            item {
                OrcaListRow(
                    title = stringResource(R.string.source_code),
                    details = sourceUrl,
                    onClick = { runCatching { uriHandler.openUri(sourceUrl) } },
                    modifier = Modifier.pageContent(),
                )
            }
        }

        item { SectionTitle(stringResource(R.string.section_credits)) }
        item { BodyText(stringResource(R.string.credits)) }
        item {
            OrcaListRow(
                title = stringResource(R.string.third_party_title),
                details = stringResource(R.string.third_party_summary),
                onClick = onOpenThirdParty,
                modifier = Modifier.pageContent(),
            )
        }
        item { BodyText(stringResource(R.string.disclaimer), secondary = true, modifier = Modifier.padding(top = 12.dp)) }
        item { BodyText(stringResource(R.string.app_copyright), secondary = true) }
    }
}

@Composable
internal fun ThirdPartyRoute(viewModel: ThirdPartyViewModel, onOpenComponent: (ComponentId) -> Unit, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ThirdPartyScreen(state, onOpenComponent, onBack)
}

@Composable
internal fun ThirdPartyScreen(
    state: ThirdPartyUiState,
    onOpenComponent: (ComponentId) -> Unit,
    onBack: () -> Unit,
) {
    AboutPage(title = stringResource(R.string.third_party_title), onBack = onBack) {
        when (state) {
            ThirdPartyUiState.Loading -> item { Loading() }
            is ThirdPartyUiState.Ready -> items(state.components, key = { it.id.value }) { component ->
                OrcaListRow(
                    title = component.name,
                    details = listOfNotNull(component.version, component.licenses.joinToString { it.name }.ifEmpty { null }).joinToString(" · "),
                    onClick = { onOpenComponent(component.id) },
                    modifier = Modifier.pageContent(),
                )
            }
        }
    }
}

@Composable
internal fun NoticeRoute(viewModel: NoticeViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    NoticeScreen(state, onBack)
}

/** A component's notice or a license, with the full license texts. */
@Composable
internal fun NoticeScreen(state: NoticeUiState, onBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    val title = (state as? NoticeUiState.Ready)?.title ?: stringResource(R.string.third_party_title)
    AboutPage(title = title, onBack = onBack) {
        when (state) {
            NoticeUiState.Loading -> item { Loading() }
            NoticeUiState.Missing -> item { BodyText(stringResource(R.string.notice_missing), secondary = true, modifier = Modifier.padding(top = 16.dp)) }
            is NoticeUiState.Ready -> {
                state.version?.let { version -> item { BodyText(stringResource(R.string.component_version, version), secondary = true, modifier = Modifier.padding(top = 12.dp)) } }
                state.description?.let { description -> item { BodyText(description) } }
                state.website?.let { website ->
                    item {
                        Box(Modifier.pageContent().padding(horizontal = 16.dp)) {
                            OrcaLink(website, onClick = { runCatching { uriHandler.openUri(website) } })
                        }
                    }
                }
                state.licenses.forEach { license ->
                    // A license page already carries the license name as its title.
                    if (license.name != state.title) {
                        item { SectionTitle(license.name) }
                    }
                    if (license.paragraphs.isEmpty()) {
                        license.url?.let { url ->
                            item { BodyText(stringResource(R.string.license_link_only, url)) }
                        }
                    }
                    items(license.paragraphs) { paragraph -> BodyText(paragraph, small = true) }
                }
            }
        }
    }
}

/** A page over the workspace: the title bar and a scrolling column that stays readable in a wide window. */
@Composable
private fun AboutPage(
    title: String,
    onBack: () -> Unit,
    content: LazyListScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(OrcaTheme.colors.window),
    ) {
        OrcaPageTopBar(title = title, backDescription = stringResource(R.string.back), onBack = onBack)
        val insets = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal).asPaddingValues()
        val layoutDirection = LocalLayoutDirection.current
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = insets.calculateStartPadding(layoutDirection),
                end = insets.calculateEndPadding(layoutDirection),
                bottom = insets.calculateBottomPadding() + 24.dp,
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content,
        )
    }
}

private fun Modifier.pageContent() = widthIn(max = 720.dp).fillMaxWidth()

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = OrcaTheme.colors.text,
        style = OrcaTheme.typography.head15,
        modifier = Modifier
            .pageContent()
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 4.dp)
            .semantics { heading() },
    )
}

@Composable
private fun BodyText(
    text: String,
    modifier: Modifier = Modifier,
    secondary: Boolean = false,
    small: Boolean = false,
) {
    Text(
        text = text,
        color = if (secondary) OrcaTheme.colors.textSide else OrcaTheme.colors.text,
        style = if (small || secondary) OrcaTheme.typography.body13 else OrcaTheme.typography.body14,
        modifier = modifier
            .pageContent()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

@Composable
private fun Loading() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = OrcaTheme.colors.accent)
    }
}

private val PreviewAppInfo = AppInfo(
    name = "Orcinus",
    version = "0.1.0",
    orcaRelease = "2.4.2",
    sourceUrl = "https://example.org/orcinus",
    license = LicenseId("AGPL-3.0-only"),
)

@Preview(name = "About", widthDp = 400, heightDp = 900)
@Preview(name = "About dark", widthDp = 400, heightDp = 900, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AboutPreview() = OrcinusTheme {
    AboutScreen(PreviewAppInfo, logo = {}, onOpenLicense = {}, onOpenThirdParty = {}, onBack = {})
}

@Preview(name = "Notice", widthDp = 400, heightDp = 700)
@Composable
private fun NoticePreview() = OrcinusTheme {
    NoticeScreen(
        NoticeUiState.Ready(
            title = "Qhull",
            version = "8.0.2",
            website = "http://www.qhull.org",
            licenses = listOf(LicenseTextUi("Qhull License", null, licenseParagraphs("Qhull, Copyright (c) 1993-2020\n\nThis software includes Qhull from C.B. Barber\nand The Geometry Center."))),
        ),
        onBack = {},
    )
}
