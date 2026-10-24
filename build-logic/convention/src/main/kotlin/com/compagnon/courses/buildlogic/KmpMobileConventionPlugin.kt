package com.compagnon.courses.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * `compagnon.kmp.mobile`: same as `compagnon.kmp.library` without the JVM target
 * (Android, iosArm64, iosSimulatorArm64). Used by `:shared:umbrella`.
 */
class KmpMobileConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.configureKotlinMultiplatform(withJvm = false)
    }
}
