package app.orcinus.shadow.slicing.api

import app.orcinus.shadow.core.model.EmbossPlacement
import app.orcinus.shadow.core.model.EmbossVolumeOutcome
import app.orcinus.shadow.core.model.FontFace
import app.orcinus.shadow.core.model.ModelLoadOutcome
import app.orcinus.shadow.core.model.ModelPath
import app.orcinus.shadow.core.model.PlacedModel
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.core.model.SlicingProfileSelection
import app.orcinus.shadow.core.model.TextStyle
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
}
