package app.orcinus.shadow.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.draw.rotate
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
    /** What stands where OrcaSlicer's menu icon does; empty space keeps the items aligned. */
    leading: @Composable () -> Unit = { Spacer(Modifier.size(OrcaTheme.dimensions.iconSmall)) },
) {
    DropdownMenuItem(
        text = { MenuText(text, enabled) },
        onClick = onClick,
        enabled = enabled,
        leadingIcon = leading,
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
    DropdownMenuItem(
        text = { MenuText(text, enabled) },
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

/**
 * A submenu of an OrcaSlicer menu (append_submenu). A phone has no room for a
 * menu beside the menu, so a tap opens its items under it, indented.
 */
@Composable
fun OrcaSubmenu(
    text: String,
    enabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    DropdownMenuItem(
        text = { MenuText(text, enabled) },
        onClick = { open = !open },
        enabled = enabled,
        leadingIcon = { Spacer(Modifier.size(OrcaTheme.dimensions.iconSmall)) },
        trailingIcon = {
            Icon(
                painterResource(R.drawable.orca_drop_down),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier
                    .size(OrcaTheme.dimensions.iconSmall)
                    .rotate(if (open) 180f else 0f),
            )
        },
    )
    if (open && enabled) {
        Column(Modifier.padding(start = 16.dp), content = content)
    }
}

/**
 * The text of a menu item: one line as in OrcaSlicer's menus while it fits.
 * A phone's menu is at most 280 dp wide, so a longer one goes on to a second line.
 */
@Composable
private fun MenuText(text: String, enabled: Boolean) {
    val colors = OrcaTheme.colors
    Text(text, color = if (enabled) colors.text else colors.textDisabled, style = OrcaTheme.typography.body14, maxLines = 2)
}
