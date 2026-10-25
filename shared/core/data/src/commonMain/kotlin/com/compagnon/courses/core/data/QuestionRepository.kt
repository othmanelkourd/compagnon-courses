package com.compagnon.courses.core.data

import com.compagnon.courses.core.database.CompagnonDatabase
import com.compagnon.courses.core.database.Question_session
import com.compagnon.courses.core.model.AddedVia
import com.compagnon.courses.core.model.ListId
import com.compagnon.courses.engine.AnswerKind
import com.compagnon.courses.engine.AnswerSubject
import com.compagnon.courses.engine.DraftItem
import com.compagnon.courses.engine.DraftSource
import com.compagnon.courses.engine.PlannedQuestion
import com.compagnon.courses.engine.PlanningInput
import com.compagnon.courses.engine.Predictor
import com.compagnon.courses.engine.ReasonCode
import com.compagnon.courses.engine.RecordedAnswer
import com.compagnon.courses.engine.SessionContext
import com.compagnon.courses.engine.SessionData
import com.compagnon.courses.engine.SessionId
import com.compagnon.courses.engine.SessionMode
import com.compagnon.courses.engine.SessionStats
import kotlin.coroutines.CoroutineContext
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Everything a session is planned from, and the catalog version it was planned on. */
data class PlanningSetup(
    val input: PlanningInput,
    val catalogVersion: Long,
    val country: String,
    val locale: String,
)

/** One reducer event of a session, as saved: [payload] is opaque here (the ViewModel encodes it). */
data class StoredSessionEvent(val index: Int, val subject: String, val payload: String, val recordedAt: Instant)

/**
 * An unfinished session saved on the device: its planning parameters ([plannedAt] and [seed] are the `now` and
 * `seed` of its [PlanningInput]) and every event, enough to rebuild it after process death.
 */
data class StoredSession(
    val id: SessionId,
    val mode: SessionMode,
    val listId: ListId?,
    val plannedAt: Instant,
    val seed: Long,
    val catalogVersion: Long,
    val startedAt: Instant,
    val events: List<StoredSessionEvent>,
)

/** A row of `question_answer`: an answer, or the compensation of an undone one ([compensates]). */
data class AnswerRecord(val answer: RecordedAnswer, val compensates: Boolean, val at: Instant)

/**
 * Persistence of question sessions, and the bridge between the local data and the engine: it builds the
 * [PlanningInput] (catalog + household + statistics) and applies the outcome of a session (list items,
 * answers, statistics through the engine's [Predictor]) in one transaction.
 */
