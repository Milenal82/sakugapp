// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.sakugapp.ui.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Native navigation with real public results; the API 35 job uses actual left/right edge gestures. */
@RunWith(AndroidJUnit4::class)
class SearchNavigationDeviceTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val device get()=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private var rightEdge=false
    private fun back(){
        if(InstrumentationRegistry.getArguments().getString("gestural")=="true"){
            rightEdge=!rightEdge
            val w=device.displayWidth;val h=device.displayHeight
            assertTrue(device.swipe(if(rightEdge)w-1 else 1,h/2,if(rightEdge)w*2/3 else w/3,h/2,24))
        }else device.pressBack()
    }
    private fun position()=ui.onNodeWithTag("post-grid").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
    private fun openCombined(){
        ui.onNodeWithTag("search-entry").performClick()
        ui.onNodeWithTag("open-combined-search").performClick()
        ui.onNodeWithTag("query-expression").assertIsDisplayed()
    }
    private fun chooseHistory(query:String){
        ui.onNodeWithTag("combined-history-toggle").performScrollTo().performClick()
        ui.onNode(hasText(query) and hasAnyAncestor(hasTestTag("combined-history"))).performScrollTo().performClick()
    }
    @Test fun combinedHistoryOnlyFillsDraftAndBackRestoresTheOriginalResultAndBrowseLists(){
        lateinit var app:AppViewModel;lateinit var feed:FeedViewModel
        ui.runOnUiThread{app=ViewModelProvider(ui.activity)[AppViewModel::class.java];feed=ViewModelProvider(ui.activity)[FeedViewModel::class.java]}
        ui.waitUntil(60000){feed.state.value.posts.size>=20 && !feed.state.value.loading}
        ui.onNodeWithTag("post-grid").performScrollToIndex(12)
        ui.onNodeWithTag("post-grid").performTouchInput{swipeDown(startY=height*0.4f,endY=height*0.6f,durationMillis=400)}
        ui.waitUntil(60000){!feed.state.value.loading};ui.waitForIdle()
        val browse=feed.state.value;val browsePosition=position()
        val query="animated order:score";val changed="id:${browse.posts.first().id}"
        runBlocking{app.preferences.remember(changed);app.preferences.remember(query)}
        ui.waitUntil(10000){query in app.history.value && changed in app.history.value}
        openCombined();chooseHistory(query)
        assertEquals("Choosing history must not submit",browse.query,feed.state.value.query)
        ui.onNodeWithTag("query-order-score").performScrollTo().assertIsSelected()
        ui.onNodeWithTag("query-expression").performScrollTo().assertTextContains(query)
        val label=if(InstrumentationRegistry.getArguments().getString("gestural")=="true")"api35"else"api28"
        UiEvidence.capture("combined-search-draft-$label.png")
        ui.onNodeWithTag("apply-combined-search").performClick()
        ui.waitUntil(60000){feed.state.value.query==query && feed.state.value.posts.size>=20 && !feed.state.value.loading}
        ui.onNodeWithTag("post-grid").performScrollToIndex(12)
        ui.onNodeWithTag("post-grid").performTouchInput{swipeDown(startY=height*0.4f,endY=height*0.6f,durationMillis=400)}
        ui.waitUntil(60000){!feed.state.value.loading};ui.waitForIdle()
        val results=feed.state.value.posts.map{it.id};val resultPosition=position()
        openCombined();ui.onNodeWithTag("query-expression").assertTextContains(query)
        chooseHistory(changed);assertEquals(query,feed.state.value.query)
        back();ui.onNodeWithTag("query-expression").assertDoesNotExist()
        assertEquals(query,feed.state.value.query);assertEquals(results,feed.state.value.posts.map{it.id})
        assertEquals(resultPosition.toDouble(),position().toDouble(),2.0)
        openCombined();chooseHistory(changed);ui.onNodeWithTag("apply-combined-search").performClick()
        ui.waitUntil(60000){feed.state.value.query==changed && !feed.state.value.loading}
        assertEquals(listOf(browse.posts.first().id),feed.state.value.posts.map{it.id})
        if(InstrumentationRegistry.getArguments().getString("gestural")=="true"){
            val y=device.displayHeight/2
            assertTrue(device.swipe(arrayOf(android.graphics.Point(1,y),android.graphics.Point(device.displayWidth/3,y),android.graphics.Point(2,y)),18))
            ui.waitForIdle();assertEquals("Cancelled predictive back must retain results",changed,feed.state.value.query)
        }
        back();ui.waitUntil(10000){feed.state.value.query==browse.query}
        ui.onNodeWithTag("search-entry").assertExists()
        assertEquals(browse.posts.map{it.id},feed.state.value.posts.take(browse.posts.size).map{it.id})
        assertEquals(browsePosition.toDouble(),position().toDouble(),2.0)
        assertFalse(ui.activity.isFinishing)
        UiEvidence.capture("search-return-$label.png")
    }
    @Test fun systemBackClosesTheImeThenTheEditorWithoutFinishingTheActivity(){
        fun ime():Boolean{var shown=false;ui.runOnUiThread{shown=ViewCompat.getRootWindowInsets(ui.activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime())==true};return shown}
        ui.onNodeWithTag("search-entry").performClick()
        ui.onNodeWithTag("search-input").assertIsFocused().performTextInput("animated")
        ui.waitUntil(15000){ime()}
        back();ui.waitUntil(10000){!ime()}
        assertFalse(ui.activity.isFinishing)
        ui.onNodeWithTag("search-input").assertExists()
        back();ui.onNodeWithTag("search-input").assertDoesNotExist()
        ui.onNodeWithTag("search-entry").assertIsDisplayed();assertFalse(ui.activity.isFinishing)
    }
}
