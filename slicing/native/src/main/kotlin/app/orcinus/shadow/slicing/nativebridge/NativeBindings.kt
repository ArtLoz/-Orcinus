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
    @JvmField val toolpathsWritten: Boolean,
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

/** Constructed by the native bridge; see PlateDescription in orca_engine_adapter.hpp. */
internal class NativePlateDescription(
    @JvmField val status: Long,
    @JvmField val message: String,
    @JvmField val printableArea: DoubleArray,
    @JvmField val printableHeight: Double,
    @JvmField val plateTriangles: FloatArray,
    @JvmField val excludeTriangles: FloatArray,
    @JvmField val thinGridLines: FloatArray,
    @JvmField val boldGridLines: FloatArray,
    @JvmField val bedModelMesh: String,
    @JvmField val bedTexture: String,
    @JvmField val filamentColour: String,
)

/** Constructed by the native bridge; see ModelInspection in orca_engine_adapter.hpp. */
internal class NativeModelInspection(
    @JvmField val status: Long,
    @JvmField val message: String,
    @JvmField val facetCount: Long,
    @JvmField val sizeX: Double,
    @JvmField val sizeY: Double,
    @JvmField val sizeZ: Double,
    @JvmField val instanceMatrix: DoubleArray,
    /** NativeVolumeState. */
    @JvmField val volumeState: Long,
    @JvmField val sphereCenter: DoubleArray,
    @JvmField val sphereRadius: Double,
    @JvmField val rotationDegrees: DoubleArray,
    @JvmField val unscaledSize: DoubleArray,
    @JvmField val boxCenter: DoubleArray,
)

/** Constructed by the native bridge; see PlateInspection in orca_engine_adapter.hpp. */
internal class NativePlateInspection(
    @JvmField val status: Long,
    @JvmField val message: String,
    /** Every object of the plate as placed, in the plate's order. */
    @JvmField val objects: Array<NativeModelInspection>,
)

internal class NativeFlatteningPlanes(
    @JvmField val status: Long,
    @JvmField val message: String,
    /** x, y, z per plane. */
    @JvmField val normals: DoubleArray,
    /** Points per plane. */
    @JvmField val vertexCounts: IntArray,
    /** x, y, z per point of every plane in turn. */
    @JvmField val vertices: FloatArray,
)

/** SceneStatus in orca_engine_adapter.hpp. */
internal object NativeSceneStatus {
    const val SUCCESS = 0L
}

/** VolumeState in orca_engine_adapter.hpp. */
internal object NativeVolumeState {
    const val INSIDE = 0L
    const val PARTLY_OUTSIDE = 1L
}

internal object NativeBindings {
    init {
        System.loadLibrary("orcinus_engine")
    }

    external fun engineVersion(): String

    /** Returns null when the engine is ready, otherwise the reason it is not. */
    external fun initialize(dataDir: String, resourcesDir: String, temporaryDir: String): String?

    /**
     * Slices the objects of the plate, given per object: [modelPaths] (empty for
     * the built-in 20 mm calibration cube), 16 [placements] elements of its
     * instance transformation, column-major 4 x 4, and [autoDrops].
     */
    external fun slice(
        jobId: String,
        modelPaths: Array<String>,
        placements: DoubleArray,
        autoDrops: BooleanArray,
        outputPath: String,
        /** Where the toolpaths for libvgcode go; null writes none. */
        toolpathsPath: String?,
        printerProfile: String,
        filamentProfile: String,
        processProfile: String,
        progressListener: NativeProgressListener,
    ): NativeSliceResult

    external fun cancel(jobId: String): Boolean

    external fun describePlate(
        printerProfile: String,
        filamentProfile: String,
        processProfile: String,
        outputDirectory: String,
    ): NativePlateDescription

    /**
     * An empty [modelPath] inspects the built-in 20 mm calibration cube. The
     * objects already on the plate are given as for [slice].
     */
    external fun inspectModel(
        modelPath: String,
        printerProfile: String,
        filamentProfile: String,
        processProfile: String,
        meshPath: String,
        plateModelPaths: Array<String>,
        platePlacements: DoubleArray,
        plateAutoDrops: BooleanArray,
    ): NativeModelInspection

    /**
     * Commits [manipulation] (Manipulation in orca_engine_adapter.hpp) of the object of [modelPath]
     * (empty for the cube) from [previousPlacement] to [placement], column-major 4 x 4.
     */
    external fun placeModel(
        modelPath: String,
        printerProfile: String,
        filamentProfile: String,
        processProfile: String,
        previousPlacement: DoubleArray,
        placement: DoubleArray,
        autoDrop: Boolean,
        manipulation: Long,
        /** For lay_on_face: x, y, z in object coordinates. */
        faceNormal: DoubleArray?,
    ): NativeModelInspection

    /**
     * Commits [manipulation] (PlateManipulation in orca_engine_adapter.hpp) of
     * the objects of the plate, given as for [slice], with a [selected] flag per
     * object for auto orient.
     */
    external fun placeObjects(
        modelPaths: Array<String>,
        placements: DoubleArray,
        autoDrops: BooleanArray,
        selected: BooleanArray,
        printerProfile: String,
        filamentProfile: String,
        processProfile: String,
        manipulation: Long,
        /** ArrangeSettings for arrange. */
        arrangeDistance: Double,
        arrangeEnableRotation: Boolean,
        arrangeAllowMultiMaterials: Boolean,
        arrangeAlignToYAxis: Boolean,
    ): NativePlateInspection

    external fun describeFlatteningPlanes(
        modelPath: String,
        printerProfile: String,
        filamentProfile: String,
        processProfile: String,
        placement: DoubleArray,
    ): NativeFlatteningPlanes
}
