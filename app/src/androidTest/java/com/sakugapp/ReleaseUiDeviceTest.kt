// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.google.gson.Gson
import com.sakugapp.data.*
import com.sakugapp.ui.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import java.io.File
import kotlin.random.Random

/** Real native screens/Media3 over legal local fixtures; never a live-account or network assertion. */
class ReleaseUiDeviceTest {
 @get:Rule val ui=createAndroidComposeRule<MainActivity>()
 private fun scene():Pair<FeedViewModel,AppViewModel>{
  val context=InstrumentationRegistry.getInstrumentation().context
  val video=File(ui.activity.cacheDir,"v130-quiz.mp4");context.assets.open("clip-gop.mp4").use{input->video.outputStream().use{input.copyTo(it)}}
  val base=PostParser.posts(context.assets.open("library-real-posts.json").bufferedReader().use{it.readText()}).first()
  val artists=listOf("artist_with_a_long_original_tag","artist_b","artist_c","artist_d")
  val posts=(1L..8L).map{base.copy(id=it,previewUrl=null,fileUrl=video.toURI().toString(),fileExt="mp4",tags=listOf(Tag(artists.first(),TagCategory.ARTIST),Tag("test_show",TagCategory.COPYRIGHT)))}
  val repo=object:PostRepository{override suspend fun page(query:String,page:Int)=PostPage(posts,posts.size)}
  val types=artists.associateWith{TagCategory.ARTIST}+mapOf("test_show" to TagCategory.COPYRIGHT)
  lateinit var feed:FeedViewModel;lateinit var app:AppViewModel
  ui.runOnUiThread{feed=FeedViewModel(SavedStateHandle(),repo);app=AppViewModel(ui.activity.application,DiscoveryRepository(repo,{types}),QuizRepository(repo,{types},Random(3)),suggestionsOverride=object:SuggestionSource{override suspend fun suggestions(prefix:String)=emptyList<TagSuggestion>()});ui.activity.setContent{Sakugapp(feed,app)}}
  return feed to app
 }
 private fun bounded(tag:String){
  val root=ui.onRoot().fetchSemanticsNode().boundsInRoot
  val node=ui.onNodeWithTag(tag).performScrollTo().assertIsDisplayed().fetchSemanticsNode().boundsInRoot
  assertTrue("$tag fits width",node.left>=root.left-1 && node.right<=root.right+1)
 }
 @Test fun allFourPalettesBothBackgroundsAndThreeFontSizesFitHomeAndProfile()=runBlocking<Unit>{
  val(_,app)=scene();val matrix=mutableListOf<Map<String,Any>>()
  try{
   for(palette in listOf("default","mint","pink","amber"))for(light in listOf(false,true))for(scale in listOf(.75f,1f,1.25f)){
    app.preferences.accent(palette);app.preferences.theme(if(light)"light"else"dark");app.preferences.number("fontScale",scale)
    ui.waitUntil(10000){app.settings.value.accent==palette && app.settings.value.fontScale==scale && app.settings.value.themeMode==(if(light)"light"else"dark")};ui.waitForIdle()
    ui.onNodeWithTag("nav-search_home").performClick();ui.onNodeWithTag("home-search-input").performScrollTo()
    val first=ui.onNodeWithTag("home-combined").fetchSemanticsNode().boundsInRoot
    val second=ui.onNodeWithTag("home-history").fetchSemanticsNode().boundsInRoot
    assertEquals(first.width,second.width,1f);assertEquals(first.height,second.height,1f)
    if(scale==1f)UiEvidence.capture("v130-home-$palette-${if(light)"light"else"dark"}-100.png")
    listOf("home-combined","home-history","home-browsing","home-shows","home-artists","home-explore","home-quiz").forEach(::bounded)
    val quiz=ui.onNodeWithTag("home-quiz").fetchSemanticsNode().boundsInRoot
    assertEquals(first.width,quiz.width,1f)
    ui.onNodeWithTag("nav-profile").performClick();bounded("profile-card")
    for(tag in listOf("change-avatar","edit-nickname","reset-avatar","reset-nickname"))ui.onNodeWithTag(tag).assertExists()
    if(palette=="amber" && scale==1.25f)UiEvidence.capture("v130-profile-${if(light)"light"else"dark"}-125.png")
    bounded("profile-folders");bounded("profile-favorites")
    matrix+=mapOf("palette" to palette,"background" to if(light)"light"else"dark","font" to scale,"home_and_profile" to "passed")
   }
   val file=File(ui.activity.getExternalFilesDir(null),"ui-evidence/v130-layout-matrix.json");file.parentFile!!.mkdirs();file.writeText(Gson().toJson(matrix))
   val device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());device.executeShellCommand("cp ${file.path} /sdcard/Download/sakugapp-ui-evidence/v130-layout-matrix.json")
  }finally{app.preferences.number("fontScale",1f);app.preferences.theme("system");app.preferences.accent("default")}
 }
 @Test fun groupedSearchKeepsDraftAndDatesWhileApplyRemainsVisible(){
  val(feed,_)=scene();ui.onNodeWithTag("home-combined").performScrollTo().performClick()
  val query="~tag1 ~tag2 -excluded score:>=3 date:2024-02-29...2024-03-01 order:score rating:safe"
  ui.onNodeWithTag("query-expression").performTextReplacement(query)
  ui.onNodeWithTag("sort-rating-toggle").performScrollTo().performClick()
  ui.onNodeWithTag("query-order-score").performScrollTo().assertIsSelected()
  ui.onNodeWithTag("query-rating-safe").performScrollTo().assertIsSelected()
  ui.onNodeWithTag("apply-combined-search").assertIsDisplayed();assertEquals("",feed.state.value.query)
  ui.onNodeWithTag("clear-date").performScrollTo().performClick()
  ui.onNodeWithTag("query-expression").performScrollTo().assertTextContains("~tag1 ~tag2 -excluded score:>=3 order:score rating:safe")
  ui.onNodeWithTag("query-expression").performClick()
  fun ime():Int {var pixels=0;ui.runOnUiThread{pixels=androidx.core.view.ViewCompat.getRootWindowInsets(ui.activity.window.decorView)?.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime())?.bottom ?: 0};return pixels}
  ui.waitUntil(15000){ime()>0};ui.waitForIdle()
  val bottom=ui.onNodeWithTag("apply-combined-search").assertIsDisplayed().fetchSemanticsNode().boundsInRoot.bottom
  assertTrue("Apply stays above the real IME",bottom<=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).displayHeight-ime()+2)
  UiEvidence.capture("v130-search-groups.png")
  ui.onNodeWithTag("apply-combined-search").performClick()
  ui.waitUntil(10000){feed.searchMode.value && !feed.state.value.loading}
  assertEquals("~tag1 ~tag2 -excluded score:>=3 order:score rating:safe",feed.state.value.query)
  ui.onNodeWithTag("nav-posts").assertIsSelected()
 }
 @Test fun quizFourEqualAnswersPlayPauseAndReachRealComputedResults()=runBlocking<Unit>{
  val(_,app)=scene();app.preferences.quiz(QuizSettings(5));ui.waitUntil(10000){app.quizSettings.value.count==5}
  ui.onNodeWithTag("home-quiz").performScrollTo().performClick();ui.onNodeWithTag("start-quiz").performScrollTo().performClick()
  repeat(5){index->
   ui.waitUntil(30000){runCatching{ui.onNodeWithTag("quiz-option-0").assertIsEnabled()}.isSuccess}
   ui.onNodeWithTag("quiz-answers").performScrollTo()
   val cells=(0..3).map{ui.onNodeWithTag("quiz-option-$it").fetchSemanticsNode().boundsInRoot}
   assertEquals(cells[0].top,cells[1].top,1f);assertEquals(cells[2].top,cells[3].top,1f);assertTrue(cells[2].top>cells[0].top)
   cells.forEach{assertEquals(cells[0].width,it.width,1f);assertEquals(cells[0].height,it.height,1f)}
   if(index==0)UiEvidence.capture("v130-quiz-answers.png")
   ui.onNodeWithTag("quiz-option-0").performClick().assertIsNotEnabled()
   ui.onNodeWithTag("quiz-next").performScrollTo().performClick()
  }
  ui.onNodeWithTag("quiz-summary").assertIsDisplayed();UiEvidence.capture("v130-quiz-results.png")
  ui.onNodeWithText(ui.activity.getString(R.string.ui_quiz_restart)).performScrollTo().performClick()
  ui.waitUntil(30000){runCatching{ui.onNodeWithTag("quiz-option-0").assertIsEnabled()}.isSuccess}
  app.preferences.quiz(QuizSettings())
 }
}
