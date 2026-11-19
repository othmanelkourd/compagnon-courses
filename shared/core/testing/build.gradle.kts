// Test support shared by the tests of the shared modules: in-memory databases, a seed-shaped catalog,
// and a controllable clock. Only ever a test dependency (never shipped in an app).
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    alias(libs.plugins.compagnon.kmp.library)
}

kotlin {
    // iOS ships SQLite: the native binaries (test executables) link it. The app links it in Xcode (-lsqlite3),
    // since the static Shared framework does not carry linker options.
    targets.withType<KotlinNativeTarget>().configureEach {
        binaries.all { linkerOpts("-lsqlite3") }
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.shared.core.catalogBundle)
            api(projects.shared.core.catalogFormat)
            api(projects.shared.core.database)
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
