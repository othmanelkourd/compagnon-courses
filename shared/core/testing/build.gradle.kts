// Test support shared by the tests of the shared modules: in-memory databases, a seed-shaped catalog,
// a controllable clock and MockEngine helpers. Only ever a test dependency (never shipped in an app).
plugins {
    alias(libs.plugins.compagnon.kmp.library)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.shared.core.database)
            api(projects.shared.core.network)
            api(libs.ktor.client.mock)
        }
        // Android host tests run on the JVM: the JDBC driver works there too.
        jvmMain.dependencies {
            implementation(libs.sqldelight.sqliteDriver)
        }
        androidMain.dependencies {
            implementation(libs.sqldelight.sqliteDriver)
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.nativeDriver)
        }
    }
}
