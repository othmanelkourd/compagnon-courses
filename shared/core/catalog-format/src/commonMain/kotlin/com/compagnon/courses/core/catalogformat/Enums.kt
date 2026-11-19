package com.compagnon.courses.core.catalogformat

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class DimensionDto {
    @SerialName("mass")
    MASS,

    @SerialName("volume")
    VOLUME,

    @SerialName("count")
    COUNT,

    @SerialName("package")
    PACKAGE,
}

@Serializable
enum class CycleClassDto {
    @SerialName("short")
    SHORT,

    @SerialName("medium")
    MEDIUM,

    @SerialName("long")
    LONG,
}
