// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.ui

import com.sakugapp.R

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.MediaExtractor
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import com.sakugapp.data.FrameIndex
import com.sakugapp.data.FrameTimeline
import com.sakugapp.data.PostParser
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.*
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private val frameCacheMutex=Mutex()

internal class FrameDecoder(private val context:Context,private val url:String,private val preparedFile:File?=null) {
    private val mutex=Mutex()
    private var retriever:MediaMetadataRetriever?=null
    private var timeline:FrameTimeline?=null
    fun frameTimeMs(index:Int)=timeline?.timeMs(index)
    fun indexAt(positionMs:Long)=timeline?.indexAt(positionMs)
    private val client=OkHttpClient.Builder().callTimeout(120,TimeUnit.SECONDS).build()
    @RequiresApi(28)
    suspend fun open():Int = mutex.withLock {
        val directory=File(context.cacheDir,"frames").apply { mkdirs() }
        val name=MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString(""){ "%02x".format(it) }
        val local=if(url.startsWith("file:"))File(java.net.URI(url)).canonicalFile.also{file->
            check(file.isFile && listOf(context.cacheDir,File(context.filesDir,"favorites-media")).any{root->file.path.startsWith(root.canonicalPath+File.separator)}){"Untrusted local media."}
        }else null
        if(local==null)require(PostParser.safeUrl(url)!=null)
        val file=preparedFile ?: local ?: File(directory,"$name.media")
        if(preparedFile==null && local==null)frameCacheMutex.withLock{
            fun trim(max:Long){var bytes=directory.listFiles().orEmpty().filter{it.extension=="media"}.sumOf{it.length()};directory.listFiles().orEmpty().filter{it!=file && it.extension=="media"}.sortedBy{it.lastModified()}.forEach{old->if(bytes>max){val size=old.length();if(old.delete())bytes-=size}}}
            if(!file.exists()){trim(200L*1024*1024);download(file)}
            file.setLastModified(System.currentTimeMillis());trim(400L*1024*1024)
        }
        withContext(Dispatchers.IO) {
            val r=MediaMetadataRetriever();retriever=r;r.setDataSource(file.absolutePath)
            val w=r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toLongOrNull() ?: 0
            val h=r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toLongOrNull() ?: 0
            check(w*h in 1..8_294_400) { "Frame analysis supports video up to 3840×2160 pixels." }
            val count=r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)?.toIntOrNull()
            check(count!=null && count>0) { "This device/format does not expose a decoded frame count. No estimated FPS stepping is used." }
            timeline=null
            if(count<=200000){
                val extractor=MediaExtractor()
                try{
                    extractor.setDataSource(file.absolutePath)
                    val track=(0 until extractor.trackCount).firstOrNull{extractor.getTrackFormat(it).getString(android.media.MediaFormat.KEY_MIME)?.startsWith("video/")==true}
                    if(track!=null){
                        extractor.selectTrack(track);val timestamps=ArrayList<Long>()
                        while(extractor.sampleTime>=0 && timestamps.size<=count){timestamps+=extractor.sampleTime;if(!extractor.advance())break}
                        timeline=FrameTimeline.verified(timestamps,count)
                    }
                }catch(_:Exception){timeline=null}finally{extractor.release()}
            }
            count
        }
    }
    private suspend fun download(destination:File):Unit = suspendCancellableCoroutine { continuation ->
        val part=File(destination.path+".part")
        val call=client.newCall(Request.Builder().url(url).build());continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object:Callback {
            override fun onFailure(call:Call,e:java.io.IOException){part.delete();if(!continuation.isCancelled)continuation.resumeWithException(e)}
            override fun onResponse(call:Call,response:Response) {
                try {
                    response.use {
                        check(it.isSuccessful && it.request.url.isHttps) { "Media download rejected (HTTP ${it.code})." }
                        val body=checkNotNull(it.body);val max=200L*1024*1024
                        check(body.contentLength()<=max) { "This video exceeds the 200 MiB analysis limit." }
                        body.byteStream().use { input -> part.outputStream().use { output ->
                            val buffer=ByteArray(32*1024);var total=0L
                            while(true){if(continuation.isCancelled)throw java.io.IOException("Cancelled");val n=input.read(buffer);if(n<0)break;total+=n;check(total<=max){"Video exceeds 200 MiB."};output.write(buffer,0,n)}
                        } }
                    }
                    check(!continuation.isCancelled && part.renameTo(destination)) { "Unable to cache the video." }
                    continuation.resume(Unit)
                } catch(e:Exception) { part.delete();if(!continuation.isCancelled)continuation.resumeWithException(e) }
            }
        })
    }
    @RequiresApi(28)
    suspend fun frame(index:Int):Bitmap = mutex.withLock { withContext(Dispatchers.IO) {
        // getFrameAtIndex decodes the requested actual index. No seekTo, timestamp/FPS arithmetic or closest-sync approximation.
        checkNotNull(retriever?.getFrameAtIndex(index)) { "The device decoder could not return this exact frame." }
    } }
    suspend fun close() = mutex.withLock { withContext(Dispatchers.IO) { retriever?.release();retriever=null;timeline=null } }
}

