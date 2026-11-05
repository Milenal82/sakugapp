package com.sakugapp.upgradecheck

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern

/** Separate test process: the application itself is genuinely force-stopped and relaunched. */
@RunWith(AndroidJUnit4::class)
class DirectoryPersistenceTest {
    private val device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private fun find(s:BySelector)=checkNotNull(device.wait(Until.findObject(s),15000)){"Missing $s"}
    private fun visible(s:BySelector):UiObject2{
        repeat(18){device.findObject(s)?.takeIf{it.visibleBounds.height()>0}?.let{return it};device.swipe(device.displayWidth/2,(device.displayHeight*.82).toInt(),device.displayWidth/2,(device.displayHeight*.3).toInt(),24)}
        error("Unable to scroll to $s")
    }
    private fun settings(){find(By.text("Menu")).click();find(By.text("Settings")).click()}
    private fun launch(){device.executeShellCommand("am start -W -n com.sakugapp/.MainActivity");find(By.text("Menu"))}
    private fun choose(label:String,name:String){
        device.executeShellCommand("mkdir -p /sdcard/Download/$name")
        val title=visible(By.text(label));val bottom=title.visibleBounds.bottom
        val button=device.findObjects(By.text("Change folder")).filter{it.visibleBounds.centerY()>bottom}.minByOrNull{it.visibleBounds.centerY()}
        checkNotNull(button).click()
        device.wait(Until.findObject(By.desc("Show roots")),3000)?.click();device.wait(Until.findObject(By.text("Downloads")),3000)?.click()
        if(device.wait(Until.findObject(By.text(name)),2000)==null){
            val root=By.text(Pattern.compile("(?i)(internal storage|.*sdk.*|.*emulator.*|.*phone.*)"))
            if(device.findObject(root)==null)device.findObject(By.desc("Show roots"))?.click()
            find(root).click();device.wait(Until.findObject(By.text("Download")),3000)?.click()
        }
        find(By.text(name)).click();find(By.text(Pattern.compile("(?i)(use this folder|select|select folder)"))).click()
        device.wait(Until.findObject(By.text(Pattern.compile("(?i)allow"))),2500)?.click()
        find(By.text(Pattern.compile(".*$name.*")))
    }
    @Test fun savedTreesAndGrantsSurviveForceStop(){
        launch();settings();choose("Single video folder","PersistSingle");choose("Batch video folder","PersistBatch")
        device.executeShellCommand("am force-stop com.sakugapp");launch();settings()
        visible(By.text(Pattern.compile(".*PersistSingle.*")));visible(By.text(Pattern.compile(".*PersistBatch.*")))
        val grants=device.executeShellCommand("dumpsys activity permissions")
        for(name in listOf("PersistSingle","PersistBatch")){
            val index=grants.indexOf(name);assertTrue("Saved grant for $name",index>=0)
            val block=grants.substring(maxOf(0,index-600),minOf(grants.length,index+1800))
            assertTrue("Durable read/write modes for $name",block.contains("persistedModeFlags=0x3"))
        }
    }
}
