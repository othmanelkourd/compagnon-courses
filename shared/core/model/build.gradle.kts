plugins {
    alias(libs.plugins.compagnon.kmp.library)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // Mappers from contract DTOs live in com.compagnon.courses.core.model.mapping.
            api(projects.contract.apiContract)
        }
        commonTest.dependencies {
            implementation(libs.kotest.property)
            implementation(libs.kotest.assertions.core)
        }
    }
}
