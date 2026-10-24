plugins {
    alias(libs.plugins.compagnon.kmp.library)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.shared.core.database)
            api(projects.shared.core.network)
            api(projects.shared.engine)
            api(libs.kotlinx.coroutines.core)
            api(libs.koin.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kermit)
        }
        commonTest.dependencies {
            implementation(projects.shared.core.testing)
            implementation(libs.turbine)
        }
    }
}
