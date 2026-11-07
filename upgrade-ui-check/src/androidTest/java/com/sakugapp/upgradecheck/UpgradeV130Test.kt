// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.upgradecheck

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern

/** Public native UI and document pickers only; independent from app-internal IDs and debug access. */
@RunWith(AndroidJUnit4::class)
class UpgradeV130Test {
 private val device=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
 private val args get()=InstrumentationRegistry.getArguments()
 private val legacy get()=args.getString("stage")=="seed"
 private fun find(selector:BySelector)=checkNotNull(device.wait(Until.findObject(selector),20000)){"Missing native control: $selector"}
 private fun click(selector:BySelector){repeat(3){attempt->try{find(selector).click(100);return}catch(e:StaleObjectException){if(attempt==2)throw e;device.waitForIdle()}}}
 private fun visible(selector:BySelector):UiObject2{
  repeat(18){device.findObject(selector)?.takeIf{it.visibleBounds.height()>0}?.let{return it};device.swipe(device.displayWidth/2,(device.displayHeight*.8).toInt(),device.displayWidth/2,(device.displayHeight*.3).toInt(),22)}
  error("Cannot scroll to $selector")
 }
 private fun control(node:UiObject2):UiObject2{var current:UiObject2?=node;repeat(5){val row=checkNotNull(current);if(row.isCheckable)return row;current=row.parent};error("Checkable control missing")}
 private fun menu(route:String){
  click(if(legacy)By.text("Menu")else By.res("main-menu"))
  if(legacy)click(By.text(when(route){"library"->"Favorites";"history"->"Browsing history";"settings"->"Settings";else->error(route)}))
  // Compose popup windows do not inherit the Activity's test-tag resource-ID marker.
  // Use the visible native menu labels, just as the legacy UI checks do.
  else click(By.text(when(route){"library"->"Folders";"settings"->"Settings";"search_home"->"Search";else->error(route)}))
 }
 private fun library(){menu("library");repeat(3){device.findObject(By.res("folder-back"))?.click(100)}}
 private fun export(name:String){visible(By.res("export-favorites")).click(100);find(By.clazz("android.widget.EditText")).text=name;click(By.text(Pattern.compile("(?i)save")));find(By.text("Exported"))}
 private fun post(id:String){library();visible(By.text("Upgrade A")).click(100);visible(By.res("post-media-$id")).click(100);find(By.res("detail-post-id"))}
 private fun history(){if(legacy)menu("history")else{menu("search_home");visible(By.res("home-browsing")).click(100)}}
 @Test fun officialV122DataAndNewLocalProfileSurviveNativeUpgradeAndRestart(){
  device.wakeUp();val first=args.getString("firstId")!!;val next=args.getString("nextId")!!
  when(args.getString("stage")){
   "seed"->{
    library();click(By.res("import-favorites"))
    var file=device.wait(Until.findObject(By.text("official-v122-favorites.json")),5000)
    if(file==null){device.findObject(By.desc("Show roots"))?.click();click(By.text("Downloads"));file=find(By.text("official-v122-favorites.json"))}
    file!!.click(100);click(By.text("Merge"));find(By.res("backup-result"));export("before-upgrade.json")
    post(first);history();find(By.res("viewed-post-$first"))
    menu("settings");click(By.desc("Light"));click(By.res("accent-mint"))
    val wrap=control(visible(By.desc("Wrap tags")));if(wrap.isChecked)wrap.click(100)
    repeat(30){if(!control(find(By.desc("Wrap tags"))).isChecked)return;Thread.sleep(100)};error("Wrap value not saved")
   }
   "verify"->{
    library();export("after-upgrade.json");history();find(By.res("viewed-post-$first"))
    post(next);history();find(By.res("viewed-post-$first"));find(By.res("viewed-post-$next"))
    menu("settings");assertTrue(control(find(By.res("accent-mint"))).isChecked);assertTrue(control(find(By.desc("Light"))).isChecked)
    assertFalse(control(visible(By.desc("Wrap tags"))).isChecked)
    menu("search_home");click(By.res("nav-profile"));click(By.res("edit-nickname"))
    find(By.clazz("android.widget.EditText")).text="Upgrade profile";click(By.text("Save edit"))
    assertTrue(device.wait(Until.gone(By.clazz("android.widget.EditText")),10000));find(By.text("Upgrade profile"))
   }
   "restart"->{
    menu("search_home");click(By.res("nav-profile"));find(By.text("Upgrade profile"))
    library();find(By.res("tag-favorites-entry"))
    menu("settings");assertTrue(control(find(By.res("accent-mint"))).isChecked);assertTrue(control(find(By.desc("Light"))).isChecked)
   }
   else->error("Unknown stage")
  }
 }
}
