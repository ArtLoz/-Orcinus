package app.orcinus.shadow.core.ui.preset

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.orcinus.shadow.core.designsystem.component.OrcaPickerAnchor
import app.orcinus.shadow.core.designsystem.component.OrcaPickerSheet
import app.orcinus.shadow.core.designsystem.component.OrcaTextField
import app.orcinus.shadow.core.designsystem.component.orcaClickable
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import app.orcinus.shadow.core.ui.R
import app.orcinus.shadow.core.ui.orca.orcaString

/** The length a list is searched from, as the preset list uses. */
private const val SEARCH_THRESHOLD = 12

/**
 * The drop-down list of a combo box that offers plain texts, such as the vendor
 * and the type of a filament the user creates: a bottom sheet on a phone, and
 * on a larger window the drop-down of the combo box of [anchor]. A long list
 * can be searched.
 */
@Composable
fun ChoiceListSheet(
    title: String,
    items: List<String>,
    onDismiss: () -> Unit,
    onChoose: (String) -> Unit,
    /** The combo box the list belongs to. */
    anchor: OrcaPickerAnchor? = null,
) {
    val colors = OrcaTheme.colors
    var search by rememberSaveable { mutableStateOf("") }
    val shown = remember(items, search) {
        if (search.isBlank()) items else items.filter { it.contains(search.trim(), ignoreCase = true) }
    }
    OrcaPickerSheet(
        anchor = anchor,
        onDismissRequest = onDismiss,
        title = title,
        skipPartiallyExpanded = items.size > SEARCH_THRESHOLD,
    ) {
        Column(Modifier.navigationBarsPadding()) {
            // A long list is searched, as the preset list of a combo box is.
            if (items.size > SEARCH_THRESHOLD) {
                OrcaTextField(
                    value = search,
                    onValueChange = { search = it },
                    hint = orcaString("Search"),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
            LazyColumn(Modifier.heightIn(max = 420.dp).weight(1f, fill = false)) {
                items(shown, key = { it }) { item ->
                    Text(
                        text = item,
                        color = colors.text,
                        style = OrcaTheme.typography.body14,
                        modifier = Modifier
                            .fillMaxWidth()
                            .orcaClickable(role = Role.Button) { onChoose(item) }
                            .heightIn(min = OrcaTheme.dimensions.minimumTouchTarget)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                    HorizontalDivider(color = colors.separator, thickness = 1.dp)
                }
            }
        }
    }
}
