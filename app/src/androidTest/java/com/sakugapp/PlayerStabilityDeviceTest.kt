// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import android.content.res.Configuration
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sakugapp.data.*
import com.sakugapp.ui.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

/** Short resource/interaction checks; no website writes, extended soak or unrelated suite. */
@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlayerStabilityDeviceTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private class Owner:LifecycleOwner {val registry=LifecycleRegistry(this);override val lifecycle:Lifecycle get()=registry}
    private fun tapVideo(){ui.onNodeWithTag("video-gestures").performTouchInput{click(Offset(width*.25f,height*.3f))};Thread.sleep(400);ui.waitForIdle()}
    private fun posts():List<Post>{val assets=InstrumentationRegistry.getInstrumentation().context.assets
        val types=assets.open("personal-tag-summary.json").bufferedReader().use{PostParser.tagSummary(it.readText())}
        return assets.open("library-real-posts.json").bufferedReader().use{PostParser.posts(it.readText(),types)}}
    @Test fun fullscreenReusesPlayerPreservesPositionAndGesturesAcrossLifecycleAndVideoSwitches(){
        val context=ui.activity;val assets=InstrumentationRegistry.getInstrumentation().context.assets
        val wide=File(context.cacheDir,"stability-wide.mp4");val tall=File(context.cacheDir,"stability-tall.mp4")
        assets.open("frame-index-vfr.mp4").use{input->wide.outputStream().use{input.copyTo(it)}}
        assets.open("portrait-pattern.mp4").use{input->tall.outputStream().use{input.copyTo(it)}}
        var url by mutableStateOf(wide.toURI().toString());var shown by mutableStateOf(true)
        lateinit var owner1:Owner;lateinit var owner2:Owner
        var owner by mutableStateOf<LifecycleOwner?>(null);var current:Player?=null;var creations=0
        ui.runOnUiThread{owner1=Owner();owner2=Owner();owner1.registry.currentState=Lifecycle.State.RESUMED;owner2.registry.currentState=Lifecycle.State.RESUMED;owner=owner1
            context.setContent{SakugappTheme(false){Surface(Modifier.fillMaxSize().systemBarsPadding()){
                CompositionLocalProvider(LocalLifecycleOwner provides owner!!){if(shown)VideoPanel(url,onPlayerReady={current=it;creations++})}
            }}}
        }
        fun ready(){ui.waitUntil(20000){var value=false;ui.runOnUiThread{value=current?.playbackState==Player.STATE_READY};value}}
        try {
            ready();val first=current
            ui.runOnUiThread{current!!.seekTo(400);current!!.setPlaybackSpeed(1.5f)};ready()
            ui.runOnUiThread{owner1.registry.currentState=Lifecycle.State.STARTED;owner=owner2}
            ready();assertSame(first,current);assertEquals(1,creations)
            repeat(3){
                ui.runOnUiThread{current!!.pause();current!!.seekTo(400)};ready()
                ui.onNodeWithTag("toggle-fullscreen").performClick()
                ui.waitUntil(15000){context.resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE}
                ui.onNodeWithTag("toggle-fullscreen").assertDoesNotExist()
                ui.onNodeWithTag("more-controls").assertDoesNotExist()
                assertSame(first,current);assertEquals(1,creations)
                ui.runOnUiThread{assertTrue(kotlin.math.abs(current!!.currentPosition-400)<=50)}
                if(it==0)UiEvidence.capture("stability-full-hidden.png")
                tapVideo();ui.onNodeWithTag("toggle-fullscreen").assertIsDisplayed()
                tapVideo();ui.onNodeWithTag("more-controls").assertDoesNotExist()
                ui.onNodeWithTag("video-gestures").performTouchInput{doubleClick(Offset(width*.25f,height*.3f))}
                Thread.sleep(400);ui.waitForIdle();ui.onNodeWithTag("more-controls").assertDoesNotExist()
                ui.runOnUiThread{current!!.pause();current!!.seekTo(400)};ready()
                tapVideo();ui.onNodeWithTag("more-controls").performClick()
                ui.onNodeWithTag("speed-1.5").performScrollTo().performClick().assertIsSelected()
                ui.onNodeWithTag("set-a").performScrollTo().performClick()
                ui.runOnUiThread{current!!.seekTo(1200)};ready()
                ui.onNodeWithTag("set-b").performScrollTo().performClick()
                ui.onNodeWithTag("ab-toggle").performScrollTo().assertIsEnabled().performClick().assertIsSelected()
                ui.onNodeWithTag("open-frame-controls").performScrollTo().assertIsDisplayed()
                if(it==0)UiEvidence.capture("stability-full-controls.png")
                ui.onNodeWithTag("toggle-fullscreen").performClick()
                ui.waitUntil(15000){context.resources.configuration.orientation==Configuration.ORIENTATION_PORTRAIT}
                assertSame(first,current);assertEquals(1,creations)
                ui.runOnUiThread{assertEquals(1.5f,current!!.playbackParameters.speed,0f);assertTrue(kotlin.math.abs(current!!.currentPosition-1200)<=50)}
                ui.onNodeWithTag("ab-toggle").performScrollTo().assertIsSelected()
            }
            repeat(4){index->ui.runOnUiThread{url=if(index%2==0)tall.toURI().toString()else wide.toURI().toString()};ui.waitUntil(10000){creations==index+2};ready()}
            ui.runOnUiThread{url=tall.toURI().toString()};ui.waitUntil(10000){creations==6};ready()
            ui.onNodeWithTag("toggle-fullscreen").performClick()
            ui.waitUntil(15000){context.resources.configuration.orientation==Configuration.ORIENTATION_PORTRAIT}
            ui.onNodeWithTag("more-controls").assertDoesNotExist()
            tapVideo();ui.onNodeWithTag("toggle-fullscreen").performClick()
            ui.runOnUiThread{shown=false};ui.waitForIdle()
            ui.runOnUiThread{assertTrue((current as ExoPlayer).isReleased)}
        }finally{ui.runOnUiThread{shown=false};wide.delete();tall.delete()}
    }
    @Test fun zeroSpacingIsTightAndFolderTilesToggleIndependentMembershipsInBothThemes(){
        val post=posts().first();lateinit var app:AppViewModel
        ui.runOnUiThread{app=ViewModelProvider(ui.activity)[AppViewModel::class.java]}
        val folder=runBlocking{app.localLibrary.ready();app.localLibrary.createFolder("Stability test")}
        val previousSpacing=app.settings.value.tagVerticalSpacing
        runBlocking{app.preferences.number("tagVerticalSpacing",20f);assertEquals(20f,PreferencesStore(ui.activity).settings.first().tagVerticalSpacing,0f)}
        var gap by mutableFloatStateOf(0f);var light by mutableStateOf(false);var query=""
        ui.runOnUiThread{ui.activity.setContent{SakugappTheme(light){Surface(Modifier.fillMaxSize().systemBarsPadding()){
            Column(Modifier.verticalScroll(rememberScrollState())){
                Box(Modifier.width(156.dp)){PostCard(post,AppSettings(lightTheme=light,tagVerticalSpacing=gap),false,false,{}, {query=it}, {}, {})}
                FavoriteControl(post,app)
            }
        }}}}
        try {
            val image=ui.onNodeWithTag("post-media-${post.id}").fetchSemanticsNode().boundsInRoot
            val zero=ui.onNodeWithTag("post-caption-${post.id}").fetchSemanticsNode().boundsInRoot.height
            ui.onNodeWithTag("tag-mokmok",useUnmergedTree=true).performClick();assertEquals("mokmok",query)
            UiEvidence.capture("stability-tags-zero.png")
            ui.runOnUiThread{gap=20f};ui.waitForIdle()
            assertTrue(ui.onNodeWithTag("post-caption-${post.id}").fetchSemanticsNode().boundsInRoot.height>zero+20)
            assertEquals(image,ui.onNodeWithTag("post-media-${post.id}").fetchSemanticsNode().boundsInRoot)
            UiEvidence.capture("stability-tags-twenty.png")
            for(value in listOf(false,true)){
                ui.runOnUiThread{light=value}
                ui.onNodeWithTag("favorite-action").performScrollTo().performClick()
                ui.onNodeWithTag("favorite-folder-1").performScrollTo().performClick()
                ui.waitUntil(10000){app.library.value.contains(post.id,1)}
                ui.onNodeWithTag("favorite-folder-$folder").performScrollTo().performClick()
                ui.waitUntil(10000){app.library.value.contains(post.id,folder)}
                ui.onNodeWithTag("favorite-folder-$folder").assertIsOn()
                UiEvidence.capture("stability-favorite-${if(value)"light"else"dark"}.png")
                ui.onNodeWithTag("favorite-folder-1").performScrollTo().performClick()
                ui.waitUntil(10000){!app.library.value.contains(post.id,1)}
                assertTrue(app.library.value.contains(post.id,folder))
                ui.onNodeWithTag("favorite-folder-$folder").performClick();ui.waitUntil(10000){!app.library.value.contains(post.id,folder)}
                ui.onNodeWithText(ui.activity.getString(R.string.ui_close),substring=false).performClick()
            }
        }finally{runBlocking{app.localLibrary.deleteFolder(folder);app.localLibrary.membership(post,1,false);app.preferences.number("tagVerticalSpacing",previousSpacing)}}
    }
}
