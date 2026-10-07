package app.orcinus.shadow.core.ui.plate

import androidx.compose.runtime.Composable
import app.orcinus.shadow.core.designsystem.component.OrcaMenuItem
import app.orcinus.shadow.core.designsystem.component.OrcaMenuSeparator
import app.orcinus.shadow.core.model.HandyModel
import app.orcinus.shadow.core.ui.orca.orcaString

/**
 * What the menu of a plate shows: the plate at [index], whether it is the
 * current plate the items act on ([current], false while the plate cannot be
 * changed), whether a copy stands on it ([occupied], PartPlate::get_objects),
 * and whether it is [locked]; [deletable] for Plater::can_delete_plate(),
 * [anyObjects] for "Select All Plates", and [enabled] while the plate can be
 * changed.
 */
data class PartPlateMenuState(
    val index: Int,
    val current: Boolean,
    val occupied: Boolean,
    val locked: Boolean,
    val deletable: Boolean,
    val anyObjects: Boolean,
    val enabled: Boolean,
)

/** What the items of a plate's menu do; the plate's items act on the current plate. */
class PartPlateMenuActions(
    val selectPlateObjects: () -> Unit,
    val selectAllPlates: () -> Unit,
    val deletePlateObjects: () -> Unit,
    val arrangePlate: (Int) -> Unit,
    val reloadAll: () -> Unit,
    val orientPlate: (Int) -> Unit,
    val deletePlate: (Int) -> Unit,
    val addPrimitive: (shape: String, name: String) -> Unit,
    val addHandyModel: (HandyModel) -> Unit,
    val addModels: () -> Unit,
    val replaceAllOnPlate: (Int) -> Unit,
    val lockPlate: (Int) -> Unit,
    /** "Edit Plate Name": the caller asks for the name. */
    val rename: (Int) -> Unit,
    /** The text and SVG of "Add Primitive" (append_submenu_add_generic()); null where none is placed. */
    val addText: (() -> Unit)? = null,
    val addSvg: (() -> Unit)? = null,
)

/**
 * The menu of a plate, which the object list's plate item and a finger held on
 * a plate in the 3D view open (MenuFactory::create_plate_menu() and the lock
 * and name items plate_menu() adds). Its items act on the current plate, which
 * the plate is while the menu is open; the ones that need objects need a copy
 * on the plate. Arranging and orienting a locked plate only tells the desktop
 * user that it is locked, so here they are not offered.
 */
@Composable
fun PartPlateMenuItems(state: PartPlateMenuState, actions: PartPlateMenuActions, dismiss: () -> Unit) {
    val index = state.index
    val current = state.current
    @Composable
    fun item(text: String, isEnabled: Boolean, action: () -> Unit) = OrcaMenuItem(
        text = text,
        enabled = isEnabled,
        onClick = {
            dismiss()
            action()
        },
    )
    item(orcaString("Select All"), current && state.occupied, actions.selectPlateObjects)
    item(orcaString("Select All Plates"), state.enabled && state.anyObjects, actions.selectAllPlates)
    item(orcaString("Delete All"), current && state.occupied, actions.deletePlateObjects)
    item(orcaString("Arrange"), current && state.occupied && !state.locked) { actions.arrangePlate(index) }
    item(orcaString("Reload All"), current && state.occupied, actions.reloadAll)
    item(orcaString("Auto Rotate"), current && state.occupied && !state.locked) { actions.orientPlate(index) }
    item(orcaString("Delete Plate"), current && state.deletable) { actions.deletePlate(index) }
    OrcaMenuSeparator()
    AddObjectItems(
        enabled = state.enabled,
        dismiss = dismiss,
        addPrimitive = actions.addPrimitive,
        addHandyModel = actions.addHandyModel,
        addModels = actions.addModels,
        addText = actions.addText,
        addSvg = actions.addSvg,
    )
    item(orcaString("Replace all with 3D files") + "...", current) { actions.replaceAllOnPlate(index) }
    item(orcaString(if (state.locked) "Unlock" else "Lock"), current) { actions.lockPlate(index) }
    item(orcaString("Edit Plate Name"), current) { actions.rename(index) }
}
