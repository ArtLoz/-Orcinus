package app.orcinus.shadow.core.ui.settings

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

/** The label colours of GUI_App: which one a setting's label takes (Tab::decorate). */
enum class SettingLabelColor { SYSTEM, MODIFIED, DEFAULT }

/** How a setting's field edits its value (OptionsGroup::build_field). */
enum class SettingFieldKind {
    /** CheckBox */
    CHECK_BOX,

    /** Choice of the enum's values. */
    CHOICE,

    /** Choice that also takes a typed value. */
    OPEN_CHOICE,

    /** TextCtrl or SpinCtrl for a number. */
    NUMBER,

    /** TextCtrl for text, on one line or several. */
    TEXT,

    /** ColourPicker */
    COLOR,

    /** PointCtrl: the x and y of a point. */
    POINT,

    /** A label that only shows the value (LEGEND). */
    LEGEND,

    /** A control the app does not offer yet. */
    UNSUPPORTED,
}

/** The tab's visible content for the edited preset, as ParamsPanel shows it. */
class SettingsView(
    val tab: SettingsTab,
    val settings: PresetSettings,
) {
    private val states: Map<String, SettingState> = settings.settings.associateBy(SettingState::id)

    fun state(option: SettingsLineOption): SettingState? = states[option.id]

    fun definition(option: SettingsLineOption): SettingDefinition? = tab.definitions[option.key]

    /**
     * OG_CustomCtrl::CtrlLine::update_visibility(): the line's first option is
     * in the mode, and ConfigManipulation did not hide the line.
     */
    fun isVisible(line: SettingsLine): Boolean {
        if (line.separator) return true
        if (line.widget != SettingWidget.NONE && line.options.isEmpty()) return true
        val first = line.options.firstOrNull()?.let(::definition) ?: return false
        return first.mode.ordinal <= settings.mode.ordinal && line.options.all { states[it.id]?.visible == true }
    }

    /** OG_CustomCtrl::update_visibility(): a group shows when a line other than a separator does. */
    fun isVisible(group: SettingsGroup): Boolean = group.lines.any { !it.separator && isVisible(it) }

    /** Page::update_visibility(): a page shows when one of its groups does. */
    fun isVisible(page: SettingsPage): Boolean = page.groups.any(::isVisible)

    /** The pages of the tab for this preset, without the ones every line of which is hidden. */
    val visiblePages: List<SettingsPage> get() = settings.pages.filter(::isVisible)

    /** Tab::decorate() */
    fun labelColor(state: SettingState): SettingLabelColor = when {
        !state.system -> if (state.modified) SettingLabelColor.MODIFIED else SettingLabelColor.DEFAULT
        settings.isDefault -> SettingLabelColor.DEFAULT
        else -> SettingLabelColor.SYSTEM
    }

    /** The entries of a combo box: the ones the tab sets, or the definition's. */
    fun choices(definition: SettingDefinition, state: SettingState): List<SettingChoice> =
        state.choices ?: definition.enumValues.mapIndexed { index, value ->
            SettingChoice(value, definition.enumLabels.getOrElse(index) { value })
        }

    /** The label of an option: the one its line gave it, or the definition's. */
    fun label(definition: SettingDefinition, option: SettingsLineOption): String =
        option.label.ifEmpty { definition.label }

    companion object {
        /** OptionsGroup::build_field() */
        fun fieldKind(definition: SettingDefinition, state: SettingState): SettingFieldKind = when {
            state.choices != null -> SettingFieldKind.CHOICE
            definition.control == SettingControl.INT_ENUM_OPEN || definition.control == SettingControl.FLOAT_ENUM_OPEN ||
                definition.control == SettingControl.SELECT_OPEN -> SettingFieldKind.OPEN_CHOICE
            definition.control == SettingControl.ONE_STRING -> SettingFieldKind.TEXT
            definition.control == SettingControl.COLOR -> SettingFieldKind.COLOR
            definition.control == SettingControl.LEGEND -> SettingFieldKind.LEGEND
            definition.control != SettingControl.DEFAULT -> SettingFieldKind.UNSUPPORTED
            else -> when (definition.type) {
                SettingType.BOOL, SettingType.BOOLS -> SettingFieldKind.CHECK_BOX
                SettingType.ENUM, SettingType.ENUMS -> SettingFieldKind.CHOICE
                SettingType.FLOAT, SettingType.FLOATS, SettingType.PERCENT, SettingType.PERCENTS,
                SettingType.FLOAT_OR_PERCENT, SettingType.FLOATS_OR_PERCENTS, SettingType.INT, SettingType.INTS -> SettingFieldKind.NUMBER
                SettingType.STRING, SettingType.STRINGS -> SettingFieldKind.TEXT
                SettingType.POINT, SettingType.POINTS, SettingType.POINT3 -> SettingFieldKind.POINT
                else -> SettingFieldKind.UNSUPPORTED
            }
        }

        /** A text field spans several lines: its definition or the tab's layout says so. */
        fun isMultiline(definition: SettingDefinition, option: SettingsLineOption): Boolean =
            option.multiline || definition.multiline

        fun isCode(definition: SettingDefinition, option: SettingsLineOption): Boolean = option.isCode || definition.isCode

        /** A field spans the width under its label: its definition, the tab's layout, or the text's height says so. */
        fun isFullWidth(definition: SettingDefinition, option: SettingsLineOption, kind: SettingFieldKind): Boolean =
            option.fullWidth || definition.fullWidth || (kind == SettingFieldKind.TEXT && isMultiline(definition, option)) ||
                // A list of points is a long text.
                (kind == SettingFieldKind.POINT && definition.type != SettingType.POINT)
    }
}

/** The label of a mode switch position (ModeSwitchButton): simple, advanced, expert. */
val SettingsMode.switchPosition: Int
    get() = when (this) {
        SettingsMode.SIMPLE -> 0
        SettingsMode.ADVANCED -> 1
        SettingsMode.EXPERT, SettingsMode.DEVELOP -> 2
    }

fun settingsModeAt(position: Int): SettingsMode = when (position) {
    0 -> SettingsMode.SIMPLE
    1 -> SettingsMode.ADVANCED
    else -> SettingsMode.EXPERT
}
