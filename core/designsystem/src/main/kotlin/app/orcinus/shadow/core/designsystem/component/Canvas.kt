package app.orcinus.shadow.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import app.orcinus.shadow.core.designsystem.R
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme

/** The 3D canvas surface: OrcaSlicer's canvas background colour. */
@Composable
fun OrcaCanvas(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier.background(OrcaTheme.colors.canvas), content = content)
}

/**
 * The toolbar floating at the top of the canvas (add, arrange, orient, gizmos, ...).
 * OrcaSlicer shrinks its icons until every toolbar fits the canvas; on a
 * phone that would leave icons too small to touch, so the row scrolls instead.
 */
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
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/**
 * A tool of [OrcaCanvasToolbar]. OrcaSlicer draws toolbar icons in one colour
 * and the active gizmo's icon, [selected], in the icon's own colours
 * (GLTexture::load_from_svg_files_as_sprites_array).
 */
@Composable
fun OrcaCanvasTool(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    selected: Boolean = false,
) {
    val colors = OrcaTheme.colors
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.semantics { this.selected = selected },
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            tint = when {
                !enabled -> colors.textDisabledOnBox
                selected -> Color.Unspecified
                else -> colors.onCanvasPanel
            },
            modifier = Modifier.size(OrcaTheme.dimensions.iconLarge),
        )
    }
}

/** A separator of OrcaSlicer's toolbars: seperator.svg, half an icon wide, in the toolbar colour. */
@Composable
fun OrcaCanvasToolbarSeparator() {
    Icon(
        painter = painterResource(R.drawable.orca_seperator),
        contentDescription = null,
        tint = OrcaTheme.colors.onCanvasPanel,
        modifier = Modifier.size(width = OrcaTheme.dimensions.iconLarge / 2, height = OrcaTheme.dimensions.iconLarge * 2),
    )
}

/**
 * The window a gizmo opens under the toolbar (GLGizmoBase::on_render_input_window),
 * as OrcaSlicer's ImGui toolbar style draws it.
 */
@Composable
fun OrcaGizmoPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    // ImGuiWindowFlags_AlwaysAutoResize: as wide as the content.
    Column(
        modifier = modifier
            .width(IntrinsicSize.Max)
            .shadow(2.dp, OrcaTheme.shapes.canvasPanel)
            .clip(OrcaTheme.shapes.canvasPanel)
            .background(OrcaTheme.colors.canvasPanel)
            .padding(12.dp),
        content = content,
    )
}

/**
 * Round canvas buttons of OrcaSlicer's bottom-left corner (menu, zoom):
 * ImGui::ImageButton3() draws the button's own 36 x 36 image, circle and all,
 * in its light or dark variant.
 */
@Composable
fun OrcaCanvasRoundButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Image(
        painter = painterResource(icon),
        contentDescription = contentDescription,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(36.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
    )
}

/** How OrcaSlicer's NotificationManager draws a notification of a level. */
enum class OrcaNotificationLevel {
    /** The canvas panel with the accent bar on the left. */
    Regular,

    /** WarningNotificationLevel: the canvas panel with the bar in OrcaSlicer's warning colour. */
    Warning,

    /** ErrorNotificationLevel: white text on OrcaSlicer's error colour. */
    Error,
}

private val LocalNotificationLevel = staticCompositionLocalOf { OrcaNotificationLevel.Regular }

// NotificationManager::PopNotification::m_ErrorColor, m_WarnColor and m_HyperTextColor; the error's link colour.
private val NotificationErrorColor = Color(0xFFE14747)
private val NotificationWarningColor = Color(0xFFF59B16)
private val NotificationLinkColor = Color(0f, 0.588f, 0.533f)
private val NotificationErrorLinkColor = Color(135, 43, 43)

/**
 * OrcaSlicer's canvas notification, as the object information and slicing
 * progress in the bottom-right corner.
 */
@Composable
fun OrcaNotification(
    modifier: Modifier = Modifier,
    level: OrcaNotificationLevel = OrcaNotificationLevel.Regular,
    action: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = OrcaTheme.colors
    val error = level == OrcaNotificationLevel.Error
    Row(
        modifier = modifier
            .widthIn(max = 360.dp)
            .height(IntrinsicSize.Min)
            .shadow(2.dp, OrcaTheme.shapes.canvasPanel)
            .clip(OrcaTheme.shapes.canvasPanel)
            .background(if (error) NotificationErrorColor else colors.canvasPanel),
    ) {
        Box(
            Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(
                    when (level) {
                        OrcaNotificationLevel.Error -> NotificationErrorColor
                        OrcaNotificationLevel.Warning -> NotificationWarningColor
                        OrcaNotificationLevel.Regular -> colors.accent
                    },
                ),
        )
        CompositionLocalProvider(LocalNotificationLevel provides level) {
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                content = content,
            )
        }
        action?.let {
            Row(
                modifier = Modifier.padding(end = 4.dp, top = 2.dp),
                verticalAlignment = Alignment.Top,
                content = it,
            )
        }
    }
}

/** PopNotification::render_hypertext(): the notification's underlined link, such as "Jump to". */
@Composable
fun OrcaNotificationLink(text: String, onClick: () -> Unit) {
    val color = if (LocalNotificationLevel.current == OrcaNotificationLevel.Error) NotificationErrorLinkColor else NotificationLinkColor
    Text(
        text = text,
        color = color,
        style = OrcaTheme.typography.body13.copy(textDecoration = TextDecoration.Underline),
        modifier = Modifier
            .padding(top = 4.dp)
            .clickable(role = Role.Button, onClick = onClick),
    )
}

/** Body text line of a notification. */
@Composable
fun OrcaNotificationText(text: String, emphasized: Boolean = false) {
    Text(
        text = text,
        color = when {
            LocalNotificationLevel.current == OrcaNotificationLevel.Error -> Color.White
            emphasized -> OrcaTheme.colors.onCanvasPanel
            else -> OrcaTheme.colors.textSoft
        },
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
