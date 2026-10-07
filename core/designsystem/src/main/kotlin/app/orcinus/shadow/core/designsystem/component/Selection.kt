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
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import app.orcinus.shadow.core.designsystem.R
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme

// The switch of the desktop app is 48 x 18 dp with its dots 15 dp apart; on a
// phone it takes a finger-sized area around that track.
private const val MODE_SWITCH_MODES = 3
private val MODE_SWITCH_TRACK_WIDTH = 48.dp
private val MODE_SWITCH_DOT_CENTRE = 9.dp
private val MODE_SWITCH_DOT_STEP = 15.dp
private val MODE_SWITCH_TOUCH_WIDTH = 96.dp
private val MODE_SWITCH_TOUCH_HEIGHT = 40.dp

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

/**
 * OrcaSlicer's radio button (Widgets/RadioBox), drawn with its own radio_on /
 * radio_off icons: one choice of a group.
 */
@Composable
fun OrcaRadioButton(
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val icon = when {
        !enabled -> R.drawable.orca_radio_disabled
        selected -> R.drawable.orca_radio_on
        else -> R.drawable.orca_radio_off
    }
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .then(
                if (onClick != null) {
                    Modifier.selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(icon), contentDescription = null, modifier = Modifier.size(OrcaTheme.dimensions.checkBox))
    }
}

/**
 * OrcaSlicer's toggle switch (Widgets/SwitchButton): grey track, accent thumb
 * when on. A [mixed] switch stands for values that differ, as the half-checked
 * check box (CheckBox::SetHalfChecked()) does: the thumb in the middle, and a
 * tap switches it on.
 */
@Composable
fun OrcaSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    mixed: Boolean = false,
) {
    val colors = OrcaTheme.colors
    val on = checked && !mixed
    val thumbOffset by animateDpAsState(
        when {
            mixed -> 10.dp
            on -> 18.dp
            else -> 2.dp
        },
        label = "thumb",
    )
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .then(
                if (mixed) {
                    Modifier.triStateToggleable(ToggleableState.Indeterminate, enabled = enabled, role = Role.Switch) { onCheckedChange(true) }
                } else {
                    Modifier.toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .width(36.dp)
                .height(20.dp)
                .clip(CircleShape)
                .background(if (on && enabled) colors.accent.copy(alpha = 0.35f) else colors.switchTrack),
        ) {
            Box(
                Modifier
                    .offset(x = thumbOffset, y = 2.dp)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            !enabled -> colors.textDisabledOnBox
                            on -> colors.accent
                            else -> colors.switchThumb
                        },
                    ),
            )
        }
    }
}

/**
 * OrcaSlicer's two-state switch board, as "Global | Objects" above the
 * process settings: the selected option is filled with the accent colour. The
 * option at [modifiedIndex] takes the modified label colour while it is not
 * selected (SwitchButton::SetTextColor2()).
 */
@Composable
fun OrcaSegmentedSwitch(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    modifiedIndex: Int? = null,
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
                    .orcaSelectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = { onSelect(index) })
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = option,
                    color = when {
                        !enabled -> colors.textDisabledOnBox
                        selected -> colors.onAccent
                        index == modifiedIndex -> colors.labelModified
                        else -> colors.textLabel
                    },
                    style = OrcaTheme.typography.body12,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * OrcaSlicer's settings visibility switch (ModeSwitchButton): three dots on a
 * grey track that the accent fills up to the selected mode, simple, advanced,
 * or expert. In [developer] mode the switch shows "DEV" and selects nothing.
 *
 * The desktop app picks the mode under the pointer; a phone has one small
 * switch and a wide finger, so a tap anywhere on it steps to the next mode and
 * round again. The dots move at once and the engine's answer confirms them.
 */
@Composable
fun OrcaModeSwitch(
    selection: Int,
    onSelect: (Int) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    developer: Boolean = false,
) {
    val colors = OrcaTheme.colors
    val track = if (colors.isDark) Color(0xFF4A4A51) else Color(0xFFD9D9D9)
    // The mode the switch was last told to show, until the answer for it
    // arrives: a second tap steps on from there, not from the mode on screen.
    var asked by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(selection) { asked = null }
    val shown = asked ?: selection
    // The switch answers with its dot, as the desktop app does; the buttons of
    // OrcaSlicer have no ripple either.
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .size(width = MODE_SWITCH_TOUCH_WIDTH, height = MODE_SWITCH_TOUCH_HEIGHT)
            .semantics { this.contentDescription = contentDescription }
            .clickable(interactionSource = interaction, indication = null, enabled = enabled && !developer, role = Role.Button) {
                val next = (shown + 1) % MODE_SWITCH_MODES
                asked = next
                onSelect(next)
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .width(MODE_SWITCH_TRACK_WIDTH)
                .height(18.dp)
                .clip(CircleShape)
                .background(track),
        ) {
            if (developer) {
                Text("DEV", color = colors.textDimmed, style = OrcaTheme.typography.body10, modifier = Modifier.align(Alignment.Center))
            } else {
                // The track to the selected dot, and the dots: the selected one larger.
                Box(
                    Modifier
                        .fillMaxHeight()
                        .width(18.dp + MODE_SWITCH_DOT_STEP * shown)
                        .clip(CircleShape)
                        .background(colors.accent),
                )
                repeat(MODE_SWITCH_MODES) { index ->
                    val dot = if (index == shown) 11.5.dp else 5.75.dp
                    Box(
                        Modifier
                            .offset(x = MODE_SWITCH_DOT_CENTRE + MODE_SWITCH_DOT_STEP * index - dot / 2, y = 9.dp - dot / 2)
                            .size(dot)
                            .clip(CircleShape)
                            .background(if (index <= shown) Color(0xFFFFFEFE) else Color(0xFFEEEEEE)),
                    )
                }
            }
        }
    }
}
