// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import com.sakugapp.data.*
import com.sakugapp.ui.*
import androidx.compose.ui.graphics.luminance
import java.io.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.mockwebserver.*
import okhttp3.tls.*
import org.junit.*
import org.junit.Assert.*

class AppearanceAndDownloadTest {
    private val server=MockWebServer()
    private lateinit var client:OkHttpClient
    @Before fun start(){
        val certificate=HeldCertificate.Builder().commonName("localhost").addSubjectAlternativeName("localhost").build()
        val serverTls=HandshakeCertificates.Builder().heldCertificate(certificate).build()
        server.useHttps(serverTls.sslSocketFactory(),false);server.start()
        val trust=HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        client=OkHttpClient.Builder().sslSocketFactory(trust.sslSocketFactory(),trust.trustManager).followRedirects(false).retryOnConnectionFailure(false).readTimeout(3,TimeUnit.SECONDS).build()
    }
    @After fun stop(){server.shutdown()}
    private fun post()=PostParser.posts(javaClass.getResource("/live/posts.json")!!.readText()).first()
    private fun plan()=VideoDownloadPlan.from(post().copy(fileUrl=server.url("/original.mp4").toString()))
    @Test fun scorePresetReplacesAllScoreFieldsAndPreservesOtherSyntax(){
        assertEquals("~a ~b -rating:questionable id:100.. order:score score:>=3",SearchSyntax.score("~a ~b score:>1 -score:0 -rating:questionable id:100.. order:score","score:>=3"))
        assertEquals("tag score:>=100",SearchSyntax.score("tag score:>=3","score:>=100"))
        assertThrows(IllegalArgumentException::class.java){SearchSyntax.score("tag","score:invented")}
    }
    @Test fun wikiTitleIsEncodedAsOneExactParameterOnTheOfficialHost(){
        assertEquals("https://www.sakugabooru.com/wiki/show?title=mokmok",UrlPolicy.artistWiki("mokmok"))
        val url=UrlPolicy.artistWiki("a&title=b#c").toHttpUrl()
        assertEquals("www.sakugabooru.com",url.host);assertEquals("a&title=b#c",url.queryParameter("title"));assertEquals(1,url.querySize)
    }
    @Test fun bothThemesKeepTagTextReadableAndUseDifferentBackgrounds(){
        assertTrue(appColors(true).background.luminance()>0.9f);assertTrue(appColors(false).background.luminance()<0.02f)
        for(light in listOf(true,false)) {
            val bg=appColors(light).background.luminance()
            for(color in listOf(copyrightColor(light),artistColor(light))) {
                val fg=color.luminance();val contrast=(maxOf(bg,fg)+0.05f)/(minOf(bg,fg)+0.05f)
                assertTrue("Readable tag text in $light theme: $contrast",contrast>=4.5f)
            }
        }
    }
    @Test fun downloadUsesOnlyTheActualMediaUrlAndSafeFilename(){
        val plan=VideoDownloadPlan.from(post());assertEquals(post().fileUrl,plan.url.toString());assertEquals("post_319947.mp4",plan.filename);assertEquals("video/mp4",plan.mime)
        assertThrows(IllegalArgumentException::class.java){VideoDownloadPlan.from(post().copy(fileUrl=null))}
        assertThrows(IllegalStateException::class.java){VideoDownloadPlan.from(post().copy(fileUrl="http://example.org/a.mp4"))}
    }
    @Test fun downloadStreamsAllBytesAndReportsCompletionOnlyAfterClosingDestination()=runBlocking {
        val expected=ByteArray(65539){(it%251).toByte()};server.enqueue(MockResponse().setBody(okio.Buffer().write(expected)).setHeader("Content-Type","video/mp4"))
        val closed=AtomicBoolean(false);val output=object:ByteArrayOutputStream(){override fun close(){closed.set(true);super.close()}}
        var deleted=false;var progress=0L
        val count=VideoDownloader(client).download(plan(),{output},{deleted=true}){bytes,_->progress=bytes}
        assertEquals(expected.size.toLong(),count);assertEquals(count,progress);assertArrayEquals(expected,output.toByteArray());assertTrue(closed.get());assertFalse(deleted)
        assertEquals("/original.mp4",server.takeRequest().path)
    }
    @Test fun forbiddenDoesNotOpenDestinationRetryOrGuessAnAlternative():Unit=runBlocking {
        server.enqueue(MockResponse().setResponseCode(403));var opened=false;var deleted=false
        try{VideoDownloader(client).download(plan(),{opened=true;ByteArrayOutputStream()},{deleted=true});fail("expected failure")}
        catch(e:DataFailure){assertTrue(e.message!!.contains("403"))}
        assertFalse(opened);assertTrue(deleted);assertEquals(1,server.requestCount)
    }
    @Test fun htmlWithSuccessStatusCannotBeSavedAsAVideo():Unit=runBlocking {
        server.enqueue(MockResponse().setBody("<html>denied</html>").setHeader("Content-Type","text/html"));var deleted=false
        try{VideoDownloader(client).download(plan(),{fail("must not open");ByteArrayOutputStream()},{deleted=true});fail("expected failure")}
        catch(_:DataFailure){assertTrue(deleted)}
    }
    @Test fun truncatedResponseDiscardsPartialOutputAndDoesNotReportSuccess():Unit=runBlocking {
        server.enqueue(MockResponse().setBody("abc").setHeader("Content-Length","100").setSocketPolicy(SocketPolicy.DISCONNECT_AT_END));var deleted=false
        try{VideoDownloader(client).download(plan(),{ByteArrayOutputStream()},{deleted=true});fail("expected failure")}
        catch(_:DataFailure){assertTrue(deleted)}
    }
    @Test fun destinationFailureClosesAndDiscardsOutput():Unit=runBlocking {
        server.enqueue(MockResponse().setBody("actual test bytes"));var closed=false;var deleted=false
        val output=object:OutputStream(){override fun write(b:Int){throw IOException("test full destination")};override fun close(){closed=true}}
        try{VideoDownloader(client).download(plan(),{output},{deleted=true});fail("expected failure")}
        catch(_:DataFailure){assertTrue(closed);assertTrue(deleted)}
    }
    @Test fun cancellationStopsNetworkAndDiscardsPartialFile()=runBlocking {
        server.enqueue(MockResponse().setBody("x".repeat(1024)).throttleBody(16,1,TimeUnit.SECONDS))
        val started=CountDownLatch(1);val deleted=CountDownLatch(1)
        val task=launch(Dispatchers.Default){VideoDownloader(client).download(plan(),{ByteArrayOutputStream()},{deleted.countDown()}){_,_->started.countDown()}}
        assertTrue(started.await(3,TimeUnit.SECONDS));task.cancelAndJoin();assertTrue(deleted.await(3,TimeUnit.SECONDS));assertEquals(1,server.requestCount)
    }
}
