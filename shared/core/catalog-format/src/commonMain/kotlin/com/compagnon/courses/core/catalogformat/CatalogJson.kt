package com.compagnon.courses.core.catalogformat

import kotlinx.serialization.json.Json

/**
 * JSON configuration of the catalog files: unknown keys are ignored (forward compatibility),
 * null values are omitted, and default values are always written.
 */
val CatalogJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}
