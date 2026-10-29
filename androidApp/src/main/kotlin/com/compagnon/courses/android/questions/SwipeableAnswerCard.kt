package com.compagnon.courses.android.questions

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.compagnon.courses.android.ui.components.softShadow
import com.compagnon.courses.android.ui.theme.CompagnonColors
import com.compagnon.courses.android.ui.theme.CompagnonMotion
import com.compagnon.courses.android.ui.theme.CompagnonShapes
import com.compagnon.courses.android.ui.theme.CompagnonType
import com.compagnon.courses.android.ui.theme.LocalReduceMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** Where a swipeable card rests: thrown left (no), in the middle, or thrown right (yes). */
internal enum class SwipeAnswer { NO, UNDECIDED, YES }

/** Share of the card width to drag past for a slow release to count as an answer. */
internal const val SWIPE_THRESHOLD_FRACTION: Float = 0.35f

/** Maximum tilt of the card while dragged, in degrees. */
private const val MAX_TILT_DEGREES: Float = 8f

/** How long an answered card waits for the session to move on before it comes back to the middle. */
private const val COME_BACK_AFTER_MILLIS: Long = 600

/** The two cards of the questionnaire. */
internal enum class AnswerCardStyle {
    /** Product card: Surface, Trait border, the next card showing behind it. */
    PRODUCT,

    /** Aisle card: Carte sombre, white text. */
    AISLE,
}

/**
 * Drives a [SwipeableAnswerCard]: a swipe, a button ([answer]) or a TalkBack action answers the same way, the card
 * flying off to its side while the card behind comes forward (animation 3). Remembered per question
 * ([rememberAnswerCardState]).
 */
@Stable
internal class AnswerCardState(private val scope: CoroutineScope) {
    internal val drag = AnchoredDraggableState(initialValue = SwipeAnswer.UNDECIDED)

    /** The card's opacity: it fades out instead of flying with « Réduire les animations ». */
    internal val fade = Animatable(1f)

    /** How far the card behind has come forward (0: resting at 0.96, 1: in place of this one). */
    internal val advance = Animatable(0f)

    internal var reduceMotion: Boolean = false

    /** An answer is under way: the card no longer drags and other answers are ignored. */
    var answering: Boolean by mutableStateOf(false)
        private set

    private var pending: (() -> Unit)? = null

    /**
     * Answers [yes] as a swipe would: the card flies off (exit curve, 250) while the one behind comes forward
     * (standard 400), then [action] runs. With « Réduire les animations », the card fades out instead. Ignored while
     * another answer is under way.
     */
    fun answer(yes: Boolean, action: () -> Unit) {
        if (answering) return
        answering = true
        scope.launch {
            if (reduceMotion) {
                fade.animateTo(0f, CompagnonMotion.standard())
                action()
                comeBack()
            } else {
                // The settled anchor runs [action] (see [SwipeableAnswerCard]), as after a swipe.
                pending = action
                launch { advance.animateTo(1f, CompagnonMotion.standard(CompagnonMotion.AMPLE)) }
                drag.animateTo(if (yes) SwipeAnswer.YES else SwipeAnswer.NO, CompagnonMotion.exit())
            }
        }
    }

    /** A card settled on a side: the button's action, or null after a swipe. */
    internal fun settled(): (() -> Unit)? {
        answering = true
        return pending.also { pending = null }
    }

    /**
     * Still composed a moment after an answer: the session did not move on (otherwise the step leaves with its
     * card), so the card comes back to the middle.
     */
    internal suspend fun comeBack() {
        // Counted in frames, not with a timer: UI tests wait for it like for an animation.
        val answeredAt = withFrameMillis { it }
        do {
            val now = withFrameMillis { it }
        } while (now - answeredAt < COME_BACK_AFTER_MILLIS)
        coroutineScope {
            launch { fade.animateTo(1f, CompagnonMotion.standard()) }
            launch { advance.animateTo(0f, CompagnonMotion.standard()) }
            drag.animateTo(SwipeAnswer.UNDECIDED)
        }
        answering = false
    }
}

/** The [AnswerCardState] of one question: callers key it by question (`key(state.stepKey())`). */
@Composable
internal fun rememberAnswerCardState(): AnswerCardState {
    val scope = rememberCoroutineScope()
    val state = remember(scope) { AnswerCardState(scope) }
    val reduceMotion = LocalReduceMotion.current
    SideEffect { state.reduceMotion = reduceMotion }
    return state
}

/**
 * A full-width card that answers by swiping: right = Oui ([onYes]), left = Non ([onNo]). The screen always
 * shows buttons doing the same through [AnswerCardState.answer] (a swipe is never the only way), and TalkBack gets
 * both as custom actions.
 *
 * The card fills the [modifier]'s height when [fillHeight] (product card), else it wraps its content. A product card
 * shows the next one behind it ([behind], e.g. its image), which comes forward from 0.96 as this one leaves.
 *
 * Callers key the [state] by question: a new question starts centred. When an answer does not move the session
 * on (the ViewModel ignored it), the card comes back to the middle.
 */
