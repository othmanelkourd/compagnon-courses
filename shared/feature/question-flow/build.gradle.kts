import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    alias(libs.plugins.compagnon.kmp.library)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    // iOS ships SQLite: the native binaries (test executables) link it. The app links it in Xcode (-lsqlite3),
    // since the static Shared framework does not carry linker options.
    targets.withType<KotlinNativeTarget>().configureEach {
        binaries.all { linkerOpts("-lsqlite3") }
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.shared.core.data)
            // ViewModel and StateFlow are part of the public (and exported Swift) API.
            api(libs.androidx.lifecycle.viewmodel)
            api(libs.kotlinx.coroutines.core)
            api(libs.koin.core)
            implementation(libs.koin.coreViewmodel)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kermit)
        }
        commonTest.dependencies {
            implementation(projects.shared.core.testing)
            implementation(libs.turbine)
        }
    }
}
