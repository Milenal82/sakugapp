// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sakugapp.data.*
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.mockwebserver.*
import okhttp3.tls.*
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.RandomAccessFile
import java.net.InetAddress

/** Controlled TLS fixture transfer only. No website downloads or relaxed TLS in the production client. */
@RunWith(AndroidJUnit4::class)
class OfflineLibraryDeviceTest {
    @Test fun completedPrivateDownloadMatchesBytesWhileBrokenAndOverQuotaTransfersLeaveNoFakeOfflineEntry()=runBlocking{
        val instrumentation=InstrumentationRegistry.getInstrumentation();val context=instrumentation.targetContext
        val data=instrumentation.context.assets.open("frame-index-vfr.mp4").use{it.readBytes()}
        val original=PostParser.posts(instrumentation.context.assets.open("library-real-posts.json").bufferedReader().use{it.readText()}).first()
        val root=File(context.cacheDir,"offline-transfer-${System.nanoTime()}").apply{mkdirs()}
        val certificate=HeldCertificate.Builder().addSubjectAlternativeName("www.sakugabooru.com").build()
        val serverTLS=HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientTLS=HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        MockWebServer().use{server->
            server.useHttps(serverTLS.sslSocketFactory(),false);server.start()
            val client=OkHttpClient.Builder().sslSocketFactory(clientTLS.sslSocketFactory(),clientTLS.trustManager)
                .dns(object:Dns{override fun lookup(hostname:String):List<InetAddress> = if(hostname=="www.sakugabooru.com")listOf(InetAddress.getByName("127.0.0.1"))else Dns.SYSTEM.lookup(hostname)}).followRedirects(false).retryOnConnectionFailure(false).build()
            val store=OfflineLibraryMedia(context,VideoDownloader(client),root)
            fun post(path:String)=original.copy(fileUrl=server.url(path).newBuilder().host("www.sakugabooru.com").build().toString(),fileSize=data.size.toLong(),fileExt="mp4")
            try{
                val good=post("/complete.mp4")
                server.enqueue(MockResponse().setHeader("Content-Type","video/mp4").setBody(Buffer().write(data)))
                val file=store.download(good);assertArrayEquals(data,file.readBytes())
                assertEquals(VideoDownloadPlan.from(good).filename,file.name)
                assertEquals(file,store.existing(good.copy(source="different source",tags=emptyList())))
                val legacyRoot=File(root,"legacy").apply{mkdirs()}
                val hash=java.security.MessageDigest.getInstance("SHA-256").digest(good.fileUrl!!.toByteArray()).take(12).joinToString(""){"%02x".format(it)}
                val legacy=File(legacyRoot,"${good.id}-$hash.mp4").apply{writeBytes(data)}
                assertEquals(legacy,OfflineLibraryMedia(context,VideoDownloader(client),legacyRoot).existing(good))
                legacyRoot.deleteRecursively()
;assertEquals(file,store.existing(good));assertEquals(1,server.requestCount)
                store.download(good);assertEquals("Completed files must be reused",1,server.requestCount)
                val broken=post("/broken.mp4")
                server.enqueue(MockResponse().setHeader("Content-Type","video/mp4").setBody(Buffer().write(data)).setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY))
                try{store.download(broken);fail("Incomplete transfer must fail")}catch(_:DataFailure){}
                assertNull(store.existing(broken));assertTrue(root.listFiles().orEmpty().none{it.name.endsWith(".part")})
                val sparse=File(root,"quota-fixture")
                RandomAccessFile(sparse,"rw").use{it.setLength(OfflineLibraryMedia.MAX_TOTAL)}
                val requests=server.requestCount
                try{store.download(post("/over-quota.mp4"));fail("Quota must reject before networking")}catch(e:LibraryFailure){assertEquals("media_limit",e.code)}
                assertEquals(requests,server.requestCount);assertArrayEquals(data,file.readBytes())
                sparse.delete();store.clear();assertNull(store.existing(good))
            }finally{root.deleteRecursively();client.connectionPool.evictAll();client.dispatcher.executorService.shutdown()}
        }
    }
}
