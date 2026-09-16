package app.orcinus.shadow.slicing.nativebridge

import android.content.Context
import java.io.File

internal data class OrcaDirectories(
    val data: File,
    val resources: File,
    val temporary: File,
)

/**
 * Copies the Orca files packaged by the Gradle task prepareOrcaAssets into app
 * storage: vendor profile bundles into data/system, as the desktop app keeps
 * them, and runtime tables into resources. The copy is refreshed whenever a new
 * APK is installed.
 */
internal object OrcaAssets {
    private const val ASSET_ROOT = "orca"
    private const val MANIFEST = "$ASSET_ROOT/manifest.txt"

    fun materialize(context: Context): OrcaDirectories {
        val root = File(context.noBackupFilesDir, ASSET_ROOT)
        val directories = OrcaDirectories(
            data = File(root, "data"),
            resources = File(root, "resources"),
            temporary = File(context.cacheDir, ASSET_ROOT),
        )
        val marker = File(root, ".installed-apk")
        val installedApk = context.packageManager
            .getPackageInfo(context.packageName, 0)
            .lastUpdateTime
            .toString()
        if (marker.isFile && marker.readText() == installedApk) {
            return directories
        }

        File(directories.data, "system").deleteRecursively()
        directories.resources.deleteRecursively()
        val entries = context.assets.open(MANIFEST).bufferedReader().use { reader ->
            reader.readLines().filter(String::isNotBlank)
        }
        for (entry in entries) {
            val destination = File(root, entry)
            destination.parentFile?.mkdirs()
            context.assets.open("$ASSET_ROOT/$entry").use { input ->
                destination.outputStream().use(input::copyTo)
            }
        }
        marker.writeText(installedApk)
        return directories
    }
}
