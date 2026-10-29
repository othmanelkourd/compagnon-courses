plugins {
    alias(libs.plugins.compagnon.kmp.library)
    alias(libs.plugins.kover)
}

// V0 exit criterion: the engine is covered at 90 % of its lines or more (measured on the jvm tests).
// `./gradlew :shared:engine:koverHtmlReport` shows what is missing.
kover {
    reports {
        verify {
            rule {
                minBound(90)
            }
        }
    }
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.shared.core.model)
        }
        commonTest.dependencies {
            implementation(libs.kotest.property)
            implementation(libs.kotest.assertions.core)
        }
    }
}
