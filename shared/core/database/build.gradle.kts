plugins {
    alias(libs.plugins.compagnon.kmp.library)
    alias(libs.plugins.sqldelight)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // CompagnonDatabase, SqlDriver and the Flow extensions are part of the public API.
            api(libs.sqldelight.runtime)
            api(libs.sqldelight.coroutines)
        }
        androidMain.dependencies {
            implementation(libs.sqldelight.androidDriver)
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.nativeDriver)
        }
        // The JVM target only exists for fast host tests: JDBC sqlite driver.
        jvmMain.dependencies {
            implementation(libs.sqldelight.sqliteDriver)
        }
    }
}

sqldelight {
    databases {
        create("CompagnonDatabase") {
            packageName.set("com.compagnon.courses.core.database")
            // Schema snapshots (1.db, 2.db…) are versioned: `verifyCommonMainCompagnonDatabaseMigration` checks
            // that every migration (n.sqm) turns snapshot n into snapshot n + 1. Regenerate the snapshot of a new
            // version with `generateCommonMainCompagnonDatabaseSchema`.
            schemaOutputDirectory.set(file("src/commonMain/sqldelight/databases"))
            verifyMigrations.set(true)
        }
    }
}
