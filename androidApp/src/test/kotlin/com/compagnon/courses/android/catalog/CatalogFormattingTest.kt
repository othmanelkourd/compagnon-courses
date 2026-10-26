package com.compagnon.courses.android.catalog

import com.compagnon.courses.android.home.formatHomeDate
import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogFormattingTest {

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

    @Test
    fun theHomeDateIsTheCapitalizedDayAndMonth() {
        val sunday = LocalDate.of(2026, 10, 25)

        assertEquals("Dimanche 25 octobre", formatHomeDate(sunday, "EEEE d MMMM", Locale.FRANCE))
        assertEquals("Sunday, October 25", formatHomeDate(sunday, "EEEE, MMMM d", Locale.US))
    }
}
