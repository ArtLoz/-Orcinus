package app.orcinus.shadow.domain.plate

import app.orcinus.shadow.core.model.CutId
import app.orcinus.shadow.core.model.DialogIcon
import app.orcinus.shadow.core.model.OrcaText
import app.orcinus.shadow.core.model.PendingPlateQuestion
import app.orcinus.shadow.core.model.PlateInstanceId
import app.orcinus.shadow.core.model.PlateObject
import app.orcinus.shadow.core.model.PlateRequest
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.SettingsDialog
import app.orcinus.shadow.core.model.VolumeType
import app.orcinus.shadow.core.model.isCut
import app.orcinus.shadow.core.model.withCutId
import app.orcinus.shadow.core.model.withParts
import app.orcinus.shadow.core.model.withoutCut

/**
 * ObjectList::invalidate_cut_info_for_object() ("Invalidate cut info"): the
 * object with the [mesh] file and the other parts of its cut are no longer
 * parts of it, nor their volumes connectors, so they can be changed one by one.
 */
class InvalidateCutInfoUseCase(private val repository: PlateRepository) {
    operator fun invoke(mesh: ScenePath) = repository.update { state ->
        val cutId = state.objects.withMesh(mesh)?.cutId
        if (state.busy || cutId == null) return@update state
        state.recorded().copy(objects = state.objects.withoutCut(cutId))
    }

    /** The question of ObjectList::del_from_cut_object() answered: "Invalidate cut info", or Cancel. */
    fun answer(yes: Boolean) {
        val request = repository.state.value.plateQuestion?.request as? PlateRequest.InvalidateCut ?: return
        repository.update { it.copy(plateQuestion = null) }
        if (yes) invoke(request.mesh)
    }
}

/**
 * The object list's "Cut connectors" item of a part of a cut: picking it
 * selects the object's connectors (ObjectList::update_selections_on_canvas()),
 * and deleting it asks del_from_cut_object() of a connector, whose answers
 * invalidate the cut info or delete every connector of the cut
 * (delete_all_connectors_for_object()).
 */
class CutConnectorsUseCase(
    private val repository: PlateRepository,
    private val invalidateCutInfo: InvalidateCutInfoUseCase,
) {
    fun select(copy: PlateInstanceId) = repository.update { state ->
        val target = state.objects.withMesh(copy.mesh)
        if (target == null || target.parts.none { it.cutInfo.connector }) return@update state
        state.copy(selectedInstances = setOf(copy), selectedPart = null, selectedRange = null, selectedConnectors = copy)
    }

    /** ObjectList::del_info_item() of the item: the question first. */
    fun delete(mesh: ScenePath) = repository.update { state ->
        val target = state.objects.withMesh(mesh)
        if (state.busy || target == null || !target.isCut) return@update state
        state.copy(plateQuestion = connectorsQuestion(mesh))
    }

    /** Its answer: "Invalidate cut info", or "Delete all connectors". */
    fun answer(yes: Boolean) {
        val request = repository.state.value.plateQuestion?.request as? PlateRequest.DeleteCutConnectors ?: return
        repository.update { it.copy(plateQuestion = null) }
        if (yes) invalidateCutInfo(request.mesh) else deleteAll(request.mesh)
    }

    /**
     * ObjectList::delete_all_connectors_for_object(): every object of the
     * cut loses its connectors (ModelObject::delete_connectors()), one step
     * of Undo.
     */
    fun deleteAll(mesh: ScenePath) = repository.update { state ->
        val cutId = state.objects.withMesh(mesh)?.cutId
        if (state.busy || cutId == null) return@update state
        val objects = state.objects.map { plateObject ->
            if (plateObject.cutId?.isEqual(cutId) == true) plateObject.withParts(plateObject.parts.filterNot { it.cutInfo.connector }) else plateObject
        }
        if (objects == state.objects) return@update state
        state.recorded().copy(objects = objects, selectedPart = null, selectedConnectors = null, result = null)
    }

    private fun connectorsQuestion(mesh: ScenePath) = PendingPlateQuestion(
        PlateRequest.DeleteCutConnectors(mesh),
        SettingsDialog(
            id = "delete_connector_from_cut_object",
            icon = DialogIcon.WARNING,
            title = listOf(OrcaText("Delete connector from object which is a part of cut")),
            text = listOf(
                OrcaText(
                    "This action will break a cut correspondence.\n" +
                        "After that model consistency can't be guaranteed.\n" +
                        "\n" +
                        "To manipulate with solid parts or negative volumes you have to invalidate cut information first.",
                ),
                OrcaText("%s", listOf("\n")),
                OrcaText("To save cut correspondence you can delete all connectors from all related objects."),
            ),
            question = true,
            yes = OrcaText("Invalidate cut info"),
            no = OrcaText("Delete all connectors"),
            cancel = OrcaText("Cancel"),
        ),
        emptyMap(),
        emptyList(),
    )
}

/** Every object of the cut [cutId] (CutObjectBase::is_equal()) no longer a part of it. */
internal fun List<PlateObject>.withoutCut(cutId: CutId): List<PlateObject> =
    map { if (it.cutId?.isEqual(cutId) == true) it.withoutCut() else it }

/**
 * synchronize_model_after_cut(): the other parts of the cut [cutId] was made
 * from take the cut as it now stands.
 */
internal fun List<PlateObject>.synchronizedAfterCut(cutId: CutId): List<PlateObject> =
    map { plateObject ->
        val own = plateObject.cutId
        if (own != null && own.hasSameId(cutId) && !own.isEqual(cutId)) plateObject.withCutId(cutId) else plateObject
    }

/**
 * ObjectList::del_subobject_from_object() for a volume of a part of a cut: a
 * solid part or a negative volume stays, and the user is asked whether to
 * invalidate the cut info first (del_from_cut_object()); null when the
 * volume may go.
 */
internal fun PlateObject.cutVolumeQuestion(type: VolumeType): PendingPlateQuestion? {
    if (!isCut || (type != VolumeType.PART && type != VolumeType.NEGATIVE)) return null
    val title = if (type == VolumeType.PART) {
        "Delete solid part from object which is a part of cut"
    } else {
        "Delete negative volume from object which is a part of cut"
    }
    val dialog = SettingsDialog(
        id = "delete_from_cut_object",
        icon = DialogIcon.WARNING,
        title = listOf(OrcaText(title)),
        text = listOf(
            OrcaText(
                "This action will break a cut correspondence.\n" +
                    "After that model consistency can't be guaranteed.\n" +
                    "\n" +
                    "To manipulate with solid parts or negative volumes you have to invalidate cut information first.",
            ),
        ),
        question = true,
        yes = OrcaText("Invalidate cut info"),
        no = OrcaText("Cancel"),
    )
    return PendingPlateQuestion(PlateRequest.InvalidateCut(mesh = instances.first().inspection.mesh), dialog, emptyMap(), emptyList())
}

/** Plater::priv::delete_object_from_model()'s warning before a part of a cut goes. */
internal fun deleteCutObjectQuestion(request: PlateRequest) = PendingPlateQuestion(
    request,
    SettingsDialog(
        id = "delete_cut_object",
        icon = DialogIcon.WARNING,
        title = listOf(OrcaText("Delete object which is a part of cut object")),
        text = listOf(
            OrcaText(
                "You try to delete an object which is a part of a cut object.\n" +
                    "This action will break a cut correspondence.\n" +
                    "After that model consistency can't be guaranteed.",
            ),
        ),
        question = true,
        yes = OrcaText("Delete"),
        no = OrcaText("Cancel"),
    ),
    emptyMap(),
    emptyList(),
)
