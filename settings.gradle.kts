// 🪨 BLOCK 1 — PLUGIN MANAGEMENT AND REPOSITORIES
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

// 🪨 BLOCK 2 — PROJECT IDENTITY SCOPE
rootProject.name = "rangewater-android"
include(":app")
