// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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

@RunWith(AndroidJUnit4::class)
class CompactInterfaceDeviceTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    @Test fun compactSearchRetainsHistoryFiltersFocusAndTheGridAnchor(){
        lateinit var app:AppViewModel;lateinit var feed:FeedViewModel
        ui.runOnUiThread{app=ViewModelProvider(ui.activity)[AppViewModel::class.java];feed=ViewModelProvider(ui.activity)[FeedViewModel::class.java]}
        runBlocking{app.preferences.remember("id:319947")}
        ui.waitUntil(10000){"id:319947" in app.history.value}
        ui.onNodeWithTag("search-input").assertDoesNotExist()
        ui.onNodeWithText("Filters",substring=false).assertDoesNotExist()
        ui.onNodeWithTag("search-history").assertDoesNotExist()
        val gridBounds=ui.onNodeWithTag("post-grid").fetchSemanticsNode().boundsInRoot
        ui.waitUntil(60000){feed.state.value.posts.isNotEmpty() || feed.state.value.error!=null}
        val anchor=feed.state.value.posts.firstOrNull()?.id
        val before=anchor?.let{ui.onNodeWithTag("post-media-$it").fetchSemanticsNode().boundsInRoot}
        ui.onNodeWithTag("search-entry").performClick()
        ui.onNodeWithTag("search-input").assertIsFocused()
        ui.onNodeWithTag("search-history").assertDoesNotExist()
        assertEquals(gridBounds.top,ui.onNodeWithTag("post-grid").fetchSemanticsNode().boundsInRoot.top)
        if(before!=null)assertEquals(before.top,ui.onNodeWithTag("post-media-$anchor").fetchSemanticsNode().boundsInRoot.top)
        UiEvidence.capture("compact-search-expanded.png")
        ui.onNodeWithText("History",substring=false).performClick()
        ui.onNodeWithTag("search-history").assertIsDisplayed()
        ui.onNode(hasText("id:319947") and hasAnyAncestor(hasTestTag("search-history"))).performClick()
        ui.waitUntil(10000){feed.state.value.query=="id:319947"}
        ui.onNodeWithTag("search-input").assertDoesNotExist()
        ui.onNodeWithTag("search-entry").performClick()
        ui.onNodeWithText("Filters",substring=false).performClick()
        ui.onNodeWithText("score:>=3",substring=false).performScrollTo().performClick()
        ui.onNodeWithText("Apply",substring=false).performClick()
        ui.waitUntil(10000){feed.state.value.query=="id:319947 score:>=3"}
        ui.onNodeWithTag("search-input").assertDoesNotExist()
        assertTrue(app.history.value.contains("id:319947"))
        UiEvidence.capture("compact-post-grid.png")
    }

    @Test fun fiveActionsFitOnPhoneAndTabletAndScoreIsOnlyInMetadata(){
        val device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val post=PostParser.posts(InstrumentationRegistry.getInstrumentation().context.assets.open("library-real-posts.json").bufferedReader().use{it.readText()}).first()
        lateinit var app:AppViewModel;var direction=0;var opened=""
        ui.runOnUiThread{app=ViewModelProvider(ui.activity)[AppViewModel::class.java]}
        try{
            for((size,density,light)in listOf(
                Triple("640x1136","320",false),
                Triple("1920x1200","160",true))){
                device.executeShellCommand("wm size $size");device.executeShellCommand("wm density $density")
                val label=if(light)"tablet-light"else"phone-dark"
                val expected=if(light)600 else 320
                ui.waitUntil(20000){if(light)ui.activity.resources.configuration.smallestScreenWidthDp>=expected else ui.activity.resources.configuration.screenWidthDp==expected}
                ui.runOnUiThread{ui.activity.setContent{SakugappTheme(light){
                    CompositionLocalProvider(LocalUriHandler provides object:UriHandler{override fun openUri(uri:String){opened=uri}}){
                        PostDetail(post,AppSettings(lightTheme=light),false,app,false,{}, {}, {}, {}, {},false,true,{direction=it;true})
                    }
                }}}
                ui.onNodeWithTag("detail-actions").performScrollTo()
                val row=ui.onNodeWithTag("detail-actions").fetchSemanticsNode().boundsInRoot
                val tags=listOf("previous-post","next-post","download-video","original-post","favorite-action")
                val bounds=tags.map{ui.onNodeWithTag(it).assertIsDisplayed().fetchSemanticsNode().boundsInRoot}
                assertTrue(bounds.all{it.left>=row.left-1 && it.right<=row.right+1})
                assertTrue(bounds.zipWithNext().all{(a,b)->a.right<=b.left+1 && kotlin.math.abs(a.top-b.top)<1})
                ui.onNodeWithTag("previous-post").assertIsNotEnabled()
                ui.onNodeWithTag("next-post").assertIsEnabled().performClick();assertEquals(1,direction)
                ui.onNodeWithTag("original-post").performClick();assertEquals(post.pageUrl,opened)
                ui.onNodeWithTag("next-post").performTouchInput{longClick()}
                ui.onNodeWithText("Next post",substring=false).assertExists()
                ui.mainClock.advanceTimeBy(2000);ui.waitForIdle()
                ui.onNodeWithTag("detail-score").performScrollTo().assertTextEquals("Score ${post.score ?: "?"}")
                ui.onAllNodesWithTag("detail-score").assertCountEquals(1)
                assertTrue(ui.onNodeWithTag("detail-score").fetchSemanticsNode().parent?.id==ui.onNodeWithTag("detail-post-id").fetchSemanticsNode().parent?.id)
                if(PostParser.safeUrl(post.source)!=null){ui.onNodeWithTag("post-source").performClick();assertEquals(post.source,opened)}
                ui.onNodeWithTag("detail-actions").performScrollTo()
                UiEvidence.capture("compact-detail-$label.png")
            }
        }finally{device.executeShellCommand("wm size reset");device.executeShellCommand("wm density reset")}
    }
}
