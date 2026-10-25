package com.compagnon.courses.core.data

import com.compagnon.courses.core.model.AddedVia
import com.compagnon.courses.engine.AnswerKind
import com.compagnon.courses.engine.AnswerSubject
import com.compagnon.courses.engine.SessionEffect
import com.compagnon.courses.engine.SessionEvent
import com.compagnon.courses.engine.SessionMode
import com.compagnon.courses.engine.SessionReducer
import com.compagnon.courses.engine.SessionState
import com.compagnon.courses.engine.Transition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest

class QuestionRepositoryTest {
    private val reducer = SessionReducer()

    @Test
    fun noPlanningWithoutACatalog() = runTest {
        val data = TestData(StandardTestDispatcher(testScheduler))
        assertNull(data.questions.planningSetup(SessionMode.QUICK, data.clock.now, seed = 1))
    }

    @Test
    fun planningUsesTheCatalogTheHouseholdAndTheStats() = runTest {
        val data = TestData(StandardTestDispatcher(testScheduler))
        data.syncSeed()

        val setup = assertNotNull(data.questions.planningSetup(SessionMode.QUICK, data.clock.now, seed = 42))

        assertEquals(312, setup.input.products.size)
        assertEquals(2, setup.input.context.persons)
        assertEquals(7, setup.input.context.horizonDays)
        assertEquals(42, setup.input.seed)
        assertEquals(1, setup.catalogVersion)
        assertEquals("fr-FR", setup.locale)
    }

    @Test
    fun startedSessionsAreOpenWithTheirEvents() = runTest {
        val data = TestData(StandardTestDispatcher(testScheduler))
        data.syncSeed()
        val setup = assertNotNull(data.questions.planningSetup(SessionMode.QUICK, data.clock.now, seed = 7))
        val listId = data.lists.activeListId()

        val id = data.questions.startSession(setup, listId)
        data.questions.recordEvent(id, "meta:HORIZON_DAYS", """{"a":1}""")
        data.questions.recordEvent(id, "meta:PERSONS", """{"a":2}""")

        val open = assertNotNull(data.questions.openSession())
        assertEquals(id, open.id)
        assertEquals(listId, open.listId)
        assertEquals(7, open.seed)
        assertEquals(data.clock.now, open.plannedAt)
        assertEquals(listOf(1, 2), open.events.map { it.index })
        assertEquals(listOf("meta:HORIZON_DAYS", "meta:PERSONS"), open.events.map { it.subject })
    }

