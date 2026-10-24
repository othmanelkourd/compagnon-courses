plugins {
    alias(libs.plugins.compagnon.kmp.library)
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
