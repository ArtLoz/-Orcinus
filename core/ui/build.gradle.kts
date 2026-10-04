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

/** The texts of OrcaSlicer's web pages, data/text.js, as the asset orca/web/text.js (OrcaWebTexts.kt). */
abstract class PrepareOrcaWebTexts : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val texts: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun copy() {
        val root = outputDirectory.get().asFile
        root.deleteRecursively()
        texts.get().asFile.copyTo(root.resolve("orca/web/text.js"))
    }
}

val prepareOrcaWebTexts = tasks.register<PrepareOrcaWebTexts>("prepareOrcaWebTexts") {
    texts.set(rootProject.layout.projectDirectory.file("upstream/OrcaSlicer/resources/web/data/text.js"))
    outputDirectory.set(layout.buildDirectory.dir("generated/orcaWebTexts"))
}

/** The hints of DailyTipsPanel, data/hints.ini, as the asset orca/data/hints.ini (OrcaHints.kt). */
abstract class PrepareOrcaHints : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val hints: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun copy() {
        val root = outputDirectory.get().asFile
        root.deleteRecursively()
        hints.get().asFile.copyTo(root.resolve("orca/data/hints.ini"))
    }
}

val prepareOrcaHints = tasks.register<PrepareOrcaHints>("prepareOrcaHints") {
    hints.set(rootProject.layout.projectDirectory.file("upstream/OrcaSlicer/resources/data/hints.ini"))
    outputDirectory.set(layout.buildDirectory.dir("generated/orcaHints"))
}

androidComponents {
    onVariants(selector().all()) { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(prepareOrcaCatalogues, PrepareOrcaCatalogues::outputDirectory)
        variant.sources.assets?.addGeneratedSourceDirectory(prepareOrcaWebTexts, PrepareOrcaWebTexts::outputDirectory)
        variant.sources.assets?.addGeneratedSourceDirectory(prepareOrcaHints, PrepareOrcaHints::outputDirectory)
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
