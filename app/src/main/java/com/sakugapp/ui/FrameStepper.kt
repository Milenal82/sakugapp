// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.ui

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.runtime.*
import androidx.media3.common.Player
import com.sakugapp.data.FramePosition
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal data class ConfirmedFrame(val index:Int,val count:Int,val timeMs:Long,val decoded:Boolean,val exactTime:Boolean,val bitmap:Bitmap?)
internal class FrameStepper(private val player:Player,private val decoder:FrameDecoder,private val fps:()->Float?,private val duration:()->Long){
    var confirmed by mutableStateOf<ConfirmedFrame?>(null);private set
    var pending by mutableIntStateOf(0);private set
    var exporting by mutableStateOf(false);private set
    var failure by mutableStateOf<String?>(null);private set
    private var disposed=false
    private var epoch=0
    private val queue=Channel<Pair<Int,Int>>(Channel.UNLIMITED)
    private var opened=false;private var count:Int?=null
    var expectedSeek:Long?=null;private set
    fun step(delta:Int){if(disposed || exporting)return;require(delta in -3..3 && delta!=0);player.pause();pending++;queue.trySend(delta to epoch)}
    suspend fun beginExport()=withContext(Dispatchers.Main.immediate){exporting=true;epoch++;pending=0;decoder.close();opened=false;count=null}
    fun endExport(){exporting=false}
    fun dispose(){disposed=true;epoch++;pending=0;confirmed=null;queue.close()}
    fun reset(){epoch++;pending=0;confirmed=null;failure=null;expectedSeek=null;queue.trySend(0 to epoch)}
    fun discontinuity(position:Long){if(expectedSeek?.let{kotlin.math.abs(it-position)<=1}==true)expectedSeek=null else reset()}
    suspend fun run()=withContext(Dispatchers.Main.immediate){try{for((delta,generation)in queue){
        if(disposed)break
        if(generation!=epoch)continue
        if(delta==0){decoder.close();opened=false;count=null;continue}
        player.pause();player.stop()
        try{
            if(!opened){opened=true;if(Build.VERSION.SDK_INT>=28)try{count=decoder.open()}catch(e:CancellationException){throw e}catch(_:Exception){decoder.close();count=null}}
            if(generation!=epoch)continue
            val rate=FramePosition.fps(fps());val length=duration().coerceAtLeast(1)
            val n=count ?: FramePosition.count(length,rate)
            val base=confirmed?.index ?: decoder.indexAt(player.currentPosition) ?: FramePosition.index(player.currentPosition,rate)
            val target=FramePosition.move(base,delta,n)
            val time=decoder.frameTimeMs(target) ?: FramePosition.time(target,rate,length)
            val bitmap=if(count!=null && Build.VERSION.SDK_INT>=28)decoder.frame(target)else null
            if(generation!=epoch)continue
            if(bitmap!=null){expectedSeek=time;player.seekTo(time)}else seekAndRender(time)
            if(generation!=epoch)continue
            confirmed=ConfirmedFrame(target,n,time,bitmap!=null,decoder.hasTimeline,bitmap)
            failure=null
        }catch(e:CancellationException){throw e}catch(e:Exception){failure=e.message;confirmed=null}
        finally{if(!disposed){if(generation==epoch)pending=(pending-1).coerceAtLeast(0);player.pause()}}
    }}finally{queue.close();withContext(NonCancellable){decoder.close()}}}
    private suspend fun seekAndRender(time:Long){
        val completed=withTimeoutOrNull(15000){suspendCancellableCoroutine<Unit>{continuation->
            val listener=object:Player.Listener{
                override fun onRenderedFirstFrame(){if(!disposed)player.removeListener(this);if(continuation.isActive)continuation.resume(Unit)}
                override fun onPlayerError(error:androidx.media3.common.PlaybackException){if(!disposed)player.removeListener(this);if(continuation.isActive)continuation.resumeWithException(error)}
            }
            player.addListener(listener)
            continuation.invokeOnCancellation{if(!disposed)player.removeListener(listener)}
            expectedSeek=time;player.seekTo(time);player.prepare();player.pause()
        }}
        check(completed!=null){"The device decoder could not return this exact frame."}
    }
}
