package app.orcinus.shadow.core.ui.settings

import app.orcinus.shadow.core.model.OrcaText
import app.orcinus.shadow.core.model.PresetKind
import app.orcinus.shadow.core.model.PresetSettings
import app.orcinus.shadow.core.model.SettingChoice
import app.orcinus.shadow.core.model.SettingControl
import app.orcinus.shadow.core.model.SettingDefinition
import app.orcinus.shadow.core.model.SettingState
import app.orcinus.shadow.core.model.SettingType
import app.orcinus.shadow.core.model.SettingWidget
import app.orcinus.shadow.core.model.SettingsGroup
import app.orcinus.shadow.core.model.SettingsLine
import app.orcinus.shadow.core.model.SettingsLineOption
import app.orcinus.shadow.core.model.SettingsMode
import app.orcinus.shadow.core.model.SettingsPage
import app.orcinus.shadow.core.model.SettingsTab
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsViewTest {
    @Test
    fun `a line shows when its first option is in the mode and the tab did not hide it`() {
        val view = view(SettingsMode.SIMPLE, state("wall_loops"), state("seam_gap"), state("spiral_mode_smooth", visible = false))

        assertTrue(view.isVisible(line("wall_loops")))
        // An advanced setting in simple mode.
        assertFalse(view.isVisible(line("seam_gap")))
        assertFalse(view.isVisible(line("spiral_mode_smooth")))
        assertTrue(view(SettingsMode.ADVANCED, state("seam_gap")).isVisible(line("seam_gap")))
        // A setting the preset does not have.
        assertFalse(view.isVisible(line("unknown")))
    }

    @Test
    fun `a line of several options shows unless the tab hid one of them`() {
        val speeds = SettingsLine(
            label = "Overhang speed",
            options = listOf(option("overhang_1_4_speed"), option("overhang_2_4_speed")),
        )

        assertTrue(view(SettingsMode.SIMPLE, state("overhang_1_4_speed"), state("overhang_2_4_speed")).isVisible(speeds))
        assertFalse(view(SettingsMode.SIMPLE, state("overhang_1_4_speed"), state("overhang_2_4_speed", visible = false)).isVisible(speeds))
    }

    @Test
    fun `one value of a vector setting is its own option`() {
        val line = SettingsLine(label = "Length", options = listOf(option("retraction_length", index = 0)))
        val view = view(SettingsMode.SIMPLE, state("retraction_length#0", key = "retraction_length"))

        assertTrue(view.isVisible(line))
        assertEquals("1", view.state(line.options.first())?.value)
    }

    @Test
    fun `groups and pages show when they have a visible line other than a separator`() {
        val hidden = SettingsGroup("Special mode", "param_special", listOf(line("spiral_mode_smooth"), SettingsLine(separator = true)))
        val shown = SettingsGroup("Walls", "param_wall", listOf(line("wall_loops")))
        val view = view(SettingsMode.SIMPLE, state("wall_loops"), state("spiral_mode_smooth", visible = false))

        assertFalse(view.isVisible(hidden))
        assertTrue(view.isVisible(shown))
        assertFalse(view.isVisible(page("Others", hidden)))
        assertTrue(view.isVisible(page("Strength", hidden, shown)))
    }

    @Test
    fun `a line of a widget alone shows`() {
        val bed = SettingsLine(label = "Printable space", widget = SettingWidget.BED_SHAPE)

        assertTrue(view(SettingsMode.SIMPLE).isVisible(bed))
    }

    @Test
    fun `the visible pages are the ones of the edited preset`() {
        val walls = page("Strength", SettingsGroup("Walls", "param_wall", listOf(line("wall_loops"))))
        val special = page("Others", SettingsGroup("Special mode", "param_special", listOf(line("spiral_mode_smooth"))))
        val view = view(SettingsMode.SIMPLE, state("wall_loops"), state("spiral_mode_smooth", visible = false), pages = listOf(walls, special))

        assertEquals(listOf(walls), view.visiblePages)
    }

    @Test
    fun `labels take the colour Tab decorate gives them`() {
        val view = view(SettingsMode.SIMPLE)

        assertEquals(SettingLabelColor.SYSTEM, view.labelColor(state("wall_loops")))
        assertEquals(SettingLabelColor.MODIFIED, view.labelColor(state("wall_loops", modified = true, system = false)))
        assertEquals(SettingLabelColor.DEFAULT, view.labelColor(state("wall_loops", system = false)))
        // A modified value that equals the parent's keeps the system colour.
        assertEquals(SettingLabelColor.SYSTEM, view.labelColor(state("wall_loops", modified = true)))
        assertEquals(SettingLabelColor.DEFAULT, view(SettingsMode.SIMPLE, default = true).labelColor(state("wall_loops")))
    }

    @Test
    fun `fields follow OptionsGroup build_field, and the tab's entries replace the definition's`() {
        val choices = listOf(SettingChoice("default", "Default"), SettingChoice("grid", "Grid"))

        assertEquals(SettingFieldKind.CHECK_BOX, SettingsView.fieldKind(definition("spiral_mode", SettingType.BOOL), state("spiral_mode")))
        assertEquals(SettingFieldKind.NUMBER, SettingsView.fieldKind(definition("layer_height", SettingType.FLOAT), state("layer_height")))
        assertEquals(SettingFieldKind.CHOICE, SettingsView.fieldKind(definition("seam_position", SettingType.ENUM), state("seam_position")))
        assertEquals(
            SettingFieldKind.OPEN_CHOICE,
            SettingsView.fieldKind(definition("support_top_z_distance", SettingType.FLOAT, SettingControl.FLOAT_ENUM_OPEN), state("support_top_z_distance")),
        )
        assertEquals(
            SettingFieldKind.CHOICE,
            SettingsView.fieldKind(definition("support_filament", SettingType.INT, SettingControl.INT_ENUM_OPEN), state("support_filament", choices = choices)),
        )
        assertEquals(SettingFieldKind.TEXT, SettingsView.fieldKind(definition("post_process", SettingType.STRINGS), state("post_process")))
        assertEquals(SettingFieldKind.POINT, SettingsView.fieldKind(definition("printable_area", SettingType.POINTS), state("printable_area")))
        assertEquals(
            SettingFieldKind.COLOR,
            SettingsView.fieldKind(definition("filament_colour", SettingType.STRINGS, SettingControl.COLOR), state("filament_colour")),
        )
        assertEquals(
            SettingFieldKind.LEGEND,
            SettingsView.fieldKind(definition("cooling_tube_length", SettingType.FLOAT, SettingControl.LEGEND), state("cooling_tube_length")),
        )
        assertEquals(choices, view(SettingsMode.SIMPLE).choices(definition("support_style", SettingType.ENUM), state("support_style", choices = choices)))
    }

    @Test
    fun `an option takes the label its line gave it, and spans the width when either says so`() {
        val definition = definition("bed_custom_texture", SettingType.STRING)
        val named = option("bed_custom_texture", label = "Texture")
        val view = view(SettingsMode.SIMPLE)

        assertEquals("Texture", view.label(definition, named))
        assertEquals("bed_custom_texture", view.label(definition, option("bed_custom_texture")))
        assertTrue(SettingsView.isFullWidth(definition, named.copy(fullWidth = true), SettingFieldKind.TEXT))
        assertTrue(SettingsView.isFullWidth(definition, named.copy(multiline = true), SettingFieldKind.TEXT))
        assertFalse(SettingsView.isFullWidth(definition, named, SettingFieldKind.TEXT))
    }

    private fun view(
        mode: SettingsMode,
        vararg states: SettingState,
        default: Boolean = false,
        pages: List<SettingsPage> = emptyList(),
    ) = SettingsView(
        SettingsTab(
            kind = PresetKind.PRINT,
            definitions = listOf(
                definition("wall_loops", SettingType.INT),
                definition("seam_gap", SettingType.FLOAT_OR_PERCENT, mode = SettingsMode.ADVANCED),
                definition("spiral_mode_smooth", SettingType.BOOL),
                definition("overhang_1_4_speed", SettingType.FLOAT_OR_PERCENT),
                definition("overhang_2_4_speed", SettingType.FLOAT_OR_PERCENT),
                definition("retraction_length", SettingType.FLOATS),
            ).associateBy(SettingDefinition::key),
        ),
        PresetSettings(
            kind = PresetKind.PRINT,
            preset = "0.20mm Standard",
            label = "0.20mm Standard",
            dirty = false,
            isDefault = default,
            isSystem = !default,
            hasParent = true,
            canDelete = false,
            mode = mode,
            pages = pages,
            activePage = pages.firstOrNull()?.title.orEmpty(),
            settings = states.toList(),
            saveName = "0.20mm Standard",
            saveNameCopySuffix = true,
        ),
    )

    private fun line(key: String) = SettingsLine(options = listOf(option(key)))

    private fun option(key: String, index: Int = -1, label: String = "") =
        SettingsLineOption(id = if (index < 0) key else "$key#$index", key = key, index = index, label = label)

    private fun page(title: String, vararg groups: SettingsGroup) = SettingsPage(title, listOf(OrcaText(title)), "custom-gcode_$title", groups.toList())

    private fun state(
        id: String,
        key: String = id,
        modified: Boolean = false,
        system: Boolean = true,
        visible: Boolean = true,
        choices: List<SettingChoice>? = null,
    ) = SettingState(id, key, "1", modified, system, enabled = true, visible = visible, choices = choices)

    private fun definition(
        key: String,
        type: SettingType,
        control: SettingControl = SettingControl.DEFAULT,
        mode: SettingsMode = SettingsMode.SIMPLE,
    ) = SettingDefinition(
        key = key,
        type = type,
        label = key,
        sidetext = "",
        category = "",
        mode = mode,
        control = control,
        enumValues = emptyList(),
        enumLabels = emptyList(),
        multiline = false,
        fullWidth = false,
        isCode = false,
        height = -1,
    )
}
