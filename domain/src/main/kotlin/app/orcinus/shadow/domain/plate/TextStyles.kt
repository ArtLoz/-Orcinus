package app.orcinus.shadow.domain.plate

import app.orcinus.shadow.core.model.EmbossKind
import app.orcinus.shadow.core.model.LayerRangeId
import app.orcinus.shadow.core.model.ModelLoadOutcome
import app.orcinus.shadow.core.model.ObjectPartId
import app.orcinus.shadow.core.model.PlateInstanceId
import app.orcinus.shadow.core.model.PlateObject
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.TextStyle
import app.orcinus.shadow.core.model.TextStylesOutcome
import app.orcinus.shadow.core.model.mesh
import app.orcinus.shadow.domain.placed
import app.orcinus.shadow.slicing.api.EmbossEditor
import app.orcinus.shadow.storage.api.SceneFiles
import kotlin.math.abs
import kotlinx.coroutines.CancellationException

/**
 * StyleManager's styles (EmbossStyleManager.cpp): the styles in the order the
 * text tool lists them, and the index of the last stored style loaded
 * (m_last_style_index), which a new text takes when the tool's style is a
 * temporary one.
 */
data class TextStyleList(val styles: List<TextStyle>, val lastIndex: Int)

/**
 * StyleManager's dealings with the app configuration and the fonts: the
 * styles it keeps, read when the text tool first needs them (init()), with
 * OrcaSlicer's default styles of the phone's fonts while none is kept; a
 * style loads when the phone has its font file (load_style()).
 */
class TextStylesUseCase(
    private val editor: EmbossEditor,
    private val fonts: TextFontsUseCase,
    private val sceneFiles: SceneFiles,
    private val repository: PlateRepository,
) {
    /**
     * StyleManager::init(): the kept styles, or the default styles with names
     * of their own ([names] translates theirs); the active style must load,
     * else it leaves the list and the first that loads is taken
     * (load_valid_style()). Null when no font of the phone loads.
     */
    suspend fun init(names: (String) -> String): TextStyleList? {
        val kept = (editor.textStyles() as? TextStylesOutcome.Success)?.stored
        val styles = kept?.styles.orEmpty().toMutableList()
        if (styles.isEmpty()) {
            // No styles loaded from ini file so use default
            fonts.defaultStyles(names).forEach { styles += it.copy(name = makeUniqueName(styles, it.name)) }
        }
        var active = kept?.active ?: 0
        if (active >= styles.size) active = 0
        // find valid font item
        if (styles.isNotEmpty() && canLoad(styles[active])) return TextStyleList(styles, active)
        // Try to fix that style can't be loaded
        if (styles.isNotEmpty()) styles.removeAt(active)
        return loadValidStyle(styles, names)
    }

    /** load_valid_style(): the first style that loads, the ones before it gone; the defaults when none does. */
    suspend fun loadValidStyle(styles: List<TextStyle>, names: (String) -> String): TextStyleList? {
        val remaining = styles.toMutableList()
        // iterate over all known styles
        while (remaining.isNotEmpty()) {
            if (canLoad(remaining[0])) return TextStyleList(remaining, 0)
            // can't load so erase it from list
            remaining.removeAt(0)
        }
        // no one style is loadable, set up default font list
        fonts.defaultStyles(names).forEach { remaining += it.copy(name = makeUniqueName(remaining, it.name)) }
        while (remaining.isNotEmpty()) {
            if (canLoad(remaining[0])) return TextStyleList(remaining, 0)
            remaining.removeAt(0)
        }
        return null
    }

    /** StyleManager::load_style(): the style's font file is one the phone has (create_font_file()). */
    suspend fun canLoad(style: TextStyle): Boolean = fonts.faces().any { it.path == style.fontPath }

    /** store_styles_to_app_config()'s writing: [styles] kept, with [active] the active one's index. */
    suspend fun store(styles: List<TextStyle>, active: Int): Boolean =
        try {
            editor.storeTextStyles(styles, active) is TextStylesOutcome.Success
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            false
        }

    /**
     * draw_style_rename_popup(): every text of the plate whose style is
     * [oldName] takes [newName], with no step of Undo, as the desktop app
     * renames them in its model. Returns the meshes of the objects written
     * anew, by the meshes they had.
     */
    suspend fun renameInVolumes(oldName: String, newName: String): Map<ScenePath, ScenePath> {
        val renamed = mutableMapOf<ScenePath, ScenePath>()
        val meshes = repository.state.value.objects.filter(PlateObject::hasText).map(PlateObject::mesh)
        for (mesh in meshes) {
            val state = repository.state.value
            val profiles = state.profiles ?: break
            val index = state.objects.indexOfFirst { it.mesh == mesh }
            if (state.busy || index < 0) continue
            val source = state.objects[index]
            val prefix = sceneFiles.newImportPrefix()
            val outcome = try {
                editor.renameTextStyle(state.objects.map { it.placed() }, index, oldName, newName, profiles, prefix)
            } catch (cancellation: CancellationException) {
                sceneFiles.deleteImport(prefix)
                throw cancellation
            } catch (error: Exception) {
                ModelLoadOutcome.Failure(error.message.orEmpty())
            }
            val made = (outcome as? ModelLoadOutcome.Success)?.objects?.singleOrNull()?.toPlateObjectOf(source)
            if (made == null) {
                sceneFiles.deleteImport(prefix)
                continue
            }
            var replaced = false
            repository.update { now ->
                replaced = now.objects.withMesh(mesh) != null
                if (!replaced) return@update now
                now.copy(
                    objects = now.objects.map { if (it.mesh == mesh) made else it },
                    // The selection follows the object to its new files.
                    selectedInstances = now.selectedInstances.mapTo(LinkedHashSet()) {
                        if (it.mesh == mesh) PlateInstanceId(made.mesh, it.instance) else it
                    },
                    selectedPart = now.selectedPart?.let { if (it.mesh == mesh) ObjectPartId(made.mesh, it.index) else it },
                    selectedRange = now.selectedRange?.let { if (it.mesh == mesh) LayerRangeId(made.mesh, it.index) else it },
                )
            }
            if (replaced) renamed[mesh] = made.mesh else sceneFiles.deleteImport(prefix)
        }
        return renamed
    }
}

