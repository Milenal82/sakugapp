// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.data

/** The site's query language is sent intact. The builder adds tokens, never expands OR into AND. */
object SearchSyntax {
    data class Draft(val expression:String,val order:String="keep",val rating:String="keep")
    /** Select only unique, understood fields; retain every original token in the editable expression. */
    fun draft(query:String):Draft {
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
    /** Keep quoted/escaped raw values together, including unfinished input; never rewrite their contents. */
    fun tokens(query:String):List<String> {
        val terms=mutableListOf<String>();var start=-1;var quote:Char?=null;var escaped=false
        for(index in query.indices){
            val char=query[index]
            if(start<0){if(char.isWhitespace())continue;start=index}
            if(escaped){escaped=false;continue}
            if(char=='\\'){escaped=true;continue}
            if(quote!=null){if(char==quote)quote=null}
            else if(char=='"' || (char=='\'' && (index==start || query[index-1]==':')))quote=char
            else if(char.isWhitespace()){terms+=query.substring(start,index);start=-1}
        }
        if(start>=0)terms+=query.substring(start)
        return terms
    }
    fun field(token:String):String? = token.takeUnless{it.any{char->char=='"' || char=='\''}}
        ?.removePrefix("-")?.substringBefore(':',"")?.takeIf(String::isNotBlank)
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
