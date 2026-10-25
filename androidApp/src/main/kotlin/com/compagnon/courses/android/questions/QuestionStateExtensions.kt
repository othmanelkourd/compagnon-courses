package com.compagnon.courses.android.questions

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.compagnon.courses.feature.questionflow.QuestionHaptic
import com.compagnon.courses.feature.questionflow.QuestionProgress
import com.compagnon.courses.feature.questionflow.QuestionUiState

/**
 * The position shown in the top bar, « 8 / 12 »: the question on screen, counted from 1 ([QuestionProgress.answered]
 * questions are behind it), never past the total. Null when there is nothing to count.
 */
internal data class ProgressPosition(val current: Int, val total: Int) {
    /** Share of the session already answered, for the progress bar. */
    val fraction: Float get() = if (total == 0) 0f else (current - 1).coerceIn(0, total) / total.toFloat()
}

internal fun QuestionProgress.position(): ProgressPosition? =
    if (total <= 0) null else ProgressPosition(current = (answered + 1).coerceIn(1, total), total = total)

/** The progress of the states that have one (questions and review). */
internal val QuestionUiState.progress: QuestionProgress?
    get() = when (this) {
        is QuestionUiState.Meta -> progress
        is QuestionUiState.Suggested -> progress
        is QuestionUiState.Card -> progress
        is QuestionUiState.Gate -> progress
        is QuestionUiState.GateItems -> progress
        is QuestionUiState.Review -> progress
        QuestionUiState.Loading, QuestionUiState.Committing, is QuestionUiState.Done, is QuestionUiState.Error -> null
    }

/** Whether « Annuler » (and the system back) can go back one question. */
internal val QuestionUiState.canUndo: Boolean
    get() = when (this) {
        is QuestionUiState.Meta -> canUndo
        is QuestionUiState.Suggested -> canUndo
        is QuestionUiState.Card -> canUndo
        is QuestionUiState.Gate -> canUndo
        is QuestionUiState.GateItems -> canUndo
        is QuestionUiState.Review -> canUndo
        QuestionUiState.Loading, QuestionUiState.Committing, is QuestionUiState.Done, is QuestionUiState.Error -> false
    }

/** Whether the session is asking questions (« Passer au récapitulatif » and « Abandonner » make sense). */
internal val QuestionUiState.isAsking: Boolean
    get() = when (this) {
        is QuestionUiState.Meta, is QuestionUiState.Suggested, is QuestionUiState.Card,
        is QuestionUiState.Gate, is QuestionUiState.GateItems,
        -> true
        else -> false
    }

/**
 * Identity of the step on screen, for transitions: a new key is a new question (animated, swipe state reset);
 * a quantity change keeps the key.
 */
internal fun QuestionUiState.stepKey(): String = when (this) {
    QuestionUiState.Loading -> "loading"
    QuestionUiState.Committing -> "committing"
    is QuestionUiState.Meta -> "meta:${question.kind}:${progress.answered}"
    is QuestionUiState.Suggested -> "suggested:${progress.answered}"
    is QuestionUiState.Card -> "card:${card.productId}:${progress.answered}"
    is QuestionUiState.Gate -> "gate:${gate.categoryId}:${progress.answered}"
    is QuestionUiState.GateItems -> "gate-items:${gate.categoryId}:${progress.answered}"
    is QuestionUiState.Review -> "review"
    is QuestionUiState.Done -> "done"
    is QuestionUiState.Error -> "error:$kind"
}

/** The platform feedback of each shared haptic. */
internal fun QuestionHaptic.feedbackType(): HapticFeedbackType = when (this) {
    QuestionHaptic.ITEM_ADDED -> HapticFeedbackType.Confirm
    QuestionHaptic.ANSWERED -> HapticFeedbackType.SegmentTick
    QuestionHaptic.UNDONE -> HapticFeedbackType.VirtualKey
}
