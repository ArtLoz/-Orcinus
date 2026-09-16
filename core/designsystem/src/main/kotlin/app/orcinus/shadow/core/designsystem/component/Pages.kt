package app.orcinus.shadow.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.orcinus.shadow.core.designsystem.R
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme

/**
 * The title bar of a page opened over the workspace, such as About: the colours
 * of OrcaSlicer's tab bar with a back button. It runs under the status bar; its
 * content stays below it.
 */
@Composable
fun OrcaPageTopBar(
    title: String,
    backDescription: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OrcaTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.tabBar)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .height(OrcaTheme.dimensions.tabBarHeight)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OrcaIconButton(
            icon = R.drawable.orca_cali_page_caption_prev,
            contentDescription = backDescription,
            onClick = onBack,
            tint = colors.onTabBar,
        )
        Text(
            text = title,
            color = colors.onTabBar,
            style = OrcaTheme.typography.head15,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(start = 4.dp, end = 12.dp)
                .semantics { heading() },
        )
    }
}

/** A line of a list page that opens another page: title, optional details, and a chevron. */
@Composable
fun OrcaListRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    details: String? = null,
) {
    val colors = OrcaTheme.colors
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick)
                .heightIn(min = OrcaTheme.dimensions.minimumTouchTarget)
                .padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, color = colors.text, style = OrcaTheme.typography.body15)
                details?.let {
                    Text(it, color = colors.textSide, style = OrcaTheme.typography.body12, modifier = Modifier.padding(top = 2.dp))
                }
            }
            Icon(
                painter = painterResource(R.drawable.orca_hms_arrow),
                contentDescription = null,
                tint = colors.textSide,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(OrcaTheme.dimensions.iconSmall),
            )
        }
        HorizontalDivider(color = colors.separator, thickness = 1.dp)
    }
}

/** A text link in the accent colour, with a touch target of its own. */
@Composable
fun OrcaLink(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        color = OrcaTheme.colors.accent,
        style = OrcaTheme.typography.body14,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 4.dp),
    )
}
