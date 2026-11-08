// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import android.content.Intent
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.test.platform.app.InstrumentationRegistry
import com.sakugapp.data.*
import com.sakugapp.ui.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import java.io.File

class DebugLinksDeviceTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private fun fixture():Post = PostParser.posts(instrumentation.context.assets.open("library-real-posts.json").bufferedReader().use{it.readText()}).first()
    private fun posts(count:Int=30):List<Post> = (1..count).map{fixture().copy(id=it.toLong(),fileUrl=null,fileExt=null,previewUrl=null,tags=listOf(Tag("artist_a",TagCategory.ARTIST),Tag("test_show",TagCategory.COPYRIGHT)),source="episode 12")}
    private fun scene():Pair<FeedViewModel,AppViewModel>{
        val posts=posts();val repo=object:PostRepository{override suspend fun page(query:String,page:Int)=PostPage(if(page==1)posts else emptyList(),if(page==1)posts.size else 0)}
        val discovery=DiscoveryRepository(repo,{mapOf("artist_a" to TagCategory.ARTIST,"test_show" to TagCategory.COPYRIGHT)})
        lateinit var model:FeedViewModel;lateinit var app:AppViewModel
        ui.runOnUiThread{model=FeedViewModel(SavedStateHandle(),repo);app=AppViewModel(ui.activity.application,discovery);ui.activity.setContent{Sakugapp(model,app)}}
        ui.waitUntil(10000){!model.state.value.loading}
        return model to app
    }
    private fun back(){ui.runOnUiThread{ui.activity.onBackPressedDispatcher.onBackPressed()};ui.mainClock.advanceTimeBy(500);ui.waitForIdle()}
    @Test fun sixEqualActionsKeepFixedHeightAndFavoriteAndGuestVoteWork(){
        var login=0;lateinit var app:AppViewModel
        val file=File(ui.activity.cacheDir,"six-actions.mp4").apply{writeBytes(instrumentation.context.assets.open("frame-index-vfr.mp4").use{it.readBytes()})}
        val post=fixture().copy(fileUrl=file.toURI().toString(),fileExt="mp4")
        try{
            for(light in listOf(false,true))for(scale in listOf(.75f,1.25f)){
                ui.runOnUiThread{app=AppViewModel(ui.activity.application);ui.activity.setContent{SakugappTheme(light,scale){Surface(Modifier.fillMaxSize().systemBarsPadding()){PostDetail(post,AppSettings(fontScale=scale),false,app,false,{},{},{login++},{},{},false,true,{true})}}}}
                ui.waitUntil(20000){ui.onAllNodesWithContentDescription(ui.activity.getString(R.string.ui_video_frame_rendered)).fetchSemanticsNodes().isNotEmpty()}
                ui.onNodeWithTag("detail-actions").performScrollTo()
                val tags=listOf("previous-post","next-post","vote-icon","favorite-action","share-post","download-video")
                val nodes=tags.map{ui.onNodeWithTag(it).fetchSemanticsNode()}
                val toolbar=ui.onNodeWithTag("detail-actions").fetchSemanticsNode()
                val (bounds,bar)=ui.runOnIdle{nodes.map{it.boundsInRoot} to toolbar.boundsInRoot}
                bounds.zipWithNext().forEach{(a,b)->assertEquals(a.top,b.top,1f);assertTrue(a.right<=b.left+1f)}
                bounds.forEach{assertTrue(it.width>=with(ui.density){48.dp.toPx()}-1);assertTrue(it.left>=bar.left-1 && it.right<=bar.right+1)}
                ui.onNodeWithTag("previous-post").assertIsNotEnabled();ui.onNodeWithTag("next-post").assertIsEnabled()
                ui.onNodeWithTag("vote-icon").performClick();assertTrue(login>0)
                assertEquals(bar.height,ui.onNodeWithTag("detail-actions").fetchSemanticsNode().boundsInRoot.height,.1f)
                ui.onNodeWithTag("favorite-action").performClick();ui.onNodeWithTag("favorite-folder-1").assertExists()
                ui.onNodeWithText(ui.activity.getString(R.string.ui_close)).performClick()
                ui.mainClock.advanceTimeBy(500);ui.waitForIdle()
                ui.onNodeWithTag("favorite-folder-1").assertDoesNotExist()
                ui.onNodeWithTag("detail-actions").performScrollTo()
                UiEvidence.capture("debug-six-actions-$light-${(scale*100).toInt()}.png")
                if(!light && scale==.75f){
                    val device=androidx.test.uiautomator.UiDevice.getInstance(instrumentation)
                    ui.onNodeWithTag("share-post").performClick()
                    ui.waitUntil(10000){device.currentPackageName!=ui.activity.packageName}
                    device.pressBack();ui.waitForIdle();ui.onNodeWithTag("detail-actions").assertIsDisplayed()
                }
            }
            val chooser=postShareIntent(post);assertEquals(Intent.ACTION_CHOOSER,chooser.action)
            @Suppress("DEPRECATION") val send=chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
            assertEquals(Intent.ACTION_SEND,send.action);assertEquals("text/plain",send.type);assertEquals(post.pageUrl,send.getStringExtra(Intent.EXTRA_TEXT))
            ui.onNodeWithTag("detail-post-id").performScrollTo().assertHasClickAction()
        }finally{file.delete()}
    }
    @Test fun toolbarFavoritePickerCanSelectAndCancelWithoutChangingOtherFavorites()=runBlocking<Unit>{
        lateinit var app:AppViewModel
        val post=fixture().copy(id=98765001L)
        ui.runOnUiThread{app=AppViewModel(ui.activity.application);ui.activity.setContent{SakugappTheme(false){Surface(Modifier.fillMaxSize().systemBarsPadding()){FavoriteControl(post,app)}}}}
        app.localLibrary.ready()
        try{
            ui.waitUntil(10000){app.library.value.folders.any{it.id==1L}}
            ui.onNodeWithTag("favorite-action").performClick()
            ui.onNodeWithTag("favorite-folder-1").performClick()
            ui.waitUntil(10000){app.library.value.contains(post.id,1L)}
            ui.onNodeWithTag("favorite-folder-1").assertIsOn().performClick()
            ui.waitUntil(10000){!app.library.value.contains(post.id,1L)}
            ui.onNodeWithTag("favorite-folder-1").assertIsOff()
            ui.onNodeWithText(ui.activity.getString(R.string.ui_close)).performClick()
        }finally{app.localLibrary.batch(setOf(post.id),null,null)}
    }
    @Test fun actualPostsGridHonorsBothAxesAtZeroAndTenForSixEightAndTwelveColumns(){
        val posts=posts(48);var settings by mutableStateOf(AppSettings(columns=6,cardSpacing=0f));lateinit var grid:LazyGridState
        ui.runOnUiThread{val app=AppViewModel(ui.activity.application);ui.activity.setContent{SakugappTheme(false){Surface(Modifier.fillMaxSize().systemBarsPadding()){
            grid=rememberLazyGridState();CompositionLocalProvider(LocalTagActions provides app){PostsScreen(FeedState(posts=posts,endReached=true),grid,settings,emptyList(),false,app,{},{},{},{})}
        }}}}
        for(columns in listOf(6,8,12))for(spacing in listOf(0f,10f)){
            ui.runOnUiThread{settings=settings.copy(columns=columns,cardSpacing=spacing)};ui.waitForIdle();ui.onNodeWithTag("post-grid").performScrollToIndex(0)
            val first=ui.onNodeWithTag("post-card-1").fetchSemanticsNode().boundsInRoot
            val second=ui.onNodeWithTag("post-card-2").fetchSemanticsNode().boundsInRoot
            val below=ui.onNodeWithTag("post-card-${columns+1}").fetchSemanticsNode().boundsInRoot
            val expected=with(ui.density){spacing.dp.toPx()}
            assertEquals("Horizontal $columns / $spacing",expected,second.left-first.right,1.1f)
            assertEquals("Vertical $columns / $spacing",expected,below.top-first.bottom,1.1f)
            if(columns==6)UiEvidence.capture("debug-grid-$columns-${spacing.toInt()}.png")
        }
    }
    @Test fun verifiedArtistOpensStatsAndJointSearchReturnsToSameStatistics(){
        val(model,_)=scene();ui.onNodeWithTag("nav-posts").performClick()
        ui.onNodeWithTag("post-grid").performScrollToIndex(0);ui.onAllNodesWithTag("tag-artist_a")[0].performClick()
        ui.onNodeWithTag("view-artist-stats").performClick();ui.onNodeWithTag("artist-stats").assertIsDisplayed()
        ui.waitUntil(10000){ui.onAllNodesWithTag("frequency-test_show").fetchSemanticsNodes().isNotEmpty()}
        ui.onAllNodesWithTag("frequency-test_show")[0].performScrollTo().performClick()
        ui.waitUntil(10000){model.state.value.query=="artist_a test_show" && !model.state.value.loading}
        back();ui.onNodeWithTag("artist-stats").assertIsDisplayed();UiEvidence.capture("debug-linked-artist.png")
    }
    @Test fun episodeArtistFilterKeepsSampleScopeAndDetailBackKeepsScroll(){
        val(model,_)=scene();ui.onNodeWithTag("home-shows").performScrollTo().performClick()
        ui.onNodeWithTag("discovery-query").performTextInput("test_show")
        ui.waitUntil(10000){ui.onAllNodesWithTag("candidate-test_show").fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithTag("candidate-test_show").performClick()
        ui.waitUntil(10000){ui.onAllNodesWithTag("episode-episode:12").fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithTag("episode-episode:12").performScrollTo().performClick()
        ui.onNodeWithTag("sample-artist-filter").performScrollTo().performClick()
        ui.onNodeWithTag("frequency-artist_a").performScrollTo().performClick()
        ui.onNodeWithTag("search-artist").performClick()
        ui.waitUntil(10000){model.state.value.sampleOnly && model.state.value.query=="test_show artist_a"}
        ui.onNodeWithTag("episode-sample-scope").assertExists()
        ui.onNodeWithTag("post-grid").performScrollToIndex(18)
        ui.onNodeWithTag("post-media-18").performClick();ui.onNodeWithTag("detail-actions").assertExists()
        back();ui.onNodeWithTag("post-media-18").assertIsDisplayed();assertTrue(model.state.value.sampleOnly)
        back();ui.onNodeWithTag("episode-posts").assertIsDisplayed();UiEvidence.capture("debug-episode-artists.png")
    }
    @Test fun tagSelectionSurvivesAndAndSearchAndEditorShareTheSameRawQuery()=runBlocking<Unit>{
        val(model,app)=scene();app.personal.saveTag(Tag("artist_a",TagCategory.ARTIST));app.personal.saveTag(Tag("test_show",TagCategory.COPYRIGHT))
        try{
            ui.onNodeWithTag("main-menu").performClick();ui.onNodeWithText(ui.activity.getString(R.string.ui_menu_library)).performClick()
            ui.onNodeWithTag("tag-favorites-entry").performClick();ui.waitUntil(10000){app.savedTags.value.size>=2}
            ui.onNodeWithTag("select-tags").performClick();ui.onNodeWithTag("select-tag-artist_a").performClick();ui.onNodeWithTag("select-tag-test_show").performClick()
            ui.onNodeWithTag("edit-selected-tags").performClick();ui.onNodeWithTag("query-expression").assertTextContains("artist_a test_show")
            ui.onNodeWithText(ui.activity.getString(R.string.ui_cancel),substring=false).performClick()
            ui.onNodeWithTag("search-selected-tags").performClick();ui.waitUntil(10000){model.state.value.query=="artist_a test_show" && !model.state.value.loading}
            back();ui.onNodeWithTag("select-tag-artist_a").assertIsOn();ui.onNodeWithTag("select-tag-test_show").assertIsOn()
            UiEvidence.capture("debug-tags-multi.png")
        }finally{app.personal.removeTag("artist_a");app.personal.removeTag("test_show")}
    }
}
