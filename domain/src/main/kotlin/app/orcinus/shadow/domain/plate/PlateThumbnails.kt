package app.orcinus.shadow.domain.plate

import app.orcinus.shadow.core.model.PlateDescription
import app.orcinus.shadow.core.model.PlateObject
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.SlicingProfileSelection
import app.orcinus.shadow.core.model.ThumbnailImage
import app.orcinus.shadow.core.model.ThumbnailSize
import app.orcinus.shadow.core.model.ThumbnailSizesOutcome
import app.orcinus.shadow.slicing.api.SlicerEngine
import app.orcinus.shadow.storage.api.SceneFiles
import kotlin.coroutines.cancellation.CancellationException

/**
 * GLCanvas3D::render_thumbnail() as OrcaSlicer runs it while it exports
 * G-code: pictures of the objects the plate prints, which the G-code carries
 * for the printer's screen.
 */
fun interface PlateThumbnailRenderer {
    /**
     * Renders the printable [objects] standing on [plate], each in the colour
     * of the filament it prints with ([filamentColors], "#RRGGBB" by filament),
     * at every one of [sizes], into the file [fileFor] names. A size it cannot
     * render is left out.
     */
    suspend fun render(
        objects: List<PlateObject>,
        plate: PlateDescription,
        filamentColors: List<String>,
        sizes: List<ThumbnailSize>,
        fileFor: (ThumbnailSize) -> ScenePath,
    ): List<ThumbnailImage>
}

/**
 * The thumbnails the G-code of the plate carries (the ThumbnailsGeneratorCallback
 * BackgroundSlicingProcess exports G-code with). The desktop app renders them
 * on its screen when the export asks for them; the engine slices in a process
 * without one, so the app renders the sizes the printer asks for before it
 * slices. A plate that cannot be rendered slices without them, as the desktop
 * app leaves out a thumbnail it failed to render.
 */
class RenderThumbnailsUseCase(
    private val engine: SlicerEngine,
    private val renderer: PlateThumbnailRenderer,
    private val sceneFiles: SceneFiles,
) {
    /** The thumbnails of a slice whose toolpaths go to [toolpaths]; their files are kept and deleted with them. */
    suspend operator fun invoke(
        objects: List<PlateObject>,
        plate: PlateDescription?,
        filamentColors: List<String>,
        profiles: SlicingProfileSelection,
        toolpaths: ScenePath,
    ): List<ThumbnailImage> {
        if (plate == null) return emptyList()
        val sizes = (engine.thumbnailSizes(profiles) as? ThumbnailSizesOutcome.Success)?.sizes.orEmpty()
        if (sizes.isEmpty()) return emptyList()
        return try {
            renderer.render(objects, plate, filamentColors, sizes) { size -> sceneFiles.thumbnailOf(toolpaths, size) }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            emptyList()
        }
    }
}
