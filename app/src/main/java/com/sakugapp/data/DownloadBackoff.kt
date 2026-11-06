// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.data

import java.io.IOException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.delay

class MediaHttpFailure(val status:Int,val retryAfterMs:Long):IOException("HTTP $status")
object DownloadBackoff {
    fun retryDelay(status:Int,attempt:Int,retryAfter:String?,now:Long=System.currentTimeMillis()):Long{
        val seconds=retryAfter?.trim()?.toLongOrNull()?.takeIf{it>=0}?.coerceAtMost(Long.MAX_VALUE/1000)?.times(1000)
        val date=runCatching{ZonedDateTime.parse(retryAfter,DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()-now}.getOrNull()?.coerceAtLeast(0)
        val base=(if(status==421 || status==429)5000L else 1000L)*(1L shl (attempt-1).coerceIn(0,8))
        return maxOf(base,seconds ?: date ?: 0)
    }
    fun serverCooldown(status:Int,attempt:Int,retryAfterMs:Long):Long=
        if(status in setOf(421,429,503))maxOf(retryAfterMs,retryDelay(status,attempt,null))else 0
    private val blocked=ConcurrentHashMap<String,Long>()
    fun defer(host:String,until:Long){blocked.merge(host,until,::maxOf)}
    fun remaining(host:String)=((blocked[host] ?: 0)-System.currentTimeMillis()).coerceAtLeast(0)
    suspend fun await(host:String){while(true){val remaining=remaining(host);if(remaining<=0)return;delay(minOf(remaining,1000))}}
}