@OptIn(ExperimentalObjCRefinement::class)
@HiddenFromObjC
class QuestionRepository(
    private val database: CompagnonDatabase,
    private val households: HouseholdRepository,
    private val ids: IdGenerator = UuidV7Generator,
    private val clock: Clock = Clock.System,
    private val context: CoroutineContext = Dispatchers.Default,
    private val predictor: Predictor = Predictor(),
) {
    private val catalog = CatalogStore(database)
    private val lists = ListStore(database, ids)
    private val stats = StatsStore(database, ids)
    private val queries get() = database.questionQueries

    /**
     * The engine input for a session planned at [now] with [seed], or null when there is no local catalog yet.
     * The same arguments on the same data give the same input (sessions are rebuilt this way).
     */
    suspend fun planningSetup(mode: SessionMode, now: Instant, seed: Long): PlanningSetup? = withContext(context) {
        val loaded = catalog.load()?.catalog ?: return@withContext null
        if (loaded.products.isEmpty()) return@withContext null
        val household = households.read()
        val sessionContext = SessionContext.forHousehold(household.profile, household.usualHorizonDays, mode)
            .copy(categoryOrder = household.storeCategoryOrder)
        PlanningSetup(
            input = PlanningInput(
                products = loaded.products,
                categories = loaded.categories,
                storageZones = loaded.storageZones,
                units = loaded.units,
                household = household.profile,
                stats = stats.all(),
                context = sessionContext,
                lastList = lists.lastCompletedQuantities(),
                now = now,
                seed = seed,
            ),
            catalogVersion = loaded.version,
            country = loaded.country.value,
            locale = loaded.locale,
        )
    }

    /** Saves a new session planned from [setup]; [listId] is the list it will fill. */
    suspend fun startSession(setup: PlanningSetup, listId: ListId?): SessionId = withContext(context) {
        val id = SessionId(ids.newId())
        val sessionContext = setup.input.context
        queries.insertSession(
            id = id.value,
            mode = sessionContext.mode.wireName,
            listId = listId?.value,
            country = setup.country,
            locale = setup.locale,
            catalogVersion = setup.catalogVersion,
            seed = setup.input.seed,
            plannedAt = setup.input.now.toEpochMilliseconds(),
            horizonDays = sessionContext.horizonDays.toLong(),
            persons = sessionContext.persons.toLong(),
            guests = sessionContext.guests.toLong(),
            startedAt = clock.now().toEpochMilliseconds(),
        )
        id
    }

    /** The most recent session neither completed nor abandoned, with its events. */
    suspend fun openSession(): StoredSession? = withContext(context) {
        val row = queries.selectOpenSession().executeAsOneOrNull() ?: return@withContext null
        row.toStored()
    }

    /** Appends one event to the session log (every transition is persisted). */
    suspend fun recordEvent(sessionId: SessionId, subject: String, payload: String) = withContext(context) {
        val now = clock.now().toEpochMilliseconds()
        database.transaction {
            val index = queries.nextEventIndex(sessionId.value).executeAsOne()
            queries.insertEvent(sessionId.value, index, subject, payload, now)
            queries.touchSession(now, sessionId.value)
        }
    }

    /**
     * Commits a finished session in one transaction: the [drafts] are merged into the list ([listId] when it is
     * still the active list, otherwise the active list), the [answers] are written, the household statistics
     * are updated from the final answers of [session], and the session is marked completed.
     *
     * @return the list the items were written to.
     */
    suspend fun commit(
        sessionId: SessionId,
        session: SessionData,
        listId: ListId?,
        drafts: List<DraftItem>,
        sessionStats: SessionStats,
        answers: List<AnswerRecord>,
    ): ListId = withContext(context) {
        val now = clock.now()
        val nowMillis = now.toEpochMilliseconds()
        database.transactionWithResult {
            val active = lists.activeList()?.takeIf { it.id == listId?.value } ?: lists.ensureActiveList(nowMillis)
            drafts.forEach { draft ->
                lists.addProduct(
                    listId = active.id,
                    productId = draft.productId,
                    quantity = draft.quantity,
                    product = draft.product,
                    units = session.input.units,
                    addedVia = if (draft.source == DraftSource.OTHER) AddedVia.MANUAL else AddedVia.QUESTION_FLOW,
                    now = nowMillis,
                )
            }
            insertAnswers(sessionId, answers)
            applyAnswers(session, now, includeYes = true)
            queries.completeSession(
                completedAt = nowMillis,
                listId = active.id,
                asked = sessionStats.asked.toLong(),
                answered = sessionStats.answered.toLong(),
                itemsAdded = drafts.size.toLong(),
                id = sessionId.value,
            )
            ListId(active.id)
        }
    }

    /**
     * Abandons a session: nothing is added to the list, but the answers already given are kept and their
     * preferences applied (« je n'en achète plus », « j'en ai encore »…). [session] is null when the session
     * could not be rebuilt.
     */
    suspend fun abandon(
        sessionId: SessionId,
        session: SessionData?,
        sessionStats: SessionStats,
        answers: List<AnswerRecord>,
    ) = withContext(context) {
        val now = clock.now()
        database.transaction {
            insertAnswers(sessionId, answers)
            if (session != null) applyAnswers(session, now, includeYes = false)
            queries.abandonSession(
                abandonedAt = now.toEpochMilliseconds(),
                asked = sessionStats.asked.toLong(),
                answered = sessionStats.answered.toLong(),
                id = sessionId.value,
            )
        }
    }

    private fun insertAnswers(sessionId: SessionId, answers: List<AnswerRecord>) {
        answers.forEach { record ->
            val answer = record.answer
            val (kind, subjectId) = when (val subject = answer.subject) {
                is AnswerSubject.Meta -> "meta" to subject.key.name
                is AnswerSubject.Item -> "product" to subject.productId.value
                is AnswerSubject.Gate -> "category" to subject.categoryId.value
            }
            queries.insertAnswer(
                sessionId = sessionId.value,
                seq = answer.seq.toLong(),
                subjectKind = kind,
                subjectId = subjectId,
                answer = answer.kind.wireName,
                value = answer.value?.toLong(),
                quantityMilli = answer.quantity?.milli,
                unit = answer.quantity?.unit?.value,
                score = answer.score,
                reason = answer.reason?.wireName,
                compensates = record.compensates.toLong(),
                answeredAt = record.at.toEpochMilliseconds(),
            )
        }
    }

    /** Statistics and preferences from the final (not undone) answers of [session]. */
    private fun applyAnswers(session: SessionData, now: Instant, includeYes: Boolean) {
        val products = session.input.products.associateBy { it.id }
        val gates = session.plan.questions.filterIsInstance<PlannedQuestion.Gate>().associateBy { it.category.id }
        val household = session.input.household
        for (answer in session.answers) {
            when (val subject = answer.subject) {
                is AnswerSubject.Item -> {
                    if (answer.kind == AnswerKind.YES && !includeYes) continue
                    val product = products[subject.productId] ?: continue
                    val current = stats.get(subject.productId)
                    val updated = predictor.onAnswer(current, product, answer.kind, now, household, answer.quantity, answer.value)
                    if (updated != current) stats.put(subject.productId, updated)
                }
                is AnswerSubject.Gate -> if (answer.kind == AnswerKind.GATE_NO) {
                    gates[subject.categoryId]?.items?.forEach { item ->
                        stats.put(item.productId, predictor.onGateNo(stats.get(item.productId)))
                    }
                }
                is AnswerSubject.Meta -> Unit
            }
        }
    }

    private fun Question_session.toStored(): StoredSession = StoredSession(
        id = SessionId(id),
        mode = SessionMode.entries.firstOrNull { it.wireName == mode } ?: SessionMode.QUICK,
        listId = list_id?.let(::ListId),
        plannedAt = Instant.fromEpochMilliseconds(planned_at),
        seed = seed,
        catalogVersion = catalog_version,
        startedAt = Instant.fromEpochMilliseconds(started_at),
        events = queries.selectEvents(id).executeAsList().map {
            StoredSessionEvent(it.idx.toInt(), it.subject, it.event, Instant.fromEpochMilliseconds(it.recorded_at))
        },
    )
}

/** `question_session.mode`. */
val SessionMode.wireName: String
    get() = when (this) {
        SessionMode.QUICK -> "quick"
        SessionMode.HOME_TOUR -> "home_tour"
        SessionMode.ZONE -> "zone"
        SessionMode.FULL -> "full"
    }

/** `question_answer.answer`. */
val AnswerKind.wireName: String get() = name.lowercase()

/** `question_answer.reason`: the reason code, e.g. `due`, `staple`, `radar`. */
val ReasonCode.wireName: String
    get() = when (this) {
        is ReasonCode.Due -> "due"
        ReasonCode.Staple -> "staple"
        ReasonCode.Seasonal -> "seasonal"
        is ReasonCode.Event -> "event"
        is ReasonCode.Radar -> "radar"
        is ReasonCode.Prior -> "prior"
        ReasonCode.Exploration -> "exploration"
        ReasonCode.LittleLeft -> "little_left"
        ReasonCode.LastList -> "last_list"
    }
