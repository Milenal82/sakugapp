// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import com.google.gson.JsonParser
import com.sakugapp.data.*
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class SakugabotTranslationsTest {
    private val artist=Tag("yutaka_nakamura",TagCategory.ARTIST)
    private val work=Tag("mob_psycho_100",TagCategory.COPYRIGHT)
    private fun fixture(name:String)=javaClass.getResource("/sakugabot/$name.json")!!.readText()
    private fun withoutChinese(name:String)=JsonParser.parseString(fixture(name)).asJsonObject.apply{
        add("detail",getAsJsonArray("detail").filter{it.asJsonObject["attribute"].asJsonObject["code"].asString!="name_zh"}
            .let{items->com.google.gson.JsonArray().apply{items.forEach{add(it)}}})
    }.toString()
    private fun body(tag:String)=fixture("yutaka_nakamura").replace("yutaka_nakamura",tag)
    private fun response(body:String)=MockResponse().setHeader("Content-Type","application/json").setBody(body)

    @Test fun actualChineseAttributeWinsOverTheJapaneseMainName(){
        assertEquals("中村丰",SakugabotNameParser.parse(fixture(artist.name),artist)!!.chinese)
        assertEquals("灵能百分百",SakugabotNameParser.parse(fixture(work.name),work)!!.chinese)
    }
    @Test fun mainNameNeedsIndependentChineseEvidence(){
        assertNull(SakugabotNameParser.parse(withoutChinese(artist.name),artist))
        val yoh=Tag("yoh_yoshinari",TagCategory.ARTIST)
        assertEquals("吉成曜",SakugabotNameParser.parse(withoutChinese(yoh.name),yoh)!!.chinese)
        assertNull(SakugabotNameParser.parse("""{"name":"yutaka_nakamura","type":1,"main_name":"English Name","detail":[]}""",artist))
    }
    @Test fun missingMalformedWrongTagAndUnverifiedCategoriesFallBack(){
        assertNull(SakugabotNameParser.parse(fixture("mokmok"),Tag("mokmok",TagCategory.ARTIST)))
        for(body in listOf("{}","[]","not json",fixture(work.name),fixture(artist.name).replace("\"type\": 1","\"type\": 3")))
            assertNull(SakugabotNameParser.parse(body,artist))
        assertNull(SakugabotNameParser.parse(fixture(artist.name),artist.copy(category=TagCategory.UNKNOWN)))
    }
    @Test fun sourceOnlyUsesExactUnambiguousCopyrightNames(){
        val name=SakugabotNameParser.parse(fixture(work.name),work)!!
        assertEquals("灵能百分百",translatedSource("Mob Psycho 100",listOf(name)))
        assertEquals("灵能百分百",translatedSource(work.name,listOf(name)))
        for(source in listOf(null,"","Mob Psycho 100 #12","https://example.com/mob_psycho_100","Episode 12","Yutaka Nakamura"))
            assertNull(translatedSource(source,listOf(name,SakugabotNameParser.parse(fixture(artist.name),artist)!!)))
        assertNull(translatedSource(work.name,listOf(name,name.copy(chinese="conflicting"))))
    }
    @Test fun concurrentLookupsShareCacheAndOnlyUseOnePublicEncodedGet()=runBlocking{
        MockWebServer().use{server->
            val raw="artist/part?x=1&#";val tag=artist.copy(name=raw)
            server.enqueue(response(body(raw)).setBodyDelay(80,TimeUnit.MILLISECONDS))
            val repository=SakugabotTranslations(server.url("/api/").newBuilder().host("127.0.0.1").build(),intervalMs=0)
            val values=coroutineScope{List(8){async{repository.lookup(tag)}}.awaitAll()}
            assertTrue(values.all{it?.chinese=="中村丰"});assertEquals(1,server.requestCount)
            val request=server.takeRequest();assertEquals("GET",request.method)
            assertNull(request.getHeader("Cookie"));assertNull(request.getHeader("Authorization"))
            assertEquals(listOf("api","tags",raw,""),request.requestUrl!!.pathSegments)
            assertNull(request.requestUrl!!.query);assertTrue(request.path!!.contains("%2F"))
        }
    }
    @Test fun cacheIsBoundedAndExpiresAndUnknownTagsNeverRequest()=runBlocking{
        MockWebServer().use{server->
            var now=0L
            val repo=SakugabotTranslations(server.url("/api/").newBuilder().host("127.0.0.1").build(),clock={now},intervalMs=0,capacity=2)
            for(name in listOf("a","b","c","b","a"))server.enqueue(response(body(name)))
            for(name in listOf("a","b","a","c","a","b"))assertNotNull(repo.lookup(artist.copy(name=name)))
            // a was recently used; b was evicted and has to be requested again.
            assertEquals(4,server.requestCount)
            now=24*60*60_000L+1
            assertNotNull(repo.lookup(artist.copy(name="a")));assertEquals(5,server.requestCount)
            assertNull(repo.lookup(artist.copy(category=TagCategory.UNKNOWN)));assertEquals(5,server.requestCount)
        }
    }
    @Test fun cacheOnlyNamesExpireAndPublishedNamesExcludeFailuresAndEvictions():Unit=runBlocking{
        MockWebServer().use{server->
            var now=0L
            val repo=SakugabotTranslations(server.url("/api/").newBuilder().host("127.0.0.1").build(),clock={now},intervalMs=0,capacity=1)
            assertNull(repo.cached(artist));assertTrue(repo.cachedNames.value.isEmpty());assertEquals(0,server.requestCount)
            server.enqueue(response(fixture(artist.name)));assertNotNull(repo.lookup(artist))
            assertEquals("中村丰",repo.cached(artist)?.chinese);assertEquals(setOf(artist),repo.cachedNames.value.keys)
            assertEquals(1,server.requestCount)
            now=24*60*60_000L+1
            assertNull(repo.cached(artist));assertEquals(1,server.requestCount)
            server.enqueue(response(fixture(work.name)));repo.lookup(work)
            assertEquals(setOf(work),repo.cachedNames.value.keys);assertNull(repo.cached(artist))
            server.enqueue(MockResponse().setResponseCode(404));repo.lookup(artist)
            assertTrue(repo.cachedNames.value.isEmpty());assertEquals(3,server.requestCount)
        }
    }
    @Test fun cachedDownloadNamesNeverWaitForAnUnrelatedNetworkLookup():Unit=runBlocking{
        MockWebServer().use{server->
            val repo=SakugabotTranslations(server.url("/api/").newBuilder().host("127.0.0.1").build(),intervalMs=0)
            server.enqueue(response(fixture(artist.name)));repo.lookup(artist);server.takeRequest()
            server.enqueue(response(fixture(work.name)).setBodyDelay(2,TimeUnit.SECONDS))
            val pending=launch{repo.lookup(work)}
            withContext(Dispatchers.IO){assertNotNull(server.takeRequest(2,TimeUnit.SECONDS))}
            val cached=withTimeout(500){withContext(Dispatchers.Default){TranslationLabels.download(listOf(artist,work),true,repo)}}
            assertEquals(mapOf(artist to "中村丰"),cached);assertFalse(pending.isCompleted)
            pending.cancelAndJoin()
        }
    }
    @Test fun missingAndBadResponsesAreCachedWithoutAffectingBrowsing()=runBlocking{
        MockWebServer().use{server->
            var now=0L;val repo=SakugabotTranslations(server.url("/api/").newBuilder().host("127.0.0.1").build(),clock={now},intervalMs=0)
            server.enqueue(MockResponse().setResponseCode(404))
            repeat(3){assertNull(repo.lookup(artist))};assertEquals(1,server.requestCount)
            now=3_600_001;server.enqueue(response("unexpected JSON"))
            repeat(3){assertNull(repo.lookup(artist))};assertEquals(2,server.requestCount)
        }
    }
    @Test fun rateLimitsBackOffAcrossTagsAndDoNotRetryAutomatically()=runBlocking{
        MockWebServer().use{server->
            var now=0L;val repo=SakugabotTranslations(server.url("/api/").newBuilder().host("127.0.0.1").build(),clock={now},intervalMs=0)
            server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After","120"))
            assertNull(repo.lookup(artist));now=90_000;assertNull(repo.lookup(work));assertEquals(1,server.requestCount)
            now=120_001;server.enqueue(response(fixture(work.name)));assertNotNull(repo.lookup(work));assertEquals(2,server.requestCount)
        }
    }
    @Test fun networkFailureSilentlyFallsBackAndCancellationDoesNotPoisonTheCache()=runBlocking{
        MockWebServer().use{server->
            val client=OkHttpClient.Builder().callTimeout(250,TimeUnit.MILLISECONDS).retryOnConnectionFailure(false).build()
            val repo=SakugabotTranslations(server.url("/api/").newBuilder().host("127.0.0.1").build(),client,intervalMs=0)
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            assertNull(repo.lookup(artist));assertNull(repo.lookup(work));assertEquals(1,server.requestCount)
        }
        MockWebServer().use{server->
            val failures=java.util.Collections.synchronizedList(mutableListOf<String>())
            val cancelled=java.util.concurrent.CountDownLatch(1)
            val client=OkHttpClient.Builder().retryOnConnectionFailure(false).eventListener(object:okhttp3.EventListener(){
                override fun callFailed(call:okhttp3.Call,ioe:java.io.IOException){failures+=ioe.toString();cancelled.countDown()}
            }).build()
            val repo=SakugabotTranslations(server.url("/api/").newBuilder().host("127.0.0.1").build(),client,intervalMs=0)
            server.enqueue(response(fixture(artist.name)).setBodyDelay(2,TimeUnit.SECONDS))
            val pending=launch{repo.lookup(artist)}
            withContext(Dispatchers.IO){assertNotNull(server.takeRequest(2,TimeUnit.SECONDS))}
            pending.cancelAndJoin()
            withContext(Dispatchers.IO){assertTrue(cancelled.await(3,TimeUnit.SECONDS))}
            server.enqueue(response(fixture(artist.name)));val name=repo.lookup(artist)
            assertNotNull("Cancellation should allow another lookup: $failures; requests=${server.requestCount}",name);assertEquals(2,server.requestCount)
        }
    }
}
