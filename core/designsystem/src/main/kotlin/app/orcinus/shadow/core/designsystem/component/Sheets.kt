package app.orcinus.shadow.core.designsystem.component

import android.graphics.Rect
import android.view.View
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import app.orcinus.shadow.core.designsystem.layout.OrcaWindowLayout
import app.orcinus.shadow.core.designsystem.layout.currentOrcaWindowLayout
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme

/** The widths of OrcaSlicer's dialogs a sheet becomes on a large window. */
object OrcaDialogWidth {
    /** A small dialog: a question, a few choices (MessageDialog, 400 px wide). */
    val Small: Dp = 420.dp

    /** A dialog of a form or a list, as most of OrcaSlicer's are. */
    val Medium: Dp = 560.dp

    /** A dialog of a table or of two panes. */
    val Large: Dp = 760.dp
}

/**
 * A window that a phone opens as a bottom sheet, and a larger window as a
 * dialog in its middle, [dialogWidth] wide as the OrcaSlicer dialog it stands
 * for: a sheet across a tablet's width is far from where the user looks, and
 * a desktop app opens its dialogs over the window. The content's own insets
 * padding is consumed in the dialog, which no system bar overlaps.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrcaSheet(
    onDismissRequest: () -> Unit,
    dialogWidth: Dp = OrcaDialogWidth.Medium,
    /** The sheet opens at full height, as a long one does. */
    skipPartiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (currentOrcaWindowLayout() == OrcaWindowLayout.Compact) {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = skipPartiallyExpanded),
            containerColor = OrcaTheme.colors.window,
            dragHandle = { OrcaSheetHandle() },
            content = content,
        )
    } else {
        Dialog(onDismissRequest = onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            OrcaDialogSurface(width = dialogWidth) {
                Column(Modifier.padding(top = 12.dp, bottom = 8.dp), content = content)
            }
        }
    }
}

/**
 * The frame of a dialog on a large window: OrcaSlicer's window colour and
 * corners, [width] wide while the window has room, with a margin to the
 * window's edges.
 */
@Composable
fun OrcaDialogSurface(
    width: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier
            .padding(DIALOG_MARGIN)
            .widthIn(max = width)
            .fillMaxWidth()
            .consumeWindowInsets(WindowInsets.safeDrawing),
        shape = OrcaTheme.shapes.window,
        color = OrcaTheme.colors.window,
        contentColor = OrcaTheme.colors.text,
        shadowElevation = 8.dp,
        content = content,
    )
}

/**
 * A dialog that covers a phone's window, as one of OrcaSlicer's larger dialogs
 * has no room there otherwise, and on a larger window stands in its middle at
 * the dialog's own size ([width] × [height], within the window), as the
 * desktop opens it over the plater. [content] fills it with its caption bar
 * first; the insets of the system bars are its own concern on a phone only.
 */
@Composable
fun OrcaFullScreenDialog(
    onDismissRequest: () -> Unit,
    width: Dp,
    height: Dp,
    content: @Composable () -> Unit,
) {
    if (currentOrcaWindowLayout() == OrcaWindowLayout.Compact) {
        Dialog(
            onDismissRequest = onDismissRequest,
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
            content = content,
        )
    } else {
        Dialog(onDismissRequest = onDismissRequest, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(
                modifier = Modifier
                    .padding(DIALOG_MARGIN)
                    .widthIn(max = width)
                    .heightIn(max = height)
                    .fillMaxSize()
                    .consumeWindowInsets(WindowInsets.safeDrawing),
                shape = OrcaTheme.shapes.window,
                color = OrcaTheme.colors.window,
                contentColor = OrcaTheme.colors.text,
                shadowElevation = 8.dp,
                content = content,
            )
        }
    }
}

/**
 * Where a field is in its window, for the list it opens to drop down from it
 * ([OrcaPickerSheet]); [orcaPickerAnchor] keeps it up to date.
 */
@Stable
class OrcaPickerAnchor {
    internal var bounds: IntRect? by mutableStateOf(null)
}

@Composable
fun rememberOrcaPickerAnchor(): OrcaPickerAnchor = remember { OrcaPickerAnchor() }

/** Marks the field [anchor] stands for. */
fun Modifier.orcaPickerAnchor(anchor: OrcaPickerAnchor): Modifier = onGloballyPositioned { coordinates ->
    anchor.bounds = IntRect(coordinates.positionInWindow().round(), coordinates.size)
}

