package com.compagnon.courses.android.questions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.compagnon.courses.android.ui.components.BoxedStepper
import com.compagnon.courses.android.ui.components.QuantityChoiceTile

/** One large choice of [BigChoiceQuestion]: [value] (« 7 », « Chaque semaine ») and its [subline] (« jours »). */
internal data class BigChoice(
    val value: String,
    val subline: String?,
    val selected: Boolean,
    val contentDescription: String?,
    val onClick: () -> Unit,
)

/**
 * The body of a question answered by large choices (meta questions, the onboarding rhythm): the [choices] in
 * balanced rows ([metaColumns]), then the boxed stepper « Autre durée : 7 jours » ([otherLabel]) for any other
 * value. [valueStyle] and [tileHeight] override the tile look (words instead of numbers).
 */
@Composable
internal fun BigChoiceQuestion(
    choices: List<BigChoice>,
    otherLabel: String,
    canDecrement: Boolean,
    canIncrement: Boolean,
    onStep: (increment: Boolean) -> Unit,
    decreaseDescription: String,
    increaseDescription: String,
    modifier: Modifier = Modifier,
    choicesTop: Dp = 32.dp,
    valueStyle: TextStyle? = null,
    tileHeight: Dp? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (choices.isNotEmpty()) {
            Spacer(Modifier.height(choicesTop))
            val columns = metaColumns(choices.size)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                choices.chunked(columns).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { choice ->
                            QuantityChoiceTile(
                                value = choice.value,
                                subline = choice.subline,
                                selected = choice.selected,
                                large = true,
                                contentDescription = choice.contentDescription,
                                onClick = choice.onClick,
                                valueStyle = valueStyle,
                                minHeight = tileHeight,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        BoxedStepper(
            label = otherLabel,
            canDecrement = canDecrement,
            canIncrement = canIncrement,
            onStep = onStep,
            decreaseDescription = decreaseDescription,
            increaseDescription = increaseDescription,
        )
    }
}
