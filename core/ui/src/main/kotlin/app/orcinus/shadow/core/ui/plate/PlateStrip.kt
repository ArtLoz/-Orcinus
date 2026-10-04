package app.orcinus.shadow.core.ui.plate

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.stateDescription
import app.orcinus.shadow.core.ui.R
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.orcinus.shadow.core.designsystem.R as DesignR
import app.orcinus.shadow.core.designsystem.component.OrcaButton
import app.orcinus.shadow.core.designsystem.component.OrcaButtonStyle
import app.orcinus.shadow.core.designsystem.component.OrcaCanvasToolbar
import app.orcinus.shadow.core.designsystem.component.OrcaContextMenu
import app.orcinus.shadow.core.designsystem.component.OrcaMenuItem
import app.orcinus.shadow.core.designsystem.component.OrcaTextField
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import app.orcinus.shadow.core.ui.orca.orcaString

/**
 * The plates of the project under the thumb. OrcaSlicer writes every plate's
 * number beside it (PartPlate::render_only_numbers) and draws its actions as
 * icons over it (render_icons), which a mouse hovers; on a phone the plates
 * are small and the icons smaller, so the numbers stand in a strip, a locked
 * plate's with the lock its icon shows. A tap on one selects the plate, as a
 * click on the plate does; holding one opens the plate's [actions], as its
 * icons offer them.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlateStrip(
    count: Int,
    current: Int,
    onSelect: (Int) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    /** The locked plates, by index (PartPlate::is_locked). */
    locked: Set<Int> = emptySet(),
    /** An item before the plates, such as the preview's all plates stats item. */
    leading: (@Composable () -> Unit)? = null,
    /** The actions of the plate at an index; [dismiss] closes them. */
    actions: (@Composable ColumnScope.(index: Int, dismiss: () -> Unit) -> Unit)? = null,
) {
    val colors = OrcaTheme.colors
    var menu by remember { mutableStateOf<Int?>(null) }
    val lockedState = stringResource(R.string.plate_locked)
    OrcaCanvasToolbar(modifier) {
        leading?.invoke()
        repeat(count) { index ->
            val selected = index == current
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(horizontal = 2.dp, vertical = 4.dp)
                    .size(OrcaTheme.dimensions.minimumTouchTarget - 8.dp)
                    .background(if (selected) colors.accentSelected else Color.Transparent, OrcaTheme.shapes.control)
                    .border(1.dp, if (selected) colors.accent else Color.Transparent, OrcaTheme.shapes.control)
                    .semantics {
                        this.selected = selected
                        if (index in locked) stateDescription = lockedState
                    }
                    .combinedClickable(
                        enabled = enabled,
                        role = Role.Tab,
                        onClick = { if (!selected) onSelect(index) },
                        onLongClick = actions?.let { { menu = index } },
                    ),
            ) {
                Text(
                    // GLTexture::generate_from_text_string() for PartPlateList::m_idx_textures.
                    text = if (index < 9) "0${index + 1}" else "${index + 1}",
                    color = if (enabled) colors.onCanvasPanel else colors.textDisabledOnBox,
                    style = OrcaTheme.typography.head15,
                )
                if (index in locked) {
                    Icon(
                        painter = painterResource(DesignR.drawable.orca_plate_locked),
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(12.dp),
                    )
                }
                if (menu == index && actions != null) {
                    OrcaContextMenu(expanded = true, position = IntOffset.Zero, onDismissRequest = { menu = null }) {
                        actions(index) { menu = null }
                    }
                }
            }
        }
    }
}

/** What a plate's icons do (Plater::select_plate_by_hover_id), and whether each can. */
class PlateIconActions(
    val delete: () -> Unit,
    val orient: () -> Unit,
    val arrange: () -> Unit,
    val lock: () -> Unit,
    val settings: () -> Unit,
    val moveToFront: () -> Unit,
    val rename: () -> Unit,
)

/**
 * The icons over a plate (PartPlate::render_icons) as a menu, with their
 * tooltips: remove, orient, arrange, lock, move to the front, and the name's
 * edit icon. [locked] shows the lock's state; orienting and arranging are
 * always there, as the desktop icons are, and warn on a locked plate;
 * removing needs a plate that is not the last one, and moving one that is not
 * the first.
 */
@Composable
fun PlateMenuItems(
    actions: PlateIconActions,
    dismiss: () -> Unit,
    enabled: Boolean,
    locked: Boolean,
    deletable: Boolean,
    first: Boolean,
    /** The plate has settings of its own, which its settings icon shows. */
    customized: Boolean,
) {
    @Composable
    fun item(icon: Int, text: String, isEnabled: Boolean, action: () -> Unit) = OrcaMenuItem(
        text = orcaString(text),
        onClick = {
            dismiss()
            action()
        },
        enabled = enabled && isEnabled,
        leading = { Icon(painterResource(icon), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(OrcaTheme.dimensions.iconSmall)) },
    )
    item(DesignR.drawable.orca_plate_close, "Remove current plate (if not last one)", deletable, actions.delete)
    item(DesignR.drawable.orca_plate_orient, "Auto orient objects on current plate", enabled, actions.orient)
    item(DesignR.drawable.orca_plate_arrange, "Arrange objects on current plate", enabled, actions.arrange)
    if (locked) {
        item(DesignR.drawable.orca_plate_locked, "Unlock current plate", true, actions.lock)
    } else {
        item(DesignR.drawable.orca_plate_unlocked, "Lock current plate", true, actions.lock)
    }
    item(if (customized) DesignR.drawable.orca_plate_settings_changed else DesignR.drawable.orca_plate_settings, "Customize current plate", true, actions.settings)
    item(DesignR.drawable.orca_plate_move_front, "Move plate to the front", !first, actions.moveToFront)
    item(DesignR.drawable.orca_plate_name_edit, "Edit current plate name", true, actions.rename)
}

/** PlateNameEditDialog: the plate's name, at most 250 characters, taken with OK. */
@Composable
fun PlateNameDialog(name: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val colors = OrcaTheme.colors
    var text by rememberSaveable { mutableStateOf(name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { OrcaButton(orcaString("OK"), onClick = { onConfirm(text) }) },
        dismissButton = { OrcaButton(orcaString("Cancel"), onClick = onDismiss, style = OrcaButtonStyle.Regular) },
        title = { Text(orcaString("Edit Plate Name"), style = OrcaTheme.typography.head16) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(orcaString("Plate name"), style = OrcaTheme.typography.body14)
                OrcaTextField(
                    value = text,
                    onValueChange = { value -> text = value.take(PLATE_NAME_LENGTH) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onConfirm(text) }),
                )
            }
        },
        containerColor = colors.window,
        titleContentColor = colors.text,
        textContentColor = colors.text,
        shape = OrcaTheme.shapes.window,
    )
}

/** The length PlateNameEditDialog's field takes. */
private const val PLATE_NAME_LENGTH = 250
