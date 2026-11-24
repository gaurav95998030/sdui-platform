pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
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

rootProject.name = "sdui-platform"

// ---- SDK modules (published as a library) ----
include(":sdui-core")
include(":sdui-parser")
include(":sdui-state")
include(":sdui-theme")
include(":sdui-cache")
include(":sdui-network")
include(":sdui-analytics")
include(":sdui-actions")
include(":sdui-navigation")
include(":sdui-renderer")
include(":sdui-components")
include(":sdui")

// ---- Consumer ----
include(":demo-app")
