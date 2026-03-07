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

rootProject.name = "mhtml_reader"
include(":app")
include(":core-base")
include(":core-storage")
include(":core-data")
include(":core-domain")
include(":feature-files")
include(":feature-reader")
