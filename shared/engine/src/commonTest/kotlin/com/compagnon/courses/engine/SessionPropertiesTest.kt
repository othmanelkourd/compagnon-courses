package com.compagnon.courses.engine

import com.compagnon.courses.core.model.ListId
import com.compagnon.courses.core.model.Quantity
import io.kotest.property.Arb
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Random walks through the session state machine, checking its invariants after every event. */
class SessionPropertiesTest {

    @Test
    fun randomSessionsKeepTheInvariants() = runTest {
        checkAll(300, Worlds.world, Arb.long()) { world, walkSeed ->
            val random = Random(walkSeed)
            val reducer = SessionReducer(QuestionPlanner(world.config))
            val excluded = world.input.household.excludedTags
            var state: SessionState = SessionState.Idle
            var lastSeq = 0
            val retracted = mutableSetOf<Int>()

            var event: SessionEvent = SessionEvent.Start(SessionId("s"), world.input, ListId("l"))
            for (step in 0 until STEPS) {
                val transition = reducer.reduce(state, event)
                state = transition.state

                for (effect in transition.effects) {
                    when (effect) {
                        is SessionEffect.PersistAnswer -> {
                            assertTrue(effect.answer.seq > lastSeq, "seq ${effect.answer.seq} reused after $lastSeq")
                            lastSeq = effect.answer.seq
                        }
                        is SessionEffect.RetractAnswer ->
                            assertTrue(retracted.add(effect.answer.seq), "seq ${effect.answer.seq} retracted twice")
                        else -> Unit
                    }
                }

                val shown: List<ProposedItem> = when (state) {
                    is SessionState.Suggested -> state.items
                    is SessionState.Asking -> listOf(state.card.item)
                    is SessionState.GateItems -> state.gate.items
                    else -> emptyList()
                }
                assertTrue(shown.none { item -> item.product.tags.any { it in excluded } }, "excluded product shown")

                if (state is SessionState.Interactive) {
                    val progress = state.progress
                    assertTrue(progress.answered <= progress.total, "progress $progress")
                    world.budget?.let { assertTrue(progress.total <= it, "total ${progress.total} > budget $it") }
                    val drafts = state.session.drafts
                    assertEquals(drafts.size, drafts.map { it.productId }.toSet().size, "duplicate draft")
                }
                if (state is SessionState.Completed) break
                event = RandomEvents.next(state, random)
            }
        }
    }

    private companion object {
        const val STEPS: Int = 120
    }
}

/** Mostly sensible events for the current state, with some noise (undo, pause, invalid answers). */
private object RandomEvents {
    fun next(state: SessionState, random: Random): SessionEvent {
        when (random.nextInt(20)) {
            0 -> return SessionEvent.Undo
            1 -> return SessionEvent.Pause
            2 -> return SessionEvent.Resume
            3 -> return SessionEvent.No
            4 -> if (random.nextInt(5) == 0) return SessionEvent.Abort
        }
        return when (state) {
            SessionState.Idle -> SessionEvent.No
            is SessionState.Meta -> SessionEvent.MetaAnswered(
                key = if (random.nextInt(10) == 0) MetaKey.entries.random(random) else state.question.key,
                value = random.nextInt(-1, 40),
            )
            is SessionState.Suggested -> confirm(state.items, random)
            is SessionState.Asking -> when (random.nextInt(9)) {
                0 -> SessionEvent.Yes(state.card.item.proposal.quantity)
                1 -> SessionEvent.Yes(state.card.item.proposal.choices.random(random))
                2 -> SessionEvent.Yes(Quantity(random.nextLong(0, 5_000), state.card.item.proposal.policy.unit))
                3 -> SessionEvent.No
                4 -> SessionEvent.LittleLeft
                5 -> SessionEvent.HaveEnough(random.nextInt(0, 40))
                6 -> SessionEvent.Later
                7 -> SessionEvent.NotThisTime
                else -> SessionEvent.Never
            }
            is SessionState.CategoryGate -> SessionEvent.Gate(random.nextBoolean())
            is SessionState.GateItems -> confirm(state.gate.items, random)
            is SessionState.Review -> {
                val products = state.session.input.products
                if (products.isNotEmpty() && random.nextInt(3) == 0) {
                    val product = products.random(random)
                    val unit = product.unitPolicies.randomOrNull(random)?.unit ?: Fixtures.LITER
                    SessionEvent.AddOther(product.id, Quantity(random.nextLong(0, 3_000), unit))
                } else {
                    SessionEvent.Finish
                }
            }
            is SessionState.Committing -> if (random.nextInt(4) == 0) {
                SessionEvent.CommitFailed(DomainError.StorageFailed("random"))
            } else {
                SessionEvent.CommitSucceeded(ListId("l"))
            }
            is SessionState.Paused -> SessionEvent.Resume
            is SessionState.Failed -> if (random.nextBoolean()) SessionEvent.Resume else SessionEvent.Abort
            is SessionState.Completed -> SessionEvent.Finish
        }
    }

    private fun confirm(items: List<ProposedItem>, random: Random): SessionEvent {
        val kept = items.filter { random.nextBoolean() }.map { it.productId }.toSet()
        val edits = kept.filter { random.nextInt(3) == 0 }.associateWith { id ->
            val item = items.first { it.productId == id }
            item.proposal.choices.random(random)
        }
        return SessionEvent.SuggestedConfirmed(kept, edits)
    }
}
