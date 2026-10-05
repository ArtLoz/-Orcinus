package app.orcinus.shadow.domain.plate

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ZipDirectoryTest {
    private val picture = ByteArray(300) { (it * 7).toByte() }
    private val relationships = """
        <?xml version="1.0" encoding="UTF-8"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
         <Relationship Target="/3D/3dmodel.model" Id="rel-1" Type="http://schemas.microsoft.com/3dmanufacturing/2013/01/3dmodel"/>
         <Relationship Target="/Metadata/plate_1.png" Id="rel-2" Type="http://schemas.openxmlformats.org/package/2006/relationships/metadata/thumbnail"/>
         <Relationship Target="/Metadata/cover.png" Id="rel-4" Type="http://schemas.bambulab.com/package/2021/cover-thumbnail-middle"/>
        </Relationships>
    """.trimIndent().toByteArray()

    // As OrcaSlicer's 3MF writer lays out the package: the pictures stored, the
    // texts deflated, each entry followed by its data descriptor.
    private val archive = minizArchive(
        listOf(
            Triple("[Content_Types].xml", "<Types/>".toByteArray(), true),
            Triple("Metadata/plate_1.png", ByteArray(40) { 1 }, false),
            Triple("Metadata/cover.png", picture, false),
            Triple("_rels/.rels", relationships, true),
        ),
    )

    @Test
    fun `ZipInputStream cannot read a stored entry with a data descriptor, which the central directory gives`() {
        assertFailsWith<ZipException> {
            ZipInputStream(ByteArrayInputStream(archive)).use { zip -> generateSequence { zip.nextEntry }.forEach { _ -> zip.readBytes() } }
        }
        val directory = ZipDirectory.of(ByteArrayInputStream(archive))!!
        assertContentEquals(picture, directory.entry("Metadata/cover.png"))
        assertContentEquals(relationships, directory.entry("_rels/.rels"))
        assertNull(directory.entry("3D/3dmodel.model"))
    }

    @Test
    fun `the home page takes the cover the relationships name, from a file read where it lies`() {
        assertContentEquals(picture, ThreeMfThumbnail.read { ByteArrayInputStream(archive) })
        val file = File.createTempFile("recent", ".3mf")
        try {
            file.writeBytes(archive)
            assertContentEquals(picture, ThreeMfThumbnail.read { file.inputStream() })
        } finally {
            file.delete()
        }
    }

    private fun minizArchive(entries: List<Triple<String, ByteArray, Boolean>>): ByteArray {
        val out = ByteArrayOutputStream()
        val central = ByteArrayOutputStream()
        for ((name, data, deflate) in entries) {
            val offset = out.size()
            val crc = CRC32().apply { update(data) }.value.toInt()
            val stored = if (deflate) deflated(data) else data
            val method = if (deflate) 8 else 0
            val nameBytes = name.toByteArray()
            out.write(le(0x04034b50, 4) + le(20, 2) + le(0x0808, 2) + le(method, 2) + le(0, 4) + le(0, 4) + le(0, 4) + le(0, 4) + le(nameBytes.size, 2) + le(0, 2))
            out.write(nameBytes)
            out.write(stored)
            out.write(le(0x08074b50, 4) + le(crc, 4) + le(stored.size, 4) + le(data.size, 4))
            central.write(
                le(0x02014b50, 4) + le(20, 2) + le(20, 2) + le(0x0808, 2) + le(method, 2) + le(0, 4) + le(crc, 4) + le(stored.size, 4) +
                    le(data.size, 4) + le(nameBytes.size, 2) + le(0, 2) + le(0, 2) + le(0, 2) + le(0, 2) + le(0, 4) + le(offset, 4),
            )
            central.write(nameBytes)
        }
        val directoryOffset = out.size()
        out.write(central.toByteArray())
        out.write(le(0x06054b50, 4) + le(0, 2) + le(0, 2) + le(entries.size, 2) + le(entries.size, 2) + le(central.size(), 4) + le(directoryOffset, 4) + le(0, 2))
        return out.toByteArray()
    }

    private fun deflated(data: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.DEFAULT_COMPRESSION, true)
        deflater.setInput(data)
        deflater.finish()
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer))
        deflater.end()
        return out.toByteArray()
    }

    private fun le(value: Int, bytes: Int): ByteArray =
        ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array().copyOf(bytes)
}
