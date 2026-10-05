package app.orcinus.shadow.core.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import app.orcinus.shadow.core.designsystem.component.OrcaButton
import app.orcinus.shadow.core.designsystem.component.OrcaButtonStyle
import app.orcinus.shadow.core.designsystem.component.OrcaSegmentedSwitch
import app.orcinus.shadow.core.designsystem.component.OrcaTextField
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import app.orcinus.shadow.core.model.BedFileOutcome
import app.orcinus.shadow.core.model.BedPreview
import app.orcinus.shadow.core.model.BedShape
import app.orcinus.shadow.core.model.BedShapeKind
import app.orcinus.shadow.core.model.BedShapeOutcome
import app.orcinus.shadow.core.model.ExternalDocumentReference
import app.orcinus.shadow.core.model.Point2
import app.orcinus.shadow.core.ui.orca.orcaString
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.launch

/** What BedShapeDialog reads and writes besides the shape: its files and its drawing. */
class BedShapeFileActions(
    /** BedShapePanel::load_stl(): the shape of the STL document, or Orca's message. */
    val loadShape: suspend (ExternalDocumentReference) -> BedShapeOutcome,
    /** load_texture() (true) and load_model(): the document kept for the preset, or Orca's message. */
    val keep: suspend (ExternalDocumentReference, Boolean) -> BedFileOutcome,
    /** Bed_2D::repaint()'s grid over the shape. */
    val preview: suspend (List<Point2>) -> BedPreview,
    /** Whether a texture or model file is still there. */
    val exists: (String) -> Boolean,
) {
    companion object {
        val NONE = BedShapeFileActions(
            loadShape = { BedShapeOutcome.Failure("") },
            keep = { _, _ -> BedFileOutcome.Failure("") },
            preview = { BedPreview() },
            exists = { true },
        )
    }
}

/**
 * BedShapeDialog (BedShapePanel): the "Shape" with its pages — the size and
 * origin of a rectangle, the diameter of a circle, or a custom shape loaded
 * from an STL file — the "Texture" and the "Model" of the bed, each with its
 * file's name ("None" without one, red when the file is missing), "Load..."
 * and "Remove", and the drawing of the shape (Bed_2D) with its grid, axes and
 * origin. OK writes the shape, texture and model.
 */
