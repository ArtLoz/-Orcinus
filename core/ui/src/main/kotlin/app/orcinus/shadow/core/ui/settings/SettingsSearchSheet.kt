package app.orcinus.shadow.core.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.orcinus.shadow.core.designsystem.component.OrcaPickerAnchor
import app.orcinus.shadow.core.designsystem.component.OrcaPickerSheet
import app.orcinus.shadow.core.designsystem.component.OrcaTextField
import app.orcinus.shadow.core.designsystem.component.orcaClickable
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import app.orcinus.shadow.core.model.OrcaText
import app.orcinus.shadow.core.model.PresetKind
import app.orcinus.shadow.core.model.SearchCatalogOutcome
import app.orcinus.shadow.core.model.SearchOption
import app.orcinus.shadow.core.model.SearchResult
import app.orcinus.shadow.core.model.SettingsMode
import app.orcinus.shadow.core.ui.R
import app.orcinus.shadow.core.ui.orca.LocalOrcaCatalog
import app.orcinus.shadow.core.ui.orca.orcaString
import app.orcinus.shadow.core.ui.orca.orcaText

/**
 * OrcaSlicer's settings search (Search::SearchDialog): what the user types is
 * matched against every setting of the process, filament and printer tabs, and
 * the best matches come first. Choosing one opens the setting on its tab, as
 * the desktop app's jump-to-option does.
 *
 * [mode] keeps the settings the tabs show in that mode, and [loadCatalog] asks
 * the engine for the settings once the sheet opens. The search button of a tab
 * ("Search in preset", Plater::search(false, m_type)) finds the settings of
 * its [kind] alone, and without a query lists only them.
 *
 * A phone shows it as a sheet; a larger window drops it down from the search
 * button of [anchor], as SearchDialog pops up under the sidebar's search, 41 em
 * wide (POPUP_WIDTH), or shows it as a dialog of that width without one.
 */
@Composable
fun SettingsSearchSheet(
    mode: SettingsMode,
    loadCatalog: suspend () -> SearchCatalogOutcome,
    onChoose: (SearchOption) -> Unit,
    onDismiss: () -> Unit,
    kind: PresetKind? = null,
    anchor: OrcaPickerAnchor? = null,
) {
    val colors = OrcaTheme.colors
    var query by rememberSaveable { mutableStateOf("") }
    var options by remember { mutableStateOf<List<SearchOption>?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        when (val outcome = loadCatalog()) {
            is SearchCatalogOutcome.Success -> options = outcome.options.filter { kind == null || it.kind == kind }
            is SearchCatalogOutcome.Failure -> problem = outcome.message
        }
    }
    val catalog = options
    // The texts are translated with the catalogue the app holds, which the
    // search matches the query against.
    val texts = LocalOrcaCatalog.current
    val results = if (catalog == null) emptyList() else SettingsSearch.search(query, catalog, mode, texts::format)
    OrcaPickerSheet(anchor = anchor, onDismissRequest = onDismiss, minPopupWidth = SEARCH_POPUP_WIDTH) {
        Column(Modifier.navigationBarsPadding()) {
            OrcaTextField(
                value = query,
                onValueChange = { query = it },
                enabled = catalog != null,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {}),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )
            problem?.let {
                Text(it, color = colors.error, style = OrcaTheme.typography.body13, modifier = Modifier.padding(horizontal = 16.dp))
            }
            when {
                catalog == null && problem == null ->
                    CircularProgressIndicator(color = colors.accent, modifier = Modifier.padding(16.dp))
                results.isEmpty() -> Text(
                    text = stringResource(R.string.settings_search_empty),
                    color = colors.textSide,
                    style = OrcaTheme.typography.body13,
                    modifier = Modifier.padding(16.dp),
                )
                else -> LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(results, key = { "${it.option.kind}:${it.option.id}" }) { result ->
                        SearchResultRow(result) { onChoose(result.option) }
                    }
                }
            }
        }
    }
}

/** One found setting: its "Group : Label", with the matched characters marked, over the page it is on. */
@Composable
private fun SearchResultRow(result: SearchResult, onClick: () -> Unit) {
    val colors = OrcaTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .orcaClickable(role = Role.Button, onClick = onClick)
            .heightIn(min = OrcaTheme.dimensions.minimumTouchTarget)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = buildAnnotatedString {
                append(result.text)
                // mark_string() of Search.cpp marks the characters the query matched.
                result.matches.forEach { at ->
                    if (at in result.text.indices) addStyle(SpanStyle(fontWeight = FontWeight.Bold, color = colors.accent), at, at + 1)
                }
            },
            color = colors.text,
            style = OrcaTheme.typography.body14,
        )
        Text(
            text = orcaString(result.option.kind.tabTitle) + SettingsSearch.SEPARATOR + orcaText(result.option.page),
            color = colors.textSide,
            style = OrcaTheme.typography.body12,
        )
    }
}

/** SearchDialog's POPUP_WIDTH: 41 em. */
private val SEARCH_POPUP_WIDTH = 410.dp
