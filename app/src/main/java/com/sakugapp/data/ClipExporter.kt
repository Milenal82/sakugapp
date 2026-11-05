// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.data

import android.content.Context
import android.media.*
import android.net.Uri
import android.os.StatFs
import androidx.media3.common.*
import androidx.media3.transformer.*
import kotlinx.coroutines.*
import java.io.File
import java.nio.ByteBuffer
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class ClipRange(val startMs:Long,val endMs:Long){init{require(startMs>=0 && endMs>startMs)};val durationMs get()=endMs-startMs}
enum class ClipMode { COPY, PRECISE }
data class ClipResult(val file:File,val mode:ClipMode,val selected:ClipRange,val actualStartUs:Long,val actualEndUs:Long,val videoSamples:Int,val mime:String)

/** Originals are read-only. All processing is confined to a disposable task folder. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class ClipExporter(private val context:Context){
    companion object{const val MAX_SOURCE=200L*1024*1024}
    suspend fun stage(url:String,cache:PageVideoCache?,directory:File):File=withContext(Dispatchers.IO){
        directory.mkdirs();val target=File(directory,"source.media")
        val factory=cache?.factory() ?: androidx.media3.datasource.DefaultDataSource.Factory(context)
        val source=factory.createDataSource()
        try{
            val length=source.open(androidx.media3.datasource.DataSpec.Builder().setUri(url).build())
            if(length>MAX_SOURCE)throw LibraryFailure("media_limit")
            if(StatFs(directory.path).availableBytes<maxOf(length,0)*3+16L*1024*1024)throw LibraryFailure("clip_space")
            var bytes=0L;target.outputStream().use{out->val buffer=ByteArray(65536);while(true){currentCoroutineContext().ensureActive();val n=source.read(buffer,0,buffer.size);if(n<0)break;bytes+=n;if(bytes>MAX_SOURCE)throw LibraryFailure("media_limit");if(StatFs(directory.path).availableBytes<8L*1024*1024)throw LibraryFailure("clip_space");out.write(buffer,0,n)}}
            check(bytes>0 && (length<0 || bytes==length));target
        }catch(e:Exception){target.delete();throw e}finally{source.close()}
    }
    suspend fun export(source:File,directory:File,range:ClipRange,mode:ClipMode):ClipResult {
        require(source.length() in 1..MAX_SOURCE)
        return if(mode==ClipMode.COPY)withContext(Dispatchers.IO){copy(source,directory,range)}else{
            val out=File(directory,"clip.mp4")
            encode(source,out,range)
            withContext(Dispatchers.IO){val scan=scan(out);val input=scan(source);val kept=input.timesUs.filter{it>=range.startMs*1000 && it<range.endMs*1000};check(kept.isNotEmpty() && scan.samples==kept.size && scan.durationUs>0){"clip_verify expected=${kept.size} output=${scan.samples} inputFirst=${input.firstUs} outputPTS=${scan.timesUs} duration=${scan.durationUs}"}
                // A real encoded frame interval bounds the observed output-duration difference.
                check(kotlin.math.abs(scan.durationUs-range.durationMs*1000)<=maxOf(scan.maxStepUs,100_000L)){"clip_verify"}
                ClipResult(out,mode,range,kept.first(),input.timesUs.firstOrNull{it>kept.last()} ?: input.durationUs,scan.samples,"video/mp4")
            }
        }
    }
    data class Scan(val firstUs:Long,val durationUs:Long,val samples:Int,val maxStepUs:Long,val timesUs:List<Long>)
    fun scan(file:File):Scan{
        val index=MediaSampleIndex.read(file);val times=index.video.samples.map{it.timeUs}.sorted();check(times.isNotEmpty())
        val step=times.zipWithNext().maxOfOrNull{it.second-it.first} ?: 41667L
        return Scan(times.first(),maxOf(index.durationUs,times.last()+minOf(step,100000)),times.size,step,times)
    }
    private suspend fun copy(source:File,directory:File,range:ClipRange):ClipResult{
        val index=MediaSampleIndex.read(source);val video=index.video
        val vm=checkNotNull(video.format?.sampleMimeType);val webm=vm in setOf("video/x-vnd.on2.vp8","video/x-vnd.on2.vp9")
        val supported=if(webm)setOf("video/x-vnd.on2.vp8","video/x-vnd.on2.vp9","audio/vorbis","audio/opus")else setOf("video/avc","video/hevc","video/mp4v-es","audio/mp4a-latm","audio/3gpp","audio/amr-wb")
        check(vm in supported){"clip_unsupported"}
        val sync=video.samples.filter{it.key}.map{it.timeUs}.sorted()
        val start=sync.lastOrNull{it<=range.startMs*1000} ?: error("clip_unsupported")
        val sourceScan=scan(source)
        val end=sync.firstOrNull{it>=range.endMs*1000 && it>start} ?: sourceScan.durationUs
        check(end>start && range.startMs*1000<sourceScan.durationUs){"clip_unsupported"}
        val output=File(directory,if(webm)"clip.webm"else"clip.mp4")
        val muxer=MediaMuxer(output.path,if(webm)MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM else MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var started=false
        try{
            val probe=MediaExtractor();val tracks=mutableListOf<Triple<Int,MediaSampleIndex.Track,Int>>()
            try{probe.setDataSource(source.path);val remaining=index.tracks.toMutableList()
                for(i in 0 until probe.trackCount){val format=probe.getTrackFormat(i);val mime=format.getString(MediaFormat.KEY_MIME).orEmpty()
                    if(!mime.startsWith("video/") && !mime.startsWith("audio/"))continue
                    check(mime in supported){"clip_unsupported"}
                    val track=remaining.firstOrNull{it.format?.sampleMimeType==mime} ?: error("clip_unsupported");remaining.remove(track)
                    tracks+=Triple(i,track,muxer.addTrack(format))
                }
            }finally{probe.release()}
            val rotation=video.format?.rotationDegrees ?: 0;if(!webm)muxer.setOrientationHint(rotation)
            muxer.start();started=true;val buffer=ByteBuffer.allocateDirect(16*1024*1024);val info=MediaCodec.BufferInfo()
            var copied=0
            for((trackId,track,target)in tracks){
                val e=MediaExtractor()
                try{e.setDataSource(source.path);e.selectTrack(trackId);var ordinal=0
                    while(e.sampleTime>=0){currentCoroutineContext().ensureActive()
                        val sample=track.samples.getOrNull(ordinal++) ?: error("clip_unsupported")
                        if(sample.timeUs>=start && sample.timeUs<end){
                            buffer.clear();val n=e.readSampleData(buffer,0);check(n>0 && n<=buffer.capacity()){"clip_unsupported"}
                            info.set(0,n,sample.timeUs-start,if(sample.key)MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                            muxer.writeSampleData(target,buffer,info);if(track.type==C.TRACK_TYPE_VIDEO)copied++
                        }
                        if(!e.advance())break
                    }
                    check(ordinal==track.samples.size){"clip_unsupported"}
                }finally{e.release()}
            }
            muxer.stop();started=false
            val result=scan(output);check(copied>0 && result.samples==copied && result.durationUs>0){"clip_verify"}
            return ClipResult(output,ClipMode.COPY,range,start+result.firstUs,start+result.durationUs,copied,if(webm)"video/webm"else"video/mp4")
        }finally{if(started)runCatching{muxer.stop()};muxer.release()}
    }
    private suspend fun encode(source:File,out:File,range:ClipRange)=withContext(Dispatchers.Main.immediate){
        var active:Transformer?=null
        try{suspendCancellableCoroutine<Unit>{c->
            val base=DefaultEncoderFactory.Builder(context).setEnableFallback(true).build()
            val force=object:Codec.EncoderFactory by base{override fun videoNeedsEncoding()=true;override fun audioNeedsEncoding()=true}
            val transformer=Transformer.Builder(context).setEncoderFactory(force).setVideoMimeType(MimeTypes.VIDEO_H264).setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(object:Transformer.Listener{
                    override fun onCompleted(composition:Composition,result:ExportResult){if(c.isActive){if(result.videoEncoderName==null)c.resumeWithException(IllegalStateException("clip_verify"))else c.resume(Unit)}}
                    override fun onError(composition:Composition,result:ExportResult,error:ExportException){if(c.isActive)c.resumeWithException(IllegalStateException("clip_unsupported",error))}
                }).build()
            active=transformer
            c.invokeOnCancellation{if(android.os.Looper.myLooper()==android.os.Looper.getMainLooper())transformer.cancel()else android.os.Handler(android.os.Looper.getMainLooper()).post{transformer.cancel()}}
            try{val media=MediaItem.Builder().setUri(Uri.fromFile(source)).setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs((range.startMs-1).coerceAtLeast(0)).setEndPositionMs(range.endMs).build()).build();transformer.start(EditedMediaItem.Builder(media).build(),out.path)}catch(e:Exception){if(c.isActive)c.resumeWithException(e)}
        }}finally{withContext(NonCancellable+Dispatchers.Main.immediate){active?.cancel()}}
    }
}
