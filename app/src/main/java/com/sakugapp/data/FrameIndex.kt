// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.data

/** A decoded frame index, never an estimate calculated from timestamps or an assumed FPS. */
data class FrameIndex(val count:Int,val index:Int=0) {
    init { require(count>0 && index in 0 until count) }
    fun move(delta:Int) = copy(index=(index.toLong()+delta).coerceIn(0L,count.toLong()-1).toInt())
}
