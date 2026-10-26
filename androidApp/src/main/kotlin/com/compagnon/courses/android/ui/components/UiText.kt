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

    /** [args] fill the placeholders; an argument that is itself a [UiText] is resolved first. */
    data class Res(@param:StringRes val id: Int, val args: List<Any> = emptyList()) : UiText

    /** [count] selects the plural form; [args] fill the placeholders (usually the count itself). */
    data class Plural(@param:PluralsRes val id: Int, val count: Int, val args: List<Any> = listOf(count)) : UiText
}

@Composable
@ReadOnlyComposable
internal fun UiText.resolve(): String = when (this) {
    is UiText.Raw -> text
    is UiText.Res -> stringResource(id, *args.map { if (it is UiText) it.resolve() else it }.toTypedArray())
    is UiText.Plural -> pluralStringResource(id, count, *args.toTypedArray())
}
