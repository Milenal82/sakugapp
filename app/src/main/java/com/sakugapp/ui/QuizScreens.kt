// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.*
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.sakugapp.R
import com.sakugapp.data.*
import kotlinx.coroutines.*

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun QuizRoute(app:AppViewModel,model:QuizViewModel){
    val s by model.state.collectAsStateWithLifecycle();val config by app.quizSettings.collectAsStateWithLifecycle()
    LaunchedEffect(model){model.resume()}
    LifecycleEventEffect(Lifecycle.Event.ON_START){model.resume()}
    BackHandler(s.started){model.home()}
    LifecycleEventEffect(Lifecycle.Event.ON_STOP){model.stopLoading()}
    Box(Modifier.fillMaxSize(),contentAlignment=Alignment.TopCenter){Column(Modifier.widthIn(max=700.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp).testTag("quiz-page"),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Text(text(R.string.ui_quiz_title),style=MaterialTheme.typography.titleLarge)
        if(!s.started){
            Text(text(R.string.ui_quiz_intro));Text(text(R.string.ui_question_count))
            FlowRow{QuizSettings.counts.forEach{count->FilterChip(selected=config.count==count,onClick={app.quizSettings(config.copy(count=count))},label={Text(count.toString())})}}
            FlowRow{FilterChip(selected=!config.custom,onClick={app.quizSettings(config.copy(custom=false))},label={Text(text(R.string.ui_all_works))});FilterChip(selected=config.custom,onClick={app.quizSettings(config.copy(custom=true))},label={Text(text(R.string.ui_custom_pool))})}
            if(config.custom){
                TagLookup(app.discovery,TagCategory.COPYRIGHT){tag->if(tag !in config.works && config.works.size<20)app.quizSettings(config.copy(works=config.works+tag))}
                config.works.forEach{tag->Row(verticalAlignment=Alignment.CenterVertically){Text(tag,Modifier.weight(1f));ActionIcon(R.drawable.ic_close,text(R.string.ui_delete),{app.quizSettings(config.copy(works=config.works-tag))})}}
                TextButton(onClick={app.quizSettings(config.copy(works=emptyList()))}){Text(text(R.string.ui_clear_pool))}
            }
            Button(enabled=!config.custom || config.works.isNotEmpty(),onClick={model.start(config)},modifier=Modifier.testTag("start-quiz")){Text(text(R.string.ui_quiz_start))}
        }else if(s.result){
            val right=s.answers.count{it.right};val percent=QuizGenerator.accuracy(s.answers)?.let{String.format(java.util.Locale.ROOT,"%.1f",it)} ?: "0"
            Text(text(R.string.ui_quiz_summary,right,s.answers.size-right,s.answers.size,percent),modifier=Modifier.testTag("quiz-summary"))
            OutlinedButton(onClick={model.start(s.settings)}){Text(text(R.string.ui_quiz_restart))};TextButton(onClick=model::home){Text(text(R.string.ui_quiz_home))}
        }else{
            Text(text(R.string.ui_quiz_question,s.answers.size+(if(s.selected==null)1 else 0),s.settings.count))
            if(s.loading)LinearProgressIndicator(Modifier.fillMaxWidth())
            s.error?.let{Text(extensionError(it),color=MaterialTheme.colorScheme.error);TextButton(onClick={if(s.used.isEmpty())model.start(s.settings)else model.next()}){Text(text(R.string.ui_retry))};if(s.answers.isNotEmpty())TextButton(onClick=model::results){Text(text(R.string.ui_quiz_results))}}
            s.question?.let{q->key(q.post.id){
                var playable by remember{mutableStateOf(false)};var mediaFailed by remember{mutableStateOf(false)}
                QuizVideo(q.post,{playable=it},{mediaFailed=it})
                if(mediaFailed){Text(text(R.string.ui_quiz_media_error),color=MaterialTheme.colorScheme.error);TextButton(onClick=model::next){Text(text(R.string.ui_quiz_skip))}}
                Text(text(R.string.ui_quiz_prompt))
                q.options.forEachIndexed{index,option->
                    val revealed=s.selected!=null;val correct=revealed && option==q.correct;val wrong=revealed && option==s.selected && !correct
                    val background=when{correct->Color(0xFF286C39);wrong->Color(0xFFB3261E);else->MaterialTheme.colorScheme.surfaceContainerHigh}
                    Surface(onClick={model.choose(option)},enabled=playable && !revealed,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("quiz-option-$index"),shape=MaterialTheme.shapes.medium,color=background,contentColor=if(correct||wrong)Color.White else MaterialTheme.colorScheme.onSurface){
                        Row(Modifier.padding(12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){Text(option,Modifier.weight(1f));if(correct||wrong)Text(text(if(correct)R.string.ui_quiz_correct else R.string.ui_quiz_wrong))}
                    }
                }
                if(s.selected!=null)OutlinedButton(onClick=model::next,modifier=Modifier.testTag("quiz-next")){Text(text(if(s.answers.size>=s.settings.count)R.string.ui_quiz_results else R.string.ui_quiz_next))}
            }}
        }
    }}
}

/** A single muted loop player; no Post metadata or answer-bearing title is sent to PlayerView. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable internal fun QuizVideo(post:Post,ready:(Boolean)->Unit,failed:(Boolean)->Unit){
    val context=LocalContext.current;val cache=LocalPageVideoCache.current
    var position by rememberSaveable(post.id){mutableLongStateOf(0)};var muted by rememberSaveable(post.id){mutableStateOf(true)};var loop by rememberSaveable(post.id){mutableStateOf(true)};var error by remember(post.id){mutableStateOf(false)}
    val readyNow by rememberUpdatedState(ready);val failedNow by rememberUpdatedState(failed)
    val lease=remember(post.id,cache){cache?.retain()}
    val player=remember(post.id,cache){ExoPlayer.Builder(context.applicationContext).apply{cache?.let{setMediaSourceFactory(DefaultMediaSourceFactory(it.factory()))}}.build().apply{volume=0f;repeatMode=Player.REPEAT_MODE_ONE;setMediaItem(MediaItem.fromUri(post.fileUrl!!));seekTo(position);prepare();playWhenReady=true}}
    DisposableEffect(player){val listener=object:Player.Listener{override fun onRenderedFirstFrame(){readyNow(true);failedNow(false)};override fun onPlayerError(e:PlaybackException){error=true;readyNow(false);failedNow(true)}};player.addListener(listener);onDispose{player.removeListener(listener);player.release();lease?.close()}}
    LaunchedEffect(player){while(true){position=player.currentPosition.coerceAtLeast(0);delay(250)}}
    LifecycleEventEffect(Lifecycle.Event.ON_STOP){position=player.currentPosition.coerceAtLeast(0);player.pause()}
    SideEffect{player.volume=if(muted)0f else 1f;player.repeatMode=if(loop)Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF}
    val ratio=if(post.width!=null && post.height!=null && post.width>0 && post.height>0)post.width.toFloat()/post.height else 16f/9f
    BoxWithConstraints(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center){AndroidView(factory={PlayerView(it).apply{this.player=player;controllerAutoShow=false}},update={it.player=player},onRelease={it.player=null},modifier=Modifier.width(minOf(maxWidth,320.dp*ratio)).aspectRatio(ratio).testTag("quiz-video"))}
    Row{TextButton(onClick={muted=!muted}){Text(text(if(muted)R.string.ui_unmute else R.string.ui_mute))};TextButton(onClick={loop=!loop}){Text(text(if(loop)R.string.ui_loop_on else R.string.ui_loop_off))};if(error)TextButton(onClick={error=false;failedNow(false);player.prepare();player.play()}){Text(text(R.string.ui_retry))}}
}
