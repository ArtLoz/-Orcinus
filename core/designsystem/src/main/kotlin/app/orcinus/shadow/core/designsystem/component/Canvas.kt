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
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import app.orcinus.shadow.core.designsystem.R
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import kotlin.math.roundToInt

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

    /** SeriousWarningNotificationLevel: white text on OrcaSlicer's warning colour. */
    SeriousWarning,

    /** ErrorNotificationLevel: white text on OrcaSlicer's error colour. */
    Error,
}

private val LocalNotificationLevel = staticCompositionLocalOf { OrcaNotificationLevel.Regular }

// NotificationManager::PopNotification::m_ErrorColor, m_WarnColor and m_HyperTextColor; the error's link colour.
private val NotificationErrorColor = Color(0xFFE14747)
private val NotificationWarningColor = Color(0xFFF59B16)
private val NotificationLinkColor = Color(0f, 0.588f, 0.533f)
private val NotificationErrorLinkColor = Color(135, 43, 43)
private val NotificationSeriousWarningLinkColor = Color(0f, 0f, 0f, 0.4f)

/** What a notification's close and minimize buttons tell accessibility services, and its "More", in the app's language. */
@Immutable
data class OrcaNotificationLabels(
    val close: String = "Close",
    val more: String = "More",
    val minimize: String = "Minimize",
)

/** The labels of every notification below; the app provides them in its language. */
val LocalOrcaNotificationLabels = staticCompositionLocalOf { OrcaNotificationLabels() }

/**
 * OrcaSlicer's canvas notification, as the object information and slicing
 * progress in the bottom-right corner (NotificationManager::PopNotification).
 * [onClose] is its close button (render_close_button()); none without it, as
 * a job's progress shows its Cancel instead. A text of more than six lines
 * shows its first two, the second ending in "..More", which shows it whole
 * (init(), render_text()); [multiline] true shows it whole from the start, as
 * set_Multiline(true) does. Shown whole, a text of more than three lines can
 * be minimized to its first two again (render_minimize_button()), except on
 * an error or a serious warning, which OrcaSlicer draws as block
 * notifications without that button. Lines count in the height of "A" in the
 * notification's text, as CalcTextSize("A") does; [collapsible] false keeps a
 * notification that draws itself, as the slicing progress, whole.
 */
