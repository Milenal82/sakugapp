// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sakugapp.R
import com.sakugapp.data.*

@OptIn(ExperimentalMaterial3Api::class,ExperimentalLayoutApi::class)
@Composable fun BatchQueuePanel(queue:BatchDownloadQueue,dismiss:()->Unit){
    val state by queue.state.collectAsStateWithLifecycle();val error by queue.error.collectAsStateWithLifecycle()
    val context=LocalContext.current
    ModalBottomSheet(onDismissRequest=dismiss){
        LazyColumn(Modifier.fillMaxWidth().heightIn(max=620.dp).testTag("batch-queue"),contentPadding=PaddingValues(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            item{
                Column(verticalArrangement=Arrangement.spacedBy(6.dp)){
                    Text(text(R.string.ui_download_queue),style=MaterialTheme.typography.titleMedium)
                    val complete=state.tasks.count{it.status==BatchStatus.COMPLETE}
                    Text("$complete / ${state.tasks.size}",style=MaterialTheme.typography.labelMedium)
                    LinearProgressIndicator(progress={if(state.tasks.isEmpty())0f else state.tasks.sumOf{if(it.status==BatchStatus.COMPLETE)1.0 else if(it.total>0)(it.bytes.toDouble()/it.total).coerceIn(0.0,1.0)else 0.0}.toFloat()/state.tasks.size},modifier=Modifier.fillMaxWidth())
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                        OutlinedButton(onClick={if(state.paused)queue.resume()else queue.pause()}){Text(text(if(state.paused)R.string.ui_resume_batch else R.string.ui_pause_batch))}
                        TextButton(onClick={queue.cancel()}){Text(text(R.string.ui_cancel_download))}
                    }
                    Text(text(R.string.ui_batch_parallelism,state.parallelism),style=MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){(1..3).forEach{count->FilterChip(selected=state.parallelism==count,onClick={queue.parallelism(count)},label={Text(count.toString())})}}
                    error?.let{Text(if(it=="start")text(R.string.ui_queue_start_error)else libraryError(context,LibraryFailure(it)),color=MaterialTheme.colorScheme.error);TextButton(onClick=queue::foreground){Text(text(R.string.ui_retry))}}
                    if(state.tasks.isEmpty())Text(text(R.string.ui_queue_empty))
                }
            }
            state.tasks.asReversed().groupBy{it.batch}.forEach{(batch,tasks)->
            item(key="batch-$batch"){
                val completed=tasks.count{it.status==BatchStatus.COMPLETE}
                Text(text(R.string.ui_download_batch_progress,completed,tasks.size),style=MaterialTheme.typography.titleSmall)
                LinearProgressIndicator(progress={tasks.sumOf{if(it.status==BatchStatus.COMPLETE)1.0 else if(it.total>0)(it.bytes.toDouble()/it.total).coerceIn(0.0,1.0)else 0.0}.toFloat()/tasks.size},modifier=Modifier.fillMaxWidth())
            }
            items(tasks,key={it.id}){task->
                Surface(shape=MaterialTheme.shapes.medium,color=MaterialTheme.colorScheme.surfaceContainerHigh){
                    Column(Modifier.fillMaxWidth().padding(10.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){
                        Text(remember(task.post){VideoDownloadPlan.from(task.post).filename},style=MaterialTheme.typography.bodySmall)
                        Text(text(when(task.status){BatchStatus.WAITING->R.string.ui_queue_waiting;BatchStatus.DOWNLOADING->R.string.ui_queue_active;BatchStatus.COMPLETE->R.string.ui_queue_done;BatchStatus.FAILED->R.string.ui_queue_failed;BatchStatus.CANCELLED->R.string.ui_queue_cancelled;BatchStatus.PAUSED->R.string.ui_queue_paused}),style=MaterialTheme.typography.labelMedium)
                        if(task.status==BatchStatus.DOWNLOADING){
                            if(task.total>0)LinearProgressIndicator(progress={(task.bytes.toDouble()/task.total).toFloat().coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth())else LinearProgressIndicator(Modifier.fillMaxWidth())
                            Text("${task.bytes/1024} / ${if(task.total>=0)(task.total/1024).toString()else "—"} KiB",style=MaterialTheme.typography.labelSmall)
                        }
                        task.error?.let{Text(if(it.startsWith("HTTP "))it else libraryError(context,LibraryFailure(it)),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)}
                        when(task.status){
                            BatchStatus.FAILED,BatchStatus.CANCELLED->TextButton(onClick={queue.retry(task.id)}){Text(text(R.string.ui_retry))}
                            BatchStatus.WAITING,BatchStatus.DOWNLOADING,BatchStatus.PAUSED->TextButton(onClick={queue.cancel(task.id)}){Text(text(R.string.ui_cancel_download))}
                            else->Unit
                        }
                    }
                }
            }
            }
        }
    }
}
