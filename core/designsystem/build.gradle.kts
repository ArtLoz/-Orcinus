import com.android.ide.common.vectordrawable.Svg2Vector

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
}

/**
 * Converts OrcaSlicer's SVG icons from the pinned submodule into vector
 * drawables named orca_<icon>, with the converter Android Studio uses for SVG
 * import. The night variants apply the colour replacements OrcaSlicer applies
 * when it loads an icon in dark mode, so icons follow upstream updates without
 * copies in this repository.
 */
abstract class ConvertOrcaIcons : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val icons: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun convert() {
        val root = outputDirectory.get().asFile
        root.deleteRecursively()
        val scratch = temporaryDir
        for ((folder, replacements) in listOf("drawable" to lightReplacements, "drawable-night" to darkReplacements)) {
            val drawables = root.resolve(folder).apply { mkdirs() }
            icons.files.sortedBy { it.name }.forEach { svg ->
                val recoloured = scratch.resolve(svg.name)
                recoloured.writeText(replacements.entries.fold(svg.readText()) { text, (from, to) -> text.replace(from, to) })
                val target = drawables.resolve("orca_${svg.nameWithoutExtension}.xml")
                val errors = target.outputStream().use { Svg2Vector.parseSvgToXml(recoloured.toPath(), it) }
                check(target.length() > 0) { "Unable to convert ${svg.name}: $errors" }
            }
        }
    }

    private companion object {
        // BitmapCache::load_svg in OrcaSlicer's src/slic3r/GUI/BitmapCache.cpp.
        val commonReplacements = mapOf("\"#0x00AE42\"" to "\"#009688\"")
        val lightReplacements = commonReplacements + mapOf(
            "\"#00FF00\"" to "\"#52c7b8\"",
            "#949494" to "#7C8282",
        )
        val darkReplacements = commonReplacements + mapOf(
            "\"#262E30\"" to "\"#EFEFF0\"",
            "\"#323A3D\"" to "\"#B3B3B5\"",
            "\"#808080\"" to "\"#818183\"",
            "\"#CECECE\"" to "\"#54545B\"",
            "\"#6B6B6B\"" to "\"#818182\"",
            "\"#909090\"" to "\"#FFFFFF\"",
            "\"#00FF00\"" to "\"#FF0000\"",
            "\"#009688\"" to "\"#00675b\"",
            "\"#F1F1F1\"" to "\"#36363B\"",
            "#DBDBDB" to "#4A4A51",
            "#F0F0F1" to "#333337",
            "#262E30" to "#EFEFF0",
        )
    }
}

val orcaImages = rootProject.layout.projectDirectory.dir("upstream/OrcaSlicer/resources/images")
// Icons used by the app, named as in OrcaSlicer.
val orcaIconNames = listOf(
    "add", "cali_page_caption_prev", "canvas_menu", "canvas_zoom", "collapse", "check_half",
    "check_half_disabled", "check_off", "check_off_disabled", "check_on", "check_on_disabled", "cog",
    "delete", "drop_down", "edit", "filament", "help", "hms_arrow", "im_visible", "param_cooling", "param_infill", "param_layer_height",
    "param_precision", "param_retraction", "param_seam", "param_speed", "param_support",
    "param_wall", "plate_settings", "printer", "process", "search", "spin_dec", "spin_inc",
    "tab_3d_active", "tab_monitor_active", "tab_preview_active", "toolbar_arrange", "toolbar_open",
    "toolbar_orient",
)

val convertOrcaIcons = tasks.register<ConvertOrcaIcons>("convertOrcaIcons") {
    icons.from(orcaIconNames.map { orcaImages.file("$it.svg") })
    outputDirectory.set(layout.buildDirectory.dir("generated/orcaIcons/res"))
}

android {
    namespace = "app.orcinus.shadow.core.designsystem"
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
        variant.sources.res?.addGeneratedSourceDirectory(convertOrcaIcons, ConvertOrcaIcons::outputDirectory)
    }
}

dependencies {
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
