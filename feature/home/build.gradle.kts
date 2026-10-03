plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * Copies files of OrcaSlicer's home page (resources/web) as they are, each to
 * its path under the output directory: the texts of data/text.js as the asset
 * orca/web/text.js (HomeTexts.kt), and the pictures of homepage/img as
 * drawables named after them.
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

val orcaWeb = rootProject.layout.projectDirectory.dir("upstream/OrcaSlicer/resources/web")

val prepareHomeTexts = tasks.register<CopyOrcaFiles>("prepareHomeTexts") {
    orcaDirectory.set(orcaWeb.dir("data"))
    files.put("orca/web/text.js", "text.js")
    outputDirectory.set(layout.buildDirectory.dir("generated/homeTexts"))
}

val prepareHomeImages = tasks.register<CopyOrcaFiles>("prepareHomeImages") {
    orcaDirectory.set(orcaWeb.dir("homepage/img"))
    // The pictures of "New Project" and "Open Project", and of a recent file without its own.
    files.put("drawable-nodpi/homepage_i4.png", "i4.png")
    files.put("drawable-nodpi/homepage_i5.png", "i5.png")
    files.put("drawable-nodpi/homepage_d.png", "d.png")
    outputDirectory.set(layout.buildDirectory.dir("generated/homeImages"))
}

android {
    namespace = "app.orcinus.shadow.feature.home"
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
        variant.sources.assets?.addGeneratedSourceDirectory(prepareHomeTexts, CopyOrcaFiles::outputDirectory)
        variant.sources.res?.addGeneratedSourceDirectory(prepareHomeImages, CopyOrcaFiles::outputDirectory)
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
