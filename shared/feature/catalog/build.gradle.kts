plugins {
    alias(libs.plugins.compagnon.kmp.library)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.shared.core.network)
            // ViewModel and StateFlow are part of the public (and exported Swift) API.
            api(libs.androidx.lifecycle.viewmodel)
            api(libs.kotlinx.coroutines.core)
            api(libs.koin.core)
            implementation(libs.koin.coreViewmodel)
            implementation(libs.kermit)
        }
        commonTest.dependencies {
            implementation(libs.turbine)
            implementation(libs.ktor.client.mock)
        }
    }
}
