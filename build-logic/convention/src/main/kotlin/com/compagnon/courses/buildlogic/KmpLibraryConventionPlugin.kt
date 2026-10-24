package com.compagnon.courses.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * `compagnon.kmp.library`: KMP library targeting jvm, Android, iosArm64 and iosSimulatorArm64.
 *
 * The Android namespace defaults to the project path (see [defaultAndroidNamespace]); override it with
 * `kotlin { android { namespace = "…" } }`.
 */
class KmpLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.configureKotlinMultiplatform(withJvm = true)
    }
}
