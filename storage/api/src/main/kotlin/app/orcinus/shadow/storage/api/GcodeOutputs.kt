package app.orcinus.shadow.storage.api

import app.orcinus.shadow.core.model.OutputPath

/** Where sliced G-code is written. */
fun interface GcodeOutputs {
    /** The G-code file for a plate named [name], without extension. */
    fun outputFor(name: String): OutputPath
}
