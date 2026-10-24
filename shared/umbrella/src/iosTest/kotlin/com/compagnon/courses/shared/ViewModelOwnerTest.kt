package com.compagnon.courses.shared

import androidx.lifecycle.ViewModel
import kotlin.test.Test
import kotlin.test.assertEquals

class ViewModelOwnerTest {

    private class TrackingViewModel : ViewModel() {
        var clearedCount = 0
            private set

        override fun onCleared() {
            clearedCount++
        }
    }

    @Test
    fun clearClearsEveryOwnedViewModelOnce() {
        val owner = ViewModelOwner()
        val first = TrackingViewModel()
        val second = TrackingViewModel()

        owner.own(first)
        owner.own(second)
        owner.own(first)
        owner.clear()

        assertEquals(1, first.clearedCount)
        assertEquals(1, second.clearedCount)
    }

    @Test
    fun clearIsIdempotentAndTheOwnerIsReusable() {
        val owner = ViewModelOwner()
        val first = TrackingViewModel()
        owner.own(first)
        owner.clear()
        owner.clear()

        val next = TrackingViewModel()
        owner.own(next)
        owner.clear()

        assertEquals(1, first.clearedCount)
        assertEquals(1, next.clearedCount)
    }

    @Test
    fun unownedViewModelsAreNotCleared() {
        val owner = ViewModelOwner()
        val other = TrackingViewModel()
        owner.own(TrackingViewModel())
        owner.clear()
        assertEquals(0, other.clearedCount)
    }
}
