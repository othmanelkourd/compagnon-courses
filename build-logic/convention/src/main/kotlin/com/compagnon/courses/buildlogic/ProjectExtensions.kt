package com.compagnon.courses.buildlogic

import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.getByType

/** Root package of every module; the Android namespaces are derived from it. */
internal const val BASE_PACKAGE: String = "com.compagnon.courses"

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.versionInt(alias: String): Int =
    findVersion(alias)
        .orElseThrow { IllegalStateException("Version '$alias' is missing from gradle/libs.versions.toml") }
        .requiredVersion
        .toInt()

internal fun VersionCatalog.library(alias: String): Provider<MinimalExternalModuleDependency> =
    findLibrary(alias)
        .orElseThrow { IllegalStateException("Library '$alias' is missing from gradle/libs.versions.toml") }

internal val Project.jvmToolchainVersion: Int
    get() = libs.versionInt("jvmToolchain")

/**
 * Android namespace derived from the Gradle path:
 * `:shared:core:model` -> `com.compagnon.courses.shared.core.model`,
 * `:contract:api-contract` -> `com.compagnon.courses.contract.apicontract`.
 */
internal fun Project.defaultAndroidNamespace(): String =
    path.split(':')
        .filter { it.isNotEmpty() }
        .joinToString(separator = ".", prefix = "$BASE_PACKAGE.") { segment ->
            segment.lowercase().filter { it.isLetterOrDigit() }
        }
