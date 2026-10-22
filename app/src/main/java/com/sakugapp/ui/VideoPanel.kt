// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun VideoPanel(url: String) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var fullscreen by rememberSaveable(url) { mutableStateOf(false) }
    var muted by rememberSaveable(url) { mutableStateOf(false) }
    var loop by rememberSaveable(url) { mutableStateOf(false) }
    var position by rememberSaveable(url) { mutableLongStateOf(0L) }
    var error by remember(url) { mutableStateOf<String?>(null) }
    val player = remember(url) {
        ExoPlayer.Builder(context.applicationContext).build().apply {
            setMediaItem(MediaItem.fromUri(url)); seekTo(position); prepare()
            playWhenReady = false // No automatic downloads/playback beyond normal buffering.
        }
    }
    DisposableEffect(player, owner) {
        val listener = object : Player.Listener {
            override fun onPlayerError(failure: PlaybackException) {
                error = "视频播放失败（${failure.errorCodeName}）。可能是网络、访问限制或设备解码能力。"
            }
        }
        player.addListener(listener)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) { position = player.currentPosition; player.pause() }
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            position = player.currentPosition
            owner.lifecycle.removeObserver(observer); player.removeListener(listener); player.release()
        }
    }
    LaunchedEffect(muted, loop) {
        player.volume = if (muted) 0f else 1f
        player.repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }
    BackHandler(fullscreen) { fullscreen = false }
    val content: @Composable (Modifier) -> Unit = { modifier ->
        Column(modifier.background(Color.Black)) {
            AndroidView(
                factory = { PlayerView(it).apply {
                    this.player = player; useController = true
                    setShowNextButton(false); setShowPreviousButton(false)
                } },
                modifier = Modifier.fillMaxWidth().weight(1f),
                update = { it.player = player },
                onRelease = { it.player = null },
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton(onClick = { muted = !muted }) { Text(if (muted) "取消静音" else "静音") }
                TextButton(onClick = { loop = !loop }) { Text(if (loop) "关闭循环" else "循环") }
                TextButton(onClick = { fullscreen = !fullscreen }) { Text(if (fullscreen) "退出全屏" else "全屏") }
            }
            error?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp))
                TextButton(onClick = { error = null; player.prepare(); player.play() }) { Text("重试播放") }
            }
        }
    }
    if (fullscreen) {
        Dialog(onDismissRequest = { fullscreen = false }, properties = DialogProperties(
            usePlatformDefaultWidth = false, decorFitsSystemWindows = false,
        )) { content(Modifier.fillMaxSize().systemBarsPadding()) }
        Text("视频正在全屏显示")
    } else content(Modifier.fillMaxWidth().height(300.dp))
}
