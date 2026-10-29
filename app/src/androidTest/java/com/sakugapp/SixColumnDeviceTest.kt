// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.*
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.sakugapp.data.*
import com.sakugapp.ui.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Fresh hardware profiles avoid API 28 framebuffer corruption from changing wm size while rendering. */
@RunWith(AndroidJUnit4::class)
class SixColumnDeviceTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    companion object {
        private fun device()=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        @JvmStatic @BeforeClass fun setFontBeforeLaunchingTheActivity(){
            val phone=InstrumentationRegistry.getArguments().getString("geometry")=="phone"
            device().executeShellCommand("settings put system font_scale "+if(phone)"2.0"else"1.0")
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        }
        @JvmStatic @AfterClass fun restoreFont(){device().executeShellCommand("settings put system font_scale 1.0")}
    }
    private fun requireRenderedCapture(file:java.io.File){
        val bitmap=android.graphics.BitmapFactory.decodeFile(file.path);var visible=0;var total=0
        assertEquals(device().displayWidth,bitmap.width)
        for(y in 0 until bitmap.height step 16)for(x in 0 until bitmap.width step 16){
            val pixel=bitmap.getPixel(x,y);total++;if((pixel and 0x00ffffff)>0x080808)visible++
        }
        bitmap.recycle();assertTrue("A real rendered window is required, not a black framebuffer: $visible/$total",visible>total/4)
    }
    @Test fun actualSixColumnHardwareKeepsScoreRatioAndAnchorAcrossColumnSwitchesAndRestart(){
        val phone=InstrumentationRegistry.getArguments().getString("geometry")=="phone"
        val device=device()
        val config=ui.activity.resources.configuration
        if(phone){assertTrue(config.screenWidthDp in 300..340);assertTrue(config.fontScale>1.9f)}
        else{assertTrue(config.smallestScreenWidthDp>=600);assertTrue(config.screenWidthDp>config.screenHeightDp)}
        val posts=runBlocking{SakugaRepository().page("",1).posts}.distinctBy{it.id}.sortedByDescending{it.id}
        assertTrue("At least 30 real public posts are required; no generated IDs or placeholder rows",posts.size>=30)
        assertTrue("Actual thumbnail URLs are required",posts.any{it.previewUrl!=null})
        lateinit var app:AppViewModel;ui.runOnUiThread{app=ViewModelProvider(ui.activity)[AppViewModel::class.java]}
        runBlocking{app.localLibrary.ready();posts.forEach{app.localLibrary.membership(it,1,true)};app.preferences.columns(6);app.preferences.librarySort(2);app.preferences.theme("dark")}
        try{
            ui.waitUntil(10000){app.settings.value.columns==6 && app.settings.value.librarySort==2}
            ui.onNodeWithText("Menu",substring=false).performClick();ui.onNodeWithText("Favorites",substring=false).performClick()
            ui.onNodeWithTag("local-library").performScrollToNode(hasTestTag("folder-entry-1"))
            ui.onNodeWithTag("folder-entry-1").performClick();ui.onNodeWithTag("local-library").performScrollToIndex(1)
            val grid=ui.onNodeWithTag("local-library").fetchSemanticsNode().boundsInRoot
            val media=ui.onNodeWithTag("post-media-${posts.first().id}").fetchSemanticsNode().boundsInRoot
            assertTrue("Physical viewport width",grid.width<=device.displayWidth+2f)
            assertTrue("Six actual columns",media.width<=grid.width/6+2f)
            assertTrue("Aspect ratio is not stretched",kotlin.math.abs(media.width/media.height-16f/9f)<0.08f)
            ui.onNodeWithTag("post-score-${posts.first().id}",useUnmergedTree=true).assertIsDisplayed()
            val score=mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            ui.onNodeWithTag("post-score-${posts.first().id}",useUnmergedTree=true).performSemanticsAction(SemanticsActions.GetTextLayoutResult){it(score)}
            assertEquals(1,score.single().lineCount);assertFalse(score.single().isLineEllipsized(0))
            val name=if(phone)"six-columns-large-font.png"else"six-columns-landscape.png"
            requireRenderedCapture(UiEvidence.capture(name))
            ui.onNodeWithTag("local-library").performScrollToIndex(24)
            val anchor=ui.onAllNodes(hasClickAction()).fetchSemanticsNodes().filter{node->
                node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("post-media-")==true && node.boundsInRoot.top>=grid.top && node.boundsInRoot.bottom<=grid.bottom
            }.minWith(compareBy({it.boundsInRoot.top},{it.boundsInRoot.left})).config[SemanticsProperties.TestTag]
            for(columns in listOf(2,6)){
                ui.onNodeWithText("Menu",substring=false).performClick();ui.onNodeWithText("Settings",substring=false).performScrollTo().performClick()
                ui.onNodeWithTag("setting-columns-$columns").performScrollTo().performClick();ui.waitUntil(10000){app.settings.value.columns==columns}
                device.pressBack();ui.onNodeWithTag(anchor).assertIsDisplayed()
            }
            ui.activityRule.scenario.recreate();ui.waitUntil(15000){app.settings.value.columns==6}
            ui.onNodeWithTag(anchor).assertIsDisplayed()
            assertTrue("Grid exposure is not a detail visit",app.viewedPosts.value.isEmpty())
        }finally{runBlocking{app.preferences.columns(2);app.preferences.librarySort(0);app.preferences.theme("system");posts.forEach{app.localLibrary.membership(it,1,false)}}}
    }
}
