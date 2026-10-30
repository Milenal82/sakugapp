// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.ui

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.view.*
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.*
import androidx.core.content.FileProvider
import androidx.core.view.*
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.ui.PlayerView
import com.sakugapp.R
import com.sakugapp.data.*
import kotlinx.coroutines.*
import java.io.File
import java.util.Locale

interface VideoOrientationHost { var hasFullscreenPlayer:Boolean }

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VideoPanel(url:String,settings:AppSettings=AppSettings(),preview:Boolean=false,seekRequest:Pair<Long,Int>?=null,
    onPlayerReady:((Player)->Unit)?=null,onDurationChanged:(Long)->Unit={},onNavigate:(Int)->Boolean={false}) {
    val context=LocalContext.current;val owner=LocalLifecycleOwner.current;val scope=rememberCoroutineScope()
    val shareTitle=text(R.string.ui_share_frame)
    var fullscreen by rememberSaveable(url){mutableStateOf(false)}
    var more by rememberSaveable(url){mutableStateOf(false)}
    var frames by remember(url){mutableStateOf(false)};var frameStep by remember(url){mutableIntStateOf(0)};var frameStart by remember(url){mutableLongStateOf(0)}
    var muted by rememberSaveable(url){mutableStateOf(if(preview)true else settings.muted)}
    var loop by rememberSaveable(url){mutableStateOf(settings.loop)}
    var speed by rememberSaveable(url){mutableFloatStateOf(settings.speed)}
    var volume by rememberSaveable(url){mutableFloatStateOf(1f)}
    var position by rememberSaveable(url){mutableLongStateOf(0L)}
    var duration by remember(url){mutableLongStateOf(0L)};var nominalFps by remember(url){mutableStateOf<Float?>(null)}
    var a by rememberSaveable(url){mutableStateOf<Long?>(null)};var b by rememberSaveable(url){mutableStateOf<Long?>(null)}
    var abEnabled by rememberSaveable(url){mutableStateOf(false)};var invalidAB by remember(url){mutableStateOf(false)}
    val ab=ABLoop(a,b,abEnabled)
    var zoom by rememberSaveable(url){mutableFloatStateOf(1f)};var panX by rememberSaveable(url){mutableFloatStateOf(0f)};var panY by rememberSaveable(url){mutableFloatStateOf(0f)}
    var error by remember(url){mutableStateOf<String?>(null)}
    var view by remember{mutableStateOf<PlayerView?>(null)}
    var rendered by remember(url){mutableStateOf(false)};var playing by remember(url){mutableStateOf(false)}
    val activity=LocalActivity.current;val focus=LocalFocusManager.current
    val orientation=LocalConfiguration.current.orientation
    var normalOrientation by rememberSaveable(url){mutableIntStateOf(activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)}
    fun exitFullscreen(){activity?.requestedOrientation=normalOrientation;fullscreen=false}
    fun enterFullscreen(){
        focus.clearFocus(force=true)
        activity?.window?.let{WindowCompat.getInsetsController(it,it.decorView).hide(WindowInsetsCompat.Type.ime())}
        normalOrientation=activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;fullscreen=true
    }
    val player=remember(url){ExoPlayer.Builder(context.applicationContext).build().apply{
        setSeekParameters(SeekParameters.EXACT);setMediaItem(MediaItem.fromUri(url));seekTo(position);prepare();playWhenReady=preview
    }}
    val durationCallback by rememberUpdatedState(onDurationChanged)
    DisposableEffect(player,owner){
        onPlayerReady?.invoke(player);var reportedDuration=-1L
        val listener=object:Player.Listener{
            override fun onEvents(player:Player,events:Player.Events){
                val actual=player.duration
                if(actual>0 && actual!=C.TIME_UNSET && actual!=reportedDuration){reportedDuration=actual;duration=actual;durationCallback(actual)}
                nominalFps=(player as? ExoPlayer)?.videoFormat?.frameRate?.takeIf{it>0 && it.isFinite()}
            }
            override fun onPlayerError(failure:PlaybackException){error=context.getString(R.string.ui_playback_failed,failure.errorCodeName)}
            override fun onRenderedFirstFrame(){rendered=true}
            override fun onIsPlayingChanged(isPlaying:Boolean){playing=isPlaying}
        }
        player.addListener(listener)
        val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_STOP){position=player.currentPosition;player.pause()}}
        owner.lifecycle.addObserver(observer)
        onDispose{position=player.currentPosition;owner.lifecycle.removeObserver(observer);player.removeListener(listener);player.release()}
    }
    LaunchedEffect(muted,loop,speed,volume,abEnabled){player.volume=if(muted)0f else volume;player.repeatMode=if(loop || abEnabled)Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF;player.setPlaybackSpeed(speed)}
    LaunchedEffect(player,ab){
        while(isActive){
            if(!frames)position=player.currentPosition.coerceAtLeast(0)
            if(player.isPlaying)ab.wrapPosition(player.currentPosition)?.let{player.seekTo(it)}
            delay(if(ab.enabled && !frames)30 else 250)
        }
    }
    LaunchedEffect(seekRequest){seekRequest?.let{player.seekTo(it.first)}}
    BackHandler(fullscreen){exitFullscreen()}
    DisposableEffect(fullscreen,activity){
        (activity as? VideoOrientationHost)?.hasFullscreenPlayer=fullscreen
        val restoreOnDispose=fullscreen;val restoreOrientation=normalOrientation
        if(fullscreen)activity?.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_FULL_USER
        onDispose{if(restoreOnDispose){(activity as? VideoOrientationHost)?.hasFullscreenPlayer=false;activity?.requestedOrientation=restoreOrientation}}
    }
    fun openFrames(delta:Int=0){frameStart=player.currentPosition;frameStep=delta;player.pause();player.stop();if(fullscreen)exitFullscreen();frames=true}
    fun navigate(delta:Int){
        if(onNavigate(delta)){player.pause();player.stop()}else{
            activity?.window?.decorView?.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            android.widget.Toast.makeText(context,R.string.ui_post_boundary,android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    val content: @Composable (Modifier) -> Unit = { modifier ->
        BoxWithConstraints(modifier.background(if(fullscreen || preview)Color.Black else MaterialTheme.colorScheme.surface)){
        val controlsHeight=if(fullscreen)maxHeight*0.45f else 260.dp
        Column(Modifier.fillMaxSize()){
            AndroidView(factory={VideoGestureHost(it)},modifier=Modifier.fillMaxWidth().weight(1f).testTag("video-gestures").semantics{
                contentDescription=context.getString(if(rendered)R.string.ui_video_frame_rendered else R.string.ui_video_loading)
                stateDescription=context.getString(if(playing)R.string.ui_playing else R.string.ui_paused)
            },onRelease={host->if(view===host.playerView)view=null;host.playerView.player=null;host.onPostSwipe={};host.onPlaybackRequest={};host.playbackRequested={false};host.onTransform={}},update={host->
                val v=host.playerView;view=v;v.player=player;v.useController=!preview
                v.setShowNextButton(false);v.setShowPreviousButton(false);v.setShowFastForwardButton(false);v.setShowRewindButton(false)
                host.configure(settings,VideoTransform(zoom,panX,panY));host.gesturesEnabled=settings.gestures && !preview
                host.playbackRequested={player.playWhenReady};host.onPlaybackRequest={if(it)player.play()else player.pause()}
                host.onPostSwipe=::navigate;host.onTransform={zoom=it.scale;panX=it.panX;panY=it.panY}
            })
            if(!preview){
                FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)){
                    TextButton(contentPadding=textButtonPadding(),onClick={if(fullscreen)exitFullscreen()else enterFullscreen()}){Text(text(if(fullscreen)R.string.ui_exit_full_screen else R.string.ui_full_screen))}
                    TextButton(contentPadding=textButtonPadding(),onClick={more=!more},modifier=Modifier.testTag("more-controls")){Text(text(if(more)R.string.ui_fewer_controls else R.string.ui_more_controls))}
                    if(fullscreen){
                        TextButton(contentPadding=textButtonPadding(),onClick={activity?.requestedOrientation=if(orientation==Configuration.ORIENTATION_LANDSCAPE)ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE},modifier=Modifier.testTag("rotate-video")){Text(text(R.string.ui_rotate_screen))}
                        TextButton(contentPadding=textButtonPadding(),onClick={activity?.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_FULL_USER}){Text(text(R.string.ui_follow_rotation_lock))}
                    }
                }
                AnimatedVisibility(more,enter=expandVertically(tween(Motion.controls)),exit=shrinkVertically(tween(Motion.controls))){
                Column(Modifier.fillMaxWidth().heightIn(max=controlsHeight).verticalScroll(rememberScrollState()).padding(horizontal=8.dp).testTag("advanced-controls"),verticalArrangement=Arrangement.spacedBy(8.dp)){
                    ControlGroup(text(R.string.ui_playback_speed),"speed-controls","$speed×"){
                        FlowRow(horizontalArrangement=Arrangement.spacedBy(4.dp)){
                            for(value in PreferenceRules.speeds)FilterChip(selected=speed==value,onClick={speed=value},label={Text("$value×")},modifier=Modifier.heightIn(min=40.dp).testTag("speed-$value"))
                        }
                    }
                    ControlGroup(text(R.string.ui_ab_loop),"ab-controls"){
                    Text(text(R.string.ui_ab_interval,a?.let(::mediaTime) ?: "—",b?.let(::mediaTime) ?: "—"),Modifier.testTag("ab-interval"))
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(4.dp)){
                        TextButton(contentPadding=textButtonPadding(),onClick={val updated=ab.withA(player.currentPosition.coerceAtLeast(0));a=updated.aMs;b=updated.bMs;abEnabled=false;invalidAB=false},modifier=Modifier.testTag("set-a")){Text(text(R.string.ui_set_a))}
                        TextButton(contentPadding=textButtonPadding(),onClick={try{val updated=ab.withB(player.currentPosition.coerceAtLeast(0));b=updated.bMs;abEnabled=false;invalidAB=false}catch(_:IllegalArgumentException){invalidAB=true}},modifier=Modifier.testTag("set-b")){Text(text(R.string.ui_set_b))}
                        FilterChip(selected=abEnabled,enabled=ab.valid,onClick={abEnabled=!abEnabled},modifier=Modifier.heightIn(min=40.dp).testTag("ab-toggle"),label={Text(text(if(abEnabled)R.string.ui_disable_ab else R.string.ui_enable_ab))})
                        TextButton(contentPadding=textButtonPadding(),onClick={a=null;b=null;abEnabled=false;invalidAB=false}){Text(text(R.string.ui_clear_ab))}
                    }
                    if(duration>0 && ab.valid){
                        RangeSlider(value=a!!.toFloat()..b!!.toFloat(),onValueChange={range->a=range.start.toLong().coerceIn(0,duration-1);b=range.endInclusive.toLong().coerceIn(a!!+1,duration);abEnabled=false},valueRange=0f..duration.toFloat(),modifier=Modifier.testTag("ab-range"))
                    }
                    if(invalidAB)Text(text(R.string.ui_ab_invalid),color=MaterialTheme.colorScheme.error)
                    Text(text(R.string.ui_ab_precision),style=MaterialTheme.typography.labelSmall)
                    }
                    ControlGroup(text(R.string.ui_frame_analysis),"frame-controls"){
                    Text(text(R.string.ui_playback_time_value,mediaTime(position),if(duration>0)mediaTime(duration)else"—"),style=MaterialTheme.typography.labelMedium,modifier=Modifier.testTag("analysis-time"))
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(4.dp)){
                        OutlinedButton(contentPadding=textButtonPadding(),onClick={openFrames()},modifier=Modifier.testTag("open-frame-controls")){Text(text(R.string.ui_frame_by_frame))}
                        TextButton(contentPadding=textButtonPadding(),onClick={openFrames(-1)}){Text(text(R.string.ui_previous_frame))}
                        TextButton(contentPadding=textButtonPadding(),onClick={openFrames(1)}){Text(text(R.string.ui_next_frame))}
                    }
                    nominalFps?.let{Text(text(R.string.ui_nominal_fps,String.format(Locale.ROOT,"%.3f",it)),style=MaterialTheme.typography.labelSmall)}
                    }
                    ControlGroup(text(R.string.ui_audio_display),"audio-controls"){
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(4.dp)){
                        TextButton(contentPadding=textButtonPadding(),onClick={muted=!muted}){Text(text(if(muted)R.string.ui_unmute else R.string.ui_mute))}
                        TextButton(contentPadding=textButtonPadding(),onClick={loop=!loop}){Text(text(if(loop)R.string.ui_loop_on else R.string.ui_loop_off))}
                        TextButton(contentPadding=textButtonPadding(),onClick={zoom=1f;panX=0f;panY=0f},modifier=Modifier.testTag("reset-zoom")){Text(text(R.string.ui_reset_zoom))}
                        Text(text(R.string.ui_zoom_value,String.format(Locale.ROOT,"%.2f",zoom)),Modifier.testTag("zoom-level"))
                        if(settings.screenshot)TextButton(contentPadding=textButtonPadding(),onClick={
                            val bitmap=(view?.videoSurfaceView as? TextureView)?.bitmap
                            if(bitmap==null)error=context.getString(R.string.ui_no_decoded_frame_is_available_to_capture)
                            else scope.launch{
                                try{val uri=withContext(Dispatchers.IO){val dir=File(context.cacheDir,"screenshots").apply{mkdirs()};val file=File(dir,"frame-${System.currentTimeMillis()}.png");file.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};FileProvider.getUriForFile(context,"${context.packageName}.files",file)}
                                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),shareTitle))
                                }catch(e:CancellationException){throw e}catch(e:Exception){error=context.getString(R.string.ui_unable_to_share_the_screenshot)}finally{bitmap.recycle()}
                            }
                        }){Text(text(R.string.ui_snapshot))}
                    }
                    Row{Text(text(R.string.ui_volume));Slider(value=volume,onValueChange={volume=it;muted=it==0f},modifier=Modifier.weight(1f))}
                    }
                }}
            }
            error?.let{message->Text(translateMessage(message),color=MaterialTheme.colorScheme.error,modifier=Modifier.padding(8.dp));TextButton(contentPadding=textButtonPadding(),onClick={error=null;player.prepare();player.play()}){Text(text(R.string.ui_retry))}}
        }}
    }
    val panelColors=if(fullscreen || preview)appColors(false)else MaterialTheme.colorScheme
    MaterialTheme(colorScheme=panelColors){
        if(fullscreen){Dialog(onDismissRequest={exitFullscreen()},properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)){
            BackHandler{exitFullscreen()}
            val window=(LocalView.current.parent as? DialogWindowProvider)?.window
            DisposableEffect(window){
                val dialog=window?.callback as? android.app.Dialog
                dialog?.setOnKeyListener{_,code,event->if(code==KeyEvent.KEYCODE_BACK){if(event.action==KeyEvent.ACTION_UP && !event.isCanceled)exitFullscreen();true}else false}
                val controller=window?.let{WindowCompat.getInsetsController(it,it.decorView)}
                controller?.isAppearanceLightStatusBars=false;controller?.isAppearanceLightNavigationBars=false
                controller?.systemBarsBehavior=WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE;controller?.hide(WindowInsetsCompat.Type.systemBars())
                onDispose{dialog?.setOnKeyListener(null);controller?.show(WindowInsetsCompat.Type.systemBars())}
            }
            content(Modifier.fillMaxSize().displayCutoutPadding())
        };Text(text(R.string.ui_video_is_in_full_screen))}
        else content(if(preview)Modifier.fillMaxWidth().aspectRatio(16f/9f)else Modifier.fillMaxWidth().animateContentSize(tween(Motion.controls)).height(if(more)480.dp else 300.dp))
        if(frames)StrictFrameDialog(url,startPositionMs=frameStart,initialDelta=frameStep,onFrameTime={position=it}){frames=false;player.seekTo(position);player.prepare()}
    }
}

@Composable private fun ControlGroup(title:String,tag:String,value:String?=null,content:@Composable ColumnScope.()->Unit){
    Surface(modifier=Modifier.fillMaxWidth().testTag(tag),shape=RoundedCornerShape(12.dp),color=MaterialTheme.colorScheme.surfaceContainerHigh){
        Column(Modifier.padding(textInset(10.dp)),verticalArrangement=Arrangement.spacedBy(4.dp)){
            Row(verticalAlignment=Alignment.CenterVertically){
                Text(title,style=MaterialTheme.typography.titleSmall,modifier=Modifier.weight(1f))
                value?.let{Text(it,style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)}
            }
            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
            content()
        }
    }
}
