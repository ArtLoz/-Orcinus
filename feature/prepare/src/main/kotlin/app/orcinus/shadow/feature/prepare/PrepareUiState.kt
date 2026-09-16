package app.orcinus.shadow.feature.prepare

import app.orcinus.shadow.core.model.PlateObject
import app.orcinus.shadow.core.model.PlateProblem
import app.orcinus.shadow.core.model.PlateSlicing
import app.orcinus.shadow.core.model.PlateState

data class PrepareUiState(
    val importing: Boolean,
    val plateObject: PlateObject?,
    val slicing: PlateSlicing?,
    val problem: PlateProblem?,
    val canEditPlate: Boolean,
    val canSlice: Boolean,
)

internal fun PlateState.toPrepareUiState() = PrepareUiState(
    importing = importing,
    plateObject = plateObject,
    slicing = slicing,
    problem = problem,
    canEditPlate = !busy,
    canSlice = canSlice,
)
