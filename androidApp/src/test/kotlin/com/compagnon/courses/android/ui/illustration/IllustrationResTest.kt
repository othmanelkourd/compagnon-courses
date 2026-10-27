package com.compagnon.courses.android.ui.illustration

import com.compagnon.courses.core.model.illustration.Illustrations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IllustrationResTest {

    @Test
    fun everySharedKeyHasItsDrawable() {
        val missing = Illustrations.ALL.filter { illustrationRes(it) == null }

        assertTrue("no drawable for $missing", missing.isEmpty())
    }

    @Test
    fun eachKeyHasItsOwnDrawable() {
        assertEquals(Illustrations.ALL.size, Illustrations.ALL.mapNotNull(::illustrationRes).toSet().size)
    }

    @Test
    fun theScreenKeysAndTheFallbackAreBundled() {
        val screen = listOf(
            Illustrations.Screen.FALLBACK,
            Illustrations.Screen.FREE_TEXT,
            Illustrations.Screen.HOME_SESSION,
            Illustrations.Screen.HOME_LIST,
            Illustrations.Screen.HOME_HOUSEHOLD,
            Illustrations.Screen.SESSION_DONE,
            Illustrations.Screen.LIST_EMPTY,
            Illustrations.Screen.DECOR_APPLE,
            Illustrations.Screen.DECOR_BAGUETTE,
            Illustrations.Screen.DECOR_LEAFY,
            Illustrations.Screen.DECOR_MILK,
            Illustrations.Screen.DECOR_CARROT,
            Illustrations.Screen.DECOR_CHEESE,
        )

        assertTrue(screen.all { it in Illustrations.ALL && illustrationRes(it) != null })
    }

    @Test
    fun anUnknownKeyHasNoDrawable() {
        // [Illustration] then shows the fallback image.
        assertNull(illustrationRes("not_an_illustration"))
        assertNull(illustrationRes(null))
    }
}
