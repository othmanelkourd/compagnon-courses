package com.compagnon.courses.buildlogic

import dev.detekt.gradle.extensions.DetektExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * Lint of every Kotlin module: detekt 2 on top of its default rules (`config/detekt/detekt.yml`), with the ktlint
 * formatting rules (wrapper, settings in `.editorconfig`).
 *
 * - `./gradlew detekt`: analyses `src/` (every source set) against the module baseline (`detekt-baseline.xml`).
 * - `./gradlew detekt -Pdetekt.autoCorrect`: also fixes what ktlint can fix.
 * - `./gradlew detektBaseline`: accepts the current findings of the module.
 */
internal fun Project.configureQuality() {
    pluginManager.apply("dev.detekt")

    extensions.configure<DetektExtension> {
        buildUponDefaultConfig.set(true)
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
        source.setFrom(files("src"))
        baseline.set(file("detekt-baseline.xml"))
        basePath.set(rootProject.layout.projectDirectory)
        parallel.set(true)
        autoCorrect.set(providers.gradleProperty("detekt.autoCorrect").isPresent)
    }

    dependencies {
        add("detektPlugins", libs.library("detekt-rules-ktlintWrapper"))
    }
}
