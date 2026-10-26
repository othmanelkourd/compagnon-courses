package com.compagnon.courses.android.questions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
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
import com.compagnon.courses.android.ui.theme.CompagnonShapes
import com.compagnon.courses.android.ui.theme.CompagnonType
import kotlin.math.abs
import kotlin.math.roundToInt

/** Where a swipeable card rests: thrown left (no), in the middle, or thrown right (yes). */
internal enum class SwipeAnswer { NO, UNDECIDED, YES }

/** Share of the card width to drag past for a slow release to count as an answer. */
internal const val SWIPE_THRESHOLD_FRACTION: Float = 0.35f

/** Maximum tilt of the card while dragged, in degrees. */
private const val MAX_TILT_DEGREES: Float = 8f

/** The two cards of the questionnaire. */
internal enum class AnswerCardStyle {
    /** Product card: Surface, Trait border, the next card showing behind it (70 %). */
    PRODUCT,

    /** Aisle card: Carte sombre, white text. */
    AISLE,
}

/**
 * A full-width card that answers by swiping: right = Oui ([onYes]), left = Non ([onNo]). The screen always
 * shows buttons doing the same (a swipe is never the only way), and TalkBack gets both as custom actions.
 *
 * The card fills the [modifier]'s height when [fillHeight] (product card), else it wraps its content.
 *
 * Callers wrap it in `key(question)`: a new question starts centred. When an answer does not move the session
 * on (the ViewModel ignored it), the card comes back to the middle.
 */
@Composable
internal fun SwipeableAnswerCard(
    yesLabel: String,
    noLabel: String,
    onYes: () -> Unit,
    onNo: () -> Unit,
    modifier: Modifier = Modifier,
    style: AnswerCardStyle = AnswerCardStyle.PRODUCT,
    fillHeight: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val state = remember { AnchoredDraggableState(initialValue = SwipeAnswer.UNDECIDED) }
    val currentOnYes by rememberUpdatedState(onYes)
    val currentOnNo by rememberUpdatedState(onNo)
    val fling = AnchoredDraggableDefaults.flingBehavior(
        state = state,
        positionalThreshold = { distance -> distance * SWIPE_THRESHOLD_FRACTION },
    )

    LaunchedEffect(state) {
        snapshotFlow { state.settledValue }.collect { settled ->
            when (settled) {
                SwipeAnswer.YES -> currentOnYes()
                SwipeAnswer.NO -> currentOnNo()
                SwipeAnswer.UNDECIDED -> return@collect
            }
            // Still composed after the answer: the session did not move on, bring the card back.
            state.animateTo(SwipeAnswer.UNDECIDED)
        }
    }

    val stacked = style == AnswerCardStyle.PRODUCT
    Box(
        modifier = modifier
            .onSizeChanged { size ->
                val width = size.width.toFloat()
                state.updateAnchors(
                    DraggableAnchors {
                        SwipeAnswer.NO at -width
                        SwipeAnswer.UNDECIDED at 0f
                        SwipeAnswer.YES at width
                    },
                )
            }
            // Physical directions on purpose: right is always yes, whatever the layout direction.
            .anchoredDraggable(state = state, orientation = Orientation.Horizontal, flingBehavior = fling)
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(yesLabel) { currentOnYes(); true },
                    CustomAccessibilityAction(noLabel) { currentOnNo(); true },
                )
            }
            .testTag(SWIPE_CARD_TAG),
    ) {
        val offset = state.offset.takeUnless { it.isNaN() } ?: 0f
        val width = state.anchors.maxPosition().takeIf { it > 0f && !it.isNaN() } ?: 1f
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

        if (stacked) {
            // The next card, behind: inset 14, 14 lower, 96 %, 70 % opaque (it stays put while the top one moves).
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(start = 14.dp, end = 14.dp, bottom = 18.dp)
                    .graphicsLayer {
                        translationY = 14.dp.toPx()
                        scaleX = 0.96f
                        scaleY = 0.96f
                        alpha = 0.7f
                    }
                    .clip(shape)
                    .background(CompagnonColors.Surface)
                    .border(1.dp, CompagnonColors.Line, shape),
            )
        }
        val cardModifier = Modifier
            .then(if (fillHeight) Modifier.fillMaxSize().padding(bottom = if (stacked) 18.dp else 0.dp) else Modifier.fillMaxWidth())
            .absoluteOffset { IntOffset(offset.roundToInt(), 0) }
            .graphicsLayer { rotationZ = progress * MAX_TILT_DEGREES }
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
