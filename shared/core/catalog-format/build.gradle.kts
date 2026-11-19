plugins {
    alias(libs.plugins.compagnon.kmp.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.serialization.json)
        }
    }
}
