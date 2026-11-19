plugins {
    alias(libs.plugins.compagnon.kmp.library)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Mappers from the catalog format DTOs live in com.compagnon.courses.core.model.mapping.
            api(projects.shared.core.catalogFormat)
        }
        commonTest.dependencies {
            implementation(libs.kotest.property)
            implementation(libs.kotest.assertions.core)
        }
    }
}
