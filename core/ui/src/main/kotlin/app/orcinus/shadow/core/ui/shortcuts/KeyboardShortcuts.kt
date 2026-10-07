package app.orcinus.shadow.core.ui.shortcuts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.focus.onFocusChanged
import app.orcinus.shadow.core.model.CanvasTool
import app.orcinus.shadow.core.model.KeyPress
import app.orcinus.shadow.core.model.ShortcutAction
import app.orcinus.shadow.core.model.ShortcutContext
import app.orcinus.shadow.core.model.ShortcutKey
import app.orcinus.shadow.core.model.ShortcutPage

/** What the Prepare page's canvas shows that decides where a key goes (see [ShortcutContext]). */
data class CanvasShortcutState(
    val tool: CanvasTool? = null,
    val selected: Boolean = false,
    val layerEditing: Boolean = false,
    val arrangeOpen: Boolean = false,
    val assemblyView: Boolean = false,
)

/** What a page does with a shortcut's action; true when it did something with it. */
fun interface ShortcutTarget {
    fun handle(action: ShortcutAction): Boolean
}

/**
 * The app's keyboard, as OrcaSlicer's main window has it: the activity hands
 * every key over before the views see it, [resolve] tells what it asks for
 * where it is pressed, and the pages shown do it. A page adds its
 * [ShortcutTarget] while it is shown, the latest first; a key none of them
 * does goes on to the views, as an unhandled key does on the desktop.
 */
class KeyboardShortcuts(private val resolve: (KeyPress, ShortcutContext) -> ShortcutAction?) {
    /** The workspace's tab shown. */
    var page: ShortcutPage = ShortcutPage.OTHER

    /** A page stands over the workspace, which keeps its keys. */
    var covered: Boolean = false

    /** What the Prepare page's canvas shows; the default while it is not shown. */
    var prepareCanvas: CanvasShortcutState = CanvasShortcutState()

    private val focusedCanvases = HashSet<ShortcutPage>()
    private val handlers = ArrayList<ShortcutTarget>()

    /** The keys whose press a page took, whose release it takes too. */
    private val heldKeys = HashSet<ShortcutKey>()

    /** Where a key goes now, with a text field taking the keys when [typing]. */
    fun context(typing: Boolean): ShortcutContext {
        val prepare = page == ShortcutPage.PREPARE
        val canvas = if (prepare) prepareCanvas else CanvasShortcutState()
        return ShortcutContext(
            page = page,
            covered = covered,
            typing = typing,
            canvasFocused = page in focusedCanvases,
            tool = canvas.tool,
            selected = canvas.selected,
            layerEditing = canvas.layerEditing,
            arrangeOpen = canvas.arrangeOpen,
            assemblyView = canvas.assemblyView,
        )
    }

    /** A key went down or up; true when a page took it, which then goes no further. */
    fun dispatch(press: KeyPress, typing: Boolean): Boolean {
        val action = resolve(press, context(typing))
        if (!press.down) {
            action?.let(::perform)
            return heldKeys.remove(press.key)
        }
        val handled = action != null && (action == ShortcutAction.Swallowed || perform(action))
        if (handled) heldKeys += press.key
        return handled
    }

    /** Does [action] as the pages shown do it, as a menu item does: true when one did. */
    fun perform(action: ShortcutAction): Boolean = handlers.toList().asReversed().any { it.handle(action) }

    fun add(handler: ShortcutTarget) {
        handlers += handler
    }

    fun remove(handler: ShortcutTarget) {
        handlers -= handler
    }

    internal fun setCanvasFocused(page: ShortcutPage, focused: Boolean) {
        if (focused) focusedCanvases += page else focusedCanvases -= page
    }
}

/** The app's keyboard; null in previews and tests, where no key is handed over. */
val LocalKeyboardShortcuts = staticCompositionLocalOf<KeyboardShortcuts?> { null }

/** [handle] does the shortcuts' actions while this is composed; the latest composed first. */
@Composable
fun ShortcutHandler(enabled: Boolean = true, handle: (ShortcutAction) -> Boolean) {
    val shortcuts = LocalKeyboardShortcuts.current ?: return
    val current by rememberUpdatedState(handle)
    DisposableEffect(shortcuts, enabled) {
        if (!enabled) return@DisposableEffect onDispose {}
        val handler = ShortcutTarget { action -> current(action) }
        shortcuts.add(handler)
        onDispose { shortcuts.remove(handler) }
    }
}

/**
 * The 3D canvas of a page as a holder of the keyboard focus (GLCanvas3D's
 * wxGLCanvas): it takes the focus as the page shows and whenever the canvas
 * is pressed (on_mouse()'s SetFocus() on a button down), and while it holds
 * it, the canvas's navigation keys are its own.
 */
@Stable
class ShortcutCanvas internal constructor(internal val page: ShortcutPage, internal val shortcuts: KeyboardShortcuts?) {
    internal val requester = FocusRequester()

    /** m_canvas->SetFocus(); nothing while the canvas is not laid out. */
    fun requestFocus() {
        runCatching { requester.requestFocus() }
    }
}

/** The canvas of the tab [page], which takes the focus as it shows. */
@Composable
fun rememberShortcutCanvas(page: ShortcutPage): ShortcutCanvas {
    val shortcuts = LocalKeyboardShortcuts.current
    val canvas = remember(page, shortcuts) { ShortcutCanvas(page, shortcuts) }
    LaunchedEffect(canvas) { canvas.requestFocus() }
    DisposableEffect(canvas) { onDispose { shortcuts?.setCanvasFocused(page, false) } }
    return canvas
}

/** Makes the canvas's root the focus target [canvas] stands for; the canvas's windows and fields lie inside it. */
fun Modifier.shortcutCanvas(canvas: ShortcutCanvas): Modifier = this
    .focusRequester(canvas.requester)
    .onFocusChanged { canvas.shortcuts?.setCanvasFocused(canvas.page, it.isFocused) }
    .focusTarget()

/** The Prepare page tells the keyboard what its canvas shows, for as long as it is shown. */
@Composable
fun ReportCanvasShortcutState(state: CanvasShortcutState) {
    val shortcuts = LocalKeyboardShortcuts.current ?: return
    SideEffect { shortcuts.prepareCanvas = state }
    DisposableEffect(shortcuts) { onDispose { shortcuts.prepareCanvas = CanvasShortcutState() } }
}
