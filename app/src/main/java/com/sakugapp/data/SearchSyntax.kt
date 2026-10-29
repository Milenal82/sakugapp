// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.data

/** The site's query language is sent intact. The builder adds tokens, never expands OR into AND. */
object SearchSyntax {
    data class Draft(val expression:String,val order:String="keep",val rating:String="keep")
    /** Select only unique, understood fields; retain every original token in the editable expression. */
    fun draft(query:String):Draft {
        if(query.any{it=='\'' || it=='"'})return Draft(query)
        fun value(fieldName:String,allowed:List<String>):String {
            val fields=tokens(query).filter{field(it)==fieldName}
            val token=fields.singleOrNull() ?: return "keep"
            return token.removePrefix("$fieldName:").takeIf{token.startsWith("$fieldName:") && it in allowed} ?: "keep"
        }
        return Draft(query,value("order",orders),value("rating",listOf("safe","questionable","explicit")))
    }
    val orders = listOf("id", "id_desc", "score", "score_asc", "mpixels", "mpixels_asc", "landscape", "portrait", "vote", "random")
    val scorePresets = listOf("score:>=3", "score:>=100")
    fun score(query:String,preset:String):String {
        require(preset in scorePresets)
        return (tokens(query).filterNot{field(it)=="score"}+preset).joinToString(" ")
    }
    fun tokens(query: String) = query.trim().split(Regex("\\s+")).filter(String::isNotBlank)
    fun field(token: String): String? = token.removePrefix("-").substringBefore(':', "").takeIf(String::isNotBlank)
    fun filters(query: String, order: String = "keep", rating: String = "keep"): String {
        require(order in orders || order in setOf("keep", "date"))
        require(rating in setOf("keep", "all", "safe", "questionable", "explicit"))
        if (order == "keep" && rating == "keep") return query.trim()
        val base = tokens(query).filterNot {
            (order != "keep" && field(it) == "order") || (rating != "keep" && field(it) == "rating")
        }
        return (base + listOfNotNull(order.takeIf { it != "keep" && it != "date" }?.let { "order:$it" },
            rating.takeIf { it != "keep" && it != "all" }?.let { "rating:$it" })).joinToString(" ")
    }
    fun add(query: String, name: String, mode: String): String {
        require(mode in setOf("and", "or", "exclude"))
        require(name.isNotBlank() && name.none(Char::isWhitespace) && !name.startsWith('-') && !name.startsWith('~'))
        require(mode != "or" || ':' !in name)
        val token = (when(mode) { "or" -> "~"; "exclude" -> "-"; else -> "" }) + name
        return (tokens(query) + token).distinct().joinToString(" ")
    }
    fun suggestionPrefix(query: String): String? = query.substringAfterLast(' ').removePrefix("-").removePrefix("~")
        .takeIf { it.length >= 2 && ':' !in it && '*' !in it }
    fun suggestion(query: String, name: String): String {
        val last = query.substringAfterLast(' ')
        val prefix = query.substringBeforeLast(' ', "").let { if(it.isBlank()) "" else "$it " }
        return prefix + (when { last.startsWith('~') -> "~"; last.startsWith('-') -> "-"; else -> "" }) + name
    }
    /** Informational constraints; never silently delete or reinterpret the user's expression. */
    fun warnings(query: String): List<String> = buildList {
        val terms = tokens(query)
        val tags = terms.filter { ':' !in it }
        if (tags.any { '*' in it } && tags.size > 1) add("Wildcard combinations may be limited by the website.")
        if (tags.any { it.startsWith('~') } && tags.any { !it.startsWith('~') }) add("Mixing OR tags with required or excluded tags may be limited by the website.")
        if (terms.mapNotNull(::field).groupingBy { it }.eachCount().any { it.value > 1 }) add("Repeated fields are ambiguous; use one value or range per field.")
        if ("order:vote" in terms && terms.none { it.startsWith("vote:") }) add("order:vote requires a vote:score:user search.")
    }
}
