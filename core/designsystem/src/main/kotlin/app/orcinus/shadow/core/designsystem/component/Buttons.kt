package app.orcinus.shadow.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.orcinus.shadow.core.designsystem.theme.OrcaColors
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme

/** Colour sets of OrcaSlicer's buttons (btn_regular, btn_confirm, btn_alert in Widgets/Button.cpp). */
enum class OrcaButtonStyle {
    /** Neutral grey button. */
    Regular,

    /** Accent button for the main action, such as "Slice plate". */
    Confirm,

    /** Grey button that turns red while pressed, for destructive actions. */
    Alert,
}

/** Sizes of OrcaSlicer's button types. */
enum class OrcaButtonSize {
    Compact,
    Window,
    Choice,
    Parameter,
    Expanded,
}

private data class ButtonColors(val background: Color, val content: Color)

private fun OrcaColors.buttonColors(style: OrcaButtonStyle, enabled: Boolean, pressed: Boolean): ButtonColors = when {
    !enabled -> ButtonColors(buttonBackground, textDimmed)
    style == OrcaButtonStyle.Confirm -> ButtonColors(if (pressed) accentPressed else accent, onAccent)
    style == OrcaButtonStyle.Alert && pressed -> ButtonColors(alert, onAccent)
    else -> ButtonColors(if (pressed) buttonBackgroundPressed else buttonBackground, text)
}

private data class ButtonMetrics(val minHeight: Dp, val minWidth: Dp, val padding: PaddingValues, val cornerRadius: Dp)

private fun OrcaButtonSize.metrics() = when (this) {
    OrcaButtonSize.Compact -> ButtonMetrics(24.dp, 0.dp, PaddingValues(horizontal = 8.dp, vertical = 3.dp), 8.dp)
    OrcaButtonSize.Window -> ButtonMetrics(32.dp, 58.dp, PaddingValues(horizontal = 16.dp), 16.dp)
    OrcaButtonSize.Choice -> ButtonMetrics(32.dp, 100.dp, PaddingValues(horizontal = 12.dp, vertical = 8.dp), 4.dp)
    OrcaButtonSize.Parameter -> ButtonMetrics(28.dp, 120.dp, PaddingValues(horizontal = 12.dp), 4.dp)
    OrcaButtonSize.Expanded -> ButtonMetrics(32.dp, 0.dp, PaddingValues(horizontal = 12.dp, vertical = 8.dp), 4.dp)
}

@Composable
private fun OrcaButtonSize.textStyle(): TextStyle = when (this) {
    OrcaButtonSize.Compact -> OrcaTheme.typography.body10
    OrcaButtonSize.Window -> OrcaTheme.typography.body14
    else -> OrcaTheme.typography.body14
}

/**
 * OrcaSlicer's text button. The drawn size follows the desktop widget; the
 * touch target grows to the Android minimum around it.
 */
@Composable
fun OrcaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: OrcaButtonStyle = OrcaButtonStyle.Confirm,
    size: OrcaButtonSize = OrcaButtonSize.Window,
    enabled: Boolean = true,
    @DrawableRes icon: Int? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val colors = OrcaTheme.colors.buttonColors(style, enabled, pressed)
    val metrics = size.metrics()
    val shape = RoundedCornerShape(metrics.cornerRadius)
    Row(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .defaultMinSize(minWidth = metrics.minWidth, minHeight = metrics.minHeight)
            .clip(shape)
            .background(colors.background)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(metrics.padding),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let { Icon(painterResource(it), contentDescription = null, tint = colors.content, modifier = Modifier.size(16.dp)) }
        Text(text, color = colors.content, style = size.textStyle(), maxLines = 1)
    }
}

/** OrcaSlicer's 26 px icon button, used next to presets and in panels. */
@Composable
fun OrcaIconButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = OrcaTheme.colors.textSide,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val colors = OrcaTheme.colors
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(26.dp)
            .clip(OrcaTheme.shapes.control)
            .background(if (pressed) colors.buttonBackground else Color.Transparent)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            tint = if (enabled) tint else colors.textDisabledOnBox,
            modifier = Modifier.size(OrcaTheme.dimensions.icon),
        )
    }
}

/** Outlined variant of a choice button: a bordered box with centred text, as preset choices. */
@Composable
fun OrcaOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = OrcaTheme.colors
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .height(OrcaTheme.dimensions.controlHeight)
            .clip(OrcaTheme.shapes.control)
            .border(1.dp, colors.border, OrcaTheme.shapes.control)
            .background(if (enabled) colors.window else colors.controlDisabledBackground)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (enabled) colors.text else colors.textDisabled, style = OrcaTheme.typography.body14, maxLines = 1)
    }
}
