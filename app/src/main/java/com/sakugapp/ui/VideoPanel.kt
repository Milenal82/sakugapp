// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.ui

import android.content.Intent
import android.graphics.*
import android.view.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
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

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VideoPanel(url:String,settings:AppSettings=AppSettings(),preview:Boolean=false,seekRequest:Pair<Long,Int>?=null) {
    val context=LocalContext.current;val owner=LocalLifecycleOwner.current;val scope=rememberCoroutineScope()
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
    val player=remember(url){ExoPlayer.Builder(context.applicationContext).build().apply{
        setMediaItem(MediaItem.fromUri(url));seekTo(position);prepare();playWhenReady=preview
    }}
    DisposableEffect(player,owner){
        val listener=object:Player.Listener{
            override fun onPlayerError(failure:PlaybackException){error="Playback failed: ${failure.errorCodeName}"}
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
    BackHandler(fullscreen){fullscreen=false}
    val content: @Composable (Modifier) -> Unit = { modifier ->
        Column(modifier.background(Color.Black)){
            AndroidView(factory={LayoutInflater.from(it).inflate(R.layout.native_player,android.widget.FrameLayout(it),false) as PlayerView},
                modifier=Modifier.fillMaxWidth().weight(1f).semantics{contentDescription=if(rendered)"Video frame rendered"else"Video loading";stateDescription=if(playing)"Playing"else"Paused"},onRelease={it.player=null;view=null},update={v->
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
                FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(4.dp)){
                    TextButton(onClick={muted=!muted}){Text(if(muted)text("Unmute","ミュート解除")else text("Mute","ミュート"))}
                    TextButton(onClick={loop=!loop}){Text(if(loop)text("Loop on","ループ：オン")else text("Loop off","ループ：オフ"))}
                    TextButton(onClick={fullscreen=!fullscreen}){Text(if(fullscreen)text("Exit full screen","全画面解除")else text("Full screen","全画面"))}
                    TextButton(onClick={position=player.currentPosition;player.pause();player.stop();frames=true}){Text(text("Exact frames","フレーム解析"))}
                    if(settings.screenshot)TextButton(onClick={
                        val bitmap=(view?.videoSurfaceView as? TextureView)?.bitmap
                        if(bitmap==null)error="No decoded frame is available to capture."
                        else scope.launch{
                            try{
                                val uri=withContext(Dispatchers.IO){val dir=File(context.cacheDir,"screenshots").apply{mkdirs()};val file=File(dir,"frame-${System.currentTimeMillis()}.png");file.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};FileProvider.getUriForFile(context,"${context.packageName}.files",file)}
                                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),"Share frame"))
                            }catch(e:Exception){error="Unable to share the screenshot."}finally{bitmap.recycle()}
                        }
                    }){Text(text("Snapshot","スクリーンショット"))}
                }
                FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly){
                    for(value in PreferenceRules.speeds)TextButton(onClick={speed=value}){Text(if(speed==value)"[$value×]"else"$value×")}
                }
                Row(Modifier.padding(horizontal=12.dp)){
                    Text(text("Volume","音量"));Slider(value=volume,onValueChange={volume=it;muted=it==0f},modifier=Modifier.weight(1f))
                }
            }
            error?.let{message->Text(message,color=MaterialTheme.colorScheme.error,modifier=Modifier.padding(8.dp));TextButton(onClick={error=null;player.prepare();player.play()}){Text(text("Retry","再試行"))}}
        }
    }
    if(fullscreen){Dialog(onDismissRequest={fullscreen=false},properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)){content(Modifier.fillMaxSize().systemBarsPadding())};Text(text("Video is in full screen","動画は全画面表示中です"))}
    else content(if(preview)Modifier.fillMaxWidth().aspectRatio(16f/9f) else Modifier.fillMaxWidth().height(480.dp))
    if(frames)StrictFrameDialog(url){frames=false;player.seekTo(position);player.prepare()}
}
