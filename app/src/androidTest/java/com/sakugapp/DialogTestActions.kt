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
 var height=0
 Espresso.onView(isRoot()).inRoot(isDialog()).perform(object:ViewAction{
  override fun getConstraints():Matcher<View> = isRoot()
  override fun getDescription()="Read keyboard insets from the focused dialog"
  override fun perform(controller:UiController,view:View){height=ViewCompat.getRootWindowInsets(view)?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: 0}
 })
 return height
}
