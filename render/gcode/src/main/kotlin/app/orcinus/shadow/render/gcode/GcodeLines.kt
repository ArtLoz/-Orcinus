package app.orcinus.shadow.render.gcode

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile

/**
 * The G-code the preview shows, as GCodeViewer::SequentialView::GCodeWindow
 * reads it: the file, with where each of its lines ends
 * (GCodeProcessorResult::lines_ends, the offset after every line break), so a
 * few lines are read at a time. Lines are numbered from 1, as the moves'
 * gcode_id number them.
 */
class GcodeLines private constructor(
    private val file: File,
    private val ends: LongArray,
) {
    /** How many lines the file has. */
    val count: Int get() = ends.size

    /** The lines [from] to [to], both included, each as the file holds it, with its line break. */
    fun read(from: Int, to: Int): List<String> {
        if (from < 1 || to > ends.size || from > to) return emptyList()
        val start = startOf(from)
        val bytes = ByteArray((ends[to - 1] - start).toInt())
        RandomAccessFile(file, "r").use { input ->
            input.seek(start)
            input.readFully(bytes)
        }
        return (from..to).map { id ->
            val offset = (startOf(id) - start).toInt()
            String(bytes, offset, (ends[id - 1] - startOf(id)).toInt(), Charsets.UTF_8)
        }
    }

    private fun startOf(id: Int): Long = if (id == 1) 0L else ends[id - 2]

    companion object {
        /** Reads where the lines of the G-code at [path] end; null when it cannot be read. */
        suspend fun open(path: String): GcodeLines? = withContext(Dispatchers.IO) {
            val file = File(path)
            runCatching {
                var ends = LongArray(INITIAL_LINES)
                var count = 0
                var position = 0L
                val buffer = ByteArray(BUFFER_SIZE)
                file.inputStream().use { input ->
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        for (index in 0 until read) {
                            if (buffer[index] == LINE_FEED) {
                                if (count == ends.size) ends = ends.copyOf(ends.size * 2)
                                ends[count++] = position + index + 1
                            }
                        }
                        position += read
                    }
                }
                GcodeLines(file, ends.copyOf(count))
            }.getOrNull()
        }

        private const val LINE_FEED = '\n'.code.toByte()
        private const val BUFFER_SIZE = 1 shl 16
        private const val INITIAL_LINES = 1 shl 12
    }
}
