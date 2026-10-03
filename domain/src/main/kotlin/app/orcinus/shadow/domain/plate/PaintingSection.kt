package app.orcinus.shadow.domain.plate

import app.orcinus.shadow.core.model.ClippingPlane
import app.orcinus.shadow.core.model.PlateInstanceId
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.domain.placed
import app.orcinus.shadow.slicing.api.PlateInspector
import app.orcinus.shadow.storage.api.SceneFiles

/**
 * ObjectClipper::render_cut() of a painting tool's "Section view": the cut of
 * the volumes of the painted copy's object at the plane, where the copy
 * stands on the plate, as a mesh named anew each time; the one shown before
 * goes.
 */
class PaintingSectionUseCase(
    private val inspector: PlateInspector,
    private val sceneFiles: SceneFiles,
    private val repository: PlateRepository,
) {
    private var prefix: ScenePath? = null
    private var made = 0
    private var shown: ScenePath? = null

    /** The cut of the copy [copy] at [plane]; null while the plane meets nothing. */
    suspend fun section(copy: PlateInstanceId, plane: ClippingPlane): ScenePath? {
        val state = repository.state.value
        val profiles = state.profiles ?: return null
        val target = state.objects.withMesh(copy.mesh) ?: return null
        val instance = target.instances.getOrNull(copy.instance) ?: return null
        val base = prefix ?: sceneFiles.newCutMeshes().also { prefix = it }
        val path = ScenePath("${base.value}-${made++}-painting-section.mesh")
        val cut = inspector.paintingSection(target.placed(), profiles, instance.inspection.placement, plane, path)
        shown?.takeIf { it != cut }?.let(sceneFiles::deleteObjectMesh)
        shown = cut
        return cut
    }

    /** ObjectClipper::on_release(): the cut goes with the tool, or with a section back at 0. */
    fun clear() {
        shown?.let(sceneFiles::deleteObjectMesh)
        shown = null
    }
}
