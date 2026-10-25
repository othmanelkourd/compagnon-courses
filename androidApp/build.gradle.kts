plugins {
    alias(libs.plugins.compagnon.android.application)
    // Navigation 3 keys are @Serializable (saved back stack).
    alias(libs.plugins.kotlin.serialization)
}

// Dev only: the local server as seen from the Android emulator (10.0.2.2 is the host's loopback).
// Override with -Pcompagnon.apiBaseUrl=..., e.g. http://localhost:8080 on a physical device after
// `adb reverse tcp:8080 tcp:8080`. Cleartext HTTP is only allowed for 10.0.2.2 and localhost, in debug builds
// (src/debug/res/xml/network_security_config.xml).
val debugApiBaseUrl: String = providers.gradleProperty("compagnon.apiBaseUrl")
    .getOrElse("http://10.0.2.2:8080")

// TODO(ADR-007): real API host once hosting is decided. `.invalid` never resolves (RFC 2606).
val releaseApiBaseUrl = "https://api.compagnon.invalid"

fun buildConfigString(value: String): String {
    require(value.matches(Regex("""https?://[^\s"\\]+"""))) { "API base URL must be an http(s) URL, got '$value'" }
    return "\"$value\""
}

android {
    namespace = "com.compagnon.courses.android"

    defaultConfig {
        applicationId = "com.compagnon.courses"
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        debug {
            buildConfigField("String", "API_BASE_URL", buildConfigString(debugApiBaseUrl))
        }
        release {
            buildConfigField("String", "API_BASE_URL", buildConfigString(releaseApiBaseUrl))
        }
    }

    buildFeatures {
        buildConfig = true
    }

    androidResources {
        // French is the default (unqualified) language, English lives in values-en (see res/resources.properties).
        // Drops the other languages shipped by libraries and publishes both locales to the per-app language
        // settings (Android 13+).
        localeFilters += listOf("fr", "en")
        generateLocaleConfig = true
    }
}

dependencies {
    implementation(projects.shared.umbrella)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.materialIconsCore)
    implementation(libs.androidx.compose.uiToolingPreview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtimeCompose)
    implementation(libs.androidx.lifecycle.viewmodelCompose)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    // ViewModels scoped to a Navigation 3 entry (cleared when the entry leaves the back stack).
    implementation(libs.androidx.lifecycle.viewmodelNavigation3)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.koin.compose.viewmodel)
    debugImplementation(libs.androidx.compose.uiTooling)

    testImplementation(libs.junit4)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.uiTestJunit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.extJunit)
    debugImplementation(libs.androidx.compose.uiTestManifest)
}
