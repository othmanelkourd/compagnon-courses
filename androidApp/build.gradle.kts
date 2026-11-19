plugins {
    alias(libs.plugins.compagnon.android.application)
    // Navigation 3 keys are @Serializable (saved back stack).
    alias(libs.plugins.kotlin.serialization)
}

// The upload key of Google Play (Play App Signing signs the published app with its own key), from
// ~/.gradle/gradle.properties, never from the repository: ocimio.upload.storeFile, ocimio.upload.storePassword,
// ocimio.upload.keyAlias, ocimio.upload.keyPassword (docs/PUBLICATION.md). Without them, the release is signed with
// the debug key: it installs on a test device, Google Play refuses it.
val uploadKey: Map<String, String>? = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
    .associateWith { providers.gradleProperty("ocimio.upload.$it").orNull }
    .takeIf { values -> values.values.all { it != null } }
    ?.mapValues { it.value.orEmpty() }

android {
    namespace = "com.compagnon.courses.android"

    defaultConfig {
        applicationId = "com.ocimio.courses"
        // Bump versionCode at every upload to the stores (CURRENT_PROJECT_VERSION on iOS); versionName is the
        // version users see (MARKETING_VERSION on iOS).
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        if (uploadKey != null) {
            create("upload") {
                storeFile = file(uploadKey.getValue("storeFile"))
                storePassword = uploadKey.getValue("storePassword")
                keyAlias = uploadKey.getValue("keyAlias")
                keyPassword = uploadKey.getValue("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("upload") ?: signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    lint {
        // Any new warning fails `lintDebug`; the existing ones are listed in lint-baseline.xml.
        abortOnError = true
        warningsAsErrors = true
        baseline = file("lint-baseline.xml")
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
