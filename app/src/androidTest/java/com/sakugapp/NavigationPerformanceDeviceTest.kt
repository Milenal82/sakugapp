// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.*
import androidx.navigation.*
import androidx.navigation.compose.*
import com.sakugapp.data.*
import com.sakugapp.ui.*
import org.junit.*
import org.junit.Assert.*
import java.io.File

class NavigationPerformanceDeviceTest {
    @get:Rule val ui=createAndroidComposeRule<PerformanceHostActivity>()
    private fun scene():Pair<FeedViewModel,java.util.concurrent.atomic.AtomicInteger>{
        val posts=(1L..80L).map{id->PostParser.posts("""[{"id":$id,"tags":"animated","score":3}]""").single()}
        val calls=java.util.concurrent.atomic.AtomicInteger()
        val repository=object:PostRepository{override val pageSize=1000;override suspend fun page(query:String,page:Int):PostPage{calls.incrementAndGet();return PostPage(posts,posts.size)}}
        lateinit var model:FeedViewModel
        ui.runOnUiThread{model=FeedViewModel(SavedStateHandle(),repository);val app=ViewModelProvider(ui.activity)[AppViewModel::class.java];ui.activity.setContent{Sakugapp(model,app)}}
        ui.onNodeWithTag("search-home").assertIsDisplayed();return model to calls
    }
    @Test fun onePrimaryActionCannotRestoreAChildThenPopAgain(){
        lateinit var nav:NavHostController;val events=mutableListOf<String?>()
        ui.runOnUiThread{ui.activity.setContent{
            nav=rememberNavController()
            DisposableEffect(nav){val listener=NavController.OnDestinationChangedListener{_,destination,_->events+=destination.route};nav.addOnDestinationChangedListener(listener);onDispose{nav.removeOnDestinationChangedListener(listener)}}
            NavHost(nav,startDestination="search_home"){
                composable("search_home"){Text("Search")};composable("posts"){Text("Posts")};composable("profile"){Text("Profile")};composable("post/{id}"){Text("Detail")}
            }
        }};ui.waitForIdle()
        ui.runOnUiThread{nav.navigate("posts");nav.navigate("post/1");nav.navigate("profile")};ui.waitForIdle()
        val before=events.size;ui.runOnUiThread{navigatePrimary(nav,"posts")};ui.waitForIdle()
        assertEquals(before+1,events.size);assertEquals("posts",nav.currentDestination?.route)
        assertEquals("search_home",nav.previousBackStackEntry?.destination?.route)
        ui.runOnUiThread{navigatePrimary(nav,"posts")};assertEquals(before+1,events.size)
    }
    @Test fun scrollingChromeNeverChangesTheGridViewport(){
        scene();ui.onNodeWithTag("nav-posts").performClick();ui.waitForIdle()
        val before=ui.onNodeWithTag("post-grid").fetchSemanticsNode().boundsInRoot
        ui.onNodeWithTag("post-grid").performTouchInput{swipeUp()};ui.mainClock.advanceTimeBy(700);ui.waitForIdle()
        ui.onNodeWithTag("main-bottom-nav").assertDoesNotExist()
        val hidden=ui.onNodeWithTag("post-grid").fetchSemanticsNode().boundsInRoot
        assertEquals(before.top,hidden.top,.5f);assertEquals(before.bottom,hidden.bottom,.5f)
        ui.onNodeWithTag("post-grid").performTouchInput{swipeDown()};ui.mainClock.advanceTimeBy(700);ui.waitForIdle()
        ui.onNodeWithTag("main-bottom-nav").assertIsDisplayed()
        assertEquals(before.height,ui.onNodeWithTag("post-grid").fetchSemanticsNode().boundsInRoot.height,.5f)
    }
    @Test fun draftsFeedAndScrollSurviveRepeatedPrimarySwitches(){
        val(model,calls)=scene();ui.onNodeWithTag("home-search-input").performTextInput("draft artist_tag score:>=3")
        ui.onNodeWithTag("nav-posts").performClick();ui.onNodeWithTag("post-grid").performScrollToIndex(20)
        val before=ui.onNodeWithTag("post-grid").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        repeat(5){ui.onNodeWithTag("nav-profile").performClick();ui.onNodeWithTag("profile-home").assertIsDisplayed();ui.onNodeWithTag("nav-posts").performClick()}
        val after=ui.onNodeWithTag("post-grid").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertEquals(before,after,.01f);assertEquals(80,model.state.value.posts.size);assertEquals(1,calls.get())
        ui.onNodeWithTag("nav-search_home").performClick();ui.onNodeWithTag("home-search-input").assertTextContains("draft artist_tag score:>=3")
    }
    @Test fun outgoingContentIsKeptForTheActualNavigationAnimation(){
        scene();ui.mainClock.autoAdvance=false
        try{
            val paths=listOf(Triple("search-home","posts","post-grid"),Triple("post-grid","search_home","search-home"),Triple("search-home","posts","post-grid"),Triple("post-grid","profile","profile-home"),Triple("profile-home","posts","post-grid"))
            for((old,target,new)in paths){
                ui.onNodeWithTag("nav-$target").performClick();ui.mainClock.advanceTimeBy(80)
                ui.onNodeWithTag(old).assertExists();ui.onNodeWithTag(new).assertExists()
                ui.mainClock.advanceTimeBy(300);ui.waitForIdle();ui.onNodeWithTag(old).assertDoesNotExist();ui.onNodeWithTag(new).assertIsDisplayed()
            }
        }finally{ui.mainClock.autoAdvance=true}
    }
    @Test fun rapidInterruptedTabTransitionsDoNotReuseAnActiveStateKey(){
        scene();ui.mainClock.autoAdvance=false
        try{
            repeat(6){for(route in listOf("profile","posts","search_home")){ui.onNodeWithTag("nav-$route").performClick();ui.mainClock.advanceTimeBy(32)}}
            ui.mainClock.advanceTimeBy(400);ui.waitForIdle();ui.onNodeWithTag("search-home").assertIsDisplayed()
        }finally{ui.mainClock.autoAdvance=true}
    }
    @Test fun mainSnapshotsCanBeWrittenToAnAndroidParcel(){
        val states=MainPageState();val registry=androidx.compose.runtime.saveable.SaveableStateRegistry(null){true}
        registry.registerProvider("draft"){mutableStateOf("raw artist_tag")}
        states.attach("entry","search_home",registry);states.activate("entry")
        val parcel=android.os.Parcel.obtain()
        try{parcel.writeValue(states.snapshot());parcel.setDataPosition(0)
            @Suppress("UNCHECKED_CAST","DEPRECATION") val restored=parcel.readValue(javaClass.classLoader) as HashMap<String,Map<String,List<Any?>>>
            val value=MainPageState(restored).values("search_home")!!["draft"]!!.single() as State<*>
            assertEquals("raw artist_tag",value.value)
        }finally{parcel.recycle()}
    }
    @Test fun pausedPreviewReusesItsPlayerAndRecoversAfterBackground(){
        val context=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context
        val file=File(ui.activity.cacheDir,"pause-preview.mp4").apply{writeBytes(context.assets.open("clip-gop.mp4").use{it.readBytes()})}
        var paused by mutableStateOf(false);var player:androidx.media3.common.Player?=null
        ui.runOnUiThread{ui.activity.setContent{SakugappTheme(false){VideoPanel(file.toURI().toString(),preview=true,previewPaused=paused,onPlayerReady={player=it})}}}
        try{
            ui.waitUntil(20000){var playing=false;ui.runOnUiThread{playing=player?.isPlaying==true};playing};val original=player
            ui.runOnUiThread{paused=true};ui.waitUntil(10000){var stopped=false;ui.runOnUiThread{stopped=player?.playWhenReady==false};stopped}
            assertSame(original,player);ui.runOnUiThread{paused=false};ui.waitUntil(10000){var playing=false;ui.runOnUiThread{playing=player?.isPlaying==true};playing}
            ui.activityRule.scenario.moveToState(Lifecycle.State.CREATED);ui.runOnUiThread{assertFalse(player!!.playWhenReady)}
            ui.activityRule.scenario.moveToState(Lifecycle.State.RESUMED);ui.waitUntil(20000){var playing=false;ui.runOnUiThread{playing=player?.isPlaying==true};playing};assertSame(original,player)
        }finally{file.delete()}
    }
}
