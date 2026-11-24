// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.*
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.google.gson.*
import com.sakugapp.data.*
import com.sakugapp.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/** Production layouts, captured public metadata and explicitly generated fixture posters. */
class ContentFirstDeviceTest {
    @get:Rule val ui=createAndroidComposeRule<PerformanceHostActivity>()
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val device get()=UiDevice.getInstance(instrumentation)
    private lateinit var app:AppViewModel
    private lateinit var previous:AppSettings
    private val files=mutableListOf<File>()
    companion object {private val layouts=CopyOnWriteArrayList<Map<String,Any?>>()}
    private fun posts():List<Post>{
        val assets=instrumentation.context.assets
        val types=assets.open("personal-tag-summary.json").bufferedReader().use{PostParser.tagSummary(it.readText())}
        val poster=File(ui.activity.cacheDir,"content-first-poster.png").also{files+=it}
        val bitmap=Bitmap.createBitmap(320,180,Bitmap.Config.ARGB_8888).apply{eraseColor(android.graphics.Color.rgb(68,96,126))}
        poster.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        return assets.open("posts-five-pages.json").bufferedReader().use{JsonParser.parseString(it.readText()).asJsonArray
            .flatMap{page->PostParser.posts(page.toString(),types)}}.distinctBy{it.id}
            .map{it.copy(previewUrl=android.net.Uri.fromFile(poster).toString(),fileUrl=null,fileExt=null)}
    }
    private fun modelApp():AppViewModel{
        if(!::app.isInitialized){
            ui.runOnUiThread{app=ViewModelProvider(ui.activity,object:ViewModelProvider.Factory{
                override fun <T:ViewModel> create(type:Class<T>):T{
                    @Suppress("UNCHECKED_CAST") return AppViewModel(ui.activity.application,
                        suggestionsOverride=object:SuggestionSource{override suspend fun suggestions(prefix:String)=emptyList<TagSuggestion>()},
                        translationsOverride=object:DetailTranslationSource{override suspend fun lookup(tag:Tag):ChineseTagName?=null}) as T
                }
            })[AppViewModel::class.java]}
            runBlocking{previous=app.preferences.settings.first();app.preferences.flag("auto",false);app.preferences.number("fontScale",1f);app.preferences.columns(2)}
        }
        return app
    }
    private fun scene(repository:PostRepository):FeedViewModel{
        val app=modelApp();lateinit var feed:FeedViewModel
        ui.runOnUiThread{
            feed=ViewModelProvider(ui.activity,object:ViewModelProvider.Factory{
                override fun <T:ViewModel> create(type:Class<T>):T{
                    @Suppress("UNCHECKED_CAST") return FeedViewModel(SavedStateHandle(),repository) as T
                }
            })[FeedViewModel::class.java]
            ui.activity.setContent{Sakugapp(feed,app)}
        }
        ui.waitUntil(10000){!feed.state.value.loading};return feed
    }
    private fun back(){ui.runOnUiThread{ui.activity.onBackPressedDispatcher.onBackPressed()};ui.mainClock.advanceTimeBy(500);ui.waitForIdle()}
    private fun position()=ui.onNodeWithTag("post-grid").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
    @After fun finish(){
        if(::previous.isInitialized)runBlocking{
            app.preferences.columns(previous.columns);app.preferences.number("fontScale",previous.fontScale)
            app.preferences.theme(previous.themeMode);app.preferences.accent(previous.accent);app.preferences.flag("auto",previous.autoPreview)
        }
        ui.runOnUiThread{ui.activity.setContent{}};files.forEach{it.delete()}
        val folder=File(ui.activity.getExternalFilesDir(null),"ui-evidence").apply{mkdirs()}
        val record=File(folder,"content-first-layouts.json")
        record.writeText(Gson().toJson(mapOf("api" to android.os.Build.VERSION.SDK_INT,"fixture" to "public metadata, generated local poster", "cases" to layouts.toList())))
        device.executeShellCommand("mkdir -p /sdcard/Download/sakugapp-ui-evidence")
        device.executeShellCommand("cp ${record.absolutePath} /sdcard/Download/sakugapp-ui-evidence/${record.name}")
    }

