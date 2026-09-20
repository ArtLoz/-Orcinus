package app.orcinus.shadow.storage.android

import android.content.Context
import android.util.Log
import app.orcinus.shadow.core.model.ColorRgba
import app.orcinus.shadow.core.model.PlateDescription
import app.orcinus.shadow.core.model.PlateGeometry
import app.orcinus.shadow.core.model.Point2
import app.orcinus.shadow.core.model.ScenePath
import app.orcinus.shadow.storage.api.CachedPlate
import app.orcinus.shadow.storage.api.PlateCache
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException

/**
 * The last plate in one file next to the bed model and texture it points at, so
 * the 3D view shows the bed at once while the engine loads the profiles. The
 * file is written whenever the engine describes the plate, and read only until
 * that answer arrives; a file of another format version, or one that points at
 * files that are gone, is ignored.
 */
class AppPlateCache(context: Context) : PlateCache {
    private val file = File(File(context.applicationContext.noBackupFilesDir, "scene"), "plate.cache")

    override fun read(): CachedPlate? = try {
        if (file.exists()) DataInputStream(file.inputStream().buffered()).use(::readPlate) else null
    } catch (error: IOException) {
        Log.w(TAG, "Unreadable plate cache", error)
        null
    }

    override fun write(printer: String, description: PlateDescription) {
        try {
            file.parentFile?.mkdirs()
            DataOutputStream(file.outputStream().buffered()).use { stream -> stream.writePlate(printer, description) }
        } catch (error: IOException) {
            Log.w(TAG, "Cannot keep the plate", error)
        }
    }

    override fun clear() {
        file.delete()
    }

    private fun readPlate(stream: DataInputStream): CachedPlate? {
        if (stream.readInt() != MAGIC || stream.readInt() != VERSION) return null
        val printer = stream.readUTF()
        val geometry = PlateGeometry(
            printableArea = stream.readPoints(),
            printableHeight = stream.readDouble(),
            plateTriangles = stream.readPoints(),
            excludeTriangles = stream.readPoints(),
            thinGridLines = stream.readPoints(),
            boldGridLines = stream.readPoints(),
            bedModel = stream.readPath(),
            bedTexture = stream.readPath(),
        )
        val description = PlateDescription(
            geometry = geometry,
            filamentColor = ColorRgba(stream.readFloat(), stream.readFloat(), stream.readFloat(), stream.readFloat()),
        )
        // The bed model and texture are files of their own; without them the
        // view would draw a plate with holes in it.
        if (geometry.bedModel?.exists() == false || geometry.bedTexture?.exists() == false) return null
        return CachedPlate(printer, description)
    }

    private fun DataOutputStream.writePlate(printer: String, description: PlateDescription) {
        writeInt(MAGIC)
        writeInt(VERSION)
        writeUTF(printer)
        with(description.geometry) {
            writePoints(printableArea)
            writeDouble(printableHeight)
            writePoints(plateTriangles)
            writePoints(excludeTriangles)
            writePoints(thinGridLines)
            writePoints(boldGridLines)
            writePath(bedModel)
            writePath(bedTexture)
        }
        with(description.filamentColor) {
            writeFloat(red)
            writeFloat(green)
            writeFloat(blue)
            writeFloat(alpha)
        }
    }

    private fun DataInputStream.readPoints(): List<Point2> {
        val count = readInt()
        if (count < 0 || count > MAX_POINTS) throw IOException("Unexpected point count $count")
        return List(count) { Point2(readDouble(), readDouble()) }
    }

    private fun DataOutputStream.writePoints(points: List<Point2>) {
        writeInt(points.size)
        points.forEach {
            writeDouble(it.x)
            writeDouble(it.y)
        }
    }

    private fun DataInputStream.readPath(): ScenePath? = readUTF().takeIf(String::isNotEmpty)?.let(::ScenePath)

    private fun DataOutputStream.writePath(path: ScenePath?) = writeUTF(path?.value.orEmpty())

    private fun ScenePath.exists(): Boolean = File(value).exists()

    private companion object {
        const val TAG = "AppPlateCache"

        // "ORPL"
        const val MAGIC = 0x4F52504C
        const val VERSION = 1
        const val MAX_POINTS = 4_000_000
    }
}
