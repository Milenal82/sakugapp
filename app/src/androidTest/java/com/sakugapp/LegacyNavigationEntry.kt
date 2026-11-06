// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule
/** Existing Posts regressions explicitly select their page now that startup is Search. */
internal fun enterPostsForLegacyTest(ui:ComposeTestRule){
    ui.waitForIdle()
    if(ui.onAllNodesWithTag("nav-posts").fetchSemanticsNodes().isNotEmpty())ui.onNodeWithTag("nav-posts").performClick()
}
