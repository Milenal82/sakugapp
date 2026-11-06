// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import android.content.res.Configuration
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.media3.common.Player
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sakugapp.data.*
import com.sakugapp.ui.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class FrameControlsDeviceTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private fun fixture()=File(ui.activity.cacheDir,"frame-controls.mp4").apply{
        InstrumentationRegistry.getInstrumentation().context.assets.open("frame-index-vfr.mp4").use{input->outputStream().use{input.copyTo(it)}}
    }
    private fun counter()=ui.onNodeWithTag("frame-counter").fetchSemanticsNode().config[SemanticsProperties.Text].joinToString(""){it.text}.substringBefore("·").substringBefore("/").trim()
    @Test fun rapidRealFrameStepsPauseAndShareConfirmedPointsWithLoop(){
        val file=fixture();var player:Player?=null;var instances=0
        fun matches(check:(Player)->Boolean):Boolean{var answer=false;ui.runOnUiThread{player?.let{answer=check(it)}};return answer}
        try{
            ui.runOnUiThread{ui.activity.setContent{SakugappTheme(false){Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState())){
                VideoPanel(file.toURI().toString(),onPlayerReady={player=it;instances++})
            }}}}
            ui.waitUntil(20000){matches{it.playbackState==Player.STATE_READY}}
            ui.runOnUiThread{player!!.pause();player!!.seekTo(400)}
            ui.onNodeWithTag("step-1").performScrollTo().performClick()
            ui.waitUntil(20000){counter()=="2"}
            assertTrue(matches{!it.playWhenReady})
            val before=ui.onNodeWithTag("video-gestures").getUnclippedBoundsInRoot()
            ui.onNodeWithTag("more-controls").performClick()
            ui.onNodeWithTag("frame-precision").performScrollTo().assertTextEquals("Decoded frame index · verified timestamps")
            ui.onNodeWithTag("more-controls").performScrollTo()
            val after=ui.onNodeWithTag("video-gestures").getUnclippedBoundsInRoot()
            assertEquals(before.width.value,after.width.value,0.1f);assertEquals(before.height.value,after.height.value,0.1f)
            ui.onNodeWithTag("set-a").performScrollTo().performClick()
            ui.onNodeWithTag("set-b").performScrollTo().performClick()
            ui.onNodeWithTag("ab-toggle").assertIsNotEnabled()
            for(delta in listOf(1,2,3))ui.onNodeWithTag("step-$delta").performScrollTo().performClick()
            ui.waitUntil(20000){counter()=="8"}
            assertTrue(matches{!it.playWhenReady})
            ui.onNodeWithTag("set-b").performScrollTo().performClick()
            ui.onNodeWithTag("ab-interval").assertTextContains("0:00.600",substring=true).assertTextContains("0:01.700",substring=true)
            ui.onNodeWithTag("ab-toggle").assertIsEnabled().performClick().assertIsSelected()
            assertEquals(1,instances)
            for(delta in listOf(-3,-2,-1))ui.onNodeWithTag("step-$delta").performScrollTo().performClick()
            ui.waitUntil(20000){ui.onAllNodesWithText("Locating frame…").fetchSemanticsNodes().isEmpty()}
            assertTrue(matches{!it.playWhenReady})
            UiEvidence.capture("frames-inline-confirmed.png")
            ui.runOnUiThread{player!!.seekTo(1000)}
            ui.waitUntil(15000){matches{it.playbackState==Player.STATE_READY && !it.playWhenReady && it.currentPosition==1000L}}
            assertEquals(1,instances)
        }finally{file.delete()}
    }
    @Test fun queuedStepsClampedAtBothEndsAndResetDoesNotReuseAnotherVideosSelection()=runBlocking{
        val file=fixture();val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
        lateinit var player:androidx.media3.exoplayer.ExoPlayer;lateinit var stepper:FrameStepper;var created=false;var runner:Job?=null
        try{
            withContext(Dispatchers.Main){player=androidx.media3.exoplayer.ExoPlayer.Builder(ui.activity).build();created=true;player.setMediaItem(androidx.media3.common.MediaItem.fromUri(file.toURI().toString()));player.seekTo(400)
                stepper=FrameStepper(player,FrameDecoder(ui.activity,file.toURI().toString()),{10f},{2200L});runner=scope.launch{stepper.run()}
                for(n in listOf(1,2,3,3,3))stepper.step(n)
            }
            withTimeout(30000){while(withContext(Dispatchers.Main){stepper.pending>0})delay(25)}
            withContext(Dispatchers.Main){assertEquals(11,stepper.confirmed!!.index);assertEquals(2100L,stepper.confirmed!!.timeMs);assertFalse(player.playWhenReady)
                for(n in listOf(-3,-2,-1,-3,-3))stepper.step(n)
            }
            withTimeout(30000){while(withContext(Dispatchers.Main){stepper.pending>0})delay(25)}
            withContext(Dispatchers.Main){assertEquals(0,stepper.confirmed!!.index);assertEquals(400L,stepper.confirmed!!.timeMs);assertFalse(player.playWhenReady);stepper.reset();assertNull(stepper.confirmed)}
        }finally{withContext(Dispatchers.Main){runner?.cancelAndJoin();if(created)player.release()};scope.cancel();file.delete()}
    }
    @Test fun estimatedFallbackWaitsForRenderedSeekAndStaysPaused()=runBlocking{
        val file=fixture();val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
        lateinit var player:androidx.media3.exoplayer.ExoPlayer;lateinit var stepper:FrameStepper;var runner:Job?=null;var rendered=0
        try{
            ui.runOnUiThread{
                player=androidx.media3.exoplayer.ExoPlayer.Builder(ui.activity).build()
                player.addListener(object:Player.Listener{override fun onRenderedFirstFrame(){rendered++}})
                player.setMediaItem(androidx.media3.common.MediaItem.fromUri(file.toURI().toString()));player.prepare()
                // A real working player paired with unavailable index decoding exercises the fallback.
                stepper=FrameStepper(player,FrameDecoder(ui.activity,File(ui.activity.cacheDir,"no-frame-index.mp4").toURI().toString()),{10f},{2200L})
                runner=scope.launch{stepper.run()}
                ui.activity.setContent{androidx.compose.ui.viewinterop.AndroidView(factory={VideoGestureHost(it).apply{playerView.player=player}},modifier=Modifier.fillMaxSize())}
            }
            ui.waitUntil(20000){var ready=false;ui.runOnUiThread{ready=player.playbackState==Player.STATE_READY && rendered>0};ready}
            var before=0;ui.runOnUiThread{player.seekTo(400);before=rendered;stepper.step(3);assertTrue(stepper.pending>0)}
            ui.waitUntil(20000){var done=false;ui.runOnUiThread{done=stepper.pending==0 && stepper.confirmed!=null};done}
            ui.runOnUiThread{val frame=stepper.confirmed!!;assertFalse(frame.decoded);assertFalse(frame.exactTime);assertEquals(700L,frame.timeMs);assertTrue(rendered>before);assertFalse(player.playWhenReady)}
        }finally{withContext(Dispatchers.Main){runner?.cancelAndJoin();player.release()};scope.cancel();file.delete()}
    }
    @Test fun frameBarAndMoreKeepVideoGeometryAndSamePlayerInBothThemes(){
        val file=fixture();var player:Player?=null;var instances=0
        var selectedPost by mutableLongStateOf(100)
        val original=PostParser.posts(InstrumentationRegistry.getInstrumentation().context.assets.open("library-real-posts.json").bufferedReader().use{it.readText()}).first()
        try{
            for(light in listOf(false,true)){
                ui.runOnUiThread{player=null;ui.activity.setContent{SakugappTheme(light,if(light)1.25f else .75f){key(light){Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState())){VideoPanel(file.toURI().toString(),downloadPost=original.copy(id=selectedPost),onPlayerReady={player=it;instances++})}}}}}
                ui.waitUntil(20000){var ready=false;ui.runOnUiThread{ready=player?.playbackState==Player.STATE_READY};ready}
                val identity=player
                ui.onNodeWithTag("step-1").performScrollTo().performClick()
                ui.waitUntil(20000){counter()=="2"}
                val surface=ui.onNodeWithTag("video-gestures").getUnclippedBoundsInRoot()
                val toolbar=ui.onNodeWithTag("frame-toolbar").fetchSemanticsNode().boundsInRoot
                val full=ui.onNodeWithTag("toggle-fullscreen").fetchSemanticsNode().boundsInRoot
                val more=ui.onNodeWithTag("more-controls").fetchSemanticsNode().boundsInRoot
                for(delta in listOf(-3,-2,-1,1,2,3)){
                    val bounds=ui.onNodeWithTag("step-$delta").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                    assertTrue(bounds.right<=full.left+0.5f);assertTrue(bounds.left>=0);assertTrue(bounds.width>0 && bounds.height>0)
                }
                assertTrue(full.right<=more.left+0.5f);assertTrue(toolbar.top>=surface.bottom.value*ui.activity.resources.displayMetrics.density-0.5f)
                ui.onNodeWithTag("more-controls").performClick()
                val expanded=ui.onNodeWithTag("video-gestures").getUnclippedBoundsInRoot()
                assertEquals(surface.height.value,expanded.height.value,0.1f);assertEquals(surface.width.value,expanded.width.value,0.1f)
                assertSame(identity,player);assertEquals("2",counter())
                UiEvidence.capture(if(light)"frames-layout-light.png"else"frames-layout-dark.png")
                ui.onNodeWithTag("set-a").performScrollTo().performClick()
                ui.runOnUiThread{selectedPost++}
                ui.waitUntil(15000){player!==identity}
                ui.onNodeWithTag("more-controls").performScrollTo().performClick()
                ui.onNodeWithTag("ab-toggle").performScrollTo().assertIsNotEnabled()
            }
        }finally{file.delete()}
    }
}
