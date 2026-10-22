// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.data

import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.coroutines.resumeWithException

data class VideoDownloadPlan(val url:HttpUrl,val filename:String,val mime:String) {
    companion object {
        fun from(post:Post):VideoDownloadPlan {
            require(post.isVideo && post.id>0)
            val url=checkNotNull(PostParser.safeUrl(post.fileUrl)).toHttpUrl()
            val ext=(post.fileExt ?: url.pathSegments.last().substringAfterLast('.')).lowercase()
            val mime=when(ext){"mp4"->"video/mp4";"webm"->"video/webm";"mkv"->"video/x-matroska";else->error("Unsupported video format.")}
            return VideoDownloadPlan(url,"sakugapp-${post.id}.$ext",mime)
        }
    }
}

/** Streams the actual original file. No credentials, automatic retries or guessed media URLs. */
class VideoDownloader(private val client:OkHttpClient=OkHttpClient.Builder()
    .followRedirects(false).retryOnConnectionFailure(false).readTimeout(30,TimeUnit.SECONDS).build()) {
    suspend fun download(plan:VideoDownloadPlan,open:()->OutputStream,discard:()->Unit,
        progress:(Long,Long)->Unit={_,_->}):Long = suspendCancellableCoroutine { continuation ->
        require(plan.url.isHttps && plan.url.username.isEmpty() && plan.url.password.isEmpty())
        val discarded=AtomicBoolean(false)
        fun cleanup(){if(discarded.compareAndSet(false,true))runCatching(discard)}
        val call=client.newCall(Request.Builder().url(plan.url).header("Accept",plan.mime+",application/octet-stream").build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object:Callback {
            override fun onFailure(call:Call,e:IOException) {
                cleanup();if(continuation.isActive)continuation.resumeWithException(DataFailure("Video download failed. Please retry.",e))
            }
            override fun onResponse(call:Call,response:Response) {
                try {
                    var bytes=0L
                    response.use {
                        if(!it.isSuccessful)throw DataFailure(httpError(it.code))
                        val body=it.body ?: throw DataFailure("Empty media response.")
                        val type=body.contentType()?.let{t->"${t.type}/${t.subtype}"}
                        if(type!=null && !type.startsWith("video/") && type!="application/octet-stream")throw DataFailure("The website did not return a video file.")
                        val total=body.contentLength()
                        body.byteStream().use { input ->open().use { output ->
                            val buffer=ByteArray(32*1024)
                            while(true) {
                                if(!continuation.isActive)throw IOException("Cancelled")
                                val n=input.read(buffer);if(n<0)break
                                output.write(buffer,0,n);bytes+=n;progress(bytes,total)
                            }
                            if(bytes==0L || (total>=0 && bytes!=total))throw DataFailure("The video download is incomplete.")
                            output.flush()
                        } }
                    }
                    continuation.resume(bytes,onCancellation={_,_,_->cleanup()})
                } catch(e:Exception) {
                    cleanup();if(continuation.isActive)continuation.resumeWithException(if(e is DataFailure)e else DataFailure("Video download failed. Please retry.",e))
                }
            }
        })
    }
}