    @Test
    fun commitWritesTheListTheAnswersAndTheStatsTogether() = runTest {
        val data = TestData(StandardTestDispatcher(testScheduler))
        data.syncSeed()
        val setup = assertNotNull(data.questions.planningSetup(SessionMode.QUICK, data.clock.now, seed = 7))
        val listId = data.lists.activeListId()
        val sessionId = data.questions.startSession(setup, listId)
        val answers = mutableListOf<AnswerRecord>()
        var state: SessionState = SessionState.Idle
        fun send(event: SessionEvent) {
            val transition: Transition = reducer.reduce(state, event)
            state = transition.state
            transition.effects.forEach { effect ->
                when (effect) {
                    is SessionEffect.PersistAnswer -> answers += AnswerRecord(effect.answer, false, data.clock.now)
                    is SessionEffect.RetractAnswer -> answers += AnswerRecord(effect.answer, true, data.clock.now)
                    else -> Unit
                }
            }
        }

        send(SessionEvent.Start(sessionId, setup.input, listId))
        while (state is SessionState.Meta) send(SessionEvent.MetaAnswered((state as SessionState.Meta).question.key, (state as SessionState.Meta).question.prefilled))
        val suggested = assertIs<SessionState.Suggested>(state)
        send(SessionEvent.SuggestedConfirmed(kept = suggested.items.filter { it.preChecked }.map { it.productId }.toSet()))
        var cards = 0
        var noProduct: com.compagnon.courses.core.model.ProductId? = null
        while (state !is SessionState.Review) {
            when (val current = state) {
                is SessionState.Asking -> {
                    if (cards++ % 2 == 0) {
                        send(SessionEvent.Yes(current.card.item.proposal.quantity))
                    } else {
                        noProduct = current.card.item.productId
                        send(SessionEvent.No)
                    }
                }
                is SessionState.CategoryGate -> send(SessionEvent.Gate(yes = false))
                is SessionState.GateItems -> send(SessionEvent.SuggestedConfirmed(emptySet()))
                else -> error("Unexpected state $current")
            }
        }
        // An undone answer leaves a compensation.
        val review = state as SessionState.Review
        send(SessionEvent.Undo)
        val redo = state
        if (redo is SessionState.Asking) send(SessionEvent.Yes(redo.card.item.proposal.quantity)) else send(SessionEvent.Finish)
        while (state !is SessionState.Review) send(SessionEvent.Gate(yes = false))
        send(SessionEvent.Finish)
        val committing = assertIs<SessionState.Committing>(state)
        assertTrue(review.drafts.isNotEmpty())

        val written = data.questions.commit(
            sessionId = sessionId,
            session = committing.session,
            listId = listId,
            drafts = committing.drafts,
            sessionStats = com.compagnon.courses.engine.SessionStats(asked = 5, answered = 5, itemsAdded = committing.drafts.size, abandoned = false),
            answers = answers,
        )

        assertEquals(listId, written)
        val items = data.lists.activeList().items
        assertEquals(committing.drafts.map { it.productId }.toSet(), items.mapNotNull { it.productId }.toSet())
        assertTrue(items.all { it.addedVia == AddedVia.QUESTION_FLOW })
        committing.drafts.forEach { draft ->
            assertEquals(draft.quantity, items.first { it.productId == draft.productId }.quantity)
        }
        val rows = data.database.questionQueries.selectAnswers(sessionId.value).executeAsList()
        assertEquals(answers.size, rows.size)
        assertTrue(rows.any { it.compensates == 1L })
        val session = data.database.questionQueries.selectSession(sessionId.value).executeAsOne()
        assertNotNull(session.completed_at)
        assertEquals(committing.drafts.size.toLong(), session.items_added)
        assertNull(data.questions.openSession())
        val yes = committing.session.answers.first { it.kind == AnswerKind.YES && it.subject is AnswerSubject.Item }
        val yesId = (yes.subject as AnswerSubject.Item).productId
        assertEquals(yes.quantity, data.database.householdQueries.selectPref(yesId.value).executeAsOne().usual_qty_milli?.let { com.compagnon.courses.core.model.Quantity(it, yes.quantity!!.unit) })
        noProduct?.let { id ->
            if (committing.session.answers.any { it.kind == AnswerKind.NO && it.subject == AnswerSubject.Item(id) }) {
                val stats = data.database.householdQueries.selectStats(id.value).executeAsOne()
                assertEquals(1.0, stats.consecutive_no)
                assertTrue(assertNotNull(stats.next_due_at) >= (data.clock.now + 3.days).toEpochMilliseconds())
            }
        }
    }

    @Test
    fun abandonKeepsTheAnswersButAddsNothing() = runTest {
        val data = TestData(StandardTestDispatcher(testScheduler))
        data.syncSeed()
        val setup = assertNotNull(data.questions.planningSetup(SessionMode.QUICK, data.clock.now, seed = 7))
        val sessionId = data.questions.startSession(setup, listId = null)
        var transition = reducer.reduce(SessionState.Idle, SessionEvent.Start(sessionId, setup.input))
        val answers = mutableListOf<AnswerRecord>()
        while (transition.state is SessionState.Meta) {
            val meta = transition.state as SessionState.Meta
            transition = reducer.reduce(meta, SessionEvent.MetaAnswered(meta.question.key, meta.question.prefilled))
            transition.effects.filterIsInstance<SessionEffect.PersistAnswer>().forEach { answers += AnswerRecord(it.answer, false, data.clock.now) }
        }
        val abort = reducer.reduce(transition.state, SessionEvent.Abort)
        val stats = assertIs<SessionEffect.Abandon>(abort.effects.single()).stats

        data.questions.abandon(sessionId, (transition.state as SessionState.Interactive).session, stats, answers)

        assertEquals(emptyList(), data.lists.activeList().items)
        assertEquals(3, data.database.questionQueries.selectAnswers(sessionId.value).executeAsList().size)
        assertNotNull(data.database.questionQueries.selectSession(sessionId.value).executeAsOne().abandoned_at)
        assertNull(data.questions.openSession())
    }
}
