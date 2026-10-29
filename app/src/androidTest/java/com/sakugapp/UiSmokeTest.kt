// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import org.junit.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UiSmokeTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private fun screenLuminance():Float {
        val pixels=ui.onRoot().captureToImage().toPixelMap();var sum=0f;var count=0
        for(y in 8 until pixels.height step 24)for(x in 8 until pixels.width step 24){sum+=pixels[x,y].luminance();count++}
        return sum/count
    }
    @Test fun launchSettingsLanguageAndAccountAreNativeAndUsable(){
        ui.onNodeWithText("Menu").assertIsDisplayed().performClick()
        ui.onNodeWithText("Settings").performClick()
        ui.onNodeWithText("Appearance").assertIsDisplayed()
        ui.onNodeWithText("Default mute").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("Menu").performClick();ui.onNodeWithText("Language").performClick()
        ui.onNodeWithText("日本語").performClick()
        ui.waitUntil(10000){ui.onAllNodesWithText("メニュー").fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithText("メニュー").performClick();ui.onNodeWithText("言語").performClick()
        ui.onNodeWithText("繁體中文").performClick()
        ui.waitUntil(10000){ui.onAllNodesWithText("選單").fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithText("選單").performClick();ui.onNodeWithText("設定").performClick()
        ui.onNodeWithText("外觀").assertIsDisplayed()
        ui.onNodeWithText("選單").performClick();ui.onNodeWithText("語言").performClick()
        ui.onNodeWithText("简体中文").performClick()
        ui.waitUntil(10000){ui.onAllNodesWithText("菜单").fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithText("菜单").performClick();ui.onNodeWithText("设置").performClick()
        ui.onNodeWithText("外观").assertIsDisplayed()
        UiEvidence.capture("compact-settings-zh-cn.png")
        ui.onNodeWithText("菜单").performClick();ui.onNodeWithText("语言").performClick()
        ui.onNodeWithText("English").performClick()
        ui.waitUntil(10000){ui.onAllNodesWithText("Menu").fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithText("Menu").performClick();ui.onNodeWithText("My Account").performClick()
        ui.onNodeWithText("Username").assertIsDisplayed()
        ui.onNodeWithText("Password").assertIsDisplayed()
        ui.onNodeWithText("Log in").assertIsNotEnabled()
    }
    @Test fun lightAndDarkThemeChoicesApplyAndPersist(){
        ui.onNodeWithText("Menu").performClick();ui.onNodeWithText("Settings").performClick()
        lateinit var app:com.sakugapp.ui.AppViewModel
        ui.runOnUiThread{app=androidx.lifecycle.ViewModelProvider(ui.activity)[com.sakugapp.ui.AppViewModel::class.java]}
        try{
        ui.onNodeWithContentDescription("Light").performClick().assertIsSelected()
        org.junit.Assert.assertTrue("Light settings background",screenLuminance()>0.6f)
        ui.activityRule.scenario.recreate()
        ui.waitUntil(10000){ui.onAllNodesWithContentDescription("Light").fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithContentDescription("Light").assertIsSelected()
        ui.onNodeWithContentDescription("Dark").performClick().assertIsSelected()
        org.junit.Assert.assertTrue("Dark settings background",screenLuminance()<0.2f)
        }finally{
            ui.runOnUiThread{app.theme("dark")}
            ui.waitUntil(10000){app.settings.value.themeMode=="dark"}
        }
    }
    @Test fun scoreShortcutsReplaceOnlyTheScoreCondition(){
        ui.onNodeWithTag("search-entry").performClick()
        ui.onNode(hasSetTextAction()).performTextReplacement("~mokmok ~artist_unknown score:>1 -rating:questionable order:score")
        ui.onNodeWithTag("open-combined-search").performScrollTo().performClick()
        ui.onNodeWithText("score:>=3",substring=false).performScrollTo().performClick()
        ui.onNodeWithTag("query-expression").assertTextContains("~mokmok ~artist_unknown -rating:questionable order:score score:>=3")
        ui.onNodeWithText("score:>=100",substring=false).performScrollTo().performClick()
        ui.onNodeWithTag("query-expression").assertTextContains("~mokmok ~artist_unknown -rating:questionable order:score score:>=100")
        ui.onNodeWithText("Cancel").performClick()
    }
    @Test fun searchDialogKeepsCombinedQueryAndDisplaysTheReference(){
        val query="~mokmok ~artist_unknown -genga -rating:questionable id:>=100 order:mpixels"
        ui.onNodeWithTag("search-entry").performClick()
        ui.onNode(hasSetTextAction()).performTextReplacement(query)
        ui.onNodeWithTag("open-combined-search").performScrollTo().performClick()
        ui.onNodeWithTag("query-expression").assertTextContains(query)
        ui.onNodeWithTag("search-reference-tool").performScrollTo().performClick()
        ui.onNodeWithText("Both tags (AND)").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("~tag1 ~tag2").performScrollTo().performClick()
        ui.onNodeWithTag("query-expression").assertTextContains("~tag1 ~tag2")
        ui.onNodeWithText("Cancel").performClick()
        ui.onNodeWithTag("search-entry").performClick()
        ui.onNodeWithTag("search-input").assertTextContains(query)
    }
}
