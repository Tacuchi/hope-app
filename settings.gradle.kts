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

rootProject.name = "hope"
include(":app")
include(":core:domain")
include(":core:data")
include(":core:media")
include(":core:ui")
include(":feature:library")
include(":feature:assets")
include(":feature:editor")
include(":feature:importing")
include(":feature:search")
include(":feature:settings")
