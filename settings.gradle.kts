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
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "courses"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

// API contract shared by the server and the clients
include(":contract:api-contract")

// Kotlin Multiplatform client code
include(":shared:core:model")
include(":shared:core:network")
include(":shared:core:database")
include(":shared:core:data")
include(":shared:core:testing")
include(":shared:engine")
include(":shared:feature:catalog")
include(":shared:feature:question-flow")
include(":shared:feature:lists")
include(":shared:umbrella")

// Apps
include(":androidApp")
include(":server:app")
