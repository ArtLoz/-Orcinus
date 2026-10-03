package app.orcinus.shadow.feature.prepare

import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.orcinus.shadow.core.designsystem.component.OrcaButton
import app.orcinus.shadow.core.designsystem.component.OrcaButtonSize
import app.orcinus.shadow.core.designsystem.component.OrcaButtonStyle
import app.orcinus.shadow.core.designsystem.component.OrcaComboField
import app.orcinus.shadow.core.designsystem.component.OrcaContextMenu
import app.orcinus.shadow.core.designsystem.component.OrcaIconButton
import app.orcinus.shadow.core.designsystem.component.OrcaMenuItem
import app.orcinus.shadow.core.designsystem.component.OrcaRadioButton
import app.orcinus.shadow.core.designsystem.component.OrcaSheetHandle
import app.orcinus.shadow.core.designsystem.component.OrcaTextField
import app.orcinus.shadow.core.designsystem.component.textLocale
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import app.orcinus.shadow.core.model.EmbossRequest
import app.orcinus.shadow.core.model.ObjectPartId
import app.orcinus.shadow.core.model.Point2
import app.orcinus.shadow.core.model.TextFontFamily
import app.orcinus.shadow.core.model.TextHorizontalAlign
import app.orcinus.shadow.core.model.TextStyle
import app.orcinus.shadow.core.model.TextVerticalAlign
import app.orcinus.shadow.core.model.VolumeType
import app.orcinus.shadow.core.ui.orca.orcaString
import app.orcinus.shadow.render.scene.SurfaceHit
import java.io.File
import app.orcinus.shadow.core.designsystem.R as DesignR

