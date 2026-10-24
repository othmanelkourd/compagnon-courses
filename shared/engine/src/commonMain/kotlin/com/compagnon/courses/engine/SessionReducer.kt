package com.compagnon.courses.engine

import com.compagnon.courses.core.model.Product
import com.compagnon.courses.core.model.Quantity
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
import com.compagnon.courses.engine.SessionState.Asking
import com.compagnon.courses.engine.SessionState.CategoryGate
import com.compagnon.courses.engine.SessionState.Committing
import com.compagnon.courses.engine.SessionState.Completed
import com.compagnon.courses.engine.SessionState.Failed
import com.compagnon.courses.engine.SessionState.GateItems
import com.compagnon.courses.engine.SessionState.Idle
import com.compagnon.courses.engine.SessionState.Interactive
import com.compagnon.courses.engine.SessionState.Meta
import com.compagnon.courses.engine.SessionState.Paused
import com.compagnon.courses.engine.SessionState.Review
import com.compagnon.courses.engine.SessionState.Suggested

/**
 * The pure session state machine: `(state, event) -> Transition(state, effects)`. It never throws and
 * performs no I/O; events that make no sense in the current state are ignored (same state, no effect).
 *
 * Answers are recorded as immutable events. Undo restores the previous interactive state and emits a
 * compensation for each undone answer. « Plus tard » moves a card to the end of the session once; a second
 * « Plus tard » on the same card drops it. A user quantity is accepted when it is positive and expressed in
 * one of the product's policy units.
 */
class SessionReducer(private val planner: QuestionPlanner = QuestionPlanner()) {

    fun reduce(state: SessionState, event: SessionEvent): Transition = when (state) {
        Idle -> if (event is Start) start(event) else Transition(state)
        is Interactive -> interactive(state, event)
        is Paused -> when (event) {
            Resume -> Transition(state.resumeAt)
            Abort -> abandon(state.resumeAt)
            else -> Transition(state)
        }
        is Committing -> when (event) {
            is CommitSucceeded -> {
                val session = state.session
                val stats = stats(session, abandoned = false, onQuestion = false)
                Transition(Completed(session.sessionId, event.listId, session.drafts, stats))
            }
            is CommitFailed -> Transition(Failed(state.session.sessionId, event.error, Review(state.session)))
            else -> Transition(state)
        }
        is Failed -> when (event) {
            Resume -> state.recovery?.let { Transition(it) } ?: Transition(state)
            Abort -> state.recovery?.let { abandon(it) } ?: run {
                val stats = SessionStats(asked = 0, answered = 0, itemsAdded = 0, abandoned = true)
                Transition(
                    Completed(state.sessionId, listId = null, drafts = emptyList(), stats = stats),
                    listOf(SessionEffect.Abandon(state.sessionId, stats)),
                )
            }
            else -> Transition(state)
        }
        is Completed -> Transition(state)
    }

    private fun start(event: Start): Transition {
        val plan = tryPlan(event.input).getOrElse { error ->
            return Transition(Failed(event.sessionId, planningFailed(error), recovery = null))
        }
        val session = SessionData(
            sessionId = event.sessionId,
            listId = event.listId,
            input = event.input,
            plan = plan,
            pendingMeta = plan.meta,
            pendingSuggested = plan.suggested,
            pendingQuestions = plan.questions,
            postponed = emptySet(),
            drafts = emptyList(),
            answers = emptyList(),
            completedQuestions = 0,
            nextSeq = 1,
            previous = null,
        )
        return Transition(next(session, from = null))
    }

    private fun interactive(state: Interactive, event: SessionEvent): Transition = when (event) {
        Pause -> Transition(Paused(state))
        Abort -> abandon(state)
        Undo -> undo(state)
        Finish -> if (state is Review) commit(state) else Transition(Review(state.session.copy(previous = state)))
        else -> when (state) {
            is Meta -> onMeta(state, event)
            is Suggested -> if (event is SuggestedConfirmed) {
                confirm(state, state.items, event, DraftSource.SUGGESTED, countsAsQuestion = true)
            } else {
                Transition(state)
            }
            is Asking -> onCard(state, event)
            is CategoryGate -> if (event is Gate) onGate(state, event.yes) else Transition(state)
            is GateItems -> if (event is SuggestedConfirmed) {
                confirm(state, state.gate.items, event, DraftSource.GATE, countsAsQuestion = false)
            } else {
                Transition(state)
            }
            is Review -> if (event is AddOther) addOther(state, event) else Transition(state)
        }
    }

