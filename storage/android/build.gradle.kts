plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "app.orcinus.shadow.storage.android"
    compileSdk = 37

    defaultConfig {
        minSdk = 29
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":storage:api"))
    implementation(libs.kotlinx.coroutines.core)
}
