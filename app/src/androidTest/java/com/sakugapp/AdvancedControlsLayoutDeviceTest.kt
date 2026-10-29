// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import android.content.res.Configuration
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.runtime.key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.media3.common.Player
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.sakugapp.data.*
import com.sakugapp.ui.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

/** Fresh physical phone/tablet profiles; actual player and decoded fixture, not a drawn UI mock. */
@RunWith(AndroidJUnit4::class)
class AdvancedControlsLayoutDeviceTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    @Test fun groupedControlsWrapAndRetainPlaybackSpeedInBothThemesAndLandscape(){
        val instrumentation=InstrumentationRegistry.getInstrumentation();val device=UiDevice.getInstance(instrumentation)
        val file=File(ui.activity.cacheDir,"controls-layout.mp4")
        instrumentation.context.assets.open("frame-index-vfr.mp4").use{input->file.outputStream().use{input.copyTo(it)}}
        var player:Player?=null
        val geometry=InstrumentationRegistry.getArguments().getString("geometry") ?: "portrait"
        fun matches(check:(Player)->Boolean):Boolean{var result=false;ui.runOnUiThread{player?.let{result=check(it)}};return result}
        fun bounded(tag:String){
            val node=ui.onNodeWithTag(tag).performScrollTo().assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue("$tag fits the real viewport",node.left>=-1 && node.right<=device.displayWidth+1)
        }
        try{
            for(light in listOf(false,true)){
                val theme=if(light)"light"else"dark"
                ui.runOnUiThread{ui.activity.setContent{SakugappTheme(light){Surface(Modifier.fillMaxSize().systemBarsPadding()){
                    key(light){VideoPanel(file.toURI().toString(),AppSettings(lightTheme=light),onPlayerReady={player=it})}
                }}}}
                ui.waitUntil(20000){matches{it.playbackState==Player.STATE_READY}}
                ui.onNodeWithTag("speed-controls").assertDoesNotExist()
                ui.onNodeWithTag("more-controls").performClick()
                for(speed in PreferenceRules.speeds)bounded("speed-$speed")
                ui.onNodeWithTag("speed-1.5").performScrollTo().performClick().assertIsSelected()
                ui.waitUntil(5000){matches{it.playbackParameters.speed==1.5f}}
                UiEvidence.capture("controls-$geometry-$theme.png")
                for(tag in listOf("set-a","set-b","ab-toggle","open-frame-controls","analysis-time"))bounded(tag)
                if(geometry=="portrait" && !light)UiEvidence.capture("controls-frame-group.png")
                ui.onNodeWithTag("set-a").performScrollTo().performClick()
                ui.runOnUiThread{player!!.pause();player!!.seekTo(1200)}
                ui.waitUntil(10000){matches{it.playbackState==Player.STATE_READY && it.currentPosition==1200L}}
                ui.onNodeWithTag("set-b").performScrollTo().performClick()
                ui.onNodeWithTag("ab-toggle").performScrollTo().assertIsEnabled().performClick().assertIsSelected()
                if(geometry=="portrait" && !light)UiEvidence.capture("controls-ab-group.png")
                if(ui.activity.resources.configuration.smallestScreenWidthDp<600){
                    ui.onNodeWithText("Full screen",substring=false).performClick()
                    ui.onNodeWithTag("rotate-video").performClick()
                    ui.waitUntil(15000){ui.activity.resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE}
                    bounded("speed-1.5");ui.onNodeWithTag("speed-1.5").assertIsSelected()
                    if(geometry=="portrait")UiEvidence.capture("controls-phone-landscape-$theme.png")
                    for(tag in listOf("set-a","set-b","ab-toggle","open-frame-controls"))bounded(tag)
                    ui.onNodeWithText("Exit full screen",substring=false).performClick()
                    ui.waitUntil(15000){ui.activity.resources.configuration.orientation==Configuration.ORIENTATION_PORTRAIT}
                }
                ui.onNodeWithTag("more-controls").performClick()
                ui.onNodeWithTag("speed-controls").assertDoesNotExist()
                assertTrue(matches{it.playbackParameters.speed==1.5f})
            }
        }finally{file.delete()}
    }
}
