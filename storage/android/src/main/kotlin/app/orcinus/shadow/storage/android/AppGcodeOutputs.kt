package app.orcinus.shadow.storage.android

import android.content.Context
import app.orcinus.shadow.core.model.OutputPath
import app.orcinus.shadow.storage.api.GcodeOutputs
import java.io.File

/** G-code in the app's private files/gcode directory. */
class AppGcodeOutputs(context: Context) : GcodeOutputs {
    private val directory = File(context.applicationContext.filesDir, "gcode")

    override fun outputFor(name: String): OutputPath {
        directory.mkdirs()
        val fileName = name.replace('/', '_').ifBlank { "plate" }
        return OutputPath(File(directory, "$fileName.gcode").absolutePath)
    }
}
