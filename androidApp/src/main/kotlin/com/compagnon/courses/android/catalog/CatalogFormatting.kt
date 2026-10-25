package com.compagnon.courses.android.catalog

import java.text.NumberFormat
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Formats the catalog's RFC 3339 publication timestamp as a long localized date in [zone]
 * (`24 octobre 2026` in French). An unparsable value is returned unchanged rather than hidden.
 */
internal fun formatPublishedAt(publishedAt: String, locale: Locale, zone: ZoneId): String =
    try {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)
            .withLocale(locale)
            .withZone(zone)
            .format(OffsetDateTime.parse(publishedAt))
    } catch (_: DateTimeParseException) {
        publishedAt
    }

/**
 * Formats the RFC 3339 time of the last catalog sync as a medium date and a short time in [zone]
 * (`24 oct. 2026, 10:30` in French). An unparsable value is returned unchanged.
 */
internal fun formatSyncedAt(syncedAt: String, locale: Locale, zone: ZoneId): String =
    try {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withLocale(locale)
            .withZone(zone)
            .format(OffsetDateTime.parse(syncedAt))
    } catch (_: DateTimeParseException) {
        syncedAt
    }

/** Formats a count with the locale's digit grouping (`1 234` in French, `1,234` in English). */
internal fun formatCount(count: Int, locale: Locale): String =
    NumberFormat.getIntegerInstance(locale).format(count)