    private fun onMeta(state: Meta, event: SessionEvent): Transition {
        if (event !is MetaAnswered) return Transition(state)
        if (event.key != state.question.key || !SessionContext.isValid(event.key, event.value)) return Transition(state)
        val recorder = Recorder(state.session)
        recorder.record(AnswerSubject.Meta(event.key), AnswerKind.META, value = event.value)
        var session = recorder.session.let {
            it.copy(
                input = it.input.copy(context = it.input.context.with(event.key, event.value)),
                completedQuestions = it.completedQuestions + 1,
            )
        }
        if (session.pendingMeta.isEmpty()) {
            // The answers change H, the persons and the guests: plan the products again. On failure the
            // session goes back to this question, so the answer is not persisted.
            val plan = tryPlan(session.input).getOrElse { error ->
                return Transition(Failed(session.sessionId, planningFailed(error), recovery = state))
            }
            session = session.copy(plan = plan, pendingSuggested = plan.suggested, pendingQuestions = plan.questions)
        }
        return Transition(next(session, from = state), recorder.effects)
    }

    private fun onCard(state: Asking, event: SessionEvent): Transition {
        val item = state.card.item
        val subject = AnswerSubject.Item(item.productId)
        val recorder = Recorder(state.session)
        var haptic = HapticKind.ANSWERED
        when (event) {
            is Yes -> {
                if (!isAcceptable(item.product, event.quantity)) return Transition(state)
                recorder.record(subject, AnswerKind.YES, item = item, quantity = event.quantity)
                recorder.session = recorder.session.withDraft(item.product, event.quantity, DraftSource.CARD)
                haptic = HapticKind.ITEM_ADDED
            }
            No -> recorder.record(subject, AnswerKind.NO, item = item)
            LittleLeft -> recorder.record(subject, AnswerKind.LITTLE_LEFT, item = item)
            is HaveEnough -> {
                if (event.days !in 1..MAX_SNOOZE_DAYS) return Transition(state)
                recorder.record(subject, AnswerKind.HAVE_ENOUGH, item = item, value = event.days)
            }
            NotThisTime -> recorder.record(subject, AnswerKind.NOT_THIS_TIME, item = item)
            Never -> recorder.record(subject, AnswerKind.NEVER, item = item)
            Later -> {
                recorder.record(subject, AnswerKind.LATER, item = item)
                val session = recorder.session
                if (item.productId !in session.postponed) {
                    recorder.session = session.copy(
                        pendingQuestions = session.pendingQuestions + state.card,
                        postponed = session.postponed + item.productId,
                    )
                    val effects = recorder.effects + SessionEffect.Haptic(haptic)
                    return Transition(next(recorder.session, from = state), effects)
                }
            }
            else -> return Transition(state)
        }
        val session = recorder.session.copy(completedQuestions = recorder.session.completedQuestions + 1)
        return Transition(next(session, from = state), recorder.effects + SessionEffect.Haptic(haptic))
    }

    private fun onGate(state: CategoryGate, yes: Boolean): Transition {
        val recorder = Recorder(state.session)
        val kind = if (yes) AnswerKind.GATE_YES else AnswerKind.GATE_NO
        recorder.record(AnswerSubject.Gate(state.gate.category.id), kind)
        val session = recorder.session.copy(completedQuestions = recorder.session.completedQuestions + 1)
        val effects = recorder.effects + SessionEffect.Haptic(HapticKind.ANSWERED)
        return if (yes && state.gate.items.isNotEmpty()) {
            Transition(GateItems(state.gate, session.copy(previous = state)), effects)
        } else {
            Transition(next(session, from = state), effects)
        }
    }

    private fun confirm(
        state: Interactive,
        items: List<ProposedItem>,
        event: SuggestedConfirmed,
        source: DraftSource,
        countsAsQuestion: Boolean,
    ): Transition {
        val byId = items.associateBy { it.productId }
        if (!byId.keys.containsAll(event.kept) || !byId.keys.containsAll(event.edits.keys)) return Transition(state)
        val quantities = event.kept.associateWith { id -> event.edits[id] ?: byId.getValue(id).proposal.quantity }
        val acceptable = quantities.all { (id, quantity) -> isAcceptable(byId.getValue(id).product, quantity) }
        if (!acceptable) return Transition(state)

        val recorder = Recorder(state.session)
        for (item in items) {
            val subject = AnswerSubject.Item(item.productId)
            val quantity = quantities[item.productId]
            if (quantity != null) {
                recorder.record(subject, AnswerKind.YES, item = item, quantity = quantity)
                recorder.session = recorder.session.withDraft(item.product, quantity, source)
            } else if (item.preChecked) {
                recorder.record(subject, AnswerKind.NO, item = item)
            }
        }
        val session = recorder.session.let {
            if (countsAsQuestion) it.copy(completedQuestions = it.completedQuestions + 1) else it
        }
        val haptic = if (quantities.isEmpty()) HapticKind.ANSWERED else HapticKind.ITEM_ADDED
        return Transition(next(session, from = state), recorder.effects + SessionEffect.Haptic(haptic))
    }

    private fun addOther(state: Review, event: AddOther): Transition {
        val product = state.session.input.products.firstOrNull { it.id == event.productId } ?: return Transition(state)
        if (!isAcceptable(product, event.quantity)) return Transition(state)
        val session = state.session.withDraft(product, event.quantity, DraftSource.OTHER).copy(previous = state)
        return Transition(Review(session), listOf(SessionEffect.Haptic(HapticKind.ITEM_ADDED)))
    }

