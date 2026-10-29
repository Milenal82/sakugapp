// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FilterOutputStream
import java.io.OutputStream
import java.security.MessageDigest

/** User-initiated, sequential, bounded private downloads; never deletes media when a relation is removed. */
class OfflineLibraryMedia(context:Context,private val downloader:VideoDownloader=VideoDownloader(),private val root:File=File(context.filesDir,"favorites-media")){
    companion object {private val lock=Mutex();const val MAX_FILE=200L*1024*1024;const val MAX_TOTAL=1024L*1024*1024;const val BATCH_SIZE=20}
    private fun legacy(post:Post):File {
        val plan=VideoDownloadPlan.from(post);LibraryCodec.trustedMedia(plan.url.toString())
        val hash=MessageDigest.getInstance("SHA-256").digest(plan.url.toString().toByteArray()).take(12).joinToString(""){"%02x".format(it)}
        return File(root,"${post.id}-$hash.${plan.filename.substringAfterLast('.')}")
    }
    private fun indexFile()=android.util.AtomicFile(File(root,".media-index"))
    private fun index()=java.util.Properties().apply{runCatching{indexFile().openRead().use{load(it)}}}
    private fun key(post:Post)=legacy(post).name
    private fun writeIndex(key:String,name:String){
        val values=index().apply{setProperty(key,name)};val atomic=indexFile();val stream=atomic.startWrite()
        try{values.store(stream,null);atomic.finishWrite(stream)}catch(e:Exception){atomic.failWrite(stream);throw e}
    }
    fun existing(post:Post):File?=if(!post.isVideo)null else runCatching{
        val old=legacy(post)
        val name=index().getProperty(old.name)
        val current=name?.takeIf{it==File(it).name && !it.startsWith(".")}?.let{File(root,it)}
        current?.takeIf{it.isFile && it.length()>0} ?: old.takeIf{it.isFile && it.length()>0}
    }.getOrNull()
    fun bytes():Long=root.listFiles().orEmpty().filter{!it.name.startsWith(".")}.sumOf{it.length()}
    suspend fun download(post:Post,progress:(Long,Long)->Unit={_,_->}):File=lock.withLock{
        withContext(Dispatchers.IO){
            existing(post)?.let{return@withContext it}
            if(post.fileSize?.let{it>MAX_FILE}==true)throw LibraryFailure("media_limit")
            root.mkdirs();val target=File(root,MediaFileNames.unique(VideoDownloadPlan.from(post).filename){File(root,it).exists()});val part=File(target.path+".part")
            // Remove only an interrupted transfer with this exact content key.
            part.delete();val available=MAX_TOTAL-bytes();if(available<=0)throw LibraryFailure("media_limit")
            var written=0L
            try {
                PublicReadThrottle.awaitTurn()
                downloader.download(VideoDownloadPlan.from(post),open={object:FilterOutputStream(part.outputStream()){
                    override fun write(buffer:ByteArray,offset:Int,count:Int){if(written+count>minOf(MAX_FILE,available))throw LibraryFailure("media_limit");out.write(buffer,offset,count);written+=count}
                    override fun write(value:Int){if(written+1>minOf(MAX_FILE,available))throw LibraryFailure("media_limit");out.write(value);written++}
                }},discard={part.delete()},progress=progress)
                if(!part.renameTo(target))throw LibraryFailure("media_failure")
                try{writeIndex(key(post),target.name)}catch(e:Exception){target.delete();throw e}
                target
            }finally{part.delete()}
        }
    }
    suspend fun clear()=lock.withLock{withContext(Dispatchers.IO){root.listFiles().orEmpty().forEach{if(!it.delete())throw LibraryFailure("media_failure")}}}
}
