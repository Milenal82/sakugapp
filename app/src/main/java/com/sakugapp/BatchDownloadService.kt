// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import android.app.*
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.sakugapp.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.sample

/** Data-sync foreground lifetime, with persistent task-level recovery on the next app visit. */
@OptIn(kotlinx.coroutines.FlowPreview::class)
class BatchDownloadService:Service(){
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val queue get()=(application as SakugappApplication).batchDownloads
    private var worker:Job?=null
    override fun onCreate(){super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("batch-downloads",getString(R.string.ui_download_queue),NotificationManager.IMPORTANCE_LOW))
        startForeground(71,notification(BatchQueueState()))
        scope.launch{queue.state.sample(1000).collect{getSystemService(NotificationManager::class.java).notify(71,notification(it))}}
    }
    private fun notification(state:BatchQueueState):Notification {
        val complete=state.tasks.count{it.status==BatchStatus.COMPLETE};val all=state.tasks.size
        val open=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop=PendingIntent.getService(this,1,Intent(this,javaClass).setAction("pause"),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this,"batch-downloads").setSmallIcon(R.drawable.ic_download).setContentTitle(getString(R.string.ui_download_queue))
            .setContentText("$complete / $all").setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .setProgress(all.coerceAtLeast(1),complete,all==0).addAction(R.drawable.ic_pause,getString(R.string.ui_pause_batch),stop).build()
    }
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        if(intent?.action=="pause"){queue.pause();worker?.cancel();stopSelf();return START_NOT_STICKY}
        if(worker?.isActive!=true)worker=scope.launch{try{do{queue.run();yield()}while(isActive && queue.state.value.pending && !queue.state.value.paused)}catch(e:CancellationException){throw e}catch(e:Exception){queue.pause()}finally{stopForeground(STOP_FOREGROUND_REMOVE);stopSelf()}}
        return START_REDELIVER_INTENT
    }
    override fun onTimeout(startId:Int,fgsType:Int){queue.pause();worker?.cancel();stopForeground(STOP_FOREGROUND_REMOVE);stopSelf()}
    override fun onDestroy(){scope.cancel();super.onDestroy()}
    override fun onBind(intent:Intent?):IBinder?=null
}
