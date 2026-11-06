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
enum class ClipMode { PRECISE }
data class ClipResult(val file:File,val mode:ClipMode,val selected:ClipRange,val actualStartUs:Long,val actualEndUs:Long,val videoSamples:Int,val mime:String,val outputDurationUs:Long=actualEndUs-actualStartUs)

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
        require(mode==ClipMode.PRECISE)
        return precise(source,directory,range)
    }
    private suspend fun precise(source:File,directory:File,range:ClipRange):ClipResult {
        val index=withContext(Dispatchers.IO){MediaSampleIndex.read(source)}
        val times=index.video.samples.map{it.timeUs}.sorted()
        val sourceDuration=maxOf(index.durationUs,times.last()+(times.zipWithNext().lastOrNull()?.let{it.second-it.first} ?: 41667))
        val expected=times.filter{it>=range.startMs*1000 && it<range.endMs*1000};check(expected.isNotEmpty()){"clip_verify"}
        val sync=index.video.samples.filter{it.key}.map{it.timeUs}.sorted()
        val decodeStart=(sync.lastOrNull{it<range.startMs*1000} ?: sync.first()).coerceAtLeast(0)/1000
        val nextKey=sync.firstOrNull{it>=range.endMs*1000}
        val decodeEnd=if(nextKey!=null)minOf((sourceDuration+999)/1000,nextKey/1000+1)else (sourceDuration+999)/1000
        val gate=ClipFrameGate(range,decodeStart)
        val video=File(directory,"video-${java.util.UUID.randomUUID()}.mp4")
        val encoded=transform(source,video,ClipRange(decodeStart,decodeEnd),copy=false,removeAudio=true,effect=gate,outputDurationMs=range.durationMs)
        val accepted=synchronized(gate.accepted){gate.accepted.toList().sorted()}
        check(accepted.size==expected.size && accepted.zip(expected).all{kotlin.math.abs(it.first-it.second)<=1000}){"clip_verify gate expected=$expected actual=$accepted"}
        val audio=if(index.tracks.any{it.type==C.TRACK_TYPE_AUDIO})File(directory,"audio-${java.util.UUID.randomUUID()}.mp4")else null
        val output=if(audio==null)video else {
            transform(source,audio,range,copy=false,removeVideo=true)
            val v=EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(video))).build()
            val a=EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(audio))).build()
            val composition=Composition.Builder(EditedMediaItemSequence.Builder(v).build(),EditedMediaItemSequence.Builder(a).build()).setTransmuxAudio(true).setTransmuxVideo(true).build()
            val merged=File(directory,"clip-${java.util.UUID.randomUUID()}.mp4")
            val result=transform(video,merged,range,copy=true,composition=composition)
            check(result.videoEncoderName==null && result.audioEncoderName==null){"clip_verify"};merged
        }
        val result=withContext(Dispatchers.IO){scan(output)}
        check(result.samples==accepted.size && result.samples==encoded.videoFrameCount){"clip_verify encoded=${encoded.videoFrameCount} expected=${accepted.size} output=${result.samples}"}
        check(kotlin.math.abs(result.durationUs-range.durationMs*1000)<=maxOf(result.maxStepUs,100000L)){"clip_verify duration=${result.durationUs}"}
        return ClipResult(output,ClipMode.PRECISE,range,accepted.first(),times.firstOrNull{it>accepted.last()} ?: index.durationUs,result.samples,"video/mp4",result.durationUs)
    }
    data class Scan(val firstUs:Long,val durationUs:Long,val samples:Int,val maxStepUs:Long,val timesUs:List<Long>)
    fun scan(file:File):Scan{
        val index=MediaSampleIndex.read(file);val times=index.video.samples.map{it.timeUs}.sorted();check(times.isNotEmpty())
        val step=times.zipWithNext().maxOfOrNull{it.second-it.first} ?: 41667L
        return Scan(times.first(),maxOf(index.durationUs,times.last()+minOf(step,100000)),times.size,step,times)
    }
    private suspend fun transform(source:File,out:File,range:ClipRange,copy:Boolean,removeAudio:Boolean=false,removeVideo:Boolean=false,effect:androidx.media3.effect.GlEffect?=null,composition:Composition?=null,outputDurationMs:Long=range.durationMs):ExportResult=withContext(Dispatchers.Main.immediate){
        var active:Transformer?=null
        try{suspendCancellableCoroutine<ExportResult>{c->
            val base=DefaultEncoderFactory.Builder(context).setEnableFallback(true).build()
            val force=object:Codec.EncoderFactory by base{override fun videoNeedsEncoding()=true;override fun audioNeedsEncoding()=true}
            val builder=Transformer.Builder(context).setMuxerFactory(InAppMp4Muxer.Factory().setVideoDurationUs(outputDurationMs*1000))
            if(!copy)builder.setEncoderFactory(force).setVideoMimeType(MimeTypes.VIDEO_H264).setAudioMimeType(MimeTypes.AUDIO_AAC)
            val transformer=builder.addListener(object:Transformer.Listener{
                    override fun onCompleted(composition:Composition,result:ExportResult){if(c.isActive){if(!copy && (if(removeVideo)result.audioEncoderName==null else result.videoEncoderName==null))c.resumeWithException(IllegalStateException("clip_verify"))else c.resume(result)}}
                    override fun onError(composition:Composition,result:ExportResult,error:ExportException){if(c.isActive){val full=generateSequence<Throwable>(error){it.cause}.any{it.message?.contains("ENOSPC")==true || it.message?.contains("No space left")==true};c.resumeWithException(if(full)LibraryFailure("storage_full")else IllegalStateException("clip_unsupported",error))}}
                }).build()
            active=transformer
            c.invokeOnCancellation{if(android.os.Looper.myLooper()==android.os.Looper.getMainLooper())transformer.cancel()else android.os.Handler(android.os.Looper.getMainLooper()).post{transformer.cancel()}}
            try{val media=MediaItem.Builder().setUri(Uri.fromFile(source)).setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(range.startMs).setEndPositionMs(range.endMs).setStartsAtKeyFrame(copy || removeAudio).build()).build();val edited=EditedMediaItem.Builder(media).setRemoveAudio(removeAudio).setRemoveVideo(removeVideo)
                if(effect!=null)edited.setEffects(Effects(emptyList(),listOf(effect)))
                if(composition!=null)transformer.start(composition,out.path)else transformer.start(edited.build(),out.path)}catch(e:Exception){if(c.isActive)c.resumeWithException(e)}
        }}finally{withContext(NonCancellable+Dispatchers.Main.immediate){active?.cancel()}}
    }
}
