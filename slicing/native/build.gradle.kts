import java.time.LocalDateTime
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject

plugins {
    alias(libs.plugins.android.library)
}

/**
 * Builds liborcinus_engine.so with engine/CMakePresets.json, so the JNI bridge
 * is compiled with exactly the toolchain and flags of OrcaSlicer's libslic3r.
 * The dependency prefix comes from scripts/engine.ps1 -Stage deps.
 */
abstract class BuildOrcaEngine @Inject constructor(
    private val execOperations: ExecOperations,
) : DefaultTask() {
    @get:Internal
    abstract val engineDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val jniLibsDirectory: DirectoryProperty

    @TaskAction
    fun build() {
        val engine = engineDirectory.get().asFile
        check(engine.resolve("build/deps-arm64/prefix/lib/cmake").isDirectory) {
            "OrcaSlicer engine dependencies are missing. Run: " +
                "powershell -ExecutionPolicy Bypass -File scripts/engine.ps1 -Stage deps"
        }
        if (!engine.resolve("build/engine-arm64/build.ninja").isFile) {
            execOperations.exec {
                workingDir = engine
                commandLine("cmake", "--preset", "android-arm64")
            }
        }
        execOperations.exec {
            workingDir = engine
            commandLine("cmake", "--build", "--preset", "android-arm64", "--target", "orcinus_engine")
        }

        val library = engine.resolve("build/engine-arm64/jniLibs/arm64-v8a/liborcinus_engine.so")
        val packaged = jniLibsDirectory.get().asFile.resolve("arm64-v8a/${library.name}")
        // Only the current library is packaged, even after the target was renamed.
        jniLibsDirectory.get().asFile.walkBottomUp()
            .filter { it.isFile && it != packaged }
            .forEach { it.delete() }
        if (!packaged.isFile || packaged.lastModified() != library.lastModified() || packaged.length() != library.length()) {
            packaged.parentFile.mkdirs()
            library.copyTo(packaged, overwrite = true)
            packaged.setLastModified(library.lastModified())
        }
    }
}

/**
 * Packages OrcaSlicer's runtime files from the pinned submodule as the asset
 * orca/resources.zip, laid out as Orca's resources directory: every vendor's
 * profiles under profiles/, with the printer covers, bed models, and textures
 * they name, the info/ and flush/ tables, and the printer types of printers/.
 * OrcaAssets extracts it into app storage; the engine installs the vendor
 * bundles the Setup Wizard chooses from there into data/system, as the desktop
 * app does. The archive's entries are sorted and dated alike, so it only
 * changes with the files. They are stored uncompressed: the APK compresses the
 * archive as a whole, which suits 12 000 small JSON files better than
 * compressing each.
 */
abstract class PrepareOrcaAssets : DefaultTask() {
    @get:Internal
    abstract val orcaResources: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val packagedFiles: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun prepare() {
        val resources = orcaResources.get().asFile
        val root = outputDirectory.get().asFile.resolve("orca")
        root.deleteRecursively()
        root.mkdirs()
        val entries = packagedFiles.files.filter(File::isFile)
            .associateBy { it.relativeTo(resources).invariantSeparatorsPath }
            .toSortedMap()
        ZipOutputStream(root.resolve("resources.zip").outputStream().buffered()).use { archive ->
            entries.forEach { (name, file) ->
                val bytes = file.readBytes()
                val entry = ZipEntry(name).apply {
                    timeLocal = LocalDateTime.of(1980, 2, 1, 0, 0)
                    method = ZipEntry.STORED
                    size = bytes.size.toLong()
                    compressedSize = size
                    crc = CRC32().apply { update(bytes) }.value
                }
                archive.putNextEntry(entry)
                archive.write(bytes)
                archive.closeEntry()
            }
        }
    }
}

val orcaResourcesDirectory = rootProject.layout.projectDirectory.dir("upstream/OrcaSlicer/resources")

val buildOrcaEngine = tasks.register<BuildOrcaEngine>("buildOrcaEngine") {
    engineDirectory.set(rootProject.layout.projectDirectory.dir("engine"))
    jniLibsDirectory.set(layout.buildDirectory.dir("generated/orcaEngine/jniLibs"))
    // Ninja decides what is out of date; the no-op build takes about a second.
    outputs.upToDateWhen { false }
}

val prepareOrcaAssets = tasks.register<PrepareOrcaAssets>("prepareOrcaAssets") {
    orcaResources.set(orcaResourcesDirectory)
    packagedFiles.from(
        orcaResourcesDirectory.dir("info"),
        orcaResourcesDirectory.dir("flush"),
        // DevPrinterConfigUtil's printer types, which ConfigManipulation asks.
        orcaResourcesDirectory.dir("printers").asFileTree.matching { include("*.json") },
        orcaResourcesDirectory.dir("profiles").asFileTree.matching { include("**/*.json", "**/*.png", "**/*.stl", "**/*.svg") },
        // MenuFactory::append_submenu_add_handy_model()
        orcaResourcesDirectory.dir("handy_models").asFileTree.matching { include("*.drc", "*.3mf") },
        // The models of the Calibration menu (Plater::calib_*).
        orcaResourcesDirectory.dir("calib").asFileTree.matching { include("**/*.drc", "**/*.3mf") },
        // The page the Device tab shows until the printer has a host
        // (Sidebar::update_all_preset_comboboxes()), with what it loads, and
        // Elegoo's LAN page of a Centauri Carbon 2 (ElegooLink::get_print_host_webui()).
        orcaResourcesDirectory.dir("web").asFileTree.matching {
            include("orca/**", "data/text.js", "homepage/js/jquery-3.6.0.min.js", "homepage/js/json2.js", "homepage/js/globalapi.js", "homepage/js/home.js")
            include("elegoolink/**")
        },
    )
    outputDirectory.set(layout.buildDirectory.dir("generated/orcaAssets"))
}

android {
    namespace = "app.orcinus.shadow.slicing.nativebridge"
    compileSdk = 37
    ndkVersion = "28.2.13676358"

    defaultConfig {
        minSdk = 29
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

androidComponents {
    onVariants(selector().all()) { variant ->
        variant.sources.jniLibs?.addGeneratedSourceDirectory(buildOrcaEngine, BuildOrcaEngine::jniLibsDirectory)
        variant.sources.assets?.addGeneratedSourceDirectory(prepareOrcaAssets, PrepareOrcaAssets::outputDirectory)
    }
}

// The port of OrcaSlicer's settings tabs is checked against the pinned sources.
tasks.withType<Test>().configureEach {
    systemProperty("orcinus.upstreamTab", rootProject.layout.projectDirectory.file("upstream/OrcaSlicer/src/slic3r/GUI/Tab.cpp").asFile.absolutePath)
    systemProperty("orcinus.adapterSources", layout.projectDirectory.dir("src/main/cpp").asFile.absolutePath)
    systemProperty("orcinus.consumerRules", layout.projectDirectory.file("consumer-rules.pro").asFile.absolutePath)
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":slicing:api"))
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(kotlin("test-junit"))
}
