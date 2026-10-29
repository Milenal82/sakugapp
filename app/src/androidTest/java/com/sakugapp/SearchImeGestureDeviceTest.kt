// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import android.os.SystemClock
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import com.sakugapp.ui.FeedViewModel
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Keep the real platform Choreographer/IME animation clock on API 35, without a Compose test owner. */
@RunWith(AndroidJUnit4::class)
class SearchImeGestureDeviceTest {
    @get:Rule val activity=ActivityScenarioRule(MainActivity::class.java)
    private val device get()=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private var right=false
    private fun field()=device.wait(Until.findObject(By.res("search-input")),10000).also{assertNotNull("Search editor",it)}!!
    private fun entry()=device.wait(Until.findObject(By.res("search-entry")),10000).also{assertNotNull("Underlying Post page",it)}!!
    private fun query():String{var value="";activity.scenario.onActivity{value=ViewModelProvider(it)[FeedViewModel::class.java].state.value.query};return value}
    private fun ime():Boolean{var visible=false;activity.scenario.onActivity{visible=ViewCompat.getRootWindowInsets(it.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime())==true};return visible}
    private fun await(message:String,condition:()->Boolean){
        val deadline=SystemClock.elapsedRealtime()+15000
        while(!condition() && SystemClock.elapsedRealtime()<deadline)SystemClock.sleep(50)
        assertTrue(message,condition())
    }
    private fun back(){
        right=!right
        val width=device.displayWidth
        // Use an edge above the keyboard's own touch/gesture region.
        val y=device.displayHeight/4
        assertTrue(device.swipe(if(right)width-1 else 1,y,if(right)width*2/3 else width/3,y,24))
        device.waitForIdle()
    }
    @Test fun actualImeEdgeBackKeepsRootAndSearchResultsWithoutFinishingOrSubmitting(){
        assertEquals("2",device.executeShellCommand("settings get secure navigation_mode").trim())
        for(context in listOf("root","results")){
            if(context=="results"){
                entry().click();field().text="animated"
                val submit=device.wait(Until.findObject(By.res("submit-search")),10000);assertNotNull(submit);submit.click()
                await("Submitted query becomes active"){query()=="animated" && !device.hasObject(By.res("search-input"))}
            }
            val original=query()
            entry().click();field().text="animated effects"
            await("Real software keyboard is visible"){ime()}
            UiEvidence.capture("search-ime-$context-before-api35.png")
            back();UiEvidence.capture("search-ime-$context-after-api35.png")
            await("Back hides the keyboard or exits editing"){!ime()}
            activity.scenario.onActivity{assertFalse("Back must not finish the Activity",it.isFinishing)}
            assertEquals("Back must not submit or pop the underlying query",original,query())
            if(device.hasObject(By.res("search-input")))back()
            assertTrue(device.wait(Until.gone(By.res("search-input")),10000))
            entry();assertEquals(original,query())
            activity.scenario.onActivity{assertFalse(it.isFinishing)}
        }
    }
}
