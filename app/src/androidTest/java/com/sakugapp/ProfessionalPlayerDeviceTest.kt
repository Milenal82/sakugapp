// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.media3.common.Player
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import com.sakugapp.data.AppSettings
import com.sakugapp.ui.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class ProfessionalPlayerDeviceTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    @Test fun actualLoopPinchPanDoubleTapAndSwipeRespectTransportControls(){
        val instrumentation=InstrumentationRegistry.getInstrumentation();val device=UiDevice.getInstance(instrumentation)
        val file=File(ui.activity.cacheDir,"professional-fixture.mp4")
        instrumentation.context.assets.open("frame-index-vfr.mp4").use{input->file.outputStream().use{input.copyTo(it)}}
        var player:Player?=null;val seeks=AtomicInteger();val directions=mutableListOf<Int>()
        fun matches(test:(Player)->Boolean):Boolean{var result=false;ui.runOnUiThread{player?.let{result=test(it)}};return result}
        fun host(view:View=ui.activity.window.decorView):VideoGestureHost?{
            if(view is VideoGestureHost)return view
            if(view is ViewGroup)for(i in 0 until view.childCount)host(view.getChildAt(i))?.let{return it}
            return null
        }
        try{
            ui.runOnUiThread{ui.activity.setContent{SakugappTheme(false){VideoPanel(file.toURI().toString(),AppSettings(),onPlayerReady={
                player=it;it.addListener(object:Player.Listener{
                    override fun onPositionDiscontinuity(old:Player.PositionInfo,new:Player.PositionInfo,reason:Int){if(reason==Player.DISCONTINUITY_REASON_SEEK)seeks.incrementAndGet()}
                })
            },onNavigate={directions+=it;false})}}}
            ui.waitUntil(20000){matches{it.playbackState==Player.STATE_READY}}
            ui.onNodeWithText("Set A").assertDoesNotExist()
            ui.onNodeWithTag("more-controls").performClick()
            ui.runOnUiThread{player!!.pause();player!!.seekTo(500)}
            ui.waitUntil(10000){matches{it.playbackState==Player.STATE_READY && it.currentPosition==500L}}
            ui.onNodeWithText("Set A",substring=false).performScrollTo().performClick()
            ui.runOnUiThread{player!!.seekTo(1200)}
            ui.waitUntil(10000){matches{it.playbackState==Player.STATE_READY && it.currentPosition==1200L}}
            ui.onNodeWithText("Set B",substring=false).performScrollTo().performClick()
            ui.onNodeWithTag("ab-toggle").performScrollTo().performClick()
            val beforeLoop=seeks.get()
            ui.runOnUiThread{player!!.seekTo(800);player!!.play()}
            ui.waitUntil(15000){seeks.get()>beforeLoop+1 && matches{it.currentPosition in 500..700}}
            ui.onNodeWithText("Disable A–B loop",substring=false).assertExists()
            ui.runOnUiThread{player!!.pause()}
            ui.onNodeWithText("Full screen",substring=false).performClick()
            ui.onNodeWithTag("rotate-video").performClick()
            ui.waitUntil(15000){ui.activity.resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE}
            ui.onNodeWithText("Disable A–B loop",substring=false).assertExists()
            ui.onNodeWithText("Exit full screen",substring=false).performClick()
            ui.waitUntil(15000){ui.activity.resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_PORTRAIT}
            ui.onNodeWithTag("ab-toggle").performScrollTo().performClick()
            ui.runOnUiThread{player!!.pause();player!!.seekTo(500)}
            ui.waitUntil(10000){matches{it.playbackState==Player.STATE_READY && it.currentPosition==500L}}
            ui.onNodeWithTag("open-frame-controls").performScrollTo().performClick()
            ui.onNodeWithText("Decode local video",substring=false).performClick()
            ui.waitUntil(20000){ui.onAllNodesWithContentDescription("Decoded frame").fetchSemanticsNodes().isNotEmpty()}
            ui.onNodeWithTag("frame-number").performScrollTo().performTextReplacement("1")
            ui.onNodeWithTag("go-to-frame").performScrollTo().performClick()
            ui.onNodeWithTag("exact-next-frame").performScrollTo().performClick()
            ui.waitUntil(5000){ui.onAllNodesWithText("2 / 12",substring=false).fetchSemanticsNodes().isNotEmpty()}
            ui.onNodeWithTag("exact-previous-frame").performClick()
            ui.waitUntil(5000){ui.onAllNodesWithText("1 / 12",substring=false).fetchSemanticsNodes().isNotEmpty()}
            ui.onNodeWithText("Close",substring=false).performClick()
            ui.waitUntil(15000){matches{it.playbackState==Player.STATE_READY && !it.playWhenReady}}
            val beforeDouble=seeks.get()
            ui.onNodeWithTag("video-gestures").performTouchInput{doubleClick()}
            ui.waitUntil(5000){matches{it.playWhenReady}}
            Thread.sleep(350) // Separate two intentional double taps beyond Android's double-tap timeout.
            ui.onNodeWithTag("video-gestures").performTouchInput{advanceEventTime(400);doubleClick()}
            ui.waitUntil(5000){matches{!it.playWhenReady}}
            assertEquals("Double tap must not seek",beforeDouble,seeks.get())
            val native=device.wait(Until.findObject(By.desc("Video frame rendered")),15000)
            assertNotNull(native);native.pinchOpen(0.6f,1600)
            ui.waitUntil(10000){var zoom=1f;ui.runOnUiThread{zoom=host()!!.transform.scale};zoom>1.05f}
            ui.onNodeWithTag("video-gestures").performTouchInput{swipeLeft(durationMillis=400)}
            ui.runOnUiThread{assertTrue(host()!!.transform.panX!=0f);assertTrue(directions.isEmpty())}
            ui.onNodeWithTag("reset-zoom").performScrollTo().performClick()
            ui.runOnUiThread{assertEquals(1f,host()!!.transform.scale,0.001f)}
            ui.onNodeWithTag("video-gestures").performTouchInput{swipeLeft(durationMillis=400)}
            ui.waitUntil(5000){directions==listOf(1)}
            UiEvidence.capture("professional-controls.png")
        }finally{file.delete()}
    }
}
