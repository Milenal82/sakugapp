// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.data

import com.google.gson.Gson
import com.google.gson.JsonParser

enum class BatchStatus { WAITING, DOWNLOADING, COMPLETE, FAILED, CANCELLED, PAUSED }
data class BatchTask(val id:String,val batch:String,val post:Post,val status:BatchStatus=BatchStatus.WAITING,
    val bytes:Long=0,val total:Long = -1,val attempts:Int=0,val after:Long=0,val error:String?=null)
data class BatchQueueState(val format:String="sakugapp-download-queue",val schemaVersion:Int=1,
    val parallelism:Int=2,val paused:Boolean=false,val tasks:List<BatchTask> = emptyList()) {
    val pending get()=tasks.any{it.status==BatchStatus.WAITING || it.status==BatchStatus.DOWNLOADING}
}
/** Private task journal, separate from all favorites, media receipts and backup formats. */
object BatchQueueCodec {
    const val MAX_TASKS=200
    fun encode(state:BatchQueueState):String=Gson().toJson(state).also{require(it.length<=4*1024*1024)}
    fun decode(text:String):BatchQueueState {
        require(text.length<=4*1024*1024)
        val json=JsonParser.parseString(text).asJsonObject
        require(json["format"]?.asString=="sakugapp-download-queue" && json["schemaVersion"]?.asInt==1)
        val state=Gson().fromJson(json,BatchQueueState::class.java)
        require(state.parallelism in 1..3 && state.tasks.size<=MAX_TASKS)
        require(state.tasks.map{it.id}.distinct().size==state.tasks.size)
        state.tasks.forEach{require(it.id.length in 1..80 && it.batch.length in 1..80 && it.bytes>=0 && it.attempts in 0..100);require((it.status as BatchStatus?)!=null);VideoDownloadPlan.from(it.post)}
        return state
    }
    fun recover(state:BatchQueueState)=state.copy(tasks=state.tasks.map{if(it.status==BatchStatus.DOWNLOADING)it.copy(status=if(state.paused)BatchStatus.PAUSED else BatchStatus.WAITING,bytes=0,total= -1)else it})
}
