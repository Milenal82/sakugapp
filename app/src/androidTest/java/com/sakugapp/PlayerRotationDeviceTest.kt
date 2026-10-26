// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import android.content.res.Configuration
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.media3.common.Player
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.sakugapp.data.AppSettings
import com.sakugapp.ui.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class PlayerRotationDeviceTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    @Test fun manualRotationWithSystemLockPreservesThePlayerPositionAndControls(){
        val instrumentation=InstrumentationRegistry.getInstrumentation();val device=UiDevice.getInstance(instrumentation)
        val oldLock=device.executeShellCommand("settings get system accelerometer_rotation").trim()
        val file=File(ui.activity.cacheDir,"rotation-fixture.mp4")
        instrumentation.context.assets.open("frame-index-vfr.mp4").use{input->file.outputStream().use{input.copyTo(it)}}
        val instances=AtomicInteger();var player:Player?=null
        fun playerMatches(predicate:(Player?)->Boolean):Boolean {
            var result=false;ui.runOnUiThread{result=predicate(player)};return result
        }
        val activity=ui.activity
        fun awaitStage(name:String,timeout:Long,condition:()->Boolean){
            println("Rotation stage: $name")
            try{ui.waitUntil(timeout){condition()}}
            catch(failure:Exception){
                var diagnostic=""
                ui.runOnUiThread{diagnostic="stage=$name state=${player?.playbackState} error=${player?.playerError?.errorCodeName} orientation=${ui.activity.resources.configuration.orientation} requested=${ui.activity.requestedOrientation} instances=${instances.get()}"}
                throw AssertionError("Rotation stage failed: $diagnostic",failure)
            }
        }
        try {
            device.executeShellCommand("settings put system accelerometer_rotation 0")
            ui.runOnUiThread{activity.setContent{SakugappTheme(false){VideoPanel(file.toURI().toString(),AppSettings(),onPlayerReady={player=it;instances.incrementAndGet()})}}}
            awaitStage("video ready",15000){playerMatches{it?.playbackState==Player.STATE_READY}}
            ui.onNodeWithText("1.5×",substring=false).performClick()
            ui.runOnUiThread{player!!.pause();player!!.seekTo(400)}
            awaitStage("seek paused video",5000){playerMatches{it!=null && it.currentPosition>=350}}
            val retained=player
            ui.onNodeWithText("Full screen",substring=false).performClick()
            ui.onNodeWithTag("rotate-video").performClick()
            awaitStage("manual landscape",15000){ui.activity.resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE}
            ui.runOnUiThread{
                assertSame(retained,player);assertEquals(1,instances.get());assertFalse(player!!.playWhenReady)
                assertEquals(1.5f,player!!.playbackParameters.speed,0.001f);assertEquals(Player.REPEAT_MODE_ONE,player!!.repeatMode);assertEquals(0f,player!!.volume,0.001f)
                assertTrue(kotlin.math.abs(player!!.currentPosition-400)<=100)
            }
            device.pressBack()
            awaitStage("restore portrait",15000){ui.activity.resources.configuration.orientation==Configuration.ORIENTATION_PORTRAIT}
            ui.onNodeWithText("Full screen",substring=false).assertExists()
            assertSame(activity,ui.activity);assertEquals(1,instances.get())
        }finally{device.executeShellCommand("settings put system accelerometer_rotation ${oldLock.toIntOrNull() ?: 1}");file.delete()}
    }
}
