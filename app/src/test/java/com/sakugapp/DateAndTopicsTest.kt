// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import com.sakugapp.data.*
import java.time.LocalDate
import org.junit.Test
import org.junit.Assert.*

class DateAndTopicsTest {
    @Test fun realCalendarRangesAndInclusiveSingleDays(){
        for((a,b)in listOf("2024-02-29" to "2024-02-29","2025-12-31" to "2026-01-02","2026-01-31" to "2026-02-01")){
            val r=CalendarRange(LocalDate.parse(a),LocalDate.parse(b));assertEquals(r,DateRangeFilter.read(r.token))
        }
        assertEquals(LocalDate.of(2024,2,29),DateRangeFilter.read("date:2024-02-29")!!.end)
        for(q in listOf("date:2025-02-29","date:2026-02-30","date:2026-02-02...2026-01-01","date:>=2026-01-01","date:2026-01-01 date:2026-01-02")){
            assertTrue(q,DateRangeFilter.unsupported(q));assertThrows(IllegalArgumentException::class.java){DateRangeFilter.set(q,null)}
        }
    }
    @Test fun replacingAndClearingDatesKeepTheOtherRawConditions(){
        val q="a ~b ~c -d source:\"quoted value\" score:>=3 rating:safe order:score"
        val r=CalendarRange(LocalDate.of(2026,1,1),LocalDate.of(2026,1,31))
        assertEquals(q,DateRangeFilter.set(DateRangeFilter.set(q,r),null))
        val changed=DateRangeFilter.set("$q date:2025-01-01..2025-01-31",r)
        assertEquals(1,SearchSyntax.tokens(changed).count{SearchSyntax.field(it)=="date"});assertEquals(r,DateRangeFilter.read(changed))
        assertEquals(changed,SearchSyntax.filters(changed,"keep","keep"))
    }
    @Test fun popularDatesRecomputeAtClickTimeWithMondayWeekStart(){
        val date=LocalDate.of(2026,11,6)
        assertEquals("date:2026-11-02...2026-11-06 order:score",DateRangeFilter.popular("week",date))
        assertEquals("date:2026-11-01...2026-11-06 order:score",DateRangeFilter.popular("month",date))
        assertEquals("date:2026-01-01...2026-11-06 order:score",DateRangeFilter.popular("year",date))
        assertNotEquals(DateRangeFilter.popular("day",date),DateRangeFilter.popular("day",date.plusDays(1)))
    }
    @Test fun tagPagesRejectWrongTypesMissingCountsAndUnverifiedOrdering(){
        val body="""[{"id":5,"name":"artist_a","type":1,"count":20},{"id":4,"name":"artist_b","type":1,"count":10}]"""
        assertEquals(2,TagDirectory.parse(body,TagCategory.ARTIST,"date").size)
        assertEquals(20L,TagDirectory.parse(body,TagCategory.ARTIST,"count").first().count)
        for(b in listOf(body.replace("\"type\":1","\"type\":3"),body.replace("\"count\":10","\"count\":30"),body.replace("\"count\":10","\"count\":null"))){assertThrows(DataFailure::class.java){TagDirectory.parse(b,TagCategory.ARTIST,"count")}}
        assertTrue(TagDirectory.parse("[]",TagCategory.ARTIST,"count").isEmpty())
    }
}
