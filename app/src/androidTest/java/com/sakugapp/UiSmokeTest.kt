// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UiSmokeTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    @Test fun launchSettingsLanguageAndAccountAreNativeAndUsable(){
        ui.onNodeWithText("Menu").assertIsDisplayed().performClick()
        ui.onNodeWithText("Settings").performClick()
        ui.onNodeWithText("Appearance · Dark theme").assertIsDisplayed()
        ui.onNodeWithText("Default mute").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("Menu").performClick();ui.onNodeWithText("Language").performClick()
        ui.onNodeWithText("日本語").performClick()
        ui.waitUntil(10000){ui.onAllNodesWithText("メニュー").fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithText("メニュー").performClick();ui.onNodeWithText("言語").performClick()
        ui.onNodeWithText("繁體中文").performClick()
        ui.waitUntil(10000){ui.onAllNodesWithText("選單").fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithText("選單").performClick();ui.onNodeWithText("設定").performClick()
        ui.onNodeWithText("外觀 · 深色主題").assertIsDisplayed()
        ui.onNodeWithText("選單").performClick();ui.onNodeWithText("語言").performClick()
        ui.onNodeWithText("English").performClick()
        ui.waitUntil(10000){ui.onAllNodesWithText("Menu").fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithText("Menu").performClick();ui.onNodeWithText("My Account").performClick()
        ui.onNodeWithText("Username").assertIsDisplayed()
        ui.onNodeWithText("Password").assertIsDisplayed()
        ui.onNodeWithText("Log in").assertIsNotEnabled()
    }
    @Test fun searchDialogKeepsCombinedQueryAndDisplaysTheReference(){
        val query="~mokmok ~artist_unknown -genga -rating:questionable id:>=100 order:mpixels"
        ui.onNode(hasSetTextAction()).performTextReplacement(query)
        ui.onNodeWithText("Filters").performClick()
        ui.onNodeWithTag("query-expression").assertTextContains(query)
        ui.onNodeWithText("Search reference").performClick()
        ui.onNodeWithText("Both tags (AND)").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("~tag1 ~tag2").performScrollTo().performClick()
        ui.onNodeWithTag("query-expression").assertTextContains("~tag1 ~tag2")
        ui.onNodeWithText("Cancel").performClick()
        ui.onNode(hasSetTextAction()).assertTextContains(query)
    }
}
