// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Live-site device test, run explicitly; deterministic CI tests do not depend on the site's availability. */
@RunWith(AndroidJUnit4::class)
class PublicBrowseTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private fun frameCounter(index:Int)=SemanticsMatcher("frame counter $index"){node->
        node.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)?.any{Regex("$index / [0-9]+").matches(it.text)}==true
    }
    @Test fun actualPostSearchDetailsPlaybackAndFrameAnalysis(){
        ui.onNodeWithTag("search-entry").performClick()
        ui.onNode(hasSetTextAction()).performTextReplacement("id:319947")
        ui.onNodeWithTag("submit-search").performClick()
        ui.waitUntil(90000){ui.onAllNodesWithContentDescription("Post 319947").fetchSemanticsNodes().isNotEmpty()}
        val inCaption=hasAnyAncestor(hasTestTag("post-caption-319947"))
        ui.onNode(hasText("mokmok") and inCaption,useUnmergedTree=true).assertIsDisplayed()
        ui.onNode(hasText("jujutsu_kaisen_season_3") and inCaption,useUnmergedTree=true).assertIsDisplayed()
        ui.onNodeWithTag("post-score-319947",useUnmergedTree=true).assertIsDisplayed()
        val score=ui.onNodeWithTag("post-score-319947",useUnmergedTree=true).fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].single().text
        assertTrue("Real score remains visible",Regex("Score -?[0-9]+").matches(score))
        ui.onNodeWithContentDescription("Post 319947").performClick()
        ui.onNodeWithTag("detail-post-id").performScrollTo().assertIsDisplayed()
        ui.waitUntil(90000){ui.onAllNodesWithContentDescription("Video frame rendered").fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithContentDescription("Video frame rendered").performScrollTo()
        val device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        // A software emulator can show a System UI ANR during cold boot. Never dismiss an app ANR.
        if(device.hasObject(By.text("System UI isn't responding"))) {
            device.findObject(By.text("Close app"))?.click()
            device.wait(Until.gone(By.text("System UI isn't responding")),15000)
        }
        val play=device.wait(Until.findObject(By.desc("Play")),10000)
        assertNotNull("Native player Play control",play);play.click()
        ui.waitUntil(15000){ui.onAllNodes(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription,"Playing")).fetchSemanticsNodes().isNotEmpty()}
        UiEvidence.capture("phone-post-detail.png")
        ui.onNodeWithTag("more-controls").performClick()
        ui.onNodeWithTag("speed-1.5").performScrollTo().performClick().assertIsSelected()
        ui.onNodeWithText("Full screen").performScrollTo().performClick();ui.onNodeWithText("Exit full screen").assertExists()
        ui.onNodeWithText("Exit full screen").performClick()
        ui.onNodeWithTag("open-frame-controls").performScrollTo().performClick()
        ui.onNodeWithText("Download and decode").performClick()
        ui.waitUntil(120000){ui.onAllNodesWithContentDescription("Decoded frame").fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithTag("frame-number").performScrollTo().performTextReplacement("1")
        ui.onNodeWithTag("go-to-frame").performScrollTo().performClick()
        ui.waitUntil(15000){ui.onAllNodes(frameCounter(1)).fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithTag("exact-next-frame").performScrollTo().performClick()
        ui.waitUntil(15000){ui.onAllNodes(frameCounter(2)).fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithTag("exact-previous-frame").performScrollTo().performClick()
        ui.waitUntil(15000){ui.onAllNodes(frameCounter(1)).fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithTag("frame-number").performScrollTo().performTextReplacement("3")
        ui.onNodeWithTag("go-to-frame").performScrollTo().performClick()
        ui.waitUntil(15000){ui.onAllNodes(frameCounter(3)).fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithText("Close").performClick()
        ui.onNodeWithTag("detail-tag-mokmok").performScrollTo().assertIsDisplayed()
        ui.onNodeWithTag("artist-wiki-mokmok").performClick()
        device.wait(Until.findObject(By.text("Back to app")),15000).also {
            assertNotNull("Official wiki browser has a return action",it);it.click()
        }
        ui.waitUntil(15000){ui.onAllNodesWithTag("download-video").fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithTag("download-video").performScrollTo().performClick()
        val save=device.wait(Until.findObject(By.text(java.util.regex.Pattern.compile("(?i)save"))),15000)
        assertNotNull("System save document picker",save);save.click()
        ui.waitUntil(90000){ui.onAllNodesWithTag("download-complete").fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithTag("download-complete").assertTextContains("Saved")
        ui.onNodeWithText("Close",substring=false).performClick()
        ui.runOnUiThread{ui.activity.onBackPressedDispatcher.onBackPressed()}
        ui.onNodeWithContentDescription("Post 319947").assertExists()
        ui.onNodeWithTag("search-entry").assertTextContains("id:319947")
    }
}
