plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "app.orcinus.shadow.slicing.service"
    compileSdk = 37

    defaultConfig {
        minSdk = 29
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        aidl = true
    }
}

dependencies {
    api(project(":slicing:api"))
    implementation(libs.kotlinx.coroutines.core)
}
