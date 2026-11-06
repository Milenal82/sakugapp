// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract as Docs
import android.provider.MediaStore
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.util.regex.Pattern

@RunWith(AndroidJUnit4::class)
class DownloadFoldersDeviceTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private val resolver get()=context.contentResolver
    private val device get()=UiDevice.getInstance(instrumentation)
    private fun find(s:BySelector)=checkNotNull(device.wait(Until.findObject(s),15000)){"Missing picker control $s"}
    private fun picker(kind:DownloadStorage.Kind,name:String,storage:DownloadStorage){
        device.executeShellCommand("mkdir -p /sdcard/Download/$name")
        ui.onAllNodesWithText(ui.activity.getString(R.string.ui_change_directory))[kind.ordinal].performScrollTo().performClick()
        device.wait(Until.findObject(By.desc("Show roots")),3000)?.click()
        device.wait(Until.findObject(By.text("Downloads")),3000)?.click()
        if(device.wait(Until.findObject(By.text(name)),3000)==null){
            val root=By.text(Pattern.compile("(?i)(internal storage|.*sdk.*|.*emulator.*|.*phone.*)"))
            if(device.findObject(root)==null)device.findObject(By.desc("Show roots"))?.click()
            find(root).click();device.wait(Until.findObject(By.text("Download")),3000)?.click()
        }
        find(By.text(name)).click();val choose=device.wait(Until.findObject(By.res("com.android.documentsui:id/action_menu_select")),3000)
            ?: find(By.text(Pattern.compile("(?i)(use this folder|select|select folder)")))
        choose.click();device.wait(Until.findObject(By.text(Pattern.compile("(?i)allow"))),2000)?.click()
        ui.waitUntil(15000){storage.directory(kind)?.toString()?.contains(name)==true}
    }
    @Test fun safFoldersPersistTransfersCancelFailuresAndLegacyExportAreSafe()=runBlocking{
        lateinit var app:com.sakugapp.ui.AppViewModel;ui.runOnUiThread{app=ViewModelProvider(ui.activity)[com.sakugapp.ui.AppViewModel::class.java]}
        val image=PostParser.posts(instrumentation.context.assets.open("library-real-posts.json").bufferedReader().use{it.readText()}).first().copy(fileExt="jpg")
        assertNull("Image favorites are not video downloads",app.offlineUri(image))
        val single="SakugappSingleTest";val batch="SakugappBatchTest";val clips="SakugappClipTest"
        ui.onNodeWithTag("main-menu").performClick();ui.onNodeWithText("Settings").performClick()
        picker(DownloadStorage.Kind.SINGLE,single,app.downloadStorage);picker(DownloadStorage.Kind.BATCH,batch,app.downloadStorage);picker(DownloadStorage.Kind.CLIP,clips,app.downloadStorage)
        val storedClip=app.downloadStorage.directory(DownloadStorage.Kind.CLIP)!!
        val storedSingle=app.downloadStorage.directory(DownloadStorage.Kind.SINGLE)!!;val storedBatch=app.downloadStorage.directory(DownloadStorage.Kind.BATCH)!!
        ui.activityRule.scenario.recreate()
        val reopened=DownloadStorage(context);assertEquals(storedClip,reopened.directory(DownloadStorage.Kind.CLIP));assertEquals(storedSingle,reopened.directory(DownloadStorage.Kind.SINGLE));assertEquals(storedBatch,reopened.directory(DownloadStorage.Kind.BATCH))
        assertTrue(resolver.persistedUriPermissions.any{it.uri==storedSingle && it.isReadPermission && it.isWritePermission})
        val bytes=instrumentation.context.assets.open("frame-index-vfr.mp4").use{it.readBytes()}
        val post=PostParser.posts(instrumentation.context.assets.open("library-real-posts.json").bufferedReader().use{it.readText()}).first()
        val cert=HeldCertificate.Builder().addSubjectAlternativeName("localhost").build();val trust=HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate).build()
        val server=MockWebServer();server.useHttps(HandshakeCertificates.Builder().heldCertificate(cert).build().sslSocketFactory(),false);server.start()
        val client=OkHttpClient.Builder().sslSocketFactory(trust.sslSocketFactory(),trust.trustManager).retryOnConnectionFailure(false).build()
        val storage=DownloadStorage(context,VideoDownloader(client))
        val created=mutableListOf<Uri>()
        fun variant(path:String)=post.copy(fileUrl=server.url(path).toString(),fileExt="mp4",fileSize=bytes.size.toLong(),source="episode 12",tags=listOf(Tag("test_artist",TagCategory.ARTIST)))
        fun response()=MockResponse().setHeader("Content-Type","video/mp4").setBody(Buffer().write(bytes))
        fun contents(tree:Uri):Set<String> = resolver.query(Docs.buildChildDocumentsUriUsingTree(tree,Docs.getTreeDocumentId(tree)),arrayOf(Docs.Document.COLUMN_DOCUMENT_ID),null,null,null)!!.use{cursor->buildSet{while(cursor.moveToNext())add(cursor.getString(0))}}
        try{
            for(kind in listOf(DownloadStorage.Kind.SINGLE,DownloadStorage.Kind.BATCH)){val value=variant("/${kind.name}.mp4");server.enqueue(response());val uri=storage.download(value,kind);created+=uri
                assertTrue(uri.toString().contains(if(kind==DownloadStorage.Kind.SINGLE)single else batch));assertArrayEquals(bytes,resolver.openInputStream(uri)!!.use{it.readBytes()})
                resolver.query(uri,arrayOf(Docs.Document.COLUMN_DISPLAY_NAME),null,null,null)!!.use{assertTrue(it.moveToFirst());assertEquals(VideoDownloadPlan.from(value).filename,it.getString(0))}
                val count=server.requestCount;assertEquals(uri,storage.download(value,kind));assertEquals(count,server.requestCount)
            }
            server.enqueue(response());val collision=storage.download(variant("/collision.mp4"),DownloadStorage.Kind.SINGLE);created+=collision
            assertNotEquals(created.first(),collision);assertArrayEquals(bytes,resolver.openInputStream(created.first())!!.use{it.readBytes()})
            val clipFile=File(context.cacheDir,"save-clip.mp4").apply{writeBytes(bytes)}
            try{val name=MediaFileNames.forClip(post,"mp4",ClipRange(400,800),ClipMode.PRECISE)
                val one=storage.saveClip(clipFile,name,"video/mp4");val two=storage.saveClip(clipFile,name,"video/mp4");created+=one;created+=two
                assertTrue(one.toString().contains(clips));assertNotEquals(one,two);assertArrayEquals(bytes,resolver.openInputStream(one)!!.use{it.readBytes()})
                val beforeClips=contents(storedClip)
                val fullClip=DownloadStorage(context,output={throw IOException("ENOSPC: No space left")})
                try{fullClip.saveClip(clipFile,name,"video/mp4");fail("Full destination must fail")}catch(e:LibraryFailure){assertEquals("storage_full",e.code)}
                assertEquals(beforeClips,contents(storedClip))
                val startedClip=CompletableDeferred<Unit>();val large=File(context.cacheDir,"cancel-clip.mp4").apply{writeBytes(ByteArray(256*1024))}
                try{val slow=DownloadStorage(context,output={uri->object:java.io.FilterOutputStream(resolver.openOutputStream(uri,"w")!!){override fun write(b:ByteArray,o:Int,n:Int){out.write(b,o,n);startedClip.complete(Unit);Thread.sleep(100)}}})
                    val cancel=launch{slow.saveClip(large,"cancel-clip.mp4","video/mp4")};withTimeout(10000){startedClip.await()};cancel.cancelAndJoin();assertEquals(beforeClips,contents(storedClip));assertTrue(large.exists())
                }finally{large.delete()}
                assertEquals(storedSingle,storage.directory(DownloadStorage.Kind.SINGLE));assertEquals(storedBatch,storage.directory(DownloadStorage.Kind.BATCH))
            }finally{clipFile.delete()}
            val before=contents(storedSingle)
            server.enqueue(response().throttleBody(512,1,java.util.concurrent.TimeUnit.SECONDS));val started=CompletableDeferred<Unit>()
            val task=launch{storage.download(variant("/cancel.mp4"),DownloadStorage.Kind.SINGLE){n,_->if(n>0)started.complete(Unit)}}
            withTimeout(15000){started.await()};task.cancelAndJoin();withTimeout(10000){while(contents(storedSingle)!=before)delay(50)}
            server.enqueue(response());val full=DownloadStorage(context,VideoDownloader(client)){throw IOException("ENOSPC: No space left")}
            try{full.download(variant("/full.mp4"),DownloadStorage.Kind.SINGLE);fail("Must report storage full")}catch(e:LibraryFailure){assertEquals("storage_full",e.code)}
            assertEquals(before,contents(storedSingle))
            val legacy=File(context.filesDir,"favorites-media/old_export_fixture.mp4").apply{parentFile!!.mkdirs();writeBytes(bytes)}
            try{val requests=server.requestCount;assertTrue(storage.exportLegacy(legacy));assertFalse(storage.exportLegacy(legacy));assertArrayEquals(bytes,legacy.readBytes());assertEquals(requests,server.requestCount)}finally{legacy.delete()}
            resolver.releasePersistableUriPermission(storedSingle,Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            try{storage.download(variant("/revoked.mp4"),DownloadStorage.Kind.SINGLE);fail("Must require reauthorization")}catch(e:LibraryFailure){assertEquals("directory_access",e.code)}
            assertTrue(storage.existing(variant("/BATCH.mp4"),DownloadStorage.Kind.BATCH)!=null)
        }finally{created.forEach{runCatching{Docs.deleteDocument(resolver,it)}};storage.reset(DownloadStorage.Kind.SINGLE);storage.reset(DownloadStorage.Kind.BATCH);storage.reset(DownloadStorage.Kind.CLIP);server.shutdown();client.connectionPool.evictAll();client.dispatcher.executorService.shutdown()}
    }
    @Test fun unsetDestinationOpensTheSystemPickerAndCancellationRetainsLegacyFiles()=runBlocking{
        val storage=DownloadStorage(context);DownloadStorage.Kind.entries.forEach(storage::reset)
        val file=File(context.filesDir,"favorites-media/picker-cancel.mp4").apply{parentFile!!.mkdirs();writeBytes(instrumentation.context.assets.open("frame-index-vfr.mp4").use{it.readBytes()})}
        try{
            ui.onNodeWithTag("main-menu").performClick();ui.onNodeWithText("Settings").performClick()
            ui.onNodeWithText(ui.activity.getString(R.string.ui_export_legacy)).performScrollTo().performClick()
            assertTrue(device.wait(Until.hasObject(By.pkg("com.android.documentsui")),10000))
            device.pressBack();ui.waitForIdle();assertNull(storage.directory(DownloadStorage.Kind.BATCH));assertTrue(file.exists())
        }finally{file.delete()}
    }
}
