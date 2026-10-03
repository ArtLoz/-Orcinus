package app.orcinus.shadow.domain.plate

import app.orcinus.shadow.core.model.EmbossKind
import app.orcinus.shadow.core.model.EmbossPlacement
import app.orcinus.shadow.core.model.EmbossRequest
import app.orcinus.shadow.core.model.EmbossVolumeOutcome
import app.orcinus.shadow.core.model.FontFace
import app.orcinus.shadow.core.model.ImportedModelFile
import app.orcinus.shadow.core.model.ModelLoadOutcome
import app.orcinus.shadow.core.model.ModelPath
import app.orcinus.shadow.core.model.ObjectPartId
import app.orcinus.shadow.core.model.PlateInstanceId
import app.orcinus.shadow.core.model.PlateObject
import app.orcinus.shadow.core.model.PlateProblem
import app.orcinus.shadow.core.model.PlateProblemKind
import app.orcinus.shadow.core.model.PlateState
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.SvgFileEdit
import app.orcinus.shadow.core.model.SvgPreviewOutcome
import app.orcinus.shadow.core.model.TextFontFamily
import app.orcinus.shadow.core.model.TextStyle
import app.orcinus.shadow.core.model.EmbossTransform
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
 * StyleManager::set_wx_font() with a [face] of the phone: the style takes its
 * file, and WxFontUtils::update_property() its family's name, and its style
 * and weight where they are not the normal ones, which leave the style's.
 */
fun TextStyle.withFace(face: FontFace): TextStyle = copy(
    fontPath = face.path,
    collectionNumber = face.index.takeIf { it > 0 },
    faceName = face.family.ifEmpty { faceName },
    style = if (face.italic) ITALIC else style,
    weight = weightName(face.weight) ?: weight,
)

/** GLGizmoEmboss::select_facename(): the family's face of normal style and weight, the style's skew and boldness kept. */
fun TextStyle.withFamily(family: TextFontFamily): TextStyle? = family.match(italic = false, weight = REGULAR_WEIGHT)?.let(::withFace)

/**
 * draw_italic_button(): an italic style (skewed, or of an italic face) is
 * unset: no skew, and the family's upright face of its weight; otherwise the
 * family's italic face of its weight (WxFontUtils::set_italic()), or a skew
 * of 0.2 when the family has none.
 */
fun TextStyle.toggledItalic(families: List<TextFontFamily>): TextStyle {
    val (family, face) = faceOf(families) ?: return this
    if (skew != null || face.italic) {
        val upright = family.match(italic = false, weight = face.weight)
        val unskewed = copy(skew = null)
        return if (face.italic && upright != null) unskewed.withFace(upright) else unskewed
    }
    val italic = family.match(italic = true, weight = face.weight)?.takeIf { it != face }
    return italic?.let(::withFace) ?: copy(skew = ITALIC_SKEW)
}

/**
 * draw_bold_button(): a bold style (with boldness, or of a face of other
 * than the normal weight, WxFontUtils::is_bold()) is unset: no boldness, and
 * the family's face of normal weight; otherwise the family's face for bold,
 * heavy, extra bold or extra heavy that is another face
 * (WxFontUtils::set_bold()), or a boldness of 20 when there is none.
 */
fun TextStyle.toggledBold(families: List<TextFontFamily>): TextStyle {
    val (family, face) = faceOf(families) ?: return this
    if (boldness != null || weightName(face.weight) != null) {
        val regular = family.match(italic = face.italic, weight = REGULAR_WEIGHT)
        val unbold = copy(boldness = null)
        return if (weightName(face.weight) != null && regular != null) unbold.withFace(regular) else unbold
    }
    val bold = BOLD_WEIGHTS.firstNotNullOfOrNull { weight -> family.match(italic = face.italic, weight = weight)?.takeIf { it != face } }
    return bold?.let(::withFace) ?: copy(boldness = BOLDNESS)
}

/** The family and face of the style's font among [families]; null for a font the phone has not. */
private fun TextStyle.faceOf(families: List<TextFontFamily>): Pair<TextFontFamily, FontFace>? {
    for (family in families) {
        val face = family.faces.firstOrNull { it.path == fontPath && it.index == (collectionNumber ?: 0) } ?: continue
        return family to face
    }
    return null
}

/**
 * The face the system's font matching gives for a style and a weight: of the
 * family's faces of that style, the one of the nearest weight; null for none.
 */
private fun TextFontFamily.match(italic: Boolean, weight: Int): FontFace? =
    faces.filter { it.italic == italic }.minByOrNull { abs(it.weight - weight) }

/**
 * WxFontUtils::type_to_weight of the wxFontWeight a face's weight rounds to
 * (wxFontInfo::GetWeightClosestToNumericValue()); null for the normal one.
 */
private fun weightName(weight: Int): String? = when (((weight + 50) / 100 * 100).coerceIn(100, 1000)) {
    100 -> "thin"
    200 -> "extraLight"
    300 -> "light"
    400 -> null
    500 -> "medium"
    600 -> "semibold"
    700 -> "bold"
    800 -> "extraBold"
    900 -> "heavy"
    else -> "extraHeavy"
}

private const val ITALIC = "italic"
private const val REGULAR_WEIGHT = 400

/** The weights WxFontUtils::set_bold() asks for in turn: bold, heavy, extra bold, extra heavy. */
private val BOLD_WEIGHTS = listOf(700, 900, 800, 1000)

