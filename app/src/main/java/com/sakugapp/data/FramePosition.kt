// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.data

import kotlin.math.floor
import kotlin.math.roundToLong

/** Verified timestamps take priority. FPS describes an estimate, never a decoded index. */
object FramePosition {
    fun fps(reported:Float?):Double=reported?.takeIf{it.isFinite() && it>0 && it<=240}?.toDouble() ?: 24.0
    fun index(timeMs:Long,fps:Double)=floor(timeMs.coerceAtLeast(0)*fps/1000).toInt().coerceAtLeast(0)
    fun count(durationMs:Long,fps:Double)=kotlin.math.ceil(durationMs.coerceAtLeast(1)*fps/1000).toInt().coerceAtLeast(1)
    fun time(index:Int,fps:Double,durationMs:Long)=((index.coerceAtLeast(0)*1000.0/fps).roundToLong()).coerceIn(0,(durationMs-1).coerceAtLeast(0))
    fun move(index:Int,delta:Int,count:Int)=(index.toLong()+delta).coerceIn(0,(count-1).coerceAtLeast(0).toLong()).toInt()
}
