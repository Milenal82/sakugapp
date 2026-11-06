// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.sakugapp.data.*
import com.sakugapp.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import java.util.concurrent.atomic.AtomicInteger

class MenuAndPoolDeviceTest {
 @get:Rule val ui=createAndroidComposeRule<MainActivity>()
 private val device get()=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
 private val lookups=AtomicInteger();private val quizLoads=AtomicInteger()
 private fun scene():Pair<FeedViewModel,AppViewModel>{
  val context=InstrumentationRegistry.getInstrumentation().context
  val base=PostParser.posts(context.assets.open("library-real-posts.json").bufferedReader().use{it.readText()}).first()
  val posts=(1L..60L).map{base.copy(id=it,previewUrl=null)}
  val source=object:PostRepository{override suspend fun page(query:String,page:Int)=PostPage(posts,posts.size)}
  val quizSource=object:PostRepository{override suspend fun page(query:String,page:Int):PostPage{quizLoads.incrementAndGet();return PostPage(emptyList(),0)}}
  val works=(0..30).map{"work_$it"}+"work_extra"
  val types=works.associateWith{TagCategory.COPYRIGHT}+listOf("artist_a","artist_b","artist_c","artist_d").associateWith{TagCategory.ARTIST}
  val suggestions=object:SuggestionSource{override suspend fun suggestions(prefix:String):List<TagSuggestion>{lookups.incrementAndGet();return (works.map{TagSuggestion(it,TagCategory.COPYRIGHT,8)}+TagSuggestion("artist_only",TagCategory.ARTIST,1)+TagSuggestion("unknown_only",TagCategory.UNKNOWN,null)).filter{it.name.contains(prefix)}}}
  lateinit var model:FeedViewModel;lateinit var app:AppViewModel
  ui.runOnUiThread{model=FeedViewModel(SavedStateHandle(),source);app=AppViewModel(ui.activity.application,DiscoveryRepository(source,{types}),QuizRepository(quizSource,{types}),suggestionsOverride=suggestions);ui.activity.setContent{Sakugapp(model,app)}}
  return model to app
 }
 private fun menu(route:String){ui.onNodeWithTag("main-menu").performClick();ui.onNodeWithTag("menu-$route").performClick()}
 private fun openPool(){ui.onNodeWithTag("home-quiz").performScrollTo().performClick();ui.onNodeWithTag("quiz-pool-open").performScrollTo().performClick()}
 private fun query(value:String){
  ui.onNodeWithTag("pool-list").performScrollToIndex(3);val before=lookups.get()
  // Semantics editing avoids racing an IME composing update against a second touch event.
  ui.onNodeWithTag("pool-query").performScrollTo().performTextReplacement(value)
  ui.runOnUiThread{(ui.activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(ui.activity.window.decorView.windowToken,0)}
  ui.mainClock.advanceTimeBy(600);ui.waitForIdle()
  ui.onNodeWithTag("pool-query").assertTextContains(value)
  ui.waitUntil(10000){lookups.get()>before};ui.waitForIdle()
 }
 private fun poolNode(tag:String):SemanticsNodeInteraction {ui.onNodeWithTag("pool-list").performScrollToNode(hasTestTag(tag));return ui.onNodeWithTag(tag)}
 @Test fun sixOrderedMenuRoutesAndOrdinaryBrowseCacheArePreserved(){
  val(model,_)=scene();ui.onNodeWithTag("nav-posts").performClick();ui.waitUntil(10000){model.state.value.posts.size==60 && !model.state.value.loading}
  ui.onNodeWithTag("post-grid").performScrollToIndex(10)
  fun position()=ui.onNodeWithTag("post-grid").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
  val before=position();val ids=model.state.value.posts.map{it.id}
  ui.runOnUiThread{model.beginSearch("work_1")};ui.waitUntil(10000){model.searchMode.value && !model.state.value.loading}
  ui.onNodeWithTag("main-menu").performClick()
  val routes=listOf("posts","search_home","account","library","settings","legal")
  ui.onAllNodes(hasClickAction() and hasAnyAncestor(hasTestTag("main-overflow"))).assertCountEquals(6)
  val tops=routes.map{ui.onNodeWithTag("menu-$it").fetchSemanticsNode().boundsInRoot.top};assertEquals(tops.sorted(),tops)
  UiEvidence.capture("extension-menu-six.png");device.pressBack();ui.onNodeWithTag("main-overflow").assertDoesNotExist();assertTrue(model.searchMode.value)
  menu("posts");assertFalse(model.searchMode.value);assertEquals(ids,model.state.value.posts.map{it.id});assertEquals(before.toDouble(),position().toDouble(),2.0)
  menu("search_home");ui.onNodeWithTag("search-home").assertIsDisplayed();ui.onNodeWithTag("nav-search_home").assertIsSelected()
  menu("account");ui.onNodeWithText(ui.activity.getString(R.string.ui_username)).assertExists();ui.onNodeWithTag("profile-home").assertDoesNotExist()
  menu("library");ui.waitUntil(10000){ui.onAllNodesWithTag("folder-entry-0").fetchSemanticsNodes().isNotEmpty()};ui.onNodeWithTag("website-favorites").assertDoesNotExist();ui.onNodeWithTag("tag-favorites-entry").assertExists()
  menu("settings");ui.onNodeWithText(ui.activity.getString(R.string.ui_app_language)).performScrollTo().assertExists()
  menu("legal");ui.onNodeWithText(ui.activity.getString(R.string.ui_gpl_license)).assertExists();ui.onNodeWithText(ui.activity.getString(R.string.ui_notices)).assertExists();device.pressBack()
  menu("search_home");ui.waitUntil(10000){ui.onAllNodesWithTag("nav-profile").fetchSemanticsNodes().isNotEmpty()};ui.onNodeWithTag("nav-profile").performClick();ui.onNodeWithText(ui.activity.getString(R.string.ui_blog)).performScrollTo().assertExists();ui.onNodeWithText(ui.activity.getString(R.string.ui_forum)).performScrollTo().assertExists();assertFalse(ui.activity.isFinishing)
 }
 @Test fun poolAutoSavesAddsDeduplicatesSwitchesAndConfirmsClear()=runBlocking<Unit>{
  val(_,app)=scene();app.preferences.quiz(QuizSettings(5));ui.waitUntil(10000){app.quizSettings.value.count==5};openPool();assertEquals(0,lookups.get())
  ui.onNodeWithTag("pool-mode-custom").performClick();ui.waitUntil(10000){app.quizSettings.value.custom};query("work_")
  poolNode("pool-add-work_0").performClick();ui.waitUntil(10000){app.quizSettings.value.works==listOf("work_0")};poolNode("pool-add-work_0").assertIsNotEnabled()
  poolNode("pool-add-work_1").performClick();ui.waitUntil(10000){app.quizSettings.value.works.size==2}
  ui.onNodeWithTag("pool-list").performScrollToIndex(0);ui.onNodeWithTag("pool-mode-all").performClick();ui.waitUntil(10000){!app.quizSettings.value.custom}
  assertEquals(listOf("work_0","work_1"),app.quizSettings.value.works)
  ui.onNodeWithContentDescription(ui.activity.getString(R.string.ui_back_to_app)).performClick();ui.onNodeWithTag("start-quiz").assertIsEnabled()
  ui.onNodeWithTag("quiz-pool-open").performClick();val count=lookups.get();ui.onNodeWithTag("pool-mode-custom").performClick();ui.waitUntil(10000){app.quizSettings.value.custom};poolNode("pool-selected-work_0").assertExists();assertEquals(count,lookups.get())
  poolNode("pool-remove-work_0").performClick();ui.waitUntil(10000){app.quizSettings.value.works==listOf("work_1")}
  poolNode("pool-clear").performClick();ui.onNodeWithText(ui.activity.getString(R.string.ui_cancel)).performClick();assertEquals(listOf("work_1"),app.quizSettings.value.works)
  poolNode("pool-clear").performClick();ui.onNodeWithTag("pool-confirm-clear").performClick();ui.waitUntil(10000){app.quizSettings.value.works.isEmpty()}
  val saved=PreferencesStore(ui.activity).quiz.first();assertTrue(saved.custom);assertEquals(5,saved.count);assertTrue(saved.works.isEmpty());assertEquals(0,quizLoads.get())
  ui.onNodeWithContentDescription(ui.activity.getString(R.string.ui_back_to_app)).performClick();ui.onNodeWithTag("start-quiz").assertIsNotEnabled();ui.onNodeWithText(ui.activity.getString(R.string.ui_quiz_choose_work)).assertExists()
  app.preferences.quiz(QuizSettings())
 }
 @Test fun poolRejectsNonCopyrightAndEnforcesThirtyWithoutDroppingOldSelections()=runBlocking<Unit>{
  val(_,app)=scene();val old=(0..29).map{"work_$it"};app.preferences.quiz(QuizSettings(custom=true,works=old));ui.waitUntil(10000){app.quizSettings.value.works.size==30};openPool()
  query("artist");ui.onNodeWithTag("pool-add-artist_only").assertDoesNotExist();assertEquals(old,app.quizSettings.value.works)
  query("unknown");ui.onNodeWithTag("pool-add-unknown_only").assertDoesNotExist()
  query("extra");poolNode("pool-add-work_extra").performClick();ui.onNodeWithTag("pool-list").performScrollToIndex(0)
  ui.waitUntil(10000){ui.onAllNodesWithTag("pool-save-error").fetchSemanticsNodes().isNotEmpty()};ui.onNodeWithTag("pool-save-error").assertTextEquals(ui.activity.getString(R.string.ui_pool_limit));assertEquals(old,PreferencesStore(ui.activity).quiz.first().works)
  for(light in listOf(false,true)){
   app.preferences.theme(if(light)"light"else"dark");app.preferences.accent("amber");app.preferences.number("fontScale",1.25f)
   ui.waitUntil(10000){app.settings.value.fontScale==1.25f && app.settings.value.accent=="amber" && app.settings.value.themeMode==if(light)"light"else"dark"};ui.waitForIdle();UiEvidence.capture("extension-pool-$light.png")
  }
  app.preferences.quiz(QuizSettings());app.preferences.number("fontScale",1f);app.preferences.theme("system");app.preferences.accent("default")
 }
}
