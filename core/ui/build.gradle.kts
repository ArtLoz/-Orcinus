plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
}

/**
 * Packages every gettext catalogue of the pinned OrcaSlicer as the asset
 * orca/i18n/<language>.po, with what msgfmt keeps of it: the header and the
 * translated entries that are not fuzzy, without comments. The app loads the
 * one of its language (OrcaLanguages.kt).
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
            root.resolve("${catalogue.parentFile.name}.po").writeText(compact(catalogue.readText()))
        }
    }

    /** The entries the app reads, one after another; an entry is its lines without comments. */
    private fun compact(po: String): String = po.replace("\r\n", "\n").split(Regex("\n{2,}")).mapNotNull { entry ->
        val lines = entry.lines().filter(String::isNotBlank)
        val fuzzy = lines.any { it.startsWith("#,") && it.substring(2).split(',').any { flag -> flag.trim() == "fuzzy" } }
        val kept = lines.filterNot { it.startsWith("#") }
        if (kept.isEmpty()) return@mapNotNull null
        // The strings after msgstr, and the lines that continue them.
        var inMsgstr = false
        val translation = StringBuilder()
        for (line in kept) {
            when {
                line.startsWith("msgstr") -> {
                    inMsgstr = true
                    translation.append(line.substringAfter(' ').trim().removeSurrounding("\""))
                }
                line.startsWith("\"") -> if (inMsgstr) translation.append(line.trim().removeSurrounding("\""))
                else -> inMsgstr = false
            }
        }
        // The header's msgid is empty; a long msgid starts empty too, and goes on on the next line.
        val header = kept.size >= 2 && kept[0] == "msgid \"\"" && kept[1].startsWith("msgstr")
        kept.joinToString("\n").takeIf { header || (!fuzzy && translation.isNotEmpty()) }
    }.joinToString("\n\n", postfix = "\n")
}

val prepareOrcaCatalogues = tasks.register<PrepareOrcaCatalogues>("prepareOrcaCatalogues") {
    catalogues.from(
        rootProject.layout.projectDirectory.dir("upstream/OrcaSlicer/localization/i18n").asFileTree.matching { include("*/OrcaSlicer_*.po") },
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
    // The request for the local network, which printers on Wi-Fi are on.
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.kotlinx.coroutines.core)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(kotlin("test-junit"))
}
