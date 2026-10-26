// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.ui

/** Accumulates consumed scroll only, with a separate threshold in each direction. */
internal class ScrollChrome(private val hideDistance:Float,private val showDistance:Float) {
    private var distance=0f
    fun consume(delta:Float,visible:Boolean):Boolean {
        if(delta==0f)return visible
        if((distance>0 && delta<0)||(distance<0 && delta>0))distance=0f
        distance+=delta
        if(visible && distance<=-hideDistance){distance=0f;return false}
        if(!visible && distance>=showDistance){distance=0f;return true}
        return visible
    }
    fun reset(){distance=0f}
}
