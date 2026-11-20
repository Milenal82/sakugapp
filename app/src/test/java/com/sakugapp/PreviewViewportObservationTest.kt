// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import com.sakugapp.ui.*
import org.junit.Assert.*
import org.junit.Test

class PreviewViewportObservationTest {
    @Test fun defaultPreviewOffDoesNotObserveLayoutOnEveryScroll(){
        assertFalse(needsPreviewViewport(false,true,PreviewSelection()))
        assertFalse(needsPreviewViewport(true,false,PreviewSelection(12)))
    }
    @Test fun manualPlaybackAndUserPauseStillObserveScrollingAndVisibility(){
        assertTrue(needsPreviewViewport(false,true,PreviewSelection(12)))
        assertTrue(needsPreviewViewport(false,true,PreviewSelection(pausedByUser=true)))
        assertTrue(needsPreviewViewport(true,true,PreviewSelection()))
        val manual=PreviewSelection(12)
        assertNull(manual.retain(PreviewViewport(listOf(13),true,true,true,false)).id)
        val unpaused=PreviewSelection(pausedByUser=true).retain(PreviewViewport(listOf(13),true,true,true,false))
        assertFalse(unpaused.pausedByUser)
    }
}
