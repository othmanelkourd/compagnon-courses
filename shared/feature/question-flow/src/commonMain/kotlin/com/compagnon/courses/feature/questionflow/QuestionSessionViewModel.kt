package com.compagnon.courses.feature.questionflow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import com.compagnon.courses.core.data.AnswerRecord
import com.compagnon.courses.core.data.CatalogRepository
import com.compagnon.courses.core.data.CatalogScope
import com.compagnon.courses.core.data.PlanningSetup
import com.compagnon.courses.core.data.QuestionRepository
import com.compagnon.courses.core.data.ShoppingListRepository
import com.compagnon.courses.core.data.StoredSession
import com.compagnon.courses.core.model.ListId
import com.compagnon.courses.core.model.ProductId
import com.compagnon.courses.core.model.Quantity
import com.compagnon.courses.core.model.UnitPolicy
import com.compagnon.courses.engine.DomainError
import com.compagnon.courses.engine.HapticKind
import com.compagnon.courses.engine.SessionEffect
import com.compagnon.courses.engine.SessionEvent
import com.compagnon.courses.engine.SessionId
import com.compagnon.courses.engine.SessionMode
import com.compagnon.courses.engine.SessionReducer
import com.compagnon.courses.engine.SessionState
import com.compagnon.courses.engine.SessionStats
import com.compagnon.courses.engine.Transition
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Drives one question session: the engine's [SessionReducer] and `QuestionPlanner` over the local catalog,
 * household and statistics, offline.
 *
 * - On creation it resumes the unfinished session if there is one (rebuilt from its saved events, e.g. after
 *   process death), otherwise it plans a new quick session (syncing the catalog first if there is none).
 * - Every transition is persisted (one event row per answer). Completion writes the list items, the answers
 *   and the statistics in one transaction.
 * - Leaving the screen keeps the session open: the next ViewModel resumes it. [QuestionEvent.Abandon] ends it.
 *
 * Failures are values of [state] ([QuestionUiState.Error]); this ViewModel never throws.
 */
