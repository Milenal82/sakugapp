// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
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
            // Pick a real, fully visible thumbnail below the overlay rather than assuming row heights.
            fun visibleThumbnails():List<androidx.compose.ui.semantics.SemanticsNode>{
                val divider=ui.onNodeWithTag("search-divider").fetchSemanticsNode().boundsInRoot.bottom
                val bottom=ui.onNodeWithTag("post-grid").fetchSemanticsNode().boundsInRoot.bottom
                return ui.onAllNodesWithContentDescription("Post ",substring=true).fetchSemanticsNodes()
                    .filter{node->node.boundsInRoot.top>divider && node.boundsInRoot.bottom<bottom && node.config[SemanticsProperties.ContentDescription].firstOrNull{it.startsWith("Post ")}?.substringAfter(' ')?.toLongOrNull()?.let{id->feed.state.value.posts.firstOrNull{it.id==id}?.isVideo}==true}
            }
            ui.waitUntil(30000){visibleThumbnails().isNotEmpty()}
            val description=visibleThumbnails().first().config[SemanticsProperties.ContentDescription].first{it.startsWith("Post ")}
            val anchor=feed.state.value.posts.first{it.id==description.substringAfter(' ').toLong()}
            ui.onNodeWithContentDescription(description).performClick()
            ui.onNodeWithText("#${anchor.id}").performScrollTo().assertIsDisplayed()
            val next=feed.adjacent(1)
            assertNotNull("Five batches provide a real adjacent result",next)
            ui.onNodeWithTag("video-gestures").performScrollTo().performTouchInput{swipeLeft(durationMillis=400)}
            ui.onNodeWithText("#${next!!.id}").performScrollTo().assertIsDisplayed()
            assertEquals(next.id,feed.selected.value!!.id)
            device.pressBack()
            ui.onNodeWithContentDescription("Post ${anchor.id}").assertExists()
            assertTrue(feed.state.value.nextPage>=6);assertEquals(ids,feed.state.value.posts.take(ids.size).map{it.id})
            val phoneCapture=java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),"ui-evidence").apply{mkdirs()}
            assertTrue(device.takeScreenshot(java.io.File(phoneCapture,"phone-post-grid.png")))
            device.executeShellCommand("wm size 1920x1200");device.executeShellCommand("wm density 160")
            ui.waitUntil(20000){ui.activity.resources.configuration.smallestScreenWidthDp>=600}
            ui.onNodeWithTag("post-grid").performScrollToIndex(0)
            for(columns in listOf(8,12)){
                ui.onNodeWithTag("grid-columns").performClick()
                ui.onNodeWithText(ui.activity.getString(R.string.ui_columns_count,columns),substring=false).performClick()
                ui.waitUntil(10000){app.settings.value.columns==columns}
                val bounds=ui.onNodeWithTag("post-media-${ids.first()}").fetchSemanticsNode().boundsInRoot
                val full=ui.onNodeWithTag("post-grid").fetchSemanticsNode().boundsInRoot
                assertTrue(bounds.width<=full.width/columns+2f)
                ui.onNodeWithTag("post-score-${ids.first()}",useUnmergedTree=true).assertIsDisplayed()
            }
            val capture=java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),"ui-evidence").apply{mkdirs()}
            assertTrue(device.takeScreenshot(java.io.File(capture,"tablet-12-columns.png")))

        }finally{
            ui.runOnUiThread{app.columns(2)}
            ui.waitUntil(10000){app.settings.value.columns==2}
            device.executeShellCommand("wm size reset");device.executeShellCommand("wm density reset")
        }
    }
}
