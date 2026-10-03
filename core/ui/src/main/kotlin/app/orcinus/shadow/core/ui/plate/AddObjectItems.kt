package app.orcinus.shadow.core.ui.plate

import androidx.compose.runtime.Composable
import app.orcinus.shadow.core.designsystem.component.OrcaMenuItem
import app.orcinus.shadow.core.designsystem.component.OrcaSubmenu
import app.orcinus.shadow.core.model.HandyModel
import app.orcinus.shadow.core.ui.orca.orcaString

/** The shapes of "Add Primitive" (MenuFactory::append_submenu_add_generic), in its order. */
private val PRIMITIVES = listOf("Cube", "Cylinder", "Sphere", "Cone", "Disc", "Torus")

/**
 * Add Primitive, Add Handy models and Add Models, which the canvas menu
 * (MenuFactory::default_menu) and the plate menu of the object list
 * (create_plate_menu) offer. A primitive is added with its translated name
 * (ObjectList::load_shape_object); text and SVG shapes come with the text and
 * SVG tools.
 */
@Composable
fun AddObjectItems(
    enabled: Boolean,
    dismiss: () -> Unit,
    addPrimitive: (shape: String, name: String) -> Unit,
    addHandyModel: (HandyModel) -> Unit,
    addModels: () -> Unit,
    /** append_menu_item_add_text() of the shapes' submenu: an object of a text; null where the canvas places none. */
    addText: (() -> Unit)? = null,
) {
    OrcaSubmenu(text = orcaString("Add Primitive"), enabled = enabled) {
        PRIMITIVES.forEach { shape ->
            val name = orcaString(shape)
            OrcaMenuItem(
                text = name,
                enabled = enabled,
                onClick = {
                    dismiss()
                    addPrimitive(shape, name)
                },
            )
        }
        addText?.let { add ->
            OrcaMenuItem(
                text = orcaString("Text"),
                enabled = enabled,
                onClick = {
                    dismiss()
                    add()
                },
            )
        }
    }
    OrcaSubmenu(text = orcaString("Add Handy models"), enabled = enabled) {
        HandyModel.entries.forEach { model ->
            OrcaMenuItem(
                text = orcaString(model.label),
                enabled = enabled,
                onClick = {
                    dismiss()
                    addHandyModel(model)
                },
            )
        }
    }
    OrcaMenuItem(
        text = orcaString("Add Models"),
        enabled = enabled,
        onClick = {
            dismiss()
            addModels()
        },
    )
}
