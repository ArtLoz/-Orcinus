package app.orcinus.shadow.core.designsystem.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import app.orcinus.shadow.core.designsystem.R
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme

/**
 * OrcaSlicer's check box, drawn with its own check_on / check_off /
 * check_half icons. A null [checked] shows the mixed state.
 */
@Composable
fun OrcaCheckBox(
    checked: Boolean?,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val state = when (checked) {
        true -> ToggleableState.On
        false -> ToggleableState.Off
        null -> ToggleableState.Indeterminate
    }
    val icon = when (state) {
        ToggleableState.On -> if (enabled) R.drawable.orca_check_on else R.drawable.orca_check_on_disabled
        ToggleableState.Off -> if (enabled) R.drawable.orca_check_off else R.drawable.orca_check_off_disabled
        ToggleableState.Indeterminate -> if (enabled) R.drawable.orca_check_half else R.drawable.orca_check_half_disabled
    }
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .then(
                if (onCheckedChange != null) {
                    Modifier.triStateToggleable(state = state, enabled = enabled, role = Role.Checkbox) {
                        onCheckedChange(checked != true)
                    }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(icon), contentDescription = null, modifier = Modifier.size(OrcaTheme.dimensions.checkBox))
    }
}

/** OrcaSlicer's toggle switch (Widgets/SwitchButton): grey track, accent thumb when on. */
@Composable
fun OrcaSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = OrcaTheme.colors
    val thumbOffset by animateDpAsState(if (checked) 18.dp else 2.dp, label = "thumb")
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .width(36.dp)
                .height(20.dp)
                .clip(CircleShape)
                .background(if (checked && enabled) colors.accent.copy(alpha = 0.35f) else colors.switchTrack),
        ) {
            Box(
                Modifier
                    .offset(x = thumbOffset, y = 2.dp)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            !enabled -> colors.textDisabledOnBox
                            checked -> colors.accent
                            else -> colors.switchThumb
                        },
                    ),
            )
        }
    }
}

/**
 * OrcaSlicer's two-state switch board, as "Global | Objects" above the
 * process settings: the selected option is filled with the accent colour.
 */
@Composable
fun OrcaSegmentedSwitch(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = OrcaTheme.colors
    Row(
        modifier = modifier
            .height(24.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.separator)
            .selectableGroup(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEachIndexed { index, option ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected && enabled) colors.accent else Color.Transparent)
                    .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = { onSelect(index) })
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = option,
                    color = when {
                        !enabled -> colors.textDisabledOnBox
                        selected -> colors.onAccent
                        else -> colors.textLabel
                    },
                    style = OrcaTheme.typography.body12,
                    maxLines = 1,
                )
            }
        }
    }
}
