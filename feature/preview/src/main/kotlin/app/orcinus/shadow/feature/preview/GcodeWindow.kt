package app.orcinus.shadow.feature.preview

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.orcinus.shadow.core.designsystem.theme.OrcaTheme
import app.orcinus.shadow.render.gcode.GcodeLines
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A line of the G-code window (GCodeWindow::Line), with its number. */
internal data class GcodeWindowLine(val id: Int, val command: String, val parameters: String, val comment: String) {
    companion object {
        /**
         * GCodeWindow::render()'s update_lines(): a line longer than 55
         * characters, its break included, keeps 52 and "..."; the comment is
         * what follows its last ';', the command the first word before it, the
         * parameters the other words, each after a space. The window shows a
         * line on one row, so its break is left out.
         */
        fun parse(id: Int, text: String): GcodeWindowLine {
            val line = if (text.length > MAX_LINE) text.substring(0, MAX_LINE - 3) + "..." else text
            val parts = boostSplit(line, ';')
            var command = parts.first()
            val comment = if (parts.size > 1) ";" + parts.last() else ""
            var parameters = ""
            if (command.isNotEmpty()) {
                val words = boostSplit(command, ' ')
                command = words.first()
                parameters = words.drop(1).joinToString("") { " $it" }
            }
            return GcodeWindowLine(id, command.trimBreak(), parameters.trimBreak(), comment.trimBreak())
        }

        /** boost::split() with token_compress_on: separators side by side make one. */
        private fun boostSplit(text: String, separator: Char): List<String> {
            val tokens = text.split(separator)
            return tokens.filterIndexed { index, token -> index == 0 || index == tokens.lastIndex || token.isNotEmpty() }
        }

        private fun String.trimBreak() = trimEnd('\n', '\r')

        private const val MAX_LINE = 55
    }
}

/**
 * GCodeWindow::render(): the [lines] lines around [current], half of them
 * before it, kept inside the [count] lines of the file.
 */
internal fun gcodeWindowRange(current: Int, lines: Int, count: Int): IntRange {
    val half = lines / 2
    var start = if (current >= half) current - half else 0
    var end = start + lines - 1
    if (end >= count) {
        end = count - 1
        start = end - lines + 1
    }
    // Lines are numbered from 1.
    return maxOf(start, 1)..end
}

/**
 * GCodeViewer::SequentialView::GCodeWindow: the lines of the G-code around
 * the current move, numbered, the current one framed, the command, its
 * parameters and the comment in their colours. The desktop window fills the
 * canvas's height on its right; here it holds [GcodeWindowLines] lines over
 * the move slider.
 */
@Composable
internal fun GcodeWindow(lines: GcodeLines, current: Int, modifier: Modifier = Modifier) {
    val range = gcodeWindowRange(current, GcodeWindowLines, lines.count)
    val shown by produceState(emptyList<GcodeWindowLine>(), lines, range) {
        value = withContext(Dispatchers.IO) {
            runCatching { lines.read(range.first, range.last).mapIndexed { index, text -> GcodeWindowLine.parse(range.first + index, text) } }
                .getOrDefault(emptyList())
        }
    }
    val colors = OrcaTheme.colors
    // The colours of GCodeWindow::render(), the command's darker on a light window.
    val command = if (colors.isDark) Color(0.8f, 0.8f, 0.0f) else Color(0.55f, 0.55f, 0.0f)
    val idWidth = range.last.toString().length
    Surface(
        modifier = modifier.height(GcodeWindowHeight),
        shape = RoundedCornerShape(8.dp),
        color = colors.window.copy(alpha = 0.8f),
    ) {
        Column(Modifier.padding(vertical = WindowPadding, horizontal = 8.dp)) {
            for (line in shown) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(LineHeight)
                        .let { if (line.id == current) it.border(1.dp, OrangeDark) else it },
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = OrangeLight)) { append(line.id.toString().padStart(idWidth)) }
                            if (line.command.isNotEmpty() || line.comment.isNotEmpty()) append(' ')
                            withStyle(SpanStyle(color = command)) { append(line.command) }
                            withStyle(SpanStyle(color = colors.text)) { append(line.parameters) }
                            withStyle(SpanStyle(color = colors.textDimmed)) { append(line.comment) }
                        },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 2.dp),
                    )
                }
            }
        }
    }
}

/** The lines the window holds: the current one in the middle. */
internal const val GcodeWindowLines = 5
private val LineHeight = 16.dp
private val WindowPadding = 6.dp

/** The window's height, which the layer slider keeps clear of. */
internal val GcodeWindowHeight = LineHeight * GcodeWindowLines + WindowPadding * 2

/** ImGuiWrapper::COL_ORANGE_LIGHT (ColorRGBA::ORANGE()) of the line numbers, COL_ORANGE_DARK of the current line's frame. */
private val OrangeLight = Color(0.923f, 0.504f, 0.264f)
private val OrangeDark = Color(0.757f, 0.404f, 0.216f)
