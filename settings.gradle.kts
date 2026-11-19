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


// Kotlin Multiplatform client code
include(":shared:core:catalog-format")
include(":shared:core:model")
include(":shared:core:catalog-bundle")
include(":shared:core:database")
include(":shared:core:data")
include(":shared:core:testing")
include(":shared:engine")
include(":shared:feature:catalog")
include(":shared:feature:question-flow")
include(":shared:feature:lists")
include(":shared:feature:onboarding")
include(":shared:feature:about")
include(":shared:umbrella")

// Apps
include(":androidApp")
