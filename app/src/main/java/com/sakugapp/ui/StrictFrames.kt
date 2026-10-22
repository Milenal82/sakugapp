// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.ui

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.sakugapp.data.FrameIndex
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

internal class FrameDecoder(private val context:Context,private val url:String,private val preparedFile:File?=null) {
    private val mutex=Mutex()
    private var retriever:MediaMetadataRetriever?=null
    private val client=OkHttpClient.Builder().callTimeout(120,TimeUnit.SECONDS).build()
    @RequiresApi(28)
    suspend fun open():Int = mutex.withLock {
        require(PostParser.safeUrl(url)!=null)
        val directory=File(context.cacheDir,"frames").apply { mkdirs() }
        val name=MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString(""){"%02x".format(it)}
        val file=preparedFile ?: File(directory,"$name.media")
        if(!file.exists())download(file)
        withContext(Dispatchers.IO) {
            val r=MediaMetadataRetriever();retriever=r;r.setDataSource(file.absolutePath)
            val w=r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toLongOrNull() ?: 0
            val h=r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toLongOrNull() ?: 0
            check(w*h in 1..8_294_400) { "Frame analysis supports video up to 3840×2160 pixels." }
            val count=r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)?.toIntOrNull()
            check(count!=null && count>0) { "This device/format does not expose a decoded frame count. No estimated FPS stepping is used." }
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
    suspend fun close() = mutex.withLock { withContext(Dispatchers.IO) { retriever?.release();retriever=null } }
}

@Composable
fun StrictFrameDialog(url:String,close:()->Unit) {
    val context=androidx.compose.ui.platform.LocalContext.current
    val decoder=remember(url){FrameDecoder(context.applicationContext,url)}
    var consent by remember { mutableStateOf(false) }
    var index by remember { mutableStateOf<FrameIndex?>(null) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope=rememberCoroutineScope()
    LaunchedEffect(decoder,consent) {
        if(!consent)return@LaunchedEffect
        try {
            busy=true
            if(Build.VERSION.SDK_INT<28)error="Exact indexing requires Android 9 or later."
            else { index=FrameIndex(decoder.open());bitmap=decoder.frame(0) }
            busy=false
            awaitCancellation()
        } catch(e:CancellationException){throw e} catch(e:Exception){error=e.message;busy=false}
        finally { withContext(NonCancellable){decoder.close()} }
    }
    AlertDialog(onDismissRequest=close,title={Text(text("Exact frame analysis","正確なフレーム解析"))},text={
        Column(Modifier.heightIn(max=500.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            if(!consent)Text(text("Downloads the original video into private cache (up to 200 MiB). Android 9+ and a decoder supporting frame indexes are required. Starts at actual frame 1, independent of playback position.","元動画を専用キャッシュに保存します（最大200 MiB）。Android 9以上とフレーム索引対応デコーダーが必要です。再生時刻とは別に、実際の先頭フレームから解析します。"))
            if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            bitmap?.let { Image(it.asImageBitmap(),"Decoded frame",Modifier.fillMaxWidth().heightIn(max=280.dp)) }
            index?.let { current ->
                Text("${current.index+1} / ${current.count}")
                Row {
                    for(delta in listOf(-1,1))TextButton(enabled=!busy && (if(delta<0)current.index>0 else current.index<current.count-1),onClick={
                        scope.launch { busy=true;error=null;try { val next=current.move(delta);if(Build.VERSION.SDK_INT>=28){bitmap=decoder.frame(next.index);index=next} }catch(e:CancellationException){throw e}catch(e:Exception){error=e.message}finally{busy=false} }
                    }){Text(if(delta<0)text("Previous frame","前のフレーム") else text("Next frame","次のフレーム"))}
                }
            }
            error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
        }
    },confirmButton={if(!consent)TextButton(onClick={consent=true}){Text(text("Download and decode","保存して解析"))}else TextButton(onClick=close){Text(text("Close","閉じる"))}},dismissButton={if(!consent)TextButton(onClick=close){Text(text("Cancel","キャンセル"))}})
}