    @Test fun hiddenNavigationSpaceIsOccupiedByPostCardsAcrossColumnsAndThemes(){
        val values=posts();scene(object:PostRepository{override val pageSize=1000;override suspend fun page(query:String,page:Int)=PostPage(values,values.size)})
        ui.onNodeWithTag("nav-posts").performClick()
        val columns=if(ui.activity.resources.configuration.smallestScreenWidthDp>=600)listOf(2,4,6,8,12)else listOf(2,4,6)
        for(light in listOf(false,true))for(count in columns){
            runBlocking{app.preferences.columns(count);app.preferences.theme(if(light)"light"else"dark")}
            ui.waitUntil(10000){app.settings.value.columns==count && app.settings.value.themeMode==(if(light)"light"else"dark")}
            ui.onNodeWithTag("post-grid").performScrollToIndex(0);ui.mainClock.advanceTimeBy(700);ui.waitForIdle()
            val shown=ui.onNodeWithTag("post-grid").fetchSemanticsNode().boundsInRoot
            UiEvidence.capture("content-first-grid-$count-${if(light)"light"else"dark"}-shown.png")
            ui.onNodeWithTag("post-grid").performTouchInput{swipeUp(startY=height*.75f,endY=height*.35f,durationMillis=450)}
            ui.mainClock.advanceTimeBy(700);ui.waitForIdle();ui.onNodeWithTag("main-bottom-nav").assertDoesNotExist()
            val hidden=ui.onNodeWithTag("post-grid").fetchSemanticsNode().boundsInRoot
            val released=hidden.bottom-shown.bottom
            assertEquals(with(ui.density){72.dp.toPx()},released,1.5f);assertTrue(position()>0)
            val cards=ui.onAllNodes(SemanticsMatcher("post card"){it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("post-card-")==true}).fetchSemanticsNodes()
            assertTrue("The released band must contain actual cards, not only the grid background",cards.any{it.boundsInRoot.bottom>shown.bottom+1 && it.boundsInRoot.top<hidden.bottom})
            // A completely collapsed slot has empty clipped semantics bounds (Rect.Zero).
            val slot=ui.onNodeWithTag("main-nav-slot").getUnclippedBoundsInRoot()
            assertEquals(hidden.bottom,with(ui.density){slot.top.toPx()},1.5f)
            UiEvidence.capture("content-first-grid-$count-${if(light)"light"else"dark"}-hidden.png")
            ui.onNodeWithTag("post-grid").performTouchInput{swipeDown(startY=height*.35f,endY=height*.7f,durationMillis=450)}
            ui.mainClock.advanceTimeBy(700);ui.waitForIdle();ui.onNodeWithTag("main-bottom-nav").assertIsDisplayed()
            assertEquals(shown.bottom,ui.onNodeWithTag("post-grid").fetchSemanticsNode().boundsInRoot.bottom,1.5f)
            layouts+=mapOf("check" to "released_posts_space","columns" to count,"light" to light,"released_px" to released,
                "shown_viewport_px" to listOf(shown.top,shown.bottom),"hidden_viewport_px" to listOf(hidden.top,hidden.bottom),"cards_in_released_band" to true)
        }
        ui.onNodeWithTag("nav-profile").performClick();ui.onNodeWithTag("profile-home").assertIsDisplayed()
        UiEvidence.capture("content-first-profile-root.png")
        ui.onNodeWithTag("nav-search_home").performClick();ui.onNodeWithTag("search-home").assertIsDisplayed()
        UiEvidence.capture("content-first-search-root.png")
    }
    @Test fun postsTabIssuesLatestRequestsCoalescesAndDetailBackKeepsTheResult(){
        val values=posts().take(60);val requests=CopyOnWriteArrayList<Pair<String,Int>>()
        var gate:CompletableDeferred<Unit>?=null;var fail=false
        val model=scene(object:PostRepository{override val pageSize=1000;override suspend fun page(query:String,page:Int):PostPage{
            requests+=query to page;gate?.await();if(fail)throw DataFailure("HTTP 503");return PostPage(values,values.size)
        }})
        val initial=requests.size;gate=CompletableDeferred()
        ui.onNodeWithTag("nav-posts").performClick();repeat(3){ui.onNodeWithTag("nav-posts").performClick()}
        ui.waitUntil(10000){requests.size==initial+1};assertTrue(model.state.value.refreshing)
        assertEquals(values,model.state.value.posts);assertEquals("" to 1,requests.last())
        gate!!.complete(Unit);gate=null;ui.waitUntil(10000){!model.state.value.refreshing}
        ui.onNodeWithTag("search-entry").performClick();ui.onNodeWithTag("search-input").performTextReplacement("animated order:score")
        ui.onNodeWithTag("submit-search").performClick();ui.waitUntil(10000){model.state.value.query=="animated order:score" && !model.state.value.loading}
        ui.onNodeWithTag("post-grid").performScrollToIndex(8);val before=position();val count=requests.size
        ui.onNodeWithTag("post-media-${values[8].id}").performClick();ui.onNodeWithTag("detail-actions").assertExists();back()
        assertEquals(count,requests.size);assertEquals("animated order:score",model.state.value.query);assertEquals(before,position(),2f)
        fail=true;ui.onNodeWithTag("nav-posts").performClick();ui.waitUntil(10000){model.state.value.refreshError!=null}
        assertEquals("",model.state.value.query);assertEquals(values,model.state.value.posts);assertEquals("" to 1,requests.last())
        ui.onNodeWithTag("retry-refresh").assertExists();fail=false;ui.onNodeWithTag("retry-refresh").performClick()
        ui.waitUntil(10000){!model.state.value.refreshing && model.state.value.refreshError==null};assertEquals(0f,position(),1f)
        val profileRequests=requests.size;ui.onNodeWithTag("nav-profile").performClick();ui.onNodeWithTag("nav-posts").performClick()
        ui.waitUntil(10000){requests.size==profileRequests+1};assertEquals("" to 1,requests.last())
        layouts+=mapOf("check" to "latest_tab_requests","requests" to requests.map{mapOf("query" to it.first,"page" to it.second)},"detail_return_extra_requests" to 0)
    }
    @Test fun shortContentBouncesBothWaysWithoutChangingItsScrollPosition(){
        lateinit var scroll:ScrollState
        ui.runOnUiThread{ui.activity.setContent{SakugappTheme(false){Surface(Modifier.fillMaxSize().systemBarsPadding()){
            scroll=rememberScrollState();Column(Modifier.fillMaxSize().searchElasticScroll(scroll).testTag("elastic-short")){
                Text("Fixture",Modifier.padding(top=80.dp).testTag("elastic-marker"))
            }
        }}}}
        ui.waitForIdle();assertEquals(0,scroll.maxValue)
        val original=ui.onNodeWithTag("elastic-marker").fetchSemanticsNode().boundsInRoot.top
        ui.mainClock.autoAdvance=false
        try{for(direction in listOf(1f,-1f)){
            ui.onNodeWithTag("elastic-short").performTouchInput{down(Offset(width*.6f,height*.55f));moveBy(Offset(0f,direction*180f))}
            ui.mainClock.advanceTimeByFrame()
            val moved=ui.onNodeWithTag("elastic-marker").fetchSemanticsNode().boundsInRoot.top-original
            assertTrue("Edge drag must move content in its own direction",moved*direction>1)
            assertTrue(kotlin.math.abs(moved)<=with(ui.density){24.dp.toPx()}+1)
            UiEvidence.capture("content-first-search-bounce-${if(direction>0)"down"else"up"}.png")
            ui.onNodeWithTag("elastic-short").performTouchInput{up()};ui.mainClock.advanceTimeBy(1000);ui.waitForIdle()
            assertEquals(original,ui.onNodeWithTag("elastic-marker").fetchSemanticsNode().boundsInRoot.top,1f)
            assertEquals(0,scroll.value)
        }}finally{ui.mainClock.autoAdvance=true}
        layouts+=mapOf("check" to "short_search_rebound","directions" to 2,"limit_dp" to 24,"scroll_value" to scroll.value)
    }
    @Test fun actualSearchKeepsItsToolsAndImeGestures(){
        val app=modelApp();val searches=mutableListOf<String>();val routes=mutableListOf<String>()
        ui.runOnUiThread{ui.activity.setContent{SakugappTheme(false,1.25f){Surface(Modifier.fillMaxSize().systemBarsPadding()){
            SearchHomeScreen(app,AppSettings(fontScale=1.25f),{searches+=it},{routes+=it})
        }}}}
        listOf("home-combined","home-history","home-browsing","home-shows","home-artists","home-explore","home-quiz").forEach{ui.onNodeWithTag(it).performScrollTo().assertIsDisplayed()}
        ui.onNodeWithTag("home-search-input").performScrollTo().performClick().performTextInput("animated")
        fun ime():Boolean{var visible=false;ui.runOnUiThread{visible=ViewCompat.getRootWindowInsets(ui.activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime())==true};return visible}
        ui.waitUntil(15000){ime()};device.waitForIdle();Thread.sleep(350)
        // bring-into-view may leave a legitimate scroll offset after visiting the lower tools.
        ui.onNodeWithTag("search-home").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy){it(0f,-10000f)}
        ui.waitForIdle()
        val input=ui.onNodeWithTag("home-search-input")
        val textX=with(ui.density){80.dp.toPx()}
        input.performTouchInput{longClick(Offset(textX,centerY))}
        ui.waitUntil(10000){!input.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange].collapsed}
        input.performTouchInput{click(Offset(textX,centerY))}
        ui.waitUntil(10000){input.fetchSemanticsNode().config[SemanticsProperties.TextSelectionRange].collapsed}
        val before=input.fetchSemanticsNode().boundsInRoot.top
        ui.onNodeWithTag("search-home").performTouchInput{swipeDown(startY=height*.2f,endY=height*.35f,durationMillis=400)}
        ui.waitForIdle();assertEquals(before,ui.onNodeWithTag("home-search-input").fetchSemanticsNode().boundsInRoot.top,1f)
        device.waitForIdle();Thread.sleep(350);device.pressBack();ui.waitUntil(10000){!ime()};assertFalse(ui.activity.isFinishing)
        ui.onNodeWithTag("home-search-input").assertTextContains("animated")
        ui.onNodeWithTag("home-submit").performClick();assertEquals(listOf("animated"),searches)
        UiEvidence.capture("content-first-search-home.png")
    }
    @Test fun profileRowsAdaptToWindowWidthWithoutLosingNicknameOrTheEditor(){
        val app=modelApp();val before=runBlocking{app.localProfileStore.profile.first()}
        var width by mutableStateOf(if(ui.activity.resources.configuration.smallestScreenWidthDp>=600)760.dp else 320.dp)
        val routes=mutableListOf<String>();val sites=mutableListOf<String>()
        try{
            runBlocking{app.localProfileStore.nickname("ContentFirst ユーザー")}
            ui.runOnUiThread{ui.activity.setContent{SakugappTheme(true,1.25f,"pink"){Surface(Modifier.fillMaxSize().systemBarsPadding()){
                // Surface propagates its minimum width. A loose outer Box lets this inner
                // window really shrink across 600 dp instead of being clamped to the tablet.
                Box(Modifier.fillMaxSize()){Box(Modifier.width(width).fillMaxHeight()){ProfileHomeScreen(app,{routes+=it},{sites+=it})}}
            }}}}
            ui.waitUntil(10000){app.localProfile.value.nickname=="ContentFirst ユーザー"}
            val wide=width>=600.dp
            ui.onNodeWithTag(if(wide)"profile-two-column"else"profile-single-column").assertExists()
            if(wide){val card=ui.onNodeWithTag("profile-card").fetchSemanticsNode().boundsInRoot;val links=ui.onNodeWithTag("profile-functions").fetchSemanticsNode().boundsInRoot;assertTrue(card.right<links.left);assertEquals(card.top,links.top,1f)}
            val tags=listOf("profile-folders","profile-favorites","profile-account","profile-blog","profile-forum","profile-settings")
            for(tag in tags)ui.onNodeWithTag(tag).performScrollTo().assertIsDisplayed().performClick()
            assertEquals(listOf("library","website_favorites","account","settings"),routes)
            assertEquals(listOf("https://blog.sakugabooru.com/","https://www.sakugabooru.com/forum"),sites)
            UiEvidence.capture("content-first-profile-${if(wide)"wide"else"phone"}.png")
            ui.onNodeWithTag("edit-nickname").performScrollTo().performClick();ui.onNodeWithTag("nickname-input").performTextReplacement("保持草稿")
            ui.runOnUiThread{width=480.dp};ui.waitForIdle();ui.onNodeWithTag("nickname-input").assertTextContains("保持草稿")
            ui.onNodeWithText(ui.activity.getString(R.string.ui_cancel)).performClick()
            ui.onNodeWithTag("profile-single-column").assertExists()
            assertEquals("ContentFirst ユーザー",runBlocking{app.localProfileStore.profile.first()}.nickname)
            assertEquals(before.avatar,app.localProfile.value.avatar)
            val effectiveWidth=ui.onNodeWithTag("profile-home").fetchSemanticsNode().boundsInRoot.width/ui.density.density
            layouts+=mapOf("check" to "profile_responsive_rows","initial_wide" to wide,"narrow_requested_width_dp" to 480,
                "narrow_effective_width_dp" to effectiveWidth,"destinations" to routes,"sites" to sites,"draft_preserved" to true)
        }finally{runBlocking{app.localProfileStore.nickname(before.nickname)}}
    }
    @Test fun compactFrameVisualsKeepAllSixTargetsAndExploreQueries(){
        var explore by mutableStateOf(false);val steps=mutableListOf<Int>();val queries=mutableListOf<String>();val routes=mutableListOf<String>()
        ui.runOnUiThread{ui.activity.setContent{SakugappTheme(false,1.25f,"mint"){Surface(Modifier.fillMaxSize().systemBarsPadding()){
            if(explore)ExploreScreen({queries+=it},{routes+=it})else FrameControlBar(null,0,0,true,{steps+=it},active=true,actions={Row{repeat(3){ActionIcon(R.drawable.ic_more,"Fixture action",{},touchSize=36.dp)}}})
        }}}}
        val toolbar=ui.onNodeWithTag("frame-toolbar").fetchSemanticsNode().boundsInRoot
        for(delta in listOf(-3,-2,-1,1,2,3)){
            val target=ui.onNodeWithTag("step-$delta").fetchSemanticsNode().boundsInRoot
            assertEquals(with(ui.density){48.dp.toPx()},target.height,1f)
            assertEquals(with(ui.density){32.dp.toPx()},ui.onNodeWithTag("step-visual-$delta",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot.height,1f)
            assertTrue(target.left>=toolbar.left && target.right<=toolbar.right+1)
            ui.onNodeWithTag("step-$delta").performClick().assertIsSelected()
        }
        assertEquals(listOf(-3,-2,-1,1,2,3),steps);UiEvidence.capture("content-first-frame-toolbar.png")
        ui.runOnUiThread{explore=true};ui.waitForIdle()
        for(period in listOf("day","week","month","year")){
            ui.onNodeWithTag("topic-$period").performScrollTo().performClick();assertEquals(DateRangeFilter.popular(period),queries.last())
        }
        for(type in listOf("artist","copyright"))for(order in listOf("date","count")){
            ui.onNodeWithTag("topic-$type-$order").performScrollTo().performClick();assertEquals("topic_tags/$type/$order",routes.last())
        }
        for(tag in listOf("character_acting","fighting","liquid","explosions","hair","production_materials")){
            ui.onNodeWithTag("topic-$tag").performScrollTo().performClick();assertEquals(tag,queries.last())
        }
        UiEvidence.capture("content-first-explore.png")
        layouts+=mapOf("check" to "compact_tools","steps" to steps,"explore_entries" to queries.size+routes.size,"touch_height_dp" to 48,"visual_height_dp" to 32)
    }
    @Test fun chromeSettlementChangesViewportOnceAndKeepsThePlayerAndPageCache(){
        val video=File(ui.activity.cacheDir,"content-first-player.mp4").also{files+=it}
        instrumentation.context.assets.open("clip-gop.mp4").use{input->video.outputStream().use{input.copyTo(it)}}
        var visible by mutableStateOf(true);var player:androidx.media3.common.Player?=null;var created=0
        val heights=mutableListOf<Int>();val caches=mutableListOf<PageVideoCache?>()
        ui.runOnUiThread{ui.activity.setContent{SakugappTheme(false){Scaffold(bottomBar={MainNavigationChrome("posts",visible,{})}){insets->
            Box(Modifier.fillMaxSize().padding(insets).onSizeChanged{heights+=it.height}){
                PageMediaScope{
                    val cache=LocalPageVideoCache.current;SideEffect{caches+=cache}
                    VideoPanel(video.toURI().toString(),preview=true,onPlayerReady={created++;player=it})
                }
            }
        }}}}
        ui.waitUntil(20000){player!=null};ui.waitForIdle();val first=player
        ui.runOnIdle{val settled=heights.last();heights.clear();heights+=settled}
        ui.mainClock.autoAdvance=false
        try{
            ui.runOnUiThread{visible=false};repeat(16){ui.mainClock.advanceTimeBy(16)};ui.waitForIdle()
            assertEquals("No per-animation-frame viewport resizing",2,heights.distinct().size)
            ui.runOnUiThread{visible=true};repeat(16){ui.mainClock.advanceTimeBy(16)};ui.waitForIdle()
            assertEquals(2,heights.distinct().size);assertEquals(heights.first(),heights.last())
            repeat(6){ui.runOnUiThread{visible=!visible};ui.mainClock.advanceTimeBy(64)}
            ui.runOnUiThread{visible=true};ui.mainClock.advanceTimeBy(500);ui.waitForIdle()
            assertSame(first,player);assertEquals(1,created)
            assertTrue(caches.isNotEmpty());assertNotNull(caches.first());assertTrue(caches.all{it===caches.first()})
            layouts+=mapOf("check" to "settled_layout_and_player_reuse","viewport_heights_px" to heights.toList(),
                "distinct_heights" to heights.distinct().size,"player_instances" to created,"page_cache_reused" to true)
        }finally{ui.mainClock.autoAdvance=true}
    }
}
