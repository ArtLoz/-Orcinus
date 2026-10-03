package app.orcinus.shadow.domain.plate

import app.orcinus.shadow.core.model.EmbossKind
import app.orcinus.shadow.core.model.EmbossPlacement
import app.orcinus.shadow.core.model.EmbossRequest
import app.orcinus.shadow.core.model.EmbossVolumeOutcome
import app.orcinus.shadow.core.model.FontFace
import app.orcinus.shadow.core.model.ModelLoadOutcome
import app.orcinus.shadow.core.model.ObjectPartId
import app.orcinus.shadow.core.model.PlateInstanceId
import app.orcinus.shadow.core.model.PlateObject
import app.orcinus.shadow.core.model.PlateProblem
import app.orcinus.shadow.core.model.PlateProblemKind
import app.orcinus.shadow.core.model.PlateState
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.TextFontFamily
import app.orcinus.shadow.core.model.TextStyle
import app.orcinus.shadow.core.model.Transform3
import app.orcinus.shadow.core.model.VolumeType
import app.orcinus.shadow.core.model.mesh
import app.orcinus.shadow.core.model.placing
import app.orcinus.shadow.core.model.volumeAt
import app.orcinus.shadow.domain.placed
import app.orcinus.shadow.slicing.api.EmbossEditor
import app.orcinus.shadow.slicing.api.PlateInspector
import app.orcinus.shadow.storage.api.SceneFiles
import app.orcinus.shadow.storage.api.SystemFonts
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The fonts OrcaSlicer's text tool offers (init_face_names()): the phone's
 * font files as the engine reads them, a face it can't read left out, by
 * family in the order of their names.
 */
class TextFontsUseCase(
    private val systemFonts: SystemFonts,
    private val editor: EmbossEditor,
) {
    private val lock = Mutex()
    private var faces: List<FontFace>? = null

    suspend fun faces(): List<FontFace> = lock.withLock {
        faces ?: editor.describeFonts(systemFonts.fontFiles()).also { described -> if (described.isNotEmpty()) faces = described }
    }

    suspend fun families(): List<TextFontFamily> = faces()
        .groupBy(FontFace::family)
        .map { (name, faces) -> TextFontFamily(name, faces.sortedWith(compareBy<FontFace>({ it.italic }, { abs(it.weight - REGULAR) }))) }
        .sortedBy { it.name.lowercase() }

    /**
     * GLGizmoEmboss::create_default_styles(): NORMAL, SMALL (2 points smaller),
     * ITALIC, SWISS and MODERN, each of a font that loads; the desktop app takes
     * them from wxWidgets' fonts of the computer, the phone from its own:
     * Roboto for the sans-serif fonts, Noto Serif for the italic roman one,
     * Droid Sans Mono for the bold modern one. Without them, the first font of
     * the list ("First font"). Their sizes are those of wxWidgets' fonts on
     * Windows, in points as millimetres. [name] translates a style's name.
     */
    suspend fun defaultStyles(name: (String) -> String): List<TextStyle> {
        val families = families()
        fun face(family: String, italic: Boolean): FontFace? =
            families.firstOrNull { it.name == family }?.faces?.firstOrNull { it.italic == italic }
        val sans = face(SANS, italic = false)
        val styles = listOfNotNull(
            sans?.let { styleOf(name("NORMAL"), it, NORMAL_SIZE) },
            sans?.let { styleOf(name("SMALL"), it, SMALL_SIZE) },
            face(SERIF, italic = true)?.let { styleOf(name("ITALIC"), it, NORMAL_SIZE, family = "roman", style = "italic") },
            sans?.let { styleOf(name("SWISS"), it, NORMAL_SIZE, family = "swiss") },
            face(MONOSPACE, italic = false)?.let { styleOf(name("MODERN"), it, MODERN_SIZE, family = "modern", weight = "bold") },
        )
        if (styles.isNotEmpty()) return styles
        // use first alphabetic sorted installed font
        return listOfNotNull(families.firstOrNull()?.faces?.firstOrNull()?.let { styleOf(name("First font"), it, NORMAL_SIZE) })
    }

    private companion object {
        const val REGULAR = 400
        const val SANS = "Roboto"
        const val SERIF = "Noto Serif"
        const val MONOSPACE = "Droid Sans Mono"
        const val NORMAL_SIZE = 9.0
        const val SMALL_SIZE = 7.0
        const val MODERN_SIZE = 10.0
    }
}

