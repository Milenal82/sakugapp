// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.data

/** Time-based loop only; decoder seeking and polling do not provide frame-accurate A–B playback. */
data class ABLoop(val aMs:Long?=null,val bMs:Long?=null,val enabled:Boolean=false){
    val valid:Boolean get()=aMs!=null && bMs!=null && aMs>=0 && aMs<bMs
    fun withA(value:Long):ABLoop {require(value>=0);return copy(aMs=value,bMs=bMs?.takeIf{it>value},enabled=false)}
    fun withB(value:Long):ABLoop {require(aMs!=null && value>aMs);return copy(bMs=value,enabled=false)}
    fun activate(value:Boolean)=copy(enabled=value && valid)
    fun wrapPosition(position:Long):Long?=if(enabled && valid && (position>=bMs!! || position<aMs!!))aMs else null
}
/** A presentation-order mapping exists only when the actual sample timestamps match decoded frame count. */
class FrameTimeline private constructor(private val timesUs:LongArray){
    val size get()=timesUs.size
    fun indexAt(positionMs:Long):Int {
        val match=timesUs.binarySearch(positionMs.coerceAtLeast(0)*1000)
        return if(match>=0)match else (-match-2).coerceIn(0,timesUs.lastIndex)
    }
    fun timeMs(index:Int):Long=timesUs[index]/1000
    companion object {
        fun verified(samples:List<Long>,decodedFrames:Int):FrameTimeline?{
            if(decodedFrames !in 1..200000 || samples.size!=decodedFrames || samples.any{it<0})return null
            val sorted=samples.distinct().sorted()
            if(sorted.size!=decodedFrames || sorted.firstOrNull()!=0L)return null
            return FrameTimeline(sorted.toLongArray())
        }
    }
}
data class VideoTransform(val scale:Float=1f,val panX:Float=0f,val panY:Float=0f){
    fun bounded():VideoTransform {val s=scale.takeIf{it.isFinite()}?.coerceIn(1f,5f) ?: 1f;if(s==1f)return VideoTransform();val limit=(s-1)/2;return VideoTransform(s,panX.takeIf{it.isFinite()}?.coerceIn(-limit,limit) ?: 0f,panY.takeIf{it.isFinite()}?.coerceIn(-limit,limit) ?: 0f)}
}
