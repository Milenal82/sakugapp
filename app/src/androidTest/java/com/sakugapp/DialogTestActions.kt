// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.espresso.*
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import org.hamcrest.Matcher

/** Read the dialog's own window, not the inactive Activity window behind its IME. */
internal fun dialogImeHeight():Int {
 // During the IME back-callback handoff a visible dialog can temporarily lack focus.
 // Reading its insets must not enter Espresso's focused-root picker and freeze that handoff.
 if(android.os.Build.VERSION.SDK_INT>=29){
  var height=-1
  androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync{
   val dialog=android.view.inspector.WindowInspector.getGlobalWindowViews().lastOrNull{view->
    view.isAttachedToWindow && (view.layoutParams as? android.view.WindowManager.LayoutParams)?.type==android.view.WindowManager.LayoutParams.TYPE_APPLICATION
   }
   height=dialog?.let{ViewCompat.getRootWindowInsets(it)?.getInsets(WindowInsetsCompat.Type.ime())?.bottom} ?: -1
  }
  return height
 }
 var height=0
 Espresso.onView(isRoot()).inRoot(isDialog()).perform(object:ViewAction{
  override fun getConstraints():Matcher<View> = isRoot()
  override fun getDescription()="Read keyboard insets from the focused dialog"
  override fun perform(controller:UiController,view:View){height=ViewCompat.getRootWindowInsets(view)?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0}
 })
 return height
}