/**
 * WxFontUtils::create_emboss_style() for a [face] of the phone: the style
 * [name] of the font file, [size] millimetres high, with the font's
 * description (FontProp::face_name, family, style and weight).
 */
fun styleOf(name: String, face: FontFace, size: Double, family: String = "", style: String = "", weight: String = "") = TextStyle(
    name = name,
    fontPath = face.path,
    sizeInMm = size,
    collectionNumber = face.index.takeIf { it > 0 },
    family = family,
    faceName = face.family,
    style = style,
    weight = weight,
)

/**
 * GLGizmoEmboss with its jobs (EmbossJob.cpp): a text joins an object as a
 * volume or the plate as an object of its own, and an edit embosses it anew;
 * each is one step of Undo, as the jobs take their snapshots ("Add Emboss text
 * Volume", "Add Emboss text object", "Emboss attribute change"). The engine
 * writes the object anew, so the volume edited afterwards is the one returned.
 */
class EmbossTextUseCase(
    private val editor: EmbossEditor,
    private val inspector: PlateInspector,
    private val sceneFiles: SceneFiles,
    private val repository: PlateRepository,
) {
    /** GLGizmoEmboss::set_volume_by_selection(): what the text [volume] is. */
    suspend fun describe(volume: ObjectPartId): EmbossVolumeOutcome {
        val state = repository.state.value
        val profiles = state.profiles ?: return EmbossVolumeOutcome.Failure("The presets are not loaded")
        val index = state.objects.indexOfFirst { it.mesh == volume.mesh }
        if (index < 0) return EmbossVolumeOutcome.Failure("The object is not on the plate")
        return editor.describeEmboss(state.objects.map { it.placed() }, index, volume.index, profiles)
    }

    /**
     * GLGizmoEmboss::create_volume(): [text] in [style] as a volume of [type]
     * at [placement], selected; the volume, or null when it could not be made.
     */
    suspend fun create(placement: EmbossPlacement, type: VolumeType, text: String, style: TextStyle): ObjectPartId? {
        val state = repository.state.value
        val profiles = state.profiles
        if (state.busy || profiles == null || state.objects.any(PlateObject::placing)) return null
        val source = state.objects.getOrNull(placement.objectIndex)
        val prefix = sceneFiles.newImportPrefix()
        val outcome = run(prefix) { editor.createText(state.objects.map { it.placed() }, placement, type, text, style, profiles, prefix) }
        // An object of no file names its G-code after itself.
        return joined(outcome, prefix, source, newObjectName = text.replace('\n', ' '))
    }

    /** GLGizmoEmboss::process(): the text [volume] embossed anew; [placement] places it first. */
    suspend fun update(volume: ObjectPartId, text: String, style: TextStyle, placement: Transform3? = null): ObjectPartId? {
        val state = repository.state.value
        val profiles = state.profiles
        val index = state.objects.indexOfFirst { it.mesh == volume.mesh }
        if (state.busy || profiles == null || index < 0) return null
        val prefix = sceneFiles.newImportPrefix()
        val outcome = run(prefix) {
            editor.updateText(state.objects.map { it.placed() }, index, volume.index, text, style, placement, profiles, prefix)
        }
        return joined(outcome, prefix, state.objects[index], newObjectName = "")
    }

    /**
     * GLGizmoEmboss::draw_model_type(): the text [volume] takes [type] and the
     * volumes are sorted by type ("Change Text Type"); a text that goes into or
     * out of the part side is embossed anew for its side of the surface.
     */
    suspend fun changeType(volume: ObjectPartId, type: VolumeType, text: String, style: TextStyle): ObjectPartId? {
        val state = repository.state.value
        val profiles = state.profiles
        val index = state.objects.indexOfFirst { it.mesh == volume.mesh }
        val old = state.objects.getOrNull(index)?.volumeAt(volume.index)?.type
        if (state.busy || profiles == null || old == null || old == type) return null
        val prefix = sceneFiles.newImportPrefix()
        val outcome = run(prefix) { inspector.setVolumeType(state.objects.map { it.placed() }, index, volume.index, type, profiles, prefix) }
        val changed = joined(outcome, prefix, state.objects[index], newObjectName = "") ?: return null
        // Update volume position when switch (from part) or (into part)
        if (old != VolumeType.PART && type != VolumeType.PART) return changed
        return update(changed, text, style) ?: changed
    }

    /** The engine's call, its files gone when it is cancelled. */
    private suspend fun run(prefix: ScenePath, call: suspend () -> ModelLoadOutcome): ModelLoadOutcome = try {
        call()
    } catch (cancellation: CancellationException) {
        sceneFiles.deleteImport(prefix)
        throw cancellation
    } catch (error: Exception) {
        ModelLoadOutcome.Failure(error.message.orEmpty())
    }

    /**
     * The edited object in the place of [source], or a new object at the end
     * of the plate, selected with its embossed volume; null when the engine
     * made nothing, with its message shown.
     */
    private fun joined(outcome: ModelLoadOutcome, prefix: ScenePath, source: PlateObject?, newObjectName: String): ObjectPartId? {
        val success = outcome as? ModelLoadOutcome.Success
        val loaded = success?.objects?.singleOrNull()
        val made = when {
            loaded == null -> null
            success.appended -> loaded.toPlateObject(newObjectName)
            source != null -> loaded.toPlateObjectOf(source)
            else -> null
        }
        if (made == null) {
            sceneFiles.deleteImport(prefix)
            val message = (outcome as? ModelLoadOutcome.Failure)?.message
            repository.update { state -> state.copy(problem = PlateProblem(PlateProblemKind.PLACEMENT_FAILED, message)) }
            return null
        }
        val volume = ObjectPartId(made.mesh, success?.selectedVolume?.takeIf { it >= 0 } ?: 0)
        var placed = false
        repository.update { state ->
            placed = false
            val appended = success?.appended == true
            if (!appended && source != null && state.objects.withMesh(source.mesh) == null) return@update state
            placed = true
            state.recorded().copy(
                objects = if (appended) state.objects + made else state.objects.replaced(source!!.mesh, made),
                selectedInstances = setOf(PlateInstanceId(made.mesh)),
                // An object made of the text is selected whole.
                selectedPart = volume.takeIf { made.parts.isNotEmpty() },
                selectedRange = null,
                result = null,
            )
        }
        return volume.takeIf { placed }
    }
}

