package com.compagnon.courses.android.ui.components

import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.compagnon.courses.android.R
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop

/**
 * A one-line search field. The text lives in [state] (hoisted, so the caller can clear it after an addition);
 * every change after the first composition is reported to [onQueryChange].
 */
@Composable
internal fun SearchField(
    state: TextFieldState,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onSubmit: (() -> Unit)? = null,
) {
    val currentOnQueryChange by rememberUpdatedState(onQueryChange)
    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() }
            .distinctUntilChanged()
            .drop(1)
            .collect { currentOnQueryChange(it) }
    }
    OutlinedTextField(
        state = state,
        modifier = modifier.testTag(SEARCH_FIELD_TAG),
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = if (state.text.isNotEmpty()) {
            {
                IconButton(onClick = { state.clearText() }) {
                    Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.search_clear))
                }
            }
        } else {
            null
        },
        lineLimits = TextFieldLineLimits.SingleLine,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = if (onSubmit != null) ImeAction.Done else ImeAction.Search,
        ),
        onKeyboardAction = KeyboardActionHandler { performDefaultAction ->
            if (onSubmit != null) onSubmit() else performDefaultAction()
        },
    )
}

/** Test tag of [SearchField]'s text field. */
internal const val SEARCH_FIELD_TAG: String = "search_field"
