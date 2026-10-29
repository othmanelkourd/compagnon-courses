package com.compagnon.courses.buildlogic

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Shared setup of the KMP conventions: Android (AGP 9 KMP library plugin) + iosArm64 +
 * iosSimulatorArm64, plus a JVM target when [withJvm] is true.
 */
internal fun Project.configureKotlinMultiplatform(withJvm: Boolean) {
    with(pluginManager) {
        apply("org.jetbrains.kotlin.multiplatform")
        apply("com.android.kotlin.multiplatform.library")
    }
    configureQuality()

    val kotlin = extensions.getByType<KotlinMultiplatformExtension>()
    kotlin.jvmToolchain(jvmToolchainVersion)

    if (withJvm) {
        kotlin.jvm()
    }

    // `kotlin { android { … } }` — the namespace can be overridden by the module build file.
    (kotlin as ExtensionAware).extensions.configure<KotlinMultiplatformAndroidLibraryTarget>("android") {
        namespace = defaultAndroidNamespace()
        compileSdk = libs.versionInt("android-compileSdk")
        minSdk = libs.versionInt("android-minSdk")
        // commonTest also runs on the Android host JVM (task `testAndroidHostTest`).
        withHostTest {}
    }

    kotlin.iosArm64()
    kotlin.iosSimulatorArm64()

    kotlin.compilerOptions {
        // expect/actual classes are still Beta; opt in once for the whole codebase.
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    kotlin.sourceSets.getByName("commonTest").dependencies {
        implementation(libs.library("kotlin-test"))
        implementation(libs.library("kotlinx-coroutines-test"))
    }

    // JVM tests run on the JUnit Platform (kotlin-test resolves to kotlin-test-junit5).
    tasks.withType<Test>().named { it == "jvmTest" }.configureEach {
        useJUnitPlatform()
    }
}