@Composable
fun BedShapeSheet(
    load: suspend () -> BedShapeOutcome,
    files: BedShapeFileActions,
    onApply: (BedShape) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = OrcaTheme.colors
    val scope = rememberCoroutineScope()
    var shape by remember { mutableStateOf<BedShape?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var kind by remember { mutableStateOf(BedShapeKind.RECTANGLE) }
    var sizeX by remember { mutableStateOf("") }
    var sizeY by remember { mutableStateOf("") }
    var originX by remember { mutableStateOf("") }
    var originY by remember { mutableStateOf("") }
    var diameter by remember { mutableStateOf("") }
    // m_loaded_shape, m_custom_texture and m_custom_model ("" for NONE).
    var loadedShape by remember { mutableStateOf<List<Point2>>(emptyList()) }
    var texture by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        when (val outcome = load()) {
            is BedShapeOutcome.Success -> {
                shape = outcome.shape
                kind = outcome.shape.kind
                sizeX = number(outcome.shape.sizeX)
                sizeY = number(outcome.shape.sizeY)
                originX = number(outcome.shape.originX)
                originY = number(outcome.shape.originY)
                diameter = number(outcome.shape.diameter.takeIf { it > 0.0 } ?: DEFAULT_DIAMETER)
                // set_shape(): a custom shape's points are the loaded shape.
                if (outcome.shape.kind == BedShapeKind.CUSTOM) loadedShape = outcome.shape.points
                texture = outcome.shape.texture
                model = outcome.shape.model
            }
            is BedShapeOutcome.Failure -> problem = outcome.message
        }
    }
    val shapePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            when (val loaded = files.loadShape(ExternalDocumentReference(uri.toString()))) {
                is BedShapeOutcome.Success -> loadedShape = loaded.shape.points
                is BedShapeOutcome.Failure -> error = loaded.message
            }
        }
    }
    val texturePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        texture = ""
        scope.launch {
            when (val kept = files.keep(ExternalDocumentReference(uri.toString()), true)) {
                is BedFileOutcome.Kept -> texture = kept.path
                is BedFileOutcome.Failure -> error = kept.message
                // BedShapeDialog takes a file of any size.
                is BedFileOutcome.TooLarge -> Unit
            }
        }
    }
    val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        model = ""
        scope.launch {
            when (val kept = files.keep(ExternalDocumentReference(uri.toString()), false)) {
                is BedFileOutcome.Kept -> model = kept.path
                is BedFileOutcome.Failure -> error = kept.message
                // BedShapeDialog takes a file of any size.
                is BedFileOutcome.TooLarge -> Unit
            }
        }
    }
    // update_shape(): the shape of the page, or the last one while its fields give none.
    var drawn by remember { mutableStateOf<List<Point2>>(emptyList()) }
    val points = shapeOf(kind, sizeX, sizeY, originX, originY, diameter, loadedShape)
    if (points != null && points != drawn) drawn = points
    var preview by remember { mutableStateOf(BedPreview()) }
    LaunchedEffect(drawn) { preview = if (drawn.size >= 3) files.preview(drawn) else BedPreview() }
    val current = shape
    val apply = {
        current?.let {
            onApply(
                it.copy(
                    kind = kind,
                    sizeX = sizeX.toDoubleOrNull() ?: 0.0,
                    sizeY = sizeY.toDoubleOrNull() ?: 0.0,
                    originX = originX.toDoubleOrNull() ?: 0.0,
                    originY = originY.toDoubleOrNull() ?: 0.0,
                    diameter = diameter.toDoubleOrNull() ?: 0.0,
                    texture = texture,
                    model = model,
                    points = loadedShape,
                ),
            )
        } ?: onDismiss()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        // The three shapes side by side need more than a phone dialog's usual width.
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.padding(horizontal = 16.dp),
        confirmButton = { OrcaButton(orcaString("OK"), onClick = apply, enabled = current != null) },
        dismissButton = { OrcaButton(orcaString("Cancel"), onClick = onDismiss, style = OrcaButtonStyle.Regular) },
        title = { Text(orcaString("Bed Shape"), style = OrcaTheme.typography.head16) },
        text = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                problem?.let { Text(it, color = colors.error, style = OrcaTheme.typography.body13) }
                if (current == null && problem == null) {
                    CircularProgressIndicator(color = colors.accent)
                    return@Column
                }
                // The right pane of the desktop dialog, Bed_2D.
                BedPreviewCanvas(drawn, preview)
                // BedShapePanel::build_panel(): "Shape" with its three pages.
                Text(orcaString("Shape"), color = colors.textLabel, style = OrcaTheme.typography.body13, modifier = Modifier.padding(top = 8.dp))
                OrcaSegmentedSwitch(
                    options = listOf(orcaString("Rectangular"), orcaString("Circular"), orcaString("Custom")),
                    selectedIndex = kind.ordinal,
                    onSelect = { kind = BedShapeKind.entries[it] },
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                when (kind) {
                    BedShapeKind.RECTANGLE -> {
                        BedShapeRow(orcaString("Size"), sizeX, sizeY, { sizeX = it }, { sizeY = it })
                        BedShapeRow(orcaString("Origin"), originX, originY, { originX = it }, { originY = it })
                    }
                    BedShapeKind.CIRCLE -> BedShapeRow(orcaString("Diameter"), diameter, null, { diameter = it }, {})
                    BedShapeKind.CUSTOM -> OrcaButton(
                        text = orcaString("Load shape from STL..."),
                        onClick = { shapePicker.launch(arrayOf("*/*")) },
                        style = OrcaButtonStyle.Regular,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                    )
                }
                // init_texture_panel() and init_model_panel()
                BedFilePanel(orcaString("Texture"), texture, files.exists, onLoad = { texturePicker.launch(arrayOf("*/*")) }, onRemove = { texture = "" })
                BedFilePanel(orcaString("Model"), model, files.exists, onLoad = { modelPicker.launch(arrayOf("*/*")) }, onRemove = { model = "" })
            }
        },
        containerColor = colors.window,
        titleContentColor = colors.text,
        textContentColor = colors.text,
        shape = OrcaTheme.shapes.window,
    )
    // show_error()
    error?.let { message ->
        AlertDialog(
            onDismissRequest = { error = null },
            confirmButton = { OrcaButton(orcaString("OK"), onClick = { error = null }) },
            title = { Text(orcaString("Error"), style = OrcaTheme.typography.head16) },
            text = { Text(orcaString(message), style = OrcaTheme.typography.body14) },
            containerColor = colors.window,
            titleContentColor = colors.text,
            textContentColor = colors.text,
            shape = OrcaTheme.shapes.window,
        )
    }
}