/** Whether a volume of the object is a text (ModelVolume::is_text()). */
private fun PlateObject.hasText(): Boolean =
    volume.emboss?.kind == EmbossKind.TEXT || parts.any { it.emboss?.kind == EmbossKind.TEXT }

/**
 * make_unique_name() of EmbossStyleManager.cpp: [name], or "Text style" for
 * none, numbered " (2)", " (3)", ... while a style of [styles] has it.
 */
fun makeUniqueName(styles: List<TextStyle>, name: String): String {
    fun isUnique(name: String) = styles.none { it.name == name }
    // Style name can't be empty so default name is set
    var base = name.ifEmpty { "Text style" }
    // When name is already unique, nothing need to be changed
    if (isUnique(base)) return base
    // when there is previous version of style name only find number
    val pos = base.lastIndexOfAny(charArrayOf(' ', '('))
    if (base.endsWith(')') && pos >= 0) {
        // short name by ord number
        base = base.substring(0, pos)
    }
    var order = 1 // start with value 2 to represents same font name
    var newName: String
    do {
        newName = "$base (${++order})"
    } while (!isUnique(newName))
    return newName
}

/**
 * StyleManager::Style::operator==(), by which the text tool tells a modified
 * style: the name and the font, FontProp's values (the size, boldness and
 * skew nearly), the projection, and the distance and angle. A face of a font
 * collection is a font of its own here, as the desktop app's wxFont
 * descriptor names the face.
 */
fun TextStyle.sameStyleAs(other: TextStyle): Boolean =
    name == other.name && fontPath == other.fontPath && collectionNumber == other.collectionNumber &&
        charGap == other.charGap && lineGap == other.lineGap && perGlyph == other.perGlyph &&
        horizontalAlign == other.horizontalAlign && verticalAlign == other.verticalAlign &&
        isApprox(sizeInMm, other.sizeInMm) && isApprox(boldness, other.boldness) && isApprox(skew, other.skew) &&
        depth == other.depth && useSurface == other.useSurface && distance == other.distance && angle == other.angle

/** is_approx() of libslic3r with its EPSILON; two unset values are alike. */
private fun isApprox(value: Double?, test: Double?): Boolean = when {
    value == null || test == null -> value == test
    else -> abs(value - test) < APPROX_EPSILON
}

private const val APPROX_EPSILON = 1e-4
