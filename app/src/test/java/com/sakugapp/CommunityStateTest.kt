// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import com.sakugapp.data.*
import com.sakugapp.ui.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class CommunityStateTest {
    private lateinit var server:MockWebServer
    private lateinit var scope:CoroutineScope
    @Before fun setup(){Dispatchers.setMain(Dispatchers.Unconfined);server=MockWebServer().apply{start()};scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)}
    @After fun close(){scope.cancel();server.shutdown();Dispatchers.resetMain()}
    private suspend fun until(check:()->Boolean){withTimeout(5000){while(!check())delay(10)}}
    private fun token(){server.enqueue(MockResponse().setBody("<meta name=\"csrf-token\" content=\"test\">"))}
    @Test fun collapsedThreadDoesNotFetchAndReopeningKeepsDraftAndLoadedPage()=runBlocking{
        val thread=CommentThread(9,CommunityRepository(base=server.url("/")),scope)
        delay(50);assertEquals(0,server.requestCount)
        server.enqueue(MockResponse().setBody("""[{"id":2,"post_id":9,"body":"0:03.8 credit"}]"""))
        thread.expand();thread.expand();until{thread.loaded};assertEquals(1,server.requestCount)
        thread.draft("unsent draft");thread.expand();delay(50)
        assertEquals(1,server.requestCount);assertEquals("unsent draft",thread.draft);assertEquals(2L,thread.comments.single().id)
        val other=CommentThread(10,CommunityRepository(base=server.url("/")),scope)
        assertFalse(other.loaded);assertEquals("",other.draft);assertEquals(1,server.requestCount)
    }
    @Test fun failedCommentsStayRetryableWithoutRequestsOnEveryReopen()=runBlocking{
        val thread=CommentThread(9,CommunityRepository(base=server.url("/")),scope)
        server.enqueue(MockResponse().setResponseCode(503));thread.expand();until{thread.error!=null};thread.expand();delay(30);assertEquals(1,server.requestCount)
        server.enqueue(MockResponse().setBody("[]"));thread.retry();until{thread.loaded};assertTrue(thread.end);assertNull(thread.error)
    }
    @Test fun voteChoicesKeepServerMeaningAndOnlyConfirmedScoresUpdate()=runBlocking{
        val vote=PostVote(9,CommunityRepository(base=server.url("/")),scope);val scores=mutableListOf<Int?>()
        vote.choose(3,scores::add);delay(20);assertEquals(0,server.requestCount)
        token();server.enqueue(MockResponse().setBody("""{"success":true,"vote":0}"""));vote.identify(Account(7,"test"));until{vote.value==0}
        repeat(2){server.takeRequest()}
        for(value in listOf(1,2,3,0)){
            token();server.enqueue(MockResponse().setBody("""{"success":true,"posts":[{"id":9,"score":${30+value}}]}"""));token();server.enqueue(MockResponse().setBody("""{"success":true,"vote":$value}"""))
            vote.choose(value,scores::add);until{!vote.busy && vote.value==value};until{server.requestCount>=2+(scores.size*4)}
            server.takeRequest();assertEquals("id=9&score=$value",server.takeRequest().body.readUtf8());server.takeRequest();assertEquals("id=9",server.takeRequest().body.readUtf8())
        }
        assertEquals(listOf(31,32,33,30),scores)
        token();server.enqueue(MockResponse().setResponseCode(503));vote.choose(3,scores::add);until{!vote.busy};assertNotNull(vote.error);assertEquals(0,vote.value);assertEquals(4,scores.size)
        val requests=server.requestCount;vote.clearError();assertNull(vote.error);assertEquals(requests,server.requestCount)
        vote.identify(null);assertNull(vote.value)
    }
}
