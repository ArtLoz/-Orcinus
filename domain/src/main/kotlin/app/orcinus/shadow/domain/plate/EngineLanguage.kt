package app.orcinus.shadow.domain.plate

/**
 * The app's language as the engine's messages need it: OrcaSlicer's
 * [catalog] the engine translates its messages with, and the name the app
 * gives its calibration cube in that language, which the messages call it by.
 */
data class EngineLanguage(val catalog: String, val calibrationCubeName: String)
