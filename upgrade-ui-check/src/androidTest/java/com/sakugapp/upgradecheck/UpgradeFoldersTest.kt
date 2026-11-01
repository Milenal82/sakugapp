// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.upgradecheck

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern

/** Operates both official APKs through normal UI and system document pickers, never private app files. */
@RunWith(AndroidJUnit4::class)
class UpgradeFoldersTest {
    private val device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private fun find(selector:BySelector):UiObject2=checkNotNull(device.wait(Until.findObject(selector),20000)){"Native UI missing: $selector"}
    private fun click(selector:BySelector){find(selector).click(100)}
    private fun checkable(node:UiObject2):UiObject2{
        var current:UiObject2?=node
        repeat(4){val value=checkNotNull(current){"Checkable control missing"};if(value.isCheckable)return value;current=value.parent}
        error("Expected a checkable control, not its text label")
    }
    private fun menu(name:String){click(By.text("Menu"));click(By.text(name))}
    private fun export(name:String){
        click(By.res("export-favorites"));find(By.clazz("android.widget.EditText")).text=name
        click(By.text(Pattern.compile("(?i)save")));find(By.text("Exported"))
    }
    private fun wrapOff(change:Boolean=true){
        repeat(12){
            val row=device.findObject(By.desc("Wrap tags"))
            if(row!=null){
                val toggle=checkable(row);if(change && toggle.isChecked)toggle.click(100)
                repeat(50){if(!checkable(find(By.desc("Wrap tags"))).isChecked)return;Thread.sleep(100)}
                fail("Wrap tags was not retained as off")
            }
            device.swipe(device.displayWidth/2,(device.displayHeight*.8).toInt(),device.displayWidth/2,(device.displayHeight*.3).toInt(),24)
        }
        error("Wrap setting not found")
    }
    @Test fun retainFavoriteFoldersAndPreferencesAcrossOfficialUpgrade(){
        device.wakeUp();val stage=InstrumentationRegistry.getArguments().getString("stage")
        menu("Favorites")
        if(stage=="seed"){
            click(By.res("import-favorites"))
            var file=device.wait(Until.findObject(By.text("official-v120-favorites.json")),6000)
            if(file==null){device.findObject(By.desc("Show roots"))?.click();click(By.text("Downloads"));file=find(By.text("official-v120-favorites.json"))}
            file!!.click(100);click(By.text("Merge"));find(By.res("backup-result"))
            export("before-upgrade.json")
            menu("Settings");click(By.desc("Light"));wrapOff()
        }else{
            assertEquals("verify",stage)
            export("after-upgrade.json")
            menu("Settings")
            // Compose exposes the label as a child; FilterChip selection is checked on its parent.
            val light=checkable(find(By.desc("Light")))
            assertTrue("Light theme remains checked after upgrade",light.isChecked)
            wrapOff(false)
        }
    }
}
