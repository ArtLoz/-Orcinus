package app.orcinus.shadow.slicing.api

import app.orcinus.shadow.core.model.EmbossPlacement
import app.orcinus.shadow.core.model.EmbossVolumeOutcome
import app.orcinus.shadow.core.model.FontFace
import app.orcinus.shadow.core.model.ModelLoadOutcome
import app.orcinus.shadow.core.model.ModelPath
import app.orcinus.shadow.core.model.PlacedModel
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.SlicingProfileSelection
import app.orcinus.shadow.core.model.SvgFileEdit
import app.orcinus.shadow.core.model.SvgPreviewOutcome
import app.orcinus.shadow.core.model.TextStyle
import app.orcinus.shadow.core.model.TextStylesOutcome
import app.orcinus.shadow.core.model.EmbossTransform
import app.orcinus.shadow.core.model.Transform3
import app.orcinus.shadow.core.model.VolumeType

/**
 * OrcaSlicer's text and SVG tools (GLGizmoEmboss, GLGizmoSVG): text and SVG
 * embossed on the objects of a plate. The edits write the object anew, named
 * after the prefix, as the other edits of an object do.
 */
interface EmbossEditor {
    /** The faces of the font files at [paths]; a file the engine can't read has none. */
    suspend fun describeFonts(paths: List<String>): List<FontFace>

    /** GLGizmoEmboss::create_volume(): [text] in [style] as a volume of [type] at [placement]. */
    suspend fun createText(
        plate: List<PlacedModel>,
        placement: EmbossPlacement,
        type: VolumeType,
        text: String,
        style: TextStyle,
        profiles: SlicingProfileSelection,
        prefix: ScenePath,
    ): ModelLoadOutcome

    /** GLGizmoEmboss::process(): the text [volume] of the object at [index] embossed anew; [placement] places it first. */
    suspend fun updateText(
        plate: List<PlacedModel>,
        index: Int,
        volume: Int,
        text: String,
        style: TextStyle,
        placement: Transform3?,
        profiles: SlicingProfileSelection,
        prefix: ScenePath,
    ): ModelLoadOutcome

    /** GLGizmoSVG::create_volume(): the SVG file [svg] as a volume of [type] at [placement]. */
    suspend fun createSvg(
        plate: List<PlacedModel>,
        placement: EmbossPlacement,
        type: VolumeType,
        svg: ModelPath,
        profiles: SlicingProfileSelection,
        prefix: ScenePath,
    ): ModelLoadOutcome

    /** GLGizmoSVG::process(): the SVG [volume] of the object at [index] [depth] deep, from [svg] when given. */
    suspend fun updateSvg(
        plate: List<PlacedModel>,
        index: Int,
        volume: Int,
        depth: Double,
        useSurface: Boolean,
        svg: ModelPath?,
        placement: Transform3?,
        profiles: SlicingProfileSelection,
        prefix: ScenePath,
    ): ModelLoadOutcome

    /** What the text or SVG [volume] of the object at [index] is. */
    suspend fun describeEmboss(plate: List<PlacedModel>, index: Int, volume: Int, profiles: SlicingProfileSelection): EmbossVolumeOutcome

    /**
     * The text [volume] of the object at [index], seen on its copy [instance],
     * turned and moved as [transform] says; then embossed anew from [text] and
     * [style] when [reEmboss] is set, or when it uses the surface or is placed per glyph.
     */
    suspend fun transformEmboss(
        plate: List<PlacedModel>,
        index: Int,
        instance: Int,
        volume: Int,
        transform: EmbossTransform,
        text: String,
        style: TextStyle,
        reEmboss: Boolean,
        profiles: SlicingProfileSelection,
        prefix: ScenePath,
    ): ModelLoadOutcome

    /**
     * draw_style_rename_popup(): the texts of the object at [index] whose
     * style is [oldName] take [newName]; the object anew when one did, none otherwise.
     */
    suspend fun renameTextStyle(
        plate: List<PlacedModel>,
        index: Int,
        oldName: String,
        newName: String,
        profiles: SlicingProfileSelection,
        prefix: ScenePath,
    ): ModelLoadOutcome

    /** GLGizmoSVG::draw_preview(): the SVG [volume] drawn into [picture], at most [maxSize] pixels on its longer side. */
    suspend fun previewSvg(plate: List<PlacedModel>, index: Int, volume: Int, picture: ScenePath, maxSize: Int, profiles: SlicingProfileSelection): SvgPreviewOutcome

    /** The SVG window's file menu on the SVG [volume]; [path] is where "Save as" writes it. */
    suspend fun editSvgFile(
        plate: List<PlacedModel>,
        index: Int,
        volume: Int,
        edit: SvgFileEdit,
        path: String,
        profiles: SlicingProfileSelection,
        prefix: ScenePath,
    ): ModelLoadOutcome

    /** StyleManager's styles as the app configuration keeps them (load_styles(), load_style_index()). */
    suspend fun textStyles(): TextStylesOutcome

    /** store_styles() and store_style_index(): [styles] kept, with [active] the active one's index. */
    suspend fun storeTextStyles(styles: List<TextStyle>, active: Int?): TextStylesOutcome
}
