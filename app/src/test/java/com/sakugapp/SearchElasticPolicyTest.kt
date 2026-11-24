// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import com.sakugapp.ui.SearchElasticPolicy
import org.junit.Test
import org.junit.Assert.*

class SearchElasticPolicyTest {
    @Test fun shortContentCanMoveInBothDirectionsButNeverPastTheLimit(){
        assertEquals(10f,SearchElasticPolicy.drag(0f,40f,24f),.001f)
        assertEquals(-10f,SearchElasticPolicy.drag(0f,-40f,24f),.001f)
        var offset=0f
        repeat(1000){offset=SearchElasticPolicy.drag(offset,20f,24f)}
        assertEquals(24f,offset,.001f)
        repeat(1000){offset=SearchElasticPolicy.drag(offset,-20f,24f)}
        assertEquals(-24f,offset,.001f)
        assertEquals(offset,SearchElasticPolicy.drag(offset,Float.NaN,24f),0f)
    }
    @Test fun reversingUnwindsOnlyTheOverscrollAndLeavesTheRemainderForRealContent(){
        val consumed=SearchElasticPolicy.unwind(12f,-100f)
        assertEquals(-48f,consumed,.001f);assertEquals(-52f,-100f-consumed,.001f)
        assertEquals(0f,SearchElasticPolicy.drag(12f,consumed,24f),.001f)
        assertEquals(48f,SearchElasticPolicy.unwind(-12f,100f),.001f)
        assertEquals(0f,SearchElasticPolicy.unwind(0f,100f),0f)
        assertEquals(0f,SearchElasticPolicy.unwind(12f,100f),0f)
    }
}
