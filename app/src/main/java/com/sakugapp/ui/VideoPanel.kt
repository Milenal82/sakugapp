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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.*
import androidx.compose.ui.layout.onSizeChanged
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
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.ui.PlayerView
import com.sakugapp.R
import com.sakugapp.data.*
import kotlinx.coroutines.*
import java.io.File
import java.util.Locale

interface VideoOrientationHost { var hasFullscreenPlayer:Boolean;var fullscreenOwner:Any?;var fullscreenHandoff:FullscreenHandoff? }

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@OptIn(ExperimentalLayoutApi::class,ExperimentalMaterial3Api::class)
@Composable
fun VideoPanel(url:String,settings:AppSettings=AppSettings(),preview:Boolean=false,seekRequest:Pair<Long,Int>?=null,
    onPlayerReady:((Player)->Unit)?=null,onDurationChanged:(Long)->Unit={},onNavigate:(Int)->Boolean={false},videoDimensions:Pair<Int,Int>?=null,downloadPost:Post?=null,maxInlineHeight:androidx.compose.ui.unit.Dp?=null) {
    val mediaKey=url+":"+downloadPost?.id
    val pageCache=LocalPageVideoCache.current
    val context=LocalContext.current;val owner=LocalLifecycleOwner.current;val scope=rememberCoroutineScope()
    val shareTitle=text(R.string.ui_share_frame)
    val activity=LocalActivity.current;val orientationHost=activity as? VideoOrientationHost
    val handoff=remember(mediaKey){if(preview)null else orientationHost?.fullscreenHandoff}
    var isPlayerLocked by rememberSaveable(mediaKey){mutableStateOf(false)}
    var hostView by remember{mutableStateOf<VideoGestureHost?>(null)}
    var controlsVisible by remember(mediaKey){mutableStateOf(false)}
    var manualRotation by remember(mediaKey){mutableStateOf(false)}
    var mediaWidth by remember(mediaKey){mutableIntStateOf(videoDimensions?.first ?: 0)}
    var mediaHeight by remember(mediaKey){mutableIntStateOf(videoDimensions?.second ?: 0)}
    var fullscreen by rememberSaveable(mediaKey){mutableStateOf(handoff!=null)}
    var more by rememberSaveable(mediaKey){mutableStateOf(false)}
    var inlineHeight by remember{mutableStateOf(300.dp)}
    var muted by rememberSaveable(mediaKey){mutableStateOf(true)}
    var loop by rememberSaveable(mediaKey){mutableStateOf(true)}
    var speed by rememberSaveable(mediaKey){mutableFloatStateOf(settings.speed)}
    var volume by rememberSaveable(mediaKey){mutableFloatStateOf(1f)}
    var position by rememberSaveable(mediaKey){mutableLongStateOf(0L)}
    var duration by remember(mediaKey){mutableLongStateOf(0L)};var nominalFps by remember(mediaKey){mutableStateOf<Float?>(null)}
    var a by rememberSaveable(mediaKey){mutableStateOf<Long?>(null)};var b by rememberSaveable(mediaKey){mutableStateOf<Long?>(null)}
    var abEnabled by rememberSaveable(mediaKey){mutableStateOf(false)};var invalidAB by remember(mediaKey){mutableStateOf(false)}
    val ab=ABLoop(a,b,abEnabled)
    var zoom by rememberSaveable(mediaKey){mutableFloatStateOf(1f)};var panX by rememberSaveable(mediaKey){mutableFloatStateOf(0f)};var panY by rememberSaveable(mediaKey){mutableFloatStateOf(0f)}
    var error by remember(mediaKey){mutableStateOf<String?>(null)}
    var view by remember{mutableStateOf<PlayerView?>(null)}
    var rendered by remember(mediaKey){mutableStateOf(false)};var playing by remember(mediaKey){mutableStateOf(false)}
    var resumeRequested by rememberSaveable(mediaKey){mutableStateOf(handoff?.play ?: preview)}
    var capturing by remember(mediaKey){mutableStateOf(false)}
    val focus=LocalFocusManager.current
    val orientation=LocalConfiguration.current.orientation
    var normalOrientation by rememberSaveable(mediaKey){mutableIntStateOf(handoff?.orientation ?: activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)}
    fun exitFullscreen(){
        orientationHost?.fullscreenHandoff=null;orientationHost?.hasFullscreenPlayer=false;orientationHost?.fullscreenOwner=null
        fullscreen=false;activity?.requestedOrientation=normalOrientation
    }
    fun enterFullscreen(){
        focus.clearFocus(force=true)
        activity?.window?.let{WindowCompat.getInsetsController(it,it.decorView).hide(WindowInsetsCompat.Type.ime())}
        normalOrientation=activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        controlsVisible=false;manualRotation=false;orientationHost?.hasFullscreenPlayer=true;fullscreen=true
    }
    val player=remember(mediaKey){ExoPlayer.Builder(context.applicationContext).apply{pageCache?.let{setMediaSourceFactory(androidx.media3.exoplayer.source.DefaultMediaSourceFactory(it.factory()))}}.setLoadControl(DefaultLoadControl.Builder()
        .setBufferDurationsMs(3000,15000,500,1000).setTargetBufferBytes((if(preview)8 else 24)*1024*1024)
        .setPrioritizeTimeOverSizeThresholds(false).build()).build().apply{
        setSeekParameters(SeekParameters.EXACT);setMediaItem(MediaItem.fromUri(url));seekTo(position);playWhenReady=handoff?.play ?: preview
    }}
    val currentRate by rememberUpdatedState(nominalFps)
    val currentLength by rememberUpdatedState(duration)
    val stepper=remember(player,mediaKey){FrameStepper(player,FrameDecoder(context.applicationContext,url,pageCache=pageCache),{currentRate},{currentLength})}
    LaunchedEffect(stepper){stepper.run()}
    LaunchedEffect(stepper.confirmed?.timeMs){stepper.confirmed?.let{position=it.timeMs}}
    fun resumePlayback(){if(stepper.pending>0)return;stepper.reset();if(player.playbackState==Player.STATE_IDLE)player.prepare();player.play()}
    val durationCallback by rememberUpdatedState(onDurationChanged)
    DisposableEffect(player,owner){
        var reportedDuration=-1L
        val listener=object:Player.Listener{
            override fun onEvents(player:Player,events:Player.Events){
                val actual=player.duration
                if(actual>0 && actual!=C.TIME_UNSET && actual!=reportedDuration){reportedDuration=actual;duration=actual;durationCallback(actual)}
                (player as? ExoPlayer)?.videoFormat?.frameRate?.takeIf{it>0 && it.isFinite()}?.let{nominalFps=it}
            }
            override fun onPlayerError(failure:PlaybackException){error=context.getString(R.string.ui_playback_failed,failure.errorCodeName)}
            override fun onRenderedFirstFrame(){rendered=true}
            override fun onVideoSizeChanged(size:VideoSize){
                if(size.width>0 && size.height>0){
                    val rotated=size.unappliedRotationDegrees%180!=0
                    mediaWidth=if(rotated)size.height else (size.width*size.pixelWidthHeightRatio).toInt()
                    mediaHeight=if(rotated)size.width else size.height
                }
            }
            override fun onPositionDiscontinuity(old:Player.PositionInfo,new:Player.PositionInfo,reason:Int){
                if(reason==Player.DISCONTINUITY_REASON_SEEK){
                    val selected=stepper.confirmed!=null || stepper.pending>0
                    val owned=stepper.expectedSeek?.let{kotlin.math.abs(it-new.positionMs)<=1}==true
                    stepper.discontinuity(new.positionMs)
                    if(selected && !owned && player.playbackState==Player.STATE_IDLE)player.prepare()
                }
            }
            override fun onPlayWhenReadyChanged(ready:Boolean,reason:Int){if(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)){if(ready && stepper.pending>0){player.pause();resumeRequested=false}else{resumeRequested=player.playWhenReady;if(ready && player.playWhenReady){stepper.reset();PlaybackResources.claim(player)}}}}
            override fun onIsPlayingChanged(isPlaying:Boolean){playing=isPlaying;if(isPlaying)PlaybackResources.claim(player)}
        }
        player.addListener(listener)
        val observer=LifecycleEventObserver{_,event->when(event){
            Lifecycle.Event.ON_PAUSE->{resumeRequested=player.playWhenReady;if(stepper.confirmed==null && player.playbackState!=Player.STATE_IDLE)position=player.currentPosition;player.pause();player.stop()}
            Lifecycle.Event.ON_RESUME->{if(stepper.confirmed==null){player.seekTo(position);if(player.playbackState==Player.STATE_IDLE)player.prepare();player.playWhenReady=resumeRequested}}
            else->Unit
        }}
        owner.lifecycle.addObserver(observer)
        onDispose{owner.lifecycle.removeObserver(observer);player.removeListener(listener)}
    }
    DisposableEffect(player){
        val cacheLease=pageCache?.retain()
        onPlayerReady?.invoke(player)
        if(player.playWhenReady)PlaybackResources.claim(player)
        if(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && player.playbackState==Player.STATE_IDLE)player.prepare()
        onDispose{if(stepper.confirmed==null && player.playbackState!=Player.STATE_IDLE)position=player.currentPosition;stepper.dispose();if(view?.player===player){view?.player=null;view=null};PlaybackResources.release(player);player.release();cacheLease?.close()}
    }
    LaunchedEffect(player,muted,loop,speed,volume,abEnabled){player.volume=if(muted)0f else volume;player.repeatMode=if(loop || abEnabled)Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF;player.setPlaybackSpeed(speed)}
    LaunchedEffect(player,ab,owner){
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED){var lastUiUpdate=0L;while(isActive){
            val now=android.os.SystemClock.elapsedRealtime()
            if(stepper.confirmed==null && stepper.pending==0 && player.playbackState!=Player.STATE_IDLE && now-lastUiUpdate>=250){position=player.currentPosition.coerceAtLeast(0);lastUiUpdate=now}
            if(player.isPlaying)ab.wrapPosition(player.currentPosition)?.let{player.seekTo(it)}
            delay(if(ab.enabled && stepper.pending==0)10 else 100)
        }}
    }
    LaunchedEffect(player,seekRequest){seekRequest?.let{player.seekTo(it.first)}}
    BackHandler(fullscreen){exitFullscreen()}
    DisposableEffect(fullscreen,activity,player){
        if(fullscreen){orientationHost?.hasFullscreenPlayer=true;orientationHost?.fullscreenOwner=player;orientationHost?.fullscreenHandoff=null}
        onDispose{if(fullscreen && orientationHost?.fullscreenOwner===player && orientationHost.fullscreenHandoff==null){
            orientationHost.hasFullscreenPlayer=false;orientationHost.fullscreenOwner=null;activity?.requestedOrientation=normalOrientation
        }}
    }
    LaunchedEffect(fullscreen,mediaWidth,mediaHeight){
        if(fullscreen && !manualRotation && mediaWidth>0 && mediaHeight>0)
            activity?.requestedOrientation=if(mediaWidth>mediaHeight)ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }
    fun navigate(delta:Int){
        if(fullscreen)orientationHost?.fullscreenHandoff=FullscreenHandoff(normalOrientation,player.playWhenReady)
        if(onNavigate(delta)){stepper.reset();player.pause();player.stop()}else{
            orientationHost?.fullscreenHandoff=null
            activity?.window?.decorView?.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            android.widget.Toast.makeText(context,R.string.ui_post_boundary,android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    val clipControls=downloadPost?.let{post->key(mediaKey){rememberClipExport(post,url,ab,stepper.pending>0,prepare={stepper.beginExport()},finished={stepper.endExport()})}}
    val primaryControls:@Composable ()->Unit={
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides androidx.compose.ui.unit.Dp.Unspecified){
            Row{
                ActionIcon(if(isPlayerLocked)R.drawable.ic_lock else R.drawable.ic_unlock,text(if(isPlayerLocked)R.string.ui_unlock_player else R.string.ui_lock_player),{
                    isPlayerLocked=!isPlayerLocked;hostView?.cancelPendingTouches();controlsVisible=false;view?.hideController();if(isPlayerLocked)more=false
                },Modifier.testTag("lock-player"),selected=isPlayerLocked,touchSize=36.dp)
                ActionIcon(if(fullscreen)R.drawable.ic_fullscreen_exit else R.drawable.ic_fullscreen,text(if(fullscreen)R.string.ui_exit_full_screen else R.string.ui_full_screen),{if(fullscreen)exitFullscreen()else enterFullscreen()},Modifier.testTag("toggle-fullscreen"),touchSize=36.dp)
                ActionIcon(R.drawable.ic_more,text(if(more)R.string.ui_fewer_controls else R.string.ui_more_controls),{more=!more},Modifier.testTag("more-controls").semantics{selected=more},selected=more,touchSize=36.dp)
            }
        }
    }
    val advancedControls:@Composable (androidx.compose.ui.unit.Dp?)->Unit={controlsHeight->
                AnimatedVisibility(more,enter=expandVertically(tween(Motion.controls)),exit=shrinkVertically(tween(Motion.controls))){
                val panelScroll=rememberScrollState()
                val compact=MaterialTheme.typography.copy(titleSmall=MaterialTheme.typography.labelLarge,bodyLarge=MaterialTheme.typography.bodySmall,bodyMedium=MaterialTheme.typography.bodySmall,labelLarge=MaterialTheme.typography.labelMedium)
                MaterialTheme(typography=compact){
                Column(Modifier.fillMaxWidth().then(if(controlsHeight!=null)Modifier.heightIn(max=controlsHeight).verticalScroll(panelScroll)else Modifier).padding(horizontal=8.dp).testTag("advanced-controls"),verticalArrangement=Arrangement.spacedBy(4.dp)){
                    if(fullscreen){ActionIcon(R.drawable.ic_rotate,text(R.string.ui_rotate_screen),{
                    manualRotation=true;activity?.requestedOrientation=if(orientation==Configuration.ORIENTATION_LANDSCAPE)ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                    },Modifier.testTag("rotate-video"))}
                    if(fullscreen)TextButton(contentPadding=textButtonPadding(),onClick={manualRotation=true;activity?.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_FULL_USER}){Text(text(R.string.ui_follow_rotation_lock))}
                    ControlGroup(text(R.string.ui_playback_speed),"speed-controls","$speed×"){
                        Row(verticalAlignment=Alignment.CenterVertically){
                            ActionIcon(if(playing)R.drawable.ic_pause else R.drawable.ic_play,text(if(playing)R.string.ui_pause_playback else R.string.ui_resume_playback),{if(player.playWhenReady)player.pause()else resumePlayback()},Modifier.testTag("dedicated-play"))
                            Text(text(R.string.ui_playback_time_value,formatClipTime(position),formatClipTime(duration)),style=MaterialTheme.typography.labelSmall)
                        }
                        if(duration>0)Slider(value=position.toFloat().coerceIn(0f,duration.toFloat()),onValueChange={stepper.reset();player.seekTo(it.toLong());if(player.playbackState==Player.STATE_IDLE)player.prepare()},valueRange=0f..duration.toFloat(),modifier=Modifier.testTag("dedicated-seek"))
                        FlowRow(horizontalArrangement=Arrangement.spacedBy(4.dp)){
                            for(value in PreferenceRules.speeds)FilterChip(selected=speed==value,onClick={speed=value},label={Text("$value×")},modifier=Modifier.testTag("speed-$value"))
                        }
                    }
                    ControlGroup(text(R.string.ui_ab_loop),"ab-controls"){
                    Text(text(R.string.ui_ab_interval,a?.let(::formatClipTime) ?: "—",b?.let(::formatClipTime) ?: "—"),Modifier.testTag("ab-interval"))
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(4.dp)){
                        TextButton(enabled=stepper.pending==0 && stepper.failure==null,contentPadding=textButtonPadding(),onClick={val updated=ab.withA((stepper.confirmed?.timeMs ?: player.currentPosition).coerceAtLeast(0));a=updated.aMs;b=updated.bMs;abEnabled=false;invalidAB=false},modifier=Modifier.testTag("set-a")){Text(text(R.string.ui_set_a))}
                        TextButton(enabled=stepper.pending==0 && stepper.failure==null,contentPadding=textButtonPadding(),onClick={try{val updated=ab.withB((stepper.confirmed?.timeMs ?: player.currentPosition).coerceAtLeast(0));b=updated.bMs;abEnabled=false;invalidAB=false}catch(_:IllegalArgumentException){b=null;abEnabled=false;invalidAB=true}},modifier=Modifier.testTag("set-b")){Text(text(R.string.ui_set_b))}
                        FilterChip(selected=abEnabled,enabled=ab.valid,onClick={if(!abEnabled){stepper.reset();if(player.playbackState==Player.STATE_IDLE)player.prepare();player.seekTo(a!!)};abEnabled=!abEnabled},modifier=Modifier.testTag("ab-toggle"),label={Text(text(if(abEnabled)R.string.ui_disable_ab else R.string.ui_enable_ab))})
                        TextButton(contentPadding=textButtonPadding(),onClick={a=null;b=null;abEnabled=false;invalidAB=false}){Text(text(R.string.ui_clear_ab))}
                    }
                    if(duration>0 && ab.valid){
                        RangeSlider(value=a!!.toFloat()..b!!.toFloat(),onValueChange={range->stepper.reset();a=range.start.toLong().coerceIn(0,duration-1);b=range.endInclusive.toLong().coerceIn(a!!+1,duration);abEnabled=false},valueRange=0f..duration.toFloat(),modifier=Modifier.testTag("ab-range"))
                    }
                    if(invalidAB)Text(text(R.string.ui_ab_invalid),color=MaterialTheme.colorScheme.error)
                    Text(text(R.string.ui_ab_precision),style=MaterialTheme.typography.labelSmall)
                    clipControls?.invoke()
                    stepper.confirmed?.let{frame->Text(text(when{frame.decoded && frame.exactTime->R.string.ui_frame_exact;frame.decoded->R.string.ui_frame_index_estimated_time;else->R.string.ui_frame_estimate}),style=MaterialTheme.typography.labelSmall,modifier=Modifier.testTag("frame-precision"))}
                    }
                    ControlGroup(text(R.string.ui_audio_display),"audio-controls"){
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(4.dp)){
                        TextButton(contentPadding=textButtonPadding(),onClick={muted=!muted}){Text(text(if(muted)R.string.ui_unmute else R.string.ui_mute))}
                        TextButton(contentPadding=textButtonPadding(),onClick={loop=!loop}){Text(text(if(loop)R.string.ui_loop_on else R.string.ui_loop_off))}
                        TextButton(contentPadding=textButtonPadding(),onClick={zoom=1f;panX=0f;panY=0f},modifier=Modifier.testTag("reset-zoom")){Text(text(R.string.ui_reset_zoom))}
                        Text(text(R.string.ui_zoom_value,String.format(Locale.ROOT,"%.2f",zoom)),Modifier.testTag("zoom-level"))
                        TextButton(contentPadding=textButtonPadding(),enabled=!capturing,onClick={
                            capturing=true
                            val bitmap=stepper.confirmed?.bitmap?.copy(Bitmap.Config.ARGB_8888,false) ?: (view?.videoSurfaceView as? TextureView)?.bitmap
                            if(bitmap==null){error=context.getString(R.string.ui_no_decoded_frame_is_available_to_capture);capturing=false}
                            else scope.launch{
                                try{val uri=withContext(Dispatchers.IO){val dir=File(context.cacheDir,"screenshots").apply{mkdirs()};val file=File(dir,"frame-${System.currentTimeMillis()}.png");file.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};FileProvider.getUriForFile(context,"${context.packageName}.files",file)}
                                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),shareTitle))
                                }catch(e:CancellationException){throw e}catch(e:Exception){error=context.getString(R.string.ui_unable_to_share_the_screenshot)}finally{bitmap.recycle();capturing=false}
                            }
                        }){Text(text(R.string.ui_snapshot))}
                    }
                    Row{Text(text(R.string.ui_volume));Slider(value=volume,onValueChange={volume=it;muted=it==0f},modifier=Modifier.weight(1f))}
                    }
                }}}
    }
    val feedback:@Composable ()->Unit={
        error?.let{message->
            Surface(shape=RoundedCornerShape(12.dp),color=MaterialTheme.colorScheme.surface){Column(Modifier.padding(8.dp)){
                Text(translateMessage(message),color=MaterialTheme.colorScheme.error)
                TextButton(contentPadding=textButtonPadding(),onClick={error=null;player.seekTo(position);player.prepare();player.play()}){Text(text(R.string.ui_retry))}
            }}
        }
    }
    val videoSurface:@Composable (Modifier)->Unit={videoModifier->
AndroidView(factory={VideoGestureHost(it)},modifier=videoModifier.clipToBounds().testTag("video-gestures").semantics{
                contentDescription=context.getString(if(rendered)R.string.ui_video_frame_rendered else R.string.ui_video_loading)
                stateDescription=context.getString(if(playing)R.string.ui_playing else R.string.ui_paused)
            },onRelease={host->if(view===host.playerView)view=null;host.playerView.player=null;host.onPostSwipe={};host.onPlaybackRequest={};host.playbackRequested={false};host.onTransform={};host.onConfirmedTap={};host.showFrame(null);host.onControlsVisibility={};if(hostView===host)hostView=null},update={host->
                hostView=host
                host.isPlayerLocked=isPlayerLocked
                host.onControlsVisibility={visible->if(!isPlayerLocked)controlsVisible=visible}
                host.showFrame(stepper.confirmed?.bitmap)
                val v=host.playerView
                v.setControllerAutoShow(false);v.setControllerHideOnTouch(false)
                v.useController=!preview && !isPlayerLocked;v.controllerShowTimeoutMs=5000
                v.setShowBuffering(if(isPlayerLocked || (fullscreen && !controlsVisible))PlayerView.SHOW_BUFFERING_NEVER else PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                val rebound=view!==v || v.player!==player
                if(view!==v){PlayerView.switchTargetView(player,view,v);view=v}else if(v.player!==player)v.player=player
                if(rebound || isPlayerLocked || preview){v.hideController();controlsVisible=false}
                v.resizeMode=androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                host.immersiveTapControl=!preview;host.onConfirmedTap={if(!isPlayerLocked){if(v.isControllerFullyVisible){v.hideController();controlsVisible=false}else{v.showController();controlsVisible=true}}}
                v.setShowNextButton(false);v.setShowPreviousButton(false);v.setShowFastForwardButton(false);v.setShowRewindButton(false)
                host.configure(settings,VideoTransform(zoom,panX,panY));host.gesturesEnabled=!preview
                host.playbackRequested={player.playWhenReady};host.onPlaybackRequest={if(it)resumePlayback()else player.pause()}
                host.onPostSwipe=::navigate;host.onTransform={zoom=it.scale;panX=it.panX;panY=it.panY}
            })
    }
    val ratio=if(mediaWidth>0 && mediaHeight>0)mediaWidth.toFloat()/mediaHeight else 16f/9f
    val toolbar:@Composable ()->Unit={
        FrameControlBar(stepper.confirmed,position,stepper.pending,duration>0 && !stepper.exporting,{delta->controlsVisible=false;hostView?.cancelPendingTouches();view?.hideController();abEnabled=false;resumeRequested=false;stepper.step(delta)},primaryControls)
        stepper.failure?.let{Text(translateMessage(it),color=MaterialTheme.colorScheme.error)}
    }
    if(fullscreen){
        Spacer(Modifier.fillMaxWidth().height(inlineHeight))
        Dialog(onDismissRequest={exitFullscreen()},properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)){
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
            BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black).displayCutoutPadding()){
                val maxPanelHeight=maxHeight*.45f
                videoSurface(if(isPlayerLocked)Modifier.fillMaxWidth().height((maxHeight-48.dp).coerceAtLeast(1.dp))else Modifier.fillMaxSize())
                if(isPlayerLocked || controlsVisible){
                    Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(MaterialTheme.colorScheme.surface)){toolbar()}
                    Column(Modifier.align(Alignment.BottomCenter).padding(bottom=48.dp).fillMaxWidth().background(MaterialTheme.colorScheme.surface)){advancedControls(maxPanelHeight);if(!isPlayerLocked || more)feedback()}
                }
            }
        }
    }else{
        val density=LocalDensity.current
        Column(Modifier.fillMaxWidth().onSizeChanged{inlineHeight=with(density){it.height.toDp()}}){
            BoxWithConstraints(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center){
                val fittedRatio=if(preview)16f/9f else ratio
                val width=if(!preview && maxInlineHeight!=null)minOf(maxWidth,maxInlineHeight*fittedRatio)else maxWidth
                videoSurface(Modifier.width(width).aspectRatio(fittedRatio).background(Color.Black))
            }
            if(!preview){toolbar();advancedControls(null)}
            feedback()
        }
    }

}

@Composable private fun ControlGroup(title:String,tag:String,value:String?=null,content:@Composable ColumnScope.()->Unit){
    Surface(modifier=Modifier.fillMaxWidth().testTag(tag),shape=RoundedCornerShape(12.dp),color=MaterialTheme.colorScheme.surfaceContainerHigh){
        Column(Modifier.padding(horizontal=textInset(8.dp),vertical=textInset(4.dp)),verticalArrangement=Arrangement.spacedBy(2.dp)){
            Row(verticalAlignment=Alignment.CenterVertically){
                Text(title,style=MaterialTheme.typography.titleSmall,modifier=Modifier.weight(1f))
                value?.let{Text(it,style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)}
            }
            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
            content()
        }
    }
}
