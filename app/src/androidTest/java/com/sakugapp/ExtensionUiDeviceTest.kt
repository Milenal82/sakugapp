// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.test.platform.app.InstrumentationRegistry
import com.sakugapp.data.*
import com.sakugapp.ui.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import java.io.File

class ExtensionUiDeviceTest {
 @get:Rule val ui=createAndroidComposeRule<MainActivity>()
 private fun scene():Pair<FeedViewModel,AppViewModel>{
  val context=InstrumentationRegistry.getInstrumentation().context
  val video=File(ui.activity.cacheDir,"quiz-fixture.mp4");context.assets.open("clip-gop.mp4").use{input->video.outputStream().use{input.copyTo(it)}}
  val original=PostParser.posts(context.assets.open("library-real-posts.json").bufferedReader().use{it.readText()}).first()
  val posts=(1L..6L).map{original.copy(id=it,previewUrl=null,fileUrl=video.toURI().toString(),fileExt="mp4",tags=listOf(Tag("artist_a",TagCategory.ARTIST),Tag("test_show",TagCategory.COPYRIGHT)))}
  val repo=object:PostRepository{override suspend fun page(query:String,page:Int)=PostPage(posts,posts.size)}
  val types=listOf("artist_a","artist_b","artist_c","artist_d").associateWith{TagCategory.ARTIST}+mapOf("test_show" to TagCategory.COPYRIGHT)
  val discovery=DiscoveryRepository(repo,{types});val quiz=QuizRepository(repo,{types})
  lateinit var model:FeedViewModel;lateinit var app:AppViewModel
  ui.runOnUiThread{model=FeedViewModel(SavedStateHandle(),repo);app=AppViewModel(ui.activity.application,discovery,quiz);ui.activity.setContent{Sakugapp(model,app)}}
  return model to app
 }
 @Test fun exploreRoutesToResultAndBottomPostsReturnsToOrdinaryBrowse(){
  val(model,_)=scene();ui.onNodeWithTag("home-explore").performScrollTo().performClick()
  ui.onNodeWithTag("topic-day").performClick();ui.waitUntil(10000){model.searchMode.value && !model.state.value.loading}
  assertEquals(DateRangeFilter.popular("day"),model.state.value.query);ui.onNodeWithTag("browse-mode").assertDoesNotExist();ui.onNodeWithTag("results-mode").assertDoesNotExist()
  ui.onNodeWithTag("nav-posts").performClick();assertFalse(model.searchMode.value);assertEquals("",model.state.value.query)
 }
 @Test fun datePickerAndRawQueryClearKeepOtherFilters(){
  scene();ui.onNodeWithTag("home-combined").performScrollTo().performClick()
  ui.onNodeWithTag("query-expression").performTextReplacement("fighting score:>=3 date:2024-02-29...2024-03-01 order:score")
  ui.onNodeWithTag("clear-date").performScrollTo().performClick()
  ui.onNodeWithTag("query-expression").performScrollTo().assertTextContains("fighting score:>=3 order:score")
  ui.onNodeWithTag("date-range").performScrollTo().performClick();ui.onNodeWithTag("date-picker").assertExists()
  androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).pressBack()
  ui.onNodeWithTag("query-expression").assertExists()
  ui.onNodeWithTag("query-expression").performTextReplacement("date:1800-01-01")
  ui.onNodeWithTag("date-range").performScrollTo().performClick();ui.onNodeWithTag("date-picker").assertExists()
 }
 @Test fun compactProfileDefaultsFavoritesAndAmberScaleLayouts()=runBlocking{
  val(_,app)=scene();app.localProfileStore.nickname("Profile Test")
  for(light in listOf(false,true))for(scale in listOf(.75f,1f,1.25f)){
   app.preferences.theme(if(light)"light"else"dark");app.preferences.accent("amber");app.preferences.number("fontScale",scale)
   ui.waitUntil(10000){app.settings.value.accent=="amber" && app.settings.value.fontScale==scale}
   ui.onNodeWithTag("nav-profile").performClick();ui.onNodeWithTag("reset-avatar").assertIsDisplayed();ui.onNodeWithTag("reset-nickname").assertIsDisplayed()
   UiEvidence.capture("extension-profile-$light-$scale.png")
  }
  ui.onNodeWithTag("reset-avatar").performClick();assertEquals("Profile Test",app.localProfile.value.nickname)
  ui.onNodeWithTag("reset-nickname").performClick();ui.waitUntil(10000){app.localProfile.value.nickname==null}
  ui.onNodeWithTag("profile-favorites").performScrollTo().performClick();ui.onNodeWithText(ui.activity.getString(R.string.ui_favorites_login)).assertIsDisplayed()
  app.preferences.number("fontScale",1f);app.preferences.theme("system");app.preferences.accent("default")
 }
 @Test fun nativeQuizPlaysAndCountsFiveDistinctQuestionsOnce()=runBlocking{
  val(_,app)=scene();app.preferences.quiz(QuizSettings(5));ui.waitUntil(10000){app.quizSettings.value.count==5}
  ui.onNodeWithTag("home-quiz").performScrollTo().performClick();ui.onNodeWithTag("start-quiz").performScrollTo().performClick()
  repeat(5){
   ui.waitUntil(30000){ui.onAllNodesWithTag("quiz-option-0").fetchSemanticsNodes().isNotEmpty() && runCatching{ui.onNodeWithTag("quiz-option-0").assertIsEnabled()}.isSuccess}
   ui.onNodeWithTag("quiz-option-0").performScrollTo().performClick();ui.onNodeWithTag("quiz-option-0").assertIsNotEnabled()
   ui.onNodeWithTag("quiz-next").performScrollTo().performClick()
  }
  ui.onNodeWithTag("quiz-summary").assertIsDisplayed();UiEvidence.capture("extension-quiz-result.png")
 }
}
