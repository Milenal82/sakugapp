// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import android.content.pm.ActivityInfo
import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.sakugapp.data.*
import com.sakugapp.ui.*
import org.junit.*
import org.junit.Assert.*
import java.io.File

class DetailLayoutDeviceTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    @Test fun actualWindowAndVideoAspectConstrainSingleColumnWithoutMoreResizingVideo(){
        val original=PostParser.posts(InstrumentationRegistry.getInstrumentation().context.assets.open("library-real-posts.json").bufferedReader().use{it.readText()}).first()
        val requested=ui.activity.requestedOrientation
        val files=mutableListOf<File>()
        try{
            for(orientation in listOf(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)){
                ui.runOnUiThread{ui.activity.detailRotatable=true;ui.activity.requestedOrientation=orientation}
                ui.waitUntil(15000){ui.activity.resources.configuration.orientation==(if(orientation==ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)1 else 2)}
                for((asset,w,h)in listOf(Triple("layout-wide.mp4",160,90),Triple("portrait-pattern.mp4",64,96),Triple("layout-four-three.mp4",160,120))){
                    val file=File(ui.activity.cacheDir,"layout-$asset").apply{writeBytes(InstrumentationRegistry.getInstrumentation().context.assets.open(asset).use{it.readBytes()})};files+=file
                    val post=original.copy(id=original.id+files.size,fileUrl=file.toURI().toString(),fileExt="mp4",width=w,height=h)
                    ui.runOnUiThread{val app=ViewModelProvider(ui.activity)[AppViewModel::class.java];ui.activity.setContent{SakugappTheme(false){PostDetail(post,AppSettings(),false,app,false,{},{},{},{},{},false,false,{false})}}}
                    ui.waitUntil(20000){ui.onAllNodesWithContentDescription("Video frame rendered").fetchSemanticsNodes().isNotEmpty()}
                    ui.onNodeWithTag("video-gestures").performScrollTo()
                    val video=ui.onNodeWithTag("video-gestures").fetchSemanticsNode().boundsInRoot
                    assertEquals("$asset should fit actual aspect",w.toFloat()/h,video.width/video.height,.025f)
                    val window=ui.onRoot().fetchSemanticsNode().boundsInRoot
                    assertTrue(video.width<=window.width);assertTrue(video.height<window.height)
                    ui.onNodeWithTag("more-controls").performScrollTo().performClick()
                    ui.mainClock.advanceTimeBy(1000);ui.waitForIdle()
                    val panel=ui.onNodeWithTag("advanced-controls").getUnclippedBoundsInRoot()
                    val actions=ui.onNodeWithTag("detail-actions").getUnclippedBoundsInRoot()
                    assertTrue("Expanded controls must precede, not cover, detail actions",actions.top>=panel.bottom)
                    val expanded=ui.onNodeWithTag("video-gestures").fetchSemanticsNode().boundsInRoot
                    assertEquals(video.width,expanded.width,.5f);assertEquals(video.height,expanded.height,.5f)
                    if(asset=="layout-wide.mp4")UiEvidence.capture("detail-${if(orientation==1)"portrait"else"landscape"}.png")
                }
            }
        }finally{ui.runOnUiThread{ui.activity.detailRotatable=false;ui.activity.requestedOrientation=requested};files.forEach{it.delete()}}
    }
}
