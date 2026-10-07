package app.orcinus.shadow

import android.view.KeyEvent
import android.view.KeyboardShortcutGroup
import android.view.KeyboardShortcutInfo
import app.orcinus.shadow.core.model.KeyPress
import app.orcinus.shadow.core.model.OrcaKeyboardShortcuts
import app.orcinus.shadow.core.model.ShortcutKey
import app.orcinus.shadow.core.model.ShortcutKeys
import app.orcinus.shadow.core.ui.orca.OrcaCatalog

/**
 * The key of an Android key event as OrcaSlicer's shortcuts name it, or null
 * for one they do not use. The keys that type are read as the keyboard's
 * layout types them, so '?' and '+' are found wherever they lie, and a letter
 * where its layout puts it; a key that types no Latin letter, such as on a
 * Cyrillic layout, goes by the key's own letter, as Ctrl+S does.
 */
internal fun KeyEvent.toKeyPress(): KeyPress? {
    val down = when (action) {
        KeyEvent.ACTION_DOWN -> true
        KeyEvent.ACTION_UP -> false
        else -> return null
    }
    val key = shortcutKey() ?: return null
    return KeyPress(key, down = down, ctrl = isCtrlPressed, shift = isShiftPressed, alt = isAltPressed, repeat = repeatCount)
}

private fun KeyEvent.shortcutKey(): ShortcutKey? {
    NAMED_KEYS[keyCode]?.let { return it }
    if (keyCode in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 && isNumLockOn) return ShortcutKey.ofDigit(keyCode - KeyEvent.KEYCODE_NUMPAD_0)
    // The character the key types with Shift alone, as Ctrl and Alt change what a layout gives.
    val typed = getUnicodeChar(metaState and (KeyEvent.META_CTRL_MASK or KeyEvent.META_ALT_MASK or KeyEvent.META_META_MASK).inv()).toChar()
    when (typed) {
        '?' -> return ShortcutKey.QUESTION_MARK
        '+' -> return ShortcutKey.PLUS
        '-' -> return ShortcutKey.MINUS
        in '0'..'9' -> return ShortcutKey.ofDigit(typed - '0')
        in 'a'..'z' -> return ShortcutKey.entries[typed - 'a']
        in 'A'..'Z' -> return ShortcutKey.entries[typed - 'A']
    }
    // A digit key typing something else (Shift+1 is '!') is no digit; with Ctrl it is.
    if (keyCode in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 && isCtrlPressed) return ShortcutKey.ofDigit(keyCode - KeyEvent.KEYCODE_0)
    if (keyCode in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z && (typed.code == 0 || typed.code > 0x7F)) return ShortcutKey.entries[keyCode - KeyEvent.KEYCODE_A]
    return null
}

private val NAMED_KEYS = mapOf(
    KeyEvent.KEYCODE_TAB to ShortcutKey.TAB,
    KeyEvent.KEYCODE_FORWARD_DEL to ShortcutKey.DELETE,
    KeyEvent.KEYCODE_DEL to ShortcutKey.BACKSPACE,
    KeyEvent.KEYCODE_ESCAPE to ShortcutKey.ESCAPE,
    KeyEvent.KEYCODE_DPAD_UP to ShortcutKey.UP,
    KeyEvent.KEYCODE_DPAD_DOWN to ShortcutKey.DOWN,
    KeyEvent.KEYCODE_DPAD_LEFT to ShortcutKey.LEFT,
    KeyEvent.KEYCODE_DPAD_RIGHT to ShortcutKey.RIGHT,
    KeyEvent.KEYCODE_MOVE_HOME to ShortcutKey.HOME,
    KeyEvent.KEYCODE_MOVE_END to ShortcutKey.END,
    KeyEvent.KEYCODE_PAGE_UP to ShortcutKey.PAGE_UP,
    KeyEvent.KEYCODE_PAGE_DOWN to ShortcutKey.PAGE_DOWN,
    KeyEvent.KEYCODE_NUMPAD_ADD to ShortcutKey.PLUS,
    KeyEvent.KEYCODE_NUMPAD_SUBTRACT to ShortcutKey.MINUS,
)

/**
 * KBShortcutsDialog's pages for Android's list of the app's shortcuts
 * (Meta+/): the rows a key combination names, in OrcaSlicer's words through
 * [catalog]; the mouse's rows and the keys named in words stay in the app's own list.
 */
internal fun systemShortcutGroups(catalog: OrcaCatalog): List<KeyboardShortcutGroup> = OrcaKeyboardShortcuts.groups.mapNotNull { group ->
    val items = group.rows.mapNotNull { row ->
        val combo = row.keys as? ShortcutKeys.Combo ?: return@mapNotNull null
        val label = catalog.translate(row.description)
        val modifiers = (if (combo.ctrl) KeyEvent.META_CTRL_ON else 0) or
            (if (combo.shift) KeyEvent.META_SHIFT_ON else 0) or
            (if (combo.alt) KeyEvent.META_ALT_ON else 0)
        when (combo.key) {
            ShortcutKey.QUESTION_MARK -> KeyboardShortcutInfo(label, '?', modifiers)
            ShortcutKey.PLUS -> KeyboardShortcutInfo(label, '+', modifiers)
            ShortcutKey.MINUS -> KeyboardShortcutInfo(label, '-', modifiers)
            else -> keyCodeOf(combo.key)?.let { KeyboardShortcutInfo(label, it, modifiers) }
        }
    }
    items.takeIf { it.isNotEmpty() }?.let { KeyboardShortcutGroup(catalog.translate(group.title), it) }
}

private fun keyCodeOf(key: ShortcutKey): Int? = when {
    key.letter -> KeyEvent.KEYCODE_A + key.ordinal
    key.digit != null -> KeyEvent.KEYCODE_0 + key.digit!!
    else -> NAMED_KEYS.entries.firstOrNull { it.value == key }?.key
}
