// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import android.graphics.Bitmap
import android.graphics.Color
import android.os.*
import android.provider.Settings
import android.view.*
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.lifecycle.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.sakugapp.data.*
import com.sakugapp.ui.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Paired Debug/AVD measurements on Android's actual frame clock. No Compose test clock or live-site requests. */
@OptIn(ExperimentalComposeApi::class)
class PerformanceScenarioDeviceTest {
    @get:Rule val rule=ActivityScenarioRule(PerformanceHostActivity::class.java)
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val device get()=UiDevice.getInstance(instrumentation)
    private lateinit var activity:PerformanceHostActivity
    private lateinit var app:AppViewModel
    private lateinit var model:FeedViewModel
    private lateinit var posts:List<Post>
    private val requests=AtomicInteger()
    private val report=linkedMapOf<String,Any?>()
    private val phases=mutableListOf<Map<String,Any?>>()
    private lateinit var output:File
    private val label get()=InstrumentationRegistry.getArguments().getString("profileLabel") ?: "current"
    private fun node(tag:String):UiObject2=checkNotNull(device.wait(Until.findObject(By.res(tag)),10000)){"Missing $tag"}
    private fun await(condition:()->Boolean){val until=SystemClock.uptimeMillis()+15000;while(!condition()){check(SystemClock.uptimeMillis()<until){"Condition timeout"};Thread.sleep(25)}}
    private fun settle(){instrumentation.waitForIdleSync();device.waitForIdle();Thread.sleep(300)}
    private fun tap(tag:String){
        val p=node(tag).visibleCenter;val now=SystemClock.uptimeMillis()
        for(action in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)){
            val event=MotionEvent.obtain(now,SystemClock.uptimeMillis(),action,p.x.toFloat(),p.y.toFloat(),0).apply{source=android.view.InputDevice.SOURCE_TOUCHSCREEN}
            try{check(instrumentation.uiAutomation.injectInputEvent(event,true))}finally{event.recycle()}
        }
    }
    private fun memory():Map<String,Long>{
        val info=Debug.MemoryInfo();Debug.getMemoryInfo(info)
        return mapOf("pss_kib" to info.totalPss.toLong(),"java_heap_bytes" to (Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory()),"native_heap_bytes" to Debug.getNativeHeapAllocatedSize())
    }
    private fun recompositions()=Recomposer.runningRecomposers.value.sumOf{it.changeCount}
    private fun players():Map<String,Any>{
        val log=device.executeShellCommand("logcat -d --pid=${Process.myPid()} -s ExoPlayerImpl:I")
        val events=Regex("(Init|Release) ([0-9a-f]+)").findAll(log).toList();val live=mutableSetOf<String>()
        events.forEach{if(it.groupValues[1]=="Init")live+=it.groupValues[2]else live-=it.groupValues[2]}
        return mapOf("init" to events.count{it.groupValues[1]=="Init"},"release" to events.count{it.groupValues[1]=="Release"},"unreleased_ids" to live.toList())
    }
    private fun bounds(tag:String)=device.findObject(By.res(tag))?.visibleBounds?.let{listOf(it.left,it.top,it.right,it.bottom)}
    private fun top(){
        // Android accessibility scroll-to-position, followed by a bounded native-gesture fallback.
        val grid=instrumentation.uiAutomation.rootInActiveWindow?.findAccessibilityNodeInfosByViewId("post-grid")?.firstOrNull()
        grid?.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_TO_POSITION.id,Bundle().apply{
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_ROW_INT,0);putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_COLUMN_INT,0)
        })
        Thread.sleep(200)
        repeat(25){if(device.hasObject(By.res("post-media-${posts.first().id}"))){settle();return}
            device.swipe(device.displayWidth/2,device.displayHeight/3,device.displayWidth/2,device.displayHeight*4/5,5)
        };check(device.hasObject(By.res("post-media-${posts.first().id}"))){"Unable to reset grid"};settle()
    }
    private fun measure(name:String,action:()->Unit):Map<String,Any?> {
        val frames=Collections.synchronizedList(mutableListOf<Map<String,Double>>());val dropped=AtomicInteger()
        val thread=HandlerThread("fixture-frame-metrics").apply{start()};val handler=Handler(thread.looper)
        val listener=Window.OnFrameMetricsAvailableListener{_,m,missed->
            dropped.addAndGet(missed)
            val metrics=linkedMapOf("total" to FrameMetrics.TOTAL_DURATION,"layout" to FrameMetrics.LAYOUT_MEASURE_DURATION,"draw" to FrameMetrics.DRAW_DURATION,"input" to FrameMetrics.INPUT_HANDLING_DURATION,"sync" to FrameMetrics.SYNC_DURATION,"commands" to FrameMetrics.COMMAND_ISSUE_DURATION,"swap" to FrameMetrics.SWAP_BUFFERS_DURATION)
            frames+=metrics.mapValues{m.getMetric(it.value).coerceAtLeast(0)/1_000_000.0}
        }
        val changes=recompositions();val before=memory();val playerBefore=players();val requestBefore=requests.get()
        rule.scenario.onActivity{it.window.addOnFrameMetricsAvailableListener(listener,handler)}
        val start=SystemClock.elapsedRealtime()
        try{action();settle()}finally{
            rule.scenario.onActivity{it.window.removeOnFrameMetricsAvailableListener(listener)}
            val drained=CountDownLatch(1);handler.post{drained.countDown()};check(drained.await(5,TimeUnit.SECONDS));thread.quitSafely()
        }
        val measured=synchronized(frames){frames.toList()};check(measured.isNotEmpty()){"No frame samples for $name"}
        val budget=1000.0/activity.windowManager.defaultDisplay.refreshRate
        val summary=measured.first().keys.associateWith{key->val sorted=measured.map{it.getValue(key)}.sorted()
            mapOf("p50_ms" to sorted[sorted.size/2],"p95_ms" to sorted[(sorted.size*.95).toInt().coerceAtMost(sorted.lastIndex)],"max_ms" to sorted.last())}
        return mapOf("name" to name,"frames" to measured.size,"over_budget_frames" to measured.count{it.getValue("total")>budget},"budget_ms" to budget,
            "metrics" to summary,"dropped_frame_callbacks" to dropped.get(),"elapsed_ms" to SystemClock.elapsedRealtime()-start,
            "recomposer_change_delta" to recompositions()-changes,"memory_before" to before,"memory_after" to memory(),"players_before" to playerBefore,"players_after" to players(),"repository_requests_delta" to requests.get()-requestBefore)
            .also{phases+=it;println("PERF "+Gson().toJson(it))}
    }
    private fun bitmap()=checkNotNull(instrumentation.uiAutomation.takeScreenshot())
    private fun save(name:String,bitmap:Bitmap){File(output,name).outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}}
    private fun transition(from:String,to:String):Map<String,Any?>{
        settle();val before=bitmap();val started=SystemClock.elapsedRealtime();tap("nav-$to")
        Thread.sleep(55);val sampleStart=SystemClock.elapsedRealtime();val middle=bitmap();val sampleEnd=SystemClock.elapsedRealtime()
        Thread.sleep((300-(SystemClock.elapsedRealtime()-started)).coerceAtLeast(0));settle();val after=bitmap()
        var mixed=0;var samples=0
        fun distance(a:Int,b:Int)=kotlin.math.abs(Color.red(a)-Color.red(b))+kotlin.math.abs(Color.green(a)-Color.green(b))+kotlin.math.abs(Color.blue(a)-Color.blue(b))
        if(before.width==middle.width && before.height==middle.height && before.width==after.width && before.height==after.height){
            for(y in before.height/5 until before.height*3/4 step 4)for(x in before.width/10 until before.width*9/10 step 4){samples++;val m=middle.getPixel(x,y);if(distance(m,before.getPixel(x,y))>24 && distance(m,after.getPixel(x,y))>24)mixed++}
        }
        val prefix="perf-$label-$from-$to";save("$prefix-before.png",before);save("$prefix-middle.png",middle);save("$prefix-after.png",after)
        before.recycle();middle.recycle();after.recycle()
        return mapOf("from" to from,"to" to to,"middle_capture_start_ms" to sampleStart-started,"middle_capture_end_ms" to sampleEnd-started,"mixed_pixels" to mixed,"sampled_pixels" to samples)
    }
    @Test fun profileRealScaffoldScrollPreviewAndNavigation()=runBlocking<Unit>{
        require(label in listOf("baseline","current"));device.executeShellCommand("logcat -c")
        rule.scenario.onActivity{activity=it;output=File(it.getExternalFilesDir(null),"performance-$label").apply{mkdirs()}}
        val fixtureDir=File(activity.cacheDir,"performance-fixtures").apply{mkdirs()}
        val video=File(fixtureDir,"preview.mp4").apply{writeBytes(instrumentation.context.assets.open("clip-gop.mp4").use{it.readBytes()})}
        val poster=Bitmap.createBitmap(320,180,Bitmap.Config.ARGB_8888).apply{eraseColor(Color.rgb(68,96,126))}
        val bytes=java.io.ByteArrayOutputStream().apply{poster.compress(Bitmap.CompressFormat.PNG,100,this)}.toByteArray();poster.recycle()
        val types=instrumentation.context.assets.open("personal-tag-summary.json").bufferedReader().use{PostParser.tagSummary(it.readText())}
        posts=instrumentation.context.assets.open("posts-five-pages.json").bufferedReader().use{JsonParser.parseString(it.readText()).asJsonArray.flatMap{page->PostParser.posts(page.toString(),types)}}.distinctBy{it.id}.map{post->
            val image=File(fixtureDir,"poster-${post.id}.png").apply{writeBytes(bytes)}
            post.copy(previewUrl=android.net.Uri.fromFile(image).toString(),fileUrl=android.net.Uri.fromFile(video).buildUpon().appendQueryParameter("post",post.id.toString()).build().toString(),fileExt="mp4")
        };assertEquals(300,posts.size)
        val repository=object:PostRepository{override val pageSize=1000;override suspend fun page(query:String,page:Int):PostPage{requests.incrementAndGet();return PostPage(if(page==1)posts else emptyList(),if(page==1)posts.size else 0)}}
        rule.scenario.onActivity{host->
            app=ViewModelProvider(host)[AppViewModel::class.java]
            model=ViewModelProvider(host,object:ViewModelProvider.Factory{override fun <T:ViewModel> create(cls:Class<T>):T{@Suppress("UNCHECKED_CAST")return FeedViewModel(SavedStateHandle(),repository) as T}})["performance-feed",FeedViewModel::class.java]
        }
        app.preferences.theme("dark");app.preferences.number("fontScale",1f);app.preferences.number("cardSpacing",8f)
        rule.scenario.onActivity{it.setContent{Sakugapp(model,app)}};node("search-home");tap("nav-posts");node("post-grid");settle()
        report["label"]=label;report["scope"]="Debug fixture host running production Sakugapp; real Android frame clock; AVD/SwiftShader, not physical-device or live-network performance"
        report["device"]=Build.MODEL;report["api"]=Build.VERSION.SDK_INT;report["post_count"]=posts.size
        report["animator_scale"]=Settings.Global.getFloat(activity.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)
        report["window_animation_scale"]=Settings.Global.getFloat(activity.contentResolver,Settings.Global.WINDOW_ANIMATION_SCALE,1f)
        report["transition_scale"]=Settings.Global.getFloat(activity.contentResolver,Settings.Global.TRANSITION_ANIMATION_SCALE,1f)
        report["memory_start"]=memory()
        val traceStart=device.executeShellCommand("atrace --async_start -b 8192 -a com.sakugapp gfx view input wm am sched freq")
        report["atrace_start"]=traceStart.take(300)
        try{
            val columns=listOf(2,4,6)+if(activity.resources.configuration.smallestScreenWidthDp>=600)listOf(8,12)else emptyList()
            for(count in columns)for(auto in listOf(false,true)){
                app.preferences.columns(count);app.preferences.flag("auto",auto);await{app.settings.value.columns==count && app.settings.value.autoPreview==auto};top()
                val shown=bounds("post-grid")
                val result=measure("scroll-$count-auto-$auto"){
                    repeat(12){device.swipe(device.displayWidth/2,device.displayHeight*4/5,device.displayWidth/2,device.displayHeight/4,16)}
                    Thread.sleep(700)
                    repeat(12){device.swipe(device.displayWidth/2,device.displayHeight/4,device.displayWidth/2,device.displayHeight*4/5,16)}
                    Thread.sleep(700)
                }
                phases[phases.lastIndex]=result+mapOf("columns" to count,"auto_preview" to auto,"grid_before" to shown,"grid_after" to bounds("post-grid"),"image_memory_cache_bytes" to coil.Coil.imageLoader(activity).memoryCache?.size)
            }
        }finally{
            val pfd=instrumentation.uiAutomation.executeShellCommand("atrace --async_stop")
            ParcelFileDescriptor.AutoCloseInputStream(pfd).use{input->File(output,"perf-$label-scroll.atrace").outputStream().use{input.copyTo(it)}}
        }
        app.preferences.flag("auto",false);app.preferences.columns(2);top()
        report["transitions"]=listOf(transition("posts","search_home"),transition("search_home","posts"),transition("posts","profile"),transition("profile","posts"))
        top();val gridBefore=bounds("post-grid")
        measure("detail-return"){
            tap("post-media-${posts.first().id}");node("detail-actions");Thread.sleep(350);device.pressBack();node("post-grid")
        };report["detail_grid_before"]=gridBefore;report["detail_grid_after"]=bounds("post-grid")
        measure("twenty-main-switches"){repeat(5){for(route in listOf("search_home","posts","profile","posts")){tap("nav-$route");Thread.sleep(260)}}}
        report["phases"]=phases;report["memory_end"]=memory();report["repository_requests"]=requests.get();report["players_end"]=players()
        val file=File(output,"perf-$label.json");file.writeText(Gson().toJson(report));println("PERFORMANCE_REPORT "+file.readText())
        device.executeShellCommand("mkdir -p /sdcard/Download/sakugapp-performance/$label")
        device.executeShellCommand("cp ${output.absolutePath}/* /sdcard/Download/sakugapp-performance/$label/")
        assertEquals("Tab switches must not fetch an unchanged feed",1,requests.get())
    }
}
