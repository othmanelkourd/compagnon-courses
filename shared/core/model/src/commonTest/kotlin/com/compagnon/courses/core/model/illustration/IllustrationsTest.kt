package com.compagnon.courses.core.model.illustration

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IllustrationsTest {
    @Test
    fun aProductTakesItsOwnImageThenItsAislesThenTheFallback() {
        assertEquals("red_apple", Illustrations.forProduct("pommes", "fruits-et-legumes"))
        assertEquals("canned_food", Illustrations.forProduct("sel", "epicerie-salee"))
        assertEquals(Illustrations.Screen.FALLBACK, Illustrations.forProduct("inconnu", "rayon-inconnu"))
        assertEquals(Illustrations.Screen.FALLBACK, Illustrations.forProduct("inconnu", null))
    }

    @Test
    fun everyKeyHasAnImage() {
        val screens = listOf(
            Illustrations.Screen.FALLBACK, Illustrations.Screen.FREE_TEXT, Illustrations.Screen.SESSION_DONE,
            Illustrations.Screen.LIST_EMPTY, Illustrations.Screen.PET_DOG, Illustrations.Screen.AVOID_GLUTEN,
        )
        screens.forEach { assertTrue(it in Illustrations.ALL, it) }
        assertTrue(Illustrations.forCategory("bebe") in Illustrations.ALL)
    }
}
