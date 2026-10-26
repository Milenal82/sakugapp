// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.ui

import android.content.Context
import android.graphics.*
import android.view.*
import android.widget.FrameLayout
import androidx.media3.ui.PlayerView
import com.sakugapp.R
import com.sakugapp.data.AppSettings
import com.sakugapp.data.VideoTransform
import kotlin.math.abs

/** Gestures affect only the video surface. Native transport controls and OS edge gestures keep ownership. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class VideoGestureHost(context:Context):FrameLayout(context){
    val playerView=LayoutInflater.from(context).inflate(R.layout.native_player,this,false) as PlayerView
    var gesturesEnabled=true
    var onPostSwipe:(Int)->Unit={}
    var onPlaybackRequest:(Boolean)->Unit={}
    var playbackRequested:()->Boolean={false}
    var onTransform:(VideoTransform)->Unit={}
    var transform=VideoTransform();private set
    private var settings:AppSettings?=null
    private val density=resources.displayMetrics.density
    private var downX=0f;private var downY=0f;private var lastX=0f;private var lastY=0f
    private var excluded=false;private var intercepting=false;private var multitouch=false;private var doubled=false
    private var baselinePlaying=false
    private val touchSlop=ViewConfiguration.get(context).scaledTouchSlop
    private val taps=GestureDetector(context,object:GestureDetector.SimpleOnGestureListener(){
        override fun onDown(event:MotionEvent)=true
        override fun onDoubleTap(event:MotionEvent):Boolean{
            if(!gesturesEnabled || excluded)return false
            doubled=true;return true
        }
    })
    private val scales=ScaleGestureDetector(context,object:ScaleGestureDetector.SimpleOnScaleGestureListener(){
        override fun onScaleBegin(detector:ScaleGestureDetector):Boolean{multitouch=true;intercepting=true;return !excluded && gesturesEnabled}
        override fun onScale(detector:ScaleGestureDetector):Boolean{
            change(transform.copy(scale=transform.scale*detector.scaleFactor));return true
        }
    })
    init{addView(playerView,LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.MATCH_PARENT));clipChildren=true}
    fun configure(value:AppSettings,requested:VideoTransform){
        gesturesEnabled=value.gestures
        val changed=settings?.let{listOf(it.mirror,it.saturation,it.contrast,it.brightness)!=listOf(value.mirror,value.saturation,value.contrast,value.brightness)} ?: true
        settings=value;transform=requested.bounded();applySurface(changed)
    }
    private fun change(value:VideoTransform){transform=value.bounded();applySurface(false);onTransform(transform)}
    private fun applySurface(effects:Boolean){
        (playerView.videoSurfaceView as? TextureView)?.apply{
            scaleX=transform.scale*(if(settings?.mirror==true)-1f else 1f);scaleY=transform.scale
            translationX=transform.panX*width;translationY=transform.panY*height
            if(effects){
                val s=settings ?: return@apply
                val matrix=ColorMatrix().apply{setSaturation(s.saturation)}
                val c=s.contrast;val offset=(1-c)*128+(s.brightness-1)*255
                matrix.postConcat(ColorMatrix(floatArrayOf(c,0f,0f,0f,offset,0f,c,0f,0f,offset,0f,0f,c,0f,offset,0f,0f,0f,1f,0f)))
                setLayerType(View.LAYER_TYPE_HARDWARE,Paint().apply{colorFilter=ColorMatrixColorFilter(matrix)})
            }
        }
    }
    override fun onLayout(changed:Boolean,l:Int,t:Int,r:Int,b:Int){super.onLayout(changed,l,t,r,b);applySurface(false)}
    private fun touchesProgress(event:MotionEvent):Boolean{
        val bar=playerView.findViewById<View>(androidx.media3.ui.R.id.exo_progress) ?: return false
        val rect=Rect();if(!bar.getGlobalVisibleRect(rect))return false
        rect.inset((-12*density).toInt(),(-16*density).toInt())
        return rect.contains(event.rawX.toInt(),event.rawY.toInt())
    }
    override fun dispatchTouchEvent(event:MotionEvent):Boolean{
        if(!gesturesEnabled)return super.dispatchTouchEvent(event)
        if(event.actionMasked==MotionEvent.ACTION_DOWN){
            downX=event.x;downY=event.y;lastX=event.x;lastY=event.y
            intercepting=false;multitouch=false;doubled=false
            excluded=event.x<24*density || event.x>width-24*density || touchesProgress(event)
        }
        if(!excluded){
            taps.onTouchEvent(event)
            if(event.actionMasked==MotionEvent.ACTION_DOWN && !doubled)baselinePlaying=playbackRequested()
            scales.onTouchEvent(event)
            if(event.pointerCount>1){multitouch=true;intercepting=true}
            if(event.actionMasked==MotionEvent.ACTION_MOVE && event.pointerCount==1){
                val dx=event.x-lastX;val dy=event.y-lastY
                if(transform.scale>1.01f){
                    if(abs(event.x-downX)+abs(event.y-downY)>touchSlop){intercepting=true
                        val surface=playerView.videoSurfaceView
                        change(transform.copy(panX=transform.panX+dx/(surface?.width?.coerceAtLeast(1) ?: width.coerceAtLeast(1)),panY=transform.panY+dy/(surface?.height?.coerceAtLeast(1) ?: height.coerceAtLeast(1))))
                    }
                }else if(!multitouch && abs(event.x-downX)>64*density && abs(event.x-downX)>abs(event.y-downY)*1.5f)intercepting=true
            }
            if(event.actionMasked==MotionEvent.ACTION_UP && doubled && !multitouch && abs(event.x-downX)+abs(event.y-downY)<=touchSlop)onPlaybackRequest(!baselinePlaying)
            if(event.actionMasked==MotionEvent.ACTION_UP && intercepting && !multitouch && !doubled && transform.scale<=1.01f && abs(event.x-downX)>64*density && abs(event.x-downX)>abs(event.y-downY)*1.5f){
                val direction=if(event.x<downX)1 else -1;post{onPostSwipe(direction)}
            }
        }
        if(event.actionMasked==MotionEvent.ACTION_POINTER_UP && event.pointerCount==2){val remaining=if(event.actionIndex==0)1 else 0;lastX=event.getX(remaining);lastY=event.getY(remaining)}else{lastX=event.x;lastY=event.y}
        val result=super.dispatchTouchEvent(event)
        if(event.actionMasked==MotionEvent.ACTION_UP || event.actionMasked==MotionEvent.ACTION_CANCEL){intercepting=false;multitouch=false;doubled=false}
        return result
    }
    override fun onInterceptTouchEvent(event:MotionEvent)=gesturesEnabled && !excluded && (intercepting || doubled)
    override fun onTouchEvent(event:MotionEvent):Boolean{if(event.actionMasked==MotionEvent.ACTION_UP)performClick();return gesturesEnabled}
    override fun performClick():Boolean=super.performClick()
}
