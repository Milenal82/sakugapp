// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.*
import com.google.gson.JsonParser
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

/** Uses the actual app navigation; the saved post is a legally captured public response. */
@RunWith(AndroidJUnit4::class)
class PersonalFeatureUiDeviceTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private fun app():AppViewModel {lateinit var result:AppViewModel;ui.runOnUiThread{result=ViewModelProvider(ui.activity)[AppViewModel::class.java]};return result}
    private fun menu(label:String){ui.onNodeWithText("Menu",substring=false).performClick();ui.onNodeWithText(label,substring=false).performClick()}
    @Test fun detailVisitsTagNotesFavoritesSearchAndHistoryUseIndependentNativePages(){
        val app=app();val instrumentation=InstrumentationRegistry.getInstrumentation();val device=UiDevice.getInstance(instrumentation)
        val categories=PostParser.tagSummary(instrumentation.context.assets.open("personal-tag-summary.json").bufferedReader().use{it.readText()})
        val post=PostParser.posts(instrumentation.context.assets.open("library-real-posts.json").bufferedReader().use{it.readText()},categories).first()
        runBlocking{app.localLibrary.ready();app.localLibrary.membership(post,1,true);app.personal.clearHistory();app.personal.recording(true);app.personal.removeTag("mokmok");app.personal.note("mokmok","")}
        try{
            menu("Favorites");ui.onNodeWithTag("folder-entry-1").performClick()
            ui.onNodeWithTag("post-media-${post.id}").performClick()
            ui.waitUntil(15000){app.viewedPosts.value.any{it.id==post.id}}
            ui.onNodeWithTag("detail-tag-mokmok").performScrollTo()
            ui.onNodeWithText("mokmok",substring=false).performTouchInput{longClick()}
            ui.onNodeWithTag("toggle-tag-favorite").performClick()
            ui.waitUntil(10000){app.savedTags.value.any{it.name=="mokmok"}}
            ui.onNodeWithTag("edit-tag-note").performClick()
            ui.onNodeWithTag("tag-note-input").performTextReplacement("Animation study\nlocal note")
            ui.onNodeWithTag("save-tag-note").performClick()
            ui.waitUntil(10000){app.tagNotes.value.any{it.name=="mokmok" && it.note.contains("local note")}}
            ui.onNodeWithText("Close",substring=false).performClick()
            device.pressBack();menu("Browsing history")
            ui.onNodeWithTag("viewed-post-${post.id}").assertIsDisplayed()
            UiEvidence.capture("history-page.png")
            ui.activityRule.scenario.recreate()
            ui.onNodeWithTag("viewed-post-${post.id}").assertIsDisplayed().performClick()
            ui.onNodeWithTag("detail-post-id").performScrollTo().assertIsDisplayed();device.pressBack()
            ui.onNodeWithTag("viewed-post-${post.id}").assertIsDisplayed()
            ui.onNodeWithTag("record-browsing-history").performClick();ui.waitUntil(10000){!app.recordHistory.value}
            ui.onNodeWithTag("clear-browsing-history").performClick()
            assertTrue(app.viewedPosts.value.isNotEmpty())
            ui.onNodeWithTag("confirm-clear-browsing-history").performClick();ui.waitUntil(10000){app.viewedPosts.value.isEmpty()}
            assertTrue(app.library.value.contains(post.id));assertTrue(app.savedTags.value.any{it.name=="mokmok"})
            menu("Favorites");ui.onNodeWithTag("tag-favorites-entry").performClick()
            ui.onNodeWithTag("saved-tags-search").performTextInput("local note")
            ui.onNodeWithTag("saved-tag-mokmok").assertIsDisplayed()
            ui.runOnUiThread{(ui.activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(ui.activity.window.decorView.windowToken,0)}
            ui.waitForIdle();UiEvidence.capture("tag-favorites-page.png")
            ui.onNodeWithTag("saved-tag-options-mokmok").performClick()
            ui.onNodeWithTag("edit-tag-note").performClick()
            UiEvidence.capture("tag-note-editor.png")
            ui.onNodeWithTag("tag-note-input").performTextReplacement("Local revised note")
            ui.onNodeWithTag("save-tag-note").performClick();ui.onNodeWithText("Close",substring=false).performClick()
            ui.onNodeWithTag("saved-tags-search").performTextClearance()
            ui.onNodeWithTag("saved-tag-mokmok").performClick()
            ui.onNodeWithTag("search-tag-favorite").assertIsDisplayed();ui.onNodeWithTag("grid-columns").assertDoesNotExist()
            ui.onNodeWithTag("search-entry").assertTextContains("mokmok",substring=true)
            ui.onNodeWithTag("search-tag-favorite").performClick();ui.onNodeWithTag("toggle-tag-favorite").performClick()
            ui.waitUntil(10000){app.savedTags.value.none{it.name=="mokmok"}}
            assertEquals("Local revised note",app.tagNotes.value.single{it.name=="mokmok"}.note)
            assertTrue(app.viewedPosts.value.isEmpty())
        }finally{runBlocking{app.personal.recording(true);app.personal.clearHistory();app.personal.removeTag("mokmok");app.personal.note("mokmok","");app.localLibrary.membership(post,1,false)}}
    }
    @Test fun historyReturnRestoresScrolledPositionAfterRecencyReorderingAndSingleDelete(){
        val app=app();val instrumentation=InstrumentationRegistry.getInstrumentation();val device=UiDevice.getInstance(instrumentation)
        val posts=instrumentation.context.assets.open("posts-five-pages.json").bufferedReader().use{PostParser.posts(JsonParser.parseString(it.readText()).asJsonArray[0].toString())}.take(24)
        runBlocking{app.personal.clearHistory();app.personal.recording(true);posts.forEach{app.personal.record(it)}}
        try{
            menu("Browsing history");ui.waitUntil(10000){app.viewedPosts.value.size==24}
            ui.onNodeWithTag("browsing-history-list").performScrollToIndex(8)
            val bounds=ui.onNodeWithTag("browsing-history-list").fetchSemanticsNode().boundsInRoot
            val row=ui.onAllNodes(hasClickAction()).fetchSemanticsNodes().last{node->node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("viewed-post-")==true && node.boundsInRoot.top>=bounds.top && node.boundsInRoot.bottom<=bounds.bottom}
            val tag=row.config[SemanticsProperties.TestTag]
            val before=ui.onNodeWithTag("browsing-history-list").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
            ui.onNodeWithTag(tag).performClick();ui.onNodeWithTag("detail-post-id").performScrollTo().assertIsDisplayed();device.pressBack()
            ui.waitForIdle()
            val after=ui.onNodeWithTag("browsing-history-list").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
            assertEquals("History scroll position survives detail recency update",before.toDouble(),after.toDouble(),2.0)
            val visible=ui.onAllNodes(hasClickAction()).fetchSemanticsNodes().first{node->node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("remove-viewed-")==true && node.boundsInRoot.top>=bounds.top && node.boundsInRoot.bottom<=bounds.bottom}
            ui.onNodeWithTag(visible.config[SemanticsProperties.TestTag]).performClick();ui.waitUntil(10000){app.viewedPosts.value.size==23}
        }finally{runBlocking{app.personal.clearHistory();app.personal.recording(true)}}
    }
    @Test fun newLanguagePickerPersistsAcrossRecreationAndKeepsWebsiteLocaleSeparate(){
        val app=app()
        menu("Language")
        val choices=listOf("fr" to "Français","es" to "Español","ru" to "Русский","ko" to "한국어")
        try{
            for((code,label)in choices){
                ui.onNodeWithText(label,substring=false).performScrollTo().performClick()
                ui.waitUntil(20000){ui.activity.resources.configuration.locales[0].language==code}
                ui.onNodeWithText(ui.activity.getString(R.string.ui_app_language),substring=false).assertIsDisplayed()
                assertEquals("en",PreferenceRules.siteLanguage(code));assertEquals(code,app.settings.value.language)
                UiEvidence.capture("language-$code.png")
                ui.activityRule.scenario.recreate()
                ui.waitUntil(20000){ui.activity.resources.configuration.locales[0].language==code}
            }
        }finally{ui.runOnUiThread{app.language("en")};ui.waitUntil(20000){ui.activity.resources.configuration.locales[0].language=="en"}}
    }
}
