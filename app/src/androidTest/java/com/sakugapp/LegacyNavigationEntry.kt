// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeTestRule
/** Existing Posts regressions explicitly select their page now that startup is Search. */
internal fun enterPostsForLegacyTest(ui:ComposeTestRule){
    ui.waitForIdle()
    if(ui.onAllNodesWithTag("nav-posts").fetchSemanticsNodes().isNotEmpty())ui.onNodeWithTag("nav-posts").performClick()
}

/** Removed overflow entries retain their proper page entry, without adding them back to production. */
internal fun openLegacyDestination(ui:ComposeTestRule,label:String){
    val target=when(label){"Favorites"->"library";"Posts"->"posts";"Settings"->"settings";"Browsing history"->"search_home";else->error("Unmapped test destination: $label")}
    ui.onNodeWithTag("main-menu").performClick();ui.onNodeWithTag("menu-$target").performClick()
    if(label=="Favorites")repeat(3){if(ui.onAllNodesWithTag("folder-back").fetchSemanticsNodes().isNotEmpty())ui.onNodeWithTag("folder-back").performClick()}
    if(label=="Browsing history")ui.onNodeWithTag("home-browsing").performScrollTo().performClick()
}
