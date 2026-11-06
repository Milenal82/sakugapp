// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import com.sakugapp.data.*
import org.junit.Test
import org.junit.Assert.*

class BatchQueueTest {
    private fun post()=PostParser.posts(javaClass.getResource("/live/posts.json")!!.readText()).first().copy(fileUrl="https://example.org/video.webm",fileExt="webm")
    @Test fun privateJournalRecoversOnlyInterruptedTasksAndKeepsSettingsAndCompletedFiles(){
        val tasks=BatchStatus.entries.mapIndexed{i,status->BatchTask("t$i","batch",post().copy(id=i+1L),status,bytes=64,total=128)}
        val restored=BatchQueueCodec.recover(BatchQueueCodec.decode(BatchQueueCodec.encode(BatchQueueState(parallelism=3,tasks=tasks))))
        assertEquals(3,restored.parallelism);assertEquals(BatchStatus.WAITING,restored.tasks[1].status);assertEquals(0L,restored.tasks[1].bytes)
        assertEquals(BatchStatus.COMPLETE,restored.tasks[2].status);assertEquals(64L,restored.tasks[2].bytes)
        assertEquals(BatchStatus.FAILED,restored.tasks[3].status);assertEquals(BatchStatus.CANCELLED,restored.tasks[4].status)
        assertEquals(BatchStatus.PAUSED,BatchQueueCodec.recover(restored.copy(paused=true,tasks=listOf(tasks[1]))).tasks.single().status)
    }
    @Test fun malformedUnsupportedAndDuplicateJournalEntriesAreRejected(){
        val task=BatchTask("id","batch",post());val state=BatchQueueState(tasks=listOf(task))
        assertThrows(Exception::class.java){BatchQueueCodec.decode("{broken")}
        assertThrows(Exception::class.java){BatchQueueCodec.decode(BatchQueueCodec.encode(state.copy(schemaVersion=99)))}
        assertThrows(Exception::class.java){BatchQueueCodec.decode(BatchQueueCodec.encode(state.copy(tasks=listOf(task,task))))}
        assertThrows(Exception::class.java){BatchQueueCodec.decode(BatchQueueCodec.encode(state.copy(parallelism=4)))}
        assertThrows(Exception::class.java){BatchQueueCodec.decode(BatchQueueCodec.encode(state).replace("WAITING","UNKNOWN"))}
    }
    @Test fun exhaustedTaskStillBlocksItsHostAndHonorsServerRetryAfter(){
        val cooldown=DownloadBackoff.serverCooldown(429,3,120000)
        assertEquals(120000L,cooldown)
        val host="rate-limited.example.org";DownloadBackoff.defer(host,System.currentTimeMillis()+cooldown)
        assertTrue(DownloadBackoff.remaining(host) in 119000..120000)
        assertEquals(20000L,DownloadBackoff.serverCooldown(421,3,0))
        assertEquals(0L,DownloadBackoff.serverCooldown(403,3,0))
    }
    @Test fun rateLimitBackoffHonorsServerSecondsAndHttpDate(){
        assertEquals(5000L,DownloadBackoff.retryDelay(421,1,null));assertEquals(20000L,DownloadBackoff.retryDelay(429,3,null))
        assertEquals(120000L,DownloadBackoff.retryDelay(429,1,"120"))
        assertEquals(120000L,DownloadBackoff.retryDelay(429,1,"Thu, 01 Jan 1970 00:02:00 GMT",0))
        assertEquals(1000L,DownloadBackoff.retryDelay(503,1,"garbage"))
    }
}
