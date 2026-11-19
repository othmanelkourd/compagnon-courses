plugins {
    `kotlin-dsl`
}

group = "com.compagnon.courses.buildlogic"

kotlin {
    jvmToolchain(17)
}

dependencies {
    // compileOnly: the plugins themselves are put on the classpath once, by the root build
    // (`apply false`), so every module shares the same plugin classloader.
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.kotlin.composeCompilerGradlePlugin)
    compileOnly(libs.detekt.gradlePlugin)
}

tasks.validatePlugins {
    enableStricterValidation = true
    failOnWarning = true
}

gradlePlugin {
    plugins {
        register("kmpLibrary") {
            id = "compagnon.kmp.library"
            implementationClass = "com.compagnon.courses.buildlogic.KmpLibraryConventionPlugin"
        }
        register("kmpMobile") {
            id = "compagnon.kmp.mobile"
            implementationClass = "com.compagnon.courses.buildlogic.KmpMobileConventionPlugin"
        }
        register("androidApplication") {
            id = "compagnon.android.application"
            implementationClass = "com.compagnon.courses.buildlogic.AndroidApplicationConventionPlugin"
        }
    }
}
