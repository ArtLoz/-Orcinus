package app.orcinus.shadow.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.orcinus.shadow.core.designsystem.R
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme

/**
 * OrcaSlicer's parameter input (Widgets/TextInput): a thin bordered box with an
 * optional unit on the right. The border turns to the accent colour on focus.
 */
@Composable
fun OrcaTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    unit: String? = null,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val colors = OrcaTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.height(OrcaTheme.dimensions.parameterControlHeight),
        enabled = enabled,
        singleLine = true,
        textStyle = OrcaTheme.typography.body14.copy(color = if (enabled) colors.text else colors.textDisabled),
        keyboardOptions = keyboardOptions,
        cursorBrush = SolidColor(colors.accent),
        interactionSource = interaction,
        decorationBox = { field ->
            Row(
                modifier = Modifier
                    .clip(OrcaTheme.shapes.control)
                    .background(if (enabled) colors.window else colors.controlDisabledBackground)
                    .border(1.dp, if (focused) colors.accent else colors.border, OrcaTheme.shapes.control)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) { field() }
                unit?.let { Text(it, color = colors.textSide, style = OrcaTheme.typography.body12, modifier = Modifier.padding(start = 6.dp)) }
            }
        },
    )
}

/**
 * The closed state of OrcaSlicer's combo box: bordered field, selected text, and
 * its drop_down arrow. [leading] holds content such as a filament colour slot.
 */
@Composable
fun OrcaComboField(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    leading: @Composable RowScope.() -> Unit = {},
) {
    val colors = OrcaTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(OrcaTheme.dimensions.controlHeight)
            .clip(OrcaTheme.shapes.control)
            .background(if (enabled) colors.window else colors.controlDisabledBackground)
            .border(1.dp, colors.border, OrcaTheme.shapes.control)
            .then(if (onClick != null) Modifier.clickable(enabled = enabled, role = Role.DropdownList, onClick = onClick) else Modifier)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Text(
            text = text,
            color = if (enabled) colors.text else colors.textDisabled,
            style = OrcaTheme.typography.body14,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (onClick != null) {
            Icon(painterResource(R.drawable.orca_drop_down), contentDescription = null, tint = colors.textSide, modifier = Modifier.size(OrcaTheme.dimensions.iconSmall))
        }
    }
}

/** OrcaSlicer's combo box: [OrcaComboField] with a drop-down list, the checked item tinted with the accent. */
@Composable
fun <T> OrcaComboBox(
    items: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leading: @Composable RowScope.() -> Unit = {},
) {
    val colors = OrcaTheme.colors
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OrcaComboField(label(selected), enabled = enabled, onClick = { expanded = true }, leading = leading)
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = colors.window,
            shape = OrcaTheme.shapes.control,
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(label(item), color = colors.text, style = OrcaTheme.typography.body14) },
                    onClick = {
                        expanded = false
                        onSelect(item)
                    },
                    modifier = if (item == selected) Modifier.background(colors.accentSelected) else Modifier,
                )
            }
        }
    }
}

/** The numbered square of a filament slot, filled with the filament colour. */
@Composable
fun OrcaFilamentSlot(
    number: Int,
    modifier: Modifier = Modifier,
    color: Color = OrcaTheme.colors.accent,
) {
    Box(
        modifier = modifier
            .size(22.dp)
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(number.toString(), color = OrcaTheme.colors.onAccent, style = OrcaTheme.typography.body13)
    }
}

/** A leading icon for combo fields, spaced like OrcaSlicer's preset icons. */
@Composable
fun RowScope.OrcaFieldIcon(@DrawableRes icon: Int) {
    Icon(painterResource(icon), contentDescription = null, tint = OrcaTheme.colors.textSide, modifier = Modifier.size(OrcaTheme.dimensions.iconSmall))
    Spacer(Modifier.width(8.dp))
}
