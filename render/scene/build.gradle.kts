plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
}

/**
 * Packages OrcaSlicer's GLSL 1.40 shaders from the pinned submodule as OpenGL
 * ES 3.0 shaders. The two dialects differ only in the version line and in the
 * default float precision, which ES requires in fragment shaders, so the
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
        const val ES_HEADER = "#version 300 es\nprecision highp float;\nprecision highp int;"
    }
}

val orcaShaders = rootProject.layout.projectDirectory.dir("upstream/OrcaSlicer/resources/shaders/140")
// Shaders the plate view uses, named as in OrcaSlicer.
val orcaShaderNames = listOf("flat", "gouraud", "gouraud_light", "hotbed", "printbed")

val convertOrcaShaders = tasks.register<ConvertOrcaShaders>("convertOrcaShaders") {
    shaders.from(orcaShaderNames.flatMap { listOf(orcaShaders.file("$it.vs"), orcaShaders.file("$it.fs")) })
    outputDirectory.set(layout.buildDirectory.dir("generated/orcaShaders/assets"))
}

android {
    namespace = "app.orcinus.shadow.render.scene"
    compileSdk = 37

    defaultConfig {
        minSdk = 29
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

androidComponents {
    onVariants(selector().all()) { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(convertOrcaShaders, ConvertOrcaShaders::outputDirectory)
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
