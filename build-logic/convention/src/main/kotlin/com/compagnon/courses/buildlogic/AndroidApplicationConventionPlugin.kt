package com.compagnon.courses.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidExtension

/**
 * `compagnon.android.application`: Android application with Compose.
 *
 * AGP 9 compiles Kotlin itself (built-in Kotlin), so `org.jetbrains.kotlin.android` is not applied;
 * only the Compose compiler plugin is added.
 */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("com.android.application")
                apply("org.jetbrains.kotlin.plugin.compose")
            }

            val javaVersion = JavaVersion.toVersion(jvmToolchainVersion)
            extensions.configure<ApplicationExtension> {
                compileSdk = libs.versionInt("android-compileSdk")
                defaultConfig {
                    minSdk = libs.versionInt("android-minSdk")
                    targetSdk = libs.versionInt("android-targetSdk")
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                }
                compileOptions {
                    sourceCompatibility = javaVersion
                    targetCompatibility = javaVersion
                }
                buildFeatures {
                    compose = true
                }
            }

            extensions.configure<KotlinAndroidExtension> {
                jvmToolchain(jvmToolchainVersion)
            }
        }
    }
}
