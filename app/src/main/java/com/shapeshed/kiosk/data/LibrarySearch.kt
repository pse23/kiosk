package com.shapeshed.kiosk.data

/**
 * True when every whitespace-separated term of [query] appears (ignoring case) in at least one of
 * [fields]. A blank query matches everything.
 */
fun matchesLibraryQuery(query: String, vararg fields: String?): Boolean {
    val terms = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return terms.all { term -> fields.any { it?.contains(term, ignoreCase = true) == true } }
}
