// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.data

import android.content.Context
import android.net.Uri
import android.os.StatFs
import androidx.media3.common.*
import androidx.media3.transformer.*
import kotlinx.coroutines.*
import java.io.File
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
        return if(mode==ClipMode.COPY)copy(source,directory,range)else{
            val out=File(directory,"clip-${java.util.UUID.randomUUID()}.mp4")
            val encoded=transform(source,out,range,copy=false)
            withContext(Dispatchers.IO){val scan=scan(out);val input=scan(source);val kept=input.timesUs.filter{it>=range.startMs*1000 && it<range.endMs*1000};check(kept.isNotEmpty() && scan.samples==kept.size && scan.durationUs>0){"clip_verify encoded=${encoded.videoFrameCount} expected=${kept.size} output=${scan.samples} inputFirst=${input.firstUs} outputPTS=${scan.timesUs} duration=${scan.durationUs}"}
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
    private suspend fun copy(source:File,directory:File,range:ClipRange):ClipResult=withContext(Dispatchers.IO){
        val task=currentCoroutineContext();val index=MediaSampleIndex.read(source){task.ensureActive()};val video=index.video
        val vm=checkNotNull(video.format?.sampleMimeType);val webm=vm in setOf(MimeTypes.VIDEO_VP8,MimeTypes.VIDEO_VP9)
        val supported=if(webm)setOf(MimeTypes.VIDEO_VP8,MimeTypes.VIDEO_VP9,MimeTypes.AUDIO_VORBIS,MimeTypes.AUDIO_OPUS)else setOf(MimeTypes.VIDEO_H264,MimeTypes.VIDEO_H265,MimeTypes.VIDEO_MP4V,MimeTypes.AUDIO_AAC,MimeTypes.AUDIO_AMR_NB,MimeTypes.AUDIO_AMR_WB)
        check(index.tracks.all{it.format?.sampleMimeType in supported}){"clip_unsupported"}
        val sync=video.samples.filter{it.key}.map{it.timeUs}.sorted()
        val start=sync.lastOrNull{it<=range.startMs*1000} ?: error("clip_unsupported")
        val end=sync.firstOrNull{it>=range.endMs*1000 && it>start} ?: scan(source).durationUs
        val output=File(directory,"clip-${java.util.UUID.randomUUID()}.${if(webm)"webm"else"mp4"}")
        val copied=transform(source,output,ClipRange(start/1000,(end+999)/1000),copy=true,webm=webm)
        check(copied.videoEncoderName==null && copied.audioEncoderName==null){"clip_verify copy videoEncoder=${copied.videoEncoderName} audioEncoder=${copied.audioEncoderName}"}
        val result=scan(output);val kept=video.samples.filter{it.timeUs>=start && it.timeUs<end}
        check(result.samples==kept.size && result.samples>0){"clip_verify copy expected=${kept.size} output=${result.samples}"}
        ClipResult(output,ClipMode.COPY,range,start+result.firstUs,start+result.durationUs,result.samples,if(webm)"video/webm"else"video/mp4")
    }
    private suspend fun transform(source:File,out:File,range:ClipRange,copy:Boolean,webm:Boolean=false):ExportResult=withContext(Dispatchers.Main.immediate){
        var active:Transformer?=null
        try{suspendCancellableCoroutine<ExportResult>{c->
            val base=DefaultEncoderFactory.Builder(context).setEnableFallback(true).build()
            val force=object:Codec.EncoderFactory by base{override fun videoNeedsEncoding()=true;override fun audioNeedsEncoding()=true}
            val builder=Transformer.Builder(context).setMuxerFactory(if(webm)WebmClipMuxer.Factory()else InAppMp4Muxer.Factory())
            if(!copy)builder.setEncoderFactory(force).setVideoMimeType(MimeTypes.VIDEO_H264).setAudioMimeType(MimeTypes.AUDIO_AAC)
            val transformer=builder.addListener(object:Transformer.Listener{
                    override fun onCompleted(composition:Composition,result:ExportResult){if(c.isActive){if(!copy && result.videoEncoderName==null)c.resumeWithException(IllegalStateException("clip_verify"))else c.resume(result)}}
                    override fun onError(composition:Composition,result:ExportResult,error:ExportException){if(c.isActive){val full=generateSequence<Throwable>(error){it.cause}.any{it.message?.contains("ENOSPC")==true || it.message?.contains("No space left")==true};c.resumeWithException(if(full)LibraryFailure("storage_full")else IllegalStateException("clip_unsupported",error))}}
                }).build()
            active=transformer
            c.invokeOnCancellation{if(android.os.Looper.myLooper()==android.os.Looper.getMainLooper())transformer.cancel()else android.os.Handler(android.os.Looper.getMainLooper()).post{transformer.cancel()}}
            try{val media=MediaItem.Builder().setUri(Uri.fromFile(source)).setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(if(copy)range.startMs else (range.startMs-1).coerceAtLeast(0)).setEndPositionMs(range.endMs).setStartsAtKeyFrame(copy).build()).build();transformer.start(EditedMediaItem.Builder(media).build(),out.path)}catch(e:Exception){if(c.isActive)c.resumeWithException(e)}
        }}finally{withContext(NonCancellable+Dispatchers.Main.immediate){active?.cancel()}}
    }
}
