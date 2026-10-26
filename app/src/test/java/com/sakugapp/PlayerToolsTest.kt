// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import com.sakugapp.data.*
import org.junit.Assert.*
import org.junit.Test

class PlayerToolsTest {
    @Test fun loopRequiresOrderedPointsWrapsAtTheBoundaryAndNeverLeaksAcrossANewWindow(){
        var loop=ABLoop().withA(400)
        assertThrows(IllegalArgumentException::class.java){loop.withB(399)}
        assertFalse(loop.activate(true).enabled)
        loop=loop.withB(900).activate(true)
        assertNull(loop.wrapPosition(899));assertEquals(400L,loop.wrapPosition(900));assertEquals(400L,loop.wrapPosition(0))
        assertNull(loop.activate(false).wrapPosition(900))
        assertFalse(loop.withA(1000).valid);assertNull(loop.withA(1000).bMs)
        assertFalse(ABLoop().valid);assertFalse(ABLoop().enabled)
    }
    @Test fun variablePresentationTimesUseTheActualSampleIndexAndRejectUnreliableMappings(){
        val timeline=FrameTimeline.verified(listOf(0,200000,80000,320000,600000),5)!!
        assertEquals(1,timeline.indexAt(100));assertEquals(2,timeline.indexAt(210));assertEquals(4,timeline.indexAt(9999))
        assertEquals(320L,timeline.timeMs(3))
        assertNull(FrameTimeline.verified(listOf(0,40000,40000),3))
        assertNull(FrameTimeline.verified(listOf(0,40000),3))
        assertNull(FrameTimeline.verified(listOf(400000,600000),2))
        assertNull(FrameTimeline.verified(listOf(-1,0),2))
    }
    @Test fun pinchAndPanAreFiniteBoundedAndCanReturnToTheOriginalSurface(){
        assertEquals(VideoTransform(),VideoTransform(Float.NaN,Float.NaN,Float.POSITIVE_INFINITY).bounded())
        val max=VideoTransform(99f,99f,-99f).bounded()
        assertEquals(5f,max.scale);assertEquals(2f,max.panX);assertEquals(-2f,max.panY)
        assertEquals(VideoTransform(),VideoTransform(1f,10f,-10f).bounded())
    }
}
