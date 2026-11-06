// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.data

import android.content.Context
import android.content.Intent
import android.util.AtomicFile
import androidx.core.content.ContextCompat
import com.sakugapp.BatchDownloadService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.UUID

/** Application commands + service-owned workers: no transfer belongs to a Compose scope. */
class BatchDownloadQueue(private val context:Context,val storage:DownloadStorage=DownloadStorage(context),journalName:String="batch-download-queue-v1.json",private val startService:(()->Unit)?=null):AutoCloseable {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val file=AtomicFile(File(context.filesDir,journalName))
    private val gate=Mutex()
    private val _state=MutableStateFlow(BatchQueueState());val state=_state.asStateFlow()
    private val _error=MutableStateFlow<String?>(null);val error=_error.asStateFlow()
    private val workers=mutableMapOf<String,Job>()
    private var valid=true
    private val ready=scope.async {
        try {
            val restored=withContext(Dispatchers.IO){
                storage.cleanAbandoned()
                val saved=if(file.baseFile.exists())BatchQueueCodec.recover(BatchQueueCodec.decode(file.openRead().bufferedReader().use{it.readText()}))else BatchQueueState()
                val legacy=OfflineLibraryMedia(context)
                saved.copy(tasks=saved.tasks.map{if(it.status==BatchStatus.COMPLETE && legacy.existing(it.post)==null && storage.existing(it.post,DownloadStorage.Kind.BATCH)==null)it.copy(status=BatchStatus.FAILED,bytes=0,error="media_failure")else it})
            }
            change{restored}
        }catch(e:CancellationException){throw e}catch(e:Exception){valid=false;_error.value="download_storage"}
    }
    private suspend fun change(block:(BatchQueueState)->BatchQueueState){gate.withLock{
        val next=block(_state.value)
        withContext(Dispatchers.IO){val out=file.startWrite();try{out.write(BatchQueueCodec.encode(next).toByteArray());file.finishWrite(out)}catch(e:Exception){file.failWrite(out);throw e}}
        _state.value=next
    }}
    private suspend fun update(id:String,block:(BatchTask)->BatchTask)=change{s->s.copy(tasks=s.tasks.map{if(it.id==id)block(it)else it})}
    private fun command(block:suspend()->Unit){scope.launch{ready.await();if(!valid)return@launch;try{_error.value=null;block()}catch(e:CancellationException){throw e}catch(e:Exception){_error.value="download_storage"}}}
    private fun start(){try{if(startService!=null)startService.invoke()else ContextCompat.startForegroundService(context,Intent(context,BatchDownloadService::class.java))}catch(e:Exception){_error.value="start"}}
    fun foreground(){command{if(state.value.pending && !state.value.paused)start()}}
    fun enqueue(posts:List<Post>){command{
        val batch=UUID.randomUUID().toString()
        change{s->
            val additions=posts.filter{it.isVideo}.distinctBy{it.id}.take(OfflineLibraryMedia.BATCH_SIZE)
                .filter{p->s.tasks.none{it.post.fileUrl==p.fileUrl && it.status in setOf(BatchStatus.WAITING,BatchStatus.DOWNLOADING,BatchStatus.PAUSED)}}
                .map{BatchTask(UUID.randomUUID().toString(),batch,it,status=if(s.paused)BatchStatus.PAUSED else BatchStatus.WAITING)}
            val active=s.tasks.filter{it.status in setOf(BatchStatus.WAITING,BatchStatus.DOWNLOADING,BatchStatus.PAUSED)}
            require(active.size+additions.size<=100)
            s.copy(tasks=s.tasks.filterNot{it in active}.takeLast((BatchQueueCodec.MAX_TASKS-active.size-additions.size).coerceAtLeast(0))+active+additions)
        }
        if(!state.value.paused)start()
    }}
    fun parallelism(value:Int){require(value in 1..3);command{change{it.copy(parallelism=value)}}}
    fun pause(){command{change{s->s.copy(paused=true,tasks=s.tasks.map{if(it.status==BatchStatus.WAITING || it.status==BatchStatus.DOWNLOADING)it.copy(status=BatchStatus.PAUSED)else it})};workers.values.toList().forEach{it.cancel()}}}
    fun resume(){command{change{s->s.copy(paused=false,tasks=s.tasks.map{if(it.status==BatchStatus.PAUSED)it.copy(status=BatchStatus.WAITING,bytes=0)else it})};start()}}
    fun cancel(id:String?=null){command{change{s->s.copy(tasks=s.tasks.map{if((id==null || it.id==id) && it.status in setOf(BatchStatus.WAITING,BatchStatus.DOWNLOADING,BatchStatus.PAUSED))it.copy(status=BatchStatus.CANCELLED)else it})};workers.filterKeys{id==null || it==id}.values.toList().forEach{it.cancel()}}}
    fun retry(id:String){command{update(id){if(it.status==BatchStatus.FAILED || it.status==BatchStatus.CANCELLED)it.copy(status=if(state.value.paused)BatchStatus.PAUSED else BatchStatus.WAITING,bytes=0,total= -1,attempts=0,after=0,error=null)else it};if(!state.value.paused)start()}}
    /** Called once by the foreground service. Cancellation leaves interrupted files queued. */
    suspend fun run()=coroutineScope {
        ready.await();if(!valid)return@coroutineScope
        state.value.tasks.filter{it.after>System.currentTimeMillis()}.forEach{DownloadBackoff.defer(VideoDownloadPlan.from(it.post).url.host,it.after)}
        try {while(isActive){
            val current=state.value
            if(current.paused)break
            val free=(current.parallelism-workers.size).coerceAtLeast(0)
            val next=current.tasks.filter{it.status==BatchStatus.WAITING && it.id !in workers && it.after<=System.currentTimeMillis()}.take(free)
            next.forEach{task->
                var claimed=false
                change{s->s.copy(tasks=s.tasks.map{if(it.id==task.id && it.status==BatchStatus.WAITING && !s.paused){claimed=true;it.copy(status=BatchStatus.DOWNLOADING,bytes=0,total= -1,attempts=it.attempts+1,error=null)}else it})}
                if(claimed)workers[task.id]=launch(start=CoroutineStart.LAZY){transfer(task)}.also{it.start()}
            }
            if(workers.isEmpty() && !state.value.pending)break
            delay(250)
        }} finally {withContext(NonCancellable){workers.values.toList().forEach{it.cancel()};workers.values.toList().forEach{it.join()};workers.clear()
            change{s->s.copy(tasks=s.tasks.map{if(it.status==BatchStatus.DOWNLOADING)it.copy(status=BatchStatus.WAITING,bytes=0)else it})}}}
    }
    override fun close(){workers.values.toList().forEach{it.cancel()};scope.cancel()}
    private suspend fun transfer(task:BatchTask){
        try {
            var last=0L
            val legacy=withContext(Dispatchers.IO){OfflineLibraryMedia(context).existing(task.post)}
            if(legacy==null)storage.download(task.post,DownloadStorage.Kind.BATCH){bytes,total->
                val now=System.nanoTime();if(now-last>200_000_000 || bytes==total){last=now;scope.launch{gate.withLock{_state.value=state.value.let{s->s.copy(tasks=s.tasks.map{if(it.id==task.id && it.status==BatchStatus.DOWNLOADING)it.copy(bytes=bytes,total=total)else it})}}}}
            }
            update(task.id){it.copy(status=BatchStatus.COMPLETE,error=null)}
        }catch(e:CancellationException){throw e}catch(e:Exception){
            val http=generateSequence<Throwable>(e){it.cause}.filterIsInstance<MediaHttpFailure>().firstOrNull()
            val attempts=state.value.tasks.first{it.id==task.id}.attempts
            val retryable=http?.status in setOf(421,429,500,502,503,504) || (e is DataFailure && http==null && e.message?.contains("network",true)==true)
            val wait=if(retryable && attempts<3)maxOf(http?.retryAfterMs ?: 0,DownloadBackoff.retryDelay(http?.status ?: 0,attempts,null))else 0
            val now=System.currentTimeMillis();val after=now+minOf(wait,Long.MAX_VALUE-now)
            if(wait>0)DownloadBackoff.defer(VideoDownloadPlan.from(task.post).url.host,after)
            update(task.id){if(it.status!=BatchStatus.DOWNLOADING)it else it.copy(status=if(wait>0)BatchStatus.WAITING else BatchStatus.FAILED,bytes=0,after=after,error=(e as? LibraryFailure)?.code ?: if(http!=null)"HTTP ${http.status}" else "download_storage")}
        }finally{workers.remove(task.id)}
    }
}
