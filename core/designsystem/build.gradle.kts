import com.android.ide.common.vectordrawable.Svg2Vector

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
}

/**
 * Converts OrcaSlicer's SVG icons from the pinned submodule into vector
 * drawables named orca_<icon>, with the converter Android Studio uses for SVG
 * import. A night variant is the icon's own <icon>_dark.svg where OrcaSlicer
 * has one, as its toolbars load it in dark mode; otherwise it applies the
 * colour replacements OrcaSlicer applies when it loads an icon in dark mode.
 * Icons follow upstream updates without copies in this repository.
 */
abstract class ConvertOrcaIcons : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val icons: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val darkIcons: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun convert() {
        val root = outputDirectory.get().asFile
        root.deleteRecursively()
        val scratch = temporaryDir
        val darkFiles = darkIcons.files.associateBy { it.nameWithoutExtension.removeSuffix("_dark") }
        for ((folder, dark) in listOf("drawable" to false, "drawable-night" to true)) {
            val drawables = root.resolve(folder).apply { mkdirs() }
            icons.files.sortedBy { it.name }.forEach { light ->
                val ownDark = darkFiles[light.nameWithoutExtension].takeIf { dark }
                val svg = ownDark ?: light
                val replacements = when {
                    ownDark != null -> emptyMap()
                    dark -> darkReplacements
                    else -> lightReplacements
                }
                val recoloured = scratch.resolve(svg.name)
                recoloured.writeText(replacements.entries.fold(svg.readText()) { text, (from, to) -> text.replace(from, to) })
                val target = drawables.resolve("orca_${light.nameWithoutExtension}.xml")
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
    "add", "cali_page_caption_prev", "canvas_menu", "canvas_zoom", "check_half", "checked",
    "check_half_disabled", "check_off", "check_off_disabled", "check_on", "check_on_disabled",
    "cog", "collapse", "delete", "drop_down", "edit", "filament", "help", "hms_arrow",
    "im_visible", "instance_add", "instance_remove", "param_cooling", "param_infill",
    "param_layer_height", "param_precision", "param_retraction", "param_seam", "param_speed",
    "param_support", "param_wall", "plate_settings", "printer", "process", "search", "seperator",
    "spin_dec", "spin_inc", "split_objects", "split_parts", "tab_3d_active", "tab_monitor_active",
    "tab_preview_active", "toolbar_add_plate", "toolbar_arrange", "toolbar_assemble",
    "toolbar_assembly", "toolbar_brimears", "toolbar_cut", "toolbar_flatten",
    "toolbar_fuzzy_skin_paint", "toolbar_measure", "toolbar_meshboolean", "toolbar_move",
    "toolbar_open", "toolbar_orient", "toolbar_reset", "toolbar_reset_zero", "toolbar_rotate", "toolbar_scale", "toolbar_seam",
    "toolbar_support", "toolbar_text", "toolbar_variable_layer_height",
)
// Toolbar icons, whose dark variant OrcaSlicer loads from <icon>_dark.svg
// (GLCanvas3D::_init_main_toolbar, GLGizmosManager::init).
val orcaDarkIconNames = listOf(
    "toolbar_open", "toolbar_add_plate", "toolbar_orient", "toolbar_arrange", "instance_add",
    "instance_remove", "split_objects", "split_parts", "toolbar_variable_layer_height",
    "toolbar_move", "toolbar_rotate", "toolbar_scale", "toolbar_flatten", "toolbar_cut",
    "toolbar_meshboolean", "toolbar_support", "toolbar_seam", "toolbar_fuzzy_skin_paint",
    "toolbar_text", "toolbar_measure", "toolbar_assembly", "toolbar_brimears", "toolbar_assemble",
)

val convertOrcaIcons = tasks.register<ConvertOrcaIcons>("convertOrcaIcons") {
    icons.from(orcaIconNames.map { orcaImages.file("$it.svg") })
    darkIcons.from(orcaDarkIconNames.map { orcaImages.file("${it}_dark.svg") })
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
