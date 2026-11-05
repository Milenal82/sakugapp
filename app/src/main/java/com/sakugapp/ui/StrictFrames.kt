// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.ui

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.MediaExtractor
import androidx.annotation.RequiresApi
import com.sakugapp.data.FrameTimeline
import com.sakugapp.data.PostParser
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

internal class FrameDecoder(private val context:Context,private val url:String,private val preparedFile:File?=null,
    private val pageCache:com.sakugapp.data.PageVideoCache?=null) {
    private val mutex=Mutex()
    private var retriever:MediaMetadataRetriever?=null
    private var timeline:FrameTimeline?=null
    private var data:android.media.MediaDataSource?=null
    private var ownCache:com.sakugapp.data.PageVideoCache?=null
    fun frameTimeMs(index:Int)=timeline?.timeMs(index)
    fun indexAt(positionMs:Long)=timeline?.indexAt(positionMs)
    val hasTimeline get()=timeline!=null
    @RequiresApi(28)
    suspend fun open():Int=mutex.withLock{withContext(Dispatchers.IO){
        val uri=android.net.Uri.parse(url)
        val r=MediaMetadataRetriever();retriever=r
        val local=preparedFile ?: if(uri.scheme=="file")File(java.net.URI(url)).canonicalFile.also{file->
            check(file.isFile && listOf(context.cacheDir,File(context.filesDir,"favorites-media")).any{file.path.startsWith(it.canonicalPath+File.separator)}){"Untrusted local media."}
        }else null
        if(local!=null)r.setDataSource(local.path)
        else if(uri.scheme=="content")r.setDataSource(context,uri)
        else{require(PostParser.safeUrl(url)!=null);val cache=pageCache ?: com.sakugapp.data.PageVideoCache(context).also{ownCache=it};data=cache.mediaDataSource(url);check(data!!.size in 1..200L*1024*1024){"This video exceeds the 200 MiB analysis limit."};r.setDataSource(data!!)}
        val w=r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toLongOrNull() ?: 0
        val h=r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toLongOrNull() ?: 0
        check(w*h in 1..8_294_400){"Frame analysis supports video up to 3840×2160 pixels."}
        val count=r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)?.toIntOrNull()
        check(count!=null && count>0){"This device/format does not expose a decoded frame count."}
        timeline=null
        (data as? java.io.Flushable)?.flush() // commit read ranges before the extractor opens another reader
        if(count<=200000){
            val second=if(local==null && uri.scheme!="content")(pageCache ?: ownCache)!!.mediaDataSource(url)else null
            try{
                val samples=when{
                    local!=null->com.sakugapp.data.MediaSampleIndex.read(local)
                    second!=null->com.sakugapp.data.MediaSampleIndex.read(second.size){p,b,o,n->second.readAt(p,b,o,n)}
                    else->context.contentResolver.openFileDescriptor(uri,"r")!!.use{descriptor->
                        java.io.FileInputStream(descriptor.fileDescriptor).channel.use{channel->com.sakugapp.data.MediaSampleIndex.read(channel.size()){p,b,o,n->channel.position(p);channel.read(java.nio.ByteBuffer.wrap(b,o,n))}}
                    }
                }
                timeline=FrameTimeline.verified(samples.video.samples.map{it.timeUs},count)
            }catch(_:CancellationException){throw CancellationException()}catch(_:Exception){timeline=null}finally{second?.close()}
        }
        count
    }}
    @RequiresApi(28) suspend fun frame(index:Int):Bitmap=mutex.withLock{withContext(Dispatchers.IO){try{checkNotNull(retriever?.getFrameAtIndex(index)){"The device decoder could not return this exact frame."}}finally{(data as? java.io.Flushable)?.flush()}}}
    suspend fun close()=mutex.withLock{withContext(Dispatchers.IO){try{retriever?.release()}finally{retriever=null;data?.close();data=null;ownCache?.close();ownCache=null;timeline=null}}}
}