@Composable
internal fun SwipeableAnswerCard(
    yesLabel: String,
    noLabel: String,
    onYes: () -> Unit,
    onNo: () -> Unit,
    modifier: Modifier = Modifier,
    state: AnswerCardState = rememberAnswerCardState(),
    style: AnswerCardStyle = AnswerCardStyle.PRODUCT,
    fillHeight: Boolean = true,
    behind: (@Composable BoxScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val drag = state.drag
    val reduceMotion = LocalReduceMotion.current
    val currentOnYes by rememberUpdatedState(onYes)
    val currentOnNo by rememberUpdatedState(onNo)
    val fling = AnchoredDraggableDefaults.flingBehavior(
        state = drag,
        positionalThreshold = { distance -> distance * SWIPE_THRESHOLD_FRACTION },
    )

    LaunchedEffect(state) {
        snapshotFlow { drag.settledValue }.collect { settled ->
            if (settled == SwipeAnswer.UNDECIDED) return@collect
            val action = state.settled() ?: if (settled == SwipeAnswer.YES) currentOnYes else currentOnNo
            action()
            state.comeBack()
        }
    }

    val stacked = style == AnswerCardStyle.PRODUCT
    Box(
        modifier = modifier
            .onSizeChanged { size ->
                val width = size.width.toFloat()
                drag.updateAnchors(
                    DraggableAnchors {
                        SwipeAnswer.NO at -width
                        SwipeAnswer.UNDECIDED at 0f
                        SwipeAnswer.YES at width
                    },
                )
            }
            // Physical directions on purpose: right is always yes, whatever the layout direction.
            .anchoredDraggable(
                state = drag,
                orientation = Orientation.Horizontal,
                enabled = !state.answering,
                flingBehavior = fling,
            )
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(yesLabel) {
                        state.answer(yes = true) { currentOnYes() }
                        true
                    },
                    CustomAccessibilityAction(noLabel) {
                        state.answer(yes = false) { currentOnNo() }
                        true
                    },
                )
            }
            .testTag(SWIPE_CARD_TAG),
    ) {
        val offset = drag.offset.takeUnless { it.isNaN() } ?: 0f
        val width = drag.anchors.maxPosition().takeIf { it > 0f && !it.isNaN() } ?: 1f
        val progress = (offset / width).coerceIn(-1f, 1f)
        val yesColor = CompagnonColors.Basil
        val noColor = CompagnonColors.TomatoText
        val restingBorder = if (stacked) CompagnonColors.Line else Color.Transparent
        val borderColor = when {
            progress > 0.02f -> yesColor.copy(alpha = (progress * 3f).coerceIn(0.3f, 1f))
            progress < -0.02f -> noColor.copy(alpha = (-progress * 3f).coerceIn(0.3f, 1f))
            else -> restingBorder
        }
        val shape = CompagnonShapes.Card

        if (stacked && behind != null) {
            // The next card, behind: inset 14, 14 lower, 96 %, 70 % opaque; it comes forward as this one leaves
            // (dragged or answered), in place of it at the end. It stays put with « Réduire les animations ».
            val forward = if (reduceMotion) 0f else max(abs(progress), state.advance.value)
            val inset = 14.dp * (1f - forward)
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(start = inset, end = inset, bottom = 18.dp)
                    .graphicsLayer {
                        translationY = 14.dp.toPx() * (1f - forward)
                        scaleX = 0.96f + 0.04f * forward
                        scaleY = 0.96f + 0.04f * forward
                        alpha = 0.7f + 0.3f * forward
                    }
                    .clip(shape)
                    .background(CompagnonColors.Surface)
                    .border(1.dp, CompagnonColors.Line, shape),
                content = behind,
            )
        }
        val cardModifier = Modifier
            .then(if (fillHeight) Modifier.fillMaxSize().padding(bottom = if (stacked) 18.dp else 0.dp) else Modifier.fillMaxWidth())
            .absoluteOffset { IntOffset(offset.roundToInt(), 0) }
            .graphicsLayer {
                rotationZ = progress * MAX_TILT_DEGREES
                alpha = state.fade.value
            }
            .then(
                if (stacked) {
                    Modifier.softShadow(shape, radius = 24.dp, offsetY = 8.dp, alpha = 0.08f)
                } else {
                    Modifier.softShadow(shape, radius = 28.dp, offsetY = 12.dp, alpha = 0.18f)
                },
            )
            .clip(shape)
            .background(if (stacked) CompagnonColors.Surface else CompagnonColors.DarkCard)
            .border(width = if (abs(progress) > 0.02f) 3.dp else 1.dp, color = borderColor, shape = shape)
        Box(modifier = cardModifier) {
            Column(modifier = if (fillHeight) Modifier.fillMaxSize() else Modifier.fillMaxWidth(), content = content)
            if (abs(progress) > 0.05f) {
                val yes = progress > 0f
                val stampColor = when {
                    !stacked -> Color.White
                    yes -> yesColor
                    else -> noColor
                }
                Text(
                    text = if (yes) yesLabel else noLabel,
                    style = CompagnonType.SheetTitle,
                    color = stampColor,
                    modifier = Modifier
                        .align(if (yes) Alignment.TopStart else Alignment.TopEnd)
                        .padding(20.dp)
                        .graphicsLayer {
                            alpha = (abs(progress) * 3f).coerceAtMost(1f)
                            rotationZ = if (yes) -12f else 12f
                        }
                        .border(2.dp, stampColor, RoundedCornerShape(8.dp))
                        .background(if (stacked) CompagnonColors.Surface else CompagnonColors.DarkCard, RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
        }
    }
}

/** Test tag of the swipeable card (UI tests swipe it). */
internal const val SWIPE_CARD_TAG: String = "swipe_card"
