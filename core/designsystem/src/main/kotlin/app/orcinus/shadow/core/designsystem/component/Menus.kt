package app.orcinus.shadow.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.orcinus.shadow.core.designsystem.R
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme

/**
 * OrcaSlicer's context menu (a wxMenu from MenuFactory), opened where it was
 * asked for: at [position] in pixels from the top start of the parent box.
 */
@Composable
fun OrcaContextMenu(
    expanded: Boolean,
    position: IntOffset,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(Modifier.offset { position }.size(0.dp)) {
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            containerColor = OrcaTheme.colors.window,
            shape = OrcaTheme.shapes.control,
            content = content,
        )
    }
}

/** An item of an OrcaSlicer menu (append_menu_item), aligned with the check items. */
@Composable
fun OrcaMenuItem(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val colors = OrcaTheme.colors
    DropdownMenuItem(
        text = { Text(text, color = if (enabled) colors.text else colors.textDisabled, style = OrcaTheme.typography.body14, maxLines = 1, softWrap = false) },
        onClick = onClick,
        enabled = enabled,
        leadingIcon = { Spacer(Modifier.size(OrcaTheme.dimensions.iconSmall)) },
    )
}

/** wxMenu::AppendSeparator(). */
@Composable
fun OrcaMenuSeparator() {
    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = OrcaTheme.colors.border)
}

/** A check item of an OrcaSlicer menu (append_menu_check_item), marked with the drop-down list's check bitmap. */
@Composable
fun OrcaMenuCheckItem(
    text: String,
    checked: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val colors = OrcaTheme.colors
    DropdownMenuItem(
        // A menu item is one line, as in OrcaSlicer's menus.
        text = { Text(text, color = if (enabled) colors.text else colors.textDisabled, style = OrcaTheme.typography.body14, maxLines = 1, softWrap = false) },
        onClick = onClick,
        enabled = enabled,
        leadingIcon = {
            if (checked) {
                // Widgets/DropDown draws "checked" as it is.
                Icon(painterResource(R.drawable.orca_checked), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(OrcaTheme.dimensions.iconSmall))
            } else {
                Spacer(Modifier.size(OrcaTheme.dimensions.iconSmall))
            }
        },
    )
}