/**
 * The object list's "Edit text", "Edit SVG" and "Add part" > "Text" or "SVG":
 * the request waits for the canvas's tool, which takes it ([done]).
 */
class RequestEmbossUseCase(private val repository: PlateRepository) {
    fun edit(volume: ObjectPartId) = repository.update { state ->
        if (state.objects.withMesh(volume.mesh) == null) state else state.copy(embossRequest = EmbossRequest.Edit(volume))
    }

    fun add(kind: EmbossKind, mesh: ScenePath, type: VolumeType) = repository.update { state ->
        if (state.objects.withMesh(mesh) == null) state else state.copy(embossRequest = EmbossRequest.Add(kind, mesh, type))
    }

    fun done() = repository.update { state -> if (state.embossRequest == null) state else state.copy(embossRequest = null) }
}

/** The list with [made] in the place of the object with the [mesh] file. */
private fun List<PlateObject>.replaced(mesh: ScenePath, made: PlateObject): List<PlateObject> = map { if (it.mesh == mesh) made else it }

/**
 * ModelVolume::is_text() of the [volume] (0 for the object's own mesh) of the
 * object with its mesh file, as the plate holds it.
 */
fun PlateState.isTextVolume(volume: ObjectPartId): Boolean = embossKindOf(volume) == EmbossKind.TEXT

/** What the [volume] of the object with its mesh file was embossed from; null for neither text nor SVG. */
fun PlateState.embossKindOf(volume: ObjectPartId): EmbossKind? {
    val target = objects.withMesh(volume.mesh) ?: return null
    val emboss = if (volume.index == 0) target.volume.emboss else target.parts.getOrNull(volume.index - 1)?.emboss
    return emboss?.kind
}

/**
 * The text or SVG volume the canvas's tool opens on for the selection: the
 * selected part, or the object's own mesh of an object that is a text or SVG
 * alone (a volume selected alone, as the desktop selection holds it).
 */
fun PlateState.selectedEmbossVolume(kind: EmbossKind): ObjectPartId? {
    selectedPart?.takeIf { embossKindOf(it) == kind }?.let { return it }
    val mesh = selectedInstances.map { it.mesh }.distinct().singleOrNull() ?: return null
    val target = objects.withMesh(mesh) ?: return null
    return ObjectPartId(mesh, 0).takeIf { target.parts.isEmpty() && target.volume.emboss?.kind == kind }
}
