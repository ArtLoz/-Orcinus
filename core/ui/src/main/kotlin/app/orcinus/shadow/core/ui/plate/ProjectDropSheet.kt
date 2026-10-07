package app.orcinus.shadow.core.ui.plate

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.orcinus.shadow.core.designsystem.component.OrcaDialogWidth
import app.orcinus.shadow.core.designsystem.component.OrcaSheet
import app.orcinus.shadow.core.designsystem.component.orcaClickable
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import app.orcinus.shadow.core.model.ModelLoad
import app.orcinus.shadow.core.ui.orca.orcaString

/**
 * ProjectDropDialog: what a 3MF file picked for a plate with objects loads as.
 * The desktop dialog chooses with radio buttons and OK; a phone lists the two
 * actions in a sheet, a larger window in a small dialog (the dialog wraps the
 * file name at FromDIP(300)), and dismissing it is Cancel.
 */
@Composable
fun ProjectDropSheet(fileName: String, onChoose: (ModelLoad?) -> Unit) {
    val colors = OrcaTheme.colors
    OrcaSheet(onDismissRequest = { onChoose(null) }, dialogWidth = OrcaDialogWidth.Small) {
        Column(Modifier.navigationBarsPadding()) {
            Text(
                text = orcaString("Please select an action"),
                color = colors.textSide,
                style = OrcaTheme.typography.body14,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            // ORCA use file name on new line to create room for longer names
            Text(
                text = fileName,
                color = colors.text,
                style = OrcaTheme.typography.head14,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            )
            DropAction(orcaString("Open as project")) { onChoose(ModelLoad.PROJECT) }
            DropAction(orcaString("Import geometry only")) { onChoose(ModelLoad.GEOMETRY) }
        }
    }
}

@Composable
private fun DropAction(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        color = OrcaTheme.colors.text,
        style = OrcaTheme.typography.body14,
        modifier = Modifier
            .fillMaxWidth()
            .orcaClickable(role = Role.Button, onClick = onClick)
            .heightIn(min = OrcaTheme.dimensions.minimumTouchTarget)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    )
}
