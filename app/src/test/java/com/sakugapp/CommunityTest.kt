// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import com.sakugapp.data.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*

class CommunityTest {
    @Test fun sessionIdentityMustComeFromTheServerAccountMenu(){
        assertEquals(9L,CommunityParser.homeUserId("""<div id="main-menu"><li class="user"><ul class="submenu"><a href="/user/show/9">Profile</a></ul></li></div>"""))
        assertNull(CommunityParser.homeUserId("""<div id="main-menu"><li class="user"><a href="/user/login">Login</a></li></div>"""))
        assertThrows(DataFailure::class.java){CommunityParser.homeUserId("<html>challenge</html>")}
    }
    @Test fun currentVoteCannotOverflowIntoAFalseZero(){assertThrows(DataFailure::class.java){CommunityParser.currentVote("""{"success":true,"vote":4294967296}""")}}
    @Test fun unknownUserWithSuccessFlagIsNotSuccessfulLogin(){
        assertThrows(DataFailure::class.java){CommunityParser.login("""{"response":"unknown-user","exists":false,"success":true}""")}
    }
    @Test fun wrongPasswordWithPublicProfileDoesNotAuthenticate(){
        assertThrows(DataFailure::class.java){CommunityParser.login("""{"response":"wrong-password","exists":true,"id":8,"name":"synthetic","success":true}""")}
    }
    @Test fun loginRequiresServerConfirmedIdentity(){
        assertEquals(Account(8,"synthetic"),CommunityParser.login("""{"response":"success","success":true,"id":8,"name":"synthetic","pass_hash":"ignored-test-secret"}"""))
        assertThrows(DataFailure::class.java){CommunityParser.login("""{"response":"success","success":true,"name":"synthetic"}""")}
    }
    @Test fun votingUsesServerScoreAndRejectsWrongPost(){
        assertEquals(VoteResult(9,44),CommunityParser.vote("""{"success":true,"posts":[{"id":9,"score":44}]}""",9))
        assertThrows(DataFailure::class.java){CommunityParser.vote("""{"success":true,"posts":[{"id":8,"score":44}]}""",9)}
        assertThrows(DataFailure::class.java){CommunityParser.vote("""{"success":false,"posts":[{"id":9,"score":44}]}""",9)}
    }
    @Test fun missingServerScoreRemainsUnknown(){assertNull(CommunityParser.vote("""{"success":true,"posts":[{"id":9}]}""",9).score)}
    @Test fun csrfIsParsedWithoutExecutingPageScripts(){
        assertEquals("test-token",CommunityParser.csrf("""<html><head><meta name="csrf-token" content="test-token"></head><script>throw new Error()</script></html>"""))
        assertThrows(DataFailure::class.java){CommunityParser.csrf("<html>Access denied</html>")}
    }
    @Test fun realSanitizedCommentsAndTimecodesParse(){
        val comments=CommunityParser.comments(javaClass.getResource("/live/community-comments.json")!!.readText())
        assertEquals(3,comments.size);assertTrue(comments.all{it.postId==317910L})
        assertTrue(CommunityParser.timecodes(comments.first().body).any{it.first=="0:03.8" && it.second==3800L})
        assertNull(comments.first().author)
    }
    @Test fun invalidTimecodesAreNotTurnedIntoSeeks(){
        assertEquals(listOf("1:02.34" to 62340L),CommunityParser.timecodes("1:02.34 bad 3:99.2 01:12:30"))
    }
    @Test fun malformedCommentsAndFalseSuccessAreRejected(){
        assertThrows(DataFailure::class.java){CommunityParser.comments("<html>login</html>")}
        assertThrows(DataFailure::class.java){CommunityParser.comments("""[{"id":1,"post_id":9}]""")}
        assertThrows(DataFailure::class.java){CommunityParser.success("""{"success":false,"reason":"rate limited"}""")}
    }
    @Test fun loginAndVoteSendCsrfAndCredentialsOnlyInPostBody()=runBlocking{
        val server=MockWebServer();server.start()
        try{
            val repo=CommunityRepository(base=server.url("/"))
            server.enqueue(MockResponse().setBody("""<meta name="csrf-token" content="csrf-test">"""))
            server.enqueue(MockResponse().setBody("""{"success":true,"response":"success","id":9,"name":"synthetic"}"""))
            assertEquals(Account(9,"synthetic"),repo.login("synthetic","test-password"))
            val preflight=server.takeRequest();assertEquals("GET",preflight.method)
            val login=server.takeRequest();assertEquals("POST",login.method);assertFalse(login.path!!.contains("password"));assertTrue(login.body.readUtf8().contains("password=test-password"));assertEquals("csrf-test",login.getHeader("X-CSRF-Token"))
            server.enqueue(MockResponse().setBody("""<meta name="csrf-token" content="vote-csrf">"""))
            server.enqueue(MockResponse().setBody("""{"success":true,"posts":[{"id":9,"score":17}]}"""))
            assertEquals(17,repo.vote(9,3).score);server.takeRequest()
            val vote=server.takeRequest();assertEquals("id=9&score=3",vote.body.readUtf8());assertEquals("vote-csrf",vote.getHeader("X-CSRF-Token"))
        }finally{server.shutdown()}
    }
    @Test fun currentVoteUsesPostWithoutScoreToAvoidMutating()=runBlocking{
        val server=MockWebServer();server.start()
        try{
            val repo=CommunityRepository(base=server.url("/"))
            server.enqueue(MockResponse().setBody("""<meta name="csrf-token" content="test">"""));server.enqueue(MockResponse().setBody("""{"success":true,"vote":3}"""))
            assertEquals(3,repo.currentVote(9));server.takeRequest();val request=server.takeRequest()
            assertEquals("POST",request.method);assertEquals("id=9",request.body.readUtf8())
        }finally{server.shutdown()}
    }
    @Test fun commentCreateEditDeleteAreExplicitAndAuthenticatedByServer()=runBlocking{
        val server=MockWebServer();server.start()
        try{
            val repo=CommunityRepository(base=server.url("/"))
            for(i in 1..3){server.enqueue(MockResponse().setBody("""<meta name="csrf-token" content="test">"""));server.enqueue(MockResponse().setBody("""{"success":true}"""))}
            repo.comment(9,"credit & source")
            server.takeRequest();val create=server.takeRequest();assertTrue(create.path!!.startsWith("/comment/create.json"));assertTrue(create.body.readUtf8().contains("comment%5Bbody%5D=credit%20%26%20source"))
            repo.comment(9,"edited",5);server.takeRequest();val edit=server.takeRequest();assertTrue(edit.path!!.startsWith("/comment/update.json"));assertTrue(edit.body.readUtf8().contains("id=5"))
            repo.deleteComment(9,5);server.takeRequest();val delete=server.takeRequest();assertTrue(delete.path!!.startsWith("/comment/destroy.json"));assertEquals("id=5",delete.body.readUtf8())
        }finally{server.shutdown()}
    }
    @Test fun commentsCannotBeAttachedToDifferentPost()=runBlocking{
        val server=MockWebServer();server.start()
        try{server.enqueue(MockResponse().setBody("""[{"id":1,"post_id":8,"body":"test"}]"""));
            try{CommunityRepository(base=server.url("/")).comments(9,1);fail("must reject mismatched post")}catch(e:DataFailure){assertTrue(e.message!!.contains("different post"))}
        }finally{server.shutdown()}
    }
    @Test fun forbiddenAndCsrfFailuresDoNotRetryOrClaimSuccess()=runBlocking{
        val server=MockWebServer();server.start()
        try{
            val repo=CommunityRepository(base=server.url("/"));server.enqueue(MockResponse().setBody("<html>blocked</html>"))
            try{repo.vote(9,1);fail("must fail")}catch(e:DataFailure){assertTrue(e.message!!.contains("CSRF"))};assertEquals(1,server.requestCount)
        }finally{server.shutdown()}
    }
}
