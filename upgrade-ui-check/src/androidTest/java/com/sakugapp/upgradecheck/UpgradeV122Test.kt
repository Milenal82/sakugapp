// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.upgradecheck

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern

/** Normal UI only: legacy off values, nested favorites and actual browsing history survive install -r. */
@RunWith(AndroidJUnit4::class)
class UpgradeV122Test {
    private val device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val args get()=InstrumentationRegistry.getArguments()
    private fun find(selector:BySelector)=checkNotNull(device.wait(Until.findObject(selector),20000)){"Missing native control: $selector"}
    private fun click(selector:BySelector){find(selector).click(100)}
    private fun control(node:UiObject2):UiObject2{
        var current:UiObject2?=node
        repeat(4){val row=checkNotNull(current);if(row.isCheckable)return row;current=row.parent};error("Checkable control missing")
    }
    private fun visible(selector:BySelector):UiObject2{
        repeat(16){
            val node=device.findObject(selector)
            if(node!=null && node.visibleBounds.height()>0)return node
            device.swipe(device.displayWidth/2,(device.displayHeight*.82).toInt(),device.displayWidth/2,(device.displayHeight*.35).toInt(),22)
        };error("Cannot scroll to $selector")
    }
    private fun toggle(label:String,value:Boolean){
        val node=control(visible(By.desc(label)));if(node.isChecked!=value)node.click(100)
        repeat(50){if(control(find(By.desc(label))).isChecked==value)return;Thread.sleep(100)}
        error("Toggle not saved: $label")
    }
    private fun menu(name:String){click(By.text("Menu"));click(By.text(name))}
    private fun export(name:String){click(By.res("export-favorites"));find(By.clazz("android.widget.EditText")).text=name;click(By.text(Pattern.compile("(?i)save")));find(By.text("Exported"))}
    private fun openSavedPost(id:String){
        menu("Favorites");visible(By.text("Upgrade A")).click(100);visible(By.res("post-media-$id")).click(100)
        find(By.res("detail-post-id"))
    }
    @Test fun officialV121UpgradeRetainsDataAndAppliesNewPolicies(){
        device.wakeUp();val first=args.getString("firstId")!!;val next=args.getString("nextId")!!
        if(args.getString("stage")=="seed"){
            menu("Favorites");click(By.res("import-favorites"))
            var file=device.wait(Until.findObject(By.text("official-v121-favorites.json")),5000)
            if(file==null){device.findObject(By.desc("Show roots"))?.click();click(By.text("Downloads"));file=find(By.text("official-v121-favorites.json"))}
            file!!.click(100);click(By.text("Merge"));find(By.res("backup-result"));export("before-upgrade.json")
            openSavedPost(first);menu("Browsing history");find(By.res("viewed-post-$first"))
            val recording=control(find(By.res("record-browsing-history")));if(recording.isChecked)recording.click(100)
            menu("Settings");click(By.desc("Light"));click(By.text("Blue (cyan)"))
            toggle("Wrap tags",false)
            for((label,value)in listOf("Video gestures" to false,"Allow previews on metered networks" to false,"Default mute" to false,"Default loop" to false,"Enable snapshot sharing" to false,"Load images only on unmetered networks" to true))toggle(label,value)
        }else{
            assertEquals("verify",args.getString("stage"))
            menu("Favorites");export("after-upgrade.json")
            menu("Browsing history");find(By.res("viewed-post-$first"));assertFalse(device.hasObject(By.res("record-browsing-history")))
            openSavedPost(next);menu("Browsing history");find(By.res("viewed-post-$first"));find(By.res("viewed-post-$next"))
            menu("Settings");assertTrue(control(find(By.res("accent-mint"))).isChecked)
            assertTrue(control(find(By.desc("Light"))).isChecked)
            assertFalse(control(visible(By.desc("Wrap tags"))).isChecked)
        }
    }
}
