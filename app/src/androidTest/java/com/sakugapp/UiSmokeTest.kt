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
        ui.onNodeWithText("メニュー").performClick();ui.onNodeWithText("Language").performClick()
        ui.onNodeWithText("English").performClick()
        ui.waitUntil(10000){ui.onAllNodesWithText("Menu").fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithText("Menu").performClick();ui.onNodeWithText("My Account").performClick()
        ui.onNodeWithText("Username").assertIsDisplayed()
        ui.onNodeWithText("Password").assertIsDisplayed()
        ui.onNodeWithText("Log in").assertIsNotEnabled()
    }
}
