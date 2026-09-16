package app.orcinus.shadow.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme

/** Title bar of a sidebar section (Printer, Filament, Process), with its gradient and actions. */
@Composable
fun OrcaSidebarTitle(
    title: String,
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = OrcaTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(OrcaTheme.dimensions.sidebarTitleHeight)
            .background(Brush.verticalGradient(listOf(colors.sidebarTitleTop, colors.sidebarTitleBottom)))
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = colors.textSide, modifier = Modifier.size(OrcaTheme.dimensions.icon))
        Text(
            text = title,
            color = colors.text,
            style = OrcaTheme.typography.body15,
            modifier = Modifier
                .padding(start = 8.dp)
                .weight(1f),
        )
        actions()
    }
}

/** Content area under a sidebar title. */
@Composable
fun OrcaSidebarSection(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(OrcaTheme.colors.window)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        content()
    }
}

/** Heading of a parameter group (Layer height, Seam, ...) with its param_ icon. */
@Composable
fun OrcaParameterGroupHeader(
    title: String,
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
) {
    val colors = OrcaTheme.colors
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(icon), contentDescription = null, tint = colors.textLabel, modifier = Modifier.size(OrcaTheme.dimensions.iconSmall))
            Text(title, color = colors.text, style = OrcaTheme.typography.head14, modifier = Modifier.padding(start = 8.dp))
        }
        HorizontalDivider(color = colors.separator, thickness = 1.dp)
    }
}

/** A parameter line: label on the left, its control on the right. */
@Composable
fun OrcaParameterRow(
    label: String,
    modifier: Modifier = Modifier,
    control: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .padding(start = 24.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = OrcaTheme.colors.textLabel,
            style = OrcaTheme.typography.body14,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Box(Modifier.width(140.dp), contentAlignment = Alignment.CenterEnd) { control() }
    }
}

/** OrcaSlicer's settings page tabs (Quality, Strength, Support, ...): accent text and underline when selected. */
@Composable
fun OrcaUnderlineTabs(
    titles: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OrcaTheme.colors
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectableGroup(),
        ) {
            titles.forEachIndexed { index, title ->
                val selected = index == selectedIndex
                Column(
                    modifier = Modifier
                        .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(index) })
                        .padding(horizontal = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = title,
                        color = if (selected) colors.text else colors.textDimmed,
                        style = if (selected) OrcaTheme.typography.head14 else OrcaTheme.typography.body14,
                        maxLines = 1,
                        modifier = Modifier.padding(vertical = 10.dp),
                    )
                    Box(
                        Modifier
                            .height(2.dp)
                            .fillMaxWidth()
                            .background(if (selected) colors.accent else Color.Transparent),
                    )
                }
            }
        }
        HorizontalDivider(color = colors.separator, thickness = 1.dp)
    }
}
