package com.compagnon.courses.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldDecorator
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.compagnon.courses.android.R
import com.compagnon.courses.android.ui.theme.CompagnonColors
import com.compagnon.courses.android.ui.theme.CompagnonShapes
import com.compagnon.courses.android.ui.theme.CompagnonType
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop

/** The leading icon of [SearchField]: the magnifier (search), or a Basilic « + » (« Ajouter un article »). */
internal enum class FieldIcon { SEARCH, ADD }

/**
 * « Champ de saisie »: Surface, Trait border, radius 16, 52 high, icon on the left. The text lives in [state]
 * (hoisted, so the caller can clear it after an addition); every change after the first composition is reported
 * to [onQueryChange].
 */
@Composable
internal fun SearchField(
    state: TextFieldState,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    icon: FieldIcon = FieldIcon.SEARCH,
    onSubmit: (() -> Unit)? = null,
) {
    val currentOnQueryChange by rememberUpdatedState(onQueryChange)
    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() }
            .distinctUntilChanged()
            .drop(1)
            .collect { currentOnQueryChange(it) }
    }
    val clearLabel = stringResource(R.string.search_clear)
    BasicTextField(
        state = state,
        modifier = modifier
            .testTag(SEARCH_FIELD_TAG)
            .semantics { contentDescription = placeholder },
        textStyle = CompagnonType.Body.copy(color = CompagnonColors.Ink),
        cursorBrush = SolidColor(CompagnonColors.Basil),
        lineLimits = TextFieldLineLimits.SingleLine,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = if (onSubmit != null) ImeAction.Done else ImeAction.Search,
        ),
        onKeyboardAction = KeyboardActionHandler { performDefaultAction ->
            if (onSubmit != null) onSubmit() else performDefaultAction()
        },
        decorator = TextFieldDecorator { innerTextField ->
            Row(
                modifier = Modifier
                    .heightIn(min = 52.dp)
                    .clip(CompagnonShapes.Field)
                    .background(CompagnonColors.Surface)
                    .border(1.dp, CompagnonColors.Line, CompagnonShapes.Field)
                    .padding(start = 14.dp, end = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when (icon) {
                    FieldIcon.SEARCH -> Icon(
                        AppIcons.Search,
                        contentDescription = null,
                        tint = CompagnonColors.InkSoft,
                        modifier = Modifier.size(20.dp),
                    )
                    FieldIcon.ADD -> Icon(
                        AppIcons.Add,
                        contentDescription = null,
                        tint = CompagnonColors.Basil,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (state.text.isEmpty()) {
                        Text(text = placeholder, style = CompagnonType.Body, color = CompagnonColors.InkMuted, maxLines = 1)
                    }
                    innerTextField()
                }
                if (state.text.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .clickable(role = Role.Button) { state.clearText() }
                            .semantics { contentDescription = clearLabel },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(AppIcons.Close, contentDescription = null, tint = CompagnonColors.InkSoft, modifier = Modifier.size(18.dp))
                    }
                } else {
                    Box(Modifier.size(width = 10.dp, height = 44.dp))
                }
            }
        },
    )
}

/** Test tag of [SearchField]'s text field. */
internal const val SEARCH_FIELD_TAG: String = "search_field"