/**
 * The list of a combo box's values, such as the presets of the sidebar: a
 * bottom sheet on a phone, which has no room for a list under the field, and
 * on a larger window the drop-down of OrcaSlicer's combo box, under the field
 * of [anchor] (or over it where the window ends) and at least as wide. Without
 * a field to drop down from it is a dialog. [title] heads the sheet and the
 * dialog; the drop-down hangs under its field and needs none.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrcaPickerSheet(
    anchor: OrcaPickerAnchor?,
    onDismissRequest: () -> Unit,
    title: String? = null,
    skipPartiallyExpanded: Boolean = false,
    /** The drop-down's least width, which a narrow field widens to. */
    minPopupWidth: Dp = MIN_POPUP_WIDTH,
    content: @Composable ColumnScope.() -> Unit,
) {
    val bounds = anchor?.bounds
    if (currentOrcaWindowLayout() == OrcaWindowLayout.Compact || bounds == null) {
        OrcaSheet(onDismissRequest = onDismissRequest, skipPartiallyExpanded = skipPartiallyExpanded) {
            title?.let { PickerTitle(it) }
            content()
        }
        return
    }
    val density = LocalDensity.current
    val view = LocalView.current
    val margin = with(density) { POPUP_MARGIN.roundToPx() }
    // The field's window may be a dialog in the middle of the screen: the room
    // around the field is measured on the screen the system bars leave.
    val room = remember(bounds, margin) { PopupRoom.around(view, bounds, margin) }
    val width = with(density) { maxOf(bounds.width.toDp(), minPopupWidth).coerceAtMost((room.right - room.left).coerceAtLeast(0).toDp()) }
    Popup(
        popupPositionProvider = remember(bounds, room) { AnchoredPosition(bounds, room) },
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true),
    ) {
        Surface(
            modifier = Modifier
                .width(width)
                // The drop-down takes the side of the field with the more room.
                .heightIn(max = with(density) { maxOf(room.above, room.below).coerceAtLeast(0).toDp() })
                .consumeWindowInsets(WindowInsets.safeDrawing),
            shape = OrcaTheme.shapes.control,
            color = OrcaTheme.colors.window,
            contentColor = OrcaTheme.colors.text,
            border = BorderStroke(1.dp, OrcaTheme.colors.border),
            shadowElevation = 8.dp,
        ) {
            Column(Modifier.padding(vertical = 4.dp), content = content)
        }
    }
}

/** The heading of a sheet or a dialog of values. */
@Composable
private fun PickerTitle(title: String) {
    Text(
        title,
        color = OrcaTheme.colors.text,
        style = OrcaTheme.typography.head16,
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .semantics { heading() },
    )
}

/**
 * The room a drop-down has around its field: the heights over and under it,
 * and the leftmost and rightmost place it may take, in the coordinates of the
 * field's window, within the part of the screen the system bars leave.
 */
private data class PopupRoom(val above: Int, val below: Int, val left: Int, val right: Int, val top: Int) {
    companion object {
        fun around(view: View, field: IntRect, margin: Int): PopupRoom {
            val frame = Rect().also(view::getWindowVisibleDisplayFrame)
            val origin = IntArray(2).also(view.rootView::getLocationOnScreen)
            return PopupRoom(
                above = field.top + origin[1] - frame.top - margin * 2,
                below = frame.bottom - field.bottom - origin[1] - margin * 2,
                left = frame.left - origin[0] + margin,
                right = frame.right - origin[0] - margin,
                top = frame.top - origin[1] + margin,
            )
        }
    }
}

/** Under the field, or over it when it does not fit under and has more room there; never past the screen's sides. */
private class AnchoredPosition(private val field: IntRect, private val room: PopupRoom) : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val start = if (layoutDirection == LayoutDirection.Ltr) field.left else field.right - popupContentSize.width
        val x = start.coerceIn(room.left, (room.right - popupContentSize.width).coerceAtLeast(room.left))
        val y = if (popupContentSize.height <= room.below || room.below >= room.above) {
            field.bottom
        } else {
            field.top - popupContentSize.height
        }
        return IntOffset(x, y.coerceAtLeast(room.top))
    }
}

private val DIALOG_MARGIN = 24.dp
private val MIN_POPUP_WIDTH = 280.dp
private val POPUP_MARGIN = 8.dp
