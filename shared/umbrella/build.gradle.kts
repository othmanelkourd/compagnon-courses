import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    alias(libs.plugins.compagnon.kmp.mobile)
    // SKIE is applied only here: it post-processes the "Shared" Apple framework.
    alias(libs.plugins.skie)
}

kotlin {
    // iOS ships SQLite: the native binaries (test executables) link it. The app links it in Xcode (-lsqlite3),
    // since the static Shared framework does not carry linker options.
    targets.withType<KotlinNativeTarget>().configureEach {
        binaries.all { linkerOpts("-lsqlite3") }
    }

    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            isStatic = true
            binaryOption("bundleId", "com.compagnon.courses.Shared")
            // Swift sees the feature API without module prefixes. Network/contract types stay internal to
            // the framework's Kotlin side: Swift only talks to ViewModels and UI state.
            export(projects.shared.feature.catalog)
            export(projects.shared.feature.questionFlow)
            export(projects.shared.feature.lists)
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.shared.feature.catalog)
            api(projects.shared.feature.questionFlow)
            api(projects.shared.feature.lists)
            // `sharedModules(config: NetworkConfig, scope: CatalogScope)` is public API.
            api(projects.shared.core.network)
            api(projects.shared.core.data)
            implementation(projects.shared.core.database)
            api(libs.koin.core)
        }
        commonTest.dependencies {
            implementation(projects.shared.core.testing)
            implementation(libs.ktor.client.mock)
            implementation(libs.turbine)
        }
    }
}

skie {
    analytics {
        // No telemetry uploads from developer machines or CI.
        enabled = false
    }
}
