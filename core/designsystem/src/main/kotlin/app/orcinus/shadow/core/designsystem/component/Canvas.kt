package app.orcinus.shadow.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme

/** The 3D canvas surface: OrcaSlicer's canvas background colour. */
@Composable
fun OrcaCanvas(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.background(OrcaTheme.colors.canvas), content = content)
}

/** The toolbar floating at the top of the canvas (add, arrange, orient, ...). */
@Composable
fun OrcaCanvasToolbar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .shadow(2.dp, OrcaTheme.shapes.canvasPanel)
            .clip(OrcaTheme.shapes.canvasPanel)
            .background(OrcaTheme.colors.canvasPanel)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** A tool of [OrcaCanvasToolbar]. */
@Composable
fun OrcaCanvasTool(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val colors = OrcaTheme.colors
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            tint = if (enabled) colors.onCanvasPanel else colors.textDisabledOnBox,
            modifier = Modifier.size(OrcaTheme.dimensions.iconLarge),
        )
    }
}

/** Round canvas buttons of OrcaSlicer's bottom-left corner (menu, zoom). */
@Composable
fun OrcaCanvasRoundButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(36.dp)
            .shadow(2.dp, CircleShape)
            .clip(CircleShape)
            .background(OrcaTheme.colors.canvasPanel)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = contentDescription, tint = OrcaTheme.colors.onCanvasPanel, modifier = Modifier.size(OrcaTheme.dimensions.icon))
    }
}

/**
 * OrcaSlicer's canvas notification: a panel with the accent bar on the left,
 * as the object information and slicing progress in the bottom-right corner.
 */
@Composable
fun OrcaNotification(
    modifier: Modifier = Modifier,
    action: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = OrcaTheme.colors
    Row(
        modifier = modifier
            .widthIn(max = 360.dp)
            .height(IntrinsicSize.Min)
            .shadow(2.dp, OrcaTheme.shapes.canvasPanel)
            .clip(OrcaTheme.shapes.canvasPanel)
            .background(colors.canvasPanel),
    ) {
        Box(
            Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(colors.accent),
        )
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            content = content,
        )
        action?.let {
            Row(
                modifier = Modifier.padding(end = 4.dp, top = 2.dp),
                verticalAlignment = Alignment.Top,
                content = it,
            )
        }
    }
}

/** Body text line of a notification. */
@Composable
fun OrcaNotificationText(text: String, emphasized: Boolean = false) {
    Text(
        text = text,
        color = if (emphasized) OrcaTheme.colors.onCanvasPanel else OrcaTheme.colors.textSoft,
        style = if (emphasized) OrcaTheme.typography.head13 else OrcaTheme.typography.body13,
    )
}

/** Slicing progress notification with its cancel link. */
@Composable
fun OrcaProgressNotification(
    title: String,
    detail: String?,
    progress: Float,
    cancelLabel: String,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = OrcaTheme.colors
    OrcaNotification(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = colors.onCanvasPanel, style = OrcaTheme.typography.head13, modifier = Modifier.weight(1f, fill = false))
            Text(
                text = cancelLabel,
                color = colors.accent,
                style = OrcaTheme.typography.body13,
                modifier = Modifier
                    .padding(start = 16.dp)
                    .clickable(role = Role.Button, onClick = onCancel)
                    .padding(vertical = 4.dp),
            )
        }
        LinearProgressIndicator(
            progress = { progress },
            color = colors.accent,
            trackColor = colors.canvasPanelSeparator,
            modifier = Modifier
                .padding(vertical = 6.dp)
                .width(240.dp),
        )
        detail?.let { Text(it, color = colors.textSide, style = OrcaTheme.typography.body12) }
    }
}
