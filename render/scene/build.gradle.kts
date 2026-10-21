import javax.inject.Inject

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
}

/**
 * Packages OrcaSlicer's GLSL 1.40 shaders from the pinned submodule as OpenGL
 * ES 3.0 shaders. The two dialects differ only in the version line and in the
 * default precisions, which ES requires in fragment shaders, so the
 * shaders stay OrcaSlicer's own and follow upstream updates.
 */
abstract class ConvertOrcaShaders : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val shaders: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun convert() {
        val target = outputDirectory.get().asFile.resolve("orca/shaders")
        target.deleteRecursively()
        target.mkdirs()
        shaders.files.sortedBy { it.name }.forEach { shader ->
            val source = shader.readText().replace("\r\n", "\n")
            check(source.startsWith(DESKTOP_VERSION)) { "${shader.name} does not start with $DESKTOP_VERSION" }
            target.resolve(shader.name).writeText(ES_HEADER + source.removePrefix(DESKTOP_VERSION))
        }
    }

    private companion object {
        const val DESKTOP_VERSION = "#version 140"
        // ES samples with lowp by default in fragment shaders; the outline of gouraud reads depths.
        const val ES_HEADER = "#version 300 es\nprecision highp float;\nprecision highp int;\nprecision highp sampler2D;"
    }
}

val orcaShaders = rootProject.layout.projectDirectory.dir("upstream/OrcaSlicer/resources/shaders/140")
// Shaders the plate view and the G-code thumbnails use, named as in OrcaSlicer.
val orcaShaderNames = listOf(
    "flat", "flat_texture", "fxaa", "gouraud", "gouraud_light", "hotbed", "phong", "printbed", "ssao", "thumbnail", "variable_layer_height",
)

val convertOrcaShaders = tasks.register<ConvertOrcaShaders>("convertOrcaShaders") {
    shaders.from(orcaShaderNames.flatMap { listOf(orcaShaders.file("$it.vs"), orcaShaders.file("$it.fs")) })
    outputDirectory.set(layout.buildDirectory.dir("generated/orcaShaders/assets"))
}

/**
 * Builds liborcinus_scene.so, the native queries of large meshes, with
 * engine/CMakePresets.json, as :render:gcode builds its library, so every
 * native library of the app uses the toolchain and flags of OrcaSlicer's build.
 */
abstract class BuildOrcaScene @Inject constructor(
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
            commandLine("cmake", "--build", "--preset", "android-arm64", "--target", "orcinus_scene")
        }

        val library = engine.resolve("build/engine-arm64/jniLibs/arm64-v8a/liborcinus_scene.so")
        val packaged = jniLibsDirectory.get().asFile.resolve("arm64-v8a/${library.name}")
        if (!packaged.isFile || packaged.lastModified() != library.lastModified() || packaged.length() != library.length()) {
            packaged.parentFile.mkdirs()
            library.copyTo(packaged, overwrite = true)
            packaged.setLastModified(library.lastModified())
        }
    }
}

val buildOrcaScene = tasks.register<BuildOrcaScene>("buildOrcaScene") {
    engineDirectory.set(rootProject.layout.projectDirectory.dir("engine"))
    jniLibsDirectory.set(layout.buildDirectory.dir("generated/orcaScene/jniLibs"))
    // Ninja decides what is out of date; the no-op build takes about a second.
    outputs.upToDateWhen { false }
    // Two ninja runs must not share the engine's build directory.
    mustRunAfter(":slicing:native:buildOrcaEngine", ":render:gcode:buildOrcaToolpaths")
}

android {
    namespace = "app.orcinus.shadow.render.scene"
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

    buildFeatures {
        compose = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

androidComponents {
    onVariants(selector().all()) { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(convertOrcaShaders, ConvertOrcaShaders::outputDirectory)
        variant.sources.jniLibs?.addGeneratedSourceDirectory(buildOrcaScene, BuildOrcaScene::jniLibsDirectory)
    }
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:designsystem"))
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.core)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)

    testImplementation(kotlin("test-junit"))
}
