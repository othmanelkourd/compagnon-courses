package com.compagnon.courses.feature.questionflow

import com.compagnon.courses.core.model.ProductId
import com.compagnon.courses.core.model.Quantity
import com.compagnon.courses.core.model.UnitCode
import com.compagnon.courses.engine.MetaKey
import com.compagnon.courses.engine.SessionEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SessionLogTest {

    @Test
    fun persistedEventsRoundTrip() {
        val events = listOf(
            SessionEvent.MetaAnswered(MetaKey.PERSONS, 3),
            SessionEvent.SuggestedConfirmed(
                kept = setOf(ProductId("b"), ProductId("a")),
                edits = mapOf(ProductId("a") to Quantity(1500, UnitCode.KILOGRAM)),
            ),
            SessionEvent.Yes(Quantity.of(2, UnitCode("bottle"))),
            SessionEvent.No,
            SessionEvent.LittleLeft,
            SessionEvent.HaveEnough(30),
            SessionEvent.Later,
            SessionEvent.NotThisTime,
            SessionEvent.Never,
            SessionEvent.Gate(yes = true),
            SessionEvent.AddOther(ProductId("c"), Quantity.of(1, UnitCode.PIECE)),
            SessionEvent.Undo,
            SessionEvent.Finish,
            SessionEvent.Resume,
        )

        events.forEach { event ->
            val payload = SessionLog.encode(event)
            assertEquals(event, SessionLog.decode(checkNotNull(payload)), payload)
        }
    }

    @Test
    fun transientEventsAreNotPersistedAndGarbageIsIgnored() {
        assertNull(SessionLog.encode(SessionEvent.Abort))
        assertNull(SessionLog.encode(SessionEvent.Pause))
        assertNull(SessionLog.decode("{\"type\":\"teleport\"}"))
        assertNull(SessionLog.decode("not json"))
        assertNull(SessionLog.decode("{\"type\":\"meta\",\"key\":\"MOOD\",\"value\":1}"))
    }
}
