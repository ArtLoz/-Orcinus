package app.orcinus.shadow.feature.prepare

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
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
import app.orcinus.shadow.core.model.DialogIcon
import app.orcinus.shadow.core.model.EmbossRequest
import app.orcinus.shadow.core.model.ObjectPartId
import app.orcinus.shadow.core.model.OrcaText
import app.orcinus.shadow.core.model.Point2
import app.orcinus.shadow.core.model.SettingsDialog
import app.orcinus.shadow.core.model.TextFontFamily
import app.orcinus.shadow.core.model.TextHorizontalAlign
import app.orcinus.shadow.core.model.TextStyle
import app.orcinus.shadow.core.model.TextVerticalAlign
import app.orcinus.shadow.core.model.Transform3
import app.orcinus.shadow.core.model.VolumeType
import app.orcinus.shadow.core.ui.orca.orcaString
import app.orcinus.shadow.core.ui.orca.orcaText
import app.orcinus.shadow.core.ui.settings.SettingsNoticeDialog
import app.orcinus.shadow.core.ui.settings.SettingsQuestionDialog
import app.orcinus.shadow.render.scene.CameraEye
import app.orcinus.shadow.render.scene.SurfaceHit
import java.io.File
import kotlin.math.abs
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
    /** The style list: the stored style at the index. */
    val selectStyle: (Int) -> Unit,
    val reset: () -> Unit,
    val setAdvanced: (Boolean) -> Unit,
    val setType: (VolumeType) -> Unit,
    /** The style list's buttons: save, save as a new style, rename, and delete with its answer. */
    val saveStyle: () -> Unit,
    val addStyle: (String) -> Unit,
    val renameStyle: (String) -> Unit,
    val askDeleteStyle: () -> Unit,
    val deleteStyle: (Boolean) -> Unit,
    /** A style dragged over its neighbour. */
    val swapStyles: (Int, Int) -> Unit,
    val dismissNotice: () -> Unit,
    /** The From surface slider let go, in millimetres; null for none. */
    val moveText: (Double?) -> Unit,
    /** The Rotation slider let go, in degrees clockwise. */
    val rotateText: (Double) -> Unit,
    /** The lock beside Rotation. */
    val setKeepUp: (Boolean) -> Unit,
    /** "Set text to face camera", with the camera of the canvas. */
    val faceCamera: (CameraEye?) -> Unit,
    /** "Collection": a face of a font file of several. */
    val setCollection: (Int) -> Unit,
    /** The text where a finger left it on its object (SurfaceDrag). */
    val drag: (Transform3) -> Unit,
    /** The text let go on its rotation ring, turned by the angle (radians) about its own Z axis. */
    val turn: (Double) -> Unit,
    /** A double tap on the volume (its mesh) of the copy at an index: a text or an SVG opens its tool. */
    val openByDoubleTap: (index: Int, key: String) -> Unit = { _, _ -> },
) {
    companion object {
        val NONE = TextActions(
            { _, _, _ -> }, { _, _, _, _, _ -> }, { _, _, _ -> }, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {},
            {}, {}, {}, {}, {}, { _, _ -> }, {},
            {}, {}, {}, {}, {}, {}, {},
        )
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
internal fun TextPanel(
    mode: TextMode,
    families: List<TextFontFamily>,
    actions: TextActions,
    imperial: Boolean,
    eye: () -> CameraEye?,
    modifiersOffered: Boolean = true,
) {
    val colors = OrcaTheme.colors
    val style = mode.style
    val stored = mode.storedStyle
    val face = families.flatMap(TextFontFamily::faces).firstOrNull { it.path == style.fontPath && it.index == (style.collectionNumber ?: 0) }
    val family = families.firstOrNull { it.faces.any { face -> face.path == style.fontPath } }
    val editable = !mode.unknownFont
    var choosingFont by remember { mutableStateOf(false) }
    var choosingStyle by remember { mutableStateOf(false) }
    var naming by remember { mutableStateOf<StyleNaming?>(null) }
    // draw_style_list()'s question before a modified style is left.
    var leavingFor by remember { mutableStateOf<Int?>(null) }
    // m_scale_height and m_scale_depth: how large the text stands in the world.
    val heightScale = mode.described?.scaleHeight ?: 1.0
    val depthScale = mode.described?.scaleDepth ?: 1.0
    PaintingPanelFrame(orcaString("Emboss"), orcaString("Done"), actions.close) {
        // draw_text_input(): the text in its font, at the size the style gives it
        // (get_imgui_font_size()) within the input's limits, and its warning.
        val inputFont = remember(face?.path, face?.index) { face?.let { inputFontOf(it.path, it.index) } }
        val inputSize = inputFont?.let { it.lineHeightPerEm * abs(style.sizeInMm) / POINT_MM * heightScale }
        TextInput(
            mode.text,
            actions.setText,
            enabled = editable,
            font = face?.let { typefaceOf(it.path, it.index) },
            size = (inputSize ?: MIN_INPUT_FONT_SIZE).coerceIn(MIN_INPUT_FONT_SIZE, MAX_INPUT_FONT_SIZE),
        )
        inputWarning(mode, known = face != null, font = inputFont, size = inputSize)?.let { Warning(it) }
        // draw_style_list(): the label in OrcaSlicer's colour while the style is a temporary one.
        TextRow(orcaString("Style"), labelColor = if (mode.styleIndex == null) colors.accent else colors.onCanvasPanel) {
            Box(Modifier.weight(1f)) {
                OrcaComboField(
                    // add_text_modify(): the mark of a modified preset.
                    text = style.name.ifEmpty { "—" } + if (mode.styleModified) orcaString("*") else "",
                    enabled = editable && mode.styles.isNotEmpty(),
                    onClick = { choosingStyle = true },
                )
            }
        }
        StyleButtons(mode, editable, onRename = { naming = StyleNaming.RENAME }, onSaveAs = { naming = StyleNaming.SAVE_AS }, actions)
        // draw_font_list_line(): the font, then italic and bold, and the revert of
        // the font's changes; the label in the colour of a modified value.
        val fontChanged = stored != null && fontChanged(style, stored, families)
        TextRow(orcaString("Font"), labelColor = if (fontChanged || stored == null) colors.labelModified else colors.onCanvasPanel) {
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
            if (fontChanged && stored != null) {
                RevertButton(orcaString("Revert font changes."), enabled = editable) {
                    actions.setStyle {
                        it.copy(
                            fontPath = stored.fontPath,
                            collectionNumber = stored.collectionNumber,
                            faceName = stored.faceName,
                            boldness = stored.boldness,
                            skew = stored.skew,
                        )
                    }
                }
            }
        }
        // draw_height() and draw_depth(): millimetres, or inches with "use_inches",
        // as large as the text stands in the world (rev_input_mm() with
        // m_scale_height and m_scale_depth); the limits hold for the style.
        // Each reverts to the stored style's.
        val heightChanged = stored != null && abs(style.sizeInMm - stored.sizeInMm) > REVERT_EPSILON
        TextRow(orcaString("Height"), labelColor = if (heightChanged || stored == null) colors.labelModified else colors.onCanvasPanel) {
            LengthField(style.sizeInMm * heightScale, imperial, enabled = editable) { value ->
                actions.setStyle { it.copy(sizeInMm = (value / heightScale).coerceIn(SIZE_MIN, SIZE_MAX)) }
            }
            if (heightChanged && stored != null) {
                RevertButton(orcaString("Revert text size."), enabled = editable) { actions.setStyle { it.copy(sizeInMm = stored.sizeInMm) } }
            }
        }
        val depthChanged = stored != null && style.depth != stored.depth
        TextRow(orcaString("Depth"), labelColor = if (depthChanged || stored == null) colors.labelModified else colors.onCanvasPanel) {
            LengthField(style.depth * depthScale, imperial, enabled = editable) { value ->
                actions.setStyle { it.copy(depth = (value / depthScale).coerceIn(DEPTH_MIN, DEPTH_MAX)) }
            }
            if (depthChanged && stored != null) {
                RevertButton(orcaString("Revert embossed depth."), enabled = editable) { actions.setStyle { it.copy(depth = stored.depth) } }
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
            // ff.font_file->infos: the faces of the font's file.
            val collection = families.sumOf { item -> item.faces.count { it.path == style.fontPath } }
            Advanced(mode, face?.ascent ?: DEFAULT_ASCENT, stored, actions, imperial, collection, eye)
        }
        // draw_model_type(): not for a text that is its object.
        if (!mode.onlyPart) {
            EmbossOperation(mode.described?.type, enabled = editable && !mode.busy, modifiersOffered = modifiersOffered, onType = actions.setType)
        }
        // Reset: every option of the first style but the text and the operation.
        OrcaButton(
            text = orcaString("Reset"),
            size = OrcaButtonSize.Compact,
            style = OrcaButtonStyle.Regular,
            enabled = editable && mode.resettable,
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
    if (choosingStyle) {
        StyleSheet(mode, sample = mode.text, onDismiss = { choosingStyle = false }, onSwap = actions.swapStyles, onChoose = { index ->
            choosingStyle = false
            // Check whether user wants lose actual style modification
            if (mode.styleModified) leavingFor = index else actions.selectStyle(index)
        })
    }
    StyleDialogs(mode, naming, leavingFor, onNamed = { naming = null }, onLeft = { leavingFor = null }, actions)
}

/** The popups of draw_style_rename_button() and draw_style_add_button(). */
private enum class StyleNaming { RENAME, SAVE_AS }

/**
 * draw_style_rename_button(), draw_style_save_button(), draw_style_add_button()
 * and draw_delete_style_button(), under the style list; their tooltips name them.
 */
@Composable
private fun StyleButtons(mode: TextMode, editable: Boolean, onRename: () -> Unit, onSaveAs: () -> Unit, actions: TextActions) {
    val stored = mode.styleIndex != null
    val name = mode.style.name
    val last = mode.styles.size == 1
    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
        OrcaIconButton(
            icon = DesignR.drawable.orca_edit_button,
            contentDescription = orcaString(if (stored) "Rename current style." else "Can't rename temporary style."),
            onClick = onRename,
            enabled = editable && stored,
            tint = Color.Unspecified,
        )
        OrcaIconButton(
            icon = DesignR.drawable.orca_save,
            contentDescription = when {
                !stored -> orcaString("First Add style to list.")
                mode.styleModified -> orcaText(OrcaText("Save %1% style", listOf(name)))
                else -> orcaString("No changes to save.")
            },
            onClick = actions.saveStyle,
            enabled = editable && mode.styleModified,
            tint = Color.Unspecified,
        )
        OrcaIconButton(
            icon = DesignR.drawable.orca_add_copies,
            contentDescription = orcaString(if (stored) "Save as new style." else "Add style to my list."),
            // A temporary style joins the list as it is.
            onClick = { if (stored) onSaveAs() else actions.saveStyle() },
            enabled = editable,
            tint = Color.Unspecified,
        )
        OrcaIconButton(
            icon = DesignR.drawable.orca_delete,
            contentDescription = when {
                stored && !last -> orcaText(OrcaText("Delete \"%1%\" style.", listOf(name)))
                last -> orcaText(OrcaText("Can't delete \"%1%\". It is last style.", listOf(name)))
                else -> orcaText(OrcaText("Can't delete temporary style \"%1%\".", listOf(name)))
            },
            onClick = actions.askDeleteStyle,
            enabled = editable && stored && !last,
            tint = Color.Unspecified,
        )
    }
}

/**
 * The window's popups and message boxes: a style's new name (rename, or save
 * as a new style), leaving a modified style, removing a style, and the
 * messages of the style list.
 */
@Composable
private fun StyleDialogs(mode: TextMode, naming: StyleNaming?, leavingFor: Int?, onNamed: () -> Unit, onLeft: () -> Unit, actions: TextActions) {
    when (naming) {
        StyleNaming.RENAME -> {
            val old = mode.storedStyle?.name.orEmpty()
            StyleNameDialog(
                title = orcaString("Rename style"),
                label = orcaText(OrcaText("Rename style (%1%) for embossing text", listOf(old))) + ": ",
                initial = mode.style.name,
                // could be same as before rename
                unique = { name -> name == old || mode.styles.none { it.name == name } },
                onDismiss = onNamed,
                onConfirm = { name ->
                    onNamed()
                    actions.renameStyle(name)
                },
            )
        }
        StyleNaming.SAVE_AS -> StyleNameDialog(
            title = orcaString("Save as new style"),
            label = orcaString("New name of style") + ": ",
            initial = mode.style.name,
            unique = { name -> mode.styles.none { it.name == name } },
            onDismiss = onNamed,
            onConfirm = { name ->
                onNamed()
                actions.addStyle(name)
            },
        )
        null -> Unit
    }
    leavingFor?.let { index ->
        val name = mode.styles.getOrNull(index)?.name.orEmpty()
        SettingsQuestionDialog(
            SettingsDialog(
                id = "emboss_style_change",
                icon = DialogIcon.WARNING,
                title = listOf(OrcaText("Warning")),
                text = listOf(OrcaText("Changing style to \"%1%\" will discard current style modification.\n\nWould you like to continue anyway?", listOf(name))),
                question = true,
                yes = null,
                no = null,
            ),
        ) { yes ->
            onLeft()
            if (yes) actions.selectStyle(index)
        }
    }
    mode.deleting?.let { name ->
        SettingsQuestionDialog(
            SettingsDialog(
                id = "emboss_style_remove",
                icon = DialogIcon.WARNING,
                title = listOf(OrcaText("Remove style")),
                text = listOf(OrcaText("Are you sure you want to permanently remove the \"%1%\" style?", listOf(name))),
                question = true,
                yes = null,
                no = null,
            ),
            onAnswer = actions.deleteStyle,
        )
    }
    when (val notice = mode.notice) {
        is TextNotice.InvalidStyle -> SettingsNoticeDialog(
            SettingsDialog(
                id = "emboss_style_invalid",
                icon = DialogIcon.INFO,
                title = listOf(OrcaText("Not valid style.")),
                text = listOf(OrcaText("Style \"%1%\" can't be used and will be removed from a list.", listOf(notice.name))),
                question = false,
                yes = null,
                no = null,
            ),
            onDismiss = actions.dismissNotice,
        )
        TextNotice.LastStyle -> SettingsNoticeDialog(
            SettingsDialog(
                id = "emboss_style_last",
                icon = DialogIcon.ERROR,
                title = listOf(OrcaText("Remove style")),
                text = listOf(OrcaText("Can't remove the last existing style.")),
                question = false,
                yes = null,
                no = null,
            ),
            onDismiss = actions.dismissNotice,
        )
        null -> Unit
    }
}

/**
 * The popups of the style list's names: an empty name or one another style
 * has can't be taken ([unique]), as the popups tell under the field.
 */
@Composable
private fun StyleNameDialog(title: String, label: String, initial: String, unique: (String) -> Boolean, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val colors = OrcaTheme.colors
    var name by rememberSaveable { mutableStateOf(initial) }
    val problem = when {
        name.isEmpty() -> orcaString("Name can't be empty.")
        !unique(name) -> orcaString("Name has to be unique.")
        else -> null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { OrcaButton(orcaString("OK"), onClick = { onConfirm(name) }, enabled = problem == null) },
        dismissButton = { OrcaButton(orcaString("Cancel"), onClick = onDismiss, style = OrcaButtonStyle.Regular) },
        title = { Text(title, style = OrcaTheme.typography.head16) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(label, style = OrcaTheme.typography.body14)
                OrcaTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (problem == null) onConfirm(name) }),
                )
                if (problem != null) {
                    Text(problem, color = colors.warning, style = OrcaTheme.typography.body13, modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
        containerColor = colors.window,
        titleContentColor = colors.text,
        textContentColor = colors.text,
        shape = OrcaTheme.shapes.window,
    )
}

/**
 * draw_style_list()'s combo box: the stored styles, the text written in each
 * one's font (init_style_images()), the tool's marked. After a long press a
 * style is dragged over its neighbours, as the desktop list reorders them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StyleSheet(mode: TextMode, sample: String, onDismiss: () -> Unit, onSwap: (Int, Int) -> Unit, onChoose: (Int) -> Unit) {
    val colors = OrcaTheme.colors
    val rowHeight = with(LocalDensity.current) { StyleRowHeight.toPx() }
    val count by rememberUpdatedState(mode.styles.size)
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.window, dragHandle = { OrcaSheetHandle() }) {
        Column(
            Modifier
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
        ) {
            Text(orcaString("Style"), color = colors.text, style = OrcaTheme.typography.head16, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            mode.styles.forEachIndexed { index, item ->
                key(item.name) {
                    val at by rememberUpdatedState(index)
                    var dragged by remember { mutableFloatStateOf(0f) }
                    val font = remember(item.fontPath, item.collectionNumber) { typefaceOf(item.fontPath, item.collectionNumber ?: 0) }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = StyleRowHeight)
                            .background(if (index == mode.styleIndex) colors.accentSelected else Color.Transparent)
                            .pointerInput(Unit) {
                                detectDragGesturesAfterLongPress(
                                    onDragEnd = { dragged = 0f },
                                    onDragCancel = { dragged = 0f },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragged += amount.y
                                        // reorder items
                                        if (dragged > rowHeight / 2 && at + 1 < count) {
                                            onSwap(at, at + 1)
                                            dragged -= rowHeight
                                        } else if (dragged < -rowHeight / 2 && at > 0) {
                                            onSwap(at, at - 1)
                                            dragged += rowHeight
                                        }
                                    },
                                )
                            }
                            .selectable(selected = index == mode.styleIndex, role = Role.Button, onClick = { onChoose(at) })
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Text(item.name, color = colors.text, style = OrcaTheme.typography.body14, maxLines = 1, modifier = Modifier.weight(1f))
                        if (font != null) {
                            Text(
                                text = sample.lineSequence().firstOrNull()?.takeIf(String::isNotBlank) ?: item.name,
                                color = colors.textSide,
                                style = OrcaTheme.typography.body14.copy(fontFamily = font),
                                maxLines = 1,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 12.dp),
                            )
                        }
                    }
                }
            }
        }
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
private fun Advanced(mode: TextMode, ascent: Int, stored: TextStyle?, actions: TextActions, imperial: Boolean, collection: Int, eye: () -> CameraEye?) {
    val style = mode.style
    val onlyPart = mode.onlyPart
    val colors = OrcaTheme.colors
    val labelColor = { changed: Boolean -> if (changed || stored == null) colors.labelModified else colors.onCanvasPanel }
    // when using surface distance is not used
    val useSurface = { use: Boolean -> actions.setStyle { it.copy(useSurface = use, distance = if (use) null else it.distance) } }
    val surfaceChanged = stored != null && style.useSurface != stored.useSurface
    TextCheck(
        orcaString("Use surface"),
        style.useSurface,
        enabled = style.useSurface || !onlyPart,
        labelColor = labelColor(surfaceChanged),
        revert = stored?.takeIf { surfaceChanged }?.let { { useSurface(it.useSurface) } },
        revertDescription = orcaString("Revert using of model surface."),
        onChange = useSurface,
    )
    val perGlyphChanged = stored != null && style.perGlyph != stored.perGlyph
    TextCheck(
        orcaString("Per glyph"),
        style.perGlyph,
        enabled = style.perGlyph || !onlyPart,
        labelColor = labelColor(perGlyphChanged),
        revert = stored?.takeIf { perGlyphChanged }?.let { { actions.setStyle { current -> current.copy(perGlyph = it.perGlyph) } } },
        revertDescription = orcaString("Revert Transformation per glyph."),
    ) { use ->
        actions.setStyle { it.copy(perGlyph = use) }
    }
    val alignChanged = stored != null && (style.horizontalAlign != stored.horizontalAlign || style.verticalAlign != stored.verticalAlign)
    TextRow(orcaString("Alignment"), labelColor = labelColor(alignChanged)) {
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
        if (alignChanged && stored != null) {
            RevertButton(orcaString("Revert alignment.")) {
                actions.setStyle { it.copy(horizontalAlign = stored.horizontalAlign, verticalAlign = stored.verticalAlign) }
            }
        }
    }
    val halfAscent = (ascent / 2).coerceAtLeast(1)
    val points = orcaString("points")
    val locale = textLocale()
    OptionalSlider(
        label = orcaString("Char gap"),
        hasRevert = stored != null,
        value = style.charGap?.toFloat(),
        range = -halfAscent.toFloat()..halfAscent.toFloat(),
        text = { String.format(locale, "%.0f %s", it, points) },
        revert = stored?.charGap?.toFloat(),
        onChange = { value -> actions.setStyle { it.copy(charGap = value?.toInt()?.coerceIn(-CHAR_GAP_LIMIT, CHAR_GAP_LIMIT)) } },
    )
    OptionalSlider(
        label = orcaString("Line gap"),
        hasRevert = stored != null,
        value = style.lineGap?.toFloat(),
        range = -halfAscent.toFloat()..halfAscent.toFloat(),
        text = { String.format(locale, "%.0f %s", it, points) },
        revert = stored?.lineGap?.toFloat(),
        enabled = mode.text.contains('\n'),
        onChange = { value -> actions.setStyle { it.copy(lineGap = value?.toInt()?.coerceIn(-CHAR_GAP_LIMIT, CHAR_GAP_LIMIT)) } },
    )
    OptionalSlider(
        label = orcaString("Boldness"),
        hasRevert = stored != null,
        value = style.boldness?.toFloat(),
        range = (ascent * BOLDNESS_GUI_MIN)..(ascent * BOLDNESS_GUI_MAX),
        text = { String.format(locale, "%.0f %s", it, points) },
        revert = stored?.boldness?.toFloat(),
        onChange = { value -> actions.setStyle { it.copy(boldness = value?.toDouble()) } },
    )
    OptionalSlider(
        label = orcaString("Skew ratio"),
        hasRevert = stored != null,
        value = style.skew?.toFloat(),
        range = SKEW_GUI_MIN..SKEW_GUI_MAX,
        text = { String.format(locale, "%.2f", it) },
        revert = stored?.skew?.toFloat(),
        onChange = { value -> actions.setStyle { it.copy(skew = value?.toDouble()) } },
    )
    // input surface distance, in inches with "use_inches"
    val scale = if (imperial) MM_TO_IN else 1.0
    val maxDistance = (2 * style.depth * scale).toFloat()
    CommittedSlider(
        label = orcaString("From surface"),
        value = style.distance?.let { (it * scale).toFloat() },
        range = -maxDistance..maxDistance,
        text = { String.format(locale, if (imperial) "%.3f in" else "%.2f mm", it) },
        revert = stored?.let { it.distance?.let { distance -> (distance * scale).toFloat() } },
        hasRevert = stored != null,
        modified = stored == null || style.distance != stored.distance,
        enabled = !style.useSurface && !onlyPart && !mode.busy,
        onCommit = { value -> actions.moveText(value?.let { it / scale }) },
    )
    // slider for Clockwise angle in degress; stored angle is optional CCW and in radians
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            CommittedSlider(
                label = orcaString("Rotation"),
                value = Math.toDegrees(-(style.angle ?: 0.0)).toFloat(),
                range = ANGLE_MIN..ANGLE_MAX,
                text = { String.format(locale, "%.2f °", it) },
                revert = stored?.let { Math.toDegrees(-(it.angle ?: 0.0)).toFloat() },
                hasRevert = stored != null,
                modified = stored == null || style.angle != stored.angle,
                enabled = !mode.busy,
                onCommit = { value -> actions.rotateText((value ?: 0f).toDouble()) },
            )
        }
        // Keep up - lock button icon
        if (!onlyPart) {
            OrcaIconButton(
                icon = if (mode.keepUp) DesignR.drawable.orca_lock_closed else DesignR.drawable.orca_lock_open,
                contentDescription = orcaString(
                    if (mode.keepUp) "Unlock the text's rotation when moving text along the object's surface."
                    else "Lock the text's rotation when moving text along the object's surface.",
                ),
                onClick = { actions.setKeepUp(!mode.keepUp) },
                tint = Color.Unspecified,
            )
        }
    }
    // when more collection add selector
    if (collection > 1) {
        TextRow(orcaString("Collection")) {
            var open by remember { mutableStateOf(false) }
            Box(Modifier.weight(1f)) {
                OrcaComboField(text = (style.collectionNumber ?: 0).toString(), onClick = { open = true })
                if (open) {
                    OrcaContextMenu(expanded = true, position = androidx.compose.ui.unit.IntOffset.Zero, onDismissRequest = { open = false }) {
                        repeat(collection) { index ->
                            OrcaMenuItem(text = index.toString(), onClick = {
                                open = false
                                actions.setCollection(index)
                            })
                        }
                    }
                }
            }
        }
    }
    OrcaButton(
        text = orcaString("Set text to face camera"),
        size = OrcaButtonSize.Compact,
        style = OrcaButtonStyle.Regular,
        enabled = !mode.busy,
        onClick = { actions.faceCamera(eye()) },
        modifier = Modifier.padding(top = 8.dp),
    )
}

/**
 * rev_slider() of a value the text is moved by: the slider follows the
 * finger, and the change applies once it lets go, as the desktop app moves
 * the model once the slider is left (deactivated_after_edit); the revert to
 * the stored style's value applies at once.
 */
@Composable
internal fun CommittedSlider(
    label: String,
    value: Float?,
    range: ClosedFloatingPointRange<Float>,
    text: (Float) -> String,
    revert: Float?,
    hasRevert: Boolean,
    enabled: Boolean,
    onCommit: (Float?) -> Unit,
    /** The label in ImGuiWrapper::COL_MODIFIED, as the SVG window marks a value it has. */
    modified: Boolean = false,
) {
    var held by remember { mutableStateOf<Float?>(null) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            color = when {
                !enabled -> OrcaTheme.colors.textDimmed
                modified -> OrcaTheme.colors.labelModified
                else -> OrcaTheme.colors.onCanvasPanel
            },
            style = OrcaTheme.typography.body12,
            modifier = Modifier.width(LabelWidth),
        )
        Slider(
            value = (held ?: value ?: 0f).coerceIn(range),
            onValueChange = { held = it },
            onValueChangeFinished = {
                held?.let(onCommit)
                held = null
            },
            valueRange = range,
            enabled = enabled,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = text(held ?: value ?: 0f),
            color = OrcaTheme.colors.onCanvasPanel,
            style = OrcaTheme.typography.body12,
            modifier = Modifier
                .padding(start = 6.dp)
                .width(ValueWidth),
        )
        if (hasRevert && value != revert) {
            OrcaIconButton(
                icon = DesignR.drawable.orca_undo,
                contentDescription = orcaString("Reset"),
                onClick = { onCommit(revert) },
                enabled = enabled,
            )
        }
    }
}

/** ImGui::InputTextMultiline("##Text"): the text in its own font, as the desktop input shows it. */
@Composable
private fun TextInput(text: String, onChange: (String) -> Unit, enabled: Boolean, font: FontFamily?, size: Double) {
    val colors = OrcaTheme.colors
    val fontSize = with(LocalDensity.current) { size.toFloat().dp.toSp() }
    BasicTextField(
        value = text,
        onValueChange = onChange,
        enabled = enabled,
        textStyle = OrcaTheme.typography.body14.copy(
            color = if (enabled) colors.text else colors.textDisabled,
            fontFamily = font ?: FontFamily.Default,
            fontSize = fontSize,
            lineHeight = fontSize * INPUT_LINE_HEIGHT,
        ),
        cursorBrush = SolidColor(colors.accent),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .height(TEXT_INPUT_HEIGHT)
            .background(colors.window, OrcaTheme.shapes.control)
            .border(1.dp, colors.border, OrcaTheme.shapes.control)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

/** A field of millimetres, or of inches with the Preferences' "use_inches", as rev_input_mm() takes it. */
@Composable
internal fun androidx.compose.foundation.layout.RowScope.LengthField(millimetres: Double, imperial: Boolean, enabled: Boolean, onValue: (Double) -> Unit) {
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
internal fun TextRow(
    label: String,
    labelColor: Color = OrcaTheme.colors.onCanvasPanel,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier
        .fillMaxWidth()
        .padding(top = 6.dp)) {
        Text(label, color = labelColor, style = OrcaTheme.typography.body13, modifier = Modifier.width(LabelWidth))
        content()
    }
}

@Composable
internal fun TextCheck(
    text: String,
    checked: Boolean,
    enabled: Boolean,
    labelColor: Color = OrcaTheme.colors.onCanvasPanel,
    /** rev_checkbox()'s revert to the stored style, while the value differs from it. */
    revert: (() -> Unit)? = null,
    revertDescription: String = "",
    onChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .toggleable(value = checked, role = Role.Checkbox, enabled = enabled, onValueChange = onChange),
        ) {
            app.orcinus.shadow.core.designsystem.component.OrcaCheckBox(checked = checked, onCheckedChange = null, enabled = enabled)
            Text(
                text = text,
                color = if (enabled) labelColor else OrcaTheme.colors.textDimmed,
                style = OrcaTheme.typography.body12,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
        if (revert != null) RevertButton(revertDescription, enabled = enabled, onClick = revert)
    }
}

/** revertible()'s undo icon: back to the stored style's value. */
@Composable
private fun RevertButton(description: String, enabled: Boolean = true, onClick: () -> Unit) {
    OrcaIconButton(icon = DesignR.drawable.orca_undo, contentDescription = description, onClick = onClick, enabled = enabled)
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
    /** A stored style to revert to; without one the label shows the value modified (revertible()). */
    hasRevert: Boolean = true,
) {
    val shown = (value ?: 0f).coerceIn(range)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            color = when {
                !enabled -> OrcaTheme.colors.textDimmed
                !hasRevert || value != revert -> OrcaTheme.colors.labelModified
                else -> OrcaTheme.colors.onCanvasPanel
            },
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
        if (hasRevert && value != revert) {
            OrcaIconButton(
                icon = DesignR.drawable.orca_undo,
                contentDescription = orcaString("Reset"),
                onClick = { onChange(revert) },
                enabled = enabled,
            )
        }
    }
}

/**
 * draw_model_type() of the text and SVG tools: Join, Cut or, but in simple
 * mode ([modifiersOffered] off), Modifier, the volume's [type] chosen.
 */
@Composable
internal fun EmbossOperation(type: VolumeType?, enabled: Boolean, modifiersOffered: Boolean, onType: (VolumeType) -> Unit) {
    val colors = OrcaTheme.colors
    Text(orcaString("Operation"), color = colors.onCanvasPanel, style = OrcaTheme.typography.body13, modifier = Modifier.padding(top = 8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOfNotNull(
            VolumeType.PART to orcaString("Join"),
            VolumeType.NEGATIVE to orcaString("Cut", "EmbossOperation"),
            (VolumeType.MODIFIER to orcaString("Modifier")).takeIf { modifiersOffered },
        ).forEach { (item, label) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.selectable(selected = type == item, role = Role.RadioButton, enabled = enabled, onClick = { onType(item) }),
            ) {
                OrcaRadioButton(selected = type == item, onClick = null)
                Text(label, color = colors.onCanvasPanel, style = OrcaTheme.typography.body12)
            }
        }
    }
}

@Composable
internal fun Warning(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Icon(painterResource(DesignR.drawable.orca_obj_warning), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(16.dp))
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

/**
 * The face of a font file as the text input draws the text with it
 * (StyleManager::create_imgui_font()): its line height in ems, (ascent -
 * descent + line gap) / units per em, and the file's first face without the
 * system's fallback fonts, which tells the glyphs the font has
 * (create_range_text()).
 */
private class InputFont(val lineHeightPerEm: Double, private val glyphs: Paint) {
    /** create_range_text()'s exist_unknown: a character but a new line, a return or a tab the font has no glyph for. */
    fun lacksGlyph(text: String): Boolean {
        var offset = 0
        while (offset < text.length) {
            val code = text.codePointAt(offset)
            offset += Character.charCount(code)
            if (code == '\n'.code || code == '\r'.code || code == '\t'.code) continue
            if (!glyphs.hasGlyph(String(Character.toChars(code)))) return true
        }
        return false
    }
}

/** The face [index] of the font file at [path] for the text input; null when it does not load. */
private fun inputFontOf(path: String, index: Int): InputFont? = runCatching {
    val metrics = Paint().apply {
        typeface = faceWithoutFallback(path, index)
        textSize = PROBE_TEXT_SIZE
    }.fontMetrics
    InputFont((metrics.descent - metrics.ascent + metrics.leading).toDouble() / PROBE_TEXT_SIZE, Paint().apply { typeface = faceWithoutFallback(path, 0) })
}.getOrNull()

/** A typeface of the face alone, which falls back to no other font. */
private fun faceWithoutFallback(path: String, index: Int): Typeface {
    val font = android.graphics.fonts.Font.Builder(File(path)).setTtcIndex(index).build()
    return Typeface.CustomFallbackBuilder(android.graphics.fonts.FontFamily.Builder(font).build()).build()
}

/**
 * draw_text_input()'s warning, its lines under each other: the font that
 * cannot write the text ([known] faces only), the empty text, the glyphs the
 * font lacks, what the input does not show of the style, and the input font
 * of [size] beyond its limits.
 */
@Composable
private fun inputWarning(mode: TextMode, known: Boolean, font: InputFont?, size: Double?): String? {
    val style = mode.style
    val warnings = ArrayList<String>()
    if (known && font == null) {
        warnings += orcaString("The text cannot be written using the selected font. Please try choosing a different font.")
    } else {
        if (mode.blank) warnings += orcaString("Embossed text cannot contain only white spaces.")
        if (font?.lacksGlyph(mode.text) == true) warnings += orcaString("Text contains character glyph (represented by '?') unknown by font.")
        if (style.skew != null) warnings += orcaString("Text input doesn't show font skew.")
        if (style.boldness != null) warnings += orcaString("Text input doesn't show font boldness.")
        if (style.lineGap != null) warnings += orcaString("Text input doesn't show gap between lines.")
        if (size != null && size > MAX_INPUT_FONT_SIZE) warnings += orcaString("Too tall, diminished font height inside text input.")
        if (size != null && size < MIN_INPUT_FONT_SIZE) warnings += orcaString("Too small, enlarged font height inside text input.")
        // m_text_lines, which the text holds while it is transformed per glyph.
        val multiline = style.perGlyph && mode.text.contains('\n')
        if (multiline && (style.horizontalAlign == TextHorizontalAlign.CENTER || style.horizontalAlign == TextHorizontalAlign.RIGHT)) {
            warnings += orcaString("Text doesn't show current horizontal alignment.")
        }
    }
    return warnings.takeIf { it.isNotEmpty() }?.joinToString("\n")
}

/**
 * StyleManager::is_font_changed(): another face name than the stored style's,
 * or italic or bold where it is not, or the other way round.
 */
private fun fontChanged(style: TextStyle, stored: TextStyle, families: List<TextFontFamily>): Boolean {
    fun faceOf(of: TextStyle) = families.flatMap(TextFontFamily::faces).firstOrNull { it.path == of.fontPath && it.index == (of.collectionNumber ?: 0) }
    fun familyOf(of: TextStyle) = families.firstOrNull { family -> family.faces.any { it.path == of.fontPath } }?.name ?: of.faceName
    if (familyOf(style) != familyOf(stored)) return true
    val face = faceOf(style)
    val storedFace = faceOf(stored)
    if ((style.skew != null || face?.italic == true) != (stored.skew != null || storedFace?.italic == true)) return true
    return (style.boldness != null || (face?.weight ?: 400) > 400) != (stored.boldness != null || (storedFace?.weight ?: 400) > 400)
}

/** StyleManager::min_imgui_font_size and max_imgui_font_size, the input's font limits in desktop pixels. */
private const val MIN_INPUT_FONT_SIZE = 18.0
private const val MAX_INPUT_FONT_SIZE = 60.0

/** get_imgui_font_size(): a point, 1/72 of an inch, in millimetres. */
private const val POINT_MM = 0.3528

/** The text size the font's metrics are read at. */
private const val PROBE_TEXT_SIZE = 1000f

/** The input's lines, apart by the font's own height. */
private const val INPUT_LINE_HEIGHT = 1.2f

/** create_gui_configuration(): the input holds three lines of the smallest input font, and scrolls. */
private val TEXT_INPUT_HEIGHT = 72.dp

/** is_approx()'s EPSILON, which the reverts of floats compare with. */
private const val REVERT_EPSILON = 1e-4

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

/** limits.angle of GLGizmoEmboss.cpp, in degrees. */
private const val ANGLE_MIN = -180f
private const val ANGLE_MAX = 180f

/** An ascent for the sliders' ranges while the font is not known. */
private const val DEFAULT_ASCENT = 1000

/** GizmoObjectManipulation::mm_to_in */
internal const val MM_TO_IN = 0.0393700787

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

/** A row of the style list, which a dragged style passes over half of to change places. */
private val StyleRowHeight = 48.dp
private val ValueWidth = 72.dp
