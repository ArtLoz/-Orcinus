pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Orcinus"
include(":app")
include(":core:model")
include(":core:designsystem")
include(":core:ui")
include(":data:notices")
include(":data:plate")
include(":domain")
include(":feature:about")
include(":feature:prepare")
include(":feature:preview")
include(":feature:sidebar")
include(":slicing:api")
include(":slicing:native")
include(":slicing:service")
include(":storage:api")
include(":storage:android")