/** A texture or model of the bed: its file's name, red and "Not found:" when it is missing, "Load..." and "Remove". */
@Composable
private fun BedFilePanel(title: String, path: String, exists: (String) -> Boolean, onLoad: () -> Unit, onRemove: () -> Unit) {
    val colors = OrcaTheme.colors
    val missing = path.isNotEmpty() && !exists(path)
    Text(title, color = colors.textLabel, style = OrcaTheme.typography.body13, modifier = Modifier.padding(top = 12.dp))
    Text(
        text = if (path.isEmpty()) orcaString(NONE) else (if (missing) orcaString("Not found:") + " " else "") + path.substringAfterLast('/'),
        color = if (missing) MISSING_COLOR else colors.text,
        style = OrcaTheme.typography.body14,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(vertical = 4.dp),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OrcaButton(orcaString("Load..."), onClick = onLoad, style = OrcaButtonStyle.Regular, modifier = Modifier.weight(1f))
        OrcaButton(orcaString("Remove"), onClick = onRemove, style = OrcaButtonStyle.Regular, enabled = path.isNotEmpty(), modifier = Modifier.weight(1f))
    }
}

/**
 * Bed_2D::repaint(): the shape filled in the bed's colour, its grid and
 * contour, the X and Y axes and the origin with "(0,0)", fitted with a
 * border, and "1x1 Grid: %d mm" under it.
 */
@Composable
private fun BedPreviewCanvas(shape: List<Point2>, preview: BedPreview) {
    val dark = OrcaTheme.colors.window.luminance() < 0.5f
    val measurer = rememberTextMeasurer()
    val gridLabel = orcaString("1x1 Grid: %d mm").replace("%d", preview.step.toString())
    val labelColor = OrcaTheme.colors.text
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(240.dp),
    ) {
        if (shape.size < 3) return@Canvas
        val border = 10.dp.toPx()
        val cw = size.width - 2 * border
        val ch = size.height - 2 * border
        // The shape's box with the origin in it.
        val minX = min(shape.minOf { it.x }, 0.0)
        val maxX = max(shape.maxOf { it.x }, 0.0)
        val minY = min(shape.minOf { it.y }, 0.0)
        val maxY = max(shape.maxOf { it.y }, 0.0)
        val factor = min(cw / (maxX - minX), ch / (maxY - minY))
        val shiftX = cw / 2 - (minX + maxX) / 2 * factor
        val shiftY = ch / 2 - (minY + maxY) / 2 * factor
        fun pixels(x: Double, y: Double) = Offset((x * factor + shiftX + border).toFloat(), (ch - (y * factor + shiftY) + border).toFloat())

        val bedBase = if (dark) BED_COLOR_DARK else BED_COLOR
        val bedColor = Color(bedBase.red * 0.8f, bedBase.green * 0.8f, bedBase.blue * 0.8f)
        val bold = if (dark) GRID_COLOR_DARK else GRID_COLOR
        val thin = Color(bold.red * 0.85f, bold.green * 0.85f, bold.blue * 0.85f)
        val contour = Path().apply {
            shape.forEachIndexed { index, point -> pixels(point.x, point.y).let { if (index == 0) moveTo(it.x, it.y) else lineTo(it.x, it.y) } }
            close()
        }
        drawPath(contour, bedColor)
        drawLines(preview.thin, thin, ::pixels)
        drawLines(preview.bold, bold, ::pixels)
        drawPath(contour, bold, style = Stroke(1.dp.toPx()))
        // The axes, five em long, and the origin.
        val origin = pixels(0.0, 0.0)
        val axis = 50.dp.toPx()
        drawLine(AXIS_X, origin, origin + Offset(axis, 0f), 2.dp.toPx())
        drawLine(AXIS_Y, origin, origin - Offset(0f, axis), 2.dp.toPx())
        drawCircle(AXIS_Z, 3.dp.toPx(), origin)
        val originLabel = measurer.measure("(0,0)", TextStyle(color = Color.White, fontSize = 10.sp))
        val labelTopLeft = Offset(origin.x + 2.dp.toPx(), origin.y - originLabel.size.height - 2.dp.toPx())
        drawRect(bedColor, labelTopLeft, Size(originLabel.size.width.toFloat(), originLabel.size.height.toFloat()))
        drawText(originLabel, topLeft = labelTopLeft)
        if (preview.step > 0) {
            val corner = pixels(min(0.0, minX), min(0.0, minY))
            drawText(measurer.measure(gridLabel, TextStyle(color = labelColor, fontSize = 10.sp)), topLeft = Offset(corner.x, corner.y + 5.dp.toPx()))
        }
    }
}

