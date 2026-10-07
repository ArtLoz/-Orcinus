package app.orcinus.shadow.slicing.service

import app.orcinus.shadow.core.model.GcodeLoadOutcome
import app.orcinus.shadow.core.model.GcodeSettingsIds
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.SliceStatistics
import app.orcinus.shadow.core.model.amounts
import app.orcinus.shadow.core.model.filamentUsagesOf

internal fun GcodeLoadOutcome.toParcel() = GcodeLoadParcel().also {
    when (this) {
        is GcodeLoadOutcome.Failure -> it.message = message
        is GcodeLoadOutcome.Success -> {
            it.success = true
            it.valid = valid
            it.layerCount = statistics.layerCount
            it.estimatedPrintTimeSeconds = statistics.estimatedPrintTimeSeconds
            it.filamentMillimeters = statistics.filamentMillimeters
            it.cost = statistics.cost
            it.filaments = statistics.filaments.map { usage -> usage.filament }.toIntArray()
            it.filamentAmounts = statistics.filaments.amounts()
            it.toolpathsPath = toolpaths?.value
            it.sliceInfoPath = sliceInfo?.value
            it.printerSettings = settingsIds.printer
            it.printSettings = settingsIds.print
            it.filamentSettings = settingsIds.filaments.toTypedArray()
            it.bedTypeChanged = bedTypeChanged
        }
    }
}

internal fun GcodeLoadParcel.toGcodeLoadOutcome(): GcodeLoadOutcome = if (!success) {
    GcodeLoadOutcome.Failure(message.orEmpty())
} else {
    GcodeLoadOutcome.Success(
        statistics = SliceStatistics(
            layerCount = layerCount,
            estimatedPrintTimeSeconds = estimatedPrintTimeSeconds,
            filamentMillimeters = filamentMillimeters,
            cost = cost,
            filaments = filamentUsagesOf(filaments ?: IntArray(0), filamentAmounts ?: DoubleArray(0)),
        ),
        toolpaths = toolpathsPath?.let(::ScenePath),
        sliceInfo = sliceInfoPath?.let(::ScenePath),
        settingsIds = GcodeSettingsIds(printerSettings.orEmpty(), printSettings.orEmpty(), filamentSettings?.toList().orEmpty()),
        valid = valid,
        bedTypeChanged = bedTypeChanged,
    )
}
