// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import com.sakugapp.data.*
import com.sakugapp.ui.*
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test

class PersonalizationTest {
    @Test fun tabletDensityNeverOverwritesTheSavedChoiceOnAPhone(){
        assertEquals(listOf(1,2,3,4,6),GridPolicy.options(599))
        assertEquals(listOf(1,2,3,4,6,8,12),GridPolicy.options(600))
        assertEquals(6,GridPolicy.effective(6,320))
        assertEquals(12,GridPolicy.effective(12,800));assertEquals(4,GridPolicy.effective(12,360))
        assertEquals(8,GridPolicy.effective(8,600));assertEquals(3,GridPolicy.effective(3,360))
        assertEquals(2,GridPolicy.effective(5,800))
    }
    @Test fun legacyExplicitThemeWinsAndOnlyNewInstallFollowsSystem(){
        assertEquals("light",PreferenceRules.legacyTheme(null,true,true))
        assertEquals("dark",PreferenceRules.legacyTheme(null,false,true))
        assertEquals("dark",PreferenceRules.legacyTheme(null,null,true))
        assertEquals("system",PreferenceRules.legacyTheme(null,null,false))
        assertEquals("system",PreferenceRules.legacyTheme("system",true,true))
        assertFalse(themeIsLight("system",true));assertTrue(themeIsLight("system",false))
        assertFalse(themeIsLight("dark",false));assertTrue(themeIsLight("light",true))
    }
    @Test fun allTextTokensRetainAAContrastAgainstTheirSurfaces(){
        for(light in listOf(false,true)){
            val scheme=appColors(light)
            val badge=scheme.surface.copy(alpha=0.96f).compositeOver(if(light)Color.Black else Color.White)
            val red=scoreColor(light).luminance();val ground=badge.luminance()
            assertTrue((maxOf(red,ground)+0.05f)/(minOf(red,ground)+0.05f)>=4.5f)
            for(color in listOf(scoreColor(light),artistColor(light),copyrightColor(light),scheme.onSurface,scheme.onSurfaceVariant)){
                val a=color.luminance();val b=scheme.surface.luminance()
                assertTrue("Token contrast in light=$light",(maxOf(a,b)+0.05f)/(minOf(a,b)+0.05f)>=4.5f)
            }
        }
    }
    @Test fun mediaTimeNeverGuessesAFrameOrAZeroDuration(){
        assertEquals("0:12",mediaTime(12345));assertEquals("1:01",mediaTime(61000))
        assertEquals("1:00:01",mediaTime(3601000))
        val post=PostParser.posts("""[{"id":1,"tags":"real_tag","score":4}]""").single()
        assertNull(post.durationMs)
    }
}
