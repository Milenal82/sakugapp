// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import androidx.compose.runtime.saveable.SaveableStateRegistry
import com.sakugapp.ui.MainPageState
import org.junit.Test
import org.junit.Assert.*

class MainPageStateTest {
    @Test fun captureKeepsDraftAndScrollBeforeNavigationWithoutRetainingMedia(){
        val pages=MainPageState();var draft="draft";var scroll=21
        val registry=SaveableStateRegistry(null){true};registry.registerProvider("draft"){draft};registry.registerProvider("scroll"){scroll}
        pages.attach("old","posts",registry);pages.activate("old");draft="artist score:>=3";scroll=42;pages.capture()
        assertEquals(listOf("artist score:>=3"),pages.values("posts")!!["draft"]);assertEquals(listOf(42),pages.values("posts")!!["scroll"])
    }
    @Test fun overlappingEntriesCanRestoreTheSameRouteAndOldExitCannotOverwriteTheNewOne(){
        val pages=MainPageState();val old=SaveableStateRegistry(null){true};old.registerProvider("value"){"old"}
        pages.attach("old","profile",old);pages.activate("old");pages.capture()
        val fresh=SaveableStateRegistry(pages.values("profile")){true};assertEquals("old",fresh.consumeRestored("value"));fresh.registerProvider("value"){"new"}
        pages.attach("fresh","profile",fresh);pages.activate("fresh");pages.capture();pages.detach("old")
        assertEquals(listOf("new"),pages.values("profile")!!["value"])
    }
    @Test fun snapshotRoundTripRestoresIndependentMainPages(){
        val pages=MainPageState()
        for(route in listOf("search_home","posts","profile")){val r=SaveableStateRegistry(null){true};r.registerProvider("value"){route};pages.attach(route,route,r);pages.activate(route)}
        val restored=MainPageState(pages.snapshot())
        for(route in listOf("search_home","posts","profile"))assertEquals(listOf(route),restored.values(route)!!["value"])
        assertNull(restored.values("unknown"))
    }
}
