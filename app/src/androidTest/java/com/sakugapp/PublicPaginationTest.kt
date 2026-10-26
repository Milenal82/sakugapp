// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.sakugapp.ui.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Public read-only test: five real API batches, small-screen density, scroll chrome and state restoration. */
@RunWith(AndroidJUnit4::class)
class PublicPaginationTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    @Test fun fiveBatchesDensityAndDetailReturnKeepRealPostsAndScrollState(){
        val device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        lateinit var feed:FeedViewModel;lateinit var app:AppViewModel
        ui.runOnUiThread{feed=ViewModelProvider(ui.activity)[FeedViewModel::class.java];app=ViewModelProvider(ui.activity)[AppViewModel::class.java]}
        try {
            device.executeShellCommand("wm size 720x1280");device.executeShellCommand("wm density 320")
            ui.waitUntil(20000){ui.activity.resources.configuration.screenWidthDp in 340..380}
            ui.waitUntil(90000){feed.state.value.posts.size>=60 && !feed.state.value.loading}
            repeat(4){
                val before=feed.state.value.nextPage
                ui.onNodeWithTag("post-grid").performScrollToIndex(feed.state.value.posts.size-2)
                ui.waitUntil(90000){feed.state.value.nextPage>before && !feed.state.value.loading}
                assertNull(feed.state.value.error)
            }
            val ids=feed.state.value.posts.map{it.id}
            assertTrue(ids.size>=300);assertEquals(ids.size,ids.distinct().size)
            println("Public pagination: ${ids.size} unique posts, next page ${feed.state.value.nextPage}")
            for(columns in 1..4){
                ui.onNodeWithTag("post-grid").performScrollToIndex(0)
                ui.waitUntil(10000){ui.onAllNodesWithTag("grid-columns").fetchSemanticsNodes().isNotEmpty()}
                ui.onNodeWithTag("grid-columns").performClick()
                ui.onNodeWithText(ui.activity.getString(R.string.ui_columns_count,columns),substring=false).performClick()
                ui.waitUntil(10000){app.settings.value.columns==columns}
                ui.onNodeWithTag("post-score-${ids.first()}",useUnmergedTree=true).assertIsDisplayed()
            }
            val layout=mutableListOf<TextLayoutResult>()
            ui.onNodeWithTag("post-score-${ids.first()}",useUnmergedTree=true).performSemanticsAction(SemanticsActions.GetTextLayoutResult){it(layout)}
            assertEquals(scoreColor(false),layout.single().layoutInput.style.color)
            assertTrue(layout.single().layoutInput.style.fontSize.value>=11f)
            ui.activityRule.scenario.recreate()
            val persistedLabel=ui.activity.getString(R.string.ui_grid_selection,4)
            ui.waitUntil(10000){ui.onAllNodesWithContentDescription(persistedLabel).fetchSemanticsNodes().isNotEmpty()}
            ui.onNodeWithTag("grid-columns").assertContentDescriptionEquals(persistedLabel)
            ui.onNodeWithTag("post-grid").performScrollToIndex(32)
            ui.onNodeWithTag("post-grid").performTouchInput{swipeUp(durationMillis=400)}
            ui.waitForIdle();ui.onNodeWithTag("search-input").assertIsNotDisplayed()
            ui.onNodeWithTag("post-grid").performTouchInput{swipeDown(durationMillis=400)}
            ui.waitForIdle();ui.onNodeWithTag("search-input").assertIsDisplayed()
            ui.onNodeWithTag("post-grid").performScrollToIndex(0)
            ui.onNodeWithText("History",substring=false).performClick()
            ui.onNodeWithTag("search-history").assertIsDisplayed()
            ui.onNodeWithText("History",substring=false).performClick()
            ui.onNodeWithTag("search-history").assertDoesNotExist()
            ui.onNodeWithTag("post-grid").performScrollToIndex(32)
            val anchor=feed.state.value.posts[32]
            ui.onNodeWithContentDescription("Post ${anchor.id}").performClick()
            ui.onNodeWithText("#${anchor.id}").performScrollTo().assertIsDisplayed()
            device.pressBack()
            ui.onNodeWithContentDescription("Post ${anchor.id}").assertExists()
            assertTrue(feed.state.value.nextPage>=6);assertEquals(ids,feed.state.value.posts.take(ids.size).map{it.id})
        }finally{
            ui.runOnUiThread{app.columns(2)}
            ui.waitUntil(10000){app.settings.value.columns==2}
            device.executeShellCommand("wm size reset");device.executeShellCommand("wm density reset")
        }
    }
}
