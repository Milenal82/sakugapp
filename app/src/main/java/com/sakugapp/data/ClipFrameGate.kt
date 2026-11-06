// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.data

import android.content.Context
import androidx.media3.common.GlObjectsProvider
import androidx.media3.common.GlTextureInfo
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import androidx.media3.effect.PassthroughShaderProgram

/** Decode through the next GOP for B-frame references; only selected presentation frames reach the encoder. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class ClipFrameGate(private val range:ClipRange,private val inputStartMs:Long):GlEffect {
    val accepted=java.util.Collections.synchronizedList(mutableListOf<Long>())
    override fun toGlShaderProgram(context:Context,useHdr:Boolean):GlShaderProgram=object:PassthroughShaderProgram(){
        override fun queueInputFrame(provider:GlObjectsProvider,texture:GlTextureInfo,presentationTimeUs:Long){
            val sourceTime=presentationTimeUs+inputStartMs*1000
            if(sourceTime>=range.startMs*1000 && sourceTime<range.endMs*1000){
                accepted+=sourceTime
                super.queueInputFrame(provider,texture,sourceTime-range.startMs*1000)
            }else{inputListener.onInputFrameProcessed(texture);inputListener.onReadyToAcceptInputFrame()}
        }
    }
}
