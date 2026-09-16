package app.orcinus.shadow.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.orcinus.shadow.core.designsystem.R
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme

/** OrcaSlicer's G-code legend: a dark translucent panel over the preview canvas. */
@Composable
fun OrcaLegend(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .widthIn(max = 380.dp)
            .clip(OrcaTheme.shapes.canvasPanel)
            .background(OrcaTheme.colors.legend)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        content = content,
    )
}

/** Section title inside the legend (Line Type, Settings, Summary). */
@Composable
fun OrcaLegendTitle(text: String) {
    Text(text, color = OrcaTheme.colors.onLegend, style = OrcaTheme.typography.head13, modifier = Modifier.padding(top = 6.dp, bottom = 4.dp))
    HorizontalDivider(color = OrcaTheme.colors.onLegend.copy(alpha = 0.2f), thickness = 1.dp)
}

/** Label and value line of the legend. */
@Composable
fun OrcaLegendValue(label: String, value: String) {
    Row(Modifier.padding(vertical = 2.dp)) {
        Text(label, color = OrcaTheme.colors.onLegend.copy(alpha = 0.7f), style = OrcaTheme.typography.body12, modifier = Modifier.width(130.dp))
        Text(value, color = OrcaTheme.colors.onLegend, style = OrcaTheme.typography.body12, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * A feature line of the legend: colour, name, time, share, usage, and the
 * visibility toggle, as the Line Type table in OrcaSlicer's preview.
 */
@Composable
fun OrcaLegendItem(
    color: Color,
    name: String,
    time: String,
    percent: String,
    usage: String,
    visible: Boolean,
    onVisibleChange: ((Boolean) -> Unit)?,
) {
    val onLegend = OrcaTheme.colors.onLegend
    Row(
        modifier = Modifier
            .then(if (onVisibleChange != null) Modifier.toggleable(visible, role = Role.Checkbox, onValueChange = onVisibleChange) else Modifier)
            .padding(vertical = 3.dp)
            .alpha(if (visible) 1f else 0.5f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(10.dp)
                .background(color),
        )
        Text(name, color = onLegend, style = OrcaTheme.typography.body12, maxLines = 1, modifier = Modifier.padding(start = 8.dp).width(140.dp))
        Text(time, color = onLegend, style = OrcaTheme.typography.body12, maxLines = 1, modifier = Modifier.width(56.dp))
        Text(percent, color = onLegend, style = OrcaTheme.typography.body12, maxLines = 1, modifier = Modifier.width(36.dp))
        Text(usage, color = onLegend, style = OrcaTheme.typography.body12, maxLines = 1, modifier = Modifier.width(56.dp))
        if (onVisibleChange != null) {
            Icon(
                painter = painterResource(R.drawable.orca_im_visible),
                contentDescription = null,
                tint = if (visible) OrcaTheme.colors.accent else onLegend.copy(alpha = 0.5f),
                modifier = Modifier.size(OrcaTheme.dimensions.iconSmall),
            )
        }
    }
}
