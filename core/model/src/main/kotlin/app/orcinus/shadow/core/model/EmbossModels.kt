package app.orcinus.shadow.core.model

/** What a volume was embossed from: ModelVolume::is_text() or is_svg(). */
enum class EmbossKind { TEXT, SVG }

/**
 * ModelVolume::text_configuration and emboss_shape of a volume, which the
 * engine keeps in [file] for the volume to be embossed anew and stored in a
 * project; [kind] tells the text from the SVG.
 */
data class EmbossData(val file: ScenePath, val kind: EmbossKind)

/**
 * A face of a font file, as the font list of the text tool shows it: the
 * names of its naming table, its weight and whether it is italic; [index]
 * is the face in a collection (.ttc).
 */
data class FontFace(
    val path: String,
    val index: Int,
    val family: String,
    val subfamily: String,
    val weight: Int,
    val italic: Boolean,
    /** FontFile::Info::ascent in font units, which the advanced options scale their ranges by. */
    val ascent: Int = 0,
)

/**
 * A family of fonts, as the font list of the text tool names it (a face name
 * of wxFontEnumerator), with its faces, the regular one first.
 */
data class TextFontFamily(val name: String, val faces: List<FontFace>)

/** FontProp::HorizontalAlign */
enum class TextHorizontalAlign { LEFT, CENTER, RIGHT }

/** FontProp::VerticalAlign */
enum class TextVerticalAlign { TOP, CENTER, BOTTOM }

/**
 * StyleManager::Style: the style of an embossed text (EmbossStyle with its
 * FontProp, the font a file), how deep it is embossed and onto what
 * (EmbossProjection), and its turn about the surface's normal ([angle],
 * counterclockwise in radians) and its distance from the surface. A null
 * value is one FontProp leaves unset.
 */
data class TextStyle(
    val name: String,
    val fontPath: String,
    val sizeInMm: Double = 10.0,
    val perGlyph: Boolean = false,
    val horizontalAlign: TextHorizontalAlign = TextHorizontalAlign.CENTER,
    val verticalAlign: TextVerticalAlign = TextVerticalAlign.CENTER,
    val charGap: Int? = null,
    val lineGap: Int? = null,
    val boldness: Double? = null,
    val skew: Double? = null,
    val collectionNumber: Int? = null,
    /** FontProp::family, face_name, style and weight, by which another computer finds the font. */
    val family: String = "",
    val faceName: String = "",
    val style: String = "",
    val weight: String = "",
    val depth: Double = 1.0,
    val useSurface: Boolean = false,
    val angle: Double? = null,
    val distance: Double? = null,
)

/**
 * Where a new text or SVG goes: onto the surface of the copy [instanceIndex]
 * of the object at [objectIndex] where a ray from the screen hit it
 * ([position] and [normal] in world coordinates), beside the copy without a
 * hit, or for [objectIndex] -1 as an object of its own standing at [bedPoint]
 * on the bed (the plate's middle when null or off it).
 */
data class EmbossPlacement(
    val objectIndex: Int,
    val instanceIndex: Int = 0,
    val position: Vector3? = null,
    val normal: Vector3? = null,
    val bedPoint: Point2? = null,
) {
    companion object {
        /** An object of its own on the plate. */
        fun onBed(bedPoint: Point2?) = EmbossPlacement(objectIndex = -1, bedPoint = bedPoint)
    }
}

/**
 * What a text or SVG volume is, as its tool's window shows it: the text with
 * its style (whose angle and distance are measured from where the volume
 * stands), or the SVG's file name, whether that file is still there, and the
 * size of its shape; its type, whether it is the object's only part, and the
 * scale of its height and depth in the world.
 */
data class EmbossVolume(
    val kind: EmbossKind,
    val text: String,
    val style: TextStyle,
    val svgName: String,
    val svgReloadable: Boolean,
    val width: Double,
    val height: Double,
    val type: VolumeType,
    val onlyPart: Boolean,
    val scaleHeight: Double,
    val scaleDepth: Double,
)

/**
 * What the object list asks of the canvas's text or SVG tool: "Edit text" or
 * "Edit SVG" of a volume, or a text or SVG added to an object as a volume of
 * a type, which the canvas places on it as the desktop app places one without
 * a mouse position (start_create_volume_without_position()).
 */
sealed interface EmbossRequest {
    data class Edit(val volume: ObjectPartId) : EmbossRequest

    data class Add(val kind: EmbossKind, val mesh: ScenePath, val type: VolumeType) : EmbossRequest
}

/**
 * The text tool's styles the app configuration keeps (StyleManager's styles),
 * with the index of the active one it names; null for none.
 */
data class StoredTextStyles(val styles: List<TextStyle>, val active: Int?)

sealed interface TextStylesOutcome {
    data class Success(val stored: StoredTextStyles) : TextStylesOutcome

    data class Failure(val message: String) : TextStylesOutcome
}

sealed interface EmbossVolumeOutcome {
    data class Success(val volume: EmbossVolume) : EmbossVolumeOutcome

    data class Failure(val message: String) : EmbossVolumeOutcome
}
