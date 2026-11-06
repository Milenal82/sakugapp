// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import android.provider.DocumentsContract as Docs
import androidx.compose.ui.test.*
import android.provider.MediaStore
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import com.sakugapp.data.*
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.mockwebserver.*
import okhttp3.tls.*
import okio.Buffer
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.concurrent.TimeUnit

class BatchQueueDeviceTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val context get()=ui.activity.applicationContext
    private fun selectBatchDirectory(storage:DownloadStorage){
        val device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val directory="SakugappBatchQueueTest"
        device.executeShellCommand("mkdir -p /sdcard/Download/$directory")
        ui.onNodeWithText("Menu").performClick();ui.onNodeWithText("Settings").performClick()
        ui.onAllNodesWithText(ui.activity.getString(R.string.ui_change_directory))[DownloadStorage.Kind.BATCH.ordinal].performScrollTo().performClick()
        device.wait(Until.findObject(By.desc("Show roots")),3000)?.click()
        device.wait(Until.findObject(By.text("Downloads")),3000)?.click()
        if(device.wait(Until.findObject(By.text(directory)),3000)==null){
            val root=By.text(java.util.regex.Pattern.compile("(?i)(internal storage|.*sdk.*|.*emulator.*|.*phone.*)"))
            if(device.findObject(root)==null)device.findObject(By.desc("Show roots"))?.click()
            checkNotNull(device.wait(Until.findObject(root),10000)).click()
            device.wait(Until.findObject(By.text("Download")),3000)?.click()
        }
        checkNotNull(device.wait(Until.findObject(By.text(directory)),10000)).click()
        val choose=device.wait(Until.findObject(By.res("com.android.documentsui:id/action_menu_select")),3000)
            ?: device.wait(Until.findObject(By.text(java.util.regex.Pattern.compile("(?i)(use this folder|select|select folder)"))),10000)
        checkNotNull(choose).click();device.wait(Until.findObject(By.text(java.util.regex.Pattern.compile("(?i)allow"))),2000)?.click()
        ui.waitUntil(15000){storage.directory(DownloadStorage.Kind.BATCH)?.toString()?.contains(directory)==true}
    }
    @Test fun productionForegroundServiceKeepsDownloadingAfterActivityGoesToBackground()=runBlocking{
        val instrumentation=InstrumentationRegistry.getInstrumentation();val device=UiDevice.getInstance(instrumentation)
        if(android.os.Build.VERSION.SDK_INT>=33){device.executeShellCommand("pm grant com.sakugapp android.permission.POST_NOTIFICATIONS");assertEquals(android.content.pm.PackageManager.PERMISSION_GRANTED,context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS))}
        val storage=DownloadStorage(context);storage.reset(DownloadStorage.Kind.BATCH);selectBatchDirectory(storage)
        val original=PostParser.posts(instrumentation.context.assets.open("library-real-posts.json").bufferedReader().use{it.readText()}).first()
        val expected=instrumentation.context.assets.open("clip-gop.mp4").use{it.readBytes()}
        val source="https://raw.githubusercontent.com/Milenal82/sakugapp/7fcc30a5519d2943fc7948da4af33c282c927c5b/app/src/androidTest/assets/clip-gop.mp4"
        val posts=listOf(original.copy(id=906,fileUrl=source,fileExt="mp4",fileSize=expected.size.toLong()),original.copy(id=907,fileUrl="$source?download=1",fileExt="mp4",fileSize=expected.size.toLong()))
        val queue=(context as SakugappApplication).batchDownloads
        try{
            withContext(Dispatchers.Main){queue.enqueue(posts)}
            val notifications=context.getSystemService(android.app.NotificationManager::class.java)
            withTimeout(30000){while(notifications.activeNotifications.none{it.id==71})delay(10)}
            assertTrue(notifications.activeNotifications.first{it.id==71}.notification.flags and android.app.Notification.FLAG_ONGOING_EVENT!=0)
            withContext(Dispatchers.Main){assertTrue(ui.activity.moveTaskToBack(true))}
            withTimeout(30000){while(posts.any{p->queue.state.value.tasks.none{it.post.id==p.id && it.status==BatchStatus.COMPLETE}})delay(25)}
            for(post in posts){val uri=checkNotNull(storage.existing(post,DownloadStorage.Kind.BATCH));assertArrayEquals(expected,context.contentResolver.openInputStream(uri)!!.use{it.readBytes()})}
            withTimeout(10000){while(notifications.activeNotifications.any{it.id==71})delay(25)}
        }finally{
            withContext(Dispatchers.Main){posts.forEach{post->queue.state.value.tasks.firstOrNull{it.post.id==post.id}?.let{queue.cancel(it.id)}}}
            device.executeShellCommand("am start -f 0x00020000 -n com.sakugapp/.MainActivity")
            posts.forEach{post->storage.existing(post,DownloadStorage.Kind.BATCH)?.let{Docs.deleteDocument(context.contentResolver,it)}}
            storage.reset(DownloadStorage.Kind.BATCH)
        }
    }
    @Test fun twoActualTransfersDeduplicateRetryCancelAndRecoverTaskJournal()=runBlocking{
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val cert=HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val trust=HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate).build()
        val client=OkHttpClient.Builder().sslSocketFactory(trust.sslSocketFactory(),trust.trustManager).retryOnConnectionFailure(false).build()
        val server=MockWebServer().apply{useHttps(HandshakeCertificates.Builder().heldCertificate(cert).build().sslSocketFactory(),false);start()}
        val storage=DownloadStorage(context,VideoDownloader(client));storage.reset(DownloadStorage.Kind.BATCH)
        selectBatchDirectory(storage)
        val snapshot=PostParser.posts(instrumentation.context.assets.open("library-real-posts.json").bufferedReader().use{it.readText()}).first()
        val bytes=ByteArray(128*1024){(it%255).toByte()}
        val baseUrl=server.url("/").toString()
        fun post(id:Long)=snapshot.copy(id=id,fileUrl="${baseUrl}$id.mp4",fileExt="mp4",fileSize=bytes.size.toLong())
        val journal="queue-device-${System.nanoTime()}.json"
        lateinit var queue:BatchDownloadQueue;var runner:Job?=null
        suspend fun stage(name:String,test:()->Boolean){
            try{withTimeout(30000){while(!test())delay(25)}}catch(e:TimeoutCancellationException){
                val details=queue.state.value.tasks.joinToString{"${it.post.id}:${it.status}:${it.bytes}:${it.error}"}
                val disk=runCatching{BatchQueueCodec.decode(File(context.filesDir,journal).readText()).tasks.size.toString()}.getOrElse{"${it.javaClass.simpleName}:${it.message}"}
                throw AssertionError("$name: requests=${server.requestCount}; error=${queue.error.value}; tasks=$details; journal=$disk",e)
            }
        }
        val serviceScope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
        fun response()=MockResponse().setHeader("Content-Type","video/mp4").setBody(Buffer().write(bytes)).setBodyDelay(3,TimeUnit.SECONDS)
        try{
            withContext(Dispatchers.Main){queue=BatchDownloadQueue(context,storage,journal){};queue.enqueue(listOf(post(901),post(902),post(901)))}
            stage("admit first batch"){queue.state.value.tasks.size==2};assertEquals(2,queue.state.value.parallelism)
            server.enqueue(response());server.enqueue(response())
            withContext(Dispatchers.Main){runner=serviceScope.launch{queue.run()}}
            stage("two simultaneous requests"){server.requestCount==2}
            assertEquals("A global transfer mutex must not serialize the two requests",2,queue.state.value.tasks.count{it.status==BatchStatus.DOWNLOADING})
            stage("complete first batch"){queue.state.value.tasks.all{it.status==BatchStatus.COMPLETE}}
            val one=checkNotNull(storage.existing(post(901),DownloadStorage.Kind.BATCH));val two=checkNotNull(storage.existing(post(902),DownloadStorage.Kind.BATCH))
            assertArrayEquals(bytes,context.contentResolver.openInputStream(one)!!.use{it.readBytes()});assertArrayEquals(bytes,context.contentResolver.openInputStream(two)!!.use{it.readBytes()})
            runner!!.join()
            // A failed file remains retryable without removing successful media.
            server.enqueue(MockResponse().setHeader("Content-Type","video/mp4").setBody(Buffer().write(bytes)).setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY))
            withContext(Dispatchers.Main){queue.enqueue(listOf(post(903)))};stage("admit failure"){queue.state.value.tasks.size==3}
            withContext(Dispatchers.Main){runner=serviceScope.launch{queue.run()}}
            stage("record failure"){queue.state.value.tasks.last().status==BatchStatus.FAILED};runner!!.join()
            server.enqueue(response());withContext(Dispatchers.Main){queue.retry(queue.state.value.tasks.last().id)}
            stage("retry pending"){queue.state.value.tasks.last().status==BatchStatus.WAITING};withContext(Dispatchers.Main){runner=serviceScope.launch{queue.run()}}
            stage("retry complete"){queue.state.value.tasks.last().status==BatchStatus.COMPLETE};runner!!.join();assertNotNull(storage.existing(post(901),DownloadStorage.Kind.BATCH))
            server.enqueue(response().throttleBody(512,1,TimeUnit.SECONDS))
            withContext(Dispatchers.Main){queue.enqueue(listOf(post(904)))};stage("admit cancellation"){queue.state.value.tasks.size==4}
            withContext(Dispatchers.Main){runner=serviceScope.launch{queue.run()}}
            stage("receive partial bytes"){queue.state.value.tasks.last().bytes>0}
            withContext(Dispatchers.Main){queue.pause()};stage("pause persisted"){queue.state.value.paused};runner!!.join()
            assertNull(storage.existing(post(904),DownloadStorage.Kind.BATCH));assertNotNull(storage.existing(post(902),DownloadStorage.Kind.BATCH))
            withContext(Dispatchers.Main){queue.parallelism(3)};stage("parallelism persisted"){queue.state.value.parallelism==3};withContext(Dispatchers.Main){queue.close()}
            withContext(Dispatchers.Main){queue=BatchDownloadQueue(context,storage,journal){}}
            stage("restore journal"){queue.state.value.tasks.size==4};assertTrue(queue.state.value.paused);assertEquals(3,queue.state.value.parallelism);assertEquals(BatchStatus.PAUSED,queue.state.value.tasks.last().status)
            withContext(Dispatchers.Main){queue.cancel(queue.state.value.tasks.last().id)};stage("cancel restored task"){queue.state.value.tasks.last().status==BatchStatus.CANCELLED}
            assertNotNull(storage.existing(post(901),DownloadStorage.Kind.BATCH))
            // Completed receipts, not temporary output documents, determine offline readiness.
            assertNull(storage.existing(post(904),DownloadStorage.Kind.BATCH))
            server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After","1"));server.enqueue(response().setBodyDelay(0,TimeUnit.SECONDS))
            val before=android.os.SystemClock.elapsedRealtime();val requests=server.requestCount
            withContext(Dispatchers.Main){queue.resume();queue.enqueue(listOf(post(905)))}
            stage("rate-limit task admitted"){queue.state.value.tasks.size==5}
            withContext(Dispatchers.Main){runner=serviceScope.launch{queue.run()}}
            stage("rate-limit recovery"){queue.state.value.tasks.last().status==BatchStatus.COMPLETE};runner!!.join()
            assertTrue(android.os.SystemClock.elapsedRealtime()-before>=5000);assertEquals(requests+2,server.requestCount)

        }finally{
            runner?.cancelAndJoin();withContext(Dispatchers.Main){runCatching{queue.close()}};serviceScope.cancel()
            for(id in 901L..905L)storage.existing(post(id),DownloadStorage.Kind.BATCH)?.let{Docs.deleteDocument(context.contentResolver,it)}
            storage.reset(DownloadStorage.Kind.BATCH);File(context.filesDir,journal).delete();server.shutdown();client.connectionPool.evictAll();client.dispatcher.executorService.shutdown()
        }
    }
}
