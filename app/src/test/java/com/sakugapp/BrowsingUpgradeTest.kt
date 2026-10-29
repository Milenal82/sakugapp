// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import androidx.lifecycle.SavedStateHandle
import com.google.gson.JsonParser
import com.sakugapp.data.*
import com.sakugapp.ui.*
import androidx.compose.ui.graphics.luminance
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class BrowsingUpgradeTest {
    @Test fun completionReplacesTheCaretTokenAndPreservesAllOtherSyntax(){
        val original="~gun -effects score:>=3 order:score"
        val result=SearchEditing.complete(original,4,"gundam")
        assertEquals("~gundam -effects score:>=3 order:score",result.text)
        assertEquals(8,result.cursor)
        val middle="first -gund last rating:safe"
        assertEquals("first -gundam last rating:safe",SearchEditing.complete(middle,10,"gundam").text)
        assertEquals("mokmok ",SearchEditing.complete("mok",3,"mokmok").text)
        assertNull(SearchEditing.prefix("source:https://site.com",22))
        assertNull(SearchEditing.prefix("source:https://site.com",2))
        assertEquals("source:https://site.com",SearchEditing.complete("source:https://site.com",2,"sakura").text)
        assertNull(SearchEditing.prefix("tag*",4))
        assertEquals("gu",SearchEditing.prefix("a ~gund b",5))
    }
    @Test fun smallScrollsDoNotFlickerAndDirectionChangesResetAccumulation(){
        val chrome=ScrollChrome(64f,28f)
        assertTrue(chrome.consume(-20f,true));assertTrue(chrome.consume(-20f,true))
        assertFalse(chrome.consume(-25f,true))
        assertFalse(chrome.consume(10f,false));assertFalse(chrome.consume(10f,false))
        assertTrue(chrome.consume(9f,false))
        chrome.reset();assertTrue(chrome.consume(-60f,true));assertTrue(chrome.consume(2f,true));assertTrue(chrome.consume(-10f,true))
    }
    @Test fun defaultSystemLanguageIsSimplifiedChineseOrEnglishWhileManualLocalesRemain(){
        for(tag in listOf("zh-CN","zh-SG","zh-Hans-CN"))assertEquals("zh-CN",PreferenceRules.resolveLanguage("system",tag))
        for((tag,expected)in mapOf("ja-JP" to "ja","en-US" to "en","fr-FR" to "fr","es-ES" to "es","ru-RU" to "ru","ko-KR" to "ko","zh-TW" to "zh-TW","zh-Hant-HK" to "zh-TW","de-DE" to "en"))assertEquals(expected,PreferenceRules.resolveLanguage("system",tag))
        assertEquals("ja",PreferenceRules.resolveLanguage("ja","zh-CN"))
        assertEquals("zh-TW",PreferenceRules.resolveLanguage("zh-TW","en-US"))
        assertEquals("en",PreferenceRules.siteLanguage("zh-CN"))
        assertEquals("zh-TW",PreferenceRules.platformLanguage("zh-Hant-TW"))
        assertEquals("system",PreferenceRules.platformLanguage(""))
    }
    @Test fun oldHistoryIsPreservedDeduplicatedAndExtendedToFiftyWithoutSavingBlankSearches(){
        val legacy=(1..30).map{"tag$it"}
        assertEquals(legacy,PreferenceRules.history(legacy,""))
        var list=legacy
        for(i in 31..70)list=PreferenceRules.history(list,"tag$i")
        assertEquals(50,list.size);assertEquals("tag70",list.first());assertEquals("tag10",list.last())
        assertEquals(50,PreferenceRules.history(list," tag50 ").size)
        assertEquals("tag50",PreferenceRules.history(list," tag50 ").first())
    }
    @Test fun actualFiveCapturedPagesAppendInWebsiteOrderAndStopAfterAShortPage()=runTest {
        val dispatcher=StandardTestDispatcher(testScheduler);Dispatchers.setMain(dispatcher)
        try {
            val pages=JsonParser.parseString(javaClass.getResource("/live/posts-five-pages.json")!!.readText()).asJsonArray
            val requests=mutableListOf<Int>()
            val repository=object:PostRepository{
                override suspend fun page(query:String,page:Int):PostPage{
                    requests+=page
                    val posts=if(page<=5)PostParser.posts(pages[page-1].toString()) else emptyList()
                    return PostPage(posts,posts.size)
                }
            }
            val model=FeedViewModel(SavedStateHandle(),repository);advanceUntilIdle()
            repeat(4){model.loadMore();advanceUntilIdle()}
            assertFalse(model.state.value.autoLoadPaused)
            assertEquals(300,model.state.value.posts.size)
            assertEquals(300,model.state.value.posts.distinctBy{it.id}.size)
            assertEquals(listOf(1,2,3,4,5),requests)
            assertEquals(pages.flatMap{PostParser.posts(it.toString())}.map{it.id},model.state.value.posts.map{it.id})
            model.loadMore();advanceUntilIdle();assertTrue(model.state.value.endReached)
            model.loadMore();advanceUntilIdle();assertEquals(6,requests.size)
        }finally{Dispatchers.resetMain()}
    }
    @Test fun duplicateOnlyPagePausesAutomaticLoadingAndNeverCreatesFakeIds(){
        val posts=PostParser.posts(javaClass.getResource("/live/posts.json")!!.readText())
        val state=FeedState(posts=posts).append(PostPage(posts,60),60)
        assertTrue(state.autoLoadPaused);assertFalse(state.endReached)
        assertEquals(posts.map{it.id},state.posts.map{it.id})
        val last=state.append(PostPage(emptyList(),0),60)
        assertTrue(last.endReached)
    }
    @Test fun scoreRedHasReadableContrastInBothThemes(){
        for(light in listOf(false,true)){
            val a=scoreColor(light).luminance();val b=appColors(light).surface.luminance()
            assertTrue((maxOf(a,b)+0.05f)/(minOf(a,b)+0.05f)>=4.5f)
        }
    }
}

