package app.orcinus.shadow.domain.plate

import app.orcinus.shadow.core.model.ModelSettings
import app.orcinus.shadow.core.model.PlateObject
import app.orcinus.shadow.core.model.PlateState
import app.orcinus.shadow.core.model.PresetKind
import app.orcinus.shadow.core.model.PresetSettings
import app.orcinus.shadow.core.model.SettingState
import app.orcinus.shadow.core.model.SettingsMode
import app.orcinus.shadow.core.model.SettingsTabState
import kotlin.test.Test
import kotlin.test.assertEquals

class SupportThresholdAngleTest {
    @Test
    fun `the support painting highlights from the threshold angle of auto supports, the object settings over the process ones`() {
        val plate = plateWith("enable_support" to "1", "support_type" to "tree(auto)", "support_threshold_angle" to "30")
        assertEquals(30, plate.supportThresholdAngle(cube()))
        assertEquals(45, plate.supportThresholdAngle(cube("support_threshold_angle" to "45")))
        // is_auto(): manual supports, or none, highlight nothing.
        assertEquals(0, plate.supportThresholdAngle(cube("support_type" to "normal(manual)")))
        assertEquals(0, plate.supportThresholdAngle(cube("enable_support" to "0")))
        assertEquals(0, plateWith("enable_support" to "0", "support_type" to "normal(auto)").supportThresholdAngle(cube()))
        assertEquals(20, plateWith("enable_support" to "0", "support_threshold_angle" to "20").supportThresholdAngle(cube("enable_support" to "1", "support_type" to "normal(auto)")))
    }

    private fun cube(vararg settings: Pair<String, String>) = PlateObject.CalibrationCube(instances = emptyList(), settings = ModelSettings(settings.toMap()))

    private fun plateWith(vararg values: Pair<String, String>): PlateState {
        val process = PresetSettings(
            kind = PresetKind.PRINT,
            preset = "process",
            label = "process",
            dirty = false,
            isDefault = false,
            isSystem = true,
            hasParent = false,
            canDelete = false,
            mode = SettingsMode.SIMPLE,
            pages = emptyList(),
            activePage = "",
            settings = values.map { (key, value) -> SettingState(key, key, value, modified = false, system = true, enabled = true, visible = true) },
            saveName = "process",
            saveNameCopySuffix = true,
        )
        val state = PlateState()
        return state.copy(settingsTabs = state.settingsTabs + (PresetKind.PRINT to SettingsTabState(PresetKind.PRINT, settings = process)))
    }
}
