package com.compagnon.courses.core.testing

import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/** A clock that only moves when told to. */
class TestClock(var now: Instant = DEFAULT_NOW) : Clock {
    override fun now(): Instant = now

    fun advance(duration: Duration) {
        now += duration
    }

    companion object {
        val DEFAULT_NOW: Instant = Instant.parse("2026-10-24T10:00:00Z")
    }
}
