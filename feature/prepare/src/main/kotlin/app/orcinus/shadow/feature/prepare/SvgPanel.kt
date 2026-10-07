package app.orcinus.shadow.feature.prepare

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.orcinus.shadow.core.designsystem.component.OrcaButton
import app.orcinus.shadow.core.designsystem.component.OrcaButtonSize
import app.orcinus.shadow.core.designsystem.component.OrcaButtonStyle
import app.orcinus.shadow.core.designsystem.component.OrcaContextMenu
import app.orcinus.shadow.core.designsystem.component.OrcaIconButton
import app.orcinus.shadow.core.designsystem.component.OrcaMenuItem
import app.orcinus.shadow.core.designsystem.component.textLocale
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import app.orcinus.shadow.core.model.Axis
import app.orcinus.shadow.core.model.EmbossRequest
import app.orcinus.shadow.core.model.ObjectPartId
import app.orcinus.shadow.core.model.OrcaText
import app.orcinus.shadow.core.model.Point2
import app.orcinus.shadow.core.model.SvgPreview
import app.orcinus.shadow.core.model.SvgWarning
import app.orcinus.shadow.core.model.Transform3
import app.orcinus.shadow.core.model.VolumeType
import app.orcinus.shadow.core.ui.orca.LocalOrcaCatalog
import app.orcinus.shadow.core.ui.orca.orcaString
import app.orcinus.shadow.render.scene.CameraEye
import app.orcinus.shadow.render.scene.SurfaceHit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import app.orcinus.shadow.core.designsystem.R as DesignR

/** What the SVG tool's window does (GLGizmoSVG). */
internal class SvgActions(
    /** "SVG" of the menus: over the copy [copy] where [hit] says, or as an object at [bedPoint], once a file is picked. */
    val choose: (copy: Int?, type: VolumeType, hit: SurfaceHit?, bedPoint: Point2?) -> Unit,
    /** The object list's "Add part" > "SVG", which the canvas places. */
    val chooseRequested: (EmbossRequest.Add, SurfaceHit?) -> Unit,
    /** choose_svg_file(): the picker of SVG files. */
    val pickFile: () -> Unit,
    /** "Change file": the picker, for the open SVG. */
    val changeFile: () -> Unit,
    /** "Edit SVG" of the menus. */
    val edit: (ObjectPartId) -> Unit,
    val close: () -> Unit,
    val setDepth: (Double) -> Unit,
    val setUseSurface: (Boolean) -> Unit,
    /** The size, in millimetres: a width, or a height with the ratio unlocked. */
    val setSize: (width: Double?, height: Double?) -> Unit,
    val resetSize: () -> Unit,
    val setKeepRatio: (Boolean) -> Unit,
    /** From surface let go, in millimetres; null for none. */
    val move: (Double?) -> Unit,
    /** Rotation let go, in degrees clockwise. */
    val rotate: (Double) -> Unit,
    val setKeepUp: (Boolean) -> Unit,
    val mirror: (Axis) -> Unit,
    val faceCamera: (CameraEye?) -> Unit,
    val setType: (VolumeType) -> Unit,
    /** The file menu: reload, forget the path, bake, and the picker of "Save as". */
    val reload: () -> Unit,
    val forgetPath: () -> Unit,
    val bake: () -> Unit,
    val saveAs: () -> Unit,
    /** The SVG where a finger left it on its object (SurfaceDrag). */
    val drag: (Transform3) -> Unit,
    /** The SVG let go on its rotation ring, turned by the angle (radians) about its own Z axis. */
    val turn: (Double) -> Unit,
) {
    companion object {
        val NONE = SvgActions(
            { _, _, _, _ -> }, { _, _ -> }, {}, {}, {}, {}, {}, {}, { _, _ -> }, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {},
        )
    }
}

/**
 * GLGizmoSVG::draw_window(): the picture of the SVG, its file with the warnings
 * about its shapes and the file menu, its depth and size, the surface, the
 * distance from it, the rotation, the mirrors, facing the camera, and the
 * operation of an SVG that is not its object's only part. The desktop app
 * moves an SVG as a slider moves; here a slider's value applies once the
 * finger lets go.
 */
