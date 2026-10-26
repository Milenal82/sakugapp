// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.ui

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.*
import android.view.*
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.sakugapp.R
import com.sakugapp.data.AppSettings
import com.sakugapp.data.PreferenceRules
import kotlinx.coroutines.*
import java.io.File

interface VideoOrientationHost { var hasFullscreenPlayer:Boolean }

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VideoPanel(url:String,settings:AppSettings=AppSettings(),preview:Boolean=false,seekRequest:Pair<Long,Int>?=null,onPlayerReady:((Player)->Unit)?=null,onDurationChanged:(Long)->Unit={}) {
    val context=LocalContext.current;val owner=LocalLifecycleOwner.current;val scope=rememberCoroutineScope()
    val shareTitle=text(R.string.ui_share_frame)
    var fullscreen by rememberSaveable(url){mutableStateOf(false)}
    var frames by remember{mutableStateOf(false)}
    var muted by rememberSaveable(url){mutableStateOf(if(preview)true else settings.muted)}
    var loop by rememberSaveable(url){mutableStateOf(settings.loop)}
    var speed by rememberSaveable(url){mutableFloatStateOf(settings.speed)}
    var volume by rememberSaveable(url){mutableFloatStateOf(1f)}
    var position by rememberSaveable(url){mutableLongStateOf(0L)}
    var error by remember(url){mutableStateOf<String?>(null)}
    var view by remember{mutableStateOf<PlayerView?>(null)}
    var rendered by remember(url){mutableStateOf(false)}
    var playing by remember(url){mutableStateOf(false)}
    var speedMenu by remember{mutableStateOf(false)}
    val activity=LocalActivity.current
    val focus=LocalFocusManager.current
    val orientation=LocalConfiguration.current.orientation
    var normalOrientation by rememberSaveable(url){mutableIntStateOf(activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)}
    fun exitFullscreen(){
        activity?.requestedOrientation=normalOrientation
        fullscreen=false
    }
    fun enterFullscreen(){
        focus.clearFocus(force=true)
        activity?.window?.let{WindowCompat.getInsetsController(it,it.decorView).hide(WindowInsetsCompat.Type.ime())}
        normalOrientation=activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        fullscreen=true
    }
    val player=remember(url){ExoPlayer.Builder(context.applicationContext).build().apply{
        setMediaItem(MediaItem.fromUri(url));seekTo(position);prepare();playWhenReady=preview
    }}
    DisposableEffect(player,owner){
        onPlayerReady?.invoke(player)
        var reportedDuration=-1L
        val listener=object:Player.Listener{
            override fun onEvents(player:Player,events:Player.Events){val actual=player.duration;if(actual>0 && actual!=C.TIME_UNSET && actual!=reportedDuration){reportedDuration=actual;onDurationChanged(actual)}}
            override fun onPlayerError(failure:PlaybackException){error=context.getString(R.string.ui_playback_failed,failure.errorCodeName)}
            override fun onRenderedFirstFrame(){rendered=true}
            override fun onIsPlayingChanged(isPlaying:Boolean){playing=isPlaying}
        }
        player.addListener(listener)
        val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_STOP){position=player.currentPosition;player.pause()}}
        owner.lifecycle.addObserver(observer)
        onDispose{position=player.currentPosition;owner.lifecycle.removeObserver(observer);player.removeListener(listener);player.release()}
    }
    LaunchedEffect(muted,loop,speed,volume){player.volume=if(muted)0f else volume;player.repeatMode=if(loop)Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF;player.setPlaybackSpeed(speed)}
    LaunchedEffect(seekRequest){seekRequest?.let{player.seekTo(it.first)}}
    BackHandler(fullscreen){exitFullscreen()}
    DisposableEffect(fullscreen,activity){
        (activity as? VideoOrientationHost)?.hasFullscreenPlayer=fullscreen
        val restoreOnDispose=fullscreen
        val restoreOrientation=normalOrientation
        if(fullscreen)activity?.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_FULL_USER
        onDispose{if(restoreOnDispose){(activity as? VideoOrientationHost)?.hasFullscreenPlayer=false;activity?.requestedOrientation=restoreOrientation}}
    }
    fun openFrames(){position=player.currentPosition;player.pause();player.stop();frames=true}
    val content: @Composable (Modifier) -> Unit = { modifier ->
        Column(modifier.background(Color.Black)){
            AndroidView(factory={LayoutInflater.from(it).inflate(R.layout.native_player,android.widget.FrameLayout(it),false) as PlayerView},
                modifier=Modifier.fillMaxWidth().weight(1f).semantics{contentDescription=context.getString(if(rendered)R.string.ui_video_frame_rendered else R.string.ui_video_loading);stateDescription=context.getString(if(playing)R.string.ui_playing else R.string.ui_paused)},onRelease={if(view===it)view=null;it.player=null},update={v->
                    view=v;v.player=player;v.useController=true;v.setShowNextButton(false);v.setShowPreviousButton(false)
                    (v.videoSurfaceView as? TextureView)?.apply{
                        scaleX=if(settings.mirror)-1f else 1f
                        val matrix=ColorMatrix().apply{setSaturation(settings.saturation)}
                        val c=settings.contrast;val offset=(1-c)*128+(settings.brightness-1)*255
                        matrix.postConcat(ColorMatrix(floatArrayOf(c,0f,0f,0f,offset,0f,c,0f,0f,offset,0f,0f,c,0f,offset,0f,0f,0f,1f,0f)))
                        setLayerType(View.LAYER_TYPE_HARDWARE,Paint().apply{colorFilter=ColorMatrixColorFilter(matrix)})
                    }
                })
            if(!preview){
                if(!fullscreen)OutlinedButton(onClick=::openFrames,modifier=Modifier.fillMaxWidth().padding(horizontal=8.dp).testTag("open-frame-controls")){
                    Text(text(R.string.ui_frame_by_frame))
                }
                FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)){
                    TextButton(onClick={muted=!muted}){Text(if(muted)text(R.string.ui_unmute)else text(R.string.ui_mute))}
                    TextButton(onClick={loop=!loop}){Text(if(loop)text(R.string.ui_loop_on)else text(R.string.ui_loop_off))}
                    TextButton(onClick={if(fullscreen)exitFullscreen()else enterFullscreen()}){Text(if(fullscreen)text(R.string.ui_exit_full_screen)else text(R.string.ui_full_screen))}
                    if(fullscreen){
                        TextButton(onClick={activity?.requestedOrientation=if(orientation==Configuration.ORIENTATION_LANDSCAPE)ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE},modifier=Modifier.testTag("rotate-video")){Text(text(R.string.ui_rotate_screen))}
                        TextButton(onClick={activity?.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_FULL_USER}){Text(text(R.string.ui_follow_rotation_lock))}
                        Box{TextButton(onClick={speedMenu=true}){Text("$speed×")};DropdownMenu(expanded=speedMenu,onDismissRequest={speedMenu=false}){for(value in PreferenceRules.speeds)DropdownMenuItem(text={Text("$value×")},onClick={speed=value;speedMenu=false})}}
                    }
                    if(settings.screenshot)TextButton(onClick={
                        val bitmap=(view?.videoSurfaceView as? TextureView)?.bitmap
                        if(bitmap==null)error=context.getString(R.string.ui_no_decoded_frame_is_available_to_capture)
                        else scope.launch{
                            try{
                                val uri=withContext(Dispatchers.IO){val dir=File(context.cacheDir,"screenshots").apply{mkdirs()};val file=File(dir,"frame-${System.currentTimeMillis()}.png");file.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};FileProvider.getUriForFile(context,"${context.packageName}.files",file)}
                                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),shareTitle))
                            }catch(e:Exception){error=context.getString(R.string.ui_unable_to_share_the_screenshot)}finally{bitmap.recycle()}
                        }
                    }){Text(text(R.string.ui_snapshot))}
                }
                if(!fullscreen)FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly){
                    for(value in PreferenceRules.speeds)TextButton(onClick={speed=value}){Text(if(speed==value)"[$value×]"else"$value×")}
                }
                if(!fullscreen)Row(Modifier.padding(horizontal=12.dp)){
                    Text(text(R.string.ui_volume));Slider(value=volume,onValueChange={volume=it;muted=it==0f},modifier=Modifier.weight(1f))
                }
            }
            error?.let{message->Text(translateMessage(message),color=MaterialTheme.colorScheme.error,modifier=Modifier.padding(8.dp));TextButton(onClick={error=null;player.prepare();player.play()}){Text(text(R.string.ui_retry))}}
        }
    }
    androidx.compose.material3.MaterialTheme(colorScheme=appColors(false)){
    if(fullscreen){Dialog(onDismissRequest={exitFullscreen()},properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)){
        BackHandler{exitFullscreen()}
        val window=(LocalView.current.parent as? DialogWindowProvider)?.window
        DisposableEffect(window){
            val dialog=window?.callback as? android.app.Dialog
            dialog?.setOnKeyListener{_,code,event->
                if(code==KeyEvent.KEYCODE_BACK){
                    if(event.action==KeyEvent.ACTION_UP && !event.isCanceled)exitFullscreen()
                    true
                }else false
            }
            val controller=window?.let{WindowCompat.getInsetsController(it,it.decorView)}
            controller?.systemBarsBehavior=WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
            onDispose{dialog?.setOnKeyListener(null);controller?.show(WindowInsetsCompat.Type.systemBars())}
        }
        content(Modifier.fillMaxSize().displayCutoutPadding())
    };Text(text(R.string.ui_video_is_in_full_screen))}
    else content(if(preview)Modifier.fillMaxWidth().aspectRatio(16f/9f) else Modifier.fillMaxWidth().height(480.dp))
    if(frames)StrictFrameDialog(url){frames=false;player.seekTo(position);player.prepare()}
    }
}
