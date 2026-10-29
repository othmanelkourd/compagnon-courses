package com.compagnon.courses.feature.questionflow

import com.compagnon.courses.core.model.ProductId
import com.compagnon.courses.core.model.Quantity
import com.compagnon.courses.core.model.UnitCode
import com.compagnon.courses.engine.MetaKey
import com.compagnon.courses.engine.SessionEvent
import com.compagnon.courses.engine.SessionState
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The persisted form of the reducer events (`question_session_event.event`). A session is rebuilt by replaying
 * them from `Start`; each one is stored with the [subjectOf] the state it was applied to, so that a replay on a
 * catalog that changed meanwhile stops where it no longer matches instead of answering the wrong question.
 */
@Serializable
internal sealed interface StoredEvent {
    @Serializable
    @SerialName("meta")
    data class Meta(val key: String, val value: Int) : StoredEvent

    @Serializable
    @SerialName("confirm")
    data class Confirmed(val kept: List<String>, val edits: Map<String, StoredQuantity> = emptyMap()) : StoredEvent

    @Serializable
    @SerialName("yes")
    data class Yes(val quantity: StoredQuantity) : StoredEvent

    @Serializable
    @SerialName("no")
    data object No : StoredEvent

    @Serializable
    @SerialName("little_left")
    data object LittleLeft : StoredEvent

    @Serializable
    @SerialName("have_enough")
    data class HaveEnough(val days: Int) : StoredEvent

    @Serializable
    @SerialName("later")
    data object Later : StoredEvent

    @Serializable
    @SerialName("not_this_time")
    data object NotThisTime : StoredEvent

    @Serializable
    @SerialName("never")
    data object Never : StoredEvent

    @Serializable
    @SerialName("gate")
    data class Gate(val yes: Boolean) : StoredEvent

    @Serializable
    @SerialName("add_other")
    data class AddOther(val productId: String, val quantity: StoredQuantity) : StoredEvent

    @Serializable
    @SerialName("undo")
    data object Undo : StoredEvent

    @Serializable
    @SerialName("finish")
    data object Finish : StoredEvent

    @Serializable
    @SerialName("resume")
    data object Resume : StoredEvent
}

@Serializable
internal data class StoredQuantity(val milli: Long, val unit: String)

internal object SessionLog {
    private val json = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "type"
    }

    /** The persisted form of [event], or null for events that are not persisted (start, commit results…). */
    fun encode(
        event: SessionEvent,
    ): String? = toStored(event)?.let { json.encodeToString(StoredEvent.serializer(), it) }

    /** The reducer event, or null when [payload] is unreadable (newer app version, corrupted row). */
    fun decode(payload: String): SessionEvent? = try {
        json.decodeFromString(StoredEvent.serializer(), payload).toEvent()
    } catch (_: IllegalArgumentException) {
        null
    }

    /** Key of the question [state] is on (`card:<productId>`, `meta:PERSONS`, `review`…). */
    fun subjectOf(state: SessionState): String = when (state) {
        is SessionState.Meta -> "meta:${state.question.key.name}"
        is SessionState.Suggested -> "suggested"
        is SessionState.Asking -> "card:${state.card.item.productId.value}"
        is SessionState.CategoryGate -> "gate:${state.gate.category.id.value}"
        is SessionState.GateItems -> "gate-items:${state.gate.category.id.value}"
        is SessionState.Review -> "review"
        is SessionState.Failed -> "failed"
        SessionState.Idle -> "idle"
        is SessionState.Committing -> "committing"
        is SessionState.Paused -> "paused"
        is SessionState.Completed -> "completed"
    }

    private fun toStored(event: SessionEvent): StoredEvent? = when (event) {
        is SessionEvent.MetaAnswered -> StoredEvent.Meta(event.key.name, event.value)
        is SessionEvent.SuggestedConfirmed -> StoredEvent.Confirmed(
            kept = event.kept.map { it.value }.sorted(),
            edits = event.edits.entries.associate { (id, quantity) -> id.value to quantity.stored() },
        )
        is SessionEvent.Yes -> StoredEvent.Yes(event.quantity.stored())
        SessionEvent.No -> StoredEvent.No
        SessionEvent.LittleLeft -> StoredEvent.LittleLeft
        is SessionEvent.HaveEnough -> StoredEvent.HaveEnough(event.days)
        SessionEvent.Later -> StoredEvent.Later
        SessionEvent.NotThisTime -> StoredEvent.NotThisTime
        SessionEvent.Never -> StoredEvent.Never
        is SessionEvent.Gate -> StoredEvent.Gate(event.yes)
        is SessionEvent.AddOther -> StoredEvent.AddOther(event.productId.value, event.quantity.stored())
        SessionEvent.Undo -> StoredEvent.Undo
        SessionEvent.Finish -> StoredEvent.Finish
        SessionEvent.Resume -> StoredEvent.Resume
        is SessionEvent.Start, SessionEvent.Pause, SessionEvent.Abort, is SessionEvent.CommitSucceeded,
        is SessionEvent.CommitFailed,
        -> null
    }

    private fun StoredEvent.toEvent(): SessionEvent? = when (this) {
        is StoredEvent.Meta -> MetaKey.entries.firstOrNull { it.name == key }?.let { SessionEvent.MetaAnswered(it, value) }
        is StoredEvent.Confirmed -> SessionEvent.SuggestedConfirmed(
            kept = kept.mapTo(LinkedHashSet(), ::ProductId),
            edits = edits.entries.mapNotNull { (id, quantity) -> quantity.toQuantity()?.let { ProductId(id) to it } }.toMap(),
        )
        is StoredEvent.Yes -> quantity.toQuantity()?.let(SessionEvent::Yes)
        StoredEvent.No -> SessionEvent.No
        StoredEvent.LittleLeft -> SessionEvent.LittleLeft
        is StoredEvent.HaveEnough -> SessionEvent.HaveEnough(days)
        StoredEvent.Later -> SessionEvent.Later
        StoredEvent.NotThisTime -> SessionEvent.NotThisTime
        StoredEvent.Never -> SessionEvent.Never
        is StoredEvent.Gate -> SessionEvent.Gate(yes)
        is StoredEvent.AddOther -> quantity.toQuantity()?.let { SessionEvent.AddOther(ProductId(productId), it) }
        StoredEvent.Undo -> SessionEvent.Undo
        StoredEvent.Finish -> SessionEvent.Finish
        StoredEvent.Resume -> SessionEvent.Resume
    }

    private fun Quantity.stored() = StoredQuantity(milli, unit.value)

    private fun StoredQuantity.toQuantity(): Quantity? =
        if (milli >= 0 && unit.isNotBlank()) Quantity(milli, UnitCode(unit)) else null
}