/** What the text tool's window does (GLGizmoEmboss). */
internal class TextActions(
    /**
     * The toolbar's Text: the tool opens on the selected text, or adds one
     * where the canvas says ([hit] on the selected copy, [bedPoint] on the
     * plate without one), named [defaultText].
     */
    val toggle: (hit: SurfaceHit?, bedPoint: Point2?, defaultText: String) -> Unit,
    /** "Text" of the menus: over the copy [copy] where [hit] says, or as an object at [bedPoint]. */
    val add: (copy: Int?, type: VolumeType, hit: SurfaceHit?, bedPoint: Point2?, defaultText: String) -> Unit,
    /** The object list's "Add part" > "Text", which the canvas places. */
    val addRequested: (EmbossRequest.Add, SurfaceHit?, String) -> Unit,
    /** "Edit text" of the menus. */
    val edit: (ObjectPartId) -> Unit,
    val close: () -> Unit,
    val setText: (String) -> Unit,
    val setStyle: ((TextStyle) -> TextStyle) -> Unit,
    val setFont: (TextFontFamily) -> Unit,
    val toggleItalic: () -> Unit,
    val toggleBold: () -> Unit,
    val selectStyle: (TextStyle) -> Unit,
    val reset: () -> Unit,
    val setAdvanced: (Boolean) -> Unit,
    val setType: (VolumeType) -> Unit,
) {
    companion object {
        val NONE = TextActions({ _, _, _ -> }, { _, _, _, _, _ -> }, { _, _, _ -> }, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
    }
}

/**
 * GLGizmoEmboss::draw_window(): the text, its style and font with italic and
 * bold, its height and depth, the advanced options, the operation of a text
 * that is not its object's only part, Reset and Done. The desktop window
 * edits a style's every value with a slider and a field; a phone keeps the
 * sliders, with the values beside them, and the fields for the sizes. While
 * the font is one the phone has not, only another font can be chosen.
 */
@Composable
internal fun TextPanel(mode: TextMode, families: List<TextFontFamily>, actions: TextActions, imperial: Boolean) {
    val colors = OrcaTheme.colors
    val style = mode.style
    val stored = mode.styles.firstOrNull { it.name == style.name }
    val face = families.flatMap(TextFontFamily::faces).firstOrNull { it.path == style.fontPath && it.index == (style.collectionNumber ?: 0) }
    val family = families.firstOrNull { it.faces.any { face -> face.path == style.fontPath } }
    val editable = !mode.unknownFont
    var choosingFont by remember { mutableStateOf(false) }
    PaintingPanelFrame(orcaString("Emboss"), orcaString("Done"), actions.close) {
        TextInput(mode.text, actions.setText, enabled = editable, font = face?.let { typefaceOf(it.path, it.index) })
        // draw_text_input()'s warning.
        if (mode.blank) {
            Warning(orcaString("Embossed text cannot contain only white spaces."))
        }
        // draw_style_list()
        TextRow(orcaString("Style")) {
            var open by remember { mutableStateOf(false) }
            Box(Modifier.weight(1f)) {
                OrcaComboField(
                    text = style.name.ifEmpty { "—" } + if (stored != null && stored.copy(angle = style.angle, distance = style.distance) != style) "*" else "",
                    enabled = editable && mode.styles.isNotEmpty(),
                    onClick = { open = true },
                )
                if (open) {
                    OrcaContextMenu(expanded = true, position = androidx.compose.ui.unit.IntOffset.Zero, onDismissRequest = { open = false }) {
                        mode.styles.forEach { item ->
                            OrcaMenuItem(text = item.name, onClick = {
                                open = false
                                actions.selectStyle(item)
                            })
                        }
                    }
                }
            }
        }
        // draw_font_list_line(): the font, then italic and bold.
        TextRow(orcaString("Font")) {
            Box(Modifier.weight(1f)) {
                OrcaComboField(text = family?.name ?: style.faceName.ifEmpty { " --- " }, onClick = { choosingFont = true })
            }
            val italic = style.skew != null || face?.italic == true
            OrcaIconButton(
                icon = if (italic) DesignR.drawable.orca_make_unitalic else DesignR.drawable.orca_make_italic,
                contentDescription = orcaString(if (italic) "Unset italic" else "Set italic"),
                onClick = actions.toggleItalic,
                enabled = editable && face != null,
                tint = Color.Unspecified,
            )
            val bold = style.boldness != null || (face?.weight ?: 400) > 400
            OrcaIconButton(
                icon = if (bold) DesignR.drawable.orca_make_unbold else DesignR.drawable.orca_make_bold,
                contentDescription = orcaString(if (bold) "Unset bold" else "Set bold"),
                onClick = actions.toggleBold,
                enabled = editable && face != null,
                tint = Color.Unspecified,
            )
        }
        // draw_height() and draw_depth(): millimetres, or inches with "use_inches".
        TextRow(orcaString("Height")) {
            LengthField(style.sizeInMm, imperial, enabled = editable) { value ->
                actions.setStyle { it.copy(sizeInMm = value.coerceIn(SIZE_MIN, SIZE_MAX)) }
            }
        }
        TextRow(orcaString("Depth")) {
            LengthField(style.depth, imperial, enabled = editable) { value ->
                actions.setStyle { it.copy(depth = value.coerceIn(DEPTH_MIN, DEPTH_MAX)) }
            }
        }
        // "Advanced"
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .toggleable(value = mode.advanced, role = Role.Button, enabled = editable, onValueChange = actions.setAdvanced),
        ) {
            Icon(
                painterResource(DesignR.drawable.orca_drop_down),
                contentDescription = null,
                tint = colors.onCanvasPanel,
                modifier = Modifier
                    .size(16.dp)
                    .rotate(if (mode.advanced) 0f else -90f),
            )
            Text(orcaString("Advanced"), color = colors.onCanvasPanel, style = OrcaTheme.typography.body13, modifier = Modifier.padding(start = 6.dp))
        }
        if (mode.advanced && editable) {
            Advanced(mode, face?.ascent ?: DEFAULT_ASCENT, stored, actions)
        }
        // draw_model_type(): not for a text that is its object.
        if (!mode.onlyPart) {
            val type = mode.described?.type
            Text(orcaString("Operation"), color = colors.onCanvasPanel, style = OrcaTheme.typography.body13, modifier = Modifier.padding(top = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf(
                    VolumeType.PART to orcaString("Join"),
                    VolumeType.NEGATIVE to orcaString("Cut", "EmbossOperation"),
                    VolumeType.MODIFIER to orcaString("Modifier"),
                ).forEach { (item, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.selectable(selected = type == item, role = Role.RadioButton, enabled = editable && !mode.busy, onClick = { actions.setType(item) }),
                    ) {
                        OrcaRadioButton(selected = type == item, onClick = null)
                        Text(label, color = colors.onCanvasPanel, style = OrcaTheme.typography.body12)
                    }
                }
            }
        }
        // Reset: every option of the first style but the text and the operation.
        OrcaButton(
            text = orcaString("Reset"),
            size = OrcaButtonSize.Compact,
            style = OrcaButtonStyle.Regular,
            enabled = editable && mode.defaultStyle?.let { default -> default.copy(angle = style.angle, distance = style.distance) != style } == true,
            onClick = actions.reset,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
    if (choosingFont) {
        FontSheet(families, current = family, sample = mode.text, onDismiss = { choosingFont = false }, onChoose = { chosen ->
            choosingFont = false
            actions.setFont(chosen)
        })
    }
}

/**
 * draw_advanced(): use surface and per glyph (for a text that is not its
 * object's only part, or already using them), the alignment, the gaps
 * between characters and lines, the boldness and the skew, in the font's
 * points scaled by its ascent as the desktop sliders are; a value that is
 * not the style's has its revert.
 */
@Composable
private fun Advanced(mode: TextMode, ascent: Int, stored: TextStyle?, actions: TextActions) {
    val style = mode.style
    val onlyPart = mode.onlyPart
    TextCheck(orcaString("Use surface"), style.useSurface, enabled = style.useSurface || !onlyPart) { use ->
        // when using surface distance is not used
        actions.setStyle { it.copy(useSurface = use, distance = if (use) null else it.distance) }
    }
    TextCheck(orcaString("Per glyph"), style.perGlyph, enabled = style.perGlyph || !onlyPart) { use ->
        actions.setStyle { it.copy(perGlyph = use) }
    }
    TextRow(orcaString("Alignment")) {
        TextHorizontalAlign.entries.forEach { align ->
            AlignButton(ALIGN_ICONS_HORIZONTAL.getValue(align), orcaString(ALIGN_NAMES_HORIZONTAL.getValue(align), "Alignment"), style.horizontalAlign == align) {
                actions.setStyle { it.copy(horizontalAlign = align) }
            }
        }
        Box(Modifier.width(8.dp))
        TextVerticalAlign.entries.forEach { align ->
            AlignButton(ALIGN_ICONS_VERTICAL.getValue(align), orcaString(ALIGN_NAMES_VERTICAL.getValue(align), "Alignment"), style.verticalAlign == align) {
                actions.setStyle { it.copy(verticalAlign = align) }
            }
        }
    }
    val halfAscent = (ascent / 2).coerceAtLeast(1)
    val points = orcaString("points")
    val locale = textLocale()
    OptionalSlider(
        label = orcaString("Char gap"),
        value = style.charGap?.toFloat(),
        range = -halfAscent.toFloat()..halfAscent.toFloat(),
        text = { String.format(locale, "%.0f %s", it, points) },
        revert = stored?.charGap?.toFloat(),
        onChange = { value -> actions.setStyle { it.copy(charGap = value?.toInt()?.coerceIn(-CHAR_GAP_LIMIT, CHAR_GAP_LIMIT)) } },
    )
    OptionalSlider(
        label = orcaString("Line gap"),
        value = style.lineGap?.toFloat(),
        range = -halfAscent.toFloat()..halfAscent.toFloat(),
        text = { String.format(locale, "%.0f %s", it, points) },
        revert = stored?.lineGap?.toFloat(),
        enabled = mode.text.contains('\n'),
        onChange = { value -> actions.setStyle { it.copy(lineGap = value?.toInt()?.coerceIn(-CHAR_GAP_LIMIT, CHAR_GAP_LIMIT)) } },
    )
    OptionalSlider(
        label = orcaString("Boldness"),
        value = style.boldness?.toFloat(),
        range = (ascent * BOLDNESS_GUI_MIN)..(ascent * BOLDNESS_GUI_MAX),
        text = { String.format(locale, "%.0f %s", it, points) },
        revert = stored?.boldness?.toFloat(),
        onChange = { value -> actions.setStyle { it.copy(boldness = value?.toDouble()) } },
    )
    OptionalSlider(
        label = orcaString("Skew ratio"),
        value = style.skew?.toFloat(),
        range = SKEW_GUI_MIN..SKEW_GUI_MAX,
        text = { String.format(locale, "%.2f", it) },
        revert = stored?.skew?.toFloat(),
        onChange = { value -> actions.setStyle { it.copy(skew = value?.toDouble()) } },
    )
}

/** ImGui::InputTextMultiline("##Text"): the text in its own font, as the desktop input shows it. */
@Composable
private fun TextInput(text: String, onChange: (String) -> Unit, enabled: Boolean, font: FontFamily?) {
    val colors = OrcaTheme.colors
    BasicTextField(
        value = text,
        onValueChange = onChange,
        enabled = enabled,
        textStyle = OrcaTheme.typography.body14.copy(color = if (enabled) colors.text else colors.textDisabled, fontFamily = font ?: FontFamily.Default),
        cursorBrush = SolidColor(colors.accent),
        minLines = 2,
        maxLines = 4,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(colors.window, OrcaTheme.shapes.control)
            .border(1.dp, colors.border, OrcaTheme.shapes.control)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

/** A field of millimetres, or of inches with the Preferences' "use_inches", as rev_input_mm() takes it. */
@Composable
private fun androidx.compose.foundation.layout.RowScope.LengthField(millimetres: Double, imperial: Boolean, enabled: Boolean, onValue: (Double) -> Unit) {
    val scale = if (imperial) MM_TO_IN else 1.0
    PositionField(
        value = millimetres * scale,
        onValue = { if (enabled) onValue(it / scale) },
        modifier = Modifier.weight(1f),
    )
    Text(
        if (imperial) "in" else orcaString("mm"),
        color = OrcaTheme.colors.textSide,
        style = OrcaTheme.typography.body12,
        modifier = Modifier.padding(start = 6.dp),
    )
}

@Composable
private fun TextRow(label: String, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier
        .fillMaxWidth()
        .padding(top = 6.dp)) {
        Text(label, color = OrcaTheme.colors.onCanvasPanel, style = OrcaTheme.typography.body13, modifier = Modifier.width(LabelWidth))
        content()
    }
}

@Composable
private fun TextCheck(text: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, enabled = enabled, onValueChange = onChange),
    ) {
        app.orcinus.shadow.core.designsystem.component.OrcaCheckBox(checked = checked, onCheckedChange = null, enabled = enabled)
        Text(
            text = text,
            color = if (enabled) OrcaTheme.colors.onCanvasPanel else OrcaTheme.colors.textDimmed,
            style = OrcaTheme.typography.body12,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

@Composable
private fun AlignButton(icon: Int, description: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .background(if (selected) OrcaTheme.colors.accentSelected else Color.Transparent, OrcaTheme.shapes.control),
    ) {
        OrcaIconButton(icon = icon, contentDescription = description, onClick = onClick, tint = Color.Unspecified)
    }
}

/**
 * rev_slider() of an optional value: unset shows 0, a move sets it, and the
 * revert sets the stored style's value again.
 */
@Composable
private fun OptionalSlider(
    label: String,
    value: Float?,
    range: ClosedFloatingPointRange<Float>,
    text: (Float) -> String,
    revert: Float?,
    onChange: (Float?) -> Unit,
    enabled: Boolean = true,
) {
    val shown = (value ?: 0f).coerceIn(range)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            color = if (enabled) OrcaTheme.colors.onCanvasPanel else OrcaTheme.colors.textDimmed,
            style = OrcaTheme.typography.body12,
            modifier = Modifier.width(LabelWidth),
        )
        Slider(
            value = shown,
            onValueChange = { onChange(it) },
            valueRange = range,
            enabled = enabled,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = text(value ?: 0f),
            color = OrcaTheme.colors.onCanvasPanel,
            style = OrcaTheme.typography.body12,
            modifier = Modifier
                .padding(start = 6.dp)
                .width(ValueWidth),
        )
        if (value != revert) {
            OrcaIconButton(
                icon = DesignR.drawable.orca_undo,
                contentDescription = orcaString("Reset"),
                onClick = { onChange(revert) },
                enabled = enabled,
            )
        }
    }
}

@Composable
private fun Warning(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Icon(painterResource(DesignR.drawable.orca_exclamation), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(16.dp))
        Text(text, color = OrcaTheme.colors.onCanvasPanel, style = OrcaTheme.typography.body12, modifier = Modifier.padding(start = 6.dp))
    }
}

/**
 * draw_font_list(): the phone's fonts by family, each name written in its
 * own font (draw_font_preview()), with a filter as the desktop combo box has.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FontSheet(families: List<TextFontFamily>, current: TextFontFamily?, sample: String, onDismiss: () -> Unit, onChoose: (TextFontFamily) -> Unit) {
    val colors = OrcaTheme.colors
    var filter by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.window, dragHandle = { OrcaSheetHandle() }) {
        Column(Modifier.navigationBarsPadding()) {
            Text(orcaString("Font"), color = colors.text, style = OrcaTheme.typography.head16, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            OrcaTextField(
                value = filter,
                onValueChange = { filter = it },
                hint = orcaString("Search"),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            val shown = families.filter { it.name.contains(filter.trim(), ignoreCase = true) }
            LazyColumn(Modifier.heightIn(max = 480.dp)) {
                items(shown, key = TextFontFamily::name) { family ->
                    val face = family.faces.first()
                    val font = remember(face.path, face.index) { typefaceOf(face.path, face.index) }
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(if (family.name == current?.name) colors.accentSelected else Color.Transparent)
                            .selectable(selected = family.name == current?.name, role = Role.Button, onClick = { onChoose(family) })
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text(family.name, color = colors.text, style = OrcaTheme.typography.body14)
                        if (font != null) {
                            Text(
                                text = sample.lineSequence().firstOrNull()?.takeIf(String::isNotBlank) ?: family.name,
                                color = colors.textSide,
                                style = OrcaTheme.typography.body14.copy(fontFamily = font),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** The face [index] of the font file at [path] as Compose draws it; null when Android can't load it. */
private fun typefaceOf(path: String, index: Int): FontFamily? = runCatching {
    FontFamily(androidx.compose.ui.text.font.Typeface(Typeface.Builder(File(path)).setTtcIndex(index).build()))
}.getOrNull()

/** GLGizmoEmboss.cpp's limits: size_in_mm, emboss depth, char and line gaps, and the sliders of boldness and skew. */
private const val SIZE_MIN = 0.1
private const val SIZE_MAX = 1000.0
private const val DEPTH_MIN = 0.01
private const val DEPTH_MAX = 1e4
private const val CHAR_GAP_LIMIT = 20000
private const val BOLDNESS_GUI_MIN = -0.5f
private const val BOLDNESS_GUI_MAX = 0.5f
private const val SKEW_GUI_MIN = -1f
private const val SKEW_GUI_MAX = 1f

/** An ascent for the sliders' ranges while the font is not known. */
private const val DEFAULT_ASCENT = 1000

/** GizmoObjectManipulation::mm_to_in */
private const val MM_TO_IN = 0.0393700787

private val ALIGN_ICONS_HORIZONTAL = mapOf(
    TextHorizontalAlign.LEFT to DesignR.drawable.orca_align_horizontal_left,
    TextHorizontalAlign.CENTER to DesignR.drawable.orca_align_horizontal_center,
    TextHorizontalAlign.RIGHT to DesignR.drawable.orca_align_horizontal_right,
)
private val ALIGN_ICONS_VERTICAL = mapOf(
    TextVerticalAlign.TOP to DesignR.drawable.orca_align_vertical_top,
    TextVerticalAlign.CENTER to DesignR.drawable.orca_align_vertical_center,
    TextVerticalAlign.BOTTOM to DesignR.drawable.orca_align_vertical_bottom,
)
private val ALIGN_NAMES_HORIZONTAL = mapOf(TextHorizontalAlign.LEFT to "Left", TextHorizontalAlign.CENTER to "Center", TextHorizontalAlign.RIGHT to "Right")
private val ALIGN_NAMES_VERTICAL = mapOf(TextVerticalAlign.TOP to "Top", TextVerticalAlign.CENTER to "Middle", TextVerticalAlign.BOTTOM to "Bottom")

private val LabelWidth = 96.dp
private val ValueWidth = 72.dp
