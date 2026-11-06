// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import android.os.Build
import android.provider.MediaStore
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
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
    @Test fun twoActualTransfersDeduplicateRetryCancelAndRecoverTaskJournal()=runBlocking{
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        if(Build.VERSION.SDK_INT<29)UiDevice.getInstance(instrumentation).executeShellCommand("pm grant com.sakugapp android.permission.WRITE_EXTERNAL_STORAGE")
        val cert=HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val trust=HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate).build()
        val client=OkHttpClient.Builder().sslSocketFactory(trust.sslSocketFactory(),trust.trustManager).retryOnConnectionFailure(false).build()
        val server=MockWebServer().apply{useHttps(HandshakeCertificates.Builder().heldCertificate(cert).build().sslSocketFactory(),false);start()}
        val storage=DownloadStorage(context,VideoDownloader(client));storage.reset(DownloadStorage.Kind.BATCH)
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
        }finally{
            runner?.cancelAndJoin();withContext(Dispatchers.Main){runCatching{queue.close()}};serviceScope.cancel()
            for(id in 901L..904L)storage.existing(post(id),DownloadStorage.Kind.BATCH)?.let{context.contentResolver.delete(it,null,null)}
            File(context.filesDir,journal).delete();server.shutdown();client.connectionPool.evictAll();client.dispatcher.executorService.shutdown()
        }
    }
}
