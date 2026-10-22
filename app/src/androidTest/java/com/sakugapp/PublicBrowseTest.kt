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
        ui.onNode(hasSetTextAction()).performTextReplacement("id:319947")
        ui.onNodeWithText("Search",useUnmergedTree=true).performClick()
        ui.waitUntil(90000){ui.onAllNodesWithContentDescription("Post 319947").fetchSemanticsNodes().isNotEmpty()}
        val caption=ui.onNodeWithTag("post-caption-319947").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].first()
        assertTrue("Verified animator tag below thumbnail",caption.text.contains("mokmok"))
        assertTrue("Verified copyright tag below thumbnail",caption.text.contains("jujutsu_kaisen"))
        assertTrue(caption.spanStyles.any{it.item.color==androidx.compose.ui.graphics.Color(0xFFFFD54F)})
        assertTrue(caption.spanStyles.any{it.item.color==androidx.compose.ui.graphics.Color(0xFFCE93D8)})
        assertTrue("Real score remains visible",Regex("Score -?[0-9]+").containsMatchIn(caption.text))
        ui.onNodeWithContentDescription("Post 319947").performClick()
        ui.onNodeWithText("#319947").assertIsDisplayed()
        ui.waitUntil(90000){ui.onAllNodesWithContentDescription("Video frame rendered").fetchSemanticsNodes().isNotEmpty()}
        val device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        // A software emulator can show a System UI ANR during cold boot. Never dismiss an app ANR.
        if(device.hasObject(By.text("System UI isn't responding"))) {
            device.findObject(By.text("Close app"))?.click()
            device.wait(Until.gone(By.text("System UI isn't responding")),15000)
        }
        val play=device.wait(Until.findObject(By.desc("Play")),10000)
        assertNotNull("Native player Play control",play);play.click()
        ui.waitUntil(15000){ui.onAllNodes(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription,"Playing")).fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithText("1.5×").performScrollTo().performClick();ui.onNodeWithText("[1.5×]").assertExists()
        ui.onNodeWithText("Full screen").performScrollTo().performClick();ui.onNodeWithText("Exit full screen").assertExists()
        ui.onNodeWithText("Exit full screen").performClick()
        ui.onNodeWithTag("open-frame-controls").performScrollTo().performClick()
        ui.onNodeWithText("Download and decode").performClick()
        ui.waitUntil(120000){ui.onAllNodesWithContentDescription("Decoded frame").fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithText("Next frame").performScrollTo().performClick()
        ui.waitUntil(15000){ui.onAllNodes(frameCounter(2)).fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithText("Previous frame").performScrollTo().performClick()
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
        ui.onNodeWithTag("download-complete").assertTextContains("Video saved.")
        ui.runOnUiThread{ui.activity.onBackPressedDispatcher.onBackPressed()}
        ui.onNodeWithContentDescription("Post 319947").assertExists()
        ui.onNode(hasSetTextAction()).assertTextContains("id:319947")
    }
}
