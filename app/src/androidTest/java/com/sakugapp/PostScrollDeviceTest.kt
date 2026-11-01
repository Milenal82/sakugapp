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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.semantics.*
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
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
import kotlinx.coroutines.delay

/** Same 300 captured posts and rapid gestures on both revisions. Isolates layout, not website/image latency. */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalComposeUiApi::class)
class PostScrollDeviceTest {
    @get:Rule val activityRule=ActivityScenarioRule(MainActivity::class.java)
    @Test fun rapidUpAndDownScrollCollectsActualFrameDurations(){
        val instrumentation=InstrumentationRegistry.getInstrumentation();val assets=instrumentation.context.assets
        val types=assets.open("personal-tag-summary.json").bufferedReader().use{PostParser.tagSummary(it.readText())}
        val posts=assets.open("posts-five-pages.json").bufferedReader().use{reader->JsonParser.parseString(reader.readText()).asJsonArray.flatMap{PostParser.posts(it.toString(),types)}}
            .distinctBy{it.id}.map{it.copy(previewUrl=null)}
        assertEquals(300,posts.size)
        val state=FeedState(posts=posts,endReached=true);lateinit var activity:MainActivity
        val warmed=CountDownLatch(1)
        activityRule.scenario.onActivity{current->activity=current
            val app=ViewModelProvider(current)[AppViewModel::class.java];current.setContent{
            SakugappTheme(false){Surface(Modifier.fillMaxSize().systemBarsPadding().semantics{testTagsAsResourceId=true}){
                val grid=rememberLazyGridState()
                LaunchedEffect(Unit){grid.scrollToItem(100);delay(200);grid.scrollToItem(0);delay(600);warmed.countDown()}
                PostsScreen(state,grid,AppSettings(tagVerticalSpacing=0f),emptyList(),false,app,{},{},{},{})
            }}
        }}
        assertTrue(warmed.await(20,TimeUnit.SECONDS))
        // ActivityScenario leaves Compose on the real Android frame clock; no synthetic test-clock timings.
        val device=UiDevice.getInstance(instrumentation);assertTrue(device.wait(Until.hasObject(By.res("post-grid")),10000));device.waitForIdle()
        val label=InstrumentationRegistry.getArguments().getString("profileLabel") ?: "current"
        require(label in listOf("baseline","current"))
        val samples=Collections.synchronizedList(mutableListOf<Map<String,Double>>())
        val thread=HandlerThread("short-scroll-frames").apply{start()};val handler=Handler(thread.looper)
        val listener=Window.OnFrameMetricsAvailableListener{_,metrics,_->
            fun ms(id:Int)=metrics.getMetric(id).coerceAtLeast(0)/1_000_000.0
            samples.add(mapOf("total" to ms(FrameMetrics.TOTAL_DURATION),"layout" to ms(FrameMetrics.LAYOUT_MEASURE_DURATION),"draw" to ms(FrameMetrics.DRAW_DURATION),
                "animation" to ms(FrameMetrics.ANIMATION_DURATION),"input" to ms(FrameMetrics.INPUT_HANDLING_DURATION),"sync" to ms(FrameMetrics.SYNC_DURATION),
                "commands" to ms(FrameMetrics.COMMAND_ISSUE_DURATION),"swap" to ms(FrameMetrics.SWAP_BUFFERS_DURATION)))
        }
        activityRule.scenario.onActivity{it.window.addOnFrameMetricsAvailableListener(listener,handler)}
        val x=device.displayWidth/2;val top=(device.displayHeight*.25f).toInt();val bottom=(device.displayHeight*.8f).toInt()
        try{
            repeat(12){device.swipe(x,bottom,x,top,16)}
            repeat(12){device.swipe(x,top,x,bottom,16)}
            device.waitForIdle();instrumentation.waitForIdleSync()
        }finally{
            activityRule.scenario.onActivity{it.window.removeOnFrameMetricsAvailableListener(listener)}
            val drained=CountDownLatch(1);handler.post{drained.countDown()};drained.await(5,TimeUnit.SECONDS);thread.quitSafely()
        }
        val measured=synchronized(samples){samples.toList()};val sorted=measured.map{it.getValue("total")}.sorted();assertTrue("Actual UI frame samples collected",sorted.size>20)
        val components=measured.first().keys.associateWith{key->val values=measured.map{it.getValue(key)}.sorted();mapOf("mean_ms" to values.average(),"p95_ms" to values[(values.size*.95).toInt().coerceAtMost(values.lastIndex)])}
        val result=mapOf("label" to label,"scope" to "API 28 Debug on real Android frame clock (ActivityScenario); 300 real response posts; zero tag spacing; thumbnails deliberately absent in both revisions to isolate layout; 24 identical swipes; not a real-device/network benchmark",
            "frames" to sorted.size,"mean_ms" to sorted.average(),"p50_ms" to sorted[sorted.size/2],"p95_ms" to sorted[(sorted.size*.95).toInt().coerceAtMost(sorted.lastIndex)],
            "max_ms" to sorted.last(),"over_34ms" to sorted.count{it>34},"over_700ms" to sorted.count{it>700},"components" to components)
        val folder=File(activity.getExternalFilesDir(null),"ui-evidence").apply{mkdirs()};val file=File(folder,"scroll-$label.json");file.writeText(Gson().toJson(result))
        device.executeShellCommand("mkdir -p /sdcard/Download/sakugapp-ui-evidence")
        device.executeShellCommand("cp ${file.absolutePath} /sdcard/Download/sakugapp-ui-evidence/scroll-$label.json")
        println("Short scroll frame metrics: "+file.readText())
        assertTrue(device.hasObject(By.res("post-grid")))
    }
}
