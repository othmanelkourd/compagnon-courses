package com.compagnon.courses.android.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * The theme of the app, from the shared charter (`design/CHARTE.md`): fixed Basilic palette ([CompagnonColors]),
 * Bricolage Grotesque and Figtree ([CompagnonType]), the charter's radii ([CompagnonShapes]).
 *
 * Light only, even when the system is dark (the dark mode is not designed yet), and no dynamic colour.
 */
@Composable
fun CompagnonTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CompagnonColorScheme,
        typography = CompagnonTypography,
        shapes = CompagnonMaterialShapes,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides CompagnonColors.Ink,
            LocalTextStyle provides CompagnonType.Body,
            content = content,
        )
    }
}
