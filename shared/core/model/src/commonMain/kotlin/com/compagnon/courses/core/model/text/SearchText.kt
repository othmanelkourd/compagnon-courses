package com.compagnon.courses.core.model.text

/**
 * Normalized form of a text for local search: lower case, without diacritics (« Œufs » → « oeufs »),
 * apostrophes and punctuation turned into spaces, whitespace collapsed.
 */
fun normalizeForSearch(text: String): String {
    val builder = StringBuilder(text.length)
    for (char in text.lowercase()) {
        val folded = FOLDED[char]
        when {
            folded != null -> builder.append(folded)
            char.isLetterOrDigit() -> builder.append(char)
            else -> builder.append(' ')
        }
    }
    return builder.toString().split(' ').filter { it.isNotEmpty() }.joinToString(" ")
}

private val FOLDED: Map<Char, String> = buildMap {
    fun fold(chars: String, to: String) = chars.forEach { put(it, to) }
    fold("àáâãäåā", "a")
    fold("çćč", "c")
    fold("èéêëēė", "e")
    fold("ìíîïī", "i")
    fold("ñń", "n")
    fold("òóôõöøō", "o")
    fold("ùúûüū", "u")
    fold("ýÿ", "y")
    fold("œ", "oe")
    fold("æ", "ae")
    fold("ß", "ss")
}
