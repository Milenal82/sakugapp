// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import android.media.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sakugapp.data.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class ClipExportDeviceTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun fixture(name:String,folder:File)=File(folder,name).apply{InstrumentationRegistry.getInstrumentation().context.assets.open(name).use{input->outputStream().use{input.copyTo(it)}}}
    private fun hash(file:File)=MessageDigest.getInstance("SHA-256").digest(file.readBytes()).toList()
    private fun samples(file:File):Pair<String,Map<Long,List<Byte>>>{
        val e=MediaExtractor();try{e.setDataSource(file.path);val v=(0 until e.trackCount).first{e.getTrackFormat(it).getString(MediaFormat.KEY_MIME)!!.startsWith("video/")};val mime=e.getTrackFormat(v).getString(MediaFormat.KEY_MIME)!!;e.selectTrack(v)
            val values=mutableMapOf<Long,List<Byte>>();val buffer=ByteBuffer.allocate(1 shl 20)
            while(e.sampleTime>=0){val n=e.readSampleData(buffer,0);if(n<0)break;val bytes=ByteArray(n);buffer.position(0);buffer.get(bytes);values[e.sampleTime]=MessageDigest.getInstance("SHA-256").digest(bytes).toList();if(!e.advance())break};return mime to values
        }finally{e.release()}
    }
    @Test fun streamCopyKeepsEncodedSamplesAndOriginalAcrossMp4WebmAndMkv()=runBlocking{
        val folder=File(context.cacheDir,"clip-copy-test").apply{mkdirs()};val exporter=ClipExporter(context)
        try{for(name in listOf("clip-gop.mp4","clip-gop.webm","clip-gop.mkv")){
            val source=fixture(name,folder);val digest=hash(source);val input=samples(source)
            for(range in listOf(ClipRange(1250,2750),ClipRange(250,1700))){
                val result=exporter.export(source,folder,range,ClipMode.COPY);val output=samples(result.file)
                assertEquals(ClipMode.COPY,result.mode);assertEquals(input.first,output.first);assertTrue(result.actualStartUs<=range.startMs*1000);assertTrue(result.actualEndUs>=range.endMs*1000)
                assertTrue(output.second.isNotEmpty());assertEquals(result.videoSamples,output.second.size)
                for((time,bytes)in output.second)assertEquals("Encoded samples must be unchanged, $name PTS=$time",input.second[time+result.actualStartUs],bytes)
                assertEquals(digest,hash(source));assertTrue(exporter.scan(result.file).durationUs>0)
                val decoder=MediaMetadataRetriever();try{decoder.setDataSource(result.file.path);assertNotNull(decoder.getFrameAtTime(0,MediaMetadataRetriever.OPTION_CLOSEST))}finally{decoder.release()}
                println("COPY $name selected=$range actual=${result.actualStartUs}..${result.actualEndUs} samples=${result.videoSamples} mime=${result.mime}")
                result.file.delete()
            }
        }}finally{folder.deleteRecursively()}
    }
    @Test fun preciseModeReencodesAndVerifiesFrameCountDurationAndReadOnlySource()=runBlocking{
        val folder=File(context.cacheDir,"clip-precise-test").apply{mkdirs()};val exporter=ClipExporter(context)
        try{val source=fixture("clip-gop.mp4",folder);val original=hash(source)
            for(range in listOf(ClipRange(1200,2700),ClipRange(400,1300))){
                val result=withTimeout(60000){exporter.export(source,folder,range,ClipMode.PRECISE)}
                assertEquals("video/mp4",result.mime);assertEquals(ClipMode.PRECISE,result.mode)
                val retained=exporter.scan(source).timesUs.filter{it>=range.startMs*1000 && it<range.endMs*1000}
                assertEquals(retained.size,result.videoSamples);assertEquals(retained.first(),result.actualStartUs)
                assertTrue(kotlin.math.abs((result.actualEndUs-result.actualStartUs)-range.durationMs*1000)<=100000)
                assertEquals(original,hash(source));assertEquals("video/avc",samples(result.file).first)
                println("PRECISE selected=$range actual=${result.actualStartUs}..${result.actualEndUs} frames=${result.videoSamples}");result.file.delete()
            }
        }finally{folder.deleteRecursively()}
    }
    @Test fun cancelledStagingAndUnsupportedMediaNeverTouchTheOriginal()=runBlocking{
        val folder=File(context.cacheDir,"clip-failure-test").apply{mkdirs()};val exporter=ClipExporter(context)
        try{val source=fixture("clip-gop.mp4",folder);val digest=hash(source)
            val cancelled=launch(start=CoroutineStart.LAZY){exporter.stage(source.toURI().toString(),null,File(folder,"cancel"))};cancelled.cancelAndJoin();assertFalse(File(folder,"cancel/source.media").exists())
            val bad=File(folder,"broken.mp4").apply{writeText("not video")}
            try{exporter.export(bad,folder,ClipRange(100,500),ClipMode.COPY);fail("Damaged source must fail")}catch(_:Exception){}
            assertEquals(digest,hash(source))
        }finally{folder.deleteRecursively()}
    }
}
