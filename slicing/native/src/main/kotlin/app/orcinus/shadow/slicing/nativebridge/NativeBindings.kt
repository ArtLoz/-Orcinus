package app.orcinus.shadow.slicing.nativebridge

/** Called by the native bridge, possibly from an Orca worker thread. */
internal fun interface NativeProgressListener {
    fun onProgress(percent: Int, message: String)
}

/** Constructed by the native bridge; field order matches native_bridge.cpp. */
internal class NativeSliceResult(
    @JvmField val status: Long,
    @JvmField val message: String,
    @JvmField val layerCount: Long,
    @JvmField val estimatedPrintTimeSeconds: Long,
    @JvmField val filamentMicrometers: Long,
) {
    companion object {
        const val SUCCESS = 0L
        const val CANCELLED = 1L
        const val BUSY = 2L
        const val OUTPUT_WRITE_FAILED = 3L
        const val SLICING_FAILED = 4L
        const val PROFILE_NOT_FOUND = 5L
        const val MODEL_READ_FAILED = 6L
        const val ENGINE_NOT_READY = 7L
        const val INVALID_PRINT = 8L
    }
}

internal object NativeBindings {
    init {
        System.loadLibrary("orcinus_engine")
    }

    external fun engineVersion(): String

    /** Returns null when the engine is ready, otherwise the reason it is not. */
    external fun initialize(dataDir: String, resourcesDir: String, temporaryDir: String): String?

    /** An empty [modelPath] slices the built-in 20 mm calibration cube. */
    external fun slice(
        jobId: String,
        modelPath: String,
        outputPath: String,
        printerProfile: String,
        filamentProfile: String,
        processProfile: String,
        progressListener: NativeProgressListener,
    ): NativeSliceResult

    external fun cancel(jobId: String): Boolean

    external fun inspectStl(inputPath: String): LongArray?
}
