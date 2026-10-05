package app.orcinus.shadow.domain.plate

import java.io.FileInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.zip.Inflater

/**
 * The entries of a ZIP archive found through its central directory, as miniz's
 * mz_zip_reader reads a 3MF file. OrcaSlicer's 3MF files store their pictures
 * uncompressed with a data descriptor after them (general purpose bit 3), which
 * java.util.zip.ZipInputStream refuses to read; the central directory gives
 * every entry's size and place. An archive of a file is read where it lies; any
 * other stream is read into memory first.
 */
internal class ZipDirectory private constructor(private val source: Source, private val entries: Map<String, Entry>) {
    /** The bytes of the entry [name]; null when the archive has none or its data is unreadable. */
    fun entry(name: String): ByteArray? {
        val entry = entries[name] ?: return null
        val local = source.read(entry.localHeader, LOCAL_HEADER_SIZE) ?: return null
        if (local.int(0) != LOCAL_HEADER_SIGNATURE) return null
        val start = entry.localHeader + LOCAL_HEADER_SIZE + local.short(26) + local.short(28)
        val data = source.read(start, entry.compressedSize) ?: return null
        return when (entry.method) {
            STORED -> data
            DEFLATED -> inflate(data, entry.size)
            else -> null
        }
    }

    private class Entry(val method: Int, val compressedSize: Int, val size: Int, val localHeader: Long)

    /** Where the archive's bytes are read from. */
    private interface Source {
        val size: Long

        /** [length] bytes at [position]; null where the archive ends before them. */
        fun read(position: Long, length: Int): ByteArray?
    }

    private class ChannelSource(private val channel: FileChannel) : Source {
        override val size: Long = channel.size()

        override fun read(position: Long, length: Int): ByteArray? {
            if (position < 0 || length < 0 || position + length > size) return null
            val buffer = ByteBuffer.allocate(length)
            var at = position
            while (buffer.hasRemaining()) {
                val read = channel.read(buffer, at)
                if (read < 0) return null
                at += read
            }
            return buffer.array()
        }
    }

    private class BytesSource(private val bytes: ByteArray) : Source {
        override val size: Long = bytes.size.toLong()

        override fun read(position: Long, length: Int): ByteArray? {
            if (position < 0 || length < 0 || position + length > size) return null
            return bytes.copyOfRange(position.toInt(), position.toInt() + length)
        }
    }

    companion object {
        /** The archive [stream] holds, which stays open while its entries are read; null for none. */
        fun of(stream: InputStream): ZipDirectory? {
            val channel = (stream as? FileInputStream)?.channel?.takeIf { runCatching { it.size() > 0 }.getOrDefault(false) }
            val source = if (channel != null) ChannelSource(channel) else BytesSource(stream.readBytes())
            return read(source)
        }

        private fun read(source: Source): ZipDirectory? {
            // The end of central directory record, before a comment of up to 64 KiB.
            val tailLength = minOf(source.size, (END_RECORD_SIZE + MAX_COMMENT).toLong()).toInt()
            val tailStart = source.size - tailLength
            val tail = source.read(tailStart, tailLength) ?: return null
            val end = (tailLength - END_RECORD_SIZE downTo 0).firstOrNull { tail.int(it) == END_RECORD_SIGNATURE } ?: return null
            val directorySize = tail.uint(end + 12)
            val directoryOffset = tail.uint(end + 16)
            if (directorySize > Int.MAX_VALUE) return null
            val directory = source.read(directoryOffset, directorySize.toInt()) ?: return null
            val entries = HashMap<String, Entry>()
            var at = 0
            while (at + CENTRAL_HEADER_SIZE <= directory.size && directory.int(at) == CENTRAL_HEADER_SIGNATURE) {
                val nameLength = directory.short(at + 28)
                val extraLength = directory.short(at + 30)
                val commentLength = directory.short(at + 32)
                if (at + CENTRAL_HEADER_SIZE + nameLength > directory.size) break
                val name = String(directory, at + CENTRAL_HEADER_SIZE, nameLength, Charsets.UTF_8)
                val compressed = directory.uint(at + 20)
                val size = directory.uint(at + 24)
                // ZIP64 sizes stand in an extra field this reader does not follow.
                if (compressed <= Int.MAX_VALUE && size <= Int.MAX_VALUE) {
                    entries[name] = Entry(directory.short(at + 10), compressed.toInt(), size.toInt(), directory.uint(at + 42))
                }
                at += CENTRAL_HEADER_SIZE + nameLength + extraLength + commentLength
            }
            return ZipDirectory(source, entries)
        }

        private fun inflate(data: ByteArray, size: Int): ByteArray? {
            val inflater = Inflater(true)
            return try {
                inflater.setInput(data)
                val out = ByteArray(size)
                var filled = 0
                while (filled < size && !inflater.finished()) {
                    val count = inflater.inflate(out, filled, size - filled)
                    if (count == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
                    filled += count
                }
                out.takeIf { filled == size }
            } catch (_: java.util.zip.DataFormatException) {
                null
            } finally {
                inflater.end()
            }
        }

        private fun ByteArray.int(at: Int): Int = ByteBuffer.wrap(this, at, 4).order(ByteOrder.LITTLE_ENDIAN).int

        private fun ByteArray.uint(at: Int): Long = int(at).toLong() and 0xFFFFFFFFL

        private fun ByteArray.short(at: Int): Int = ByteBuffer.wrap(this, at, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xFFFF

        private const val STORED = 0
        private const val DEFLATED = 8
        private const val LOCAL_HEADER_SIGNATURE = 0x04034b50
        private const val CENTRAL_HEADER_SIGNATURE = 0x02014b50
        private const val END_RECORD_SIGNATURE = 0x06054b50
        private const val LOCAL_HEADER_SIZE = 30
        private const val CENTRAL_HEADER_SIZE = 46
        private const val END_RECORD_SIZE = 22
        private const val MAX_COMMENT = 0xFFFF
    }
}