class TagSuggestionsTest {
    private fun fixture()=javaClass.getResource("/live/tag-suggestions.json")!!.readText()
    @Test fun realTagResponseSuppliesClassificationAndCountWithoutNameGuessing(){
        val tags=SuggestionParser.parse(fixture())
        assertEquals(8,tags.size)
        assertEquals("a_boy_with_gundam",tags.first().name)
        assertEquals(TagCategory.COPYRIGHT,tags.first().category)
        assertEquals(6L,tags.first().count)
        val unknown=SuggestionParser.parse("""[{"name":"artist_unknown","type":99}]""").single()
        assertEquals(TagCategory.UNKNOWN,unknown.category);assertNull(unknown.count)
        assertThrows(DataFailure::class.java){SuggestionParser.parse("<html>blocked</html>")}
    }
    @Test fun cacheAvoidsRepeatedRequestsExpiresAndNeverAddsSessionCredentials()=runBlocking {
        MockWebServer().use{server->server.start()
            var now=1_000L
            val repo=TagSuggestionRepository(server.url("/"),clock={now},permit={})
            server.enqueue(MockResponse().setBody(fixture()))
            val result=repo.suggestions("gund")
            assertEquals(result,repo.suggestions("gund"));assertEquals(1,server.requestCount)
            val request=server.takeRequest();assertEquals("gund",request.requestUrl!!.queryParameter("name"));assertEquals("8",request.requestUrl!!.queryParameter("limit"))
            assertNull(request.getHeader("Cookie"));assertNull(request.getHeader("Authorization"))
            now+=120_001;server.enqueue(MockResponse().setBody(fixture()))
            assertEquals(result,repo.suggestions("gund"));assertEquals(2,server.requestCount)
        }
    }
    @Test fun accessDenialFailsOnceAndDoesNotCacheTheFailureOrBlockManualSearch()=runBlocking {
        MockWebServer().use{server->server.start();val repo=TagSuggestionRepository(server.url("/"),permit={})
            server.enqueue(MockResponse().setResponseCode(403))
            try{repo.suggestions("gund");fail()}catch(e:DataFailure){assertTrue(e.message!!.contains("403"))}
            assertEquals(1,server.requestCount)
            server.enqueue(MockResponse().setBody(fixture()));assertEquals(8,repo.suggestions("gund").size)
            assertTrue(repo.suggestions("id:100").isEmpty());assertEquals(2,server.requestCount)
        }
    }
}