class QuestionSessionViewModel(
    private val questions: QuestionRepository,
    private val catalog: CatalogRepository,
    private val lists: ShoppingListRepository,
    private val scope: CatalogScope,
    private val clock: Clock = Clock.System,
    private val random: Random = Random.Default,
) : ViewModel() {

    private val reducer = SessionReducer()

    private val _state = MutableStateFlow<QuestionUiState>(QuestionUiState.Loading)
    val state: StateFlow<QuestionUiState> = _state.asStateFlow()

    private val _haptics = MutableSharedFlow<QuestionHaptic>(extraBufferCapacity = 16)

    /** One-shot haptic feedback after answers, additions and undo. */
    val haptics: SharedFlow<QuestionHaptic> = _haptics.asSharedFlow()

    private var engine: SessionState = SessionState.Idle
    private var sessionId: SessionId? = null
    private var listId: ListId? = null
    private var locale: String = scope.locale
    private var mapper: QuestionUiMapper? = null
    private var selection: LocalSelection = LocalSelection(subject = "idle")
    private val answers = mutableListOf<AnswerRecord>()

    /** Persistence runs in order, one write at a time, off the reducer path. */
    private val writes = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private var startJob: Job? = null
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            for (write in writes) {
                withContext(NonCancellable) { write() }
            }
        }
        start(resume = true)
    }

    fun onEvent(event: QuestionEvent) {
        when (event) {
            QuestionEvent.Retry -> retry()
            QuestionEvent.StartOver -> startOver()
            QuestionEvent.Abandon -> dispatch(SessionEvent.Abort)
            QuestionEvent.Undo -> dispatch(SessionEvent.Undo)
            QuestionEvent.EndQuestions -> if (engine !is SessionState.Review) dispatch(SessionEvent.Finish)
            QuestionEvent.Confirm -> if (engine is SessionState.Review) dispatch(SessionEvent.Finish)
            is QuestionEvent.AnswerMeta -> (engine as? SessionState.Meta)?.let {
                dispatch(SessionEvent.MetaAnswered(it.question.key, event.value))
            }
            is QuestionEvent.ToggleItem -> toggle(ProductId(event.productId))
            is QuestionEvent.StepItem -> stepItem(ProductId(event.productId), event.increment)
            QuestionEvent.ConfirmItems -> if (engine is SessionState.Suggested || engine is SessionState.GateItems) {
                dispatch(SessionEvent.SuggestedConfirmed(kept = selection.checked, edits = selection.edits))
            }
            is QuestionEvent.Yes -> yes(event.quantity)
            QuestionEvent.No -> dispatch(SessionEvent.No)
            is QuestionEvent.Nuanced -> dispatch(
                when (event.answer) {
                    NuancedAnswer.LITTLE_LEFT -> SessionEvent.LittleLeft
                    NuancedAnswer.ENOUGH_FOR_A_WEEK -> SessionEvent.HaveEnough(WEEK_DAYS)
                    NuancedAnswer.ENOUGH_FOR_A_MONTH -> SessionEvent.HaveEnough(MONTH_DAYS)
                    NuancedAnswer.LATER -> SessionEvent.Later
                    NuancedAnswer.NOT_THIS_TIME -> SessionEvent.NotThisTime
                    NuancedAnswer.NEVER -> SessionEvent.Never
                },
            )
            is QuestionEvent.StepQuantity -> stepCard(event.increment)
            is QuestionEvent.SelectUnit -> selectUnit(event.unit)
            is QuestionEvent.AnswerGate -> dispatch(SessionEvent.Gate(event.yes))
            is QuestionEvent.Search -> search(event.query)
            is QuestionEvent.AddOther -> addOther(ProductId(event.productId))
        }
    }

    // --- Start and resume ---

    private fun start(resume: Boolean) {
        if (startJob?.isActive == true) return
        _state.value = QuestionUiState.Loading
        startJob = viewModelScope.launch {
            try {
                val open = if (resume) questions.openSession() else null
                if (open != null && resume(open)) return@launch
                startNew()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.e(e) { "Question session could not be started" }
                _state.value = QuestionUiState.Error(QuestionErrorKind.STORAGE_FAILED, retryable = true)
            }
        }
    }

    private suspend fun startNew() {
        var setup = questions.planningSetup(SessionMode.QUICK, clock.now(), random.nextLong())
        if (setup == null) {
            catalog.installBundled(scope.country, scope.locale)
            setup = questions.planningSetup(SessionMode.QUICK, clock.now(), random.nextLong())
        }
        if (setup == null) {
            _state.value = QuestionUiState.Error(QuestionErrorKind.NO_CATALOG, retryable = true)
            return
        }
        val list = lists.activeListId()
        val id = questions.startSession(setup, list)
        begin(id, list, setup)
        apply(reducer.reduce(SessionState.Idle, SessionEvent.Start(id, setup.input, list)), event = null, persist = false)
    }

    /** Rebuilds [stored] by replaying its events; false when it cannot be planned again (it is abandoned). */
    private suspend fun resume(stored: StoredSession): Boolean {
        val setup = questions.planningSetup(stored.mode, stored.plannedAt, stored.seed)
        if (setup == null) {
            questions.abandon(stored.id, null, SessionStats(0, 0, 0, abandoned = true), emptyList())
            return false
        }
        begin(stored.id, stored.listId, setup)
        var transition = reducer.reduce(SessionState.Idle, SessionEvent.Start(stored.id, setup.input, stored.listId))
        collectAnswers(transition.effects, stored.plannedAt)
        for ((index, saved) in stored.events.withIndex()) {
            var current = transition.state
            if (current is SessionState.Committing) {
                // The commit never completed (the session is still open): replay its failure.
                transition = reducer.reduce(current, SessionEvent.CommitFailed(DomainError.StorageFailed("interrupted")))
                current = transition.state
            }
            val event = SessionLog.decode(saved.payload)
            if (event == null || SessionLog.subjectOf(current) != saved.subject) {
                log.w { "Session ${stored.id.value}: replay stopped at event ${index + 1} of ${stored.events.size}" }
                break
            }
            transition = reducer.reduce(current, event)
            collectAnswers(transition.effects, saved.recordedAt)
        }
        engine = transition.state
        val pendingCommit = transition.effects.filterIsInstance<SessionEffect.Commit>().lastOrNull()
            ?: (engine as? SessionState.Committing)?.let {
                SessionEffect.Commit(it.session.sessionId, it.session.listId, it.drafts, reducerStats(it))
            }
        render()
        if (engine is SessionState.Committing && pendingCommit != null) commit(pendingCommit)
        return true
    }

    private fun begin(id: SessionId, list: ListId?, setup: PlanningSetup) {
        sessionId = id
        listId = list
        locale = setup.locale
        answers.clear()
        engine = SessionState.Idle
        mapper = null
    }

    private fun retry() {
        when (val current = engine) {
            is SessionState.Failed -> if (current.recovery != null) dispatch(SessionEvent.Resume) else startOver()
            SessionState.Idle -> start(resume = true)
            else -> Unit
        }
    }

    private fun startOver() {
        val current = engine
        if (current is SessionState.Interactive || current is SessionState.Failed || current is SessionState.Paused) {
            apply(reducer.reduce(current, SessionEvent.Abort), event = SessionEvent.Abort, persist = false)
        }
        engine = SessionState.Idle
        start(resume = false)
    }

    // --- Events ---

    private fun dispatch(event: SessionEvent) {
        val current = engine
        if (current == SessionState.Idle || current is SessionState.Completed) return
        val transition = reducer.reduce(current, event)
        if (transition.state === current && transition.effects.isEmpty()) return
        apply(transition, event, persist = true, from = current)
    }

    private fun apply(
        transition: Transition,
        event: SessionEvent?,
        persist: Boolean,
        from: SessionState = engine,
    ) {
        val id = sessionId
        if (persist && event != null && id != null) {
            val payload = SessionLog.encode(event)
            if (payload != null) {
                val subject = SessionLog.subjectOf(from)
                enqueue { questions.recordEvent(id, subject, payload) }
            }
        }
        engine = transition.state
        val now = clock.now()
        collectAnswers(transition.effects, now)
        render()
        for (effect in transition.effects) {
            when (effect) {
                is SessionEffect.Haptic -> _haptics.tryEmit(effect.kind.toUi())
                is SessionEffect.Commit -> commit(effect)
                is SessionEffect.Abandon -> {
                    val session = (from as? SessionState.Interactive)?.session
                        ?: (from as? SessionState.Paused)?.resumeAt?.session
                        ?: (from as? SessionState.Failed)?.recovery?.session
                    val answerLog = answers.toList()
                    enqueue { questions.abandon(effect.sessionId, session, effect.stats, answerLog) }
                }
                is SessionEffect.PersistAnswer, is SessionEffect.RetractAnswer -> Unit
            }
        }
    }

    private fun commit(effect: SessionEffect.Commit) {
        val committing = engine as? SessionState.Committing ?: return
        val answerLog = answers.toList()
        enqueue {
            val result = try {
                val written = questions.commit(
                    sessionId = effect.sessionId,
                    session = committing.session,
                    listId = effect.listId ?: listId,
                    drafts = effect.drafts,
                    sessionStats = effect.stats,
                    answers = answerLog,
                )
                SessionEvent.CommitSucceeded(written)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.e(e) { "Session ${effect.sessionId.value} could not be committed" }
                SessionEvent.CommitFailed(DomainError.StorageFailed(e.message ?: "commit failed"))
            }
            // Writes run on the ViewModel's (main) dispatcher: the state can be updated here.
            if (engine === committing) apply(reducer.reduce(committing, result), event = null, persist = false)
        }
    }

    private fun yes(value: QuantityValue?) {
        val asking = engine as? SessionState.Asking ?: return
        val quantity = value?.let(QuestionUiMapper::unitOf)
            ?: selection.cardQuantity?.takeIf { selection.subject == SessionLog.subjectOf(asking) }
            ?: asking.card.item.proposal.quantity
        dispatch(SessionEvent.Yes(quantity))
    }

    private fun toggle(productId: ProductId) {
        val items = listItems() ?: return
        if (items.none { it.productId == productId }) return
        val checked = if (productId in selection.checked) selection.checked - productId else selection.checked + productId
        selection = selection.copy(checked = checked)
        render()
    }

    private fun stepItem(productId: ProductId, increment: Boolean) {
        val item = listItems()?.firstOrNull { it.productId == productId } ?: return
        val current = selection.edits[productId] ?: item.proposal.quantity
        val policy = item.product.policyFor(current.unit) ?: return
        val next = step(policy, current, increment) ?: return
        // Changing a quantity also checks the line: that is what the user means.
        selection = selection.copy(edits = selection.edits + (productId to next), checked = selection.checked + productId)
        render()
    }

    private fun stepCard(increment: Boolean) {
        val asking = engine as? SessionState.Asking ?: return
        val current = selection.cardQuantity ?: asking.card.item.proposal.quantity
        val policy = asking.card.item.product.policyFor(current.unit) ?: return
        val next = step(policy, current, increment) ?: return
        selection = selection.copy(cardQuantity = next)
        render()
    }

    private fun selectUnit(unit: String) {
        val asking = engine as? SessionState.Asking ?: return
        val proposal = asking.card.item.proposal
        val policy = asking.card.item.product.unitPolicies.firstOrNull { it.unit.value == unit } ?: return
        val quantity = if (policy == proposal.policy) proposal.quantity else QuestionUiMapper.firstQuantity(policy)
        selection = selection.copy(cardQuantity = quantity)
        render()
    }

    private fun search(query: String) {
        if (engine !is SessionState.Review) return
        selection = selection.copy(searchQuery = query)
        render()
        searchJob?.cancel()
        if (query.isBlank()) {
            selection = selection.copy(searchResults = emptyList())
            render()
            return
        }
        searchJob = viewModelScope.launch {
            val results = try {
                catalog.search(query)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.w(e) { "Search failed" }
                emptyList()
            }
            val review = engine as? SessionState.Review ?: return@launch
            val known = review.session.input.products.associateBy { it.id }
            // Only products of the planned catalog can be added to the session.
            selection = selection.copy(searchResults = results.mapNotNull { known[it.id] })
            render()
        }
    }

    private fun addOther(productId: ProductId) {
        val review = engine as? SessionState.Review ?: return
        val product = review.session.input.products.firstOrNull { it.id == productId } ?: return
        val quantity = mapperFor(review)?.defaultQuantity(product) ?: return
        dispatch(SessionEvent.AddOther(productId, quantity))
    }

    // --- Rendering ---

    private fun render() {
        val current = engine
        _state.value = when (current) {
            SessionState.Idle -> QuestionUiState.Loading
            is SessionState.Interactive -> {
                val mapper = mapperFor(current) ?: return
                val subject = SessionLog.subjectOf(current)
                if (selection.subject != subject) selection = mapper.initialSelection(current)
                mapper.map(current, selection)
            }
            is SessionState.Committing -> QuestionUiState.Committing
            is SessionState.Paused -> QuestionUiState.Loading
            is SessionState.Completed -> QuestionUiState.Done(itemsAdded = current.drafts.size, abandoned = current.stats.abandoned)
            is SessionState.Failed -> QuestionUiState.Error(current.error.toUiKind(), retryable = true)
        }
    }

    private fun mapperFor(state: SessionState.Interactive): QuestionUiMapper? {
        val existing = mapper
        // The planning input changes after the meta questions (re-planned), hence the identity check.
        if (existing != null && existing.isFor(state.session)) return existing
        return QuestionUiMapper(state.session, locale).also { mapper = it }
    }

    private fun listItems() = when (val current = engine) {
        is SessionState.Suggested -> current.items
        is SessionState.GateItems -> current.gate.items
        else -> null
    }

    private fun collectAnswers(effects: List<SessionEffect>, at: Instant) {
        for (effect in effects) {
            when (effect) {
                is SessionEffect.PersistAnswer -> answers += AnswerRecord(effect.answer, compensates = false, at = at)
                is SessionEffect.RetractAnswer -> answers += AnswerRecord(effect.answer, compensates = true, at = at)
                else -> Unit
            }
        }
    }

    private fun enqueue(write: suspend () -> Unit) {
        writes.trySend {
            try {
                write()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.e(e) { "Question session write failed" }
            }
        }
    }

    override fun onCleared() {
        writes.close()
    }

    private companion object {
        val log = Logger.withTag("QuestionSessionViewModel")
        const val WEEK_DAYS = 7
        const val MONTH_DAYS = 30

        fun step(policy: UnitPolicy, current: Quantity, increment: Boolean): Quantity? {
            val steps = current.milli / policy.step.milli + if (increment) 1 else -1
            val max = policy.maxSteps
            if (steps < policy.minSteps || (max != null && steps > max)) return null
            return policy.quantityOf(steps)
        }

        fun reducerStats(state: SessionState.Committing) = SessionStats(
            asked = state.session.completedQuestions,
            answered = state.session.completedQuestions,
            itemsAdded = state.drafts.size,
            abandoned = false,
        )
    }
}

private fun HapticKind.toUi(): QuestionHaptic = when (this) {
    HapticKind.ITEM_ADDED -> QuestionHaptic.ITEM_ADDED
    HapticKind.ANSWERED -> QuestionHaptic.ANSWERED
    HapticKind.UNDONE -> QuestionHaptic.UNDONE
}
