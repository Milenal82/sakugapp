// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp
import com.sakugapp.data.*
import org.junit.Assert.*
import org.junit.Test
class FramePositionTest {
    @Test fun fallbackUsesValidMetadataAndClampsAtTheLastFrame(){
        assertEquals(24.0,FramePosition.fps(null),0.0)
        for(value in listOf(0f,-1f,Float.NaN,Float.POSITIVE_INFINITY,1000f))assertEquals(24.0,FramePosition.fps(value),0.0)
        val rate=FramePosition.fps(23.976f);assertEquals(24,FramePosition.count(1000,rate))
        assertEquals(0,FramePosition.move(0,-3,24));assertEquals(23,FramePosition.move(22,3,24))
        assertEquals(999L,FramePosition.time(Int.MAX_VALUE,rate,1000))
        assertEquals(42L,FramePosition.time(1,rate,1000))
    }
    @Test fun clipNamesRetainIdExtensionAndRangeUnderTheByteLimit(){
        val value=PostParser.posts(javaClass.getResource("/live/posts.json")!!.readText()).first().copy(tags=listOf(Tag("長".repeat(100),TagCategory.ARTIST)),source="episode 12")
        for(mode in ClipMode.entries){val name=MediaFileNames.forClip(value,"webm",ClipRange(600,1400),mode)
            assertTrue(name.toByteArray().size<=MediaFileNames.MAX_BYTES)
            assertTrue(name.contains("_${value.id}_AB_600-1400_"));assertTrue(name.endsWith(".webm"));assertFalse(name.contains('/'))
        }
        assertThrows(IllegalArgumentException::class.java){ClipRange(100,100)}
        assertThrows(IllegalArgumentException::class.java){ClipRange(-1,100)}
    }
}