private fun DrawScope.drawLines(lines: List<List<Point2>>, color: Color, pixels: (Double, Double) -> Offset) {
    lines.forEach { line ->
        line.zipWithNext { a, b -> drawLine(color, pixels(a.x, a.y), pixels(b.x, b.y), 1.dp.toPx()) }
    }
}

/** BedShapePanel::update_shape(): the points of the page's fields; null while they give none. */
private fun shapeOf(kind: BedShapeKind, sizeX: String, sizeY: String, originX: String, originY: String, diameter: String, loaded: List<Point2>): List<Point2>? =
    when (kind) {
        BedShapeKind.RECTANGLE -> {
            val x = sizeX.toDoubleOrNull()
            val y = sizeY.toDoubleOrNull()
            val dx = originX.toDoubleOrNull()
            val dy = originY.toDoubleOrNull()
            if (x == null || y == null || dx == null || dy == null || x == 0.0 || y == 0.0) {
                null
            } else {
                listOf(Point2(-dx, -dy), Point2(x - dx, -dy), Point2(x - dx, y - dy), Point2(-dx, y - dy))
            }
        }
        BedShapeKind.CIRCLE -> diameter.toDoubleOrNull()?.takeIf { it != 0.0 }?.let { d ->
            // Don't change this value without adjusting BuildVolume constructor detecting circle diameter!
            (1..CIRCLE_EDGES).map { i ->
                val angle = i * 2 * Math.PI / CIRCLE_EDGES
                Point2(d / 2 * cos(angle), d / 2 * sin(angle))
            }
        }
        BedShapeKind.CUSTOM -> loaded
    }

/** One line of the bed shape dialog: a label and one or two millimetre fields. */
@Composable
private fun BedShapeRow(label: String, first: String, second: String?, onFirst: (String) -> Unit, onSecond: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, color = OrcaTheme.colors.text, style = OrcaTheme.typography.body14, modifier = Modifier.weight(1f))
        OrcaTextField(
            value = first,
            onValueChange = onFirst,
            unit = if (second == null) orcaString("mm") else "x",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
            modifier = Modifier.width(96.dp),
        )
        if (second != null) {
            OrcaTextField(
                value = second,
                onValueChange = onSecond,
                unit = orcaString("mm"),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                modifier = Modifier
                    .padding(start = 6.dp)
                    .width(96.dp),
            )
        }
    }
}

/** A length as the desktop dialog writes it: without a trailing ".0". */
private fun number(value: Double): String = if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

/** The 200 mm BedShape::append_option_line() starts a circular plate with. */
private const val DEFAULT_DIAMETER = 200.0

/** BedShapePanel::update_shape()'s edges of a circle. */
private const val CIRCLE_EDGES = 72

/** BedShapePanel::NONE */
private const val NONE = "None"

/** The colour of a missing file's name. */
private val MISSING_COLOR = Color(0xFFE14747)

/** Bed3D::DEFAULT_MODEL_COLOR and DEFAULT_MODEL_COLOR_DARK. */
private val BED_COLOR = Color(0.3255f, 0.337f, 0.337f)
private val BED_COLOR_DARK = Color(0.255f, 0.255f, 0.283f)

/** PartPlate::LINE_TOP_SEL_COLOR and LINE_TOP_SEL_DARK_COLOR. */
private val GRID_COLOR = Color(0.5294f, 0.5451f, 0.5333f)
private val GRID_COLOR_DARK = Color(0.298f, 0.298f, 0.3333f)

/** ColorRGB::X(), Y() and Z(). */
private val AXIS_X = Color(255, 60, 91)
private val AXIS_Y = Color(100, 200, 24)
private val AXIS_Z = Color(47, 136, 233)
