// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.sakugapp.data.*
import com.sakugapp.ui.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/** Fixtures isolate name presentation and committed system-back state from website availability. */
class PreviewToolsBackDeviceTest {
    @get:Rule val ui=createAndroidComposeRule<PerformanceHostActivity>()
    private val artist=Tag("yutaka_nakamura",TagCategory.ARTIST)
    private val work=Tag("mob_psycho_100",TagCategory.COPYRIGHT)
    private fun fixture()=PostParser.posts(InstrumentationRegistry.getInstrumentation().context.assets
        .open("library-real-posts.json").bufferedReader().use{it.readText()}).first()
    private fun back(){ui.runOnUiThread{ui.activity.onBackPressedDispatcher.onBackPressed()};ui.mainClock.advanceTimeBy(500);ui.waitForIdle()}
    private fun position()=ui.onNodeWithTag("post-grid").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()

    @Test fun previewHasOneCopyrightNameAndRawArtistWhileDetailsKeepBoth(){
        var enabled by mutableStateOf(true);var details by mutableStateOf(false);var light by mutableStateOf(false)
        var searched=""
        val names=mapOf(work.name to "灵能百分百",artist.name to "中村丰")
        val post=fixture().copy(tags=listOf(work,artist),previewUrl=null,fileUrl=null,fileExt=null)
        ui.runOnUiThread{ui.activity.setContent{SakugappTheme(light){Surface(Modifier.fillMaxSize()){
            Box(Modifier.systemBarsPadding().padding(12.dp).widthIn(max=360.dp)){
                if(details)DetailTags(post.tags,light,{searched=it},{},names)
                else PostCard(post,AppSettings(chineseDetailTranslations=enabled),false,false,{}, {searched=it},{},{},previewNames=names)
            }
        }}}}
        for(theme in listOf(false,true)){
            ui.runOnUiThread{light=theme};ui.waitForIdle()
            ui.onNodeWithTag("tag-${work.name}",useUnmergedTree=true).assertTextEquals("灵能百分百").performClick()
            assertEquals(work.name,searched)
            ui.onNodeWithTag("tag-${artist.name}",useUnmergedTree=true).assertTextEquals(artist.name)
            ui.onNodeWithText("中村丰").assertDoesNotExist()
            UiEvidence.capture("preview-tools-names-${if(theme)"light"else"dark"}.png")
        }
        ui.runOnUiThread{enabled=false};ui.waitForIdle()
        ui.onNodeWithTag("tag-${work.name}",useUnmergedTree=true).assertTextEquals(work.name)
        ui.runOnUiThread{details=true};ui.waitForIdle()
        for((tag,name)in names){
            ui.onNodeWithTag("translated-tag-$tag",useUnmergedTree=true).assertTextEquals(name)
            ui.onNodeWithTag("tag-$tag",useUnmergedTree=true).assertTextEquals(tag)
        }
        UiEvidence.capture("preview-tools-unchanged-details.png")
    }

    @Test fun frameTilesStayInOneRowAndReflectTheSelectedDirection(){
        val deltas=mutableListOf<Int>();var width by mutableStateOf(320.dp)
        ui.runOnUiThread{ui.activity.setContent{SakugappTheme(false){Surface(Modifier.fillMaxSize()){
            Box(Modifier.systemBarsPadding().width(width)){
                FrameControlBar(null,0,0,true,{deltas+=it},active=true,actions={Row{
                    ActionIcon(R.drawable.ic_unlock,"Lock",{},touchSize=36.dp)
                    ActionIcon(R.drawable.ic_fullscreen,"Fullscreen",{},touchSize=36.dp)
                    ActionIcon(R.drawable.ic_more,"More",{},touchSize=36.dp)
                }})
            }
        }}}}
        for(size in listOf(320.dp,600.dp)){
            ui.runOnUiThread{width=size};ui.waitForIdle()
            val bar=ui.onNodeWithTag("frame-toolbar").fetchSemanticsNode().boundsInRoot
            val buttons=listOf(-3,-2,-1,1,2,3).map{ui.onNodeWithTag("step-$it")}
            val bounds=buttons.map{it.fetchSemanticsNode().boundsInRoot}
            bounds.zipWithNext().forEach{(a,b)->assertEquals(a.top,b.top,1f);assertTrue(a.right<=b.left+1)}
            assertTrue(bounds.all{it.left>=bar.left && it.right<=bar.right+1})
            for(delta in listOf(-3,-2,-1,1,2,3)){
                ui.onNodeWithTag("step-$delta").performClick().assertIsSelected()
                assertEquals(delta,deltas.last())
            }
        }
        ui.onNodeWithTag("frame-tool-title",useUnmergedTree=true).assertExists()
        UiEvidence.capture("preview-tools-frame-tiles.png")
    }

    @Test fun detailBackKeepsResultsThenResultBackRestoresDefaultCacheScrollAndBlankEditor(){
        val base=fixture().copy(previewUrl=null,fileUrl=null,fileExt=null,tags=emptyList())
        val calls=AtomicInteger()
        val ordinary=(1L..45L).map{base.copy(id=it)};val results=ordinary.map{it.copy(id=it.id+1000)}
        val repository=object:PostRepository{override val pageSize=1000
            override suspend fun page(query:String,page:Int):PostPage{
                calls.incrementAndGet();val values=if(query.isBlank())ordinary else results
                return PostPage(values,values.size)
            }
        }
        lateinit var model:FeedViewModel
        ui.runOnUiThread{
            model=FeedViewModel(SavedStateHandle(),repository)
            val app=ViewModelProvider(ui.activity)[AppViewModel::class.java]
            ui.activity.setContent{Sakugapp(model,app)}
        }
        ui.onNodeWithTag("nav-posts").performClick()
        ui.waitUntil(10000){!model.state.value.loading && model.state.value.posts.isNotEmpty()}
        ui.onNodeWithTag("post-grid").performScrollToIndex(20);val before=position()
        ui.onNodeWithTag("search-entry").performClick()
        ui.onNodeWithTag("search-input").performTextReplacement("mob_psycho_100 score:>=3")
        ui.onNodeWithTag("submit-search").performClick()
        ui.waitUntil(10000){model.state.value.query.isNotBlank() && !model.state.value.loading}
        ui.onNodeWithTag("post-grid").performScrollToIndex(12);val resultPosition=position()
        ui.onNodeWithTag("post-media-1013").performClick()
        ui.onNodeWithTag("detail-actions").assertExists();back()
        assertEquals("mob_psycho_100 score:>=3",model.state.value.query)
        assertEquals(resultPosition,position(),2f)
        val requests=calls.get();back()
        assertEquals("",model.state.value.query);assertFalse(model.searchMode.value)
        assertEquals(ordinary,model.state.value.posts);assertEquals(requests,calls.get())
        assertEquals(before,position(),2f)
        ui.onNodeWithTag("search-entry").performClick()
        assertEquals("",ui.onNodeWithTag("search-input").fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        assertFalse(ui.activity.isFinishing)
        UiEvidence.capture("preview-tools-default-back.png")
    }
}
