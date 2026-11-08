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
    private val requestLog=Collections.synchronizedList(mutableListOf<String>())
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
    private fun visiblePostIds():List<Long>{
        // UiDevice.findObjects waits for accessibility idle; that inserts ~1 s between fast
        // swipes and artificially starts automatic previews. Read a bounded snapshot instead.
        val pending=java.util.ArrayDeque<AccessibilityNodeInfo>()
        instrumentation.uiAutomation.rootInActiveWindow?.let{pending.add(it)}
        val ids=mutableListOf<Long>()
        var inspected=0
        while(pending.isNotEmpty()){
            val item=pending.removeFirst()
            try{if(inspected++<1000){
                if(item.isVisibleToUser)item.viewIdResourceName?.substringAfterLast("post-media-","")?.toLongOrNull()?.let(ids::add)
                repeat(item.childCount){index->item.getChild(index)?.let{pending.add(it)}}
            }}finally{@Suppress("DEPRECATION") item.recycle()}
        }
        return ids
    }
    private fun gridNode():AccessibilityNodeInfo?{
        // Compose testTag resource names are virtual IDs, not qualified Android View IDs.
        // findAccessibilityNodeInfosByViewId("post-grid") cannot resolve them.
        val pending=java.util.ArrayDeque<AccessibilityNodeInfo>()
        instrumentation.uiAutomation.rootInActiveWindow?.let{pending.add(it)}
        while(pending.isNotEmpty()){
            val item=pending.removeFirst()
            if(item.viewIdResourceName?.substringAfterLast('/')=="post-grid"){
                @Suppress("DEPRECATION") pending.forEach{it.recycle()}
                return item
            }
            repeat(item.childCount){index->item.getChild(index)?.let{pending.add(it)}}
            @Suppress("DEPRECATION") item.recycle()
        }
        return null
    }
    private fun top(){
        // Precondition outside the measured gesture window. Accessibility page actions avoid
        // fling-distance differences between the phone and a slow software-rendered tablet.
        gridNode()?.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_TO_POSITION.id,Bundle().apply{
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_ROW_INT,0);putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_COLUMN_INT,0)
        })
        repeat(160){
            if(posts.first().id in visiblePostIds()){settle();return}
            gridNode()?.let{node->try{node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)}finally{@Suppress("DEPRECATION") node.recycle()}}
            Thread.sleep(60)
        }
        val capture=bitmap();save("reset-failed.png",capture);capture.recycle()
        device.dumpWindowHierarchy(File(output,"reset-failed.xml"))
        error("Unable to reset grid; visible IDs=${visiblePostIds()}")
    }
    @After fun exportMeasurementsEvenWhenAPreconditionFails(){
        if(!::output.isInitialized)return
        report["label"]=label;report["phases"]=phases;report["players_end"]=players();report["memory_end"]=memory()
        File(output,"perf-$label.json").writeText(Gson().toJson(report))
        device.executeShellCommand("mkdir -p /sdcard/Download/sakugapp-performance/$label")
        output.listFiles().orEmpty().forEach{captured->
            val target="/sdcard/Download/sakugapp-performance/$label/${captured.name}"
            device.executeShellCommand("cp ${captured.absolutePath} $target")
            check(device.executeShellCommand("stat -c %s $target").trim().toLongOrNull()==captured.length()){"Export failed: ${captured.name}"}
        }
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
        Trace.beginSection("sakugapp:$name")
        try{action();settle()}finally{
            Trace.endSection()
            rule.scenario.onActivity{it.window.removeOnFrameMetricsAvailableListener(listener)}
            val drained=CountDownLatch(1);handler.post{drained.countDown()};check(drained.await(5,TimeUnit.SECONDS));thread.quitSafely()
        }
        val measured=synchronized(frames){frames.toList()};check(measured.isNotEmpty()){"No frame samples for $name"}
        File(output,"frames-$label-$name.csv").writeText(measured.first().keys.joinToString(",")+"\n"+measured.joinToString("\n"){row->row.values.joinToString(",")})
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
    private fun stopPreview(){
        if((players()["unreleased_ids"] as List<*>).isEmpty())return
        for(media in device.findObjects(By.res(java.util.regex.Pattern.compile("post-media-[0-9]+")))){
            media.longClick();val stop=device.findObject(By.text(activity.getString(R.string.ui_stop_preview)))
            if(stop!=null){stop.click();settle();return}else device.pressBack()
        }
    }
    @Test fun profileRealScaffoldScrollPreviewAndNavigation()=runBlocking<Unit>{
        require(label in listOf("baseline","current"));device.executeShellCommand("logcat -c")
        rule.scenario.onActivity{activity=it;output=File(it.getExternalFilesDir(null),"performance-$label").apply{mkdirs()}}
        val fixtureDir=File(activity.cacheDir,"performance-fixtures").apply{mkdirs()}
        val videoBytes=instrumentation.context.assets.open("clip-gop.mp4").use{it.readBytes()}
        val offline=File(activity.filesDir,"favorites-media").apply{mkdirs()}
        val poster=Bitmap.createBitmap(320,180,Bitmap.Config.ARGB_8888).apply{eraseColor(Color.rgb(68,96,126))}
        val bytes=java.io.ByteArrayOutputStream().apply{poster.compress(Bitmap.CompressFormat.PNG,100,this)}.toByteArray();poster.recycle()
        val types=instrumentation.context.assets.open("personal-tag-summary.json").bufferedReader().use{PostParser.tagSummary(it.readText())}
        posts=instrumentation.context.assets.open("posts-five-pages.json").bufferedReader().use{JsonParser.parseString(it.readText()).asJsonArray.flatMap{page->PostParser.posts(page.toString(),types)}}.distinctBy{it.id}.map{post->
            val image=File(fixtureDir,"poster-${post.id}.png").apply{writeBytes(bytes)}
            // Synthetic media URLs exist only in this fixture. Seed owned offline files before rendering;
            // this exercises the real local-first resolver and trusted duration-fact path without HTTP.
            val url="https://www.sakugabooru.com/data/performance-fixture-${post.id}.mp4"
            val hash=java.security.MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).take(12).joinToString(""){"%02x".format(it)}
            File(offline,"${post.id}-$hash.mp4").writeBytes(videoBytes)
            post.copy(previewUrl=android.net.Uri.fromFile(image).toString(),fileUrl=url,fileExt="mp4")
        };assertEquals(300,posts.size)
        val repository=object:PostRepository{override val pageSize=1000;override suspend fun page(query:String,page:Int):PostPage{requests.incrementAndGet();requestLog+="$query:$page";return PostPage(if(page==1)posts else emptyList(),if(page==1)posts.size else 0)}}
        rule.scenario.onActivity{host->
            app=ViewModelProvider(host)[AppViewModel::class.java]
            model=ViewModelProvider(host,object:ViewModelProvider.Factory{override fun <T:ViewModel> create(cls:Class<T>):T{@Suppress("UNCHECKED_CAST")return FeedViewModel(SavedStateHandle(),repository) as T}})["performance-feed",FeedViewModel::class.java]
        }
        assertTrue("All fixture video URLs must resolve offline before any player is created",posts.all{app.offlineUri(it)!=null})
        app.preferences.theme("dark");app.preferences.number("fontScale",1f);app.preferences.number("cardSpacing",8f)
        rule.scenario.onActivity{it.setContent{Sakugapp(model,app)}};node("search-home");tap("nav-posts");node("post-grid");settle()
        report["label"]=label;report["scope"]="Debug fixture host running production Sakugapp; real Android frame clock; AVD/SwiftShader, not physical-device or live-network performance"
        report["device"]=Build.MODEL;report["api"]=Build.VERSION.SDK_INT;report["pid"]=android.os.Process.myPid()
        report["frame_definition"]="FrameMetrics TOTAL_DURATION > display refresh period; not SurfaceFlinger FrameTimeline jank";report["display_px"]=listOf(device.displayWidth,device.displayHeight);report["density_dpi"]=activity.resources.displayMetrics.densityDpi
        report["post_count"]=posts.size;report["media"]="300 owned copies of a deterministic MP4 and generated 320x180 PNGs; public response IDs/tags/scores, synthetic media URLs never requested"
        report["animator_scale"]=Settings.Global.getFloat(activity.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)
        report["window_animation_scale"]=Settings.Global.getFloat(activity.contentResolver,Settings.Global.WINDOW_ANIMATION_SCALE,1f)
        report["transition_scale"]=Settings.Global.getFloat(activity.contentResolver,Settings.Global.TRANSITION_ANIMATION_SCALE,1f)
        report["memory_start"]=memory()
        val traceStart=device.executeShellCommand("atrace --async_start -b 8192 -a com.sakugapp gfx view input wm am sched freq")
        report["atrace_start"]=traceStart.take(300)
        try{
            val columns=listOf(2,4,6)+if(activity.resources.configuration.smallestScreenWidthDp>=600)listOf(8,12)else emptyList()
            for(count in columns)for(auto in listOf(false,true)){
                app.preferences.flag("auto",false);app.preferences.columns(count);await{app.settings.value.columns==count && !app.settings.value.autoPreview};top()
                app.preferences.flag("auto",auto);await{app.settings.value.autoPreview==auto}
                val shown=bounds("post-grid");var hidden:List<Int>?=null;var navHidden=false
                val directions=mutableListOf<String>();val nearTopIds=posts.take(count*8).map{it.id}.toSet();val nearEndIds=posts.takeLast(count*4).map{it.id}.toSet()
                var towardEnd=true
                val result=measure("scroll-$count-auto-$auto"){
                    repeat(24){step->
                        val visible=visiblePostIds()
                        if(visible.any{it in nearTopIds})towardEnd=true else if(visible.any{it in nearEndIds})towardEnd=false
                        directions+=if(towardEnd)"down"else"up"
                        val first=if(towardEnd)device.displayHeight*4/5 else device.displayHeight/4
                        val last=if(towardEnd)device.displayHeight/4 else device.displayHeight*4/5
                        device.swipe(device.displayWidth/2,first,device.displayWidth/2,last,16)
                        if(step==11){Thread.sleep(700);hidden=bounds("post-grid");navHidden=!device.hasObject(By.res("main-bottom-nav"))}
                    };Thread.sleep(700)
                }
                phases[phases.lastIndex]=result+mapOf("gesture_directions" to directions,"refresh_revision" to model.state.value.refreshRevision,"columns" to count,"auto_preview" to auto,"grid_before" to shown,"grid_midway" to hidden,"bottom_nav_midway_hidden" to navHidden,"grid_after" to bounds("post-grid"),"image_memory_cache_bytes" to coil.Coil.imageLoader(activity).memoryCache?.size)
            }
        }finally{
            val pfd=instrumentation.uiAutomation.executeShellCommand("atrace --async_stop")
            ParcelFileDescriptor.AutoCloseInputStream(pfd).use{input->File(output,"perf-$label-scroll.atrace").outputStream().use{input.copyTo(it)}}
        }
        app.preferences.flag("auto",false);app.preferences.columns(2);top();stopPreview()
        report["transitions"]=listOf(transition("posts","search_home"),transition("search_home","posts"),transition("posts","profile"),transition("profile","posts"))
        top();val gridBefore=bounds("post-grid")
        measure("detail-return"){
            tap("post-media-${posts.first().id}");node("detail-actions");Thread.sleep(350);device.pressBack();node("post-grid")
        };report["detail_grid_before"]=gridBefore;report["detail_grid_after"]=bounds("post-grid")
        val navigationRequests=requests.get()
        measure("twenty-main-switches"){repeat(5){for(route in listOf("search_home","posts","profile","posts")){tap("nav-$route");Thread.sleep(260)}}}
        report["phases"]=phases;report["memory_end"]=memory();report["repository_requests"]=requests.get();report["repository_request_log"]=requestLog.toList();report["navigation_requests_delta"]=requests.get()-navigationRequests;report["players_end"]=players()
        val file=File(output,"perf-$label.json");file.writeText(Gson().toJson(report));println("PERFORMANCE_REPORT "+file.readText())
        assertEquals("Tab switches must not fetch an unchanged feed",navigationRequests,requests.get());report["scenario_completed"]=true
    }
}
