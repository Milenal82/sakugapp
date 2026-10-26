// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import androidx.activity.compose.setContent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import com.sakugapp.data.*
import com.sakugapp.ui.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Exercises the actual Android document picker, streams, confirmation and Room import. */
@RunWith(AndroidJUnit4::class)
class LibraryDocumentsDeviceTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    @Test fun exportAndImportThroughSystemDocumentsRestoreTheFolder(){
        val instrumentation=InstrumentationRegistry.getInstrumentation();val device=UiDevice.getInstance(instrumentation)
        val post=PostParser.posts(instrumentation.context.assets.open("library-real-posts.json").bufferedReader().use{it.readText()}).first()
        val folderName="JSON device ${System.nanoTime()}";val filename="sakugapp-ui-${System.nanoTime()}.json"
        lateinit var app:AppViewModel;var exportedPath:String?=null
        ui.runOnUiThread{app=ViewModelProvider(ui.activity)[AppViewModel::class.java]}
        try{
            runBlocking{val id=app.localLibrary.createFolder(folderName);app.localLibrary.membership(post,id,true)}
            ui.runOnUiThread{ui.activity.setContent{SakugappTheme(false){LibraryScreen(app){_,_->}}}}
            ui.onNodeWithText("Export JSON",substring=false).performClick()
            val name=device.wait(Until.findObject(By.clazz("android.widget.EditText")),15000)
            assertNotNull("System filename field",name);name.text=filename
            val save=device.wait(Until.findObject(By.text(java.util.regex.Pattern.compile("(?i)save"))),15000)
            assertNotNull("System save action",save);save.click()
            ui.waitUntil(15000){ui.onAllNodesWithText("Exported",substring=false).fetchSemanticsNodes().isNotEmpty()}
            exportedPath=device.executeShellCommand("find /sdcard/Download /sdcard/Documents -name $filename").lineSequence().firstOrNull{it.endsWith(filename) && it.startsWith("/")}
            assertNotNull("Exported JSON exists in shared storage: "+device.executeShellCommand("find /sdcard/Download -maxdepth 1 -name sakugapp-ui-*"),exportedPath)
            val body=device.executeShellCommand("cat $exportedPath")
            val decoded=LibraryCodec.decode(body)
            assertTrue(decoded.posts.any{it.id==post.id});assertTrue(decoded.folders.any{it.name==folderName})
            assertFalse(com.google.gson.JsonParser.parseString(body).asJsonObject.has("translations"))
            runBlocking{app.localLibrary.deleteFolder(app.library.value.folders.single{it.name==folderName}.id)}
            ui.waitUntil(10000){app.library.value.folders.none{it.name==folderName}}
            ui.onNodeWithText("Import JSON",substring=false).performClick()
            var document=device.wait(Until.findObject(By.text(filename)),10000)
            if(document==null){
                device.findObject(By.desc("Show roots"))?.click()
                device.wait(Until.findObject(By.text("Downloads")),10000)?.click()
                document=device.wait(Until.findObject(By.text(filename)),15000)
            }
            assertNotNull("Exported file is selectable",document);document!!.click()
            ui.onNodeWithText("Merge",substring=false).performClick()
            ui.waitUntil(15000){app.library.value.folders.firstOrNull{it.name==folderName}?.let{app.library.value.contains(post.id,it.id)}==true}
            assertEquals(1,app.library.value.folders.count{it.name==folderName})
            UiEvidence.capture("compact-json-imported.png")
        }finally{
            runBlocking{app.library.value.folders.firstOrNull{it.name==folderName}?.let{app.localLibrary.deleteFolder(it.id)}}
            exportedPath?.let{device.executeShellCommand("rm -f $it")}
        }
    }
}
