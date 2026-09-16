package app.orcinus.shadow.core.designsystem.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.orcinus.shadow.core.designsystem.R
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme

/**
 * OrcaSlicer's sidebar collapse button (collapse.svg) in the top-left corner of
 * the canvas.
 */
@Composable
fun OrcaSidebarToggle(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(36.dp)
            .shadow(2.dp, OrcaTheme.shapes.canvasPanel)
            .clip(OrcaTheme.shapes.canvasPanel)
            .background(OrcaTheme.colors.canvasPanel)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(R.drawable.orca_collapse), contentDescription = null, modifier = Modifier.size(OrcaTheme.dimensions.icon))
    }
}

/** A label and its value in [OrcaInfoPanel]. */
@Immutable
data class OrcaInfoItem(val label: String, val value: String)

/**
 * A panel docked to the bottom edge of the canvas, in the style of OrcaSlicer's
 * "Sliced Info" box: a title and label-value pairs in two columns. The
 * background runs under the system navigation bar; the content stays above it.
 */
@Composable
fun OrcaInfoPanel(
    title: String,
    items: List<OrcaInfoItem>,
    modifier: Modifier = Modifier,
) {
    val colors = OrcaTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(8.dp, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
            .background(colors.window)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(title, color = colors.text, style = OrcaTheme.typography.head14)
        HorizontalDivider(color = colors.separator, thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))
        items.chunked(2).forEach { pair ->
            Row(Modifier.fillMaxWidth()) {
                pair.forEach { item ->
                    Column(
                        Modifier
                            .weight(1f)
                            .padding(vertical = 4.dp),
                    ) {
                        Text(item.label, color = colors.textSide, style = OrcaTheme.typography.body12, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(item.value, color = colors.text, style = OrcaTheme.typography.head14, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (pair.size == 1) {
                    Box(Modifier.weight(1f))
                }
            }
        }
    }
}
