// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.data

import android.content.Context
import android.media.MediaDataSource
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.*
import androidx.media3.datasource.cache.*
import kotlinx.coroutines.*
import java.io.File
import java.io.IOException
import java.util.UUID

/** A page owns the session; player/decoder leases outlive view replacement, never the page. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PageVideoCache(context:Context,private val upstream:DataSource.Factory=DefaultDataSource.Factory(context,
    DefaultHttpDataSource.Factory().setConnectTimeoutMs(15000).setReadTimeoutMs(15000)),private val limit:Long=200L*1024*1024){
    companion object {
        private val process=UUID.randomUUID().toString()
        private val io=CoroutineScope(SupervisorJob()+Dispatchers.IO)
        @Synchronized fun cleanOrphans(context:Context){
            val root=File(context.cacheDir,"page-video")
            root.listFiles().orEmpty().filter{it.name!=process}.forEach{it.deleteRecursively()}
            File(context.cacheDir,"frames").deleteRecursively() // obsolete cross-page frame copies
        }
    }
    val directory=File(context.cacheDir,"page-video/$process/${UUID.randomUUID()}")
    private var cache:SimpleCache?=null
    private var closed=false
    private var leases=0
    private var cleaning=false
    private val readers=mutableSetOf<DataSource>()
    @Synchronized private fun cachedFactory():DataSource.Factory {
        if(closed)throw IOException("Page closed")
        return try{
            val store=cache ?: SimpleCache(directory,LeastRecentlyUsedCacheEvictor(limit)).also{cache=it}
            CacheDataSource.Factory().setCache(store).setUpstreamDataSourceFactory(upstream)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR or CacheDataSource.FLAG_BLOCK_ON_CACHE)
        }catch(_:Exception){upstream} // full/unavailable temporary storage must not stop playback
    }
    fun factory()=DataSource.Factory { object:DataSource {
        private var delegate:DataSource?=null
        private var lease:AutoCloseable?=null
        private var original:DataSpec?=null;private var consumed=0L;private var bypass=false
        private val listeners=mutableListOf<TransferListener>()
        override fun addTransferListener(listener:TransferListener){listeners+=listener;delegate?.addTransferListener(listener)}
        override fun open(spec:DataSpec):Long {original=spec;consumed=0;bypass=false;lease=retain();try{val source=cachedFactory().createDataSource();delegate=source;synchronized(this@PageVideoCache){if(closed)throw IOException("Page closed");readers.add(this)};listeners.forEach(source::addTransferListener);return try{source.open(spec)}catch(e:Cache.CacheException){runCatching{source.close()};bypass=true;val fresh=upstream.createDataSource();delegate=fresh;listeners.forEach(fresh::addTransferListener);fresh.open(spec)}}catch(e:Exception){close();throw e}}
        override fun read(buffer:ByteArray,offset:Int,length:Int):Int{
            val n=try{checkNotNull(delegate).read(buffer,offset,length)}catch(e:IOException){
                var cause:Throwable?=e;var cacheError=false;while(cause!=null){if(cause is Cache.CacheException)cacheError=true;cause=cause.cause}
                if(!cacheError || bypass)throw e
                runCatching{delegate?.close()};bypass=true;val fresh=upstream.createDataSource();delegate=fresh;listeners.forEach(fresh::addTransferListener)
                fresh.open(checkNotNull(original).subrange(consumed));fresh.read(buffer,offset,length)
            };if(n>0)consumed+=n;return n
        }
        override fun getUri():Uri?=delegate?.uri
        override fun getResponseHeaders():Map<String,List<String>> = delegate?.responseHeaders.orEmpty()
        @Synchronized override fun close(){try{delegate?.close()}finally{delegate=null;synchronized(this@PageVideoCache){readers.remove(this)};lease?.close();lease=null}}
    }}
    @Synchronized fun retain():AutoCloseable {
        check(!closed);leases++;var released=false
        return AutoCloseable{synchronized(this){if(!released){released=true;leases--;cleanup()}}}
    }
    @Synchronized fun close(){closed=true;val active=readers.toList();io.launch{active.forEach{runCatching{it.close()}}};cleanup()}
    @Synchronized private fun cleanup(){if(closed && leases==0 && !cleaning){cleaning=true;val store=cache;io.launch{try{store?.release()}finally{directory.deleteRecursively()}}}}
    /** MediaRetriever/Extractor seek into the same cached byte ranges, without a second full copy. */
    fun mediaDataSource(url:String):MediaDataSource = object:MediaDataSource(){
        private val lease=retain();private var source:DataSource?=null;private var at=0L;private var size=-1L;private var ended=false
        @Synchronized override fun getSize():Long{if(size<0)open(0);return size}
        private fun open(position:Long){source?.close();source=factory().createDataSource();at=position
            val length=source!!.open(DataSpec.Builder().setUri(url).setPosition(position).build());if(length!=C.LENGTH_UNSET.toLong())size=position+length}
        @Synchronized override fun readAt(position:Long,buffer:ByteArray,offset:Int,count:Int):Int{
            if(ended)throw IOException("Decoder closed");if(count==0)return 0;if(size>=0 && position>=size)return -1
            if(source==null || position!=at)open(position)
            val read=source!!.read(buffer,offset,count);if(read>0)at+=read;return read
        }
        @Synchronized override fun close(){if(!ended){ended=true;try{source?.close()}finally{source=null;lease.close()}}}
    }
}
