import javax.inject.Inject

plugins {
    alias(libs.plugins.android.library)
}

/**
 * Builds liborcinus_toolpaths.so, OrcaSlicer's libvgcode with this module's JNI
 * bridge, with engine/CMakePresets.json, as :slicing:native builds the engine,
 * so both use the toolchain and flags of OrcaSlicer's own build.
 */
abstract class BuildOrcaToolpaths @Inject constructor(
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
            commandLine("cmake", "--build", "--preset", "android-arm64", "--target", "orcinus_toolpaths")
        }

        val library = engine.resolve("build/engine-arm64/jniLibs/arm64-v8a/liborcinus_toolpaths.so")
        val packaged = jniLibsDirectory.get().asFile.resolve("arm64-v8a/${library.name}")
        if (!packaged.isFile || packaged.lastModified() != library.lastModified() || packaged.length() != library.length()) {
            packaged.parentFile.mkdirs()
            library.copyTo(packaged, overwrite = true)
            packaged.setLastModified(library.lastModified())
        }
    }
}

val buildOrcaToolpaths = tasks.register<BuildOrcaToolpaths>("buildOrcaToolpaths") {
    engineDirectory.set(rootProject.layout.projectDirectory.dir("engine"))
    jniLibsDirectory.set(layout.buildDirectory.dir("generated/orcaToolpaths/jniLibs"))
    // Ninja decides what is out of date; the no-op build takes about a second.
    outputs.upToDateWhen { false }
    // Two ninja runs must not share the engine's build directory.
    mustRunAfter(":slicing:native:buildOrcaEngine")
}

android {
    namespace = "app.orcinus.shadow.render.gcode"
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
        variant.sources.jniLibs?.addGeneratedSourceDirectory(buildOrcaToolpaths, BuildOrcaToolpaths::jniLibsDirectory)
    }
}

dependencies {
    api(project(":render:scene"))
    implementation(project(":core:model"))
    implementation(libs.kotlinx.coroutines.core)
}
