plugins {
    alias(libs.plugins.compagnon.kmp.mobile)
    // SKIE is applied only here: it post-processes the "Shared" Apple framework.
    alias(libs.plugins.skie)
}

kotlin {
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            isStatic = true
            binaryOption("bundleId", "com.compagnon.courses.Shared")
            // Swift sees the feature API without module prefixes. Network/contract types stay internal to
            // the framework's Kotlin side: Swift only talks to ViewModels and UI state.
            export(projects.shared.feature.catalog)
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.shared.feature.catalog)
            // `sharedModules(config: NetworkConfig)` is public API.
            api(projects.shared.core.network)
            api(libs.koin.core)
        }
        commonTest.dependencies {
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
