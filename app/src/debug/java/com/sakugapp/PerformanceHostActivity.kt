// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.sakugapp.ui.FullscreenHandoff
import com.sakugapp.ui.VideoOrientationHost

/** Instrumentation installs the real composables with deterministic repositories, without a live startup request. */
class PerformanceHostActivity:AppCompatActivity(),VideoOrientationHost {
    override var hasFullscreenPlayer=false
    override var fullscreenOwner:Any?=null
    override var fullscreenHandoff:FullscreenHandoff?=null
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);enableEdgeToEdge()}
}
