// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.data

import java.time.LocalDate

/** Only unambiguous calendar dates are adopted by the picker. Raw unsupported syntax stays intact. */
data class CalendarRange(val start:LocalDate,val end:LocalDate){init{require(start.year in 1..9999 && end.year in 1..9999 && !end.isBefore(start))};val token get()="date:$start...$end"}
object DateRangeFilter {
    fun read(query:String):CalendarRange? {
        val tokens=SearchSyntax.tokens(query).filter{SearchSyntax.field(it)=="date"}
        val token=tokens.singleOrNull()?.takeIf{it.startsWith("date:")} ?: return null
        val value=token.removePrefix("date:")
        if(!value.matches(Regex("\\d{4}-\\d{2}-\\d{2}(?:\\.{2,3}\\d{4}-\\d{2}-\\d{2})?")))return null
        return runCatching{val dates=value.split(Regex("\\.{2,3}"));CalendarRange(LocalDate.parse(dates[0]),LocalDate.parse(dates.last()))}.getOrNull()
    }
    fun unsupported(query:String)=SearchSyntax.tokens(query).any{SearchSyntax.field(it)=="date"} && read(query)==null
    fun set(query:String,range:CalendarRange?):String {
        require(!unsupported(query)){"date_conflict"}
        read(query) ?: return if(range==null)query else (query.trim()+" "+range.token).trim()
        val token=SearchSyntax.tokens(query).single{SearchSyntax.field(it)=="date"}
        return SearchSyntax.tokens(query).mapNotNull{if(it==token)range?.token else it}.joinToString(" ")
    }
    fun popular(period:String,today:LocalDate=LocalDate.now()):String {
        val start=when(period){"day"->today;"week"->today.minusDays((today.dayOfWeek.value-1).toLong());"month"->today.withDayOfMonth(1);"year"->today.withDayOfYear(1);else->error("period")}
        return (if(start==today)"date:$today"else CalendarRange(start,today).token)+" order:score"
    }
}
