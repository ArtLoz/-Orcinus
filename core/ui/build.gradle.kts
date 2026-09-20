plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
}

/**
 * Packages OrcaSlicer's gettext catalogues from the pinned submodule as the
 * assets orca/i18n/<language>.po, for the languages the app's texts are in
 * (orca_catalog_language in the string resources).
 */
abstract class PrepareOrcaCatalogues : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val catalogues: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun prepare() {
        val root = outputDirectory.get().asFile.resolve("orca/i18n")
        root.deleteRecursively()
        root.mkdirs()
        catalogues.files.forEach { catalogue ->
            catalogue.copyTo(root.resolve("${catalogue.parentFile.name}.po"))
        }
    }
}

val orcaCatalogueLanguages = listOf("ru")

val prepareOrcaCatalogues = tasks.register<PrepareOrcaCatalogues>("prepareOrcaCatalogues") {
    catalogues.from(
        orcaCatalogueLanguages.map { rootProject.layout.projectDirectory.file("upstream/OrcaSlicer/localization/i18n/$it/OrcaSlicer_$it.po") },
    )
    outputDirectory.set(layout.buildDirectory.dir("generated/orcaCatalogues"))
}

android {
    namespace = "app.orcinus.shadow.core.ui"
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
        variant.sources.assets?.addGeneratedSourceDirectory(prepareOrcaCatalogues, PrepareOrcaCatalogues::outputDirectory)
    }
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:designsystem"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.kotlinx.coroutines.core)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(kotlin("test-junit"))
}
