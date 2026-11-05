// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import com.sakugapp.ui.*
import androidx.media3.common.*
import androidx.media3.datasource.*
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sakugapp.data.*
import com.sakugapp.ui.FrameDecoder
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.mockwebserver.*
import okhttp3.tls.*
import okio.Buffer
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicLong

@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PageCacheDeviceTest {
    @get:Rule val ui=androidx.compose.ui.test.junit4.createAndroidComposeRule<MainActivity>()
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private val server=MockWebServer();private lateinit var client:OkHttpClient
    private lateinit var data:ByteArray
    private val bytes=AtomicLong()
    @Before fun setup(){
        data=instrumentation.context.assets.open("frame-index-vfr.mp4").use{it.readBytes()}
        val cert=HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val tls=HandshakeCertificates.Builder().heldCertificate(cert).build();val trust=HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate).build()
        client=OkHttpClient.Builder().sslSocketFactory(trust.sslSocketFactory(),trust.trustManager).build();server.useHttps(tls.sslSocketFactory(),false)
        server.dispatcher=object:okhttp3.mockwebserver.Dispatcher(){override fun dispatch(request:RecordedRequest):MockResponse{
            val range=request.getHeader("Range")?.let{Regex("bytes=(\\d+)-(\\d*)").find(it)}
            val start=range?.groupValues?.get(1)?.toInt() ?: 0;val end=range?.groupValues?.get(2)?.toIntOrNull()?.coerceAtMost(data.lastIndex) ?: data.lastIndex
            if(start>data.lastIndex)return MockResponse().setResponseCode(416).setHeader("Content-Range","bytes */${data.size}")
            val result=data.copyOfRange(start,end+1);bytes.addAndGet(result.size.toLong())
            return MockResponse().setResponseCode(if(range!=null)206 else 200).setHeader("Content-Type","video/mp4").setHeader("Accept-Ranges","bytes").apply{if(range!=null)setHeader("Content-Range","bytes $start-$end/${data.size}")}.setBody(Buffer().write(result))
        }};server.start()
    }
    @After fun end(){server.shutdown();client.connectionPool.evictAll();client.dispatcher.executorService.shutdown()}
    private fun session(limit:Long=200L*1024*1024)=PageVideoCache(context,OkHttpDataSource.Factory(client),limit)
    private fun read(cache:PageVideoCache,start:Long=0,length:Long=C.LENGTH_UNSET.toLong()):ByteArray{
        val source=cache.factory().createDataSource();try{source.open(DataSpec.Builder().setUri(server.url("/video.mp4").toString()).setPosition(start).setLength(length).build());val out=java.io.ByteArrayOutputStream();val b=ByteArray(1024);while(true){val n=source.read(b,0,b.size);if(n<0)break;out.write(b,0,n)};return out.toByteArray()}finally{source.close()}
    }
    @Test fun rangesRepeatsFrameIndexAndReentryReuseOnlyTheirPage()=runBlocking{
        val cache=session();val url=server.url("/video.mp4").toString()
        assertArrayEquals(data.copyOfRange(0,1024),withContext(Dispatchers.IO){read(cache,0,1024)})
        val partial=server.requestCount
        repeat(3){assertArrayEquals(data.copyOfRange(10,100),withContext(Dispatchers.IO){read(cache,10,90)})};assertEquals(partial,server.requestCount)
        val partialDecoder=FrameDecoder(context,url,pageCache=cache)
        try{assertTrue(withTimeout(20000){partialDecoder.open()}>1)}finally{partialDecoder.close()}
        assertArrayEquals(data,withContext(Dispatchers.IO){read(cache)})
        val complete=server.requestCount
        repeat(3){assertArrayEquals(data,withContext(Dispatchers.IO){read(cache)})}
        val decoder=FrameDecoder(context,url,pageCache=cache)
        try{assertTrue(decoder.open()>1);val bitmap=decoder.frame(1);bitmap.recycle()}finally{decoder.close()}
        assertEquals("Frame analysis must not fetch cached bytes again",complete,server.requestCount)
        cache.close();withTimeout(10000){while(cache.directory.exists())delay(20)}
        val next=session();try{withContext(Dispatchers.IO){read(next)};assertTrue(server.requestCount>complete)}finally{next.close()}
        println("Page cache requests: partial=$partial complete=$complete reentry=${server.requestCount}; fixture bytes served=${bytes.get()}")
    }
    @Test fun actualExoPlayerLoopsThreeTimesWithoutAnotherTransfer()=runBlocking{
        val cache=session();lateinit var player:ExoPlayer;val repeats=AtomicLong()
        instrumentation.runOnMainSync{player=ExoPlayer.Builder(context).setMediaSourceFactory(DefaultMediaSourceFactory(cache.factory())).build();player.addListener(object:Player.Listener{override fun onMediaItemTransition(item:MediaItem?,reason:Int){if(reason==Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT)repeats.incrementAndGet()}});player.repeatMode=Player.REPEAT_MODE_ONE;player.setMediaItem(MediaItem.fromUri(server.url("/video.mp4").toString()));player.volume=0f;player.prepare();player.play()}
        try{withTimeout(30000){while(repeats.get()<1)delay(50)};val requests=server.requestCount
            withTimeout(40000){while(repeats.get()<3)delay(50)};assertEquals(requests,server.requestCount)
            instrumentation.runOnMainSync{repeat(3){player.seekTo(0)}};delay(500);assertEquals(requests,server.requestCount)
        }finally{instrumentation.runOnMainSync{player.release()};cache.close()}
    }
    @Test fun fullscreenAndPlayerReplacementKeepTheSamePageBytes()=runBlocking{
        val cache=session();var player:Player?=null;var show by mutableStateOf(true)
        ui.runOnUiThread{ui.activity.setContent{SakugappTheme(false){CompositionLocalProvider(LocalPageVideoCache provides cache){if(show)VideoPanel(server.url("/video.mp4").toString(),onPlayerReady={player=it},videoDimensions=320 to 180)}}}}
        try{
            ui.waitUntil(15000){var ready=false;ui.runOnUiThread{ready=player?.playbackState==Player.STATE_READY};ready}
            withContext(Dispatchers.IO){read(cache)};val requests=server.requestCount;val original=player
            ui.onNodeWithTag("toggle-fullscreen").performClick()
            ui.waitUntil(10000){ui.activity.requestedOrientation==android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE}
            androidx.test.uiautomator.UiDevice.getInstance(instrumentation).pressBack()
            ui.waitForIdle();assertSame(original,player)
            ui.runOnUiThread{show=false};ui.waitForIdle();ui.runOnUiThread{show=true}
            ui.waitUntil(15000){var ready=false;ui.runOnUiThread{ready=player!==original && player?.playbackState==Player.STATE_READY};ready}
            assertEquals("Fullscreen and view recreation must reuse cache",requests,server.requestCount)
        }finally{ui.runOnUiThread{show=false};ui.waitForIdle();cache.close()}
    }
    @Test fun boundedRepeatedPagesCleanUpWithoutLocksOrResidualMedia()=runBlocking{
        System.gc();val runtime=Runtime.getRuntime();val before=runtime.totalMemory()-runtime.freeMemory()
        repeat(15){val cache=session(2048);withContext(Dispatchers.IO){read(cache,0,1024)};cache.close();withTimeout(10000){while(cache.directory.exists())delay(10)}}
        System.gc();val growth=runtime.totalMemory()-runtime.freeMemory()-before;println("15 pages Java heap delta: $growth bytes");assertTrue("Bounded short-run heap growth",growth<32L*1024*1024)
    }
}
