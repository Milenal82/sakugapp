// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.test.platform.app.InstrumentationRegistry
import com.sakugapp.ui.*
import org.junit.*
import org.junit.Assert.*
import java.io.File

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlayerLockDeviceTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private fun hosts():List<VideoGestureHost>{val found=mutableListOf<VideoGestureHost>();fun scan(v:android.view.View){if(v is VideoGestureHost)found+=v;if(v is android.view.ViewGroup)for(i in 0 until v.childCount)scan(v.getChildAt(i))};scan(ui.activity.window.decorView);return found}
    @Test fun externalFrameStepsNeverShowControllerAndLockBlocksVideoGestures(){
        val file=File(ui.activity.cacheDir,"lock-test.mp4").apply{writeBytes(InstrumentationRegistry.getInstrumentation().context.assets.open("frame-index-vfr.mp4").use{it.readBytes()})}
        var player:Player?=null;var made=0
        fun checkPlayer(test:(Player)->Boolean):Boolean{var yes=false;ui.runOnUiThread{player?.let{yes=test(it)}};return yes}
        try{
            ui.runOnUiThread{ui.activity.setContent{SakugappTheme(false){Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState())){VideoPanel(file.toURI().toString(),onPlayerReady={player=it;made++})}}}}
            ui.waitUntil(20000){checkPlayer{it.playbackState==Player.STATE_READY}}
            ui.onNodeWithTag("video-gestures").performTouchInput{click()}
            ui.waitUntil(5000){var shown=false;ui.runOnUiThread{shown=hosts().single().playerView.isControllerFullyVisible};shown}
            ui.onNodeWithTag("step-1").performScrollTo().performClick()
            ui.waitUntil(20000){checkPlayer{it.playbackState==Player.STATE_IDLE}}
            ui.runOnUiThread{assertFalse(hosts().single().playerView.isControllerFullyVisible)}
            ui.onNodeWithTag("lock-player").performClick()
            for(delta in listOf(1,2,3,-1,-2,-3))ui.onNodeWithTag("step-$delta").performClick()
            ui.waitUntil(20000){ui.onNodeWithTag("frame-counter").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].any{it.text.startsWith("2·") || it.text.startsWith("2/")}}
            ui.onNodeWithTag("video-gestures").performTouchInput{click();doubleClick();swipeLeft()}
            ui.runOnUiThread{val host=hosts().single();assertTrue(host.isPlayerLocked);assertFalse(host.playerView.useController);assertFalse(host.playerView.isControllerFullyVisible);assertFalse(player!!.playWhenReady)}
            ui.onNodeWithTag("more-controls").performClick();ui.onNodeWithTag("set-a").performScrollTo().performClick()
            ui.onNodeWithTag("step-3").performScrollTo().performClick()
            ui.waitUntil(20000){ui.onNodeWithTag("frame-counter").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].any{it.text.startsWith("5·") || it.text.startsWith("5/")}}
            ui.onNodeWithTag("set-b").performScrollTo().performClick();ui.onNodeWithTag("ab-toggle").performScrollTo().assertIsEnabled()
            ui.onNodeWithTag("toggle-fullscreen").performScrollTo().performClick()
            ui.onNodeWithTag("lock-player").assertIsDisplayed();ui.onNodeWithTag("step-1").assertIsDisplayed()
            ui.onNodeWithTag("lock-player").performClick();ui.onNodeWithTag("video-gestures").performTouchInput{click()}
            ui.waitUntil(5000){ui.onAllNodesWithTag("toggle-fullscreen").fetchSemanticsNodes().isNotEmpty()}
            ui.onNodeWithTag("toggle-fullscreen").assertIsDisplayed().performClick()
            assertEquals(1,made);assertTrue(checkPlayer{!it.playWhenReady})
            UiEvidence.capture("player-lock-inline.png")
        }finally{file.delete()}
    }
    @Test fun oneRowToolbarFitsNarrowAndTabletWidthsWithNoFrameDescriptions(){
        var width by mutableStateOf(296.dp)
        ui.runOnUiThread{ui.activity.setContent{SakugappTheme(false,1.25f){Box(Modifier.width(width)){FrameControlBar(null,0,0,true,{},{repeat(3){androidx.compose.material3.IconButton(onClick={},modifier=Modifier.size(36.dp)){}}})}}}}
        for(w in listOf(296.dp,360.dp,600.dp,840.dp)){
            ui.runOnUiThread{width=w};ui.waitForIdle()
            val nodes=listOf(-3,-2,-1,1,2,3).map{ui.onNodeWithTag("step-$it").fetchSemanticsNode()}
            nodes.forEach{assertFalse(it.config.contains(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription))}
            for(pair in nodes.zipWithNext()){assertEquals(pair.first.boundsInRoot.top,pair.second.boundsInRoot.top,.5f);assertTrue(pair.first.boundsInRoot.right<=pair.second.boundsInRoot.left+.5f)}
        }
    }
}
