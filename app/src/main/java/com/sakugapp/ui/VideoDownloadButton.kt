// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.ui

import com.sakugapp.R

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.sakugapp.data.*
import kotlinx.coroutines.*

@Composable fun VideoDownloadButton(post:Post) {
    val context=LocalContext.current
    val resolver=context.contentResolver
    val plan=remember(post){VideoDownloadPlan.from(post)}
    val scope=rememberCoroutineScope()
    var job by remember{mutableStateOf<Job?>(null)}
    var busy by remember{mutableStateOf(false)}
    var done by remember{mutableStateOf(false)}
    var cancelled by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf<String?>(null)}
    var bytes by remember{mutableLongStateOf(0L)}
    var total by remember{mutableLongStateOf(-1L)}
    var status by remember{mutableStateOf(false)}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(plan.mime)){uri->
        if(uri!=null) {
            busy=true;done=false;cancelled=false;error=null;bytes=0;total=-1;status=true
            job=scope.launch {
                var updated=0L
                try {
                    VideoDownloader().download(plan,
                        open={checkNotNull(resolver.openOutputStream(uri,"w"))},
                        discard={android.provider.DocumentsContract.deleteDocument(resolver,uri)},
                        progress={received,length->
                            val now=System.nanoTime()
                            if(now-updated>100_000_000L || received==length){updated=now;scope.launch{bytes=received;total=length}}
                        })
                    done=true
                }catch(e:CancellationException){cancelled=true;throw e}
                catch(e:Exception){error=e.message ?: context.getString(R.string.ui_video_download_failed_please_retry)}
                finally{busy=false}
            }
        }
    }
    ActionIcon(R.drawable.ic_download,text(R.string.ui_download_video),{if(busy)status=true else picker.launch(plan.filename)},Modifier.testTag("download-video"))
    if(status)AlertDialog(onDismissRequest={status=false},title={Text(text(R.string.ui_download_video))},text={Column {
        if(busy) {
            if(total>0)LinearProgressIndicator(progress={(bytes.toDouble()/total).toFloat().coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth())else LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(if(total>0)text(R.string.ui_download_total,bytes/1024,total/1024)else text(R.string.ui_download_bytes,bytes/1024))
            Text(text(R.string.ui_keep_this_page_open_while_downloading),style=MaterialTheme.typography.bodySmall)
            TextButton(onClick={job?.cancel()}){Text(text(R.string.ui_cancel_download))}
        }
        if(done)Text(text(R.string.ui_video_saved),modifier=Modifier.testTag("download-complete"))
        if(cancelled)Text(text(R.string.ui_download_cancelled))
        error?.let{Text(translateMessage(it),color=MaterialTheme.colorScheme.error)}
    }},confirmButton={TextButton(onClick={status=false}){Text(text(R.string.ui_close))}})
}
