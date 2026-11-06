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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
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
@Composable internal fun QuizRoute(app:AppViewModel,model:QuizViewModel,managePool:()->Unit={}){
    val s by model.state.collectAsStateWithLifecycle();val config by app.quizSettings.collectAsStateWithLifecycle()
    val scope=rememberCoroutineScope()
    LaunchedEffect(model){model.resume()}
    LifecycleEventEffect(Lifecycle.Event.ON_START){model.resume()}
    BackHandler(s.started){model.home()}
    LifecycleEventEffect(Lifecycle.Event.ON_STOP){model.stopLoading()}
    Box(Modifier.fillMaxSize(),contentAlignment=Alignment.TopCenter){Column(Modifier.widthIn(max=700.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp).testTag("quiz-page"),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Text(text(R.string.ui_quiz_title),style=MaterialTheme.typography.titleLarge)
        if(!s.started){
            Text(text(R.string.ui_quiz_intro),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text(R.string.ui_question_count),style=MaterialTheme.typography.titleSmall)
            FlowRow{QuizSettings.counts.forEach{count->FilterChip(selected=config.count==count,onClick={scope.launch{app.preferences.quizCount(count)}},label={Text(count.toString())})}}
            FeatureSurface(Modifier.fillMaxWidth().testTag("quiz-pool-open"),onClick=managePool){
                Row(Modifier.padding(FeatureStyle.inset),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){
                    Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_book),null,Modifier.size(22.dp),tint=MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f)){Text(text(R.string.ui_quiz_work_range),style=MaterialTheme.typography.titleSmall);Text(if(config.custom)text(R.string.ui_quiz_scope_custom,config.works.size)else text(R.string.ui_all_works),style=MaterialTheme.typography.bodySmall)}
                }
            }
            if(config.custom && config.works.isEmpty())Text(text(R.string.ui_quiz_choose_work),style=MaterialTheme.typography.bodySmall)
            Button(enabled=!config.custom || config.works.isNotEmpty(),onClick={model.start(config)},modifier=Modifier.testTag("start-quiz")){Text(text(R.string.ui_quiz_start))}
        }else if(s.result){
            val right=s.answers.count{it.right};val accuracy=QuizGenerator.accuracy(s.answers)
            val percent=accuracy?.let{String.format(java.util.Locale.ROOT,"%.1f",it)} ?: "—"
            Column(Modifier.fillMaxWidth().testTag("quiz-summary"),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)){
                Text(text(R.string.ui_quiz_accuracy),style=MaterialTheme.typography.titleSmall)
                Box(Modifier.size(104.dp),contentAlignment=Alignment.Center){
                    CircularProgressIndicator(progress={(accuracy ?: 0.0).toFloat()/100f},modifier=Modifier.fillMaxSize(),strokeWidth=6.dp)
                    Text(if(accuracy==null)percent else "$percent%",style=MaterialTheme.typography.titleLarge)
                }
                Text(text(R.string.ui_quiz_counts,right,s.answers.size-right,s.answers.size),style=MaterialTheme.typography.bodyMedium)
            }
            OutlinedButton(onClick={model.start(s.settings)}){Text(text(R.string.ui_quiz_restart))};TextButton(onClick=model::home){Text(text(R.string.ui_quiz_home))}
        }else{
            Row(verticalAlignment=Alignment.CenterVertically){ActionIcon(R.drawable.ic_back,text(R.string.ui_quiz_home),model::home);Text(text(R.string.ui_quiz_question,s.answers.size+(if(s.selected==null)1 else 0),s.settings.count),style=MaterialTheme.typography.titleSmall)}
            LinearProgressIndicator(progress={s.answers.size.toFloat()/s.settings.count},modifier=Modifier.fillMaxWidth().height(3.dp).testTag("quiz-progress"))
            if(s.loading)LinearProgressIndicator(Modifier.fillMaxWidth())
            s.error?.let{Text(extensionError(it),color=MaterialTheme.colorScheme.error);TextButton(onClick={if(s.used.isEmpty())model.start(s.settings)else model.next()}){Text(text(R.string.ui_retry))};if(s.answers.isNotEmpty())TextButton(onClick=model::results){Text(text(R.string.ui_quiz_results))};TextButton(onClick={model.home();managePool()}){Text(text(R.string.ui_pool_manage))};TextButton(onClick=model::home){Text(text(R.string.ui_quiz_change_count))}}
            s.question?.let{q->key(q.post.id){
                var playable by remember{mutableStateOf(false)};var mediaFailed by remember{mutableStateOf(false)}
                QuizVideo(q.post,{playable=it},{mediaFailed=it})
                if(mediaFailed){Text(text(R.string.ui_quiz_media_error),color=MaterialTheme.colorScheme.error);TextButton(onClick=model::next){Text(text(R.string.ui_quiz_skip))}}
                Text(text(R.string.ui_quiz_prompt))
                EqualAnswerGrid(Modifier.fillMaxWidth().testTag("quiz-answers")){
                    q.options.forEachIndexed{index,option->
                        val revealed=s.selected!=null;val correct=revealed && option==q.correct;val wrong=revealed && option==s.selected && !correct
                        val light=MaterialTheme.colorScheme.background.luminance()>.5f
                        val background=when{correct->if(light)Color(0xFFD8EDDB)else Color(0xFF263F2D);wrong->MaterialTheme.colorScheme.errorContainer;else->MaterialTheme.colorScheme.surfaceContainerHigh}
                        val foreground=when{correct->if(light)Color(0xFF244F32)else Color(0xFFCFEAD4);wrong->MaterialTheme.colorScheme.onErrorContainer;else->MaterialTheme.colorScheme.onSurface}
                        Surface(onClick={model.choose(option)},enabled=playable && !revealed,modifier=Modifier.testTag("quiz-option-$index"),shape=FeatureStyle.shape,color=background,contentColor=foreground){
                            Column(Modifier.padding(textInset(12.dp)),verticalArrangement=Arrangement.Center){Text(option,style=MaterialTheme.typography.bodyMedium);if(correct||wrong)Text(text(if(correct)R.string.ui_quiz_correct else R.string.ui_quiz_wrong),style=MaterialTheme.typography.labelSmall)}
                        }
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
    BoxWithConstraints(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center){AndroidView(factory={PlayerView(it).apply{this.player=player;controllerAutoShow=false}},update={it.player=player},onRelease={it.player=null},modifier=Modifier.width(minOf(maxWidth,320.dp*ratio)).aspectRatio(ratio).clip(FeatureStyle.shape).testTag("quiz-video"))}
    Row{TextButton(onClick={muted=!muted}){Text(text(if(muted)R.string.ui_unmute else R.string.ui_mute))};TextButton(onClick={loop=!loop}){Text(text(if(loop)R.string.ui_loop_on else R.string.ui_loop_off))};if(error)TextButton(onClick={error=false;failedNow(false);player.prepare();player.play()}){Text(text(R.string.ui_retry))}}
}

/** Match all four heights to the largest real text measurement, including feedback and font scaling. */
@Composable private fun EqualAnswerGrid(modifier:Modifier=Modifier,content:@Composable ()->Unit){
    Layout(content,modifier){children,constraints->
        val gap=8.dp.roundToPx();val width=((constraints.maxWidth-gap)/2).coerceAtLeast(1)
        val height=children.maxOfOrNull{it.maxIntrinsicHeight(width)}.orEmptyHeight(64.dp.roundToPx())
        val cells=children.map{it.measure(Constraints.fixed(width,height))}
        layout(constraints.maxWidth,((cells.size+1)/2)*height+if(cells.size>2)gap else 0){
            cells.forEachIndexed{index,cell->cell.placeRelative((index%2)*(width+gap),(index/2)*(height+gap))}
        }
    }
}
private fun Int?.orEmptyHeight(min:Int)=(this ?: min).coerceAtLeast(min)