    private fun commit(state: Review): Transition {
        val session = state.session
        val stats = stats(session, abandoned = false, onQuestion = false)
        val commit = SessionEffect.Commit(session.sessionId, session.listId, session.drafts, stats)
        return Transition(Committing(session), listOf(commit))
    }

    private fun abandon(state: Interactive): Transition {
        val session = state.session
        val onQuestion = state !is Review && state !is GateItems
        val stats = stats(session, abandoned = true, onQuestion = onQuestion)
        return Transition(
            Completed(session.sessionId, session.listId, drafts = emptyList(), stats = stats),
            listOf(SessionEffect.Abandon(session.sessionId, stats)),
        )
    }

    private fun undo(state: Interactive): Transition {
        val previous = state.session.previous ?: return Transition(state)
        val retracted = state.session.answers.drop(previous.session.answers.size)
        // Sequence numbers are never reused: the compensation events keep theirs.
        val restored = previous.withSession(previous.session.copy(nextSeq = state.session.nextSeq))
        val compensations = retracted.map { SessionEffect.RetractAnswer(state.session.sessionId, it) }
        return Transition(restored, compensations + SessionEffect.Haptic(HapticKind.UNDONE))
    }

    /** The next state after [from]: remaining meta questions, then the T1 screen, then the questions. */
    private fun next(session: SessionData, from: Interactive?): Interactive {
        val current = session.copy(previous = from)
        return when {
            current.pendingMeta.isNotEmpty() ->
                Meta(current.pendingMeta.first(), current.copy(pendingMeta = current.pendingMeta.drop(1)))
            current.pendingSuggested.isNotEmpty() ->
                Suggested(current.pendingSuggested, current.copy(pendingSuggested = emptyList()))
            current.pendingQuestions.isNotEmpty() -> {
                val rest = current.copy(pendingQuestions = current.pendingQuestions.drop(1))
                when (val question = current.pendingQuestions.first()) {
                    is PlannedQuestion.Card -> Asking(question, rest)
                    is PlannedQuestion.Gate -> CategoryGate(question, rest)
                }
            }
            else -> Review(current)
        }
    }

    private fun tryPlan(input: PlanningInput): Result<QuestionPlan> = try {
        Result.success(planner.plan(input))
    } catch (e: IllegalArgumentException) {
        Result.failure(e)
    } catch (e: IllegalStateException) {
        Result.failure(e)
    } catch (e: ArithmeticException) {
        Result.failure(e)
    }

    private fun planningFailed(error: Throwable): DomainError =
        DomainError.PlanningFailed(error.message ?: "planning failed")

    private fun stats(session: SessionData, abandoned: Boolean, onQuestion: Boolean): SessionStats = SessionStats(
        asked = session.completedQuestions + if (onQuestion) 1 else 0,
        answered = session.completedQuestions,
        itemsAdded = if (abandoned) 0 else session.drafts.size,
        abandoned = abandoned,
    )

    private fun isAcceptable(product: Product, quantity: Quantity): Boolean =
        quantity.milli > 0 && product.policyFor(quantity.unit) != null

    /** Records answers on a working copy of the session and collects the matching effects. */
    private class Recorder(var session: SessionData) {
        val effects: MutableList<SessionEffect> = mutableListOf()

        fun record(
            subject: AnswerSubject,
            kind: AnswerKind,
            item: ProposedItem? = null,
            quantity: Quantity? = null,
            value: Int? = null,
        ) {
            val answer = RecordedAnswer(
                seq = session.nextSeq,
                subject = subject,
                kind = kind,
                quantity = quantity,
                value = value,
                score = item?.score,
                reason = item?.reasons?.firstOrNull(),
            )
            session = session.copy(answers = session.answers + answer, nextSeq = session.nextSeq + 1)
            effects += SessionEffect.PersistAnswer(session.sessionId, answer)
        }
    }

    private companion object {
        const val MAX_SNOOZE_DAYS: Int = 365
    }
}

/** Adds an item to the drafts; the same product in the same unit is summed, otherwise replaced. */
private fun SessionData.withDraft(product: Product, quantity: Quantity, source: DraftSource): SessionData {
    val index = drafts.indexOfFirst { it.productId == product.id }
    if (index < 0) return copy(drafts = drafts + DraftItem(product, quantity, source))
    val existing = drafts[index]
    val merged = if (existing.quantity.unit == quantity.unit) {
        try {
            existing.copy(quantity = existing.quantity + quantity)
        } catch (_: ArithmeticException) {
            existing.copy(quantity = quantity)
        }
    } else {
        existing.copy(quantity = quantity, source = source)
    }
    return copy(drafts = drafts.toMutableList().also { it[index] = merged })
}

private fun Interactive.withSession(session: SessionData): Interactive = when (this) {
    is Meta -> copy(session = session)
    is Suggested -> copy(session = session)
    is Asking -> copy(session = session)
    is CategoryGate -> copy(session = session)
    is GateItems -> copy(session = session)
    is Review -> copy(session = session)
}
