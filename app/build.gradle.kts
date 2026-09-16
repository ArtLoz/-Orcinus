import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.aboutlibraries.android)
}

/** The OrcaSlicer release the build bundles, from the submodule's lock file. */
val orcaRelease: Provider<String> = providers
    .fileContents(rootProject.layout.projectDirectory.file("upstream/orca.lock.json"))
    .asText
    .map { lock -> requireNotNull(Regex("\"release\"\\s*:\\s*\"v?([^\"]+)\"").find(lock)) { "No release in upstream/orca.lock.json" }.groupValues[1] }

/**
 * The public repository with the source code of this build, shown on the About
 * page. Set orcinus.sourceUrl in gradle.properties or pass -Porcinus.sourceUrl;
 * release builds require it, because the GNU AGPL obliges whoever distributes
 * the app to offer its source.
 */
val publicSourceUrl: Provider<String> = providers.gradleProperty("orcinus.sourceUrl").orElse("")

/**
 * Release signing: keystore.properties in the repository root (never committed)
 * or ORCINUS_* environment variables for CI. Without them the release build is
 * unsigned. See docs/publishing.md.
 */
val keystoreProperties: Provider<Properties> = providers
    .fileContents(rootProject.layout.projectDirectory.file("keystore.properties"))
    .asText
    .map { text -> Properties().apply { load(text.reader()) } }

fun signingValue(key: String, environmentVariable: String): String? =
    providers.environmentVariable(environmentVariable)
        .orElse(keystoreProperties.map { it.getProperty(key).orEmpty() })
        .orNull
        ?.takeIf { it.isNotBlank() }

android {
    namespace = "app.orcinus.shadow"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.orcinus.shadow"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        ndk {
            abiFilters += "arm64-v8a"
        }

        buildConfigField("String", "ORCA_RELEASE", "\"${orcaRelease.get()}\"")
        buildConfigField("String", "SOURCE_URL", "\"${publicSourceUrl.get()}\"")
        buildConfigField("String", "LICENSE_ID", "\"AGPL-3.0-only\"")
    }

    signingConfigs {
        create("release") {
            signingValue("storeFile", "ORCINUS_KEYSTORE_FILE")?.let { path ->
                storeFile = rootProject.file(path)
                storePassword = signingValue("storePassword", "ORCINUS_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "ORCINUS_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "ORCINUS_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("release").takeIf { it.storeFile != null }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

}

aboutLibraries {
    // License texts come from the build, never from the network: Maven
    // artifacts get theirs from app/notices by SPDX id, and the native engine
    // libraries and the font are described there by scripts/notices/update_notices.py.
    offlineMode = true
    collect {
        configPath = layout.projectDirectory.dir("notices")
    }
}

/** Stops a release build that would not tell users where its source code is. */
abstract class CheckSourceUrl : DefaultTask() {
    @get:Input
    abstract val sourceUrl: Property<String>

    @TaskAction
    fun check() {
        check(Regex("https://[^\\s\"\\\\]+").matches(sourceUrl.get())) {
            "Set orcinus.sourceUrl to the https:// address of the public repository (gradle.properties or -Porcinus.sourceUrl=...)"
        }
    }
}

val checkSourceUrl = tasks.register<CheckSourceUrl>("checkSourceUrl") {
    sourceUrl.set(publicSourceUrl)
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(checkSourceUrl)
}

dependencies {
    implementation(project(":core:designsystem"))
    implementation(project(":data:notices"))
    implementation(project(":data:plate"))
    implementation(project(":domain"))
    implementation(project(":feature:about"))
    implementation(project(":feature:prepare"))
    implementation(project(":feature:preview"))
    implementation(project(":feature:sidebar"))
    implementation(project(":slicing:api"))
    implementation(project(":slicing:native"))
    implementation(project(":slicing:service"))
    implementation(project(":storage:android"))
    implementation(project(":storage:api"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.core)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
}
