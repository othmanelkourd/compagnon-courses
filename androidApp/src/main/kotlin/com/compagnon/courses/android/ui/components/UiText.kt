package com.compagnon.courses.android.ui.components

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource

/**
 * A localized text decided outside Compose (so the choice of wording is unit tested on the JVM) and resolved
 * against the resources at composition.
 */
internal sealed interface UiText {

    /** Text that is already localized (catalog content). */
    data class Raw(val text: String) : UiText

    data class Res(@param:StringRes val id: Int, val args: List<Any> = emptyList()) : UiText

    /** [count] selects the plural form; [args] fill the placeholders (usually the count itself). */
    data class Plural(@param:PluralsRes val id: Int, val count: Int, val args: List<Any> = listOf(count)) : UiText
}

@Composable
@ReadOnlyComposable
internal fun UiText.resolve(): String = when (this) {
    is UiText.Raw -> text
    is UiText.Res -> stringResource(id, *args.toTypedArray())
    is UiText.Plural -> pluralStringResource(id, count, *args.toTypedArray())
}

/** Resolves and joins [texts] with a space (sentences of one explanation). */
@Composable
@ReadOnlyComposable
internal fun resolveAll(texts: List<UiText>): String = texts.map { it.resolve() }.joinToString(" ")
