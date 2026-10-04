plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * Copies files of OrcaSlicer's project page (resources/web/model) as they are,
 * each to its path under the output directory: the pictures of model/img as
 * drawables named after them. Its texts come with core:ui (OrcaWebTexts.kt).
 */
abstract class CopyOrcaFiles : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val orcaDirectory: DirectoryProperty

    /** The paths under [orcaDirectory] by the paths they get under [outputDirectory]. */
    @get:Input
    abstract val files: MapProperty<String, String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun copy() {
        val root = outputDirectory.get().asFile
        root.deleteRecursively()
        files.get().forEach { (target, source) ->
            orcaDirectory.get().file(source).asFile.copyTo(root.resolve(target))
        }
    }
}

val orcaWebModel = rootProject.layout.projectDirectory.dir("upstream/OrcaSlicer/resources/web/model/img")

val prepareProjectImages = tasks.register<CopyOrcaFiles>("prepareProjectImages") {
    orcaDirectory.set(orcaWebModel)
    // "No model information", the icons of the accessories, and the licences' badges (ShowModelInfo()).
    listOf(
        "null", "excel", "pdf", "default",
        "cc-zero", "by", "by-sa", "by-nd", "by-nc", "by-nc-sa", "by-nc-nd",
    ).forEach { name -> files.put("drawable-nodpi/project_${name.replace('-', '_')}.png", "$name.png") }
    outputDirectory.set(layout.buildDirectory.dir("generated/projectImages"))
}

android {
    namespace = "app.orcinus.shadow.feature.project"
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
        variant.sources.res?.addGeneratedSourceDirectory(prepareProjectImages, CopyOrcaFiles::outputDirectory)
    }
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(project(":core:designsystem"))
    implementation(project(":core:ui"))
    implementation(project(":domain"))
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.kotlinx.serialization.core)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)

    testImplementation(kotlin("test-junit"))
}
