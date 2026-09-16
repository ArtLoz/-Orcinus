package app.orcinus.shadow.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme

/** A page of the main tab bar, as Prepare and Preview in OrcaSlicer. */
@Immutable
data class OrcaTab(
    val title: String,
    @param:DrawableRes val icon: Int,
)

/**
 * OrcaSlicer's main tab bar. It runs under the status bar; its content stays
 * below it. With [fillWidth] the tabs share the whole width and [actions] are
 * not shown, for narrow windows.
 */
@Composable
fun OrcaTabBar(
    tabs: List<OrcaTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    fillWidth: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = OrcaTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.tabBar)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .height(OrcaTheme.dimensions.tabBarHeight)
            .selectableGroup(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEachIndexed { index, tab ->
            val selected = index == selectedIndex
            val content = if (selected) colors.onTabBar else colors.onTabBarInactive
            Row(
                modifier = Modifier
                    .then(if (fillWidth) Modifier.weight(1f) else Modifier)
                    .fillMaxHeight()
                    .background(if (selected) colors.accent else Color.Transparent)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(index) })
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(tab.icon), contentDescription = null, tint = content, modifier = Modifier.size(OrcaTheme.dimensions.icon))
                Text(
                    text = tab.title,
                    color = content,
                    style = OrcaTheme.typography.body15,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
        if (!fillWidth) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
    }
}
