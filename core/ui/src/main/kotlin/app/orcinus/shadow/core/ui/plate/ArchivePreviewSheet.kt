package app.orcinus.shadow.core.ui.plate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.orcinus.shadow.core.designsystem.component.OrcaButton
import app.orcinus.shadow.core.designsystem.component.OrcaButtonStyle
import app.orcinus.shadow.core.designsystem.component.OrcaCheckBox
import app.orcinus.shadow.core.designsystem.component.OrcaSheet
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import app.orcinus.shadow.core.model.ArchiveEntry
import app.orcinus.shadow.core.model.ArchivePreview
import app.orcinus.shadow.core.ui.orca.orcaString

/**
 * FileArchiveDialog ("Archive preview"): the archive's model and project
 * files under the folders they sit in, each with its check box, and a
 * folder's check box for every file under it; "All" and "None" pick every
 * file or none, "Open" loads those picked. A phone shows the tree as a list
 * in a sheet, a larger window as a dialog 45 em wide (FileArchiveDialog's
 * SetSize()), and dismissing it is Cancel.
 */
@Composable
fun ArchivePreviewSheet(preview: ArchivePreview, onAnswer: (List<ArchiveEntry>?) -> Unit) {
    val colors = OrcaTheme.colors
    var picked by remember(preview) { mutableStateOf(preview.picked.toSet()) }
    val folders = preview.entries.groupBy { it.path.substringBeforeLast('/', "") }
    OrcaSheet(onDismissRequest = { onAnswer(null) }, dialogWidth = ARCHIVE_DIALOG_WIDTH) {
        Column(Modifier.navigationBarsPadding()) {
            Text(
                text = orcaString("Archive preview"),
                color = colors.text,
                style = OrcaTheme.typography.head16,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            Text(
                text = preview.name,
                color = colors.textSide,
                style = OrcaTheme.typography.body13,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            )
            LazyColumn(Modifier.weight(1f, fill = false)) {
                folders.forEach { (folder, files) ->
                    if (folder.isNotEmpty()) {
                        item(key = "folder:$folder") {
                            // A folder's toggle sets every file under it.
                            val under = preview.entries.filter { it.path.startsWith("$folder/") }
                            val state = when {
                                under.all { it in picked } -> true
                                under.none { it in picked } -> false
                                else -> null
                            }
                            ArchiveRow(folder, checked = state, depth = 0, folder = true) { on ->
                                picked = if (on) picked + under else picked - under.toSet()
                            }
                        }
                    }
                    items(files, key = { "file:${it.path}:${it.size}" }) { entry ->
                        ArchiveRow(entry.path.substringAfterLast('/'), checked = entry in picked, depth = if (folder.isEmpty()) 0 else 1, folder = false) { on ->
                            picked = if (on) picked + entry else picked - entry
                        }
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                OrcaButton(orcaString("All"), onClick = { picked = preview.entries.toSet() }, style = OrcaButtonStyle.Regular)
                OrcaButton(orcaString("None"), onClick = { picked = emptySet() }, style = OrcaButtonStyle.Regular)
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                    // FileArchiveDialog::on_open_button(): the files in the archive's order.
                    OrcaButton(orcaString("Open"), onClick = { onAnswer(preview.entries.filter { it in picked }) })
                }
            }
        }
    }
}

@Composable
private fun ArchiveRow(name: String, checked: Boolean?, depth: Int, folder: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = OrcaTheme.dimensions.minimumTouchTarget)
            .padding(start = 8.dp + 24.dp * depth, end = 16.dp),
    ) {
        OrcaCheckBox(checked = checked, onCheckedChange = onCheckedChange)
        Text(
            text = name,
            color = if (folder) OrcaTheme.colors.textSide else OrcaTheme.colors.text,
            style = OrcaTheme.typography.body14,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** FileArchiveDialog's SetSize(): 45 em. */
private val ARCHIVE_DIALOG_WIDTH = 460.dp
