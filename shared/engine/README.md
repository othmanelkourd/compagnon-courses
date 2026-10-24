# :shared:engine

Question engine v0 (`docs/question-engine.md`): pure Kotlin, no I/O, no coroutines. Same input (including
`now` and `seed`) → same output, on every platform.

| Piece | Role |
|---|---|
| `NeedScorer` | p(need within H): EWMA interval shrunk toward the country prior, quantity correction, logistic curve, odds modifiers (season, event, guests, consecutive "no"), « il en reste un peu » floor. Typed `ReasonCode`s. |
| `QuantityAdvisor` | Base (usual answer → smoothed history → first preset → step), scaling by horizon and persons, rounding to the step (up for counts and packages, nearest for masses and volumes), clamping, three quick choices. |
| `QuestionPlanner` | Candidates, tiers T1–T4, budget Q_max, store / zone ordering. Returns a `QuestionPlan`. Allocation order: meta (≤ 3), T1 screen (1 unit, capped at `maxSuggested` = 15 items, the rest folds into gates), reserved slots (radar; exploration off by default), then **cards first** by decreasing p (watched first), minus a small gate reserve (`gateSlots` = 2, at most a quarter of the slots, only when there are gates). T2 overflow: p > 0.6 joins the T1 screen unchecked while it has room, else folds into its category gate. Gates (T3 + folded overflow) take the slots left, ranked by the p of their likeliest product, then Σp. |
| `Predictor` | Statistics updates: EWMA of the interval and of the quantity on a purchase (implicit purchases weigh half), and the effect of each answer (« non » → `consecutiveNo` + next due date, « il en reste un peu » → watch, « j'en ai encore » → snooze, « je n'en achète plus » → never ask, gate « non » → +0.5). |
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
