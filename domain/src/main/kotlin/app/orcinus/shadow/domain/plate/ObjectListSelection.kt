package app.orcinus.shadow.domain.plate

import app.orcinus.shadow.core.model.DialogIcon
import app.orcinus.shadow.core.model.LayerRangeEditor
import app.orcinus.shadow.core.model.LayerRangeId
import app.orcinus.shadow.core.model.ObjectPartId
import app.orcinus.shadow.core.model.OrcaText
import app.orcinus.shadow.core.model.PlateInstanceId
import app.orcinus.shadow.core.model.PlateState
import app.orcinus.shadow.core.model.SettingsDialog
import app.orcinus.shadow.core.model.volumeAt

/**
 * A Ctrl-click on a row of the object list, which the list gives while it
 * picks several items: ObjectList::check_last_selection() and
 * fix_multiselection_conflicts(). The list holds objects and their copies
 * (smInstance), volumes of one object (smVolume) or height ranges of one
 * object (smLayer), never a mix: a volume, a range or a copy that does not
 * fit what it holds is refused with "Selection conflicts" and the selection
 * stays; an object's row picked among volumes or ranges leaves them as they
 * are. With nothing selected the row is picked alone.
 */
class PickListItemUseCase(private val repository: PlateRepository) {
    /** The row of a copy ([copyRow], an itInstance row), or of its object, which picks its first copy as a tap on it does. */
    fun copy(id: PlateInstanceId, copyRow: Boolean) = repository.update { state ->
        if (state.objects.withMesh(id.mesh)?.instances?.getOrNull(id.instance) == null) return@update state
        when (val mode = state.listSelectionMode()) {
            ListSelectionMode.VOLUME, ListSelectionMode.LAYER -> if (copyRow) state.conflicting(mode) else state
            ListSelectionMode.UNDEF -> state.withCopies(setOf(id))
            ListSelectionMode.INSTANCE ->
                state.withCopies(if (id in state.selectedInstances) state.selectedInstances - id else state.selectedInstances + id)
        }
    }

    /** The row of a volume (ModelObject::volumes, its own mesh at 0). */
    fun part(id: ObjectPartId) = repository.update { state ->
        if (state.objects.withMesh(id.mesh)?.volumeAt(id.index) == null) return@update state
        val current = state.selectedPart
        when (val mode = state.listSelectionMode()) {
            ListSelectionMode.UNDEF -> state.copy(
                selectedInstances = setOf(PlateInstanceId(id.mesh, 0)),
                selectedPart = id,
                selectedPartGroup = emptySet(),
                selectedRange = null,
                selectedConnectors = null,
            )
            ListSelectionMode.INSTANCE, ListSelectionMode.LAYER -> state.conflicting(mode)
            ListSelectionMode.VOLUME -> if (current == null || current.mesh != id.mesh) {
                state.conflicting(mode)
            } else {
                val selected = state.selectedParts()
                val group = if (id in selected) selected - id else selected + id
                when {
                    group.isEmpty() -> state.copy(selectedPart = null, selectedPartGroup = emptySet())
                    group.size == 1 -> state.copy(selectedPart = group.single(), selectedPartGroup = emptySet())
                    else -> state.copy(selectedPart = current.takeIf { it in group } ?: group.first(), selectedPartGroup = group.toSet())
                }
            }
        }
    }

    /** The row of a height range: several ranges of one object are edited and copied together. */
    fun range(id: LayerRangeId) = repository.update { state ->
        if ((state.objects.withMesh(id.mesh)?.layerRanges?.size ?: 0) <= id.index) return@update state
        val current = state.selectedRange
        when (val mode = state.listSelectionMode()) {
            ListSelectionMode.UNDEF -> state.copy(
                selectedInstances = setOf(PlateInstanceId(id.mesh)),
                selectedPart = null,
                selectedRange = id,
                selectedRangeGroup = emptySet(),
                selectedConnectors = null,
                layerRangeEditor = LayerRangeEditor.LAYER_HEIGHT,
            )
            ListSelectionMode.INSTANCE, ListSelectionMode.VOLUME -> state.conflicting(mode)
            ListSelectionMode.LAYER -> if (current == null || current.mesh != id.mesh) {
                state.conflicting(mode)
            } else {
                val selected = state.selectedRanges()
                val group = if (id in selected) selected - id else selected + id
                when {
                    group.isEmpty() -> state.copy(selectedRange = null, selectedRangeGroup = emptySet())
                    group.size == 1 -> state.copy(selectedRange = group.single(), selectedRangeGroup = emptySet())
                    else -> state.copy(selectedRange = current.takeIf { it in group } ?: group.first(), selectedRangeGroup = group.toSet())
                }
            }
        }
    }

    private fun PlateState.withCopies(selected: Set<PlateInstanceId>) =
        copy(selectedInstances = selected, selectedPart = null, selectedPartGroup = emptySet(), selectedRange = null, selectedConnectors = null)

    /** check_last_selection()'s show_info(): the selection stays, and "Selection conflicts" says why, by what it holds. */
    private fun PlateState.conflicting(mode: ListSelectionMode) = copy(plateNotices = plateNotices + selectionConflicts(mode == ListSelectionMode.INSTANCE))
}

/** ObjectList's SELECTION_MODE of what the list holds selected (update_selection_mode()). */
private enum class ListSelectionMode { UNDEF, INSTANCE, VOLUME, LAYER }

/** The connectors' info item, as any item the list selects with an object, counts as smInstance. */
private fun PlateState.listSelectionMode(): ListSelectionMode = when {
    selectedRange != null -> ListSelectionMode.LAYER
    selectedPart != null -> ListSelectionMode.VOLUME
    selectedInstances.isNotEmpty() -> ListSelectionMode.INSTANCE
    else -> ListSelectionMode.UNDEF
}

/** The message box of check_last_selection(), "Info", with the rule of what the list holds: [objects], or volumes or ranges. */
private fun selectionConflicts(objects: Boolean) = SettingsDialog(
    id = "selection_conflicts",
    icon = DialogIcon.INFO,
    title = listOf(OrcaText("Info")),
    text = listOf(
        OrcaText("Selection conflicts"),
        OrcaText("%s", listOf("\n\n")),
        OrcaText(
            if (objects) {
                "If the first selected item is an object, the second should also be an object."
            } else {
                "If the first selected item is a part, the second should be a part in the same object."
            },
        ),
    ),
    question = false,
    yes = null,
    no = null,
)