@Composable
internal fun SvgPanel(mode: SvgMode, actions: SvgActions, imperial: Boolean, eye: () -> CameraEye?, modifiersOffered: Boolean = true) {
    val colors = OrcaTheme.colors
    val locale = textLocale()
    val described = mode.described
    val enabled = described != null && !mode.busy
    val unit = if (imperial) MM_TO_IN else 1.0
    var warningsShown by remember { mutableStateOf(false) }
    PaintingPanelFrame(orcaString("SVG"), orcaString("Done"), actions.close) {
        mode.preview?.let { SvgPicture(it, mode.previewVersion) }
        SvgFileRow(mode, enabled, actions, onWarnings = { warningsShown = true })
        // draw_depth(): the depth in the world, in inches with "use_inches".
        val depthScale = described?.scaleDepth ?: 1.0
        TextRow(orcaString("Depth")) {
            LengthField(mode.depth * depthScale, imperial, enabled = enabled) { value -> actions.setDepth(value / depthScale) }
        }
        // draw_size(): the width with the ratio locked, or the width and the height.
        val width = described?.width ?: 0.0
        val height = described?.height ?: 0.0
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                if (mode.keepRatio) {
                    CommittedSlider(
                        label = orcaString("Size"),
                        value = (width * unit).toFloat(),
                        range = if (imperial) UI_SIZE_IN else UI_SIZE_MM,
                        text = { shown ->
                            val ratio = if (width > 0.0) shown / (width * unit) else 1.0
                            String.format(locale, "%.2f x %.2f %s", shown, height * unit * ratio, if (imperial) "in" else "mm")
                        },
                        revert = null,
                        hasRevert = false,
                        enabled = enabled,
                        modified = mode.scaled,
                        onCommit = { value -> value?.let { actions.setSize(it / unit, null) } },
                    )
                } else {
                    TextRow(orcaString("Size"), labelColor = if (mode.scaled) colors.labelModified else colors.onCanvasPanel) {
                        LengthField(width, imperial, enabled = enabled) { value -> actions.setSize(value, null) }
                        LengthField(height, imperial, enabled = enabled) { value -> actions.setSize(null, value) }
                    }
                }
            }
            // Lock on ratio m_keep_ratio
            OrcaIconButton(
                icon = if (mode.keepRatio) DesignR.drawable.orca_lock_closed else DesignR.drawable.orca_lock_open,
                contentDescription = orcaString("Lock/unlock the aspect ratio of the SVG."),
                onClick = { actions.setKeepRatio(!mode.keepRatio) },
                tint = Color.Unspecified,
            )
            if (mode.scaled) {
                OrcaIconButton(
                    icon = DesignR.drawable.orca_undo,
                    contentDescription = orcaString("Reset scale"),
                    onClick = actions.resetSize,
                    enabled = enabled,
                )
            }
        }
        // draw_use_surface(): an SVG that is its object has no surface, but one that uses it can stop.
        TextCheck(orcaString("Use surface"), mode.useSurface, enabled = enabled && (mode.useSurface || !mode.onlyPart)) { use ->
            actions.setUseSurface(use)
        }
        // draw_distance()
        val maxDistance = (2 * mode.depth * unit).toFloat()
        CommittedSlider(
            label = orcaString("From surface"),
            value = mode.distance?.let { (it * unit).toFloat() },
            range = -maxDistance..maxDistance,
            text = { String.format(locale, if (imperial) "%.3f in" else "%.2f mm", it) },
            revert = null,
            hasRevert = mode.distance != null,
            enabled = enabled && !mode.useSurface && !mode.onlyPart,
            modified = mode.distance != null,
            onCommit = { value -> actions.move(value?.let { it / unit }) },
        )
        // draw_rotation(): clockwise degrees from the counterclockwise angle.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                CommittedSlider(
                    label = orcaString("Rotation"),
                    value = Math.toDegrees(-(mode.angle ?: 0.0)).toFloat(),
                    range = -180f..180f,
                    text = { String.format(locale, "%.2f °", it) },
                    revert = 0f,
                    hasRevert = mode.angle != null,
                    enabled = enabled,
                    modified = mode.angle != null,
                    onCommit = { value -> actions.rotate((value ?: 0f).toDouble()) },
                )
            }
            // Keep up - lock button icon
            if (!mode.onlyPart) {
                OrcaIconButton(
                    icon = if (mode.keepUp) DesignR.drawable.orca_lock_closed else DesignR.drawable.orca_lock_open,
                    contentDescription = orcaString("Lock/unlock rotation angle when dragging above the surface."),
                    onClick = { actions.setKeepUp(!mode.keepUp) },
                    tint = Color.Unspecified,
                )
            }
        }
        // draw_mirroring()
        TextRow(orcaString("Mirror")) {
            OrcaIconButton(
                icon = DesignR.drawable.orca_reflection_x,
                contentDescription = orcaString("Mirror vertically"),
                onClick = { actions.mirror(Axis.X) },
                enabled = enabled,
                tint = Color.Unspecified,
            )
            OrcaIconButton(
                icon = DesignR.drawable.orca_reflection_y,
                contentDescription = orcaString("Mirror horizontally"),
                onClick = { actions.mirror(Axis.Y) },
                enabled = enabled,
                tint = Color.Unspecified,
            )
        }
        // draw_face_the_camera()
        OrcaButton(
            text = orcaString("Face the camera"),
            size = OrcaButtonSize.Compact,
            style = OrcaButtonStyle.Regular,
            enabled = enabled,
            onClick = { actions.faceCamera(eye()) },
            modifier = Modifier.padding(top = 8.dp),
        )
        if (!mode.onlyPart) {
            EmbossOperation(described?.type, enabled = enabled, modifiersOffered = modifiersOffered, onType = actions.setType)
        }
    }
    if (warningsShown) {
        SvgWarningsDialog(mode.preview?.warnings.orEmpty(), onDismiss = { warningsShown = false })
    }
}

