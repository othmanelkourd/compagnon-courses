# :shared:engine

Question engine v0 (`docs/question-engine.md`): pure Kotlin, no I/O, no coroutines. Same input (including
`now` and `seed`) → same output, on every platform.

| Piece | Role |
|---|---|
| `NeedScorer` | p(need within H): EWMA interval shrunk toward the country prior, quantity correction, logistic curve, odds modifiers (season, event, guests, consecutive "no"), « il en reste un peu » floor. Typed `ReasonCode`s. |
| `QuantityAdvisor` | Base (usual answer → smoothed history → first preset → step), scaling by horizon and persons, rounding to the step (up for counts and packages, nearest for masses and volumes), clamping, three quick choices. |
| `QuestionPlanner` | Candidates, tiers T1–T4, budget Q_max with reserved slots (radar; exploration is off by default), category gates, store / zone ordering. Returns a `QuestionPlan`. |
| `SessionReducer` | `reduce(state, event): Transition(state, effects)`, from `Idle` to `Completed`, with undo, pause/resume and abort. |

## Driving a session (future `QuestionSessionViewModel`)

```kotlin
val reducer = SessionReducer()
var state: SessionState = SessionState.Idle

fun dispatch(event: SessionEvent) {
    val transition = reducer.reduce(state, event)
    state = transition.state                 // persist it for pause / process death
    transition.effects.forEach(::perform)    // PersistAnswer, RetractAnswer, Commit, Abandon, Haptic
}

dispatch(SessionEvent.Start(sessionId, planningInput, listId))
```

- The reducer never throws; an event that does not fit the current state is ignored (same state, no effect).
  Errors are values (`SessionState.Failed`), so nothing unexpected reaches Swift.
- Answers are immutable events. `Undo` restores the previous state and emits one `RetractAnswer`
  (a compensation) per undone answer; sequence numbers are never reused.
- `Finish` from the review emits `Commit`: write the drafts and the outbox in one transaction, then send
  `CommitSucceeded(listId)` or `CommitFailed(error)` (the latter can be resumed back to the review).
- The planner runs at `Start` and again after the last meta question, since H, the persons and the guests
  change the scores and the quantities.