/** draw_italic_button()'s skew and draw_bold_button()'s boldness of a family without such a face. */
private const val ITALIC_SKEW = 0.2
private const val BOLDNESS = 20.0

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
 * GLGizmoEmboss and GLGizmoSVG with their jobs (EmbossJob.cpp): a text or an
 * SVG joins an object as a volume or the plate as an object of its own, and an
 * edit embosses it anew; each is one step of Undo, as the jobs take their
 * snapshots ("Add Emboss text Volume", "Add Emboss text object", "Emboss
 * attribute change"). The engine writes the object anew, so the volume edited
 * afterwards is the one returned.
 */
class EmbossUseCase(
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
     * The text [volume], seen on its copy [instance], turned and moved as
     * [transform] says (the Rotation and From surface sliders, "Set text to
     * face camera", fix_transformation() of another style), and embossed anew
     * from [text] and [style] when [reEmboss] is set or the engine has to.
     */
    suspend fun transform(volume: ObjectPartId, instance: Int, transform: EmbossTransform, text: String, style: TextStyle, reEmboss: Boolean): ObjectPartId? {
        val state = repository.state.value
        val profiles = state.profiles
        val index = state.objects.indexOfFirst { it.mesh == volume.mesh }
        if (state.busy || profiles == null || index < 0) return null
        val prefix = sceneFiles.newImportPrefix()
        val outcome = run(prefix) {
            editor.transformEmboss(state.objects.map { it.placed() }, index, instance, volume.index, transform, text, style, reEmboss, profiles, prefix)
        }
        return joined(outcome, prefix, state.objects[index], newObjectName = "")
    }

    /**
     * GLGizmoSVG::create_volume(): the SVG file [svg] as a volume of [type] at
     * [placement], or as an object named after the file; the volume, or null.
     */
    suspend fun createSvg(placement: EmbossPlacement, type: VolumeType, svg: ImportedModelFile): ObjectPartId? {
        val state = repository.state.value
        val profiles = state.profiles
        if (state.busy || profiles == null || state.objects.any(PlateObject::placing)) return null
        val source = state.objects.getOrNull(placement.objectIndex)
        val prefix = sceneFiles.newImportPrefix()
        val outcome = run(prefix) { editor.createSvg(state.objects.map { it.placed() }, placement, type, svg.path, profiles, prefix) }
        // volume_name(): the file's name without its extension.
        return joined(outcome, prefix, source, newObjectName = svg.displayName.substringBeforeLast('.'))
    }

    /**
     * GLGizmoSVG::process(): the SVG [volume] [depth] deep, on the surface or
     * not, from another file [svg] when given ("Change file", reload).
     */
    suspend fun updateSvg(volume: ObjectPartId, depth: Double, useSurface: Boolean, svg: ModelPath? = null): ObjectPartId? {
        val state = repository.state.value
        val profiles = state.profiles
        val index = state.objects.indexOfFirst { it.mesh == volume.mesh }
        if (state.busy || profiles == null || index < 0) return null
        val prefix = sceneFiles.newImportPrefix()
        val outcome = run(prefix) {
            editor.updateSvg(state.objects.map { it.placed() }, index, volume.index, depth, useSurface, svg, null, profiles, prefix)
        }
        return joined(outcome, prefix, state.objects[index], newObjectName = "")
    }

    /**
     * The SVG window's file menu: "Forget the file path", "Bake" (the volume
     * is a mesh alone afterwards) and "Save as" into [path].
     */
    suspend fun editSvgFile(volume: ObjectPartId, edit: SvgFileEdit, path: String = ""): ObjectPartId? {
        val state = repository.state.value
        val profiles = state.profiles
        val index = state.objects.indexOfFirst { it.mesh == volume.mesh }
        if (state.busy || profiles == null || index < 0) return null
        val prefix = sceneFiles.newImportPrefix()
        val outcome = run(prefix) { editor.editSvgFile(state.objects.map { it.placed() }, index, volume.index, edit, path, profiles, prefix) }
        return joined(outcome, prefix, state.objects[index], newObjectName = "")
    }

    /** GLGizmoSVG::draw_preview() and draw_filename(): the SVG [volume]'s picture, at most [maxSize] pixels, and its warnings. */
    suspend fun previewSvg(volume: ObjectPartId, maxSize: Int): SvgPreviewOutcome {
        val state = repository.state.value
        val profiles = state.profiles ?: return SvgPreviewOutcome.Failure("The presets are not loaded")
        val index = state.objects.indexOfFirst { it.mesh == volume.mesh }
        if (index < 0) return SvgPreviewOutcome.Failure("The object is not on the plate")
        return editor.previewSvg(state.objects.map { it.placed() }, index, volume.index, sceneFiles.svgPreview(), maxSize, profiles)
    }

    /** A new file the SVG [name]d so is saved to before it goes into a document. */
    fun newSavedSvg(name: String): ScenePath = sceneFiles.newSavedSvg(name)

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
    /** The list selects the volume (its object's first copy), as a click on its row does, for the tool to open on. */
    fun edit(volume: ObjectPartId) = repository.update { state ->
        val target = state.objects.withMesh(volume.mesh) ?: return@update state
        state.copy(
            embossRequest = EmbossRequest.Edit(volume),
            selectedInstances = setOf(PlateInstanceId(volume.mesh)),
            selectedPart = volume.takeIf { target.parts.isNotEmpty() },
            selectedRange = null,
        )
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
