// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import com.sakugapp.data.*
import com.sakugapp.ui.*
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.*
import org.junit.Test

class PersonalizationTest {
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
