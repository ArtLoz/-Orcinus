package app.orcinus.shadow.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.orcinus.shadow.core.designsystem.R
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme

private val SheetPadding = 16.dp
private val ChipShape = RoundedCornerShape(16.dp)

/** The small bar at the top of a bottom sheet that shows it can be dragged. */
@Composable
fun OrcaSheetHandle(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(top = 10.dp, bottom = 6.dp)
            .size(width = 36.dp, height = 4.dp)
            .clip(CircleShape)
            .background(OrcaTheme.colors.textSide.copy(alpha = 0.4f)),
    )
}

/** A figure of the preview's summary: an icon, the value, and what it is. */
data class OrcaSummaryItem(
    @param:DrawableRes val icon: Int,
    val value: String,
    val caption: String,
)

/** The preview's key figures side by side, as the collapsed bottom sheet shows them. */
@Composable
fun OrcaSummaryRow(items: List<OrcaSummaryItem>, modifier: Modifier = Modifier) {
    val colors = OrcaTheme.colors
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SheetPadding),
    ) {
        // A narrow panel (the legend over a tablet's canvas) keeps the figures
        // whole: the icons give way before the numbers do.
        val showIcons = items.isEmpty() || maxWidth / items.size >= SummaryItemWithIconMinWidth
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items.forEach { item ->
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    if (showIcons) {
                        Box(
                            Modifier
                                .padding(end = 8.dp)
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(colors.accentSubtle),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(painterResource(item.icon), contentDescription = null, tint = colors.textSide, modifier = Modifier.size(18.dp))
                        }
                    }
                    Column {
                        Text(item.value, color = colors.text, style = OrcaTheme.typography.head14, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(item.caption, color = colors.textSide, style = OrcaTheme.typography.body11, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/** The width a figure needs beside its icon to show a time like 10m54s whole. */
private val SummaryItemWithIconMinWidth = 112.dp

/** Choices as a row of chips that scrolls sideways, the selected one filled with the accent. */
@Composable
fun OrcaChoiceChips(
    items: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OrcaTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = SheetPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEachIndexed { index, item ->
            val isSelected = index == selected
            Box(
                modifier = Modifier
                    .height(32.dp)
                    .clip(ChipShape)
                    .background(if (isSelected) colors.accent else Color.Transparent)
                    .border(1.dp, if (isSelected) colors.accent else colors.border, ChipShape)
                    .clickable(role = Role.RadioButton) { onSelect(index) }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(item, color = if (isSelected) colors.onAccent else colors.text, style = OrcaTheme.typography.body13, maxLines = 1)
            }
        }
    }
}

/** A titled group of the legend. */
@Composable
fun OrcaLegendSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        Text(
            text = title,
            color = OrcaTheme.colors.textSide,
            style = OrcaTheme.typography.head12,
            modifier = Modifier.padding(start = SheetPadding, end = SheetPadding, top = 16.dp, bottom = 4.dp),
        )
        content()
    }
}

/**
 * A line of the legend: what [color] stands for, its [detail] (time and
 * share), a bar of its [share] of the print, the [usage] at the end, and, when
 * [visible] is set, the eye that shows or hides it; a tap on the line does too.
 */
@Composable
fun OrcaLegendItem(
    color: Color,
    title: String,
    detail: String?,
    share: Float?,
    usage: String?,
    usageDetail: String?,
    visible: Boolean?,
    onToggle: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = OrcaTheme.colors
    val shown = visible != false
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onToggle != null) Modifier.clickable(role = Role.Checkbox, onClick = onToggle) else Modifier)
            .padding(horizontal = SheetPadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(if (shown) color else color.copy(alpha = 0.3f)),
        )
        Column(
            Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(title, color = if (shown) colors.text else colors.textSide, style = OrcaTheme.typography.body14, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (share != null) {
                Box(
                    Modifier
                        .padding(top = 4.dp, bottom = 2.dp)
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(colors.border.copy(alpha = 0.6f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(share.coerceIn(0f, 1f))
                            .height(3.dp)
                            .clip(CircleShape)
                            .background(if (shown) color else color.copy(alpha = 0.3f)),
                    )
                }
            }
            if (detail != null) {
                Text(detail, color = colors.textSide, style = OrcaTheme.typography.body12, maxLines = 1)
            }
        }
        if (usage != null || usageDetail != null) {
            Column(Modifier.padding(start = 12.dp), horizontalAlignment = Alignment.End) {
                usage?.let { Text(it, color = colors.text, style = OrcaTheme.typography.body13, maxLines = 1) }
                usageDetail?.let { Text(it, color = colors.textSide, style = OrcaTheme.typography.body12, maxLines = 1) }
            }
        }
        if (visible != null) {
            Icon(
                painter = painterResource(if (visible) R.drawable.orca_im_visible else R.drawable.orca_im_hidden),
                contentDescription = null,
                // OrcaSlicer's eye keeps its own ORCA colour; the closed one is dimmed.
                tint = if (visible) Color.Unspecified else colors.textSide,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .size(20.dp),
            )
        }
    }
}

/**
 * A colour range step by step, as the desktop legend lists it: each step's
 * colour beside its value, in the given order. The swatches stand one on
 * another as a bar.
 */
@Composable
fun OrcaColorSteps(steps: List<Pair<Color, String>>, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = SheetPadding, vertical = 8.dp)) {
        steps.forEachIndexed { index, (color, value) ->
            val shape = RoundedCornerShape(
                topStart = if (index == 0) 3.dp else 0.dp,
                topEnd = if (index == 0) 3.dp else 0.dp,
                bottomStart = if (index == steps.lastIndex) 3.dp else 0.dp,
                bottomEnd = if (index == steps.lastIndex) 3.dp else 0.dp,
            )
            Row(Modifier.height(22.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .width(16.dp)
                        .fillMaxHeight()
                        .clip(shape)
                        .background(color),
                )
                Text(value, color = OrcaTheme.colors.text, style = OrcaTheme.typography.body13, modifier = Modifier.padding(start = 12.dp))
            }
        }
    }
}

/** A label and its value on one line of the legend, the value at the end. */
@Composable
fun OrcaLegendValue(label: String, value: String, emphasized: Boolean = false) {
    val colors = OrcaTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SheetPadding, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = colors.textSide, style = OrcaTheme.typography.body14, modifier = Modifier.weight(1f))
        Text(
            value,
            color = colors.text,
            style = OrcaTheme.typography.body14.copy(fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Normal),
            maxLines = 1,
        )
    }
}

/** Space between the groups of a sheet. */
@Composable
fun OrcaSheetSpacer() = Spacer(Modifier.width(1.dp).height(8.dp))