/** draw_preview(): the picture of the SVG's shape, centred. */
@Composable
private fun SvgPicture(preview: SvgPreview, version: Int) {
    val picture by produceState<ImageBitmap?>(null, preview.picture, version) {
        value = withContext(Dispatchers.IO) { runCatching { BitmapFactory.decodeFile(preview.picture.value)?.asImageBitmap() }.getOrNull() }
    }
    val image = picture ?: return
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Image(
            bitmap = image,
            contentDescription = null,
            modifier = Modifier
                .padding(vertical = 4.dp)
                .heightIn(max = PICTURE_HEIGHT),
        )
    }
}

/**
 * draw_filename(): the warnings' sign, the file's name with ".svg" in grey,
 * its reload while it has a path, and the file menu.
 */
@Composable
private fun SvgFileRow(mode: SvgMode, enabled: Boolean, actions: SvgActions, onWarnings: () -> Unit) {
    val colors = OrcaTheme.colors
    val preview = mode.preview
    // TRN - Preview of filename after clear local filepath.
    val name = mode.described?.svgName?.takeIf(String::isNotEmpty) ?: orcaString("Unknown filename")
    var menuOpen by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        if (!preview?.warnings.isNullOrEmpty()) {
            OrcaIconButton(
                icon = DesignR.drawable.orca_obj_warning,
                contentDescription = orcaString("Warning"),
                onClick = onWarnings,
                tint = Color.Unspecified,
            )
        }
        Text(name, color = colors.onCanvasPanel, style = OrcaTheme.typography.body13, maxLines = 1)
        Text(".svg", color = colors.textDimmed, style = OrcaTheme.typography.body13, modifier = Modifier.weight(1f))
        // Re-Load button
        if (!preview?.svgPath.isNullOrEmpty()) {
            OrcaIconButton(
                icon = DesignR.drawable.orca_refresh,
                contentDescription = orcaString("Reload SVG file from disk."),
                onClick = actions.reload,
                enabled = enabled,
                tint = Color.Unspecified,
            )
        }
        Box {
            OrcaIconButton(
                icon = DesignR.drawable.orca_drop_down,
                contentDescription = orcaString("File"),
                onClick = { menuOpen = true },
                enabled = enabled,
            )
            if (menuOpen) {
                OrcaContextMenu(expanded = true, position = IntOffset.Zero, onDismissRequest = { menuOpen = false }) {
                    OrcaMenuItem(text = orcaString("Change file") + "...", onClick = {
                        menuOpen = false
                        actions.changeFile()
                    })
                    OrcaMenuItem(text = orcaString("Forget the file path"), enabled = !preview?.svgPath.isNullOrEmpty(), onClick = {
                        menuOpen = false
                        actions.forgetPath()
                    })
                    // TRN: An menu option to convert the SVG into an unmodifiable model part.
                    OrcaMenuItem(text = orcaString("Bake"), onClick = {
                        menuOpen = false
                        actions.bake()
                    })
                    OrcaMenuItem(text = orcaString("Save as") + "...", onClick = {
                        menuOpen = false
                        actions.saveAs()
                    })
                }
            }
        }
    }
}

/** The warnings' tooltip of draw_filename(), as a box a finger opens. */
@Composable
private fun SvgWarningsDialog(warnings: List<SvgWarning>, onDismiss: () -> Unit) {
    val colors = OrcaTheme.colors
    val catalog = LocalOrcaCatalog.current
    val lines = warnings.map { warning ->
        if (warning.unsupported.isEmpty()) {
            catalog.format(warning.text)
        } else {
            val unsupported = warning.unsupported.joinToString(", ") { catalog.format(it) }
            catalog.format(OrcaText(warning.text.msgid, warning.text.args + unsupported, context = warning.text.context))
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { OrcaButton(orcaString("OK"), onClick = onDismiss) },
        icon = { Icon(painterResource(DesignR.drawable.orca_obj_warning), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(24.dp)) },
        text = {
            Column {
                lines.forEach { line -> Text(line, style = OrcaTheme.typography.body14, modifier = Modifier.padding(vertical = 2.dp)) }
            }
        },
        containerColor = colors.window,
        textContentColor = colors.text,
        shape = OrcaTheme.shapes.window,
    )
}

/** limits.ui_size and ui_size_in of GLGizmoSVG.cpp: the size slider's range. */
private val UI_SIZE_MM = 5f..100f
private val UI_SIZE_IN = .1f..4f

/** The picture's height on the window, which its width follows. */
private val PICTURE_HEIGHT = 140.dp
