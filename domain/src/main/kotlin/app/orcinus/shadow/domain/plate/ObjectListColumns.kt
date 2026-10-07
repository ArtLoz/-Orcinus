package app.orcinus.shadow.domain.plate

import app.orcinus.shadow.core.model.Manipulation
import app.orcinus.shadow.core.model.PaintKind
import app.orcinus.shadow.core.model.PaintedFacets
import app.orcinus.shadow.core.model.PlateInstanceId
import app.orcinus.shadow.core.model.PlateObject
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.mesh
import app.orcinus.shadow.storage.api.SceneFiles
import java.io.Reader
import java.io.StringReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ObjectList::update_info_items(): the columns of an object's row that tell of
 * its paint and of its sinking (colSupportPaint, colColorPaint, colSinking),
 * and the click on the sinking one (ObjectList::list_manipulation()).
 */
class ObjectListColumnsUseCase(
    private val sceneFiles: SceneFiles,
    private val repository: PlateRepository,
    private val placePlateObject: PlacePlateObjectUseCase,
) {
    // The kinds every painting read so far holds, by the painting: the engine
    // writes a painting anew to a file of its own, so a file never changes.
    private val kinds = object : LinkedHashMap<String, Set<PaintKind>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Set<PaintKind>>?) = size > CACHED_PAINTINGS
    }

    /**
     * The kinds of paint on the volumes of each object ([paintingsOf]), by its
     * mesh: support painting is ModelVolume::supported_facets of any of them,
     * colour painting mmu_segmentation_facets.
     */
    suspend fun paintedKinds(paintings: Map<ScenePath, List<PaintedFacets>>): Map<ScenePath, Set<PaintKind>> = withContext(Dispatchers.IO) {
        paintings.mapValues { (_, volumes) -> volumes.filterNot(PaintedFacets::isEmpty).flatMapTo(HashSet(), ::kindsOf) }
    }

    private fun kindsOf(painting: PaintedFacets): Set<PaintKind> {
        synchronized(kinds) { kinds[painting.value] }?.let { return it }
        val file = painting.file
        val read = if (file == null) paintedKinds(StringReader(painting.value)) else sceneFiles.openPainting(file)?.use(::paintedKinds) ?: return emptySet()
        synchronized(kinds) { kinds[painting.value] = read }
        return read
    }

    /**
     * colSinking's click: "Shift objects to bed", ModelObject::ensure_on_bed()
     * of the object, which the app does copy by copy; a copy that does not
     * drop by itself stays where it is.
     */
    fun shiftToBed(mesh: ScenePath) {
        var target: PlateObject? = null
        repository.update { state ->
            target = state.objects.withMesh(mesh)?.takeUnless { state.busy }
            if (target == null) state else state.recorded()
        }
        target?.instances?.forEachIndexed { index, copy ->
            placePlateObject(PlateInstanceId(mesh, index), copy.inspection.placement, Manipulation.EnsureOnBed, record = false)
        }
    }

    private companion object {
        const val CACHED_PAINTINGS = 256
    }
}

/** The paintings of every volume of each object (ModelObject::volumes), by its mesh. */
fun paintingsOf(objects: List<PlateObject>): Map<ScenePath, List<PaintedFacets>> =
    objects.associate { plateObject -> plateObject.mesh to listOf(plateObject.painted) + plateObject.parts.map { it.painted } }

/**
 * GLCanvas3D::is_object_sinking(): a copy of the object reaches below the bed,
 * as GLVolume::is_sinking() and is_below_printbed() find a model part of it
 * whose box starts below SINKING_Z_THRESHOLD.
 */
val PlateObject.isSinking: Boolean
    get() = instances.any { copy -> copy.inspection.boxCenter.z - copy.inspection.dimensions.heightMillimeters / 2 < -SINKING_Z_THRESHOLD }

/** SINKING_Z_THRESHOLD of libslic3r, as a distance below the bed. */
private const val SINKING_Z_THRESHOLD = 0.001

/**
 * The kinds of paint a volume's painting holds, read as split_painting() of
 * the engine's painting.cpp reads it: "<kind>=<facets>" of each kind it
 * holds, joined with ';', the facets in hexadecimal; a painting without a name
 * is colour, as the app kept paintings before it painted anything else. The
 * facets themselves are passed over, which a painting of megabytes takes a
 * read of.
 */
internal fun paintedKinds(painting: Reader): Set<PaintKind> {
    val found = HashSet<PaintKind>()
    val name = StringBuilder()
    // At the start and after each ';' the kind's name runs up to its '='.
    var naming = true
    var named = false
    var empty = true
    val buffer = CharArray(BUFFER)
    while (true) {
        val read = painting.read(buffer)
        if (read < 0) break
        for (index in 0 until read) {
            val char = buffer[index]
            empty = false
            when {
                char == ';' -> {
                    naming = true
                    name.setLength(0)
                }
                naming && char == '=' -> {
                    named = true
                    naming = false
                    PAINT_KIND_NAMES[name.toString()]?.let(found::add)
                }
                naming && name.length <= LONGEST_NAME -> name.append(char)
            }
        }
    }
    return if (!named && !empty) setOf(PaintKind.COLOR) else found
}

/** KIND_NAMES of painting.cpp: the name each kind's facets go by. */
private val PAINT_KIND_NAMES = mapOf(
    "supports" to PaintKind.SUPPORTS,
    "seam" to PaintKind.SEAM,
    "color" to PaintKind.COLOR,
    "fuzzy_skin" to PaintKind.FUZZY_SKIN,
)

private val LONGEST_NAME = PAINT_KIND_NAMES.keys.maxOf(String::length)

private const val BUFFER = 64 * 1024

/**
 * What the object list asks of the 3D view, which takes it there: a painting
 * tool opened or closed from the paint columns, and the selection framed
 * when a row is activated.
 */
class CanvasRequestsUseCase(private val repository: PlateRepository) {
    /**
     * colSupportPaint's and colColorPaint's click: the row's object is
     * selected, as the click selects the row, and the painting tool of [kind]
     * opens on it, or closes while it is open (GLGizmosManager::open_gizmo()
     * and reset_all_states()).
     */
    fun paint(mesh: ScenePath, kind: PaintKind) = repository.update { state ->
        if (state.objects.withMesh(mesh) == null) return@update state
        state.copy(
            selectedInstances = setOf(PlateInstanceId(mesh)),
            selectedPart = null,
            selectedPartGroup = emptySet(),
            selectedRange = null,
            selectedConnectors = null,
            paintingRequest = kind,
        )
    }

    /** The canvas took the painting request. */
    fun paintingTaken() = repository.update { state -> if (state.paintingRequest == null) state else state.copy(paintingRequest = null) }

    /** wxEVT_DATAVIEW_ITEM_ACTIVATED of an object's, a volume's or a copy's row: GLCanvas3D::zoom_to_selection(). */
    fun zoomToSelection() = repository.update { state -> state.copy(zoomToSelection = state.zoomToSelection + 1) }
}
