// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.google.gson.JsonParser
import com.sakugapp.data.*
import com.sakugapp.ui.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RefreshGridDeviceTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private fun pages():List<List<Post>> = InstrumentationRegistry.getInstrumentation().context.assets.open("posts-five-pages.json").bufferedReader().use{reader->JsonParser.parseString(reader.readText()).asJsonArray.map{PostParser.posts(it.toString())}}
    @Test fun pullAtTopRefreshesWhileMidListScrollDoesNotAndFailureKeepsTheRealSnapshot(){
        val pages=pages();val initial=pages.first();var pending=CompletableDeferred<PostPage>();var first=true;var refreshes=0
        val repository=object:PostRepository{override suspend fun page(query:String,page:Int):PostPage{
            assertEquals("animated order:score",query)
            if(page!=1)return PostPage(emptyList(),0)
            if(first){first=false;return PostPage(initial,initial.size)}
            return pending.await()
        }}
        lateinit var model:FeedViewModel;lateinit var grid:LazyGridState;lateinit var app:AppViewModel
        ui.runOnUiThread{
            app=ViewModelProvider(ui.activity)[AppViewModel::class.java];model=FeedViewModel(SavedStateHandle(mapOf("query" to "animated order:score")),repository)
            ui.activity.setContent{SakugappTheme(false){Surface(Modifier.fillMaxSize().systemBarsPadding()){
                val state by model.state.collectAsStateWithLifecycle();grid=rememberLazyGridState()
                CompositionLocalProvider(LocalTagActions provides app){PostsScreen(state,grid,AppSettings(),emptyList(),false,app,model::search,{},{model.loadMore()},{refreshes++;model.refresh()})}
            }}}
        }
        ui.waitUntil(15000){model.state.value.posts.size==initial.size}
        ui.onNodeWithTag("post-grid").performScrollToIndex(20)
        ui.onNodeWithTag("post-grid").performTouchInput{swipeDown(startY=height*0.4f,endY=height*0.75f,durationMillis=400)}
        ui.waitForIdle();assertTrue(grid.canScrollBackward);assertEquals(0,refreshes)
        ui.onNodeWithTag("post-grid").performScrollToIndex(0)
        ui.onNodeWithTag("post-grid").performTouchInput{swipeDown(startY=height*0.3f,endY=height*0.9f,durationMillis=500)}
        ui.waitUntil(10000){model.state.value.refreshing};assertEquals(1,refreshes)
        ui.onNodeWithTag("refresh-indicator").assertIsDisplayed();UiEvidence.capture("posts-refreshing.png")
        pending.completeExceptionally(DataFailure("HTTP 503"))
        ui.waitUntil(10000){model.state.value.refreshError!=null};assertEquals(initial.map{it.id},model.state.value.posts.map{it.id})
        ui.onNodeWithTag("retry-refresh").assertIsDisplayed();UiEvidence.capture("posts-refresh-failed.png")
        pending=CompletableDeferred();ui.onNodeWithTag("retry-refresh").performClick();ui.waitUntil(10000){model.state.value.refreshing}
        val replacement=pages[1].take(2);pending.complete(PostPage(listOf(replacement[0],replacement[0],replacement[1]),3))
        ui.waitUntil(10000){!model.state.value.refreshing && model.state.value.posts.size==2}
        assertEquals(replacement.map{it.id},model.state.value.posts.map{it.id});assertEquals(2,model.state.value.nextPage)
        assertEquals("animated order:score",model.state.value.query);assertEquals(0,grid.firstVisibleItemIndex)
    }
}
