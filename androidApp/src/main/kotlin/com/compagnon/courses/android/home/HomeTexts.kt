package com.compagnon.courses.android.home

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Today's date above the home headline, with the locale's [pattern] (`EEEE d MMMM` in French), capitalized:
 * « Dimanche 25 octobre ».
 */
internal fun formatHomeDate(date: LocalDate, pattern: String, locale: Locale): String =
    DateTimeFormatter.ofPattern(pattern, locale).format(date).replaceFirstChar { it.titlecase(locale) }
