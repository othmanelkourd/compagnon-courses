// Root build: puts every third-party plugin on the classpath once (`apply false`) so that all modules,
// and the build-logic convention plugins, share the same plugin classloader.
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.skie) apply false
    alias(libs.plugins.mokkery) apply false
    alias(libs.plugins.sqldelight) apply false
    alias(libs.plugins.kover) apply false
    alias(libs.plugins.detekt) apply false
}