@Composable
fun StrictFrameDialog(url:String,startPositionMs:Long?=null,initialDelta:Int=0,onFrameTime:(Long)->Unit={},close:()->Unit) {
    val context=androidx.compose.ui.platform.LocalContext.current
    val decoder=remember(url){FrameDecoder(context.applicationContext,url)}
    var consent by remember { mutableStateOf(false) }
    var index by remember { mutableStateOf<FrameIndex?>(null) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var frameInput by remember { mutableStateOf("1") }
    var slider by remember { mutableFloatStateOf(0f) }
    var alignmentUnknown by remember{mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    fun decode(target:Int) { scope.launch {
        busy=true;error=null
        try { if(Build.VERSION.SDK_INT>=28) {
            val result=decoder.frame(target)
            bitmap=result;index=index?.copy(index=target);frameInput=(target+1).toString();slider=target.toFloat();decoder.frameTimeMs(target)?.let(onFrameTime)
        } }catch(e:CancellationException){throw e}catch(e:Exception){error=e.message}finally{busy=false}
    } }
    LaunchedEffect(decoder,consent,attempt) {
        if(!consent)return@LaunchedEffect
        try {
            busy=true;error=null;index=null;bitmap=null;frameInput="1";slider=0f
            if(Build.VERSION.SDK_INT<28)error=context.getString(R.string.ui_exact_indexing_requires_android_9_or_later)
            else {
                val count=decoder.open();val mapped=startPositionMs?.let{decoder.indexAt(it)}
                alignmentUnknown=startPositionMs!=null && mapped==null
                val initial=if(mapped!=null)(mapped+initialDelta).coerceIn(0,count-1)else 0
                index=FrameIndex(count,initial);bitmap=decoder.frame(initial);frameInput=(initial+1).toString();slider=initial.toFloat();decoder.frameTimeMs(initial)?.let(onFrameTime)
            }
            busy=false
            awaitCancellation()
        } catch(e:CancellationException){throw e} catch(e:Exception){error=e.message;busy=false}
        finally { withContext(NonCancellable){decoder.close()} }
    }
    AlertDialog(onDismissRequest=close,title={Text(text(R.string.ui_exact_frame_analysis))},text={
        Column(Modifier.heightIn(max=500.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            if(!consent)Text(text(if(url.startsWith("file:"))R.string.ui_local_frame_analysis_hint else R.string.ui_downloads_the_original_video_into_private_cache_up_to_200_mib_and))
            if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            if(alignmentUnknown)Text(text(R.string.ui_frame_alignment_unknown),style=MaterialTheme.typography.bodySmall)
            bitmap?.let { Image(it.asImageBitmap(),text(R.string.ui_decoded_frame),Modifier.fillMaxWidth().heightIn(max=280.dp)) }
            index?.let { current ->
                Text("${current.index+1} / ${current.count}",Modifier.testTag("frame-counter"))
                Text(text(R.string.ui_use_the_actual_decoded_frame_index_not_a_time_estimate),style=MaterialTheme.typography.bodySmall)
                Row {
                    for(delta in listOf(-1,1))TextButton(modifier=Modifier.testTag(if(delta<0)"exact-previous-frame"else"exact-next-frame"),enabled=!busy && (if(delta<0)current.index>0 else current.index<current.count-1),onClick={
                        decode(current.move(delta).index)
                    }){Text(if(delta<0)text(R.string.ui_previous_frame) else text(R.string.ui_next_frame))}
                }
                if(current.count>1)Slider(value=slider,onValueChange={slider=it},onValueChangeFinished={decode(slider.toInt().coerceIn(0,current.count-1))},valueRange=0f..(current.count-1).toFloat(),enabled=!busy,modifier=Modifier.testTag("frame-slider"))
                OutlinedTextField(frameInput,{frameInput=it},label={Text(text(R.string.ui_frame_number))},singleLine=true,enabled=!busy,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.fillMaxWidth().testTag("frame-number"))
                TextButton(enabled=!busy && frameInput.toIntOrNull()?.let{it in 1..current.count}==true,onClick={decode(frameInput.toInt()-1)},modifier=Modifier.testTag("go-to-frame")){Text(text(R.string.ui_go_to_frame))}
            }
            error?.let { Text(translateMessage(it),color=MaterialTheme.colorScheme.error);TextButton(enabled=!busy,onClick={attempt++}){Text(text(R.string.ui_retry))} }
        }
    },confirmButton={if(!consent)TextButton(onClick={consent=true}){Text(text(if(url.startsWith("file:"))R.string.ui_decode_local_video else R.string.ui_download_and_decode))}else TextButton(onClick=close){Text(text(R.string.ui_close))}},dismissButton={if(!consent)TextButton(onClick=close){Text(text(R.string.ui_cancel))}})
}
