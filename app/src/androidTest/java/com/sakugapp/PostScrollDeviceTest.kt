// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import android.os.Handler
import android.os.HandlerThread
import android.view.FrameMetrics
import android.view.Window
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.sakugapp.data.*
import com.sakugapp.ui.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Same 300 captured posts and rapid gestures on both revisions. Isolates layout, not website/image latency. */
@RunWith(AndroidJUnit4::class)
class PostScrollDeviceTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    @Test fun rapidUpAndDownScrollCollectsActualFrameDurations(){
        val instrumentation=InstrumentationRegistry.getInstrumentation();val assets=instrumentation.context.assets
        val types=assets.open("personal-tag-summary.json").bufferedReader().use{PostParser.tagSummary(it.readText())}
        val posts=assets.open("posts-five-pages.json").bufferedReader().use{reader->JsonParser.parseString(reader.readText()).asJsonArray.flatMap{PostParser.posts(it.toString(),types)}}
            .distinctBy{it.id}.map{it.copy(previewUrl=null)}
        assertEquals(300,posts.size)
        val state=FeedState(posts=posts,endReached=true);lateinit var app:AppViewModel
        ui.runOnUiThread{app=ViewModelProvider(ui.activity)[AppViewModel::class.java];ui.activity.setContent{
            SakugappTheme(false){Surface(Modifier.fillMaxSize().systemBarsPadding()){
                PostsScreen(state,rememberLazyGridState(),AppSettings(tagVerticalSpacing=0f),emptyList(),false,app,{},{},{},{})
            }}
        }}
        ui.onNodeWithTag("post-grid").performScrollToIndex(100);ui.onNodeWithTag("post-grid").performScrollToIndex(0)
        val label=InstrumentationRegistry.getArguments().getString("profileLabel") ?: "current"
        require(label in listOf("baseline","current"))
        val samples=Collections.synchronizedList(mutableListOf<Double>())
        val thread=HandlerThread("short-scroll-frames").apply{start()};val handler=Handler(thread.looper)
        val listener=Window.OnFrameMetricsAvailableListener{_,metrics,_->samples.add(metrics.getMetric(FrameMetrics.TOTAL_DURATION)/1_000_000.0)}
        ui.runOnUiThread{ui.activity.window.addOnFrameMetricsAvailableListener(listener,handler)}
        val device=UiDevice.getInstance(instrumentation);val x=device.displayWidth/2;val top=(device.displayHeight*.25f).toInt();val bottom=(device.displayHeight*.8f).toInt()
        try{
            repeat(12){device.swipe(x,bottom,x,top,16)}
            repeat(12){device.swipe(x,top,x,bottom,16)}
            ui.waitForIdle()
        }finally{
            ui.runOnUiThread{ui.activity.window.removeOnFrameMetricsAvailableListener(listener)}
            val drained=CountDownLatch(1);handler.post{drained.countDown()};drained.await(5,TimeUnit.SECONDS);thread.quitSafely()
        }
        val sorted=synchronized(samples){samples.toList().sorted()};assertTrue("Actual UI frame samples collected",sorted.size>20)
        val result=mapOf("label" to label,"scope" to "API 28 Debug; 300 real response posts; zero tag spacing; thumbnails deliberately absent in both revisions to isolate layout; 24 identical swipes; not a real-device/network benchmark",
            "frames" to sorted.size,"mean_ms" to sorted.average(),"p50_ms" to sorted[sorted.size/2],"p95_ms" to sorted[(sorted.size*.95).toInt().coerceAtMost(sorted.lastIndex)],
            "max_ms" to sorted.last(),"over_34ms" to sorted.count{it>34},"over_700ms" to sorted.count{it>700})
        val folder=File(ui.activity.getExternalFilesDir(null),"ui-evidence").apply{mkdirs()};val file=File(folder,"scroll-$label.json");file.writeText(Gson().toJson(result))
        device.executeShellCommand("mkdir -p /sdcard/Download/sakugapp-ui-evidence")
        device.executeShellCommand("cp ${file.absolutePath} /sdcard/Download/sakugapp-ui-evidence/scroll-$label.json")
        println("Short scroll frame metrics: "+file.readText())
        ui.onNodeWithTag("post-grid").assertExists()
    }
}
