package com.compagnon.courses.engine

import com.compagnon.courses.core.model.CategoryId
import com.compagnon.courses.core.model.ListId
import com.compagnon.courses.core.model.Product
import com.compagnon.courses.core.model.ProductId
import com.compagnon.courses.core.model.Quantity
import kotlin.jvm.JvmInline

@JvmInline
value class SessionId(val value: String)

/** « 8 / 12 »: [answered] questions out of the [total] of the plan. */
data class Progress(val answered: Int, val total: Int)

/** How a draft list item was added; maps to `list_item.added_via` (`question_flow`, or `manual` for [OTHER]). */
enum class DraftSource { SUGGESTED, CARD, GATE, OTHER }

/** A list item to be written when the session is committed. */
data class DraftItem(val product: Product, val quantity: Quantity, val source: DraftSource) {
    val productId: ProductId get() = product.id
}

/** What an answer is about. */
sealed interface AnswerSubject {
    data class Meta(val key: MetaKey) : AnswerSubject

    data class Item(val productId: ProductId) : AnswerSubject

    /** A category gate. */
    data class Gate(val categoryId: CategoryId) : AnswerSubject
}

/** Answer kinds; the product and gate ones match `question_answer.answer`. */
enum class AnswerKind { META, YES, NO, LITTLE_LEFT, HAVE_ENOUGH, LATER, NOT_THIS_TIME, NEVER, GATE_YES, GATE_NO }

/**
 * One answer, as persisted in `question_answer`. Answers are immutable events: an undo emits a
 * [SessionEffect.RetractAnswer] compensation instead of deleting them.
 *
 * @property seq position in the session; never reused, even after an undo.
 * @property value the answered value of a meta question, or the snooze days of [AnswerKind.HAVE_ENOUGH].
 * @property score p at the time of the question, for product answers.
 * @property reason the main reason the question was asked.
 */
data class RecordedAnswer(
    val seq: Int,
    val subject: AnswerSubject,
    val kind: AnswerKind,
    val quantity: Quantity? = null,
    val value: Int? = null,
    val score: Double? = null,
    val reason: ReasonCode? = null,
)

/** Counters of `question_session`. [asked] counts questions shown, [answered] the ones answered. */
data class SessionStats(val asked: Int, val answered: Int, val itemsAdded: Int, val abandoned: Boolean)

/** Errors are values in the state, never exceptions (an undeclared Kotlin exception crashes a Swift app). */
sealed interface DomainError {
    data class PlanningFailed(val reason: String) : DomainError

    /** The list could not be written; the session can go back to its review. */
    data class StorageFailed(val reason: String) : DomainError
}

/**
 * The data carried by every interactive state of a session. Built only by [SessionReducer].
 *
 * @property previous the state to restore on undo (a persistent stack).
 */
@ConsistentCopyVisibility
data class SessionData internal constructor(
    val sessionId: SessionId,
    val listId: ListId?,
    val input: PlanningInput,
    val plan: QuestionPlan,
    internal val pendingMeta: List<MetaQuestion>,
    internal val pendingSuggested: List<ProposedItem>,
    internal val pendingQuestions: List<PlannedQuestion>,
    internal val postponed: Set<ProductId>,
    val drafts: List<DraftItem>,
    val answers: List<RecordedAnswer>,
    val completedQuestions: Int,
    internal val nextSeq: Int,
    internal val previous: SessionState.Interactive?,
) {
    val progress: Progress get() = Progress(answered = completedQuestions, total = plan.cost)

    /** The questions still to come after the current one. */
    val remainingQuestions: List<PlannedQuestion> get() = pendingQuestions

    val canUndo: Boolean get() = previous != null

    override fun toString(): String =
        "SessionData(sessionId=${sessionId.value}, progress=${progress.answered}/${progress.total}, " +
            "drafts=${drafts.size}, answers=${answers.size}, pending=${pendingQuestions.size})"
}

/**
 * The session state machine of `docs/question-engine.md` §6.6:
 * `Idle → Meta* → Suggested? → (Asking | CategoryGate → GateItems)* → Review → Committing → Completed`.
 * Pause, undo and abort are possible from every interactive state.
 */
sealed interface SessionState {
    data object Idle : SessionState

    /** A state waiting for the household; it can be paused, undone and aborted. */
    sealed interface Interactive : SessionState {
        val session: SessionData
        val progress: Progress get() = session.progress
    }

