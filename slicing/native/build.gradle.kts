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
 * Packages OrcaSlicer's runtime files from the pinned submodule: vendor profile
 * bundles (loaded by PresetBundle from data/system) and the info/ and flush/
 * tables read from the resources directory. manifest.txt lists every file for
 * OrcaAssets, which copies them into app storage.
 */
abstract class PrepareOrcaAssets : DefaultTask() {
    @get:Internal
    abstract val orcaResources: DirectoryProperty

    @get:Input
    abstract val vendors: ListProperty<String>

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
        val manifest = mutableListOf<String>()

        fun copyTree(source: File, destination: String, include: (File) -> Boolean) {
            source.walkTopDown().filter { it.isFile && include(it) }.forEach { file ->
                val entry = "$destination/${file.relativeTo(source).invariantSeparatorsPath}"
                file.copyTo(root.resolve(entry))
                manifest += entry
            }
        }

        listOf("info", "flush").forEach { folder ->
            copyTree(resources.resolve(folder), "resources/$folder") { true }
        }
        vendors.get().forEach { vendor ->
            val bundle = resources.resolve("profiles/$vendor.json")
            check(bundle.isFile) { "OrcaSlicer vendor bundle is missing: $bundle" }
            bundle.copyTo(root.resolve("data/system/${bundle.name}"))
            manifest += "data/system/${bundle.name}"
            copyTree(resources.resolve("profiles/$vendor"), "data/system/$vendor") { it.extension == "json" }
        }

        root.resolve("manifest.txt").writeText(manifest.sorted().joinToString(separator = "\n", postfix = "\n"))
    }
}

val orcaResourcesDirectory = rootProject.layout.projectDirectory.dir("upstream/OrcaSlicer/resources")
// OrcaFilamentLibrary holds filaments that other vendor bundles may inherit from.
val bundledOrcaVendors = listOf("OrcaFilamentLibrary", "Creality")

val buildOrcaEngine = tasks.register<BuildOrcaEngine>("buildOrcaEngine") {
    engineDirectory.set(rootProject.layout.projectDirectory.dir("engine"))
    jniLibsDirectory.set(layout.buildDirectory.dir("generated/orcaEngine/jniLibs"))
    // Ninja decides what is out of date; the no-op build takes about a second.
    outputs.upToDateWhen { false }
}

val prepareOrcaAssets = tasks.register<PrepareOrcaAssets>("prepareOrcaAssets") {
    orcaResources.set(orcaResourcesDirectory)
    vendors.set(bundledOrcaVendors)
    packagedFiles.from(
        orcaResourcesDirectory.dir("info"),
        orcaResourcesDirectory.dir("flush"),
        bundledOrcaVendors.map { orcaResourcesDirectory.file("profiles/$it.json") },
        bundledOrcaVendors.map { vendor ->
            orcaResourcesDirectory.dir("profiles/$vendor").asFileTree.matching { include("**/*.json") }
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

dependencies {
    implementation(project(":core:model"))
    implementation(project(":slicing:api"))
    implementation(libs.kotlinx.coroutines.core)
}
