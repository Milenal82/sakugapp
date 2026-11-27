// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.*
import androidx.test.platform.app.InstrumentationRegistry
import com.sakugapp.data.*
import com.sakugapp.ui.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import java.io.File

class V134UiDeviceTest {
 @get:Rule val ui=createAndroidComposeRule<PerformanceHostActivity>()
 private fun app():AppViewModel {
  lateinit var value:AppViewModel
  ui.runOnUiThread{value=ViewModelProvider(ui.activity)[AppViewModel::class.java]}
  return value
 }
 private fun fixture()=PostParser.posts(InstrumentationRegistry.getInstrumentation().context.assets.open("library-real-posts.json").bufferedReader().use{it.readText()}).first()
 @Test fun searchAndBrowsingHistoryKeepIndependentDataAndDestinations()=runBlocking<Unit>{
  val model=app();val query="v134_fixture score:>=3";val post=fixture();var searched="";var opened:Long?=null
  try{
   model.preferences.remember(query);model.personal.record(post)
   ui.waitUntil(15000){query in model.history.value && model.viewedPosts.value.any{it.id==post.id}}
   ui.runOnUiThread{ui.activity.setContent{SakugappTheme(false){SearchHistoryScreen(model){searched=it}}}}
   ui.onNodeWithTag("search-history-page").assertExists()
   ui.onNodeWithText(query).performScrollTo().performClick();assertEquals(query,searched)
   ui.onNodeWithTag("viewed-post-${post.id}").assertDoesNotExist()
   ui.runOnUiThread{ui.activity.setContent{SakugappTheme(true){BrowsingHistoryScreen(model,{},{p,_->opened=p.id})}}}
   ui.onNodeWithTag("browsing-history-list").performScrollToNode(hasTestTag("viewed-post-${post.id}"))
   ui.onNodeWithTag("viewed-post-${post.id}").performClick();assertEquals(post.id,opened)
   assertTrue(query in model.history.value)
   model.personal.removeHistory(post.id);assertTrue(query in model.preferences.history.first())
   UiEvidence.capture("v134-browsing-history.png")
  }finally{model.preferences.deleteHistory(query);model.personal.removeHistory(post.id)}
 }
 @Test fun resultsToolbarEditsReturnsFromDetailAndExitsToOrdinaryFeed(){
  val posts=(1L..60L).map{fixture().copy(id=it,fileUrl=null,fileExt=null,previewUrl=null)}
  val repo=object:PostRepository{override val pageSize=1000;override suspend fun page(query:String,page:Int)=PostPage(posts,posts.size)}
  val app=app();lateinit var feed:FeedViewModel
  ui.runOnUiThread{feed=ViewModelProvider(ui.activity,object:ViewModelProvider.Factory{override fun <T:ViewModel> create(c:Class<T>):T{@Suppress("UNCHECKED_CAST") return FeedViewModel(SavedStateHandle(),repo) as T}})[FeedViewModel::class.java];ui.activity.setContent{Sakugapp(feed,app)}}
  ui.onNodeWithTag("home-search-input").performTextReplacement("animated")
  ui.onNodeWithTag("home-submit").performClick();ui.onNodeWithTag("search-results-toolbar").assertIsDisplayed()
  ui.onNodeWithTag("search-entry").performClick();ui.onNodeWithTag("search-input").assertTextContains("animated")
  ui.onNodeWithTag("collapse-search").performClick()
  ui.onNodeWithTag("post-grid").performScrollToIndex(10)
  ui.onNodeWithTag("post-media-11").performClick();ui.onNodeWithTag("detail-actions").assertExists()
  ui.runOnUiThread{ui.activity.onBackPressedDispatcher.onBackPressed()};ui.waitForIdle()
  ui.onNodeWithTag("post-media-11").assertIsDisplayed();assertEquals("animated",feed.state.value.query)
  ui.onNodeWithTag("exit-search-results").performClick();ui.onNodeWithTag("browse-toolbar").assertIsDisplayed()
  assertEquals("",feed.state.value.query);assertFalse(feed.searchMode.value)
  UiEvidence.capture("v134-default-after-search.png")
 }
 @Test fun profileKeepsSixRowsAndDistinctBlogForumDestinations(){
  val app=app();val sites=mutableListOf<String>();val routes=mutableListOf<String>()
  ui.runOnUiThread{ui.activity.setContent{SakugappTheme(false){ProfileHomeScreen(app,{routes+=it},{sites+=it})}}}
  for(tag in listOf("profile-folders","profile-favorites","profile-account","profile-blog","profile-forum","profile-settings"))ui.onNodeWithTag(tag).performScrollTo().performClick()
  assertEquals(listOf("library","website_favorites","account","settings"),routes)
  assertEquals(listOf("https://blog.sakugabooru.com/","https://www.sakugabooru.com/forum"),sites)
  UiEvidence.capture("v134-profile.png")
 }
 @Test fun hiddenBottomNavigationReleasesRealGridSpaceAcrossDensities()=runBlocking<Unit>{
  val app=app();val previous=app.preferences.settings.first()
  val assets=InstrumentationRegistry.getInstrumentation().context.assets
  val types=assets.open("personal-tag-summary.json").bufferedReader().use{PostParser.tagSummary(it.readText())}
  val posts=assets.open("posts-five-pages.json").bufferedReader().use{com.google.gson.JsonParser.parseString(it.readText()).asJsonArray.flatMap{page->PostParser.posts(page.toString(),types)}}.distinctBy{it.id}.map{it.copy(previewUrl=null,fileUrl=null,fileExt=null)}
  val repo=object:PostRepository{override val pageSize=1000;override suspend fun page(query:String,page:Int)=PostPage(posts,posts.size)}
  try{
   ui.runOnUiThread{val feed=FeedViewModel(SavedStateHandle(),repo);ui.activity.setContent{Sakugapp(feed,app)}}
   ui.onNodeWithTag("nav-posts").performClick()
   val counts=if(ui.activity.resources.configuration.smallestScreenWidthDp>=600)listOf(2,4,6,8,12)else listOf(2,4,6)
   for(light in listOf(false,true))for(count in counts){
    app.preferences.columns(count);app.preferences.theme(if(light)"light"else"dark")
    ui.waitUntil(10000){app.settings.value.columns==count && app.settings.value.themeMode==(if(light)"light"else"dark")}
    ui.onNodeWithTag("post-grid").performScrollToIndex(0);ui.waitForIdle()
    val shown=ui.onNodeWithTag("post-grid").fetchSemanticsNode().boundsInRoot
    ui.onNodeWithTag("post-grid").performTouchInput{swipeUp(startY=height*.75f,endY=height*.35f,durationMillis=450)}
    ui.mainClock.advanceTimeBy(700);ui.waitForIdle();ui.onNodeWithTag("main-bottom-nav").assertDoesNotExist()
    val hidden=ui.onNodeWithTag("post-grid").fetchSemanticsNode().boundsInRoot
    assertEquals(with(ui.density){72.dp.toPx()},hidden.bottom-shown.bottom,1.5f)
    UiEvidence.capture("v134-grid-$count-$light-hidden.png")
   }
  }finally{app.preferences.columns(previous.columns);app.preferences.theme(previous.themeMode)}
 }
 @Test fun abIsTheFirstAdvancedGroupAndMoreKeepsVideoGeometry(){
  val file=File(ui.activity.cacheDir,"v134-frame.mp4")
  InstrumentationRegistry.getInstrumentation().context.assets.open("frame-index-vfr.mp4").use{input->file.outputStream().use{input.copyTo(it)}}
  try{
   ui.runOnUiThread{ui.activity.setContent{SakugappTheme(false){Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())){VideoPanel(file.toURI().toString())}}}}
   ui.onNodeWithTag("more-controls").performScrollTo()
   val before=ui.onNodeWithTag("video-gestures").getUnclippedBoundsInRoot()
   ui.onNodeWithTag("more-controls").performClick()
   ui.onNodeWithTag("ab-controls").performScrollTo()
   val a=ui.onNodeWithTag("ab-controls").getUnclippedBoundsInRoot()
   val s=ui.onNodeWithTag("speed-controls").getUnclippedBoundsInRoot();assertTrue(a.top<s.top)
   val after=ui.onNodeWithTag("video-gestures").getUnclippedBoundsInRoot()
   assertEquals(before.width,after.width);assertEquals(before.height,after.height)
   ui.onNodeWithTag("set-a").assertExists();ui.onNodeWithTag("set-b").assertExists()
   UiEvidence.capture("v134-ab-first.png")
  }finally{file.delete()}
 }
}