@Composable
fun OrcaNotification(
    modifier: Modifier = Modifier,
    level: OrcaNotificationLevel = OrcaNotificationLevel.Regular,
    onClose: (() -> Unit)? = null,
    multiline: Boolean? = null,
    collapsible: Boolean = true,
    action: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = OrcaTheme.colors
    val labels = LocalOrcaNotificationLabels.current
    val block = level == OrcaNotificationLevel.Error || level == OrcaNotificationLevel.SeriousWarning
    val background = when (level) {
        OrcaNotificationLevel.Error -> NotificationErrorColor
        OrcaNotificationLevel.SeriousWarning -> NotificationWarningColor
        else -> colors.canvasPanel
    }
    val lineHeight = rememberTextMeasurer().measure("A", OrcaTheme.typography.body13).size.height.toFloat()
    // m_lines_count of the last layout, m_multiline as init() set it, and as "More" and the minimize button set it.
    var lines by remember { mutableIntStateOf(0) }
    var initialMultiline by remember { mutableStateOf<Boolean?>(null) }
    var userMultiline by remember { mutableStateOf<Boolean?>(null) }
    fun whole(count: Int): Boolean = userMultiline ?: initialMultiline ?: multiline ?: (count <= MAX_WHOLE_LINES)
    val collapsed = collapsible && lines > COLLAPSED_LINES && !whole(lines)
    val minimizable = collapsible && !block && lines > MINIMIZABLE_LINES && whole(lines)
    Row(
        modifier = modifier
            .widthIn(max = 360.dp)
            .height(IntrinsicSize.Min)
            .shadow(2.dp, OrcaTheme.shapes.canvasPanel)
            .clip(OrcaTheme.shapes.canvasPanel)
            .background(background),
    ) {
        Box(
            Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(
                    when (level) {
                        OrcaNotificationLevel.Error -> NotificationErrorColor
                        OrcaNotificationLevel.Warning, OrcaNotificationLevel.SeriousWarning -> NotificationWarningColor
                        OrcaNotificationLevel.Regular -> colors.accent
                    },
                ),
        )
        CompositionLocalProvider(LocalNotificationLevel provides level) {
            Box(
                Modifier
                    .weight(1f, fill = false)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Column(
                    modifier = Modifier
                        .clipToBounds()
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
                            val count = (placeable.height / lineHeight).roundToInt()
                            val shown = !collapsible || count <= COLLAPSED_LINES || whole(count)
                            val height = if (shown) placeable.height else minOf(placeable.height, (COLLAPSED_LINES * lineHeight).roundToInt())
                            layout(placeable.width, height) { placeable.place(0, 0) }
                        }
                        .onSizeChanged { size ->
                            val count = (size.height / lineHeight).roundToInt()
                            if (initialMultiline == null) initialMultiline = multiline ?: (count <= MAX_WHOLE_LINES)
                            lines = count
                        },
                    content = content,
                )
                if (collapsed) {
                    // render_text(): the second line ends in ".." and the "More" hypertext.
                    Row(Modifier.align(Alignment.BottomEnd).background(background).padding(start = 4.dp)) {
                        OrcaNotificationText("..")
                        Text(
                            text = labels.more,
                            color = notificationLinkColor(level),
                            style = OrcaTheme.typography.body13.copy(textDecoration = TextDecoration.Underline),
                            modifier = Modifier.clickable(role = Role.Button) { userMultiline = true },
                        )
                    }
                }
            }
        }
        if (action != null || onClose != null || minimizable) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(end = 4.dp, top = 2.dp, bottom = 2.dp),
                horizontalAlignment = Alignment.End,
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    action?.invoke(this)
                    onClose?.let { close ->
                        OrcaIconButton(
                            icon = if (block) R.drawable.orca_block_notification_close else R.drawable.orca_notification_close,
                            contentDescription = labels.close,
                            onClick = close,
                            tint = Color.Unspecified,
                        )
                    }
                }
                if (minimizable) {
                    Spacer(Modifier.weight(1f))
                    OrcaIconButton(
                        icon = R.drawable.orca_notification_minimalize,
                        contentDescription = labels.minimize,
                        onClick = { userMultiline = false },
                        tint = Color.Unspecified,
                    )
                }
            }
        }
    }
}

/** PopNotification::init(): a text of up to six lines shows whole. */
private const val MAX_WHOLE_LINES = 6

/** render_text(): a collapsed text shows two lines. */
private const val COLLAPSED_LINES = 2

/** PopNotification::render(): the minimize button of a text of more than three lines. */
private const val MINIMIZABLE_LINES = 3

/** render_hypertext()'s colour of a notification of [level]. */
private fun notificationLinkColor(level: OrcaNotificationLevel): Color = when (level) {
    OrcaNotificationLevel.Error -> NotificationErrorLinkColor
    // render_hypertext(): the serious warning's link is a translucent black.
    OrcaNotificationLevel.SeriousWarning -> NotificationSeriousWarningLinkColor
    else -> NotificationLinkColor
}

/** PopNotification::render_hypertext(): the notification's underlined link, such as "Jump to". */
@Composable
fun OrcaNotificationLink(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        color = notificationLinkColor(LocalNotificationLevel.current),
        style = OrcaTheme.typography.body13.copy(textDecoration = TextDecoration.Underline),
        modifier = Modifier
            .padding(top = 4.dp)
            .clickable(role = Role.Button, onClick = onClick),
    )
}

/**
 * Body text line of a notification; an [error] line is in OrcaSlicer's error
 * colour, as PopNotification draws the text between <Error> marks.
 */
@Composable
fun OrcaNotificationText(text: String, emphasized: Boolean = false, error: Boolean = false) {
    Text(
        text = text,
        color = when {
            LocalNotificationLevel.current == OrcaNotificationLevel.Error ||
                LocalNotificationLevel.current == OrcaNotificationLevel.SeriousWarning -> Color.White
            error -> NotificationErrorColor
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
    /** What the notification holds under a separator line, such as SlicingProgressNotification's daily tips. */
    below: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val colors = OrcaTheme.colors
    // ProgressBarNotification keeps its lines and has no minimize button.
    OrcaNotification(modifier, collapsible = false) {
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
        below?.let { content ->
            Box(
                Modifier
                    .padding(vertical = 6.dp)
                    .width(240.dp)
                    .height(1.dp)
                    .background(colors.canvasPanelSeparator),
            )
            content()
        }
    }
}
