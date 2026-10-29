// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.google.gson.JsonParser
import com.sakugapp.data.*
import com.sakugapp.ui.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RefreshGridDeviceTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private fun pages():List<List<Post>> = InstrumentationRegistry.getInstrumentation().context.assets.open("posts-five-pages.json").bufferedReader().use{reader->JsonParser.parseString(reader.readText()).asJsonArray.map{PostParser.posts(it.toString())}}
    @Test fun pullAtTopRefreshesWhileMidListScrollDoesNotAndFailureKeepsTheRealSnapshot(){
        val pages=pages();val initial=pages.first();var pending=CompletableDeferred<PostPage>();var first=true;var refreshes=0
        val repository=object:PostRepository{override suspend fun page(query:String,page:Int):PostPage{
            assertEquals("animated order:score",query)
            if(page!=1)return PostPage(emptyList(),0)
            if(first){first=false;return PostPage(initial,initial.size)}
            return pending.await()
        }}
        lateinit var model:FeedViewModel;lateinit var grid:LazyGridState;lateinit var app:AppViewModel
        ui.runOnUiThread{
            app=ViewModelProvider(ui.activity)[AppViewModel::class.java];model=FeedViewModel(SavedStateHandle(mapOf("query" to "animated order:score")),repository)
            ui.activity.setContent{SakugappTheme(false){Surface(Modifier.fillMaxSize().systemBarsPadding()){
                val state by model.state.collectAsStateWithLifecycle();grid=rememberLazyGridState()
                CompositionLocalProvider(LocalTagActions provides app){PostsScreen(state,grid,AppSettings(),emptyList(),false,app,model::search,{},{model.loadMore()},{refreshes++;model.refresh()})}
            }}}
        }
        ui.waitUntil(15000){model.state.value.posts.size==initial.size}
        ui.onNodeWithTag("post-grid").performScrollToIndex(20)
        ui.onNodeWithTag("post-grid").performTouchInput{swipeDown(startY=height*0.4f,endY=height*0.75f,durationMillis=400)}
        ui.waitForIdle();assertTrue(grid.canScrollBackward);assertEquals(0,refreshes)
        ui.onNodeWithTag("post-grid").performScrollToIndex(0)
        ui.onNodeWithTag("post-grid").performTouchInput{swipeDown(startY=height*0.3f,endY=height*0.9f,durationMillis=500)}
        ui.waitUntil(10000){model.state.value.refreshing};assertEquals(1,refreshes)
        ui.onNodeWithTag("refresh-indicator").assertIsDisplayed();UiEvidence.capture("posts-refreshing.png")
        pending.completeExceptionally(DataFailure("HTTP 503"))
        ui.waitUntil(10000){model.state.value.refreshError!=null};assertEquals(initial.map{it.id},model.state.value.posts.map{it.id})
        ui.onNodeWithTag("retry-refresh").assertIsDisplayed();UiEvidence.capture("posts-refresh-failed.png")
        pending=CompletableDeferred();ui.onNodeWithTag("retry-refresh").performClick();ui.waitUntil(10000){model.state.value.refreshing}
        val replacement=pages[1].take(2);pending.complete(PostPage(listOf(replacement[0],replacement[0],replacement[1]),3))
        ui.waitUntil(10000){!model.state.value.refreshing && model.state.value.posts.size==2}
        assertEquals(replacement.map{it.id},model.state.value.posts.map{it.id});assertEquals(2,model.state.value.nextPage)
        assertEquals("animated order:score",model.state.value.query);assertEquals(0,grid.firstVisibleItemIndex)
    }
    private fun requireRenderedCapture(file:java.io.File){
        val bitmap=android.graphics.BitmapFactory.decodeFile(file.path);var visible=0;var total=0
        for(y in 0 until bitmap.height step 16)for(x in 0 until bitmap.width step 16){
            val pixel=bitmap.getPixel(x,y);total++;if((pixel and 0x00ffffff)>0x080808)visible++
        }
        bitmap.recycle();assertTrue("Screenshot must contain a rendered window, not a black display: $visible / $total",visible>total/4)
    }
    @Test fun sixColumnsRemainReadableOnNarrowLargeFontAndLandscapeAndPersistAfterRestart(){
        val app=run{lateinit var a:AppViewModel;ui.runOnUiThread{a=ViewModelProvider(ui.activity)[AppViewModel::class.java]};a}
        val device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());val posts=pages().first()
        runBlocking{app.localLibrary.ready();posts.forEach{app.localLibrary.membership(it,1,true)};app.preferences.columns(6);app.preferences.librarySort(2);app.preferences.theme("dark")}
        try{
            ui.waitUntil(10000){app.settings.value.columns==6}
            ui.onNodeWithText("Menu",substring=false).performClick();ui.onNodeWithText("Favorites",substring=false).performClick();ui.onNodeWithTag("folder-entry-1").performClick()
            device.executeShellCommand("wm size 640x1280");device.executeShellCommand("wm density 320")
            ui.waitUntil(20000){ui.activity.resources.configuration.screenWidthDp in 300..340}
            device.executeShellCommand("settings put system font_scale 2.0")
            ui.waitUntil(20000){ui.activity.resources.configuration.fontScale>1.9f}
            ui.activityRule.scenario.recreate()
            ui.waitUntil(20000){ui.activity.window.decorView.width==device.displayWidth}
            ui.onNodeWithTag("local-library").performScrollToIndex(1)
            val bounds=ui.onNodeWithTag("post-media-${posts.first().id}").fetchSemanticsNode().boundsInRoot
            val all=ui.onNodeWithTag("local-library").fetchSemanticsNode().boundsInRoot
            assertTrue("Grid uses the actual display, not stale pre-resize bounds: $all vs ${device.displayWidth}",all.width<=device.displayWidth+2f)
            assertTrue("Six narrow columns",bounds.width<=all.width/6+2f)
            assertTrue("No stretched thumbnail",kotlin.math.abs(bounds.width/bounds.height-16f/9f)<0.08f)
            ui.onNodeWithTag("post-score-${posts.first().id}",useUnmergedTree=true).assertIsDisplayed()
            val phone=UiEvidence.capture("six-columns-large-font.png")
            requireRenderedCapture(phone)

            device.executeShellCommand("settings put system font_scale 1.0")
            device.executeShellCommand("wm size 1280x800");device.executeShellCommand("wm density 160")
            ui.waitUntil(20000){ui.activity.resources.configuration.screenWidthDp>ui.activity.resources.configuration.screenHeightDp}
            ui.activityRule.scenario.recreate()
            ui.waitUntil(20000){ui.activity.window.decorView.width==device.displayWidth}
            ui.onNodeWithTag("post-media-${posts.first().id}").assertIsDisplayed()
            val landscape=UiEvidence.capture("six-columns-landscape.png");requireRenderedCapture(landscape)
            ui.onNodeWithTag("local-library").performScrollToIndex(24)
            val listBounds=ui.onNodeWithTag("local-library").fetchSemanticsNode().boundsInRoot
            val anchor=ui.onAllNodes(hasClickAction()).fetchSemanticsNodes().filter{node->
                node.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.TestTag)?.startsWith("post-media-")==true && node.boundsInRoot.top>=listBounds.top && node.boundsInRoot.bottom<=listBounds.bottom
            }.minWith(compareBy({it.boundsInRoot.top},{it.boundsInRoot.left})).config[androidx.compose.ui.semantics.SemanticsProperties.TestTag]
            for(columns in listOf(2,6)){
                ui.onNodeWithText("Menu",substring=false).performClick();ui.onNodeWithText("Settings",substring=false).performClick()
                ui.onNodeWithTag("setting-columns-$columns").performScrollTo().performClick()
                ui.waitUntil(10000){app.settings.value.columns==columns};device.pressBack()
                ui.onNodeWithTag(anchor).assertIsDisplayed()
            }
            ui.activityRule.scenario.recreate()
            ui.waitUntil(15000){app.settings.value.columns==6}
            ui.onNodeWithTag(anchor).assertIsDisplayed()
        }finally{
            device.executeShellCommand("settings put system font_scale 1.0");device.executeShellCommand("wm size reset");device.executeShellCommand("wm density reset")
            runBlocking{app.preferences.columns(2);app.preferences.librarySort(0);app.preferences.theme("system");posts.forEach{app.localLibrary.membership(it,1,false)}}
        }
    }
}
