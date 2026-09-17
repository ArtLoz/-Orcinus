package app.orcinus.shadow.slicing.nativebridge

import android.content.Context
import java.io.File
import java.util.zip.ZipInputStream

internal data class OrcaDirectories(
    val data: File,
    val resources: File,
    val temporary: File,
)

/**
 * Extracts the Orca resources packaged by the Gradle task prepareOrcaAssets into
 * app storage whenever a new APK is installed: every vendor's profiles and the
 * runtime tables, laid out as Orca's resources directory. The data directory is
 * the engine's, as the desktop app keeps it: the app configuration and the
 * vendor bundles installed from the resources, which the engine updates when
 * the resources bring newer ones.
 */
internal object OrcaAssets {
    private const val ASSET_ROOT = "orca"
    private const val RESOURCES_ARCHIVE = "$ASSET_ROOT/resources.zip"

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

        marker.delete()
        directories.resources.deleteRecursively()
        ZipInputStream(context.assets.open(RESOURCES_ARCHIVE).buffered()).use { archive ->
            while (true) {
                val entry = archive.nextEntry ?: break
                require(!entry.name.startsWith("/") && entry.name.split('/').none { it == ".." }) {
                    "Unexpected entry in $RESOURCES_ARCHIVE: ${entry.name}"
                }
                if (entry.isDirectory) {
                    continue
                }
                val destination = File(directories.resources, entry.name)
                destination.parentFile?.mkdirs()
                destination.outputStream().use(archive::copyTo)
            }
        }
        directories.data.mkdirs()
        marker.writeText(installedApk)
        return directories
    }
}