    data class Meta(val question: MetaQuestion, override val session: SessionData) : Interactive

    /** The T1 screen « Probablement nécessaire ». */
    data class Suggested(val items: List<ProposedItem>, override val session: SessionData) : Interactive

    data class Asking(val card: PlannedQuestion.Card, override val session: SessionData) : Interactive

    data class CategoryGate(val gate: PlannedQuestion.Gate, override val session: SessionData) : Interactive

    /** The products of a gate answered yes, all unchecked. */
    data class GateItems(val gate: PlannedQuestion.Gate, override val session: SessionData) : Interactive

    /** Recap, « Autre chose ? » and search. */
    data class Review(override val session: SessionData) : Interactive {
        val drafts: List<DraftItem> get() = session.drafts
    }

    /** Waiting for the caller to write the list ([SessionEffect.Commit]). */
    data class Committing(val session: SessionData) : SessionState {
        val drafts: List<DraftItem> get() = session.drafts
    }

    data class Paused(val resumeAt: Interactive) : SessionState {
        val sessionId: SessionId get() = resumeAt.session.sessionId
    }

    /** Terminal. [drafts] are the items written to [listId]; an abandoned session writes none. */
    data class Completed(
        val sessionId: SessionId,
        val listId: ListId?,
        val drafts: List<DraftItem>,
        val stats: SessionStats,
    ) : SessionState

    /** [recovery] is where `Resume` goes back to, when the failure is recoverable. */
    data class Failed(val sessionId: SessionId, val error: DomainError, val recovery: Interactive?) : SessionState
}

sealed interface SessionEvent {
    /** Mode and « Reprendre ma dernière liste » come from [PlanningInput.context]. */
    data class Start(val sessionId: SessionId, val input: PlanningInput, val listId: ListId? = null) : SessionEvent

    data class MetaAnswered(val key: MetaKey, val value: Int) : SessionEvent

    /** Confirms the T1 screen or the products of a gate: [kept] are added, with [edits] overriding quantities. */
    data class SuggestedConfirmed(
        val kept: Set<ProductId>,
        val edits: Map<ProductId, Quantity> = emptyMap(),
    ) : SessionEvent

    data class Yes(val quantity: Quantity) : SessionEvent

    data object No : SessionEvent

    data object LittleLeft : SessionEvent

    /** « J'en ai encore pour ~1 semaine / ~1 mois ». */
    data class HaveEnough(val days: Int) : SessionEvent

    /** Ask again at the end of the session (once). */
    data object Later : SessionEvent

    data object NotThisTime : SessionEvent

    /** « Je n'en achète plus ». */
    data object Never : SessionEvent

    data class Gate(val yes: Boolean) : SessionEvent

    /** « Autre chose ? »: adds a product from search during the review. */
    data class AddOther(val productId: ProductId, val quantity: Quantity) : SessionEvent

    data object Undo : SessionEvent

    data object Pause : SessionEvent

    data object Resume : SessionEvent

    /** Ends the questions (from a question) or commits the list (from the review). */
    data object Finish : SessionEvent

    data object Abort : SessionEvent

    data class CommitSucceeded(val listId: ListId) : SessionEvent

    data class CommitFailed(val error: DomainError) : SessionEvent
}

enum class HapticKind { ITEM_ADDED, ANSWERED, UNDONE }

/** Side effects for the caller (the ViewModel); the reducer itself performs none. */
sealed interface SessionEffect {
    data class PersistAnswer(val sessionId: SessionId, val answer: RecordedAnswer) : SessionEffect

    /** Compensation event for an undone answer. */
    data class RetractAnswer(val sessionId: SessionId, val answer: RecordedAnswer) : SessionEffect

    data class Haptic(val kind: HapticKind) : SessionEffect

    /** Write [drafts] to the list, then send [SessionEvent.CommitSucceeded] or [SessionEvent.CommitFailed]. */
    data class Commit(
        val sessionId: SessionId,
        val listId: ListId?,
        val drafts: List<DraftItem>,
        val stats: SessionStats,
    ) : SessionEffect

    /** Mark the session abandoned (`abandoned_at`); answers already given are kept. */
    data class Abandon(val sessionId: SessionId, val stats: SessionStats) : SessionEffect
}

data class Transition(val state: SessionState, val effects: List<SessionEffect> = emptyList())
