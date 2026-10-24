package com.compagnon.courses.android.catalog

import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogFormattingTest {

    @Test
    fun formatsPublicationAsLongFrenchDate() {
        assertEquals("24 octobre 2026", formatPublishedAt("2026-10-24T08:30:00Z", Locale.FRANCE, ZoneOffset.UTC))
    }

    @Test
    fun formatsPublicationAsLongEnglishDate() {
        assertEquals("October 24, 2026", formatPublishedAt("2026-10-24T08:30:00Z", Locale.US, ZoneOffset.UTC))
    }

    @Test
    fun convertsPublicationToTheUserTimeZone() {
        val paris = ZoneId.of("Europe/Paris")

        assertEquals("25 octobre 2026", formatPublishedAt("2026-10-24T23:30:00Z", Locale.FRANCE, paris))
    }

    @Test
    fun acceptsOffsetsAndFractionalSeconds() {
        assertEquals(
            "24 octobre 2026",
            formatPublishedAt("2026-10-24T08:30:00.123+02:00", Locale.FRANCE, ZoneOffset.UTC),
        )
    }

    @Test
    fun returnsAnUnparsableTimestampUnchanged() {
        assertEquals("2026-10-24", formatPublishedAt("2026-10-24", Locale.FRANCE, ZoneOffset.UTC))
    }

    @Test
    fun groupsDigitsOfCountsByLocale() {
        assertEquals("1,234", formatCount(1234, Locale.US))
        assertEquals("18", formatCount(18, Locale.FRANCE))

        val french = formatCount(1234, Locale.FRANCE)
        // French groups with a (narrow) no-break space; its exact code point depends on the CLDR version.
        assertEquals(5, french.length)
        assertTrue(Character.isSpaceChar(french[1]))
        assertEquals("1234", french.filter(Char::isDigit))
    }
}
