// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.test.platform.app.InstrumentationRegistry
import com.sakugapp.data.*
import com.sakugapp.ui.*
import org.junit.*
import org.junit.Assert.*
import kotlinx.coroutines.*

class DiscoveryNavigationDeviceTest {
 @get:Rule val ui=createAndroidComposeRule<MainActivity>()
 private fun scene(count:Int=2):Pair<FeedViewModel,AppViewModel>{
  val original=PostParser.posts(InstrumentationRegistry.getInstrumentation().context.assets.open("library-real-posts.json").bufferedReader().use{it.readText()}).take(2)
  val posts=(0 until count).map{index->original[index%original.size].copy(id=1000L+index,previewUrl=null,tags=listOf(Tag("test_artist",TagCategory.ARTIST),Tag("test_show",TagCategory.COPYRIGHT)),source="episode 12")}
  val repo=object:PostRepository{override suspend fun page(query:String,page:Int)=PostPage(posts,posts.size)}
  val discovery=DiscoveryRepository(repo,{mapOf("test_artist" to TagCategory.ARTIST,"test_show" to TagCategory.COPYRIGHT)})
  lateinit var model:FeedViewModel;lateinit var app:AppViewModel
  ui.runOnUiThread{model=FeedViewModel(SavedStateHandle(),repo);app=AppViewModel(ui.activity.application,discovery);ui.activity.setContent{Sakugapp(model,app)}}
  return model to app
 }
 @Test fun defaultHomeTabsProfileAndSearchStayDistinct(){
  val(model,_)=scene();ui.onNodeWithTag("search-home").assertIsDisplayed();ui.mainClock.advanceTimeBy(500);ui.waitForIdle();UiEvidence.capture("discovery-home.png");ui.onNodeWithTag("nav-search_home").assertIsSelected()
  ui.onNodeWithTag("home-search-input").performTextInput("test_artist");ui.onNodeWithTag("home-submit").performClick();ui.onNodeWithTag("nav-posts").assertIsSelected()
  ui.waitUntil(10000){model.state.value.query=="test_artist" && !model.state.value.loading};assertTrue(model.searchMode.value)
  ui.onNodeWithTag("nav-profile").performClick();ui.onNodeWithTag("profile-home").assertIsDisplayed();ui.mainClock.advanceTimeBy(500);ui.waitForIdle();UiEvidence.capture("discovery-profile.png");ui.onNodeWithTag("top-profile").assertDoesNotExist()
  ui.onNodeWithTag("nav-posts").performClick();assertEquals("",model.state.value.query)
  ui.runOnUiThread{model.resumeSearch()};assertEquals("test_artist",model.state.value.query)
 }
 @Test fun artistToPostsAndBackPreservesStatistics(){
  scene();ui.onNodeWithTag("home-artists").performScrollTo().performClick();ui.onNodeWithTag("discovery-query").performTextInput("test_artist")
  ui.waitUntil(10000){ui.onAllNodesWithTag("candidate-test_artist").fetchSemanticsNodes().isNotEmpty()};ui.onNodeWithTag("candidate-test_artist").performClick()
  ui.onNodeWithText(ui.activity.getString(R.string.ui_view_posts)).performScrollTo().performClick();ui.onNodeWithTag("nav-posts").assertIsSelected()
  ui.runOnUiThread{ui.activity.onBackPressedDispatcher.onBackPressed()};ui.onNodeWithTag("artist-stats").assertIsDisplayed()
 }
 @Test fun worksEpisodeDetailAndReturnUseExistingPlayer(){
  scene();ui.onNodeWithTag("home-shows").performScrollTo().performClick();ui.onNodeWithTag("discovery-query").performTextInput("test_show")
  ui.waitUntil(10000){ui.onAllNodesWithTag("candidate-test_show").fetchSemanticsNodes().isNotEmpty()};ui.onNodeWithTag("candidate-test_show").performClick()
  ui.waitUntil(10000){ui.onAllNodesWithTag("episode-episode:12").fetchSemanticsNodes().isNotEmpty()};ui.onNodeWithTag("episode-episode:12").performScrollTo().performClick()
  ui.onNodeWithTag("episode-posts").assertIsDisplayed();ui.onNodeWithTag("main-bottom-nav").assertDoesNotExist()
  ui.runOnUiThread{ui.activity.onBackPressedDispatcher.onBackPressed()};ui.onNodeWithTag("show-detail").assertIsDisplayed()
 }
 @Test fun consumedScrollHidesAndRestoresNavAndDetailFullscreenHasNoNav(){
  val(model,_)=scene(60);ui.onNodeWithTag("nav-posts").performClick();ui.waitUntil(10000){model.state.value.posts.size==60}
  ui.onNodeWithTag("post-grid").performTouchInput{swipeUp()};ui.mainClock.advanceTimeBy(1000);ui.waitForIdle();ui.onNodeWithTag("main-bottom-nav").assertDoesNotExist()
  ui.onNodeWithTag("post-grid").performTouchInput{swipeDown()};ui.mainClock.advanceTimeBy(1000);ui.waitForIdle();ui.onNodeWithTag("main-bottom-nav").assertIsDisplayed()
  ui.onNodeWithTag("post-grid").performScrollToIndex(0);ui.onNodeWithTag("post-media-1000").performClick();ui.onNodeWithTag("main-bottom-nav").assertDoesNotExist()
  ui.waitUntil(10000){ui.onAllNodesWithTag("toggle-fullscreen").fetchSemanticsNodes().isNotEmpty()}
  ui.onNodeWithTag("toggle-fullscreen").performScrollTo().performClick();ui.onNodeWithTag("main-bottom-nav").assertDoesNotExist()
  ui.runOnUiThread{ui.activity.onBackPressedDispatcher.onBackPressed()};ui.mainClock.advanceTimeBy(500);ui.waitForIdle();ui.runOnUiThread{ui.activity.onBackPressedDispatcher.onBackPressed()}
  ui.onNodeWithTag("post-grid").assertIsDisplayed();assertEquals("",model.state.value.query)
 }
 @Test fun clearingDiscoveryInputCancelsPendingSuggestions(){
  scene();ui.onNodeWithTag("home-shows").performScrollTo().performClick()
  ui.onNodeWithTag("discovery-query").performTextInput("test_show");ui.onNodeWithTag("discovery-query").performTextClearance()
  ui.mainClock.advanceTimeBy(500);ui.waitForIdle();ui.onNodeWithTag("discovery-tag-loading").assertDoesNotExist()
  ui.onNodeWithTag("candidate-test_show").assertDoesNotExist()
 }
 @Test fun nicknameUpdatesHeaderAndPersistsWithoutWebsiteLogin()=runBlocking{
  val(_,app)=scene();ui.onNodeWithTag("nav-profile").performClick();ui.onNodeWithText(ui.activity.getString(R.string.ui_edit_nickname)).performClick()
  ui.onNodeWithTag("nickname-input").performTextClearance();ui.onNodeWithTag("nickname-input").performTextInput("原画師🙂");ui.onNodeWithTag("save-nickname").performClick()
  ui.waitUntil(10000){app.localProfile.value.nickname=="原画師🙂"};assertNull(app.account.value)
  ui.onNodeWithTag("nav-search_home").performClick();ui.onNodeWithTag("top-profile").assertTextContains("原画師🙂")
  app.localProfileStore.nickname(null)
 }
}
