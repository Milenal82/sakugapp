// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import androidx.compose.ui.graphics.Color
import com.sakugapp.data.*
import com.sakugapp.ui.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.pow

class FullThemeTest {
    private fun luminance(c:Color):Double{
        fun linear(v:Float)=if(v<=.04045f)v/12.92 else ((v+.055)/1.055).pow(2.4)
        return .2126*linear(c.red)+.7152*linear(c.green)+.0722*linear(c.blue)
    }
    private fun contrast(a:Color,b:Color):Double{val x=luminance(a);val y=luminance(b);return (maxOf(x,y)+.05)/(minOf(x,y)+.05)}
    @Test fun fourCompletePalettesUseTheirOwnBackgroundsAndReadableText(){
        for((family,light,background)in listOf(Triple("pink",true,0xFFFFF5F8),Triple("pink",false,0xFF211820),Triple("mint",true,0xFFF0FAF9),Triple("mint",false,0xFF142422),Triple("amber",true,0xFFFFF8EC),Triple("amber",false,0xFF18140F))){
            val colors=appColors(light,family)
            assertEquals(Color(background),colors.background)
            for((text,surface)in listOf(colors.onBackground to colors.background,colors.onSurface to colors.surface,colors.onSurfaceVariant to colors.surfaceContainerHigh,colors.onPrimary to colors.primary,colors.onSecondaryContainer to colors.secondaryContainer))
                assertTrue("$family/$light text contrast ${contrast(text,surface)}",contrast(text,surface)>=4.5)
            assertNotEquals(appColors(light).surfaceContainerHigh,colors.surfaceContainerHigh)
            assertEquals(colors.surface,colors.surfaceContainerLow)
        }
    }
    @Test fun legacyBlueMapsToACompleteMintPaletteAndFixedDefaultsAreIndependent(){
        assertEquals("mint",PreferenceRules.palette("blue"));assertEquals("mint",PreferenceRules.palette("mint"));assertEquals("pink",PreferenceRules.palette("pink"))
        assertEquals(appColors(true,"mint").background,appColors(true,"blue").background)
        val p=AppSettings();assertTrue(p.gestures && p.screenshot && p.loop && p.muted && p.mobilePreview);assertFalse(p.wifiImages)
    }
    @Test fun everyPaletteHasReadableSelectionAndRetainsSemanticTagColors(){
        for(light in listOf(false,true))for(family in listOf("default","mint","pink","amber")){
            val c=appColors(light,family)
            for((foreground,background)in listOf(c.onPrimaryContainer to c.primaryContainer,c.onSurface to c.surfaceContainerHigh,c.primary to c.surface,c.onErrorContainer to c.errorContainer))
                assertTrue("$family/$light contrast",contrast(foreground,background)>=4.5)
            assertEquals(c.primary,c.secondary)
            assertEquals(if(light)Color(0xFF7542B8)else Color(0xFFC59BFF),artistColor(light))
            assertEquals(if(light)Color(0xFF926000)else Color(0xFFFFD166),copyrightColor(light))
        }
    }
}
