package com.compagnon.courses.engine

import com.compagnon.courses.core.model.ListId
import com.compagnon.courses.core.model.Product
import com.compagnon.courses.core.model.Quantity
import com.compagnon.courses.engine.Fixtures.CLEANING
import com.compagnon.courses.engine.Fixtures.CUPBOARD
import com.compagnon.courses.engine.Fixtures.DRINKS
import com.compagnon.courses.engine.Fixtures.KILOGRAM
import com.compagnon.courses.engine.Fixtures.LITER
import com.compagnon.courses.engine.Fixtures.context
import com.compagnon.courses.engine.Fixtures.input
import com.compagnon.courses.engine.Fixtures.product
import com.compagnon.courses.engine.SessionEvent.Abort
import com.compagnon.courses.engine.SessionEvent.AddOther
import com.compagnon.courses.engine.SessionEvent.CommitFailed
import com.compagnon.courses.engine.SessionEvent.CommitSucceeded
import com.compagnon.courses.engine.SessionEvent.Finish
import com.compagnon.courses.engine.SessionEvent.Gate
import com.compagnon.courses.engine.SessionEvent.HaveEnough
import com.compagnon.courses.engine.SessionEvent.Later
import com.compagnon.courses.engine.SessionEvent.LittleLeft
import com.compagnon.courses.engine.SessionEvent.MetaAnswered
import com.compagnon.courses.engine.SessionEvent.Never
import com.compagnon.courses.engine.SessionEvent.No
import com.compagnon.courses.engine.SessionEvent.NotThisTime
import com.compagnon.courses.engine.SessionEvent.Pause
import com.compagnon.courses.engine.SessionEvent.Resume
import com.compagnon.courses.engine.SessionEvent.Start
import com.compagnon.courses.engine.SessionEvent.SuggestedConfirmed
import com.compagnon.courses.engine.SessionEvent.Undo
import com.compagnon.courses.engine.SessionEvent.Yes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SessionReducerTest {
    private val reducer = SessionReducer(QuestionPlanner(Fixtures.BY_SCORE))
    private val sessionId = SessionId("session-1")
    private val listId = ListId("list-1")

    private val milk = product("lait", penetration = 0.9)
    private val yogurt = product("yaourt", penetration = 0.5)
    private val water = product("eau", penetration = 0.45, category = DRINKS)
    private val bleach = product("javel", penetration = 0.25, category = CLEANING, zone = CUPBOARD)
    private val sponge = product("eponge", penetration = 0.2, category = CLEANING, zone = CUPBOARD)
    private val everything = listOf(milk, yogurt, water, bleach, sponge)

    private fun liters(text: String) = Quantity.parse(text, LITER)

    private fun start(products: List<Product> = everything, meta: List<MetaKey> = MetaKey.entries): Transition =
        reducer.reduce(SessionState.Idle, Start(sessionId, input(products, context = context(meta = meta)), listId))

    /** Applies [events] in order and returns the last state with every effect emitted on the way. */
    private fun Transition.then(vararg events: SessionEvent): Transition = events.fold(this) { acc, event ->
        val next = reducer.reduce(acc.state, event)
        Transition(next.state, acc.effects + next.effects)
    }

    private val Transition.answers: List<RecordedAnswer>
        get() = effects.filterIsInstance<SessionEffect.PersistAnswer>().map { it.answer }

    @Test
    fun walksThroughAWholeSession() {
        val started = start()
        val firstMeta = assertIs<SessionState.Meta>(started.state)
        assertEquals(MetaQuestion(MetaKey.HORIZON_DAYS, 7), firstMeta.question)
        assertEquals(Progress(answered = 0, total = 7), firstMeta.progress)

        val suggested = started.then(
            MetaAnswered(MetaKey.HORIZON_DAYS, 7),
            MetaAnswered(MetaKey.PERSONS, 4),
            MetaAnswered(MetaKey.GUESTS, 0),
        )
        val t1 = assertIs<SessionState.Suggested>(suggested.state)
        // Re-planned with 4 persons: 1 L × 4 / 2.
        assertEquals(liters("2"), t1.items.single().proposal.quantity)
        assertEquals(Progress(3, 7), t1.progress)

        val gate = suggested.then(SuggestedConfirmed(kept = setOf(milk.id)), Yes(liters("1")), No)
        val categoryGate = assertIs<SessionState.CategoryGate>(gate.state)
        assertEquals(CLEANING, categoryGate.gate.category)

        val review = gate.then(
            Gate(yes = true),
            SuggestedConfirmed(kept = setOf(sponge.id), edits = mapOf(sponge.id to liters("3"))),
        )
        val recap = assertIs<SessionState.Review>(review.state)
        assertEquals(
            listOf(
                Triple("lait", liters("2"), DraftSource.SUGGESTED),
                Triple("yaourt", liters("1"), DraftSource.CARD),
                Triple("eponge", liters("3"), DraftSource.GATE),
            ),
            recap.drafts.map { Triple(it.product.slug, it.quantity, it.source) },
        )
        assertEquals(Progress(7, 7), recap.progress)

        val committing = review.then(Finish)
        assertIs<SessionState.Committing>(committing.state)
        val commit = committing.effects.filterIsInstance<SessionEffect.Commit>().single()
        assertEquals(SessionStats(asked = 7, answered = 7, itemsAdded = 3, abandoned = false), commit.stats)
        assertEquals(listId, commit.listId)

        val done = committing.then(CommitSucceeded(listId)).state
        assertEquals(SessionState.Completed(sessionId, listId, recap.drafts, commit.stats), done)

        assertEquals((1..8).toList(), committing.answers.map { it.seq })
        assertEquals(
            listOf(
                AnswerKind.META, AnswerKind.META, AnswerKind.META,
                AnswerKind.YES, AnswerKind.YES, AnswerKind.NO, AnswerKind.GATE_YES, AnswerKind.YES,
            ),
            committing.answers.map { it.kind },
        )
        assertEquals(listOf(7, 4, 0), committing.answers.take(3).map { it.value })
        assertEquals(AnswerSubject.Gate(CLEANING.id), committing.answers[6].subject)
        assertEquals(0.5, committing.answers[4].score!!, 1e-6)
    }

    @Test
    fun undoRestoresThePreviousStateAndCompensates() {
        val onWater = start(meta = emptyList()).then(SuggestedConfirmed(setOf(milk.id)), Yes(liters("1")))
        assertEquals("eau", assertIs<SessionState.Asking>(onWater.state).card.item.product.slug)

        val undone = reducer.reduce(onWater.state, Undo)
        val backOnYogurt = assertIs<SessionState.Asking>(undone.state)
        assertEquals("yaourt", backOnYogurt.card.item.product.slug)
        assertEquals(listOf("lait"), backOnYogurt.session.drafts.map { it.product.slug })
        val retracted = undone.effects.filterIsInstance<SessionEffect.RetractAnswer>().single().answer
        assertEquals(2, retracted.seq)
        assertEquals(AnswerKind.YES, retracted.kind)
        assertTrue(SessionEffect.Haptic(HapticKind.UNDONE) in undone.effects)

        // Sequence numbers are never reused.
        val answeredAgain = Transition(undone.state).then(No)
        assertEquals(3, answeredAgain.answers.single().seq)

        // Undo twice goes back to the T1 screen and retracts its answer.
        val twice = Transition(undone.state).then(Undo)
        assertIs<SessionState.Suggested>(twice.state)
        assertEquals(listOf(1), twice.effects.filterIsInstance<SessionEffect.RetractAnswer>().map { it.answer.seq })
        // Nothing to undo before the first question.
        assertEquals(Transition(twice.state), reducer.reduce(twice.state, Undo))
    }

    @Test
    fun undoFromTheGateItemsReopensTheGate() {
        val gateItems = start(listOf(bleach, sponge), meta = emptyList()).then(Gate(yes = true))
        assertIs<SessionState.GateItems>(gateItems.state)
        val undone = gateItems.then(Undo)
        assertIs<SessionState.CategoryGate>(undone.state)
        val retracted = undone.effects.filterIsInstance<SessionEffect.RetractAnswer>().single().answer
        assertEquals(AnswerKind.GATE_YES, retracted.kind)
    }

    @Test
    fun pauseAndResume() {
        val asking = start(meta = emptyList()).then(SuggestedConfirmed(setOf(milk.id))).state
        val paused = reducer.reduce(asking, Pause).state
        assertEquals(SessionState.Paused(asking as SessionState.Interactive), paused)
        assertEquals(sessionId, (paused as SessionState.Paused).sessionId)
        assertSame(paused, reducer.reduce(paused, Yes(liters("1"))).state)
        assertEquals(asking, reducer.reduce(paused, Resume).state)
    }

    @Test
    fun abortKeepsTheAnswersButWritesNothing() {
        val asking = start(meta = emptyList()).then(SuggestedConfirmed(setOf(milk.id)))
        val aborted = asking.then(Abort)
        val stats = SessionStats(asked = 2, answered = 1, itemsAdded = 0, abandoned = true)
        assertEquals(SessionState.Completed(sessionId, listId, emptyList(), stats), aborted.state)
        assertEquals(SessionEffect.Abandon(sessionId, stats), aborted.effects.last())
        // Also from a pause.
        val paused = asking.then(Pause, Abort)
        assertIs<SessionState.Completed>(paused.state)
    }

    @Test
    fun laterAsksAgainAtTheEndOnce() {
        val cards = start(listOf(yogurt, water), meta = emptyList())
        assertEquals("yaourt", (cards.state as SessionState.Asking).card.item.product.slug)

        val postponed = cards.then(Later)
        val onWater = assertIs<SessionState.Asking>(postponed.state)
        assertEquals("eau", onWater.card.item.product.slug)
        assertEquals(Progress(0, 2), onWater.progress)

        val again = postponed.then(No)
        assertEquals("yaourt", assertIs<SessionState.Asking>(again.state).card.item.product.slug)

        val dropped = again.then(Later)
        val review = assertIs<SessionState.Review>(dropped.state)
        assertEquals(Progress(2, 2), review.progress)
        assertEquals(listOf(AnswerKind.LATER, AnswerKind.NO, AnswerKind.LATER), dropped.answers.map { it.kind })
    }

    @Test
    fun recordsNuancedAnswers() {
        val products = (1..4).map { product("p$it", penetration = 0.4 + it * 0.05) }
        val done = start(products, meta = emptyList())
            .then(Never, HaveEnough(0), HaveEnough(30), LittleLeft, NotThisTime)
        assertIs<SessionState.Review>(done.state)
        assertEquals(
            listOf(AnswerKind.NEVER, AnswerKind.HAVE_ENOUGH, AnswerKind.LITTLE_LEFT, AnswerKind.NOT_THIS_TIME),
            done.answers.map { it.kind },
        )
        assertEquals(30, done.answers[1].value)
        assertEquals(
            listOf("prod-p4", "prod-p3", "prod-p2", "prod-p1"),
            done.answers.map { (it.subject as AnswerSubject.Item).productId.value },
        )
        assertTrue(assertIs<SessionState.Review>(done.state).drafts.isEmpty())
    }

    @Test
    fun aGateAnsweredNoSkipsItsCategory() {
        val done = start(listOf(bleach, sponge, yogurt), meta = emptyList()).then(No, Gate(yes = false))
        assertIs<SessionState.Review>(done.state)
        assertEquals(AnswerSubject.Gate(CLEANING.id), done.answers.last().subject)
        assertEquals(AnswerKind.GATE_NO, done.answers.last().kind)
    }

    @Test
    fun uncheckingAProbablyNeededItemIsANo() {
        val done = start(listOf(milk), meta = emptyList()).then(SuggestedConfirmed(kept = emptySet()))
        assertIs<SessionState.Review>(done.state)
        assertEquals(AnswerKind.NO, done.answers.single().kind)
        assertTrue(SessionEffect.Haptic(HapticKind.ANSWERED) in done.effects)
    }

    @Test
    fun finishEndsTheQuestionsEarly() {
        val onYogurt = start(meta = emptyList()).then(SuggestedConfirmed(setOf(milk.id)))
        val review = onYogurt.then(Finish)
        assertEquals(listOf("lait"), assertIs<SessionState.Review>(review.state).drafts.map { it.product.slug })
        assertEquals(onYogurt.state, review.then(Undo).state)
    }

    @Test
    fun ignoresEventsThatDoNotFitTheState() {
        val meta = start().state
        assertSame(meta, reducer.reduce(meta, MetaAnswered(MetaKey.PERSONS, 3)).state)
        assertSame(meta, reducer.reduce(meta, MetaAnswered(MetaKey.HORIZON_DAYS, 0)).state)
        assertSame(meta, reducer.reduce(meta, No).state)

        val suggested = start(meta = emptyList()).state
        assertSame(suggested, reducer.reduce(suggested, SuggestedConfirmed(setOf(yogurt.id))).state)
        val zeroLiter = SuggestedConfirmed(setOf(milk.id), mapOf(milk.id to Quantity(0, LITER)))
        assertSame(suggested, reducer.reduce(suggested, zeroLiter).state)

        val asking = start(meta = emptyList()).then(SuggestedConfirmed(setOf(milk.id))).state
        assertSame(asking, reducer.reduce(asking, Yes(Quantity(0, LITER))).state)
        assertSame(asking, reducer.reduce(asking, Yes(Quantity(1000, KILOGRAM))).state)
        assertSame(asking, reducer.reduce(asking, Gate(yes = true)).state)
        assertEquals(Transition(asking), reducer.reduce(asking, CommitSucceeded(listId)))

        assertEquals(Transition(SessionState.Idle), reducer.reduce(SessionState.Idle, No))
        val completed = start().then(Abort).state
        assertSame(completed, reducer.reduce(completed, Start(sessionId, input(everything), listId)).state)
    }

    @Test
    fun aFailedCommitCanBeRetried() {
        val committing = start(listOf(milk), meta = emptyList()).then(SuggestedConfirmed(setOf(milk.id)), Finish)
        val failed = committing.then(CommitFailed(DomainError.StorageFailed("disk full"))).state
        assertEquals(DomainError.StorageFailed("disk full"), assertIs<SessionState.Failed>(failed).error)
        val review = reducer.reduce(failed, Resume).state
        assertIs<SessionState.Review>(review)
        assertIs<SessionState.Committing>(reducer.reduce(review, Finish).state)
        assertIs<SessionState.Completed>(reducer.reduce(failed, Abort).state)
    }

    @Test
    fun addOtherMergesIntoTheDrafts() {
        val review = start(listOf(milk, water), meta = emptyList()).then(SuggestedConfirmed(setOf(milk.id)), No)
        val before = assertIs<SessionState.Review>(review.state).drafts
        assertEquals(listOf("lait" to liters("1")), before.map { it.product.slug to it.quantity })

        val added = review.then(
            AddOther(milk.id, liters("1")),
            AddOther(water.id, liters("2")),
            AddOther(yogurt.id, liters("1")),
        )
        val drafts = assertIs<SessionState.Review>(added.state).drafts
        // Same product and unit: summed. The yogurt is not part of this session's catalog: ignored.
        assertEquals(
            listOf(Triple("lait", liters("2"), DraftSource.SUGGESTED), Triple("eau", liters("2"), DraftSource.OTHER)),
            drafts.map { Triple(it.product.slug, it.quantity, it.source) },
        )
        val undone = assertIs<SessionState.Review>(added.then(Undo).state)
        assertEquals(listOf(liters("2")), undone.drafts.map { it.quantity })
    }
}
